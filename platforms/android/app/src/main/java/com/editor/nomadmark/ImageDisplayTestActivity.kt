package com.editor.nomadmark

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.noties.markwon.Markwon
import io.noties.markwon.image.ImagesPlugin

/**
 * 图片显示测试 Activity
 *
 * 用于测试 Markwon 的图片显示功能
 */
class ImageDisplayTestActivity : android.app.Activity() {

    private lateinit var tvMarkdown: TextView
    private lateinit var btnTestLocalImage: Button
    private lateinit var btnTestNetworkImage: Button

    private lateinit var markwon: Markwon

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 Markwon
        markwon = Markwon.builder(this)
            .usePlugin(io.noties.markwon.core.CorePlugin.create())
            .usePlugin(ImagesPlugin.create())
            .build()

        // 创建 UI
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        btnTestNetworkImage = Button(this).apply {
            text = "测试网络图片"
            setOnClickListener { testNetworkImage() }
        }

        btnTestLocalImage = Button(this).apply {
            text = "测试本地图片"
            setOnClickListener { testLocalImage() }
        }

        tvMarkdown = TextView(this).apply {
            setTextIsSelectable(true)
            textSize = 14f
        }

        layout.addView(btnTestNetworkImage)
        layout.addView(btnTestLocalImage)
        layout.addView(tvMarkdown)

        setContentView(layout)
    }

    private fun testNetworkImage() {
        val markdown = """
# 测试网络图片

![测试图片](https://via.placeholder.com/300)

如果看到上面的图片，说明 Markwon 的网络图片功能正常。
        """.trimIndent()

        markwon.setMarkdown(tvMarkdown, markdown)
        Toast.makeText(this, "已加载网络图片测试", Toast.LENGTH_SHORT).show()
        Log.d("ImageDisplayTest", "测试网络图片")
    }

    private fun testLocalImage() {
        // 检查图片目录
        val imageDir = java.io.File(filesDir, "markdown_images")
        val debugInfo = buildString {
            append("图片目录: ${imageDir.absolutePath}\n")
            append("目录存在: ${imageDir.exists()}\n")

            if (imageDir.exists()) {
                val files = imageDir.listFiles()
                if (files != null && files.isNotEmpty()) {
                    append("文件数量: ${files.size}\n\n")
                    files.forEach { file ->
                        append("- ${file.name}\n")
                    }
                } else {
                    append("目录为空\n")
                }
            }
        }

        val markdown = """
# 测试本地图片

$debugInfo

![本地图片](file:///data/data/com.editor.nomadmark/files/markdown_images/test.jpg)

如果上面的图片无法显示，可能是因为：
1. 图片文件不存在
2. Markwon 不支持 file:// 协议
3. 需要额外的配置
        """.trimIndent()

        markwon.setMarkdown(tvMarkdown, markdown)
        Log.d("ImageDisplayTest", "测试本地图片")
    }
}
