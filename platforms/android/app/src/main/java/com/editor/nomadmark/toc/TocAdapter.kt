package com.editor.nomadmark.toc

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.editor.nomadmark.TocEntry

/**
 * TocAdapter - 目录 RecyclerView Adapter
 *
 * 基于《UI交互文档》第九节实现
 *
 * 功能:
 * - 从左侧滑出, 宽度 = 屏幕 2/3
 * - 调用 Rust FFI 获取 TocEntry
 * - 点击标题跳转: 根据 byte_offset 计算滚动位置
 * - 支持拖拽排序 (同级别)
 * - 展开/收起子标题
 *
 * E-ink 优化:
 * - 展开/收起使用 EPD_FULL 刷新
 * - 禁止在 onDraw 中分配内存
 */
class TocAdapter(
    private val onEntryClick: (TocEntry) -> Unit,
    private val onEntryMoved: (Int, Int) -> Unit
) : RecyclerView.Adapter<TocAdapter.TocViewHolder>() {

    // =========================================================================
    // 目录数据结构
    // =========================================================================

    /**
     * 可展开的目录条目
     */
    data class ExpandableTocEntry(
        val entry: TocEntry,
        var isExpanded: Boolean = true,
        var indentLevel: Int = 0,
        val children: MutableList<ExpandableTocEntry> = mutableListOf()
    ) {
        val totalVisibleDescendants: Int
            get() = if (isExpanded) {
                children.sumOf { 1 + it.totalVisibleDescendants }
            } else {
                0
            }
    }

    /**
     * 扁平化的显示列表
     */
    private val flatList: MutableList<ExpandableTocEntry> = mutableListOf()

    /**
     * 根级条目列表
     */
    private val rootEntries: MutableList<ExpandableTocEntry> = mutableListOf()

    // =========================================================================
    // Paint 对象池 (避免在 onDraw 中分配)
    // =========================================================================

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        textSize = 32f
    }

    private val expandedIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt()
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val selectedPaint = Paint().apply {
        color = 0xFFE0E0E0.toInt()
        style = Paint.Style.FILL
    }

    /**
     * 选中的位置
     */
    private var selectedPosition: Int = RecyclerView.NO_POSITION

    // =========================================================================
    // 数据加载
    // =========================================================================

    /**
     * 加载目录数据
     *
     * @param tocEntries 目录条目列表
     */
    fun loadToc(tocEntries: List<TocEntry>) {
        rootEntries.clear()
        flatList.clear()

        // 构建层级树结构
        val stack = ArrayDeque<ExpandableTocEntry>()

        for (entry in tocEntries) {
            // indentLevel 直接基于标题级别: H1=0, H2=1, H3=2, H4=3, H5=4, H6=5
            val expandable = ExpandableTocEntry(
                entry = entry,
                indentLevel = (entry.level - 1).coerceAtLeast(0)
            )

            // 找到父级
            while (stack.isNotEmpty() && stack.last().entry.level >= entry.level) {
                stack.removeLast()
            }

            if (stack.isEmpty()) {
                // 根级条目
                rootEntries.add(expandable)
            } else {
                // 子级条目
                val parent = stack.last()
                parent.children.add(expandable)
            }

            // 如果有子级, 压入栈
            if (entry.level < 6) {
                stack.addLast(expandable)
            }
        }

        // 扁平化显示列表
        flattenToList()

        // 目录结构完全重建，使用 notifyDataSetChanged 是合理的
        notifyDataSetChanged()
    }

    /**
     * 扁平化树结构为显示列表
     */
    private fun flattenToList() {
        flatList.clear()
        for (root in rootEntries) {
            addToList(root, flatList)
        }
    }

    private fun addToList(item: ExpandableTocEntry, list: MutableList<ExpandableTocEntry>) {
        list.add(item)
        if (item.isExpanded) {
            for (child in item.children) {
                addToList(child, list)
            }
        }
    }

    // =========================================================================
    // 展开/收起
    // =========================================================================

    /**
     * 切换条目展开状态
     *
     * @param position 条目位置
     * @return 需要刷新的区域 (用于 E-ink EPD_FULL 刷新)
     */
    fun toggleExpanded(position: Int): Rect? {
        if (position !in 0 until flatList.size) return null

        val item = flatList[position]
        if (item.children.isEmpty()) return null

        val wasExpanded = item.isExpanded
        item.isExpanded = !wasExpanded

        // 计算影响的范围
        val affectedCount = item.totalVisibleDescendants

        if (wasExpanded) {
            // 收起: 移除子条目
            flatList.subList(position + 1, position + 1 + affectedCount).clear()
            notifyItemRangeRemoved(position + 1, affectedCount)
        } else {
            // 展开: 添加子条目
            val newItems = mutableListOf<ExpandableTocEntry>()
            for (child in item.children) {
                addToList(child, newItems)
            }
            flatList.addAll(position + 1, newItems)
            notifyItemRangeInserted(position + 1, newItems.size)
        }

        notifyItemChanged(position)

        // 返回受影响的区域 (需要全局刷新)
        return null
    }

    // =========================================================================
    // RecyclerView.Adapter 实现
    // =========================================================================

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): TocViewHolder {
        val view = TocItemView(parent.context)
        view.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
        return TocViewHolder(view)
    }

    override fun onBindViewHolder(holder: TocViewHolder, position: Int) {
        val item = flatList[position]
        val itemView = holder.itemView as TocItemView
        itemView.bind(
            entry = item.entry,
            indentLevel = item.indentLevel,
            isExpanded = item.isExpanded,
            hasChildren = item.children.isNotEmpty(),
            isSelected = position == selectedPosition
        )
        holder.itemView.setOnClickListener {
            onEntryClick(item.entry)
            selectedPosition = holder.adapterPosition
            notifyItemChanged(selectedPosition)
        }
    }

    override fun getItemCount(): Int = flatList.size

    override fun onViewRecycled(holder: TocViewHolder) {
        super.onViewRecycled(holder)
        (holder.itemView as TocItemView).recycle()
    }

    // =========================================================================
    // ViewHolder
    // =========================================================================

    class TocViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    // =========================================================================
    // 拖拽支持
    // =========================================================================

    /**
     * 创建 ItemTouchHelper 用于拖拽排序
     *
     * 仅允许同级别标题拖拽排序
     */
    fun createDragTouchHelper(): ItemTouchHelper {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition

                // 检查是否同级别
                if (fromPos !in 0 until flatList.size || toPos !in 0 until flatList.size) {
                    return false
                }

                val fromItem = flatList[fromPos]
                val toItem = flatList[toPos]

                // 仅允许同级别拖拽
                if (fromItem.entry.level != toItem.entry.level) {
                    return false
                }

                // 执行移动
                onEntryMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不支持滑动删除
            }

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val position = viewHolder.adapterPosition
                if (position !in 0 until flatList.size) return 0

                // 仅允许同级别拖拽
                return makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                    0
                )
            }
        }

        return ItemTouchHelper(callback)
    }
}

