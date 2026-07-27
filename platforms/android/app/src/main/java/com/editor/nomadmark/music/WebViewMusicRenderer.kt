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

    // 【去重】记录正在渲染的乐谱，防止重复渲染
    private val pendingRenders = mutableSetOf<String>()

    /**
     * 渲染 ABC 乐谱为 Bitmap
     */
    fun renderToBitmap(
        musicData: MusicData,
        width: Int,
        callback: (Bitmap?) -> Unit
    ) {
        // 【去重】生成渲染键
        val renderKey = "${musicData.getCacheKey(width)}"

        // 【去重】检查是否正在渲染中
        if (pendingRenders.contains(renderKey)) {
            Log.d(TAG, "跳过重复渲染: ${musicData.title ?: musicData.id}, key=$renderKey")
            callback(null)
            return
        }

        // 检查缓存
        val cached = MusicSheetCache.get(renderKey)
        if (cached != null) {
            Log.d(TAG, "使用缓存的乐谱图片: ${musicData.title ?: musicData.id}")
            callback(cached)
            return
        }

        // 【去重】标记为正在渲染
        pendingRenders.add(renderKey)
        Log.d(TAG, "开始渲染: ${musicData.title ?: musicData.id}, key=$renderKey, 当前pending: ${pendingRenders.size}")

        // 包装回调，确保完成后清理
        val wrappedCallback = { bitmap: Bitmap? ->
            try {
                callback(bitmap)
            } finally {
                // 【去重】无论成功失败，都从待渲染列表中移除
                pendingRenders.remove(renderKey)
                Log.d(TAG, "渲染完成: ${musicData.title ?: musicData.id}, key=$renderKey, 剩余pending: ${pendingRenders.size}")
            }
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
                        captureBitmap(webView, musicData, width, wrappedCallback) {
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

            handler.postDelayed({
                    captureBitmap(webView, musicData, width, wrappedCallback) {
                        webView.destroy()   
                }
            }, 10000)

        } catch (e: Exception) {
            Log.e(TAG, "渲染失败", e)
            wrappedCallback(null)
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
                /* 增加标题和乐谱之间的间距 */
                .abcjs-header {
                    margin-bottom: 60px !important;
                }
                /* 增加乐谱各部分之间的间距 */
                .abcjs-row {
                    margin-bottom: 20px;
                }
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
                        // 【调试】拦截 ABCJS.renderAbc 调用
                        const origRenderAbc = ABCJS.renderAbc;
                        ABCJS.renderAbc = function(container, source, options) {
                            const lines = source.split('\\n').length;
                            console.log('[ABC-RENDER-DEBUG] renderAbc called:');
                            console.log('  - source length:', source.length);
                            console.log('  - lines:', lines);
                            console.log('  - first 100 chars:', source.substring(0, 100));
                            console.log('  - container:', container);
                            return origRenderAbc.call(this, container, source, options);
                        };

                        const abcCode = "$escapedContent";
                        console.log('[ABC-RENDER-DEBUG] About to render ABC code:');
                        console.log('  - abcCode length:', abcCode.length);
                        console.log('  - abcCode lines:', abcCode.split('\\n').length);

                        ABCJS.renderAbc("paper", abcCode, {
                            // 不使用 responsive:'resize'：它会把 SVG 宽度变成百分比，
                            // AndroidSVG 无法读取绝对尺寸，导致多行乐谱高度被截断
                            scale: 1.2,
                            staffwidth: $staffWidth,
                            wrap: true,  // 按谱表宽度自动换行，长乐谱在小节线处断行
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
                                console.log('[ABC-RENDER-DEBUG] SVG extracted, length:', svgString.length);
                            } else {
                                console.error('[ABC-RENDER-DEBUG] No SVG found');
                                window.SVG_ERROR = 'No SVG element found';
                            }
                        }, 300);

                        window.renderComplete = true;
                    } catch(e) {
                        console.error('[ABC-RENDER-DEBUG] ABC rendering error:', e);
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
                            // 例如: "\"<svg>...</svg>\"" 需要正确解析
                            val svgString = if (svgResult.length > 2 && svgResult.startsWith("\"") && svgResult.endsWith("\"")) {
                                // 移除外层引号
                                val inner = svgResult.substring(1, svgResult.length - 1)
                                // 处理转义字符
                                inner.replace("\\\"", "\"")
                                    .replace("\\\\", "\\")
                                    .replace("\\n", "\n")
                                    .replace("\\r", "\r")
                                    .replace("\\t", "\t")
                            } else {
                                svgResult
                            }

                            Log.d(TAG, "获取到 SVG，长度: ${svgString.length}")
                            Log.d(TAG, "SVG 开头: ${svgString.take(100)}")

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
                                // 从 Picture 创建 Bitmap
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
                                    MusicSheetCache.put(musicData.getCacheKey(width), bitmap)
                                    Log.d(TAG, "从 SVG 渲染完成: ${bitmap.width}x${bitmap.height}")
                                    callback(bitmap)
                                    onDestroy()
                                } else {
                                    Log.w(TAG, "SVG 渲染为空，使用备用方法")
                                    captureBitmapFallback(webView, musicData, width, callback) {
                                        // 备用方法完成后销毁 WebView
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
     * 从 SVG 字符串解析像素尺寸
     *
     * AndroidSVG 的 documentWidth/Height 在 SVG 使用百分比宽度（如 width="100%"）时返回 -1，
     * 不可靠。这里直接解析 width/height/viewBox 属性，保证多行（wrap）乐谱的完整高度
     * 不被错误地按默认值截断。
     */
    private data class SvgSize(val width: Float, val height: Float)

    private fun parseSvgSize(svgString: String, fallbackWidth: Int): SvgSize {
        // 仅匹配纯数字或带 px 单位的尺寸，排除百分比（如 width="100%"）
        val widthAttr = Regex("""<svg[^>]*\bwidth\s*=\s*["']([\d.]+)(?:px)?["']""").find(svgString)?.groupValues?.get(1)?.toFloatOrNull()
        val heightAttr = Regex("""<svg[^>]*\bheight\s*=\s*["']([\d.]+)(?:px)?["']""").find(svgString)?.groupValues?.get(1)?.toFloatOrNull()
        val viewBox = Regex("""<svg[^>]*\bviewBox\s*=\s*["']([-\d.\s]+)["']""").find(svgString)
            ?.groupValues?.get(1)?.trim()?.split(Regex("""\s+"""))
        val vbW = viewBox?.getOrNull(2)?.toFloatOrNull()
        val vbH = viewBox?.getOrNull(3)?.toFloatOrNull()

        val w = when {
            widthAttr != null && widthAttr > 0 -> widthAttr
            vbW != null && vbW > 0 -> vbW
            else -> fallbackWidth.toFloat()
        }
        val h = when {
            heightAttr != null && heightAttr > 0 -> heightAttr
            vbH != null && vbH > 0 -> vbH
            else -> 0f
        }
        return SvgSize(w, h)
    }

    /**
     * 从 SVG 字符串创建 Picture（使用 AndroidSVG）
     */
    private fun createPictureFromSvg(svgString: String, width: Int): Picture? {
        return try {
            val svg = SVG.getFromString(svgString)

            // 直接解析 SVG 的像素尺寸，避免 AndroidSVG 在百分比宽度下返回 -1 而误裁高度
            val size = parseSvgSize(svgString, width)
            svg.setDocumentWidth(size.width)
            if (size.height > 0) svg.setDocumentHeight(size.height)

            val picWidth = size.width.toInt().coerceAtLeast(width)
            val picHeight = if (size.height > 0) size.height.toInt() else 400

            val picture = Picture()
            val canvas = picture.beginRecording(picWidth, picHeight)
            canvas.drawColor(Color.WHITE)
            svg.renderToCanvas(canvas)
            picture.endRecording()

            val svgCount = svgString.split("<svg").size - 1
            Log.d(TAG, "SVG 渲染成功: parsed=${size.width}x${size.height}, androidSvg=(${svg.documentWidth}x${svg.documentHeight}), pic=${picWidth}x${picHeight}, svgCount=$svgCount, len=${svgString.length}")
            picture
        } catch (e: Exception) {
            Log.e(TAG, "SVG 解析失败", e)
            null
        }
    }

    /**
     * 备用的 Bitmap 截取方法
     */
    private fun captureBitmapFallback(
        webView: WebView,
        musicData: MusicData,
        width: Int,
        callback: (Bitmap?) -> Unit,
        onComplete: () -> Unit = {}
    ) {
        try {
            // 强制 WebView 重新测量和布局
            webView.forceLayout()

            val specWidth = View.MeasureSpec.makeMeasureSpec(
                width,
                View.MeasureSpec.EXACTLY
            )
            val specHeight = View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            )

            // 测量 WebView
            webView.measure(specWidth, specHeight)

            // 如果高度太小，使用更大的默认高度
            var measuredHeight = webView.measuredHeight
            Log.d(TAG, "WebView 测量尺寸: ${webView.measuredWidth}x$measuredHeight")

            if (measuredHeight < 100) {
                measuredHeight = 400 // 使用更大的默认高度
                Log.w(TAG, "WebView 高度太小，使用默认高度: $measuredHeight")
            }

            webView.layout(0, 0, webView.measuredWidth, measuredHeight)

            // 获取 WebView 内容高度（通过 JavaScript）
            webView.evaluateJavascript("(function() { return document.body.scrollHeight; })();") { height ->
                Log.d(TAG, "WebView 内容高度: $height")
            }

            // 创建 Bitmap
            val bitmap = Bitmap.createBitmap(
                webView.measuredWidth,
                measuredHeight,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            // 使用软件层绘制
            webView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            webView.draw(canvas)
            webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            // 检查 Bitmap 是否为空（全白）并统计内容分布
            var hasContent = false
            var nonWhiteCount = 0
            var firstNonWhiteX = -1
            var firstNonWhiteY = -1
            val pixels = IntArray(Math.min(bitmap.width * bitmap.height, 10000))
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, Math.min(bitmap.height, 10000 / bitmap.width))
            for (i in pixels.indices) {
                if (pixels[i] != Color.WHITE) {
                    hasContent = true
                    nonWhiteCount++
                    if (firstNonWhiteX == -1) {
                        firstNonWhiteX = i % bitmap.width
                        firstNonWhiteY = i / bitmap.width
                    }
                }
            }

            // 检查特定位置的像素
            val centerX = bitmap.width / 2
            val centerY = bitmap.height / 2
            val centerPixel = bitmap.getPixel(centerX, centerY)
            Log.d(TAG, "Bitmap 中心像素 ($centerX,$centerY): 0x${Integer.toHexString(centerPixel)}")

            if (!hasContent) {
                Log.w(TAG, "Bitmap 渲染为空（全白）")
            } else {
                Log.d(TAG, "Bitmap 有内容，非白色像素数: $nonWhiteCount, 第一个非白色像素位置: ($firstNonWhiteX,$firstNonWhiteY)")
            }

            // 缓存 Bitmap
            MusicSheetCache.put(musicData.getCacheKey(width), bitmap)

            Log.d(TAG, "渲染完成: ${bitmap.width}x${bitmap.height}, hasContent=$hasContent")
            callback(bitmap)

        } catch (e: Exception) {
            Log.e(TAG, "截取 Bitmap 失败", e)
            callback(null)
        }
    }
}
