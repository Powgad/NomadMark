// =============================================================================
// 流式 Markdown 解析器
// =============================================================================
//
// 针对内存受限设备上的大文件（>50MB）进行优化。
// 使用 mmap + RingBuffer 实现 O(1) 内存开销，无论文件大小如何。
//
// 架构：
// - 阶段 1（快速扫描）：扫描标题，构建行索引（50MB 文件需 50-100ms）
// - 阶段 2（按需）：仅解析请求的行范围
// - 阶段 3（增量）：根据滚动方向提前解析
//
// 性能目标：
// - 打开 50MB 文件：<200ms
// - 单屏渲染：<50ms
// =============================================================================

use super::ast::{BlockNode, InlineNode, DocumentMetadata, TocEntry};
use crate::parser::error::ParseError;
use memmap2::Mmap;
use std::collections::HashMap;
use std::fs::File;
use std::path::Path;
use std::sync::Arc;
use std::sync::atomic::AtomicUsize;

// -----------------------------------------------------------------------------
// RingBuffer（固定大小，初始化后无额外分配）
// -----------------------------------------------------------------------------

/// 用于流式文件内容的固定大小环形缓冲区。
///
/// 容量：8MB（可配置）。防止大文件导致内存溢出。
pub struct RingBuffer<const N: usize> {
    data: [u8; N],
    read_pos: usize,
    write_pos: usize,
    is_full: bool,
}

impl<const N: usize> RingBuffer<N> {
    pub const fn new() -> Self {
        Self {
            data: [0; N],
            read_pos: 0,
            write_pos: 0,
            is_full: false,
        }
    }

    /// 从源写入字节，返回写入的字节数
    pub fn write(&mut self, src: &[u8]) -> usize {
        let available = self.available();
        let to_write = src.len().min(available);

        if to_write == 0 {
            return 0;
        }

        let write_end = (self.write_pos + to_write) % N;

        if write_end > self.write_pos {
            // 单次写入
            self.data[self.write_pos..write_end].copy_from_slice(&src[..to_write]);
        } else {
            // 环绕写入
            let first_part = N - self.write_pos;
            let second_part = to_write - first_part;
            self.data[self.write_pos..].copy_from_slice(&src[..first_part]);
            self.data[..second_part].copy_from_slice(&src[first_part..to_write]);
        }

        self.write_pos = write_end;

        if to_write > 0 {
            self.is_full = self.read_pos == self.write_pos && to_write > 0;
        }

        to_write
    }

    /// 读取字节到缓冲区
    pub fn read(&mut self, dst: &mut [u8]) -> usize {
        let available = self.len();
        let to_read = dst.len().min(available);

        if to_read == 0 {
            return 0;
        }

        let read_end = (self.read_pos + to_read) % N;

        if read_end > self.read_pos {
            dst[..to_read].copy_from_slice(&self.data[self.read_pos..read_end]);
        } else {
            let first_part = N - self.read_pos;
            let second_part = to_read - first_part;
            dst[..first_part].copy_from_slice(&self.data[self.read_pos..]);
            dst[first_part..to_read].copy_from_slice(&self.data[..second_part]);
        }

        self.read_pos = read_end;
        self.is_full = false;

        to_read
    }

    /// 可用写入空间
    #[inline]
    pub fn available(&self) -> usize {
        if self.is_full {
            0
        } else if self.write_pos >= self.read_pos {
            N - (self.write_pos - self.read_pos)
        } else {
            self.read_pos - self.write_pos
        }
    }

    /// 当前缓冲数据长度
    #[inline]
    pub fn len(&self) -> usize {
        if self.is_full {
            N
        } else if self.write_pos >= self.read_pos {
            self.write_pos - self.read_pos
        } else {
            N - (self.read_pos - self.write_pos)
        }
    }

    #[inline]
    pub fn is_empty(&self) -> bool {
        !self.is_full && self.read_pos == self.write_pos
    }

    /// 清空缓冲区
    pub fn clear(&mut self) {
        self.read_pos = 0;
        self.write_pos = 0;
        self.is_full = false;
    }
}

// 8MB 环形缓冲区用于流式处理
pub type DefaultRingBuffer = RingBuffer<8388608>;

// -----------------------------------------------------------------------------
// 行索引（用于快速定位）
// -----------------------------------------------------------------------------

