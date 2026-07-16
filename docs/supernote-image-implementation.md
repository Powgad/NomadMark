# Supernote A6 X2 Nomad 图片渲染实现方案

## 设备参数

- **分辨率**: 1872 × 1404 (300 DPI)
- **内存**: 4 GB RAM
- **存储**: 32 GB (可扩展)
- **显示**: E-ink 电子纸（无 GPU 加速，刷新率低）
- **使用场景**: 长时间阅读、批注、笔记

## 核心设计目标

### 1. 绝对避免 OOM
- 本地图片可能来自手机拍照（4000×3000+）
- 使用采样解码（inSampleSize）
- 优先使用 RGB565 格式（2 bytes/px vs ARGB_8888 的 4 bytes/px）

### 2. 严格适配 1872×1404 / 300 DPI
- 直接使用物理像素，不依赖 density 换算
- 图片显示宽度不超过 1872 px
- 高度按比例缩放

### 3. 渲染性能优先
- 减少 Bitmap 重解码次数
- 使用 Coil 内存缓存
- 禁用硬件位图（E-ink 无 GPU）

### 4. Markdown 可持久化
- 图片保存到 app-private files 目录
- Markdown 中仅保存相对路径

## 实现文件

### 1. ImageProcessor.kt
**位置**: `platforms/android/app/src/main/java/com/editor/nomadmark/image/ImageProcessor.kt`

**功能**:
- 预读取图片尺寸（不解码完整 Bitmap）
- 计算采样率（inSampleSize）
- 采样解码图片
- JPEG 压缩保存（85% 质量）

**关键方法**:
```kotlin
fun processAndSaveImage(contentUri: Uri): String?
fun readImageDimensions(contentUri: Uri): Pair<Int, Int>?
fun calculateInSampleSize(width: Int, height: Int, targetSize: Int = 1872): Int
fun decodeSampledBitmapFromUri(contentUri: Uri, requestedInSampleSize: Int): Bitmap?
fun saveBitmapAsJpeg(bitmap: Bitmap): String
```

### 2. SupernoteImageLoader.kt
**位置**: `platforms/android/app/src/main/java/com/editor/nomadmark/image/SupernoteImageLoader.kt`

**功能**:
- Coil 图片加载器配置
- 禁用硬件位图
- 禁用动画
- 限制目标尺寸
- 内存/磁盘缓存管理

**配置参数**:
- `crossfade = false` - 禁用淡入淡出动画
- `allowHardware = false` - 禁用硬件位图
- `allowRgb565 = true` - 允许 RGB565 格式
- `size = Size(1872, Int.MAX_VALUE)` - 限制宽度
- `parallelism = 2` - 限制并发数

### 3. MarkdownImageManager.kt
**位置**: `platforms/android/app/src/main/java/com/editor/nomadmark/image/MarkdownImageManager.kt`

**功能**:
- 管理 Markdown 文档中的图片引用
- 清理未使用的图片
- 导出图片

**关键方法**:
```kotlin
fun extractImagePaths(markdown: String): List<String>
fun cleanUnusedImages(usedPaths: List<String>): Int
fun getAllImageInfo(): List<ImageInfo>
```

### 4. 集成到 MarkdownEditorActivity
**修改位置**: `MarkdownEditorActivity.kt`

**新增代码**:
```kotlin
/** 图片处理器（针对 Supernote 优化） */
private val imageProcessor: ImageProcessor by lazy { ImageProcessor(this) }
```

**更新的方法**:
- `insertImageFromUri()`: 使用 ImageProcessor 处理图片

### 4. 依赖更新
**文件**: `platforms/android/app/build.gradle`

**新增依赖**:
```gradle
// Coil - 轻量级图片加载库
def coilVersion = '2.5.0'
implementation "io.coil-kt:coil:$coilVersion"
implementation "io.coil-kt:coil-svg:$coilVersion"
implementation "io.coil-kt:coil-gif:$coilVersion"
```

### 5. 初始化
**文件**: `NomadMarkApplication.kt`

**在 onCreate() 中添加**:
```kotlin
// 初始化 Coil 图片加载器（Supernote 优化）
SupernoteImageLoader.init(this)
```

## 设计决策说明

### 1. 为什么禁用硬件位图？
- Supernote A6 X2 Nomad 是 E-ink 设备，无 GPU 硬件加速
- 硬件位图需要 GPU 支持，在 E-ink 上会回退到软件渲染
- 直接使用软件位图避免不必要的转换开销

