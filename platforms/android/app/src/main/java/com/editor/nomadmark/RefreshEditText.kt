package com.editor.nomadmark

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/**
 * 支持 E-ink 刷新的 EditText
 *
 * 在文本选择变化时自动触发墨水屏刷新。
 */
class RefreshEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    companion object {
        /** 是否启用选择变化刷新 */
        var enableSelectionRefresh = true
    }

    /** 刷新处理器 */
    private val refreshHandler = Handler(Looper.getMainLooper())

    /** 刷新 Runnable */
    private val refreshRunnable = Runnable {
        invalidate()
        einkRefreshController?.forceGlobalRefresh()
    }

    /** E-ink 刷新控制器（可选） */
    var einkRefreshController: EinkRefreshController? = null

    /** 上次选择范围 */
    private var lastSelectionStart = -1
    private var lastSelectionEnd = -1

    /**
     * 重写选择变化回调
     */
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)

        if (!enableSelectionRefresh) return
        if (selStart == lastSelectionStart && selEnd == lastSelectionEnd) return

        lastSelectionStart = selStart
        lastSelectionEnd = selEnd

        // 取消之前的刷新任务
        refreshHandler.removeCallbacks(refreshRunnable)

        // 延迟 100ms 刷新（等待游标动画完成）
        refreshHandler.postDelayed(refreshRunnable, 100)
    }

    /**
     * 重置选择状态（用于代码设置选择时避免刷新）
     */
    fun resetLastSelection() {
        lastSelectionStart = selectionStart
        lastSelectionEnd = selectionEnd
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        refreshHandler.removeCallbacks(refreshRunnable)
    }
}
