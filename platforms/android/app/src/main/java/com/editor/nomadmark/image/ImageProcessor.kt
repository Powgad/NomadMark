package com.editor.nomadmark.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 图片处理器 - 针对 Supernote A6 X2 Nomad 优化
 *
 * 设计原则：
 * 1. 绝对避免 OOM：使用采样解码，最大边限制在 1872px
 * 2. 适配 300 DPI 墨水屏：直接使用物理像素，不依赖 density 换算
 * 3. 减少内存抖动：一次解码，JPEG 压缩保存
 * 4. 持久化存储：使用 app-private files 目录
 *
 * @device Supernote A6 X2 Nomad
 * @resolution 1872 × 1404 (300 DPI)
 * @ram 4 GB
 */
class ImageProcessor(private val context: Context) {

    companion object {
        /** Supernote A6 X2 Nomad 屏幕宽度（物理像素） */
        const val MAX_SCREEN_WIDTH = 1872

        /** Supernote A6 X2 Nomad 屏幕高度（物理像素） */
        const val MAX_SCREEN_HEIGHT = 1404

        /** JPEG 压缩质量（85% 平衡清晰度与体积） */
        const val JPEG_QUALITY = 85

        /** 图片存储目录名称 */
        private const val IMAGE_DIR = "markdown_images"

        /** 标签：用于日志 */
        private const val TAG = "ImageProcessor"

        /**
         * 计算内存占用（字节）
         * @param width 图片宽度（像素）
         * @param height 图片高度（像素）
         * @param config Bitmap 配置（默认 ARGB_8888 = 4 bytes/px）
         * @return 内存占用字节数
         */
        fun calculateMemoryBytes(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Long {
            val bytesPerPixel = when (config) {
                Bitmap.Config.ARGB_8888 -> 4
                Bitmap.Config.RGB_565 -> 2
                Bitmap.Config.ALPHA_8 -> 1
                else -> 4
            }
            return width.toLong() * height.toLong() * bytesPerPixel
        }

        /**
         * 格式化内存大小为可读字符串
         */
        fun formatMemorySize(bytes: Long): String {
            return when {
                bytes < 1024 -> "${bytes}B"
                bytes < 1024 * 1024 -> "${bytes / 1024}KB"
                else -> "${bytes / (1024 * 1024)}MB"
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
     * 处理图片并保存到本地存储
     *
     * 流程：
     * 1. 预读取图片原始尺寸（不解码完整 Bitmap）
     * 2. 计算采样率（inSampleSize），确保长边 <= 1872px
     * 3. 采样解码图片
     * 4. 压缩为 JPEG 保存到 app-private 目录
     * 5. 返回相对路径用于 Markdown
     *
     * @param contentUri content:// URI（来自系统选择器）
     * @return 相对路径（如 "markdown_images/xxx.jpg"），失败返回 null
     */
    fun processAndSaveImage(contentUri: Uri): String? {
        return try {
            Log.d(TAG, "开始处理图片: $contentUri")

            // Step 1: 预读取图片尺寸
            val dimensions = readImageDimensions(contentUri)
            if (dimensions == null) {
                Log.e(TAG, "无法读取图片尺寸")
                return null
            }

            val (originalWidth, originalHeight) = dimensions
            val originalMemory = calculateMemoryBytes(originalWidth, originalHeight)
            Log.d(TAG, "原始尺寸: ${originalWidth}×${originalHeight}, 内存: ${formatMemorySize(originalMemory)}")

            // Step 2: 计算采样率
            val inSampleSize = calculateInSampleSize(originalWidth, originalHeight, MAX_SCREEN_WIDTH)
            Log.d(TAG, "采样率 inSampleSize: $inSampleSize")

            // Step 3: 采样解码
            val sampledSize = Pair(
                originalWidth / inSampleSize,
                originalHeight / inSampleSize
            )
            val sampledMemory = calculateMemoryBytes(sampledSize.first, sampledSize.second)
            Log.d(TAG, "采样后尺寸: ${sampledSize.first}×${sampledSize.second}, 内存: ${formatMemorySize(sampledMemory)}")

            val bitmap = decodeSampledBitmapFromUri(contentUri, inSampleSize)
            if (bitmap == null) {
                Log.e(TAG, "解码失败")
                return null
            }

            // Step 4: 保存为 JPEG
            val relativePath = saveBitmapAsJpeg(bitmap)

            // 回收 Bitmap
            bitmap.recycle()

            Log.d(TAG, "图片处理完成: $relativePath")
            relativePath

        } catch (e: Exception) {
            Log.e(TAG, "处理图片时出错: ${e.message}", e)
            null
        }
    }

    /**
     * 预读取图片尺寸（不解码完整 Bitmap）
     *
     * 使用 inJustDecodeBounds = true 仅读取尺寸信息，避免 OOM
     *
     * @param contentUri content:// URI
     * @return Pair<宽度, 高度>，失败返回 null
     */
    fun readImageDimensions(contentUri: Uri): Pair<Int, Int>? {
        return try {
            val inputStream = context.contentResolver.openInputStream(contentUri) ?: return null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true  // 仅读取边界，不解码像素
            }

            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.e(TAG, "无效的图片尺寸: ${options.outWidth}×${options.outHeight}")
                return null
            }

            Pair(options.outWidth, options.outHeight)

        } catch (e: Exception) {
            Log.e(TAG, "读取图片尺寸失败: ${e.message}", e)
            null
        }
    }

    /**
     * 计算采样率（inSampleSize）
     *
     * 确保解码后的图片长边不超过目标尺寸（1872px）
     *
     * @param width 原始宽度
     * @param height 原始高度
     * @param targetSize 目标最大边长（像素）
     * @return inSampleSize（必须是 2 的幂次方）
     */
    fun calculateInSampleSize(width: Int, height: Int, targetSize: Int = MAX_SCREEN_WIDTH): Int {
        var inSampleSize = 1

        val maxEdge = maxOf(width, height)

        if (maxEdge > targetSize) {
            val halfMax = maxEdge / 2
            while (halfMax / inSampleSize > targetSize) {
                inSampleSize *= 2
            }
        }

        // 确保是 2 的幂次方
        inSampleSize = Integer.highestOneBit(inSampleSize)

        // 至少为 1
        return maxOf(1, inSampleSize)
    }

    /**
     * 采样解码图片
     *
     * @param contentUri content:// URI
     * @param requestedInSampleSize 请求的采样率
     * @return Bitmap，失败返回 null
     */
    fun decodeSampledBitmapFromUri(contentUri: Uri, requestedInSampleSize: Int): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(contentUri) ?: return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = requestedInSampleSize
                // 使用 RGB_565 进一步减少内存占用（E-ink 对色彩不敏感）
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            if (bitmap != null) {
                Log.d(TAG, "解码成功: ${bitmap.width}×${bitmap.height}, 配置: ${bitmap.config}")
            } else {
                Log.e(TAG, "解码失败")
            }

            bitmap

        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM: 尝试使用更高采样率重试")
            // 尝试使用更高的采样率重试
            try {
                val retrySampleSize = requestedInSampleSize * 2
                val inputStream = context.contentResolver.openInputStream(contentUri) ?: return null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = retrySampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()
                Log.d(TAG, "重试解码成功 (采样率 $retrySampleSize)")
                bitmap
            } catch (e2: Exception) {
                Log.e(TAG, "重试失败", e2)
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "解码失败", e)
            null
        }
    }

