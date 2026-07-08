// =============================================================================
// NomadMark Core - 共享 Rust 库
// =============================================================================
//
// 本库编译为:
// - libmarkdown_core.so (Android/Linux)
// - libmarkdown_core.a (iOS/macOS)
// - markdown_core.dll (Windows)
//
// 所有 FFI 函数使用 #[no_mangle] 和 extern "C" 以实现 C ABI 兼容。
//
// 内存管理:
// - 所有返回的指针必须使用 md_free() 释放
// - 文档句柄是不透明指针 (*mut MarkdownDocument)
// - 数组以 (ptr, len) 对的形式返回
// =============================================================================

use std::ffi::{c_char, c_void};
use std::slice;
use std::str;

// 导出模块
pub mod bridge;
pub mod parser;
pub mod layout;
pub mod render;
pub mod insert;
pub mod history;
pub mod search;
pub mod replace;
pub mod math;
pub mod syntax;

// 确保 JNI 桥接已链接（bridge::jni 模块按条件编译）
use parser::streaming::StreamingParser;
use render::commands::RenderCommand;
use render::{RenderResult, dirty_rects_to_ffi};
use layout::engine::create_supernote_layouter;

// =============================================================================
// 辅助函数
// =============================================================================

/// 将 AST TOC 条目转换为 FFI TOC 条目
fn convert_toc_entry(ast_entry: &crate::parser::ast::TocEntry) -> bridge::types::TocEntry {
    bridge::types::TocEntry {
        level: ast_entry.level,
        byte_offset: ast_entry.byte_offset,
        line_number: ast_entry.line_number,
        title_len: ast_entry.title.len(),
        title_ptr: ast_entry.title.as_ptr(),
    }
}

// =============================================================================
// 文档句柄（不透明指针类型）
// =============================================================================

/// 渲染块缓存条目 (起始行, 结束行, 内存字节数)
#[derive(Clone, Debug)]
struct CachedBlock {
    start_line: usize,
    #[allow(dead_code)]
    end_line: usize,
    memory_bytes: usize,
}

/// 文档实例（内部）
///
/// 不通过 FFI 暴露 - 仅通过不透明句柄访问
pub struct MarkdownDocument {
    /// 解析的 AST（或大文件的流式解析器）
    parser: Option<StreamingParser>,
    /// 缓存的渲染结果
    cached_render: Option<RenderResult>,
    /// 上次渲染的视口
    #[allow(dead_code)]
    last_viewport: Option<(f32, f32)>,
    /// 渲染块缓存（用于内存管理）
    rendered_blocks: Vec<CachedBlock>,
    /// 总缓存内存（字节）
    cached_memory: usize,
    /// 撤销/重做操作的历史记录
    history: history::History,
}

impl MarkdownDocument {
    /// 从流式解析器创建（大文件）
    fn from_streaming(parser: StreamingParser) -> Self {
        Self {
            parser: Some(parser),
            cached_render: None,
            last_viewport: None,
            rendered_blocks: Vec::new(),
            cached_memory: 0,
            history: history::History::new(),
        }
    }

    /// 获取解析器引用
    fn parser(&self) -> &StreamingParser {
        self.parser.as_ref().expect("Document not initialized")
    }

    /// 获取历史记录引用（可变）- 用于修改
    fn history_mut(&mut self) -> &mut history::History {
        &mut self.history
    }

    /// 获取历史记录引用（只读）- 用于查询
    fn history(&self) -> &history::History {
        &self.history
    }

    /// 添加渲染块到缓存
    fn add_cached_block(&mut self, start_line: usize, end_line: usize, memory_bytes: usize) {
        self.rendered_blocks.push(CachedBlock {
            start_line,
            end_line,
            memory_bytes,
        });
        self.cached_memory += memory_bytes;
    }

    /// 释放指定行之前的缓存块
    /// 返回释放的字节数
    fn release_before(&mut self, line: usize) -> usize {
        let mut released = 0;
        self.rendered_blocks.retain(|block| {
            let keep = block.start_line >= line;
            if !keep {
                released += block.memory_bytes;
            }
            keep
        });
        self.cached_memory = self.cached_memory.saturating_sub(released);
        released
    }

    /// 获取当前缓存内存使用量
    fn cached_memory_usage(&self) -> usize {
        self.cached_memory
    }

    /// 释放最旧的缓存块以达到目标内存
    fn release_to_target(&mut self, target_bytes: usize) -> usize {
        let mut released = 0;
        while self.cached_memory > target_bytes && !self.rendered_blocks.is_empty() {
            if let Some(block) = self.rendered_blocks.first() {
                released += block.memory_bytes;
                self.cached_memory = self.cached_memory.saturating_sub(block.memory_bytes);
                self.rendered_blocks.remove(0);
            } else {
                break;
            }
        }
        released
    }
}

// =============================================================================
// FFI: 文档生命周期
// =============================================================================

/// 从内存内容创建文档实例
///
/// 适用于小文件 (<50MB)。对于大文件，使用 md_document_create_from_path
///
/// # 参数
/// - `content`: UTF-8 字符串内容指针
/// - `len`: 内容长度（字节）
///
/// # 返回值
/// 不透明文档句柄，失败时返回 NULL
#[no_mangle]
pub extern "C" fn md_document_create(
    content: *const c_char,
    len: usize,
) -> *mut MarkdownDocument {
    if content.is_null() || len == 0 {
        return std::ptr::null_mut();
    }

    unsafe {
        let bytes = slice::from_raw_parts(content as *const u8, len);

        // 验证 UTF-8
        let _content_str = match str::from_utf8(bytes) {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };

        // 为 StreamingParser 创建临时文件
        // (StreamingParser 需要文件路径用于 mmap)
        use std::io::Write;
        use std::time::{SystemTime, UNIX_EPOCH};

        let temp_dir = std::env::temp_dir();
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let temp_file = temp_dir.join(format!("nomadmark_temp_{}.md", timestamp));

        // 将内容写入临时文件
        if let Ok(mut file) = std::fs::File::create(&temp_file) {
            if file.write_all(bytes).is_ok() {
                // 使用 StreamingParser 加载内容
                match StreamingParser::new(&temp_file) {
                    Ok(parser) => {
                        // 删除临时文件（StreamingParser 已经 mmap 了它）
                        let _ = std::fs::remove_file(&temp_file);
                        let doc = Box::new(MarkdownDocument::from_streaming(parser));
                        return Box::into_raw(doc);
                    }
                    Err(_) => {
                        // 错误时清理
                        let _ = std::fs::remove_file(&temp_file);
                    }
                }
            }
        }

        std::ptr::null_mut()
    }
}

