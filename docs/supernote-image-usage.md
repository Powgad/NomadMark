# 图片功能使用示例

本文档展示如何使用 Supernote 优化的图片处理功能。

## 初始化

在 `NomadMarkApplication.onCreate()` 中初始化：

```kotlin
class NomadMarkApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 初始化 Coil 图片加载器（Supernote 优化）
        SupernoteImageLoader.init(this)
    }
}
```

## 在 Activity 中使用

### 1. 基本图片插入

```kotlin
class MarkdownEditorActivity : Activity() {
    // 图片处理器实例
    private val imageProcessor: ImageProcessor by lazy { ImageProcessor(this) }

    /**
     * 从 URI 插入图片
     */
    private fun insertImageFromUri(uri: Uri) {
        // 在后台线程处理图片
        Thread {
            // 处理图片并保存到本地
            val relativePath = imageProcessor.processAndSaveImage(uri)

            runOnUiThread {
                if (relativePath != null) {
                    // 获取完整路径
                    val fullPath = imageProcessor.getFullPath(relativePath)
                    // 插入到 Markdown
                    insertImage("file://$fullPath")
                } else {
                    Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /**
     * 插入图片 Markdown
     */
    private fun insertImage(url: String) {
        val editor = getCurrentEditor()
        val start = editor.selectionStart
        val end = editor.selectionEnd

        val imageMarkdown = if (start == end) {
            "![图片]($url)"
        } else {
            val selectedText = editor.text.substring(start, end)
            "![$selectedText]($url)"
        }

        editor.text.replace(start, end, imageMarkdown)
    }
}
```

### 2. 图片管理

```kotlin
// 创建图片管理器
val imageManager = MarkdownImageManager(context)

// 获取所有图片信息
val images = imageManager.getAllImageInfo()
images.forEach { info ->
    Log.d("Image", "${info.name}: ${info.getFormattedSize()}")
}

// 清理未使用的图片
val markdownContent = getMarkdownContent()
val usedPaths = MarkdownImageManager.extractImagePaths(markdownContent)
val deletedCount = imageManager.cleanUnusedImages(usedPaths)
Log.d("Image", "删除了 $deletedCount 个未使用的图片")

// 导出图片
val targetDir = File(Environment.getExternalStorageDirectory(), "ExportedImages")
val exportedFile = imageManager.exportImage("example.jpg", targetDir)
```

### 3. 内存管理

```kotlin
// 在内存紧张时清理缓存
lifecycle.addObserver(object : LifecycleObserver {
    @OnLifecycleEvent(Lifecycle.Event.ON_LOW_MEMORY)
    fun onLowMemory() {
        // 清理 Coil 内存缓存
        SupernoteImageLoader.clearMemoryCache(context)
    }
})
```

## 图片处理流程

```
用户选择图片
    ↓
获得 content:// URI
    ↓
ImageProcessor.processAndSaveImage()
    ↓
预读取尺寸（inJustDecodeBounds）
    ↓
计算采样率（inSampleSize）
    ↓
采样解码（RGB565）
    ↓
JPEG 压缩（85%）
    ↓
保存到 /data/data/.../files/markdown_images/
    ↓
返回相对路径（markdown_images/xxx.jpg）
    ↓
插入 Markdown: ![图片](file:///data/.../markdown_images/xxx.jpg)
    ↓
Markwon + Coil 渲染
```

## 内存占用对比

| 场景 | ARGB_8888 | RGB565 | 节省 |
|------|-----------|--------|------|
| 单张 4000×3000 照片 | 45.8 MB | 5.7 MB | 87.6% |
| 4 张 1872×1404 图片 | 40 MB | 20 MB | 50% |

## 配置说明

### Coil 图片加载器配置

```kotlin
// SupernoteImageLoader.kt 中的关键配置
ImageLoader.Builder(context)
    .crossfade(false)        // 禁用动画
    .allowHardware(false)     // 禁用硬件位图
    .allowRgb565(true)        // 使用 RGB565
    .size(Size(1872, MAX))    // 限制宽度
    .networkConcurrencyPolicy(parallelism = 2)
    .build()
```

### ImageProcessor 配置

```kotlin
// 关键参数
const val MAX_SCREEN_WIDTH = 1872   // Supernote 屏幕宽度
const val JPEG_QUALITY = 85         // JPEG 压缩质量
```

## 常见问题

### Q: 图片显示模糊？

A: 检查是否采样率过高。默认最大宽度为 1872px，对于 Supernote A6 X2 Nomad 是合适的。如需更高质量，可调整 `MAX_SCREEN_WIDTH`。

### Q: 内存占用过高？

A: 检查是否启用了 RGB565。可以通过 `SupernoteImageLoader.getCacheInfo()` 查看缓存占用。

### Q: 图片加载慢？

A: E-ink 刷新慢是正常的。可以通过限制并发数（`PARALLELISM`）来优化体验。

### Q: 如何清理缓存？

```kotlin
// 清理内存缓存
SupernoteImageLoader.clearMemoryCache(context)

// 清理磁盘缓存（需要协程作用域）
lifecycleScope.launch {
    SupernoteImageLoader.clearDiskCache(context)
}
```

## 测试建议

1. **功能测试**
   - 插入大尺寸图片（> 4000px）
   - 插入多张图片
   - 验证 Markdown 可持久化

2. **性能测试**
   - 监控内存占用（Android Profiler）
   - 检查是否触发 GC
   - 检查是否有卡顿

3. **兼容性测试**
   - 在 Supernote 设备上测试
   - 在普通手机上测试
