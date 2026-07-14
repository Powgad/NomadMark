package com.editor.nomadmark.toc

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.editor.nomadmark.EinkRefreshController
import com.editor.nomadmark.TocEntry

/**
 * TocPanel - 侧边目录面板
 *
 * 基于《UI交互文档》第九节实现
 *
 * 功能:
 * - 从左侧滑出, 宽度 = 屏幕 2/3
 * - 点击右侧页面收起目录
 * - 支持展开/收起标题
 * - 单击标题跳转对应内容
 * - 拖拽进行同级别标题调序
 *
 * E-ink 优化:
 * - 展开/收起使用 EPD_FULL 刷新
 * - 滑动动画使用 EPD_PARTIAL 刷新
 */
class TocPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // =========================================================================
    // UI 组件
    // =========================================================================

    private val recyclerView: RecyclerView
    private val dimView: View
    private lateinit var adapter: TocAdapter

    // =========================================================================
    // 布局常量
    // =========================================================================

    companion object {
        /** 目录宽度占比 (2/3) */
        const val TOC_WIDTH_RATIO = 2f / 3f

        /** 滑动动画时长 (ms) */
        const val SLIDE_DURATION = 250L

        /** 背景透明度 */
        const val DIM_ALPHA = 0x40 // 25%
    }

    // =========================================================================
    // 状态
    // =========================================================================

    private var isVisible = false
    private var documentHandle: Long = 0L
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    // =========================================================================
    // 回调
    // =========================================================================

    var onTocEntryClick: ((TocEntry) -> Unit)? = null
    var onTocDismiss: (() -> Unit)? = null

    // =========================================================================
    // E-ink 刷新
    // =========================================================================

    private lateinit var refreshController: EinkRefreshController

    // =========================================================================
    // 初始化
    // =========================================================================

    init {
        // 设置为透明背景 (由 dimView 提供遮罩)
        background = null

        // 加载布局
        LayoutInflater.from(context).inflate(R.layout.toc_panel_layout, this, true)

        // 获取视图引用
        recyclerView = findViewById(R.id.toc_recycler_view)
        dimView = findViewById(R.id.toc_dim_view)

        // 设置 RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.isFocusable = false
        recyclerView.isFocusableInTouchMode = false

        // 设置点击遮罩关闭
        dimView.setOnClickListener {
            dismiss()
        }

        // 初始状态: 隐藏
        visibility = View.GONE
        translationX = -screenWidth.toFloat()

        // 获取屏幕尺寸
        val displayMetrics = context.resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
    }

    // =========================================================================
    // 生命周期
    // =========================================================================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w
        screenHeight = h
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
     * 显示目录
     *
     * 从左侧滑出动画
     * 注意：需要先调用 loadTocData(tocEntries) 加载目录数据
     */
    fun show() {
        if (isVisible) return

        visibility = View.VISIBLE

        // 滑动动画
        val tocWidth = (screenWidth * TOC_WIDTH_RATIO).toInt()
        animatePanelOpen(tocWidth)

        isVisible = true
    }

    /**
     * 隐藏目录
     *
     * 滑出到左侧动画
     */
    fun dismiss() {
        if (!isVisible) return

        val tocWidth = (screenWidth * TOC_WIDTH_RATIO).toInt()
        animatePanelClose(tocWidth)

        isVisible = false
    }

    /**
     * 刷新目录数据
     * 注意：此方法已废弃，请使用 loadTocData(tocEntries) 重新加载数据
     */
    @Deprecated("Use loadTocData with explicit TocEntry list instead")
    fun refresh() {
        // 不再自动刷新，需要外部调用 loadTocData
    }

    // =========================================================================
    // 数据加载
    // =========================================================================

    /**
     * 从外部数据加载目录
     */
    fun loadTocData(tocEntries: List<TocEntry>) {
        // 初始化 Adapter (首次)
        if (!::adapter.isInitialized) {
            adapter = TocAdapter(
                onEntryClick = { entry ->
                    onTocEntryClick?.invoke(entry)
                    dismiss()
                },
                onEntryMoved = { fromPos, toPos ->
                    onEntryMoved(fromPos, toPos)
                }
            )
            recyclerView.adapter = adapter

            // 设置拖拽支持
            val dragHelper = adapter.createDragTouchHelper()
            dragHelper.attachToRecyclerView(recyclerView)
        }

        // 更新数据
        adapter.loadToc(tocEntries)
    }

    /**
     * 显示目录（已弃用，保留以兼容旧代码）
     */
    @Deprecated("Use loadTocData with explicit TocEntry list instead")
    private fun loadTocDataFromHandle() {
        // 此方法已废弃，不再使用 Rust Core
    }

    // =========================================================================
    // 拖拽排序
    // =========================================================================

    /**
     * 处理条目移动
     */
    private fun onEntryMoved(fromPos: Int, toPos: Int) {
        // TODO: 实现 Markdown 文档中的标题移动
        // 这需要调用 Rust Core 的编辑接口
    }

    // =========================================================================
    // 动画
    // =========================================================================

    /**
     * 面板打开动画
     */
    private fun animatePanelOpen(tocWidth: Int) {
        // 从左侧滑入
        translationX = -tocWidth.toFloat()

        val animator = ValueAnimator.ofFloat(-tocWidth.toFloat(), 0f)
        animator.duration = SLIDE_DURATION
        animator.addUpdateListener { animation ->
            translationX = animation.animatedValue as Float

            // 局部刷新移动区域
            val dirtyRect = Rect(0, 0, tocWidth + kotlin.math.abs(translationX.toInt()), screenHeight)
            invalidate(dirtyRect)
        }
        animator.start()

        // 设置遮罩透明度
        dimView.alpha = 0f
        dimView.animate()
            .alpha(DIM_ALPHA / 255f)
            .setDuration(SLIDE_DURATION)
            .start()
    }

    /**
     * 面板关闭动画
     */
    private fun animatePanelClose(tocWidth: Int) {
        val currentX = translationX
        val animator = ValueAnimator.ofFloat(currentX, -tocWidth.toFloat())
        animator.duration = SLIDE_DURATION
        animator.addUpdateListener { animation ->
            translationX = animation.animatedValue as Float

            // 局部刷新移动区域
            val dirtyRect = Rect(0, 0, tocWidth + kotlin.math.abs(translationX.toInt()), screenHeight)
            invalidate(dirtyRect)
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                visibility = View.GONE
                onTocDismiss?.invoke()
            }
        })
        animator.start()

        // 淡出遮罩
        dimView.animate()
            .alpha(0f)
            .setDuration(SLIDE_DURATION)
            .start()
    }

    // =========================================================================
    // 展开/收起处理
    // =========================================================================

    /**
     * 处理展开/收起
     *
     * 触发 EPD_FULL 刷新
     */
    fun handleToggleExpanded(position: Int) {
        if (::adapter.isInitialized.not()) return

        val affectedRect = adapter.toggleExpanded(position)

        // 展开/收起需要全局刷新
        if (::refreshController.isInitialized) {
            refreshController.requestGlobalRefresh()
        }
    }

    // =========================================================================
    // 脏矩形计算
    // =========================================================================

    /**
     * 计算当前目录区域的脏矩形
     */
    fun calculateDirtyRect(): Rect {
        val tocWidth = (screenWidth * TOC_WIDTH_RATIO).toInt()
        return Rect(0, 0, tocWidth, screenHeight)
    }
}

// =============================================================================
// 布局资源 (R.layout.toc_panel_layout)
// =============================================================================

/**
 * toc_panel_layout.xml
 *
 * <?xml version="1.0" encoding="utf-8"?>
 * <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent">
 *
 *     <!-- 目录内容 -->
 *     <androidx.recyclerview.widget.RecyclerView
 *         android:id="@+id/toc_recycler_view"
 *         android:layout_width="wrap_content"
 *         android:layout_height="match_parent"
 *         android:layout_gravity="start"
 *         android:background="#FAFAFA" />
 *
 *     <!-- 遮罩层 -->
 *     <View
 *         android:id="@+id/toc_dim_view"
 *         android:layout_width="match_parent"
 *         android:layout_height="match_parent"
 *         android:background="#66000000"
 *         android:visibility="gone" />
 *
 * </FrameLayout>
 */

object R {
    object layout {
        const val toc_panel_layout = 0
    }

    object id {
        const val toc_recycler_view = 0
        const val toc_dim_view = 0
    }
}