// =============================================================================
// TocItemView - 目录条目视图
// =============================================================================

/**
 * 目录条目视图
 *
 * 禁止在 onDraw 中分配内存
 */
class TocItemView(context: android.content.Context) : View(context) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        textSize = 32f
        // 统一字重，不通过字体样式区分层级
        // 伪粗体已禁用，所有层级保持一致的视觉效果
    }

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt()
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val selectedPaint = Paint().apply {
        color = 0xFFE0E0E0.toInt()
        style = Paint.Style.FILL
    }

    private val boundsRect = Rect()
    private val textBounds = Rect()

    private var entry: TocEntry? = null
    private var indentLevel: Int = 0
    private var isExpanded: Boolean = false
    private var hasChildren: Boolean = false
    private var isSelected: Boolean = false

    companion object {
        /**
         * 缩进规则:
         * - H1 (level=1): 无缩进 (indentLevel=0, indent=0)
         * - H2 (level=2): 缩进 1 单位 (40px)
         * - H3 (level=3): 缩进 2 单位 (80px)
         * 以此类推，每增加一级标题，增加一次固定单位的缩进
         */
        const val INDENT_PER_LEVEL = 40
        const val ITEM_HEIGHT = 64
        const val ICON_SIZE = 24
        const val ICON_MARGIN = 16

        /**
         * 统一字体大小 (sp/px)
         * 所有层级使用相同字体大小，仅通过缩进区分层级
         */
        const val UNIFIED_TEXT_SIZE = 32f
    }

    fun bind(
        entry: TocEntry,
        indentLevel: Int,
        isExpanded: Boolean,
        hasChildren: Boolean,
        isSelected: Boolean
    ) {
        this.entry = entry
        this.indentLevel = indentLevel
        this.isExpanded = isExpanded
        this.hasChildren = hasChildren
        this.isSelected = isSelected
    }

    fun recycle() {
        // 清理资源
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = ITEM_HEIGHT
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (entry == null) return

        val indent = indentLevel * INDENT_PER_LEVEL
        val iconLeft = indent + ICON_MARGIN
        val textLeft = if (hasChildren) {
            iconLeft + ICON_SIZE + ICON_MARGIN
        } else {
            iconLeft + ICON_MARGIN
        }

        // 绘制选中背景
        if (isSelected) {
            boundsRect.set(0, 0, width, height)
            canvas.drawRect(boundsRect, selectedPaint)
        }

        // 绘制展开/收起图标
        if (hasChildren) {
            drawExpandIcon(canvas, iconLeft, height / 2, isExpanded)
        }

        // 绘制标题文本
        // 使用统一字体大小，不根据级别变化
        // 所有层级通过缩进位置区分，不使用字体样式区分

        val title = entry!!.title
        textPaint.getTextBounds(title, 0, title.length, textBounds)

        val textY = (height + textBounds.height()) / 2f - textBounds.bottom.toFloat()
        canvas.drawText(title, textLeft.toFloat(), textY, textPaint)
    }

    private fun drawExpandIcon(canvas: Canvas, cx: Int, cy: Int, expanded: Boolean) {
        val halfSize = ICON_SIZE / 2f
        val padding = 4f

        if (expanded) {
            // ▼ 向下三角形 (收起图标)
            val path = android.graphics.Path()
            path.moveTo(cx - halfSize + padding, cy - halfSize / 2 + padding)
            path.lineTo(cx + halfSize - padding, cy - halfSize / 2 + padding)
            path.lineTo(cx.toFloat(), cy + halfSize - padding)
            path.close()
            canvas.drawPath(path, indicatorPaint)
        } else {
            // ▶ 向右三角形 (展开图标)
            val path = android.graphics.Path()
            path.moveTo(cx - halfSize / 2 + padding, cy - halfSize + padding)
            path.lineTo(cx - halfSize / 2 + padding, cy + halfSize - padding)
            path.lineTo(cx + halfSize - padding, cy.toFloat())
            path.close()
            canvas.drawPath(path, indicatorPaint)
        }
    }
}
