# NomadMark 缺失功能分析报告

> **生成日期**: 2026-07-02
> **分析范围**: 阶段 1 (Core 层) + 阶段 2 (Android 平台)
> **项目状态**: 约 60-70% 完成

---

## 目录

1. [概述](#概述)
2. [阶段 1: Core 层缺失功能](#阶段-1-core-层缺失功能)
3. [阶段 2: Android 平台缺失功能](#阶段-2-android-平台缺失功能)
4. [完成度统计](#完成度统计)
5. [优先级建议](#优先级建议)
6. [下一步行动计划](#下一步行动计划)

---

## 概述

本文档分析了 NomadMark Markdown 编辑器项目当前的开发进度，重点关注阶段 1 (Core 层 Rust 开发) 和阶段 2 (Android 平台开发) 的缺失功能。

### 当前项目状态

- ✅ **准备阶段**: 100% 完成
- ⚠️ **Core 层**: 约 50% 完成（基础架构完成，核心功能模块缺失）
- ⚠️ **Android 平台**: 约 67% 完成（基础 UI 完成，高级功能待完善）
- ❌ **Desktop 平台**: 约 50% 完成（框架已建立）
- ❌ **iOS 平台**: 0% 完成

---

## 阶段 1: Core 层缺失功能

### 缺失模块总览

| 步骤 | 模块 | 路线图要求 | 当前状态 | 优先级 |
|------|------|-----------|----------|--------|
| **1.6** | **搜索模块** | 全文搜索、正则表达式支持 | ❌ 目录为空，未实现 | P1 |
| **1.7** | **替换模块** | 文本替换、全部替换、正则替换、撤销替换 | ❌ 模块完全不存在 | P1 |
| **1.8** | **历史记录** | 撤销/重做栈实现 | ❌ 目录为空，未实现 | P1 |
| **1.10** | **快捷插入 API** | 插入标题/粗体/斜体/代码/链接/列表/表格 | ❌ 模块不存在 | P1 |
| **1.11** | **文档创建** | md_document_create() 从内存内容创建 | ⚠️ 标记为 TODO | P0 |
| **1.12** | **搜索 FFI** | md_document_search() 实现 | ⚠️ 标记为 TODO | P1 |
| **1.13** | **撤销/重做 FFI** | md_document_undo/redo() 实现 | ⚠️ 标记为 TODO | P1 |
| **1.14** | **文档保存** | Core::save() 方法实现 | ⚠️ 标记为 TODO | P0 |

---

### 1.6 搜索模块 (src/search/)

#### 当前状态
```
core/src/search/  # 目录存在但为空
```

#### 需要实现的功能
- 全文搜索功能
- 正则表达式搜索支持
- 大小写敏感/不敏感选项
- 搜索结果高亮区域返回

#### 建议文件结构
```
core/src/search/
├── mod.rs           # 模块入口，导出公共接口
├── searcher.rs      # 搜索算法实现
└── regex.rs         # 正则表达式搜索（可选）
```

#### FFI 接口需求
```rust
// 已声明但未实现
pub extern "C" fn md_document_search(
    handle: *mut MarkdownDocument,
    query: *const c_char,
    query_len: usize,
    out_results: *mut *const bridge::types::SearchResult,
    out_count: *mut usize,
) -> i32
```

---

### 1.7 替换模块 (src/replace/)

#### 当前状态
```
# 目录完全不存在
```

#### 需要实现的功能
- 单个替换
- 全部替换
- 正则表达式替换
- 替换操作的撤销支持

#### 建议文件结构
```
core/src/replace/
├── mod.rs           # 模块入口
├── replacer.rs      # 替换逻辑实现
└── history.rs       # 替换历史记录
```

#### 与历史记录的集成
替换操作应该记录到历史栈中，以便撤销：
```
用户操作 -> 执行替换 -> 记录到历史栈 -> 渲染更新
```

---

### 1.8 历史记录模块 (src/history/)

#### 当前状态
```
core/src/history/  # 目录存在但为空
```

#### 需要实现的功能
- 撤销栈 (Undo Stack)
- 重做栈 (Redo Stack)
- 操作命令模式 (Command Pattern)
- 内存限制（防止栈过大）

#### 建议文件结构
```
core/src/history/
├── mod.rs           # 模块入口
├── stack.rs         # 撤销/重做栈实现
└── command.rs       # 操作命令定义
```

#### FFI 接口需求
```rust
// 已声明但未实现
pub extern "C" fn md_document_undo(handle: *mut MarkdownDocument) -> i32
pub extern "C" fn md_document_redo(handle: *mut MarkdownDocument) -> i32
```

---

### 1.10 快捷插入 API (src/insert.rs)

#### 当前状态
```
# 文件不存在
```

#### 需要实现的 API
- 插入标题 (H1-H6)
- 插入粗体/斜体/删除线
- 插入代码块/行内代码
- 插入链接/图片
- 插入列表（有序/无序/任务）
- 插入表格

#### 建议接口设计
```rust
// 示例 API
pub fn insert_heading(level: u8, text: &str) -> String
pub fn insert_bold(text: &str) -> String
pub fn insert_link(text: &str, url: &str) -> String
pub fn insert_table(rows: usize, cols: usize) -> String
```

---

### 1.11 文档创建 (md_document_create)

#### 当前状态
```rust
// lib.rs:166 - TODO 标记
pub extern "C" fn md_document_create(...) -> *mut MarkdownDocument {
    // ...
    std::ptr::null_mut()  // TODO: Implement
}
```

#### 需要实现
- 从内存字节数组创建文档
- 小文件快速加载（<50MB 不使用 mmap）
- 错误处理

---

### Core 层缺失文件清单

```
core/src/
├── history/              # ❌ 空目录
│   ├── mod.rs           # 需创建：模块入口
│   ├── stack.rs         # 需创建：撤销/重做栈
│   └── command.rs       # 需创建：操作命令定义
│
├── search/               # ❌ 空目录
│   ├── mod.rs           # 需创建：搜索入口
│   ├── searcher.rs      # 需创建：搜索实现
│   └── regex.rs         # 需创建：正则搜索（可选）
│
├── replace/              # ❌ 目录不存在
│   ├── mod.rs           # 需创建：替换入口
│   ├── replacer.rs      # 需创建：替换实现
│   └── history.rs       # 需创建：替换历史
│
└── insert.rs            # ❌ 文件不存在：快捷插入 API
```

---

## 阶段 2: Android 平台缺失功能

### 缺失模块总览

| 步骤 | 模块 | 路线图要求 | 当前状态 | 优先级 |
|------|------|-----------|----------|--------|
| **2.13** | **文件操作细化** | 新建文件对话框、文件名验证、自动保存 | ⚠️ 部分实现 | P0 |
| **2.14** | **撤销/重做 Core集成** | 与 Core history 模块集成、Ctrl+Z/Y | ⚠️ 使用简单内存栈 | P1 |
| **2.15** | **快捷工具栏动画** | 展开/收起动画 | ⚠️ 基本功能存在 | P1 |
| **2.16** | **键盘适配模块** | KeyboardDetector.kt 独立类 | ❌ 未实现 | P0 |
| **2.17** | **分屏滚动同步** | 编辑区与预览区滚动位置同步 | ⚠️ 需细化 | P1 |
| **2.18** | **双指缩放** | 双指缩放支持、惯性滚动 | ⚠️ 部分实现 | P1 |
| **2.19** | **修订模式细化** | Ratta SDK 详细集成、坐标修正 | ⚠️ 基础框架存在 | P2 |
| **2.20** | **E-ink 刷新细化** | 局部刷新区域计算、刷新模式切换 | ⚠️ 嵌套类存在 | P0 |

---

### 2.13 文件操作模块细化

#### 当前状态
`MarkdownEditorActivity.kt` 中已有基本文件操作：
- ✅ loadFile() - 加载文件
- ✅ saveFile() - 保存文件
- ✅ createNewFilePath() - 创建新文件路径
- ⚠️ 缺少：新建文件对话框、文件名验证

#### 需要完善
1. **新建文件对话框** - 用户输入文件名的 UI
2. **文件名验证** - 检查非法字符、重名等
3. **自动保存** - 定时保存或修改后自动保存
4. **保存状态检测** - `isModified` 标志已有，但可更细致

---

### 2.14 撤销/重做 Core 集成

#### 当前状态
```kotlin
// MarkdownEditorActivity.kt:153-154
private val undoStack = mutableListOf<String>()
private val redoStack = mutableListOf<String>()
```

使用简单的内存栈保存完整文本，**未与 Core 层集成**。

#### 需要改进
1. 等待 Core 层 history 模块完成后
2. 调用 FFI 接口 `md_document_undo()` 和 `md_document_redo()`
3. 支持增量撤销（而非整文档替换）
4. 添加 Ctrl+Z/Ctrl+Y 快捷键支持

---

### 2.15 快捷工具栏动画

#### 当前状态
```kotlin
// MarkdownEditorActivity.kt:606-612
private fun toggleBottomToolbar() {
    if (toolbarBottom.visibility == View.VISIBLE) {
        toolbarBottom.visibility = View.GONE
    } else {
        toolbarBottom.visibility = View.VISIBLE
    }
}
```

简单的显示/隐藏切换，**无动画效果**。

#### 建议改进
```kotlin
// 使用属性动画实现平滑展开/收起
private fun toggleBottomToolbar() {
    if (isToolbarExpanded) {
        // 收起动画
        ObjectAnimator.ofFloat(toolbarBottom, "translationY", 0f, height).start()
    } else {
        // 展开动画
        ObjectAnimator.ofFloat(toolbarBottom, "translationY", height, 0f).start()
    }
    isToolbarExpanded = !isToolbarExpanded
}
```

---

### 2.16 键盘适配模块

#### 当前状态
```kotlin
// MarkdownEditorActivity.kt:1015-1019
private fun detectKeyboardStatus() {
    // 检测是否有外接键盘
    // 这需要实际设备测试，这里默认显示
    // keyboardIndicator.visibility = View.VISIBLE
}
```

**完全未实现**。

#### 需要实现功能
1. **F11 键盘检测** - 识别 F11 物理键盘
2. **分屏比例切换** - 50:50 / 40:60 切换
3. **键盘标识显示** - 显示当前键盘类型
4. **软键盘唤起逻辑** - 根据键盘类型自动调整

#### 建议文件结构
```kotlin
// KeyboardDetector.kt
class KeyboardDetector(private val context: Context) {
    fun detectKeyboardType(): KeyboardType
    fun isPhysicalKeyboardPresent(): Boolean
    fun getOptimalSplitRatio(): Float
}

enum class KeyboardType {
    NONE,           // 无键盘
    SOFT_KEYBOARD,  // 软键盘
    F11_PHYSICAL    // F11 物理键盘
}
```

---

### 2.17 分屏滚动同步

#### 当前状态
分屏模式已实现，但编辑区和预览区的**滚动位置未同步**。

#### 需要实现
1. 监听编辑区滚动事件
2. 计算对应的预览区位置
3. 同步滚动预览区
4. 反向同步（预览区 → 编辑区）

#### 建议实现
```kotlin
// ScrollSyncManager.kt
class ScrollSyncManager {
    fun syncEditorToPreview(scrollY: Int)
    fun syncPreviewToEditor(scrollY: Int)
    fun calculatePreviewPosition(editorScrollY: Int): Int
}
```

---

### 2.18 双指缩放与滚动

#### 当前状态
基础的点击滚动已实现，**双指缩放和惯性滚动未完善**。

#### 需要实现
1. **双指缩放** - onScale() 事件处理
2. **惯性滚动** - fling 效果
3. **双指滑动** - 平滑滚动

---

### 2.19 修订模式细化

#### 当前状态
基础框架已有：
- ✅ 修订模式切换
- ✅ 手势覆盖层
- ✅ 删除手势
- ⚠️ 插入手势（"开发中"）
- ⚠️ 选择手势（"开发中"）

#### 需要完善
1. **Ratta SDK 详细集成**
2. **坐标修正逻辑** - 手势坐标到文本坐标的转换
3. **插入手势实现**
4. **选择手势实现**
5. **手写延迟优化**

---

### 2.20 E-ink 刷新细化

#### 当前状态
`EinkRefreshController` 作为**内部类嵌套**在 `MarkdownEditorView.kt` 中。

#### 当前问题
1. 嵌套类不利于测试和维护
2. 刷新策略可能需要更多细化
3. 局部刷新区域计算可能需要优化

#### 建议改进
1. 将 `EinkRefreshController` 提取为**独立文件**
2. 实现更精细的局部刷新区域计算
3. 添加刷新模式切换（全局/局部/智能）

---

### Android 平台缺失文件清单

```
android/app/src/main/java/com/editor/nomadmark/
├── KeyboardDetector.kt           # ❌ 不存在：键盘类型检测、分屏比例切换
├── ScrollSyncManager.kt          # ⚠️ 建议：分屏滚动同步管理器
├── FileOperationHelper.kt        # ⚠️ 建议：文件操作辅助类
└── EinkRefreshController.kt      # ⚠️ 建议从内部类提取为独立文件
```

---

## 完成度统计

### Core 层完成度

| 模块类别 | 已完成 | 待完成 | 完成率 |
|----------|--------|--------|--------|
| 基础架构 (AST/解析/渲染) | 4 | 0 | 100% |
| 核心功能 (搜索/替换/历史) | 0 | 3 | 0% |
| FFI 接口 | 部分 | 4 | 50% |
| 快捷插入 API | 0 | 1 | 0% |
| **总计** | **4** | **8** | **~33%** |

### Android 平台完成度

| 模块类别 | 已完成 | 待完成 | 完成率 |
|----------|--------|--------|--------|
| 基础 UI (View/Activity) | 3 | 0 | 100% |
| 功能模块 (搜索/目录/手势) | 5 | 0 | 100% |
| 高级功能 (键盘/滚动同步) | 0 | 4 | 0% |
| 细化功能 (动画/刷新策略) | 部分 | 4 | 50% |
| **总计** | **8** | **8** | **~50%** |

---

## 优先级建议

### P0 (必须完成) - 阻塞核心功能

| 序号 | 模块 | 预计工时 | 依赖 |
|------|------|----------|------|
| 1 | Core: md_document_create 实现 | 4h | - |
| 2 | Core: 搜索模块 (search/) | 8h | - |
| 3 | Core: 替换模块 (replace/) | 6h | - |
| 4 | Core: 历史记录 (history/) | 6h | - |
| 5 | Android: 键盘适配模块 (KeyboardDetector.kt) | 4h | - |

**小计: 28 小时**

### P1 (重要功能) - 影响用户体验

| 序号 | 模块 | 预计工时 | 依赖 |
|------|------|----------|------|
| 1 | Core: 快捷插入 API (insert.rs) | 4h | - |
| 2 | Core: 搜索 FFI 实现 | 2h | search/ |
| 3 | Core: 撤销/重做 FFI 实现 | 2h | history/ |
| 4 | Core: 文档保存 (Core::save) | 2h | - |
| 5 | Android: 分屏滚动同步 | 4h | - |
| 6 | Android: 双指缩放完善 | 4h | - |
| 7 | Android: 文件操作细化 | 3h | - |

**小计: 21 小时**

### P2 (可选功能) - 可后续优化

| 序号 | 模块 | 预计工时 | 依赖 |
|------|------|----------|------|
| 1 | Android: 修订模式细化 | 8h | Ratta SDK |
| 2 | Android: 快捷工具栏动画 | 2h | - |
| 3 | Android: E-ink 刷新策略优化 | 4h | - |

**小计: 14 小时**

---

## 下一步行动计划

### 立即行动 (本周)

1. **Core 层优先实现**（按顺序）：
   - [ ] `md_document_create()` 实现
   - [ ] 搜索模块 (search/) 创建
   - [ ] 替换模块 (replace/) 创建
   - [ ] 历史记录 (history/) 创建

2. **Android 平台优先实现**：
   - [ ] KeyboardDetector.kt 创建
   - [ ] 文件操作细化

### 短期计划 (2周内)

1. **Core 层**：
   - [ ] 快捷插入 API (insert.rs)
   - [ ] FFI 接口完善 (search/undo/redo)
   - [ ] 单元测试覆盖

2. **Android 平台**：
   - [ ] 分屏滚动同步
   - [ ] 双指缩放
   - [ ] Core 层集成

### 中期计划 (1个月内)

1. **P2 功能**：
   - [ ] 修订模式细化
   - [ ] E-ink 刷新优化
   - [ ] 快捷工具栏动画

2. **测试与优化**：
   - [ ] 联调测试
   - [ ] 性能优化
   - [ ] E-ink 实机测试

---

## 附录

### 参考文档

- [DEVELOPMENT_ROADMAP.md](./DEVELOPMENT_ROADMAP.md) - 开发路线图
- [《架构设计书 v2.0》.md](./《架构设计书 v2.0》.md) - 系统架构设计
- [详细设计文档.md](./详细设计文档.md) - 详细设计说明
- [ROADMAP_ANALYSIS.md](./ROADMAP_ANALYSIS.md) - 缺失模块分析

### 代码位置

- **Core 层**: `core/src/`
- **Android 平台**: `android/app/src/main/java/com/editor/nomadmark/`
- **测试代码**: `tests/`

---

*文档生成时间: 2026-07-02*
*分析基于版本: DEVELOPMENT_ROADMAP.md v1.1*
