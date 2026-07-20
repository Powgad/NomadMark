package com.editor.nomadmark

import android.graphics.Rect
import android.graphics.Point
import android.util.Log
import android.util.LruCache
import android.widget.EditText

/**
 * 手势编辑器，用于笔输入和修订模式
 *
 * 使用 LRU 缓存处理坐标空间校正以提高性能。
 */
class GestureEditor {

    companion object {
        private const val TAG = "GestureEditor"
        private const val COORD_CORRECTION_THRESHOLD = 20  // 像素
        private const val CACHE_SIZE = 32  // 缓存校正数量
    }

    /**
     * 坐标校正的 LRU 缓存
     *
     * 键: "left_top_right_bottom" (例如 "100_200_300_400")
     * 值: 校正后的 Rect
     */
    private val correctionCache = object : LruCache<String, Rect>(CACHE_SIZE) {
        override fun sizeOf(key: String, value: Rect): Int {
            // 每个缓存项约占 ~1 个字符串键 + 1 个 rect
            return key.length + 16  // 近似字节数
        }
    }

    /**
     * 缓存统计
     */
    private var cacheHits = 0
    private var cacheMisses = 0

    /**
     * 处理识别结果并进行坐标校正
     *
     * @param result 识别结果数据
     * @param editor 目标 EditText
     */
    fun processRecognitionResult(result: RecognResultData, editor: EditText) {
        val bbox = result.boundingBox
        val keyPoint = result.keyPoint

        // 首先检查缓存
        val correctedRect = getCachedCorrection(bbox, keyPoint)

        // 根据手势类型执行操作
        when (result.gestureType) {
            GestureType.DELETE -> {
                deleteTextRange(correctedRect, editor)
            }
            GestureType.INSERT -> {
                // 对于插入手势，需要在矩形位置插入文本
                val (line, _) = findTextPositionsForRect(correctedRect, editor)
                insertAtLine(line, result.text, editor)
            }
            GestureType.SELECT -> {
                // 圈选功能：对文本范围加粗
                boldTextRange(correctedRect, editor)
            }
        }
    }

    /**
     * 获取坐标校正（带缓存）
     *
     * @param bbox 来自识别的原始边界框
     * @param keyPoint 来自触摸输入的关键点
     * @return 校正后的矩形
     */
    private fun getCachedCorrection(bbox: Rect, keyPoint: Point): Rect {
        // 生成缓存键
        val cacheKey = "${bbox.left}_${bbox.top}_${bbox.right}_${bbox.bottom}_${keyPoint.x}_${keyPoint.y}"

        // 检查缓存
        val cached = correctionCache.get(cacheKey)
        if (cached != null) {
            cacheHits++
            logCacheStats()
            return cached
        }

        cacheMisses++

        // 检查是否需要校正
        val corrected = if (needsCoordinateCorrection(bbox, keyPoint)) {
            correctCoordinateSpace(bbox, keyPoint)
        } else {
            bbox
        }

        // 存储到缓存
        correctionCache.put(cacheKey, corrected)
        logCacheStats()

        return corrected
    }

    /**
     * 检查是否需要坐标校正
     *
     * @param bbox 来自识别的边界框
     * @param keyPoint 来自触摸的关键点
     * @return 如果需要校正则返回 true
     */
    private fun needsCoordinateCorrection(bbox: Rect, keyPoint: Point): Boolean {
        val bboxMidX = (bbox.left + bbox.right) / 2f
        val bboxMidY = (bbox.top + bbox.bottom) / 2f

        return Math.abs(bboxMidX - keyPoint.x) > COORD_CORRECTION_THRESHOLD ||
               Math.abs(bboxMidY - keyPoint.y) > COORD_CORRECTION_THRESHOLD
    }

    /**
     * 校正坐标空间（A6X2 兼容性）
     *
     * @param bbox 原始边界框
     * @param keyPoint 参考关键点
     * @return 校正后的矩形
     */
    private fun correctCoordinateSpace(bbox: Rect, keyPoint: Point): Rect {
        val bboxCenterX = bbox.centerX().toFloat()
        val bboxCenterY = bbox.centerY().toFloat()

        // 避免除以零
        val scaleX = if (keyPoint.x != 0) bboxCenterX / keyPoint.x else 1f
        val scaleY = if (keyPoint.y != 0) bboxCenterY / keyPoint.y else 1f

        return Rect(
            (bbox.left / scaleX).toInt(),
            (bbox.top / scaleY).toInt(),
            (bbox.right / scaleX).toInt(),
            (bbox.bottom / scaleY).toInt()
        )
    }

    /**
     * 定期记录缓存统计信息
     */
    private fun logCacheStats() {
        val total = cacheHits + cacheMisses
        if (total > 0 && total % 10 == 0) {  // Log every 10 operations
            val hitRate = (cacheHits.toFloat() / total * 100)
            Log.d(TAG, "Correction cache hit rate: ${"%.1f".format(hitRate)}% ($cacheHits/$total)")
        }
    }

