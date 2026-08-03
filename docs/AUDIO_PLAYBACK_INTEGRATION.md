# 乐谱音频播放功能集成技术文档

## 文档概述

本文档描述如何将 `AudioMusicRenderer` 集成到 NomadMark 应用中，实现完整的 ABC 乐谱音频播放功能。

**版本**: 1.0
**最后更新**: 2025-07-31
**状态**: 待实施

---

## 目录

1. [架构概览](#架构概览)
2. [现有组件分析](#现有组件分析)
3. [集成方案设计](#集成方案设计)
4. [详细实施步骤](#详细实施步骤)
5. [UI/UX 设计](#uiux-设计)
6. [技术细节](#技术细节)
7. [测试验证](#测试验证)
8. [故障排查](#故障排查)

---

## 架构概览

### 当前渲染架构

```
┌─────────────────────────────────────────────────────────────┐
│                    MarkdownEditorActivity                    │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           WebViewMusicRenderer (Bitmap 模式)          │  │
│  │                                                        │  │
│  │  1. 创建 WebView                                       │  │
│  │  2. 加载 abcjs 渲染乐谱                                │  │
│  │  3. 等待 1200ms                                       │  │
│  │  4. 捕获为 Bitmap                                      │  │
│  │  5. 销毁 WebView ❌                                   │  │
│  │                                                        │  │
│  │  └─ Bitmap → MusicSheetSpan → TextView                 │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              MusicSheetSpan (ReplacementSpan)         │  │
│  │  • 挂载到 TextView 中                                  │  │
│  │  • draw() 方法绘制 Bitmap                             │  │
│  │  • 支持点击检测（当前已禁用）                          │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 目标架构（双模式）

```
┌─────────────────────────────────────────────────────────────┐
│                    MarkdownEditorActivity                    │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              渲染模式切换器                          │    │
│  │  ○ 静态图片模式 (默认)  ● 音频播放模式               │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │    静态图片模式 │ 音频播放模式                      │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │                                                        │    │
│  │  WebViewMusicRenderer    │    AudioMusicRenderer      │    │
│  │  • Bitmap 渲染            │    • WebView 直接显示     │    │
│  │  • WebView 短暂存在      │    • WebView 保持存活     │    │
│  │  • 显示在 TextView        │    • 显示在容器中         │    │
│  │  • 适合大量乐谱          │    • 支持音频交互         │    │
│  │  • 内存占用低            │    • 需要 WebView 资源    │    │
│  │                                                        │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 现有组件分析

### WebViewMusicRenderer (已存在)

**位置**: `platforms/android/app/src/main/java/com/editor/nomadmark/music/WebViewMusicRenderer.kt`

**功能**:
- 渲染 ABC/简谱为 Bitmap
- 支持缓存机制
- 非等比缩放优化
- 简谱自动转换

**限制**:
- WebView 在渲染完成后销毁（~1200ms）
- 无法支持音频播放
- HTML 中虽有音频代码，但无法实际运行

### AudioMusicRenderer (新创建)

**位置**: `platforms/android/app/src/main/java/com/editor/nomadmark/music/AudioMusicRenderer.kt`

**功能**:
- 完整的 abcjs synth 音频播放支持
- NoteHighlighter 视觉高亮
- 单击播放/暂停，双击重播
- 墨水屏专用高亮样式
- 本地/在线 SoundFont 支持
- WebView 生命周期管理
- 批量音频控制（stopAll, cleanupAll）

**API**:

```kotlin
// 渲染带音频的乐谱
fun renderWithAudio(
    musicData: MusicData,
    width: Int,
    container: ViewGroup,
    onComplete: ((Boolean) -> Unit)? = null
)

// 停止指定乐谱的播放
fun stopPlayback(musicId: String)

// 停止所有播放
fun stopAllPlayback()

// 清理指定乐谱的 WebView
fun cleanup(musicId: String)

// 清理所有 WebView
fun cleanupAll()
```

### MusicSheetDetector (已存在)

**位置**: `platforms/android/app/src/main/java/com/editor/nomadmark/music/MusicSheetDetector.kt`

**功能**:
- 从 Markdown 中检测 ABC/简谱代码块
- 解析乐谱元数据
- 支持 JSON 头部配置

---

## 集成方案设计

### 方案 A: 模式切换（推荐）

在预览区域添加模式切换按钮，用户可以选择：

1. **静态图片模式** (默认)
   - 使用 WebViewMusicRenderer
   - 显示为 Bitmap
   - 性能好，内存占用低
   - 适合阅读大量乐谱

2. **音频播放模式**
   - 使用 AudioMusicRenderer
   - WebView 直接显示
   - 支持音频播放和交互
   - 适合练习和试听

**优点**:
- 两种模式独立，互不影响
- 用户可以根据场景选择
- 保持现有架构不变

**缺点**:
- 需要维护两套渲染逻辑
- 切换模式时需要重新渲染

### 方案 B: 智能切换

根据用户行为自动切换：

- 首次渲染使用 Bitmap 模式（快速加载）
- 用户点击乐谱时，切换到音频模式
- 页面滚动到远离乐谱时，切回 Bitmap 模式释放资源

**优点**:
- 用户体验流畅
- 资源利用最优

**缺点**:
- 实现复杂
- 模式切换可能有视觉闪烁

### 方案 C: 仅音频模式

移除 Bitmap 模式，全部使用 WebView 显示

**优点**:
- 架构最简单
- 功能统一

**缺点**:
- 内存占用高
- 大量乐谱时性能问题
- 破坏现有功能

**建议**: 采用 **方案 A - 模式切换**

---

## 详细实施步骤

### 第 1 步: UI 布局修改

#### 1.1 添加模式切换按钮

修改 `activity_main.xml` 或对应的布局文件：

```xml
<!-- 在预览区域顶部添加模式切换器 -->
<LinearLayout
    android:id="@+id/music_mode_switcher"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center"
    android:padding="8dp"
    android:background="#f0f0f0"
    android:visibility="gone">

    <TextView
        android:id="@+id/music_mode_static"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="📄 图片模式"
        android:padding="8dp"
        android:background="@drawable/music_mode_bg_selected"/>

    <TextView
        android:id="@+id/music_mode_audio"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="🎵 音频模式"
        android:padding="8dp"
        android:layout_marginStart="16dp"
        android:background="@drawable/music_mode_bg_normal"/>
</LinearLayout>

<!-- 添加音频乐谱容器 -->
<FrameLayout
    android:id="@+id/music_audio_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:visibility="gone"/>
```

#### 1.2 创建按钮样式

创建 `res/drawable/music_mode_bg_selected.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#2196f3"/>
    <corners android:radius="4dp"/>
</shape>
```

创建 `res/drawable/music_mode_bg_normal.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#e0e0e0"/>
    <corners android:radius="4dp"/>
</shape>
```

### 第 2 步: Activity 代码修改

#### 2.1 添加状态变量

在 `MarkdownEditorActivity.kt` 中添加：

```kotlin
// 乐谱播放模式
private var musicPlaybackMode: MusicPlaybackMode = MusicPlaybackMode.STATIC
private val audioMusicRenderer: AudioMusicRenderer by lazy {
    AudioMusicRenderer(this)
}

enum class MusicPlaybackMode {
    STATIC,  // 静态图片模式
    AUDIO    // 音频播放模式
}
```

#### 2.2 初始化 UI 组件

在 `initViews()` 或类似方法中：

```kotlin
private fun initMusicPlaybackUI() {
    // 模式切换按钮
    findViewById<View>(R.id.music_mode_static).setOnClickListener {
        switchMusicMode(MusicPlaybackMode.STATIC)
    }
    findViewById<View>(R.id.music_mode_audio).setOnClickListener {
        switchMusicMode(MusicPlaybackMode.AUDIO)
    }
}

private fun switchMusicMode(mode: MusicPlaybackMode) {
    if (musicPlaybackMode == mode) return

    musicPlaybackMode = mode

    // 更新按钮样式
    updateModeButtons()

    // 清理当前模式
    if (mode == MusicPlaybackMode.AUDIO) {
        // 切换到音频模式
        renderMusicInAudioMode()
    } else {
        // 切换到静态模式
        audioMusicRenderer.cleanupAll()
        reRenderMarkdown()
    }
}
```

#### 2.3 音频模式渲染逻辑

```kotlin
private fun renderMusicInAudioMode() {
    val content = editorText.text.toString()
    val musicSheets = MusicSheetDetector.detectMusicSheetsFromMarkdown(content)

    if (musicSheets.isEmpty()) {
        // 没有乐谱，隐藏模式切换器
        findViewById<View>(R.id.music_mode_switcher).visibility = View.GONE
        return
    }

    // 显示模式切换器
    findViewById<View>(R.id.music_mode_switcher).visibility = View.VISIBLE

    // 清空容器
    val container = findViewById<ViewGroup>(R.id.music_audio_container)
    container.removeAllViews()

    // 获取屏幕宽度
    val screenWidth = resources.displayMetrics.widthPixels
    val horizontalMarginPx = (20 * resources.displayMetrics.density).toInt()
    val musicWidth = screenWidth - horizontalMarginPx * 2

    // 渲染每个乐谱
    musicSheets.forEach { musicBlock ->
        val musicData = musicBlock.musicData

        // 创建乐谱容器
        val musicContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 40, 0, 40)  // 上下间距
        }

        container.addView(musicContainer)

        // 使用 AudioMusicRenderer 渲染
        audioMusicRenderer.renderWithAudio(
            musicData = musicData,
            width = musicWidth,
            container = musicContainer,
            onComplete = { success ->
                Log.d(TAG, "音频乐谱渲染完成: ${musicData.title}, success=$success")
            }
        )
    }
}
```

#### 2.4 生命周期管理

```kotlin
override fun onPause() {
    super.onPause()
    // 隐藏软键盘
    hideSoftKeyboardFromAll()

    // 停止所有音频播放
    if (musicPlaybackMode == MusicPlaybackMode.AUDIO) {
        audioMusicRenderer.stopAllPlayback()
    }

    // 自动保存
    if (isModified) {
        performAutoSave()
    }
}

override fun onDestroy() {
    super.onDestroy()
    hideSoftKeyboardFromAll()

    // 清理音频渲染器
    audioMusicRenderer.cleanupAll()

    // 清理自动保存 Handler
    autoSaveHandler.removeCallbacksAndMessages(null)

    // ... 其他清理代码
}
```

### 第 3 步: 渲染触发时机

在现有的 Markdown 渲染流程中添加音频模式检查：

```kotlin
private fun renderMarkdown() {
    // 检查是否需要渲染乐谱
    val musicSheets = MusicSheetDetector.detectMusicSheetsFromMarkdown(content)

    if (musicSheets.isNotEmpty()) {
        // 有乐谱，根据模式决定渲染方式
        if (musicPlaybackMode == MusicPlaybackMode.AUDIO) {
            // 音频模式
            renderMusicInAudioMode()
            // 隐藏普通预览
            previewText.visibility = View.GONE
            findViewById<View>(R.id.music_audio_container).visibility = View.VISIBLE
        } else {
            // 静态模式
            findViewById<View>(R.id.music_mode_switcher).visibility = View.VISIBLE
            findViewById<View>(R.id.music_audio_container).visibility = View.GONE
            previewText.visibility = View.VISIBLE
            // 继续正常的 Markwon 渲染流程
        }
    } else {
        // 没有乐谱
        findViewById<View>(R.id.music_mode_switcher).visibility = View.GONE
        findViewById<View>(R.id.music_audio_container).visibility = View.GONE
        previewText.visibility = View.VISIBLE
    }

    // ... 继续正常渲染
}
```

### 第 4 步: 用户配置持久化

添加用户偏好设置：

```kotlin
private const val PREF_MUSIC_PLAYBACK_MODE = "music_playback_mode"

private fun loadMusicPlaybackMode() {
    val modeName = prefs.getString(PREF_MUSIC_PLAYBACK_MODE, MusicPlaybackMode.STATIC.name)
    musicPlaybackMode = try {
        MusicPlaybackMode.valueOf(modeName!!)
    } catch (e: Exception) {
        MusicPlaybackMode.STATIC
    }
}

private fun saveMusicPlaybackMode() {
    prefs.edit()
        .putString(PREF_MUSIC_PLAYBACK_MODE, musicPlaybackMode.name)
        .apply()
}
```

---

## UI/UX 设计

### 模式切换器位置

```
┌─────────────────────────────────────┐
│  NomadMark - 文档.md               │
├─────────────────────────────────────┤
│  [编辑] [预览] [分屏]               │
├─────────────────────────────────────┤
│                                     │
│  ┌───────────────────────────────┐  │
│  │  📄 图片模式  |  🎵 音频模式 │  │  ← 模式切换器
│  └───────────────────────────────┘  │
│                                     │
│  ┌───────────────────────────────┐  │
│  │                               │  │
│  │     ABC 乐谱内容              │  │
│  │                               │  │
│  └───────────────────────────────┘  │
│                                     │
└─────────────────────────────────────┘
```

### 交互反馈

1. **点击乐谱开始播放**:
   - 首次点击：初始化音频（可能短暂延迟）
   - 显示"♪ 播放中..."状态提示
   - 当前音符高亮（白底黑边）

2. **再次点击暂停**:
   - 音频立即停止
   - 高亮消失
   - 状态提示隐藏

3. **双击重播**:
   - 从头开始播放
   - 清除之前的高亮状态

4. **页面切换**:
   - onPause(): 立即停止播放
   - onResume(): 保持之前状态，不自动播放

### 状态提示

右上角固定位置显示播放状态：

```css
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
```

---

## 技术细节

### 音频初始化时机

```javascript
// 延迟初始化策略
setTimeout(() => {
    console.log('[ABC-RENDER] Delayed audio init');
    initAudio();
}, 500);
```

**原因**:
1. 等待 abcjs 渲染完成
2. 不阻塞初始页面加载
3. 用户点击时才真正初始化（懒加载）

### SoundFont 加载顺序

```javascript
function getSoundFontUrl() {
    const localPath = 'file:///android_asset/soundfonts/FluidR3_GM/';
    const onlinePath = 'https://paulrosen.github.io/midi-js-soundfonts/FluidR3_GM/';
    return localPath;  // 优先本地，失败自动回退
}
```

**行为**:
1. 首先尝试从本地 assets 加载
2. 如果本地不存在或加载失败，abcjs 会自动回退
3. 需要网络权限支持在线音色

### WebView 生命周期管理

```kotlin
// 存活的 WebView 管理
private val activeWebViews = mutableMapOf<String, WebView>()

// 渲染时创建并保存
activeWebViews[musicId] = webView

// 清理时销毁
fun cleanup(musicId: String) {
    activeWebViews[musicId]?.let { it.destroy() }
    activeWebViews.remove(musicId)
}
```

**内存管理**:
- 每个乐谱对应一个 WebView
- 切换到静态模式时清理所有 WebView
- Activity 销毁时清理所有 WebView
- 建议限制同时存在的 WebView 数量（如最多 5 个）

### 墨水屏高亮实现

```css
/* 禁止所有动画 */
path, g {
    transition: none !important;
    animation: none !important;
}

/* 白底黑边高亮 */
.abcjs-note.abcjs-highlight {
    fill: #ffffff !important;
    stroke: #000000 !important;
    stroke-width: 1.2 !important;
}
```

**设计考虑**:
1. 墨水屏刷新慢，动画会残影
2. 高对比度适合墨水屏
3. !important 确保覆盖 abcjs 默认样式

---

## 测试验证

### 功能测试

| 测试项 | 测试步骤 | 预期结果 |
|--------|----------|----------|
| 模式切换 | 点击模式切换按钮 | 模式正确切换，UI 正确更新 |
| 音频播放 | 音频模式下单击乐谱 | 开始播放，音符高亮 |
| 暂停播放 | 播放中再次单击 | 立即暂停，高亮消失 |
| 重播 | 双击乐谱 | 从头开始播放 |
| 页面切换 | 播放中切换到其他应用 | 音频立即停止 |
| 多乐谱 | 文档包含多个乐谱 | 每个乐谱独立播放 |
| 内存占用 | 打开 10 个乐谱的文档 | 内存增长合理 |
| 离线播放 | 断网状态播放 | 使用本地音色或回退 |

### 性能测试

| 指标 | 目标 | 测试方法 |
|------|------|----------|
| 首次渲染时间 | < 2s | 从加载到显示 |
| 音频初始化时间 | < 1s | 首次点击到播放 |
| 模式切换时间 | < 500ms | 切换到新模式 |
| 内存占用增长 | < 50MB/乐谱 | Android Profiler |
| WebView 销毁 | 完全释放 | LeakCanary |

### 兼容性测试

| 设备/场景 | 测试结果 |
|-----------|----------|
| Supernote 墨水屏 | 待测试 |
| 普通 Android 手机 | 待测试 |
| 横屏模式 | 待测试 |
| 分屏模式 | 待测试 |
| 大字体模式 | 待测试 |

---

## 故障排查

### 问题 1: 音频无法播放

**症状**: 单击乐谱没有声音

**排查步骤**:

1. 检查 WebView 设置:
   ```kotlin
   settings.mediaPlaybackRequiresUserGesture = false
   ```

2. 检查日志:
   ```bash
   adb logcat | grep ABC-AUDIO
   ```

3. 验证 abcjs synth 可用:
   ```javascript
   if (ABCJS.synth && ABCJS.synth.supportsAudio()) {
       // 音频可用
   }
   ```

4. 检查网络权限（在线音色需要）:
   ```xml
   <uses-permission android:name="android.permission.INTERNET"/>
   ```

### 问题 2: 音符高亮不显示

**症状**: 音频播放但音符不高亮

**排查步骤**:

1. 检查 CSS 是否正确加载
2. 验证 NoteHighlighter 是否正确初始化
3. 检查 abcjs 版本是否支持 CursorControl
4. 查看控制台日志:
   ```javascript
   console.log('[ABC-RENDER] Highlighted elements:', highlighted.length);
   ```

### 问题 3: 内存泄漏

**症状**: 切换模式后内存不释放

**排查步骤**:

1. 使用 LeakCanary 检测
2. 确保 WebView.destroy() 被调用
3. 检查引用链:
   ```kotlin
   audioMusicRenderer.cleanupAll()
   ```

4. 验证回调清理:
   ```javascript
   window.removeEventListener('beforeunload', ...);
   ```

### 问题 4: SoundFont 加载失败

**症状**: 控制台显示音色加载错误

**解决方案**:

1. 检查本地音色包目录:
   ```bash
   adb shell ls -la /data/app/.../assets/soundfonts/FluidR3_GM/
   ```

2. 验证在线音色可访问:
   ```bash
   curl -I https://paulrosen.github.io/midi-js-soundfonts/FluidR3_GM/piano.js
   ```

3. 添加备用音色源

### 问题 5: 双击检测失效

**症状**: 双击变成两次单击

**原因**: 延迟时间设置不当

**解决方案**:

```javascript
const DOUBLE_CLICK_DELAY = 300;  // 调整此值
```

---

## 附录

### 相关文件清单

| 文件 | 状态 | 说明 |
|------|------|------|
| `AudioMusicRenderer.kt` | ✅ 已创建 | 音频播放渲染器 |
| `WebViewMusicRenderer.kt` | ✅ 已更新 | Bitmap 渲染器（含音频代码但不可用） |
| `MusicSheetDetector.kt` | ✅ 存在 | 乐谱检测器 |
| `MusicSheetSpan.kt` | ✅ 已清理 | 移除了播放代码 |
| `NoteEvent.kt` | ❌ 已删除 | 不再需要 |
| `MusicPlaybackController.kt` | ❌ 已删除 | 不再需要 |

### 权限需求

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET"/>
<!-- 音频播放需要 -->
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>
```

### 音色包下载

**FluidR3_GM 音色包**:
- 在线地址: https://paulrosen.github.io/midi-js-soundfonts/FluidR3_GM/
- 许可: CC-BY-3.0
- 大小: 约 20MB（完整包）

**本地部署**:
1. 下载所有 `.js` 和 `.json` 文件
2. 放置到 `app/src/main/assets/soundfonts/FluidR3_GM/`
3. 重新编译 APK

---

## 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| 1.0 | 2025-07-31 | 初始版本，完整集成方案 |

---

## 参考资料

- [abcjs 官方文档](https://www.abcjs.net/abcjs-editor.html)
- [abcjs synth API](https://www.abcjs.net/abcjs-midi-api.html)
- [SoundFont 说明](https://paulrosen.github.io/midi-js-soundfonts/)
- [Android WebView 最佳实践](https://developer.android.com/guide/webapps/webview)
