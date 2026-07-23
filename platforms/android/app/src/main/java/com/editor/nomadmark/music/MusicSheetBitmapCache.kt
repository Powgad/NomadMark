package com.editor.nomadmark.music

import android.graphics.Bitmap
import android.util.Log
import com.editor.nomadmark.markwon.MusicData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 乐谱位图缓存管理器
 *
 * 管理乐谱位图的缓存状态和渲染回调
 */
object MusicSheetBitmapCache {

    private const val TAG = "MusicSheetBitmapCache"

    /**
     * 渲染状态
     */
    enum class RenderState {
        /** 未渲染 */
        IDLE,
        /** 正在渲染 */
        RENDERING,
        /** 渲染完成 */
        COMPLETED,
        /** 渲染失败 */
        FAILED
    }

    /**
     * 缓存项
     */
    data class CacheItem(
        var bitmap: Bitmap? = null,
        var state: RenderState = RenderState.IDLE,
        var error: String? = null
    )

    /**
     * 位图缓存
     */
    private val cache = ConcurrentHashMap<String, CacheItem>()

    /**
     * 渲染回调列表
     */
    private val callbacks = ConcurrentHashMap<String, CopyOnWriteArrayList<(Bitmap?) -> Unit>>()

    /**
     * 获取缓存项
     */
    fun getCacheItem(musicData: MusicData): CacheItem? {
        return cache[musicData.id]
    }

    /**
     * 获取位图
     */
    fun getBitmap(musicData: MusicData): Bitmap? {
        return cache[musicData.id]?.bitmap
    }

    /**
     * 获取渲染状态
     */
    fun getState(musicData: MusicData): RenderState {
        return cache[musicData.id]?.state ?: RenderState.IDLE
    }

    /**
     * 开始渲染
     *
     * @return 如果应该开始渲染则返回 true，如果已在渲染或已完成则返回 false
     */
    fun startRendering(musicData: MusicData): Boolean {
        val item = cache.getOrPut(musicData.id) { CacheItem() }

        return synchronized(item) {
            when (item.state) {
                RenderState.RENDERING -> false // 已在渲染中
                RenderState.COMPLETED -> false // 已完成
                else -> {
                    item.state = RenderState.RENDERING
                    item.bitmap = null
                    item.error = null
                    true
                }
            }
        }
    }

    /**
     * 完成渲染
     */
    fun completeRendering(musicData: MusicData, bitmap: Bitmap?) {
        val item = cache[musicData.id] ?: return

        synchronized(item) {
            item.bitmap = bitmap
            item.state = if (bitmap != null) RenderState.COMPLETED else RenderState.FAILED
            item.error = if (bitmap == null) "渲染失败" else null
        }

        // 通知所有等待的回调
        notifyCallbacks(musicData.id, bitmap)

        if (bitmap != null) {
            Log.d(TAG, "渲染完成: ${musicData.title ?: musicData.id}")
        } else {
            Log.e(TAG, "渲染失败: ${musicData.title ?: musicData.id}")
        }
    }

    /**
     * 添加渲染回调
     *
     * @return 如果已有位图则立即返回，否则等待渲染完成
     */
    fun addCallback(musicData: MusicData, callback: (Bitmap?) -> Unit): Bitmap? {
        val item = cache[musicData.id]

        if (item?.state == RenderState.COMPLETED && item.bitmap != null) {
            // 已有位图，立即返回
            callback(item.bitmap)
            return item.bitmap
        }

        // 添加回调等待渲染完成
        callbacks.getOrPut(musicData.id) { CopyOnWriteArrayList() }.add(callback)
        return null
    }

    /**
     * 移除回调
     */
    fun removeCallback(musicDataId: String, callback: (Bitmap?) -> Unit) {
        callbacks[musicDataId]?.remove(callback)
    }

    /**
     * 通知所有回调
     */
    private fun notifyCallbacks(musicDataId: String, bitmap: Bitmap?) {
        val callbacksList = callbacks.remove(musicDataId) ?: return
        for (callback in callbacksList) {
            try {
                callback(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "回调执行失败", e)
            }
        }
    }

    /**
     * 清除缓存
     */
    fun clear() {
        cache.values.forEach { item ->
            item.bitmap?.recycle()
        }
        cache.clear()
        callbacks.clear()
        Log.d(TAG, "缓存已清除")
    }

    /**
     * 移除特定项
     */
    fun remove(musicDataId: String) {
        cache[musicDataId]?.bitmap?.recycle()
        cache.remove(musicDataId)
        callbacks.remove(musicDataId)
    }

    /**
     * 获取缓存大小
     */
    fun getSize(): Int = cache.size

    /**
     * 获取内存占用（估算）
     */
    fun getMemorySize(): Long {
        return cache.values.sumOf { item ->
            item.bitmap?.let { bmp -> (bmp.width * bmp.height * 2).toLong() } ?: 0L
        }
    }
}
