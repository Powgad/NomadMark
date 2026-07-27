---
name: music-rendering-troubleshooting
description: Android 乐谱（ABC 记谱法）渲染排查：wrap 换行截断、ABCJS 未加载、全白、超宽、缓存宽度错乱
---

# Music Sheet Rendering Troubleshooting

## 适用范围

Android 端 ABC 记谱法乐谱渲染（` ```music ` / ` ```abc ` 代码块）的常见问题排查。基于 **WebView + abcjs + AndroidSVG** 方案：abcjs 在 WebView 里渲染 ABC → 提取 SVG 字符串 → AndroidSVG 转 Picture/Bitmap → MusicSheetSpan 显示。

---

## 架构速查

代码路径前缀：`platforms/android/app/src/main/java/com/editor/nomadmark/`

| 层 | 文件 | 职责 |
|----|------|------|
| 检测 | `music/MusicSheetDetector.kt` | 正则识别 ` ```music`/` ```abc`/` ```简谱` 块，解析 ABC 元数据 |
| 数据 | `music/MusicData.kt` | MusicData 模型、`getCacheKey(width)` |
| 渲染 | `music/WebViewMusicRenderer.kt` | abcjs 渲染 → 提取 SVG → AndroidSVG 转 Bitmap |
| 缓存 | `music/MusicSheetCache.kt` | LRU Bitmap 缓存 |
| 显示 | `markwon/MusicSheetSpan.kt` | ReplacementSpan，把代码块替换为 Bitmap |
| 集成 | `MarkdownEditorActivity.kt` | `updatePreview` → `applyMusicSheetRendering` |

渲染数据流：abcjs 渲染 ABC → `document.querySelector('#paper svg')` 提取 SVG 字符串 → `SVG.getFromString` → `Picture` → `Bitmap` → MusicSheetSpan 显示。

资源依赖：
- `app/src/main/assets/abcjs/abcjs-basic-min.js`
- `com.caverock:androidsvg:1.4`（见 `app/build.gradle`）

---

## 问题 1: 开启 wrap 自动换行后，乐谱上下被截断（显示不完整）

### 症状
在 `renderAbc` 选项加入 `wrap: true` 实现长乐谱自动换行后，渲染出的乐谱图片上下边缘内容缺失（被裁剪）。**wrap 前单行乐谱显示正常**，wrap 后多行才出问题。

### 根本原因
三个因素叠加：

1. `renderAbc` 同时传了 `responsive: 'resize'`，abcjs 据此把 SVG 的 `width` 属性设为百分比（`"100%"`）。
2. AndroidSVG 对百分比宽度无法解析为绝对值，`svg.documentWidth` 返回 `-1`。
3. `createPictureFromSvg` 原判断 `if (documentWidth <= 0 || documentHeight <= 0)` 只要有任一尺寸无效，就**把宽度和高度一起重置**（高度被硬编码为 `400f`）。

为什么 wrap 前不显现：单行乐谱高度通常 < 400px，400 够用。wrap 后多行总高远超 400px，超出部分全部被裁。

### 解决方法

**改 1：移除 `responsive: 'resize'`**（在 `generateAbcHtml` 的 `renderAbc` 选项里）。改用固定像素宽度，SVG 会带明确的 `width`/`height` 属性，AndroidSVG 能读取绝对尺寸。项目已设置 `staffwidth`，不需要 responsive。

**改 2：`createPictureFromSvg` 用真实 SVG 像素尺寸，宽高分别判断**，不要因宽度无效就把高度硬编码。新增 `parseSvgSize` 直接从 SVG 字符串解析 `width`/`height`/`viewBox`：

```kotlin
private data class SvgSize(val width: Float, val height: Float)

