package com.editor.nomadmark

/**
 * MarkdownCore - 到 Rust Core (libmarkdown_core.so) 的 FFI 桥接
 *
 * 此对象提供到共享 Rust 核心的 JNI 绑定。
 * 所有 Markdown 解析、布局和渲染逻辑必须委托给
 * 原生层 - Kotlin 中没有解析逻辑。
 *
 * 线程安全：所有原生函数都是线程安全的，可以从任何线程调用。
 * 文档句柄在每个实例上不是线程安全的。
 */
object MarkdownCore {

    init {
        System.loadLibrary("markdown_core")
    }

    // =========================================================================
    // 文档生命周期
    // =========================================================================

    /**
     * 从原始内容创建新的文档实例。
     *
     * 对于大文件（>50MB），请使用 [nativeCreateFromPath] 代替。
     *
     * @param content 原始 Markdown 内容
     * @return 原生句柄（不透明指针），失败时返回 0
     */
    external fun nativeCreate(content: String): Long

    /**
     * 从文件路径创建文档（对大文件使用 mmap）。
     *
     * 对于 >50MB 的文件的首选方法。使用流式解析器。
     *
     * @param path 绝对文件路径
     * @return 原生句柄，失败时返回 0
     */
    external fun nativeCreateFromPath(path: String): Long

    /**
     * 释放文档资源。
     *
     * 当不再需要文档时必须调用以防止内存泄漏。
     *
     * @param handle 来自 [nativeCreate] 的文档句柄
     */
    external fun nativeRelease(handle: Long)

    // =========================================================================
    // 渲染
    // =========================================================================

    /**
     * 将文档内容渲染到原生 Canvas。
     *
     * 直接渲染以获得最大性能。返回脏矩形数量
     * 用于部分刷新优化。
     *
     * @param handle 文档句柄
     * @param canvasPtr 原生 Canvas 指针（通过 Android NDK）
     * @param commands 渲染命令包
     * @return 脏矩形数量，如果未渲染任何内容则返回 0
     */
    external fun nativeRenderToCanvas(
        handle: Long,
        canvasPtr: Long,
        commands: NativeRenderCommands
    ): Int

    /**
     * 加载并渲染特定行范围（用于大文件支持）。
     *
     * 仅解析和渲染请求的范围。用于虚拟滚动
     * 和延迟加载。
     *
     * @param handle 文档句柄
     * @param startLine 起始行号（从 0 开始）
     * @param count 要加载的行数
     * @return 成功时返回 0，失败时返回 -1
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
     * 获取目录条目。
     *
     * @param handle 文档句柄
     * @param outEntries 接收 [条目指针，计数] 的输出数组
     * @return 成功时返回 0，失败时返回 -1
     */
    external fun nativeGetToc(
        handle: Long,
        outEntries: LongArray
    ): Int

    // =========================================================================
    // 目录
    // =========================================================================

    /**
     * 获取文档目录。
     *
     * @param handle 文档句柄
     * @return 目录条目数组（标题级别、标题、位置）
     */
    external fun nativeGetToc(handle: Long): Array<TocEntry>

    // =========================================================================
    // 搜索
    // =========================================================================

    /**
     * 在文档中进行全文搜索。
     *
     * @param handle 文档句柄
     * @param query 搜索查询字符串
     * @return 带有行/列位置的搜索结果数组
     */
    external fun nativeSearch(
        handle: Long,
        query: String
    ): Array<SearchResult>

    // =========================================================================
    // 历史记录（撤销/重做）
    // =========================================================================

    /**
     * 撤销上一次操作。
     *
     * @param handle 文档句柄
     * @return 如果撤销成功则返回 true，如果没有可撤销的操作则返回 false
     */
    external fun nativeUndo(handle: Long): Boolean

    /**
     * 重做上一次撤销的操作。
     *
     * @param handle 文档句柄
     * @return 如果重做成功则返回 true，如果没有可重做的操作则返回 false
     */
    external fun nativeRedo(handle: Long): Boolean

