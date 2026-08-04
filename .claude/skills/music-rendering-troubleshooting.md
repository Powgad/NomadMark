---
name: music-rendering-troubleshooting
description: Android 乐谱（ABC 记谱法）渲染排查：wrap 换行截断、ABCJS 未加载、全白、\u003C SVG 解码失败、超宽、播放器裁切对齐
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
| 配置 | `music/MusicRenderConfig.kt` | 静态/音频共用缩放与 staffwidth |
| 渲染 | `music/WebViewMusicRenderer.kt` | abcjs 渲染 → 提取 SVG → AndroidSVG 转 Bitmap |
| 音频 | `music/AudioMusicRenderer.kt` | 覆盖层 WebView + 播放；fit 到 Bitmap 尺寸 |
| 缓存 | `music/MusicSheetCache.kt` | LRU Bitmap 缓存 |
| 显示 | `markwon/MusicSheetSpan.kt` | ReplacementSpan；保存 `logicWidth` |
| 集成 | `MarkdownEditorActivity.kt` | `updatePreview` → `applyMusicSheetRendering` / 覆盖层 |

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

**改 2：`createPictureFromSvg` 用真实 SVG 像素尺寸，宽高分别判断**，不要因宽度无效就把高度硬编码。新增 `parseSvgSize` 直接从 SVG 字符串解析 `width`/`height`/`viewBox`。

`createPictureFromSvg` 用解析结果；Bitmap 宽高以 SVG 为准再乘 `MusicRenderConfig` 缩放（不要 `coerceAtLeast(logicWidth)` 把图画宽）：

```kotlin
val size = parseSvgSize(svgString, width)
svg.setDocumentWidth(size.width)
if (size.height > 0) svg.setDocumentHeight(size.height)
val picWidth = size.width.toInt().coerceAtLeast(1)
val picHeight = if (size.height > 0) size.height.toInt() else 400
```

### 验证方法
过滤日志查看渲染尺寸：
```bash
adb logcat -s WebViewMusicRenderer
```
看 `SVG 渲染成功: parsed=WxH, ... scaled=WxH`：
- `parsed` 的 **H** 应为完整乐谱高度（多行常 600~1000+），不再是 400；
- **svgCount** 应为 `1`（abcjs 单 tune 输出单 SVG）。若 >1，需合并多个 SVG。

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

## 问题 3b: SVG 解析失败（`\u003Csvg` / Unexpected token）→ 备用路径超宽裁切

### 症状
logcat：
```
SVG 开头: \u003Csvg width="..."
E WebViewMusicRenderer: SVG 解析失败
XmlPullParserException: Unexpected token (position:TEXT \u003Csvg ...
W WebViewMusicRenderer: Picture 创建失败，使用备用方法
渲染完成: 1346x...   # 宽 > musicSheetWidth(1224)，静态右侧被裁
```

### 根本原因
`WebView.evaluateJavascript` 返回值是 **JSON 编码**的；Chromium 会把 `<` 写成 `\u003C`。旧代码只剥外层引号并替换 `\"`/`\n`，**不解 `\uXXXX`**，AndroidSVG 收到字面量 `\u003Csvg...` 而非 `<svg...`，解析失败。随后走 `captureBitmapFallback`，再乘 `HORIZONTAL_SCALE`，Bitmap 超预览宽被裁切。

### 解决方法
用 `JSONTokener(result).nextValue() as String` 解码（失败时再手动解 `\uXXXX`）。解码后 SVG 应以 `<svg` 开头。缓存 version bump（当前 `v5`）以丢弃错误备用图。

### 验证方法
```bash
adb logcat -s WebViewMusicRenderer
```
- `SVG 开头: <svg width=...`（不是 `\u003Csvg`）
- `从 SVG 渲染完成: WxH`，且 **W ≤ musicSheetWidth**
- 不应再出现 `Unexpected token` / 不应依赖备用路径

---

## 问题 4: 乐谱超出屏幕宽度

### 症状
长乐谱横向溢出，右侧被裁。

### 解决方法
在 `renderAbc` 选项加 `wrap: true`（需同时设置 `staffwidth`）。abcjs 会在小节线处按 staffwidth 自动断行，宽度不超屏、内容完整。注意 wrap 会**忽略 ABC 源码里的显式换行**，完全接管断行。配合要求见问题 1（必须移除 responsive、修 createPictureFromSvg 高度），否则换行后会被高度截断。

若换行后整张图过高，可把 Canvas 缩放调小，或用 `wrap: { minSpacing, maxSpacing, preferredMeasuresPerLine }` 精细控制每行小节数。

---

## 问题 5: 切换预览/分屏宽度后乐谱尺寸错乱

