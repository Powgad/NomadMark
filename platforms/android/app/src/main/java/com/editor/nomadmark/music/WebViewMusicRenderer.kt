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

    // 用于时间轴分析的 WebView 复用
    private var analysisWebView: WebView? = null

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
        // 【简谱转换】如果是简谱类型，先转换为 ABC 记谱法
        val contentToRender = if (musicData.type == MusicType.JIANPU) {
            Log.d(TAG, "检测到简谱类型，执行转换...")
            val conversionResult = JianpuConverter.convert(musicData.content, musicData.id)
            Log.d(TAG, "简谱转换完成: warnings=${conversionResult.warnings.size}")
            conversionResult.warnings.forEach { Log.w(TAG, "简谱转换警告: $it") }
            Log.d(TAG, "转换后的 ABC 代码:\n${conversionResult.abc}")
            conversionResult.abc
        } else {
            musicData.content
        }

        // 【Supernote 优化】添加 %% 指令，避免 abcjs 依赖容器猜测
        val contentWithDirectives = "%%staffwidth 900\n%%scale 0.82\n%%wrap\n%%staffsep 24\n" + contentToRender

        // 转义内容中的特殊字符
        val escapedContent = contentWithDirectives
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("'", "\\'")

        // Supernote 优化：锁死 900 宽度，避免容器探测不准
        val staffWidth = 900

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
                /* Supernote 容器固定宽度 */
                #paper {
                    width: 936px;
                    max-width: 100%;
                    overflow-x: auto;
                }
                .abcjs-play { display: none !important; }
                /* 标题左对齐，与乐谱左边齐平 */
                .abcjs-header {
                    margin-bottom: 60px !important;
                    text-align: left !important;
                    padding-left: 0 !important;
                    margin-left: 0 !important;
                }
                .abcjs-header,
                .abcjs-header > *,
                .abcjs-header *,
                .abcjs-title,
                .abcjs-composer,
                .abcjs-meta-top {
                    text-align: left !important;
                    margin-left: 0 !important;
                    padding-left: 0 !important;
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
                            var result = origRenderAbc.call(this, container, source, options);
                            // 渲染后立即修改标题对齐
                            setTimeout(function() {
                                var header = document.querySelector('.abcjs-header');
                                if (header) {
                                    header.style.textAlign = 'left';
                                    header.style.paddingLeft = '0';
                                    header.style.marginLeft = '0';
                                    var children = header.querySelectorAll('*');
                                    for (var i = 0; i < children.length; i++) {
                                        children[i].style.textAlign = 'left';
                                        children[i].style.paddingLeft = '0';
                                        children[i].style.marginLeft = '0';
                                    }
                                    console.log('[ABC-RENDER-DEBUG] Title aligned left after render');
                                }
                            }, 50);
                            return result;
                        };

                        const abcCode = "$escapedContent";
                        console.log('[ABC-RENDER-DEBUG] About to render ABC code:');
                        console.log('  - abcCode length:', abcCode.length);
                        console.log('  - abcCode lines:', abcCode.split('\\n').length);

                        ABCJS.renderAbc("paper", abcCode, {
                            responsive: "resize",
                            staffwidth: $staffWidth,
                            scale: 0.82,
                            paddingtop: 8,
                            paddingbottom: 8,
                            paddingleft: 6,
                            paddingright: 6,
                            showDecorations: true,
                            add_classes: true,
                            format: {
                                titlefont: "\"Times New Roman\", serif",
                                titlebox: true,
                                titlealign: "left"
                            }
                        });
                        console.log('[ABC-RENDER-DEBUG] renderAbc options: staffwidth=$staffWidth, scale=0.9, wrap enabled');

                        // 获取所有 SVG 内容并合并
                        setTimeout(function() {
                            // 强制标题左对齐（在 SVG 提取时执行）
                            var header = document.querySelector('.abcjs-header');
                            if (header) {
                                header.style.textAlign = 'left';
                                header.style.paddingLeft = '0';
                                header.style.marginLeft = '0';
                                var children = header.querySelectorAll('*');
                                for (var i = 0; i < children.length; i++) {
                                    children[i].style.textAlign = 'left';
                                }
                                console.log('[ABC-RENDER-DEBUG] Title aligned left');
                            }
                            var svgs = document.querySelectorAll('#paper svg');
                            console.log('[ABC-RENDER-DEBUG] Found', svgs.length, 'SVG elements');

                            if (svgs.length === 0) {
                                console.error('[ABC-RENDER-DEBUG] No SVG found');
                                window.SVG_ERROR = 'No SVG element found';
                                window.ABCJS_SVG_RESULT = '';
                            } else if (svgs.length === 1) {
                                // 单个 SVG，直接序列化
                                var svg = svgs[0];
                                var viewBox = svg.getAttribute('viewBox');
                                var width = svg.getAttribute('width');
                                var height = svg.getAttribute('height');
                                console.log('[ABC-RENDER-DEBUG] Single SVG - viewBox:', viewBox, 'width:', width, 'height:', height);
                                var svgString = new XMLSerializer().serializeToString(svg);
                                window.ABCJS_SVG_RESULT = svgString;
                                console.log('[ABC-RENDER-DEBUG] Single SVG extracted, length:', svgString.length);
                            } else {
                                // 多个 SVG，需要合并
                                var totalHeight = 0;
                                var maxWidth = 0;
                                var svgStrings = [];

                                // 计算总高度和最大宽度
                                for (var i = 0; i < svgs.length; i++) {
                                    var svg = svgs[i];
                                    var viewBox = svg.getAttribute('viewBox');
                                    var w = 0, h = 0;

                                    if (viewBox) {
                                        var parts = viewBox.trim().split(/\s+/);
                                        if (parts.length >= 4) {
                                            w = parseFloat(parts[2]) || 0;
                                            h = parseFloat(parts[3]) || 0;
                                        }
                                    }

                                    // 如果 viewBox 解析失败，尝试从属性获取
                                    if (w === 0) w = parseFloat(svg.getAttribute('width')) || 0;
                                    if (h === 0) h = parseFloat(svg.getAttribute('height')) || 0;

                                    // 如果还是失败，尝试从 BBox 获取
                                    if (w === 0 || h === 0) {
                                        var bbox = svg.getBBox();
                                        w = bbox.width || 0;
                                        h = bbox.height || 0;
                                    }

                                    console.log('[ABC-RENDER-DEBUG] SVG', i, 'dimensions:', w, 'x', h);
                                    totalHeight += h;
                                    if (w > maxWidth) maxWidth = w;
                                    svgStrings.push(new XMLSerializer().serializeToString(svg));
                                }

                                console.log('[ABC-RENDER-DEBUG] Merging', svgs.length, 'SVGs, totalHeight:', totalHeight, ', maxWidth:', maxWidth);

                                // 创建合并后的 SVG
                                var mergedSvg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + maxWidth + ' ' + totalHeight + '" width="' + maxWidth + '" height="' + totalHeight + '">';
                                var currentY = 0;

                                for (var i = 0; i < svgStrings.length; i++) {
                                    var svgStr = svgStrings[i];
                                    var h = 0;
                                    var viewBox = svgs[i].getAttribute('viewBox');
                                    if (viewBox) {
                                        var parts = viewBox.trim().split(/\s+/);
                                        if (parts.length >= 4) h = parseFloat(parts[3]) || 0;
                                    }
                                    if (h === 0) h = parseFloat(svgs[i].getAttribute('height')) || 0;
                                    if (h === 0) {
                                        var bbox = svgs[i].getBBox();
                                        h = bbox.height || 0;
                                    }

                                    // 提取原始 SVG 的内容（去掉 <svg> 标签）
                                    var contentMatch = svgStr.match(/<svg[^>]*>([\s\S]*)<\/svg>/);
                                    if (contentMatch) {
                                        var content = contentMatch[1];
                                        mergedSvg += '<g transform="translate(0, ' + currentY + ')">';
                                        mergedSvg += content;
                                        mergedSvg += '</g>';
                                        currentY += h;
                                        console.log('[ABC-RENDER-DEBUG] Merged SVG', i, 'at Y:', currentY - h, 'height:', h);
                                    } else {
                                        console.error('[ABC-RENDER-DEBUG] Failed to extract SVG content for index', i);
                                    }
                                }
                                mergedSvg += '</svg>';

                                window.ABCJS_SVG_RESULT = mergedSvg;
                                console.log('[ABC-RENDER-DEBUG] Merged SVG created, length:', mergedSvg.length);
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

                            // 清理 SVG 字符串，移除 AndroidSVG 不支持的属性
                            val cleanedSvg = svgString
                                .replace(Regex("""role="[^"]*""""), "")
                                .replace(Regex("""aria-[a-z]+="[^"]*""""), "")
                                .replace(Regex("""class="[^"]*""""), "")
                                .replace(Regex("""data-[a-z-]+="[^"]*""""), "")

                            Log.d(TAG, "清理后 SVG 长度: ${cleanedSvg.length}")

                            if (cleanedSvg.isEmpty()) {
                                Log.w(TAG, "SVG 字符串为空，使用备用方法")
                                captureBitmapFallback(webView, musicData, width, callback) {
                                    onDestroy()
                                }
                                return@evaluateJavascript
                            }

                            // 第四步：创建 Picture 并绘制 SVG
                            val picture = createPictureFromSvg(cleanedSvg, width)
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

            // 获取 SVG 元素的实际高度（通过 JavaScript）
            webView.evaluateJavascript("(function() { " +
                "var svg = document.querySelector('#paper svg'); " +
                "if (svg) { " +
                "var viewBox = svg.getAttribute('viewBox'); " +
                "if (viewBox) { " +
                "var parts = viewBox.trim().split(/\\s+/); " +
                "return parts[3] || '0'; " +
                "} " +
                "var height = svg.getAttribute('height'); " +
                "if (height) return height.replace('px', ''); " +
                "var bbox = svg.getBBox(); " +
                "return (bbox.height || 0).toString(); " +
                "} " +
                "return document.body.scrollHeight.toString(); " +
                "})()") { heightStr ->
                val svgHeight = heightStr?.toFloatOrNull()?.toInt() ?: measuredHeight
                val finalHeight = maxOf(measuredHeight, svgHeight, 400)
                Log.d(TAG, "SVG 计算高度: $svgHeight, 最终使用高度: $finalHeight")

                // 创建 Bitmap，使用实际内容高度
                val bitmap = Bitmap.createBitmap(
                    webView.measuredWidth,
                    finalHeight,
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "截取 Bitmap 失败", e)
            callback(null)
        }
    }

    // =========================================================================
    // 音符时间轴分析（用于视觉播放）
    // =========================================================================

    /**
     * 分析乐谱获取音符时间轴
     *
     * 使用 abcjs 的 MIDI 分析功能获取每个音符的时间位置和元素 ID
     *
     * @param musicData 乐谱数据
     * @param callback 回调函数，返回音符事件列表
     */
    fun analyzeNoteTimeline(musicData: MusicData, callback: (List<NoteEvent>) -> Unit) {
        Log.d(TAG, "开始分析音符时间轴: ${musicData.title ?: musicData.id}")

        // 复用或创建 WebView
        if (analysisWebView == null) {
            analysisWebView = WebView(context).apply {
                settings.javaScriptEnabled = true
            }
        }
        val webView = analysisWebView!!

        try {
            // 生成用于分析的 HTML
            val html = generateAnalysisHtml(musicData)

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // 页面加载完成后获取音符时间轴
                    handler.postDelayed({
                        extractNoteTimeline(webView, callback)
                    }, 800)
                }
            }

            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null
            )

        } catch (e: Exception) {
            Log.e(TAG, "分析音符时间轴失败", e)
            callback(emptyList())
        }
    }

    /**
     * 生成用于音符时间轴分析的 HTML
     */
    private fun generateAnalysisHtml(musicData: MusicData): String {
        // 【简谱转换】如果是简谱类型，先转换为 ABC 记谱法
        val contentToRender = if (musicData.type == MusicType.JIANPU) {
            val conversionResult = JianpuConverter.convert(musicData.content, musicData.id)
            conversionResult.abc
        } else {
            musicData.content
        }

        // 转义内容中的特殊字符
        val escapedContent = contentToRender
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("'", "\\'")

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <script src="file:///android_asset/abcjs/abcjs-basic-min.js"></script>
        </head>
        <body>
            <div id="paper" style="position:absolute;left:-9999px;visibility:hidden;"></div>
            <script>
                try {
                    const abcCode = "$escapedContent";
                    ABCJS.renderAbc("paper", abcCode, {
                        staffwidth: 800,
                        paddingtop: 15,
                        paddingbottom: 15,
                        paddingright: 30,
                        paddingleft: 30
                    });

                    // 提取音符时间轴和位置信息
                    function extractNoteTimeline() {
                        const noteEvents = [];
                        let currentTime = 0;
                        let noteIndex = 0;

                        // 遍历所有 SVG 中的音符元素
                        const svg = document.querySelector('#paper svg');
                        if (!svg) {
                            console.error('[NOTE-TIMELINE] 找不到 SVG 元素');
                            return '[]';
                        }

                        // 使用 getBoundingClientRect 获取实际渲染尺寸
                        const svgRect = svg.getBoundingClientRect();
                        const svgWidth = svgRect.width || 800;
                        const svgHeight = svgRect.height || 400;

                        console.log('[NOTE-TIMELINE] SVG 尺寸:', svgWidth, 'x', svgHeight);

                        // 获取所有音符路径（abcjs 生成的音符类名）
                        let notes = svg.querySelectorAll('[class*="abcjs-note"]');
                        console.log('[NOTE-TIMELINE] 找到音符元素:', notes.length);

                        if (notes.length === 0) {
                            notes = svg.querySelectorAll('.abcjs-midi-note');
                            console.log('[NOTE-TIMELINE] 使用备用选择器找到:', notes.length);
                        }

                        // 计算每个音符的时值
                        const tempo = ${musicData.tempo};
                        const msPerBeat = 60000 / tempo; // 每拍的毫秒数

                        notes.forEach(function(note, index) {
                            // 获取元素的唯一 ID
                            let elementId = note.id;
                            if (!elementId) {
                                const parent = note.closest('.abcjs-midi-note');
                                if (parent && parent.id) {
                                    elementId = parent.id;
                                } else {
                                    elementId = 'note-' + index;
                                }
                            }

                            // 获取音符位置（归一化到 0-1）
                            let noteX = 0, noteY = 0, noteW = 0.05, noteH = 0.05;
                            try {
                                const bbox = note.getBBox();
                                const svgBox = svg.getBoundingClientRect();

                                // 归一化坐标
                                noteX = bbox.x / svgWidth;
                                noteY = bbox.y / svgHeight;
                                noteW = bbox.width / svgWidth;
                                noteH = bbox.height / svgHeight;

                                console.log('[NOTE-POS] Note', index, 'bbox:', bbox.x, bbox.y, bbox.width, bbox.height, 'normalized:', noteX.toFixed(3), noteY.toFixed(3), noteW.toFixed(3), noteH.toFixed(3));
                            } catch(e) {
                                console.warn('[NOTE-POS] 无法获取音符位置:', e);
                            }

                            // 简单估算时间（基于顺序）
                            noteEvents.push({
                                time: Math.round(currentTime),
                                id: elementId,
                                index: noteIndex++,
                                x: parseFloat(noteX.toFixed(3)),
                                y: parseFloat(noteY.toFixed(3)),
                                w: parseFloat(noteW.toFixed(3)),
                                h: parseFloat(noteH.toFixed(3))
                            });

                            // 简单的时间递增（每拍约 500ms）
                            currentTime += msPerBeat / 2; // 假设八分音符
                        });

                        console.log('[NOTE-TIMELINE] 提取完成，共', noteEvents.length, '个音符事件');

                        return JSON.stringify(noteEvents);
                    }

                    window.noteTimelineResult = extractNoteTimeline();
                } catch(e) {
                    console.error('音符时间轴分析失败:', e);
                    window.noteTimelineError = e.message;
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * 从 WebView 中提取音符时间轴数据
     */
    private fun extractNoteTimeline(webView: WebView, callback: (List<NoteEvent>) -> Unit) {
        webView.evaluateJavascript(
            "(function() { return window.noteTimelineResult || '[]'; })();"
        ) { result ->
            try {
                // evaluateJavascript 返回 JSON 编码的字符串，需要解码
                val decoded = if (result.length > 2 && result.startsWith("\"") && result.endsWith("\"")) {
                    result.substring(1, result.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                } else {
                    result
                }

                Log.d(TAG, "音符时间轴原始数据: ${decoded.take(200)}")

                val noteEvents = NoteEvent.fromJsonArray(decoded)
                Log.d(TAG, "解析到 ${noteEvents.size} 个音符事件")
                callback(noteEvents)

            } catch (e: Exception) {
                Log.e(TAG, "解析音符时间轴失败", e)
                callback(emptyList())
            }
        }
    }

    // =========================================================================
    // 带高亮的渲染
    // =========================================================================

    /**
     * 渲染带高亮的乐谱 Bitmap
     *
     * @param musicData 乐谱数据
     * @param width 渲染宽度
     * @param highlightElementId 要高亮的音符元素 ID（可选）
     * @param callback 回调函数
     */
    fun renderWithHighlight(
        musicData: MusicData,
        width: Int,
        highlightElementId: String?,
        callback: (Bitmap?) -> Unit
    ) {
        val renderKey = "${musicData.getCacheKey(width)}_highlight_${highlightElementId ?: "none"}"

        // 检查缓存（高亮版本单独缓存）
        val cached = MusicSheetCache.get(renderKey)
        if (cached != null) {
            Log.d(TAG, "使用缓存的高亮乐谱图片: $highlightElementId")
            callback(cached)
            return
        }

        Log.d(TAG, "渲染高亮乐谱: highlightId=$highlightElementId")

        // 每次创建新的 WebView
        val webView = WebView(context)

        try {
            configureWebView(webView, width)

            // 生成带高亮的 HTML
            val html = generateAbcHtmlWithHighlight(musicData, width, highlightElementId)

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    handler.postDelayed({
                        captureBitmap(webView, musicData, width, callback = { bitmap ->
                            // 缓存高亮版本
                            if (bitmap != null) {
                                MusicSheetCache.put(renderKey, bitmap)
                            }
                            callback(bitmap)
                        }, onDestroy = {
                            webView.destroy()
                        })
                    }, 1200)
                }
            }

            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "UTF-8",
                null
            )

        } catch (e: Exception) {
            Log.e(TAG, "渲染高亮乐谱失败", e)
            callback(null)
            webView.destroy()
        }
    }

    /**
     * 生成带高亮的 abcjs HTML
     */
    private fun generateAbcHtmlWithHighlight(
        musicData: MusicData,
        width: Int,
        highlightElementId: String?
    ): String {
        // 【简谱转换】
        val contentToRender = if (musicData.type == MusicType.JIANPU) {
            val conversionResult = JianpuConverter.convert(musicData.content, musicData.id)
            conversionResult.abc
        } else {
            musicData.content
        }

        // 【Supernote 优化】添加 %% 指令
        val contentWithDirectives = "%%staffwidth 900\n%%scale 0.82\n%%wrap\n%%staffsep 24\n" + contentToRender

        // 转义内容中的特殊字符
        val escapedContent = contentWithDirectives
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("'", "\\'")

        // Supernote 优化：锁死 900 宽度
        val staffWidth = 900
        val highlightScript = if (highlightElementId != null) {
            """
            // 高亮指定音符（发光效果）
            setTimeout(function() {
                var elements = document.querySelectorAll('#$highlightElementId, [id*="$highlightElementId"]');
                elements.forEach(function(el) {
                    el.style.fill = '#4169E1'; // 矢车菊蓝
                    el.style.filter = 'drop-shadow(0 0 8px rgba(65, 105, 225, 0.8)) brightness(1.3)';
                    el.style.transition = 'all 0.2s ease';
                });
                console.log('[HIGHLIGHT] Applied highlight to: $highlightElementId, elements:', elements.length);
            }, 300);
            """.trimIndent()
        } else {
            ""
        }

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
                /* Supernote 容器固定宽度 */
                #paper {
                    width: 936px;
                    max-width: 100%;
                    overflow-x: auto;
                }
                .abcjs-play { display: none !important; }
                /* 标题左对齐 */
                .abcjs-header {
                    margin-bottom: 60px !important;
                    text-align: left !important;
                    padding-left: 0 !important;
                    margin-left: 0 !important;
                }
                .abcjs-header,
                .abcjs-header > *,
                .abcjs-header *,
                .abcjs-title,
                .abcjs-composer,
                .abcjs-meta-top {
                    text-align: left !important;
                    margin-left: 0 !important;
                    padding-left: 0 !important;
                }
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
                        const abcCode = "$escapedContent";
                        ABCJS.renderAbc("paper", abcCode, {
                            responsive: "resize",
                            staffwidth: $staffWidth,
                            scale: 0.82,
                            paddingtop: 8,
                            paddingbottom: 8,
                            paddingleft: 6,
                            paddingright: 6,
                            showDecorations: true,
                            add_classes: true,
                            format: {
                                titlefont: "\"Times New Roman\", serif",
                                titlebox: true,
                                titlealign: "left"
                            }
                        });

                        // 获取所有 SVG 内容
                        setTimeout(function() {
                            var svgs = document.querySelectorAll('#paper svg');
                            if (svgs.length === 0) {
                                window.ABCJS_SVG_RESULT = '';
                            } else if (svgs.length === 1) {
                                var svgString = new XMLSerializer().serializeToString(svgs[0]);
                                window.ABCJS_SVG_RESULT = svgString;
                            } else {
                                // 合并多个 SVG
                                var totalHeight = 0;
                                var maxWidth = 0;
                                var svgStrings = [];

                                for (var i = 0; i < svgs.length; i++) {
                                    var svg = svgs[i];
                                    var viewBox = svg.getAttribute('viewBox');
                                    var w = 0, h = 0;
                                    if (viewBox) {
                                        var parts = viewBox.trim().split(/\s+/);
                                        if (parts.length >= 4) {
                                            w = parseFloat(parts[2]) || 0;
                                            h = parseFloat(parts[3]) || 0;
                                        }
                                    }
                                    if (w === 0) w = parseFloat(svg.getAttribute('width')) || 0;
                                    if (h === 0) h = parseFloat(svg.getAttribute('height')) || 0;
                                    if (w === 0 || h === 0) {
                                        var bbox = svg.getBBox();
                                        w = bbox.width || 0;
                                        h = bbox.height || 0;
                                    }
                                    totalHeight += h;
                                    if (w > maxWidth) maxWidth = w;
                                    svgStrings.push(new XMLSerializer().serializeToString(svg));
                                }

                                var mergedSvg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + maxWidth + ' ' + totalHeight + '" width="' + maxWidth + '" height="' + totalHeight + '">';
                                var currentY = 0;

                                for (var i = 0; i < svgStrings.length; i++) {
                                    var svgStr = svgStrings[i];
                                    var h = 0;
                                    var viewBox = svgs[i].getAttribute('viewBox');
                                    if (viewBox) {
                                        var parts = viewBox.trim().split(/\s+/);
                                        if (parts.length >= 4) h = parseFloat(parts[3]) || 0;
                                    }
                                    if (h === 0) h = parseFloat(svgs[i].getAttribute('height')) || 0;
                                    if (h === 0) {
                                        var bbox = svgs[i].getBBox();
                                        h = bbox.height || 0;
                                    }

                                    var contentMatch = svgStr.match(/<svg[^>]*>([\s\S]*)<\/svg>/);
                                    if (contentMatch) {
                                        var content = contentMatch[1];
                                        mergedSvg += '<g transform="translate(0, ' + currentY + ')">';
                                        mergedSvg += content;
                                        mergedSvg += '</g>';
                                        currentY += h;
                                    }
                                }
                                mergedSvg += '</svg>';
                                window.ABCJS_SVG_RESULT = mergedSvg;
                            }

                            // 应用高亮效果
                            $highlightScript
                        }, 300);
                    } catch(e) {
                        console.error('ABC rendering error:', e);
                    }
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}
