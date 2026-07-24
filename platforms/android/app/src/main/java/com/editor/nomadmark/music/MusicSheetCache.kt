package com.editor.nomadmark.music

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache

/**
 * 乐谱 Bitmap 缓存
 *
 * 使用 LRU 缓存策略，避免重复渲染
 */
object MusicSheetCache {

    private const val TAG = "MusicSheetCache"

    // LRU 缓存：最多缓存 20 张乐谱图片
    private val cache = LruCache<String, Bitmap>(20)

    // 缓存大小限制：50MB
    private val maxCacheSize = 50 * 1024 * 1024 // 50MB

    init {
        // 设置缓存大小监听
        cache.resize(maxCacheSize)
    }

    /**
     * 获取缓存的 Bitmap
     */
    fun get(key: String): Bitmap? {
        return cache.get(key)
    }

    /**
     * 缓存 Bitmap
     */
    fun put(key: String, bitmap: Bitmap) {
        // 检查原始 Bitmap 内容
        val samplePixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        Log.d(TAG, "缓存前原始 Bitmap (${bitmap.width}x${bitmap.height}) 中心像素: 0x${Integer.toHexString(samplePixel)}")

        // 复制 Bitmap 以避免原始 Bitmap 被回收
        val copied = bitmap.copy(bitmap.config, false)

        // 检查复制后的 Bitmap 内容
        val copiedSamplePixel = copied.getPixel(copied.width / 2, copied.height / 2)
        Log.d(TAG, "复制后 Bitmap (${copied.width}x${copied.height}) 中心像素: 0x${Integer.toHexString(copiedSamplePixel)}")

        cache.put(key, copied)
        Log.d(TAG, "缓存乐谱图片: $key, 当前缓存数量: ${cache.size()}")
    }

    /**
     * 清空缓存
     */
    fun clear() {
        cache.evictAll()
        Log.d(TAG, "清空缓存")
    }

    /**
     * 获取缓存大小（字节）
     */
    fun size(): Long {
        return cache.size().toLong()
    }
}