    /**
     * 清除缓存（在文档更改后调用）
     */
    fun clearCache() {
        correctionCache.evictAll()
        cacheHits = 0
        cacheMisses = 0
        Log.d(TAG, "Correction cache cleared")
    }

    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Int = correctionCache.size()

    // =========================================================================
    // 文本范围操作
    // =========================================================================

    /**
     * 根据水平线位置删除该行的内容
     *
     * 将屏幕坐标转换为 EditText 行号并删除该行内容。
     *
     * @param rect 屏幕坐标中的手势边界框（水平线）
     * @param editor 要从中删除的目标 EditText
     * @param scrollY ScrollView 的滚动偏移量（重要！）
     */
    fun deleteTextRange(rect: Rect, editor: EditText, scrollY: Int = 0) {
        Log.d(TAG, "deleteTextRange: rect=$rect, scrollY=$scrollY")

        // 计算水平线中心的 Y 坐标
        // 注意：rect 中的坐标是相对于 GestureOverlayView 的
        // 由于 GestureOverlayView 和 ScrollView 位置重叠（都从搜索栏下方开始），
        // 这个坐标可以直接使用，只需转换为 EditText 内容区坐标

        val gestureY = (rect.top + rect.bottom) / 2f
        val paddingTop = editor.paddingTop

        // 转换为 EditText 内容区坐标
        // gestureY 是相对于 GestureOverlayView（和 ScrollView 位置重叠）
        // 减去 paddingTop 得到相对于 EditText 内容区的坐标
        // 加上 scrollY 考虑滚动偏移
        val contentY = gestureY - paddingTop + scrollY

        Log.d(TAG, "Coordinate conversion: gestureY=$gestureY, paddingTop=$paddingTop, scrollY=$scrollY, contentY=$contentY")

        // 根据线的 Y 坐标找到对应的行
        val lineToDelete = findLineForY(contentY, editor)

        if (lineToDelete >= 0) {
            deleteLine(lineToDelete, editor)
        } else {
            Log.w(TAG, "Could not find line for Y coordinate: contentY=$contentY")
        }
    }

    /**
     * 根据 Y 坐标找到对应的行号
     *
     * 改进的定位算法：
     * - 考虑字体大小和行间距
     * - 使用最近邻匹配（容差范围内）
     * - 处理行间隙中的坐标
     *
     * 注意：输入的 y 应该已经是相对于 EditText 内容区的坐标
     * （由调用者负责减去 paddingTop 和滚动偏移）
     *
     * @param y 相对于 EditText 内容区的 Y 值
     * @param editor EditText
     * @return 行号，如果找不到则返回 -1
     */
    private fun findLineForY(y: Float, editor: EditText): Int {
        val layout = editor.layout ?: return -1
        val text = editor.text ?: return -1

        if (text.isEmpty()) return -1

        // y 已经是相对于内容区的坐标，直接使用
        val adjustedY = y.coerceAtLeast(0f)

        // 计算平均行高（用于容差计算）
        val avgLineHeight = if (layout.lineCount > 0) {
            (layout.getLineBottom(layout.lineCount - 1) - layout.getLineTop(0)).toFloat() / layout.lineCount
        } else {
            0f
        }

        // 容差：平均行高的一半，允许手势落在行间隙中
        val tolerance = avgLineHeight * 0.5f

        // 首先尝试精确匹配
        for (i in 0 until layout.lineCount) {
            val lineTop = layout.getLineTop(i).toFloat()
            val lineBottom = layout.getLineBottom(i).toFloat()

            // 精确匹配：Y 坐标在行范围内
            if (adjustedY >= lineTop && adjustedY <= lineBottom) {
                Log.d(TAG, "Exact match line $i for Y=$adjustedY (line top=$lineTop, bottom=$lineBottom)")
                return i
            }
        }

        // 如果没有精确匹配，使用最近邻算法
        var closestLine = -1
        var minDistance = Float.MAX_VALUE

        for (i in 0 until layout.lineCount) {
            val lineTop = layout.getLineTop(i).toFloat()
            val lineBottom = layout.getLineBottom(i).toFloat()
            val lineCenter = (lineTop + lineBottom) / 2f

            // 计算到行中心的距离
            val distance = Math.abs(adjustedY - lineCenter).toFloat()

            if (distance < minDistance) {
                minDistance = distance
                closestLine = i
            }
        }

        // 如果最近距离在容差范围内，返回该行
        if (closestLine >= 0 && minDistance <= tolerance) {
            val lineTop = layout.getLineTop(closestLine).toFloat()
            val lineBottom = layout.getLineBottom(closestLine).toFloat()
            Log.d(TAG, "Closest match line $closestLine for Y=$adjustedY (distance=$minDistance, tolerance=$tolerance, line top=$lineTop, bottom=$lineBottom)")
            return closestLine
        }

        Log.w(TAG, "No line found for Y=$adjustedY (closestLine=$closestLine, minDistance=$minDistance)")
        return -1
    }

