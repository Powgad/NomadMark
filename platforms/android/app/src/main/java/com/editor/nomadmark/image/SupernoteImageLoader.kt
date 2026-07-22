package com.editor.nomadmark.image

import android.content.Context
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.size.Precision
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import coil.map.Mapper

/**
 * Supernote 图片加载器配置
 *
 * 针对 E-ink 设备优化
 */
object SupernoteImageLoader {

    private const val TAG = "SupernoteImageLoader"
    private const val MAX_WIDTH = 1872
    private const val DISK_CACHE_MB = 200L
    private const val NETWORK_TIMEOUT = 30L

    /**
     * 初始化 Coil 图片加载器
     */
    fun init(context: Context) {
        val imageLoader = ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
                // 添加文件路径 Mapper，支持相对路径和 file:// 协议
                add(CoilFileMapper(context))
            }
            .crossfade(false)
            .allowHardware(false)
            .precision(Precision.INEXACT)
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_MB * 1024 * 1024)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(NETWORK_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(NETWORK_TIMEOUT, TimeUnit.SECONDS)
                    .writeTimeout(NETWORK_TIMEOUT, TimeUnit.SECONDS)
                    .build()
            }
            .build()

        // 使用 ImageLoaderFactory 避免重载歧义
        Coil.setImageLoader { imageLoader }

        Log.d(TAG, "图片加载器已初始化 - 目标尺寸: $MAX_WIDTH")
    }

    /**
     * 创建用于 Markwon 的 ImageRequest
     */
    fun createMarkwonRequest(context: Context, uri: String): ImageRequest {
        return ImageRequest.Builder(context)
            .data(uri)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .allowRgb565(false)  // 禁用 RGB_565 以避免图片渲染问题
            .crossfade(false)
            .build()
    }

    /**
     * 清理内存缓存
     */
    fun clearMemoryCache(context: Context) {
        Coil.imageLoader(context).memoryCache?.clear()
        Log.d(TAG, "内存缓存已清理")
    }

    /**
     * 清理磁盘缓存
     */
    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearDiskCache(context: Context) {
        Coil.imageLoader(context).diskCache?.clear()
        Log.d(TAG, "磁盘缓存已清理")
    }

    /**
     * 清理所有缓存
     */
    suspend fun clearAllCaches(context: Context) {
        clearMemoryCache(context)
        clearDiskCache(context)
    }
}
