# NomadMark 乐谱渲染参数与设计文档

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档版本 | v1.0 |
| 创建日期 | 2026-08-05 |
| 作者 | NomadMark Team |
| 状态 | 完整版 (静态渲染) |

---

## 一、核心组件架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     MarkdownEditorActivity                       │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                  updatePreview()                            │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │        MusicSheetDetector.detectMusicSheets()      │  │  │
│  │  │  - 检测 ```music / ```abc / ```简谱 代码块          │  │  │
│  │  │  - 解析 ABC 元数据 (T/C/Q/K)                        │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │                           ↓                                  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              WebViewMusicRenderer                    │  │  │
│  │  │  - generateAbcHtml() 生成 HTML                      │  │  │
│  │  │  - JianpuConverter.convert() 简谱转换 (如需要)       │  │  │
│  │  │  - captureBitmap() SVG → Bitmap                     │  │  │
│  │  │  - createPictureFromSvg() AndroidSVG 渲染            │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │                           ↓                                  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              MusicSheetCache                         │  │  │
│  │  │  - LRU 缓存，最多 20 条，50MB                       │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │                           ↓                                  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              MusicSheetSpan                          │  │  │
│  │  │  - 替换代码块为渲染的 Bitmap                          │  │  │
│  │  │  - 垂直边距: 80px (上下各40px)                       │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 二、abcjs 渲染参数配置

### 2.1 核心渲染参数

在 `WebViewMusicRenderer.generateAbcHtml()` 中配置的 abcjs `renderAbc()` 参数：

```javascript
ABCJS.renderAbc("paper", abcCode, {
    // 自适应响应式布局
    responsive: "resize",

    // 水平视口滚动支持
    viewportHorizontal: true
})
```

| 参数 | 值 | 说明 |
|------|-----|------|
| `responsive` | `"resize"` | 启用响应式布局，SVG 宽度随容器自适应 |
| `viewportHorizontal` | `true` | 启用水平滚动，支持宽乐谱 |

### 2.2 未配置的官方参数（可扩展）

以下 abcjs 官方参数当前未使用，可根据需要添加：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `staffwidth` | `740` | 五线谱宽度（像素） |
| `scale` | `1` | 缩放比例 |
| `paddingtop` | `15` | 顶部内边距 |
| `paddingbottom` | `15` | 底部内边距 |
| `paddingleft` | `15` | 左侧内边距 |
| `paddingright` | `15` | 右侧内边距 |
| `showDecorations` | `true` | 显示装饰音符号 |
| `add_classes` | `true` | 添加 CSS 类 |
| `format.titlefont` | - | 标题字体格式 |
| `format.composerfont` | - | 作曲家字体格式 |
| `format.tempofont` | - | 拍号字体格式 |

---

## 三、ABC 指令参数

### 3.1 自动注入的指令

在渲染前会自动注入以下 ABC 指令：

```abc
%%staffsep 24
[原始 ABC 代码]
```

| 指令 | 值 | 说明 |
|------|-----|------|
| `%%staffsep` | `24` | 五线谱行间距 (pt)，提高可读性 |

### 3.2 其他可用 ABC 指令

```abc
%%wrap              自动换行
%%stretchlast       拉伸最后一行
%%beginsymbollyric  开始符号歌词
%%voice             声部定义
%%staves              谱表定义
```

---

## 四、自适应渲染策略

### 4.1 宽度自适应

```kotlin
// WebViewMusicRenderer
val contentWidth = width  // 容器宽度
```

| 策略 | 说明 |
|------|------|
| `responsive: "resize"` | SVG 宽度随容器自动调整 |
| `viewportHorizontal: true` | 支持宽乐谱水平滚动 |

### 4.2 高度自适应

通过 JavaScript 获取 SVG 实际高度：

```javascript
// 1. 优先读取 viewBox 属性
var viewBox = svg.getAttribute('viewBox');
var parts = viewBox.trim().split(/\s+/);
var h = parseFloat(parts[3]) || 0;

// 2. 回退到 height 属性
if (h === 0) h = parseFloat(svg.getAttribute('height')) || 0;