/// 从文件路径创建文档实例（大文件使用 mmap）
///
/// 推荐用于 >50MB 的文件。使用带 mmap 的流式解析器
///
/// # 参数
/// - `path`: 以 null 结尾的 UTF-8 文件路径指针
///
/// # 返回值
/// 不透明文档句柄，失败时返回 NULL
#[no_mangle]
pub extern "C" fn md_document_create_from_path(
    path: *const c_char,
) -> *mut MarkdownDocument {
    if path.is_null() {
        return std::ptr::null_mut();
    }

    unsafe {
        let path_str = match str::from_utf8(slice::from_raw_parts(
            path as *const u8,
            libc::strlen(path),
        )) {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };

        match StreamingParser::new(std::path::Path::new(path_str)) {
            Ok(parser) => {
                let doc = Box::new(MarkdownDocument::from_streaming(parser));
                Box::into_raw(doc)
            }
            Err(_) => std::ptr::null_mut(),
        }
    }
}

/// 释放文档实例
///
/// # 参数
/// - `handle`: 文档句柄（不能为 NULL）
#[no_mangle]
pub extern "C" fn md_document_release(handle: *mut MarkdownDocument) {
    if handle.is_null() {
        return;
    }

    unsafe {
        let _ = Box::from_raw(handle);
    }
}

// =============================================================================
// FFI: 渲染功能
// =============================================================================

/// 渲染指定行范围（支持大文件）
///
/// 仅解析和渲染请求的范围。用于虚拟滚动
///
/// # 参数
/// - `handle`: 文档句柄
/// - `start_line`: 起始行号（从 0 开始）
/// - `count`: 要渲染的行数
/// - `out_commands`: 输出渲染命令数组指针
/// - `out_count`: 输出命令数量指针
///
/// # 返回值
/// - 0: 成功
/// - -1: 失败
///
/// # 调用者责任
/// 必须对返回的命令数组调用 md_free_commands()
#[no_mangle]
pub extern "C" fn md_document_load_range(
    handle: *mut MarkdownDocument,
    start_line: usize,
    count: usize,
    out_commands: *mut *const RenderCommand,
    out_count: *mut usize,
    out_dirty_rects: *mut *const i32,
    out_dirty_count: *mut usize,
) -> i32 {
    if handle.is_null() || out_commands.is_null() || out_count.is_null() {
        return -1;
    }

    unsafe {
        let doc = &mut *handle;
        let parser = doc.parser();

        // 解析请求的范围
        match parser.parse_range(start_line, count) {
            Ok(blocks) => {
                // 对块进行布局
                let mut layouter = create_supernote_layouter();
                let mut result = RenderResult::new();

                for block in &blocks {
                    let block_result = layouter.layout_block(block);
                    result.commands.extend(block_result.commands);
                    result.dirty_rects.extend(block_result.dirty_rects);
                }

                result.merge_dirty_rects();

                // 在移动向量前存储长度
                let cmd_count = result.commands.len();
                let dirty_count = result.dirty_rects.len();

                // 估计缓存跟踪的内存使用量
                // RenderCommand: ~64 字节每个, 脏矩形: 16 字节每个
                let memory_bytes = cmd_count * 64 + dirty_count * 16;
                doc.add_cached_block(start_line, start_line + count, memory_bytes);

                // SAFETY: 泄漏命令到堆并返回稳定指针
                // 必须用 md_free_commands 释放
                let commands_box = result.commands.into_boxed_slice();
                let cmd_ptr = Box::leak(commands_box).as_ptr();

                // SAFETY: 泄漏脏矩形到堆并返回稳定指针
                // 必须用 md_free_dirty_rects 释放
                let dirty_array = dirty_rects_to_ffi(&result.dirty_rects);
                let dirty_ptr = Box::leak(dirty_array.into_boxed_slice()).as_ptr();

                *out_commands = cmd_ptr;
                *out_count = cmd_count;
                *out_dirty_rects = dirty_ptr;
                *out_dirty_count = dirty_count * 4; // dirty_array.len() = rects.len() * 4

                0
            }
            Err(_) => -1,
        }
    }
}

/// 获取文档元数据（目录、统计等）
///
/// # 参数
/// - `handle`: 文档句柄
/// - `out_toc`: 输出目录数组指针
/// - `out_toc_count`: 输出目录条目数量指针
///
/// # 返回值
/// 总字符数，错误时返回 0
#[no_mangle]
pub extern "C" fn md_document_get_metadata(
    handle: *mut MarkdownDocument,
    out_toc: *mut *const bridge::types::TocEntry,
    out_toc_count: *mut usize,
) -> usize {
    if handle.is_null() {
        return 0;
    }

    unsafe {
        let doc = &*handle;
        let parser = doc.parser();
        let metadata = parser.metadata();

        if !out_toc.is_null() && !out_toc_count.is_null() {
            let toc = parser.toc();
            // 将 AST TOC 转换为 FFI TOC
            let ffi_toc: Vec<bridge::types::TocEntry> = toc.iter().map(convert_toc_entry).collect();
            // SAFETY: 泄漏 TOC 以返回稳定指针
            // 必须用 md_free_toc 释放
            let toc_box = ffi_toc.into_boxed_slice();
            let toc_ptr = Box::leak(toc_box).as_ptr();
            *out_toc = toc_ptr;
            *out_toc_count = toc.len();
        }

        metadata.total_chars
    }
}

// =============================================================================
// FFI: 目录功能
// =============================================================================

/// 获取目录条目
///
/// # 参数
/// - `handle`: 文档句柄
/// - `out_entries`: 输出条目数组指针
/// - `out_count`: 输出条目数量指针
///
/// # 返回值
/// - 0: 成功
/// - -1: 失败
#[no_mangle]
pub extern "C" fn md_document_get_toc(
    handle: *mut MarkdownDocument,
    out_entries: *mut *const bridge::types::TocEntry,
    out_count: *mut usize,
) -> i32 {
    if handle.is_null() || out_entries.is_null() || out_count.is_null() {
        return -1;
    }

    unsafe {
        let doc = &*handle;
        let parser = doc.parser();
        let toc = parser.toc();

        // 将 AST TOC 转换为 FFI TOC
        let ffi_toc: Vec<bridge::types::TocEntry> = toc.iter().map(convert_toc_entry).collect();

        // SAFETY: 泄漏 TOC 以返回稳定指针
        // 必须用 md_free_toc 释放
        let toc_box = ffi_toc.into_boxed_slice();
        let toc_ptr = Box::leak(toc_box).as_ptr();

        *out_entries = toc_ptr;
        *out_count = toc.len();

        0
    }
}

