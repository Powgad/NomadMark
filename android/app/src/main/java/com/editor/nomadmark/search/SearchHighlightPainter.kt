package com.editor.nomadmark.search

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.editor.nomadmark.SearchResult
import com.editor.nomadmark.EinkRefreshController

/**
 * SearchHighlightPainter - 搜索高亮绘制器
 *
 * 基于《架构设计书 v2.0》第 4.2 节 E-ink 刷新优化
 *
 * 功能:
 * - 使用 EPD_A2 模式绘制搜索结果高亮
 * - 支持当前选中高亮和普通高亮两种样式
 * - 计算高亮区域的脏矩形
 * - 禁止在 onDraw 中分配内存
 *
 * EPD_A2 模式特点:
 * - 快速刷新模式 (约 100-200ms)
 * - 适合小范围高亮更新
 * - 仅支持黑白两色
 */
class SearchHighlightPainter {

    // =========================================================================
    // Paint 对象池 (避免在绘制时分配)
    // =========================================================================

    /**
     * 普通高亮 Paint (灰色半透明)
     */
    private val normalHighlightPaint = Paint().apply {
        style = Paint.Style.FILL
        color = 0x40000000.toInt() // 25% 黑色半透明
        isAntiAlias = false // E-ink 不需要抗锯齿
    }

    /**
     * 选中高亮 Paint (深色)
     */
    private val selectedHighlightPaint = Paint().apply {
        style = Paint.Style.FILL
        color = 0x80000000.toInt() // 50% 黑色半透明
        isAntiAlias = false
    }

    /**
     * 边框 Paint (用于选中项)
     */
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = 0xFF000000.toInt()
        strokeWidth = 2f
        isAntiAlias = false
    }

    /**
     * 下划线 Paint (用于替代高亮背景)
     */
    private val underlinePaint = Paint().apply {
        style = Paint.Style.STROKE
        color = 0xFF000000.toInt()
        strokeWidth = 3f
        isAntiAlias = false
    }

    // =========================================================================
    // 缓存的 Rect 对象 (避免在绘制时分配)
    // =========================================================================

    private val cachedRect = Rect()
    private val cachedRectF = RectF()

    // =========================================================================
    // 高亮样式配置
    // =========================================================================

    /**
     * 高亮模式
     */
    enum class HighlightMode {
        /** 背景高亮 */
        Background,

        /** 下划线高亮 (E-ink 推荐模式) */
        Underline,

        /** 边框高亮 */
        Border
    }

    private var highlightMode = HighlightMode.Underline

    /**
     * 设置高亮模式
     */
    fun setHighlightMode(mode: HighlightMode) {
        this.highlightMode = mode
    }

    // =========================================================================
    // 绘制方法
    // =========================================================================

    /**
     * 绘制普通高亮
     *
     * @param canvas 目标 Canvas
     * @param rect 高亮区域
     */
    fun drawHighlight(canvas: Canvas, rect: Rect) {
        when (highlightMode) {
            HighlightMode.Background -> {
                canvas.drawRect(rect, normalHighlightPaint)
            }
            HighlightMode.Underline -> {
                drawUnderline(canvas, rect)
            }
            HighlightMode.Border -> {
                canvas.drawRect(rect, borderPaint)
            }
        }
    }

    /**
     * 绘制选中高亮
     *
     * @param canvas 目标 Canvas
     * @param rect 高亮区域
     */
    fun drawSelectedHighlight(canvas: Canvas, rect: Rect) {
        when (highlightMode) {
            HighlightMode.Background -> {
                canvas.drawRect(rect, selectedHighlightPaint)
                canvas.drawRect(rect, borderPaint)
            }
            HighlightMode.Underline -> {
                drawUnderline(canvas, rect, isSelected = true)
            }
            HighlightMode.Border -> {
                canvas.drawRect(rect, borderPaint)
                // 双线边框表示选中
                drawDoubleBorder(canvas, rect)
            }
        }
    }

    /**
     * 绘制下划线 (E-ink 推荐模式)
     *
     * 下划线比背景高亮更省电, 且不会影响文本可读性
     */
    private fun drawUnderline(canvas: Canvas, rect: Rect, isSelected: Boolean = false) {
        val underlineY = rect.bottom - 2f
        val underlineStart = rect.left.toFloat()
        val underlineEnd = rect.right.toFloat()

        if (isSelected) {
            // 双下划线表示选中
            canvas.drawLine(underlineStart, underlineY, underlineEnd, underlineY, underlinePaint)
            canvas.drawLine(underlineStart, underlineY - 4f, underlineEnd, underlineY - 4f, underlinePaint)
        } else {
            // 单下划线
            canvas.drawLine(underlineStart, underlineY, underlineEnd, underlineY, underlinePaint)
        }
    }

    /**
     * 绘制双线边框
     */
    private fun drawDoubleBorder(canvas: Canvas, rect: Rect) {
        val inset = 2
        cachedRect.set(
            rect.left + inset,
            rect.top + inset,
            rect.right - inset,
            rect.bottom - inset
        )
        canvas.drawRect(cachedRect, borderPaint)
    }

    // =========================================================================
    // 批量高亮绘制
    // =========================================================================

    /**
     * 绘制所有搜索结果高亮
     *
     * @param canvas 目标 Canvas
     * @param results 搜索结果列表
     * @param selectedIndex 选中的结果索引
     * @param rectCalculator 计算结果区域的函数
     */
    fun drawAllHighlights(
        canvas: Canvas,
        results: List<SearchResult>,
        selectedIndex: Int,
        rectCalculator: (SearchResult) -> Rect
    ) {
        for ((index, result) in results.withIndex()) {
            val rect = rectCalculator(result)
            if (rect.isEmpty) continue

            if (index == selectedIndex) {
                drawSelectedHighlight(canvas, rect)
            } else {
                drawHighlight(canvas, rect)
            }
        }
    }

    // =========================================================================
    // 脏矩形计算
    // =========================================================================

    /**
     * 计算高亮的脏矩形
     *
     * 用于 E-ink 局部刷新
     *
     * @param rect 原始高亮区域
     * @param mode 高亮模式
     * @return 需要刷新的区域
     */
    fun calculateDirtyRect(rect: Rect, mode: HighlightMode = this.highlightMode): Rect {
        cachedRect.set(rect)

        // 根据高亮模式扩展区域
        when (mode) {
            HighlightMode.Background -> {
                // 背景高亮需要扩展 2px 避免边缘锯齿
                cachedRect.inset(-2, -2)
            }
            HighlightMode.Underline -> {
                // 下划线只需要扩展底部
                cachedRect.top = cachedRect.bottom - 8
            }
            HighlightMode.Border -> {
                // 边框需要扩展 1px
                cachedRect.inset(-1, -1)
            }
        }

        return cachedRect
    }

    /**
     * 计算多个高亮的合并脏矩形
     *
     * @param rects 高亮区域列表
     * @return 合并后的脏矩形
     */
    fun calculateMergedDirtyRect(rects: List<Rect>): Rect {
        if (rects.isEmpty()) return Rect()

        var result = Rect(rects[0])

        for (i in 1 until rects.size) {
            result.union(rects[i])
        }

        return result
    }

    // =========================================================================
    // EPD_A2 刷新支持
    // =========================================================================

    /**
     * 获取 EPD_A2 刷新所需的脏矩形列表
     *
     * EPD_A2 模式限制:
     * - 每次最多刷新 16 个区域
     * - 单个区域不超过 1/4 屏幕面积
     *
     * @param results 搜索结果列表
     * @param rectCalculator 计算结果区域的函数
     * @return 脏矩形列表 (最多 16 个)
     */
    fun getA2RefreshRects(
        results: List<SearchResult>,
        rectCalculator: (SearchResult) -> Rect
    ): List<Rect> {
        val dirtyRects = mutableListOf<Rect>()

        for (result in results) {
            val rect = rectCalculator(result)
            if (rect.isEmpty) continue

            val dirtyRect = calculateDirtyRect(rect)
            dirtyRects.add(dirtyRect)

            // EPD_A2 最多 16 个区域
            if (dirtyRects.size >= 16) break
        }

        return dirtyRects
    }

    // =========================================================================
    // 与 EinkRefreshController 集成
    // =========================================================================

    /**
     * 请求 EPD_A2 刷新
     *
     * @param refreshController E-ink 刷新控制器
     * @param results 搜索结果列表
     * @param rectCalculator 计算结果区域的函数
     */
    fun requestA2Refresh(
        refreshController: EinkRefreshController,
        results: List<SearchResult>,
        rectCalculator: (SearchResult) -> Rect
    ) {
        val dirtyRects = getA2RefreshRects(results, rectCalculator)

        for (rect in dirtyRects) {
            refreshController.requestA2Refresh(rect)
        }
    }
}

