package com.editor.nomadmark.autosave

import android.util.Log
import java.io.File

/**
 * AutoSave 扩展函数
 *
 * 提供 File 操作的工具方法，确保操作安全且不抛出异常。
 */

private const val TAG = "AutoSaveExtensions"

/**
 * 安全删除文件
 *
 * 尝试删除文件，如果失败不会抛出异常。
 * @return 是否删除成功
 */
fun File.deleteQuietly(): Boolean {
    return try {
        if (exists()) {
            delete()
        } else {
            true // 文件不存在视为成功
        }
    } catch (e: Exception) {
        Log.w(TAG, "删除文件失败: ${absolutePath}", e)
        false
    }
}

/**
 * 读取文本内容（安全）
 *
 * 尝试读取文件内容，如果失败返回 null。
 * @return 文件内容，失败返回 null
 */
fun File.readTextQuietly(): String? {
    return try {
        if (exists()) {
            readText()
        } else {
            Log.w(TAG, "文件不存在: ${absolutePath}")
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "读取文件失败: ${absolutePath}", e)
        null
    }
}

/**
 * 写入文本内容（安全）
 *
 * 尝试写入内容到文件，如果失败返回 false。
 * @param text 要写入的文本
 * @return 是否写入成功
 */
fun File.writeTextQuietly(text: String): Boolean {
    return try {
        // 确保父目录存在
        parentFile?.mkdirs()
        writeText(text)
        true
    } catch (e: Exception) {
        Log.e(TAG, "写入文件失败: ${absolutePath}", e)
        false
    }
}

/**
 * 获取文件大小（可读格式）
 *
 * @return 格式化后的文件大小字符串，如 "1.5 KB"
 */
fun File.getFormattedSize(): String {
    val bytes = length()
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
