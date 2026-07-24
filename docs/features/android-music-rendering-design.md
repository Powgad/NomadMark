# Android 端乐谱渲染功能 - 设计方案

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档版本 | v1.0 |
| 创建日期 | 2026-07-24 |
| 作者 | NomadMark Team |
| 状态 | 设计阶段 |

---

## 一、功能概述

### 1.1 目标

在 NomadMark Android 应用中实现乐谱渲染功能，支持用户在 Markdown 文档中使用 ABC 记谱法和简谱语法，自动渲染为可视化的乐谱图片。

### 1.2 核心需求

- 支持 ABC 记谱法（`` ```music `` 或 `` ```abc `` 代码块）
- 支持简谱（`` ```简谱 `` 代码块）
- 渲染效果与 [abcjs.net](https://www.abcjs.net/) 一致
- 集成到现有的 Markdown 预览流程中
- 支持墨水屏显示优化

### 1.3 参考资料

- [abcjs 官方文档](https://docs.abcjs.net/)
- [abcjs GitHub 仓库](https://github.com/paulrosen/abcjs)
- [abcjs 在线编辑器](https://www.abcjs.net/abcjs-editor)

---

## 二、方案对比分析

### 2.1 技术方案对比

| 方案 | 技术栈 | 渲染效果 | 性能 | 开发难度 | 维护成本 | 推荐度 |
|------|--------|---------|------|----------|----------|--------|
| **方案1: WebView + abcjs** | WebView + JavaScript | ⭐⭐⭐⭐⭐ 100% | 良好 | 中 | 低 | ⭐⭐⭐⭐⭐ |
| **方案2: Verovio Native** | Verovio JNI | ⭐⭐⭐⭐ 95% | 优秀 | 高 | 中 | ⭐⭐⭐ |
| **方案3: 自绘引擎** | Canvas + 自解析 | ⭐⭐⭐ 70% | 可控 | 极高 | 高 | ⭐ |
| **方案4: 预渲染图片** | 服务端渲染 | ⭐⭐⭐⭐ 100% | 最佳 | 低 | 中 | ⭐⭐ |

### 2.2 方案详解

#### 方案1: WebView + abcjs（推荐）

**优势：**
- ✅ 使用成熟的 abcjs 库，渲染效果 100% 一致
- ✅ 支持所有 ABC 记谱法特性
- ✅ 可选的音频播放功能
- ✅ 开发周期短（5-7天）
- ✅ 维护成本低

**劣势：**
- ⚠️ WebView 内存占用较高
- ⚠️ 首次渲染需要加载 abcjs 库
- ⚠️ 需要处理 WebView 生命周期

#### 方案2: Verovio Native

**优势：**
- ✅ 原生性能优秀
- ✅ 内存占用更低

**劣势：**
- ❌ 需要 JNI 集成，开发复杂
- ❌ 部分高级特性支持不如 abcjs
- ❌ 音频播放需要额外实现

#### 方案3: 自绘引擎

**优势：**
- ✅ 完全可控

**劣势：**
- ❌ 需要完整实现 ABC 解析和渲染
- ❌ 开发周期极长（数月）
- ❌ 维护成本高

---

## 三、推荐方案架构设计

### 3.1 整体架构

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
│  │  │  │        MusicSheetRenderer                │   │  │  │
│  │  │  │  ┌─────────────────────────────────┐     │   │  │  │
│  │  │  │  │   WebViewRenderer (abcjs)      │     │   │  │  │
│  │  │  │  │   - 渲染为 Bitmap               │     │   │  │  │
│  │  │  │  │   - 缓存机制                     │     │   │  │  │
│  │  │  │  └─────────────────────────────────┘     │   │  │  │
│  │  │  └───────────────────────────────────────────┘   │  │  │
│  │  │              ↓                                  │   │  │  │
│  │  │  ┌───────────────────────────────────────────┐   │  │  │
│  │  │  │      MusicSheetSpan (ReplacementSpan)     │   │  │  │
│  │  │  │  - 显示 Bitmap                             │   │  │  │
│  │  │  │  - 支持点击播放（可选）                    │   │  │  │
│  │  │  └───────────────────────────────────────────┘   │  │  │
│  │  └─────────────────────────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 核心组件设计

#### 组件 1: MusicData（乐谱数据模型）

```kotlin
package com.editor.nomadmark.music

