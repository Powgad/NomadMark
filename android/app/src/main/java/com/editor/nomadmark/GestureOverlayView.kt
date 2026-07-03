package com.editor.nomadmark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Gesture Overlay View for revision mode
 *
 * A transparent overlay that captures touch input for gesture recognition.
 * Appears on top of the editing area when revision mode is active.
 *
 * Features:
 * - Captures touch events and records stroke trajectory
 * - Visual feedback (draws gesture stroke)
 * - Passes events to GestureRecognizer
 * - Callback for recognized gestures
 */
class GestureOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "GestureOverlayView"

        /** Minimum distance to consider as movement (avoid noise) */
        private const val MIN_MOVE_DISTANCE = 5  // pixels

        /** Timeout for gesture completion (ms) - if no touch for this long, end gesture */
        private const val GESTURE_TIMEOUT_MS = 500L

        /** Stroke color for gesture feedback */
        private const val STROKE_COLOR = 0xFF0000FF.toInt()  // Red

        /** Stroke width for gesture feedback */
        private const val STROKE_WIDTH = 4f
    }

    // =========================================================================
    // Gesture Recognition Callback
    // =========================================================================

    /**
     * Callback invoked when a gesture is recognized
     *
     * @param result The recognized gesture data
     */
    var onGestureRecognized: ((RecognResultData) -> Unit)? = null

    /**
     * Callback invoked when gesture recognition fails
     */
    var onGestureRejected: (() -> Unit)? = null

    // =========================================================================
    // State
    // =========================================================================

    /** Whether gesture recognition is enabled */
    var isGestureEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                // Clear everything when disabled
                clearCurrentGesture()
                invalidate()
            }
            // Update clickable and focusable to ensure touch events pass through
            isClickable = value
            isFocusable = value
        }

    /** Currently recorded gesture points */
    private val currentGesturePoints = mutableListOf<Point>()

    /** Last recorded touch point (for filtering small movements) */
    private var lastPoint: Point? = null

    /** Whether currently in a gesture (touch down) */
    private var isInGesture = false

    /** Runnable for gesture timeout */
    private val gestureTimeoutRunnable = Runnable {
        if (isInGesture) {
            Log.d(TAG, "Gesture timeout, ending gesture")
            endGesture()
        }
    }

    // =========================================================================
    // Drawing
    // =========================================================================

    /** Paint for drawing gesture stroke */
    private val strokePaint = Paint().apply {
        color = STROKE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 180  // Semi-transparent
    }

    /** Path for current gesture stroke */
    private val strokePath = Path()

    // =========================================================================
    // Touch Event Handling
    // =========================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isGestureEnabled) {
            return false  // Pass through to underlying views
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleActionDown(event)
                return true  // Consume event
            }

            MotionEvent.ACTION_MOVE -> {
                handleActionMove(event)
                return true
            }

            MotionEvent.ACTION_UP -> {
                handleActionUp(event)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handleActionCancel()
                return true
            }
        }

        return false
    }

    /**
     * Handle touch down - start new gesture
     */
    private fun handleActionDown(event: MotionEvent) {
        Log.d(TAG, "ACTION_DOWN: x=${event.x}, y=${event.y}")

        // Clear any previous gesture
        clearCurrentGesture()

        // Start new gesture
        isInGesture = true

        // Record first point
        val point = Point(event.x.toInt(), event.y.toInt())
        currentGesturePoints.add(point)
        lastPoint = point

        // Start path
        strokePath.reset()
        strokePath.moveTo(event.x, event.y)

        // Schedule timeout
        removeCallbacks(gestureTimeoutRunnable)
        postDelayed(gestureTimeoutRunnable, GESTURE_TIMEOUT_MS)

        // Trigger redraw
        invalidate()
    }

    /**
     * Handle touch move - record gesture path
     */
    private fun handleActionMove(event: MotionEvent) {
        if (!isInGesture) return

        val newPoint = Point(event.x.toInt(), event.y.toInt())

        // Filter small movements
        if (lastPoint != null) {
            val distance = distance(lastPoint!!, newPoint)
            if (distance < MIN_MOVE_DISTANCE) {
                return  // Skip tiny movements
            }
        }

        // Record point
        currentGesturePoints.add(newPoint)
        lastPoint = newPoint

        // Update path
        strokePath.lineTo(event.x, event.y)

        // Reset timeout
        removeCallbacks(gestureTimeoutRunnable)
        postDelayed(gestureTimeoutRunnable, GESTURE_TIMEOUT_MS)

        // Trigger redraw
        invalidate()

        Log.v(TAG, "ACTION_MOVE: points=${currentGesturePoints.size}")
    }

    /**
     * Handle touch up - recognize gesture
     */
    private fun handleActionUp(event: MotionEvent) {
        Log.d(TAG, "ACTION_UP: points=${currentGesturePoints.size}")

        // Cancel timeout
        removeCallbacks(gestureTimeoutRunnable)

        // End gesture and recognize
        endGesture()
    }

    /**
     * Handle touch cancel - discard gesture
     */
    private fun handleActionCancel() {
        Log.d(TAG, "ACTION_CANCEL")

        // Cancel timeout
        removeCallbacks(gestureTimeoutRunnable)

        // Clear gesture
        clearCurrentGesture()
        invalidate()
    }

    /**
     * End current gesture and trigger recognition
     */
    private fun endGesture() {
        if (!isInGesture) return
        isInGesture = false

        Log.d(TAG, "Ending gesture with ${currentGesturePoints.size} points")

        if (currentGesturePoints.size >= GestureRecognizer.MIN_POINTS) {
            // Attempt recognition
            val result = GestureRecognizer.recognize(currentGesturePoints)

            if (result != null) {
                Log.d(TAG, "Gesture recognized: ${result.gestureType}")
                onGestureRecognized?.invoke(result)
            } else {
                Log.d(TAG, "Gesture not recognized")
                onGestureRejected?.invoke()
            }
        } else {
            Log.d(TAG, "Too few points for recognition")
            onGestureRejected?.invoke()
        }

        // Clear gesture (with a delay for visual feedback)
        postDelayed({
            clearCurrentGesture()
            invalidate()
        }, 300)  // Keep visible for 300ms after recognition
    }

    /**
     * Clear current gesture data
     */
    private fun clearCurrentGesture() {
        currentGesturePoints.clear()
        lastPoint = null
        strokePath.reset()
        isInGesture = false
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw current gesture stroke
        if (currentGesturePoints.size > 1) {
            canvas.drawPath(strokePath, strokePaint)
        }
    }

    // =========================================================================
    // Helper Functions
    // =========================================================================

    /**
     * Calculate Euclidean distance between two points
     */
    private fun distance(p1: Point, p2: Point): Float {
        val dx = (p2.x - p1.x).toFloat()
        val dy = (p2.y - p1.y).toFloat()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Clear timeout when view is detached
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(gestureTimeoutRunnable)
    }
}
