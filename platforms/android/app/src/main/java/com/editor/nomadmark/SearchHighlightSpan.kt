package com.editor.nomadmark

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ReplacementSpan

/**
 * 搜索高亮边框 Span
 *
 * 用于在 EditText 中为搜索匹配项绘制边框（只框选匹配的文本内容，不是整行）
 *
 * @param borderColor 边框颜色
 * @param borderWidth 边框宽度
 * @param isCurrent 是否为当前选中的匹配项（选中项使用不同的边框样式）
 */
class SearchHighlightSpan(
    private val borderColor: Int = Color.GRAY,
    private val borderWidth: Float = 3f,
    private val isCurrent: Boolean = false
) : ReplacementSpan() {

    companion object {
        /** 单个搜索/默认边框颜色（灰色 - 墨水屏适配） */
        const val DEFAULT_BORDER_COLOR = Color.GRAY

        /** 当前选中项的边框颜色（深灰色 - 墨水屏适配） */
        const val CURRENT_BORDER_COLOR = Color.DKGRAY

        /** 全部搜索模式下的边框颜色（灰色 - 墨水屏适配） */
        const val ALL_SEARCH_BORDER_COLOR = Color.GRAY
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        // 返回原始文本的宽度，不替换任何内容
        return paint.measureText(text, start, end).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        // 首先绘制原始文本
        canvas.drawText(text, start, end, x, y.toFloat(), paint)

        // 然后在文本周围绘制边框
        drawBorder(canvas, text, start, end, x, top, y, bottom, paint)
    }

    private fun drawBorder(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        @Suppress("UNUSED_PARAMETER") _top: Int,
        y: Int,
        @Suppress("UNUSED_PARAMETER") _bottom: Int,
        paint: Paint
    ) {
        // 保存原始 paint 状态
        val originalColor = paint.color
        val originalStyle = paint.style
        val originalStrokeWidth = paint.strokeWidth

        try {
            // 计算文本的实际宽度
            val textWidth = paint.measureText(text, start, end)

            // 计算文本的实际高度（使用字体的 ascent 和 descent）
            val fontMetrics = paint.fontMetricsInt
            val textTop = y + fontMetrics.top
            val textBottom = y + fontMetrics.bottom

            // 创建边框矩形（只包围文本，不是整行）
            val rect = RectF(
                x - 1f,              // 左侧（留1px间隙）
                textTop - 1f,        // 顶部
                x + textWidth + 1f,  // 右侧
                textBottom + 1f      // 底部
            )

            // 设置边框样式
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (isCurrent) borderWidth + 1f else borderWidth
            paint.color = borderColor
            paint.isAntiAlias = true

            // 绘制边框
            canvas.drawRect(rect, paint)
        } finally {
            // 恢复原始 paint 状态
            paint.color = originalColor
            paint.style = originalStyle
            paint.strokeWidth = originalStrokeWidth
        }
    }
}
