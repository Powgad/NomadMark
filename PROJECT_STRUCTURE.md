# NomadMark Markdown 编辑器 - 项目结构文档

> **版本**: 1.0
> **日期**: 2026-07-02
> **项目**: 跨平台 Markdown 编辑器

---

## 项目架构概览

```
Markdowneditor2.0/
├── android/          # Android/Supernote 平台实现 (Kotlin)
├── core/             # 共享核心层 (Rust)
├── desktop/          # 桌面平台实现 (Tauri + TypeScript)
├── common/           # 通用资源 (待使用)
├── docs/             # 文档目录
├── ios/              # iOS 平台实现 (待开发)
├── tests/            # 测试文件
└── build/            # 构建输出
```

---

## 技术栈

| 层级 | 技术选型 |
|-----|---------|
| **UI 层** | Android: Kotlin / Desktop: Tauri+React / iOS: Swift |
| **桥接层** | JNI (Android) / FFI (Tauri) |
| **Core 层** | Rust |
| **存储** | 平台原生 API + LMDB |

---

## 目录结构详解

### 📁 `/android` - Android 平台实现

```
android/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml      # 应用清单文件
│   │       ├── java/                     # Kotlin/Java 源码
│   │       │   └── com/editor/nomadmark/
│   │       │       ├── MainActivity.kt           # 主 Activity 入口
│   │       │       ├── MarkdownEditorActivity.kt # 编辑器主界面
│   │       │       ├── MarkdownEditorView.kt     # 自定义编辑器 View
│   │       │       ├── MarkdownCore.kt          # Rust FFI 桥接
│   │       │       ├── GestureEditor.kt          # 手势编辑器
│   │       │       ├── GestureOverlayView.kt     # 手势覆盖层
│   │       │       ├── GestureRecognizer.kt      # 手势识别器
│   │       │       ├── ActivityLaunchTest.kt     # 启动测试
│   │       │       ├── search/                  # 搜索模块
│   │       │       │   ├── SearchPanel.kt        # 搜索面板
│   │       │       │   └── SearchHighlightPainter.kt  # 高亮绘制
│   │       │       └── toc/                     # 目录模块
│   │       │           ├── TocPanel.kt           # 目录面板 (RecyclerView)
│   │       │           └── TocAdapter.kt         # 目录适配器
│   │       ├── jniLibs/                  # Native 库 (.so 文件)
│   │       └── res/                      # Android 资源
│   │           ├── drawable/             # 图片资源
│   │           ├── layout/               # 布局文件
│   │           │   └── activity_editor.xml   # 编辑器界面布局
│   │           └── values/               # 值资源
│   │               └── styles.xml        # 样式定义
│   └── build.gradle                     # App 模块构建配置
├── build.gradle                          # 项目构建配置
├── settings.gradle                       # 项目设置
├── gradle.properties                     # Gradle 属性配置
├── local.properties                      # 本地配置 (SDK 路径等)
├── gradlew / gradlew.bat                 # Gradle 包装脚本
└── gradle/                               # Gradle 相关文件

主要文件说明:
├── MainActivity.kt           # 应用启动入口
├── MarkdownEditorActivity.kt # 编辑器主界面，包含工具栏、目录、搜索等功能
├── MarkdownEditorView.kt     # 自定义 View，负责渲染 Markdown 内容
├── MarkdownCore.kt          # JNI 桥接，调用 Rust Core 的 native 方法
├── TocAdapter.kt            # 目录列表适配器 (最新实现，使用 RecyclerView)
├── TocPanel.kt              # 目录侧滑面板
├── SearchPanel.kt           # 搜索面板
└── Gesture*.kt              # 手势相关模块
```

---

### 📁 `/core` - 共享核心层 (Rust)

