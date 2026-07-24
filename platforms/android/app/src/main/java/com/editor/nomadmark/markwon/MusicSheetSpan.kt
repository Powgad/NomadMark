package com.editor.nomadmark.markwon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.widget.TextView
import com.editor.nomadmark.music.MusicData

/**
 * 乐谱显示 Span
 *
 * 替换 ```music 代码块为渲染后的乐谱图片
 */
class MusicSheetSpan(
    private val context: Context,
    private val musicData: MusicData,
    private val screenWidth: Int
) : ReplacementSpan() {

    var bitmap: android.graphics.Bitmap? = null
        private set

    /**
     * 更新 Bitmap
     * @return 高度是否变化
     */
    fun updateBitmap(newBitmap: android.graphics.Bitmap?): Boolean {
        val oldHeight = bitmap?.height ?: 0
        bitmap = newBitmap
        val newHeight = bitmap?.height ?: 0
        android.util.Log.d("MusicSheetSpan", "updateBitmap: oldHeight=$oldHeight, newHeight=$newHeight, bitmap=${if (newBitmap != null) "${newBitmap.width}x${newBitmap.height}" else "null"}")
        return oldHeight != newHeight
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val height = bitmap?.height ?: 200 // 默认高度
        if (fm != null) {
            // 设置 FontMetricsInt 以正确计算行高
            fm.ascent = -height / 2
            fm.descent = height / 2
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        return screenWidth
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
            val bitmapTop = top + 10 // 上边距
            val bitmapPaint = Paint()
            canvas.drawBitmap(it, x, bitmapTop.toFloat(), bitmapPaint)
            android.util.Log.d("MusicSheetSpan", "drawn bitmap at x=$x, y=$bitmapTop, size=${it.width}x${it.height}")
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
