package com.editor.nomadmark

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * 可观察的 ScrollView
 *
 * 一个提供滚动变化回调的自定义 ScrollView。
 * 这确保了鼠标滚轮和触摸的滚动事件都能被捕获。
 *
 * 与 viewTreeObserver.onScrollChangedListener 不同，此视图的 onScrollChanged
 * 方法对所有滚动事件（包括鼠标滚轮事件）都会被调用。
 */
class ObservableScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    /**
     * 滚动变化的回调
     */
    var onScrollChangedCallback: ((scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) -> Unit)? = null

    /**
     * 滚动监听器接口（回调的替代方案）
     */
    interface OnScrollListener {
        fun onScrollChanged(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int)
    }

    private var scrollListener: OnScrollListener? = null

    fun setOnScrollListener(listener: OnScrollListener) {
        this.scrollListener = listener
    }

    override fun onScrollChanged(scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
        super.onScrollChanged(scrollX, scrollY, oldScrollX, oldScrollY)

        // 通知回调
        onScrollChangedCallback?.invoke(scrollX, scrollY, oldScrollX, oldScrollY)

        // 通知监听器
        scrollListener?.onScrollChanged(scrollX, scrollY, oldScrollX, oldScrollY)
    }
}
