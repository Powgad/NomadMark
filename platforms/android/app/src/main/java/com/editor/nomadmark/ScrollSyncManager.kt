package com.editor.nomadmark

import android.os.Handler
import android.os.Looper
import android.widget.ScrollView

/**
 * 滚动同步管理器
 *
 * 功能：
 * - 编辑区滚动时同步预览区
 * - 预览区滚动时同步编辑区
 * - 计算滚动位置对应关系
 *
 * 性能优化：
 * - 节流机制：避免频繁的同步调用
 * - 高度缓存：减少视图属性访问
 * - 异步执行：避免阻塞主线程
 */
class ScrollSyncManager(
    private val editorScrollView: ScrollView,
    private val previewScrollView: ScrollView
) {

    companion object {
        /** 节流间隔（毫秒）- 约 60fps */
        private const val THROTTLE_MS = 16L

        /** 高度缓存无效值 */
        private const val INVALID_HEIGHT = -1
    }

    private var isSyncing = false
    private var syncEnabled = true

    // =========================================================================
    // 性能优化：节流与缓存
    // =========================================================================

    /** 主线程 Handler（用于节流） */
    private val handler = Handler(Looper.getMainLooper())

    /** 节流 Runnable */
    private var throttleRunnable: Runnable? = null

    /** 缓存的高度值 */
    private var cachedEditorHeight = INVALID_HEIGHT
    private var cachedPreviewHeight = INVALID_HEIGHT

    /** 上次同步的滚动位置（用于跳过微小滚动） */
    private var lastEditorScrollY = INVALID_HEIGHT
    private var lastPreviewScrollY = INVALID_HEIGHT

    /** 最小滚动距离（像素）- 小于此值不触发同步 */
    private val MIN_SCROLL_DISTANCE = 5

    /**
     * 启用滚动同步
     */
    fun enable() {
        syncEnabled = true
        setupListeners()
    }

    /**
     * 禁用滚动同步
     */
    fun disable() {
        syncEnabled = false
        clearThrottle()
    }

    /**
     * 设置监听器
     * 使用 ObservableScrollView 的 onScrollChanged 回调，确保捕获所有滚动事件
     */
    private fun setupListeners() {
        // 尝试将 ScrollView 转换为 ObservableScrollView
        if (editorScrollView is ObservableScrollView) {
            editorScrollView.onScrollChangedCallback = { scrollX, scrollY, oldScrollX, oldScrollY ->
                onEditorScrolled(scrollY)
            }
        } else {
            // 降级到 viewTreeObserver (可能无法捕获鼠标滚轮事件)
            editorScrollView.viewTreeObserver?.addOnScrollChangedListener {
                onEditorScrolled(editorScrollView.scrollY)
            }
        }

        if (previewScrollView is ObservableScrollView) {
            previewScrollView.onScrollChangedCallback = { scrollX, scrollY, oldScrollX, oldScrollY ->
                onPreviewScrolled(scrollY)
            }
        } else {
            // 降级到 viewTreeObserver
            previewScrollView.viewTreeObserver?.addOnScrollChangedListener {
                onPreviewScrolled(previewScrollView.scrollY)
            }
        }
    }

    /**
     * 编辑区滚动事件处理
     */
    private fun onEditorScrolled(scrollY: Int) {
        if (!syncEnabled || isSyncing) return

        // 跳过微小滚动
        if (lastEditorScrollY != INVALID_HEIGHT &&
            Math.abs(scrollY - lastEditorScrollY) < MIN_SCROLL_DISTANCE) {
            return
        }
        lastEditorScrollY = scrollY

        // 节流：取消之前的同步请求，安排新的
        clearThrottle()
        throttleRunnable = Runnable {
            syncEditorToPreview()
        }
        handler.postDelayed(throttleRunnable!!, THROTTLE_MS)
    }

    /**
     * 预览区滚动事件处理
     */
    private fun onPreviewScrolled(scrollY: Int) {
        if (!syncEnabled || isSyncing) return

        // 跳过微小滚动
        if (lastPreviewScrollY != INVALID_HEIGHT &&
            Math.abs(scrollY - lastPreviewScrollY) < MIN_SCROLL_DISTANCE) {
            return
        }
        lastPreviewScrollY = scrollY

        // 节流：取消之前的同步请求，安排新的
        clearThrottle()
        throttleRunnable = Runnable {
            syncPreviewToEditor()
        }
        handler.postDelayed(throttleRunnable!!, THROTTLE_MS)
    }

    /**
     * 清除节流任务
     */
    private fun clearThrottle() {
        throttleRunnable?.let {
            handler.removeCallbacks(it)
            throttleRunnable = null
        }
    }

    /**
     * 编辑区滚动同步到预览区
     *
     * 使用 scrollTo() 而非 smoothScrollTo() 以避免动画造成的卡顿。
     * 滚动同步需要即时响应，动画会互相干扰导致流畅性问题。
     */
    private fun syncEditorToPreview() {
        if (isSyncing) return

        val editorScrollY = editorScrollView.scrollY
        val previewScrollY = calculatePreviewPosition(editorScrollY)

        isSyncing = true
        previewScrollView.scrollTo(0, previewScrollY)
        isSyncing = false
    }

    /**
     * 预览区滚动同步到编辑区
     *
     * 使用 scrollTo() 而非 smoothScrollTo() 以避免动画造成的卡顿。
     * 滚动同步需要即时响应，动画会互相干扰导致流畅性问题。
     */
    private fun syncPreviewToEditor() {
        if (isSyncing) return

        val previewScrollY = previewScrollView.scrollY
        val editorScrollY = calculateEditorPosition(previewScrollY)

        isSyncing = true
        editorScrollView.scrollTo(0, editorScrollY)
        isSyncing = false
    }

    /**
     * 计算编辑区滚动位置对应的预览区位置
     *
     * 使用缓存的高度值以提高性能。
     */
    private fun calculatePreviewPosition(editorScrollY: Int): Int {
        val editorTotalHeight = getCachedEditorHeight()
        val previewTotalHeight = getCachedPreviewHeight()

        if (editorTotalHeight > 0 && previewTotalHeight > 0) {
            return (editorScrollY.toFloat() / editorTotalHeight * previewTotalHeight).toInt()
        }

        return editorScrollY
    }

    /**
     * 计算预览区滚动位置对应的编辑区位置
     *
     * 使用缓存的高度值以提高性能。
     */
    private fun calculateEditorPosition(previewScrollY: Int): Int {
        val editorTotalHeight = getCachedEditorHeight()
        val previewTotalHeight = getCachedPreviewHeight()

        if (previewTotalHeight > 0 && editorTotalHeight > 0) {
            return (previewScrollY.toFloat() / previewTotalHeight * editorTotalHeight).toInt()
        }

        return previewScrollY
    }

    /**
     * 获取缓存的编辑区高度
     */
    private fun getCachedEditorHeight(): Int {
        if (cachedEditorHeight == INVALID_HEIGHT) {
            cachedEditorHeight = editorScrollView.getChildAt(0)?.height ?: 0
        }
        return cachedEditorHeight
    }

    /**
     * 获取缓存的预览区高度
     */
    private fun getCachedPreviewHeight(): Int {
        if (cachedPreviewHeight == INVALID_HEIGHT) {
            cachedPreviewHeight = previewScrollView.getChildAt(0)?.height ?: 0
        }
        return cachedPreviewHeight
    }

    /**
     * 清除高度缓存
     *
     * 应在内容变化时调用（如文本编辑、格式化等）
     */
    fun clearHeightCache() {
        cachedEditorHeight = INVALID_HEIGHT
        cachedPreviewHeight = INVALID_HEIGHT
    }

    /**
     * 检查是否已启用同步
     */
    fun isEnabled(): Boolean = syncEnabled

    /**
     * 清理资源
     *
     * 应在 Activity/Fragment 销毁时调用
     */
    fun cleanup() {
        clearThrottle()
        handler.removeCallbacksAndMessages(null)
    }
}
