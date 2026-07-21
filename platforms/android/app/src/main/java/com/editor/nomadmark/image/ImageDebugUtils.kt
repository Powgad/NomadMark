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

    /**
     * 诊断图片路径问题
     *
     * @param imagePath 图片路径（如 "11.jpg" 或 "./11.jpg"）
     * @return 诊断信息
     */
    fun diagnoseImagePath(imagePath: String): String {
        val info = buildString {
            appendLine("========== 图片路径诊断 ==========")
            appendLine("输入路径: $imagePath")

            // 检查 DocumentContextHolder 状态
            val docDir = DocumentContextHolder.getCurrentDocumentDir()
            appendLine("当前文档目录: ${docDir?.absolutePath ?: "NULL"}")
            appendLine("文档目录存在: ${docDir?.exists() ?: false}")

            if (docDir != null) {
                // 尝试解析相对路径
                val resolvedFile = DocumentContextHolder.resolveRelativePath(imagePath)
                appendLine("解析结果: ${resolvedFile?.absolutePath ?: "NULL"}")
                appendLine("文件存在: ${resolvedFile?.exists() ?: false}")

                if (resolvedFile != null && resolvedFile.exists()) {
                    appendLine("文件大小: ${resolvedFile.length()} bytes")
                    appendLine("文件可读: ${resolvedFile.canRead()}")
                } else {
                    // 列出文档目录中的文件
                    val filesInDir = docDir.listFiles()
                    if (filesInDir != null && filesInDir.isNotEmpty()) {
                        appendLine("\n文档目录中的文件:")
                        filesInDir.forEach { file ->
                            val isImg = file.extension.lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")
                            appendLine("  - ${file.name} ${if (isImg) "[图片]" else ""}")
                        }
                    } else {
                        appendLine("\n文档目录为空或无法访问")
                    }
                }
            }

            appendLine("=================================")
        }

        Log.d(TAG, info)
        return info
    }
}
