package com.editor.nomadmark

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * 测试 MainActivity 是否可以从文件管理器启动的工具类
 */
object ActivityLaunchTest {

    private const val TAG = "ActivityLaunchTest"

    /**
     * 测试应用是否可以正确处理文件打开 Intent
     *
     * @param context 应用上下文
     * @param testFile 可选的测试文件路径（如果为 null 则创建临时 .md 文件）
     * @return 包含详细信息的测试结果
     */
    fun testFileOpenLaunch(context: Context, testFile: File? = null): LaunchTestResult {
        val results = mutableMapOf<String, Boolean>()
        val details = mutableListOf<String>()

        // 1. 测试基本组件是否存在
        val componentExists = try {
            context.packageManager.getActivityInfo(
                android.content.ComponentName(context, MainActivity::class.java),
                0
            )
            details.add("✓ MainActivity 组件在 manifest 中找到")
            results["component_exists"] = true
            true
        } catch (e: Exception) {
            details.add("✗ MainActivity 组件未找到: ${e.message}")
            results["component_exists"] = false
            false
        }

        if (!componentExists) {
            return LaunchTestResult(success = false, results, details)
        }

        // 2. 测试 exported 标志
        @Suppress("UNUSED_VARIABLE")
        val isExported = try {
            val info = context.packageManager.getActivityInfo(
                android.content.ComponentName(context, MainActivity::class.java),
                0
            )
            if (info.exported) {
                details.add("✓ Activity 已导出（对其他应用可见）")
                results["exported"] = true
                true
            } else {
                details.add("✗ Activity 未导出（文件管理器无法启动）")
                results["exported"] = false
                false
            }
        } catch (e: Exception) {
            details.add("✗ 检查导出状态时出错: ${e.message}")
            results["exported"] = false
            false
        }

        // 3. 测试 markdown 文件的 Intent 过滤器
        val testFilePath = testFile ?: createTempMarkdownFile(context)
        val uri = Uri.fromFile(testFilePath)

        @Suppress("UNUSED_VARIABLE")
        val intentFiltersSupported = testIntentFilters(context, uri, details, results)

        // 4. 测试实际启动能力
        @Suppress("UNUSED_VARIABLE")
        val canLaunch = if (testFilePath.exists()) {
            testActualLaunch(context, testFilePath, details, results)
        } else {
            details.add("! 测试文件不存在，跳过启动测试")
            results["launch_test"] = false
            false
        }

        // 所有关键测试通过则整体成功
        val success = results["component_exists"] == true &&
                     results["exported"] == true &&
                     (results["intent_filter_md"] == true || results["intent_filter_mime"] == true)

        details.add("\n${if (success) "✓ PASSED" else "✗ FAILED"}: Activity ${if (success) "CAN" else "CANNOT"} be launched from file manager")

        return LaunchTestResult(success, results, details)
    }

    /**
     * 测试 Intent 过滤器是否正确配置
     */
    private fun testIntentFilters(
        context: Context,
        uri: Uri,
        details: MutableList<String>,
        results: MutableMap<String, Boolean>
    ): Boolean {
        // 测试使用 file:// 协议的 VIEW 动作
        val fileIntent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            type = "text/markdown"
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        val fileHandled = context.packageManager.queryIntentActivities(fileIntent, 0)
            .any { it.activityInfo.packageName == context.packageName }

        if (fileHandled) {
            details.add("✓ 应用响应 .md file:// intents")
            results["intent_filter_md"] = true
        } else {
            details.add("✗ 应用不响应 .md file:// intents")
            results["intent_filter_md"] = false
        }

        // 测试 MIME 类型
        val mimeIntent = Intent(Intent.ACTION_VIEW).apply {
            type = "text/markdown"
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        val mimeHandled = context.packageManager.queryIntentActivities(mimeIntent, 0)
            .any { it.activityInfo.packageName == context.packageName }

        if (mimeHandled) {
            details.add("✓ 应用响应 text/markdown MIME 类型")
            results["intent_filter_mime"] = true
        } else {
            details.add("! 应用不响应 text/markdown MIME 类型")
            results["intent_filter_mime"] = false
        }

        return fileHandled || mimeHandled
    }

    /**
     * 测试 Activity 是否可以实际启动
     */
    private fun testActualLaunch(
        context: Context,
        file: File,
        details: MutableList<String>,
        results: MutableMap<String, Boolean>
    ): Boolean {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.fromFile(file)
            putExtra("file_path", file.absolutePath)
        }

        return try {
            // 检查 Intent 是否可以解析
            if (intent.resolveActivity(context.packageManager) != null) {
                details.add("✓ Intent 解析到 MainActivity")
                results["launch_test"] = true
                true
            } else {
                details.add("✗ Intent 未解析到 MainActivity")
                results["launch_test"] = false
                false
            }
        } catch (e: Exception) {
            details.add("✗ 启动测试失败: ${e.message}")
            results["launch_test"] = false
            false
        }
    }

    /**
     * 创建用于测试的临时 markdown 文件
     */
    private fun createTempMarkdownFile(context: Context): File {
        val tempDir = context.cacheDir
        val tempFile = File(tempDir, "test_${System.currentTimeMillis()}.md")
        tempFile.writeText("# Test Markdown\n\nThis is a test file.")
        return tempFile
    }

    /**
     * 从 MainActivity 运行快速诊断
     */
    fun logDiagnosticInfo(context: Context) {
        Log.d(TAG, "=== Activity Launch Diagnostic ===")

        val pm = context.packageManager

        try {
            val info = pm.getActivityInfo(
                android.content.ComponentName(context, MainActivity::class.java),
                0
            )
            Log.d(TAG, "Exported: ${info.exported}")
            Log.d(TAG, "Permission: ${info.permission}")
            Log.d(TAG, "Enabled: ${info.enabled}")

            // 列出所有 Intent 过滤器
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "text/markdown"
                addCategory(Intent.CATEGORY_DEFAULT)
            }

            val matches = pm.queryIntentActivities(intent, 0)
            Log.d(TAG, "Apps handling .md: ${matches.size}")
            matches.forEach {
                Log.d(TAG, "  - ${it.activityInfo.packageName}/${it.activityInfo.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Diagnostic failed", e)
        }

        Log.d(TAG, "=== End Diagnostic ===")
    }
}

/**
 * 启动测试结果
 */
data class LaunchTestResult(
    val success: Boolean,
    val results: Map<String, Boolean>,
    val details: List<String>
)
