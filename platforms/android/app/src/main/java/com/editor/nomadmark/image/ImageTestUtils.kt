package com.editor.nomadmark.image

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 图片测试工具
 *
 * 用于验证图片处理流程是否正常
 */
object ImageTestUtils {
    private const val TAG = "ImageTestUtils"

    /**
     * 检查图片目录状态
     */
    fun checkImageDirectory(context: Context): String {
        val imageDir = File(context.filesDir, "markdown_images")

        return buildString {
            appendLine("=== 图片目录检查 ===")
            appendLine("目录路径: ${imageDir.absolutePath}")
            appendLine("目录存在: ${imageDir.exists()}")

            if (imageDir.exists()) {
                appendLine("可读: ${imageDir.canRead()}")
                appendLine("可写: ${imageDir.canWrite()}")

                val files = imageDir.listFiles()
                if (files != null && files.isNotEmpty()) {
                    appendLine("文件数量: ${files.size}")
                    appendLine("文件列表:")
                    files.forEach { file ->
                        appendLine("  - ${file.name} (${file.length()} bytes)")
                        appendLine("    路径: ${file.absolutePath}")
                    }
                } else {
                    appendLine("目录为空")
                }
            }
            appendLine("===================")
        }.also {
            Log.d(TAG, it)
        }
    }

    /**
     * 创建测试图片（用于验证）
     */
    fun createTestImage(context: Context): String? {
        try {
            val imageDir = File(context.filesDir, "markdown_images")
            if (!imageDir.exists()) {
                imageDir.mkdirs()
            }

            val testFile = File(imageDir, "test_image.jpg")
            if (testFile.exists()) {
                testFile.delete()
            }

            // 创建一个简单的 1x1 像素 JPEG 文件作为测试
            // 这里我们创建一个空文件作为占位符
            testFile.createNewFile()

            Log.d(TAG, "创建测试图片: ${testFile.absolutePath}")
            return "markdown_images/test_image.jpg"
        } catch (e: Exception) {
            Log.e(TAG, "创建测试图片失败", e)
            return null
        }
    }

    /**
     * 验证图片文件是否存在
     */
    fun verifyImageFile(context: Context, relativePath: String): Boolean {
        val file = File(context.filesDir, relativePath)
        return file.exists() && file.length() > 0
    }

    /**
     * 获取图片目录
     */
    fun getImageDirectory(context: Context): File {
        return File(context.filesDir, "markdown_images")
    }
}