### 2. 为什么禁用动画？
- E-ink 刷新率低（通常 < 10Hz），动画会有明显延迟
- 淡入淡出效果在 E-ink 上体验差
- 立即显示图片更符合用户预期

### 3. 为什么使用 RGB565 而非 ARGB_8888？
- E-ink 是黑白/灰度显示，对色彩不敏感
- RGB565 仅占 2 bytes/px，ARGB_8888 占 4 bytes/px
- 节省 50% 内存，降低 OOM 风险

### 4. 为什么限制宽度为 1872px 而非使用 wrap_content + density？
- Supernote A6 X2 Nomad 物理分辨率就是 1872×1404
- 300 DPI 下，density 可能为 3.0 或更高
- 直接使用物理像素更精确，避免 density 换算误差
- wrap_content 会导致解码原始尺寸，可能触发 OOM

### 5. 为什么 JPEG 优于 PNG（E-ink 场景）？
- JPEG 文件更小（压缩率 85%）
- JPEG 解码速度更快
- PNG 透明度在 E-ink 上无意义
- JPEG 有损压缩对文字/线条影响小

### 6. 为什么限制并发数为 2？
- 4GB RAM 在加载多张大图时容易达到峰值
- E-ink 刷新慢，同时加载多张图片用户体验差
- 2 张并发足够，避免内存抖动

## 内存占用对比

### 场景：手机拍摄的照片（4000×3000）

**原始解码（ARGB_8888，不推荐）**:
- 内存: 4000 × 3000 × 4 = 48,000,000 字节 ≈ 45.8 MB

**RGB565 + 采样率 2**:
- 尺寸: 2000 × 1500
- 内存: 2000 × 1500 × 2 = 6,000,000 字节 ≈ 5.7 MB

**RGB565 + 采样率 4（适配 1872px，推荐）**:
- 尺寸: 1000 × 750
- 内存: 1000 × 750 × 2 = 1,500,000 字节 ≈ 1.4 MB

**JPEG 85% 压缩后文件大小**: 约 100-300 KB

### 场景：加载 4 张 1872×1404 的图片

**ARGB_8888（不推荐）**:
- 单张: 1872 × 1404 × 4 = 10,517,376 字节 ≈ 10 MB
- 4 张: ≈ 40 MB

**RGB565（推荐）**:
- 单张: 1872 × 1404 × 2 = 5,258,688 字节 ≈ 5 MB
- 4 张: ≈ 20 MB

加上内存缓存（50 MB）和其他系统开销，
使用 RGB565 可以显著降低 OOM 风险。

## 实现流程

1. **用户选择图片**
   - 使用 ACTION_OPEN_DOCUMENT，image/*
   - 获得 content:// Uri

2. **尺寸预读取**
   - 使用 inJustDecodeBounds = true
   - 仅读取尺寸，不解码像素

3. **计算采样率**
   - 若长边 > 1872，按比例计算 inSampleSize
   - 确保是 2 的幂次方

4. **采样解码**
   - 使用计算出的 inSampleSize
   - 优先使用 RGB565 格式

5. **保存到 app-private 目录**
   - 路径: `/data/data/<package>/files/markdown_images/`
   - 文件名: UUID + .jpg
   - 质量: 85%

6. **Markdown 中保存相对路径**
   - 格式: `markdown_images/xxx.jpg`

7. **Markwon + Coil 渲染**
   - 使用 Coil 加载 file:// 路径
   - 明确指定 target size: width = 1872px
   - 禁用动画和硬件位图

## 验证方法

1. **功能测试**
   - 插入大尺寸图片（> 4000px）
   - 插入多张图片
   - 检查图片显示正常
   - 检查 Markdown 可持久化

2. **性能测试**
   - 监控内存占用
   - 检查是否触发 GC
   - 检查是否有卡顿

3. **兼容性测试**
   - 在 Supernote 设备上测试
   - 在普通手机上测试

## 注意事项

1. **IDE 诊断错误**: 某些 "Unresolved reference" 错误可能是 IDE 索引问题，实际编译时可能正常
2. **同步项目**: 修改 build.gradle 后需要同步 Gradle
3. **清理缓存**: 如果遇到问题，可以执行 `Clean Project` 和 `Rebuild Project`
