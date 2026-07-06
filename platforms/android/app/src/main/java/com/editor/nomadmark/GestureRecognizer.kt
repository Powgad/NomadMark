package com.editor.nomadmark

import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 用于修订模式的手势识别器
 *
 * 识别用于编辑操作的手写手势：
 * - DELETE：水平线条（划线删除）
 * - INSERT：插入符号 (^)
 * - SELECT：围绕文本的圆圈/环（圈选）
 *
 * 识别算法：
 * 1. 分析笔划轨迹（点序列）
 * 2. 计算线性度（线条有多直）
 * 3. 检测方向（与水平线的角度）
 * 4. 计算边界框
 * 5. 分类手势类型
 */
object GestureRecognizer {

    private const val TAG = "GestureRecognizer"

    // =========================================================================
    // 识别参数
    // =========================================================================

    /** 有效手势的最小点数 */
    const val MIN_POINTS = 5

    /** 最小手势长度（像素） */
    private const val MIN_LENGTH = 50f

    /** 最大手势长度（像素）- 防止意外整页滑动 */
    private const val MAX_LENGTH = 800f

    /** 线性度阈值：点必须在 15% 的线长偏差内 */
    private const val LINEARITY_THRESHOLD = 0.85f

    /** 水平角度容差：±30 度 */
    private const val ANGLE_TOLERANCE_DEGREES = 30f

    /** 选择手势的圆圈闭合容差 */
    private const val CIRCLE_CLOSURE_RATIO = 0.3f

    // =========================================================================
    // 主识别入口点
    // =========================================================================

    /**
     * 从点序列识别手势
     *
     * @param points 按顺序的触摸点序列
     * @return 如果手势被识别则返回 RecognResultData，否则返回 null
     */
    fun recognize(points: List<Point>): RecognResultData? {
        if (points.size < MIN_POINTS) {
            Log.d(TAG, "Too few points: ${points.size}")
            return null
        }

        val length = calculatePathLength(points)
        if (length < MIN_LENGTH) {
            Log.d(TAG, "Gesture too short: $length")
            return null
        }
        if (length > MAX_LENGTH) {
            Log.d(TAG, "Gesture too long: $length")
            return null
        }

        // 尝试识别每种手势类型
        return recognizeDeleteGesture(points)
            ?: recognizeInsertGesture(points)
            ?: recognizeSelectGesture(points)
    }

    // =========================================================================
    // DELETE 手势识别（水平线）
    // =========================================================================

    /**
     * 识别 DELETE 手势：水平线条
     *
     * 特征：
     * - 高线性度（点形成一条直线）
     * - 水平方向（角度在水平线的 ±30° 内）
     * - 长度 > MIN_LENGTH
     */
    private fun recognizeDeleteGesture(points: List<Point>): RecognResultData? {
        val linearity = calculateLinearity(points)
        if (linearity < LINEARITY_THRESHOLD) {
            Log.d(TAG, "DELETE failed: low linearity $linearity")
            return null
        }

        val angle = calculateDirectionAngle(points)
        val angleDegrees = Math.toDegrees(angle.toDouble()).toFloat()

        // 检查角度是否在水平容差范围内
        val isHorizontal = abs(angleDegrees) <= ANGLE_TOLERANCE_DEGREES ||
                           abs(abs(angleDegrees) - 180f) <= ANGLE_TOLERANCE_DEGREES

        if (!isHorizontal) {
            Log.d(TAG, "DELETE failed: not horizontal, angle=$angleDegrees°")
            return null
        }

        Log.d(TAG, "DELETE recognized: linearity=$linearity, angle=$angleDegrees°")

        val bbox = calculateBoundingBox(points)
        return RecognResultData(
            boundingBox = bbox,
            keyPoint = Point(bbox.centerX(), bbox.centerY()),
            gestureType = GestureType.DELETE
        )
    }

    // =========================================================================
    // INSERT 手势识别（插入符号）
    // =========================================================================

    /**
     * 识别 INSERT 手势：插入符号 (^)
     *
     * 特征：
     * - 两条笔划在一点汇合
     * - 从左下到上
     * - 从右下到上
     */
    private fun recognizeInsertGesture(points: List<Point>): RecognResultData? {
        // 目前，INSERT 需要一个明显的插入符号模式
        // 这是一个简化版本 - 完整实现会检测
        // 带有角度收敛的特征 "^" 形状

        // 计算方向变化（插入符号有急剧转弯）
        val directionChanges = countDirectionChanges(points)

        // 插入符号有 2-3 个方向变化（左上、右上，或反之）
        if (directionChanges < 1 || directionChanges > 3) {
            Log.d(TAG, "INSERT failed: wrong direction changes $directionChanges")
            return null
        }

        // 检查向上的趋势
        val overallDirection = calculateOverallDirection(points)
        val hasUpwardTendency = overallDirection.y < 0  // 负 Y 是向上

        if (!hasUpwardTendency) {
            Log.d(TAG, "INSERT failed: not upward")
            return null
        }

        Log.d(TAG, "INSERT recognized")

        val bbox = calculateBoundingBox(points)
        return RecognResultData(
            boundingBox = bbox,
            keyPoint = Point(bbox.centerX(), bbox.centerY()),
            gestureType = GestureType.INSERT
        )
    }

    // =========================================================================
    // SELECT 手势识别（圆圈/环）
    // =========================================================================

