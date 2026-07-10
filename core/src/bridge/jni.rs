// =============================================================================
// Android JNI 桥接层
// =============================================================================
//
// 本模块提供 JNI 风格的包装函数，将 Kotlin/Java
// 本地方法调用连接到底层 C FFI 函数。
//
// JNI 命名约定：Java_<package_name>_<class_name>_<method_name>
// =============================================================================

use std::ffi::CStr;

// 导入 JNI 函数所需的类型
use crate::render::commands::RenderCommand;
use crate::bridge::types::TocEntry;

// 简单的测试函数，用于验证 JNI 符号是否导出
// 不使用条件编译，始终包含此函数
#[no_mangle]
pub extern "C" fn test_jni_export() -> i32 {
    42
}

// 编译此模块时始终包含 JNI
#[cfg(feature = "jni")]
use jni::sys::{jfloat, jint, jlong, jstring, jobject, jsize, jbyteArray, jlongArray, jintArray};
#[cfg(feature = "jni")]
use jni::{JNIEnv, objects::{JString, JLongArray, JIntArray}};

// 从父 crate 导入 C FFI 函数
extern "C" {
    fn md_document_create(content: *const i8, len: usize) -> *mut crate::MarkdownDocument;
    fn md_document_create_from_path(path: *const i8) -> *mut crate::MarkdownDocument;
    fn md_document_release(handle: *mut crate::MarkdownDocument);
    fn md_document_get_progress(handle: *mut crate::MarkdownDocument) -> f32;
    fn md_document_get_file_size(handle: *mut crate::MarkdownDocument) -> usize;
    fn md_document_get_memory_usage(handle: *mut crate::MarkdownDocument) -> usize;
    fn md_document_release_to_target(handle: *mut crate::MarkdownDocument, target_bytes: usize) -> usize;
    fn md_document_undo(handle: *mut crate::MarkdownDocument) -> i32;
    fn md_document_redo(handle: *mut crate::MarkdownDocument) -> i32;
    fn md_document_release_before(handle: *mut crate::MarkdownDocument, line: usize) -> usize;
    fn md_document_load_range(
        handle: *mut crate::MarkdownDocument,
        start_line: usize,
        count: usize,
        out_commands: *mut *const RenderCommand,
        out_count: *mut usize,
        out_dirty_rects: *mut *const i32,
        out_dirty_count: *mut usize,
    ) -> i32;
    fn md_document_get_toc(
        handle: *mut crate::MarkdownDocument,
        out_entries: *mut *const TocEntry,
        out_count: *mut usize,
    ) -> i32;
    fn md_free_commands(ptr: *mut RenderCommand, len: usize);
    fn md_free_toc(ptr: *mut TocEntry, len: usize);
    fn md_free_dirty_rects(ptr: *mut i32, len: usize);
}