// =============================================================================
// FFI: 进度（大文件反馈）
// =============================================================================

/// 获取文档加载进度
///
/// 对于大文件，返回扫描进度（0.0 到 1.0）
/// 可从 UI 线程轮询以显示加载指示器
///
/// # 参数
/// - `handle`: 文档句柄
///
/// # 返回值
/// 进度值（0.0 = 刚开始，1.0 = 完成）
#[no_mangle]
pub extern "C" fn md_document_get_progress(
    handle: *mut MarkdownDocument,
) -> f32 {
    if handle.is_null() {
        return 1.0;  // 如果为 null，假定已完成
    }

    unsafe {
        let doc = &*handle;
        doc.parser().progress()
    }
}

/// 获取文档总文件大小（字节）
///
/// # 参数
/// - `handle`: 文档句柄
///
/// # 返回值
/// 文件大小（字节），错误时返回 0
#[no_mangle]
pub extern "C" fn md_document_get_file_size(
    handle: *mut MarkdownDocument,
) -> usize {
    if handle.is_null() {
        return 0;
    }

    unsafe {
        let doc = &*handle;
        doc.parser().total_size()
    }
}

// =============================================================================
// FFI: 搜索功能
// =============================================================================

/// 在文档中执行全文搜索
///
/// # 参数
/// - `handle`: 文档句柄
/// - `query`: 搜索查询字符串 (UTF-8)
/// - `query_len`: 查询字符串长度（字节）
/// - `out_results`: 输出搜索结果数组指针
/// - `out_count`: 输出搜索结果数量
///
/// # 返回值
/// - 0: 成功
/// - -1: 失败
///
/// # 内存管理
/// 调用者必须使用 md_free_search_results() 释放结果
#[no_mangle]
pub extern "C" fn md_document_search(
    handle: *mut MarkdownDocument,
    query: *const c_char,
    query_len: usize,
    out_results: *mut *const bridge::types::SearchResult,
    out_count: *mut usize,
) -> i32 {
    if handle.is_null() || query.is_null() || out_results.is_null() || out_count.is_null() {
        return -1;
    }

    unsafe {
        let doc = &*handle;
        let parser = doc.parser();

        // 将查询字符串转换为 Rust 字符串
        let query_str = match str::from_utf8(slice::from_raw_parts(query as *const u8, query_len)) {
            Ok(s) => s,
            Err(_) => {
                *out_results = std::ptr::null();
                *out_count = 0;
                return -1;
            }
        };

        // 获取文档内容
        let content = parser.get_content();

        // 使用 Searcher 执行搜索
        let searcher = search::Searcher::new(content);
        let options = search::SearchOptions::default();
        let results = searcher.search(query_str, &options);

        if results.is_empty() {
            *out_results = std::ptr::null();
            *out_count = 0;
            return 0;
        }

        // 将内部 SearchResult 转换为 FFI SearchResult
        let ffi_results: Vec<bridge::types::SearchResult> = results
            .into_iter()
            .map(|r| bridge::types::SearchResult {
                start: r.start,
                end: r.end,
                line_number: r.line_number,
            })
            .collect();

        // 在移动前获取数量
        let count = ffi_results.len();

        // 泄漏到堆并返回稳定指针
        let results_box = ffi_results.into_boxed_slice();
        *out_results = Box::leak(results_box).as_ptr();
        *out_count = count;

        0
    }
}

// =============================================================================
// FFI: 历史记录（撤销/重做）
// =============================================================================

/// 撤销上一个操作
///
/// # 参数
/// - `handle`: 文档句柄
///
/// # 返回值
/// - 1: 撤销成功
/// - 0: 无可撤销操作
/// - -1: 错误
///
/// # 注意
/// 此函数执行历史记录栈操作。实际的文档内容修改需要完整的编辑系统支持
#[no_mangle]
pub extern "C" fn md_document_undo(
    handle: *mut MarkdownDocument,
) -> i32 {
    if handle.is_null() {
        return -1;
    }

    unsafe {
        let doc = &mut *handle;
        if let Some(_command) = doc.history_mut().undo() {
            // TODO: 应用撤销操作到文档内容
            // 这需要完整的编辑系统来修改解析器的内容
            1
        } else {
            0
        }
    }
}

/// 重做上一个撤销的操作
///
/// # 参数
/// - `handle`: 文档句柄
///
/// # 返回值
/// - 1: 重做成功
/// - 0: 无可重做操作
/// - -1: 错误
///
/// # 注意
/// 此函数执行历史记录栈操作。实际的文档内容修改需要完整的编辑系统支持
#[no_mangle]
pub extern "C" fn md_document_redo(
    handle: *mut MarkdownDocument,
) -> i32 {
    if handle.is_null() {
        return -1;
    }

    unsafe {
        let doc = &mut *handle;
        if let Some(_command) = doc.history_mut().redo() {
            // TODO: 应用重做操作到文档内容
            // 这需要完整的编辑系统来修改解析器的内容
            1
        } else {
            0
        }
    }
}

/// 检查是否可以撤销
///
/// # 参数
/// - `handle`: 文档句柄
///
/// # 返回值
/// - 1: 可以撤销
/// - 0: 无可撤销操作
/// - -1: 错误
#[no_mangle]
pub extern "C" fn md_document_can_undo(
    handle: *mut MarkdownDocument,
) -> i32 {
    if handle.is_null() {
        return -1;
    }

    unsafe {
        let doc = &*handle;
        if doc.history().can_undo() { 1 } else { 0 }
    }
}

/// 检查是否可以重做
///
/// # 参数
/// - `handle`: 文档句柄
///
/// # 返回值
/// - 1: 可以重做
/// - 0: 无可重做操作
/// - -1: 错误
#[no_mangle]
pub extern "C" fn md_document_can_redo(
    handle: *mut MarkdownDocument,
) -> i32 {
    if handle.is_null() {
        return -1;
    }

    unsafe {
        let doc = &*handle;
        if doc.history().can_redo() { 1 } else { 0 }
    }
}

