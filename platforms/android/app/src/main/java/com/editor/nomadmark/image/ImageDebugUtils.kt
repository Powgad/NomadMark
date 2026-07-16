package com.editor.nomadmark.image

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 图片调试工具
 */
object ImageDebugUtils {
    private const val TAG = "ImageDebugUtils"

    /**
     * 检查图片目录状态
     */
    fun checkImageDirectory(context: Context): String {
        val imageDir = File(context.filesDir, "markdown_images")
        val info = buildString {
            append("图片目录: ${imageDir.absolutePath}\n")
            append("目录存在: ${imageDir.exists()}\n")
            append("可读: ${imageDir.canRead()}\n")
            append("可写: ${imageDir.canWrite()}\n")

            if (imageDir.exists()) {
                val files = imageDir.listFiles()
                if (files != null && files.isNotEmpty()) {
                    append("\n包含 ${files.size} 个文件:\n")
                    files.forEach { file ->
                        append("  - ${file.name} (${file.length()} bytes)\n")
                    }
                } else {
                    append("\n目录为空\n")
                }
            }
        }

        Log.d(TAG, info)
        return info
    }

    /**
     * 测试图片处理流程
     */
    fun testImageProcessing(context: Context) {
        Log.d(TAG, "========== 图片处理调试信息 ==========")

        // 检查目录
        checkImageDirectory(context)

        // 检查 Coil 初始化
        try {
            val imageLoader = coil.Coil.imageLoader(context)
            Log.d(TAG, "Coil ImageLoader: $imageLoader")
        } catch (e: Exception) {
            Log.e(TAG, "Coil 未初始化", e)
        }

        Log.d(TAG, "=========================================")
    }

    /**
     * 获取图片目录
     */
    fun getImageDirectory(context: Context): File {
        return File(context.filesDir, "markdown_images")
    }

    /**
     * 创建测试图片目录（如果不存在）
     */
    fun ensureImageDirectory(context: Context): File {
        val dir = getImageDirectory(context)
        if (!dir.exists()) {
            dir.mkdirs()
            Log.d(TAG, "创建图片目录: ${dir.absolutePath}")
        }
        return dir
    }
}
