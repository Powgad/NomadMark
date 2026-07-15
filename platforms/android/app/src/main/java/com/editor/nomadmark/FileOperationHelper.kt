package com.editor.nomadmark

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
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
     * 获取文件保存目录
     *
     * 优先级顺序：
     * 1. /sdcard/Document (Supernote 设备默认目录)
     * 2. Environment.getExternalStoragePublicDirectory/Documents (标准 Android)
     * 3. context.filesDir (应用私有目录，降级方案)
     */
    private fun getFileDir(): File {
        // 1. 尝试使用 Supernote 的 /sdcard/Document 目录
        val noteTakerDir = trySelectNoteTakerDocumentDir()
        if (noteTakerDir != null) {
            Log.d("FileOperationHelper", "使用 /sdcard/Document 目录")
            return noteTakerDir
        }

        // 2. 尝试使用标准 Android Documents 目录
        val standardDir = trySelectStandardDocumentsDir()
        if (standardDir != null) {
            Log.d("FileOperationHelper", "使用标准 Documents 目录")
            return standardDir
        }

        // 3. 降级到应用私有目录
        Log.d("FileOperationHelper", "降级到应用私有目录")
        return context.filesDir
    }

    /**
     * 尝试选择 /sdcard/Document 目录（Supernote 设备）
     * @return 可用的目录，如果不可用则返回 null
     */
    private fun trySelectNoteTakerDocumentDir(): File? {
        val possiblePaths = listOf(
            "/sdcard/Document",
            "/mnt/sdcard/Document",
            "/storage/emulated/0/Document"
        )

        for (path in possiblePaths) {
            try {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory && dir.canWrite()) {
                    // 目录存在且可写，直接使用
                    return dir
                } else if (dir.exists() && dir.isDirectory) {
                    // 目录存在但不可写，尝试创建子目录
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Android 11+ 检查是否有广泛存储权限
                        if (Environment.isExternalStorageManager()) {
                            // 有权限，尝试创建目录
                            if (dir.mkdirs() || dir.canWrite()) {
                                return dir
                            }
                        }
                    } else {
                        // Android 10 及以下，尝试创建目录
                        if (dir.mkdirs() || dir.canWrite()) {
                            return dir
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("FileOperationHelper", "无法访问路径: $path", e)
            }
        }

        return null
    }

    /**
     * 尝试选择标准 Android Documents 目录
     * @return 可用的目录，如果不可用则返回 null
     */
    private fun trySelectStandardDocumentsDir(): File? {
        return try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val nomadMarkDir = File(documentsDir, "NomadMark")

            if (!nomadMarkDir.exists()) {
                // 尝试创建 NomadMark 子目录
                nomadMarkDir.mkdirs()
            }

            // 验证目录是否可用
            if (nomadMarkDir.exists() && nomadMarkDir.canWrite()) {
                nomadMarkDir
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("FileOperationHelper", "无法访问标准 Documents 目录", e)
            null
        }
    }

    /**
     * 生成唯一文件路径
     */
    fun generateUniquePath(baseName: String): String {
        val dir = getFileDir()
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
     * 获取当前文件保存目录（用于调试和显示）
     * @return 当前使用的文件保存目录
     */
    fun getCurrentFileDir(): File {
        return getFileDir()
    }

    /**
     * 获取当前文件保存目录的描述信息
     * @return 目录路径和可用性描述
     */
    fun getFileDirDescription(): String {
        val dir = getFileDir()
        val canWrite = try {
            dir.canWrite()
        } catch (e: Exception) {
            false
        }
        val exists = try {
            dir.exists()
        } catch (e: Exception) {
            false
        }

        return buildString {
            append("路径: ${dir.absolutePath}\n")
            append("存在: $exists\n")
            append("可写: $canWrite")
        }
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