// =============================================================================
// FFI: 内存管理
// =============================================================================

/// 释放 Rust 分配的脏矩形数组内存 (i32 数组)
///
/// 用于释放:
/// - 脏矩形数组 (来自 md_document_load_range, md_document_get_dirty_rects)
///
/// # 参数
/// - `ptr`: i32 数组指针 (可为 NULL)
/// - `len`: 数组元素数量（不是矩形数量）
#[no_mangle]
pub extern "C" fn md_free_dirty_rects(ptr: *mut i32, len: usize) {
    if ptr.is_null() || len == 0 {
        return;
    }

    unsafe {
        // 从泄漏的切片重建 Box<[i32]> 并释放
        let _ = Box::from_raw(std::slice::from_raw_parts_mut(ptr, len));
    }
}

/// 释放 Rust 分配的渲染命令内存
///
/// 用于释放:
/// - 渲染命令数组 (来自 md_document_load_range)
///
/// # 参数
/// - `ptr`: RenderCommand 数组指针 (可为 NULL)
/// - `len`: 命令数量
#[no_mangle]
pub extern "C" fn md_free_commands(ptr: *mut RenderCommand, len: usize) {
    if ptr.is_null() || len == 0 {
        return;
    }

    unsafe {
        // 从泄漏的切片重建 Box<[RenderCommand]> 并释放
        let _ = Box::from_raw(std::slice::from_raw_parts_mut(ptr, len));
    }
}

/// 释放 Rust 分配的目录条目内存
///
/// 用于释放:
/// - 目录条目数组 (来自 md_document_get_toc, md_document_get_metadata)
///
/// # 参数
/// - `ptr`: TocEntry 数组指针 (可为 NULL)
/// - `len`: 条目数量
#[no_mangle]
pub extern "C" fn md_free_toc(ptr: *mut bridge::types::TocEntry, len: usize) {
    if ptr.is_null() || len == 0 {
        return;
    }

    unsafe {
        // 从泄漏的切片重建 Box<[TocEntry]> 并释放
        let _ = Box::from_raw(std::slice::from_raw_parts_mut(ptr, len));
    }
}

/// 释放 Rust 分配的搜索结果内存
///
/// 用于释放:
/// - 搜索结果数组 (来自 md_document_search)
///
/// # 参数
/// - `ptr`: SearchResult 数组指针 (可为 NULL)
/// - `len`: 结果数量
#[no_mangle]
pub extern "C" fn md_free_search_results(ptr: *mut bridge::types::SearchResult, len: usize) {
    if ptr.is_null() || len == 0 {
        return;
    }

    unsafe {
        // 从泄漏的切片重建 Box<[SearchResult]> 并释放
        let _ = Box::from_raw(std::slice::from_raw_parts_mut(ptr, len));
    }
}

/// 释放 Rust 分配的内存（通用回退函数）
///
/// 已弃用: 请使用特定的释放函数:
/// - md_free_dirty_rects 用于 i32 数组
/// - md_free_commands 用于 RenderCommand 数组
/// - md_free_toc 用于 TocEntry 数组
///
/// 此函数保留用于向后兼容
///
/// # 参数
/// - `ptr`: 已分配内存的指针 (可为 NULL)
#[no_mangle]
pub extern "C" fn md_free(ptr: *mut c_void) {
    if ptr.is_null() {
        return;
    }

    unsafe {
        // 重建 Box 并释放
        // 假设所有分配都是 Box::leak() 的 Box<[u8]>
        let _ = Box::from_raw(ptr as *mut u8);
    }
}

/// 获取当前内存使用量估计值
///
/// # 参数
/// - `handle`: 文档句柄
///
/// # 返回值
/// 内存使用量（字节），错误时返回 0
#[no_mangle]
pub extern "C" fn md_document_get_memory_usage(
    handle: *mut MarkdownDocument,
) -> usize {
    if handle.is_null() {
        return 0;
    }

    unsafe {
        let doc = &*handle;

        // 使用跟踪的缓存内存
        let cached = doc.cached_memory_usage();

        // 添加基础解析器内存估计
        let parser = doc.parser();
        let metadata = parser.metadata();

        // 基础估计: 行索引 + 目录 + 开销
        let base = metadata.total_lines * 16 + metadata.toc.len() * 64;

        cached + base
    }
}

/// 释放缓存内存以达到目标阈值
///
/// # 参数
/// - `handle`: 文档句柄
/// - `target_bytes`: 目标内存使用量（字节）
///
/// # 返回值
/// 实际释放的字节数
#[no_mangle]
pub extern "C" fn md_document_release_to_target(
    handle: *mut MarkdownDocument,
    target_bytes: usize,
) -> usize {
    if handle.is_null() {
        return 0;
    }

    unsafe {
        let doc = &mut *handle;
        doc.release_to_target(target_bytes)
    }
}

/// 释放指定行之前的内容以释放内存
///
/// 滚动大文档时使用，防止内存溢出
///
/// # 参数
/// - `handle`: 文档句柄
/// - `line`: 释放此行号之前的所有内容
///
/// # 返回值
/// 释放的字节数
#[no_mangle]
pub extern "C" fn md_document_release_before(
    handle: *mut MarkdownDocument,
    line: usize,
) -> usize {
    if handle.is_null() {
        return 0;
    }

    unsafe {
        let doc = &mut *handle;
        doc.release_before(line)
    }
}

// =============================================================================
// FFI: 脏矩形（用于局部刷新）
// =============================================================================

/// 计算指定行范围的脏矩形
///
/// UI 层使用此函数确定需要刷新的区域
///
/// # 参数
/// - `handle`: 文档句柄
/// - `start_line`: 起始行号
/// - `count`: 行数
/// - `out_rects`: 输出脏矩形数组指针 [x, y, w, h, x, y, w, h, ...]
/// - `out_count`: 输出矩形数量（数量 = 数组长度 / 4）
///
/// # 返回值
/// - 0: 成功
/// - -1: 失败
#[no_mangle]
pub extern "C" fn md_document_get_dirty_rects(
    handle: *mut MarkdownDocument,
    _start_line: usize,
    _count: usize,
    out_rects: *mut *const i32,
    out_count: *mut usize,
) -> i32 {
    if handle.is_null() || out_rects.is_null() || out_count.is_null() {
        return -1;
    }

    unsafe {
        let doc = &mut *handle;

        // 如果有缓存的渲染结果，使用它
        if let Some(ref result) = doc.cached_render {
            let dirty_array = dirty_rects_to_ffi(&result.dirty_rects);
            let dirty_count = dirty_array.len();
            // SAFETY: 泄漏脏矩形到堆并返回稳定指针
            let dirty_ptr = Box::leak(dirty_array.into_boxed_slice()).as_ptr();

            *out_rects = dirty_ptr;
            *out_count = dirty_count;

            return 0;
        }

        *out_rects = std::ptr::null();
        *out_count = 0;

        -1
    }
}