    /**
     * 将 Bitmap 保存为 JPEG 到 app-private 目录
     *
     * @param bitmap 要保存的 Bitmap
     * @return 相对路径（如 "markdown_images/xxx.jpg"）
     */
    fun saveBitmapAsJpeg(bitmap: Bitmap): String {
        // 生成唯一文件名
        val fileName = "${UUID.randomUUID()}.jpg"
        val file = File(imageDir, fileName)

        // 保存为 JPEG（85% 质量）
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }

        // 计算文件大小
        val fileSizeKB = file.length() / 1024
        Log.d(TAG, "保存图片: $fileName, 大小: ${fileSizeKB}KB")

        return "$IMAGE_DIR/$fileName"
    }

    /**
     * 获取图片的完整文件路径
     *
     * @param relativePath 相对路径（如 "markdown_images/xxx.jpg"）
     * @return 完整文件路径（file:// 格式）
     */
    fun getFullPath(relativePath: String): String {
        return File(context.filesDir, relativePath).absolutePath
    }

    /**
     * 删除指定的图片文件
     *
     * @param relativePath 相对路径
     * @return 是否成功删除
     */
    fun deleteImage(relativePath: String): Boolean {
        val file = File(context.filesDir, relativePath)
        return if (file.exists()) {
            val deleted = file.delete()
            Log.d(TAG, "删除图片: $relativePath, 结果: $deleted")
            deleted
        } else {
            Log.w(TAG, "文件不存在: $relativePath")
            false
        }
    }

    /**
     * 清理所有图片文件（慎用）
     *
     * @return 删除的文件数量
     */
    fun clearAllImages(): Int {
        val files = imageDir.listFiles()?.toList() ?: emptyList()
        var count = 0
        files.forEach { file ->
            if (file.delete()) count++
        }
        Log.d(TAG, "清理所有图片: 删除 $count 个文件")
        return count
    }

    /**
     * 获取所有图片文件的总大小
     */
    fun getTotalImageSize(): Long {
        val files = imageDir.listFiles()?.toList() ?: emptyList()
        return files.sumOf { it.length() }
    }
}

/**
 * 内存占用对比数据
 *
 * 示例：手机拍摄的照片（4000×3000）
 *
 * 原始解码（ARGB_8888）:
 * - 内存: 4000 × 3000 × 4 = 48,000,000 字节 ≈ 45.8 MB
 *
 * RGB_565 + 采样率 2:
 * - 尺寸: 2000 × 1500
 * - 内存: 2000 × 1500 × 2 = 6,000,000 字节 ≈ 5.7 MB
 *
 * RGB_565 + 采样率 4（适配 1872px）:
 * - 尺寸: 1000 × 750
 * - 内存: 1000 × 750 × 2 = 1,500,000 字节 ≈ 1.4 MB
 *
 * JPEG 85% 压缩后文件大小: 约 100-300 KB
 */