// 3. 最后使用 getBBox()
if (h === 0) {
    var bbox = svg.getBBox();
    h = bbox.height || 0;
}
```

### 4.3 多行 SVG 合并

当乐谱被渲染为多个 SVG 时（如多页或自动换行），代码会：

1. 计算总高度：累加所有 SVG 的高度
2. 确定最大宽度：取最宽 SVG 的宽度
3. 创建合并的 SVG 容器
4. 使用 `<g transform="translate(0, y)">` 垂直排列各行

```javascript
var mergedSvg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + maxWidth + ' ' + totalHeight + '" width="' + maxWidth + '" height="' + totalHeight + '">';
var currentY = 0;
for (var svg of svgs) {
    mergedSvg += '<g transform="translate(0, ' + currentY + ')">';
    mergedSvg += svg.innerHTML;
    mergedSvg += '</g>';
    currentY += svgHeight;
}
mergedSvg += '</svg>';
```

---

## 五、缩放参数

### 5.1 SVG → Bitmap 缩放

**目的**: 非等比缩放，弥补墨水屏分辨率限制

```kotlin
// createPictureFromSvg()
val horizontalScale = 1.1f  // 横向 1.1 倍
val verticalScale = 1.8f    // 纵向 1.8 倍

val scaledPicWidth = (picWidth * horizontalScale).toInt()
val scaledPicHeight = (picHeight * verticalScale).toInt()

canvas.scale(horizontalScale, verticalScale)
svg.renderToCanvas(canvas)
```

| 参数 | 值 | 说明 |
|------|-----|------|
| `horizontalScale` | `1.1` | 横向放大 10%，改善音符间距 |
| `verticalScale` | `1.8` | 纵向放大 80%，增强五线谱可读性 |

### 5.2 Fallback 渲染缩放

```kotlin
// captureBitmapFallback()
val horizontalScale = 1.1f
val verticalScale = 1.8f
canvas.scale(horizontalScale, verticalScale)
webView.draw(canvas)
```

---

## 六、缓存策略

### 6.1 缓存键生成

```kotlin
fun getCacheKey(width: Int): String {
    val version = "v2"  // 缓存版本号
    return "${type.name}_${content.hashCode()}_${width}_$version"
}
```

| 组成部分 | 说明 | 作用 |
|---------|------|------|
| `type.name` | 乐谱类型 (ABC/JIANPU) | 区分不同格式 |
| `content.hashCode()` | 内容哈希 | 唯一标识乐谱内容 |
| `width` | 渲染宽度 | 支持不同屏宽缓存 |
| `version` | 版本号 `v2` | 强制失效旧缓存 |

### 6.2 缓存配置

```kotlin
// MusicSheetCache
private val cache = LruCache<String, Bitmap>(20)
private val maxCacheSize = 50 * 1024 * 1024  // 50MB
```

| 参数 | 值 | 说明 |
|------|-----|------|
| 最大条目 | `20` | 最多缓存 20 张乐谱 |
| 最大大小 | `50MB` | 总缓存大小上限 |

### 6.3 去重机制

```kotlin
// pendingRenders 记录正在渲染的乐谱
private val pendingRenders = mutableSetOf<String>()

if (pendingRenders.contains(renderKey)) {
    callback(null)  // 跳过重复渲染
    return
}
pendingRenders.add(renderKey)
```

---

## 七、WebView 配置参数

### 7.1 基础配置

```kotlin
webView.apply {
    settings.apply {
        javaScriptEnabled = true
        cacheMode = WebSettings.LOAD_NO_CACHE
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    }
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    setBackgroundColor(Color.WHITE)
    setLayerType(View.LAYER_TYPE_HARDWARE, null)
}
```

| 参数 | 值 | 说明 |
|------|-----|------|
| `javaScriptEnabled` | `true` | 必须启用，运行 abcjs |
| `cacheMode` | `LOAD_NO_CACHE` | 避免旧 HTML 缓存干扰 |
| `mixedContentMode` | `ALWAYS_ALLOW` | 允许 file:// 加载本地资源 |
| `hardwareLayer` | - | 硬件加速，提升渲染性能 |

### 7.2 加载方式

```kotlin
webView.loadDataWithBaseURL(
    "file:///android_asset/",  // baseUrl，支持加载 assets 中的 abcjs
    html,
    "text/html",
    "UTF-8",
    null
)
```

### 7.3 HTML 模板结构

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { background: white; padding: 10px; }
        #paper { width: 100%; cursor: pointer; }
        #paper svg { width: 100% !important; height: auto; }
        .abcjs-play { display: none !important; }
    </style>
    <script src="file:///android_asset/abcjs/abcjs-basic-min.js"></script>
</head>
<body>
    <div id="paper"></div>
    <script>
        ABCJS.renderAbc("paper", abcCode, {
            responsive: "resize",
            viewportHorizontal: true
        });

        // 提取 SVG 用于 Bitmap 渲染
        setTimeout(function() {
            var svgs = document.querySelectorAll('#paper svg');
            // ... 合并 SVG 逻辑
            window.ABCJS_SVG_RESULT = mergedSvg;
            window.renderComplete = true;
        }, 300);
    </script>
</body>
</html>
```

