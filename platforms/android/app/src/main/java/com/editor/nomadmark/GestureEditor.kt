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
class GestureEditor(private val editorView: MarkdownEditorView?) {

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
     */
    fun processRecognitionResult(result: RecognResultData) {
        val bbox = result.boundingBox
        val keyPoint = result.keyPoint

        // 首先检查缓存
        val correctedRect = getCachedCorrection(bbox, keyPoint)

        // 转换为文档坐标
        val (line, column) = editorView?.coordinateToOffset(
            correctedRect.centerX().toFloat(),
            correctedRect.centerY().toFloat()
        ) ?: Pair(0, 0)

        // 根据手势类型执行操作
        when (result.gestureType) {
            GestureType.DELETE -> {
                editorView?.deleteLine(line)
                triggerPartialRefresh(line)
            }
            GestureType.INSERT -> {
                editorView?.insertAtLine(line, result.text)
                triggerPartialRefresh(line)
            }
            GestureType.SELECT -> {
                editorView?.selectRange(correctedRect)
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
     * 触发特定行的部分刷新
     */
    private fun triggerPartialRefresh(line: Int) {
        val rect = editorView?.getLineBoundingRect(line) ?: return
        editorView?.refreshController?.addDirty(rect)
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
     * 根据手势边界框删除文本范围
     *
     * 将屏幕坐标转换为 EditText 文本位置并删除该范围。
     *
     * @param rect 屏幕坐标中的手势边界框
     * @param editor 要从中删除的目标 EditText
     */
    fun deleteTextRange(rect: Rect, editor: EditText) {
        Log.d(TAG, "deleteTextRange: rect=$rect")

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

        Log.d(TAG, "Deleting text range: [$startPos, $endPos)")

        if (startPos >= 0 && endPos > startPos && endPos <= editor.text?.length ?: 0) {
            editor.text?.delete(startPos, endPos)

            // 触发部分刷新
            triggerPartialRefreshForRange(startPos, endPos, editor)
        } else {
            Log.w(TAG, "Invalid text range: start=$startPos, end=$endPos, length=${editor.text?.length}")
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
        val text = editor.text ?: return Pair(0, 0)

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
    private fun triggerPartialRefreshForRange(start: Int, end: Int, editor: EditText) {
        // 目前，触发编辑器视图的完整刷新
        // 未来，可以优化为仅刷新脏矩形
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
}

/**
 * 识别结果数据
 */
data class RecognResultData(
    val boundingBox: Rect,
    val keyPoint: Point,
    val gestureType: GestureType,
    val text: String = ""
)

/**
 * 手势类型枚举
 */
enum class GestureType {
    DELETE,
    INSERT,
    SELECT
}