    // =========================================================================
    // 内存管理
    // =========================================================================

    /**
     * 释放特定行之前的已解析内容以释放内存。
     *
     * 在滚动大文档时使用以防止 OOM。
     *
     * @param handle 文档句柄
     * @param line 释放此行号之前的所有内容
     */
    external fun nativeReleaseBefore(handle: Long, line: Int)

    /**
     * 获取当前内存使用估算值。
     *
     * @return 内存使用量（字节）
     */
    external fun nativeGetMemoryUsage(handle: Long): Long

    /**
     * 释放由 Rust 分配的渲染命令。
     *
     * 在处理来自 nativeLoadRange 的命令后调用此方法。
     *
     * @param ptr RenderCommand 数组的指针
     * @param len 命令数量
     */
    external fun nativeFreeCommands(ptr: Long, len: Int)

    /**
     * 释放由 Rust 分配的目录条目。
     *
     * 在处理来自 nativeGetToc 或 nativeGetMetadata 的目录后调用此方法。
     *
     * @param ptr TocEntry 数组的指针
     * @param len 条目数量
     */
    external fun nativeFreeToc(ptr: Long, len: Int)

    /**
     * 释放由 Rust 分配的脏矩形。
     *
     * 在处理来自 nativeLoadRange 或 nativeGetDirtyRects 的脏矩形后调用此方法。
     *
     * @param ptr i32 数组的指针 [x, y, w, h, ...]
     * @param len 数组中的元素数量（不是矩形数量）
     */
    external fun nativeFreeDirtyRects(ptr: Long, len: Int)

    /**
     * 释放缓存的内存以达到目标阈值。
     *
     * 当 Android 通过 onTrimMemory 发出低内存信号时使用。
     *
     * @param handle 文档句柄
     * @param targetBytes 目标内存使用量（字节）
     * @return 实际释放的字节数
     */
    external fun nativeReleaseToTarget(handle: Long, targetBytes: Long): Long

    /**
     * 获取文档加载进度。
     *
     * 对于大文件，轮询此方法以显示加载指示器。
     *
     * @param handle 文档句柄
     * @return 进度（0.0 到 1.0，其中 1.0 = 完成）
     */
    external fun nativeGetProgress(handle: Long): Float

    /**
     * 获取文档文件大小（字节）。
     *
     * @param handle 文档句柄
     * @return 文件大小（字节）
     */
    external fun nativeGetFileSize(handle: Long): Long
}

// =============================================================================
// 原生数据结构（必须匹配 Rust 中的 #[repr(C)] 类型）
// =============================================================================

/**
 * 目录条目
 *
 * 对应 Rust：TocEntry
 */
data class TocEntry(
    val level: Int,          // 标题级别（1-6）
    val title: String,       // 标题文本
    val byteOffset: Int,    // 源文件中的位置
    val lineNumber: Int     // 行号（从 0 开始）
)

/**
 * 搜索结果
 *
 * 对应 Rust：SearchResult
 */
data class SearchResult(
    val line: Int,           // 行号（从 0 开始）
    val startColumn: Int,   // 起始列（从 0 开始）
    val endColumn: Int,     // 结束列（不包含）
    val context: String      // 用于预览的周围文本
)

/**
 * 原生渲染结果包
 *
 * 对应 Rust：NativeRenderResult
 */
data class NativeRenderResult(
    val commands: NativeRenderCommands,
    val dirtyRects: IntArray,  // [x, y, w, h, x, y, w, h, ...]
    val totalHeight: Int       // 文档总高度（像素）
)

/**
 * 渲染命令包
 *
 * 原生渲染命令数组的不透明包装器。
 * 实际命令存储在原生内存中以避免 JNI 开销。
 */
class NativeRenderCommands internal constructor(
    internal val nativePtr: Long
) {
    external fun nativeRelease()
}

/**
 * 用于 RAII 风格资源管理的文档句柄包装器。
 *
 * 关闭时自动释放原生资源。
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
