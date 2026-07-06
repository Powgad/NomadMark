package com.editor.nomadmark

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.io.File

import com.editor.nomadmark.GestureType

import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import android.graphics.Color
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
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
    private lateinit var btnSearchConfirm: ImageButton
    private lateinit var btnSearchPrev: ImageButton
    private lateinit var btnSearchNext: ImageButton
    private lateinit var btnSearchClose: ImageButton
    private lateinit var replaceRow: LinearLayout
    private lateinit var replaceInput: EditText
    private lateinit var btnReplaceOne: Button
    private lateinit var btnReplaceAll: Button

    // 编辑/预览区域
    private lateinit var editorScrollView: ScrollView
    private lateinit var editorText: EditText
    private lateinit var previewScrollView: ScrollView
    private lateinit var previewText: TextView
    private lateinit var splitView: LinearLayout
    private lateinit var splitPreviewScroll: ScrollView
    private lateinit var splitPreviewText: TextView
    private lateinit var splitEditorScroll: ScrollView
    private lateinit var splitEditorText: EditText

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

    // 目录面板
    private lateinit var tocPanel: FrameLayout
    private lateinit var tocList: ListView
    private lateinit var tocCloseArea: View

    // 键盘标识
    private lateinit var keyboardIndicator: TextView

    // 手势识别组件
    private lateinit var gestureOverlay: GestureOverlayView
    private lateinit var gestureEditor: GestureEditor
    private lateinit var contentContainer: FrameLayout

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
    // Core 文档集成（暂时禁用）
    // =========================================================================
    // Core 层 JNI 接口尚未完全实现，暂时使用本地实现
    // 待实现以下函数后可重新启用：
    // - Java_com_editor_MarkdownCore_nativeSearch (需返回 Java 数组)
    // - 文档内容获取 API
    //
    // /** Core 文档句柄 */
    // private var coreDocumentHandle: Long = 0L
    // /** 是否使用 Core 层搜索 */
    // private var useCoreSearch = false

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

    // =========================================================================
    // 生命周期
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

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
        // Markwon 配置 - 使用默认配置
        // 注意：尝试自定义主题在当前版本中不可行，使用默认配置
        markwon = Markwon.builder(this)
            .usePlugin(CorePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(TaskListPlugin.create(this))
            .usePlugin(ImagesPlugin.create())
            .build()
    }

    /**
     * 移除文本中的下划线并确保分割线可见
     * 用于标题等不应有下划线的文本
     */
    private fun removeUnderlines(spanned: Spanned) {
        // 获取所有 span 类型用于调试
        val allSpans = (0 until spanned.length).flatMap { i ->
            val spans = spanned.getSpans(i, i + 1, Any::class.java)
            spans.map { it.javaClass.simpleName }
        }.distinct()
        Log.d("removeUnderlines", "All span types: $allSpans")

        // 只移除 UnderlineSpan
        val underlineSpans = spanned.getSpans(0, spanned.length, android.text.style.UnderlineSpan::class.java)
        Log.d("removeUnderlines", "Found ${underlineSpans.size} UnderlineSpans")
        for (span in underlineSpans) {
            (spanned as android.text.Spannable).removeSpan(span)
        }

        // 确保分割线可见 - 检查 ThematicBreakSpan
        val thematicBreakSpans = spanned.getSpans(0, spanned.length, io.noties.markwon.core.spans.ThematicBreakSpan::class.java)
        Log.d("removeUnderlines", "Found ${thematicBreakSpans.size} ThematicBreakSpans")
        for (span in thematicBreakSpans) {
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            Log.d("removeUnderlines", "ThematicBreakSpan at [$start-$end]")
        }
    }

    private fun initViews() {
        // 顶部工具栏
        btnBack = findViewById(R.id.btn_back)
        btnToc = findViewById(R.id.btn_toc)
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
        btnSearchConfirm = findViewById(R.id.btn_search_confirm)
        btnSearchPrev = findViewById(R.id.btn_search_prev)
        btnSearchNext = findViewById(R.id.btn_search_next)
        btnSearchClose = findViewById(R.id.btn_search_close)
        replaceRow = findViewById(R.id.replace_row)
        replaceInput = findViewById(R.id.replace_input)
        btnReplaceOne = findViewById(R.id.btn_replace_one)
        btnReplaceAll = findViewById(R.id.btn_replace_all)

        // 编辑/预览区域
        editorScrollView = findViewById(R.id.editor_scroll_view)
        editorText = findViewById(R.id.editor_text)
        previewScrollView = findViewById(R.id.preview_scroll_view)
        previewText = findViewById(R.id.preview_text)
        splitView = findViewById(R.id.split_view)
        splitPreviewScroll = findViewById(R.id.split_preview_scroll)
        splitPreviewText = findViewById(R.id.split_preview_text)
        splitEditorScroll = findViewById(R.id.split_editor_scroll)
        splitEditorText = findViewById(R.id.split_editor_text)

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

        // 目录
        tocPanel = findViewById(R.id.toc_panel)
        tocList = findViewById(R.id.toc_list)
        tocCloseArea = findViewById(R.id.toc_close_area)

        // 键盘标识
        keyboardIndicator = findViewById(R.id.keyboard_indicator)

        // 内容容器（用于添加手势覆盖层）
        contentContainer = findViewById(R.id.content_container)

        // 设置手势覆盖层
        setupGestureOverlay()

        // 设置等宽字体
        val monoFont = Typeface.MONOSPACE
        editorText.typeface = monoFont
        splitEditorText.typeface = monoFont
    }

    private fun setupGestureOverlay() {
        // 从 XML 中获取手势覆盖层
        gestureOverlay = findViewById(R.id.gesture_overlay)

        // 创建手势编辑器
        gestureEditor = GestureEditor(null)  // 暂时传 null，后续可以传 MarkdownEditorView

        // 设置手势识别回调
        gestureOverlay.onGestureRecognized = { result ->
            when (result.gestureType) {
                GestureType.DELETE -> {
                    // 删除手势
                    gestureEditor.deleteTextRange(result.boundingBox, getCurrentEditor())
                    markAsModified()
                    Toast.makeText(this, "已删除选中内容", Toast.LENGTH_SHORT).show()
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
        gestureOverlay.onGestureRejected = {
            // 可以在这里添加震动反馈等
            Log.d("MarkdownEditorActivity", "Gesture not recognized")
        }

        // 初始状态为禁用（修订模式开启时启用）
        gestureOverlay.isGestureEnabled = false

        Log.d("MarkdownEditorActivity", "GestureOverlayView setup complete")
    }

    private fun setupListeners() {
        // 顶部工具栏
        btnBack.setOnClickListener { finishWithSaveCheck() }
        btnToc.setOnClickListener { toggleToc() }
        // textFilename - 仅显示文件名，不可点击
        btnPreviewToggle.setOnClickListener { togglePreviewMode() }
        btnRevision.setOnClickListener { toggleRevisionMode() }
        btnSearch.setOnClickListener { toggleSearchBar() }
        btnSplit.setOnClickListener { toggleSplitMode() }
        btnUndo.setOnClickListener { undo() }
        btnRedo.setOnClickListener { redo() }
        btnToolbarToggle.setOnClickListener { toggleBottomToolbar() }
        // btnKeyboardSettings - 按键设置图标（功能待实现）
        btnSave.setOnClickListener { saveFile() }

        // 搜索栏
        btnSearchClose.setOnClickListener { toggleSearchBar() }
        btnSearchConfirm.setOnClickListener { performSearch() }
        searchInput.setOnEditorActionListener { _, _, _ ->
            performSearch()
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

        // 目录关闭
        tocCloseArea.setOnClickListener { toggleToc() }
    }

    // =========================================================================
    // 文件处理
    // =========================================================================

    private fun handleOpenIntent(intent: Intent?) {
        val extras = intent?.extras
        val path = extras?.getString("file_path")

        if (!path.isNullOrEmpty()) {
            filePath = path
            fileName = File(path).nameWithoutExtension
            loadFile(path)
        } else {
            // 创建新文件
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

    private fun togglePreviewMode() {
        isPreviewMode = !isPreviewMode

        if (isPreviewMode) {
            // 切换到预览模式
            btnPreviewToggle.setImageResource(R.drawable.ic_preview_on)
            editorScrollView.visibility = View.GONE
            previewScrollView.visibility = View.VISIBLE
            updatePreview()
        } else {
            // 切换到编辑模式
            btnPreviewToggle.setImageResource(R.drawable.ic_preview_off)
            editorScrollView.visibility = View.VISIBLE
            previewScrollView.visibility = View.GONE
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
            editorScrollView.visibility = View.GONE
            previewScrollView.visibility = View.GONE
            splitView.visibility = View.VISIBLE
            updatePreview()

            // 启用滚动同步
            enableScrollSync()
        } else {
            // 关闭分屏
            btnSplit.setImageResource(R.drawable.ic_split_off)
            splitView.visibility = View.GONE
            if (isPreviewMode) {
                previewScrollView.visibility = View.VISIBLE
            } else {
                editorScrollView.visibility = View.VISIBLE
            }

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

    private fun toggleRevisionMode() {
        isRevisionMode = !isRevisionMode

        if (isRevisionMode) {
            // 开启修订模式 - 光标消失
            btnRevision.alpha = 1.0f
            editorText.isCursorVisible = false
            splitEditorText.isCursorVisible = false

            // 启用手势识别
            gestureOverlay.isGestureEnabled = true

            // 隐藏软键盘
            hideSoftKeyboardFromAll()

            Toast.makeText(this, "修订模式已开启", Toast.LENGTH_SHORT).show()
        } else {
            // 关闭修订模式
            btnRevision.alpha = 0.5f
            editorText.isCursorVisible = true
            splitEditorText.isCursorVisible = true

            // 禁用手势识别
            gestureOverlay.isGestureEnabled = false

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
            // 关闭搜索栏时清除所有内容
            searchInput.text.clear()
            replaceInput.text.clear()
            searchResults.clear()
            currentSearchIndex = 0
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
        }
    }

    // =========================================================================
    // 搜索和替换
    // =========================================================================

    private fun performSearch() {
        val query = searchInput.text.toString()
        if (query.isEmpty()) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            return
        }

        // 使用本地搜索实现（Core 层 JNI 接口未完全实现）
        performLocalSearch(query)

        // 显示替换选项
        replaceRow.visibility = View.VISIBLE
    }

    /**
     * 本地搜索实现
     */
    private fun performLocalSearch(query: String) {
        searchResults.clear()
        currentSearchIndex = 0

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
            Toast.makeText(this, "找到 ${searchResults.size} 个匹配项", Toast.LENGTH_SHORT).show()
            highlightSearchResult(0)
        }
    }

    private fun findNext() {
        if (searchResults.isEmpty()) return
        currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
        highlightSearchResult(currentSearchIndex)
    }

    private fun findPrev() {
        if (searchResults.isEmpty()) return
        currentSearchIndex = (currentSearchIndex - 1 + searchResults.size) % searchResults.size
        highlightSearchResult(currentSearchIndex)
    }

    private fun highlightSearchResult(index: Int) {
        val (start, end) = searchResults[index]
        val editor = getCurrentEditor()
        editor.setSelection(start, end)
    }

    private fun replaceOne() {
        if (searchResults.isEmpty()) return
        val replaceText = replaceInput.text.toString()
        val (start, end) = searchResults[currentSearchIndex]

        val editor = getCurrentEditor()
        val content = editor.text.toString()
        val newContent = content.substring(0, start) + replaceText + content.substring(end)
        editor.setText(newContent)
        editor.setSelection(start + replaceText.length)

        // 移除当前结果
        searchResults.removeAt(currentSearchIndex)
        if (searchResults.isEmpty()) {
            Toast.makeText(this, "已完成替换", Toast.LENGTH_SHORT).show()
        }
    }

    private fun replaceAll() {
        if (searchResults.isEmpty()) return
        val replaceText = replaceInput.text.toString()
        val query = searchInput.text.toString()

        val editor = getCurrentEditor()
        val newContent = editor.text.toString().replace(query, replaceText, ignoreCase = true)
        editor.setText(newContent)

        Toast.makeText(this, "已替换 ${searchResults.size} 处", Toast.LENGTH_SHORT).show()
        searchResults.clear()
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

        redoStack.add(getCurrentContent())
        val previous = undoStack.removeAt(undoStack.size - 1)
        getCurrentEditor().setText(previous)
        markAsModified()
    }

    /**
     * 本地重做实现
     */
    private fun performLocalRedo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "没有可重做的操作", Toast.LENGTH_SHORT).show()
            return
        }

        undoStack.add(getCurrentContent())
        val next = redoStack.removeAt(redoStack.size - 1)
        getCurrentEditor().setText(next)
        markAsModified()
    }

    // =========================================================================
    // 预览渲染
    // =========================================================================

    private fun updatePreview() {
        val content = getCurrentContent()

        // 使用 Markwon 渲染 Markdown
        if (isPreviewMode) {
            markwon.setMarkdown(previewText, content)
            // 移除下划线
            removeUnderlines(previewText.text as Spanned)
        }
        if (isSplitMode) {
            markwon.setMarkdown(splitPreviewText, content)
            // 移除下划线
            removeUnderlines(splitPreviewText.text as Spanned)
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
        return if (isSplitMode) splitEditorScroll else editorScrollView
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
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (!isSyncing) {
                markAsModified()
                updatePreview()
            }
        }
        override fun afterTextChanged(s: Editable?) {}
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
}