### 症状
预览模式渲染后切到分屏（宽度不同），乐谱显示的是上一个宽度的图，或被拉伸。

### 根本原因
`MusicData.getCacheKey()` 原本不含宽度，不同屏宽共用同一条缓存。

### 解决方法
`getCacheKey(width: Int)` 把宽度纳入 key，并带 version（当前 `v3`）：`"${type.name}_${content.hashCode()}_${width}_$version"`。渲染参数变更时 bump version。

---

## 问题 6: 播放器内谱面裁切、与静态乐谱尺寸/位置不一致

### 症状
1. 点击静态乐谱后，播放器内谱面右侧/底部被截断，内容丢失。
2. 播放器内谱面尺寸、排版与静态 Bitmap 不一致。

### 根本原因
1. **渲染参数不一致**：静态曾用 `responsive: 'resize'`，覆盖层用另一套 `staffwidth`，自然尺寸不同。
2. **固定 scale + overflow:hidden**：覆盖层按固定 `scale(1.1, 1.8)`，若结果大于容器则裁切。
3. **未传 logicWidth**：覆盖层反推逻辑宽，与静态 `musicSheetWidth` 有误差。
4. **Bitmap 被撑宽**：`coerceAtLeast(width)` 使 Bitmap 比 SVG 更宽，覆盖层 fit 失真。

### 解决方法
1. **统一参数**（`MusicRenderConfig`）：静态与音频均用 `staffWidth(logicWidth)`、`ABC_PADDING`、`ABC_SCALE`，`responsive: false`。
2. **覆盖层 fit-to-container**：`scaleX = container.clientWidth / natW`，`scaleY = container.clientHeight / natH`，恰好填满 Bitmap 尺寸 WebView，不截断。
3. **`MusicSheetSpan.logicWidth`** → `createMusicOverlay` → `renderWithAudio(..., logicWidth)`。
4. **`createPictureFromSvg`** 用 SVG 真实尺寸 × 缩放；`staffWidth` 按 `HORIZONTAL_SCALE` 反算，并使最终宽 ≤ `logicWidth`；缓存 key `v4`。
5. **静态超宽**：若标题等使 SVG 比 staffwidth 更宽，创建 Bitmap 时横向钳制到 `logicWidth`，保证预览不裁切。

### 验证方法
```bash
adb logcat -s MarkdownEditorActivity AudioMusicRenderer WebViewMusicRenderer
```
- 覆盖层 `size=WxH` 与静态 Bitmap 一致。
- JS `[ABC-FIT] overlay fit, natural=... target=... scale=...`，target 等于 Bitmap，谱面完整。
- 静态 `SVG 渲染成功: ... scaled=WxH` 中 **W ≤ musicSheetWidth**；不应再出现右侧小节线丢失。
- 静态与播放器排版目视一致，滚动后覆盖层仍重合。

### 预防措施
- 改 abcjs 选项或缩放时，必须同时改 `WebViewMusicRenderer` 与 `AudioMusicRenderer`，并 bump `MusicData` 缓存 version。
- 覆盖层禁止「固定 scale + overflow 裁切」；以容器尺寸 fit 为准。
- 静态 Bitmap 最终宽度必须 ≤ 预览可用宽度（`musicSheetWidth`），`staffWidth` 计算公式须计入 `HORIZONTAL_SCALE`。

---

## 快速参考：abcjs renderAbc 关键选项

| 选项 | 作用 | 本项目设置 |
|------|------|-----------|
| `staffwidth` | 谱表像素宽度 | `MusicRenderConfig.staffWidth(logicWidth)` = `logicWidth/HORIZONTAL_SCALE - 2×ABC_PADDING`（保证 ×1.1 后 ≤ 预览宽） |
| `wrap` | 按小节线自动换行 | 当前未开（依赖 ABC 源码换行） |
| `scale` | abcjs 内缩放 | `1.0`（额外缩放由 Canvas / fit 完成） |
| `responsive` | SVG 适配容器宽度 | **false**（截图与覆盖层均不用 `'resize'`） |
| `paddingtop/bottom/left/right` | SVG 内部留白 | 各 `MusicRenderConfig.ABC_PADDING`（10） |

静态 Bitmap 额外：`Canvas.scale(HORIZONTAL_SCALE=1.1, VERTICAL_SCALE=1.8)`。  
覆盖层额外：CSS `transform: scale(cw/natW, ch/natH)` 填满 Bitmap 容器。

---

## 相关文档
- [乐谱渲染设计](../../docs/features/android-music-rendering-design.md)
- [乐谱代码参考](../../docs/features/android-music-code-reference.md)（注意：该文档部分代码已落后于实际源码，以源码为准）
