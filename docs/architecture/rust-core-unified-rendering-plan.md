# Rust Core 统一渲染方案

## 文档信息

| 属性 | 值 |
|------|-----|
| **项目名称** | NomadMark |
| **文档版本** | 1.0.0 |
| **创建日期** | 2026-07-08 |
| **目标** | 统一 Markdown 渲染到 Rust Core，移除 Markwon 依赖 |
| **预计周期** | 8-10 周 |
| **优先级** | P0（最高优先级） |

---

## 一、方案概述

### 1.1 背景与目标

**背景**：
- 当前项目使用双重渲染架构（Rust Core + Markwon）
- Markwon 仅支持 Android，无法满足跨平台需求
- 渲染一致性难以保证，技术债务累积

**目标**：
- 将所有 Markdown 渲染功能统一到 Rust Core
- 移除 Markwon 及相关依赖
- 建立跨平台一致的渲染引擎
- 优化 E-ink 显示效果

### 1.2 设计原则

```
┌─────────────────────────────────────────────────────────────┐
│                      设计原则                                 │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  1. 平台无关性                                               │
│     └─ Rust Core 不依赖任何平台特定代码                      │
│                                                               │
│  2. 渲染一致性                                               │
│     └─ 相同输入在所有平台产生相同输出                         │
│                                                               │
│  3. E-ink 优先                                               │
│     └─ 针对墨水屏优化高对比度、低刷新                         │
│                                                               │
│  4. 渐进迁移                                                 │
│     └─ 保留 Markwon 作为备用，逐步切换                        │
│                                                               │
│  5. 可测试性                                                 │
│     └─ 每个功能对应独立测试用例                               │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、功能分解

### 2.1 Markdown 语法支持清单

| 语法元素 | 当前状态 | 目标状态 | 优先级 | 复杂度 | 依赖 |
|----------|----------|----------|--------|--------|------|
| **标题 (H1-H6)** | ✅ Rust | ✅ Rust | P0 | 低 | - |
| **粗体** | ✅ Rust | ✅ Rust | P0 | 低 | - |
| **斜体** | ✅ Rust | ✅ Rust | P0 | 低 | - |
| **删除线** | ✅ Rust | ✅ Rust | P0 | 低 | - |
| **行内代码** | ✅ Rust | ✅ Rust | P0 | 低 | - |
| **代码块** | ✅ Rust | ✅ Rust | P0 | 中 | - |
| **链接** | ✅ Rust | ✅ Rust | P0 | 中 | - |
| **图片** | ✅ Rust | ✅ Rust | P0 | 中 | - |
| **无序列表** | ✅ Rust | ✅ Rust | P0 | 低 | - |
| **有序列表** | ✅ Rust | ✅ Rust | P0 | 低 | - |
| **任务列表** | ✅ Rust | ✅ Rust | P0 | 低 | - |
| **表格** | ✅ Rust | ✅ Rust | P0 | 中 | - |
| **引用块** | 🔄 Rust + Markwon | ✅ Rust | **P1** | 低 | - |
| **分割线** | 🔄 Rust + Markwon | ✅ Rust | **P1** | 低 | - |
| **代码高亮** | 🔄 Rust + Markwon | ✅ Rust | **P1** | 高 | - |
| **数学公式** | ❌ Markwon only | ✅ Rust | **P1** | 高 | KaTeX |
| **目录 (TOC)** | 🔄 Rust + Markwon | ✅ Rust | P2 | 中 | 标题解析 |
| **下划线** | ❌ - | ✅ Rust | P2 | 低 | - |
| **高亮** | ❌ - | ✅ Rust | P2 | 低 | - |
| **上标/下标** | ❌ - | ✅ Rust | P3 | 低 | - |
| **脚注** | ❌ - | ✅ Rust | P3 | 中 | - |
| **Callout** | ❌ - | ✅ Rust | P3 | 中 | - |

**状态说明**：
- ✅ Rust：已在 Rust Core 中实现
- 🔄：部分实现或使用双重实现
- ❌：尚未实现

### 2.2 优先级定义

| 优先级 | 说明 | 包含功能 |
|--------|------|----------|
| **P0** | 已实现，需验证和优化 | 基础 Markdown 语法 |
| **P1** | 当前缺失，必须实现 | 引用、分割线、代码高亮、数学公式 |
| **P2** | 重要增强功能 | 目录、下划线、高亮 |
| **P3** | 可选功能 | 上标/下标、脚注、Callout |

---

## 三、技术设计

### 3.1 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                      NomadMark 架构                            │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────────────────────────────┐       │
│  │                   Platform Layer                   │       │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐  │       │
│  │  │  Android   │  │  Desktop   │  │   Future   │  │       │
│  │  │   View     │  │   View     │  │   (Web?)   │  │       │
│  │  └────────────┘  └────────────┘  └────────────┘  │       │
│  └──────────────────────────────────────────────────┘       │
│                          ▲ FFI                                │
│  ┌──────────────────────────────────────────────────┐       │
│  │              Rust Core Layer                     │       │
│  │  ┌────────────────────────────────────────┐     │       │
│  │  │          Rendering Engine              │     │       │
│  │  │  ┌──────────┐  ┌──────────┐           │     │       │
│  │  │  │ Parser   │→│  Layout  │→ Render   │     │       │
│  │  │  └──────────┘  └──────────┘           │     │       │
│  │  │         │            │                 │     │       │
│  │  │         ▼            ▼                 │     │       │
│  │  │  ┌──────────┐  ┌──────────┐           │     │       │
│  │  │  │   AST    │  │ Commands │           │     │       │
│  │  │  └──────────┘  └──────────┘           │     │       │
│  │  └────────────────────────────────────────┘     │       │
│  │                                                  │       │
│  │  ┌────────────────────────────────────────┐     │       │
│  │  │         Math Rendering (KaTeX)         │     │       │
│  │  └────────────────────────────────────────┘     │       │
│  └──────────────────────────────────────────────────┘       │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 AST 扩展设计

```rust
// core/src/parser/ast.rs

