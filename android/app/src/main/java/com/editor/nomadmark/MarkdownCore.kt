package com.editor.nomadmark

/**
 * MarkdownCore - FFI Bridge to Rust Core (libmarkdown_core.so)
 *
 * This object provides JNI bindings to the shared Rust core.
 * All Markdown parsing, layout, and rendering logic MUST be delegated
 * to the native layer - no parsing logic in Kotlin.
 *
 * Thread Safety: All native functions are thread-safe and may be called
 * from any thread. Document handles are NOT thread-safe per instance.
 */
object MarkdownCore {

    init {
        System.loadLibrary("markdown_core")
    }

    // =========================================================================
    // Document Lifecycle
    // =========================================================================

    /**
     * Create a new document instance from raw content.
     *
     * For large files (>50MB), use [nativeCreateFromPath] instead.
     *
     * @param content Raw Markdown content
     * @return Native handle (opaque pointer), 0 if failed
     */
    external fun nativeCreate(content: String): Long

    /**
     * Create a document from file path (uses mmap for large files).
     *
     * Preferred method for files >50MB. Uses streaming parser.
     *
     * @param path Absolute file path
     * @return Native handle, 0 if failed
     */
    external fun nativeCreateFromPath(path: String): Long

    /**
     * Release document resources.
     *
     * MUST be called when document is no longer needed to prevent memory leaks.
     *
     * @param handle Document handle from [nativeCreate]
     */
    external fun nativeRelease(handle: Long)

    // =========================================================================
    // Rendering
    // =========================================================================

    /**
     * Render document content to native Canvas.
     *
     * Direct rendering for maximum performance. Returns number of
     * dirty rectangles for partial refresh optimization.
     *
     * @param handle Document handle
     * @param canvasPtr Native Canvas pointer (via Android NDK)
     * @param commands Render command bundle
     * @return Number of dirty rectangles, or 0 if nothing rendered
     */
    external fun nativeRenderToCanvas(
        handle: Long,
        canvasPtr: Long,
        commands: NativeRenderCommands
    ): Int

    /**
     * Load and render a specific line range (for large file support).
     *
     * Only parses and renders the requested range. Used for virtual scrolling
     * and lazy loading.
     *
     * @param handle Document handle
     * @param startLine Starting line number (0-based)
     * @param count Number of lines to load
     * @return 0 on success, -1 on failure
     */
    external fun nativeLoadRange(
        handle: Long,
        startLine: Int,
        count: Int,
        outCommands: LongArray,
        outDirtyRects: LongArray,
        outTotalHeight: IntArray
    ): Int

    /**
     * Get table of contents entries.
     *
     * @param handle Document handle
     * @param outEntries Output array to receive [entries pointer, count]
     * @return 0 on success, -1 on failure
     */
    external fun nativeGetToc(
        handle: Long,
        outEntries: LongArray
    ): Int

    // =========================================================================
    // Table of Contents
    // =========================================================================

    /**
     * Get document table of contents.
     *
     * @param handle Document handle
     * @return Array of TOC entries (heading level, title, position)
     */
    external fun nativeGetToc(handle: Long): Array<TocEntry>

    // =========================================================================
    // Search
    // =========================================================================

    /**
     * Full-text search within document.
     *
     * @param handle Document handle
     * @param query Search query string
     * @return Array of search results with line/column positions
     */
    external fun nativeSearch(
        handle: Long,
        query: String
    ): Array<SearchResult>

    // =========================================================================
    // History (Undo/Redo)
    // =========================================================================

    /**
     * Undo last operation.
     *
     * @param handle Document handle
     * @return true if undo was successful, false if nothing to undo
     */
    external fun nativeUndo(handle: Long): Boolean

    /**
     * Redo last undone operation.
     *
     * @param handle Document handle
     * @return true if redo was successful, false if nothing to redo
     */
    external fun nativeRedo(handle: Long): Boolean

    // =========================================================================
    // Memory Management
    // =========================================================================

