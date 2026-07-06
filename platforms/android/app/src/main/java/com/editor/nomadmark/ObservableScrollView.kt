package com.editor.nomadmark

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * Observable ScrollView
 *
 * A custom ScrollView that provides scroll change callbacks.
 * This ensures that scroll events from mouse wheel and touch are both captured.
 *
 * Unlike viewTreeObserver.onScrollChangedListener, this view's onScrollChanged
 * method is called for all scroll events, including mouse wheel events.
 */
class ObservableScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    /**
     * Callback for scroll changes
     */
    var onScrollChangedCallback: ((scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) -> Unit)? = null

    /**
     * Scroll listener interface (alternative to callback)
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

        // Notify callback
        onScrollChangedCallback?.invoke(scrollX, scrollY, oldScrollX, oldScrollY)

        // Notify listener
        scrollListener?.onScrollChanged(scrollX, scrollY, oldScrollX, oldScrollY)
    }
}
