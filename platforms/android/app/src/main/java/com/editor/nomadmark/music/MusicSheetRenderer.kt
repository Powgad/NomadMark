package com.editor.nomadmark.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import com.editor.nomadmark.markwon.MusicData
import com.editor.nomadmark.markwon.MusicType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 乐谱渲染器
 *
 * 使用 abcjs 在 WebView 中渲染 ABC 记谱法为位图
 * 支持并行渲染多个乐谱，并自动管理缓存
 */
class MusicSheetRenderer(private val context: Context) {

    init {
        Log.d(TAG, "MusicSheetRenderer 初始化完成")
    }

    companion object {
        private const val TAG = "MusicSheetRenderer"
        private const val ABCJS_VERSION = "6.2.2"
        private const val DEFAULT_WIDTH = 1200
        private const val DEFAULT_HEIGHT = 800

        /**
         * 最大并发渲染数
         */
        private const val MAX_CONCURRENT_RENDERS = 3

        /**
         * 渲染线程池
         */
        private val renderExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_RENDERS) { r ->
            Thread(r).apply { name = "MusicSheetRendererThread" }
        }

        /**
         * 当前渲染计数
         */
        private val activeRenderCount = AtomicInteger(0)

        /**
         * 活跃的 WebView 列表（用于清理）
         */
        private val activeWebViews = ConcurrentHashMap<String, WebView>()

