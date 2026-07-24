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

            // 设置超时保护（10秒后强制执行）
            handler.postDelayed({
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
                                    MusicSheetCache.put(musicData.getCacheKey(), bitmap)
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
     * 从 SVG 字符串创建 Picture（使用 AndroidSVG）
     */
    private fun createPictureFromSvg(svgString: String, width: Int): Picture? {
        return try {
            val svg = SVG.getFromString(svgString)

            // 设置渲染宽度和高度
            val documentWidth = svg.documentWidth
            val documentHeight = svg.documentHeight

            if (documentWidth <= 0 || documentHeight <= 0) {
                // 如果 SVG 没有设置尺寸，使用默认值
                svg.setDocumentWidth(width.toFloat())
                svg.setDocumentHeight(400f)
            }

            // 创建 Picture 并渲染 SVG
            val picture = Picture()
            val canvas = picture.beginRecording(
                svg.documentWidth.toInt().coerceAtLeast(width),
                svg.documentHeight.toInt().coerceAtLeast(100)
            )
            canvas.drawColor(Color.WHITE)

            // 渲染 SVG 到 Canvas
            val renderWidth = svg.documentWidth.toInt().coerceAtLeast(width)
            val renderHeight = svg.documentHeight.toInt().coerceAtLeast(100)

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
            if (measuredHeight < 100) {
                measuredHeight = 400 // 使用更大的默认高度
                Log.w(TAG, "WebView 高度太小，使用默认高度: $measuredHeight")
            }

            webView.layout(0, 0, webView.measuredWidth, measuredHeight)

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

            // 检查 Bitmap 是否为空（全白）
            var hasContent = false
            val pixels = IntArray(Math.min(bitmap.width * bitmap.height, 10000))
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, Math.min(bitmap.height, 10000 / bitmap.width))
            for (pixel in pixels) {
                if (pixel != Color.WHITE) {
                    hasContent = true
                    break
                }
            }

            if (!hasContent) {
                Log.w(TAG, "Bitmap 渲染为空（全白）")
            }

            // 缓存 Bitmap
            MusicSheetCache.put(musicData.getCacheKey(), bitmap)

            Log.d(TAG, "渲染完成: ${bitmap.width}x${bitmap.height}, hasContent=$hasContent")
            callback(bitmap)

        } catch (e: Exception) {
            Log.e(TAG, "截取 Bitmap 失败", e)
            callback(null)
        }
    }
}