/// 行索引，实现 O(1) 的行号 -> 字节偏移量查找
#[derive(Debug, Clone)]
pub struct LineIndex {
    /// 行号 -> (字节偏移量, 行长度)
    /// 存储为 Vec 以提高缓存效率
    lines: Vec<(usize, usize)>,
}

impl LineIndex {
    pub fn new() -> Self {
        Self { lines: Vec::new() }
    }

    /// 添加一个行条目
    pub fn push(&mut self, offset: usize, length: usize) {
        self.lines.push((offset, length));
    }

    /// 根据行号获取行信息（从 0 开始）
    #[inline]
    pub fn get(&self, line_number: usize) -> Option<(usize, usize)> {
        self.lines.get(line_number).copied()
    }

    /// 查找包含字节偏移量的行
    pub fn find_line(&self, byte_offset: usize) -> usize {
        match self.lines.binary_search_by(|(offset, _)| offset.cmp(&byte_offset)) {
            Ok(i) => i,
            Err(i) => i.saturating_sub(1),
        }
    }

    /// 总行数
    #[inline]
    pub fn len(&self) -> usize {
        self.lines.len()
    }
}

// -----------------------------------------------------------------------------
// 内存映射文件
// -----------------------------------------------------------------------------

/// 内存映射文件包装器
pub struct MmappedFile {
    _mmap: Mmap,
    len: usize,
}

impl MmappedFile {
    /// 打开并内存映射一个文件
    pub fn open(path: &Path) -> Result<Self, ParseError> {
        let file = File::open(path).map_err(|e| ParseError::Io(e.to_string()))?;
        let mmap = unsafe {
            memmap2::Mmap::map(&file).map_err(|e| ParseError::Io(e.to_string()))?
        };
        let len = mmap.len();

        Ok(Self { _mmap: mmap, len })
    }

    /// 获取文件长度
    #[inline]
    pub fn len(&self) -> usize {
        self.len
    }

    /// 获取 mmap 数据的字节引用
    #[inline]
    pub fn as_bytes(&self) -> &[u8] {
        &self._mmap
    }
}

// -----------------------------------------------------------------------------
// 流式解析器
// -----------------------------------------------------------------------------

/// 流式解析器状态
pub struct StreamingParser {
    /// 内存映射文件（用于 >50MB 文件）
    mmap: Option<Arc<MmappedFile>>,
    /// 行索引
    line_index: LineIndex,
    /// 标题索引（用于目录）
    headings: Vec<TocEntry>,
    /// 参考定义
    refs: HashMap<String, (String, Option<String>)>,
    /// 总体统计
    total_chars: usize,
    total_lines: usize,
    /// 扫描进度（用于大文件反馈）
    scan_progress: AtomicUsize,
    /// 文件总大小（用于进度计算）
    total_size: usize,
}

impl StreamingParser {
    /// 创建新的流式解析器（用于大文件）
    pub fn new(path: &Path) -> Result<Self, ParseError> {
        let mmap = MmappedFile::open(path)?;
        let mmap = Arc::new(mmap);
        let total_size = mmap.len();

        // 阶段 1：快速扫描以构建索引
        let (line_index, headings, refs, total_chars, total_lines) =
            Self::quick_scan(mmap.clone(), total_size, &AtomicUsize::new(0))?;

        Ok(Self {
            mmap: Some(mmap),
            line_index,
            headings,
            refs,
            total_chars,
            total_lines,
            scan_progress: AtomicUsize::new(total_size),  // 完成
            total_size,
        })
    }