/// 完整的 Markdown 块级节点
pub enum BlockNode {
    // 现有节点
    Heading { level: u8, children: Vec<InlineNode> },
    Paragraph { children: Vec<InlineNode> },
    CodeBlock { language: Option<String>, content: String },
    List { ordered: bool, start_number: Option<usize>, items: Vec<ListItem> },
    Table { headers: Vec<Vec<InlineNode>>, rows: Vec<Vec<Vec<InlineNode>>>, alignments: Vec<TableCellAlignment> },
    ThematicBreak,
    HtmlBlock { content: String },
    ReferenceDef { label: String, dest: String, title: Option<String> },
    
    // 新增节点
    Blockquote { level: u8, children: Vec<BlockNode> },
    MathBlock { latex: String },
    Callout { kind: CalloutKind, title: Option<String>, children: Vec<BlockNode> },
}

/// 新增：Callout 类型
pub enum CalloutKind {
    Info,     // ℹ️ 
    Warning,  // ⚠️ 
    Note,     // 📝
    Tip,      // 💡
    Important,// ❗
    Caution,  // ⛔
}

/// 完整的 Markdown 行内节点
pub enum InlineNode {
    // 现有节点
    Text(String),
    SoftBreak,
    HardBreak,
    Emphasis { children: Vec<InlineNode>, level: u8 },
    Strong { children: Vec<InlineNode> },
    Strikethrough { children: Vec<InlineNode> },
    Code(String),
    Link { dest: String, title: Option<String>, children: Vec<InlineNode> },
    Image { dest: String, title: Option<String>, alt: Vec<InlineNode> },
    Html(String),
    
    // 新增节点
    Underline { children: Vec<InlineNode> },
    Highlight { children: Vec<InlineNode> },
    Superscript { children: Vec<InlineNode> },
    Subscript { children: Vec<InlineNode> },
    Math { display_mode: bool, latex: String },
    FootnoteRef { ref_id: String },
}
```

### 3.3 渲染命令扩展

```rust
// core/src/render/commands.rs

/// 渲染命令类型
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RenderCommandType {
    DrawText = 0,
    FillRect = 1,
    DrawLine = 2,
    DrawImage = 3,
    
    // 新增命令类型
    DrawRoundedRect = 4,     // 圆角矩形（Callout、代码块）
    DrawBorder = 5,          // 边框（表格、引用块）
    DrawIcon = 6,            // 图标（任务列表、目录）
}

