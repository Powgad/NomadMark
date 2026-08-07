package com.editor.nomadmark.markwon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan
import com.editor.nomadmark.music.MusicData
import com.editor.nomadmark.music.WebViewMusicRenderer

/**
 * 乐谱显示 Span
 *
 * 替换 ```music 代码块为渲染后的乐谱图片
 */
class MusicSheetSpan(
    private val context: Context,
    private val musicData: MusicData,
    private val screenWidth: Int,
    /** 传给 WebViewMusicRenderer 的逻辑宽度（未乘 HORIZONTAL_SCALE） */
    val logicWidth: Int = screenWidth
) : ReplacementSpan() {

    companion object {
        private const val TAG = "MusicSheetSpan"
    }

    /** 公开访问乐谱数据 */
    val music: MusicData
        get() = musicData

    var bitmap: android.graphics.Bitmap? = null
        private set

    /** 渲染器引用 */
    private var renderer: WebViewMusicRenderer? = null

    /** 乐谱在屏幕上的边界（用于点击检测） */
    var screenBounds: RectF? = null
        set(value) {
            field = value
            android.util.Log.d(TAG, "screenBounds 更新: $value")
        }

    /**
     * 设置渲染器（在 Span 创建后调用）
     */
    fun setRenderer(renderer: WebViewMusicRenderer) {
        this.renderer = renderer
    }

    /**
     * 清空 Bitmap（用于横竖屏切换时先释放旧 bitmap，使用默认高度）
     */
    fun clearBitmap() {
        bitmap?.recycle()
        bitmap = null
        android.util.Log.d(TAG, "clearBitmap: title=${musicData.title}")
    }

    /**
     * 更新 Bitmap
     * @return 尺寸是否变化（宽度或高度变化都需要重新布局）
     */
    fun updateBitmap(newBitmap: android.graphics.Bitmap?): Boolean {
        val oldWidth = bitmap?.width ?: 0
        val oldHeight = bitmap?.height ?: 0
        bitmap = newBitmap
        val newWidth = bitmap?.width ?: 0
        val newHeight = bitmap?.height ?: 0
        android.util.Log.d(TAG, "updateBitmap: old=${oldWidth}x${oldHeight}, new=${newWidth}x${newHeight}, bitmap=${if (newBitmap != null) "${newBitmap.width}x${newBitmap.height}" else "null"}")
        return oldWidth != newWidth || oldHeight != newHeight
    }

    /**
     * 回调：当 Bitmap 更新时通知外部
     */
    var onBitmapUpdated: ((heightChanged: Boolean) -> Unit)? = null

    /**
     * 清理资源（在 Span 被回收时调用）
     */
    fun cleanup() {
        renderer = null
        bitmap?.recycle()
        bitmap = null
    }

    /**
     * 获取 Bitmap 在 TextView 坐标系中的实际边界（含 padding）。
     * 可直接与 MotionEvent 坐标比较，或配合 getLocationOnScreen 得到屏幕坐标。
     */
    fun getActualBounds(textView: android.widget.TextView): android.graphics.RectF? {
        val bmp = bitmap ?: return null

        val text = textView.text as? android.text.Spanned ?: return null
        val spanStart = text.getSpanStart(this)
        if (spanStart < 0) return null

        val layout = textView.layout ?: return null
        val line = layout.getLineForOffset(spanStart)
        if (line < 0) return null

        val lineTop = layout.getLineTop(line).toFloat()
        val lineBottom = layout.getLineBottom(line).toFloat()
        val lineLeft = layout.getLineLeft(line).toFloat()

        val bitmapWidth = bmp.width.toFloat()
        val bitmapHeight = bmp.height.toFloat()

        // layout 坐标（与 draw() 一致）
        val verticalPadding = 80f
        val totalHeight = lineBottom - lineTop
        val topMargin = ((totalHeight - bitmapHeight) / 2).coerceAtLeast(verticalPadding / 2)
        val layoutLeft = lineLeft
        val layoutTop = lineTop + topMargin

        // 转为 TextView 坐标：draw 时 canvas 已 translate(padding)，需加回 padding
        val padL = textView.totalPaddingLeft.toFloat()
        val padT = textView.totalPaddingTop.toFloat()
        return android.graphics.RectF(
            layoutLeft + padL,
            layoutTop + padT,
            layoutLeft + padL + bitmapWidth,
            layoutTop + padT + bitmapHeight
        )
    }

    /**
     * 将 draw() 记录的 layout 坐标边界转为 TextView 坐标。
     */
    fun getViewBounds(textView: android.widget.TextView): android.graphics.RectF? {
        // 优先用 layout 实测（更稳），其次用最近一次 draw 记录
        getActualBounds(textView)?.let { return it }

        val layoutBounds = screenBounds ?: return null
        val bmp = bitmap ?: return null
        val padL = textView.totalPaddingLeft.toFloat()
        val padT = textView.totalPaddingTop.toFloat()
        return android.graphics.RectF(
            layoutBounds.left + padL,
            layoutBounds.top + padT,
            layoutBounds.left + padL + bmp.width,
            layoutBounds.top + padT + bmp.height
        )
    }

    // =========================================================================
    // ReplacementSpan 实现
    // =========================================================================

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        // 上下间距，确保乐谱不与相邻内容重叠
        val verticalPadding = 80  // 上下各预留 40px
        // 默认高度根据方向设置：横屏时乐谱更大（缩放 3.3f vs 2.5f）
        val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val defaultHeight = if (isLandscape) 1500 else 600
        val bitmapHeight = bitmap?.height ?: defaultHeight
        val height = bitmapHeight + verticalPadding

        android.util.Log.d(TAG, "getSize: title=${musicData.title}, isLandscape=$isLandscape, defaultHeight=$defaultHeight, bitmapHeight=$bitmapHeight, height=$height, bitmap exists=${bitmap != null}")

        if (fm != null) {
            // 设置 FontMetricsInt 以正确计算行高
            // ascent 和 descent 决定了文本的基线位置
            // 我们让基线在中心，这样上下都有空间
            fm.ascent = -height / 2
            fm.descent = height / 2
            // top 和 bottom 决定了行的扩展边界，留出额外间距
            fm.top = fm.ascent - verticalPadding / 2
            fm.bottom = fm.descent + verticalPadding / 2
        }

        val width = bitmap?.width ?: screenWidth
        android.util.Log.d(TAG, "getSize: title=${musicData.title}, bitmap=${bitmap?.width?.toString() + "x" + bitmapHeight}, width=$width, height=$height, verticalPadding=$verticalPadding")
        // 返回 bitmap 的实际宽度（横竖屏切换后 bitmap 尺寸可能改变）
        // 如果 bitmap 还没有渲染，返回原始 screenWidth
        return width
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
        android.util.Log.d("MusicSheetSpan", "draw: title=${musicData.title}, bitmap=${if (bitmap != null) "${bitmap!!.width}x${bitmap!!.height}" else "null"}, x=$x, top=$top, bottom=$bottom")

        // 记录屏幕边界（用于点击检测）
        val totalHeight = bottom - top
        val bitmapHeight = bitmap?.height ?: 200
        val verticalPadding = 80
        val topMargin = (totalHeight - bitmapHeight) / 2
        val bitmapTop = top + topMargin.coerceAtLeast(verticalPadding / 2)
        val bitmapBottom = bitmapTop + (bitmap?.height ?: 200)

        screenBounds = android.graphics.RectF(
            x,
            bitmapTop.toFloat(),
            x + (bitmap?.width ?: screenWidth).toFloat(),
            bitmapBottom.toFloat()
        )

        bitmap?.let {
            // 检查多个位置的像素内容
            val samplePoints = listOf(
                it.width / 2 to it.height / 2,  // 中心点
                it.width / 4 to it.height / 4,  // 1/4 点
                it.width * 3 / 4 to it.height * 3 / 4,  // 3/4 点
                10 to 10  // 左上角
            )
            val pixelColors = samplePoints.map { (px, py) ->
                val pixel = it.getPixel(px, py)
                "($px,$py)=0x${Integer.toHexString(pixel)}"
            }.joinToString(", ")
            android.util.Log.d("MusicSheetSpan", "Bitmap pixels: $pixelColors, isRecycled=${it.isRecycled}")

            // 绘制 Bitmap，使用独立的 Paint 避免受原始 paint 影响
            val bitmapPaint = Paint()
            canvas.drawBitmap(it, x, bitmapTop.toFloat(), bitmapPaint)

            android.util.Log.d("MusicSheetSpan", "drawn bitmap at x=$x, y=$bitmapTop, size=${it.width}x${it.height}, totalHeight=$totalHeight, topMargin=$topMargin")
        } ?: run {
            android.util.Log.d("MusicSheetSpan", "draw: showing placeholder for ${musicData.title}")
            // 显示占位符
            val placeholderColor = Color.rgb(240, 240, 240)
            paint.color = placeholderColor
            canvas.drawRect(
                x,
                top.toFloat() + 10,
                x + screenWidth.toFloat(),
                bottom.toFloat() - 10,
                paint
            )

            // 显示文本
            paint.color = Color.rgb(150, 150, 150)
            paint.textSize = 36f
            val placeholderText = "🎵 ${musicData.title ?: "乐谱"}"
            canvas.drawText(
                placeholderText,
                x + 20,
                top + 60f,
                paint
            )
        }
    }
}