---

## 八、MusicSheetSpan 布局参数

### 8.1 垂直边距

```kotlin
val verticalPadding = 80  // 上下各预留 40px
val height = (bitmap?.height ?: 200) + verticalPadding
```

| 参数 | 值 | 说明 |
|------|-----|------|
| `verticalPadding` | `80` | 总垂直间距 |
| 上边距 | `40` | 乐谱上方留白 |
| 下边距 | `40` | 乐谱下方留白 |

### 8.2 FontMetricsInt 设置

```kotlin
if (fm != null) {
    fm.ascent = -height / 2
    fm.descent = height / 2
    fm.top = fm.ascent - verticalPadding / 2
    fm.bottom = fm.descent + verticalPadding / 2
}
```

---

## 九、简谱转换参数

### 9.1 转换触发条件

```kotlin
val contentToRender = if (musicData.type == MusicType.JIANPU) {
    val conversionResult = JianpuConverter.convert(musicData.content, musicData.id)
    conversionResult.abc  // 使用转换后的 ABC 代码
} else {
    musicData.content  // 直接使用原始 ABC
}
```

### 9.2 支持的简谱格式

通过 `JianpuConverter.convert()` 转换，支持：

```
1=C 4/4
5 5 6 6 | 5 4 3 2
```

转换为标准 ABC 记谱法。

---

## 十、SVG 处理与清理

### 10.1 SVG 属性清理

```kotlin
val cleanedSvg = svgString
    .replace(Regex("""role="[^"]*""""), "")
    .replace(Regex("""aria-[a-z]+="[^"]*""""), "")
    .replace(Regex("""class="[^"]*""""), "")
    .replace(Regex("""data-[a-z-]+="[^"]*""""), "")
```

清理 AndroidSVG 不支持的属性：
- `role` 属性
- `aria-*` 无障碍属性
- `class` 类名属性
- `data-*` 自定义属性

### 10.2 SVG 尺寸解析

```kotlin
private data class SvgSize(val width: Float, val height: Float)

private fun parseSvgSize(svgString: String, fallbackWidth: Int): SvgSize {
    val widthAttr = Regex("""<svg[^>]*\bwidth\s*=\s*["']([\d.]+)(?:px)?["']""")
        .find(svgString)?.groupValues?.get(1)?.toFloatOrNull()
    val heightAttr = Regex("""<svg[^>]*\bheight\s*=\s*["']([\d.]+)(?:px)?["']""")
        .find(svgString)?.groupValues?.get(1)?.toFloatOrNull()
    val viewBox = Regex("""<svg[^>]*\bviewBox\s*=\s*["']([-\d.\s]+)["']""")
        .find(svgString)?.groupValues?.get(1)?.trim()?.split(Regex("""\s+"""))
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
```

---

## 十一、性能优化参数

### 11.1 渲染延迟

```kotlin
// 页面加载完成后延迟等待 JavaScript 执行
handler.postDelayed({
    captureBitmap(webView, musicData, width, wrappedCallback) {
        webView.destroy()
    }
}, 1200)  // 1200ms 延迟
```

| 参数 | 值 | 说明 |
|------|-----|------|
| 渲染等待延迟 | `1200ms` | 确保 abcjs 渲染完成 |
| SVG 获取延迟 | `300ms` | 等待 DOM 更新 |

### 11.2 去重机制