    /**
     * 删除指定行
     *
     * @param lineNumber 行号（从 0 开始）
     * @param editor EditText
     */
    private fun deleteLine(lineNumber: Int, editor: EditText) {
        val layout = editor.layout ?: return
        val text = editor.text ?: return

        if (lineNumber < 0 || lineNumber >= layout.lineCount) {
            Log.w(TAG, "Invalid line number: $lineNumber (count=${layout.lineCount})")
            return
        }

        val startPos = layout.getLineStart(lineNumber)
        val endPos = layout.getLineEnd(lineNumber)

        Log.d(TAG, "Deleting line $lineNumber: [$startPos, $endPos)")

        // 检查是否是最后一行
        val isLastLine = lineNumber == layout.lineCount - 1

        if (startPos >= 0 && endPos > startPos && endPos <= text.length) {
            if (isLastLine && startPos > 0) {
                // 最后一行：删除该行以及前面的换行符
                val lineStart = startPos
                val newlineBefore = if (lineStart > 0 && text[lineStart - 1] == '\n') {
                    lineStart - 1
                } else {
                    lineStart
                }
                text.delete(newlineBefore, endPos)
            } else {
                // 非最后一行：删除该行以及后面的换行符
                val lineEnd = if (endPos < text.length && text[endPos] == '\n') {
                    endPos + 1
                } else {
                    endPos
                }
                text.delete(startPos, lineEnd)
            }

            // 触发刷新
            editor.invalidate()
        } else {
            Log.w(TAG, "Invalid text range for line deletion: start=$startPos, end=$endPos, length=${text.length}")
        }
    }

    /**
     * 查找对应于屏幕矩形的文本位置
     *
     * @param rect 编辑器相对坐标中的矩形
     * @param editor EditText
     * @return (起始，结束) 字符位置对
     */
    private fun findTextPositionsForRect(rect: Rect, editor: EditText): Pair<Int, Int> {
        if (editor.text == null) return Pair(0, 0)

        // 获取行高和位置
        val layout = editor.layout ?: return Pair(0, 0)

        val lineHeight = layout.getLineBottom(0) - layout.getLineTop(0)
        val linePadding = editor.paddingTop

        // 查找行范围
        val topLine = ((rect.top - linePadding).coerceAtLeast(0) / lineHeight).coerceAtMost(layout.lineCount - 1)
        val bottomLine = ((rect.bottom - linePadding).coerceAtLeast(0) / lineHeight).coerceAtMost(layout.lineCount - 1)

        // 获取每一行的字符位置
        val startPos = layout.getLineStart(topLine)
        val endPos = layout.getLineEnd(bottomLine)

        return Pair(startPos, endPos)
    }

    /**
     * 触发文本范围的刷新
     *
     * @param start 起始字符位置
     * @param end 结束字符位置
     * @param editor EditText
     */
    private fun triggerPartialRefreshForRange(@Suppress("UNUSED_PARAMETER") start: Int, @Suppress("UNUSED_PARAMETER") end: Int, editor: EditText) {
        // 目前，触发编辑器视图的完整刷新
        // 未来，可以优化为仅刷新脏矩形
        // start 和 end 参数可用于局部刷新优化
        editor.invalidate()
    }

    /**
     * 在特定行插入文本（用于插入手势）
     *
     * @param line 行号（从 0 开始）
     * @param textToInsert 要插入的文本
     * @param editor EditText
     */
    fun insertAtLine(line: Int, textToInsert: String, editor: EditText) {
        Log.d(TAG, "insertAtLine: line=$line, text='$textToInsert'")

        val layout = editor.layout ?: return
        val lineCount = layout.lineCount

        if (line < 0 || line >= lineCount) {
            Log.w(TAG, "Invalid line number: $line (count=$lineCount)")
            return
        }

        val position = layout.getLineStart(line)
        editor.text?.insert(position, textToInsert)

        // 触发刷新
        editor.invalidate()
    }

