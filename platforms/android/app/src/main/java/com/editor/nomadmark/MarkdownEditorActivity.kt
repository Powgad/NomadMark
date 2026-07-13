package com.editor.nomadmark

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.io.File

import com.editor.nomadmark.GestureType
import com.editor.nomadmark.SearchHighlightSpan
import com.editor.nomadmark.SearchHighlightBackgroundSpan

import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.AbstractMarkwonPlugin
import android.graphics.Color
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.image.ImagesPlugin
import android.text.Spanned
import android.text.style.URLSpan
import android.text.style.UnderlineSpan

/**
 * Markdown Editor Activity
 *
 * 基于《UI交互文档》和《详细设计文档》实现的完整编辑器
 *
 * 功能:
 * - 编辑/预览模式切换
 * - 分屏模式
 * - 顶部工具栏 (返回、目录、文件名、预览、修订、搜索、分屏、撤销/重做、按键设置、保存)
 * - 底部快捷栏 (加粗、斜体、标题、代码、链接等)
 * - 搜索和替换
 * - 目录导航
 * - 防丢失保护
 */
class MarkdownEditorActivity : android.app.Activity() {

    // =========================================================================
    // UI 组件
    // =========================================================================

    // 顶部工具栏按钮
    private lateinit var btnBack: ImageButton
    private lateinit var btnToc: ImageButton
    private lateinit var btnOpenFile: ImageButton
    private lateinit var textFilename: TextView
    private lateinit var btnPreviewToggle: ImageButton
    private lateinit var btnRevision: ImageButton
    private lateinit var btnSearch: ImageButton
    private lateinit var btnSplit: ImageButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnToolbarToggle: ImageButton
    private lateinit var btnKeyboardSettings: ImageButton
    private lateinit var btnSave: ImageButton

    // 搜索栏
    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var btnSearchSingle: ImageButton
    private lateinit var btnSearchAll: ImageButton
    private lateinit var btnSearchPrev: ImageButton
    private lateinit var btnSearchNext: ImageButton
    private lateinit var btnSearchClose: ImageButton
    private lateinit var replaceRow: LinearLayout
    private lateinit var replaceInput: EditText
    private lateinit var btnReplaceOne: Button
    private lateinit var btnReplaceAll: Button

    // =========================================================================
    // 新架构：独立层级视图
    // =========================================================================

    // 层1：编辑层
    private lateinit var editorLayer: ScrollView
    private lateinit var editorText: EditText

    // 层2：预览层
    private lateinit var previewLayer: ScrollView
    private lateinit var previewText: TextView

    // 层3：分屏层
    private lateinit var splitLayer: LinearLayout
    private lateinit var splitPreviewScroll: ObservableScrollView
    private lateinit var splitPreviewText: TextView
    private lateinit var splitEditorScroll: ObservableScrollView
    private lateinit var splitEditorText: EditText

    // 层4：手势层
    private lateinit var gestureLayer: GestureOverlayView
    private lateinit var gestureEditor: GestureEditor

    // 底部快捷栏
    private lateinit var toolbarBottom: HorizontalScrollView
    private lateinit var btnBold: Button
    private lateinit var btnItalic: Button
    private lateinit var btnHeadingUp: Button
    private lateinit var btnHeadingDown: Button
    private lateinit var btnCodeInline: Button
    private lateinit var btnCodeBlock: Button
    private lateinit var btnLink: Button
    private lateinit var btnList: Button
    private lateinit var btnQuote: Button
    private lateinit var btnTable: Button
    private lateinit var btnHr: Button
    private lateinit var btnFormula: Button

    // 目录面板
    private lateinit var tocPanel: FrameLayout
    private lateinit var tocList: ListView
    private lateinit var tocCloseArea: View

    // 键盘标识
    private lateinit var keyboardIndicator: TextView

    // =========================================================================
    // 辅助组件
    // =========================================================================

    /** 键盘检测器 */
    private val keyboardDetector: KeyboardDetector by lazy { KeyboardDetector(this) }

    /** 文件操作辅助 */
    private val fileOperationHelper: FileOperationHelper by lazy { FileOperationHelper(this) }

    /** 滚动同步管理器 */
    private var scrollSyncManager: ScrollSyncManager? = null

    // =========================================================================
    // Core 文档集成
    // =========================================================================

    /** Core 文档句柄 */
    private var rustCoreDocumentHandle: Long = 0L

    /** 是否使用 Rust Core 搜索/替换 */
    private var useRustCoreSearch = true

    /** 当前搜索的文档内容（用于计算上下文） */
    private var currentSearchContent: String = ""

    // =========================================================================
    // 渲染引擎设置
    // =========================================================================

    /**
     * 渲染引擎枚举
     */
    enum class RenderEngine {
        /** Markwon 渲染引擎 */
        MARKWON,
        /** Rust Core 渲染引擎 */
        RUST_CORE
    }

    /** 当前选择的渲染引擎 */
    private var renderEngine: RenderEngine = RenderEngine.MARKWON

    /** SharedPreferences 用于存储用户偏好 */
    private lateinit var prefs: SharedPreferences

    // =========================================================================
    // Markwon 渲染器
    // =========================================================================

    private lateinit var markwon: Markwon

    // =========================================================================
    // 状态变量
    // =========================================================================

    /** 当前文件路径 */
    private var filePath: String? = null

    /** 当前文件名 */
    private var fileName: String? = null

    /** 是否已修改 (用于防丢失保护) */
    private var isModified: Boolean = false

    /** 是否为预览模式 */
    private var isPreviewMode: Boolean = false

    /** 是否为分屏模式 */
    private var isSplitMode: Boolean = false

    /** 是否为修订模式 */
    private var isRevisionMode: Boolean = false

    /** 是否显示工具栏 */
    private var isToolbarVisible: Boolean = false

    /** 撤销/重做栈 */
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    /** 保存状态前的文本内容 */
    private var lastSavedContent: String = ""

    /** 搜索结果列表 */
    private var searchResults = mutableListOf<Pair<Int, Int>>() // start, end
    private var currentSearchIndex = 0

    /** 预览层中的实际匹配项数量（可能与 searchResults.size 不同） */
    private var previewMatchCount = 0

    /** 当前搜索模式：true=单个搜索，false=全部搜索 */
    private var isSingleSearchMode = true

    /** 是否正在执行撤销/重做操作（用于防止 textWatcher 重复保存状态） */
    private var isUndoingOrRedoing = false

    // =========================================================================
    // 生命周期
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        // 获取屏幕宽度
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels

        // 初始化 SharedPreferences
        prefs = getSharedPreferences("NomadMarkPrefs", MODE_PRIVATE)

        // 加载用户偏好设置
        loadUserPreferences()

        // 初始化 Markwon 渲染器
        initMarkwon()

        // 初始化 UI
        initViews()

        // 设置监听器
        setupListeners()

        // 处理打开的文件
        handleOpenIntent(intent)

