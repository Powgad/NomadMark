package com.editor.nomadmark.music

/**
 * 乐谱静态 Bitmap 与音频覆盖层共用的渲染参数。
 * 两边必须保持一致，否则覆盖层无法与显示的乐谱完全重合。
 */
object MusicRenderConfig {
    /** 与 WebViewMusicRenderer.createPictureFromSvg 一致的横向缩放 */
    const val HORIZONTAL_SCALE = 1.1f

    /** 与 WebViewMusicRenderer.createPictureFromSvg 一致的纵向缩放 */
    const val VERTICAL_SCALE = 1.8f

    /** 静态/音频 HTML body 内边距 */
    const val BODY_PADDING_PX = 10

    /** abcjs renderAbc 四边 padding */
    const val ABC_PADDING = 10

    /** abcjs scale（额外缩放留给 Canvas / CSS transform） */
    const val ABC_SCALE = 1.0

    /**
     * abcjs staffwidth。
     *
     * 最终静态 Bitmap 宽 ≈ (staffwidth + 2×ABC_PADDING) × HORIZONTAL_SCALE，
     * 必须 ≤ [logicWidth]（预览可用宽度），否则 MusicSheetSpan 会在右侧被 View 裁切。
     */
    fun staffWidth(logicWidth: Int): Int {
        val targetSvgWidth = logicWidth / HORIZONTAL_SCALE
        return (targetSvgWidth - ABC_PADDING * 2).toInt().coerceAtLeast(200)
    }

    /**
     * 由已缩放的 Bitmap 宽反推逻辑宽度（仅兜底；优先传真实 musicSheetWidth）。
     */
    fun logicWidthFromScaled(scaledWidth: Int): Int =
        (scaledWidth / HORIZONTAL_SCALE).toInt().coerceAtLeast(1)
}