/// 渲染命令
#[repr(C)]
#[derive(Clone, Copy)]
pub struct RenderCommand {
    pub cmd_type: RenderCommandType,
    pub x: f32,
    pub y: f32,
    pub width: f32,
    pub height: f32,
    pub color: Color,
    pub data: RenderCommandData,
}

/// 命令数据
#[repr(C)]
#[derive(Clone, Copy)]
pub union RenderCommandData {
    pub text: TextData,
    pub line: LineData,
    pub image: ImageData,
    pub border: BorderData,     // 新增：边框数据
    pub icon: IconData,         // 新增：图标数据
    pub raw: [u8; 24],
}

/// 新增：边框数据
#[repr(C)]
#[derive(Clone, Copy)]
pub struct BorderData {
    pub width: f32,
    pub style: BorderStyle,      // Solid, Dashed, Dotted
    pub radius: f32,             // 圆角半径
    pub _pad: [u8; 12],
}

/// 新增：边框样式
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum BorderStyle {
    Solid = 0,
    Dashed = 1,
    Dotted = 2,
}

/// 新增：图标数据
#[repr(C)]
#[derive(Clone, Copy)]
pub struct IconData {
    pub icon_type: IconType,
    pub size: f32,
    pub _pad: [u8; 16],
}

/// 新增：图标类型
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum IconType {
    CheckboxUnchecked = 0,
    CheckboxChecked = 1,
    Bullet = 2,
    Number = 3,
    Arrow = 4,
    Info = 5,
    Warning = 6,
}
```

### 3.4 数学公式渲染方案

#### 方案选择：KaTeX C FFI

使用 KaTeX 的 C 接口通过 FFI 集成到 Rust Core。

```
Rust Core
    │
    ├─ libkatex.so (动态链接 KaTeX C 库)
    │   ├─ 行内公式渲染 → SVG → 位图
    │   └─ 块级公式渲染 → SVG → 位图
    │
    └─ RenderCommand::DrawImage (渲染公式位图)
```

**依赖**：
```toml
# Cargo.toml
[dependencies]
libc = "0.2"

# 链接系统库或预编译的 KaTeX
[build-dependencies]
cc = "1.0"
```

**FFI 接口设计**：
```rust
// core/src/math/katex.rs

use libc::{c_char, c_int, size_t};

#[repr(C)]
pub struct KatexResult {
    pub svg_ptr: *mut c_char,
    pub svg_len: size_t,
    pub width: f32,
    pub height: f32,
}

extern "C" {
    /// 渲染 LaTeX 为 SVG
    fn katex_render(
        latex: *const c_char,
        latex_len: size_t,
        display_mode: c_int,
        out: *mut KatexResult,
    ) -> c_int;
    
    /// 释放 KaTeX 结果
    fn katex_free_result(result: *mut KatexResult);
}

pub fn render_latex(latex: &str, display_mode: bool) -> Result<(Vec<u8>, f32, f32), String> {
    // 实现逻辑
}
```

### 3.5 代码高亮方案

使用 **syntect** 库（Rust 的 TextMate 语法高亮）：

```toml
# Cargo.toml
[dependencies]
syntect = "5.0"
```

```rust
// core/src/syntax/highlighter.rs

use syntect::{parsing::SyntaxSet, highlighting::ThemeSet};

pub struct SyntaxHighlighter {
    syntax_set: SyntaxSet,
    theme: Theme,
}

impl SyntaxHighlighter {
    pub fn new() -> Self {
        let syntax_set = SyntaxSet::load_defaults_newlines();
        let theme = ThemeSet::load_defaults().themes["base16-ocean.dark"].clone();
        Self { syntax_set, theme }
    }
    
    pub fn highlight(&self, code: &str, lang: &str) -> Vec<HighlightToken> {
        // 返回带颜色信息的 token 序列
    }
}

