# NomadMark 项目结构分析文档

**文档版本**: 1.0  
**分析日期**: 2026-07-03  
**项目**: NomadMark - 跨平台 Markdown 编辑器

---

## 目录

1. [项目概览](#项目概览)
2. [目录结构](#目录结构)
3. [Core 层 (Rust)](#core-层-rust)
4. [Android 平台层](#android-平台层)
5. [Desktop 平台层 (Tauri)](#desktop-平台层-tauri)
6. [iOS 平台层](#ios-平台层)
7. [Common 共享资源](#common-共享资源)
8. [架构图](#架构图)

---

## 项目概览

### 项目定位
NomadMark 是一个专为 E-ink 设备优化的跨平台 Markdown 编辑器，支持三种平台：

| 平台 | 技术栈 | 设备支持 |
|------|--------|----------|
| **Android** | Kotlin + Rust Core (JNI) | Supernote A6 X2 Nomad |
| **Desktop** | React + Tauri + Rust Core | Windows, macOS, Linux |
| **iOS** | Swift + Rust Core (FFI) | iPhone, iPad |

### 核心特性
- ✅ 大文件支持（流式解析，内存映射）
- ✅ 实时预览（分屏模式）
- ✅ 全文搜索（正则表达式支持）
- ✅ 撤销/重做（命令模式）
- ✅ E-ink 显示优化（局部刷新、A2 模式）
- ✅ 手写识别（DELETE/INSERT/SELECT 手势）
- ✅ 目录导航（可折叠树形结构）

---

## 目录结构

```
Markdowneditor2.0/
├── core/                   # 共享 Rust 核心库
│   ├── src/               # 源代码
│   └── Cargo.toml         # Rust 依赖配置
│
├── android/               # Android 平台
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/editor/nomadmark/  # Kotlin 源码
│   │   │   ├── jniLibs/                    # 预编译 .so 文件
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   └── gradle/           # Gradle wrapper
│
├── desktop/              # Desktop 平台 (Tauri)
│   ├── src/              # React/TypeScript 源码
│   ├── src-tauri/        # Tauri Rust 后端
│   │   ├── src/
│   │   ├── Cargo.toml
│   │   └── tauri.conf.json
│   ├── package.json
│   └── vite.config.ts
│
├── ios/                  # iOS 平台
│   └── Frameworks/       # 预留框架位置
│
├── common/               # 共享资源
│   ├── fonts/           # 字体文件
│   ├── icons/           # 图标资源
│   └── themes/          # 主题配置
│
├── docs/                # 项目文档
│   ├── PROJECT_STRUCTURE_ANALYSIS.md
│   ├── VALIDATION_REPORT.md
│   └── ...
│
└── *.md                 # 根级文档
```

---

## Core 层 (Rust)

### 模块组织

```
core/src/
├── lib.rs               # 主入口，FFI 导出
├── insert.rs            # Markdown 语法插入
├── bridge/              # FFI 桥接层
│   ├── mod.rs
│   ├── types.rs         # C ABI 兼容类型
│   └── jni.rs           # Android JNI 包装
├── parser/              # Markdown 解析
│   ├── mod.rs
│   ├── ast.rs           # AST 节点定义
│   ├── error.rs         # 解析错误类型
│   └── streaming.rs     # 流式解析器（大文件）
├── layout/              # 布局引擎
│   ├── mod.rs
│   └── engine.rs        # 布局器实现
├── render/              # 渲染命令
│   ├── mod.rs
│   └── commands.rs      # 渲染命令定义
├── history/             # 撤销/重做
│   └── mod.rs
├── search/              # 全文搜索
│   └── mod.rs
└── replace/             # 文本替换
    └── mod.rs
```

### 核心模块详解

#### 1. lib.rs - 主入口

**主要结构体：**
- `MarkdownDocument` - 文档实例（包含解析器、缓存、历史）
- `CachedBlock` - 渲染缓存追踪
- `Core` - Desktop API 实例

**FFI 导出函数：**

| 类别 | 函数 | 描述 |
|------|------|------|
| 文档生命周期 | `md_document_create()` | 从内容创建 |
| | `md_document_create_from_path()` | 从路径创建（mmap） |
| | `md_document_release()` | 释放文档 |
| 渲染 | `md_document_load_range()` | 渲染行范围 |
| | `md_document_get_toc()` | 获取目录 |
| | `md_document_get_metadata()` | 获取元数据 |
| 搜索 | `md_document_search()` | 全文搜索 |
| 历史 | `md_document_undo()` | 撤销 |
| | `md_document_redo()` | 重做 |
| | `md_document_can_undo()` | 是否可撤销 |
| | `md_document_can_redo()` | 是否可重做 |
| 内存管理 | `md_document_get_memory_usage()` | 内存使用量 |
| | `md_document_release_before()` | 释放指定行之前的缓存 |
| | `md_document_release_to_target()` | 释放至目标内存 |

#### 2. parser 模块

**AST 节点类型：**
- `BlockNode` - 块级元素（Heading, Paragraph, CodeBlock, List, Table等）
- `InlineNode` - 行内元素（Text, Emphasis, Link, Image等）
- `TocEntry` - 目录条目

**StreamingParser 特性：**
- 内存映射文件读取
- 8MB 环形缓冲区
- O(1) 行号查找
- 原子进度追踪

#### 3. layout 模块

**Layouter 特性：**
- 300 DPI 优化（Supernote 规格）
- 三级字形缓存
- CJK 字符预加载
- 虚拟滚动支持

#### 4. render 模块

**RenderCommand 类型：**
- `DrawText` - 绘制文本
- `FillRect` - 填充矩形
- `DrawLine` - 绘制线条
- `DrawImage` - 绘制图像

**所有类型均为 `#[repr(C)]` 兼容。**

#### 5. search 模块

**SearchOptions：**
- `case_sensitive` - 大小写敏感
- `regex` - 正则表达式模式
- `whole_word` - 全词匹配

#### 6. history 模块

**EditCommand 类型：**
- `Insert { position, text }` - 插入操作
- `Delete { position, text }` - 删除操作
- `Replace { position, old_text, new_text }` - 替换操作

**History 特性：**
- 最大 100 条记录
- 命令合并优化
- 保存点追踪

### 依赖项

```toml
[dependencies]
pulldown-cmark = "0.9"      # Markdown 解析
rustybuzz = "0.7"          # 文本塑形
lru = "0.11"               # 缓存
rayon = "1.7"              # 并发
tantivy = "0.19"           # 全文搜索
memmap2 = "0.9"            # 内存映射
libc = "0.2"               # FFI

[target.'cfg(target_os = "android")'.dependencies]
jni = { version = "0.21", optional = true }
```

### 编译产物

- Android: `libmarkdown_core.so` (arm64-v8a, armeabi-v7a)
- Desktop: `libmarkdown_core.a` (静态库)
- iOS: `libmarkdown_core.a` (静态库)

---

## Android 平台层

### Kotlin 源码结构

```
android/app/src/main/java/com/editor/nomadmark/
├── MainActivity.kt                 # 入口 Activity
├── MarkdownEditorActivity.kt      # 主编辑器 Activity
├── MarkdownEditorView.kt           # 核心编辑视图
├── MarkdownCore.kt                 # FFI 桥接
│
├── search/                         # 搜索模块
│   ├── SearchPanel.kt             # 搜索面板
│   └── SearchHighlightPainter.kt  # 高亮绘制
│
├── toc/                            # 目录模块
│   ├── TocPanel.kt                # 目录面板
│   └── TocAdapter.kt              # RecyclerView 适配器
│
├── GestureEditor.kt               # 手写编辑器
├── GestureRecognizer.kt           # 手势识别算法
├── GestureOverlayView.kt          # 触摸捕获层
│
├── FileOperationHelper.kt         # 文件操作辅助
├── ScrollSyncManager.kt           # 滚动同步管理
├── KeyboardDetector.kt            # 键盘检测
└── EinkRefreshController.kt       # E-ink 刷新控制
```

### 核心组件

#### 1. MarkdownEditorActivity

**职责：** 主编辑器 UI 协调器

**关键方法：**
- `initViews()` - 初始化所有 UI 组件
- `setupListeners()` - 设置事件处理器
- `handleOpenIntent()` - 处理文件打开意图
- `performSearch()` / `replaceOne()` / `replaceAll()` - 搜索替换
- `undo()` / `redo()` - 历史管理

**状态变量：**
- `filePath`, `fileName` - 文件追踪
- `isPreviewMode`, `isSplitMode`, `isRevisionMode` - 模式标志
- `undoStack`, `redoStack` - 历史栈

#### 2. MarkdownEditorView

**职责：** 核心自定义视图，管理编辑器状态和渲染

**枚举类型：**
- `InputMode` - ExternalKeyboard, SoftKeyboard, Hidden
- `LayoutMode` - FullscreenEditor, FullscreenPreview, SplitView
- `FeatureFlags` - 工具栏、修订模式、目录可见性

**常量（Supernote 规格）：**
- `SCREEN_WIDTH = 1404`
- `SCREEN_HEIGHT = 1872`
- `TOOLBAR_HEIGHT = 120`
- `SPLIT_RATIO_EXTERNAL_KEYBOARD = 0.5f`
- `SPLIT_RATIO_SOFT_KEYBOARD = 0.4f`

#### 3. MarkdownCore (FFI 桥接)

**Native 方法：**

| 方法 | 描述 |
|------|------|
| `nativeCreate()` | 创建文档 |
| `nativeCreateFromPath()` | 从路径创建（mmap） |
| `nativeRelease()` | 释放文档 |
| `nativeRenderToCanvas()` | 渲染到 Canvas |
| `nativeLoadRange()` | 虚拟滚动加载 |
| `nativeGetToc()` | 获取目录 |
| `nativeSearch()` | 全文搜索 |
| `nativeUndo()` / `nativeRedo()` | 历史操作 |

#### 4. 手写识别系统

**GestureRecognizer 算法：**
- `recognizeDeleteGesture()` - 横线识别（线性度 > 0.85，水平 ±30°）
- `recognizeInsertGesture()` - 插入符识别（^ 形，向上倾向）
- `recognizeSelectGesture()` - 圆圈识别（闭合路径）

**GestureOverlayView：**
- 透明触摸捕获层
- 实时笔迹绘制
- 500ms 超时判定

#### 5. E-ink 优化

**EinkRefreshController：**
- 刷新模式：GLOBAL, PARTIAL, SMART
- 局部刷新阈值：10000 像素²
- A2 快速刷新支持
- 延迟刷新（手写后 500ms）

### 构建配置

```gradle
android {
    compileSdk 34
    defaultConfig {
        minSdk 28  // Supernote A6 X2 Nomad
        targetSdk 34
        ndk {
            abiFilters 'arm64-v8a', 'armeabi-v7a'
        }
    }
}
```

### 外部依赖

```gradle
dependencies {
    // Markwon - Markdown 渲染
    implementation "io.notices.markwon:core:4.6.2"
    implementation "io.notices.markwon:ext-strikethrough:4.6.2"
    implementation "io.notices.markwon:ext-tables:4.6.2"
    implementation "io.notices.markwon:ext-tasklist:4.6.2"
    implementation "io.notices.markwon:image:4.6.2"
    
    // AndroidX
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### APK 位置

```
android/app/build/outputs/apk/debug/app-debug.apk (≈13MB)
```

---

## Desktop 平台层 (Tauri)

### 项目结构

```
desktop/
├── src/                          # React/TypeScript 前端
│   ├── App.tsx                  # 主应用组件
│   ├── components/
│   │   └── SplitView.tsx        # 分屏布局组件
│   ├── hooks/
│   │   ├── useCanvasRenderer.ts # Canvas 渲染 Hook
│   │   └── useDocument.ts       # 文档管理 Hook
│   └── types/
│       └── index.ts             # TypeScript 类型定义
│
├── src-tauri/                   # Rust 后端
│   ├── src/
│   │   └── main.rs              # Tauri 命令
│   ├── Cargo.toml
│   └── tauri.conf.json
│
├── package.json
├── vite.config.ts
└── tsconfig.json
```

### 核心组件

#### 1. App.tsx (主应用)

**工具栏图标：**
- 预览切换: 👁️/📄
- 分屏切换: 🔲/⊞
- 修订模式: ✏️
- 目录: 📑
- 搜索: 🔍
- 保存: 💾

**状态管理：**
- `viewMode` - 编辑/预览/分屏
- `isPreviewActive`, `isSplitActive`, `isRevisionActive`
- `documentModified` - 修改标志

#### 2. SplitView.tsx (分屏组件)

**布局规则：**
- 外接键盘: 编辑区:预览区 = 50:50
- 软键盘: 编辑区:预览区 = 40:60

**分隔条：**
- 可拖动调整比例
- 实时计算区域尺寸

#### 3. main.rs (Tauri 后端)

**Tauri 命令：**

```rust
#[tauri::command]
async fn open_file(path: String) -> Result<DocumentInfo, String>

#[tauri::command]
async fn render_document(handle: u64, viewport: Viewport) -> RenderResult
```

**特性：**
- 直接调用 Rust Core（无 FFI 开销）
- Mutex 保护共享状态
- serde 序列化

### Tauri 配置

```json
{
  "windows": [{
    "fullscreen": false,
    "height": 900,
    "resizable": true,
    "width": 1200,
    "minWidth": 800,
    "minHeight": 600
  }]
}
```

### 依赖项

```json
{
  "dependencies": {
    "@tauri-apps/api": "^1.5.3",
    "react": "^18.2.0",
    "react-dom": "^18.2.0"
  }
}
```

---

## iOS 平台层

### 当前状态

⚠️ **开发中** - iOS 层目前仅预留了框架位置：

```
ios/
└── Frameworks/    # 预留用于放置预编译框架
```

### 计划实现

- Swift UI 界面
- Rust Core FFI 集成
- iPad 分屏模式支持
- Apple Pencil 手写识别

---

## Common 共享资源

### 目录结构

```
common/
├── fonts/           # 字体文件
├── icons/           # 图标资源
└── themes/          # 主题配置
```

### 资源说明

- **fonts/** - 用于各平台的字体文件
- **icons/** - 应用图标和 UI 图标
- **themes/** - 亮色/暗色主题配置

---

## 架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        NomadMark 架构                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                     Core (Rust)                             │   │
│  │  ┌───────────────────────────────────────────────────────┐ │   │
│  │  │  Parser │ Layout │ Render │ Search │ History │ Replace│ │   │
│  │  └───────────────────────────────────────────────────────┘ │   │
│  │  ┌───────────────────────────────────────────────────────┐ │   │
│  │  │           Bridge (FFI / JNI)                          │ │   │
│  │  └───────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                           │                                         │
│           ┌───────────────┼───────────────┬───────────────┐         │
│           ▼               ▼               ▼               ▼         │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ │
│  │   Android   │ │   Desktop   │ │     iOS     │ │   Future    │ │
│  │             │ │             │ │             │ │             │ │
│  │  Kotlin     │ │   React     │ │   Swift     │ │      ?      │ │
│  │  + JNI      │ │   + Tauri   │ │   + FFI     │ │             │ │
│  │             │ │   + Rust    │ │             │ │             │ │
│  │ Markwon     │ │   Canvas    │ │   UIKit     │ │             │ │
│  │ Gesture     │ │   SplitView │ │   Pencil    │ │             │ │
│  │ E-ink Opt   │ │             │ │             │ │             │ │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘ │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 数据流

### Android 平台数据流

```
┌──────────────┐
│  用户输入   │
│  (触摸/键盘) │
└──────┬───────┘
       ▼
┌──────────────────┐
│ GestureOverlay   │ ← 触摸捕获
│ MarkdownEditor   │ ← 文本输入
└────────┬─────────┘
         ▼
┌──────────────────┐
│ MarkdownCore.kt  │ ← FFI 桥接
└────────┬─────────┘
         ▼ JNI
┌──────────────────┐
│  Rust Core      │
│  - Parser       │
│  - Layout       │
│  - Render       │
└────────┬─────────┘
         ▼ RenderCommand[]
┌──────────────────┐
│ Canvas.draw()   │
└────────┬─────────┘
         ▼
┌──────────────────┐
│  E-ink 显示     │
└──────────────────┘
```

### Desktop 平台数据流

```
┌──────────────┐
│  用户输入   │
│  (键盘/鼠标) │
└──────┬───────┘
       ▼
┌──────────────────┐
│  React UI       │
│  - SplitView    │
│  - Toolbar      │
└────────┬─────────┘
         ▼ Tauri IPC
┌──────────────────┐
│  Rust Backend   │
│  (直接调用 Core) │
└────────┬─────────┘
         ▼ RenderCommand[]
┌──────────────────┐
│ Canvas 渲染      │
└────────┬─────────┘
         ▼
┌──────────────────┐
│  桌面显示       │
└──────────────────┘
```

---

## 关键设计决策

### 1. 共享 Core 架构
- **优势**: 代码复用，行为一致
- **挑战**: FFI 兼容性
- **解决**: `#[repr(C)]` 类型，JNI 包装

### 2. 流式解析
- **问题**: 大文件内存占用
- **解决**: 内存映射 + 环形缓冲区
- **效果**: 支持 GB 级文件

### 3. E-ink 优化
- **局部刷新**: 仅刷新脏矩形
- **A2 模式**: 快速黑白刷新（搜索高亮）
- **延迟刷新**: 手写后 500ms 刷新

### 4. 手写识别
- **算法**: 几何特征（线性度、方向、闭合）
- **实时性**: 500ms 超时判定
- **支持**: DELETE、INSERT、SELECT

---

## 文件统计

| 平台 | 源文件数 | 主要语言 |
|------|----------|----------|
| Core | 18 | Rust |
| Android | 15 | Kotlin |
| Desktop | 8 | TypeScript + Rust |
| iOS | 0 | (待开发) |

---

## 编译命令

### Core 层
```bash
cd core
cargo build --release              # Desktop
cargo build --release --features android  # Android JNI
```

### Android
```bash
cd android
./gradlew assembleDebug
```

### Desktop
```bash
cd desktop
npm run tauri:build
```

---

## 文档参考

- [IMPLEMENTATION_GUIDE.md](../IMPLEMENTATION_GUIDE.md) - 实现指南
- [PROJECT_PROGRESS.md](../PROJECT_PROGRESS.md) - 项目进度
- [VALIDATION_REPORT.md](../VALIDATION_REPORT.md) - 验证报告
- [《架构设计书 v2.0》.md](../《架构设计书 v2.0》.md) - 架构设计
- [详细设计文档.md](../详细设计文档.md) - 详细设计

---

**文档维护**: 本文档应随项目变更定期更新。