        // 检测外接键盘状态
        detectKeyboardStatus()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    override fun onBackPressed() {
        // 防丢失保护
        if (isModified) {
            showSaveBeforeExitDialog()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        // 防丢失保护 - 如果有修改，提示保存
        if (isModified) {
            // 实际应用中这里应该保存状态
            Log.d("MarkdownEditorActivity", "Activity paused with unsaved changes")
        }
    }

    // =========================================================================
    // 初始化
    // =========================================================================

    private fun initMarkwon() {
        markwon = Markwon.builder(this)
            .usePlugin(CorePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(TaskListPlugin.create(this))
            .usePlugin(ImagesPlugin.create())
            // 添加行内解析器（支持行内数学公式）
            .usePlugin(MarkwonInlineParserPlugin.create())
            // 数学公式渲染（JLatexMath）
            .usePlugin(JLatexMathPlugin.create(32f, object : JLatexMathPlugin.BuilderConfigure {
                override fun configureBuilder(builder: JLatexMathPlugin.Builder) {
                    // 启用行内公式（需要 $$...$$ 语法）
                    builder.inlinesEnabled(true)
                }
            }))
            // 添加自定义主题配置，移除标题下划线和链接下划线
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    // 移除 H1 和 H2 标题下方的分隔线（"下划线"效果）
                    builder.headingBreakHeight(0)
                    // 禁用链接下划线
                    builder.isLinkUnderlined(false)
                }
            })
            .build()
    }

    /**
     * 代码块边框信息
     * 用于存储代码块的范围和绘制状态
     */
    private class CodeBlockInfo(
        val blockStart: Int,    // 代码块在文本中的起始位置
        val blockEnd: Int,      // 代码块在文本中的结束位置
        val firstLine: Int,     // 第一行的行号
        val lastLine: Int       // 最后一行的行号
    )

    /** 存储所有代码块的信息 */
    private val codeBlockInfoList = mutableListOf<CodeBlockInfo>()

    /** 屏幕宽度（用于计算代码块边框） */
    private var screenWidth: Int = 0

    /** 代码块边框的水平边距（dp） */
    private val codeBlockHorizontalMarginDp = 48f  // 增加到 48dp 以防止滑动容器覆盖边框

    /**
     * 自定义代码块边框 Span
     * 使用 LineBackgroundSpan 并根据屏幕宽度绘制完整边框
     */
    private class CodeBlockBorderSpan(
        private val isFirstLine: Boolean,
        private val isLastLine: Boolean,
        private val screenWidth: Int,
        private val horizontalMarginPx: Int
    ) : LineBackgroundSpan {

        override fun drawBackground(
            canvas: Canvas,
            paint: Paint,
            left: Int,
            right: Int,
            top: Int,
            baseline: Int,
            bottom: Int,
            text: CharSequence,
            start: Int,
            end: Int,
            lineNumber: Int
        ) {
            val originalColor = paint.color
            val originalStyle = paint.style
            val originalWidth = paint.strokeWidth

            // 设置边框样式
            paint.color = android.graphics.Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f  // 加粗边框使其更明显

            val padding = 4
            // 左边框：从文本左边减去 padding
            val borderLeft = (left - padding).coerceAtLeast(0).toFloat()
            // 右边框：始终使用屏幕宽度减去边距
            val borderRight = (screenWidth - horizontalMarginPx).toFloat()
            val borderTop = (top - padding).coerceAtLeast(0).toFloat()
            val borderBottom = (bottom + padding).toFloat()

            // 计算边框的绘制区域（确保在可见区域内）
            val canvasWidth = canvas.width

            // 确定行类型用于日志
            val lineType = when {
                isFirstLine && isLastLine -> "SINGLE"
                isFirstLine -> "FIRST"
                isLastLine -> "LAST"
                else -> "MIDDLE"
            }

            android.util.Log.d("CodeBlockBorder", "Drawing $lineType line: left=$borderLeft, right=$borderRight, top=$borderTop, bottom=$borderBottom, screenWidth=$screenWidth, canvasWidth=$canvasWidth, textRight=$right")

            if (canvasWidth > 0 && borderRight > canvasWidth) {
                // 如果右边框超出 Canvas，调整到 Canvas 边缘
                val adjustedBorderRight = (canvasWidth - padding).toFloat()
                android.util.Log.d("CodeBlockBorder", "Adjusted right border from $borderRight to $adjustedBorderRight")
                when {
                    isFirstLine && isLastLine -> {
                        canvas.drawRect(borderLeft, borderTop, adjustedBorderRight, borderBottom, paint)
                    }
                    isFirstLine -> {
                        canvas.drawLine(borderLeft, borderTop, adjustedBorderRight, borderTop, paint)
                        canvas.drawLine(borderLeft, borderTop, borderLeft, borderBottom, paint)
                        canvas.drawLine(adjustedBorderRight, borderTop, adjustedBorderRight, borderBottom, paint)
                    }
                    isLastLine -> {
                        canvas.drawLine(borderLeft, borderTop, borderLeft, borderBottom, paint)
                        canvas.drawLine(adjustedBorderRight, borderTop, adjustedBorderRight, borderBottom, paint)
                        canvas.drawLine(borderLeft, borderBottom, adjustedBorderRight, borderBottom, paint)
                    }
                    else -> {
                        canvas.drawLine(borderLeft, borderTop.toFloat(), borderLeft, borderBottom.toFloat(), paint)
                        canvas.drawLine(adjustedBorderRight, borderTop.toFloat(), adjustedBorderRight, borderBottom.toFloat(), paint)
                    }
                }
            } else {
                // 正常绘制
                when {
                    isFirstLine && isLastLine -> {
                        android.util.Log.d("CodeBlockBorder", "Drawing single line rect")
                        canvas.drawRect(borderLeft, borderTop, borderRight, borderBottom, paint)
                    }
                    isFirstLine -> {
                        android.util.Log.d("CodeBlockBorder", "Drawing first line: top border + sides")
                        canvas.drawLine(borderLeft, borderTop, borderRight, borderTop, paint)
                        canvas.drawLine(borderLeft, borderTop, borderLeft, borderBottom, paint)
                        canvas.drawLine(borderRight, borderTop, borderRight, borderBottom, paint)
                    }
                    isLastLine -> {
                        android.util.Log.d("CodeBlockBorder", "Drawing last line: bottom border + sides")
                        canvas.drawLine(borderLeft, borderTop, borderLeft, borderBottom, paint)
                        canvas.drawLine(borderRight, borderTop, borderRight, borderBottom, paint)
                        canvas.drawLine(borderLeft, borderBottom, borderRight, borderBottom, paint)
                    }
                    else -> {
                        canvas.drawLine(borderLeft, borderTop.toFloat(), borderLeft, borderBottom.toFloat(), paint)
                        canvas.drawLine(borderRight, borderTop.toFloat(), borderRight, borderBottom.toFloat(), paint)
                    }
                }
            }

            // 恢复原始样式
            paint.color = originalColor
            paint.style = originalStyle
            paint.strokeWidth = originalWidth
        }
    }

    /**
     * 为渲染后的代码添加边框样式
     * 需要在渲染后调用此方法
     */
    private fun applyCodeBlockBorder(spanned: Spanned) {
        val spannable = spanned as Spannable
        codeBlockInfoList.clear()

        // 调试：打印所有 span 类型
        val allSpans = (0 until spanned.length).flatMap { i ->
            val spans = spannable.getSpans(i, i + 1, Any::class.java)
            spans.map { it.javaClass.simpleName to it }
        }.distinctBy { it.first }
        Log.d("CodeBlockBorder", "All span types: ${allSpans.map { it.first }.distinct()}")

        // 查找代码块相关的 span
        var foundCodeBlocks = false

        // 尝试查找 Markwon 的代码块 span
        try {
            val codeBlockSpanClass = Class.forName(CODE_BLOCK_SPAN)
            val codeSpans = spannable.getSpans(0, spanned.length, codeBlockSpanClass)
            Log.d("CodeBlockBorder", "Found ${codeSpans.size} CodeBlockSpan")

            for (span in codeSpans) {
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                val flags = spannable.getSpanFlags(span)
                Log.d("CodeBlockBorder", "CodeBlock at [$start-$end]")

                // 为代码块的每一行应用边框
                applyBorderToLines(spannable, start, end, flags)
                foundCodeBlocks = true
            }
        } catch (e: ClassNotFoundException) {
            Log.e("CodeBlockBorder", "CodeBlockSpan class not found", e)
        }

        // 查找 FencedCodeBlock 相关的 span
        try {
            val fencedCodeClass = Class.forName(FENCED_CODE_BLOCK_SPAN)
            val fencedSpans = spannable.getSpans(0, spanned.length, fencedCodeClass)
            Log.d("CodeBlockBorder", "Found ${fencedSpans.size} FencedCodeBlockSpan")

            for (span in fencedSpans) {
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                val flags = spannable.getSpanFlags(span)
                Log.d("CodeBlockBorder", "FencedCodeBlock at [$start-$end]")

                // 为代码块的每一行应用边框
                applyBorderToLines(spannable, start, end, flags)
                foundCodeBlocks = true
            }
        } catch (e: ClassNotFoundException) {
            Log.e("CodeBlockBorder", "FencedCodeBlockSpan class not found", e)
        }

        // 如果没找到专门的代码块 span，尝试使用背景色
        if (!foundCodeBlocks) {
            val bgSpans = spannable.getSpans(0, spanned.length, android.text.style.BackgroundColorSpan::class.java)
            Log.d("CodeBlockBorder", "Found ${bgSpans.size} BackgroundColorSpan, using as code blocks")

            // 将相邻的背景色 span 合并为代码块
            mergeAdjacentBackgroundSpans(spannable, bgSpans)
        }
    }

    /**
     * 为代码块的每一行应用边框 Span
     */
    private fun applyBorderToLines(spannable: Spannable, blockStart: Int, blockEnd: Int, flags: Int) {
        // 转换 dp 到 px
        val density = resources.displayMetrics.density
        val horizontalMarginPx = (codeBlockHorizontalMarginDp * density).toInt()

        // 遍历代码块中的每一行
        var currentPos = blockStart
        var lineIndex = 0
        val linePositions = mutableListOf<Pair<Int, Int>>() // 存储每行的起始和结束位置

        while (currentPos < blockEnd) {
            // 找到下一个换行符或块结束
            val lineEnd = spannable.indexOf("\n", currentPos).let {
                if (it == -1 || it > blockEnd) blockEnd else it + 1
            }
            linePositions.add(currentPos to lineEnd.coerceAtMost(blockEnd))
            currentPos = lineEnd
            lineIndex++
        }

        if (linePositions.isEmpty()) {
            linePositions.add(blockStart to blockEnd)
        }

        // 为每一行应用边框，传递屏幕宽度和边距
        for (i in linePositions.indices) {
            val (lineStart, lineEnd) = linePositions[i]
            val isFirstLine = (i == 0)
            val isLastLine = (i == linePositions.size - 1)

            spannable.setSpan(
                CodeBlockBorderSpan(isFirstLine, isLastLine, screenWidth, horizontalMarginPx),
                lineStart,
                lineEnd,
                flags
            )
        }

        Log.d("CodeBlockBorder", "Applied border to ${linePositions.size} lines in range [$blockStart-$blockEnd], screenWidth=$screenWidth")
    }

    /**
     * 合并相邻的背景色 span 并应用边框
     */
    private fun mergeAdjacentBackgroundSpans(spannable: Spannable, bgSpans: Array<android.text.style.BackgroundColorSpan>) {
        if (bgSpans.isEmpty()) return

        // 按起始位置排序
        val sortedSpans = bgSpans.sortedBy { spannable.getSpanStart(it) }

        // 合并相邻的 span
        val mergedRanges = mutableListOf<Pair<Int, Int>>()
        var currentStart = spannable.getSpanStart(sortedSpans[0])
        var currentEnd = spannable.getSpanEnd(sortedSpans[0])

        for (i in 1 until sortedSpans.size) {
            val span = sortedSpans[i]
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)

            // 检查是否相邻（间隔小于 2 个字符）
            if (start - currentEnd <= 2) {
                currentEnd = maxOf(currentEnd, end)
            } else {
                mergedRanges.add(currentStart to currentEnd)
                currentStart = start
                currentEnd = end
            }
        }
        mergedRanges.add(currentStart to currentEnd)

        // 移除所有背景色 span（避免覆盖边框）
        for (bgSpan in bgSpans) {
            spannable.removeSpan(bgSpan)
        }
        Log.d("CodeBlockBorder", "Removed ${bgSpans.size} BackgroundColorSpan to avoid border overlap")

        // 为每个合并后的范围应用边框
        for ((start, end) in mergedRanges) {
            val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            applyBorderToLines(spannable, start, end, flags)
        }

        Log.d("CodeBlockBorder", "Merged ${bgSpans.size} BackgroundColorSpan into ${mergedRanges.size} code blocks")
    }

    /**
     * 移除文本中的下划线并确保分割线可见
     * 用于标题等不应有下划线的文本
     */
    private fun removeUnderlines(spanned: Spanned) {
        val spannable = spanned as android.text.Spannable

        // 获取所有 span 类型用于调试
        val allSpans = (0 until spanned.length).flatMap { i ->
            val spans = spanned.getSpans(i, i + 1, Any::class.java)
            spans.map { it.javaClass.name }
        }.distinct()
        Log.d("removeUnderlines", "All span types: $allSpans")

        // 处理 Markwon 的 LinkSpan（Markwon 4.6.2 使用自定义 LinkSpan）
        try {
            val linkSpanClass = Class.forName(LINK_SPAN)
            val linkSpans = spannable.getSpans(0, spanned.length, linkSpanClass)
            if (linkSpans.isNotEmpty()) {
                Log.d("removeUnderlines", "Found ${linkSpans.size} LinkSpans")
                // 获取 destination 方法用于提取 URL
                val destinationMethod = linkSpanClass.getDeclaredMethod("destination")
                destinationMethod.isAccessible = true

                for (span in linkSpans) {
                    val start = spannable.getSpanStart(span)
                    val end = spannable.getSpanEnd(span)
                    val flags = spannable.getSpanFlags(span)
                    spannable.removeSpan(span)

                    // 从原始 LinkSpan 中提取 URL
                    val url = try {
                        destinationMethod.invoke(span) as? String ?: "about:blank"
                    } catch (e: Exception) {
                        Log.e("removeUnderlines", "Failed to extract URL from LinkSpan", e)
                        "about:blank"
                    }

                    // 创建不带下划线的自定义 Span，保留原始 URL
                    val noUnderlineSpan = object : android.text.style.URLSpan(url) {
                        override fun updateDrawState(ds: android.text.TextPaint) {
                            super.updateDrawState(ds)
                            ds.isUnderlineText = false
                            // 保留原始链接颜色
                            ds.color = ds.linkColor
                        }
                    }
                    spannable.setSpan(noUnderlineSpan, start, end, flags)
                }
            }
        } catch (e: ClassNotFoundException) {
            Log.e("removeUnderlines", "LinkSpan class not found", e)
        } catch (e: NoSuchMethodException) {
            Log.e("removeUnderlines", "LinkSpan.destination method not found", e)
        }

        // 处理标准 URLSpan（兼容处理）
        val urlSpans = spannable.getSpans(0, spanned.length, android.text.style.URLSpan::class.java)
        Log.d("removeUnderlines", "Found ${urlSpans.size} URLSpans")
        for (span in urlSpans) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            val flags = spannable.getSpanFlags(span)
            spannable.removeSpan(span)
            // 创建不带下划线的 URLSpan
            val noUnderlineSpan = object : android.text.style.URLSpan(span.url) {
                override fun updateDrawState(ds: android.text.TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                }
            }
            spannable.setSpan(noUnderlineSpan, start, end, flags)
        }

        // 移除 UnderlineSpan
        val underlineSpans = spannable.getSpans(0, spanned.length, android.text.style.UnderlineSpan::class.java)
        Log.d("removeUnderlines", "Found ${underlineSpans.size} UnderlineSpans")
        for (span in underlineSpans) {
            spannable.removeSpan(span)
        }

        // 确保分割线可见 - 检查 ThematicBreakSpan
        try {
            val thematicBreakClass = Class.forName(THEMATIC_BREAK_SPAN)
            @Suppress("UNCHECKED_CAST")
            val thematicBreakSpans = spanned.getSpans(0, spanned.length, thematicBreakClass) as Array<Any>
            if (thematicBreakSpans.isNotEmpty()) {
                Log.d("removeUnderlines", "Found ${thematicBreakSpans.size} ThematicBreakSpans")
                for (span in thematicBreakSpans) {
                    val start = spanned.getSpanStart(span)
                    val end = spanned.getSpanEnd(span)
                    Log.d("removeUnderlines", "ThematicBreakSpan at [$start-$end]")
                }
            }
        } catch (e: ClassNotFoundException) {
            // ThematicBreakSpan 不存在，跳过
        }
    }

    private fun initViews() {
        // 顶部工具栏
        btnBack = findViewById(R.id.btn_back)
        btnToc = findViewById(R.id.btn_toc)
        btnOpenFile = findViewById(R.id.btn_open_file)
        textFilename = findViewById(R.id.text_filename)
        btnPreviewToggle = findViewById(R.id.btn_preview_toggle)
        btnRevision = findViewById(R.id.btn_revision)
        btnSearch = findViewById(R.id.btn_search)
        btnSplit = findViewById(R.id.btn_split)
        btnUndo = findViewById(R.id.btn_undo)
        btnRedo = findViewById(R.id.btn_redo)
        btnToolbarToggle = findViewById(R.id.btn_toolbar_toggle)
        btnKeyboardSettings = findViewById(R.id.btn_keyboard_settings)
        btnSave = findViewById(R.id.btn_save)

        // 搜索栏
        searchBar = findViewById(R.id.search_bar)
        searchInput = findViewById(R.id.search_input)
        btnSearchSingle = findViewById(R.id.btn_search_single)
        btnSearchAll = findViewById(R.id.btn_search_all)
        btnSearchPrev = findViewById(R.id.btn_search_prev)
        btnSearchNext = findViewById(R.id.btn_search_next)
        btnSearchClose = findViewById(R.id.btn_search_close)
        replaceRow = findViewById(R.id.replace_row)
        replaceInput = findViewById(R.id.replace_input)
        btnReplaceOne = findViewById(R.id.btn_replace_one)
        btnReplaceAll = findViewById(R.id.btn_replace_all)

        // =========================================================================
        // 初始化层级视图
        // =========================================================================

        // 层1：编辑层
        editorLayer = findViewById(R.id.editor_layer)
        editorText = findViewById(R.id.editor_text)

        // 层2：预览层
        previewLayer = findViewById(R.id.preview_layer)
        previewText = findViewById(R.id.preview_text)

        // 层3：分屏层
        splitLayer = findViewById(R.id.split_layer)
        splitPreviewScroll = findViewById(R.id.split_preview_scroll)
        splitPreviewText = findViewById(R.id.split_preview_text)
        splitEditorScroll = findViewById(R.id.split_editor_scroll)
        splitEditorText = findViewById(R.id.split_editor_text)

        // 层4：手势层
        gestureLayer = findViewById(R.id.gesture_layer)

        // 底部快捷栏
        toolbarBottom = findViewById(R.id.toolbar_bottom)
        btnBold = findViewById(R.id.btn_bold)
        btnItalic = findViewById(R.id.btn_italic)
        btnHeadingUp = findViewById(R.id.btn_heading_up)
        btnHeadingDown = findViewById(R.id.btn_heading_down)
        btnCodeInline = findViewById(R.id.btn_code_inline)
        btnCodeBlock = findViewById(R.id.btn_code_block)
        btnLink = findViewById(R.id.btn_link)
        btnList = findViewById(R.id.btn_list)
        btnQuote = findViewById(R.id.btn_quote)
        btnTable = findViewById(R.id.btn_table)
        btnHr = findViewById(R.id.btn_hr)
        btnFormula = findViewById(R.id.btn_formula)

        // 目录
        tocPanel = findViewById(R.id.toc_panel)
        tocList = findViewById(R.id.toc_list)
        tocCloseArea = findViewById(R.id.toc_close_area)

        // 键盘标识
        keyboardIndicator = findViewById(R.id.keyboard_indicator)

        // 设置手势层
        setupGestureLayer()

        // 设置等宽字体
        val monoFont = Typeface.MONOSPACE
        editorText.typeface = monoFont
        splitEditorText.typeface = monoFont

        // 初始化光标状态：编辑模式下默认显示光标
        editorText.isCursorVisible = true
        splitEditorText.isCursorVisible = true

        // 确保编辑器获得焦点以显示光标
        editorText.requestFocus()
    }

    private fun setupGestureLayer() {
        // 创建手势编辑器
        gestureEditor = GestureEditor(null)  // 暂时传 null，后续可以传 MarkdownEditorView

        // 设置手势识别回调
        gestureLayer.onGestureRecognized = { result ->
            when (result.gestureType) {
                GestureType.DELETE -> {
                    // 删除手势
                    // 在分屏模式下，根据触摸位置判断操作区域
                    if (currentDisplayMode == DisplayMode.SPLIT) {
                        val touchY = result.keyPoint.y
                        val splitHeight = splitLayer.height.toFloat()
                        val splitRatio = 0.6f  // 预览区占 60%
                        val dividerY = splitLayer.top + splitHeight * splitRatio

                        if (touchY < dividerY) {
                            // 上半区（预览区）操作 - 同步到编辑区
                            Log.d("GestureLayer", "Delete in preview area (top)")
                            Toast.makeText(this, "预览区删除（已同步）", Toast.LENGTH_SHORT).show()
                        } else {
                            // 下半区（编辑区）操作
                            Log.d("GestureLayer", "Delete in editor area (bottom)")
                            gestureEditor.deleteTextRange(result.boundingBox, splitEditorText)
                            markAsModified()
                            Toast.makeText(this, "已删除选中内容", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // 非分屏模式，直接操作当前编辑器
                        gestureEditor.deleteTextRange(result.boundingBox, getCurrentEditor())
                        markAsModified()
                        Toast.makeText(this, "已删除选中内容", Toast.LENGTH_SHORT).show()
                    }
                }
                GestureType.INSERT -> {
                    // 插入手势（暂未实现）
                    Toast.makeText(this, "插入功能开发中", Toast.LENGTH_SHORT).show()
                }
                GestureType.SELECT -> {
                    // 选择手势（暂未实现）
                    Toast.makeText(this, "选择功能开发中", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 设置手势拒绝回调
        gestureLayer.onGestureRejected = {
            // 可以在这里添加震动反馈等
            Log.d("MarkdownEditorActivity", "Gesture not recognized")
        }

        // 初始状态为禁用（修订模式开启时启用）
        gestureLayer.isGestureEnabled = false

        Log.d("MarkdownEditorActivity", "GestureLayer setup complete")
    }

    private fun setupListeners() {
        // 顶部工具栏
        btnBack.setOnClickListener { finishWithSaveCheck() }
        btnToc.setOnClickListener { toggleToc() }
        btnOpenFile.setOnClickListener { showOpenFileDialog() }
        // textFilename - 仅显示文件名，不可点击
        btnPreviewToggle.setOnClickListener { togglePreviewMode() }
        btnRevision.setOnClickListener { toggleRevisionMode() }
        btnSearch.setOnClickListener { toggleSearchBar() }
        btnSplit.setOnClickListener { toggleSplitMode() }
        btnUndo.setOnClickListener { undo() }
        btnRedo.setOnClickListener { redo() }
        btnToolbarToggle.setOnClickListener { toggleBottomToolbar() }
        btnKeyboardSettings.setOnClickListener { showSettingsDialog() }
        btnSave.setOnClickListener { saveFile() }

        // 搜索栏
        btnSearchClose.setOnClickListener { toggleSearchBar() }
        btnSearchSingle.setOnClickListener { performSearch(singleMode = true) }
        btnSearchAll.setOnClickListener { performSearch(singleMode = false) }
        searchInput.setOnEditorActionListener { _, _, _ ->
            performSearch(singleMode = true)  // 回车键默认使用单个搜索模式
            true
        }
        btnSearchNext.setOnClickListener { findNext() }
        btnSearchPrev.setOnClickListener { findPrev() }
        btnReplaceOne.setOnClickListener { replaceOne() }
        btnReplaceAll.setOnClickListener { replaceAll() }

        // 文本变化监听
        editorText.addTextChangedListener(textWatcher)
        splitEditorText.addTextChangedListener(textWatcher)

        // 同步两个编辑器的内容
        syncEditors()

        // 底部快捷栏
        btnBold.setOnClickListener { insertMarkdown("**", "**") }
        btnItalic.setOnClickListener { insertMarkdown("*", "*") }
        btnHeadingUp.setOnClickListener { changeHeading(1) }
        btnHeadingDown.setOnClickListener { changeHeading(-1) }
        btnCodeInline.setOnClickListener { insertMarkdown("`", "`") }
        btnCodeBlock.setOnClickListener { insertCodeBlock() }
        btnLink.setOnClickListener { insertLink() }
        btnList.setOnClickListener { insertLine("- ") }
        btnQuote.setOnClickListener { insertLine("> ") }
        btnTable.setOnClickListener { insertTable() }
        btnHr.setOnClickListener { insertLine("---\n") }
        btnFormula.setOnClickListener { insertFormula() }

        // 目录关闭
        tocCloseArea.setOnClickListener { toggleToc() }
    }

    // =========================================================================
    // 文件处理
    // =========================================================================

    private fun handleOpenIntent(intent: Intent?) {
        val extras = intent?.extras
        val path = extras?.getString("file_path")
        val openSample = extras?.getBoolean("open_sample", false) ?: false

        if (openSample) {
            // 打开示例文件
            loadAssetSample()
        } else if (!path.isNullOrEmpty()) {
            filePath = path
            fileName = File(path).nameWithoutExtension
            loadFile(path)
        } else {
            // 创建新文件
            createNewFile()
        }
    }

    /**
     * 加载 assets 中的示例文件
     */
    private fun loadAssetSample() {
        try {
            val content = assets.open("math-formulas-example.md").bufferedReader().use { it.readText() }
            editorText.setText(content)
            splitEditorText.setText(content)
            lastSavedContent = content
            isModified = false
            updateSaveButton()

            // 示例文件特殊标识
            fileName = "数学公式示例"
            textFilename.text = "数学公式示例 (只读)"

            // 禁用保存按钮（示例文件不可保存）
            btnSave.isEnabled = false
            btnSave.alpha = 0.5f

            // 清空撤销重做栈并保存初始状态
            undoStack.clear()
            redoStack.clear()
            undoStack.add(content)

            Log.d("MarkdownEditorActivity", "Loaded asset sample, size: ${content.length}")
        } catch (e: Exception) {
            Log.e("MarkdownEditorActivity", "Failed to load asset sample", e)
            Toast.makeText(this, "加载示例文件失败: ${e.message}", Toast.LENGTH_LONG).show()
            createNewFile()
        }
    }

    private fun loadFile(path: String) {
        try {
            val file = File(path)
            val content = file.readText()
            editorText.setText(content)
            splitEditorText.setText(content)
            lastSavedContent = content
            isModified = false
            updateSaveButton()
            updateFilenameDisplay()

            // 清空撤销重做栈并保存初始状态
            undoStack.clear()
            redoStack.clear()
            undoStack.add(content)

            // 暂时禁用 Core 文档集成，因为 JNI 接口未完全实现
            // 如需启用，需先完善 core/src/bridge/jni.rs 中的 nativeSearch 等函数
            Log.d("MarkdownEditorActivity", "Loaded file: $path, size: ${content.length}")
        } catch (e: Exception) {
            Log.e("MarkdownEditorActivity", "Failed to load file", e)
            Toast.makeText(this, "加载文件失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createNewFile() {
        // 使用 FileOperationHelper 显示新建文件对话框
        fileOperationHelper.showNewFileDialog { newPath ->
            fileName = File(newPath).nameWithoutExtension
            filePath = newPath
            editorText.setText("# 新建文档\n\n")
            splitEditorText.setText("# 新建文档\n\n")
            lastSavedContent = editorText.text.toString()
            isModified = false
            updateSaveButton()
            updateFilenameDisplay()

            // 清空撤销重做栈并保存初始状态
            undoStack.clear()
            redoStack.clear()
            undoStack.add(editorText.text.toString())

            Toast.makeText(this, "已创建新文件", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveFile() {
        if (!isModified) {
            Toast.makeText(this, "没有需要保存的更改", Toast.LENGTH_SHORT).show()
            return
        }

        showSavingDialog()

        Thread {
            try {
                val content = getCurrentContent()
                val path = filePath ?: createNewFilePath()

                File(path).writeText(content)
                filePath = path
                lastSavedContent = content
                isModified = false

                Handler(Looper.getMainLooper()).post {
                    dismissSavingDialog()
                    updateSaveButton()
                    Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MarkdownEditorActivity", "Failed to save file", e)
                Handler(Looper.getMainLooper()).post {
                    dismissSavingDialog()
                    Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun createNewFilePath(): String {
        // 使用 FileOperationHelper 生成唯一路径
        return fileOperationHelper.generateUniquePath("untitled")
    }

    // =========================================================================
    // 模式切换
    // =========================================================================

    /**
     * 显示模式枚举
     * 用于按键循环切换
     */
    private enum class DisplayMode {
        EDIT,    // 编辑模式
        PREVIEW, // 预览模式
        SPLIT    // 分屏模式
    }

    /** 当前显示模式 */
    private var currentDisplayMode: DisplayMode = DisplayMode.EDIT

    /**
     * 按键切换显示模式
     * 在 编辑 ↔ 预览 ↔ 分屏 之间循环切换
     */
    private fun cycleDisplayMode() {
        val nextMode = when (currentDisplayMode) {
            DisplayMode.EDIT -> DisplayMode.PREVIEW
            DisplayMode.PREVIEW -> DisplayMode.SPLIT
            DisplayMode.SPLIT -> DisplayMode.EDIT
        }

        when (nextMode) {
            DisplayMode.EDIT -> switchToEditMode()
            DisplayMode.PREVIEW -> switchToPreviewMode()
            DisplayMode.SPLIT -> switchToSplitMode()
        }

        currentDisplayMode = nextMode
    }

    /**
     * 切换到编辑模式
     */
    private fun switchToEditMode() {
        // 关闭其他模式标记
        isPreviewMode = false
        isSplitMode = false

        // 更新按钮状态
        btnPreviewToggle.setImageResource(R.drawable.ic_preview_off)
        btnSplit.setImageResource(R.drawable.ic_split_off)

        // 显示编辑层，隐藏其他层
        editorLayer.visibility = View.VISIBLE
        previewLayer.visibility = View.GONE
        splitLayer.visibility = View.GONE

        // 恢复手势层可见性（根据修订模式状态）
        if (isRevisionMode) {
            gestureLayer.visibility = View.VISIBLE
            gestureLayer.isGestureEnabled = true
            // 修订模式下隐藏光标
            editorText.isCursorVisible = false
        } else {
            gestureLayer.visibility = View.GONE
            gestureLayer.isGestureEnabled = false
            // 编辑模式下显示光标
            editorText.isCursorVisible = true
            editorText.requestFocus()
        }

        // 禁用滚动同步
        disableScrollSync()

        Toast.makeText(this, "编辑模式", Toast.LENGTH_SHORT).show()
    }

    /**
     * 切换到预览模式
     */
    private fun switchToPreviewMode() {
        // 更新模式标记
        isPreviewMode = true
        isSplitMode = false

        // 更新按钮状态
        btnPreviewToggle.setImageResource(R.drawable.ic_preview_on)
        btnSplit.setImageResource(R.drawable.ic_split_off)

        // 显示预览层，隐藏其他层
        editorLayer.visibility = View.GONE
        previewLayer.visibility = View.VISIBLE
        splitLayer.visibility = View.GONE

        // 预览模式下光标和键盘不可用
        editorText.isCursorVisible = false

        // 恢复手势层可见性（根据修订模式状态）
        if (isRevisionMode) {
            gestureLayer.visibility = View.VISIBLE
            gestureLayer.isGestureEnabled = true
        } else {
            gestureLayer.visibility = View.GONE
            gestureLayer.isGestureEnabled = false
        }

        // 禁用滚动同步
        disableScrollSync()

        updatePreview()
        Toast.makeText(this, "预览模式", Toast.LENGTH_SHORT).show()
    }

    /**
     * 切换到分屏模式
     */
    private fun switchToSplitMode() {
        // 更新模式标记
        isPreviewMode = false
        isSplitMode = true

        // 更新按钮状态
        btnPreviewToggle.setImageResource(R.drawable.ic_preview_off)
        btnSplit.setImageResource(R.drawable.ic_split_on)

        // 显示分屏层，隐藏其他层
        editorLayer.visibility = View.GONE
        previewLayer.visibility = View.GONE
        splitLayer.visibility = View.VISIBLE

        // 恢复手势层可见性（根据修订模式状态）
        if (isRevisionMode) {
            gestureLayer.visibility = View.VISIBLE
            gestureLayer.isGestureEnabled = true
            // 修订模式下分屏两区都隐藏光标
            splitEditorText.isCursorVisible = false
        } else {
            gestureLayer.visibility = View.GONE
            gestureLayer.isGestureEnabled = false
            // 分屏模式下仅编辑区显示光标
            splitEditorText.isCursorVisible = true
            splitEditorText.requestFocus()
        }

        // 启用滚动同步
        enableScrollSync()

        updatePreview()
        Toast.makeText(this, "分屏模式", Toast.LENGTH_SHORT).show()
    }

    /**
     * 按键监听 - 处理 F11 等功能键
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // F11 键切换显示模式
        if (keyCode == KeyEvent.KEYCODE_F11) {
            cycleDisplayMode()
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    // 保留原有的按钮切换函数（用于 UI 按钮）
    private fun togglePreviewMode() {
        isPreviewMode = !isPreviewMode

        if (isPreviewMode) {
            // 切换到预览模式
            btnPreviewToggle.setImageResource(R.drawable.ic_preview_on)
            editorLayer.visibility = View.GONE
            previewLayer.visibility = View.VISIBLE

            // 在预览模式下隐藏手势覆盖层
            gestureLayer.visibility = View.GONE

            // 预览模式下隐藏光标（不支持编辑）
            editorText.isCursorVisible = false

            hideSoftKeyboardFromAll()

            updatePreview()
        } else {
            // 切换到编辑模式
            btnPreviewToggle.setImageResource(R.drawable.ic_preview_off)
            editorLayer.visibility = View.VISIBLE
            previewLayer.visibility = View.GONE

            // 恢复手势覆盖层可见性
            gestureLayer.visibility = View.VISIBLE

            // 编辑模式下显示光标（支持编辑），除非在修订模式
            if (!isRevisionMode) {
                editorText.isCursorVisible = true
                editorText.requestFocus()
            }
        }

        // 分屏模式下同步更新
        if (isSplitMode) {
            toggleSplitMode() // 先关闭分屏
        }
    }

    private fun toggleSplitMode() {
        isSplitMode = !isSplitMode

        if (isSplitMode) {
            // 开启分屏
            btnSplit.setImageResource(R.drawable.ic_split_on)
            editorLayer.visibility = View.GONE
            previewLayer.visibility = View.GONE
            splitLayer.visibility = View.VISIBLE
            updatePreview()

            // 在分屏模式下隐藏手势覆盖层，确保滚轮事件能够正确传递
            gestureLayer.visibility = View.GONE

            // 分屏模式下编辑区域显示光标（支持编辑），除非在修订模式
            if (!isRevisionMode) {
                splitEditorText.isCursorVisible = true
                splitEditorText.requestFocus()
            } else {
                splitEditorText.isCursorVisible = false
            }

            // 启用滚动同步
            enableScrollSync()
        } else {
            // 关闭分屏
            btnSplit.setImageResource(R.drawable.ic_split_off)
            splitLayer.visibility = View.GONE
            if (isPreviewMode) {
                previewLayer.visibility = View.VISIBLE
            } else {
                editorLayer.visibility = View.VISIBLE
            }

            // 恢复手势覆盖层可见性
            gestureLayer.visibility = View.VISIBLE

            // 禁用滚动同步
            disableScrollSync()
        }
    }

    /**
     * 启用滚动同步
     */
    private fun enableScrollSync() {
        if (scrollSyncManager == null) {
            scrollSyncManager = ScrollSyncManager(splitEditorScroll, splitPreviewScroll)
        }
        scrollSyncManager?.enable()
        Log.d("MarkdownEditorActivity", "Scroll sync enabled")
    }

    /**
     * 禁用滚动同步
     */
    private fun disableScrollSync() {
        scrollSyncManager?.disable()
        Log.d("MarkdownEditorActivity", "Scroll sync disabled")
    }

    /**
     * 切换修订模式
     *
     * 规则：
     * - 编辑模式：光标默认显示，开启修订后光标消失 + 手势层启用
     * - 预览模式：无光标，开启修订后手势层启用
     * - 分屏模式：编辑区有光标，开启修订后上下两区都支持手势修订
     */
    private fun toggleRevisionMode() {
        isRevisionMode = !isRevisionMode

        if (isRevisionMode) {
            // 开启修订模式
            btnRevision.alpha = 1.0f

            // 隐藏所有光标
            editorText.isCursorVisible = false
            splitEditorText.isCursorVisible = false

            // 显示并启用手势层
            gestureLayer.visibility = View.VISIBLE
            gestureLayer.isGestureEnabled = true

            // 隐藏软键盘
            hideSoftKeyboardFromAll()

            Toast.makeText(this, "修订模式已开启", Toast.LENGTH_SHORT).show()
        } else {
            // 关闭修订模式
            btnRevision.alpha = 0.5f

            // 隐藏手势层
            gestureLayer.visibility = View.GONE
            gestureLayer.isGestureEnabled = false

            // 根据当前模式恢复光标和焦点
            when (currentDisplayMode) {
                DisplayMode.EDIT -> {
                    // 编辑模式：恢复光标
                    editorText.isCursorVisible = true
                    editorText.requestFocus()
                }
                DisplayMode.PREVIEW -> {
                    // 预览模式：无光标
                }
                DisplayMode.SPLIT -> {
                    // 分屏模式：编辑区恢复光标
                    splitEditorText.isCursorVisible = true
                    splitEditorText.requestFocus()
                }
            }

            Toast.makeText(this, "修订模式已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Hide soft keyboard from all editors
     */
    private fun hideSoftKeyboardFromAll() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editorText.windowToken, 0)
        imm.hideSoftInputFromWindow(splitEditorText.windowToken, 0)

        // Clear focus and remove focus to prevent keyboard from popping up again
        editorText.clearFocus()
        splitEditorText.clearFocus()
    }

    private fun toggleSearchBar() {
        if (searchBar.visibility == View.VISIBLE) {
            // 关闭搜索栏时清除所有内容和高亮
            searchInput.text.clear()
            replaceInput.text.clear()
            searchResults.clear()
            currentSearchIndex = 0
            clearSearchHighlights()  // 清除搜索高亮
            searchBar.visibility = View.GONE
            replaceRow.visibility = View.GONE
        } else {
            searchBar.visibility = View.VISIBLE
            searchInput.requestFocus()
            showSoftKeyboard()
        }
    }

    private fun toggleBottomToolbar() {
        if (toolbarBottom.visibility == View.VISIBLE) {
            toolbarBottom.visibility = View.GONE
        } else {
            toolbarBottom.visibility = View.VISIBLE
        }
    }

    private fun toggleToc() {
        if (tocPanel.visibility == View.VISIBLE) {
            tocPanel.visibility = View.GONE
        } else {
            tocPanel.visibility = View.VISIBLE
            updateToc()
            hideSoftKeyboardFromAll()
        }
    }

    // =========================================================================
    // 搜索和替换
    // =========================================================================

    /**
     * 执行搜索
     * @param singleMode true=单个搜索模式，false=全部搜索模式
     */
    private fun performSearch(singleMode: Boolean) {
        val query = searchInput.text.toString()
        if (query.isEmpty()) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            return
        }

        // 保存当前内容用于上下文计算
        currentSearchContent = getCurrentContent()

        // 使用 Rust Core 搜索
        if (useRustCoreSearch && rustCoreDocumentHandle != 0L) {
            performRustCoreSearch(query, singleMode)
        } else {
            // 回退到本地搜索
            performLocalSearch(query, singleMode)
        }

        // 显示替换选项
        replaceRow.visibility = View.VISIBLE
    }

    /**
     * 使用 Rust Core 执行搜索
     */
    private fun performRustCoreSearch(query: String, singleMode: Boolean) {
        searchResults.clear()
        currentSearchIndex = 0
        isSingleSearchMode = singleMode

        // 先清除旧的高亮
        clearSearchHighlights()

        try {
            // 调用 Rust Core 搜索
            val results = MarkdownCore.nativeSearch(rustCoreDocumentHandle, query)

            if (results == null || results.isEmpty()) {
                Toast.makeText(this, "未找到匹配项", Toast.LENGTH_SHORT).show()
                return
            }

            // 解析结果: [start, end, line_number, ...]
            var index = 0
            while (index + 2 < results.size) {
                val start = results[index].toInt()
                val end = results[index + 1].toInt()
                val lineNumber = results[index + 2].toInt()
                searchResults.add(Pair(start, end))
                index += 3
            }

            // 应用高亮
            if (singleMode) {
                currentSearchIndex = 0
                highlightSingleResult(0)
                Toast.makeText(this, "已定位到第 1 个匹配项", Toast.LENGTH_SHORT).show()
            } else {
                applyAllHighlights()
                Toast.makeText(this, "找到 ${searchResults.size} 个匹配项", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e("MarkdownEditorActivity", "Rust Core search failed", e)
            // 回退到本地搜索
            performLocalSearch(query, singleMode)
        }
    }

    /**
     * 本地搜索实现
     * @param query 搜索关键词
     * @param singleMode true=单个搜索模式，false=全部搜索模式
     */
    private fun performLocalSearch(query: String, singleMode: Boolean) {
        searchResults.clear()
        currentSearchIndex = 0

        // 保存当前搜索模式
        isSingleSearchMode = singleMode

        // 先清除旧的高亮
        clearSearchHighlights()

        val content = getCurrentContent()
        var index = 0
        while (index < content.length) {
            val found = content.indexOf(query, index, ignoreCase = true)
            if (found == -1) break
            searchResults.add(Pair(found, found + query.length))
            index = found + 1
        }

        if (searchResults.isEmpty()) {
            Toast.makeText(this, "未找到匹配项", Toast.LENGTH_SHORT).show()
        } else {
            if (singleMode) {
                // 单个搜索：只高亮第一个匹配项
                currentSearchIndex = 0
                highlightSingleResult(0)
                Toast.makeText(this, "已定位到第 1 个匹配项", Toast.LENGTH_SHORT).show()
            } else {
                // 全部搜索：高亮所有匹配项并显示数量
                applyAllHighlights()
                Toast.makeText(this, "找到 ${searchResults.size} 个匹配项", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 清除搜索高亮（编辑层和预览层）
     */
    private fun clearSearchHighlights() {
        // 清除编辑层高亮
        val editor = getCurrentEditor()
        val text = editor.text as? android.text.Spannable ?: return

        // 移除所有 SearchHighlightSpan
        val spans = text.getSpans(0, text.length, SearchHighlightSpan::class.java)
        for (span in spans) {
            text.removeSpan(span)
        }

        // 清除预览层高亮
        clearPreviewHighlights()
    }

    /**
     * 清除预览层搜索高亮
     */
    private fun clearPreviewHighlights() {
        // 清除预览层高亮
        if (isPreviewMode && ::previewText.isInitialized) {
            val previewSpannable = previewText.text as? android.text.Spannable ?: return
            val previewSpans = previewSpannable.getSpans(0, previewSpannable.length, SearchHighlightBackgroundSpan::class.java)
            for (span in previewSpans) {
                previewSpannable.removeSpan(span)
            }
        }

        // 清除分屏预览层高亮
        if (isSplitMode && ::splitPreviewText.isInitialized) {
            val splitPreviewSpannable = splitPreviewText.text as? android.text.Spannable ?: return
            val splitPreviewSpans = splitPreviewSpannable.getSpans(0, splitPreviewSpannable.length, SearchHighlightBackgroundSpan::class.java)
            for (span in splitPreviewSpans) {
                splitPreviewSpannable.removeSpan(span)
            }
        }
    }

    /**
     * 预览层单个搜索模式：只高亮指定索引的匹配项
     *
     * 注意：由于预览文本是渲染后的结果，与原始文本位置无法直接映射，
     * 我们直接在预览文本中搜索相同的查询字符串，并跳过前 index 个匹配项，
     * 定位到第 (index + 1) 个匹配项进行高亮。
     * 这确保了预览模式和编辑模式的搜索逻辑一致。
     */
    private fun applyPreviewHighlightSingle(query: String, index: Int) {
        if (query.isEmpty()) return

        // 清除预览层现有高亮
        clearPreviewHighlights()

        // 计算预览层中的实际匹配项数量
        previewMatchCount = countPreviewMatches(query)

        // 使用模运算确保索引在预览层匹配项范围内
        val safeIndex = if (previewMatchCount > 0) index % previewMatchCount else 0

        // 在预览层中查找并高亮第 (safeIndex + 1) 个匹配项（使用深灰色边框）
        if (isPreviewMode && ::previewText.isInitialized) {
            highlightNthInTextView(previewText, query, safeIndex, true)
        }

        if (isSplitMode && ::splitPreviewText.isInitialized) {
            highlightNthInTextView(splitPreviewText, query, safeIndex, true)
        }

        // 滚动预览层到匹配项位置（使用安全索引）
        scrollPreviewToMatch(query, safeIndex)
    }

    /**
     * 预览层全部搜索模式：高亮所有匹配项
     *
     * 注意：由于预览文本是渲染后的结果，与原始文本位置无法直接映射，
     * 我们直接在预览文本中搜索相同的查询字符串来定位所有匹配项。
     * 这确保了预览模式和编辑模式的搜索逻辑一致。
     */
    private fun applyPreviewHighlightsAll(query: String) {
        if (query.isEmpty()) return

        // 清除预览层现有高亮
        clearPreviewHighlights()

        // 在预览层中查找并高亮所有匹配项（使用灰色边框，第一项使用深灰色）
        if (isPreviewMode && ::previewText.isInitialized) {
            highlightAllInTextView(previewText, query)
        }

        if (isSplitMode && ::splitPreviewText.isInitialized) {
            highlightAllInTextView(splitPreviewText, query)
        }
    }

    /**
     * 在 TextView 中高亮指定的文本（单个搜索模式）
     * @param textView 目标 TextView
     * @param searchText 要高亮的文本
     * @param startIndex 开始搜索的索引
     * @param isSingleMode 是否为单个模式（使用深灰色边框）
     */
    private fun highlightInTextView(
        textView: TextView,
        searchText: String,
        startIndex: Int,
        isSingleMode: Boolean
    ) {
        val spannable = textView.text as? android.text.Spannable ?: return
        val text = spannable.toString()
        val ignoreCase = true

        // 查找第一个匹配项
        val foundIndex = text.indexOf(searchText, startIndex, ignoreCase = ignoreCase)
        if (foundIndex >= 0) {
            spannable.setSpan(
                SearchHighlightBackgroundSpan(
                    borderColor = if (isSingleMode) {
                        SearchHighlightBackgroundSpan.CURRENT_BORDER_COLOR
                    } else {
                        SearchHighlightBackgroundSpan.ALL_SEARCH_BORDER_COLOR
                    },
                    isCurrent = isSingleMode
                ),
                foundIndex,
                foundIndex + searchText.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /**
     * 在 TextView 中高亮所有匹配项（全部搜索模式）
     * @param textView 目标 TextView
     * @param searchText 要高亮的文本
     */
    private fun highlightAllInTextView(textView: TextView, searchText: String) {
        val spannable = textView.text as? android.text.Spannable ?: return
        val text = spannable.toString()
        val ignoreCase = true
        var isFirst = true

        // 查找所有匹配项
        var searchIndex = 0
        while (searchIndex < text.length) {
            val foundIndex = text.indexOf(searchText, searchIndex, ignoreCase = ignoreCase)
            if (foundIndex < 0) break

            spannable.setSpan(
                SearchHighlightBackgroundSpan(
                    borderColor = if (isFirst) {
                        SearchHighlightBackgroundSpan.CURRENT_BORDER_COLOR
                    } else {
                        SearchHighlightBackgroundSpan.ALL_SEARCH_BORDER_COLOR
                    },
                    isCurrent = isFirst
                ),
                foundIndex,
                foundIndex + searchText.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            isFirst = false
            searchIndex = foundIndex + searchText.length
        }
    }

    /**
     * 在 TextView 中高亮第 N 个匹配项（单个搜索模式导航用）
     * @param textView 目标 TextView
     * @param searchText 要高亮的文本
     * @param nthIndex 要高亮的匹配项索引（0-based）
     * @param isSingleMode 是否为单个模式（使用深灰色边框）
     */
    private fun highlightNthInTextView(
        textView: TextView,
        searchText: String,
        nthIndex: Int,
        isSingleMode: Boolean
    ) {
        val spannable = textView.text as? android.text.Spannable ?: return
        val text = spannable.toString()
        val ignoreCase = true

        // 跳过前 nthIndex 个匹配项，高亮第 (nthIndex + 1) 个
        var searchIndex = 0
        var count = 0

        while (searchIndex < text.length) {
            val foundIndex = text.indexOf(searchText, searchIndex, ignoreCase = ignoreCase)
            if (foundIndex < 0) break

            if (count == nthIndex) {
                // 找到第 N 个匹配项，进行高亮
                spannable.setSpan(
                    SearchHighlightBackgroundSpan(
                        borderColor = if (isSingleMode) {
                            SearchHighlightBackgroundSpan.CURRENT_BORDER_COLOR
                        } else {
                            SearchHighlightBackgroundSpan.ALL_SEARCH_BORDER_COLOR
                        },
                        isCurrent = isSingleMode
                    ),
                    foundIndex,
                    foundIndex + searchText.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                return  // 只高亮这一个匹配项
            }

            count++
            searchIndex = foundIndex + searchText.length
        }
        // 如果没找到第 N 个匹配项（理论上不应该发生），则不进行高亮
    }

    private fun findNext() {
        if (searchResults.isEmpty()) return
        currentSearchIndex = (currentSearchIndex + 1) % searchResults.size

        if (isSingleSearchMode) {
            highlightSingleResult(currentSearchIndex)
        } else {
            highlightAllResults(currentSearchIndex)
        }
    }

    private fun findPrev() {
        if (searchResults.isEmpty()) return
        currentSearchIndex = (currentSearchIndex - 1 + searchResults.size) % searchResults.size

        if (isSingleSearchMode) {
            highlightSingleResult(currentSearchIndex)
        } else {
            highlightAllResults(currentSearchIndex)
        }
    }

    /**
     * 单个搜索模式：只高亮当前一个匹配项（编辑层和预览层）
     */
    private fun highlightSingleResult(index: Int) {
        if (searchResults.isEmpty() || index < 0 || index >= searchResults.size) return

        val (start, end) = searchResults[index]
        val editor = getCurrentEditor()
        val text = editor.text as? android.text.Spannable ?: return

        // 清除所有高亮
        clearSearchHighlights()

        // 编辑层：只高亮当前项（深灰色边框）
        text.setSpan(
            SearchHighlightSpan(
                borderColor = SearchHighlightSpan.CURRENT_BORDER_COLOR,
                isCurrent = true
            ),
            start, end,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // 设置选中文本并滚动到该位置
        editor.setSelection(start, end)
        scrollToPosition(start)

        // 预览层：高亮当前匹配项
        val query = searchInput.text.toString()
        if (query.isNotEmpty()) {
            applyPreviewHighlightSingle(query, index)
        }

        // 显示当前匹配项位置
        Toast.makeText(this, "第 ${index + 1} / ${searchResults.size} 个匹配项", Toast.LENGTH_SHORT).show()
    }

    /**
     * 全部搜索模式：高亮所有匹配项，当前项使用深灰色（编辑层和预览层）
     */
    private fun applyAllHighlights() {
        if (searchResults.isEmpty()) return

        val editor = getCurrentEditor()
        val text = editor.text as? android.text.Spannable ?: return

        // 编辑层：高亮所有匹配项（灰色），第一项使用深灰色
        searchResults.forEachIndexed { index, (start, end) ->
            text.setSpan(
                SearchHighlightSpan(
                    borderColor = if (index == 0) {
                        SearchHighlightSpan.CURRENT_BORDER_COLOR
                    } else {
                        SearchHighlightSpan.ALL_SEARCH_BORDER_COLOR
                    },
                    isCurrent = (index == 0)
                ),
                start, end,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 滚动到第一个匹配项
        val (start, end) = searchResults[0]
        editor.setSelection(start, end)
        scrollToPosition(start)

        // 预览层：高亮所有匹配项
        val query = searchInput.text.toString()
        if (query.isNotEmpty()) {
            applyPreviewHighlightsAll(query)
        }
    }

    /**
     * 全部搜索模式下的导航：更新当前项的高亮
     */
    private fun highlightAllResults(index: Int) {
        if (searchResults.isEmpty() || index < 0 || index >= searchResults.size) return

        val editor = getCurrentEditor()
        val text = editor.text as? android.text.Spannable ?: return

        // 清除所有高亮
        clearSearchHighlights()

        // 重新应用所有高亮，当前项使用深灰色
        searchResults.forEachIndexed { i, (start, end) ->
            text.setSpan(
                SearchHighlightSpan(
                    borderColor = if (i == index) {
                        SearchHighlightSpan.CURRENT_BORDER_COLOR
                    } else {
                        SearchHighlightSpan.ALL_SEARCH_BORDER_COLOR
                    },
                    isCurrent = (i == index)
                ),
                start, end,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 设置选中文本并滚动到该位置
        val (start, end) = searchResults[index]
        editor.setSelection(start, end)
        scrollToPosition(start)

        // 显示当前匹配项位置
        Toast.makeText(this, "第 ${index + 1} / ${searchResults.size} 个匹配项", Toast.LENGTH_SHORT).show()
    }

    /**
     * @deprecated 使用 highlightSingleResult 或 highlightAllResults 代替
     */
    private fun highlightSearchResult(index: Int) {
        if (isSingleSearchMode) {
            highlightSingleResult(index)
        } else {
            highlightAllResults(index)
        }
    }

    /**
     * 滚动到指定文本位置
     */
    private fun scrollToPosition(position: Int) {
        val scrollView = getCurrentScrollView()
        val editor = getCurrentEditor()

        // 获取文本布局信息
        val layout = editor.layout ?: return
        val line = layout.getLineForOffset(position)

        // 计算滚动位置
        val lineHeight = editor.lineHeight
        val scrollY = line * lineHeight - scrollView.height / 2

        scrollView.smoothScrollTo(0, scrollY.coerceAtLeast(0))
    }

    /**
     * 计算预览层中的实际匹配项数量
     * @param query 搜索查询字符串
     * @return 预览层中的匹配项数量
     */
    private fun countPreviewMatches(query: String): Int {
        if (query.isEmpty()) return 0

        val previewTextView = if (isPreviewMode && ::previewText.isInitialized) {
            previewText
        } else if (isSplitMode && ::splitPreviewText.isInitialized) {
            splitPreviewText
        } else {
            return 0
        }

        val text = previewTextView.text.toString()
        val ignoreCase = true
        var count = 0
        var searchIndex = 0

        while (searchIndex < text.length) {
            val foundIndex = text.indexOf(query, searchIndex, ignoreCase = ignoreCase)
            if (foundIndex < 0) break
            count++
            searchIndex = foundIndex + query.length
        }

        return count
    }

    /**
     * 滚动预览层到匹配项位置
     * @param query 搜索查询字符串
     * @param index 匹配项索引（第 N 个匹配项）
     */
    private fun scrollPreviewToMatch(query: String, index: Int) {
        if (query.isEmpty()) return

        // 获取预览 TextView 和 ScrollView
        val previewTextView = if (isPreviewMode && ::previewText.isInitialized) {
            previewText
        } else if (isSplitMode && ::splitPreviewText.isInitialized) {
            splitPreviewText
        } else {
            return
        }

        val scrollView = if (isPreviewMode) {
            previewLayer
        } else if (isSplitMode) {
            splitPreviewScroll
        } else {
            return
        }

        val spannable = previewTextView.text as? android.text.Spannable ?: return
        val text = spannable.toString()
        val ignoreCase = true

        // 查找第 N 个匹配项的位置
        var searchIndex = 0
        var count = 0
        var targetPosition = -1

        while (searchIndex < text.length) {
            val foundIndex = text.indexOf(query, searchIndex, ignoreCase = ignoreCase)
            if (foundIndex < 0) break

            if (count == index) {
                targetPosition = foundIndex
                break
            }

            count++
            searchIndex = foundIndex + query.length
        }

        if (targetPosition < 0) return

        // 获取文本布局信息
        val layout = previewTextView.layout ?: return
        val line = layout.getLineForOffset(targetPosition)

        // 使用 layout.getLineTop() 获取准确的行位置
        val lineTop = layout.getLineTop(line)

        // 计算滚动位置（居中显示）
        val scrollY = lineTop - scrollView.height / 2

        // 确保滚动位置不小于 0
        val clampedScrollY = scrollY.coerceAtLeast(0)
        // 确保滚动位置不超过最大可滚动范围
        val maxScrollY = previewTextView.height - scrollView.height
        val finalScrollY = clampedScrollY.coerceAtMost(maxScrollY)

        // 使用 post 确保在 UI 线程中执行滚动
        scrollView.post {
            scrollView.smoothScrollTo(0, finalScrollY)
        }
    }

    private fun replaceOne() {
        if (searchResults.isEmpty()) return

        // 确保索引在有效范围内
        if (currentSearchIndex >= searchResults.size) {
            currentSearchIndex = 0
        }
        if (currentSearchIndex < 0 || searchResults.isEmpty()) {
            return
        }

        val query = searchInput.text.toString()
        val replaceText = replaceInput.text.toString()

        if (query.isEmpty()) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            return
        }

        // 使用 Rust Core 替换
        if (useRustCoreSearch && rustCoreDocumentHandle != 0L) {
            performRustCoreReplaceFirst(query, replaceText)
        } else {
            // 回退到本地替换
            performLocalReplaceOne(query, replaceText)
        }
    }

    /**
     * 使用 Rust Core 替换第一个匹配项
     */
    private fun performRustCoreReplaceFirst(query: String, replacement: String) {
        try {
            val newContent = MarkdownCore.nativeReplaceFirst(
                rustCoreDocumentHandle,
                query,
                replacement
            )

            if (newContent == null) {
                Toast.makeText(this, "未找到匹配项", Toast.LENGTH_SHORT).show()
                return
            }

            // 更新编辑器内容
            getCurrentEditor().setText(newContent)

            // 更新预览（如果是预览或分屏模式）
            if (isPreviewMode || isSplitMode) {
                updatePreview()
            }

            // 重新搜索以更新匹配项位置（这会同时更新编辑层和预览层的高亮）
            performSearch(isSingleSearchMode)

            Toast.makeText(this, "已替换 1 处", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("MarkdownEditorActivity", "Rust Core replace first failed", e)
            // 回退到本地替换
            performLocalReplaceOne(query, replacement)
        }
    }

    /**
     * 本地替换第一个匹配项（回退实现）
     */
    private fun performLocalReplaceOne(query: String, replacement: String) {
        if (searchResults.isEmpty()) return

        // 确保索引在有效范围内
        if (currentSearchIndex >= searchResults.size) {
            currentSearchIndex = 0
        }
        if (currentSearchIndex < 0 || searchResults.isEmpty()) {
            return
        }

        val (start, end) = searchResults[currentSearchIndex]

        val editor = getCurrentEditor()
        val content = editor.text.toString()
        val newContent = content.substring(0, start) + replacement + content.substring(end)
        editor.setText(newContent)
        editor.setSelection(start + replacement.length)

        // 移除当前结果
        searchResults.removeAt(currentSearchIndex)

        // 调整索引指向下一个结果
        if (searchResults.isEmpty()) {
            currentSearchIndex = 0
            clearSearchHighlights()  // 清除所有高亮
            Toast.makeText(this, "已完成替换", Toast.LENGTH_SHORT).show()
        } else {
            // 如果移除的是最后一个，指向前一个
            if (currentSearchIndex >= searchResults.size) {
                currentSearchIndex = searchResults.size - 1
            }
            // 重新应用高亮以保持编辑和预览一致
            if (isSingleSearchMode) {
                highlightSingleResult(currentSearchIndex)
            } else {
                highlightAllResults(currentSearchIndex)
            }
        }
    }

    private fun replaceAll() {
        if (searchResults.isEmpty()) return

        val query = searchInput.text.toString()
        val replaceText = replaceInput.text.toString()

        if (query.isEmpty()) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            return
        }

        // 使用 Rust Core 替换
        if (useRustCoreSearch && rustCoreDocumentHandle != 0L) {
            performRustCoreReplaceAll(query, replaceText)
        } else {
            // 回退到本地替换
            performLocalReplaceAll(query, replaceText)
        }
    }

    /**
     * 使用 Rust Core 替换所有匹配项
     */
    private fun performRustCoreReplaceAll(query: String, replacement: String) {
        try {
            val newContent = MarkdownCore.nativeReplaceAll(
                rustCoreDocumentHandle,
                query,
                replacement
            )

            if (newContent == null) {
                Toast.makeText(this, "未找到匹配项", Toast.LENGTH_SHORT).show()
                return
            }

            val count = searchResults.size

            // 更新编辑器内容
            getCurrentEditor().setText(newContent)

            // 更新预览（如果是预览或分屏模式）
            if (isPreviewMode || isSplitMode) {
                updatePreview()
            }

            // 清除搜索结果和高亮（编辑层和预览层）
            searchResults.clear()
            currentSearchIndex = 0
            clearSearchHighlights()

            Toast.makeText(this, "已替换 $count 处", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("MarkdownEditorActivity", "Rust Core replace all failed", e)
            // 回退到本地替换
            performLocalReplaceAll(query, replacement)
        }
    }

    /**
     * 本地替换所有匹配项（回退实现）
     */
    private fun performLocalReplaceAll(query: String, replacement: String) {
        if (searchResults.isEmpty()) return

        val editor = getCurrentEditor()
        val newContent = editor.text.toString().replace(query, replacement, ignoreCase = true)
        editor.setText(newContent)

        // 更新预览（如果是预览或分屏模式）
        if (isPreviewMode || isSplitMode) {
            updatePreview()
        }

        Toast.makeText(this, "已替换 ${searchResults.size} 处", Toast.LENGTH_SHORT).show()
        searchResults.clear()
        currentSearchIndex = 0
        clearSearchHighlights()  // 清除所有高亮
    }

    // =========================================================================
    // Markdown 格式快捷操作
    // =========================================================================

    private fun insertMarkdown(prefix: String, suffix: String) {
        val editor = getCurrentEditor()
        val start = editor.selectionStart
        val end = editor.selectionEnd

        if (start == end) {
            // 没有选中文本，直接插入
            editor.text.insert(start, "$prefix$suffix$")
            editor.setSelection(start + prefix.length)
        } else {
            // 包裹选中的文本
            val selectedText = editor.text.substring(start, end)
            editor.text.replace(start, end, "$prefix$selectedText$suffix")
        }
        markAsModified()
    }

    private fun insertLine(line: String) {
        val editor = getCurrentEditor()
        val position = editor.selectionStart
        val text = editor.text

        // 找到当前行的行首
        var lineStart = position
        while (lineStart > 0 && text[lineStart - 1] != '\n') {
            lineStart--
        }

        text.insert(lineStart, line)
        markAsModified()
    }

    private fun insertCodeBlock() {
        val editor = getCurrentEditor()
        val position = editor.selectionStart
        editor.text.insert(position, "```\n\n```\n")
        editor.setSelection(position + 5)
        markAsModified()
    }

    private fun insertLink() {
        val editor = getCurrentEditor()
        val start = editor.selectionStart
        val end = editor.selectionEnd

        if (start == end) {
            editor.text.insert(start, "[](url)")
            editor.setSelection(start + 1)
        } else {
            val selectedText = editor.text.substring(start, end)
            editor.text.replace(start, end, "[$selectedText](url)")
        }
        markAsModified()
    }

    private fun insertTable() {
        val editor = getCurrentEditor()
        val position = editor.selectionStart
        val table = "| 列1 | 列2 | 列3 |\n|-----|-----|-----|\n| 内容1 | 内容2 | 内容3 |\n"
        editor.text.insert(position, table)
        markAsModified()
    }

    private fun insertFormula() {
        // 弹出对话框让用户选择行内公式还是块级公式
        val options = arrayOf("行内公式 $...$", "块级公式 $$...$$")
        AlertDialog.Builder(this)
            .setTitle("选择公式类型")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> insertMarkdown("$", "$")  // 行内公式
                    1 -> {  // 块级公式
                        val editor = getCurrentEditor()
                        val position = editor.selectionStart
                        editor.text.insert(position, "$$\n\n$$\n")
                        editor.setSelection(position + 3)
                        markAsModified()
                    }
                }
            }
            .show()
    }

    private fun changeHeading(delta: Int) {
        val editor = getCurrentEditor()
        val position = editor.selectionStart
        val text = editor.text

        // 找到当前行的行首
        var lineStart = position
        while (lineStart > 0 && text[lineStart - 1] != '\n') {
            lineStart--
        }

        // 检查是否已经是标题
        var hashes = 0
        while (lineStart + hashes < text.length && text[lineStart + hashes] == '#') {
            hashes++
        }

        if (hashes > 0 && hashes <= 6) {
            // 已有标题，调整级别
            val newHashes = (hashes + delta).coerceIn(1, 6)
            text.replace(lineStart, lineStart + hashes, "#".repeat(newHashes))
        } else {
            // 不是标题，添加 # 标记
            text.insert(lineStart, "# ")
        }
        markAsModified()
    }

    // =========================================================================
    // 撤销/重做
    // =========================================================================

    private fun undo() {
        // 使用本地撤销实现（Core 层 JNI 接口未完全实现）
        performLocalUndo()
    }

    private fun redo() {
        // 使用本地重做实现（Core 层 JNI 接口未完全实现）
        performLocalRedo()
    }

    /**
     * 本地撤销实现
     */
    private fun performLocalUndo() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "没有可撤销的操作", Toast.LENGTH_SHORT).show()
            return
        }

        isUndoingOrRedoing = true
        try {
            redoStack.add(getCurrentContent())
            val previous = undoStack.removeAt(undoStack.size - 1)
            getCurrentEditor().setText(previous)
            isModified = true
            updateSaveButton()
            updatePreview()
        } finally {
            isUndoingOrRedoing = false
        }
    }

    /**
     * 本地重做实现
     */
    private fun performLocalRedo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "没有可重做的操作", Toast.LENGTH_SHORT).show()
            return
        }

        isUndoingOrRedoing = true
        try {
            undoStack.add(getCurrentContent())
            val next = redoStack.removeAt(redoStack.size - 1)
            getCurrentEditor().setText(next)
            isModified = true
            updateSaveButton()
            updatePreview()
        } finally {
            isUndoingOrRedoing = false
        }
    }

    // =========================================================================
    // 预览渲染
    // =========================================================================

    private fun updatePreview() {
        val content = getCurrentContent()

        // 根据选择的渲染引擎进行渲染
        when (renderEngine) {
            RenderEngine.MARKWON -> {
                // 使用 Markwon 渲染 Markdown
                if (isPreviewMode) {
                    markwon.setMarkdown(previewText, content)
                    // 移除下划线
                    removeUnderlines(previewText.text as Spanned)
                    // 应用代码块边框
                    applyCodeBlockBorder(previewText.text as Spanned)
                }
                if (isSplitMode) {
                    markwon.setMarkdown(splitPreviewText, content)
                    // 移除下划线
                    removeUnderlines(splitPreviewText.text as Spanned)
                    // 应用代码块边框
                    applyCodeBlockBorder(splitPreviewText.text as Spanned)
                }
            }
            RenderEngine.RUST_CORE -> {
                // 使用 Rust Core 渲染 Markdown
                if (isPreviewMode) {
                    renderWithRustCore(content, previewText)
                }
                if (isSplitMode) {
                    renderWithRustCore(content, splitPreviewText)
                }
            }
        }
    }

    // =========================================================================
    // 目录
    // =========================================================================

    private fun updateToc() {
        val content = getCurrentContent()
        val headings = mutableListOf<TocItem>()

        content.lines().forEachIndexed { index, line ->
            when {
                line.startsWith("# ") -> headings.add(TocItem(line.substring(2), 1, index))
                line.startsWith("## ") -> headings.add(TocItem(line.substring(3), 2, index))
                line.startsWith("### ") -> headings.add(TocItem(line.substring(4), 3, index))
                line.startsWith("#### ") -> headings.add(TocItem(line.substring(5), 4, index))
                line.startsWith("##### ") -> headings.add(TocItem(line.substring(6), 5, index))
                line.startsWith("###### ") -> headings.add(TocItem(line.substring(7), 6, index))
            }
        }

        val adapter = TocSimpleAdapter(this, headings)
        tocList.adapter = adapter

        tocList.setOnItemClickListener { _, _, position, _ ->
            val item = headings[position]
            scrollToLine(item.lineNumber)
            toggleToc()
        }
    }

    private fun scrollToLine(lineNumber: Int) {
        val editor = getCurrentEditor()
        val text = editor.text.toString()
        val lines = text.split("\n")

        if (lineNumber < lines.size) {
            var position = 0
            for (i in 0 until lineNumber) {
                position += lines[i].length + 1
            }
            editor.setSelection(position)
            getCurrentScrollView().smoothScrollTo(0, position)
        }
    }

    // =========================================================================
    // 防丢失保护
    // =========================================================================

    private fun showSaveBeforeExitDialog() {
        val displayName = fileName ?: "未命名.md"
        fileOperationHelper.showSaveConfirmDialog(
            fileName = displayName,
            onSave = {
                saveFile()
                finish()
            },
            onDiscard = {
                finish()
            },
            onCancel = {
                // 不做任何事
            }
        )
    }

    private fun finishWithSaveCheck() {
        if (isModified) {
            showSaveBeforeExitDialog()
        } else {
            finish()
        }
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    private fun getCurrentContent(): String {
        return if (isSplitMode) {
            splitEditorText.text.toString()
        } else {
            editorText.text.toString()
        }
    }

    private fun getCurrentEditor(): EditText {
        return if (isSplitMode) splitEditorText else editorText
    }

    private fun getCurrentScrollView(): ScrollView {
        return if (isSplitMode) splitEditorScroll else editorLayer
    }

    private fun showSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(getCurrentEditor(), InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(getCurrentEditor().windowToken, 0)
    }

    private fun updateFilenameDisplay() {
        val displayName = if (fileName?.endsWith(".md") == true) {
            fileName
        } else {
            "$fileName.md"
        }
        textFilename.text = displayName ?: "未命名.md"
    }

    private var savingDialog: ProgressDialog? = null

    private fun showSavingDialog() {
        savingDialog = ProgressDialog(this).apply {
            setMessage("正在保存...")
            setCancelable(false)
            show()
        }
    }

    private fun dismissSavingDialog() {
        savingDialog?.dismiss()
        savingDialog = null
    }

    private fun markAsModified() {
        isModified = true
        updateSaveButton()
    }

    private fun updateSaveButton() {
        btnSave.isEnabled = isModified
        btnSave.alpha = if (isModified) 1.0f else 0.5f
    }

    private fun syncEditors() {
        // 同步两个编辑器的内容
        editorText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isSyncing) {
                    isSyncing = true
                    splitEditorText.setText(s?.toString() ?: "")
                    isSyncing = false
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        splitEditorText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isSyncing) {
                    isSyncing = true
                    editorText.setText(s?.toString() ?: "")
                    isSyncing = false
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private var isSyncing = false

    private fun detectKeyboardStatus() {
        // 使用 KeyboardDetector 检测键盘状态
        val keyboardType = keyboardDetector.detectKeyboardType()
        if (keyboardDetector.shouldShowIndicator()) {
            keyboardIndicator.text = keyboardDetector.getKeyboardLabelText()
            keyboardIndicator.visibility = View.VISIBLE
        } else {
            keyboardIndicator.visibility = View.GONE
        }

        // 根据 F11 键盘状态调整分屏比例
        if (keyboardType == KeyboardType.F11_PHYSICAL && isSplitMode) {
            adjustSplitRatioForKeyboard()
        }
    }

    /**
     * 根据键盘类型调整分屏比例
     */
    private fun adjustSplitRatioForKeyboard() {
        val ratio = keyboardDetector.getOptimalSplitRatio()
        // TODO: 实际调整分屏布局权重
        Log.d("MarkdownEditorActivity", "Split ratio adjusted to: $ratio")
    }

    // 文本变化监听器
    private val textWatcher = object : TextWatcher {
        private var beforeChangeContent: String = ""

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            // 保存变化前的内容（撤销/重做操作时不保存）
            if (!isSyncing && !isUndoingOrRedoing && s != null) {
                beforeChangeContent = s.toString()
            }
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (!isSyncing && !isUndoingOrRedoing) {
                markAsModified()
                updatePreview()
            }
        }

        override fun afterTextChanged(s: Editable?) {
            // 文本变化后，将变化前的状态保存到撤销栈（撤销/重做操作时不保存）
            if (!isSyncing && !isUndoingOrRedoing && beforeChangeContent.isNotEmpty()) {
                // 只有内容真正变化时才保存（避免重复保存相同内容）
                if (undoStack.isEmpty() || undoStack.last() != beforeChangeContent) {
                    undoStack.add(beforeChangeContent)
                    // 限制撤销栈大小，避免内存占用过大
                    if (undoStack.size > 50) {
                        undoStack.removeAt(0)
                    }
                }
                // 文本变化后清空重做栈
                redoStack.clear()
                beforeChangeContent = ""
            }
        }
    }

    // =========================================================================
    // 目录项数据类
    // =========================================================================

    data class TocItem(val title: String, val level: Int, val lineNumber: Int)

    // 目录适配器 - 统一格式规范
    // - 标题不包含 # 符号
    // - 所有标题字体大小统一
    // - 仅通过缩进区分层级 (H1=0, H2=40px, H3=80px...)
    class TocSimpleAdapter(context: Context, items: List<TocItem>) : ArrayAdapter<TocItem>(context, android.R.layout.simple_list_item_1, items) {
        companion object {
            /** 统一字体大小 (sp) */
            const val UNIFIED_TEXT_SIZE = 20f

            /** 每级缩进 (dp) - H1=0, H2=1单位, H3=2单位... */
            const val INDENT_PER_LEVEL_DP = 16
        }

        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val item = getItem(position) ?: return view
            val textView = view as TextView

            // 标题文本不包含 # 符号
            textView.text = item.title

            // 统一字体大小，不根据级别变化
            textView.textSize = UNIFIED_TEXT_SIZE

            // 👇 添加以下代码设置 0.5 倍行距
            textView.setLineSpacing(0f, 0.5f)  // (add, multiplier)
            textView.includeFontPadding = false

            // 通过缩进区分层级
            // H1 (level=1): 无缩进
            // H2 (level=2): 1单位缩进
            // H3 (level=3): 2单位缩进
            val indentDp = (item.level - 1) * INDENT_PER_LEVEL_DP
            val indentPx = (indentDp * textView.resources.displayMetrics.density).toInt()

            // 设置左边距实现缩进
            textView.setPadding(
                indentPx,
                textView.paddingTop,
                textView.paddingRight,
                textView.paddingBottom
            )

            return view
        }
    }

    // =========================================================================
    // 渲染引擎设置
    // =========================================================================

    /**
     * 加载用户偏好设置
     */
    private fun loadUserPreferences() {
        // 读取渲染引擎偏好，默认为 MARKWON
        val engineName = prefs.getString("render_engine", "MARKWON") ?: "MARKWON"
        renderEngine = try {
            RenderEngine.valueOf(engineName)
        } catch (e: IllegalArgumentException) {
            RenderEngine.MARKWON
        }

        Log.d("MarkdownEditorActivity", "Loaded render engine: $renderEngine")
    }

    /**
     * 保存用户偏好设置
     */
    private fun saveUserPreferences() {
        prefs.edit()
            .putString("render_engine", renderEngine.name)
            .apply()

        Log.d("MarkdownEditorActivity", "Saved render engine: $renderEngine")
    }

    /**
     * 显示设置对话框
     */
    private fun showSettingsDialog() {
        val engines = RenderEngine.values()
        val engineNames = engines.map {
            when (it) {
                RenderEngine.MARKWON -> "Markwon (稳定)"
                RenderEngine.RUST_CORE -> "Rust Core (实验性)"
            }
        }.toTypedArray()

        val currentIndex = engines.indexOf(renderEngine)

        AlertDialog.Builder(this)
            .setTitle("选择渲染引擎")
            .setSingleChoiceItems(engineNames, currentIndex) { dialog, which ->
                // 保存选择
                val selectedEngine = engines[which]
                if (selectedEngine != renderEngine) {
                    renderEngine = selectedEngine
                    saveUserPreferences()

                    // 刷新预览
                    updatePreview()

                    Toast.makeText(
                        this,
                        "已切换到 ${engineNames[which]}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 使用 Rust Core 渲染 Markdown
     */
    private fun renderWithRustCore(content: String, textView: TextView) {
        try {
            // 释放之前的文档句柄
            if (rustCoreDocumentHandle != 0L) {
                MarkdownCore.nativeRelease(rustCoreDocumentHandle)
                rustCoreDocumentHandle = 0L
            }

            // 创建新文档
            rustCoreDocumentHandle = MarkdownCore.nativeCreate(content)

            if (rustCoreDocumentHandle == 0L) {
                // Rust Core 渲染失败，显示错误信息
                textView.text = "Rust Core 渲染失败\n\n回退到 Markwon..."
                Log.e("MarkdownEditorActivity", "Rust Core nativeCreate failed")
                return
            }

            // 获取渲染命令
            val outCommands = LongArray(2)      // [commands_ptr, commands_count]
            val outDirtyRects = LongArray(8)    // [x, y, w, h, x, y, w, h]
            val outTotalHeight = IntArray(1)   // [total_height]

            val result = MarkdownCore.nativeLoadRange(
                rustCoreDocumentHandle,
                0,              // 从第 0 行开始
                1000,           // 加载前 1000 行
                outCommands,
                outDirtyRects,
                outTotalHeight
            )

            if (result == 0) {
                val commandsPtr = outCommands[0]
                val commandsCount = outCommands[1].toInt()

                Log.d("MarkdownEditorActivity", "Rust Core rendered: commandsPtr=$commandsPtr, count=$commandsCount")

                if (commandsPtr != 0L && commandsCount > 0) {
                    // 将渲染命令转换为 SpannableString
                    val spannable = convertCommandsToSpannable(commandsPtr, commandsCount)
                    textView.text = spannable

                    // 释放 Rust 分配的命令内存
                    MarkdownCore.nativeFreeCommands(commandsPtr, commandsCount)
                } else {
                    // 没有渲染命令，显示原始内容
                    textView.text = content
                }
            } else {
                // 渲染失败
                textView.text = "Rust Core 渲染失败 (错误代码: $result)\n\n$content"
            }

        } catch (e: Exception) {
            Log.e("MarkdownEditorActivity", "Error rendering with Rust Core", e)
            textView.text = "Rust Core 渲染异常: ${e.message}\n\n$content"
        }
    }

    /**
     * 将 Rust Core 渲染命令转换为 SpannableString
     *
     * Rust RenderCommand 结构（C ABI）：
     * - cmd_type: i32 (4 bytes)
     * - x, y, width, height: f32 (4 bytes each, 16 bytes total)
     * - color: Color {r,g,b,a} (4 bytes)
     * - data: RenderCommandData (24 bytes)
     *   对于 DrawText，data 包含 TextData：
     *   - text_ptr: u64 (8 bytes) @ offset 24-31
     *   - text_len: u32 (4 bytes) @ offset 32-35
     *   - font_family: u8 (1 byte) @ offset 36
     *   - font_size_pt: u8 (1 byte) @ offset 37
     *   - font_bold: u8 (1 byte) @ offset 38
     *   - font_italic: u8 (1 byte) @ offset 39
     *   - _pad: [u8; 8] (8 bytes) @ offset 40-47
     * 总计：48 bytes
     */
    private fun convertCommandsToSpannable(commandsPtr: Long, commandsCount: Int): SpannableString {
        // 读取命令数据
        val commandSize = 48  // 每个 RenderCommand 48 字节（C ABI）
        val dataSize = commandsCount * commandSize

        if (dataSize <= 0 || commandsPtr == 0L) {
            return SpannableString.valueOf("")
        }

        try {
            val bytes = MarkdownCore.nativeReadBytes(commandsPtr, dataSize)
            if (bytes == null || bytes.isEmpty()) {
                Log.e("MarkdownEditorActivity", "nativeReadBytes returned null or empty")
                return SpannableString.valueOf("")
            }

            // 存储文本片段信息
            data class TextSegment(
                val text: String,
                val x: Float,
                val y: Float,
                val color: Int,
                val fontSizePt: Int,
                val bold: Boolean,
                val italic: Boolean
            )

            val segments = mutableListOf<TextSegment>()
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.nativeOrder())

            repeat(commandsCount) {
                val startPos = buffer.position()

                val cmdType = buffer.int
                val x = buffer.float
                val y = buffer.float
                val width = buffer.float
                val height = buffer.float

                // 读取颜色（RGBA 各 1 字节，打包成 i32）
                val colorPacked = buffer.int
                val a = (colorPacked shr 24) and 0xFF
                val r = (colorPacked shr 16) and 0xFF
                val g = (colorPacked shr 8) and 0xFF
                val b = colorPacked and 0xFF
                val color = android.graphics.Color.argb(a, r, g, b)

                when (cmdType) {
                    0 -> {  // CMD_DRAW_TEXT
                        // TextData 结构（24 字节）
                        val textPtr = buffer.long
                        val textLen = buffer.int
                        val fontFamily = buffer.get().toInt()
                        val fontSizePt = buffer.get().toInt()
                        val fontBold = buffer.get().toInt() != 0
                        val fontItalic = buffer.get().toInt() != 0
                        // 跳过 padding（8 字节）
                        buffer.position(buffer.position() + 8)

                        // 读取文本内容
                        if (textPtr != 0L && textLen > 0) {
                            val textBytes = MarkdownCore.nativeReadBytes(textPtr, textLen)
                            if (textBytes != null && textBytes.isNotEmpty()) {
                                val text = String(textBytes, Charsets.UTF_8)
                                segments.add(TextSegment(text, x, y, color, fontSizePt, fontBold, fontItalic))
                            }
                        }
                    }
                    else -> {
                        // 跳过 data 区域（24 字节）和前面的字段（24 字节）
                        buffer.position(startPos + commandSize)
                    }
                }
            }

            if (segments.isEmpty()) {
                return SpannableString.valueOf("")
            }

            // 按位置排序（从上到下，从左到右）
            segments.sortWith { a, b ->
                val yDiff = a.y - b.y
                if (kotlin.math.abs(yDiff) > 10f) {
                    yDiff.compareTo(0f)
                } else {
                    a.x.compareTo(b.x)
                }
            }

            // 构建文本和 Span 信息
            val fullText = StringBuilder()

            // 使用简单数据结构存储 Span 信息
            data class SpanInfo(
                val start: Int,
                val end: Int,
                val bold: Boolean,
                val italic: Boolean,
                val color: Int,
                val fontSizePx: Int
            )

            val spanInfos = mutableListOf<SpanInfo>()

            for (segment in segments) {
                val start = fullText.length
                fullText.append(segment.text)
                val end = fullText.length

                // 记录 Span 信息
                spanInfos.add(SpanInfo(
                    start = start,
                    end = end,
                    bold = segment.bold,
                    italic = segment.italic,
                    color = segment.color,
                    fontSizePx = (segment.fontSizePt * 300 / 72).toInt() // pt to px at 300 DPI
                ))

                // 添加换行符（如果是新的行）
                fullText.append("\n")
            }

            // 创建 SpannableString 并应用 Span
            val spannable = SpannableString.valueOf(fullText.toString())
            for (info in spanInfos) {
                // 应用粗体
                if (info.bold) {
                    spannable.setSpan(
                        StyleSpan(android.graphics.Typeface.BOLD),
                        info.start, info.end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                // 应用斜体
                if (info.italic) {
                    spannable.setSpan(
                        StyleSpan(android.graphics.Typeface.ITALIC),
                        info.start, info.end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                // 应用颜色
                spannable.setSpan(
                    ForegroundColorSpan(info.color),
                    info.start, info.end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                // 应用字号
                spannable.setSpan(
                    android.text.style.AbsoluteSizeSpan(info.fontSizePx),
                    info.start, info.end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            Log.d("MarkdownEditorActivity", "Created SpannableString with ${segments.size} segments")
            return spannable

        } catch (e: Exception) {
            Log.e("MarkdownEditorActivity", "Error in convertCommandsToSpannable", e)
            return SpannableString.valueOf("")
        }
    }

    // =========================================================================
    // 文件打开功能
    // =========================================================================

    companion object {
        private const val OPEN_FILE_REQUEST_CODE = 1001
        private const val OPEN_SAMPLE_REQUEST_CODE = 1002

        // Markwon 内部类名常量（用于反射访问）
        private const val CODE_BLOCK_SPAN = "io.noties.markwon.core.spans.CodeBlockSpan"
        private const val FENCED_CODE_BLOCK_SPAN = "io.noties.markwon.core.spans.FencedCodeBlockSpan"
        private const val LINK_SPAN = "io.noties.markwon.core.span.LinkSpan"
        private const val THEMATIC_BREAK_SPAN = "io.noties.markwon.core.spans.ThematicBreakSpan"
    }

    /**
     * 显示打开文件对话框
     */
    private fun showOpenFileDialog() {
        // 创建选择对话框
        val options = arrayOf("选择文件", "打开测试文档")

        AlertDialog.Builder(this)
            .setTitle("打开文件")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openFilePicker()
                    1 -> openSampleDocument()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 打开系统文件选择器
     */
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/markdown"
            // 也支持纯文本
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/markdown",
                "text/plain",
                "text/x-markdown"
            ))
        }
        startActivityForResult(intent, OPEN_FILE_REQUEST_CODE)
    }

    /**
     * 打开测试文档
     */
    private fun openSampleDocument() {
        loadAssetSample()
    }

    /**
     * 处理文件选择结果
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            OPEN_FILE_REQUEST_CODE -> {
                if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                    data.data?.let { uri ->
                        openFileFromUri(uri)
                    }
                }
            }
        }
    }

    /**
     * 从 URI 打开文件
     */
    private fun openFileFromUri(uri: android.net.Uri) {
        try {
            // 请求持久化读取权限
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            // 读取文件内容
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }

            if (content != null) {
                // 检查是否有未保存的修改
                if (isModified) {
                    showSaveBeforeOpenDialog(content, uri)
                } else {
                    loadContent(content, uri)
                }
            } else {
                Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MarkdownEditorActivity", "Error opening file", e)
            Toast.makeText(this, "打开文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 在打开新文件前保存当前内容的对话框
     */
    private fun showSaveBeforeOpenDialog(newContent: String, uri: android.net.Uri) {
        val displayName = fileName ?: "未命名.md"

        AlertDialog.Builder(this)
            .setTitle("保存修改？")
            .setMessage("当前文档有未保存的修改，是否保存后打开新文件？")
            .setPositiveButton("保存") { _, _ ->
                saveFile()
                loadContent(newContent, uri)
            }
            .setNegativeButton("放弃") { _, _ ->
                loadContent(newContent, uri)
            }
            .setNeutralButton("取消", null)
            .show()
    }

    /**
     * 加载内容到编辑器
     */
    private fun loadContent(content: String, uri: android.net.Uri? = null) {
        editorText.setText(content)
        splitEditorText.setText(content)
        lastSavedContent = content
        isModified = false
        updateSaveButton()

        // 更新文件路径和名称
        if (uri != null) {
            filePath = uri.toString()
            fileName = getFileNameFromUri(uri)
            updateFilenameDisplay()
        }

        // 清空撤销重做栈并保存初始状态
        undoStack.clear()
        redoStack.clear()
        undoStack.add(content)

        Log.d("MarkdownEditorActivity", "Loaded file: $filePath, size: ${content.length}")
    }

    /**
     * 从 URI 获取文件名
     */
    private fun getFileNameFromUri(uri: android.net.Uri): String {
        var result = ""
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result.isEmpty()) {
            result = uri.lastPathSegment ?: "未命名.md"
        }
        return result
    }
}