/**
 * 乐谱数据模型
 */
data class MusicData(
    val id: String = generateId(),
    val type: MusicType,
    val content: String,
    val title: String? = null,
    val composer: String? = null,
    val tempo: Int = 120,
    val key: String? = null,
    val sourceRange: IntRange = 0..0  // 在原文中的位置
) : Serializable {
    
    companion object {
        fun generateId(): String = "music_${System.currentTimeMillis()}"
    }
    
    /**
     * 获取缓存键
     */
    fun getCacheKey(): String {
        return "${type.name}_${content.hashCode()}"
    }
}

/**
 * 乐谱类型
 */
enum class MusicType {
    ABC,        // ABC 记谱法 (```music 或 ```abc)
    JIANPU,     // 简谱 (```简谱)
    UNKNOWN;
    
    companion object {
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

#### 组件 2: MusicSheetDetector（乐谱块检测器）

```kotlin
package com.editor.nomadmark.music

/**
 * 乐谱块检测器
 * 
 * 检测 Markdown 中的 ```music 和 ```简谱 代码块
 */
object MusicSheetDetector {
    
    private const val TAG = "MusicSheetDetector"
    
    /**
     * 乐谱块信息
     */
    data class MusicBlock(
        val musicData: MusicData,
        val blockStart: Int,
        val blockEnd: Int
    )
    
    /**
     * 检测文本中的所有乐谱块
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
                trimmed.startsWith("T:") -> title = trimmed.substring(2).trim()
                trimmed.startsWith("C:") -> composer = trimmed.substring(2).trim()
                trimmed.startsWith("Q:") -> {
                    val tempoStr = trimmed.substring(2).trim().split(" ").first()
                    tempo = tempoStr.toIntOrNull() ?: 120
                }
                trimmed.startsWith("K:") -> key = trimmed.substring(2).trim()
            }
        }
        
        return AbcMetadata(title, composer, tempo, key)
    }
}
```

#### 组件 3: WebViewMusicRenderer（WebView 渲染器）

```kotlin
package com.editor.nomadmark.music

/**
 * 基于 WebView 的乐谱渲染器
 * 
 * 使用 abcjs 库在 WebView 中渲染 ABC 记谱法，然后转换为 Bitmap
 */
class WebViewMusicRenderer(private val context: Context) {
    
    companion object {
        private const val TAG = "WebViewMusicRenderer"
        
        // WebView 缓存池
        private val webViewPool = WebViewPool(maxSize = 3)
    }
    
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * 渲染 ABC 乐谱为 Bitmap
     */
    fun renderToBitmap(
        musicData: MusicData,
        width: Int,
        callback: (Bitmap?) -> Unit
    ) {
        // 检查缓存
        val cached = MusicSheetCache.get(musicData.getCacheKey())
        if (cached != null && cached.width == width) {
            Log.d(TAG, "使用缓存的乐谱图片: ${musicData.title ?: musicData.id}")
            callback(cached)
            return
        }
        
        // 从 WebView 池获取
        val webView = webViewPool.obtain()
        
        try {
            configureWebView(webView, width)
            
            // 生成 HTML
            val html = generateAbcHtml(musicData, width)
            
            // 渲染
            webView.loadDataWithBaseURL(
                null,
                html,
                "text/html",
                "UTF-8",
                null
            )
            
            // 延迟等待渲染完成
            handler.postDelayed({
                captureBitmap(webView, musicData, width, callback)
                webViewPool.recycle(webView)
            }, 800)
            
        } catch (e: Exception) {
            Log.e(TAG, "渲染失败", e)
            callback(null)
            webViewPool.recycle(webView)
        }
    }
    
    /**
     * 配置 WebView
     */
    private fun configureWebView(webView: WebView, width: Int) {
        webView.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            
            settings.apply {
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setMixedContentMode(WebSettings.MIXED_CONTENT_MODE_COMPATIBILITY)
                }
            }
            
            // 禁用滚动
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            
            // 白背景
            setBackgroundColor(Color.WHITE)
        }
    }
    
    /**
     * 生成 abcjs HTML
     */
    private fun generateAbcHtml(musicData: MusicData, width: Int): String {
        val content = Json.encode(musicData.content)
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
                try {
                    const abcCode = $content;
                    ABCJS.renderAbc("paper", abcCode, {
                        responsive: 'resize',
                        scale: 1.2,
                        staffwidth: $staffWidth,
                        paddingtop: 20,
                        paddingbottom: 20,
                        paddingright: 20,
                        paddingleft: 20
                    });
                    
                    // 通知渲染完成
                    window.renderComplete = true;
                } catch(e) {
                    console.error('ABC rendering error:', e);
                    window.renderError = e.message;
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }
    
    /**
     * 截取 WebView 为 Bitmap
     */
    private fun captureBitmap(
        webView: WebView,
        musicData: MusicData,
        width: Int,
        callback: (Bitmap?) -> Unit
    ) {
        try {
            // 测量 WebView
            val specWidth = View.MeasureSpec.makeMeasureSpec(
                width, 
                View.MeasureSpec.EXACTLY
            )
            val specHeight = View.MeasureSpec.makeMeasureSpec(
                0, 
                View.MeasureSpec.UNSPECIFIED
            )
            
            webView.measure(specWidth, specHeight)
            webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)
            
            // 创建 Bitmap
            val bitmap = Bitmap.createBitmap(
                webView.measuredWidth,
                webView.measuredHeight,
                Bitmap.Config.ARGB_8888
            )
            
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            webView.draw(canvas)
            
            // 缓存 Bitmap
            MusicSheetCache.put(musicData.getCacheKey(), bitmap)
            
            Log.d(TAG, "渲染完成: ${bitmap.width}x${bitmap.height}")
            callback(bitmap)
            
        } catch (e: Exception) {
            Log.e(TAG, "截取 Bitmap 失败", e)
            callback(null)
        }
    }
}

/**
 * WebView 对象池
 */
private class WebViewPool(private val maxSize: Int = 3) {
    
    private val pool = ArrayDeque<WebView>()
    private val activeViews = mutableSetOf<WebView>()
    
    @Synchronized
    fun obtain(): WebView {
        val webView = pool.removeFirstOrNull() ?: createNewWebView()
        activeViews.add(webView)
        return webView
    }
    
    @Synchronized
    fun recycle(webView: WebView) {
        activeViews.remove(webView)
        if (pool.size < maxSize) {
            webView.loadUrl("about:blank")
            pool.add(webView)
        } else {
            webView.destroy()
        }
    }
    
    private fun createNewWebView(): WebView {
        return WebView(NomadMarkApplication.instance).apply {
            // 基础配置
            settings.apply {
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
        }
    }
}
```

#### 组件 4: MusicSheetCache（缓存管理）

```kotlin
package com.editor.nomadmark.music

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
     */
    fun get(key: String): Bitmap? {
        return cache.get(key)
    }
    
    /**
     * 缓存 Bitmap
     */
    fun put(key: String, bitmap: Bitmap) {
        // 复制 Bitmap 以避免原始 Bitmap 被回收
        val copied = bitmap.copy(bitmap.config, false)
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

#### 组件 5: MusicSheetSpan（乐谱显示 Span）

```kotlin
package com.editor.nomadmark.markwon

/**
 * 乐谱显示 Span
 * 
 * 替换 ```music 代码块为渲染后的乐谱图片
 */
class MusicSheetSpan : ReplacementSpan {
    
    private val context: Context
    private val musicData: MusicData
    private val screenWidth: Int
    
    private var bitmap: Bitmap? = null
    private var targetWidth: Int = 0
    
    // 渲染器
    private val renderer: WebViewMusicRenderer by lazy { 
        WebViewMusicRenderer(context) 
    }
    
    constructor(context: Context, musicData: MusicData, screenWidth: Int) {
        this.context = context
        this.musicData = musicData
        this.screenWidth = screenWidth
    }
    
    /**
     * 更新 Bitmap
     * @return 高度是否变化
     */
    fun updateBitmap(newBitmap: Bitmap?): Boolean {
        val oldHeight = bitmap?.height ?: 0
        bitmap = newBitmap
        val newHeight = bitmap?.height ?: 0
        return oldHeight != newHeight
    }
    
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        return bitmap?.height ?: 200 // 默认高度
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
        bitmap?.let {
            // 绘制 Bitmap，居中对齐
            val bitmapTop = top + 10 // 上边距
            canvas.drawBitmap(it, x, bitmapTop.toFloat(), paint)
        } ?: run {
            // 显示占位符
            val placeholderColor = Color.rgb(240, 240, 240)
            paint.color = placeholderColor
            canvas.drawRect(
                x, 
                top.toFloat() + 10, 
                x + screenWidth, 
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

---

## 四、集成到 MarkdownEditorActivity

在 `MarkdownEditorActivity.kt` 中添加以下代码：

### 4.1 添加成员变量

```kotlin
class MarkdownEditorActivity : android.app.Activity() {
    
    // ... 其他代码
    
    /** 乐谱渲染器 */
    private val musicSheetRenderer: WebViewMusicRenderer by lazy {
        WebViewMusicRenderer(this)
    }
    
    /** 当前活动的 MusicSheetSpan 列表 */
    private val activeMusicSheetSpans = mutableListOf<MusicSheetSpan>()
```

### 4.2 添加乐谱渲染方法

```kotlin
    /**
     * 应用乐谱块渲染
     */
    private fun applyMusicSheetRendering(spanned: Spanned) {
        val spannable = spanned as Spannable
        
        try {
            // 清除旧的 MusicSheetSpan 引用
            activeMusicSheetSpans.clear()
            
            // 使用 MusicSheetDetector 检测乐谱块
            val musicSheets = MusicSheetDetector.detectMusicSheets(spanned)
            
            Log.d(TAG, "检测到 ${musicSheets.size} 个乐谱块")
            
            if (musicSheets.isEmpty()) return
            
            // 为每个乐谱块应用 MusicSheetSpan 并触发异步渲染
            for (musicSheet in musicSheets) {
                val musicSpan = MusicSheetSpan(
                    this,
                    musicSheet.musicData,
                    screenWidth
                )
                
                // 移除该范围内的所有现有 span（避免重复显示）
                val allSpans = spannable.getSpans(
                    musicSheet.blockStart, 
                    musicSheet.blockEnd, 
                    Any::class.java
                )
                for (span in allSpans) {
                    val spanStart = spannable.getSpanStart(span)
                    val spanEnd = spannable.getSpanEnd(span)
                    if (spanStart >= musicSheet.blockStart && spanEnd <= musicSheet.blockEnd) {
                        if (span !is MusicSheetSpan) {
                            spannable.removeSpan(span)
                        }
                    }
                }
                
                // 应用 MusicSheetSpan
                spannable.setSpan(
                    musicSpan,
                    musicSheet.blockStart,
                    musicSheet.blockEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                // 添加到活动列表
                activeMusicSheetSpans.add(musicSpan)
                
                // 触发异步渲染
                musicSheetRenderer.renderToBitmap(musicSheet.musicData, screenWidth) { bitmap ->
                    musicSpan.updateBitmap(bitmap)
                    
                    // 刷新显示
                    runOnUiThread {
                        previewText.invalidate()
                        splitPreviewText?.invalidate()
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理乐谱块时出错", e)
        }
    }
```

### 4.3 修改 updatePreview 方法

```kotlin
    private fun updatePreview() {
        val content = preprocessMarkdownForBreak(getCurrentContent())
        
        if (isPreviewMode) {
            markwon.setMarkdown(previewText, content)
            removeUnderlines(previewText.text as Spanned)
            // 先应用乐谱渲染
            applyMusicSheetRendering(previewText.text as Spanned)
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
            applyMusicSheetRendering(splitPreviewText.text as Spanned)
            applyCodeBlockBorder(splitPreviewText.text as Spanned)
            
            splitPreviewScroll.postDelayed({
                splitPreviewText.requestLayout()
                splitPreviewText.invalidate()
            }, 100)
        }
    }
```

---

## 五、资源文件准备

### 5.1 下载 abcjs 库

```bash
# 下载 abcjs 基础版本（约 200KB）
wget https://cdn.jsdelivr.net/npm/abcjs@6.3.0/dist/abcjs-basic-min.js

# 或使用完整版本（包含音频支持，约 350KB）
wget https://cdn.jsdelivr.net/npm/abcjs@6.3.0/dist/abcjs-plugin-min.js
```

### 5.2 放置到 assets 目录

创建目录结构：
```
platforms/android/app/src/main/assets/
└── abcjs/
    ├── abcjs-basic-min.js      # 基础版本（用于静态渲染）
    └── abcjs-plugin-min.js      # 完整版本（用于音频播放，可选）
```

---

## 六、使用示例

### 6.1 ABC 记谱法

````markdown
```music
X: 1
T: Cooley's
M: 4/4
L: 1/8
K: Emin
|:D2|"Em"EBBA B2 EB|B2 AB dBAG|"D"FDAD BDAD|FDAD dAFD:|
```
````

### 6.2 简谱

````markdown
```简谱
1=C 4/4
5 5 6 6 | 5 4 3 2
1 1 2 2 | 1 - - -
```
````

### 6.3 渲染效果

- 完整的五线谱显示
- 支持调号、拍号、反复记号
- 支持多声部
- 自动适配墨水屏显示

---

## 七、渐进式实现路径

### 阶段 1: 基础渲染（2-3天）

**任务清单：**
- [ ] 创建 `music` 包结构
- [ ] 实现 `MusicData` 数据模型
- [ ] 实现 `MusicSheetDetector` 检测器
- [ ] 实现 `WebViewMusicRenderer` 基础渲染
- [ ] 实现 `MusicSheetSpan` 显示
- [ ] 下载并放置 abcjs 库到 assets
- [ ] 集成到 `MarkdownEditorActivity.updatePreview()`
- [ ] 测试基本 ABC 渲染

### 阶段 2: 缓存优化（1天）

**任务清单：**
- [ ] 实现 `MusicSheetCache` LRU 缓存
- [ ] 实现 `WebViewPool` 对象池
- [ ] 添加缓存大小监控
- [ ] 测试缓存有效性
- [ ] 内存泄漏检查

### 阶段 3: 增强功能（2-3天）

**任务清单：**
- [ ] 支持简谱（`` ```简谱 ``）
- [ ] 添加错误处理和降级显示
- [ ] 支持自定义样式（主题配色）
- [ ] 添加占位符显示
- [ ] 优化墨水屏显示效果
- [ ] 性能优化

### 阶段 4: 音频播放（可选，3-5天）

**任务清单：**
- [ ] 集成 abcjs 音频合成器
- [ ] 实现 JavaScript 桥接
- [ ] 实现播放控制 UI
- [ ] 实现光标跟随
- [ ] 测试音频播放功能

---

## 八、性能优化策略

### 8.1 WebView 优化

| 策略 | 说明 | 效果 |
|------|------|------|
| 对象池 | 复用 WebView 实例 | 减少 30% 创建时间 |
| 预加载 | 应用启动时预创建 WebView | 首次渲染提速 50% |
| 延迟销毁 | 使用后延迟销毁 | 减少内存抖动 |

### 8.2 缓存策略

```kotlin
// LRU 缓存配置
object CacheConfig {
    // 最多缓存 20 张乐谱图片
    const val MAX_CACHE_COUNT = 20
    
    // 最大缓存 50MB
    const val MAX_CACHE_SIZE = 50 * 1024 * 1024
    
    // 缓存有效期 7 天
    const val CACHE_VALIDITY_DAYS = 7
}
```

### 8.3 内存管理

```kotlin
// 在 Activity.onDestroy() 中清理
override fun onDestroy() {
    super.onDestroy()
    
    // 清理乐谱缓存
    MusicSheetCache.clear()
    
    // 清理活动中的 Span
    activeMusicSheetSpans.forEach { span ->
        span.bitmap?.recycle()
    }
    activeMusicSheetSpans.clear()
}
```

---

## 九、测试计划

### 9.1 单元测试

```kotlin
class MusicSheetDetectorTest {
    
    @Test
    fun testDetectABC() {
        val text = """
        ```music
        X: 1
        T: Test
        K: C
        C D E F | G A B c
        ```
        """.trimIndent()
        
        val spannable = SpannableString(text)
        val results = MusicSheetDetector.detectMusicSheets(spannable)
        
        assertEquals(1, results.size)
        assertEquals(MusicType.ABC, results[0].musicData.type)
        assertEquals("Test", results[0].musicData.title)
    }
    
    @Test
    fun testDetectJianpu() {
        val text = """
        ```简谱
        1=C 4/4
        5 5 6 6 | 5 4 3 2
        ```
        """.trimIndent()
        
        val spannable = SpannableString(text)
        val results = MusicSheetDetector.detectMusicSheets(spannable)
        
        assertEquals(1, results.size)
        assertEquals(MusicType.JIANPU, results[0].musicData.type)
    }
}
```

### 9.2 集成测试

**测试用例：**
1. 基本 ABC 渲染
2. 简谱渲染
3. 多乐谱渲染
4. 缓存有效性
5. 内存占用
6. 墨水屏显示效果

### 9.3 性能测试

| 指标 | 目标 | 测试方法 |
|------|------|----------|
| 首次渲染时间 | < 1s | 计时测试 |
| 缓存命中渲染 | < 100ms | 计时测试 |
| 内存占用 | < 100MB | Android Profiler |
| WebView 复用率 | > 80% | 日志统计 |

---

## 十、已知限制与解决方案

| 限制 | 说明 | 解决方案 |
|------|------|----------|
| 首次渲染延迟 | WebView 加载 abcjs 需要时间 | 预加载 WebView，显示加载提示 |
| 内存占用 | WebView + Bitmap 占用内存 | 使用对象池和缓存限制 |
| 网络依赖 | CDN 加载 js 文件 | 打包到 assets 目录 |
| 交互功能 | 点击播放需要额外处理 | 使用 JavaScript 桥接（阶段4） |
| 大型乐谱 | 超长乐谱渲染可能较慢 | 分段渲染或缩放显示 |

---

## 十一、墨水屏优化

### 11.1 显示优化

```kotlin
// 墨水屏友好的配色
object InkScreenColors {
    const val BACKGROUND = Color.WHITE
    const val STAFF_LINES = Color.BLACK
    const val NOTES = Color.BLACK
    const val TEXT = Color.rgb(80, 80, 80)
}
```

### 11.2 刷新优化

```kotlin
// 使用 E-ink 刷新控制器
class MusicSheetSpan(
    context: Context,
    musicData: MusicData,
    screenWidth: Int
) : ReplacementSpan() {
    
    private val einkController = EinkRefreshController(null)
    
    override fun draw(canvas: Canvas, ...) {
        bitmap?.let {
            canvas.drawBitmap(it, x, bitmapTop.toFloat(), paint)
            // 触发 E-ink 局部刷新
            einkController.requestPartialRefresh()
        }
    }
}
```

---

## 十二、后续扩展

### 12.1 可能的功能扩展

1. **音频播放**
   - 集成 abcjs 音频合成器
   - 播放控制 UI
   - 光标跟随播放

2. **编辑支持**
   - 乐谱编辑模式
   - 实时预览
   - 错误检查

3. **导出功能**
   - 导出为 PDF
   - 导出为 MIDI
   - 分享功能

4. **更多格式支持**
   - MusicXML 格式
   - LilyPond 格式
   - 自定义符号

---

## 附录

### A. 参考资料

- [abcjs 官方文档](https://docs.abcjs.net/)
- [abcjs GitHub 仓库](https://github.com/paulrosen/abcjs)
- [ABC 记谱法标准](https://abcnotation.com/wiki/abc:standard:v2.1)
- [Android WebView 最佳实践](https://developer.android.com/guide/webapps/webview)

### B. 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-07-24 | 初始版本 |

### C. 相关文档

- [架构设计书](../architecture/)
- [UI 文档](../guides/)
- [测试指南](../guides/)

---

**文档结束**
