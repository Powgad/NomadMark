package com.editor.nomadmark

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.io.File

/**
 * 文件操作辅助类
 */
class FileOperationHelper(private val context: Context, private val keyboardDetector: KeyboardDetector? = null) {

    /**
     * 显示新建文件对话框
     */
    fun showNewFileDialog(onFileCreated: (String) -> Unit) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("新建文件")

        // 创建输入框
        val input = EditText(context)
        input.hint = "文件名（不含扩展名）"
        input.setSingleLine(true)

        val container = LinearLayout(context)
        container.setPadding(50, 40, 50, 0)
        container.addView(input)

        builder.setView(container)
        builder.setPositiveButton("创建") { _, _ ->
            val fileName = input.text.toString().trim()
            if (validateFileName(fileName)) {
                val fullPath = generateUniquePath(fileName)
                onFileCreated(fullPath)
            } else {
                Toast.makeText(context, "文件名无效", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("取消", null)
        builder.show()

        // 不自动弹出键盘，等待用户点击输入框
        // 当用户点击输入框时，检测键盘类型并显示相应的标识或软键盘
        input.setOnClickListener {
            handleInputFocus(input)
        }

        // 同时处理焦点变化（当输入框获得焦点时）
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                handleInputFocus(input)
            }
        }
    }

    /**
     * 处理输入框获得焦点时的逻辑
     * 检测键盘类型并显示相应的标识或软键盘
     */
    private fun handleInputFocus(input: EditText) {
        val hasPhysicalKeyboard = keyboardDetector?.hasPhysicalKeyboard() ?: false

        if (hasPhysicalKeyboard) {
            // 有外接键盘，显示键盘标识（不弹出软键盘）
            val keyboardType = keyboardDetector?.detectKeyboardType()
            val labelText = when (keyboardType) {
                KeyboardType.F11_PHYSICAL -> "📟 F11"
                KeyboardType.SOFT_KEYBOARD -> "⌨️ 软键盘"
                else -> "⌨️"
            }
            Toast.makeText(context, "$labelText 外接键盘已连接", Toast.LENGTH_SHORT).show()
            // 不弹出软键盘，用户可以直接用物理键盘输入
        } else {
            // 没有外接键盘，弹出软键盘
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * 验证文件名
     */
    fun validateFileName(name: String): Boolean {
        if (name.isEmpty()) return false

        // 检查非法字符
        val illegalChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        if (name.any { it in illegalChars }) return false

        // 检查长度
        if (name.length > 255) return false

        // 检查是否以空格开头或结尾
        if (name.trim() != name) return false

        return true
    }

    /**
     * 生成唯一文件路径
     */
    fun generateUniquePath(baseName: String): String {
        val dir = context.filesDir
        var fileName = if (baseName.endsWith(".md")) baseName else "$baseName.md"
        var path = File(dir, fileName).absolutePath

        var index = 1
        while (File(path).exists()) {
            val nameWithoutExt = if (baseName.endsWith(".md")) {
                baseName.dropLast(3)
            } else {
                baseName
            }
            fileName = "${nameWithoutExt}_$index.md"
            path = File(dir, fileName).absolutePath
            index++
        }

        return path
    }

    /**
     * 检查文件是否已存在
     */
    fun fileExists(path: String): Boolean {
        return File(path).exists()
    }

    /**
     * 获取文件大小（可读格式）
     */
    fun getFormattedFileSize(file: File): String {
        val bytes = file.length()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * 显示保存确认对话框
     */
    fun showSaveConfirmDialog(
        fileName: String,
        onSave: () -> Unit,
        onDiscard: () -> Unit,
        onCancel: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("保存更改")
            .setMessage("文件 \"$fileName\" 有未保存的更改，是否保存？")
            .setPositiveButton("保存") { _, _ -> onSave() }
            .setNegativeButton("不保存") { _, _ -> onDiscard() }
            .setNeutralButton("取消") { _, _ -> onCancel() }
            .show()
    }
}
