package com.editor.nomadmark

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.content.SharedPreferences

/**
 * Main Activity for NomadMark
 *
 * 启动 MarkdownEditorActivity 进行编辑
 */
class MainActivity : Activity(), ComponentCallbacks2 {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("NomadMarkPrefs", MODE_PRIVATE)

        // 直接启动编辑器 Activity 并传递所有数据
        launchEditorActivity(intent)

        // 注册内存回调
        application.registerComponentCallbacks(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { launchEditorActivity(it) }
    }

    /**
     * 启动编辑器 Activity
     */
    private fun launchEditorActivity(intent: Intent) {
        Log.d("MainActivity", "launchEditorActivity called")

        // 创建新的 Intent 启动 MarkdownEditorActivity
        val editorIntent = Intent(this, MarkdownEditorActivity::class.java)

        // 传递所有 extras
        intent.extras?.let {
            editorIntent.putExtras(it)
        }

        // 传递 data (URI)
        intent.data?.let {
            editorIntent.data = it
        }

        // 传递 action
        editorIntent.action = intent.action

        // 检查是否是首次启动（没有传入文件路径）
        val hasFilePath = intent.extras?.containsKey("file_path") ?: false
        val hasDataUri = intent.data != null

        if (!hasFilePath && !hasDataUri) {
            // 检查是否已展示过示例
            val hasShownSample = prefs.getBoolean("has_shown_sample", false)

            if (!hasShownSample) {
                // 首次启动，打开示例文件
                editorIntent.putExtra("open_sample", true)

                // 标记已展示过示例
                prefs.edit().putBoolean("has_shown_sample", true).apply()
            }
        }

        // 启动编辑器
        startActivity(editorIntent)

        // 完成当前 Activity
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        application.unregisterComponentCallbacks(this)
    }

    /**
     * Memory trim callback from Android system
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 内存管理由 MarkdownEditorActivity 处理
    }

    /**
     * Low memory callback
     */
    override fun onLowMemory() {
        super.onLowMemory()
        // 内存管理由 MarkdownEditorActivity 处理
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}
