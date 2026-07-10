package com.editor.nomadmark.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.editor.nomadmark.MarkdownCore
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 渲染命令执行器
 *
 * 职责：
 * - 解析 Rust Core 返回的渲染命令数组
 * - 在 Android Canvas 上执行渲染命令
 * - 支持的命令类型：DrawText, FillRect, DrawLine
 *
 * @see MarkdownCore.nativeLoadRange
 */
class RenderCommandExecutor {

    companion object {
        /** 渲染命令类型枚举 */
        private const val CMD_DRAW_TEXT = 0
        private const val CMD_FILL_RECT = 1
        private const val CMD_DRAW_LINE = 2
        private const val CMD_DRAW_IMAGE = 3

        /** 渲染命令结构大小 (字节) */
        private const val COMMAND_SIZE = 48

        /** 颜色组件 */
        private const val COLOR_MASK = 0xFF
    }

    // =========================================================================
    // Paint 对象池（避免在绘制时分配）
    // =========================================================================

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        textSize = 32f
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    // =========================================================================
    // 临时缓冲区（复用以减少分配）
    // =========================================================================

    private val tempRect = RectF()

    // =========================================================================
    // 调试统计
    // =========================================================================

    private var cmdCount = 0
    private var textCmdCount = 0
    private var fillCmdCount = 0
    private var lineCmdCount = 0

    // =========================================================================
    // 公开 API
    // =========================================================================

    /**
     * 执行渲染命令数组
     *
     * @param commandsPtr Rust 分配的命令数组指针
     * @param count 命令数量
     * @param canvas 目标 Canvas
     */
    fun execute(commandsPtr: Long, count: Int, canvas: Canvas) {
        if (commandsPtr == 0L || count <= 0) {
            android.util.Log.w("RenderCommandExecutor", "No commands to execute: ptr=$commandsPtr, count=$count")
            return
        }

        // 重置统计
        cmdCount = 0
        textCmdCount = 0
        fillCmdCount = 0
        lineCmdCount = 0

        android.util.Log.d("RenderCommandExecutor", "Executing $count commands")

        // 使用 DirectByteBuffer 直接在 Rust 内存上操作（零拷贝）
        val buffer = createDirectBuffer(commandsPtr, count * COMMAND_SIZE)
            ?: run {
                android.util.Log.e("RenderCommandExecutor", "Failed to create direct buffer")
                return
            }

        repeat(count) {
            executeCommand(buffer, canvas)
            cmdCount++
        }

        android.util.Log.d("RenderCommandExecutor", "Command stats: total=$cmdCount, text=$textCmdCount, fill=$fillCmdCount, line=$lineCmdCount")
    }

    /**
     * 执行单个渲染命令
     */
    private fun executeCommand(buffer: ByteBuffer, canvas: Canvas) {
        // 读取命令类型
        val cmdType = buffer.int

        // 读取位置和尺寸
        val x = buffer.float
        val y = buffer.float
        val width = buffer.float
        val height = buffer.float

        // 读取颜色
        val color = buffer.int

        // 根据命令类型执行
        when (cmdType) {
            CMD_DRAW_TEXT -> {
                textCmdCount++
                // 读取 data 区域 (24 字节)
                val textPtr = buffer.long
                val textLen = buffer.int
                buffer.getInt() // 跳过 4 字节填充
                val fontSizePt = buffer.get().toInt()
                // 跳过剩余字节
                buffer.position(buffer.position() + 15)
                executeDrawText(textPtr, textLen, x, y, fontSizePt, color, canvas)
            }
            CMD_FILL_RECT -> {
                fillCmdCount++
                // 跳过 data 区域 (24 字节)
                buffer.position(buffer.position() + 24)
                executeFillRect(x, y, width, height, color, canvas)
            }
            CMD_DRAW_LINE -> {
                lineCmdCount++
                // 读取 data.line 区域 (24 字节)
                val lineWidth = buffer.float
                val x1 = buffer.float
                val y1 = buffer.float
                val x2 = buffer.float
                val y2 = buffer.float
                buffer.getInt() // 跳过 padding
                android.util.Log.d("RenderCommandExecutor", "DrawLine: x1=$x1, y1=$y1, x2=$x2, y2=$y2, width=$lineWidth, color=0x${color.toString(16)}")
                executeDrawLine(x1, y1, x2, y2, lineWidth, color, canvas)
            }
            else -> {
                android.util.Log.w("RenderCommandExecutor", "Unknown command type: $cmdType")
                // 跳过未知命令的 data 区域
                buffer.position(buffer.position() + 24)
            }
        }
    }

    /**
     * 执行绘制文本命令
     */
    private fun executeDrawText(textPtr: Long, textLen: Int, x: Float, y: Float, fontSizePt: Int, color: Int, canvas: Canvas) {
        // 从 Rust 内存读取文本
        val text = readTextFromRust(textPtr, textLen)

        // 设置字体和颜色
        textPaint.color = color
        textPaint.textSize = (fontSizePt * 300 / 72).toFloat() // pt to px at 300 DPI

        // 绘制文本
        canvas.drawText(text, x, y, textPaint)
    }

    /**
     * 执行填充矩形命令
     */
    private fun executeFillRect(x: Float, y: Float, width: Float, height: Float, color: Int, canvas: Canvas) {
        fillPaint.color = color
        tempRect.set(x, y, x + width, y + height)
        canvas.drawRect(tempRect, fillPaint)
    }

    /**
     * 执行绘制线条命令
     */
    private fun executeDrawLine(x1: Float, y1: Float, x2: Float, y2: Float, lineWidth: Float, color: Int, canvas: Canvas) {
        linePaint.color = color
        linePaint.strokeWidth = lineWidth

        // 绘制线条
        canvas.drawLine(x1, y1, x2, y2, linePaint)
    }

    /**
     * 从 Rust 分配的内存读取文本
     *
     * 注意：此方法仍需复制文本内容，因为 String 是 Java 对象
     */
    private fun readTextFromRust(ptr: Long, len: Int): String {
        if (ptr == 0L || len <= 0) return ""

        return try {
            val bytes = MarkdownCore.nativeReadBytes(ptr, len)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("RenderCommandExecutor", "Failed to read text from Rust", e)
            ""
        }
    }

    /**
     * 创建指向 Rust 内存的 DirectByteBuffer（零拷贝）
     */
    private fun createDirectBuffer(ptr: Long, size: Int): ByteBuffer? {
        return MarkdownCore.nativeCreateDirectByteBuffer(ptr, size)?.order(ByteOrder.nativeOrder())
    }

    // =========================================================================
    // 资源管理
    // =========================================================================

    /**
     * 重置所有 Paint 到默认状态
     */
    fun reset() {
        textPaint.reset()
        textPaint.color = 0xFF000000.toInt()
        textPaint.isAntiAlias = true

        fillPaint.reset()
        fillPaint.style = Paint.Style.FILL

        linePaint.reset()
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeCap = Paint.Cap.ROUND
    }
}