    /**
     * 根据手势边界框对文本范围加粗
     *
     * 将屏幕坐标转换为 EditText 文本位置并给该范围的文本添加加粗格式。
     *
     * @param rect 屏幕坐标中的手势边界框
     * @param editor 要加粗的目标 EditText
     * @param scrollY ScrollView 的滚动偏移量（重要！）
     * @return 加粗的文本内容
     */
    fun boldTextRange(rect: Rect, editor: EditText, scrollY: Int = 0): String {
        Log.d(TAG, "boldTextRange: rect=$rect, scrollY=$scrollY")

        // 注意：rect 中的坐标是相对于 GestureOverlayView 的
        // 由于 GestureOverlayView 和 ScrollView 位置重叠（都从搜索栏下方开始），
        // 这个坐标可以直接使用，只需转换为 EditText 内容区坐标

        val paddingTop = editor.paddingTop
        val paddingLeft = editor.paddingLeft

        // 将矩形调整为编辑器内容区相对坐标
        // rect 坐标是相对于 GestureOverlayView（和 ScrollView 位置重叠）
        // 减去 padding 得到相对于 EditText 内容区的坐标
        // 加上 scrollY 考虑滚动偏移
        val adjustedRect = Rect(
            rect.left - paddingLeft,
            rect.top - paddingTop + scrollY,
            rect.right - paddingLeft,
            rect.bottom - paddingTop + scrollY
        )

        // 查找字符位置
        val (startPos, endPos) = findTextPositionsForRect(adjustedRect, editor)

        Log.d(TAG, "Bold text range: [$startPos, $endPos)")

        val text = editor.text
        if (text != null && startPos >= 0 && endPos > startPos && endPos <= text.length) {
            // 获取要加粗的文本
            val textToBold = text.substring(startPos, endPos)
            Log.d(TAG, "Text to bold: '$textToBold'")

            // 检查是否已经加粗（避免重复加粗）
            val alreadyBold = (startPos >= 2 && text[startPos - 1] == '*' && text[startPos - 2] == '*') ||
                               (endPos + 2 <= text.length && text[endPos] == '*' && text[endPos + 1] == '*')

            if (alreadyBold) {
                Log.d(TAG, "Text already bold, skipping")
                return textToBold
            }

            // 添加加粗格式 **text**
            val boldText = "**$textToBold**"
            text.replace(startPos, endPos, boldText)

            // 触发刷新
            editor.invalidate()

            return textToBold
        } else {
            Log.w(TAG, "Invalid text range: start=$startPos, end=$endPos, length=${editor.text?.length}")
            return ""
        }
    }

    /**
     * 根据手势边界框选择文本范围
     *
     * 将屏幕坐标转换为 EditText 文本位置并选中该范围。
     *
     * @param rect 屏幕坐标中的手势边界框
     * @param editor 要从中选择的目标 EditText
     * @return 选中的文本内容
     */
    fun selectTextRange(rect: Rect, editor: EditText): String {
        Log.d(TAG, "selectTextRange: rect=$rect")

        // 获取编辑器在屏幕上的位置
        val location = IntArray(2)
        editor.getLocationOnScreen(location)
        val editorLeft = location[0]
        val editorTop = location[1]

        // 将矩形调整为编辑器相对坐标
        val adjustedRect = Rect(
            rect.left - editorLeft,
            rect.top - editorTop,
            rect.right - editorLeft,
            rect.bottom - editorTop
        )

        // 查找字符位置
        val (startPos, endPos) = findTextPositionsForRect(adjustedRect, editor)

        Log.d(TAG, "Selecting text range: [$startPos, $endPos)")

        val text = editor.text
        if (text != null && startPos >= 0 && endPos > startPos && endPos <= text.length) {
            // 设置选择范围
            editor.setSelection(startPos, endPos)

            // 获取选中的文本
            val selectedText = text.substring(startPos, endPos)
            Log.d(TAG, "Selected text: '$selectedText'")

            // 触发刷新以显示选择高亮
            editor.invalidate()

            return selectedText
        } else {
            Log.w(TAG, "Invalid text range: start=$startPos, end=$endPos, length=${editor.text?.length}")
            return ""
        }
    }

    /**
     * 获取当前选中的文本
     *
     * @param editor EditText
     * @return 选中的文本内容，如果没有选择则返回空字符串
     */
    fun getSelectedText(editor: EditText): String {
        val text = editor.text ?: return ""
        val start = editor.selectionStart
        val end = editor.selectionEnd

        if (start >= 0 && end > start && end <= text.length) {
            return text.substring(start, end)
        }
        return ""
    }

    /**
     * 清除文本选择
     *
     * @param editor EditText
     */
    fun clearSelection(editor: EditText) {
        val textLength = editor.text?.length ?: 0
        if (textLength > 0) {
            // 将光标移动到末尾，清除选择
            editor.setSelection(textLength)
        }
        editor.invalidate()
    }
}

/**
 * 识别结果数据
 */
data class RecognResultData(
    val boundingBox: Rect,
    val keyPoint: Point,
    val gestureType: GestureType,
    val text: String = "",
    val confidence: Float = 1.0f  // 识别置信度 (0.0 - 1.0)
)

/**
 * 手势类型枚举
 */
enum class GestureType {
    DELETE,
    INSERT,
    SELECT
}
