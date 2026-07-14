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
import android.view.InputDevice
import android.view.KeyEvent
import kotlin.math.abs

/**
 * 用于修订模式的手势覆盖视图
 *
 * 一个捕获触摸输入用于手势识别的透明覆盖层。
 * 在修订模式激活时显示在编辑区域上方。
 *
 * 功能：
 * - 捕获触摸事件并记录笔划轨迹
 * - 视觉反馈（绘制手势笔划）
 * - 将事件传递给 GestureRecognizer
 * - 为识别的手势提供回调
 */
class GestureOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "GestureOverlayView"

        /** 视为移动的最小距离（避免噪点） */
        private const val MIN_MOVE_DISTANCE = 5  // pixels

        /** 手势完成超时（毫秒）- 如果在此时间内没有触摸，则结束手势 */
        private const val GESTURE_TIMEOUT_MS = 500L

        /** 手势反馈的笔划颜色 */
        private const val STROKE_COLOR = 0xFF0000FF.toInt()  // Red

        /** 手势反馈的笔划宽度 */
        private const val STROKE_WIDTH = 4f
    }

    // =========================================================================
    // 手势识别回调
    // =========================================================================

    /**
     * 当手势被识别时调用的回调
     *
     * @param result 识别到的手势数据
     */
    var onGestureRecognized: ((RecognResultData) -> Unit)? = null

    /**
     * 当手势识别失败时调用的回调
     */
    var onGestureRejected: (() -> Unit)? = null

    // =========================================================================
    // 状态
    // =========================================================================

    /** 是否启用手势识别 */
    var isGestureEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                // 禁用时清除所有内容
                clearCurrentGesture()
                invalidate()
            }
            // 更新 clickable 和 focusable 以确保触摸事件传递
            isClickable = value
            isFocusable = value
        }

    /** 当前记录的手势点 */
    private val currentGesturePoints = mutableListOf<Point>()

    /** 上一个记录的触摸点（用于过滤小幅度移动） */
    private var lastPoint: Point? = null

    /** 是否当前在手势中（触摸按下） */
    private var isInGesture = false

    /** 手势超时的 Runnable */
    private val gestureTimeoutRunnable = Runnable {
        if (isInGesture) {
            Log.d(TAG, "Gesture timeout, ending gesture")
            endGesture()
        }
    }

    // =========================================================================
    // 绘制
    // =========================================================================

    /** 用于绘制手势笔划的画笔 */
    private val strokePaint = Paint().apply {
        color = STROKE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 180  // 半透明
    }

    /** 当前手势笔划的路径 */
    private val strokePath = Path()

    // =========================================================================
    // 触摸事件处理
    // =========================================================================

    /**
     * 处理通用运动事件（例如鼠标滚轮事件）
     * 这确保了当手势识别被禁用时，鼠标滚轮事件能够传递到底层视图。
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // 处理鼠标滚轮事件
        if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0) {
            when (event.action) {
                MotionEvent.ACTION_SCROLL -> {
                    // 鼠标滚轮事件 - 当手势被禁用时传递到底层视图
                    if (!isGestureEnabled) {
                        Log.d(TAG, "Passing through mouse wheel event")
                        return false  // 传递到底层视图
                    }
                }
            }
        }

        return super.onGenericMotionEvent(event)
    }

    // 手势识别视图不是可点击视图，不需要 performClick
    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isGestureEnabled) {
            return false  // 传递到底层视图
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleActionDown(event)
                return true  // 消费事件
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
     * 处理触摸按下 - 开始新手势
     */
    private fun handleActionDown(event: MotionEvent) {
        Log.d(TAG, "ACTION_DOWN: x=${event.x}, y=${event.y}")

        // 清除任何先前的手势
        clearCurrentGesture()

        // 开始新手势
        isInGesture = true

        // 记录第一个点
        val point = Point(event.x.toInt(), event.y.toInt())
        currentGesturePoints.add(point)
        lastPoint = point

        // 开始路径
        strokePath.reset()
        strokePath.moveTo(event.x, event.y)

        // 安排超时
        removeCallbacks(gestureTimeoutRunnable)
        postDelayed(gestureTimeoutRunnable, GESTURE_TIMEOUT_MS)

        // 触发重绘
        invalidate()
    }

    /**
     * 处理触摸移动 - 记录手势路径
     */
    private fun handleActionMove(event: MotionEvent) {
        if (!isInGesture) return

        val newPoint = Point(event.x.toInt(), event.y.toInt())

        // 过滤小幅度移动
        if (lastPoint != null) {
            val distance = distance(lastPoint!!, newPoint)
            if (distance < MIN_MOVE_DISTANCE) {
                return  // 跳过微小的移动
            }
        }

        // 记录点
        currentGesturePoints.add(newPoint)
        lastPoint = newPoint

        // 更新路径
        strokePath.lineTo(event.x, event.y)

        // 重置超时
        removeCallbacks(gestureTimeoutRunnable)
        postDelayed(gestureTimeoutRunnable, GESTURE_TIMEOUT_MS)

        // 触发重绘
        invalidate()

        Log.v(TAG, "ACTION_MOVE: points=${currentGesturePoints.size}")
    }

    /**
     * 处理触摸抬起 - 识别手势
     */
    private fun handleActionUp(event: MotionEvent) {
        Log.d(TAG, "ACTION_UP: points=${currentGesturePoints.size}")

        // 取消超时
        removeCallbacks(gestureTimeoutRunnable)

        // 结束手势并识别
        endGesture()
    }

    /**
     * 处理触摸取消 - 丢弃手势
     */
    private fun handleActionCancel() {
        Log.d(TAG, "ACTION_CANCEL")

        // 取消超时
        removeCallbacks(gestureTimeoutRunnable)

        // 清除手势
        clearCurrentGesture()
        invalidate()
    }

    /**
     * 结束当前手势并触发识别
     */
    private fun endGesture() {
        if (!isInGesture) return
        isInGesture = false

        Log.d(TAG, "Ending gesture with ${currentGesturePoints.size} points")

        if (currentGesturePoints.size >= GestureRecognizer.MIN_POINTS) {
            // 尝试识别
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

        // 清除手势（延迟以提供视觉反馈）
        postDelayed({
            clearCurrentGesture()
            invalidate()
        }, 300)  // 识别后保持可见 300ms
    }

    /**
     * 清除当前手势数据
     */
    private fun clearCurrentGesture() {
        currentGesturePoints.clear()
        lastPoint = null
        strokePath.reset()
        isInGesture = false
    }

    // =========================================================================
    // 渲染
    // =========================================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制当前手势笔划
        if (currentGesturePoints.size > 1) {
            canvas.drawPath(strokePath, strokePaint)
        }
    }

    // =========================================================================
    // 辅助函数
    // =========================================================================

    /**
     * 计算两点之间的欧几里得距离
     */
    private fun distance(p1: Point, p2: Point): Float {
        val dx = (p2.x - p1.x).toFloat()
        val dy = (p2.y - p1.y).toFloat()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * 当视图分离时清除超时
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(gestureTimeoutRunnable)
    }

    // =========================================================================
    // 按键事件处理
    // =========================================================================

    /**
     * 分发按键事件
     *
     * 确保所有按键事件都传递到底层视图，而不是被手势层拦截。
     * 这使得即使在修订模式下（手势层可见），外接键盘也能正常工作。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 始终返回 false，表示我们不消费这个事件
        // 事件会继续传递到底层的 EditText
        Log.v(TAG, "Dispatching key event: ${event.keyCode}, action=${event.action}")
        return false
    }
}