pub struct HighlightToken {
    pub text: String,
    pub color: Color,
}
```

---

## 四、实施路线图

### 4.1 总体时间规划

```
Week 1-2:  ━━━━━━━━━━━━━━━━━━ Phase 1: 基础功能完善
Week 3-4:  ━━━━━━━━━━━━━━━━━━ Phase 2: 数学公式集成
Week 5-6:  ━━━━━━━━━━━━━━━━━━ Phase 3: 代码高亮
Week 7-8:  ━━━━━━━━━━━━━━━━━━ Phase 4: 高级功能
Week 9-10: ━━━━━━━━━━━━━━━━━━ Phase 5: 测试与优化
```

### 4.2 详细阶段规划

#### Phase 1: 基础功能完善 (Week 1-2)

**目标**：实现 P1 优先级的基础功能

| 任务 | 工作量 | 负责模块 | 交付物 |
|------|--------|----------|--------|
| 引用块解析 | 2天 | parser/ast.rs | Blockquote 节点 |
| 引用块渲染 | 1天 | render/commands.rs | 渲染命令 |
| 分割线解析 | 1天 | parser/ast.rs | ThematicBreak 处理 |
| 分割线渲染 | 1天 | render/commands.rs | 线条绘制 |
| Android 集成 | 3天 | platforms/android/ | FFI 调用 |
| 测试与验证 | 2天 | tests/ | 单元测试 |

**验收标准**：
- [ ] 引用块支持嵌套
- [ ] 分割线正确显示
- [ ] Android 端预览正确显示
- [ ] 通过所有单元测试

---

#### Phase 2: 数学公式集成 (Week 3-4)

**目标**：集成 KaTeX，支持行内和块级公式

| 任务 | 工作量 | 负责模块 | 交付物 |
|------|--------|----------|--------|
| KaTeX C 库编译 | 2天 | build.rs | libkatex.so |
| FFI 接口实现 | 2天 | math/katex.rs | 绑定代码 |
| LaTeX 解析 | 2天 | parser/ast.rs | Math 节点 |
| 公式渲染 | 2天 | render/commands.rs | 位图渲染 |
| Android 集成 | 1天 | platforms/android/ | 测试 |
| 测试 | 1天 | tests/ | 公式测试集 |

**验收标准**：
- [ ] 行内公式 `$E=mc^2$` 正确显示
- [ ] 块级公式 `$$...$$` 正确显示
- [ ] 支持常用 LaTeX 语法（分数、根号、积分）
- [ ] E-ink 显示清晰

---

#### Phase 3: 代码高亮 (Week 5-6)

**目标**：实现代码语法高亮

| 任务 | 工作量 | 负责模块 | 交付物 |
|------|--------|----------|--------|
| syntect 集成 | 2天 | syntax/highlighter.rs | 高亮器 |
| 主题适配 | 2天 | syntax/theme.rs | E-ink 主题 |
| Token 渲染 | 2天 | render/commands.rs | 颜色渲染 |
| 语言支持 | 2天 | syntax/languages.rs | 常用语言 |
| 测试 | 2天 | tests/ | 高亮测试 |

**验收标准**：
- [ ] 支持 10+ 种编程语言
- [ ] E-ink 高对比度配色
- [ ] 高亮性能可接受（<100ms per block）

---

#### Phase 4: 高级功能 (Week 7-8)

**目标**：实现目录、下划线、高亮等功能

| 任务 | 工作量 | 负责模块 | 交付物 |
|------|--------|----------|--------|
| 目录生成 | 2天 | parser/toc.rs | TOC 节点 |
| 目录渲染 | 1天 | render/commands.rs | 目录视图 |
| 下划线/高亮 | 2天 | parser/ast.rs | Inline 节点 |
| Callout 支持 | 3天 | parser/callout.rs | Callout 节点 |
| Android 集成 | 2天 | platforms/android/ | UI 调用 |

**验收标准**：
- [ ] 目录可点击跳转
- [ ] 下划线、高亮正确显示
- [ ] Callout 样式美观

---

#### Phase 5: 测试与优化 (Week 9-10)

**目标**：全面测试、性能优化、移除 Markwon

| 任务 | 工作量 | 说明 |
|------|--------|------|
| 渲染对比测试 | 3天 | 与 Markwon 效果对比 |
| E-ink 显示测试 | 2天 | 真机测试 |
| 性能优化 | 2天 | 渲染速度优化 |
| 内存优化 | 1天 | 大文件支持 |
| 移除 Markwon | 1天 | 清理依赖 |
| 文档更新 | 1天 | 更新技术文档 |

**验收标准**：
- [ ] 所有测试通过
- [ ] 渲染效果与 Markwon 一致或更好
- [ ] E-ink 显示无闪烁
- [ ] 大文件（>10MB）可正常渲染
- [ ] Markwon 依赖已移除

### 4.3 里程碑

| 里程碑 | 日期 | 标志 |
|--------|------|------|
| M1: Phase 1 完成 | Week 2 结束 | 引用块、分割线可用 |
| M2: Phase 2 完成 | Week 4 结束 | 数学公式可用 |
| M3: Phase 3 完成 | Week 6 结束 | 代码高亮可用 |
| M4: Phase 4 完成 | Week 8 结束 | 所有功能完成 |
| M5: Phase 5 完成 | Week 10 结束 | **正式发布** |

---

## 五、风险管理

### 5.1 技术风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| KaTeX FFI 集成困难 | 高 | 中 | 提前验证 FFI 方案，准备备选方案 |
| 代码高亮性能差 | 中 | 中 | 使用缓存，异步渲染 |
| E-ink 显示效果差 | 高 | 低 | 真机测试，调整配色 |
| 渲染不一致 | 高 | 中 | 建立测试集，对比验证 |

### 5.2 回退方案

每个 Phase 完成后，如果出现问题：
1. **保留 Markwon**：作为备用渲染引擎
2. **功能开关**：通过配置选择使用哪个渲染器
3. **渐进回退**：部分功能可回退到 Markwon

### 5.3 备选方案

| 问题 | 备选方案 |
|------|----------|
| KaTeX 集成失败 | 使用 JLatexMath（Android 端） |
| syntect 性能差 | 手写简单高亮器 |
| 渲染命令不够 | 扩展 RenderCommandType |

---

## 六、测试策略

### 6.1 测试金字塔

```
                    ┌─────────────┐
                    │   E2E 测试   │  10%
                    │  (真机测试)  │
                    ├─────────────┤
                    │  集成测试   │  20%
                    │ (FFI 调用)   │
                    ├─────────────┤
                    │  单元测试   │  70%
                    │(Rust 代码)   │
                    └─────────────┘
