package com.editor.nomadmark.music

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * 带音频播放支持的乐谱渲染器
 *
 * 覆盖层模式：WebView 固定为静态 Bitmap 的像素尺寸，abcjs 按与静态相同的逻辑宽度排版，
 * 再用 JS 把整份谱面非等比缩放到恰好填满容器（等效于静态的 Canvas.scale）。
 *
 * 注意：不要在覆盖层 HTML 里写死 device 像素宽高 + CSS transform——Android WebView
 * 的 CSS 像素受 density 影响，会导致谱面放大数倍并被裁切。
 */
class AudioMusicRenderer(private val context: Context) {

    companion object {
        private const val TAG = "AudioMusicRenderer"
    }

    private val handler = Handler(Looper.getMainLooper())

    private val activeWebViews = mutableMapOf<String, WebView>()
    private val playbackEndCallbacks = mutableMapOf<String, () -> Unit>()

    private inner class MusicJavaScriptInterface(private val musicId: String) {
        @JavascriptInterface
        fun onPlaybackEnded() {
            Log.d(TAG, "收到播放结束通知: $musicId")
            handler.post {
                playbackEndCallbacks[musicId]?.invoke()
                playbackEndCallbacks.remove(musicId)
            }
        }

        @JavascriptInterface
        fun onPlaybackStarted() {
            Log.d(TAG, "收到播放开始通知: $musicId")
        }
    }

    fun setPlaybackEndCallback(musicId: String, callback: () -> Unit) {
        playbackEndCallbacks[musicId] = callback
    }

    /**
     * @param scaledWidth 覆盖层宽度（= Bitmap 宽）；自适应模式为可用宽度
     * @param scaledHeight 覆盖层高度（= Bitmap 高）；0 = 自适应
     * @param logicWidth 与静态渲染相同的逻辑宽度（musicSheetWidth）；0 = 自动用 scaledWidth 反推
     */
    fun renderWithAudio(
        musicData: MusicData,
        scaledWidth: Int,
        scaledHeight: Int,
        container: ViewGroup,
        logicWidth: Int = 0,
        onComplete: ((success: Boolean) -> Unit)? = null,
        onPlaybackEnded: (() -> Unit)? = null
    ) {
        val musicId = musicData.id
        val overlayMode = scaledHeight > 0

        if (onPlaybackEnded != null) {
            playbackEndCallbacks[musicId] = onPlaybackEnded
        }

        activeWebViews[musicId]?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }

        val resolvedLogicWidth = when {
            logicWidth > 0 -> logicWidth
            overlayMode -> MusicRenderConfig.logicWidthFromScaled(scaledWidth)
            else -> scaledWidth.coerceAtLeast(1)
        }

        val webView = createWebView(overlayMode)
        webView.addJavascriptInterface(MusicJavaScriptInterface(musicId), "AndroidMusic")