// =============================================================================
// SearchResultRectCalculator - 搜索结果区域计算器
// =============================================================================

/**
 * 搜索结果区域计算器
 *
 * 负责将 SearchResult 转换为屏幕上的高亮区域
 */
class SearchResultRectCalculator {

    private val lineHeight: Int
    private val charWidth: Int
    private val documentTop: Int
    private val documentLeft: Int

    constructor(
        lineHeight: Int = 40,
        charWidth: Int = 20,
        documentTop: Int = 0,
        documentLeft: Int = 0
    ) {
        this.lineHeight = lineHeight
        this.charWidth = charWidth
        this.documentTop = documentTop
        this.documentLeft = documentLeft
    }

    /**
     * 计算搜索结果的屏幕区域
     *
     * @param result 搜索结果
     * @return 高亮区域
     */
    fun calculateRect(result: SearchResult): Rect {
        val left = documentLeft + result.startColumn * charWidth
        val top = documentTop + result.line * lineHeight
        val right = documentLeft + result.endColumn * charWidth
        val bottom = top + lineHeight

        return Rect(left, top, right, bottom)
    }

    /**
     * 批量计算搜索结果的屏幕区域
     *
     * @param results 搜索结果列表
     * @return 高亮区域列表
     */
    fun calculateRects(results: List<SearchResult>): List<Rect> {
        return results.map { calculateRect(it) }
    }

    /**
     * 更新布局参数
     */
    fun updateLayout(
        lineHeight: Int = this.lineHeight,
        charWidth: Int = this.charWidth,
        documentTop: Int = this.documentTop,
        documentLeft: Int = this.documentLeft
    ) {
        // 在实际实现中更新参数
    }
}