// =============================================================================
// Rust 专用 API（用于 Tauri Desktop）
// =============================================================================
//
// 这些函数不通过 FFI 导出
// 它们直接由桌面 Tauri 后端使用
// =============================================================================

/// Core 实例（桌面端单线程）
pub struct Core {
    /// 打开的文档
    documents: std::collections::HashMap<u64, MarkdownDocument>,
    /// 下一个文档 ID
    next_id: u64,
}

impl Core {
    pub fn new() -> Self {
        Self {
            documents: std::collections::HashMap::new(),
            next_id: 1,
        }
    }

    /// 从文件路径打开文档
    pub fn open_document(&mut self, path: &str) -> Result<u64, String> {
        let parser = StreamingParser::new(std::path::Path::new(path))
            .map_err(|e| e.to_string())?;

        let doc = MarkdownDocument::from_streaming(parser);
        let id = self.next_id;
        self.next_id += 1;

        self.documents.insert(id, doc);
        Ok(id)
    }

    /// 获取文档引用
    pub fn get_document(&self, id: u64) -> Option<&MarkdownDocument> {
        self.documents.get(&id)
    }

    /// 获取文档可变引用
    pub fn get_document_mut(&mut self, id: u64) -> Option<&mut MarkdownDocument> {
        self.documents.get_mut(&id)
    }

    /// 获取文档元数据
    pub fn get_metadata(&self, id: u64) -> Option<parser::ast::DocumentMetadata> {
        self.get_document(id).map(|doc| doc.parser().metadata())
    }

    /// 在文档中搜索
    pub fn search(&self, id: u64, query: &str) -> Vec<bridge::types::SearchResult> {
        if let Some(doc) = self.get_document(id) {
            let content = doc.parser().get_content();
            let searcher = search::Searcher::new(content);
            let options = search::SearchOptions::default();
            let results = searcher.search(query, &options);

            results
                .into_iter()
                .map(|r| bridge::types::SearchResult {
                    start: r.start,
                    end: r.end,
                    line_number: r.line_number,
                })
                .collect()
        } else {
            Vec::new()
        }
    }

    /// 获取目录
    pub fn get_toc(&self, id: u64) -> Vec<parser::ast::TocEntry> {
        self.get_document(id)
            .map(|doc| doc.parser().toc().to_vec())
            .unwrap_or_default()
    }

    /// 获取文档内容
    pub fn get_content(&self, id: u64) -> Result<String, String> {
        self.get_document(id)
            .map(|doc| doc.parser().get_content())
            .ok_or_else(|| "Document not found".to_string())
    }

    /// 撤销操作
    pub fn undo(&mut self, id: u64) -> Result<bool, String> {
        if let Some(doc) = self.get_document_mut(id) {
            Ok(doc.history_mut().undo().is_some())
        } else {
            Err("Document not found".to_string())
        }
    }

    /// 重做操作
    pub fn redo(&mut self, id: u64) -> Result<bool, String> {
        if let Some(doc) = self.get_document_mut(id) {
            Ok(doc.history_mut().redo().is_some())
        } else {
            Err("Document not found".to_string())
        }
    }

    /// 检查是否可以撤销
    pub fn can_undo(&self, id: u64) -> Result<bool, String> {
        if let Some(doc) = self.get_document(id) {
            Ok(doc.history().can_undo())
        } else {
            Err("Document not found".to_string())
        }
    }

    /// 检查是否可以重做
    pub fn can_redo(&self, id: u64) -> Result<bool, String> {
        if let Some(doc) = self.get_document(id) {
            Ok(doc.history().can_redo())
        } else {
            Err("Document not found".to_string())
        }
    }

    /// 保存文档
    ///
    /// # 注意
    /// 此功能需要文档关联文件路径
    /// 目前 StreamingParser 不存储路径，因此此实现是占位符
    pub fn save(&self, _id: u64) -> Result<(), String> {
        // TODO: 实现保存功能
        // 需要:
        // 1. 在 MarkdownDocument 或 StreamingParser 中存储文件路径
        // 2. 通过 get_content() 获取当前内容
        // 3. 写入文件
        Err("Save not yet implemented - requires file path storage".to_string())
    }
}

impl Default for Core {
    fn default() -> Self {
        Self::new()
    }
}

