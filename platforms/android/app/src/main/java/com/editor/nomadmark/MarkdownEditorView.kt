package com.editor.nomadmark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Scroller
import com.editor.nomadmark.render.RenderCommandExecutor
import kotlin.math.max
import kotlin.math.min

/**
 * MarkdownEditorView - 主编辑器视图
 *
 * 基于《架构设计书 v2.0》和《UI交互文档》实现
 *
 * 核心职责:
 * - 管理编辑器状态机 (InputMode, LayoutMode, FeatureFlags)
 * - 计算分屏布局比例 (外接键盘 1:1, 软键盘 4:6)
 * - 调用 Rust Core 进行渲染
 * - 通过 EinkRefreshController 优化 E-ink 刷新
 *
 * @see MarkdownCore Rust Core FFI 桥接
 * @see EinkRefreshController E-ink 刷新控制器
 */
class MarkdownEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // =========================================================================
    // 渲染命令执行器
    // =========================================================================

    /**
     * Rust Core 渲染命令执行器
     */
    private val renderCommandExecutor = RenderCommandExecutor()

    // =========================================================================
    // 状态定义 (必须严格遵守 UI 交互文档)
    // =========================================================================

    /**
     * 输入模式 (对应 UI 交互文档 三, 四, 五)
     */
    enum class InputMode {
        /**
         * 外接键盘模式
         * - 底部有固定键盘标识
         * - 分屏比例: 1:1 (UI 文档 六.1)
         * - 工具栏位置: 键盘标识上方 (UI 文档 三.2)
         */
        ExternalKeyboard,

        /**
         * 软键盘模式
         * - 底部有软键盘输入法
         * - 分屏比例: 4:6 (编辑:预览) (UI 文档 七.1)
         * - 工具栏位置: 输入法上方 (UI 文档 四.2)
         */
        SoftKeyboard,

        /**
         * 隐藏模式
         * - 预览模式/修订模式下隐藏键盘 (UI 文档 五, 八)
         * - 开启修订时强制切换到此模式
         */
        Hidden
    }

    /**
     * 布局模式 (对应 UI 交互文档 全局)
     */
    enum class LayoutMode {
        /**
         * 全屏编辑模式
         * - 仅显示编辑器
         * - 支持键盘输入
         */
        FullscreenEditor,

        /**
         * 全屏预览模式
         * - 仅显示渲染后的 Markdown
         * - 键盘隐藏 (InputMode = Hidden)
         */
        FullscreenPreview,

        /**
         * 分屏模式
         * - 上方预览, 下方编辑
         * - 比例根据 InputMode 动态调整
         */
        SplitView
    }

    /**
     * 功能开关 (对应 UI 交互文档 各功能章节)
     */
    data class FeatureFlags(
        /**
         * 工具栏可见性
         * - 点击 "+" 按钮切换
         * - 位置: 键盘/输入法上方
         */
        var isToolbarVisible: Boolean = false,

        /**
         * 修订模式
         * - 开启时 InputMode 强制为 Hidden (Rule 1)
         * - 光标消失
         * - 支持全屏和分屏 (UI 文档 八)
         */
        var isRevisionMode: Boolean = false,

        /**
         * 目录可见性
         * - 占屏幕 2/3 (UI 文档 九)
         * - 开启时键盘隐藏
         */
        var isTocVisible: Boolean = false
    )

    // =========================================================================
    // 当前状态
    // =========================================================================

    /**
     * 当前输入模式
     */
    private var inputMode: InputMode = InputMode.Hidden

    /**
     * 当前布局模式
     */
    private var layoutMode: LayoutMode = LayoutMode.FullscreenEditor

    /**
     * 功能开关
     */
    private val featureFlags = FeatureFlags()

    /**
     * 文档句柄 (来自 Rust Core)
     */
    private var documentHandle: Long = 0L

    /**
     * E-ink 刷新控制器
     */
    val refreshController: EinkRefreshController = EinkRefreshController(this)

    /**
     * 手势编辑器 (用于修订模式)
     */
    var gestureEditor: GestureEditor? = null

    /**
     * 滚动控制器 (用于平滑滚动)
     */
    private val scroller: Scroller = Scroller(context, AccelerateDecelerateInterpolator())

    // =========================================================================
    // 布局计算常量 (对应 Supernote A6 X2 Nomad 规格)
    // =========================================================================

    companion object {
        /** 屏幕宽度 (像素) */
        const val SCREEN_WIDTH = 1404

        /** 屏幕高度 (像素) */
        const val SCREEN_HEIGHT = 1872

        /** 外接键盘标识高度 (像素) */
        const val KEYBOARD_INDICATOR_HEIGHT = 80

        /** 工具栏高度 (像素) */
        const val TOOLBAR_HEIGHT = 120

        /** 顶部工具栏高度 (像素) */
        const val TOP_TOOLBAR_HEIGHT = 100

        /** 外接键盘分屏比例: 编辑区占比 (1:1 = 50%) */
        const val SPLIT_RATIO_EXTERNAL_KEYBOARD = 0.5f

        /** 软键盘分屏比例: 编辑区占比 (4:6 = 40%) */
        const val SPLIT_RATIO_SOFT_KEYBOARD = 0.4f

        /** 目录宽度占比 (2/3) */
        const val TOC_WIDTH_RATIO = 2f / 3f
    }

    // =========================================================================
    // 缓存的布局计算结果
    // =========================================================================

    /**
     * 编辑区域边界
     */
    private val editorRect = Rect()

    /**
     * 预览区域边界
     */
    private val previewRect = Rect()

    /**
     * 工具栏区域边界
     */
    private val toolbarRect = Rect()

    /**
     * 目录区域边界
     */
    private val tocRect = Rect()

    /**
     * 顶部工具栏区域边界
     */
    private val topToolbarRect = Rect()

    // =========================================================================
    // 状态管理 (严格遵循 Rule 1, 2, 3)
    // =========================================================================

    /**
     * 设置输入模式
     *
     * Rule 1: 如果开启修订模式, 强制 InputMode 为 Hidden
     */
    fun setInputMode(mode: InputMode) {
        if (featureFlags.isRevisionMode && mode != InputMode.Hidden) {
            // 修订模式开启时, 忽略非 Hidden 模式设置
            return
        }

        if (inputMode != mode) {
            val oldMode = inputMode
            inputMode = mode

            // 模式切换需要重新计算布局
            requestLayout()

            // 模式切换触发全局刷新
            requestGlobalRefresh()

            onInputModeChanged(oldMode, mode)
        }
    }

    /**
     * 获取当前输入模式
     */
    fun getInputMode(): InputMode = inputMode

    /**
     * 设置布局模式
     *
     * Rule 2: 分屏模式下根据 InputMode 动态调整比例
     */
    fun setLayoutMode(mode: LayoutMode) {
        if (layoutMode != mode) {
            val oldMode = layoutMode
            layoutMode = mode

            // 布局模式切换需要重新计算
            requestLayout()

            // 布局切换触发全局刷新
            requestGlobalRefresh()

            onLayoutModeChanged(oldMode, mode)
        }
    }

    /**
     * 获取当前布局模式
     */
    fun getLayoutMode(): LayoutMode = layoutMode

    /**
     * 设置修订模式状态
     *
     * Rule 1: 开启修订模式时, InputMode 强制设为 Hidden
     */
    fun setRevisionMode(enabled: Boolean) {
        if (featureFlags.isRevisionMode != enabled) {
            featureFlags.isRevisionMode = enabled

            if (enabled) {
                // 开启修订时, 保存当前模式并强制切换到 Hidden
                previousInputMode = inputMode
                setInputMode(InputMode.Hidden)
            } else {
                // 关闭修订时, 恢复之前模式
                setInputMode(previousInputMode ?: InputMode.ExternalKeyboard)
            }

            // 修订模式切换触发全局刷新
            requestGlobalRefresh()

            onRevisionModeChanged(enabled)
        }
    }

    /**
     * 获取修订模式状态
     */
    fun isRevisionMode(): Boolean = featureFlags.isRevisionMode

    /**
     * 设置工具栏可见性
     *
     * 工具栏位置必须在键盘/输入法上方 (UI 文档 三.2, 四.2)
     */
    fun setToolbarVisible(visible: Boolean) {
        if (featureFlags.isToolbarVisible != visible) {
            featureFlags.isToolbarVisible = visible

            // 重新计算布局
            requestLayout()

            // 工具栏切换只触发局部刷新 (工具栏区域)
            invalidate(toolbarRect)

            onToolbarVisibilityChanged(visible)
        }
    }

    /**
     * 获取工具栏可见性
     */
    fun isToolbarVisible(): Boolean = featureFlags.isToolbarVisible

    /**
     * 设置目录可见性
     *
     * Rule 3: 目录占屏 2/3, 键盘隐藏
     */
    fun setTocVisible(visible: Boolean) {
        if (featureFlags.isTocVisible != visible) {
            featureFlags.isTocVisible = visible

            if (visible) {
                // 开启目录时, 隐藏键盘
                previousInputMode = inputMode
                setInputMode(InputMode.Hidden)
            } else {
                // 关闭目录时, 恢复之前模式
                setInputMode(previousInputMode ?: InputMode.ExternalKeyboard)
            }

            // 重新计算布局
            requestLayout()

            // 目录切换触发全局刷新
            requestGlobalRefresh()

            onTocVisibilityChanged(visible)
        }
    }

    /**
     * 获取目录可见性
     */
    fun isTocVisible(): Boolean = featureFlags.isTocVisible

    // =========================================================================
    // 保存的状态 (用于模式切换后恢复)
    // =========================================================================

    private var previousInputMode: InputMode? = null

    // =========================================================================
    // 布局计算 (核心逻辑)
    // =========================================================================

    /**
     * 计算当前布局的所有区域
     *
     * 遵循以下规则:
     * - Rule 2: 分屏比例根据 InputMode 动态调整
     * - Rule 3: 目录占 2/3 屏幕
     * - 工具栏位置: 键盘/输入法上方
     */
    private fun calculateLayouts(width: Int, height: Int) {
        // 顶部工具栏始终在顶部
        topToolbarRect.set(0, 0, width, TOP_TOOLBAR_HEIGHT)

        // 目录计算 (Rule 3)
        if (featureFlags.isTocVisible) {
            val tocWidth = (width * TOC_WIDTH_RATIO).toInt()
            tocRect.set(0, TOP_TOOLBAR_HEIGHT, tocWidth, height)
        } else {
            tocRect.setEmpty()
        }

        // 计算可用高度 (减去顶部工具栏)
        val availableHeight = height - TOP_TOOLBAR_HEIGHT
        val contentTop = TOP_TOOLBAR_HEIGHT

        // 根据布局模式计算
        when (layoutMode) {
            LayoutMode.FullscreenEditor -> {
                // 全屏编辑模式
                editorRect.set(0, contentTop, width, height)
                previewRect.setEmpty()

                // 工具栏在底部键盘上方
                calculateToolbarPosition(width, height, availableHeight)
            }

            LayoutMode.FullscreenPreview -> {
                // 全屏预览模式
                editorRect.setEmpty()
                previewRect.set(0, contentTop, width, height)
                toolbarRect.setEmpty()
            }

            LayoutMode.SplitView -> {
                // 分屏模式 (Rule 2)
                val splitRatio = when (inputMode) {
                    InputMode.ExternalKeyboard -> SPLIT_RATIO_EXTERNAL_KEYBOARD  // 1:1
                    InputMode.SoftKeyboard -> SPLIT_RATIO_SOFT_KEYBOARD          // 4:6
                    InputMode.Hidden -> 0.5f                                     // 默认 1:1
                }

                val dividerY = contentTop + (availableHeight * splitRatio).toInt()

                // 上方预览区
                previewRect.set(0, contentTop, width, dividerY)

                // 下方编辑区
                editorRect.set(0, dividerY, width, height)

                // 工具栏在编辑区内, 键盘上方
                calculateToolbarPosition(width, height, height - dividerY, dividerY)
            }
        }
    }

    /**
     * 计算工具栏位置
     *
     * 位置规则:
     * - 必须在键盘/输入法标识上方 (UI 文档 三.2, 四.2)
     */
    private fun calculateToolbarPosition(
        width: Int,
        totalHeight: Int,
        availableHeight: Int,
        offsetTop: Int = 0
    ) {
        if (!featureFlags.isToolbarVisible) {
            toolbarRect.setEmpty()
            return
        }

        // 工具栏底部位置 = 键盘标识顶部
        val toolbarBottom = when (inputMode) {
            InputMode.ExternalKeyboard -> totalHeight - KEYBOARD_INDICATOR_HEIGHT
            InputMode.SoftKeyboard -> totalHeight - KEYBOARD_INDICATOR_HEIGHT
            InputMode.Hidden -> totalHeight
        }

        // 确保工具栏完全在可见区域内
        val toolbarTop = max(offsetTop, toolbarBottom - TOOLBAR_HEIGHT)

        toolbarRect.set(0, toolbarTop, width, toolbarBottom)
    }

    // =========================================================================
    // View 生命周期
    // =========================================================================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // 尺寸变化时重新计算布局
        calculateLayouts(w, h)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 使用精确尺寸 (Supernote 固定分辨率)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        // 布局变化时重新计算区域
        calculateLayouts(width, height)
    }

    // =========================================================================
    // 渲染核心 (调用 Rust Core)
    // =========================================================================

    /**
     * 绘制视图
     *
     * 禁止在 onDraw 中分配内存 (架构约束)
     */
    override fun onDraw(canvas: Canvas) {
        android.util.Log.d("MarkdownEditorView", "!!! onDraw START !!! documentHandle=$documentHandle")

        // Always draw background first
        canvas.drawColor(0xFFFFFFFF.toInt())

        if (documentHandle == 0L) {
            // No document loaded - show welcome message
            renderWelcomeScreen(canvas)
            return
        }

        // 根据布局模式渲染不同区域
        when (layoutMode) {
            LayoutMode.FullscreenEditor -> {
                renderEditor(canvas, 0)
            }

            LayoutMode.FullscreenPreview -> {
                renderPreview(canvas, 0)
            }

            LayoutMode.SplitView -> {
                renderSplitView(canvas, 0)
            }
        }

        // 渲染工具栏
        if (featureFlags.isToolbarVisible && !toolbarRect.isEmpty) {
            renderToolbar(canvas)
        }

        // 渲染目录
        if (featureFlags.isTocVisible && !tocRect.isEmpty) {
            renderToc(canvas)
        }
    }

    /**
     * 渲染欢迎屏幕（无文档时）
     */
    private fun renderWelcomeScreen(canvas: Canvas) {
        val paint = android.graphics.Paint().apply {
            color = 0xFF000000.toInt()
            textSize = 32f
            isAntiAlias = true
        }

        val centerX = width / 2f
        val centerY = height / 2f

        // 标题
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.color = 0xFF000000.toInt()
        paint.textSize = 36f
        canvas.drawText("=== WELCOME SCREEN ===", centerX, centerY - 120, paint)
        paint.textSize = 32f
        canvas.drawText("NomadMark", centerX, centerY - 80, paint)

        // 副标题
        paint.textSize = 20f
        paint.color = 0xFF666666.toInt()
        canvas.drawText("Markdown Editor for E-ink", centerX, centerY - 40, paint)

        // 状态信息
        paint.textSize = 18f
        paint.color = 0xFF999999.toInt()
        canvas.drawText("Loading document...", centerX, centerY + 20, paint)

        // 提示信息
        paint.textSize = 16f
        paint.color = 0xFFCCCCCC.toInt()
        canvas.drawText("Open a .md file to start editing", centerX, centerY + 60, paint)
    }

    /**
     * 渲染全屏编辑器
     */
    private fun renderEditor(canvas: Canvas, canvasPtr: Long) {
        android.util.Log.d("MarkdownEditorView", "renderEditor called, editorRect: $editorRect, view size: ${width}x${height}")

        // 强制全局刷新 (E-ink 需要显式刷新)
        refreshController.requestGlobalRefresh()

        // 先清空整个屏幕
        canvas.drawColor(0xFFFFFFFF.toInt())

        canvas.save()

        if (documentHandle == 0L) {
            // 没有文档时显示欢迎界面
            renderWelcomeScreen(canvas)
        } else {
            // 使用 Rust Core 渲染 Markdown 内容
            // 计算可见行数 (基于屏幕高度和行高)
            val lineHeight = 40  // 预估行高（像素）
            val visibleLines = (height / lineHeight) + 10  // 多渲染一些行用于滚动

            android.util.Log.d("MarkdownEditorView", "Rendering with Rust Core: startLine=0, lineCount=$visibleLines")
            renderFromRustCore(canvas, 0, visibleLines)
        }

        canvas.restore()
    }

    /**
     * 使用 Rust Core 渲染指定行范围
     */
    private fun renderFromRustCore(canvas: Canvas, startLine: Int, lineCount: Int) {
        if (documentHandle == 0L) return

        try {
            // 准备输出数组
            val outCommands = LongArray(2)      // [commands_ptr, commands_count]
            val outDirtyRects = LongArray(8)     // [x, y, w, h, x, y, w, h] (最多2个矩形)
            val outTotalHeight = IntArray(1)    // [total_height]

            // 调用 Rust Core 加载渲染命令
            val result = MarkdownCore.nativeLoadRange(
                documentHandle,
                startLine,
                lineCount,
                outCommands,
                outDirtyRects,
                outTotalHeight
            )

            if (result == 0) {
                val commandsPtr = outCommands[0]
                val commandsCount = outCommands[1].toInt()

                android.util.Log.d("MarkdownEditorView", "Rust Core rendered: commandsPtr=$commandsPtr, count=$commandsCount")

                if (commandsPtr != 0L && commandsCount > 0) {
                    // 执行渲染命令
                    renderCommandExecutor.execute(commandsPtr, commandsCount, canvas)

                    // 释放 Rust 分配的命令内存
                    MarkdownCore.nativeFreeCommands(commandsPtr, commandsCount)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MarkdownEditorView", "Error rendering from Rust Core", e)
        }
    }

    /**
     * 渲染全屏预览
     */
    private fun renderPreview(canvas: Canvas, canvasPtr: Long) {
        canvas.save()
        canvas.clipRect(previewRect)
        canvas.drawColor(0xFFFFFFFF.toInt())

        if (documentHandle == 0L) {
            // 没有文档时显示提示
            val paint = android.graphics.Paint().apply {
                color = 0xFFFF0000.toInt()
                textSize = 28f
                isAntiAlias = true
            }
            canvas.drawText("No document loaded", 50f, 200f, paint)
        } else {
            // 使用 Rust Core 渲染 Markdown 内容
            renderFromRustCore(canvas, 0, 100)
        }

        canvas.restore()
    }

    /**
     * 渲染分屏视图
     */
    private fun renderSplitView(canvas: Canvas, canvasPtr: Long) {
        // 先渲染上方预览区
        canvas.save()
        canvas.clipRect(previewRect)
        canvas.drawColor(0xFFFFFFFF.toInt())

        val paint = android.graphics.Paint().apply {
            color = 0xFF000000.toInt()
            textSize = 24f
            isAntiAlias = true
        }
        canvas.drawText("Preview Area (Top)", 50f, 100f, paint)
        paint.textSize = 18f
        paint.color = 0xFF999999.toInt()
        canvas.drawText("Rendered Markdown preview appears here", 50f, 130f, paint)
        canvas.restore()

        // 再渲染下方编辑区
        canvas.save()
        canvas.clipRect(editorRect)
        canvas.drawColor(0xFFF5F5F5.toInt())
        paint.color = 0xFF000000.toInt()
        paint.textSize = 24f
        canvas.drawText("Editor Area (Bottom)", 50f, (editorRect.top + 100).toFloat(), paint)
        paint.textSize = 18f
        paint.color = 0xFF999999.toInt()
        canvas.drawText("Type your Markdown here", 50f, (editorRect.top + 130).toFloat(), paint)
        canvas.restore()
    }

    /**
     * 渲染工具栏
     *
     * 禁止在 onDraw 中分配对象
     */
    private fun renderToolbar(canvas: Canvas) {
        // 绘制工具栏背景
        canvas.save()
        canvas.clipRect(toolbarRect)
        canvas.drawColor(0xF0F0F0.toInt())

        // 绘制工具栏按钮 (预分配的 Paint)
        // ... (具体按钮绘制逻辑)

        canvas.restore()
    }

    /**
     * 渲染目录
     *
     * 禁止在 onDraw 中分配对象
     */
    private fun renderToc(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(tocRect)
        canvas.drawColor(0xFAFAFA.toInt())

        // 获取目录数据
        val tocEntries = MarkdownCore.nativeGetToc(documentHandle)

        // 渲染目录条目
        // ... (具体目录渲染逻辑)

        canvas.restore()
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    /**
     * 创建渲染命令
     *
     * @param target 渲染目标
     * @param rect 目标区域
     * @return NativeRenderCommands
     */
    private fun createRenderCommands(
        target:RenderTarget,
        rect: Rect
    ): NativeRenderCommands {
        // 创建渲染命令 (实际实现在 Rust Core 中)
        // 这里返回一个占位符
        return NativeRenderCommands(0L)
    }

    /**
     * 处理脏矩形
     *
     * 将 Rust Core 返回的脏矩形传递给刷新控制器
     */
    private fun processDirtyRects(dirtyRects: IntArray) {
        // IntArray 格式: [x, y, w, h, x, y, w, h, ...]
        if (dirtyRects.isEmpty()) {
            // 如果没有脏矩形，请求全局刷新
            requestGlobalRefresh()
            return
        }
        var i = 0
        while (i < dirtyRects.size) {
            val rect = Rect(
                dirtyRects[i],
                dirtyRects[i + 1],
                dirtyRects[i] + dirtyRects[i + 2],
                dirtyRects[i + 1] + dirtyRects[i + 3]
            )
            refreshController.addDirty(rect)
            i += 4
        }
    }

    /**
     * 请求全局刷新
     *
     * 用于分屏切换、模式切换等场景
     */
    private fun requestGlobalRefresh() {
        refreshController.requestGlobalRefresh()
    }

    /**
     * 获取 Canvas 原生指针
     *
     * 通过 JNI 获取 Canvas 的底层指针
     */
    private external fun getCanvasNativePtr(canvas: Canvas): Long

    // =========================================================================
    // 文档管理
    // =========================================================================

    /**
     * 设置文档句柄
     *
     * @param handle 来自 MarkdownCore.nativeCreate() 或 nativeCreateFromPath()
     */
    fun setDocumentHandle(handle: Long) {
        android.util.Log.d("MarkdownEditorView", "setDocumentHandle called with: $handle")
        documentHandle = handle
        android.util.Log.d("MarkdownEditorView", "documentHandle is now: $documentHandle")
        invalidate()
        android.util.Log.d("MarkdownEditorView", "invalidate() called")
    }

    /**
     * 获取文档句柄
     */
    fun getDocumentHandle(): Long = documentHandle

    // =========================================================================
    // Gesture Editor 接口 (用于修订模式)
    // =========================================================================

    /**
     * 将屏幕坐标转换为文档偏移量 (行, 列)
     */
    fun coordinateToOffset(x: Float, y: Float): Pair<Int, Int> {
        // 简化实现：根据 y 坐标估算行号
        val lineHeight = 20  // 假设每行 20 像素
        val line = (y / lineHeight).toInt().coerceAtLeast(0)
        val column = 0  // 列号需要更复杂的计算
        return Pair(line, column)
    }

    /**
     * 删除指定行
     */
    fun deleteLine(line: Int) {
        // 调用 Rust Core 删除行
        if (documentHandle != 0L) {
            // TODO: 实现 nativeDeleteLine
            refreshController.requestGlobalRefresh()
        }
    }

    /**
     * 在指定行插入文本
     */
    fun insertAtLine(line: Int, text: String) {
        // 调用 Rust Core 插入文本
        if (documentHandle != 0L) {
            // TODO: 实现 nativeInsertAtLine
            refreshController.requestGlobalRefresh()
        }
    }

    /**
     * 选择指定矩形区域
     */
    fun selectRange(rect: Rect) {
        // 实现选择逻辑
        // TODO: 实现 selection 渲染
        invalidate(rect)
    }

    /**
     * 获取指定行的边界矩形
     */
    fun getLineBoundingRect(line: Int): Rect {
        val lineHeight = 20
        return Rect(
            0,  // left
            line * lineHeight,  // top
            width,  // right
            (line + 1) * lineHeight  // bottom
        )
    }

    // =========================================================================
    // 回调接口 (用于 Activity/Fragment 监听状态变化)
    // =========================================================================

    /**
     * 输入模式变化回调
     */
    private fun onInputModeChanged(oldMode: InputMode, newMode: InputMode) {
        // 通知监听器
    }

    /**
     * 布局模式变化回调
     */
    private fun onLayoutModeChanged(oldMode: LayoutMode, newMode: LayoutMode) {
        // 通知监听器
    }

    /**
     * 修订模式变化回调
     */
    private fun onRevisionModeChanged(enabled: Boolean) {
        // 通知监听器
    }

    /**
     * 工具栏可见性变化回调
     */
    private fun onToolbarVisibilityChanged(visible: Boolean) {
        // 通知监听器
    }

    /**
     * 目录可见性变化回调
     */
    private fun onTocVisibilityChanged(visible: Boolean) {
        // 通知监听器
    }

    // =========================================================================
    // 渲染目标枚举
    // =========================================================================

    private enum class RenderTarget {
        Editor,
        Preview
    }
}
