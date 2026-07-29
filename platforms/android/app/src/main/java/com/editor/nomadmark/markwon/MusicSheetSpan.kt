package com.editor.nomadmark.markwon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.widget.TextView
import com.editor.nomadmark.music.MusicData
import com.editor.nomadmark.music.MusicPlaybackController
import com.editor.nomadmark.music.NoteEvent
import com.editor.nomadmark.music.WebViewMusicRenderer

/**
 * 乐谱显示 Span
 *
 * 替换 ```music 代码块为渲染后的乐谱图片
 * 支持点击播放和高亮显示
 */
class MusicSheetSpan(
    private val context: Context,
    private val musicData: MusicData,
    private val screenWidth: Int
) : ReplacementSpan() {

    companion object {
        private const val TAG = "MusicSheetSpan"
    }

    /** 公开访问乐谱数据 */
    val music: MusicData
        get() = musicData

    var bitmap: android.graphics.Bitmap? = null
        private set

    /** 播放控制器 */
    private var playbackController: MusicPlaybackController? = null

    /** 渲染器引用 */
    private var renderer: WebViewMusicRenderer? = null

    /** 当前高亮的音符 ID */
    private var highlightedNoteId: String? = null

    /** 乐谱在屏幕上的边界（用于点击检测） */
    var screenBounds: RectF? = null
        set(value) {
            field = value
            android.util.Log.d(TAG, "screenBounds 更新: $value")
        }

    /** 是否正在初始化播放 */
    private var isInitializingPlayback: Boolean = false

    /**
     * 设置渲染器（在 Span 创建后调用）
     */
    fun setRenderer(renderer: WebViewMusicRenderer) {
        this.renderer = renderer
    }

    /**
     * 更新 Bitmap
     * @return 高度是否变化
     */
    fun updateBitmap(newBitmap: android.graphics.Bitmap?): Boolean {
        val oldHeight = bitmap?.height ?: 0
        bitmap = newBitmap
        val newHeight = bitmap?.height ?: 0
        android.util.Log.d(TAG, "updateBitmap: oldHeight=$oldHeight, newHeight=$newHeight, bitmap=${if (newBitmap != null) "${newBitmap.width}x${newBitmap.height}" else "null"}")
        return oldHeight != newHeight
    }

    // =========================================================================
    // 播放控制
    // =========================================================================

    /**
     * 处理点击事件
     * @return 是否处理了该点击
     */
    fun handleTap(x: Float, y: Float): Boolean {
        // 检查点击是否在乐谱区域内
        val bounds = screenBounds ?: return false
        if (!bounds.contains(x, y)) return false

        android.util.Log.d(TAG, "乐谱被点击: ${musicData.title ?: musicData.id}")

        // 如果正在初始化，忽略点击
        if (isInitializingPlayback) {
            android.util.Log.d(TAG, "正在初始化播放，忽略点击")
            return true
        }

        // 切换播放状态
        togglePlayback()
        return true
    }

    /**
     * 切换播放/暂停
     */
    fun togglePlayback() {
        val controller = playbackController
        android.util.Log.d(TAG, "togglePlayback: controller=$controller, isPlaying=${controller?.isPlaying}, isPaused=${controller?.isPaused}")

        when {
            controller == null || (!controller.isPlaying && !controller.isPaused) -> {
                // 开始播放
                android.util.Log.d(TAG, "togglePlayback: 开始播放")
                startPlayback()
            }
            controller.isPlaying -> {
                // 暂停
                android.util.Log.d(TAG, "togglePlayback: 暂停播放")
                controller.pausePlayback()
                showPauseIndicator()
            }
            controller.isPaused -> {
                // 恢复
                android.util.Log.d(TAG, "togglePlayback: 恢复播放")
                controller.resumePlayback()
                hidePauseIndicator()
            }
        }
    }

    /**
     * 开始播放
     */
    private fun startPlayback() {
        android.util.Log.d(TAG, "开始播放: ${musicData.title ?: musicData.id}")

        // 估算音符数量（从内容长度估算）
        val totalNotes = estimateNoteCount(musicData.content)

        // 创建播放控制器
        if (playbackController == null) {
            playbackController = MusicPlaybackController(musicData)
        }

        // 开始播放
        playbackController?.startPlayback(
            totalNotes = totalNotes,
            tempo = musicData.tempo,
            listener = createPlaybackListener()
        )
    }

    /**
     * 估算音符数量
     */
    private fun estimateNoteCount(content: String): Int {
        // 简单估算：统计可能为音符的字符
        val noteChars = setOf('C', 'D', 'E', 'F', 'G', 'A', 'B', 'c', 'd', 'e', 'f', 'g', 'a', 'b')
        var count = 0
        for (char in content) {
            if (char in noteChars) count++
        }
        return count.coerceAtLeast(8)  // 至少 8 个音符
    }

    /**
     * 创建播放监听器
     */
    private fun createPlaybackListener(): MusicPlaybackController.OnPlaybackProgressListener {
        return object : MusicPlaybackController.OnPlaybackProgressListener {
            override fun onProgressUpdate(progress: Float, isPlaying: Boolean) {
                android.util.Log.d(TAG, "播放进度: $progress")
                // 不需要重新渲染，只是更新进度显示
                onBitmapUpdated?.invoke(false)
            }

            override fun onPlaybackComplete() {
                android.util.Log.d(TAG, "播放完成")
                highlightedNoteId = null
                showCompleteIndicator()
            }
        }
    }

    /**
     * 回调：当 Bitmap 更新时通知外部
     */
    var onBitmapUpdated: ((heightChanged: Boolean) -> Unit)? = null

    /**
     * 停止播放（在 Span 被回收时调用）
     */
    fun cleanup() {
        playbackController?.cleanup()
        playbackController = null
        renderer = null
        bitmap?.recycle()
        bitmap = null
    }

    // =========================================================================
    // 可见性控制
    // =========================================================================

    /**
     * 更新可见性状态
     */
    fun updateVisibility(isVisible: Boolean) {
        playbackController?.isVisible = isVisible
    }

    // =========================================================================
    // 视觉指示器
    // =========================================================================

    /** 暂停指示器状态 */
    private var showPauseIndicator: Boolean = false

    /** 完成指示器状态 */
    private var showCompleteIndicator: Boolean = false

    private fun showPauseIndicator() {
        showPauseIndicator = true
        onBitmapUpdated?.invoke(false)
    }

    private fun hidePauseIndicator() {
        showPauseIndicator = false
        onBitmapUpdated?.invoke(false)
    }

    private fun showCompleteIndicator() {
        showCompleteIndicator = true
        onBitmapUpdated?.invoke(false)
    }

    // =========================================================================
    // 播放指示器绘制
    // =========================================================================

    /**
     * 绘制播放状态指示器
     */
    private fun drawPlaybackIndicator(
        canvas: Canvas,
        x: Float,
        bitmapY: Float,
        bitmapWidth: Int,
        bitmapHeight: Int
    ) {
        val controller = playbackController
        val isPlaying = controller?.isPlaying == true
        val progress = controller?.getProgress() ?: 0f

        if (!isPlaying && !showPauseIndicator && !showCompleteIndicator) {
            return
        }

        val margin = 16f
        val barHeight = 6f
        val barY = bitmapY + bitmapHeight + margin

        val paint = Paint()
        paint.isAntiAlias = true

        when {
            showCompleteIndicator -> {
                // 播放完成：显示重播图标
                val indicatorSize = 48f
                val indicatorX = x + bitmapWidth / 2 - indicatorSize / 2
                val indicatorY = bitmapY + bitmapHeight / 2 - indicatorSize / 2

                // 背景圆
                paint.color = Color.argb(200, 100, 180, 100)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(
                    x + bitmapWidth / 2,
                    bitmapY + bitmapHeight / 2,
                    indicatorSize / 2,
                    paint
                )

                // 重播图标
                paint.color = Color.WHITE
                val triangleSize = indicatorSize / 3
                val centerX = x + bitmapWidth / 2
                val centerY = bitmapY + bitmapHeight / 2
                val path = android.graphics.Path()
                path.moveTo(centerX - triangleSize / 3, centerY - triangleSize / 2)
                path.lineTo(centerX + triangleSize / 3, centerY)
                path.lineTo(centerX - triangleSize / 3, centerY + triangleSize / 2)
                path.close()
                canvas.drawPath(path, paint)
            }
            isPlaying || showPauseIndicator -> {
                // 绘制进度条
                val barWidth = bitmapWidth - 2 * margin

                // 背景条
                paint.color = Color.argb(100, 200, 200, 200)
                paint.style = Paint.Style.FILL
                canvas.drawRect(
                    x + margin,
                    barY,
                    x + margin + barWidth,
                    barY + barHeight,
                    paint
                )

                // 进度条
                paint.color = Color.argb(200, 65, 105, 225)
                canvas.drawRect(
                    x + margin,
                    barY,
                    x + margin + barWidth * progress,
                    barY + barHeight,
                    paint
                )

                // 绘制播放/暂停图标
                val iconSize = 32f
                val iconX = x + bitmapWidth / 2 - iconSize / 2
                val iconY = bitmapY + bitmapHeight / 2 - iconSize / 2

                // 半透明背景圆
                paint.color = Color.argb(180, if (isPlaying) 65 else 200, if (isPlaying) 105 else 150, 50)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(
                    x + bitmapWidth / 2,
                    bitmapY + bitmapHeight / 2,
                    iconSize / 2 + 8f,
                    paint
                )

                paint.color = Color.WHITE
                if (isPlaying) {
                    // 暂停图标（两条竖线）
                    val barWidth = 6f
                    val barHeight = 14f
                    val centerX = x + bitmapWidth / 2
                    val centerY = bitmapY + bitmapHeight / 2
                    canvas.drawRect(
                        centerX - barWidth - 2f,
                        centerY - barHeight / 2,
                        centerX - 2f,
                        centerY + barHeight / 2,
                        paint
                    )
                    canvas.drawRect(
                        centerX + 2f,
                        centerY - barHeight / 2,
                        centerX + barWidth + 2f,
                        centerY + barHeight / 2,
                        paint
                    )
                } else {
                    // 播放图标（三角形）
                    val triangleSize = iconSize / 3
                    val centerX = x + bitmapWidth / 2
                    val centerY = bitmapY + bitmapHeight / 2
                    val path = android.graphics.Path()
                    path.moveTo(centerX - triangleSize / 3, centerY - triangleSize / 2)
                    path.lineTo(centerX + triangleSize / 3, centerY)
                    path.lineTo(centerX - triangleSize / 3, centerY + triangleSize / 2)
                    path.close()
                    canvas.drawPath(path, paint)
                }
            }
        }
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
        val height = (bitmap?.height ?: 200) + verticalPadding

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
            // 计算垂直居中位置：总高度减去 bitmap 高度后除以 2，得到上边距
            val totalHeight = bottom - top
            val bitmapHeight = it.height
            val verticalPadding = 80  // 与 getSize() 中保持一致
            val topMargin = (totalHeight - bitmapHeight) / 2
            val bitmapTop = top + topMargin.coerceAtLeast(verticalPadding / 2)

            val bitmapPaint = Paint()
            canvas.drawBitmap(it, x, bitmapTop.toFloat(), bitmapPaint)

            // 绘制播放状态指示器
            drawPlaybackIndicator(canvas, x, bitmapTop.toFloat(), it.width, it.height)

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