// =============================================================================
// Tests
// =============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_core_new() {
        let core = Core::new();
        assert_eq!(core.next_id, 1);
    }

    #[test]
    fn test_md_document_null_safety() {
        // 这些操作不应导致崩溃
        md_document_release(std::ptr::null_mut());
        md_free(std::ptr::null_mut());
        assert_eq!(md_document_undo(std::ptr::null_mut()), -1);
        assert_eq!(md_document_redo(std::ptr::null_mut()), -1);
        assert_eq!(md_document_can_undo(std::ptr::null_mut()), -1);
        assert_eq!(md_document_can_redo(std::ptr::null_mut()), -1);
        assert_eq!(md_document_get_memory_usage(std::ptr::null_mut()), 0);
    }

    #[test]
    fn test_document_create_and_release() {
        let content = "# Test\n\nHello world";
        let ptr = md_document_create(
            content.as_ptr() as *const c_char,
            content.len(),
        );
        assert!(!ptr.is_null());
        md_document_release(ptr);
    }

    #[test]
    fn test_search_functionality() {
        let content = "Hello world, hello universe";
        let ptr = md_document_create(
            content.as_ptr() as *const c_char,
            content.len(),
        );
        assert!(!ptr.is_null());

        let mut results_ptr: *const bridge::types::SearchResult = std::ptr::null();
        let mut count: usize = 0;

        let result = md_document_search(
            ptr,
            b"hello".as_ptr() as *const c_char,
            5,
            &mut results_ptr,
            &mut count,
        );

        assert_eq!(result, 0);
        assert_eq!(count, 2); // 应该找到两次 "hello"（不区分大小写）

        // 清理
        if !results_ptr.is_null() && count > 0 {
            md_free_search_results(results_ptr as *mut bridge::types::SearchResult, count);
        }
        md_document_release(ptr);
    }

    #[test]
    fn test_undo_redo_empty_history() {
        let content = "# Test";
        let ptr = md_document_create(
            content.as_ptr() as *const c_char,
            content.len(),
        );
        assert!(!ptr.is_null());

        // 初始状态，无撤销/重做可用
        assert_eq!(md_document_can_undo(ptr), 0);
        assert_eq!(md_document_can_redo(ptr), 0);
        assert_eq!(md_document_undo(ptr), 0);
        assert_eq!(md_document_redo(ptr), 0);

        md_document_release(ptr);
    }

    #[test]
    fn test_core_search() {
        let mut core = Core::new();
        let content = "Hello world, hello universe";

        // 创建临时文件用于测试
        let temp_dir = std::env::temp_dir();
        let temp_file = temp_dir.join("test_search.md");
        std::fs::write(&temp_file, content).unwrap();

        let id = core.open_document(temp_file.to_str().unwrap()).unwrap();
        let results = core.search(id, "hello");

        assert_eq!(results.len(), 2);

        // 清理
        std::fs::remove_file(&temp_file).ok();
    }

    #[test]
    fn test_syntax_highlighting_rust() {
        use syntax::CodeHighlighter;

        let highlighter = CodeHighlighter::new();
        let code = r#"
fn main() {
    let greeting = "Hello, world!";
    println!("{}", greeting);

    // 计算斐波那契数列
    let result = fib(10);
    println!("Fibonacci: {}", result);
}

fn fib(n: u32) -> u32 {
    match n {
        0 => 0,
        1 => 1,
        _ => fib(n - 1) + fib(n - 2),
    }
}
"#;

        let tokens = highlighter.highlight(code, Some("rust"));

        // 应该有多个 token
        assert!(tokens.len() > 10);

        // 验证有不同类型的 token（不一定包含特定的关键字）
        let token_types: std::collections::HashSet<_> = tokens.iter()
            .map(|t| t.token_type)
            .collect();

        // 至少应该有多种类型的 token
        assert!(token_types.len() > 1);
    }

    #[test]
    fn test_syntax_highlighting_python() {
        use syntax::CodeHighlighter;

        let highlighter = CodeHighlighter::new();
        let code = r#"
def hello_world():
    # 这是注释
    greeting = "Hello, World!"
    print(greeting)

class MyClass:
    def __init__(self, value):
        self.value = value

    def show(self):
        return self.value
"#;

        let tokens = highlighter.highlight(code, Some("python"));

        // 应该有多个 token
        assert!(tokens.len() > 10);

        // 验证有不同类型的 token
        let token_types: std::collections::HashSet<_> = tokens.iter()
            .map(|t| t.token_type)
            .collect();

        assert!(token_types.len() > 1);
    }

    #[test]
    fn test_syntax_highlighting_javascript() {
        use syntax::CodeHighlighter;

        let highlighter = CodeHighlighter::new();
        let code = r#"
function fibonacci(n) {
    // 基础情况
    if (n <= 1) return n;

    // 递归调用
    return fibonacci(n - 1) + fibonacci(n - 2);
}

const result = fibonacci(10);
console.log(`Fibonacci: ${result}`);
"#;

        let tokens = highlighter.highlight(code, Some("javascript"));

        // 应该有多个 token
        assert!(tokens.len() > 10);
    }

    #[test]
    fn test_syntax_highlighting_unsupported_language() {
        use syntax::CodeHighlighter;

        let highlighter = CodeHighlighter::new();
        let code = "some random code without language support";

        let tokens = highlighter.highlight(code, Some("unknown_language_xyz"));

        // 不支持的语言应该返回单个文本 token
        assert_eq!(tokens.len(), 1);
        assert_eq!(tokens[0].token_type, syntax::TokenType::Text);
    }

    #[test]
    fn test_syntax_highlighting_eink_theme() {
        use syntax::CodeHighlightTheme;
        use crate::bridge::types::Color;

        let theme = CodeHighlightTheme::eink();

        // 验证 E-ink 主题颜色
        assert_eq!(theme.keyword, Color::rgb(0x00, 0x00, 0x00)); // 纯黑
        assert_eq!(theme.comment, Color::rgb(0x99, 0x99, 0x99)); // 浅灰
        assert_eq!(theme.background, Color::rgb(0xF5, 0xF5, 0xF5)); // 浅灰背景

        // 验证颜色映射
        let keyword_color = theme.color_for_token("keyword");
        assert_eq!(keyword_color, Color::rgb(0x00, 0x00, 0x00));

        let comment_color = theme.color_for_token("comment");
        assert_eq!(comment_color, Color::rgb(0x99, 0x99, 0x99));

        let string_color = theme.color_for_token("string");
        assert_eq!(string_color, Color::rgb(0x44, 0x44, 0x44));
    }

    #[test]
    fn test_language_selector() {
        use syntax::{LanguageSelector, SupportedLanguage};

        let selector = LanguageSelector::new();

        // 测试扩展名解析
        assert_eq!(selector.parse(Some("rs")), SupportedLanguage::Rust);
        assert_eq!(selector.parse(Some("py")), SupportedLanguage::Python);
        assert_eq!(selector.parse(Some("js")), SupportedLanguage::JavaScript);

        // 测试名称解析
        assert_eq!(selector.parse(Some("rust")), SupportedLanguage::Rust);
        assert_eq!(selector.parse(Some("python")), SupportedLanguage::Python);
        assert_eq!(selector.parse(Some("javascript")), SupportedLanguage::JavaScript);

        // 测试空值
        assert_eq!(selector.parse(None), SupportedLanguage::PlainText);
        assert_eq!(selector.parse(Some("")), SupportedLanguage::PlainText);
    }

    #[test]
    fn test_blockquote_parsing_and_rendering() {
        let content = r#"# 测试引用块

> 这是一个简单的引用块
> 有多行内容

> 第一级引用
>> 第二级引用
>>> 第三级引用

---

> 引用块后跟分割线
"#;

        let ptr = md_document_create(
            content.as_ptr() as *const c_char,
            content.len(),
        );
        assert!(!ptr.is_null(), "文档创建失败");

        // 渲染文档
        let mut commands_ptr: *const RenderCommand = std::ptr::null();
        let mut count: usize = 0;
        let mut dirty_ptr: *const i32 = std::ptr::null();
        let mut dirty_count: usize = 0;

        let result = md_document_load_range(
            ptr,
            0,
            100,
            &mut commands_ptr as *mut _ as *mut _,
            &mut count as *mut _ as *mut _,
            &mut dirty_ptr as *mut _ as *mut _,
            &mut dirty_count as *mut _ as *mut _,
        );

        assert_eq!(result, 0, "加载范围失败");
        assert!(count > 0, "应该有渲染命令");

        // 验证渲染命令包含引用块和分割线
        let commands = unsafe { std::slice::from_raw_parts(commands_ptr, count) };

        // 检查是否有 FillRect 命令（用于引用块背景）
        let has_fill_rect = commands.iter().any(|cmd| cmd.cmd_type == render::commands::RenderCommandType::FillRect);
        assert!(has_fill_rect, "应该有 FillRect 命令用于引用块背景");

        // 检查是否有 DrawLine 命令（用于分割线）
        let has_draw_line = commands.iter().any(|cmd| cmd.cmd_type == render::commands::RenderCommandType::DrawLine);
        assert!(has_draw_line, "应该有 DrawLine 命令用于分割线");

        // 清理
        if !commands_ptr.is_null() && count > 0 {
            md_free_commands(commands_ptr as *mut RenderCommand, count);
        }
        if !dirty_ptr.is_null() && dirty_count > 0 {
            md_free_dirty_rects(dirty_ptr as *mut i32, dirty_count);
        }
        md_document_release(ptr);
    }

    #[test]
    fn test_thematic_break_variations() {
        // 测试三种分割线变体
        let content = r#"---

***

___
"#;

        let ptr = md_document_create(
            content.as_ptr() as *const c_char,
            content.len(),
        );
        assert!(!ptr.is_null());

        let mut commands_ptr: *const RenderCommand = std::ptr::null();
        let mut count: usize = 0;
        let mut dirty_ptr: *const i32 = std::ptr::null();
        let mut dirty_count: usize = 0;

        let result = md_document_load_range(
            ptr,
            0,
            10,
            &mut commands_ptr as *mut _ as *mut _,
            &mut count as *mut _ as *mut _,
            &mut dirty_ptr as *mut _ as *mut _,
            &mut dirty_count as *mut _ as *mut _,
        );

        assert_eq!(result, 0);

        let commands = unsafe { std::slice::from_raw_parts(commands_ptr, count) };

        // 应该有3条 DrawLine 命令（三个分割线）
        let line_count = commands.iter()
            .filter(|cmd| cmd.cmd_type == render::commands::RenderCommandType::DrawLine)
            .count();
        assert_eq!(line_count, 3, "应该有3条分割线");

        // 清理
        if !commands_ptr.is_null() && count > 0 {
            md_free_commands(commands_ptr as *mut RenderCommand, count);
        }
        if !dirty_ptr.is_null() && dirty_count > 0 {
            md_free_dirty_rects(dirty_ptr as *mut i32, dirty_count);
        }
        md_document_release(ptr);
    }

    #[test]
    fn test_nested_blockquote_indentation() {
        // 测试嵌套引用块的解析
        // 注意：当前实现将嵌套引用块解析为单一 Blockquote，level 为最大深度
        // 完整的嵌套支持需要更新 parse_blockquote_lines 函数（已有 TODO）

        let content = r#"> 外层
>> 中层
>>> 内层
"#;

        let ptr = md_document_create(
            content.as_ptr() as *const c_char,
            content.len(),
        );
        assert!(!ptr.is_null());

        let mut commands_ptr: *const RenderCommand = std::ptr::null();
        let mut count: usize = 0;
        let mut dirty_ptr: *const i32 = std::ptr::null();
        let mut dirty_count: usize = 0;

        let result = md_document_load_range(
            ptr,
            0,
            10,
            &mut commands_ptr as *mut _ as *mut _,
            &mut count as *mut _ as *mut _,
            &mut dirty_ptr as *mut _ as *mut _,
            &mut dirty_count as *mut _,
        );

        assert_eq!(result, 0);

        let commands = unsafe { std::slice::from_raw_parts(commands_ptr, count) };

        // 当前实现：创建单一 Blockquote，level 为最大深度（3）
        // 这会产生一个 FillRect 背景，缩进为 3 * 12px = 36px
        let fill_rects: Vec<_> = commands.iter()
            .filter(|cmd| cmd.cmd_type == render::commands::RenderCommandType::FillRect)
            .collect();

        // 验证至少有一个背景矩形
        assert!(!fill_rects.is_empty(), "应该有引用块背景");

        // 验证缩进值（每级 12px，3级 = 36px）
        // 基础 margin_left 加上缩进
        let expected_indent = 3.0 * 12.0; // 36px for level 3
        let actual_x = fill_rects[0].x;
        // 验证 x 坐标包含预期的缩进
        assert!(actual_x >= expected_indent, "嵌套引用块应该有相应的缩进");

        // 清理
        if !commands_ptr.is_null() && count > 0 {
            md_free_commands(commands_ptr as *mut RenderCommand, count);
        }
        if !dirty_ptr.is_null() && dirty_count > 0 {
            md_free_dirty_rects(dirty_ptr as *mut i32, dirty_count);
        }
        md_document_release(ptr);
    }

    #[test]
    fn test_math_block_parsing_and_rendering() {
        let content = r#"# 数学公式测试

这是一个简单的行内公式：$E=mc^2$

块级公式：

$$
x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
$$
"#;

        let ptr = md_document_create(
            content.as_ptr() as *const c_char,
            content.len(),
        );
        assert!(!ptr.is_null());

        let mut commands_ptr: *const RenderCommand = std::ptr::null();
        let mut count: usize = 0;
        let mut dirty_ptr: *const i32 = std::ptr::null();
        let mut dirty_count: usize = 0;

        let result = md_document_load_range(
            ptr,
            0,
            20,
            &mut commands_ptr as *mut _ as *mut _,
            &mut count as *mut _ as *mut _,
            &mut dirty_ptr as *mut _ as *mut _,
            &mut dirty_count as *mut _ as *mut _,
        );

        assert_eq!(result, 0);
        assert!(count > 0, "应该有渲染命令");

        let commands = unsafe { std::slice::from_raw_parts(commands_ptr, count) };

        // 检查是否有 FillRect 命令（数学公式占位符背景）
        let fill_rect_count = commands.iter()
            .filter(|cmd| cmd.cmd_type == render::commands::RenderCommandType::FillRect)
            .count();

        assert!(fill_rect_count >= 2, "应该至少有 2 个 FillRect 命令（行内公式和块级公式）");

        // 清理
        if !commands_ptr.is_null() && count > 0 {
            md_free_commands(commands_ptr as *mut RenderCommand, count);
        }
        if !dirty_ptr.is_null() && dirty_count > 0 {
            md_free_dirty_rects(dirty_ptr as *mut i32, dirty_count);
        }
        md_document_release(ptr);
    }

    #[test]
    fn test_inline_math_in_paragraph() {
        let content = "爱因斯坦方程 $E=mc^2$ 很著名";

        let ptr = md_document_create(
            content.as_ptr() as *const c_char,
            content.len(),
        );
        assert!(!ptr.is_null());

        let mut commands_ptr: *const RenderCommand = std::ptr::null();
        let mut count: usize = 0;
        let mut dirty_ptr: *const i32 = std::ptr::null();
        let mut dirty_count: usize = 0;

        let result = md_document_load_range(
            ptr,
            0,
            10,
            &mut commands_ptr as *mut _ as *mut _,
            &mut count as *mut _ as *mut _,
            &mut dirty_ptr as *mut _ as *mut _,
            &mut dirty_count as *mut _ as *mut _,
        );

        assert_eq!(result, 0);

        let commands = unsafe { std::slice::from_raw_parts(commands_ptr, count) };

        // 应该有文本和数学公式占位符
        let text_count = commands.iter()
            .filter(|cmd| cmd.cmd_type == render::commands::RenderCommandType::DrawText)
            .count();

        assert!(text_count >= 2, "应该至少有 2 个文本命令（普通文本和数学公式占位符）");

        // 清理
        if !commands_ptr.is_null() && count > 0 {
            md_free_commands(commands_ptr as *mut RenderCommand, count);
        }
        if !dirty_ptr.is_null() && dirty_count > 0 {
            md_free_dirty_rects(dirty_ptr as *mut i32, dirty_count);
        }
        md_document_release(ptr);
    }

    #[test]
    fn test_callout_kind() {
        use crate::parser::CalloutKind;

        // 测试 Callout 类型解析
        assert_eq!(CalloutKind::from_str("info"), Some(CalloutKind::Info));
        assert_eq!(CalloutKind::from_str("warning"), Some(CalloutKind::Warning));
        assert_eq!(CalloutKind::from_str("tip"), Some(CalloutKind::Tip));
        assert_eq!(CalloutKind::from_str("important"), Some(CalloutKind::Important));
        assert_eq!(CalloutKind::from_str("caution"), Some(CalloutKind::Caution));
        assert_eq!(CalloutKind::from_str("success"), Some(CalloutKind::Success));
        assert_eq!(CalloutKind::from_str("unknown"), None);

        // 测试 Callout 属性
        let info_kind = CalloutKind::Info;
        assert_eq!(info_kind.default_title(), "信息");
        assert_eq!(info_kind.icon(), "ℹ️");

        let warning_kind = CalloutKind::Warning;
        assert_eq!(warning_kind.default_title(), "警告");
        assert_eq!(warning_kind.icon(), "⚠️");

        // 测试 E-ink 颜色
        let border = info_kind.border_color();
        assert!(border.0 < 255); // 所有颜色值应该有效
    }

    #[test]
    fn test_extension_parsing() {
        use crate::parser::extensions;

        // 测试高亮解析
        assert_eq!(extensions::find_highlight("==hello=="), Some((7, "hello".to_string())));
        assert_eq!(extensions::find_highlight("==world== text"), Some((7, "world".to_string())));
        assert!(extensions::find_highlight("==test").is_none());

        // 测试下划线解析
        assert_eq!(extensions::find_underline("<u>hello</u>"), Some((8, "hello".to_string())));
        assert_eq!(extensions::find_underline("<u>world</u> text"), Some((8, "world".to_string())));
        assert!(extensions::find_underline("<u>test").is_none());

        // 测试 TOC 标记
        assert!(extensions::is_toc_marker("[TOC]"));
        assert!(extensions::is_toc_marker("[toc]"));
        assert!(!extensions::is_toc_marker("[TODO]"));

        // 测试 Callout 检测
        assert!(extensions::is_callout_block(b"> [!INFO]").is_some());
        assert!(extensions::is_callout_block(b"> [!TIP]").is_some());
        assert!(extensions::is_callout_block(b"> [!WARNING]").is_some());
        assert!(extensions::is_callout_block(b"> [!CAUTION]").is_some());
        assert!(extensions::is_callout_block(b"> [!SUCCESS]").is_some());
        assert!(extensions::is_callout_block(b"> [!UNKNOWN]").is_none());
    }

    #[test]
    fn test_ast_extensions() {
        use crate::parser::ast::{InlineNode, BlockNode, CalloutKind};

        // 测试下划线节点
        let underline = InlineNode::Underline {
            children: vec![InlineNode::Text("test".to_string())],
        };
        assert_eq!(underline.text_content(), "test");

        // 测试高亮节点
        let highlight = InlineNode::Highlight {
            children: vec![InlineNode::Text("important".to_string())],
        };
        assert_eq!(highlight.text_content(), "important");

        // 测试 Callout 块
        let callout = BlockNode::Callout {
            kind: CalloutKind::Info,
            title: Some("注意".to_string()),
            children: vec![],
        };

        if let BlockNode::Callout { kind, title, .. } = callout {
            assert_eq!(kind, CalloutKind::Info);
            assert_eq!(title, Some("注意".to_string()));
        } else {
            panic!("应该是 Callout 节点");
        }

        // 测试目录块
        let toc = BlockNode::TableOfContents;
        match toc {
            BlockNode::TableOfContents => {}, // 正确
            _ => panic!("应该是 TableOfContents 节点"),
        }
    }
}

// 启用功能时强制包含 JNI 模块
#[cfg(feature = "jni")]
pub use bridge::jni;

// 在 lib.rs 中的直接测试函数，用于验证导出
#[no_mangle]
pub extern "C" fn direct_test_export() -> i32 {
    123
}
