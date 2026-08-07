package com.editor.nomadmark.music

import android.content.Context
import android.content.res.Configuration
import android.util.Log

/**
 * 乐谱静态 Bitmap 与音频覆盖层共用的渲染参数。
 * 两边必须保持一致，否则覆盖层无法与显示的乐谱完全重合。
 */
object MusicRenderConfig {
    private const val TAG = "MusicRenderConfig"

    /** 竖屏模式的横向缩放 */
    private const val HORIZONTAL_SCALE_PORTRAIT = 2.5f

    /** 横屏模式的横向缩放 */
    private const val HORIZONTAL_SCALE_LANDSCAPE = 3.3f

    /** 竖屏模式的纵向缩放 */
    private const val VERTICAL_SCALE_PORTRAIT = 2.5f

    /** 横屏模式的纵向缩放 */
    private const val VERTICAL_SCALE_LANDSCAPE = 3.3f

    /** 静态/音频 HTML body 内边距 */
    const val BODY_PADDING_PX = 10

    /** abcjs renderAbc 四边 padding */
    const val ABC_PADDING = 10

    /** abcjs scale（额外缩放留给 Canvas / CSS transform） */
    const val ABC_SCALE = 1.0f

    /**
     * 获取当前方向的横向缩放比例
     */
    fun getHorizontalScale(context: Context): Float {
        val landscape = isLandscape(context)
        val scale = if (landscape) {
            HORIZONTAL_SCALE_LANDSCAPE
        } else {
            HORIZONTAL_SCALE_PORTRAIT
        }
        Log.d(TAG, "getHorizontalScale: orientation=${if (landscape) "LANDSCAPE" else "PORTRAIT"}, scale=$scale")
        return scale
    }

    /**
     * 获取当前方向的纵向缩放比例
     */
    fun getVerticalScale(context: Context): Float {
        return if (isLandscape(context)) {
            VERTICAL_SCALE_LANDSCAPE
        } else {
            VERTICAL_SCALE_PORTRAIT
        }
    }

    /**
     * 判断是否为横屏
     */
    private fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * 获取当前方向的缓存键后缀
     * 用于区分横竖屏的缓存
     */
    fun getOrientationSuffix(context: Context): String {
        return if (isLandscape(context)) {
            "landscape"
        } else {
            "portrait"
        }
    }

    /**
     * abcjs staffwidth。
     *
     * 最终静态 Bitmap 宽 ≈ (staffwidth + 2×ABC_PADDING) × HORIZONTAL_SCALE，
     * 必须 ≤ [logicWidth]（预览可用宽度），否则 MusicSheetSpan 会在右侧被 View 裁切。
     */
    fun staffWidth(logicWidth: Int, context: Context): Int {
        val scale = getHorizontalScale(context)
        val targetSvgWidth = logicWidth / scale
        return (targetSvgWidth - ABC_PADDING * 2).toInt().coerceAtLeast(200)
    }

    /**
     * 由已缩放的 Bitmap 宽反推逻辑宽度（仅兜底；优先传真实 musicSheetWidth）。
     */
    fun logicWidthFromScaled(scaledWidth: Int, context: Context): Int {
        val scale = getHorizontalScale(context)
        return (scaledWidth / scale).toInt().coerceAtLeast(1)
    }
}
