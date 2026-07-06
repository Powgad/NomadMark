package com.editor.nomadmark.search

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.editor.nomadmark.EinkRefreshController
import com.editor.nomadmark.MarkdownCore
import com.editor.nomadmark.SearchResult

/**
 * SearchPanel - 搜索面板
 *
 * 基于《UI交互文档》第十节实现
 *
 * 功能:
 * - 输入框 + 确认搜索按钮
 * - 调用 Rust FFI md_document_search
 * - 显示搜索结果
 * - 高亮匹配项
 *
 * E-ink 优化:
 * - 搜索结果高亮使用 EPD_A2 模式
 * - 输入时延迟刷新以减少闪烁
 */
class SearchPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // =========================================================================
    // UI 组件
    // =========================================================================

    private val searchInput: EditText
    private val searchButton: ImageButton
    private val closeButton: ImageButton
    private val resultsRecyclerView: RecyclerView
    private val replaceButton: ImageButton

    // =========================================================================
    // 布局常量
    // =========================================================================

    companion object {
        /** 面板高度 */
        const val PANEL_HEIGHT = 120

        /** 搜索去抖动延迟 (ms) */
        const val SEARCH_DEBOUNCE_DELAY = 300L

        /** 最大结果数量 */
        const val MAX_RESULTS = 100
    }

    // =========================================================================
    // 状态
    // =========================================================================

    private var isVisible = false
    private var documentHandle: Long = 0L
    private var currentQuery: String = ""
    private var currentResults: Array<SearchResult> = emptyArray()
    private var selectedIndex: Int = -1

    // =========================================================================
    // 回调
    // =========================================================================

    var onResultSelected: ((SearchResult) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null
    var onReplaceRequested: (() -> Unit)? = null

    // =========================================================================
    // E-ink 刷新
    // =========================================================================

    private lateinit var refreshController: EinkRefreshController
    private lateinit var highlightPainter: SearchHighlightPainter

    // =========================================================================
    // 搜索去抖动
    // =========================================================================

    private val searchDebouncer = SearchDebouncer(SEARCH_DEBOUNCE_DELAY)

    // =========================================================================
    // 结果 Adapter
    // =========================================================================

    private lateinit var resultsAdapter: SearchResultAdapter

    // =========================================================================
    // 初始化
    // =========================================================================

    init {
        // 加载布局
        LayoutInflater.from(context).inflate(R.layout.search_panel_layout, this, true)

        // 获取视图引用
        searchInput = findViewById(R.id.search_input)
        searchButton = findViewById(R.id.search_button)
        closeButton = findViewById(R.id.close_button)
        resultsRecyclerView = findViewById(R.id.search_results)
        replaceButton = findViewById(R.id.replace_button)

        // 设置结果列表
        resultsRecyclerView.layoutManager = LinearLayoutManager(context)
        resultsRecyclerView.isFocusable = false

        // 设置搜索按钮点击
        searchButton.setOnClickListener {
            performSearch()
        }

        // 设置关闭按钮点击
        closeButton.setOnClickListener {
            dismiss()
        }

        // 设置替换按钮点击
        replaceButton.setOnClickListener {
            showReplacePanel()
        }

        // 设置输入监听 (去抖动)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s?.isNotEmpty() == true) {
                    searchDebouncer.debounce {
                        performSearch()
                    }
                }
            }
        })

        // 初始隐藏
        visibility = View.GONE

        // 初始化高亮绘制器
        highlightPainter = SearchHighlightPainter()
    }

    // =========================================================================
    // 公开 API
    // =========================================================================

    /**
     * 设置 E-ink 刷新控制器
     */
    fun setRefreshController(controller: EinkRefreshController) {
        refreshController = controller
    }

    /**
     * 设置文档句柄
     */
    fun setDocumentHandle(handle: Long) {
        documentHandle = handle
    }

    /**
     * 显示搜索面板
     */
    fun show() {
        if (isVisible) return

        visibility = View.VISIBLE
        searchInput.requestFocus()

        isVisible = true

        // 请求刷新面板区域
        requestDirtyRefresh()
    }

    /**
     * 隐藏搜索面板
     */
    fun dismiss() {
        if (!isVisible) return

        visibility = View.GONE
        searchInput.text.clear()
        clearResults()

        isVisible = false
        onDismiss?.invoke()

        // 请求刷新全屏 (清除高亮)
        if (::refreshController.isInitialized) {
            refreshController.requestGlobalRefresh()
        }
    }

    /**
     * 获取当前搜索结果
     */
    fun getSearchResults(): Array<SearchResult> = currentResults

    /**
     * 获取当前选中的结果
     */
    fun getSelectedResult(): SearchResult? {
        return if (selectedIndex in currentResults.indices) {
            currentResults[selectedIndex]
        } else {
            null
        }
    }

    /**
     * 跳转到下一个结果
     */
    fun goToNextResult() {
        if (currentResults.isEmpty()) return

        selectedIndex = if (selectedIndex < currentResults.size - 1) {
            selectedIndex + 1
        } else {
            0 // 循环到第一个
        }

        onResultSelected?.invoke(currentResults[selectedIndex])
        updateResultsSelection()
    }

    /**
     * 跳转到上一个结果
     */
    fun goToPreviousResult() {
        if (currentResults.isEmpty()) return

        selectedIndex = if (selectedIndex > 0) {
            selectedIndex - 1
        } else {
            currentResults.size - 1 // 循环到最后一个
        }

        onResultSelected?.invoke(currentResults[selectedIndex])
        updateResultsSelection()
    }

    // =========================================================================
    // 搜索执行
    // =========================================================================

    /**
     * 执行搜索
     */
    private fun performSearch() {
        if (documentHandle == 0L) return

        val query = searchInput.text.toString().trim()
        if (query.isEmpty()) {
            clearResults()
            return
        }

        currentQuery = query

        try {
            // 调用 Rust Core 搜索
            val results = MarkdownCore.nativeSearch(documentHandle, query)

            // 限制结果数量
            currentResults = if (results.size > MAX_RESULTS) {
                results.copyOfRange(0, MAX_RESULTS)
            } else {
                results
            }

            selectedIndex = if (currentResults.isNotEmpty()) 0 else -1

            // 更新结果列表
            updateResultsList()

            // 请求高亮刷新 (EPD_A2 模式)
            requestHighlightRefresh()

        } catch (e: Exception) {
            currentResults = emptyArray()
            selectedIndex = -1
            updateResultsList()
        }
    }

    /**
     * 清空结果
     */
    private fun clearResults() {
        currentResults = emptyArray()
        selectedIndex = -1
        updateResultsList()
    }

    // =========================================================================
    // 结果列表更新
    // =========================================================================

    /**
     * 更新结果列表
     */
    private fun updateResultsList() {
        if (!::resultsAdapter.isInitialized) {
            resultsAdapter = SearchResultAdapter(
                onResultClick = { index ->
                    selectedIndex = index
                    onResultSelected?.invoke(currentResults[index])
                    updateResultsSelection()
                }
            )
            resultsRecyclerView.adapter = resultsAdapter
        }

        resultsAdapter.updateResults(currentResults.toList())
        updateResultsSelection()
    }

    /**
     * 更新选中状态
     */
    private fun updateResultsSelection() {
        if (::resultsAdapter.isInitialized) {
            resultsAdapter.setSelectedIndex(selectedIndex)
        }
    }

    // =========================================================================
    // 替换面板
    // =========================================================================

    /**
     * 显示替换面板
     */
    private fun showReplacePanel() {
        onReplaceRequested?.invoke()
    }

    // =========================================================================
    // E-ink 刷新
    // =========================================================================

    /**
     * 请求面板区域刷新
     */
    private fun requestDirtyRefresh() {
        if (::refreshController.isInitialized) {
            val dirtyRect = calculatePanelRect()
            refreshController.addDirty(dirtyRect)
        }
    }

    /**
     * 请求高亮刷新 (EPD_A2 模式)
     */
    private fun requestHighlightRefresh() {
        if (::refreshController.isInitialized) {
            // 计算所有结果的高亮区域
            val highlightRects = currentResults.map { result ->
                calculateResultRect(result)
            }

            // 使用 EPD_A2 模式刷新高亮区域
            highlightRects.forEach { rect ->
                refreshController.requestA2Refresh(rect)
            }
        }
    }

    /**
     * 计算面板区域
     */
    private fun calculatePanelRect(): Rect {
        return Rect(0, 0, width, PANEL_HEIGHT)
    }

    /**
     * 计算结果区域 (在文档中的位置)
     */
    private fun calculateResultRect(result: SearchResult): Rect {
        // TODO: 根据行号和列位置计算高亮区域
        // 这需要与 MarkdownEditorView 配合
        return Rect(0, 0, 0, 0)
    }

    // =========================================================================
    // 视图绘制
    // =========================================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制搜索结果高亮
        if (currentResults.isNotEmpty() && ::highlightPainter.isInitialized) {
            for (result in currentResults) {
                val rect = calculateResultRect(result)
                if (!rect.isEmpty) {
                    highlightPainter.drawHighlight(canvas, rect)
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 布局变化时更新结果区域
        if (currentResults.isNotEmpty()) {
            requestHighlightRefresh()
        }
    }
}

// =============================================================================
// SearchResultAdapter - 搜索结果列表 Adapter
// =============================================================================

/**
 * 搜索结果 Adapter
 */
class SearchResultAdapter(
    private val onResultClick: (Int) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.ResultViewHolder>() {

    private val results: MutableList<SearchResult> = mutableListOf()
    private var selectedIndex: Int = -1

    fun updateResults(newResults: List<SearchResult>) {
        results.clear()
        results.addAll(newResults)
        // 数据完全替换，使用 notifyDataSetChanged 是合理的
        notifyDataSetChanged()
    }

    fun setSelectedIndex(index: Int) {
        val oldIndex = selectedIndex
        selectedIndex = index
        notifyItemChanged(oldIndex)
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ResultViewHolder {
        val view = SearchResultItemView(parent.context)
        view.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val result = results[position]
        val itemView = holder.itemView as SearchResultItemView
        itemView.bind(
            result = result,
            index = position + 1, // 1-based index
            isSelected = position == selectedIndex
        )
        holder.itemView.setOnClickListener {
            onResultClick(position)
        }
    }

    override fun getItemCount(): Int = results.size

    class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}

// =============================================================================
// SearchResultItemView - 搜索结果条目视图
// =============================================================================

/**
 * 搜索结果条目视图
 */
class SearchResultItemView(context: android.content.Context) : View(context) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        textSize = 28f
    }

    private val indexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt()
        textSize = 24f
    }

    private val selectedPaint = Paint().apply {
        color = 0xFFD0D0D0.toInt()
        style = Paint.Style.FILL
    }

    private val textBounds = Rect()
    private val bgRect = Rect()  // 预分配复用
    private var result: SearchResult? = null
    private var index: Int = 0
    private var isSelected: Boolean = false

    companion object {
        const val ITEM_HEIGHT = 60
        const val PADDING = 16
        const val INDEX_WIDTH = 60
    }

    fun bind(result: SearchResult, index: Int, isSelected: Boolean) {
        this.result = result
        this.index = index
        this.isSelected = isSelected
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = ITEM_HEIGHT
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (result == null) return

        // 绘制选中背景
        if (isSelected) {
            bgRect.set(0, 0, width, height)  // 复用预分配的 Rect
            canvas.drawRect(bgRect, selectedPaint)
        }

        // 绘制序号
        val indexText = "$index."
        indexPaint.getTextBounds(indexText, 0, indexText.length, textBounds)
        val indexX = PADDING
        val indexY = (height + textBounds.height()) / 2f - textBounds.bottom.toFloat()
        canvas.drawText(indexText, indexX.toFloat(), indexY, indexPaint)

        // 绘制行号
        val lineText = "Line ${result!!.line + 1}"
        textPaint.getTextBounds(lineText, 0, lineText.length, textBounds)
        val lineX = INDEX_WIDTH + PADDING
        val lineY = (height + textBounds.height()) / 2f - textBounds.bottom.toFloat()
        canvas.drawText(lineText, lineX.toFloat(), lineY, textPaint)

        // 绘制上下文预览
        val contextText = result!!.context
        if (contextText.isNotEmpty()) {
            val contextX = lineX + textPaint.measureText(lineText) + PADDING * 2
            canvas.drawText(contextText, contextX, lineY, indexPaint)
        }
    }
}

