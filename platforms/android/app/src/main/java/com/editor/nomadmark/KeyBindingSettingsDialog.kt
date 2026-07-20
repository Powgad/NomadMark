package com.editor.nomadmark

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 按键设置对话框
 *
 * 提供快捷键设置界面，包括快捷键列表查看、编辑、录制和恢复默认功能。
 * 同时提供正文字体大小设置功能。
 */
class KeyBindingSettingsDialog(private val activity: MarkdownEditorActivity) {

    private val keyBindingManager = KeyBindingManager(activity)
    private var currentRecordingAction: EditorAction? = null
    private var currentKeys: String = ""

    companion object {
        private const val TAG = "KeyBindingSettingsDialog"

        // 字体大小选项（单位：sp）
        private val FONT_SIZE_OPTIONS = listOf(16, 18, 20, 22, 24)
        private const val DEFAULT_FONT_SIZE = 16
        private const val PREF_KEY_FONT_SIZE = "editor_font_size"
    }

    /**
     * 显示按键设置对话框
     */
    fun show() {
        showBindingList()
    }

    /**
     * 显示快捷键列表对话框
     */
    private fun showBindingList() {
        val allBindings = keyBindingManager.getAllBindings()

        // 按功能分组
        val groupedBindings = allBindings.groupBy { binding ->
            EditorAction.fromString(binding.actionId)?.category
                ?: ActionCategory.OTHER
        }

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val scrollView = ScrollView(activity)
        val scrollContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 20)
        }

        // 添加字体大小设置区域
        val fontSizeSection = createFontSizeSection()
        scrollContent.addView(fontSizeSection)

        // 分隔线
        val separator = View(activity).apply {
            setBackgroundColor(0xFFCCCCCC.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                setMargins(0, 20, 0, 20)
            }
        }
        scrollContent.addView(separator)

        // 按分类显示快捷键
        for (category in ActionCategory.values()) {
            val bindings = groupedBindings[category] ?: continue
            if (bindings.isEmpty()) continue

            // 分类标题
            val categoryTitle = TextView(activity).apply {
                text = getCategoryLabel(category)
                textSize = 16f
                setPadding(0, 20, 0, 10)
                setTextColor(0xFF000000.toInt())
            }
            scrollContent.addView(categoryTitle)

            // 该分类下的所有快捷键
            for (binding in bindings) {
                val bindingItem = createBindingItem(binding)
                scrollContent.addView(bindingItem)
            }
        }

        scrollView.addView(scrollContent)
        layout.addView(scrollView)

        // 恢复默认按钮
        val restoreButton = Button(activity).apply {
            text = "恢复默认设置"
            setOnClickListener {
                showRestoreDefaultsDialog()
            }
        }
        layout.addView(restoreButton)

        AlertDialog.Builder(activity)
            .setTitle("快捷键设置")
            .setView(layout)
            .setPositiveButton("关闭", null)
            .show()
    }

    /**
     * 创建单个快捷键条目视图
     */
    private fun createBindingItem(binding: KeyBinding): LinearLayout {
        val itemLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
            gravity = Gravity.CENTER_VERTICAL
        }

        // 功能名称
        val actionLabel = TextView(activity).apply {
            text = KeyBinding.getActionLabel(binding.actionId)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        itemLayout.addView(actionLabel)

        // 按键组合
        val keyLabel = TextView(activity).apply {
            text = binding.displayText
            textSize = 14f
            setPadding(20, 0, 20, 0)
            setTextColor(0xFF666666.toInt())
        }
        itemLayout.addView(keyLabel)

        // 编辑按钮 - 黑色边框，透明背景
        val editButton = Button(activity).apply {
            text = "修改"
            setTextColor(0xFF000000.toInt())
            setPadding(20, 10, 20, 10)
            // 创建黑色边框背景
            background = createBorderDrawable()
            setOnClickListener {
                val action = EditorAction.fromString(binding.actionId)
                if (action != null) {
                    showRecordingDialog(action, binding)
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        itemLayout.addView(editButton)

        return itemLayout
    }

    /**
     * 显示按键录制对话框
     *
     * @param action 要设置快捷键的功能
     * @param currentBinding 当前的绑定（如果有的话）
     */
    @Suppress("UNUSED_PARAMETER")
    private fun showRecordingDialog(action: EditorAction, currentBinding: KeyBinding? = null) {
        currentRecordingAction = action
        currentKeys = ""

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
            gravity = Gravity.CENTER
        }

        val hintText = TextView(activity).apply {
            text = "请按下新的快捷键组合..."
            textSize = 16f
            gravity = Gravity.CENTER
        }
        layout.addView(hintText)

        val keysDisplay = TextView(activity).apply {
            text = ""
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
            setTextColor(0xFF000000.toInt())
        }
        layout.addView(keysDisplay)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("设置快捷键: ${action.label}")
            .setView(layout)
            .setPositiveButton("确定") { _, _ ->
                saveRecordedBinding()
            }
            .setNegativeButton("取消") { _, _ ->
                currentRecordingAction = null
                currentKeys = ""
            }
            .setNeutralButton("恢复默认") { _, _ ->
                clearBinding(action)
            }
            .create()

        // 设置按键监听器
        val keyListener = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleKeyPress(keyCode, event, keysDisplay, dialog)
                return@OnKeyListener true
            }
            false
        }

        // 为对话框设置按键监听
        dialog.setOnShowListener {
            dialog.window?.decorView?.setOnKeyListener(keyListener)
            dialog.window?.decorView?.isFocusable = true
            dialog.window?.decorView?.isFocusableInTouchMode = true
            dialog.window?.decorView?.requestFocus()
        }

        dialog.show()
    }

    /**
     * 处理按键按下事件
     */
    @Suppress("UNUSED_PARAMETER")
    private fun handleKeyPress(
        keyCode: Int,
        event: KeyEvent,
        display: TextView,
        dialog: AlertDialog
    ) {
        // 忽略系统键
        if (keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_HOME ||
            keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            return
        }

        val isCtrl = event.isCtrlPressed
        val isShift = event.isShiftPressed
        val isAlt = event.isAltPressed

        // 构建按键显示文本
        val keyText = buildString {
            if (isCtrl) append("Ctrl+")
            if (isShift) append("Shift+")
            if (isAlt) append("Alt+")
            append(KeyBinding.getLabel(keyCode))
        }

        currentKeys = "$keyCode:$isCtrl:$isShift:$isAlt"
        display.text = keyText

        // 检查冲突
        val action = currentRecordingAction ?: return
        val conflict = keyBindingManager.isBindingConflict(
            keyCode, isCtrl, isShift, isAlt,
            excludeActionId = action.id
        )

        if (conflict != null) {
            display.append("\n(已占用: $conflict)")
            display.setTextColor(0xFFFF0000.toInt())
        } else {
            display.setTextColor(0xFF000000.toInt())
        }
    }

    /**
     * 保存录制的快捷键
     */
    private fun saveRecordedBinding() {
        val action = currentRecordingAction ?: return
        val parts = currentKeys.split(":")
        if (parts.size != 4) return

        val keyCode = parts[0].toIntOrNull() ?: return
        val ctrl = parts[1].toBoolean()
        val shift = parts[2].toBoolean()
        val alt = parts[3].toBoolean()

        // 检查冲突
        val conflict = keyBindingManager.isBindingConflict(
            keyCode, ctrl, shift, alt,
            excludeActionId = action.id
        )

        if (conflict != null) {
            Toast.makeText(
                activity,
                "该快捷键已被 \"$conflict\" 占用",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // 保存绑定
        val binding = KeyBinding(keyCode, ctrl, shift, alt, action.id)
        keyBindingManager.saveBinding(binding)

        Toast.makeText(
            activity,
            "已设置: ${binding.displayText} -> ${action.label}",
            Toast.LENGTH_SHORT
        ).show()

        currentRecordingAction = null
        currentKeys = ""

        // 刷新列表
        showBindingList()
    }

    /**
     * 恢复功能的默认快捷键绑定
     */
    private fun clearBinding(action: EditorAction) {
        // 直接按 actionId 删除绑定（恢复为默认）
        keyBindingManager.removeBinding(action.id)
        Toast.makeText(
            activity,
            "已恢复: ${action.label} 的默认快捷键",
            Toast.LENGTH_SHORT
        ).show()

        currentRecordingAction = null
        currentKeys = ""

        // 刷新列表
        showBindingList()
    }

    /**
     * 显示恢复默认设置确认对话框
     */
    private fun showRestoreDefaultsDialog() {
        AlertDialog.Builder(activity)
            .setTitle("恢复默认设置")
            .setMessage("确定要恢复所有快捷键为默认设置吗？所有自定义设置将丢失。")
            .setPositiveButton("确定") { _, _ ->
                keyBindingManager.restoreDefaults()
                Toast.makeText(activity, "已恢复默认设置", Toast.LENGTH_SHORT).show()
                showBindingList()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 获取分类的显示标签
     */
    private fun getCategoryLabel(category: ActionCategory): String {
        return when (category) {
            ActionCategory.FORMAT -> "文本格式"
            ActionCategory.EDIT -> "编辑操作"
            ActionCategory.INSERT -> "插入元素"
            ActionCategory.VIEW -> "视图操作"
            ActionCategory.OTHER -> "其他"
        }
    }

    /**
     * 创建黑色边框、透明背景的 Drawable
     */
    private fun createBorderDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            // 透明背景
            setColor(0x00000000) // 完全透明
            // 黑色边框，宽度 2px
            setStroke(2, 0xFF000000.toInt())
            // 圆角 4dp
            cornerRadius = 8f
        }
    }

    // =========================================================================
    // 字体大小设置
    // =========================================================================

    /**
     * 创建字体大小设置区域
     */
    private fun createFontSizeSection(): LinearLayout {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }

        // 标题
        val title = TextView(activity).apply {
            text = "正文字体大小"
            textSize = 16f
            setTextColor(0xFF000000.toInt())
            setPadding(0, 0, 0, 10)
        }
        container.addView(title)

        // 当前字体大小显示和按钮行
        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        container.addView(buttonRow)

        // 当前值显示
        val currentSizeView = TextView(activity).apply {
            text = "${getCurrentFontSize()}sp"
            textSize = 18f
            setTextColor(0xFF000000.toInt())
            setPadding(20, 0, 20, 0)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                100,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        buttonRow.addView(currentSizeView)

        // 减小按钮
        val decreaseButton = Button(activity).apply {
            text = "-"
            textSize = 20f
            setTextColor(0xFF000000.toInt())
            setPadding(20, 10, 20, 10)
            background = createBorderDrawable()
            setOnClickListener {
                val currentSize = getCurrentFontSize()
                val index = FONT_SIZE_OPTIONS.indexOf(currentSize)
                if (index > 0) {
                    setFontSize(FONT_SIZE_OPTIONS[index - 1])
                    currentSizeView.text = "${FONT_SIZE_OPTIONS[index - 1]}sp"
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                60,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(10, 0, 10, 0)
            }
        }
        buttonRow.addView(decreaseButton)

        // 增大按钮
        val increaseButton = Button(activity).apply {
            text = "+"
            textSize = 20f
            setTextColor(0xFF000000.toInt())
            setPadding(20, 10, 20, 10)
            background = createBorderDrawable()
            setOnClickListener {
                val currentSize = getCurrentFontSize()
                val index = FONT_SIZE_OPTIONS.indexOf(currentSize)
                if (index >= 0 && index < FONT_SIZE_OPTIONS.size - 1) {
                    setFontSize(FONT_SIZE_OPTIONS[index + 1])
                    currentSizeView.text = "${FONT_SIZE_OPTIONS[index + 1]}sp"
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                60,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(10, 0, 10, 0)
            }
        }
        buttonRow.addView(increaseButton)

        // 预设选项
        val presetsLabel = TextView(activity).apply {
            text = "快速选择："
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 10, 0, 5)
        }
        container.addView(presetsLabel)

        val presetsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        container.addView(presetsRow)

        for (size in FONT_SIZE_OPTIONS) {
            val presetButton = Button(activity).apply {
                text = "${size}sp"
                textSize = 14f
                setTextColor(0xFF000000.toInt())
                setPadding(15, 8, 15, 8)
                background = createBorderDrawable()
                setOnClickListener {
                    setFontSize(size)
                    currentSizeView.text = "${size}sp"
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 10, 0)
                }
            }
            presetsRow.addView(presetButton)
        }

        // 说明文字
        val hint = TextView(activity).apply {
            text = "默认字体大小为 16sp，可以增大到最大 24sp"
            textSize = 12f
            setTextColor(0xFF999999.toInt())
            setPadding(0, 10, 0, 0)
        }
        container.addView(hint)

        return container
    }

    /**
     * 获取当前字体大小
     */
    private fun getCurrentFontSize(): Int {
        val prefs = activity.getSharedPreferences("NomadMarkPrefs", Context.MODE_PRIVATE)
        return prefs.getInt(PREF_KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
    }

    /**
     * 设置字体大小
     */
    private fun setFontSize(size: Int) {
        val prefs = activity.getSharedPreferences("NomadMarkPrefs", Context.MODE_PRIVATE)
        prefs.edit().putInt(PREF_KEY_FONT_SIZE, size).apply()

        // 应用字体大小到编辑器
        activity.applyEditorFontSize(size)

        android.util.Log.d(TAG, "Font size set to ${size}sp")
    }
}
