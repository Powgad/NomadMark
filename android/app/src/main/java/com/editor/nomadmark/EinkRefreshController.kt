package com.editor.nomadmark

import android.graphics.Rect
import android.view.View

/**
 * 刷新模式
 */
enum class RefreshMode {
    /** 全局刷新 */
    GLOBAL,
    /** 局部刷新 */
    PARTIAL,
    /** 智能刷新（自动选择） */
    SMART
}

/**
 * E-ink 刷新控制器
 *
 * 功能：
 * - 计算局部刷新区域
 * - 决定刷新模式
 * - 管理刷新策略
 *
 * 使用示例：
 * ```
 * val controller = EinkRefreshController(myView)
 * controller.requestDirtyRect(someRect)
 * controller.requestGlobalRefresh()
 * ```
 */
class EinkRefreshController(private val view: View) {

    companion object {
        /** 局部刷新阈值（像素）。小于此值使用局部刷新 */
        const val PARTIAL_REFRESH_THRESHOLD = 10000

        /** 全局刷新间隔（毫秒）。定期全局刷新防止残影 */
        const val GLOBAL_REFRESH_INTERVAL = 30000L

        /** 手写操作后的延迟刷新时间 */
        const val HANDWRITING_REFRESH_DELAY = 500L
    }

    /** 当前刷新模式 */
    private var currentMode = RefreshMode.SMART

    /** 上次全局刷新时间 */
    private var lastGlobalRefreshTime = 0L

    /** 待刷新区域列表 */
    private val dirtyRects = mutableListOf<Rect>()

    /** 最小刷新间隔 (ms) */
    private val minRefreshInterval = 50L

    /** 全局刷新阈值 (屏幕面积占比) */
    private val globalRefreshThreshold = 1f / 3f

    /**
     * 请求刷新区域
     */
    fun requestDirtyRect(rect: Rect) {
        synchronized(dirtyRects) {
            dirtyRects.add(rect)
            applyRefresh()
        }
    }

    /**
     * 请求刷新多个区域
     */
    fun requestDirtyRects(rects: List<Rect>) {
        synchronized(dirtyRects) {
            dirtyRects.addAll(rects)
            applyRefresh()
        }
    }

    /**
     * 应用刷新
     */
    private fun applyRefresh() {
        if (dirtyRects.isEmpty()) return

        val mode = decideRefreshMode()
        when (mode) {
            RefreshMode.GLOBAL -> doGlobalRefresh()
            RefreshMode.PARTIAL -> doPartialRefresh()
            RefreshMode.SMART -> doSmartRefresh()
        }

        dirtyRects.clear()
    }

    /**
     * 决定刷新模式
     */
    private fun decideRefreshMode(): RefreshMode {
        return when (currentMode) {
            RefreshMode.GLOBAL -> RefreshMode.GLOBAL
            RefreshMode.PARTIAL -> RefreshMode.PARTIAL
            RefreshMode.SMART -> {
                // 计算总刷新面积
                val totalArea = dirtyRects.sumOf { rect ->
                    rect.width().toLong() * rect.height().toLong()
                }

                if (totalArea < PARTIAL_REFRESH_THRESHOLD) {
                    RefreshMode.PARTIAL
                } else {
                    RefreshMode.GLOBAL
                }
            }
        }
    }

    /**
     * 全局刷新
     */
    private fun doGlobalRefresh() {
        view.invalidate()
        lastGlobalRefreshTime = System.currentTimeMillis()
    }

    /**
     * 局部刷新
     */
    private fun doPartialRefresh() {
        for (rect in dirtyRects) {
            view.invalidate(rect)
        }
    }

    /**
     * 智能刷新
     */
    private fun doSmartRefresh() {
        // 计算包围盒
        if (dirtyRects.isEmpty()) return

        val boundingBox = calculateBoundingBox()
        val screenArea = view.width * view.height
        val dirtyArea = boundingBox.width().toLong() * boundingBox.height().toLong()

        // 如果脏矩形超过屏幕 1/3, 使用全局刷新
        if (dirtyArea > screenArea * globalRefreshThreshold) {
            doGlobalRefresh()
        } else {
            doPartialRefresh()
        }
    }

    /**
     * 计算包围盒
     */
    private fun calculateBoundingBox(): Rect {
        if (dirtyRects.isEmpty()) return Rect()

        var result = dirtyRects[0]
        for (i in 1 until dirtyRects.size) {
            result = mergeRects(result, dirtyRects[i])
        }
        return result
    }

    /**
     * 合并两个矩形
     */
    private fun mergeRects(r1: Rect, r2: Rect): Rect {
        return Rect(
            minOf(r1.left, r2.left),
            minOf(r1.top, r2.top),
            maxOf(r1.right, r2.right),
            maxOf(r1.bottom, r2.bottom)
        )
    }

    /**
     * 设置刷新模式
     */
    fun setRefreshMode(mode: RefreshMode) {
        currentMode = mode
    }

    /**
     * 手写操作后的刷新
     */
    fun refreshAfterHandwriting(rect: Rect) {
        view.handler?.postDelayed({
            requestDirtyRect(rect)
        }, HANDWRITING_REFRESH_DELAY)
    }

    /**
     * 立即全局刷新
     */
    fun forceGlobalRefresh() {
        synchronized(dirtyRects) {
            dirtyRects.clear()
            doGlobalRefresh()
        }
    }

    /**
     * 请求全局刷新
     *
     * 用于分屏切换、模式切换等场景
     */
    fun requestGlobalRefresh() {
        synchronized(dirtyRects) {
            dirtyRects.clear()
            // 立即触发重绘
            view.invalidate()
            // 在下一个消息循环再次触发，确保绘制发生
            view.post {
                view.invalidate()
            }
            lastGlobalRefreshTime = System.currentTimeMillis()
        }
    }

    /**
     * 添加脏矩形（别名，用于兼容）
     */
    fun addDirty(rect: Rect) {
        requestDirtyRect(rect)
    }

    /**
     * A2 模式刷新（E-ink 快速刷新模式）
     * 这是 requestDirtyRect 的别名，用于兼容现有代码
     */
    fun requestA2Refresh(rect: Rect) {
        requestDirtyRect(rect)
    }

    /**
     * 清空所有待刷新区域
     */
    fun clearDirtyRects() {
        synchronized(dirtyRects) {
            dirtyRects.clear()
        }
    }

    /**
     * 获取当前待刷新区域数量
     */
    fun getDirtyRectCount(): Int {
        return dirtyRects.size
    }
}