```

### 6.2 渲染测试集

创建标准 Markdown 测试集：

```
tests/render/
├── basic/
│   ├── headings.md
│   ├── emphasis.md
│   └── lists.md
├── advanced/
│   ├── tables.md
│   ├── math.md
│   └── code_blocks.md
└── eink/
    ├── contrast.md
    └── large_files.md
```

### 6.3 验收标准

每个功能必须满足：

1. **正确性**：输出与标准 Markdown 规范一致
2. **一致性**：所有平台渲染效果相同
3. **性能**：渲染时间在可接受范围内
4. **E-ink 友好**：高对比度，低刷新

---

## 七、交付物

### 7.1 代码交付物

| 模块 | 文件 | 说明 |
|------|------|------|
| Parser | `core/src/parser/` | Markdown 解析器 |
| AST | `core/src/parser/ast.rs` | 节点定义 |
| Layout | `core/src/layout/` | 布局引擎 |
| Render | `core/src/render/` | 渲染命令 |
| Math | `core/src/math/` | 数学公式 |
| Syntax | `core/src/syntax/` | 代码高亮 |
| FFI | `core/src/bridge/` | JNI 接口 |

### 7.2 文档交付物

| 文档 | 用途 |
|------|------|
| 本文档 | 总体方案和路线图 |
| API 文档 | FFI 接口说明 |
| 测试报告 | 测试结果和覆盖率 |
| 迁移指南 | 从 Markwon 迁移步骤 |

---

## 八、附录

### 8.1 术语表

| 术语 | 说明 |
|------|------|
| AST | Abstract Syntax Tree，抽象语法树 |
| FFI | Foreign Function Interface，外部函数接口 |
| E-ink | Electronic Ink，电子墨水屏 |
| KaTeX | 快速 LaTeX 渲染库 |

### 8.2 参考资源

- [CommonMark Spec](https://spec.commonmark.org/)
- [KaTeX Documentation](https://katex.org/)
- [syntect Documentation](https://docs.rs/syntect/)
- [Supernote Developer Guide](https://supernote.com/developer)

---

**文档结束**
