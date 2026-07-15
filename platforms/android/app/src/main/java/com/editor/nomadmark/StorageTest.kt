package com.editor.nomadmark

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 存储访问测试工具
 */
object StorageTest {

    private const val TAG = "StorageTest"

    /**
     * 检查是否有存储权限
     */
    fun hasStoragePermission(context: android.content.Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 不需要传统存储权限即可访问 MediaStore 文件
            true
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 测试 Document 目录访问
     */
    fun testDocumentAccess(): String {
        val results = mutableListOf<String>()

        // 测试多个可能的路径
        val paths = listOf(
            "/sdcard/Document",
            "/mnt/sdcard/Document",
            "/storage/emulated/0/Document",
            Environment.getExternalStorageDirectory().absolutePath + "/Document"
        )

        for (path in paths) {
            val dir = File(path)
            try {
                val exists = dir.exists()
                val canRead = dir.canRead()
                val canWrite = dir.canWrite()
                val isDirectory = dir.isDirectory
                val fileCount = if (exists && isDirectory && canRead) {
                    dir.listFiles()?.count { it.extension.equals("md", ignoreCase = true) } ?: 0
                } else {
                    0
                }

                results.add("路径: $path")
                results.add("  存在: $exists, 可读: $canRead, 可写: $canWrite, MD文件: $fileCount")

                if (exists && canRead && fileCount > 0) {
                    // 测试读取第一个文件
                    val firstMd = dir.listFiles()?.firstOrNull { it.extension.equals("md", ignoreCase = true) }
                    if (firstMd != null) {
                        try {
                            val content = firstMd.readText()
                            results.add("  ✅ 成功读取文件: ${firstMd.name} (${content.length} 字节)")
                        } catch (e: Exception) {
                            results.add("  ❌ 读取文件失败: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                results.add("  ❌ 异常: ${e.message}")
            }
            results.add("")
        }

        // 测试应用私有目录
        val privateDir = Environment.getDataDirectory()
        results.add("应用私有目录: ${privateDir.absolutePath}")

        return results.joinToString("\n")
    }

    /**
     * 获取最佳 Document 目录路径
     */
    fun getBestDocumentPath(): File? {
        val paths = listOf(
            "/sdcard/Document",
            "/mnt/sdcard/Document",
            "/storage/emulated/0/Document"
        )

        for (path in paths) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory && dir.canRead()) {
                return dir
            }
        }
        return null
    }
}
