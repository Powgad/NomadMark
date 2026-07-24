# Android 乐谱渲染功能 - 代码参考文档

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档版本 | v1.0 |
| 创建日期 | 2026-07-24 |
| 作者 | NomadMark Team |
| 状态 | 维护中 |

---

## 目录

1. [架构概述](#架构概述)
2. [数据模型层](#数据模型层)
3. [检测器层](#检测器层)
4. [渲染器层](#渲染器层)
5. [缓存层](#缓存层)
6. [显示层](#显示层)
7. [主编辑器集成](#主编辑器集成)
8. [常量定义](#常量定义)

---

## 架构概述

```
┌─────────────────────────────────────────────────────────────┐
│                    MarkdownEditorActivity                    │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    updatePreview()                      │  │
│  │  ┌─────────────────────────────────────────────────┐  │  │
│  │  │           applyMusicSheetRendering()            │  │  │
│  │  │  ┌───────────────────────────────────────────┐   │  │  │
│  │  │  │      MusicSheetDetector.detectMusic()    │   │  │  │
│  │  │  │  检测 ```music 和 ```简谱 代码块          │   │  │  │
│  │  │  └───────────────────────────────────────────┘   │  │  │
│  │  │              ↓                                  │   │  │  │
│  │  │  ┌───────────────────────────────────────────┐   │  │  │
│  │  │  │        WebViewMusicRenderer                │   │  │  │
│  │  │  │  ┌─────────────────────────────────┐     │   │  │  │
│  │  │  │  │   WebView + abcjs              │     │   │  │  │
│  │  │  │  │   - 渲染为 Bitmap               │     │   │  │  │
│  │  │  │  │   - 缓存机制                     │     │   │  │  │
│  │  │  │  └─────────────────────────────────┘     │   │  │  │
│  │  │  └───────────────────────────────────────────┘   │  │  │
│  │  │              ↓                                  │   │  │  │
│  │  │  ┌───────────────────────────────────────────┐   │  │  │
│  │  │  │      MusicSheetSpan (ReplacementSpan)     │   │  │  │
│  │  │  │  - 显示 Bitmap                             │   │  │  │
│  │  │  └───────────────────────────────────────────┘   │  │  │
│  │  └─────────────────────────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 数据模型层

### 功能说明

定义乐谱数据的结构和类型，包含：
- 乐谱基本信息（类型、内容、标题、作曲者等）
- 乐谱类型枚举（ABC 记谱法、简谱）
- 缓存键生成

### 文件位置

`platforms/android/app/src/main/java/com/editor/nomadmark/music/MusicData.kt`

### 完整代码

```kotlin
package com.editor.nomadmark.music

import java.io.Serializable

/**
 * 乐谱数据模型
 *
 * @property id 唯一标识符
 * @property type 乐谱类型（ABC 或简谱）
 * @property content 乐谱内容（ABC 代码或简谱代码）
 * @property title 标题（从 ABC 元数据解析）
 * @property composer 作曲者（从 ABC 元数据解析）
 * @property tempo 速度（从 ABC 元数据解析，默认 120）
 * @property key 调性（从 ABC 元数据解析）
 * @property sourceRange 在原文中的位置范围
 */
data class MusicData(
    val id: String = generateId(),
    val type: MusicType,
    val content: String,
    val title: String? = null,
    val composer: String? = null,
    val tempo: Int = 120,
    val key: String? = null,
    val sourceRange: IntRange = 0..0
) : Serializable {

    companion object {
        /**
         * 生成唯一 ID
         */
        fun generateId(): String = "music_${System.currentTimeMillis()}"
    }

    /**
     * 获取缓存键
     * 使用类型和内容哈希值确保相同乐谱使用同一缓存
     */
    fun getCacheKey(): String {
        return "${type.name}_${content.hashCode()}"
    }
}

/**
 * 乐谱类型枚举
 */
enum class MusicType {
    /** ABC 记谱法（```music 或 ```abc 代码块） */
    ABC,

    /** 简谱（```简谱 或 ```jianpu 代码块） */
    JIANPU,

    /** 未知类型 */
    UNKNOWN;

    companion object {
        /**
         * 从语言标识转换为 MusicType
         */
        fun fromLanguage(lang: String?): MusicType {
            return when (lang?.lowercase()) {
                "music", "abc" -> ABC
                "简谱", "jianpu", "numbered" -> JIANPU
                else -> UNKNOWN
            }
        }
    }
}
```

### 使用示例

```kotlin
// 创建 ABC 乐谱数据
val abcMusic = MusicData(
    type = MusicType.ABC,
    content = """
        X: 1
        T: Cooley's
        M: 4/4
        L: 1/8
        K: Emin
        |:D2|"Em"EBBA B2 EB|B2 AB dBAG|"D"FDAD BDAD|FDAD dAFD:|
    """.trimIndent()
)

// 获取缓存键
val cacheKey = abcMusic.getCacheKey()  // "ABC_123456789"

// 创建简谱数据
val jianpuMusic = MusicData(
    type = MusicType.JIANPU,
    content = "1=C 4/4\n5 5 6 6 | 5 4 3 2"
)
```

---

## 检测器层

### 功能说明

从 Markdown 文本中检测乐谱代码块：
- 支持 `` ```music `` / `` ```abc `` 代码块（ABC 记谱法）
- 支持 `` ```简谱 `` / `` ```jianpu `` 代码块（简谱）
- 解析 ABC 元数据（标题、作曲者、调性、速度）
- 返回乐谱块列表

### 文件位置

`platforms/android/app/src/main/java/com/editor/nomadmark/music/MusicSheetDetector.kt`

### 完整代码

```kotlin
package com.editor.nomadmark.music

import android.text.Spanned
import android.util.Log

/**
 * 乐谱块检测器
 *
 * 检测 Markdown 中的 ```music 和 ```简谱 代码块
 */
object MusicSheetDetector {

    private const val TAG = "MusicSheetDetector"

    /**
     * 乐谱块信息
     *
     * @property musicData 乐谱数据
     * @property blockStart 代码块在文本中的起始位置
     * @property blockEnd 代码块在文本中的结束位置
     */
    data class MusicBlock(
        val musicData: MusicData,
        val blockStart: Int,
        val blockEnd: Int
    )

    /**
     * 从原始 Markdown 文本中检测乐谱块
     *
     * @param markdown Markdown 文本内容
     * @return 检测到的乐谱块列表
     */
    fun detectMusicSheetsFromMarkdown(markdown: String): List<MusicBlock> {
        val musicSheets = mutableListOf<MusicBlock>()

        // 检测 ```music 或 ```abc 或 ```简谱 代码块
        val fencedCodePattern = """```(music|abc|简谱|jianpu)\s*\n([\s\S]*?)```""".toRegex(RegexOption.IGNORE_CASE)

        fencedCodePattern.findAll(markdown).forEach { match ->
            val language = match.groupValues[1].lowercase()
            val content = match.groupValues[2].trim()

            // 跳过空内容
            if (content.isEmpty()) return@forEach

            val type = when (language) {
                "music", "abc" -> MusicType.ABC
                "简谱", "jianpu" -> MusicType.JIANPU
                else -> MusicType.UNKNOWN
            }

            if (type == MusicType.UNKNOWN) return@forEach

            // 解析 ABC 元数据
            val metadata = parseAbcMetadata(content)

            val musicData = MusicData(
                type = type,
                content = content,
                title = metadata.title,
                composer = metadata.composer,
                tempo = metadata.tempo,
                key = metadata.key,
                sourceRange = match.range.first..match.range.last
            )

            musicSheets.add(MusicBlock(
                musicData = musicData,
                blockStart = match.range.first,
                blockEnd = match.range.last + 1
            ))

            Log.d(TAG, "检测到乐谱块: [${match.range.first}-${match.range.last}], type=$type, title=${metadata.title}")
        }

        Log.d(TAG, "共检测到 ${musicSheets.size} 个乐谱块")
        return musicSheets
    }

    /**
     * 检测文本中的所有乐谱块
     *
     * @param spanned Spanned 文本
     * @return 检测到的乐谱块列表
     */
    fun detectMusicSheets(spanned: Spanned): List<MusicBlock> {
        val musicSheets = mutableListOf<MusicBlock>()
        val text = spanned.toString()

        // 检测 ```music 或 ```abc 或 ```简谱 代码块
        val fencedCodePattern = """```(music|abc|简谱|jianpu)\s*\n([\s\S]*?)```""".toRegex(RegexOption.IGNORE_CASE)

        fencedCodePattern.findAll(text).forEach { match ->
            val language = match.groupValues[1].lowercase()
            val content = match.groupValues[2].trim()

            // 跳过空内容
            if (content.isEmpty()) return@forEach

            val type = when (language) {
                "music", "abc" -> MusicType.ABC
                "简谱", "jianpu" -> MusicType.JIANPU
                else -> MusicType.UNKNOWN
            }

            if (type == MusicType.UNKNOWN) return@forEach

            // 解析 ABC 元数据
            val metadata = parseAbcMetadata(content)

            val musicData = MusicData(
                type = type,
                content = content,
                title = metadata.title,
                composer = metadata.composer,
                tempo = metadata.tempo,
                key = metadata.key,
                sourceRange = match.range.first..match.range.last
            )

            musicSheets.add(MusicBlock(
                musicData = musicData,
                blockStart = match.range.first,
                blockEnd = match.range.last + 1
            ))

            Log.d(TAG, "检测到乐谱块: [${match.range.first}-${match.range.last}], type=$type, title=${metadata.title}")
        }

        Log.d(TAG, "共检测到 ${musicSheets.size} 个乐谱块")
        return musicSheets
    }

    /**
     * 解析 ABC 元数据
     *
     * 支持的字段：
     * - T: 标题（Title）
     * - C: 作曲者（Composer）
     * - Q: 速度（Tempo/Q:）
     * - K: 调性（Key）
     */
    private data class AbcMetadata(
        val title: String? = null,
        val composer: String? = null,
        val tempo: Int = 120,
        val key: String? = null
    )

    private fun parseAbcMetadata(content: String): AbcMetadata {
        var title: String? = null
        var composer: String? = null
        var tempo = 120
        var key: String? = null

        content.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("T:", ignoreCase = true) -> title = trimmed.substring(2).trim()
                trimmed.startsWith("C:", ignoreCase = true) -> composer = trimmed.substring(2).trim()
                trimmed.startsWith("Q:", ignoreCase = true) -> {
                    val tempoStr = trimmed.substring(2).trim().split(" ").first()
                    tempo = tempoStr.toIntOrNull() ?: 120
                }
                trimmed.startsWith("K:", ignoreCase = true) -> key = trimmed.substring(2).trim()
            }
        }

        return AbcMetadata(title, composer, tempo, key)
    }
}
```

### 使用示例

```kotlin
// 从 Markdown 字符串检测
val markdown = """
    ```music
    X: 1
    T: Test Song
    K: C
    C D E F | G A B c
    ```
    """.trimIndent()

val blocks = MusicSheetDetector.detectMusicSheetsFromMarkdown(markdown)
println("检测到 ${blocks.size} 个乐谱块")
println("标题: ${blocks[0].musicData.title}")
println("调性: ${blocks[0].musicData.key}")
```

---

## 渲染器层

### 功能说明

使用 WebView + abcjs 库将 ABC 记谱法渲染为 Bitmap：
- 使用 abcjs 在 WebView 中渲染 ABC 代码为 SVG
- 将 SVG 转换为 Android Bitmap
- 支持缓存机制，避免重复渲染
- 包含降级方案（SVG 渲染失败时的备用方法）

### 文件位置

`platforms/android/app/src/main/java/com/editor/nomadmark/music/WebViewMusicRenderer.kt`

### 完整代码

```kotlin
package com.editor.nomadmark.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.caverock.androidsvg.SVG

/**
 * 基于 WebView 的乐谱渲染器
 *
 * 使用 abcjs 库在 WebView 中渲染 ABC 记谱法，然后转换为 Bitmap
 */
class WebViewMusicRenderer(private val context: Context) {

    companion object {
        private const val TAG = "WebViewMusicRenderer"
    }

    private val handler = Handler(Looper.getMainLooper())

    /**
     * 渲染 ABC 乐谱为 Bitmap
     *
     * @param musicData 乐谱数据
     * @param width 渲染宽度
     * @param callback 渲染完成回调，返回 Bitmap 或 null
     */
    fun renderToBitmap(
        musicData: MusicData,
        width: Int,
        callback: (Bitmap?) -> Unit
    ) {
        // 检查缓存
        val cached = MusicSheetCache.get(musicData.getCacheKey())
        if (cached != null) {
            Log.d(TAG, "使用缓存的乐谱图片: ${musicData.title ?: musicData.id}")
            callback(cached)
            return
        }

        // 每次创建新的 WebView，避免并发冲突
        val webView = WebView(context)

        try {
            configureWebView(webView, width)

            // 生成 HTML
            val html = generateAbcHtml(musicData, width)

            // 设置 WebViewClient 来监听页面加载完成
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    Log.d(TAG, "页面加载完成: ${musicData.title}, url=$url")
                    // 页面加载完成后，等待 JavaScript 执行完成
                    handler.postDelayed({
                        Log.d(TAG, "延迟后开始捕获 Bitmap")
                        captureBitmap(webView, musicData, width, callback) {
                            // 渲染完成后销毁 WebView
                            webView.destroy()
                        }
                    }, 1200)
                }
            }

            // 渲染 - 使用 file:///android_asset/ 作为 baseUrl 以支持加载 assets 文件
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null
            )

            // 超时保护
            handler.postDelayed({
                Log.w(TAG, "渲染超时，使用备用方法")
                captureBitmap(webView, musicData, width, callback) {
                    webView.destroy()
                }
            }, 10000)

        } catch (e: Exception) {
            Log.e(TAG, "渲染失败", e)
            callback(null)
            webView.destroy()
        }
    }

    /**
     * 配置 WebView
     */
    private fun configureWebView(webView: WebView, width: Int) {
        // 创建父容器来帮助 WebView 正确测量
        val parent = android.widget.FrameLayout(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        parent.addView(webView)

        webView.apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )

            settings.apply {
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }

            // 禁用滚动
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            // 白背景
            setBackgroundColor(Color.WHITE)

            // 启用硬件加速
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
    }

    /**
     * 生成 abcjs HTML（使用 SVG 输出）
     */
    private fun generateAbcHtml(musicData: MusicData, width: Int): String {
        // 转义内容中的特殊字符
        val escapedContent = musicData.content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("'", "\\'")

        val staffWidth = width - 80 // 左右留白

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    background: white;
                    padding: 10px;
                    overflow-x: hidden;
                }
                #paper {
                    min-width: ${width}px;
                }
                .abcjs-play { display: none !important; }
            </style>
            <script src="file:///android_asset/abcjs/abcjs-basic-min.js"></script>
        </head>
        <body>
            <div id="paper"></div>
            <script>
                if (typeof ABCJS === 'undefined') {
                    console.error('ABCJS not loaded');
                    document.body.innerHTML = '<div style="color:red;padding:20px;">ABCJS library failed to load</div>';
                } else {
                    try {
                        const abcCode = "$escapedContent";
                        ABCJS.renderAbc("paper", abcCode, {
                            responsive: 'resize',
                            scale: 1.2,
                            staffwidth: $staffWidth,
                            paddingtop: 20,
                            paddingbottom: 20,
                            paddingright: 20,
                            paddingleft: 20
                        });

                        // 获取 SVG 内容并通知原生
                        setTimeout(function() {
                            var svg = document.querySelector('#paper svg');
                            if (svg) {
                                var svgString = new XMLSerializer().serializeToString(svg);
                                window.ABCJS_SVG_RESULT = svgString;
                                console.log('SVG extracted, length:', svgString.length);
                            } else {
                                console.error('No SVG found');
                                window.SVG_ERROR = 'No SVG element found';
                            }
                        }, 300);

                        window.renderComplete = true;
                    } catch(e) {
                        console.error('ABC rendering error:', e);
                        document.body.innerHTML = '<div style="color:red;padding:20px;">Error: ' + e.message + '</div>';
                        window.renderError = e.message;
                    }
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * 截取 WebView 为 Bitmap（使用 Picture 方法）
     */
    private fun captureBitmap(
        webView: WebView,
        musicData: MusicData,
        width: Int,
        callback: (Bitmap?) -> Unit,
        onDestroy: () -> Unit = {}
    ) {
        try {
            Log.d(TAG, "开始截取 Bitmap, title=${musicData.title}")

            // 第一步：检查 ABCJS 是否加载
            webView.evaluateJavascript(
                "(function() { return typeof ABCJS !== 'undefined' ? 'loaded' : 'not_loaded'; })();"
            ) { result ->
                val abcjsLoaded = (result == "\"loaded\"")
                Log.d(TAG, "ABCJS 状态: $result, loaded=$abcjsLoaded")

                if (!abcjsLoaded) {
                    Log.e(TAG, "ABCJS 库未加载！使用备用方法")
                    captureBitmapFallback(webView, musicData, width, callback) {
                        onDestroy()
                    }
                    return@evaluateJavascript
                }

                // 第二步：检查 SVG 是否存在
                webView.evaluateJavascript(
                    "(function() { return document.querySelector('#paper svg') !== null ? 'has_svg' : 'no_svg'; })();"
                ) { result ->
                    val hasSvg = (result == "\"has_svg\"")
                    Log.d(TAG, "SVG 状态: $result, hasSvg=$hasSvg")

                    if (!hasSvg) {
                        Log.w(TAG, "未找到 SVG，使用备用方法")
                        captureBitmapFallback(webView, musicData, width, callback) {
                            onDestroy()
                        }
                        return@evaluateJavascript
                    }

                    // 第三步：获取 SVG 字符串
                    webView.evaluateJavascript(
                        "(function() { return window.ABCJS_SVG_RESULT || ''; })();"
                    ) { svgResult ->
                        try {
                            // evaluateJavascript 返回的是 JSON 编码的字符串
                            val svgString = if (svgResult.length > 2 && svgResult.startsWith("\"") && svgResult.endsWith("\"")) {
                                val inner = svgResult.substring(1, svgResult.length - 1)
                                inner.replace("\\\"", "\"")
                                    .replace("\\\\", "\\")
                                    .replace("\\n", "\n")
                                    .replace("\\r", "\r")
                                    .replace("\\t", "\t")
                            } else {
                                svgResult
                            }

                            Log.d(TAG, "获取到 SVG，长度: ${svgString.length}")

                            if (svgString.isEmpty()) {
                                Log.w(TAG, "SVG 字符串为空，使用备用方法")
                                captureBitmapFallback(webView, musicData, width, callback) {
                                    onDestroy()
                                }
                                return@evaluateJavascript
                            }

                            // 第四步：创建 Picture 并绘制 SVG
                            val picture = createPictureFromSvg(svgString, width)
                            if (picture != null) {
                                val bitmap = Bitmap.createBitmap(
                                    picture.getWidth(),
                                    picture.getHeight(),
                                    Bitmap.Config.ARGB_8888
                                )
                                val canvas = Canvas(bitmap)
                                canvas.drawColor(Color.WHITE)
                                picture.draw(canvas)

                                // 检查内容
                                var hasContent = false
                                val pixels = IntArray(Math.min(bitmap.width * bitmap.height, 10000))
                                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, Math.min(bitmap.height, 10000 / bitmap.width))
                                for (pixel in pixels) {
                                    if (pixel != Color.WHITE) {
                                        hasContent = true
                                        break
                                    }
                                }

                                if (hasContent) {
                                    MusicSheetCache.put(musicData.getCacheKey(), bitmap)
                                    Log.d(TAG, "从 SVG 渲染完成: ${bitmap.width}x${bitmap.height}")
                                    callback(bitmap)
                                    onDestroy()
                                } else {
                                    Log.w(TAG, "SVG 渲染为空，使用备用方法")
                                    captureBitmapFallback(webView, musicData, width, callback) {
                                        onDestroy()
                                    }
                                }
                            } else {
                                Log.w(TAG, "Picture 创建失败，使用备用方法")
                                captureBitmapFallback(webView, musicData, width, callback) {
                                    onDestroy()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "SVG 解析失败，使用备用方法", e)
                            captureBitmapFallback(webView, musicData, width, callback) {
                                onDestroy()
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "截取 Bitmap 失败", e)
            callback(null)
            onDestroy()
        }
    }

    /**
     * 从 SVG 字符串创建 Picture（使用 AndroidSVG）
     */
    private fun createPictureFromSvg(svgString: String, width: Int): Picture? {
        return try {
            val svg = SVG.getFromString(svgString)

            val documentWidth = svg.documentWidth
            val documentHeight = svg.documentHeight

            if (documentWidth <= 0 || documentHeight <= 0) {
                svg.setDocumentWidth(width.toFloat())
                svg.setDocumentHeight(400f)
            }

            val picture = Picture()
            val canvas = picture.beginRecording(
                svg.documentWidth.toInt().coerceAtLeast(width),
                svg.documentHeight.toInt().coerceAtLeast(100)
            )
            canvas.drawColor(Color.WHITE)

            svg.renderToCanvas(canvas)
            picture.endRecording()

            Log.d(TAG, "SVG 渲染成功: ${svg.documentWidth}x${svg.documentHeight}")
            picture
        } catch (e: Exception) {
            Log.e(TAG, "SVG 解析失败", e)
            null
        }
    }

    /**
     * 备用的 Bitmap 截取方法
     * 直接绘制 WebView 到 Canvas
     */
    private fun captureBitmapFallback(
        webView: WebView,
        musicData: MusicData,
        width: Int,
        callback: (Bitmap?) -> Unit,
        onComplete: () -> Unit = {}
    ) {
        try {
            webView.forceLayout()

            val specWidth = View.MeasureSpec.makeMeasureSpec(
                width,
                View.MeasureSpec.EXACTLY
            )
            val specHeight = View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            )

            webView.measure(specWidth, specHeight)

            var measuredHeight = webView.measuredHeight
            Log.d(TAG, "WebView 测量尺寸: ${webView.measuredWidth}x$measuredHeight")

            if (measuredHeight < 100) {
                measuredHeight = 400
                Log.w(TAG, "WebView 高度太小，使用默认高度: $measuredHeight")
            }

            webView.layout(0, 0, webView.measuredWidth, measuredHeight)

            val bitmap = Bitmap.createBitmap(
                webView.measuredWidth,
                measuredHeight,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            webView.draw(canvas)
            webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            // 检查 Bitmap 是否为空
            var hasContent = false
            var nonWhiteCount = 0
            val pixels = IntArray(Math.min(bitmap.width * bitmap.height, 10000))
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, Math.min(bitmap.height, 10000 / bitmap.width))
            for (i in pixels.indices) {
                if (pixels[i] != Color.WHITE) {
                    hasContent = true
                    nonWhiteCount++
                    break
                }
            }

            if (!hasContent) {
                Log.w(TAG, "Bitmap 渲染为空（全白）")
            } else {
                Log.d(TAG, "Bitmap 有内容，非白色像素数: $nonWhiteCount")
            }

            MusicSheetCache.put(musicData.getCacheKey(), bitmap)

            Log.d(TAG, "渲染完成: ${bitmap.width}x${bitmap.height}, hasContent=$hasContent")
            callback(bitmap)

        } catch (e: Exception) {
            Log.e(TAG, "截取 Bitmap 失败", e)
            callback(null)
        }
    }
}
```

### 使用示例

```kotlin
val renderer = WebViewMusicRenderer(context)

val abcMusic = MusicData(
    type = MusicType.ABC,
    content = "X:1\nT:Test\nK:C\nC D E F"
)

renderer.renderToBitmap(abcMusic, screenWidth) { bitmap ->
    if (bitmap != null) {
        // 渲染成功，使用 bitmap
        imageView.setImageBitmap(bitmap)
    } else {
        // 渲染失败
        Log.e("Render", "Failed to render music")
    }
}
```

---

## 缓存层

### 功能说明

使用 LRU 缓存策略管理渲染后的 Bitmap：
- 最多缓存 20 张乐谱图片
- 最大缓存 50MB
- 自动回收最少使用的缓存项
- 缓存 Bitmap 会被复制，避免原始 Bitmap 被回收

### 文件位置

`platforms/android/app/src/main/java/com/editor/nomadmark/music/MusicSheetCache.kt`

### 完整代码

```kotlin
package com.editor.nomadmark.music

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache

/**
 * 乐谱 Bitmap 缓存
 *
 * 使用 LRU 缓存策略，避免重复渲染
 */
object MusicSheetCache {

    private const val TAG = "MusicSheetCache"

    // LRU 缓存：最多缓存 20 张乐谱图片
    private val cache = LruCache<String, Bitmap>(20)

    // 缓存大小限制：50MB
    private val maxCacheSize = 50 * 1024 * 1024 // 50MB

    init {
        // 设置缓存大小监听
        cache.resize(maxCacheSize)
    }

    /**
     * 获取缓存的 Bitmap
     *
     * @param key 缓存键（使用 MusicData.getCacheKey() 生成）
     * @return 缓存的 Bitmap，如果不存在返回 null
     */
    fun get(key: String): Bitmap? {
        return cache.get(key)
    }

    /**
     * 缓存 Bitmap
     *
     * 注意：会复制 Bitmap 以避免原始 Bitmap 被回收
     *
     * @param key 缓存键
     * @param bitmap 要缓存的 Bitmap
     */
    fun put(key: String, bitmap: Bitmap) {
        // 检查原始 Bitmap 内容
        val samplePixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        Log.d(TAG, "缓存前原始 Bitmap (${bitmap.width}x${bitmap.height}) 中心像素: 0x${Integer.toHexString(samplePixel)}")

        // 复制 Bitmap 以避免原始 Bitmap 被回收
        val copied = bitmap.copy(bitmap.config, false)

        // 检查复制后的 Bitmap 内容
        val copiedSamplePixel = copied.getPixel(copied.width / 2, copied.height / 2)
        Log.d(TAG, "复制后 Bitmap (${copied.width}x${copied.height}) 中心像素: 0x${Integer.toHexString(copiedSamplePixel)}")

        cache.put(key, copied)
        Log.d(TAG, "缓存乐谱图片: $key, 当前缓存数量: ${cache.size()}")
    }

    /**
     * 清空缓存
     */
    fun clear() {
        cache.evictAll()
        Log.d(TAG, "清空缓存")
    }

    /**
     * 获取缓存大小（字节）
     */
    fun size(): Long {
        return cache.size().toLong()
    }
}
```

### 使用示例

```kotlin
// 生成缓存键
val musicData = MusicData(type = MusicType.ABC, content = "...")
val cacheKey = musicData.getCacheKey()

// 检查缓存
val cached = MusicSheetCache.get(cacheKey)
if (cached != null) {
    // 使用缓存的图片
    imageView.setImageBitmap(cached)
} else {
    // 渲染并缓存
    renderer.renderToBitmap(musicData, width) { bitmap ->
        if (bitmap != null) {
            MusicSheetCache.put(cacheKey, bitmap)
            imageView.setImageBitmap(bitmap)
        }
    }
}

// 清空所有缓存（如在 Activity.onDestroy() 中）
MusicSheetCache.clear()
```

---

## 显示层

### 功能说明

实现 ReplacementSpan 用于在 TextView 中显示乐谱图片：
- 替换原始代码块文本为渲染后的 Bitmap
- 支持显示加载中的占位符
- 自动计算行高
- 支持动态更新 Bitmap

### 文件位置

`platforms/android/app/src/main/java/com/editor/nomadmark/markwon/MusicSheetSpan.kt`

### 完整代码

```kotlin
package com.editor.nomadmark.markwon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Spanned
import android.text.style.ReplacementSpan
import com.editor.nomadmark.music.MusicData

/**
 * 乐谱显示 Span
 *
 * 替换 ```music 代码块为渲染后的乐谱图片
 */
class MusicSheetSpan(
    private val context: Context,
    private val musicData: MusicData,
    private val screenWidth: Int
) : ReplacementSpan() {

    var bitmap: android.graphics.Bitmap? = null
        private set

    /**
     * 更新 Bitmap
     * @return 高度是否变化
     */
    fun updateBitmap(newBitmap: android.graphics.Bitmap?): Boolean {
        val oldHeight = bitmap?.height ?: 0
        bitmap = newBitmap
        val newHeight = bitmap?.height ?: 0
        android.util.Log.d("MusicSheetSpan", "updateBitmap: oldHeight=$oldHeight, newHeight=$newHeight, bitmap=${if (newBitmap != null) "${newBitmap.width}x${newBitmap.height}" else "null"}")
        return oldHeight != newHeight
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val height = bitmap?.height ?: 200 // 默认高度
        if (fm != null) {
            // 设置 FontMetricsInt 以正确计算行高
            fm.ascent = -height / 2
            fm.descent = height / 2
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        return screenWidth
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        android.util.Log.d("MusicSheetSpan", "draw: title=${musicData.title}, bitmap=${if (bitmap != null) "${bitmap!!.width}x${bitmap!!.height}" else "null"}, x=$x, top=$top, bottom=$bottom")
        bitmap?.let {
            // 检查多个位置的像素内容
            val samplePoints = listOf(
                it.width / 2 to it.height / 2,  // 中心点
                it.width / 4 to it.height / 4,  // 1/4 点
                it.width * 3 / 4 to it.height * 3 / 4,  // 3/4 点
                10 to 10  // 左上角
            )
            val pixelColors = samplePoints.map { (px, py) ->
                val pixel = it.getPixel(px, py)
                "($px,$py)=0x${Integer.toHexString(pixel)}"
            }.joinToString(", ")
            android.util.Log.d("MusicSheetSpan", "Bitmap pixels: $pixelColors, isRecycled=${it.isRecycled}")

            // 绘制 Bitmap，使用独立的 Paint 避免受原始 paint 影响
            val bitmapTop = top + 10 // 上边距
            val bitmapPaint = Paint()
            canvas.drawBitmap(it, x, bitmapTop.toFloat(), bitmapPaint)
            android.util.Log.d("MusicSheetSpan", "drawn bitmap at x=$x, y=$bitmapTop, size=${it.width}x${it.height}")
        } ?: run {
            android.util.Log.d("MusicSheetSpan", "draw: showing placeholder for ${musicData.title}")
            // 显示占位符
            val placeholderColor = Color.rgb(240, 240, 240)
            paint.color = placeholderColor
            canvas.drawRect(
                x,
                top.toFloat() + 10,
                x + screenWidth.toFloat(),
                bottom.toFloat() - 10,
                paint
            )

            // 显示文本
            paint.color = Color.rgb(150, 150, 150)
            paint.textSize = 36f
            val placeholderText = "🎵 ${musicData.title ?: "乐谱"}"
            canvas.drawText(
                placeholderText,
                x + 20,
                top + 60f,
                paint
            )
        }
    }
}
```

### 使用示例

```kotlin
// 创建 Span
val musicSpan = MusicSheetSpan(
    context = this,
    musicData = abcMusic,
    screenWidth = resources.displayMetrics.widthPixels
)

// 应用到 Spannable
spannable.setSpan(
    musicSpan,
    startPos,
    endPos,
    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
)

// 后续更新 Bitmap
musicSpan.updateBitmap(renderedBitmap)
textView.invalidate()
```

---

## 主编辑器集成

### 功能说明

将乐谱渲染功能集成到 MarkdownEditorActivity 中：
- 在 `updatePreview()` 中调用乐谱渲染
- 检测乐谱块并应用 MusicSheetSpan
- 异步渲染 Bitmap 并刷新显示
- 避免重复渲染同一代码块

### 文件位置

`platforms/android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt`

### 关键代码片段

#### 1. 成员变量

```kotlin
class MarkdownEditorActivity : android.app.Activity() {

    // ... 其他代码

    /** 乐谱渲染器 */
    private val musicSheetRenderer: WebViewMusicRenderer by lazy {
        WebViewMusicRenderer(this)
    }

    /** 当前活动的 MusicSheetSpan 列表 */
    private val activeMusicSheetSpans = mutableListOf<MusicSheetSpan>()

    /** 是否正在更新预览（防止无限循环） */
    private var isUpdatingPreviewWithMusicSheets = false
```

#### 2. 应用乐谱渲染

```kotlin
    /**
     * 应用乐谱块渲染
     *
     * 使用已检测的乐谱块列表进行渲染
     *
     * 修复说明：
     * - 使用代码块位置去重，确保每个代码块只创建一个 MusicSheetSpan
     * - 使用 Markwon 的代码块 span 范围，确保覆盖整个代码块
     */
    private fun applyMusicSheetRendering(spanned: Spanned, musicSheets: List<MusicSheetDetector.MusicBlock>) {
        val spannable = spanned as Spannable

        try {
            // 清除旧的 MusicSheetSpan 引用
            activeMusicSheetSpans.clear()

            if (musicSheets.isEmpty()) return

            Log.d(TAG, "开始渲染 ${musicSheets.size} 个乐谱块")

            // 记录已处理的代码块位置，避免同一代码块被重复处理
            val processedCodeBlocks = mutableSetOf<String>()

            // 为每个乐谱块应用 MusicSheetSpan 并触发异步渲染
            for (musicSheet in musicSheets) {
                // 在渲染后的文本中查找乐谱代码块的位置
                // 使用第一行前 30 个字符来定位代码块
                val firstLine = musicSheet.musicData.content.lines().first().trim().take(30)
                val contentInRendered = spannable.toString()
                val startPos: Int = contentInRendered.indexOf(firstLine)

                if (startPos == -1) {
                    Log.d(TAG, "未找到乐谱内容位置: ${musicSheet.musicData.title}, searching for: \"$firstLine\"")
                    continue
                }

                // 查找包含 startPos 的代码块 span（FencedCodeBlock 或 CodeBlock）
                // 这样可以确保我们替换整个代码块，而不只是第一行
                var codeBlockStart = startPos
                var codeBlockEnd = startPos + firstLine.length

                // 尝试找到 Markwon 的代码块 span
                try {
                    val fencedCodeClass = Class.forName(FENCED_CODE_BLOCK_SPAN)
                    val fencedSpans = spannable.getSpans(0, spanned.length, fencedCodeClass)
                    for (span in fencedSpans) {
                        val spanStart = spannable.getSpanStart(span)
                        val spanEnd = spannable.getSpanEnd(span)
                        if (startPos >= spanStart && startPos < spanEnd) {
                            codeBlockStart = spanStart
                            codeBlockEnd = spanEnd
                            Log.d(TAG, "找到 FencedCodeBlock: [$spanStart-$spanEnd]")
                            break
                        }
                    }
                } catch (e: ClassNotFoundException) {
                    Log.w(TAG, "FencedCodeBlockSpan class not found: $e")
                }

                // 如果没找到 FencedCodeBlock，尝试找 CodeBlock
                if (codeBlockEnd == startPos + firstLine.length) {
                    try {
                        val codeBlockClass = Class.forName(CODE_BLOCK_SPAN)
                        val codeSpans = spannable.getSpans(0, spanned.length, codeBlockClass)
                        for (span in codeSpans) {
                            val spanStart = spannable.getSpanStart(span)
                            val spanEnd = spannable.getSpanEnd(span)
                            if (startPos >= spanStart && startPos < spanEnd) {
                                codeBlockStart = spanStart
                                codeBlockEnd = spanEnd
                                Log.d(TAG, "找到 CodeBlock: [$spanStart-$spanEnd]")
                                break
                            }
                        }
                    } catch (e: ClassNotFoundException) {
                        Log.w(TAG, "CodeBlockSpan class not found: $e")
                    }
                }

                // 如果还是没找到，就用整个包含该位置的行范围
                if (codeBlockEnd == startPos + firstLine.length) {
                    Log.d(TAG, "未找到代码块 span，使用行范围")
                    // 向前找代码块开始（``` 后面的行）
                    var lineStart = contentInRendered.lastIndexOf('\n', startPos).let {
                        if (it == -1) 0 else it + 1
                    }
                    // 向后找代码块结束（``` 前面的行）
                    var lineEnd = contentInRendered.indexOf('\n', codeBlockEnd).let {
                        if (it == -1) contentInRendered.length else it
                    }
                    codeBlockStart = lineStart
                    codeBlockEnd = lineEnd
                }

                // 使用代码块位置（而不是内容 hash）去重
                val blockPositionKey = "$codeBlockStart-$codeBlockEnd"
                if (!processedCodeBlocks.add(blockPositionKey)) {
                    Log.d(TAG, "跳过已处理的代码块: [$codeBlockStart-$codeBlockEnd]")
                    continue
                }

                val musicSpan = MusicSheetSpan(
                    this,
                    musicSheet.musicData,
                    screenWidth
                )

                // 移除该范围内的所有现有 span（避免重复显示）
                val allSpans = spannable.getSpans(
                    codeBlockStart,
                    codeBlockEnd,
                    Any::class.java
                )
                for (span in allSpans) {
                    val spanStart: Int = spannable.getSpanStart(span)
                    val spanEnd: Int = spannable.getSpanEnd(span)
                    if (spanStart >= codeBlockStart && spanEnd <= codeBlockEnd) {
                        if (span !is MusicSheetSpan) {
                            spannable.removeSpan(span)
                        }
                    }
                }

                // 应用 MusicSheetSpan 到整个代码块范围
                spannable.setSpan(
                    musicSpan,
                    codeBlockStart,
                    codeBlockEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // 添加到活动列表
                activeMusicSheetSpans.add(musicSpan)

                // 触发异步渲染，使用考虑边距后的宽度
                val musicSheetWidth = screenWidth - horizontalMarginPx * 2
                musicSheetRenderer.renderToBitmap(musicSheet.musicData, musicSheetWidth) { bitmap ->
                    Log.d(TAG, "渲染回调: title=${musicSheet.musicData.title}, bitmap=${if (bitmap != null) "${bitmap.width}x${bitmap.height}" else "null"}")
                    val wasEmpty = musicSpan.bitmap == null
                    val heightChanged = musicSpan.updateBitmap(bitmap)

                    // 刷新显示 - 只在首次设置 Bitmap 且没有正在更新时重新渲染，避免无限循环
                    runOnUiThread {
                        if (wasEmpty && bitmap != null && !isUpdatingPreviewWithMusicSheets) {
                            // 首次设置 Bitmap，需要重新渲染文本
                            isUpdatingPreviewWithMusicSheets = true
                            updatePreview()
                            isUpdatingPreviewWithMusicSheets = false
                        } else if (heightChanged) {
                            // 高度变化，只需重绘
                            previewText.invalidate()
                            splitPreviewText?.invalidate()
                        }
                    }
                }

                Log.d(TAG, "应用 MusicSheetSpan: [$codeBlockStart-$codeBlockEnd], title=${musicSheet.musicData.title}")
            }

            Log.d(TAG, "乐谱渲染完成，实际处理 ${activeMusicSheetSpans.size} 个，跳过 ${musicSheets.size - activeMusicSheetSpans.size} 个重复")

        } catch (e: Exception) {
            Log.e(TAG, "处理乐谱块时出错", e)
        }
    }
```

#### 3. 在 updatePreview 中调用

```kotlin
    private fun updatePreview() {
        val content = preprocessMarkdownForBreak(getCurrentContent())

        if (isPreviewMode) {
            markwon.setMarkdown(previewText, content)
            removeUnderlines(previewText.text as Spanned)
            // 先应用乐谱渲染
            val musicSheets = MusicSheetDetector.detectMusicSheetsFromMarkdown(content)
            applyMusicSheetRendering(previewText.text as Spanned, musicSheets)
            // 再应用代码块边框（会跳过乐谱块）
            applyCodeBlockBorder(previewText.text as Spanned)

            previewLayer.postDelayed({
                previewText.requestLayout()
                previewText.invalidate()
            }, 100)
        }

        if (isSplitMode) {
            markwon.setMarkdown(splitPreviewText, content)
            removeUnderlines(splitPreviewText.text as Spanned)
            val musicSheets = MusicSheetDetector.detectMusicSheetsFromMarkdown(content)
            applyMusicSheetRendering(splitPreviewText.text as Spanned, musicSheets)
            applyCodeBlockBorder(splitPreviewText.text as Spanned)

            splitPreviewScroll.postDelayed({
                splitPreviewText.requestLayout()
                splitPreviewText.invalidate()
            }, 100)
        }
    }
```

#### 4. 清理资源

```kotlin
    override fun onDestroy() {
        super.onDestroy()
        // 清理乐谱缓存
        try {
            MusicSheetCache.clear()
            // 清理活动中的 Span
            activeMusicSheetSpans.forEach { span ->
                span.bitmap?.recycle()
            }
            activeMusicSheetSpans.clear()
        } catch (e: Exception) {
            Log.e(TAG, "清理乐谱缓存时出错", e)
        }
    }
```

---

## 常量定义

### Markwon Span 类名

```kotlin
// MarkdownEditorActivity 中的常量
private const val FENCED_CODE_BLOCK_SPAN = "io.noties.markwon.core.span.FencedCodeBlockSpan"
private const val CODE_BLOCK_SPAN = "io.noties.markwon.core.span.CodeBlockSpan"
private const val LINK_SPAN = "io.noties.markwon.core.span.LinkSpan"
private const val THEMATIC_BREAK_SPAN = "io.noties.markwon.core.span.ThematicBreakSpan"
```

---

## 使用示例

### Markdown 输入

````markdown
# 我的乐谱

这是我的作品：

```music
X: 1
T: Cooley's
M: 4/4
L: 1/8
K: Emin
|:D2|"Em"EBBA B2 EB|B2 AB dBAG|"D"FDAD BDAD|FDAD dAFD:|
```

简谱示例：

```简谱
1=C 4/4
5 5 6 6 | 5 4 3 2
1 1 2 2 | 1 - - -
```
````

### 渲染流程

1. `updatePreview()` 调用 Markwon 渲染 Markdown
2. `MusicSheetDetector.detectMusicSheetsFromMarkdown()` 检测乐谱块
3. `applyMusicSheetRendering()` 为每个乐谱块应用 MusicSheetSpan
4. `WebViewMusicRenderer.renderToBitmap()` 异步渲染乐谱为 Bitmap
5. `MusicSheetSpan.updateBitmap()` 更新 Span 的 Bitmap
6. TextView 重绘显示乐谱图片

---

## 依赖资源

### abcjs 库

文件位置：`platforms/android/app/src/main/assets/abcjs/abcjs-basic-min.js`

下载地址：https://cdn.jsdelivr.net/npm/abcjs@6.3.0/dist/abcjs-basic-min.js

### AndroidSVG 库

在 `build.gradle` 中添加：

```gradle
implementation 'com.caverock:androidsvg-aar:1.4'
```

---

## 故障排查

### 问题：乐谱不显示

1. 检查 abcjs 库是否正确放置到 assets 目录
2. 查看日志中是否有 "ABCJS not loaded" 错误
3. 检查 WebView 是否正确初始化

### 问题：乐谱显示为空白

1. 检查 SVG 渲染是否成功
2. 查看 "Bitmap 渲染为空" 日志
3. 检查备用方法是否被调用

### 问题：同一乐谱显示多次

1. 检查代码块位置去重是否生效
2. 查看 "跳过已处理的代码块" 日志
3. 确认 `processedCodeBlocks` 集合正确工作

---

**文档结束**
