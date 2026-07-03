package com.editor.nomadmark

import android.graphics.Rect
import android.graphics.Point
import android.util.Log
import android.util.LruCache
import android.widget.EditText

/**
 * Gesture Editor for pen input and revision mode
 *
 * Handles coordinate space correction with LRU caching for performance.
 */
class GestureEditor(private val editorView: MarkdownEditorView?) {

    companion object {
        private const val TAG = "GestureEditor"
        private const val COORD_CORRECTION_THRESHOLD = 20  // pixels
        private const val CACHE_SIZE = 32  // Number of cached corrections
    }

    /**
     * LRU cache for coordinate corrections
     *
     * Key: "left_top_right_bottom" (e.g., "100_200_300_400")
     * Value: Corrected Rect
     */
    private val correctionCache = object : LruCache<String, Rect>(CACHE_SIZE) {
        override fun sizeOf(key: String, value: Rect): Int {
            // Each cache entry costs ~1 string key + 1 rect
            return key.length + 16  // Approximate bytes
        }
    }

    /**
     * Cache statistics
     */
    private var cacheHits = 0
    private var cacheMisses = 0

    /**
     * Process recognition result with coordinate correction
     *
     * @param result Recognition result data
     */
    fun processRecognitionResult(result: RecognResultData) {
        val bbox = result.boundingBox
        val keyPoint = result.keyPoint

        // Check cache first
        val correctedRect = getCachedCorrection(bbox, keyPoint)

        // Convert to document coordinates
        val (line, column) = editorView?.coordinateToOffset(
            correctedRect.centerX().toFloat(),
            correctedRect.centerY().toFloat()
        ) ?: Pair(0, 0)

        // Execute based on gesture type
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
     * Get coordinate correction with caching
     *
     * @param bbox Original bounding box from recognition
     * @param keyPoint Key point from touch input
     * @return Corrected rectangle
     */
    private fun getCachedCorrection(bbox: Rect, keyPoint: Point): Rect {
        // Generate cache key
        val cacheKey = "${bbox.left}_${bbox.top}_${bbox.right}_${bbox.bottom}_${keyPoint.x}_${keyPoint.y}"

        // Check cache
        val cached = correctionCache.get(cacheKey)
        if (cached != null) {
            cacheHits++
            logCacheStats()
            return cached
        }

        cacheMisses++

        // Check if correction is needed
        val corrected = if (needsCoordinateCorrection(bbox, keyPoint)) {
            correctCoordinateSpace(bbox, keyPoint)
        } else {
            bbox
        }

        // Store in cache
        correctionCache.put(cacheKey, corrected)
        logCacheStats()

        return corrected
    }

    /**
     * Check if coordinate correction is needed
     *
     * @param bbox Bounding box from recognition
     * @param keyPoint Key point from touch
     * @return true if correction is needed
     */
    private fun needsCoordinateCorrection(bbox: Rect, keyPoint: Point): Boolean {
        val bboxMidX = (bbox.left + bbox.right) / 2f
        val bboxMidY = (bbox.top + bbox.bottom) / 2f

        return Math.abs(bboxMidX - keyPoint.x) > COORD_CORRECTION_THRESHOLD ||
               Math.abs(bboxMidY - keyPoint.y) > COORD_CORRECTION_THRESHOLD
    }

    /**
     * Correct coordinate space (A6X2 compatibility)
     *
     * @param bbox Original bounding box
     * @param keyPoint Reference key point
     * @return Corrected rectangle
     */
    private fun correctCoordinateSpace(bbox: Rect, keyPoint: Point): Rect {
        val bboxCenterX = bbox.centerX().toFloat()
        val bboxCenterY = bbox.centerY().toFloat()

        // Avoid division by zero
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
     * Trigger partial refresh for a specific line
     */
    private fun triggerPartialRefresh(line: Int) {
        val rect = editorView?.getLineBoundingRect(line) ?: return
        editorView?.refreshController?.addDirty(rect)
    }

    /**
     * Log cache statistics periodically
     */
    private fun logCacheStats() {
        val total = cacheHits + cacheMisses
        if (total > 0 && total % 10 == 0) {  // Log every 10 operations
            val hitRate = (cacheHits.toFloat() / total * 100)
            Log.d(TAG, "Correction cache hit rate: ${"%.1f".format(hitRate)}% ($cacheHits/$total)")
        }
    }

    /**
     * Clear cache (call after document changes)
     */
    fun clearCache() {
        correctionCache.evictAll()
        cacheHits = 0
        cacheMisses = 0
        Log.d(TAG, "Correction cache cleared")
    }

    /**
     * Get cache size
     */
    fun getCacheSize(): Int = correctionCache.size()

    // =========================================================================
    // Text Range Operations
    // =========================================================================

    /**
     * Delete text range based on gesture bounding box
     *
     * Converts screen coordinates to EditText text positions and deletes the range.
     *
     * @param rect Gesture bounding box in screen coordinates
     * @param editor Target EditText to delete from
     */
    fun deleteTextRange(rect: Rect, editor: EditText) {
        Log.d(TAG, "deleteTextRange: rect=$rect")

        // Get editor position on screen
        val location = IntArray(2)
        editor.getLocationOnScreen(location)
        val editorLeft = location[0]
        val editorTop = location[1]

        // Adjust rect to editor-relative coordinates
        val adjustedRect = Rect(
            rect.left - editorLeft,
            rect.top - editorTop,
            rect.right - editorLeft,
            rect.bottom - editorTop
        )

        // Find character positions
        val (startPos, endPos) = findTextPositionsForRect(adjustedRect, editor)

        Log.d(TAG, "Deleting text range: [$startPos, $endPos)")

        if (startPos >= 0 && endPos > startPos && endPos <= editor.text?.length ?: 0) {
            editor.text?.delete(startPos, endPos)

            // Trigger partial refresh
            triggerPartialRefreshForRange(startPos, endPos, editor)
        } else {
            Log.w(TAG, "Invalid text range: start=$startPos, end=$endPos, length=${editor.text?.length}")
        }
    }

    /**
     * Find text positions corresponding to a screen rectangle
     *
     * @param rect Rectangle in editor-relative coordinates
     * @param editor The EditText
     * @return Pair of (start, end) character positions
     */
    private fun findTextPositionsForRect(rect: Rect, editor: EditText): Pair<Int, Int> {
        val text = editor.text ?: return Pair(0, 0)

        // Get line heights and positions
        val layout = editor.layout ?: return Pair(0, 0)

        val lineHeight = layout.getLineBottom(0) - layout.getLineTop(0)
        val linePadding = editor.paddingTop

        // Find line range
        val topLine = ((rect.top - linePadding).coerceAtLeast(0) / lineHeight).coerceAtMost(layout.lineCount - 1)
        val bottomLine = ((rect.bottom - linePadding).coerceAtLeast(0) / lineHeight).coerceAtMost(layout.lineCount - 1)

        // Get character positions for each line
        val startPos = layout.getLineStart(topLine)
        val endPos = layout.getLineEnd(bottomLine)

        return Pair(startPos, endPos)
    }

    /**
     * Trigger refresh for a text range
     *
     * @param start Start character position
     * @param end End character position
     * @param editor The EditText
     */
    private fun triggerPartialRefreshForRange(start: Int, end: Int, editor: EditText) {
        // For now, trigger a full refresh of the editor view
        // In the future, this could be optimized to only refresh dirty rectangles
        editor.invalidate()
    }

    /**
     * Insert text at a specific line (for INSERT gesture)
     *
     * @param line Line number (0-based)
     * @param textToInsert Text to insert
     * @param editor The EditText
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

        // Trigger refresh
        editor.invalidate()
    }
}

/**
 * Recognition result data
 */
data class RecognResultData(
    val boundingBox: Rect,
    val keyPoint: Point,
    val gestureType: GestureType,
    val text: String = ""
)

/**
 * Gesture type enum
 */
enum class GestureType {
    DELETE,
    INSERT,
    SELECT
}
