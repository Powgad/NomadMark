# NomadMark Markdown 编辑器 - 开发路线图

> **版本**: 1.1 (完整版)
> **日期**: 2026-07-02
> **项目**: 跨平台 Markdown 编辑器

---

## 目录

1. [开发步骤总览](#开发步骤总览)
2. [详细步骤说明](#详细步骤说明)
   - [阶段 0: 准备阶段](#阶段-0-准备阶段)
   - [阶段 1: Core 层开发](#阶段-1-core-层开发-rust)
   - [阶段 2: Android 平台开发](#阶段-2-android-平台开发)
   - [阶段 3: Desktop 平台开发](#阶段-3-desktop-平台开发)
   - [阶段 4: iOS 平台开发](#阶段-4-ios-平台开发-可选)
   - [阶段 5: 联调测试](#阶段-5-联调测试)
   - [阶段 6: 发布准备](#阶段-6-发布准备)
3. [开发优先级说明](#开发优先级说明)
4. [关键里程碑](#关键里程碑)
5. [技术难点与风险](#技术难点与风险)
6. [模块功能清单](#模块功能清单)
7. [版本历史](#版本历史)
8. [参考资料](#参考资料)

---

## 开发步骤总览

| 阶段                | 步骤   | 任务描述          | 输出产物                                                                                 | 优先级                       | 预计工时      |     |
| ----------------- | ---- | ------------- | ------------------------------------------------------------------------------------ | ------------------------- | --------- | --- |
| **0. 准备阶段**       | 0.1  | 环境搭建          | 开发环境配置完成                                                                             | P0                        | 4h        |     |
|                   | 0.2  | 架构设计          | 技术选型、架构设计文档                                                                          | 架构文档                      | P0        | 8h  |
| **1. Core 层开发**   | 1.1  | 项目初始化         | Rust 项目创建、目录结构                                                                       | Cargo.toml                | P0        | 1h  |
|                   | 1.2  | AST 设计        | 定义 BlockNode、InlineNode、TocEntry                                                     | ast.rs                    | P0        | 4h  |
|                   | 1.3  | 解析器开发         | Markdown 流式解析器                                                                       | streaming.rs              | P0        | 16h |
|                   | 1.4  | 排版引擎          | 字体测量、布局计算                                                                            | layout/engine.rs          | P0        | 12h |
|                   | 1.5  | 渲染模块          | 生成绘制指令                                                                               | render/*.rs               | P0        | 8h  |
|                   | 1.6  | 搜索模块          | 全文搜索功能                                                                               | search/*.rs               | P1        | 8h  |
|                   | 1.7  | 替换模块          | 文本替换、全部替换、正则替换                                                                       | replace/*.rs              | P1        | 6h  |
|                   | 1.8  | 历史记录          | 撤销/重做栈                                                                               | history/*.rs              | P1        | 6h  |
|                   | 1.9  | FFI 桥接        | JNI 接口定义                                                                             | bridge/jni.rs             | P0        | 8h  |
|                   | 1.10 | Markdown 语法扩展 | 完整 Markdown 语法解析<br>• 粗体/斜体/删除线<br>• 代码块/行内代码<br>• 链接/图片<br>• 列表/任务列表<br>• 表格/引用/分隔线 | src/parser/*.rs           | P0        | 12h |
|                   | 1.11 | 快捷插入 API      | 快捷插入文本的 API<br>• 插入标题/粗体/斜体<br>• 插入代码/链接/图片<br>• 插入列表/表格                             | src/insert.rs             | P1        | 4h  |
|                   | 1.12 | 单元测试          | Core 层测试覆盖                                                                           | tests/                    | P0        | 8h  |
| **2. Android 平台** | 2.1  | 项目创建          | Android 项目初始化                                                                        | build.gradle              | P0        | 1h  |
|                   | 2.2  | JNI 集成        | 加载 .so 库、native 方法声明                                                                 | MarkdownCore.kt           | P0        | 4h  |
|                   | 2.3  | 自定义 View      | MarkdownEditorView 基础实现                                                              | MarkdownEditorView.kt     | P0        | 12h |
|                   | 2.4  | Canvas 渲染     | 处理 Rust 绘制指令                                                                         | onDraw()                  | P0        | 8h  |
|                   | 2.5  | 触摸事件          | 点击、滚动、缩放处理                                                                           | onTouchEvent()            | P0        | 6h  |
|                   | 2.6  | 编辑功能          | 文本输入、光标移动                                                                            | EditText 集成               | P0        | 12h |
|                   | 2.7  | 主界面 Activity  | 工具栏、状态栏                                                                              | MarkdownEditorActivity.kt | P0        | 16h |
|                   | 2.8  | 目录功能          | TOC 面板、缩进规则                                                                          | TocAdapter.kt             | P1        | 8h  |
|                   | 2.9  | 搜索功能          | 搜索面板、高亮显示                                                                            | SearchPanel.kt            | P1        | 6h  |
|                   | 2.10 | 替换功能          | • 替换面板 UI<br>• 单个替换<br>• 全部替换<br>• 替换历史                                              | MarkdownEditorActivity.kt | P1        | 8h  |
|                   | 2.11 | 手势支持          | Ratta SDK 集成                                                                         | Gesture*.kt               | P2        | 16h |
|                   | 2.12 | E-ink 优化      | 局部刷新、刷新策略                                                                            | EinkRefreshController     | P0        | 8h  |
|                   | 2.13 | 文件操作模块        | • 新建文件对话框<br>• 文件名验证<br>• 文件保存逻辑<br>• 自动保存<br>• 修改状态检测                               | MarkdownEditorActivity.kt | P0        | 6h  |
|                   | 2.14 | 撤销/重做 UI      | • 按钮事件绑定<br>• 与 Core history 集成<br>• 撤销栈可视化<br>• Ctrl+Z/Ctrl+Y 快捷键                   | MarkdownEditorActivity.kt | P1        | 4h  |
|                   | 2.15 | 快捷工具栏         | • 12 个按钮事件处理<br>• H1-H6 标题插入<br>• 粗体/斜体/删除线<br>• 代码/链接/图片<br>• 列表/表格<br>• 展开/收起动画    | MarkdownEditorActivity.kt | P0        | 8h  |
|                   | 2.16 | 键盘适配模块        | • 键盘类型检测 (F11)<br>• 分屏比例切换 (50:50/40:60)<br>• 键盘标识显示<br>• 软键盘唤出逻辑                    | KeyboardDetector.kt       | P0        | 4h  |
|                   | 2.17 | 分屏模式实现        | • 分屏视图切换<br>• 编辑区与预览区同步<br>• 滚动位置同步                                                  | MarkdownEditorActivity.kt | P1        | 6h  |
|                   | 2.18 | 滚动与缩放         | • 双指滑动滚动<br>• 双指缩放支持<br>• 惯性滚动<br>• 触摸事件处理                                           | MarkdownEditorView.kt     | P1        | 6h  |
|                   | 2.19 | 修订模式细化        | • Ratta SDK 详细集成<br>• 手势识别算法<br>• 坐标修正逻辑<br>• 删除/插入手势<br>• 手写延迟优化                    | GestureEditor.kt          | P2        | 12h |
|                   | 2.20 | E-ink 刷新细化    | • 局部刷新区域计算<br>• 全局刷新触发条件<br>• 刷新模式切换<br>• 手写刷新优化                                     | EinkRefreshController.kt  | P0        | 4h  |
|                   | 2.21 | 构建配置          | NDK 构建脚本、.so 打包                                                                      | CMakeLists.txt            | P0        | 4h  |
| **3. Desktop 平台** | 3.1  | 项目创建          | Tauri + React 项目初始化                                                                  | package.json              | P1        | 2h  |
|                   | 3.2  | FFI 集成        | Tauri Command 定义                                                                     | src-tauri/src/main.rs     | P1        | 4h  |
|                   | 3.3  | React 组件      | 主应用、工具栏                                                                              | App.tsx                   | P1        | 12h |
|                   | 3.4  | Canvas 渲染     | Web Canvas 渲染适配                                                                      | SplitView.tsx             | P1        | 8h  |
|                   | 3.5  | 文档管理          | 打开、保存、状态同步                                                                           | useDocument.ts            | P1        | 6h  |
|                   | 3.6  | 打包配置          | Tauri 打包配置                                                                           | tauri.conf.json           | P1        | 2h  |
| **4. iOS 平台**     | 4.1  | 项目创建          | iOS Swift 项目初始化                                                                      | Xcode 项目                  | P2        | 2h  |
|                   | 4.2  | C-interop 集成  | Rust Core 静态库链接                                                                      | Bridging Header           | P2        | 8h  |
|                   | 4.3  | UIView 实现     | 自定义渲染 View                                                                           | MarkdownView.swift        | P2        | 16h |
|                   | 4.4  | UI 界面         | 工具栏、编辑器界面                                                                            | ViewController            | P2        | 12h |
| **5. 联调测试**       | 5.1  | 单元测试          | 各模块单元测试                                                                              | 测试报告                      | P0        | 16h |
|                   | 5.2  | 集成测试          | 跨模块集成测试                                                                              | 测试报告                      | P0        | 12h |
|                   | 5.3  | 性能测试          | 大文件、内存、帧率测试                                                                          | 性能报告                      | P0        | 8h  |
|                   | 5.4  | E-ink 测试      | Supernote 实机测试                                                                       | 测试报告                      | P0        | 8h  |
|                   | 5.5  | Bug 修复        | 测试发现的问题修复                                                                            | Bug 列表                    | P0        | 16h |
| **6. 发布准备**       | 6.1  | 代码审查          | Code Review                                                                          | Review 报告                 | P0        | 8h  |
|                   | 6.2  | 文档完善          | 用户手册、API 文档                                                                          | 文档                        | P0        | 8h  |
|                   | 6.3  | 打包发布          | APK/AAB/EXE/DMG 构建                                                                   | 安装包                       | P0        | 4h  |
|                   | 6.4  | 上架准备          | 应用商店资料准备                                                                             | 商店资料                      | P1        | 8h  |
| **总计**            | -    | -             | -                                                                                    | -                         | **~439h** |     |

---

## 详细步骤说明

### 阶段 0: 准备阶段

| 步骤 | 任务 | 详细说明 | 依赖 |
|-----|------|---------|------|
| 0.1 | 环境搭建 | • 安装 Rust 工具链<br>• 安装 Android Studio + NDK<br>• 安装 Node.js + Tauri CLI | - |
| 0.2 | 架构设计 | • 确定技术栈选型<br>• 绘制架构图<br>• 编写设计文档 | 环境搭建完成 |

### 阶段 1: Core 层开发 (Rust)

| 步骤 | 任务 | 详细说明 | 输出文件 |
|-----|------|---------|---------|
| 1.1 | 项目初始化 | `cargo new --lib markdown-core` | `Cargo.toml` |
| 1.2 | AST 设计 | 定义 Markdown 语法树结构<br>• BlockNode: 标题、段落、列表等<br>• InlineNode: 文本、加粗、链接等<br>• TocEntry: 目录条目 | `src/parser/ast.rs` |
| 1.3 | 解析器开发 | 实现流式 Markdown 解析器<br>• 支持大文件增量解析<br>• 错误恢复机制<br>• CommonMark 兼容 | `src/parser/streaming.rs` |
| 1.4 | 排版引擎 | • 字体度量计算<br>• 行高、字间距计算<br>• 300DPI 优化 | `src/layout/engine.rs` |
| 1.5 | 渲染模块 | 生成平台无关的绘制指令<br>• DrawText, DrawLine, FillRect<br>• 局部刷新支持 | `src/render/*.rs` |
| 1.6 | 搜索模块 | • 全文搜索<br>• 正则表达式支持 | `src/search/*.rs` |
| 1.7 | 替换模块 | • 文本替换<br>• 全部替换<br>• 正则替换<br>• 撤销替换 | `src/replace/*.rs` |
| 1.8 | 历史记录 | 撤销/重做栈实现 | `src/history/*.rs` |
| 1.9 | FFI 桥接 | 定义 JNI 接口<br>• 暴露给 Android 的函数<br>• 数据类型转换 | `src/bridge/jni.rs` |
| 1.10 | Markdown 语法扩展 | 完整 Markdown 语法支持<br>• 粗体/斜体/删除线<br>• 代码块/行内代码<br>• 链接/图片<br>• 列表/任务列表<br>• 表格/引用/分隔线 | `src/parser/*.rs` |
| 1.11 | 快捷插入 API | 快捷插入文本 API<br>• 插入标题/粗体/斜体<br>• 插入代码/链接/图片<br>• 插入列表/表格 | `src/insert.rs` |
| 1.12 | 单元测试 | 覆盖核心功能 | `tests/*.rs` |

### 阶段 2: Android 平台开发

| 步骤 | 任务 | 详细说明 | 输出文件 |
|-----|------|---------|---------|
| 2.1 | 项目创建 | Android Studio 新建项目 | `build.gradle` |
| 2.2 | JNI 集成 | • System.loadLibrary()<br>• native 方法声明 | `MarkdownCore.kt` |
| 2.3 | 自定义 View | • 继承 View<br>• 初始化 Canvas | `MarkdownEditorView.kt` |
| 2.4 | Canvas 渲染 | • 解析 Rust 绘制指令<br>• 转换为 Android Canvas API | `onDraw()` |
| 2.5 | 触摸事件 | • onClick 滚动跳转<br>• onScroll 翻页<br>• onScale 缩放 | `onTouchEvent()` |
| 2.6 | 编辑功能 | • 集成 EditText<br>• 光标同步<br>• 软键盘处理 | `MarkdownEditorActivity.kt` |
| 2.7 | 主界面 | • 工具栏<br>• 目录面板<br>• 搜索面板 | `MarkdownEditorActivity.kt` |
| 2.8 | 目录功能 | • TocSimpleAdapter<br>• 缩进规则 (H1=0, H2=16dp...)<br>• 字号统一 | `TocAdapter.kt` |
| 2.9 | 搜索功能 | • SearchPanel<br>• 高亮绘制 | `SearchPanel.kt` |
| 2.10 | 替换功能 | • 替换面板 UI (activity_editor.xml)<br>• 单个替换逻辑<br>• 全部替换逻辑<br>• 替换确认对话框 | `MarkdownEditorActivity.kt` |
| 2.11 | 手势支持 | • Ratta SDK 集成<br>• 手写转文字 | `Gesture*.kt` |
| 2.12 | E-ink 优化 | • 局部刷新 invalidate(Rect)<br>• EPD_FULL/EPD_PARTIAL 策略 | `EinkRefreshController.kt` |
| 2.13 | 文件操作模块 | • 新建文件对话框<br>• 文件名验证<br>• 文件保存逻辑<br>• 自动保存<br>• 修改状态检测 | `MarkdownEditorActivity.kt` |
| 2.14 | 撤销/重做 UI | • 按钮事件绑定<br>• 与 Core history 集成<br>• 撤销栈可视化<br>• Ctrl+Z/Ctrl+Y 快捷键 | `MarkdownEditorActivity.kt` |
| 2.15 | 快捷工具栏 | • 12 个按钮事件处理<br>• 标题/粗体/斜体/删除线<br>• 代码/链接/图片<br>• 列表/表格<br>• 展开/收起动画 | `MarkdownEditorActivity.kt` |
| 2.16 | 键盘适配模块 | • 键盘类型检测 (F11)<br>• 分屏比例切换 (50:50/40:60)<br>• 键盘标识显示<br>• 软键盘唤出逻辑 | `KeyboardDetector.kt` |
| 2.17 | 分屏模式实现 | • 分屏视图切换<br>• 编辑区与预览区同步<br>• 滚动位置同步 | `MarkdownEditorActivity.kt` |
| 2.18 | 滚动与缩放 | • 双指滑动滚动<br>• 双指缩放支持<br>• 惯性滚动<br>• 触摸事件处理 | `MarkdownEditorView.kt` |
| 2.19 | 修订模式细化 | • Ratta SDK 详细集成<br>• 手势识别算法<br>• 坐标修正逻辑<br>• 删除/插入手势<br>• 手写延迟优化 | `GestureEditor.kt` |
| 2.20 | E-ink 刷新细化 | • 局部刷新区域计算<br>• 全局刷新触发条件<br>• 刷新模式切换<br>• 手写刷新优化 | `EinkRefreshController.kt` |
| 2.21 | 构建配置 | • externalNativeBuild<br>• .so 库打包 | `CMakeLists.txt` |

### 阶段 3: Desktop 平台开发

| 步骤 | 任务 | 详细说明 | 输出文件 |
|-----|------|---------|---------|
| 3.1 | 项目创建 | `npm create tauri-app` | `package.json` |
| 3.2 | FFI 集成 | Tauri Command 调用 Rust | `src-tauri/src/main.rs` |
| 3.3 | React 组件 | • App.tsx 主界面<br>• SplitView 分屏<br>• Toolbar 工具栏 | `App.tsx` |
| 3.4 | Canvas 渲染 | Web Canvas API 适配 | `SplitView.tsx` |
| 3.5 | 文档管理 | • 打开文件<br>• 保存文件<br>• 状态同步 | `useDocument.ts` |
| 3.6 | 打包配置 | Windows/Mac/Linux 打包 | `tauri.conf.json` |

### 阶段 4: iOS 平台开发 (可选)

| 步骤 | 任务 | 详细说明 | 输出文件 |
|-----|------|---------|---------|
| 4.1 | 项目创建 | Xcode 新建项目 | `.xcodeproj` |
| 4.2 | C-interop | 静态库链接 | `Bridging-Header.h` |
| 4.3 | UIView | 自定义渲染 View | `MarkdownView.swift` |
| 4.4 | UI 界面 | 工具栏、编辑器 | `ViewController.swift` |

### 阶段 5: 联调测试

| 步骤 | 任务 | 详细说明 |
|-----|------|---------|
| 5.1 | 单元测试 | • Rust 单元测试<br>• Kotlin 单元测试<br>• React 组件测试 |
| 5.2 | 集成测试 | • FFI 边界测试<br>• 跨平台数据同步测试 |
| 5.3 | 性能测试 | • 100MB 大文件测试<br>• 内存占用测试<br>• 渲染帧率测试 |
| 5.4 | E-ink 测试 | Supernote A6 X2 实机测试 |
| 5.5 | Bug 修复 | 修复测试发现的问题 |

### 阶段 6: 发布准备

| 步骤 | 任务 | 详细说明 |
|-----|------|---------|
| 6.1 | 代码审查 | Code Review，代码规范检查 |
| 6.2 | 文档完善 | 用户手册、开发文档 |
| 6.3 | 打包发布 | • Android: APK/AAB<br>• Desktop: EXE/DMG/AppImage |
| 6.4 | 上架准备 | 应用商店图标、截图、描述 |

---

## 开发优先级说明

| 优先级 | 说明 | 适用模块 |
|--------|------|---------|
| **P0** | 核心功能，必须完成 | Core 层、文件操作、编辑功能、快捷工具栏 |
| **P1** | 重要功能，尽量完成 | 桌面平台、搜索/替换、目录、分屏、撤销/重做 |
| **P2** | 可选功能，视情况完成 | iOS 平台、修订模式、手势支持 |

---

## 关键里程碑

```
┌─────────────────────────────────────────────────────────────────┐
│                        开发里程碑                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  [M1] Core 层完成        → Rust 编译通过，FFI 接口稳定          │
│                                                                  │
│  [M2] Android MVP 完成   → 可在 Android 上编辑和预览 Markdown    │
│                                                                  │
│  [M3] 核心功能完善       → 文件操作、撤销/重做、快捷工具栏        │
│                                                                  │
│  [M4] 高级功能完成       → 目录、搜索/替换、分屏、修订模式         │
│                                                                  │
│  [M5] Desktop 完成       → 可在 PC 上使用                       │
│                                                                  │
│  [M6] 测试完成           → 所有测试通过                          │
│                                                                  │
│  [M7] 发布版本           → APK/EXE 可发布                        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 技术难点与风险

| 难点 | 风险 | 应对方案 |
|-----|------|---------|
| JNI 边界数据传递 | 内存泄漏、数据转换错误 | 使用 Rust FFI 安全类型，严格测试 |
| E-ink 局部刷新 | 刷新策略不当导致闪烁 | 与 Ratta 团队沟通，参考官方方案 |
| 大文件性能 | 内存溢出、卡顿 | 流式解析，增量渲染 |
| 跨平台一致性 | 各平台渲染差异 | Core 层统一抽象，UI 层适配 |
| 替换操作撤销 | 替换后无法恢复到替换前状态 | 替换操作加入历史记录栈 |

---

## 模块功能清单

### Core 层模块 (Rust)

| 模块 | 功能 | 文件路径 |
|-----|------|---------|
| **Parser** | Markdown 解析、AST 构建、完整语法支持 | `src/parser/` |
| **Layout** | 排版引擎、字体度量 | `src/layout/` |
| **Render** | 绘制指令生成 | `src/render/` |
| **Search** | 全文搜索、正则匹配 | `src/search/` |
| **Replace** | 文本替换、全部替换、正则替换 | `src/replace/` |
| **History** | 撤销/重做栈 | `src/history/` |
| **Insert** | 快捷插入文本 API | `src/insert.rs` |
| **Bridge** | JNI/FFI 接口 | `src/bridge/` |

### Android 平台模块 (Kotlin)

| 模块 | 功能 | 文件路径 |
|-----|------|---------|
| **EditorView** | 自定义渲染 View、滚动、缩放 | `MarkdownEditorView.kt` |
| **EditorActivity** | 主界面、所有功能集成 | `MarkdownEditorActivity.kt` |
| **Search** | 搜索面板、高亮 | `search/SearchPanel.kt` |
| **Replace** | 替换面板、替换逻辑 | `MarkdownEditorActivity.kt` |
| **TOC** | 目录面板、适配器、缩进规则 | `toc/TocAdapter.kt` |
| **FileOps** | 文件新建、保存、状态检测 | `MarkdownEditorActivity.kt` |
| **UndoRedo** | 撤销/重做 UI、快捷键 | `MarkdownEditorActivity.kt` |
| **QuickToolbar** | 快捷工具栏、12 个按钮 | `MarkdownEditorActivity.kt` |
| **Keyboard** | 键盘检测、分屏切换、标识 | `KeyboardDetector.kt` |
| **SplitScreen** | 分屏模式、滚动同步 | `MarkdownEditorActivity.kt` |
| **Gesture** | Ratta 手势、坐标修正、修订模式 | `GestureEditor.kt` |
| **E-ink** | 局部刷新、刷新策略 | `EinkRefreshController.kt` |
| **JNI Bridge** | Rust 调用桥接 | `MarkdownCore.kt` |

### Desktop 平台模块 (Tauri + TypeScript)

| 模块 | 功能 | 文件路径 |
|-----|------|---------|
| **App** | 主应用、工具栏、状态管理 | `App.tsx` |
| **SplitView** | 分屏组件、编辑/预览同步 | `components/SplitView.tsx` |
| **useDocument** | 文档状态管理 Hook | `hooks/useDocument.ts` |
| **Types** | TypeScript 类型定义 | `types/index.ts` |
| **Styles** | 全局样式、CSS 变量 | `index.css` |
| **Tauri Backend** | Tauri 后端、FFI 集成 | `src-tauri/src/main.rs` |

### iOS 平台模块 (Swift - 可选)

| 模块 | 功能 | 文件路径 |
|-----|------|---------|
| **AppDelegate** | 应用入口、生命周期管理 | `AppDelegate.swift` |
| **MarkdownView** | 自定义渲染 View | `MarkdownView.swift` |
| **ViewController** | 主界面、工具栏 | `ViewController.swift` |
| **EditorController** | 编辑器逻辑、输入处理 | `EditorController.swift` |
| **PreviewController** | 预览模式、Markdown 渲染 | `PreviewController.swift` |
| **TOCController** | 目录面板、导航 | `TOCController.swift` |
| **SearchController** | 搜索面板、结果高亮 | `SearchController.swift` |
| **C-interop** | Rust Core 静态库链接 | `Bridging-Header.h` |

---

## 版本历史

| 版本 | 日期 | 变更内容 |
|------|------|---------|
| 1.1 | 2026-07-02 | • 补齐修订模式、快捷工具栏等 9 个关键模块<br>• 添加 Desktop/iOS 平台模块清单<br>• 新增目录、版本历史、参考资料 |
| 1.0 | 2026-07-02 | 初始版本，基础开发路线图 |

---

## 参考资料

### 项目文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 架构设计书 | [`《架构设计书 v2.0》.md`](《架构设计书 v2.0》.md) | 系统架构设计 |
| 详细设计文档 | [`详细设计文档.md`](详细设计文档.md) | 详细设计说明 |
| UI交互文档 | [`《UI交互文档》.md`](《UI交互文档》.md) | UI/UX 设计 |
| 测试清单 | [`TESTING_CHECKLIST.md`](TESTING_CHECKLIST.md) | 测试检查项 |
| 部署指南 | [`DEPLOYMENT_GUIDE.md`](DEPLOYMENT_GUIDE.md) | 部署说明 |
| 渲染功能 | [`MARKDOWN_RENDERING_FEATURES.md`](MARKDOWN_RENDERING_FEATURES.md) | Markdown 渲染支持 |
| 项目结构 | [`PROJECT_STRUCTURE.md`](PROJECT_STRUCTURE.md) | 目录结构说明 |
| 路线图分析 | [`ROADMAP_ANALYSIS.md`](ROADMAP_ANALYSIS.md) | 缺失模块分析 |

### 外部参考

| 资源 | 链接/说明 |
|------|----------|
| Rust 官方文档 | https://doc.rust-lang.org/ |
| Android NDK | https://developer.android.com/ndk |
| Tauri 文档 | https://tauri.app/ |
| CommonMark 规范 | https://commonmark.org/ |
| Supernote 开发 | Ratta SDK 文档 |

---

*文档生成时间: 2026-07-02*
*最后更新: 2026-07-02 v1.1*