        /**
         * abcjs HTML 模板
         * 使用内嵌的 abcjs 代码避免网络依赖
         */
        private const val ABCJS_HTML = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { margin: 0; padding: 16px; background-color: #F5F5F5; }
                    #paper { background-color: #FFFFFF; padding: 16px; min-height: 200px; }
                    .error { color: red; padding: 10px; }
                </style>
            </head>
            <body>
                <div id="paper"></div>
                <div id="error"></div>
                <script src="https://cdn.jsdelivr.net/npm/abcjs@6.2.2/dist/abcjs-basic-min.js"></script>
                <script>
                    var loadTimeout;
                    var maxWaitTime = 10000; // 10秒超时

                    function renderAbc(abcCode) {
                        try {
                            document.getElementById('paper').innerHTML = '';
                            document.getElementById('error').innerHTML = '';

                            // 验证 ABC 代码不为空
                            if (!abcCode || abcCode.trim().length === 0) {
                                showError('ABC 代码为空');
                                if (window.Android) {
                                    window.Android.onRenderComplete(false, 0);
                                }
                                return;
                            }

                            console.log('开始渲染 ABC，长度: ' + abcCode.length);
                            console.log('ABC 内容: ' + abcCode.substring(0, 200));

                            var output = ABCJS.renderAbc('paper', abcCode, {
                                responsive: 'resize',
                                staffwidth: 900,
                                scale: 1.0,
                                paddingtop: 20,
                                paddingbottom: 20,
                                paddingright: 50,
                                paddingleft: 10
                            });

                            console.log('渲染结果: ' + JSON.stringify(output));

                            // 等待 DOM 更新后获取高度
                            setTimeout(function() {
                                var height = document.body.scrollHeight;
                                console.log('最终高度: ' + height);
                                if (window.Android) {
                                    window.Android.onRenderComplete(height > 100, height);
                                }
                            }, 200);

                        } catch (e) {
                            console.error('渲染异常: ' + e.message);
                            console.error(e.stack);
                            showError('渲染失败: ' + e.message);
                            if (window.Android) {
                                window.Android.onRenderComplete(false, 0);
                            }
                        }
                    }

                    function showError(msg) {
                        document.getElementById('error').innerHTML = '<div class="error">' + msg + '</div>';
                    }

                    function checkAndRender(abcCode) {
                        if (typeof ABCJS !== 'undefined' && ABCJS.renderAbc) {
                            console.log('ABCJS 已加载');
                            clearTimeout(loadTimeout);
                            renderAbc(abcCode);
                        } else {
                            console.log('等待 ABCJS 加载...');
                            if (typeof checkAndRender.counter === 'undefined') {
                                checkAndRender.counter = 0;
                            }
                            checkAndRender.counter++;

                            if (checkAndRender.counter > 50) { // 超过5秒
                                showError('ABCJS 加载超时，请检查网络连接');
                                if (window.Android) {
                                    window.Android.onRenderComplete(false, 0);
                                }
                            } else {
                                setTimeout(function() { checkAndRender(abcCode); }, 100);
                            }
                        }
                    }

                    function render(abcCode) {
                        // 设置超时检测
                        loadTimeout = setTimeout(function() {
                            if (typeof ABCJS === 'undefined') {
                                showError('ABCJS 库加载失败，请检查网络连接');
                                if (window.Android) {
                                    window.Android.onRenderComplete(false, 0);
                                }
                            }
                        }, maxWaitTime);

                        checkAndRender(abcCode);
                    }

                    // 页面加载完成日志
                    window.onload = function() {
                        console.log('页面加载完成');
                    };
                </script>
            </body>
            </html>
        """

        /**
         * 简谱转 ABC
         */
        fun jianpuToAbc(jianpu: String, title: String? = null, composer: String? = null): String {
            val lines = jianpu.lines()
            val abcLines = mutableListOf<String>()

            // 解析元数据
            var parsedTitle = title
            var parsedComposer = composer
            var key = "C"
            var meter = "4/4"
            var tempo: Int? = null
            var contentStart = 0

            for ((i, line) in lines.withIndex()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                // 检查调号（如 1=C, 1=F）
                if (trimmed.matches(Regex("1=[A-G].*"))) {
                    key = trimmed.substringAfter("=").take(1).uppercase()
                    contentStart = i + 1
                    continue
                }
                // 检查拍号（如 4/4, 2/4）
                if (trimmed.matches(Regex("\\d+/\\d+"))) {
                    meter = trimmed
                    contentStart = i + 1
                    continue
                }
                // 检查其他元数据
                if (trimmed.contains(":") && !trimmed.startsWith("#")) {
                    val parts = trimmed.split(":", limit = 2)
                    if (parts.size >= 2) {
                        val metaKey = parts[0].trim().lowercase()
                        val metaValue = parts[1].trim().removeSurrounding("\"")
                        when (metaKey) {
                            "title", "t" -> parsedTitle = metaValue
                            "composer", "c" -> parsedComposer = metaValue
                            "tempo", "q" -> tempo = metaValue.toIntOrNull()
                            "key", "k" -> key = metaValue.take(1).uppercase()
                            "audio", "audiopath" -> { } // 跳过音频路径
                            else -> { contentStart = i; break }
                        }
                        continue
                    }
                }
                // 如果前面已经有元数据，当前行是内容开始
                if (i > 0 && !lines[i - 1].trim().contains(":")) {
                    contentStart = i
                    break
                }
            }

            // 添加 ABC 头部
            abcLines.add("X:1")
            if (parsedTitle != null) abcLines.add("T:$parsedTitle")
            if (parsedComposer != null) abcLines.add("C:$parsedComposer")
            abcLines.add("K:$key")
            abcLines.add("M:$meter")
            abcLines.add("L:1/4")
            if (tempo != null) abcLines.add("Q:$tempo")

            // 解析简谱音符
            val abcNotes = mutableListOf<String>()
            val contentLines = lines.drop(contentStart).filter { it.trim().isNotEmpty() }

            android.util.Log.d(TAG, "简谱内容: ${contentLines.joinToString("\n")}")

            for (line in contentLines) {
                val converted = parseJianpuLine(line)
                if (converted.isNotEmpty()) {
                    abcNotes.add(converted)
                }
            }

            val result = abcNotes.joinToString(" | ")
            android.util.Log.d(TAG, "转换结果: $result")
            abcLines.add(result)
            return abcLines.joinToString("\n")
        }

        /**
         * 解析单行简谱
         */
        private fun parseJianpuLine(line: String): String {
            val result = mutableListOf<String>()
            var i = 0

            while (i < line.length) {
                val char = line[i]

                when {
                    // 跳过空格
                    char == ' ' -> { }
                    // 小节线 - 在 ABC 中用 | 表示
                    char == '|' || char == ':' -> result.add("|")
                    // 减时线 - 延长前一个音符
                    char == '-' -> {
                        if (result.isNotEmpty()) {
                            val last = result.last()
                            // 如果最后一个不是 |，添加延长标记
                            if (last != "|") {
                                result[result.size - 1] = last + "2"
                            }
                        }
                    }
                    // 休止符
                    char == '0' -> result.add("z")
                    // 数字音符 1-7
                    char in '1'..'7' -> result.add(convertJianpuToAbc(char.toString()))
                    // 下加点的音符（用 . 表示低八度）
                    char == '.' && i + 1 < line.length && line[i + 1] in '1'..'7' -> {
                        i++
                        result.add(convertJianpuToAbc(line[i].toString()).lowercase() + ",")
                    }
                    // 上加点的音符（用 ' 表示高八度）
                    char == '\'' -> {
                        // 跳过，后续处理
                    }
                    else -> { }
                }
                i++
            }

            return result.joinToString(" ")
        }

        private fun convertJianpuToAbc(digit: String): String {
            return when (digit) {
                "1" -> "C"
                "2" -> "D"
                "3" -> "E"
                "4" -> "F"
                "5" -> "G"
                "6" -> "A"
                "7" -> "B"
                else -> "C"
            }
        }

        /**
         * 销毁渲染器（清理资源）
         */
        fun destroy() {
            activeWebViews.values.forEach { webView ->
                try {
                    webView.destroy()
                } catch (e: Exception) {
                    Log.e(TAG, "销毁 WebView 失败", e)
                }
            }
            activeWebViews.clear()
            renderExecutor.shutdown()
            MusicSheetBitmapCache.clear()
        }
    }

    /**
     * 渲染乐谱为位图（异步）
     *
     * @param musicData 乐谱数据
     * @param callback 渲染完成回调（在主线程调用）
     */
    fun renderMusicToBitmap(
        musicData: MusicData,
        callback: (Bitmap?) -> Unit
    ) {
        Log.d(TAG, "renderMusicToBitmap 调用: ${musicData.title ?: musicData.id}, type=${musicData.type}")

        // 检查缓存
        val cachedBitmap = MusicSheetBitmapCache.getBitmap(musicData)
        if (cachedBitmap != null) {
            Log.d(TAG, "使用缓存位图: ${musicData.title ?: musicData.id}")
            callback(cachedBitmap)
            return
        }

        // 检查是否已在渲染中
        if (!MusicSheetBitmapCache.startRendering(musicData)) {
            Log.d(TAG, "已在渲染中或已完成: ${musicData.title ?: musicData.id}, state=${MusicSheetBitmapCache.getState(musicData)}")
            // 已在渲染或已完成，添加回调
            MusicSheetBitmapCache.addCallback(musicData, callback)
            return
        }

        Log.d(TAG, "提交新的渲染任务: ${musicData.title ?: musicData.id}")
        // 提交渲染任务
        renderExecutor.execute {
            doRenderMusicToBitmap(musicData, callback)
        }
    }

    /**
     * 执行渲染（在工作线程）
     */
    private fun doRenderMusicToBitmap(
        musicData: MusicData,
        callback: (Bitmap?) -> Unit
    ) {
        val count = activeRenderCount.incrementAndGet()
        Log.d(TAG, "开始渲染 [$count]: ${musicData.title ?: musicData.id}")

        val handler = Handler(Looper.getMainLooper())

        // 根据乐谱类型获取 ABC 代码
        val fullAbc = when (musicData.type) {
            MusicType.ABC -> {
                // ABC 记谱法的内容已经包含完整的标题，直接使用
                musicData.content
            }
            MusicType.JIANPU -> {
                // 简谱需要转换，jianpuToAbc 已经添加了完整的 ABC 头部
                jianpuToAbc(
                    musicData.content,
                    musicData.title,
                    musicData.composer
                )
            }
        }

        // 在主线程创建 WebView 并渲染
        handler.post {
            renderWithWebView(musicData, fullAbc) { bitmap ->
                activeRenderCount.decrementAndGet()
                MusicSheetBitmapCache.completeRendering(musicData, bitmap)
                callback(bitmap)
            }
        }
    }

    /**
     * 使用 WebView 渲染 ABC 代码
     */
    private fun renderWithWebView(
        musicData: MusicData,
        fullAbc: String,
        callback: (Bitmap?) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        val webViewId = musicData.id

        lateinit var webView: WebView

        try {
            Log.d(TAG, "创建 WebView 进行渲染: ${musicData.title ?: musicData.id}")
            Log.d(TAG, "WebView context: $context")
            webView = WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(DEFAULT_WIDTH, DEFAULT_HEIGHT)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    setSupportZoom(false)
                }

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onRenderComplete(success: Boolean, contentHeight: Int = 0) {
                        Log.d(TAG, "onRenderComplete: success=$success, height=$contentHeight, title=${musicData.title ?: musicData.id}")
                        if (success && contentHeight > 0) {
                            val bitmap = captureBitmap(this@apply, contentHeight)
                            // 在主线程清理 WebView 和回调
                            handler.post {
                                cleanupWebView(webViewId)
                                callback(bitmap)
                            }
                        } else {
                            Log.e(TAG, "渲染失败: title=${musicData.title ?: musicData.id}, success=$success, height=$contentHeight")
                            handler.post {
                                cleanupWebView(webViewId)
                                callback(null)
                            }
                        }
                    }
                }, "Android")

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        Log.d(TAG, "页面开始加载: ${musicData.title ?: musicData.id}")
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        Log.d(TAG, "页面加载完成: ${musicData.title ?: musicData.id}")
                        Log.d(TAG, "准备调用 postDelayed: ${musicData.title ?: musicData.id}")
                        // 使用 Handler 而不是 view.postDelayed，确保在主线程执行
                        handler.postDelayed({
                            Log.d(TAG, "postDelayed 回调触发: ${musicData.title ?: musicData.id}")
                            try {
                                // 改进的 JavaScript 字符串转义
                                val escaped = escapeJavaScriptString(fullAbc)
                                Log.d(TAG, "开始渲染 ABC 代码，长度: ${fullAbc.length}")
                                Log.d(TAG, "ABC 代码内容前200字符: ${fullAbc.take(200)}")
                                Log.d(TAG, "转义后长度: ${escaped.length}")

                                // 使用更安全的方式调用 JavaScript
                                val jsCode = "render('$escaped')"
                                Log.d(TAG, "执行 JavaScript: ${jsCode.take(100)}...")
                                view.evaluateJavascript(jsCode, null)
                            } catch (e: Exception) {
                                Log.e(TAG, "执行 JavaScript 失败", e)
                                cleanupWebView(webViewId)
                                handler.post { callback(null) }
                            }
                        }, 500)
                    }
                }

                setWebChromeClient(object : WebChromeClient() {
                    @Suppress("DEPRECATION")
                    override fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) {
                        Log.d(TAG, "Console: $message [line $lineNumber]")
                    }
                })
            }

            activeWebViews[webViewId] = webView
            Log.d(TAG, "加载 HTML 内容: ${musicData.title ?: musicData.id}")
            webView.loadDataWithBaseURL(null, ABCJS_HTML, "text/html", "UTF-8", null)

        } catch (e: Exception) {
            Log.e(TAG, "渲染失败", e)
            cleanupWebView(webViewId)
            callback(null)
        }
    }

    /**
     * JavaScript 字符串转义
     * 将字符串安全地转义以便在 JavaScript 中使用
     */
    private fun escapeJavaScriptString(str: String): String {
        val result = StringBuilder()
        for (char in str) {
            when (char) {
                '\\' -> result.append("\\\\")
                '\'' -> result.append("\\'")
                '\"' -> result.append("\\\"")
                '\n' -> result.append("\\n")
                '\r' -> result.append("\\r")
                '\t' -> result.append("\\t")
                '/' -> result.append("\\/")
                '$' -> result.append("\\$")
                else -> result.append(char)
            }
        }
        return result.toString()
    }

    /**
     * 捕获 WebView 为位图
     */
    private fun captureBitmap(webView: WebView, contentHeight: Int): Bitmap? {
        return try {
            val width = DEFAULT_WIDTH
            val height = maxOf(DEFAULT_HEIGHT, contentHeight + 100)

            webView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, width, height)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            webView.draw(canvas)

            val rgb565 = bitmap.copy(Bitmap.Config.RGB_565, false)
            bitmap.recycle()
            rgb565
        } catch (e: Exception) {
            Log.e(TAG, "捕获位图失败", e)
            null
        }
    }

    /**
     * 清理 WebView
     */
    private fun cleanupWebView(webViewId: String) {
        activeWebViews.remove(webViewId)?.let { webView ->
            try {
                webView.stopLoading()
                webView.clearHistory()
                webView.clearCache(true)
                webView.removeAllViews()
                webView.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "清理 WebView 失败", e)
            }
        }
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        MusicSheetBitmapCache.clear()
    }

    /**
     * 获取缓存状态
     */
    fun getCacheState(musicData: MusicData): MusicSheetBitmapCache.RenderState {
        return MusicSheetBitmapCache.getState(musicData)
    }
}
