package com.editor.nomadmark.markwon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.widget.Toast
import com.editor.nomadmark.MarkdownEditorActivity
import com.editor.nomadmark.music.MusicSheetBitmapCache
import com.editor.nomadmark.music.MusicSheetBitmapCache.RenderState

/**
 * 乐谱渲染 Span
 *
 * 为乐谱代码块添加特殊渲染，包括：
 * - 乐谱背景
 * - 渲染的位图（如果已渲染完成）
 * - 占位符（正在渲染或渲染失败时）
 * - 播放按钮（未来功能）
 *
 * 使用方式：在代码块后处理中识别 ```music 或 ```简谱 块，
 * 然后应用此 Span
 */
class MusicSheetSpan(
    private val context: MarkdownEditorActivity,
    private val musicData: MusicData,
    private val screenWidth: Int
) : ReplacementSpan() {

    /** 播放按钮区域 */
    private var playButtonRect: RectF? = null

    /** 渲染的位图 */
    private var renderedBitmap: Bitmap? = null

    /** 是否已经过一次渲染 */
    private var hasRenderedOnce = false

    /** 播放按钮点击监听器 */
    var onPlayButtonClickListener: ((MusicData) -> Unit)? = null

    /**
     * 获取渲染状态
     */
    val renderState: MusicSheetBitmapCache.RenderState
        get() = MusicSheetBitmapCache.getState(musicData)

    /**
     * 更新位图并刷新显示
     *
     * @param bitmap 新的位图
     * @return 是否需要重新布局（高度变化）
     */
    fun updateBitmap(bitmap: Bitmap?): Boolean {
        android.util.Log.d("MusicSheetSpan", "updateBitmap 被调用: bitmap=${bitmap?.width}x${bitmap?.height}")
        val oldBitmap = renderedBitmap
        renderedBitmap = bitmap

        val heightChanged = if (oldBitmap == null && bitmap != null) {
            android.util.Log.d("MusicSheetSpan", "首次渲染成功，高度将变化")
            true // 首次渲染，高度会变化
        } else if (oldBitmap != null && bitmap != null) {
            oldBitmap.height != bitmap.height
        } else {
            android.util.Log.d("MusicSheetSpan", "bitmap is null, 高度不变化")
            false
        }

        hasRenderedOnce = true
        android.util.Log.d("MusicSheetSpan", "updateBitmap 完成: heightChanged=$heightChanged")
        return heightChanged
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        // 如果有位图，使用位图高度
        renderedBitmap?.let { bmp ->
            fm?.apply {
                top = -bmp.height
                bottom = 0
                ascent = -bmp.height
                descent = 0
            }
            return screenWidth - 80
        }

        // 没有位图时，使用默认高度
        val placeholderHeight = if (musicData.title != null) {
            120 // 有标题时高度更大
        } else {
            100 // 没有标题时的默认高度
        }

        fm?.apply {
            top = -placeholderHeight
            bottom = 0
            ascent = -placeholderHeight
            descent = 0
        }

        // 返回乐谱容器的宽度（全屏宽度减去边距）
        return screenWidth - 80 // 40dp 左右边距
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        android.util.Log.d("MusicSheetSpan", "draw() 被调用: top=$top, bottom=$bottom, height=${bottom - top}")
        val width = getSize(paint, text, start, end, null).toFloat()
        val height = (bottom - top).toFloat()

        // 1. 如果有渲染好的位图，直接绘制
        renderedBitmap?.let { bmp ->
            android.util.Log.d("MusicSheetSpan", "绘制位图: ${bmp.width}x${bmp.height}")
            drawBitmap(canvas, bmp, x, top, width, paint)
            drawPlayButton(canvas, x, top, width, bmp.height, paint)
            return
        }

        // 2. 否则绘制占位符
        android.util.Log.d("MusicSheetSpan", "绘制占位符: ${width}x${height}")
        drawPlaceholder(canvas, x, top, width, height, paint)
    }

    /**
     * 绘制位图
     */
    private fun drawBitmap(
        canvas: Canvas,
        bmp: Bitmap,
        x: Float,
        top: Int,
        width: Float,
        paint: Paint
    ) {
        // 计算缩放比例以适应宽度
        val scale = width / bmp.width
        val scaledHeight = (bmp.height * scale).toInt()

        // 绘制背景
        paint.color = 0xFFF5F5F5.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRect(x, top.toFloat(), x + width, (top + scaledHeight).toFloat(), paint)

        // 绘制位图
        val dstRect = android.graphics.RectF(
            x, top.toFloat(),
            x + width, (top + scaledHeight).toFloat()
        )
        canvas.drawBitmap(bmp, null, dstRect, paint)

        // 绘制边框
        paint.color = 0xFF505050.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(x, top.toFloat(), x + width, (top + scaledHeight).toFloat(), paint)
    }

    /**
     * 绘制占位符
     */
    private fun drawPlaceholder(
        canvas: Canvas,
        x: Float,
        top: Int,
        width: Float,
        height: Float,
        paint: Paint
    ) {
        // 1. 绘制背景（浅灰，墨水屏友好）
        paint.color = 0xFFF5F5F5.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRect(x, top.toFloat(), x + width, top + height, paint)

        // 2. 绘制边框
        paint.color = 0xFF505050.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(x, top.toFloat(), x + width, top + height, paint)

        // 3. 绘制标题（如果有）
        musicData.title?.let { title ->
            paint.color = 0xFF000000.toInt()
            paint.style = Paint.Style.FILL
            paint.textSize = 24f
            canvas.drawText("🎵 $title", x + 16f, (top + 40).toFloat(), paint)
        }

        // 4. 根据渲染状态绘制不同内容
        val state = renderState
        val contentTop = if (musicData.title != null) top + 60 else top + 20

        when (state) {
            MusicSheetBitmapCache.RenderState.RENDERING -> {
                // 正在渲染
                paint.color = 0xFF808080.toInt()
                paint.textSize = 16f
                canvas.drawText("正在渲染乐谱...", x + 16f, contentTop.toFloat(), paint)
            }
            MusicSheetBitmapCache.RenderState.FAILED -> {
                // 渲染失败
                paint.color = 0xFF808080.toInt()
                paint.textSize = 16f
                canvas.drawText("渲染失败", x + 16f, contentTop.toFloat(), paint)

                // 显示类型标签
                val typeLabel = when (musicData.type) {
                    MusicType.ABC -> "ABC 记谱法"
                    MusicType.JIANPU -> "简谱"
                }
                canvas.drawText("[$typeLabel] ${musicData.content.lines().size} 行", x + 16f, (contentTop + 30).toFloat(), paint)

                // 显示前几行乐谱内容预览
                paint.textSize = 14f
                val previewLines = musicData.content.lines().take(3)
                previewLines.forEachIndexed { index, line ->
                    if (line.isNotBlank()) {
                        val yOffset = contentTop + 60 + (index + 1) * 20
                        val displayLine = if (line.length > 40) "${line.take(40)}..." else line
                        canvas.drawText(displayLine, x + 16f, yOffset.toFloat(), paint)
                    }
                }
            }
            else -> {
                // 未开始渲染或刚完成
                paint.color = 0xFF808080.toInt()
                paint.textSize = 16f

                // 显示类型标签
                val typeLabel = when (musicData.type) {
                    MusicType.ABC -> "ABC 记谱法"
                    MusicType.JIANPU -> "简谱"
                }
                canvas.drawText("[$typeLabel] ${musicData.content.lines().size} 行", x + 16f, contentTop.toFloat(), paint)

                // 显示前几行乐谱内容预览
                paint.textSize = 14f
                val previewLines = musicData.content.lines().take(3)
                previewLines.forEachIndexed { index, line ->
                    if (line.isNotBlank()) {
                        val yOffset = contentTop + 30 + (index + 1) * 20
                        val displayLine = if (line.length > 40) "${line.take(40)}..." else line
                        canvas.drawText(displayLine, x + 16f, yOffset.toFloat(), paint)
                    }
                }
            }
        }

        // 5. 绘制播放按钮（如果有音频）
        drawPlayButton(canvas, x, top, width, height.toInt(), paint)
    }

    /**
     * 绘制播放按钮
     */
    private fun drawPlayButton(
        canvas: Canvas,
        x: Float,
        top: Int,
        width: Float,
        contentHeight: Int,
        paint: Paint
    ) {
        if (!musicData.hasAudio) return

        val bitmapHeight = renderedBitmap?.height ?: contentHeight
        val buttonSize = 48f
        val buttonRight = x + width - 16f
        val buttonTop = (top + 20).toFloat()
        val buttonLeft = buttonRight - buttonSize
        val buttonBottom = buttonTop + buttonSize

        playButtonRect = RectF(buttonLeft, buttonTop, buttonRight, buttonBottom)

        // 绘制按钮背景
        paint.color = 0xFF333333.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            buttonLeft, buttonTop,
            buttonRight, buttonBottom,
            8f, 8f, paint
        )

        // 绘制播放图标（三角形）
        paint.color = 0xFFFFFFFF.toInt()
        paint.style = Paint.Style.FILL
        val iconSize = 16f
        val iconLeft = buttonLeft + (buttonSize - iconSize) / 2
        val iconTop = buttonTop + (buttonSize - iconSize) / 2

        val iconPath = android.graphics.Path().apply {
            moveTo(iconLeft, iconTop)
            lineTo(iconLeft + iconSize, iconTop + iconSize / 2)
            lineTo(iconLeft, iconTop + iconSize)
            close()
        }
        canvas.drawPath(iconPath, paint)
    }

    /**
     * 处理点击事件
     *
     * @param x 点击的 X 坐标
     * @param y 点击的 Y 坐标
     * @return 是否处理了点击事件
     */
    fun handleClick(x: Float, y: Float): Boolean {
        // 检查是否点击了播放按钮
        if (musicData.hasAudio && playButtonRect?.contains(x, y) == true) {
            onPlayButtonClickListener?.invoke(musicData)
            Toast.makeText(context, "播放: ${musicData.title ?: "乐谱"}", Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    /**
     * 获取乐谱数据
     */
    fun getMusicData(): MusicData = musicData

    /**
     * 释放资源
     */
    fun release() {
        // 不释放位图，因为它由缓存管理
        renderedBitmap = null
    }
}

/**
 * 乐谱块信息
 *
 * 用于存储检测到的乐谱块范围和数据
 */
data class MusicSheetBlockInfo(
    val blockStart: Int,
    val blockEnd: Int,
    val musicData: MusicData
)

/**
 * 乐谱块检测器
 *
 * 从渲染后的 Spanned 中识别乐谱代码块
 */
object MusicSheetDetector {

    /**
     * 检测乐谱块
     *
     * @param spanned 渲染后的文本
     * @return 检测到的乐谱块列表
     */
    fun detectMusicSheets(spanned: Spanned): List<MusicSheetBlockInfo> {
        val results = mutableListOf<MusicSheetBlockInfo>()

        try {
            // 尝试多种可能的代码块 Span 类型
            val spanTypes = listOf(
                "io.noties.markwon.core.spans.CodeBlockSpan",
                "io.noties.markwon.ext.prismj.PrismJSpan",
                "ru.noties.markwon.span.CodeBlockSpan",
                "ru.noties.markwon.span.PrismJSpan"
            )

            for (spanType in spanTypes) {
                try {
                    val codeBlockSpanClass = Class.forName(spanType)
                    val codeSpans = spanned.getSpans(0, spanned.length, codeBlockSpanClass)

                    android.util.Log.d("MusicSheetDetector", "找到 ${codeSpans.size} 个 $spanType")

                    for (span in codeSpans) {
                        val start = spanned.getSpanStart(span)
                        val end = spanned.getSpanEnd(span)
                        val blockText = spanned.subSequence(start, end).toString()

                        // 检查是否是乐谱块
                        val musicData = parseMusicBlock(blockText)
                        if (musicData != null) {
                            results.add(MusicSheetBlockInfo(start, end, musicData))
                            android.util.Log.d("MusicSheetDetector", "检测到乐谱块: [$start-$end], ${musicData.title}")
                        }
                    }

                    if (codeSpans.isNotEmpty()) {
                        break // 找到了代码块，不再尝试其他类型
                    }
                } catch (e: ClassNotFoundException) {
                    // 这个类型不存在，尝试下一个
                    android.util.Log.d("MusicSheetDetector", "未找到 Span 类型: $spanType")
                }
            }

            // 如果上述方法都没找到，尝试使用 BackgroundColorSpan
            if (results.isEmpty()) {
                android.util.Log.d("MusicSheetDetector", "尝试使用 BackgroundColorSpan 检测")
                val bgSpans = spanned.getSpans(0, spanned.length, android.text.style.BackgroundColorSpan::class.java)

                for (span in bgSpans) {
                    val start = spanned.getSpanStart(span)
                    val end = spanned.getSpanEnd(span)
                    val blockText = spanned.subSequence(start, end).toString()

                    // 检查是否是乐谱块
                    val musicData = parseMusicBlock(blockText)
                    if (musicData != null) {
                        results.add(MusicSheetBlockInfo(start, end, musicData))
                        android.util.Log.d("MusicSheetDetector", "通过 BackgroundColorSpan 检测到乐谱块: [$start-$end], ${musicData.title}")
                    }
                }
            }

            android.util.Log.d("MusicSheetDetector", "总共检测到 ${results.size} 个乐谱块")

        } catch (e: Exception) {
            android.util.Log.e("MusicSheetDetector", "检测乐谱块时出错", e)
            e.printStackTrace()
        }

        return results
    }

    /**
     * 解析乐谱块
     *
     * @param blockText 代码块文本
     * @return 解析出的 MusicData，如果不是乐谱块则返回 null
     */
    private fun parseMusicBlock(blockText: String): MusicData? {
        android.util.Log.d("MusicSheetDetector", "解析代码块文本，前100字符: ${blockText.take(100)}")

        val lines = blockText.lines()
        if (lines.isEmpty()) return null

        // 检查第一行是否是语言标识符
        val firstLine = lines[0].trim()

        // Markwon 渲染后的代码块可能不包含 ``` 标记
        // 所以我们直接检查内容来判断是否是乐谱
        if (firstLine.startsWith("```")) {
            // 包含 ``` 标记，提取语言标识符
            val language = firstLine.removePrefix("```").trim().lowercase()
            android.util.Log.d("MusicSheetDetector", "语言标识符: $language")

            val musicType = when (language) {
                "music", "abc" -> MusicType.ABC
                "简谱", "jianpu", "numbered" -> MusicType.JIANPU
                else -> return null
            }

            // 提取乐谱内容（去除语言标识符和结束围栏）
            val contentLines = lines.drop(1).dropLastWhile { it.trim().startsWith("```") }
            val content = contentLines.joinToString("\n")

            return parseMusicContent(content, musicType)
        } else {
            // 不包含 ``` 标记，可能是 Markwon 渲染后的纯文本
            // 检查是否像乐谱内容（包含 ABC 或简谱特征）
            android.util.Log.d("MusicSheetDetector", "无 ``` 标记，检查内容是否像乐谱")

            // 尝试检测是否是 ABC 乐谱（优先检测，因为 ABC 更容易识别）
            if (containsABCNotation(blockText)) {
                android.util.Log.d("MusicSheetDetector", "检测到 ABC 乐谱")
                return parseMusicContent(blockText, MusicType.ABC)
            }

            // 尝试检测是否是简谱
            if (containsJianpuNotation(blockText)) {
                android.util.Log.d("MusicSheetDetector", "检测到简谱")
                return parseMusicContent(blockText, MusicType.JIANPU)
            }

            android.util.Log.d("MusicSheetDetector", "不是乐谱块")
            return null
        }
    }

    /**
     * 解析乐谱内容
     */
    private fun parseMusicContent(content: String, type: MusicType): MusicData? {
        android.util.Log.d("MusicSheetDetector", "解析乐谱内容: type=$type, 内容前100字符=${content.take(100)}")

        // 直接使用完整内容（包括所有 ABC 字段），不进行额外的元数据解析
        // 因为 ABC 乐谱本身就需要完整的头部（X:, T:, K: 等）
        // 简谱的元数据（title:, composer: 等）保留在内容中，转换时再处理

        // 对于 ABC 类型，尝试从内容中提取标题
        var title: String? = null
        var composer: String? = null
        if (type == MusicType.ABC) {
            for (line in content.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("T:")) {
                    title = trimmed.removePrefix("T:").trim()
                } else if (trimmed.startsWith("C:")) {
                    composer = trimmed.removePrefix("C:").trim()
                }
            }
        }

        return MusicData(
            id = MusicData.generateId(),
            type = type,
            content = content,
            title = title,
            composer = composer,
            tempo = 120,
            key = null,
            audioPath = null
        )
    }

    /**
     * 检查文本是否包含 ABC 记谱法特征
     */
    private fun containsABCNotation(text: String): Boolean {
        val lines = text.lines()
        var hasFieldMarkers = 0
        var hasNotes = 0
        var hasAbcSpecific = false

        android.util.Log.d("MusicSheetDetector", "ABC 检测: 行数=${lines.size}")

        // ABC 标准字段列表（单字母后跟冒号）
        val abcFields = setOf("X", "T", "C", "M", "L", "Q", "K", "S", "A", "B", "D", "H", "N", "O", "R", "W", "Z")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            android.util.Log.d("MusicSheetDetector", "检查行: '$trimmed'")

            // 检查 ABC 字段标记（如 X:1, T:标题, K:C 等）
            if (trimmed.length >= 2 && trimmed[1] == ':') {
                val fieldChar = trimmed[0].toString().uppercase()
                if (abcFields.contains(fieldChar)) {
                    hasFieldMarkers++
                    android.util.Log.d("MusicSheetDetector", "ABC 字段标记: $trimmed")
                    // X: 和 K: 是 ABC 乐谱的强标识
                    if (fieldChar == "X" || fieldChar == "K") {
                        hasAbcSpecific = true
                    }
                }
            }

            // 检查音符和小节线组合
            // ABC 音符特点：包含 A-Ga-g 字母，可能有 /2, /4, ', ", 等修饰符
            if (trimmed.contains("|") || trimmed.contains(":") || trimmed.contains("%")) {
                // 检查是否有音符字母
                val hasNoteLetters = trimmed.matches(Regex(".*[A-Ga-g].*"))
                // 但不是编程代码（排除 if, for, var 等）
                val notCode = !trimmed.matches(Regex("(?i).*(\\b(function|var|let|const|if|for|while|return|import|from|class)\\b|\\{\\}|;).*"))

                if (hasNoteLetters && notCode) {
                    hasNotes++
                    android.util.Log.d("MusicSheetDetector", "ABC 音符行: $trimmed")
                }
            }
        }

        android.util.Log.d("MusicSheetDetector", "ABC 检测结果: hasFieldMarkers=$hasFieldMarkers, hasNotes=$hasNotes, hasAbcSpecific=$hasAbcSpecific")

        // 只要满足以下任一条件就认为是 ABC 乐谱：
        // 1. 有 X: 或 K: 字段（强标识）
        // 2. 有 2 个以上字段标记 + 1 个以上音符行
        // 3. 有 3 个以上字段标记
        return hasAbcSpecific || (hasFieldMarkers >= 2 && hasNotes >= 1) || hasFieldMarkers >= 3
    }

    /**
     * 检查文本是否包含简谱特征
     */
    private fun containsJianpuNotation(text: String): Boolean {
        val lines = text.lines()
        var hasDigitNotes = 0
        var hasBarLines = 0
        var hasKeySignature = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // 检查调号标记（如 1=C, 1=F, 1=D 等）
            if (trimmed.matches(Regex("1=[A-G].*"))) {
                hasKeySignature = true
                android.util.Log.d("MusicSheetDetector", "简谱调号: $trimmed")
                continue
            }

            // 检查拍号（如 4/4, 2/4）
            if (trimmed.matches(Regex("\\d+/\\d+.*"))) {
                android.util.Log.d("MusicSheetDetector", "简谱拍号: $trimmed")
                continue
            }

            // 检查是否包含小节线
            if (trimmed.contains("|")) {
                hasBarLines++
            }

            // 检查数字音符（1-7）- 要排除编程代码
            // 简谱特点：多个数字用空格分隔，可能带 - _ 等修饰符
            val hasJianpuPattern = trimmed.matches(Regex(".*\\b[1-7]([-_][1-7]|\\s+[1-7]).*")) ||
                                   trimmed.matches(Regex(".*[1-7]\\s+[1-7].*"))

            if (hasJianpuPattern) {
                hasDigitNotes++
                android.util.Log.d("MusicSheetDetector", "简谱音符行: $trimmed")
            }
        }

        android.util.Log.d("MusicSheetDetector", "简谱检测结果: hasKeySignature=$hasKeySignature, hasDigitNotes=$hasDigitNotes, hasBarLines=$hasBarLines")

        // 满足以下任一条件认为是简谱：
        // 1. 有调号标记 (1=C 等)
        // 2. 有 3 个以上音符行 + 1 个以上小节线
        return hasKeySignature || (hasDigitNotes >= 2 && hasBarLines >= 1)
    }

    /**
     * 检查是否是支持的语言
     */
    private fun isSupportedLanguage(language: String): Boolean {
        return when (language.lowercase()) {
            "music", "abc", "简谱", "jianpu", "numbered" -> true
            else -> false
        }
    }
}