```
core/
├── Cargo.toml                           # Rust 项目配置
├── Cargo.lock                           # 依赖锁定文件
├── src/
│   ├── lib.rs                          # 库入口
│   ├── parser/                         # Markdown 解析器模块
│   │   ├── mod.rs                     # 模块声明
│   │   ├── ast.rs                     # 抽象语法树定义
│   │   ├── error.rs                   # 错误类型定义
│   │   └── streaming.rs               # 流式解析器 (支持大文件)
│   ├── bridge/                        # FFI 桥接模块
│   │   ├── mod.rs                     # 模块声明
│   │   ├── jni.rs                     # JNI 接口 (Android)
│   │   └── types.rs                   # FFI 数据类型定义
│   ├── layout/                        # 排版引擎模块
│   │   ├── engine.rs                  # 排版引擎
│   │   └── (其他布局相关文件)
│   ├── render/                        # 渲染模块
│   │   └── (渲染相关文件)
│   ├── search/                        # 搜索模块
│   │   └── (搜索相关文件)
│   └── history/                       # 历史记录模块
│       └── (撤销/重做相关文件)
├── android/                           # Android JNI 库输出
├── target/                            # Rust 编译输出
│   ├── {abi}/release/                 # 各架构的 release 构建
│   │   └── libmarkdown_core.so       # 编译出的 Native 库
└── (测试脚本)
    ├── check_build_info.sh
    └── test_jni_feature.sh

主要模块说明:
├── lib.rs              # Rust 库入口，初始化 Core
├── parser/             # Markdown 解析
│   ├── ast.rs         # 定义 BlockNode, InlineNode, TocEntry 等结构
│   └── streaming.rs   # 流式解析，支持大文件增量解析
├── bridge/jni.rs      # Android JNI 接口定义
└── bridge/types.rs    # 跨 FFI 边界的数据结构
```

---

### 📁 `/desktop` - 桌面平台实现 (Tauri + TypeScript)

```
desktop/
├── package.json                         # NPM 配置
├── tsconfig.json                        # TypeScript 配置
├── vite.config.ts                       # Vite 构建配置
├── index.html                           # HTML 入口
├── src/
│   ├── main.tsx                        # React 入口
│   ├── App.tsx                         # 主应用组件
│   ├── index.css                       # 全局样式
│   ├── types/                          # TypeScript 类型定义
│   │   └── index.ts                    # TocEntry, ViewMode 等类型
│   ├── hooks/                          # React Hooks
│   │   └── useDocument.ts              # 文档操作 Hook
│   ├── components/                     # React 组件
│   │   └── SplitView.tsx               # 分屏组件
│   └── (其他组件和样式)
└── src-tauri/                          # Tauri 后端 (Rust)
    ├── Cargo.toml                      # Tauri Rust 配置
    ├── tauri.conf.json                 # Tauri 配置
    └── src/
        └── main.rs                     # Tauri 后端入口

主要文件说明:
├── App.tsx             # 主界面，包含工具栏、快捷栏、分屏等
├── SplitView.tsx       # 分屏视图组件
├── useDocument.ts      # 文档状态管理 Hook
└── types/index.ts      # 跨平台类型定义
```

---

### 📁 `/docs` - 文档目录

```
docs/                    # (目前为空，待使用)
```

---

### 📁 根目录文档文件

```
根目录文档:
├── 《架构设计书 v2.0》.md        # 系统架构设计文档
├── 详细设计文档.md               # 详细设计说明
├── 《UI交互文档》.md             # UI/UX 交互设计
├── DEPLOYMENT_GUIDE.md          # 部署指南
├── TESTING_CHECKLIST.md         # 测试清单
├── MARKDOWN_RENDERING_FEATURES.md    # Markdown 渲染功能
├── MARKDOWN_RENDERING_STATUS.md      # Markdown 渲染状态
├── SKILL.md                      # 技能说明
└── ratta-android-apk-check.md   # Ratta APK 检查清单
```

---

### 📁 `/common`, `/ios`, `/tests`, `/build` - 其他目录

```
common/                   # 通用资源 (待使用)
└── (目前为空)

ios/                      # iOS 平台实现 (待开发)
└── (目前为空)

tests/                    # 测试文件
└── (目前为空)

build/                    # 构建输出
└── (构建临时文件)
```

