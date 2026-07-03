package com.editor.nomadmark

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Utility class to test if MainActivity can be launched from file manager
 */
object ActivityLaunchTest {

    private const val TAG = "ActivityLaunchTest"

    /**
     * Test if the app can properly handle file open intents
     *
     * @param context Application context
     * @param testFile Optional test file path (will create temp .md file if null)
     * @return Test result with details
     */
    fun testFileOpenLaunch(context: Context, testFile: File? = null): LaunchTestResult {
        val results = mutableMapOf<String, Boolean>()
        val details = mutableListOf<String>()

        // 1. Test basic component existence
        val componentExists = try {
            context.packageManager.getActivityInfo(
                android.content.ComponentName(context, MainActivity::class.java),
                0
            )
            details.add("✓ MainActivity component found in manifest")
            results["component_exists"] = true
            true
        } catch (e: Exception) {
            details.add("✗ MainActivity component NOT found: ${e.message}")
            results["component_exists"] = false
            false
        }

        if (!componentExists) {
            return LaunchTestResult(success = false, results, details)
        }

        // 2. Test exported flag
        val isExported = try {
            val info = context.packageManager.getActivityInfo(
                android.content.ComponentName(context, MainActivity::class.java),
                0
            )
            if (info.exported) {
                details.add("✓ Activity is exported (visible to other apps)")
                results["exported"] = true
                true
            } else {
                details.add("✗ Activity is NOT exported (file manager can't launch it)")
                results["exported"] = false
                false
            }
        } catch (e: Exception) {
            details.add("✗ Error checking exported status: ${e.message}")
            results["exported"] = false
            false
        }

        // 3. Test intent filters for markdown files
        val testFilePath = testFile ?: createTempMarkdownFile(context)
        val uri = Uri.fromFile(testFilePath)

        val intentFiltersSupported = testIntentFilters(context, uri, details, results)

        // 4. Test actual launch capability
        val canLaunch = if (testFilePath.exists()) {
            testActualLaunch(context, testFilePath, details, results)
        } else {
            details.add("! Test file doesn't exist, skipping launch test")
            results["launch_test"] = false
            false
        }

        // Overall success if all critical tests pass
        val success = results["component_exists"] == true &&
                     results["exported"] == true &&
                     (results["intent_filter_md"] == true || results["intent_filter_mime"] == true)

        details.add("\n${if (success) "✓ PASSED" else "✗ FAILED"}: Activity ${if (success) "CAN" else "CANNOT"} be launched from file manager")

        return LaunchTestResult(success, results, details)
    }

    /**
     * Test if intent filters are properly configured
     */
    private fun testIntentFilters(
        context: Context,
        uri: Uri,
        details: MutableList<String>,
        results: MutableMap<String, Boolean>
    ): Boolean {
        // Test VIEW action with file scheme
        val fileIntent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            type = "text/markdown"
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        val fileHandled = context.packageManager.queryIntentActivities(fileIntent, 0)
            .any { it.activityInfo.packageName == context.packageName }

        if (fileHandled) {
            details.add("✓ App responds to .md file:// intents")
            results["intent_filter_md"] = true
        } else {
            details.add("✗ App does NOT respond to .md file:// intents")
            results["intent_filter_md"] = false
        }

        // Test with MIME type
        val mimeIntent = Intent(Intent.ACTION_VIEW).apply {
            type = "text/markdown"
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        val mimeHandled = context.packageManager.queryIntentActivities(mimeIntent, 0)
            .any { it.activityInfo.packageName == context.packageName }

        if (mimeHandled) {
            details.add("✓ App responds to text/markdown MIME type")
            results["intent_filter_mime"] = true
        } else {
            details.add("! App does NOT respond to text/markdown MIME type")
            results["intent_filter_mime"] = false
        }

        return fileHandled || mimeHandled
    }

    /**
     * Test if activity can actually be launched
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
            // Check if intent can be resolved
            if (intent.resolveActivity(context.packageManager) != null) {
                details.add("✓ Intent resolves to MainActivity")
                results["launch_test"] = true
                true
            } else {
                details.add("✗ Intent does NOT resolve to MainActivity")
                results["launch_test"] = false
                false
            }
        } catch (e: Exception) {
            details.add("✗ Launch test failed: ${e.message}")
            results["launch_test"] = false
            false
        }
    }

    /**
     * Create a temporary markdown file for testing
     */
    private fun createTempMarkdownFile(context: Context): File {
        val tempDir = context.cacheDir
        val tempFile = File(tempDir, "test_${System.currentTimeMillis()}.md")
        tempFile.writeText("# Test Markdown\n\nThis is a test file.")
        return tempFile
    }

    /**
     * Run quick diagnostic from MainActivity
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

            // List all intent filters
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
 * Result of launch test
 */
data class LaunchTestResult(
    val success: Boolean,
    val results: Map<String, Boolean>,
    val details: List<String>
)