        val lp = if (overlayMode) {
            FrameLayout.LayoutParams(scaledWidth, scaledHeight)
        } else {
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(webView, lp)
        activeWebViews[musicId] = webView

        val html = generateAudioHtml(musicData, scaledWidth, scaledHeight, resolvedLogicWidth)

        Log.d(
            TAG,
            "开始加载 WebView: ${musicData.title ?: musicData.id}, " +
                "scaled=${scaledWidth}x${scaledHeight}, logicWidth=$resolvedLogicWidth, " +
                "overlay=$overlayMode, density=${context.resources.displayMetrics.density}"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.postDelayed({
                    if (overlayMode) {
                        webView.layoutParams = FrameLayout.LayoutParams(scaledWidth, scaledHeight)
                        webView.measure(
                            android.view.View.MeasureSpec.makeMeasureSpec(
                                scaledWidth,
                                android.view.View.MeasureSpec.EXACTLY
                            ),
                            android.view.View.MeasureSpec.makeMeasureSpec(
                                scaledHeight,
                                android.view.View.MeasureSpec.EXACTLY
                            )
                        )
                        webView.layout(0, 0, scaledWidth, scaledHeight)
                        // 布局完成后再 fit 一次，避免 clientWidth 为 0
                        webView.evaluateJavascript("window.applyScale && window.applyScale();", null)
                    } else {
                        // 自适应模式：获取 SVG 的实际高度
                        webView.evaluateJavascript(
                            """
                            (function() {
                                var paper = document.getElementById('paper');
                                if (paper) {
                                    return paper.offsetHeight || 0;
                                }
                                return 0;
                            })();
                            """.trimIndent()
                        ) { heightStr ->
                            val height = heightStr?.toIntOrNull() ?: 0
                            if (height > 0) {
                                webView.layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    height
                                )
                                Log.d(TAG, "自适应模式设置高度: $height")
                            }
                        }
                    }
                    Log.d(
                        TAG,
                        "乐谱加载完成: ${musicData.title ?: musicData.id}, " +
                            "view=${webView.width}x${webView.height}, contentHeight=${webView.contentHeight}"
                    )
                    onComplete?.invoke(true)
                }, 400)
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                Log.e(TAG, "WebView 加载错误: ${error?.description}")
                onComplete?.invoke(false)
            }
        }

        webView.loadDataWithBaseURL(
            "file:///android_asset/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    fun stopPlayback(musicId: String) {
        val webView = activeWebViews[musicId] ?: return
        webView.evaluateJavascript(
            """
            (function() {
                if (window.abcAudioControl && typeof window.abcAudioControl.stop === 'function') {
                    window.abcAudioControl.stop();
                    return 'stopped';
                }
                return 'no_control';
            })();
            """.trimIndent()
        ) { result ->
            Log.d(TAG, "音频停止结果 [$musicId]: $result")
        }
    }

    fun stopAllPlayback() {
        activeWebViews.keys.forEach { stopPlayback(it) }
    }

    fun cleanup(musicId: String) {
        activeWebViews[musicId]?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        activeWebViews.remove(musicId)
        playbackEndCallbacks.remove(musicId)
    }

    fun cleanupAll() {
        stopAllPlayback()
        activeWebViews.values.forEach {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        activeWebViews.clear()
        playbackEndCallbacks.clear()
    }

    private fun createWebView(overlayMode: Boolean): WebView {
        return WebView(context).apply {
            if (!overlayMode) {
                minimumHeight = (300 * context.resources.displayMetrics.density).toInt()
            }

            settings.apply {
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = false
                loadWithOverviewMode = false
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
                textZoom = 100
            }

            // 覆盖层：固定 100% 缩放；谱面靠 applyScale fit 填满容器，避免 density 二次放大导致裁切
            if (overlayMode) {
                setInitialScale(100)
            }

            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY

            setBackgroundColor(
                if (overlayMode) android.graphics.Color.WHITE
                else android.graphics.Color.TRANSPARENT
            )

            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
    }

    private fun generateAudioHtml(
        musicData: MusicData,
        scaledWidth: Int,
        scaledHeight: Int,
        logicWidth: Int
    ): String {
        val overlayMode = scaledHeight > 0

        Log.d(
            TAG,
            "音频 HTML: scaled=${scaledWidth}x${scaledHeight}, logicWidth=$logicWidth, overlay=$overlayMode"
        )

        val contentToRender = if (musicData.type == MusicType.JIANPU) {
            JianpuConverter.convert(musicData.content, musicData.id).abc
        } else {
            musicData.content
        }

        val contentWithDirectives = "%%staffsep 24\n" + contentToRender

        val escapedContent = contentWithDirectives
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("'", "\\'")

        // 覆盖层：容器固定为 Bitmap 尺寸，谱面按自然尺寸渲染后再 fit 填满，禁止裁切丢内容
        val bodyPadding = MusicRenderConfig.BODY_PADDING_PX
        val overlayCss = if (overlayMode) {
            """
                html, body {
                    width: 100%;
                    height: 100%;
                    overflow: hidden !important;
                    background: #ffffff;
                    padding: 0 !important;
                }
                #scale-container {
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    position: relative;
                    background: #ffffff;
                }
                #paper {
                    position: absolute;
                    left: 0;
                    top: 0;
                    padding: 0;
                    box-sizing: border-box;
                    transform-origin: 0 0;
                    -webkit-transform-origin: 0 0;
                    cursor: pointer;
                    background: #ffffff;
                }
                #paper svg {
                    display: block;
                    margin: 0;
                    max-width: none !important;
                    width: auto !important;
                    height: auto !important;
                }
            """.trimIndent()
        } else {
            """
                html, body {
                    width: 100%;
                    background: transparent;
                    overflow-x: hidden;
                    padding: ${bodyPadding}px;
                }
                #scale-container {
                    width: 100%;
                    position: relative;
                }
                #paper {
                    cursor: pointer;
                    background: transparent;
                }
                #paper svg {
                    width: 100% !important;
                    height: auto !important;
                    display: block;
                    margin: 0;
                }
            """.trimIndent()
        }

        // 覆盖层 viewport = Bitmap 宽，与 WebView 布局像素对齐；谱面再用 fit 填满
        val viewportContent = if (overlayMode) {
            "width=$scaledWidth, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no, target-densitydpi=device-dpi"
        } else {
            "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"
        }

        val staffwidth = MusicRenderConfig.staffWidth(logicWidth)
        val abcPadding = MusicRenderConfig.ABC_PADDING
        val abcScale = MusicRenderConfig.ABC_SCALE

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="$viewportContent">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    margin: 0;
                    padding: 0;
                    -webkit-tap-highlight-color: transparent;
                }
                $overlayCss
                .abcjs-play { display: none !important; }
                .abcjs-title, .abcjs-composer, .abcjs-tempo { text-anchor: initial; }
                .abcjs-title { text-anchor: middle; font-weight: bold; }
                .abcjs-composer { text-anchor: end; }
                .abcjs-row { margin-bottom: 20px; }
                #paper svg path,
                #paper svg g,
                #paper svg rect {
                    transition: none !important;
                    animation: none !important;
                }
                #paper svg path.abcjs-note.abcjs-highlight,
                #paper svg path.abcjs-rest.abcjs-highlight {
                    fill: #ffffff !important;
                    stroke: #000000 !important;
                    stroke-width: 1.2 !important;
                }
                #paper svg path.abcjs-stem.abcjs-highlight,
                #paper svg .abcjs-beam.abcjs-highlight path {
                    stroke: #000000 !important;
                }
                .abc-audio-status {
                    position: fixed;
                    top: 8px;
                    right: 8px;
                    background: #000000;
                    color: #FFFFFF;
                    padding: 6px 10px;
                    border-radius: 4px;
                    font-size: 12px;
                    z-index: 1000;
                    display: none;
                }
                .abc-audio-status.playing { display: block; }
                .abcjs-controls { display: none !important; }
            </style>
            <script src="file:///android_asset/abcjs/abcjs-basic-min.js"></script>
        </head>
        <body>
            <div id="scale-container">
                <div id="paper"></div>
            </div>
            <div id="audio-status" class="abc-audio-status">♪ 播放中...</div>

            <script>
            (function() {
                var OVERLAY_MODE = ${if (overlayMode) "true" else "false"};
                var STAFF_WIDTH = $staffwidth;
                var LOGIC_WIDTH = $logicWidth;
                var SCALED_WIDTH = $scaledWidth;
                var SCALED_HEIGHT = $scaledHeight;
                var ABC_PADDING = $abcPadding;
                var ABC_SCALE = $abcScale;

                class NoteHighlighter {
                    constructor(container) {
                        this.container = container;
                        this.currentElements = [];
                    }
                    onStart() {
                        isPlaying = true;
                        this.clearHighlights();
                    }
                    onEvent(ev) {
                        if (ev.measureStart && ev.left === null) return;
                        this.clearHighlights();
                        if (ev.startChar !== undefined) {
                            var svg = this.container.querySelector('svg');
                            if (svg) {
                                var elements = svg.querySelectorAll('[data-start-char="' + ev.startChar + '"]');
                                elements.forEach(function(el) {
                                    if (el) {
                                        el.classList.add('abcjs-highlight');
                                        this.currentElements.push(el);
                                    }
                                }.bind(this));
                            }
                        }
                    }
                    onFinished() {
                        isPlaying = false;
                        this.clearHighlights();
                        var statusEl = document.getElementById('audio-status');
                        if (statusEl) statusEl.classList.remove('playing');
                        if (window.AndroidMusic && window.AndroidMusic.onPlaybackEnded) {
                            window.AndroidMusic.onPlaybackEnded();
                        }
                    }
                    clearHighlights() {
                        this.currentElements.forEach(function(el) {
                            if (el && el.classList) el.classList.remove('abcjs-highlight');
                        });
                        this.currentElements = [];
                    }
                }

                var abcCode = "$escapedContent";
                var visualObj = null;
                var midiBuffer = null;
                var synthCtrl = null;
                var timingCallbacks = null;
                var lastClickTime = 0;
                var DOUBLE_CLICK_DELAY = 300;
                var isPlaying = false;

                /**
                 * 覆盖层：把谱面非等比缩放到恰好填满容器（= 静态 Bitmap 尺寸）。
                 * 用 container.clientWidth/Height 与 SVG 自然尺寸求比例，不受 density 二次放大影响，
                 * 从而保证完整显示、不截断，并与静态图外框一致。
                 */
                function applyScale() {
                    if (!OVERLAY_MODE) return;

                    var container = document.getElementById('scale-container');
                    var paper = document.getElementById('paper');
                    if (!container || !paper) return;

                    var svgs = paper.querySelectorAll('svg');
                    if (!svgs.length) return;

                    var natW = 0;
                    var natH = 0;
                    for (var i = 0; i < svgs.length; i++) {
                        var svg = svgs[i];
                        var w = 0, h = 0;
                        var vb = svg.getAttribute('viewBox');
                        if (vb) {
                            var parts = vb.trim().split(/\s+/);
                            if (parts.length >= 4) {
                                w = parseFloat(parts[2]) || 0;
                                h = parseFloat(parts[3]) || 0;
                            }
                        }
                        if (w <= 0) {
                            w = parseFloat(svg.getAttribute('width')) || 0;
                        }
                        if (h <= 0) {
                            h = parseFloat(svg.getAttribute('height')) || 0;
                        }
                        if (w <= 0 || h <= 0) {
                            try {
                                var bbox = svg.getBBox();
                                if (w <= 0) w = bbox.width || 0;
                                if (h <= 0) h = bbox.height || 0;
                            } catch (e) {}
                        }
                        natW = Math.max(natW, w);
                        natH += h;
                        svg.style.width = w + 'px';
                        svg.style.height = h + 'px';
                    }

                    if (natW <= 0 || natH <= 0) return;

                    // 与静态 Bitmap 一致：只按 SVG 自然尺寸 fit，不含额外 HTML padding
                    paper.style.width = natW + 'px';
                    paper.style.height = natH + 'px';
                    paper.style.padding = '0';

                    // 优先用容器实际 CSS 尺寸（与 viewport 同一坐标系），避免 density 二次放大
                    var cw = container.clientWidth || SCALED_WIDTH || natW;
                    var ch = container.clientHeight || SCALED_HEIGHT || natH;
                    if (cw <= 0 || ch <= 0) return;

                    var sx = cw / natW;
                    var sy = ch / natH;
                    paper.style.transform = 'scale(' + sx + ',' + sy + ')';
                    paper.style.webkitTransform = 'scale(' + sx + ',' + sy + ')';

                    console.log('[ABC-FIT] overlay fit, natural=' + natW + 'x' + natH +
                        ' target=' + cw + 'x' + ch +
                        ' scale=' + sx.toFixed(3) + 'x' + sy.toFixed(3));
                }
                window.fitScoreToOverlay = applyScale;
                window.applyScale = applyScale;

                function getSoundFontUrl() {
                    return 'https://paulrosen.github.io/midi-js-soundfonts/FluidR3_GM/';
                }

                function initAudio() {
                    if (!ABCJS.synth) return;
                    if (midiBuffer || synthCtrl) return;
                    try {
                        var synth = ABCJS.synth;
                        if (!synth.supportsAudio()) return;

                        midiBuffer = new synth.CreateSynth();
                        synthCtrl = new synth.SynthController();

                        var controlsEl = document.getElementById('abcjs-controls');
                        if (!controlsEl) {
                            controlsEl = document.createElement('div');
                            controlsEl.id = 'abcjs-controls';
                            controlsEl.className = 'abcjs-controls';
                            document.body.appendChild(controlsEl);
                        }

                        var noteHighlighter = new NoteHighlighter(document.getElementById('paper'));
                        synthCtrl.load(controlsEl, noteHighlighter, {
                            displayLoop: false,
                            displayPlay: false,
                            displayProgress: false,
                            displayWarp: false
                        });

                        midiBuffer.init({
                            visualObj: visualObj,
                            options: {
                                soundFontUrl: getSoundFontUrl(),
                                soundFontVolumeMultiplier: 3.0,
                                fadeLength: 200
                            },
                            audioContext: null
                        }).then(function() {
                            synthCtrl.setTune(visualObj, false, { audioContext: null });
                            if (ABCJS.TimingCallbacks && !timingCallbacks) {
                                timingCallbacks = new ABCJS.TimingCallbacks(visualObj, null);
                                timingCallbacks.registerCallback(function(callbacks) {
                                    var container = document.getElementById('paper');
                                    container.querySelectorAll('.abcjs-highlight').forEach(function(el) {
                                        el.classList.remove('abcjs-highlight');
                                    });
                                    callbacks.notes.forEach(function(note) {
                                        if (note.element) note.element.classList.add('abcjs-highlight');
                                    });
                                });
                            }
                        }).catch(function(err) {
                            console.warn('[ABC-AUDIO] Init failed:', err);
                            midiBuffer = null;
                            synthCtrl = null;
                        });
                    } catch (e) {
                        console.error('[ABC-AUDIO] Init error:', e);
                    }
                }

                function togglePlayback() {
                    if (!midiBuffer || !synthCtrl) {
                        initAudio();
                        return;
                    }
                    try {
                        if (typeof midiBuffer.isPlaying === 'function' && midiBuffer.isPlaying()) {
                            synthCtrl.pause();
                            if (timingCallbacks) timingCallbacks.pause();
                        } else if (typeof synthCtrl.play === 'function') {
                            synthCtrl.play();
                            if (timingCallbacks) timingCallbacks.start();
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
                        if (timingCallbacks) {
                            timingCallbacks.stop();
                            timingCallbacks.start();
                        }
                        document.getElementById('audio-status').classList.add('playing');
                    } catch (e) {
                        console.error('[ABC-AUDIO] Restart error:', e);
                    }
                }

                function stopPlayback() {
                    if (!synthCtrl) return;
                    try {
                        synthCtrl.pause();
                        if (timingCallbacks) timingCallbacks.stop();
                        document.getElementById('paper')
                            .querySelectorAll('.abcjs-highlight')
                            .forEach(function(el) { el.classList.remove('abcjs-highlight'); });
                    } catch (e) {
                        console.error('[ABC-AUDIO] Stop error:', e);
                    }
                }

                if (typeof ABCJS === 'undefined') {
                    document.body.innerHTML = '<div style="color:red;padding:20px;">ABCJS library failed to load</div>';
                } else {
                    try {
                        // 与 WebViewMusicRenderer 使用相同的 staffwidth / padding，保证谱面排版一致
                        var renderOpts = {
                            staffwidth: STAFF_WIDTH,
                            responsive: false,
                            scale: ABC_SCALE,
                            paddingtop: ABC_PADDING,
                            paddingbottom: ABC_PADDING,
                            paddingleft: ABC_PADDING,
                            paddingright: ABC_PADDING
                        };
                        var renderOutput = ABCJS.renderAbc("paper", abcCode, renderOpts);
                        visualObj = renderOutput[0];

                        // 只在覆盖层模式下 fit 到 Bitmap 尺寸
                        if (OVERLAY_MODE) {
                            applyScale();
                            setTimeout(applyScale, 50);
                            setTimeout(applyScale, 200);
                        }

                        if (ABCJS.synth) {
                            setTimeout(initAudio, 500);
                        }

                        var paperEl = document.getElementById('paper');
                        paperEl.addEventListener('click', function() {
                            var now = Date.now();
                            if (now - lastClickTime < DOUBLE_CLICK_DELAY) {
                                clearTimeout(paperEl.clickTimer);
                                restartPlayback();
                            } else {
                                paperEl.clickTimer = setTimeout(togglePlayback, DOUBLE_CLICK_DELAY);
                            }
                            lastClickTime = now;
                        });

                        window.addEventListener('beforeunload', stopPlayback);
                        window.abcAudioControl = {
                            play: function() { if (synthCtrl) synthCtrl.play(); },
                            pause: function() { if (synthCtrl) synthCtrl.pause(); },
                            restart: restartPlayback,
                            stop: stopPlayback
                        };
                    } catch (e) {
                        console.error('[ABC-RENDER] Rendering error:', e);
                        document.body.innerHTML = '<div style="color:red;padding:20px;">Error: ' + e.message + '</div>';
                    }
                }
            })();
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}