// =============================================================================
// JNI 函数 - MarkdownCore
// =============================================================================

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeCreate
///
/// # 字符串生命周期
/// 使用 JNI 安全 API 获取字符串内容，确保指针在转换期间有效。
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeCreate(
    mut env: JNIEnv,
    _class: jobject,
    content: jstring,
) -> jlong {
    unsafe {
        if content.is_null() {
            return 0;
        }

        // 使用 get_string() 安全地获取 Java 字符串
        // JString 引用在 j_str 生命周期内保持有效
        let j_str = JString::from_raw(content);
        let s = env.get_string(&j_str);
        if s.is_err() {
            return 0;
        }

        // 在 JNI 调用期间转换，此时字符串仍然有效
        let rust_str = s.unwrap();
        let bytes = rust_str.as_bytes();
        let handle = md_document_create(bytes.as_ptr() as *const i8, bytes.len());

        // std::mem::forget 防止 j_str 过早释放（虽然这里不是必需的，
        // 因为 s 已经包含内容，但保持一致性）
        std::mem::forget(j_str);

        handle as jlong
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeCreateFromPath
///
/// # 字符串生命周期
/// 使用 JNI 安全 API 获取字符串内容，确保指针在转换期间有效。
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeCreateFromPath(
    mut env: JNIEnv,
    _class: jobject,
    path: jstring,
) -> jlong {
    unsafe {
        if path.is_null() {
            return 0;
        }

        // 使用 get_string() 安全地获取 Java 字符串
        let j_str = JString::from_raw(path);
        let s = env.get_string(&j_str);
        if s.is_err() {
            return 0;
        }

        // 在 JNI 调用期间转换，此时字符串仍然有效
        let rust_str = s.unwrap();
        let handle = md_document_create_from_path(rust_str.as_ptr() as *const i8);

        std::mem::forget(j_str);

        handle as jlong
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeRelease
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeRelease(
    _env: JNIEnv,
    _class: jobject,
    handle: jlong,
) {
    unsafe {
        if handle != 0 {
            md_document_release(handle as *mut crate::MarkdownDocument);
        }
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeGetProgress
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeGetProgress(
    _env: JNIEnv,
    _class: jobject,
    handle: jlong,
) -> jfloat {
    unsafe {
        if handle == 0 {
            return 0.0;
        }
        md_document_get_progress(handle as *mut crate::MarkdownDocument)
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeGetFileSize
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeGetFileSize(
    _env: JNIEnv,
    _class: jobject,
    handle: jlong,
) -> jlong {
    unsafe {
        if handle == 0 {
            return 0;
        }
        md_document_get_file_size(handle as *mut crate::MarkdownDocument) as jlong
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeGetMemoryUsage
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeGetMemoryUsage(
    _env: JNIEnv,
    _class: jobject,
    handle: jlong,
) -> jlong {
    unsafe {
        if handle == 0 {
            return 0;
        }
        md_document_get_memory_usage(handle as *mut crate::MarkdownDocument) as jlong
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeReleaseToTarget
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeReleaseToTarget(
    _env: JNIEnv,
    _class: jobject,
    handle: jlong,
    target_bytes: jlong,
) -> jlong {
    unsafe {
        if handle == 0 {
            return 0;
        }
        md_document_release_to_target(
            handle as *mut crate::MarkdownDocument,
            target_bytes as usize,
        ) as jlong
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeUndo
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeUndo(
    _env: JNIEnv,
    _class: jobject,
    handle: jlong,
) -> jint {
    unsafe {
        if handle == 0 {
            return 0;
        }
        md_document_undo(handle as *mut crate::MarkdownDocument)
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeRedo
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeRedo(
    _env: JNIEnv,
    _class: jobject,
    handle: jlong,
) -> jint {
    unsafe {
        if handle == 0 {
            return 0;
        }
        md_document_redo(handle as *mut crate::MarkdownDocument)
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeReleaseBefore
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeReleaseBefore(
    _env: JNIEnv,
    _class: jobject,
    handle: jlong,
    line: jint,
) {
    unsafe {
        if handle != 0 {
            md_document_release_before(handle as *mut crate::MarkdownDocument, line as usize);
        }
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeFreeCommands
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeFreeCommands(
    _env: JNIEnv,
    _class: jobject,
    ptr: jlong,
    len: jint,
) {
    unsafe {
        if ptr != 0 {
            md_free_commands(ptr as *mut crate::render::commands::RenderCommand, len as usize);
        }
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeFreeToc
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeFreeToc(
    _env: JNIEnv,
    _class: jobject,
    ptr: jlong,
    len: jint,
) {
    unsafe {
        if ptr != 0 {
            md_free_toc(ptr as *mut crate::bridge::types::TocEntry, len as usize);
        }
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeFreeDirtyRects
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeFreeDirtyRects(
    _env: JNIEnv,
    _class: jobject,
    ptr: jlong,
    len: jint,
) {
    unsafe {
        if ptr != 0 {
            md_free_dirty_rects(ptr as *mut i32, len as usize);
        }
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeRenderToCanvas
/// 将文档内容渲染到指定区域
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeRenderToCanvas(
    mut env: JNIEnv,
    _class: jobject,
    handle: jlong,
    _canvas_ptr: jlong,
    commands: jobject,
) -> jsize {
    if handle == 0 || commands.is_null() {
        return 0;
    }

    // 从 NativeRenderCommands 对象获取渲染目标和矩形
    // 目前我们使用 load_range 来获取命令并返回脏矩形数量
    unsafe {
        let doc = &mut *(handle as *mut crate::MarkdownDocument);

        // 获取解析器元数据以确定视口
        let parser = match std::ptr::replace(&mut doc.parser, None) {
            Some(p) => p,
            None => return 0,
        };
        doc.parser = Some(parser);

        let parser_ref = doc.parser.as_ref().unwrap();
        let metadata = parser_ref.metadata();

        // 渲染第一个可见范围（目前为 0 到 100 行）
        let mut out_commands: *const RenderCommand = std::ptr::null();
        let mut out_count: usize = 0;
        let mut out_dirty_rects: *const i32 = std::ptr::null();
        let mut out_dirty_count: usize = 0;

        let start_line = 0;
        let count = (100).min(metadata.total_lines);

        let result = md_document_load_range(
            handle as *mut crate::MarkdownDocument,
            start_line,
            count,
            &mut out_commands as *mut _ as *mut _,
            &mut out_count as *mut _ as *mut _,
            &mut out_dirty_rects as *mut _ as *mut _,
            &mut out_dirty_count as *mut _ as *mut _,
        );

        if result == 0 && out_dirty_count > 0 {
            // 返回脏矩形数量（数量 = 数组元素 / 4）
            return (out_dirty_count / 4) as jsize;
        }
    }
    0
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeLoadRange
/// 加载并渲染指定的行范围
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeLoadRange(
    mut env: JNIEnv,
    _class: jobject,
    handle: jlong,
    start_line: jint,
    count: jint,
    out_commands: jlongArray,
    out_dirty_rects: jlongArray,
    out_total_height: jintArray,
) -> jint {
    if handle == 0 {
        return -1;
    }

    unsafe {
        let mut commands_ptr: *const RenderCommand = std::ptr::null();
        let mut commands_count: usize = 0;
        let mut dirty_ptr: *const i32 = std::ptr::null();
        let mut dirty_count: usize = 0;

        let result = md_document_load_range(
            handle as *mut crate::MarkdownDocument,
            start_line as usize,
            count as usize,
            &mut commands_ptr as *mut _ as *mut _,
            &mut commands_count as *mut _ as *mut _,
            &mut dirty_ptr as *mut _ as *mut _,
            &mut dirty_count as *mut _ as *mut _,
        );

        if result == 0 {
            // 将输出值写入 Kotlin 数组
            // out_commands: [commands_ptr, commands_count]
            let commands_vals = [
                commands_ptr as i64,
                commands_count as i64,
            ];
            let commands_obj = JLongArray::from_raw(out_commands);
            env.set_long_array_region(&commands_obj, 0, &commands_vals).unwrap();
            std::mem::forget(commands_obj);

            // out_dirty_rects: [dirty_ptr, dirty_count]
            // dirty_count 是 i32 数组的元素数量（不是矩形数量）
            let dirty_vals = [
                dirty_ptr as i64,
                dirty_count as i64,
            ];
            let dirty_obj = JLongArray::from_raw(out_dirty_rects);
            env.set_long_array_region(&dirty_obj, 0, &dirty_vals).unwrap();
            std::mem::forget(dirty_obj);

            // out_total_height: [total_height]
            // 使用实际计算的文档高度，简化为行数 * 每行高度
            let total_height = (count * 20) as jint;
            let height_vals = [total_height];
            let height_obj = JIntArray::from_raw(out_total_height);
            env.set_int_array_region(&height_obj, 0, &height_vals).unwrap();
            std::mem::forget(height_obj);

            0
        } else {
            -1
        }
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeGetToc
/// 获取目录条目
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeGetToc(
    _env: JNIEnv,
    _class: jobject,
    handle: jlong,
    out_entries: jlong,
    out_count: jint,
) -> jint {
    if handle == 0 {
        return -1;
    }

    unsafe {
        let mut entries_ptr: *const TocEntry = std::ptr::null();
        let mut count: usize = 0;

        let result = md_document_get_toc(
            handle as *mut crate::MarkdownDocument,
            &mut entries_ptr as *mut _ as *mut _,
            &mut count as *mut _ as *mut _,
        );

        if result == 0 {
            // 将输出值写入提供的指针
            if out_entries != 0 && out_count != 0 {
                *(out_entries as *mut jlong) = entries_ptr as jlong;
                *(out_count as *mut jint) = count as jint;
            }
            0
        } else {
            -1
        }
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeSearch
/// TODO: 实现搜索功能
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeSearch(
    _env: JNIEnv,
    _class: jobject,
    _handle: jlong,
    _query: jstring,
) -> jlong {
    // TODO: 实现搜索功能
    0
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeReadBytes
/// 从 Rust 分配的内存读取字节数组
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeReadBytes(
    mut env: JNIEnv,
    _class: jobject,
    ptr: jlong,
    size: jint,
) -> jbyteArray {
    if ptr == 0 || size <= 0 {
        let arr = env.new_byte_array(0).unwrap();
        return arr.into_raw();
    }

    unsafe {
        let bytes = std::slice::from_raw_parts(ptr as *const u8, size as usize);
        let jbyte_array = env.new_byte_array(size).unwrap();
        // 将 &[u8] 转换为 &[i8]
        let bytes_i8: &[i8] = std::slice::from_raw_parts(bytes.as_ptr() as *const i8, bytes.len());
        env.set_byte_array_region(&jbyte_array, 0, bytes_i8).unwrap();
        jbyte_array.into_raw()
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeCreateDirectByteBuffer
/// 从 Rust 分配的内存创建 DirectByteBuffer（零拷贝）
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeCreateDirectByteBuffer(
    mut env: JNIEnv,
    _class: jobject,
    ptr: jlong,
    size: jint,
) -> jobject {
    if ptr == 0 || size <= 0 {
        return std::ptr::null_mut();
    }

    unsafe {
        let void_ptr = ptr as *mut std::ffi::c_void;
        env.new_direct_byte_buffer(void_ptr as *mut u8, size as usize)
            .map(|buf| buf.into_raw())
            .unwrap_or(std::ptr::null_mut())
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_00024NativeRenderCommands_nativeRelease
/// 用于 NativeRenderCommands
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_00024NativeRenderCommands_nativeRelease(
    _env: JNIEnv,
    _class: jobject,
    _ptr: jlong,
) {
    // TODO: 实现命令释放
}

// =============================================================================
// JNI 函数 - MarkdownEditorView
// =============================================================================

/// JNI: Java_com_editor_nomadmark_MarkdownEditorView_getCanvasNativePtr
/// 获取用于渲染的 Canvas 本地指针
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownEditorView_getCanvasNativePtr(
    _env: JNIEnv,
    _class: jobject,
    _canvas: jobject,
) -> jlong {
    // 返回一个有效的非空指针以允许应用启动
    // 在完整的实现中，这将提取实际的 Canvas 本地指针
    1
}
