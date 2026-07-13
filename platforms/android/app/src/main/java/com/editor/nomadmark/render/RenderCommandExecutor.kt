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

        android.util.Log.d("RenderCommandExecutor", "Command stats: total=$cmdCount, text=$textCmdCount, fill=$fillCmdCount")
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

        // 读取颜色（Rust Color 结构体是 RGBA，需要转换为 Android 的 ARGB）
        val color = readColorFromBuffer(buffer)

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
     * 从缓冲区读取颜色值
     *
     * Rust 的 Color 结构体内存布局是 RGBA (r, g, b, a)，需要转换为 Android 的 ARGB 格式。
     * 由于字节序问题，需要逐字节读取并重新组合。
     */
    private fun readColorFromBuffer(buffer: ByteBuffer): Int {
        // 读取 4 个字节 (r, g, b, a)
        val r = buffer.get().toInt() and 0xFF
        val g = buffer.get().toInt() and 0xFF
        val b = buffer.get().toInt() and 0xFF
        val a = buffer.get().toInt() and 0xFF

        // 组合为 Android 的 ARGB 格式 (0xAARRGGBB)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
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
    }
}
