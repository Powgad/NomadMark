package com.editor.nomadmark.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
        // 【去重】生成渲染键（包含方向信息）
        val renderKey = "${musicData.getCacheKey(width, context)}"

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
     * 生成 abcjs HTML（使用 SVG 输出 + 音频播放支持）
     */
    private fun generateAbcHtml(musicData: MusicData, width: Int, enableAudio: Boolean = true): String {
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

        // 添加 %% 指令（控制五线谱间距和宽度）
        val staffWidth = MusicRenderConfig.staffWidth(width, context)
        Log.d(TAG, "generateAbcHtml: width=$width, orientation=${MusicRenderConfig.getOrientationSuffix(context)}, staffWidth=$staffWidth")
        val contentWithDirectives = "%%staffsep 24\n%%staffwidth $staffWidth\n" + contentToRender

        // 转义内容中的特殊字符
        val escapedContent = contentWithDirectives
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("'", "\\'")

        // 使用文档定义的 body padding
        val bodyPadding = 10

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=$width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, target-densitydpi=device-dpi">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    background: white;
                    padding: ${bodyPadding}px;
                    overflow-x: hidden;
                    -webkit-tap-highlight-color: transparent;
                }
                /* 固定逻辑宽度，与音频覆盖层同一 staffwidth 排版 */
                #paper {
                    width: 100%;
                    max-width: 100%;
                    overflow-x: hidden;
                    cursor: pointer;
                }
                .abcjs-play { display: none !important; }
                /* 让 abcjs 自己控制标题/作者位置，禁止被外部居中/对齐规则干扰 */
                .abcjs-title, .abcjs-composer, .abcjs-tempo {
                    text-anchor: initial;
                }
                .abcjs-title {
                    text-anchor: middle;
                    font-weight: bold;
                }
                .abcjs-composer {
                    text-anchor: end;
                }
                /* 增加乐谱各部分之间的间距 */
                .abcjs-row {
                    margin-bottom: 20px;
                }
                /* 保持 SVG 像素尺寸，与提取/AndroidSVG 路径一致，勿强制 100% */
                #paper svg {
                    display: block;
                    margin: 0;
                    max-width: none !important;
                }

                /* ==================================================================== */
                /* Supernote 墨水屏专用高亮样式 */
                /* ==================================================================== */

                /* 禁止所有过渡动画，墨水屏刷新会残影 */
                #paper svg path,
                #paper svg g,
                #paper svg rect {
                    transition: none !important;
                    animation: none !important;
                }

                /* 更通用的高亮样式 - 任何有 abcjs-highlight 类的元素 */
                #paper svg .abcjs-highlight {
                    fill: #ffffff !important;
                    stroke: #000000 !important;
                    stroke-width: 1.2 !important;
                }

                #paper svg .abcjs-highlight path,
                #paper svg .abcjs-highlight ellipse,
                #paper svg .abcjs-highlight circle {
                    fill: #ffffff !important;
                    stroke: #000000 !important;
                }

                /* 高亮时符干也反色，避免灰干配白头看不清 */
                #paper svg path.abcjs-stem.abcjs-highlight,
                #paper svg .abcjs-beam.abcjs-highlight path {
                    stroke: #000000 !important;
                }

                /* 高亮时的和弦符号 */
                #paper svg text.abcjs-chord.abcjs-highlight {
                    fill: #000000 !important;
                }

                /* 播放状态指示器 */
                .abc-audio-status {
                    position: fixed;
                    top: 20px;
                    right: 20px;
                    background: rgba(0, 0, 0, 0.7);
                    color: white;
                    padding: 8px 12px;
                    border-radius: 4px;
                    font-size: 14px;
                    z-index: 1000;
                    display: none;
                }
                .abc-audio-status.playing {
                    display: block;
                }

                /* 隐藏的 controls 容器（abcjs synth 要求但不需要显示） */
                .abcjs-controls {
                    display: none !important;
                }
            </style>
            <script src="file:///android_asset/abcjs/abcjs-basic-min.js"></script>
        </head>
        <body>
            <div id="paper"></div>
            <div id="audio-status" class="abc-audio-status">♪ 播放中...</div>

            <script>
            (function() {
                // ====================================================================
                // NoteHighlighter - 实现 CursorControl 接口用于视觉高亮
                // ====================================================================
                class NoteHighlighter {
                    constructor(container) {
                        this.container = container;
                        this.currentElements = [];
                    }

                    onStart() {
                        this.clearHighlights();
                    }

                    onEvent(ev) {
                        // 调试：输出事件信息
                        console.log('[ABC-EVENT] onEvent called:', ev ? JSON.stringify({
                            type: ev.type,
                            milliseconds: ev.milliseconds,
                            elementsCount: ev.elements ? ev.elements.length : 0,
                            left: ev.left,
                            top: ev.top,
                            height: ev.height,
                            startChar: ev.startChar
                        }) : 'null');

                        // 官方推荐方式：直接使用 ev.elements 数组
                        this.clearHighlights();

                        if (ev && ev.elements && ev.elements.length > 0) {
                            console.log('[ABC-EVENT] Processing', ev.elements.length, 'element groups');
                            for (var i = 0; i < ev.elements.length; i++) {
                                var note = ev.elements[i];
                                // note 可能是数组（多声部）或单个元素
                                var elements = Array.isArray(note) ? note : [note];
                                console.log('[ABC-EVENT] Element group', i, 'has', elements.length, 'elements');
                                for (var j = 0; j < elements.length; j++) {
                                    if (elements[j] && !elements[j].classList.contains('abcjs-highlight')) {
                                        elements[j].classList.add('abcjs-highlight');
                                        this.currentElements.push(elements[j]);
                                        console.log('[ABC-EVENT] Added highlight to element', j);
                                    }
                                }
                            }
                        } else {
                            console.warn('[ABC-EVENT] No elements to highlight! ev.elements:', ev ? ev.elements : 'ev is null/undefined');
                        }
                    }

                    onFinished() {
                        this.clearHighlights();
                        document.getElementById('audio-status').classList.remove('playing');
                    }

                    clearHighlights() {
                        this.currentElements.forEach(el => {
                            el.classList.remove('abcjs-highlight');
                        });
                        this.currentElements = [];
                    }
                }

                // ====================================================================
                // 音频播放初始化
                // ====================================================================
                const abcCode = "$escapedContent";
                const enableAudio = ${if (enableAudio) "true" else "false"};

                let visualObj = null;
                let midiBuffer = null;
                let synthCtrl = null;
                let clickCount = 0;
                let clickTimer = null;

                function getSoundFontUrl() {
                    // 使用在线音色包（本地 file:// 协议在 WebView 中受 CORS 限制）
                    return 'https://paulrosen.github.io/midi-js-soundfonts/FluidR3_GM/';
                }

                function initAudio() {
                    if (!enableAudio || !ABCJS.synth) {
                        console.log('[ABC-AUDIO] Audio not enabled or synth not available');
                        return;
                    }

                    try {
                        const synth = ABCJS.synth;
                        if (!synth.supportsAudio()) {
                            console.warn('[ABC-AUDIO] Audio not supported');
                            return;
                        }

                        // 调试：检查 visualObj 状态
                        console.log('[ABC-AUDIO] initAudio called, visualObj:', visualObj);
                        console.log('[ABC-AUDIO] visualObj.engraver exists:', !!visualObj.engraver);
                        if (visualObj.engraver) {
                            console.log('[ABC-AUDIO] engraver.staffgroups:', visualObj.engraver.staffgroups);
                            console.log('[ABC-AUDIO] staffgroups length:', visualObj.engraver.staffgroups ? visualObj.engraver.staffgroups.length : 'N/A');
                        }

                        // 清理旧的实例（如果存在）
                        if (synthCtrl) {
                            try { synthCtrl.destroy(); } catch(e) { console.warn('Destroy synthCtrl error:', e); }
                            synthCtrl = null;
                        }
                        if (midiBuffer) {
                            try { midiBuffer.stop(); } catch(e) { console.warn('Stop midiBuffer error:', e); }
                            midiBuffer = null;
                        }

                        // 创建新实例
                        midiBuffer = new synth.CreateSynth();

                        // 创建隐藏的 controls 容器
                        let controlsEl = document.getElementById('abcjs-controls');
                        if (!controlsEl) {
                            controlsEl = document.createElement('div');
                            controlsEl.id = 'abcjs-controls';
                            controlsEl.className = 'abcjs-controls';
                            document.body.appendChild(controlsEl);
                        }

                        // 创建 SynthController（如果还没创建）
                        if (!synthCtrl) {
                            synthCtrl = new synth.SynthController();
                            const noteHighlighter = new NoteHighlighter(document.getElementById('paper'));
                            synthCtrl.load(controlsEl, noteHighlighter, {
                                displayLoop: false,
                                displayPlay: false,
                                displayProgress: false,
                                displayWarp: false
                            });
                        }

                        console.log('[ABC-AUDIO] Starting audio init for visualObj');

                        // 初始化音频
                        const audioOptions = {
                            soundFontUrl: getSoundFontUrl(),
                            soundFontVolumeMultiplier: 3.0,
                            fadeLength: 200,
                            startTime: 0,
                            endTime: 0,
                            transposition: 0,
                            program: 0
                        };

                        midiBuffer.init({
                            visualObj: visualObj,
                            options: audioOptions,
                            audioContext: null
                        }).then(() => {
                            console.log('[ABC-AUDIO] CreateSynth init successful');
                            // 设置曲谱到 SynthController
                            if (synthCtrl) {
                                // userAction=true 确保 TimingCallbacks 被创建
                                synthCtrl.setTune(visualObj, true).then(function(response) {
                                    console.log('[ABC-AUDIO] setTune successful');
                                    console.log('[ABC-AUDIO] noteTimings length:',
                                        visualObj.noteTimings ? visualObj.noteTimings.length : 'null');
                                    if (visualObj.noteTimings && visualObj.noteTimings.length > 0) {
                                        console.log('[ABC-AUDIO] First noteTiming:', visualObj.noteTimings[0]);
                                    }
                                }).catch(function(err) {
                                    console.warn('[ABC-AUDIO] setTune failed:', err);
                                });
                            }
                        }).catch(err => {
                            console.warn('[ABC-AUDIO] Init failed:', err);
                        });

                    } catch (e) {
                        console.error('[ABC-AUDIO] Init error:', e);
                    }
                }

                function togglePlayback() {
                    if (!midiBuffer || !synthCtrl) {
                        console.log('[ABC-AUDIO] Not initialized, initializing...');
                        initAudio();
                        return;
                    }

                    try {
                        const isRunning = midiBuffer.isRunning();
                        if (isRunning) {
                            synthCtrl.pause();
                            console.log('[ABC-AUDIO] Paused');
                        } else {
                            synthCtrl.play();
                            console.log('[ABC-AUDIO] Playing');
                            document.getElementById('audio-status').classList.add('playing');
                        }
                    } catch (e) {
                        console.error('[ABC-AUDIO] Play/Pause error:', e);
                    }
                }

                function restartPlayback() {
                    if (!synthCtrl) {
                        initAudio();
                        return;
                    }

                    try {
                        synthCtrl.restart();
                        console.log('[ABC-AUDIO] Restarted');
                        document.getElementById('audio-status').classList.add('playing');
                    } catch (e) {
                        console.error('[ABC-AUDIO] Restart error:', e);
                    }
                }

                function stopPlayback() {
                    if (synthCtrl) {
                        try {
                            synthCtrl.pause();
                            console.log('[ABC-AUDIO] Stopped');
                        } catch (e) {
                            console.error('[ABC-AUDIO] Stop error:', e);
                        }
                    }
                }

                // ====================================================================
                // 渲染乐谱
                // ====================================================================
                if (typeof ABCJS === 'undefined') {
                    console.error('ABCJS not loaded');
                    document.body.innerHTML = '<div style="color:red;padding:20px;">ABCJS library failed to load</div>';
                } else {
                    try {
                        console.log('[ABC-RENDER] About to render ABC code');

                        // 使用文档定义的核心渲染参数 + add_classes（音符跳动需要）
                        const renderOutput = ABCJS.renderAbc("paper", abcCode, {
                            add_classes: true,
                            responsive: "resize",
                            viewportHorizontal: true
                        });

                        visualObj = renderOutput[0];
                        console.log('[ABC-RENDER] Render complete, visualObj obtained');

                        // 延迟初始化音频（不阻塞渲染）
                        if (enableAudio && ABCJS.synth) {
                            setTimeout(() => {
                                console.log('[ABC-RENDER] Delayed audio init starting...');
                                initAudio();
                            }, 500);
                        }

                        // 获取所有 SVG 内容并合并（用于 Bitmap 渲染）
                        setTimeout(function() {
                            var svgs = document.querySelectorAll('#paper svg');
                            console.log('[ABC-RENDER] Found', svgs.length, 'SVG elements');

                            if (svgs.length === 0) {
                                console.error('[ABC-RENDER] No SVG found');
                                window.SVG_ERROR = 'No SVG element found';
                                window.ABCJS_SVG_RESULT = '';
                            } else if (svgs.length === 1) {
                                // 即使是单个 SVG，也要确保有明确的像素高度
                                var svg = svgs[0];
                                var w = 0, h = 0;
                                var viewBox = svg.getAttribute('viewBox');
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
                                // 序列化并添加明确的 width/height 属性
                                var svgString = new XMLSerializer().serializeToString(svg);
                                // 移除现有的 width/height 属性，添加像素值
                                svgString = svgString.replace(/<svg/, '<svg').replace(/\s*width\s*=\s*["'][^"']*["']/g, '').replace(/\s*height\s*=\s*["'][^"']*["']/g, '');
                                svgString = svgString.replace('<svg', '<svg width=\"' + w + '\" height=\"' + h + '\"');
                                window.ABCJS_SVG_RESULT = svgString;
                                console.log('[ABC-RENDER] Single SVG extracted, size:', w, 'x', h, ', length:', svgString.length);
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
                                console.log('[ABC-RENDER] Merged SVG created, length:', mergedSvg.length);
                            }

                            window.renderComplete = true;
                        }, 300);

                        // ====================================================================
                        // 交互绑定：单击播放/暂停，双击重播
                        // ====================================================================
                        const paperEl = document.getElementById('paper');
                        let lastClickTime = 0;
                        const DOUBLE_CLICK_DELAY = 300;

                        paperEl.addEventListener('click', function(e) {
                            const now = Date.now();
                            const timeSinceLastClick = now - lastClickTime;

                            if (timeSinceLastClick < DOUBLE_CLICK_DELAY) {
                                // 双击：重播
                                clickCount = 2;
                                clearTimeout(clickTimer);
                                console.log('[ABC-AUDIO] Double click detected, restarting');
                                restartPlayback();
                            } else {
                                // 单击：等待延迟确认不是双击
                                clickCount = 1;
                                clickTimer = setTimeout(function() {
                                    if (clickCount === 1) {
                                        console.log('[ABC-AUDIO] Single click detected, toggling playback');
                                        togglePlayback();
                                    }
                                }, DOUBLE_CLICK_DELAY);
                            }

                            lastClickTime = now;
                        });

                        // 生命周期清理：页面卸载时停止播放
                        window.addEventListener('beforeunload', function() {
                            stopPlayback();
                        });

                        // 暴露给外部调用的接口
                        window.abcAudioControl = {
                            play: () => synthCtrl && synthCtrl.play(),
                            pause: () => synthCtrl && synthCtrl.pause(),
                            restart: () => restartPlayback(),
                            stop: () => stopPlayback()
                        };

                    } catch(e) {
                        console.error('[ABC-RENDER] Rendering error:', e);
                        document.body.innerHTML = '<div style="color:red;padding:20px;">Error: ' + e.message + '</div>';
                        window.renderError = e.message;
                    }
                }
            })();
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
                            // Chromium 会对 < 等做 \u003C 编码；必须用 JSON 解码，否则 AndroidSVG 解析失败
                            val svgString = decodeEvaluateJavascriptString(svgResult)

                            Log.d(TAG, "获取到 SVG，长度: ${svgString.length}")
                            Log.d(TAG, "SVG 开头: ${svgString.take(100)}")

                            // 清理 SVG 字符串，移除 AndroidSVG 不支持的属性
                            val cleanedSvg = svgString
                                .replace(Regex("""role="[^"]*""""), "")
                                .replace(Regex("""aria-[a-z]+="[^"]*""""), "")
                                .replace(Regex("""class="[^"]*""""), "")
                                .replace(Regex("""data-[a-z-]+="[^"]*""""), "")

                            Log.d(TAG, "清理后 SVG 长度: ${cleanedSvg.length}")

                            if (cleanedSvg.isEmpty() || !cleanedSvg.contains("<svg")) {
                                Log.w(TAG, "SVG 字符串无效（空或未解码），使用备用方法")
                                captureBitmapFallback(webView, musicData, width, callback) {
                                    onDestroy()
                                }
                                return@evaluateJavascript
                            }

                            // 第四步：创建 Picture 并绘制 SVG
                            val picture = createPictureFromSvg(cleanedSvg, width, context)
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
     * 解码 WebView.evaluateJavascript 返回的字符串结果。
     *
     * Chromium 会把返回值编成 JSON，并把 `<` 写成 `\u003C` 以防 XSS。
     * 若只剥外层引号而不解 `\uXXXX`，AndroidSVG 会报 Unexpected token。
     */
    private fun decodeEvaluateJavascriptString(result: String?): String {
        if (result.isNullOrBlank() || result == "null") return ""
        return try {
            when (val value = org.json.JSONTokener(result).nextValue()) {
                is String -> value
                else -> value?.toString() ?: ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "JSONTokener 解码失败，回退手动 \\u 解码", e)
            decodeJsonStringEscapesManually(result)
        }
    }

    private fun decodeJsonStringEscapesManually(raw: String): String {
        var input = raw
        if (input.length >= 2 && input.startsWith("\"") && input.endsWith("\"")) {
            input = input.substring(1, input.length - 1)
        }
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                when (val next = input[i + 1]) {
                    'u' -> if (i + 5 < input.length) {
                        val hex = input.substring(i + 2, i + 6)
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            sb.append(code.toChar())
                            i += 6
                            continue
                        }
                    }
                    'n' -> { sb.append('\n'); i += 2; continue }
                    'r' -> { sb.append('\r'); i += 2; continue }
                    't' -> { sb.append('\t'); i += 2; continue }
                    '"', '\\', '/' -> { sb.append(next); i += 2; continue }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
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
    private fun createPictureFromSvg(svgString: String, width: Int, context: Context): Picture? {
        return try {
            val svg = SVG.getFromString(svgString)

            // 直接解析 SVG 的像素尺寸，避免 AndroidSVG 在百分比宽度下返回 -1 而误裁高度
            val size = parseSvgSize(svgString, width)
            svg.setDocumentWidth(size.width)
            if (size.height > 0) svg.setDocumentHeight(size.height)

            // 使用 SVG 真实像素尺寸
            val picWidth = size.width.toInt().coerceAtLeast(1)
            val picHeight = if (size.height > 0) size.height.toInt() else 400

            // 【非等比缩放】目标：最终宽不超过 logicWidth(=width)，避免预览右侧被裁切
            val horizontalScale = MusicRenderConfig.getHorizontalScale(context)
            val verticalScale = MusicRenderConfig.getVerticalScale(context)
            var drawHScale = horizontalScale
            var drawVScale = verticalScale
            var scaledPicWidth = (picWidth * drawHScale).toInt()
            var scaledPicHeight = (picHeight * drawVScale).toInt()

            Log.d(TAG, "createPictureFromSvg: picWidth=$picWidth, targetWidth=$width, horizontalScale=$horizontalScale, scaledPicWidth=$scaledPicWidth")

            // 安全钳制：staffwidth 已按 HORIZONTAL_SCALE 反算，仍超出则压到可用宽度
            if (width > 0 && scaledPicWidth > width && picWidth > 0) {
                Log.w(TAG, "静态乐谱超宽，需要钳制: scaledPicWidth=$scaledPicWidth > targetWidth=$width, horizontalScale=$horizontalScale, svgW=$picWidth")
                drawHScale = width.toFloat() / picWidth
                scaledPicWidth = width
                Log.w(TAG, "静态乐谱超宽，横向缩放钳制为 $drawHScale (targetWidth=$width, svgW=$picWidth)")
            }

            val picture = Picture()
            val canvas = picture.beginRecording(scaledPicWidth, scaledPicHeight)
            canvas.drawColor(Color.WHITE)
            canvas.scale(drawHScale, drawVScale)
            svg.renderToCanvas(canvas)
            picture.endRecording()

            val svgCount = svgString.split("<svg").size - 1
            Log.d(TAG, "SVG 渲染成功: parsed=${size.width}x${size.height}, androidSvg=(${svg.documentWidth}x${svg.documentHeight}), pic=${picWidth}x${picHeight}, scaled=${scaledPicWidth}x${scaledPicHeight}, drawScale=${drawHScale}x${drawVScale}, svgCount=$svgCount, len=${svgString.length}")
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
        context: Context = this.context,
        onComplete: () -> Unit = {}
    ) {
        try {
            // 强制 WebView 重新测量和布局
            webView.forceLayout()

            val specWidth = android.view.View.MeasureSpec.makeMeasureSpec(
                width,
                android.view.View.MeasureSpec.EXACTLY
            )
            val specHeight = android.view.View.MeasureSpec.makeMeasureSpec(
                0,
                android.view.View.MeasureSpec.UNSPECIFIED
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

                // 【非等比缩放】最终宽不超过 logicWidth(=width)，避免预览右侧被裁切
                val horizontalScale = MusicRenderConfig.getHorizontalScale(context)
                val verticalScale = MusicRenderConfig.getVerticalScale(context)
                val baseW = webView.measuredWidth.coerceAtLeast(1)
                var drawHScale = horizontalScale
                val drawVScale = verticalScale
                var scaledFinalWidth = (baseW * drawHScale).toInt()
                val scaledFinalHeight = (finalHeight * drawVScale).toInt()
                if (width > 0 && scaledFinalWidth > width) {
                    drawHScale = width.toFloat() / baseW
                    scaledFinalWidth = width
                    Log.w(TAG, "备用路径超宽，横向缩放钳制为 $drawHScale")
                }

                // 创建 Bitmap，使用拉伸后的尺寸
                val bitmap = Bitmap.createBitmap(
                    scaledFinalWidth,
                    scaledFinalHeight,
                    Bitmap.Config.ARGB_8888
                )

                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                // 非等比缩放绘制
                canvas.scale(drawHScale, drawVScale)

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
}