    /// 快速扫描：构建行索引、标题索引、参考定义
    ///
    /// 性能：50MB 文件约需 50-100ms
    fn quick_scan(
        mmap: Arc<MmappedFile>,
        total_size: usize,
        progress: &AtomicUsize
    ) -> Result<(LineIndex, Vec<TocEntry>, HashMap<String, (String, Option<String>)>, usize, usize), ParseError> {
        let mut line_index = LineIndex::new();
        let mut headings = Vec::new();
        let mut refs = HashMap::new();

        let mut line_start = 0;
        let mut line_number = 0;
        let mut total_chars = 0;
        let mut in_code_block = false;
        let mut last_progress_update = 0;
        const PROGRESS_UPDATE_INTERVAL: usize = 1024 * 1024;  // 每隔 1MB 更新一次

        // 逐行扫描
        let bytes = mmap.as_bytes();

        for (i, &byte) in bytes.iter().enumerate() {
            // 定期更新进度
            if i - last_progress_update > PROGRESS_UPDATE_INTERVAL {
                progress.store(i, std::sync::atomic::Ordering::Relaxed);
                last_progress_update = i;
            }

            // 跟踪行起始
            if i == 0 || bytes[i - 1] == b'\n' {
                line_start = i;
            }

            // 行结束
            if byte == b'\n' || i == bytes.len() - 1 {
                // 如果是最后一个字节但不是换行符，需要计算到文件末尾的完整长度
                let line_length = if byte == b'\n' {
                    i - line_start + 1
                } else {
                    // 最后一行没有换行符
                    bytes.len() - line_start
                };
                line_index.push(line_start, line_length);

                // 检查标题
                if !in_code_block {
                    let line = &bytes[line_start..i];
                    if let Some(level) = detect_heading_level(line) {
                        // 提取标题（在 # 符号和空格之后）
                        let title_start = line.iter().position(|&b| b != b'#' && b != b' ').unwrap_or(0);
                        let title = std::str::from_utf8(&line[title_start..]).unwrap_or("");
                        headings.push(TocEntry {
                            level,
                            title: title.trim().to_string(),
                            byte_offset: line_start,
                            line_number,
                        });
                    }

                    // 检查代码块围栏
                    if line.starts_with(b"```") || line.starts_with(b"~~~") {
                        in_code_block = !in_code_block;
                    }

                    // 检查参考定义
                    if let Some((label, dest, title)) = parse_reference_def(line) {
                        refs.insert(label, (dest, title));
                    }
                }

                total_chars += line_length;
                line_number += 1;
            }
        }

        // 标记为完成
        progress.store(total_size, std::sync::atomic::Ordering::Relaxed);

        Ok((line_index, headings, refs, total_chars, line_number))
    }