// =============================================================================
// SearchDebouncer - 搜索去抖动
// =============================================================================

/**
 * 搜索去抖动工具
 */
class SearchDebouncer(private val delay: Long) {
    private var job: android.os.Handler? = null
    private var currentRunnable: Runnable? = null

    fun debounce(action: () -> Unit) {
        currentRunnable?.let { job?.removeCallbacks(it) }
        val runnable = Runnable { action() }
        currentRunnable = runnable
        job = android.os.Handler(android.os.Looper.getMainLooper())
        job?.postDelayed(runnable, delay)
    }

    fun cancel() {
        currentRunnable?.let { job?.removeCallbacks(it) }
        currentRunnable = null
        job = null
    }
}

// =============================================================================
// 布局资源 (R.layout.search_panel_layout)
// =============================================================================

/**
 * search_panel_layout.xml
 *
 * <?xml version="1.0" encoding="utf-8"?>
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:orientation="vertical"
 *     android:background="#F5F5F5"
 *     android:elevation="4dp">
 *
 *     <!-- 搜索输入行 -->
 *     <LinearLayout
 *         android:layout_width="match_parent"
 *         android:layout_height="@dimen/search_panel_height"
 *         android:orientation="horizontal"
 *         android:padding="8dp"
 *         android:gravity="center_vertical">
 *
 *         <EditText
 *             android:id="@+id/search_input"
 *             android:layout_width="0dp"
 *             android:layout_height="match_parent"
 *             android:layout_weight="1"
 *             android:hint="Search..."
 *             android:background="@drawable/search_input_background"
 *             android:paddingStart="16dp"
 *             android:paddingEnd="16dp" />
 *
 *         <ImageButton
 *             android:id="@+id/search_button"
 *             android:layout_width="48dp"
 *             android:layout_height="48dp"
 *             android:layout_marginStart="8dp"
 *             android:src="@drawable/ic_search"
 *             android:background="?attr/selectableItemBackgroundBorderless"
 *             android:contentDescription="Search" />
 *
 *         <ImageButton
 *             android:id="@+id/replace_button"
 *             android:layout_width="48dp"
 *             android:layout_height="48dp"
 *             android:layout_marginStart="4dp"
 *             android:src="@drawable/ic_replace"
 *             android:background="?attr/selectableItemBackgroundBorderless"
 *             android:contentDescription="Replace" />
 *
 *         <ImageButton
 *             android:id="@+id/close_button"
 *             android:layout_width="48dp"
 *             android:layout_height="48dp"
 *             android:layout_marginStart="4dp"
 *             android:src="@drawable/ic_close"
 *             android:background="?attr/selectableItemBackgroundBorderless"
 *             android:contentDescription="Close" />
 *     </LinearLayout>
 *
 *     <!-- 搜索结果列表 -->
 *     <androidx.recyclerview.widget.RecyclerView
 *         android:id="@+id/search_results"
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content"
 *         android:maxHeight="200dp"
 *         android:background="#FFFFFF" />
 *
 * </LinearLayout>
 */

object R {
    object layout {
        const val search_panel_layout = 0
    }

    object id {
        const val search_input = 0
        const val search_button = 0
        const val replace_button = 0
        const val close_button = 0
        const val search_results = 0
    }

    object drawable {
        const val search_input_background = 0
        const val ic_search = 0
        const val ic_replace = 0
        const val ic_close = 0
    }

    object dimen {
        const val search_panel_height = 0
    }
}
