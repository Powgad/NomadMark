package com.editor.nomadmark.image

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Markdown 图片管理器
 *
 * 功能：
 * - 管理 Markdown 文档中的图片引用
 * - 清理未使用的图片
 * - 迁移旧图片到新格式
 */
class MarkdownImageManager(private val context: Context) {

    companion object {
        private const val TAG = "MarkdownImageManager"
        private const val IMAGE_DIR = "markdown_images"

        /**
         * 从 Markdown 内容中提取所有图片路径
         *
         * 支持的格式：
         * - `![alt](relative_path)`
         * - `![alt](file:///absolute_path)`
         * - `<img src="...">`
         */
        fun extractImagePaths(markdown: String): List<String> {
            val paths = mutableListOf<String>()

            // 匹配 `![alt](path)` 格式
            val imagePattern = """!\[.*?\]\(([^)]+)\)""".toRegex()
            imagePattern.findAll(markdown).forEach { match ->
                val path = match.groupValues[1]
                paths.add(path)
            }

            // 匹配 `<img src="...">` 格式
            val imgTagPattern = """<img\s+src=["']([^"']+)["']""".toRegex()
            imgTagPattern.findAll(markdown).forEach { match ->
                val path = match.groupValues[1]
                paths.add(path)
            }

            return paths
        }

        /**
         * 判断路径是否为本地图片路径
         */
        fun isLocalImagePath(path: String): Boolean {
            return when {
                path.startsWith("file://") -> true
                path.contains("markdown_images") -> true
                path.startsWith("images/") -> true
                else -> false
            }
        }

        /**
         * 将路径转换为相对路径
         *
         * 输入：`file:///data/data/com.editor.nomadmark/files/markdown_images/xxx.jpg`
         * 输出：`markdown_images/xxx.jpg`
         */
        fun toRelativePath(fullPath: String): String? {
            return when {
                fullPath.contains("markdown_images") -> {
                    // 提取 markdown_images/xxx.jpg 部分
                    val index = fullPath.indexOf("markdown_images")
                    fullPath.substring(index)
                }
                else -> null
            }
        }
    }

    /** 图片存储目录 */
    private val imageDir: File by lazy {
        File(context.filesDir, IMAGE_DIR).apply {
            if (!exists()) {
                mkdirs()
                Log.d(TAG, "创建图片目录: $absolutePath")
            }
        }
    }

    /**
     * 获取所有图片文件
     */
    fun getAllImageFiles(): List<File> {
        return imageDir.listFiles()?.toList() ?: emptyList()
    }

    /**
     * 获取图片文件总数
     */
    fun getImageCount(): Int {
        return getAllImageFiles().size
    }

    /**
     * 获取所有图片的总大小
     */
    fun getTotalImageSize(): Long {
        return getAllImageFiles().sumOf { it.length() }
    }

    /**
     * 清理未使用的图片
     *
     * @param usedPaths Markdown 中使用的图片路径列表
     * @return 删除的文件数量
     */
    fun cleanUnusedImages(usedPaths: List<String>): Int {
        val usedFiles = usedPaths.mapNotNull { path ->
            when {
                path.contains("markdown_images") -> {
                    val fileName = path.substringAfterLast("/")
                    File(imageDir, fileName)
                }
                else -> null
            }
        }.toSet()

        val allFiles = getAllImageFiles()
        var deletedCount = 0

        allFiles.forEach { file ->
            if (file !in usedFiles) {
                if (file.delete()) {
                    deletedCount++
                    Log.d(TAG, "删除未使用的图片: ${file.name}")
                }
            }
        }

        Log.d(TAG, "清理完成: 删除 $deletedCount 个未使用的图片")
        return deletedCount
    }

    /**
     * 删除指定图片
     */
    fun deleteImage(fileName: String): Boolean {
        val file = File(imageDir, fileName)
        return if (file.exists()) {
            val deleted = file.delete()
            Log.d(TAG, "删除图片: $fileName, 结果: $deleted")
            deleted
        } else {
            Log.w(TAG, "文件不存在: $fileName")
            false
        }
    }

    /**
     * 清空所有图片
     */
    fun clearAllImages(): Int {
        val files = getAllImageFiles()
        var count = 0
        files.forEach { file ->
            if (file.delete()) count++
        }
        Log.d(TAG, "清空所有图片: 删除 $count 个文件")
        return count
    }

    /**
     * 导出图片到指定目录
     *
     * @param targetDir 目标目录
     * @param fileName 文件名
     * @return 导出的文件
     */
    fun exportImage(fileName: String, targetDir: File): File? {
        val sourceFile = File(imageDir, fileName)
        if (!sourceFile.exists()) {
            Log.w(TAG, "源文件不存在: $fileName")
            return null
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val targetFile = File(targetDir, fileName)
        return try {
            sourceFile.copyTo(targetFile, overwrite = true)
            Log.d(TAG, "导出图片: $fileName -> ${targetFile.absolutePath}")
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "导出图片失败", e)
            null
        }
    }

    /**
     * 获取图片文件信息
     */
    fun getImageInfo(fileName: String): ImageInfo? {
        val file = File(imageDir, fileName)
        if (!file.exists()) {
            return null
        }

        return ImageInfo(
            name = fileName,
            size = file.length(),
            lastModified = file.lastModified(),
            path = file.absolutePath
        )
    }

    /**
     * 获取所有图片信息
     */
    fun getAllImageInfo(): List<ImageInfo> {
        return getAllImageFiles().map { file ->
            ImageInfo(
                name = file.name,
                size = file.length(),
                lastModified = file.lastModified(),
                path = file.absolutePath
            )
        }
    }

    /**
     * 图片信息数据类
     */
    data class ImageInfo(
        val name: String,
        val size: Long,
        val lastModified: Long,
        val path: String
    ) {
        /**
         * 格式化文件大小
         */
        fun getFormattedSize(): String {
            return when {
                size < 1024 -> "${size}B"
                size < 1024 * 1024 -> "${size / 1024}KB"
                else -> "${size / (1024 * 1024)}MB"
            }
        }
    }
}