private fun parseSvgSize(svgString: String, fallbackWidth: Int): SvgSize {
    // 仅匹配纯数字或带 px 单位，排除百分比（width="100%"）
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
```

`createPictureFromSvg` 用解析结果，高度仅在真读不到时才用默认，且不再被宽度问题波及：

```kotlin
val size = parseSvgSize(svgString, width)
svg.setDocumentWidth(size.width)
if (size.height > 0) svg.setDocumentHeight(size.height)
val picWidth = size.width.toInt().coerceAtLeast(width)
val picHeight = if (size.height > 0) size.height.toInt() else 400
```

### 验证方法
过滤日志查看渲染尺寸：
```bash
adb logcat -s WebViewMusicRenderer
```
看 `SVG 渲染成功: parsed=WxH, androidSvg=(WxH), pic=WxH, svgCount=N, len=...`：
- `parsed` 的 **H** 应为完整乐谱高度（多行常 600~1000+），不再是 400；
- **svgCount** 应为 `1`（abcjs 单 tune 输出单 SVG）。若 >1，说明 wrap 触发了多 SVG，当前 `querySelector` 只取第一个会丢行，需要改为遍历 `querySelectorAll('#paper svg')` 合并多个 SVG（分别渲染到同一 Canvas 并累加 Y 偏移）。

### 预防措施
- SVG → Bitmap 转换时，永远用从 SVG 实际解析出的像素尺寸，**不要因任一维度读不到就硬编码另一个维度的默认值**；宽高分别处理。
- 使用 AndroidSVG 做离线截图时，不要让 SVG 源带百分比尺寸；abcjs 渲染用于截图时不要开 `responsive: 'resize'`。

---

## 问题 2: 乐谱完全不显示

### 症状
` ```music ` 块区域只显示占位符（🎵 标题）或空白，无乐谱图片。

### 排查步骤
1. 确认 `app/src/main/assets/abcjs/abcjs-basic-min.js` 存在且非空。
2. logcat 查 `WebViewMusicRenderer` 是否有 `ABCJS 库未加载` / `ABCJS 状态: "not_loaded"`。若有，检查 `generateAbcHtml` 里 `<script src="file:///android_asset/abcjs/abcjs-basic-min.js">` 路径，以及 `loadDataWithBaseURL` 的 baseUrl 必须是 `file:///android_asset/`（否则 assets 相对路径加载不到）。
3. 确认 WebView 未被复用冲突（当前实现每次渲染新建 WebView 再 destroy）。
4. 看 `MusicSheetSpan.draw` 日志的 bitmap 是否一直为 null（渲染回调未返回）。

---

## 问题 3: 乐谱图片全白

### 症状
MusicSheetSpan 区域有高度但内容全白。

### 排查步骤
- logcat 查 `Bitmap 渲染为空（全白）` / `SVG 渲染为空`。
- 主路径（SVG → Picture）失败会自动回退 `captureBitmapFallback`（直接 `webView.draw(canvas)`）。若两条路径都全白：
  - 检查 `captureBitmap` 的像素采样判断逻辑（采样上限 10000 像素，大图可能误判）；
  - 检查 WebView 在捕获时是否已完成布局——`onPageFinished` 后延迟 1200ms 捕获，wrap 多行渲染更慢，可能需要更长延迟；
  - 检查捕获时 WebView 是否已 detach（父容器 FrameLayout 是否挂载）。

---

## 问题 4: 乐谱超出屏幕宽度

### 症状
长乐谱横向溢出，右侧被裁。

### 解决方法
在 `renderAbc` 选项加 `wrap: true`（需同时设置 `staffwidth`）。abcjs 会在小节线处按 staffwidth 自动断行，宽度不超屏、内容完整。注意 wrap 会**忽略 ABC 源码里的显式换行**，完全接管断行。配合要求见问题 1（必须移除 responsive、修 createPictureFromSvg 高度），否则换行后会被高度截断。

若换行后整张图过高，可把 `scale` 从 `1.2` 调小至 `1.0`，或用 `wrap: { minSpacing, maxSpacing, preferredMeasuresPerLine }` 精细控制每行小节数。

---

## 问题 5: 切换预览/分屏宽度后乐谱尺寸错乱

### 症状
预览模式渲染后切到分屏（宽度不同），乐谱显示的是上一个宽度的图，或被拉伸。

### 根本原因
`MusicData.getCacheKey()` 原本不含宽度，不同屏宽共用同一条缓存。

### 解决方法
`getCacheKey(width: Int)` 把宽度纳入 key：`"${type.name}_${content.hashCode()}_$width"`。所有调用点（`renderToBitmap` 读缓存、`captureBitmap`/`captureBitmapFallback` 写缓存）统一传 `width`。

---

## 快速参考：abcjs renderAbc 关键选项

| 选项 | 作用 | 本项目设置 |
|------|------|-----------|
| `staffwidth` | 谱表像素宽度，wrap 的前提 | `width - 80` |
| `wrap` | 按小节线自动换行 | `true` |
| `scale` | 整体缩放，影响每行可容纳的小节数 | `1.2`（图过高可调小至 1.0） |
| `responsive` | SVG 适配容器宽度 | **不要用 `'resize'`**（致百分比宽度，见问题 1） |
| `paddingtop/bottom/left/right` | SVG 内部留白 | 各 20 |

---

## 相关文档
- [乐谱渲染设计](../../docs/features/android-music-rendering-design.md)
- [乐谱代码参考](../../docs/features/android-music-code-reference.md)（注意：该文档部分代码已落后于实际源码，以源码为准）