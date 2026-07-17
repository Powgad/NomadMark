package com.editor.nomadmark.image

import android.content.Context
import android.util.Log
import coil.map.Mapper
import coil.request.Options
import java.io.File

/**
 * Coil 文件路径 Mapper
 *
 * 支持以下路径格式：
 * 1. markdown_images/xxx.jpg - 应用私有目录中的图片
 * 2. file:///完整路径/xxx.jpg - file:// 协议绝对路径
 * 3. /sdcard/Document/xxx.jpg - 直接绝对路径
 * 4. 11.jpg 或 ./11.jpg - 当前文档目录中的相对路径
 * 5. https://... - 网络图片
 */
class CoilFileMapper(private val context: Context) : Mapper<String, File> {

    companion object {
        private const val TAG = "CoilFileMapper"

        /** 支持的图片文件扩展名 */
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")

        /**
         * 判断路径是否为图片文件
         */
        private fun isImageFile(path: String): Boolean {
            val extension = path.substringAfterLast('.', "").lowercase()
            return extension in IMAGE_EXTENSIONS
        }

        /**
         * 判断路径是否为绝对路径
         * - 以 / 开头（如 /sdcard/xxx.jpg）
         * - 包含盘符（Windows 风格，Android 不常见）
         */
        private fun isAbsolutePath(path: String): Boolean {
            return path.startsWith("/") || path.contains(":\\") || path.contains(":/")
        }
    }

    override fun map(data: String, options: Options): File? {
        Log.d(TAG, "映射路径: $data")

        return when {
            // 1. 处理应用私有目录: markdown_images/xxx.jpg
            data.contains("markdown_images") -> {
                val fileName = data.substringAfterLast("/")
                val file = File(context.filesDir, "markdown_images/$fileName")
                if (file.exists()) {
                    Log.d(TAG, "找到文件 (markdown_images): ${file.absolutePath}")
                    file
                } else {
                    Log.w(TAG, "文件不存在 (markdown_images): ${file.absolutePath}")
                    null
                }
            }

            // 2. 处理 file:// 协议
            data.startsWith("file://") -> {
                val filePath = data.removePrefix("file://")
                val file = File(filePath)
                if (file.exists()) {
                    Log.d(TAG, "找到文件 (file://): ${file.absolutePath}")
                    file
                } else {
                    Log.w(TAG, "文件不存在 (file://): ${file.absolutePath}")
                    null
                }
            }

            // 3. 处理 http/https URL（跳过，让 Coil 自己处理）
            data.startsWith("http://") || data.startsWith("https://") -> {
                Log.d(TAG, "网络图片，跳过映射: $data")
                null
            }

            // 4. 处理绝对路径: /sdcard/Document/11.jpg
            isAbsolutePath(data) && isImageFile(data) -> {
                val file = File(data)
                if (file.exists()) {
                    Log.d(TAG, "找到文件 (绝对路径): ${file.absolutePath}")
                    file
                } else {
                    Log.w(TAG, "文件不存在 (绝对路径): ${file.absolutePath}")
                    null
                }
            }

            // 5. 处理同目录相对路径: 11.jpg 或 ./11.jpg
            isImageFile(data) -> {
                val file = DocumentContextHolder.resolveRelativePath(data)
                if (file != null && file.exists()) {
                    Log.d(TAG, "找到文件 (相对路径): ${file.absolutePath}")
                    file
                } else {
                    Log.w(TAG, "文件不存在 (相对路径): $data, 当前文档目录: ${DocumentContextHolder.getCurrentDocumentDir()?.absolutePath}")
                    null
                }
            }

            // 6. 其他情况返回 null，让 Coil 尝试其他方式
            else -> {
                Log.d(TAG, "未匹配的路径格式: $data")
                null
            }
        }
    }
}