```kotlin
private val pendingRenders = mutableSetOf<String>()

// 渲染键格式
val renderKey = "${musicData.getCacheKey(width)}"
```

---

## 十二、错误处理与 Fallback

### 12.1 渲染检测点

```javascript
// 1. 检测 ABCJS 是否加载
typeof ABCJS !== 'undefined'

// 2. 检测 SVG 是否存在
document.querySelector('#paper svg') !== null

// 3. 检测 SVG 内容
window.ABCJS_SVG_RESULT !== ''
```

### 12.2 Fallback 触发条件

当以下情况发生时，触发 `captureBitmapFallback()`：

- ABCJS 未加载
- SVG 元素不存在
- SVG 字符串为空
- Bitmap 内容为空（全白）

### 12.3 Fallback 渲染方法

```kotlin
private fun captureBitmapFallback(
    webView: WebView,
    musicData: MusicData,
    width: Int,
    callback: (Bitmap?) -> Unit
) {
    // 强制 WebView 重新测量和布局
    webView.forceLayout()
    webView.measure(specWidth, specHeight)
    webView.layout(0, 0, webView.measuredWidth, measuredHeight)

    // 应用非等比缩放
    canvas.scale(horizontalScale, verticalScale)
    webView.draw(canvas)
}
```

---

## 十三、完整参数速查表

### abcjs 启用的官方参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `responsive` | `"resize"` | 响应式布局 |
| `viewportHorizontal` | `true` | 水平滚动 |

### abcjs 未启用（可扩展）的参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `staffwidth` | `740` | 五线谱宽度 |
| `scale` | `1` | 缩放比例 |
| `paddingtop` | `15` | 顶部内边距 |
| `paddingbottom` | `15` | 底部内边距 |
| `paddingleft` | `15` | 左侧内边距 |
| `paddingright` | `15` | 右侧内边距 |
| `showDecorations` | `true` | 显示装饰音 |
| `add_classes` | `true` | 添加 CSS 类 |
| `format.*` | - | 字体格式配置 |

### 缩放参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `horizontalScale` | `1.1` | 横向放大 10% |
| `verticalScale` | `1.8` | 纵向放大 80% |

### ABC 指令

| 指令 | 值 | 说明 |
|------|-----|------|
| `%%staffsep` | `24` | 五线谱行间距 |

### 布局参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `verticalPadding` | `80` | 乐谱垂直总间距 |
| 上边距 | `40` | 乐谱上方留白 |
| 下边距 | `40` | 乐谱下方留白 |

### 缓存参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 最大条目 | `20` | 最多缓存乐谱数 |
| 最大大小 | `50MB` | 缓存总大小上限 |
| 版本号 | `v2` | 缓存失效标记 |

### 性能参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 渲染延迟 | `1200ms` | 等待 abcjs 渲染 |
| SVG 获取延迟 | `300ms` | 等待 DOM 更新 |

---

## 十四、代码文件索引

| 文件 | 路径 | 功能 |
|------|------|------|
| WebViewMusicRenderer | `platforms/android/app/src/main/java/com/editor/nomadmark/music/WebViewMusicRenderer.kt` | 静态乐谱渲染 |
| MusicSheetCache | `platforms/android/app/src/main/java/com/editor/nomadmark/music/MusicSheetCache.kt` | LRU 缓存管理 |
| MusicSheetSpan | `platforms/android/app/src/main/java/com/editor/nomadmark/markwon/MusicSheetSpan.kt` | 替换 Span 显示 |
| MusicData | `platforms/android/app/src/main/java/com/editor/nomadmark/music/MusicData.kt` | 数据模型 |
| MusicSheetDetector | `platforms/android/app/src/main/java/com/editor/nomadmark/music/MusicSheetDetector.kt` | 乐谱块检测 |
| JianpuConverter | `platforms/android/app/src/main/java/com/editor/nomadmark/music/JianpuConverter.kt` | 简谱转 ABC |

---

## 附录：abcjs 官方文档参考

- [abcjs 官方文档](https://docs.abcjs.net/)
- [abcjs GitHub 仓库](https://github.com/paulrosen/abcjs)
- [ABC 记谱法标准](https://abcnotation.com/wiki/abc:standard:v2.1)

---

**文档结束**
