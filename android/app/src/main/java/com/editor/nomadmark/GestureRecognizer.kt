package com.editor.nomadmark

import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Gesture Recognizer for revision mode
 *
 * Recognizes handwritten gestures for editing operations:
 * - DELETE: Horizontal line stroke (划线删除)
 * - INSERT: Caret mark (插入符号)
 * - SELECT: Circle/loop around text (圈选)
 *
 * Recognition algorithm:
 * 1. Analyze stroke trajectory (point sequence)
 * 2. Calculate linearity (how straight the line is)
 * 3. Detect direction (angle from horizontal)
 * 4. Calculate bounding box
 * 5. Classify gesture type
 */
object GestureRecognizer {

    private const val TAG = "GestureRecognizer"

    // =========================================================================
    // Recognition Parameters
    // =========================================================================

    /** Minimum number of points for a valid gesture */
    const val MIN_POINTS = 5

    /** Minimum gesture length (pixels) */
    private const val MIN_LENGTH = 50f

    /** Maximum gesture length (pixels) - prevent accidental whole-page swipes */
    private const val MAX_LENGTH = 800f

    /** Linearity threshold: points must be within 15% of line length deviation */
    private const val LINEARITY_THRESHOLD = 0.85f

    /** Horizontal angle tolerance: ±30 degrees */
    private const val ANGLE_TOLERANCE_DEGREES = 30f

    /** Circle closure tolerance for select gesture */
    private const val CIRCLE_CLOSURE_RATIO = 0.3f

    // =========================================================================
    // Main Recognition Entry Point
    // =========================================================================

    /**
     * Recognize gesture from point sequence
     *
     * @param points Sequence of touch points (in order)
     * @return RecognResultData if gesture recognized, null otherwise
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

        // Try to recognize each gesture type
        return recognizeDeleteGesture(points)
            ?: recognizeInsertGesture(points)
            ?: recognizeSelectGesture(points)
    }

    // =========================================================================
    // DELETE Gesture Recognition (Horizontal Line)
    // =========================================================================

    /**
     * Recognize DELETE gesture: horizontal line stroke
     *
     * Characteristics:
     * - High linearity (points form a straight line)
     * - Horizontal direction (angle within ±30° of horizontal)
     * - Length > MIN_LENGTH
     */
    private fun recognizeDeleteGesture(points: List<Point>): RecognResultData? {
        val linearity = calculateLinearity(points)
        if (linearity < LINEARITY_THRESHOLD) {
            Log.d(TAG, "DELETE failed: low linearity $linearity")
            return null
        }

        val angle = calculateDirectionAngle(points)
        val angleDegrees = Math.toDegrees(angle.toDouble()).toFloat()

        // Check if angle is within horizontal tolerance
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
    // INSERT Gesture Recognition (Caret Mark)
    // =========================================================================

    /**
     * Recognize INSERT gesture: caret mark (^)
     *
     * Characteristics:
     * - Two strokes meeting at a point
     * - Bottom-Left to Top
     * - Bottom-Right to Top
     */
    private fun recognizeInsertGesture(points: List<Point>): RecognResultData? {
        // For now, INSERT requires a distinct caret pattern
        // This is a simplified version - full implementation would detect
        // the characteristic "^" shape with angle convergence

        // Count direction changes (carets have sharp turns)
        val directionChanges = countDirectionChanges(points)

        // Caret has 2-3 direction changes (up-left, up-right, or vice versa)
        if (directionChanges < 1 || directionChanges > 3) {
            Log.d(TAG, "INSERT failed: wrong direction changes $directionChanges")
            return null
        }

        // Check for upward tendency
        val overallDirection = calculateOverallDirection(points)
        val hasUpwardTendency = overallDirection.y < 0  // Negative Y is up

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
    // SELECT Gesture Recognition (Circle/Loop)
    // =========================================================================

    /**
     * Recognize SELECT gesture: circle/loop around text
     *
     * Characteristics:
     * - Closed or nearly-closed loop
     * - Start and end points are close together
     */
    private fun recognizeSelectGesture(points: List<Point>): RecognResultData? {
        if (points.size < 8) {
            // Circle needs more points
            return null
        }

        val bbox = calculateBoundingBox(points)
        val closureDistance = distance(points.first(), points.last())
        val bboxDiagonal = sqrt(bbox.width().toFloat() * bbox.width() + bbox.height().toFloat() * bbox.height())

        // Check if start and end are close (closed loop)
        val closureRatio = closureDistance / bboxDiagonal
        if (closureRatio > CIRCLE_CLOSURE_RATIO) {
            Log.d(TAG, "SELECT failed: not closed, ratio=$closureRatio")
            return null
        }

        // Check aspect ratio - circles are roughly square-ish
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
    // Helper Functions
    // =========================================================================

    /**
     * Calculate total path length (sum of segment lengths)
     */
    private fun calculatePathLength(points: List<Point>): Float {
        var length = 0f
        for (i in 1 until points.size) {
            length += distance(points[i - 1], points[i])
        }
        return length
    }

    /**
     * Calculate linearity score (0.0 to 1.0)
     *
     * Higher score = more linear (straight line)
     * Uses least squares fit to a line
     */
    private fun calculateLinearity(points: List<Point>): Float {
        if (points.size < 2) return 0f

        // Calculate centroid
        var sumX = 0
        var sumY = 0
        for (p in points) {
            sumX += p.x
            sumY += p.y
        }
        val centroidX = sumX.toFloat() / points.size
        val centroidY = sumY.toFloat() / points.size

        // Calculate direction vector (from first to last point)
        val dx = (points.last().x - points.first().x).toFloat()
        val dy = (points.last().y - points.first().y).toFloat()
        val length = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        if (length < 1f) return 0f

        // Normalize direction
        val dirX = dx / length
        val dirY = dy / length

        // Calculate perpendicular distances
        var totalDistance = 0f
        for (p in points) {
            // Vector from centroid to point
            val vx = p.x - centroidX
            val vy = p.y - centroidY

            // Perpendicular distance to line (cross product / length)
            val perpDist = abs(vx * dirY - vy * dirX)
            totalDistance += perpDist
        }

        val avgDistance = totalDistance / points.size

        // Linearity score: 1 - (avg_distance / total_length)
        return 1f - (avgDistance / length).coerceAtMost(1f)
    }

    /**
     * Calculate direction angle of gesture (in radians)
     *
     * Returns angle from positive X axis:
     * - 0° = horizontal right
     * - 90° = vertical down
     * - -90° = vertical up
     * - 180° = horizontal left
     */
    private fun calculateDirectionAngle(points: List<Point>): Float {
        val dx = points.last().x - points.first().x
        val dy = points.last().y - points.first().y
        return atan2(dy.toFloat(), dx.toFloat())
    }

    /**
     * Calculate overall direction vector (normalized)
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
     * Count direction changes in the stroke
     *
     * Used to detect sharp turns (like in caret marks)
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
                // Normalize to [0, π]
                val normalizedDiff = if (angleDiff > Math.PI) {
                    (2 * Math.PI - angleDiff).toFloat()
                } else {
                    angleDiff
                }

                // Significant direction change (> 45 degrees)
                if (normalizedDiff > Math.PI / 4) {
                    changes++
                }
            }
            lastAngle = angle
        }

        return changes
    }

    /**
     * Calculate bounding box of point sequence
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
     * Calculate Euclidean distance between two points
     */
    private fun distance(p1: Point, p2: Point): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt((dx * dx + dy * dy).toFloat())
    }
}