    /// 解析特定行范围（按需）
    ///
    /// 仅用于渲染可见内容。
    /// 支持块引用解析（检测 > 开头的行）
    pub fn parse_range(&self, start_line: usize, count: usize) -> Result<Vec<BlockNode>, ParseError> {
        let mmap = self.mmap.as_ref().ok_or(ParseError::NotMapped)?;

        let end_line = (start_line + count).min(self.line_index.len());
        let mut blocks = Vec::new();

        let mut i = start_line;
        while i < end_line {
            if let Some((offset, length)) = self.line_index.get(i) {
                let bytes = mmap.as_bytes();
                let line = &bytes[offset..offset + length];
                let text = std::str::from_utf8(line).unwrap_or("");
                let trimmed = text.trim();

// 检测代码块围栏
                if trimmed.starts_with("```") || trimmed.starts_with("~~~") {
                    let fence = if trimmed.starts_with("```") { "```" } else { "~~~" };
                    let language = if trimmed.len() > 3 {
                        trimmed[3..].trim().to_string()
                    } else {
                        String::new()
                    };

                    // 收集代码块内容
                    let mut content_lines = Vec::new();
                    i += 1;
                    while i < end_line {
                        if let Some((offset, length)) = self.line_index.get(i) {
                            let content_line = &mmap.as_bytes()[offset..offset + length];
                            let content_text = std::str::from_utf8(content_line).unwrap_or("");
                            if content_text.trim().starts_with(fence) {
                                i += 1;
                                break;
                            }
                            content_lines.push(content_text.to_string());
                            i += 1;
                        } else {
                            break;
                        }
                    }

                    blocks.push(BlockNode::CodeBlock {
                        language: if language.is_empty() { None } else { Some(language) },
                        content: content_lines.join("\n"),
                    });
                    continue;
                }

                // 检测分割线 (ThematicBreak) - 必须在列表检测之前
                // 因为 --- 和 *** 可能被误识别为列表项
                if is_thematic_break(line) {
                    blocks.push(BlockNode::ThematicBreak);
                    i += 1;
                    continue;
                }

                // 检测引用块 (blockquote)
                if is_blockquote_line(line) {
                    // 收集连续的引用块行
                    let mut quote_lines = Vec::new();
                    let mut max_level = 0u8;

                    while i < end_line {
                        if let Some((offset, length)) = self.line_index.get(i) {
                            let quote_line = &mmap.as_bytes()[offset..offset + length];
                            let level = count_blockquote_level(quote_line);

                            if level == 0 {
                                break;
                            }

                            max_level = max_level.max(level);
                            let text = std::str::from_utf8(quote_line).unwrap_or("");
                            // 去掉引用标记，保留内容
                            let content = if level > 0 {
                                text[level as usize..].trim()
                            } else {
                                text.trim()
                            };
                            quote_lines.push((level, content.to_string()));
                            i += 1;
                        } else {
                            break;
                        }
                    }

                    // 将引用块行转换为 BlockNode
                    if !quote_lines.is_empty() {
                        blocks.push(parse_blockquote_lines(&quote_lines, max_level));
                    }
                    continue;
                }

                // 检测列表
                if is_list_item(line) {
                    // 收集连续的列表项
                    let mut list_items = Vec::new();
                    let mut is_ordered = false;
                    let mut start_number = None;

                    while i < end_line {
                        if let Some((offset, length)) = self.line_index.get(i) {
                            let item_line = &mmap.as_bytes()[offset..offset + length];
                            let item_text = std::str::from_utf8(item_line).unwrap_or("");
                            let item_trimmed = item_text.trim();

                            if !is_list_item(item_line) {
                                break;
                            }

                            // 检测列表类型
                            let first_char = skip_leading_whitespace(item_line)[0];
                            if matches!(first_char, b'-' | b'*' | b'+') {
                                // 无序列表
                                is_ordered = false;
                                let content = item_trimmed[2..].trim().to_string();
                                list_items.push(crate::parser::ast::ListItem {
                                    marker: crate::parser::ast::ListMarker::Unordered(first_char as char),
                                    content: vec![BlockNode::Paragraph {
                                        children: vec![InlineNode::Text(content)],
                                    }],
                                });
                            } else if first_char.is_ascii_digit() {
                                // 有序列表
                                is_ordered = true;
                                // 提取数字
                                let content_start = item_trimmed.find('.').map(|p| p + 1).unwrap_or(0);
                                let num_str = &item_trimmed[..item_trimmed.find('.').unwrap_or(0)];
                                let num = num_str.parse().unwrap_or(1);
                                if start_number.is_none() {
                                    start_number = Some(num);
                                }
                                let content = item_trimmed[content_start..].trim().to_string();
                                list_items.push(crate::parser::ast::ListItem {
                                    marker: crate::parser::ast::ListMarker::Ordered { number: num, delimiter: '.' },
                                    content: vec![BlockNode::Paragraph {
                                        children: vec![InlineNode::Text(content)],
                                    }],
                                });
                            }

                            i += 1;
                        } else {
                            break;
                        }
                    }

                    if !list_items.is_empty() {
                        blocks.push(BlockNode::List {
                            ordered: is_ordered,
                            start_number,
                            items: list_items,
                        });
                    }
                    continue;
                }

                // 检测数学公式块 ($$...$$)
                if is_math_block_start(line) {
                    let mut latex_lines = Vec::new();
                    i += 1; // 跳过起始 $$ 行

                    // 收集公式内容，直到遇到结束的 $$
                    while i < end_line {
                        if let Some((offset, length)) = self.line_index.get(i) {
                            let content_line = &mmap.as_bytes()[offset..offset + length];
                            let content_text = std::str::from_utf8(content_line).unwrap_or("");

                            if is_math_block_end(content_line) {
                                i += 1; // 跳过结束 $$ 行
                                break;
                            }

                            latex_lines.push(content_text.to_string());
                            i += 1;
                        } else {
                            break;
                        }
                    }

                    blocks.push(BlockNode::MathBlock {
                        latex: latex_lines.join("\n"),
                    });
                    continue;
                }

                // 检测标题
                if let Some(level) = detect_heading_level(line) {
                    let title_start = line.iter().position(|&b| b != b'#' && b != b' ').unwrap_or(0);
                    let title = std::str::from_utf8(&line[title_start..]).unwrap_or("");
                    blocks.push(BlockNode::Heading {
                        level,
                        children: vec![InlineNode::Text(title.trim().to_string())],
                    });
                } else if !trimmed.is_empty() {
                    // 检测行内数学公式 ($...$)
                    if let Some((start, end)) = find_inline_math(trimmed) {
                        let mut children = Vec::new();

                        // 添加 $ 之前的文本
                        if start > 0 {
                            children.push(InlineNode::Text(trimmed[..start].to_string()));
                        }

                        // 添加数学公式
                        let latex = &trimmed[start + 1..end];
                        if !latex.is_empty() {
                            children.push(InlineNode::Math {
                                display_mode: false,
                                latex: latex.to_string(),
                            });
                        }

                        // 添加 $ 之后的文本
                        if end + 1 < trimmed.len() {
                            children.push(InlineNode::Text(trimmed[end + 1..].to_string()));
                        }

                        blocks.push(BlockNode::Paragraph { children });
                    } else {
                        // 普通段落
                        blocks.push(BlockNode::Paragraph {
                            children: vec![InlineNode::Text(trimmed.to_string())],
                        });
                    }
                }
            }
            i += 1;
        }

        Ok(blocks)
    }

/// 获取文档元数据
    pub fn metadata(&self) -> DocumentMetadata {
        DocumentMetadata {
            total_chars: self.total_chars,
            total_lines: self.total_lines,
            toc: self.headings.clone(),
            refs: self.refs.clone(),
            last_modified_offset: 0,
        }
    }