    /**
     * Release parsed content before a specific line to free memory.
     *
     * Use this when scrolling large documents to prevent OOM.
     *
     * @param handle Document handle
     * @param line Release all content before this line number
     */
    external fun nativeReleaseBefore(handle: Long, line: Int)

    /**
     * Get current memory usage estimate.
     *
     * @return Memory usage in bytes
     */
    external fun nativeGetMemoryUsage(handle: Long): Long

    /**
     * Free render commands allocated by Rust.
     *
     * Call this after processing commands from nativeLoadRange.
     *
     * @param ptr Pointer to RenderCommand array
     * @param len Number of commands
     */
    external fun nativeFreeCommands(ptr: Long, len: Int)

    /**
     * Free TOC entries allocated by Rust.
     *
     * Call this after processing TOC from nativeGetToc or nativeGetMetadata.
     *
     * @param ptr Pointer to TocEntry array
     * @param len Number of entries
     */
    external fun nativeFreeToc(ptr: Long, len: Int)

    /**
     * Free dirty rects allocated by Rust.
     *
     * Call this after processing dirty rects from nativeLoadRange or nativeGetDirtyRects.
     *
     * @param ptr Pointer to i32 array [x, y, w, h, ...]
     * @param len Number of elements in the array (NOT number of rects)
     */
    external fun nativeFreeDirtyRects(ptr: Long, len: Int)

    /**
     * Release cached memory to reach a target threshold.
     *
     * Use when Android signals low memory via onTrimMemory.
     *
     * @param handle Document handle
     * @param targetBytes Target memory usage in bytes
     * @return Number of bytes actually released
     */
    external fun nativeReleaseToTarget(handle: Long, targetBytes: Long): Long

    /**
     * Get document loading progress.
     *
     * For large files, poll this to show loading indicator.
     *
     * @param handle Document handle
     * @return Progress (0.0 to 1.0, where 1.0 = complete)
     */
    external fun nativeGetProgress(handle: Long): Float

    /**
     * Get document file size in bytes.
     *
     * @param handle Document handle
     * @return File size in bytes
     */
    external fun nativeGetFileSize(handle: Long): Long
}

// =============================================================================
// Native Data Structures (must match #[repr(C)] types in Rust)
// =============================================================================

/**
 * Table of Contents entry
 *
 * Corresponds to Rust: TocEntry
 */
data class TocEntry(
    val level: Int,          // Heading level (1-6)
    val title: String,       // Heading text
    val byteOffset: Int,    // Position in source file
    val lineNumber: Int     // Line number (0-based)
)

/**
 * Search result
 *
 * Corresponds to Rust: SearchResult
 */
data class SearchResult(
    val line: Int,           // Line number (0-based)
    val startColumn: Int,   // Start column (0-based)
    val endColumn: Int,     // End column (exclusive)
    val context: String      // Surrounding text for preview
)

/**
 * Native render result bundle
 *
 * Corresponds to Rust: NativeRenderResult
 */
data class NativeRenderResult(
    val commands: NativeRenderCommands,
    val dirtyRects: IntArray,  // [x, y, w, h, x, y, w, h, ...]
    val totalHeight: Int       // Total document height in pixels
)

/**
 * Bundle of render commands
 *
 * Opaque wrapper for native render command array.
 * The actual commands are stored in native memory to avoid JNI overhead.
 */
class NativeRenderCommands internal constructor(
    internal val nativePtr: Long
) {
    external fun nativeRelease()
}

/**
 * Document handle wrapper for RAII-style resource management.
 *
 * Automatically releases native resources when closed.
 */
class DocumentHandle(
    private val handle: Long
) : AutoCloseable {

    init {
        require(handle != 0L) { "Invalid document handle" }
    }

    fun get(): Long = handle

    override fun close() {
        if (handle != 0L) {
            MarkdownCore.nativeRelease(handle)
        }
    }

    protected fun finalize() {
        close()
    }
}
