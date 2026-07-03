// =============================================================================
// JNI Bridge for Android
// =============================================================================
//
// This module provides JNI-style wrapper functions that connect Kotlin/Java
// native method calls to the underlying C FFI functions.
//
// JNI naming convention: Java_<package_name>_<class_name>_<method_name>
// =============================================================================

use std::ffi::CStr;

// Import types needed for JNI functions
use crate::render::commands::RenderCommand;
use crate::bridge::types::TocEntry;

// Always include JNI when this module is compiled
#[cfg(feature = "jni")]
use jni::sys::{jfloat, jint, jlong, jstring, jobject, jsize};
#[cfg(feature = "jni")]
use jni::{JNIEnv, objects::JString};

// Simple test function to verify JNI symbols are exported
#[no_mangle]
pub extern "C" fn test_jni_export() -> i32 {
    42
}

// Import C FFI functions from parent crate
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
// JNI Functions - MarkdownCore
// =============================================================================

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeCreate
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

        let jstr = JString::from_raw(content);
        let s = env.get_string(&jstr);
        if s.is_err() {
            return 0;
        }
        let j_str = s.unwrap();

        let c_str = CStr::from_ptr(j_str.as_ptr());
        let bytes = c_str.to_bytes();
        let handle = md_document_create(bytes.as_ptr() as *const i8, bytes.len());

        handle as jlong
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeCreateFromPath
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

        let jstr = JString::from_raw(path);
        let s = env.get_string(&jstr);
        if s.is_err() {
            return 0;
        }
        let j_str = s.unwrap();

        let c_str = CStr::from_ptr(j_str.as_ptr());
        let handle = md_document_create_from_path(c_str.as_ptr() as *const i8);

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
/// Renders document content to the given area
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

    // Get render target and rect from NativeRenderCommands object
    // For now, we'll use load_range to get commands and return dirty rects count
    unsafe {
        let doc = &mut *(handle as *mut crate::MarkdownDocument);

        // Get parser metadata to determine viewport
        let parser = match std::ptr::replace(&mut doc.parser, None) {
            Some(p) => p,
            None => return 0,
        };
        doc.parser = Some(parser);

        let parser_ref = doc.parser.as_ref().unwrap();
        let metadata = parser_ref.metadata();

        // Render first visible range (0 to 100 lines for now)
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
            // Return number of dirty rectangles (count is array elements / 4)
            return (out_dirty_count / 4) as jsize;
        }
    }
    0
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeLoadRange
/// Load and render a specific line range
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeLoadRange(
    _env: JNIEnv,
    _class: jobject,
    handle: jlong,
    start_line: jint,
    count: jint,
    out_commands: jlong,
    out_count: jint,
    out_dirty_rects: jlong,
    out_dirty_count: jint,
    out_total_height: jlong,
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
            // Write output values to the provided pointers (LongArray in Kotlin)
            if out_commands != 0 && out_count != 0 {
                *(out_commands as *mut jlong) = commands_ptr as jlong;
                *(out_count as *mut jint) = commands_count as jint;
            }
            if out_dirty_rects != 0 && out_dirty_count != 0 {
                *(out_dirty_rects as *mut jlong) = dirty_ptr as jlong;
                *(out_dirty_count as *mut jint) = dirty_count as jint;
            }
            // Estimate total height (20px per line for now)
            if out_total_height != 0 {
                *(out_total_height as *mut jint) = (count * 20) as jint;
            }
            0
        } else {
            -1
        }
    }
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_nativeGetToc
/// Get table of contents entries
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
            // Write output values to the provided pointers
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
/// TODO: Implement search
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeSearch(
    _env: JNIEnv,
    _class: jobject,
    _handle: jlong,
    _query: jstring,
) -> jlong {
    // TODO: Implement search
    0
}

/// JNI: Java_com_editor_nomadmark_MarkdownCore_00024NativeRenderCommands_nativeRelease
/// For NativeRenderCommands
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_00024NativeRenderCommands_nativeRelease(
    _env: JNIEnv,
    _class: jobject,
    _ptr: jlong,
) {
    // TODO: Implement commands release
}

// =============================================================================
// JNI Functions - MarkdownEditorView
// =============================================================================

/// JNI: Java_com_editor_nomadmark_MarkdownEditorView_getCanvasNativePtr
/// Get native pointer to Canvas for rendering
#[cfg(feature = "jni")]
#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownEditorView_getCanvasNativePtr(
    _env: JNIEnv,
    _class: jobject,
    _canvas: jobject,
) -> jlong {
    // Return a valid non-null pointer to allow app to start
    // In a full implementation, this would extract the actual Canvas native pointer
    1
}