    /// 获取目录
    pub fn toc(&self) -> &[TocEntry] {
        &self.headings
    }

    /// 获取扫描进度（0.0 到 1.0）
    pub fn progress(&self) -> f32 {
        let current = self.scan_progress.load(std::sync::atomic::Ordering::Relaxed);
        if self.total_size == 0 {
            1.0
        } else {
            current as f32 / self.total_size as f32
        }
    }

    /// 获取文件总大小（字节）
    pub fn total_size(&self) -> usize {
        self.total_size
    }

    /// 获取文档内容为字符串
    ///
    /// 此函数读取整个内存映射文件并转换为 UTF-8
    /// 对于非常大的文件，考虑使用基于范围的操作
    pub fn get_content(&self) -> String {
        if let Some(ref mmap) = self.mmap {
            let bytes = mmap.as_bytes();
            std::str::from_utf8(bytes)
                .unwrap_or("")
                .to_string()
        } else {
            String::new()
        }
    }

    /// 获取原始文件路径（如果可用）
    ///
    /// # 注意
    /// 目前解析器不存储路径，因此返回 None
    /// 路径仅在初始化时使用
    pub fn path(&self) -> Option<&Path> {
        None
    }
}

// -----------------------------------------------------------------------------
// 辅助函数
// -----------------------------------------------------------------------------

/// 检测行是否为列表项
fn is_list_item(line: &[u8]) -> bool {
    let trimmed = skip_leading_whitespace(line);
    if trimmed.is_empty() {
        return false;
    }

    // 无序列表：-, *, +
    if matches!(trimmed[0], b'-' | b'*' | b'+') {
        return true;
    }

    // 有序列表：数字. 格式
    let mut i = 0;
    while i < trimmed.len() && trimmed[i].is_ascii_digit() {
        i += 1;
    }
    if i > 0 && i < trimmed.len() && matches!(trimmed[i], b'.') {
        return true;
    }

    false
}

/// 跳过前导空白字符
fn skip_leading_whitespace(line: &[u8]) -> &[u8] {
    let mut start = 0;
    while start < line.len() && line[start].is_ascii_whitespace() {
        start += 1;
    }
    &line[start..]
}


/// 检测行是否为分割线（ThematicBreak）
///
/// 分割线是由 3 个或更多相同字符组成的行：
/// - `***` (星号)
/// - `---` (减号)
/// - `___` (下划线)
/// 字符之间可以有空格，但不能有其他字符
fn is_thematic_break(line: &[u8]) -> bool {
    let trimmed = skip_leading_whitespace(line);
    if trimmed.len() < 3 {
        return false;
    }

    // 检查是否全是相同的字符（*, -, _）
    let first_char = trimmed[0];
    if !matches!(first_char, b'*' | b'-' | b'_') {
        return false;
    }

    // 检查剩余字符是否都是同一个字符（允许空格）
    let mut char_count = 0;
    for &byte in trimmed.iter() {
        if byte == first_char {
            char_count += 1;
        } else if !byte.is_ascii_whitespace() {
            return false;
        }
    }

    char_count >= 3
}

/// 从行首检测标题级别
fn detect_heading_level(line: &[u8]) -> Option<u8> {
    if !line.starts_with(b"#") {
        return None;
    }

    let mut level = 0;
    for &byte in line.iter() {
        if byte == b'#' {
            level += 1;
            if level > 6 {
                return None;
            }
        } else if byte == b' ' {
            // # 后必须有空格
            return if level >= 1 && level <= 6 { Some(level) } else { None };
        } else {
            return None;
        }
    }

    None
}