---

## 核心数据流

```
┌─────────────────────────────────────────────────────────────────┐
│                        数据流向示意图                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  用户输入                                                       │
│     ↓                                                           │
│  ┌─────────────┐                                               │
│  │ UI Layer    │  (Kotlin View / Tauri Window)                │
│  └─────────────┘                                               │
│     ↓ (JNI/FFI)                                                │
│  ┌─────────────┐                                               │
│  │ Bridge Layer│  (jni.rs / FFI)                              │
│  └─────────────┘                                               │
│     ↓                                                           │
│  ┌─────────────┐                                               │
│  │ Rust Core   │                                               │
│  │ ┌─────────┐ │                                               │
│  │ │ Parser  │ │ → AST                                       │
│  │ ├─────────┤ │                                               │
│  │ │ Layout  │ │ → Layout                                    │
│  │ ├─────────┤ │                                               │
│  │ │ Renderer│ │ → Render Commands                           │
│  │ └─────────┘ │                                               │
│  └─────────────┘                                               │
│     ↑                                                           │
│  Render Commands → Canvas 绘制                                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 构建产物位置

### Android
```
android/app/build/outputs/apk/
├── debug/app-debug.apk              # Debug APK
└── release/app-release.apk           # Release APK
```

### Rust Core (Android Native Library)
```
core/target/
├── aarch64-linux-android/release/    # ARM64 (主流设备)
│   └── libmarkdown_core.so
├── armv7-linux-androideabi/release/  # ARM32 (旧设备)
│   └── libmarkdown_core.so
└── x86_64-linux-android/release/     # x86_64 (模拟器)
    └── libmarkdown_core.so
```

### Desktop
```
desktop/src-tauri/target/
└── release/
    └── nomadmark.exe                 # Windows 可执行文件
```

---

## 关键文件索引

### Android 关键文件

| 文件 | 作用 | 行数参考 |
|-----|------|---------|
| `MarkdownEditorActivity.kt` | 编辑器主界面，含目录/搜索/工具栏 | ~1080 行 |
| `MarkdownEditorView.kt` | 自定义 View，渲染内容 | ~1000 行 |
| `MarkdownCore.kt` | Rust FFI 桥接 | ~250 行 |
| `TocAdapter.kt` | 目录适配器 (RecyclerView) | ~440 行 |
| `TocPanel.kt` | 目录侧滑面板 | ~375 行 |
| `SearchPanel.kt` | 搜索面板 | ~300 行 |
| `Gesture*.kt` | 手势识别与处理 | ~400 行 |

### Core 关键文件

| 文件 | 作用 |
|-----|------|
| `lib.rs` | Rust 库入口 |
| `parser/ast.rs` | AST 定义 (BlockNode, InlineNode, TocEntry) |
| `parser/streaming.rs` | 流式 Markdown 解析器 |
| `bridge/jni.rs` | Android JNI 接口定义 |
| `bridge/types.rs` | FFI 数据类型 |

### Desktop 关键文件

| 文件 | 作用 |
|-----|------|
| `App.tsx` | 主应用组件 |
| `SplitView.tsx` | 分屏视图 |
| `useDocument.ts` | 文档状态管理 |
| `types/index.ts` | TypeScript 类型定义 |

---

## 依赖关系

```
                ┌──────────────┐
                │   Rust Core  │
                │  (libmarkdown│
                │   _core.so)  │
                └──────────────┘
                       ↑
        ┌──────────────┼──────────────┐
        │              │              │
┌───────────────┐ ┌──────────┐ ┌──────────┐
│   Android     │ │ Desktop  │ │   iOS    │
│  (Kotlin)     │ │ (Tauri)  │ │ (Swift)  │
│  JNI Bridge   │ │   FFI     │ │ C-interop│
└───────────────┘ └──────────┘ └──────────┘
```

---

*文档生成时间: 2026-07-02*