    /**
     * 识别 SELECT 手势：围绕文本的圆圈/环
     *
     * 特征：
     * - 闭合或近乎闭合的环
     * - 起点和终点靠得很近
     */
    private fun recognizeSelectGesture(points: List<Point>): RecognResultData? {
        if (points.size < 8) {
            // 圆圈需要更多的点
            return null
        }

        val bbox = calculateBoundingBox(points)
        val closureDistance = distance(points.first(), points.last())
        val bboxDiagonal = sqrt(bbox.width().toFloat() * bbox.width() + bbox.height().toFloat() * bbox.height())

        // 检查起点和终点是否接近（闭合环）
        val closureRatio = closureDistance / bboxDiagonal
        if (closureRatio > CIRCLE_CLOSURE_RATIO) {
            Log.d(TAG, "SELECT failed: not closed, ratio=$closureRatio")
            return null
        }

        // 检查长宽比 - 圆圈大致为正方形
        val aspectRatio = bbox.width().toFloat() / bbox.height().toFloat()
        if (aspectRatio < 0.5f || aspectRatio > 2.0f) {
            Log.d(TAG, "SELECT failed: wrong aspect ratio $aspectRatio")
            return null
        }

        Log.d(TAG, "SELECT recognized")

        return RecognResultData(
            boundingBox = bbox,
            keyPoint = Point(bbox.centerX(), bbox.centerY()),
            gestureType = GestureType.SELECT
        )
    }

    // =========================================================================
    // 辅助函数
    // =========================================================================

    /**
     * 计算总路径长度（线段长度之和）
     */
    private fun calculatePathLength(points: List<Point>): Float {
        var length = 0f
        for (i in 1 until points.size) {
            length += distance(points[i - 1], points[i])
        }
        return length
    }

    /**
     * 计算线性度分数（0.0 到 1.0）
     *
     * 分数越高 = 越线性（直线）
     * 使用最小二乘法拟合到一条线
     */
    private fun calculateLinearity(points: List<Point>): Float {
        if (points.size < 2) return 0f

        // 计算质心
        var sumX = 0
        var sumY = 0
        for (p in points) {
            sumX += p.x
            sumY += p.y
        }
        val centroidX = sumX.toFloat() / points.size
        val centroidY = sumY.toFloat() / points.size

        // 计算方向向量（从第一个到最后一个点）
        val dx = (points.last().x - points.first().x).toFloat()
        val dy = (points.last().y - points.first().y).toFloat()
        val length = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        if (length < 1f) return 0f

        // 归一化方向
        val dirX = dx / length
        val dirY = dy / length

        // 计算垂直距离
        var totalDistance = 0f
        for (p in points) {
            // 从质心到点的向量
            val vx = p.x - centroidX
            val vy = p.y - centroidY

            // 到线的垂直距离（叉积 / 长度）
            val perpDist = abs(vx * dirY - vy * dirX)
            totalDistance += perpDist
        }

        val avgDistance = totalDistance / points.size

        // 线性度分数：1 - (平均距离 / 总长度)
        return 1f - (avgDistance / length).coerceAtMost(1f)
    }

    /**
     * 计算手势的方向角度（以弧度为单位）
     *
     * 返回从正 X 轴的角度：
     * - 0° = 水平向右
     * - 90° = 垂直向下
     * - -90° = 垂直向上
     * - 180° = 水平向左
     */
    private fun calculateDirectionAngle(points: List<Point>): Float {
        val dx = points.last().x - points.first().x
        val dy = points.last().y - points.first().y
        return atan2(dy.toFloat(), dx.toFloat())
    }

    /**
     * 计算总体方向向量（归一化）
     */
    private fun calculateOverallDirection(points: List<Point>): Point {
        var totalX = 0
        var totalY = 0
        for (i in 1 until points.size) {
            totalX += points[i].x - points[i - 1].x
            totalY += points[i].y - points[i - 1].y
        }
        return Point(totalX, totalY)
    }

    /**
     * 计算笔划中的方向变化次数
     *
     * 用于检测急剧转弯（如插入符号中的）
     */
    private fun countDirectionChanges(points: List<Point>): Int {
        if (points.size < 3) return 0

        var changes = 0
        var lastAngle = 0f

        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            val angle = atan2(dy.toFloat(), dx.toFloat())

            if (i > 1) {
                val angleDiff = abs(angle - lastAngle)
                // 归一化到 [0, π]
                val normalizedDiff = if (angleDiff > Math.PI) {
                    (2 * Math.PI - angleDiff).toFloat()
                } else {
                    angleDiff
                }

                // 显著的方向变化（> 45 度）
                if (normalizedDiff > Math.PI / 4) {
                    changes++
                }
            }
            lastAngle = angle
        }

        return changes
    }

    /**
     * 计算点序列的边界框
     */
    private fun calculateBoundingBox(points: List<Point>): Rect {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE

        for (p in points) {
            minX = minOf(minX, p.x)
            minY = minOf(minY, p.y)
            maxX = maxOf(maxX, p.x)
            maxY = maxOf(maxY, p.y)
        }

        return Rect(minX, minY, maxX, maxY)
    }

    /**
     * 计算两点之间的欧几里得距离
     */
    private fun distance(p1: Point, p2: Point): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt((dx * dx + dy * dy).toFloat())
    }
}