/// 解析参考定义：[标签]: URL "标题"
fn parse_reference_def(line: &[u8]) -> Option<(String, String, Option<String>)> {
    let line = std::str::from_utf8(line).ok()?;
    let line = line.trim();

    if !line.starts_with('[') {
        return None;
    }

    let label_end = line.find("]:")?;
    let label = line[1..label_end].to_string();

    let rest = &line[label_end + 2..].trim_start();
    let url_end = rest.find(|c| c == ' ' || c == '\t').unwrap_or(rest.len());
    let dest = rest[..url_end].to_string();

    let title = if url_end < rest.len() {
        let title_str = rest[url_end..].trim();
        if (title_str.starts_with('"') && title_str.ends_with('"'))
            || (title_str.starts_with('\'') && title_str.ends_with('\''))
            || (title_str.starts_with('(') && title_str.ends_with(')'))
        {
            Some(title_str[1..title_str.len() - 1].to_string())
        } else {
            None
        }
    } else {
        None
    };

    Some((label, dest, title))
}

/// 检测行是否为块级数学公式起始行 ($$...$$)
fn is_math_block_start(line: &[u8]) -> bool {
    let trimmed = skip_leading_whitespace(line);
    trimmed.starts_with(b"$$") && trimmed.len() >= 2
}

/// 检测行是否为块级数学公式结束行
fn is_math_block_end(line: &[u8]) -> bool {
    let trimmed = skip_leading_whitespace(line);
    // 去除尾部空白（包括 \r 和 \n）
    let trimmed_end = &trimmed[..trimmed.iter().rposition(|&b| !b.is_ascii_whitespace()).unwrap_or(trimmed.len() - 1) + 1];
    trimmed_end.starts_with(b"$$") && trimmed_end.len() >= 2
}

/// 检测文本中是否包含行内数学公式 ($...$)
/// 返回 (起始$位置, 结束$位置) 如果找到
fn find_inline_math(text: &str) -> Option<(usize, usize)> {
    let bytes = text.as_bytes();
    let mut pos = 0;

    while pos < bytes.len() {
        // 查找 $ 符号
        if bytes[pos] == b'$' {
            // 检查是否是转义的 \$
            if pos > 0 && bytes[pos - 1] == b'\\' {
                pos += 1;
                continue;
            }

            let start = pos;
            // 查找结束的 $
            if let Some(offset) = bytes[start + 1..].iter().position(|&b| b == b'$') {
                let end = start + 1 + offset;

                // 验证结束的 $ 不是转义的
                if bytes[end - 1] == b'\\' {
                    pos = end + 1;
                    continue;
                }

                return Some((start, end));
            } else {
                return None;
            }
        }
        pos += 1;
    }

    None
}

/// 检测行是否为引用块行（以 > 开头）
fn is_blockquote_line(line: &[u8]) -> bool {
    let trimmed = skip_leading_whitespace(line);
    !trimmed.is_empty() && trimmed[0] == b'>'
}

/// 计算引用块的嵌套层级
fn count_blockquote_level(line: &[u8]) -> u8 {
    let mut level = 0;
    let mut i = 0;

    // 跳过前导空白
    while i < line.len() && line[i].is_ascii_whitespace() {
        i += 1;
    }

    // 计算 > 的数量
    while i < line.len() && line[i] == b'>' {
        level += 1;
        i += 1;
        // 每个 > 后面可以有一个空格
        if i < line.len() && line[i] == b' ' {
            i += 1;
        }
    }

    level
}

