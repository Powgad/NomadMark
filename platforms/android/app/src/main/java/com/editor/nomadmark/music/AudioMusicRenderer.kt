package com.editor.nomadmark.music

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * 带音频播放支持的乐谱渲染器
 *
 * 与 WebViewMusicRenderer 不同，这个渲染器会保持 WebView 存活，
 * 直接在 WebView 中显示乐谱并支持音频播放交互。
 */
class AudioMusicRenderer(private val context: Context) {

    companion object {
        private const val TAG = "AudioMusicRenderer"
    }

    private val handler = Handler(Looper.getMainLooper())

    /** 存活的 WebView，key 为乐谱 ID */
    private val activeWebViews = mutableMapOf<String, WebView>()

    /**
     * 渲染带音频播放的乐谱
     *
     * @param musicData 乐谱数据
     * @param width 渲染宽度
     * @param container 父容器（用于放置 WebView）
     * @param onComplete 渲染完成回调
     */
    fun renderWithAudio(
        musicData: MusicData,
        width: Int,
        container: ViewGroup,
        onComplete: ((success: Boolean) -> Unit)? = null
    ) {
        val musicId = musicData.id

        // 如果已经存在 WebView，先清理
        activeWebViews[musicId]?.let {
            container.removeView(it)
            it.destroy()
        }

        // 创建新的 WebView
        val webView = createWebView(context, width)
        container.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        activeWebViews[musicId] = webView

        // 生成 HTML
        val html = generateAudioHtml(musicData, width)

        Log.d(TAG, "开始加载 WebView: ${musicData.title ?: musicData.id}, width=$width, html长度=${html.length}")

        // 设置 WebViewClient
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // 延迟获取高度，等待渲染完成
                webView.postDelayed({
                    val contentHeight = webView.contentHeight
                    val height = webView.height
                    Log.d(TAG, "乐谱加载完成: ${musicData.title ?: musicData.id}, contentHeight=$contentHeight, viewHeight=$height")
                    onComplete?.invoke(true)
                }, 500)
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

        // 加载 HTML
        webView.loadDataWithBaseURL(
            "file:///android_asset/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    /**
     * 停止指定乐谱的音频播放
     */
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

    /**
     * 停止所有音频播放
     */
    fun stopAllPlayback() {
        activeWebViews.keys.forEach { musicId ->
            stopPlayback(musicId)
        }
    }

    /**
     * 清理指定乐谱的 WebView
     */
    fun cleanup(musicId: String) {
        activeWebViews[musicId]?.let {
            it.destroy()
        }
        activeWebViews.remove(musicId)
    }

    /**
     * 清理所有 WebView
     */
    fun cleanupAll() {
        stopAllPlayback()
        activeWebViews.values.forEach { it.destroy() }
        activeWebViews.clear()
    }

    /**
     * 创建 WebView
     */
    private fun createWebView(context: Context, width: Int): WebView {
        return WebView(context).apply {
            // 设置初始最小高度，避免内容未加载时高度为0
            val minHeightPx = (300 * context.resources.displayMetrics.density).toInt()
            minimumHeight = minHeightPx

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

            settings.apply {
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                // 启用音频
                mediaPlaybackRequiresUserGesture = false
            }

            // 禁用滚动
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            // 白背景
            setBackgroundColor(android.graphics.Color.WHITE)

            // 启用硬件加速
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
    }

    /**
     * 生成带音频播放的 HTML
     */
    private fun generateAudioHtml(musicData: MusicData, width: Int): String {
        // 【简谱转换】
        val contentToRender = if (musicData.type == MusicType.JIANPU) {
            val conversionResult = JianpuConverter.convert(musicData.content, musicData.id)
            conversionResult.abc
        } else {
            musicData.content
        }

        val contentWithDirectives = "%%wrap\n%%staffsep 24\n" + contentToRender

        // 转义内容
        val escapedContent = contentWithDirectives
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("'", "\\'")

        val horizontalPadding = 30
        val staffWidth = (width - horizontalPadding).toInt().coerceAtLeast(850)

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    background: #FFFFFF;
                    padding: 10px;
                    overflow-x: hidden;
                    -webkit-tap-highlight-color: transparent;
                }
                /* 调试信息 - 墨水屏黑白样式 */
                .debug-info {
                    background: #000000;
                    color: #FFFFFF;
                    padding: 10px;
                    margin-bottom: 10px;
                    font-size: 16px;
                    font-weight: bold;
                    border: 2px solid #000000;
                    text-align: center;
                }
                #paper {
                    width: 100%;
                    max-width: 100%;
                    overflow-x: auto;
                    cursor: pointer;
                    background: #FFFFFF;
                    border: 3px solid #000000;  /* 粗黑边框，墨水屏可见 */
                    padding: 10px;
                }
                .abcjs-play { display: none !important; }
                .abcjs-title {
                    text-anchor: middle;
                    font-weight: bold;
                }
                .abcjs-composer {
                    text-anchor: end;
                }
                .abcjs-row {
                    margin-bottom: 20px;
                }
                #paper svg {
                    width: 100% !important;
                    height: auto;
                    display: block;
                    margin: 0;
                }

                /* 墨水屏高亮样式 */
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
                .abcjs-controls {
                    display: none !important;
                }
            </style>
            <script src="file:///android_asset/abcjs/abcjs-basic-min.js"></script>
        </head>
        <body>
            <div id="audio-status" class="abc-audio-status">♪ 播放中...</div>
            <div class="debug-info">🎵 音频模式已加载 - 如果看到此消息说明 WebView 正常工作</div>
            <div id="paper"></div>
            <div id="audio-status" class="abc-audio-status">♪ 播放中...</div>

            <script>
            (function() {
                class NoteHighlighter {
                    constructor(container) {
                        this.container = container;
                        this.currentElements = [];
                    }
                    onStart() {
                        this.clearHighlights();
                    }
                    onEvent(ev) {
                        if (ev.measureStart && ev.left === null) return;
                        this.clearHighlights();
                        const highlighted = this.container.querySelectorAll('.abcjs-highlight');
                        highlighted.forEach(el => {
                            if (el instanceof SVGElement) {
                                this.currentElements.push(el);
                            }
                        });
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

                const abcCode = "$escapedContent";
                let visualObj = null;
                let midiBuffer = null;
                let synthCtrl = null;
                let lastClickTime = 0;
                const DOUBLE_CLICK_DELAY = 300;

                function getSoundFontUrl() {
                    // 优先使用在线音色（本地音色未包含）
                    const onlinePath = 'https://paulrosen.github.io/midi-js-soundfonts/FluidR3_GM/';
                    const localPath = 'file:///android_asset/soundfonts/FluidR3_GM/';
                    return onlinePath;
                }

                function initAudio() {
                    if (!ABCJS.synth) return;
                    if (midiBuffer || synthCtrl) return;

                    try {
                        const synth = ABCJS.synth;
                        if (!synth.supportsAudio()) {
                            console.warn('[ABC-AUDIO] Audio not supported');
                            return;
                        }

                        midiBuffer = new synth.CreateSynth();
                        synthCtrl = new synth.SynthController();

                        let controlsEl = document.getElementById('abcjs-controls');
                        if (!controlsEl) {
                            controlsEl = document.createElement('div');
                            controlsEl.id = 'abcjs-controls';
                            controlsEl.className = 'abcjs-controls';
                            document.body.appendChild(controlsEl);
                        }

                        const noteHighlighter = new NoteHighlighter(document.getElementById('paper'));
                        synthCtrl.load(controlsEl, noteHighlighter, {
                            displayLoop: false,
                            displayPlay: false,
                            displayProgress: false,
                            displayWarp: false
                        });

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
                            console.log('[ABC-AUDIO] Audio initialized');
                            synthCtrl.setTune(visualObj, false, {
                                audioContext: null
                            });
                        }).catch(err => {
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
                        // 使用 abcjs 正确的 API
                        if (typeof midiBuffer.isPlaying === 'function' && midiBuffer.isPlaying()) {
                            synthCtrl.pause();
                            console.log('[ABC-AUDIO] Paused');
                        } else if (typeof synthCtrl.pause === 'function') {
                            // 尝试播放
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

                if (typeof ABCJS === 'undefined') {
                    console.error('ABCJS not loaded');
                    document.body.innerHTML = '<div style="color:red;padding:20px;">ABCJS library failed to load</div>';
                } else {
                    console.log('[ABC-RENDER] ABCJS loaded, version:', ABCJS.version || 'unknown');
                    try {
                        const renderOutput = ABCJS.renderAbc("paper", abcCode, {
                            responsive: "resize",
                            staffwidth: $staffWidth,
                            paddingtop: 10,
                            paddingbottom: 10,
                            paddingleft: 15,
                            paddingright: 15,
                            showDecorations: true,
                            add_classes: true,
                            format: {
                                titlefont: "Times New Roman 16 bold",
                                composerfont: "Times New Roman 14",
                                tempofont: "Times New Roman 14",
                                titlemargin: 8,
                                infofont: "Times New Roman 14 italic"
                            }
                        });

                        visualObj = renderOutput[0];
                        console.log('[ABC-RENDER] Render complete');

                        // 延迟初始化音频
                        if (ABCJS.synth) {
                            setTimeout(() => {
                                console.log('[ABC-RENDER] Delayed audio init');
                                initAudio();
                            }, 500);
                        }

                        // 交互绑定
                        const paperEl = document.getElementById('paper');
                        paperEl.addEventListener('click', function(e) {
                            const now = Date.now();
                            const timeSinceLastClick = now - lastClickTime;

                            if (timeSinceLastClick < DOUBLE_CLICK_DELAY) {
                                clearTimeout(paperEl.clickTimer);
                                console.log('[ABC-AUDIO] Double click, restarting');
                                restartPlayback();
                            } else {
                                paperEl.clickTimer = setTimeout(function() {
                                    console.log('[ABC-AUDIO] Single click, toggling playback');
                                    togglePlayback();
                                }, DOUBLE_CLICK_DELAY);
                            }

                            lastClickTime = now;
                        });

                        // 生命周期清理
                        window.addEventListener('beforeunload', function() {
                            stopPlayback();
                        });

                        // 暴露给外部
                        window.abcAudioControl = {
                            play: () => synthCtrl && synthCtrl.play(),
                            pause: () => synthCtrl && synthCtrl.pause(),
                            restart: () => restartPlayback(),
                            stop: () => stopPlayback()
                        };

                    } catch(e) {
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