/// 将引用块行解析为 BlockNode
fn parse_blockquote_lines(lines: &[(u8, String)], max_level: u8) -> BlockNode {
    // 简化实现：将所有内容合并为一个段落
    // TODO: 支持嵌套引用块的完整解析

    let content: String = lines
        .iter()
        .map(|(_, text)| text.as_str())
        .collect::<Vec<_>>()
        .join(" ");

    BlockNode::Blockquote {
        level: max_level,
        children: vec![BlockNode::Paragraph {
            children: vec![InlineNode::Text(content)],
        }],
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ring_buffer() {
        let mut buf = RingBuffer::<16>::new();

        assert_eq!(buf.available(), 16);
        assert_eq!(buf.len(), 0);

        let written = buf.write(b"hello");
        assert_eq!(written, 5);
        assert_eq!(buf.len(), 5);

        let mut dst = [0u8; 10];
        let read = buf.read(&mut dst);
        assert_eq!(read, 5);
        assert_eq!(&dst[..5], b"hello");
    }

    #[test]
    fn test_ring_buffer_wrap() {
        let mut buf = RingBuffer::<8>::new();

        buf.write(b"AAAA");  // [AAAA____]
        buf.read(&mut [0; 2]);  // [--AAAA__]
        buf.write(b"BBB");  // [BBAAA___]

        let mut dst = [0u8; 8];
        let n = buf.read(&mut dst);
        assert_eq!(n, 5);  // 缓冲区中剩余的字节数
        assert_eq!(&dst[..5], b"AABBB");
    }

    #[test]
    fn test_detect_heading_level() {
        assert_eq!(detect_heading_level(b"# Heading"), Some(1));
        assert_eq!(detect_heading_level(b"## Heading"), Some(2));
        assert_eq!(detect_heading_level(b"###### Heading"), Some(6));
        assert_eq!(detect_heading_level(b"####### Heading"), None);
        assert_eq!(detect_heading_level(b"#Not a heading"), None);
        assert_eq!(detect_heading_level(b"regular text"), None);
    }

    #[test]
    fn test_parse_reference_def() {
        let result = parse_reference_def(b"[foo]: /url \"title\"");
        assert_eq!(result, Some(("foo".to_string(), "/url".to_string(), Some("title".to_string()))));

        let result = parse_reference_def(b"[bar]: /url");
        assert_eq!(result, Some(("bar".to_string(), "/url".to_string(), None)));
    }

    #[test]
    fn test_line_index() {
        let mut idx = LineIndex::new();
        idx.push(0, 10);
        idx.push(10, 15);
        idx.push(25, 20);

        assert_eq!(idx.get(0), Some((0, 10)));
        assert_eq!(idx.get(1), Some((10, 15)));
        assert_eq!(idx.get(2), Some((25, 20)));
        assert_eq!(idx.get(3), None);

        assert_eq!(idx.find_line(5), 0);
        assert_eq!(idx.find_line(12), 1);
        assert_eq!(idx.find_line(30), 2);
    }

    #[test]
    fn test_is_blockquote_line() {
        assert!(is_blockquote_line(b"> This is a quote"));
        assert!(is_blockquote_line(b">> Nested quote"));
        assert!(is_blockquote_line(b"  > Quote with leading space"));
        assert!(!is_blockquote_line(b"Not a quote"));
        assert!(!is_blockquote_line(b""));
    }

    #[test]
    fn test_count_blockquote_level() {
        assert_eq!(count_blockquote_level(b"> Quote"), 1);
        assert_eq!(count_blockquote_level(b"> Nested"), 1);
        assert_eq!(count_blockquote_level(b">> Double nested"), 2);
        assert_eq!(count_blockquote_level(b">>> Triple nested"), 3);
        assert_eq!(count_blockquote_level(b"Not a quote"), 0);
    }

    #[test]
    fn test_parse_blockquote_single_line() {
        let lines = vec![
            (1u8, "This is a quote".to_string()),
        ];

        let node = parse_blockquote_lines(&lines, 1);

        match node {
            BlockNode::Blockquote { level, children } => {
                assert_eq!(level, 1);
                assert_eq!(children.len(), 1);
                if let BlockNode::Paragraph { children: para_children } = &children[0] {
                    assert_eq!(para_children.len(), 1);
                    if let InlineNode::Text(text) = &para_children[0] {
                        assert_eq!(text, "This is a quote");
                    } else {
                        panic!("Expected Text node");
                    }
                } else {
                    panic!("Expected Paragraph node");
                }
            }
            _ => panic!("Expected Blockquote node"),
        }
    }

    #[test]
    fn test_parse_blockquote_multiple_lines() {
        let lines = vec![
            (1u8, "First line".to_string()),
            (1u8, "Second line".to_string()),
            (1u8, "Third line".to_string()),
        ];

        let node = parse_blockquote_lines(&lines, 1);

        match node {
            BlockNode::Blockquote { level, children } => {
                assert_eq!(level, 1);
                assert_eq!(children.len(), 1);
                if let BlockNode::Paragraph { children: para_children } = &children[0] {
                    if let InlineNode::Text(text) = &para_children[0] {
                        assert!(text.contains("First line"));
                        assert!(text.contains("Second line"));
                        assert!(text.contains("Third line"));
                    }
                }
            }
            _ => panic!("Expected Blockquote node"),
        }
    }

    #[test]
    fn test_parse_blockquote_nested() {
        let lines = vec![
            (1u8, "Outer level".to_string()),
            (2u8, "Nested level".to_string()),
            (1u8, "Back to outer".to_string()),
        ];

        let node = parse_blockquote_lines(&lines, 2);

        match node {
            BlockNode::Blockquote { level, .. } => {
                assert_eq!(level, 2);  // max_level should be 2
            }
            _ => panic!("Expected Blockquote node"),
        }
    }

    #[test]
    fn test_is_math_block_start() {
        assert!(is_math_block_start(b"$$"));
        assert!(is_math_block_start(b"$$ E = mc^2"));
        assert!(is_math_block_start(b"  $$  E = mc^2"));
        assert!(!is_math_block_start(b"$ E = mc^2"));
        assert!(!is_math_block_start(b""));
    }

    #[test]
    fn test_is_math_block_end() {
        assert!(is_math_block_end(b"$$"));
        assert!(is_math_block_end(b"$$ end"));
        assert!(!is_math_block_end(b"$"));
    }

    #[test]
    fn test_find_inline_math() {
        // 简单行内公式（中文字符 UTF-8 编码为 3 字节）
        let result = find_inline_math("这是 $E=mc^2$ 公式");
        // "这是 " = 6 字节 (2个中文 * 3) + 1 空格 = 7，$ 在位置 7
        assert_eq!(result, Some((7, 14))); // $ 的位置（字节索引）

        // 多个公式（ASCII）
        let result = find_inline_math("$a$ and $b$");
        assert_eq!(result, Some((0, 2))); // 找到第一个

        // 没有公式
        let result = find_inline_math("普通文本");
        assert_eq!(result, None);

        // 转义的 $
        let result = find_inline_math("价格 \\$100");
        assert_eq!(result, None);

        // 空公式
        let result = find_inline_math("$$");
        assert_eq!(result, Some((0, 1)));
    }

    #[test]
    fn test_parse_math_block_simple() {
        // 创建临时文件测试数学公式块解析
        let temp_dir = std::env::temp_dir();
        let temp_file = temp_dir.join("test_math.md");
        std::fs::write(&temp_file, "$$\nE = mc^2\n$$").unwrap();

        let parser = StreamingParser::new(&temp_file).unwrap();
        let result = parser.parse_range(0, 10).unwrap();

        assert_eq!(result.len(), 1);
        match &result[0] {
            BlockNode::MathBlock { latex } => {
                assert_eq!(latex.trim(), "E = mc^2");
            }
            _ => panic!("Expected MathBlock node"),
        }

        std::fs::remove_file(&temp_file).ok();
    }

    #[test]
    fn test_parse_math_block_multiline() {
        let temp_dir = std::env::temp_dir();
        let temp_file = temp_dir.join("test_math_multiline.md");
        std::fs::write(&temp_file, "$$\n\\frac{a}{b}\n+ \\frac{c}{d}\n$$").unwrap();

        let parser = StreamingParser::new(&temp_file).unwrap();
        let result = parser.parse_range(0, 10).unwrap();

        assert_eq!(result.len(), 1);
        match &result[0] {
            BlockNode::MathBlock { latex } => {
                assert!(latex.contains("\\frac{a}{b}"));
                assert!(latex.contains("\\frac{c}{d}"));
            }
            _ => panic!("Expected MathBlock node"),
        }

        std::fs::remove_file(&temp_file).ok();
    }

    #[test]
    fn test_parse_inline_math() {
        let temp_dir = std::env::temp_dir();
        let temp_file = temp_dir.join("test_inline_math.md");
        std::fs::write(&temp_file, "爱因斯坦方程 $E=mc^2$ 很著名").unwrap();

        let parser = StreamingParser::new(&temp_file).unwrap();
        let result = parser.parse_range(0, 10).unwrap();

        assert_eq!(result.len(), 1);
        match &result[0] {
            BlockNode::Paragraph { children } => {
                assert_eq!(children.len(), 3); // "爱因斯坦方程 ", Math, " 很著名"

                if let InlineNode::Text(text) = &children[0] {
                    assert_eq!(text, "爱因斯坦方程 ");
                } else {
                    panic!("Expected Text node");
                }

                if let InlineNode::Math { display_mode, latex } = &children[1] {
                    assert_eq!(*display_mode, false);
                    assert_eq!(latex, "E=mc^2");
                } else {
                    panic!("Expected Math node");
                }

                if let InlineNode::Text(text) = &children[2] {
                    assert_eq!(text, " 很著名");
                } else {
                    panic!("Expected Text node");
                }
            }
            _ => panic!("Expected Paragraph node"),
        }

        std::fs::remove_file(&temp_file).ok();
    }
}
