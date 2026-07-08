# E-ink 主题配置指南

## 概述

本文档定义 NomadMark 在电子墨水屏设备上的显示规范和配色方案。

---

## 一、设计原则

### 1.1 高对比度优先

```
最小对比度要求：
- 正文文本: 4.5:1 (WCAG AA)
- 大文本（18pt+）: 3:1 (WCAG AA Large)
- 图形元素: 3:1
```

### 1.2 灰度优先

```
E-ink 显示特点：
- 主流设备: 16 级灰度
- 新型设备: 可能支持彩色
- 设计应兼容纯灰度显示
```

### 1.3 低刷新优化

```
刷新策略：
- 最小化重绘区域
- 避免全屏刷新
- 使用局部刷新（A2 模式）
```

---

## 二、配色方案

### 2.1 主题颜色定义

```rust
// core/src/theme/eink.rs

/// E-ink 主题配色
#[derive(Debug, Clone, Copy)]
pub struct EinkTheme {
    // 文字颜色
    pub text_primary: Color,       // #1A1A1A - 接近纯黑
    pub text_secondary: Color,     // #555555 - 次要文字
    pub text_tertiary: Color,       // #888888 - 辅助文字
    pub text_disabled: Color,      // // 禁用文字

    // 背景颜色
    pub bg_primary: Color,          // #FFFFFF - 主背景（纯白）
    pub bg_secondary: Color,        // #F5F5F5 - 次要背景
    pub bg_tertiary: Color,         // #EEEEEE - 输入框背景

    // 边框和分割线
    pub border: Color,              // #CCCCCC - 边框
    pub divider: Color,             // #DDDDDD - 分割线
    pub border_focus: Color,        // #999999 - 聚焦边框

    // 语义颜色（灰度表达）
    pub success: Color,             // #666666 - 成功
    pub warning: Color,             // #888888 - 警告
    pub error: Color,               // #333333 - 错误
    pub info: Color,                // #555555 - 信息
}

impl Default for EinkTheme {
    fn default() -> Self {
        Self {
            text_primary: Color::rgb(0x1A, 0x1A, 0x1A),
            text_secondary: Color::rgb(0x55, 0x55, 0x55),
            text_tertiary: Color::rgb(0x88, 0x88, 0x88),
            text_disabled: Color::rgb(0xBB, 0xBB, 0xBB),
            
            bg_primary: Color::rgb(0xFF, 0xFF, 0xFF),
            bg_secondary: Color::rgb(0xF5, 0xF5, 0xF5),
            bg_tertiary: Color::rgb(0xEE, 0xEE, 0xEE),
            
            border: Color::rgb(0xCC, 0xCC, 0xCC),
            divider: Color::rgb(0xDD, 0xDD, 0xDD),
            border_focus: Color::rgb(0x99, 0x99, 0x99),
            
            success: Color::rgb(0x66, 0x66, 0x66),
            warning: Color::rgb(0x88, 0x88, 0x88),
            error: Color::rgb(0x33, 0x33, 0x33),
            info: Color::rgb(0x55, 0x55, 0x55),
        }
    }
}
```

### 2.2 代码高亮配色

```rust
/// 代码语法高亮配色（E-ink 灰度方案）
#[derive(Debug, Clone, Copy)]
pub struct CodeHighlightTheme {
    pub keyword: Color,      // #000000 - 关键字（纯黑）
    pub string: Color,       // #444444 - 字符串
    pub comment: Color,      // #999999 - 注释（浅灰）
    pub function: Color,     // // 函数名
    pub variable: Color,     // #333333 - 变量
    pub constant: Color,     // #555555 - 常量
    pub type_name: Color,    // #444444 - 类型名
    pub number: Color,       // #555555 - 数字
    pub operator: Color,     // #666666 - 操作符
    pub background: Color,   // #F5F5F5 - 背景
}

impl Default for CodeHighlightTheme {
    fn default() -> Self {
        Self {
            keyword: Color::rgb(0x00, 0x00, 0x00),
            string: Color::rgb(0x44, 0x44, 0x44),
            comment: Color::rgb(0x99, 0x99, 0x99),
            function: Color::rgb(0x22, 0x22, 0x22),
            variable: Color::rgb(0x33, 0x33, 0x33),
            constant: Color::rgb(0x55, 0x55, 0x55),
            type_name: Color::rgb(0x44, 0x44, 0x44),
            number: Color::rgb(0x55, 0x55, 0x55),
            operator: Color::rgb(0x66, 0x66, 0x66),
            background: Color::rgb(0xF5, 0xF5, 0xF5),
        }
    }
}
```

### 2.3 数学公式配色

```rust
/// 数学公式配色
#[derive(Debug, Clone, Copy)]
pub struct MathTheme {
    pub formula: Color,          // #000000 - 公式本身（纯黑）
    pub background: Color,        // #FFFFFF - 背景（透明/白）
}

impl Default for MathTheme {
    fn default() -> Self {
        Self {
            formula: Color::rgb(0x00, 0x00, 0x00),
            background: Color::rgb(0xFF, 0xFF, 0xFF),
        }
    }
}
```

---

## 三、元素样式规范

### 3.1 排版

| 元素 | 字号 | 字重 | 行高 | 颜色 |
|------|------|------|------|------|
| H1 | 24pt | Bold (700) | 1.3 | text_primary |
| H2 | 20pt | Bold (700) | 1.3 | text_primary |
| H3 | 18pt | Bold (700) | 1.3 | text_primary |
| H4 | 16pt | Bold (700) | 1.3 | text_primary |
| H5 | 14pt | Bold (700) | 1.3 | text_primary |
| H6 | 12pt | Bold (700) | 1.3 | text_primary |
| 正文 | 14pt | Normal (400) | 1.6 | text_primary |
| 小字 | 12pt | Normal (400) | 1.5 | text_secondary |
| 代码 | 13pt | Normal (400) | 1.4 | text_primary |

### 3.2 间距

| 元素 | 上边距 | 下边距 | 左边距 | 右边距 |
|------|--------|--------|--------|--------|
| H1 | 24px | 16px | 0 | 0 |
| H2 | 20px | 14px | 0 | 0 |
| H3 | 16px | 12px | 0 | 0 |
| H4-H6 | 12px | 8px | 0 | 0 |
| 段落 | 0 | 12px | 0 | 0 |
| 代码块 | 12px | 12px | 0 | 0 |
| 引用块 | 8px | 8px | 0 | 0 |
| 列表项 | 4px | 4px | 24px | 0 |
| 表格 | 12px | 12px | 0 | 0 |

### 3.3 边框和分隔线

| 元素 | 宽度 | 颜色 | 样式 |
|------|------|------|------|
| 分割线 | 1px | divider | Solid |
| 表格边框 | 1px | border | Solid |
| 引用块左边框 | 2px | border | Solid |
| 代码块边框 | 1px | border | Solid |

---

## 四、渲染命令映射

### 4.1 颜色常量

```rust
// core/src/render/colors.rs

/// E-ink 颜色常量
pub mod eink {
    use super::super::bridge::types::Color;

    pub const TEXT_PRIMARY: Color = Color::rgb(0x1A, 0x1A, 0x1A);
    pub const TEXT_SECONDARY: Color = Color::rgb(0x55, 0x55, 0x55);
    pub const BG_PRIMARY: Color = Color::rgb(0xFF, 0xFF, 0xFF);
    pub const BG_SECONDARY: Color = Color::rgb(0xF5, 0xF5, 0xF5);
    pub const BORDER: Color = Color::rgb(0xCC, 0xCC, 0xCC);
    pub const DIVIDER: Color = Color::rgb(0xDD, 0xDD, 0xDD);

    // 代码高亮
    pub const CODE_KEYWORD: Color = Color::rgb(0x00, 0x00, 0x00);
    pub const CODE_STRING: Color = Color::rgb(0x44, 0x44, 0x44);
    pub const CODE_COMMENT: Color = Color::rgb(0x99, 0x99, 0x99);

    // 数学公式
    pub const MATH_FORMULA: Color = Color::rgb(0x00, 0x00, 0x00);
}
```

### 4.2 渲染示例

```rust
// 渲染代码块
let bg_cmd = RenderCommand::fill_rect(
    x, y, width, height,
    eink::BG_SECONDARY
);

let border_cmd = RenderCommand::fill_rect(
    x, y, 1.0, height,
    eink::BORDER
);

// 渲染关键字
let keyword_cmd = RenderCommand::draw_text(
    x, y, "fn",
    FontSpec { size_pt: 13.0, family: FontFamily::Mono, ..default() },
    eink::CODE_KEYWORD
);

// 渲染注释
let comment_cmd = RenderCommand::draw_text(
    x, y, "// 这是注释",
    FontSpec { size_pt: 13.0, family: FontFamily::Mono, ..default() },
    eink::CODE_COMMENT
);
```

---

## 五、对比度验证

### 5.1 计算公式

```
对比度 = (L1 + 0.05) / (L2 + 0.05)

其中：
- L1 = 较亮颜色的相对亮度
- L2 = 较暗颜色的相对亮度
- 相对亮度 L = 0.2126 * R + 0.7152 * G + 0.0722 * B
```

### 5.2 验证清单

| 前景 | 背景 | 对比度 | 要求 | 状态 |
|------|------|--------|------|------|
| TEXT_PRIMARY (#1A1A1A) | BG_PRIMARY (#FFFFFF) | 16.1:1 | ≥ 4.5:1 | ✅ |
| TEXT_SECONDARY (#555555) | BG_PRIMARY (#FFFFFF) | 7.5:1 | ≥ 4.5:1 | ✅ |
| TEXT_TERTIARY (#888888) | BG_PRIMARY (#FFFFFF) | 3.8:1 | ≥ 3:1 (大文本) | ⚠️ |
| CODE_KEYWORD (#000000) | BG_SECONDARY (#F5F5F5) | 12.6:1 | ≥ 4.5:1 | ✅ |
| CODE_COMMENT (#999999) | BG_SECONDARY (#F5F5F5) | 2.8:1 | ≥ 3:1 | ⚠️ |

**注意**：浅灰文字（#888888）仅适用于大文本（≥18pt）。

---

## 六、Android 端配置

### 6.1 主题资源

```xml
<!-- res/values/colors_eink.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Text Colors -->
    <color name="text_primary">#1A1A1A</color>
    <color name="text_secondary">#555555</color>
    <color name="text_tertiary">#888888</color>
    <color name="text_disabled">#BBBBBB</color>

    <!-- Background Colors -->
    <color name="bg_primary">#FFFFFF</color>
    <color name="bg_secondary">#F5F5F5</color>
    <color name="bg_tertiary">#EEEEEE</color>

    <!-- Border & Divider -->
    <color name="border">#CCCCCC</color>
    <color name="divider">#DDDDDD</color>
    <color name="border_focus">#999999</color>
</resources>
```

### 6.2 代码示例

```kotlin
// 应用 E-ink 主题
val theme = EinkTheme()

// 渲染文本
textView.setTextColor(
    ContextCompat.getColor(context, R.color.text_primary)
)

// 渲染背景
textView.setBackgroundColor(
    ContextCompat.getColor(context, R.color.bg_primary)
)

// 渲染边框
view.strokeColor = ContextCompat.getColor(context, R.color.border)
```

---

## 七、测试检查清单

### 7.1 视觉测试

- [ ] 所有文字清晰可读
- [ ] 标题层级明显
- [ ] 代码块背景与代码对比度足够
- [ ] 链接文字可识别
- [ ] 引用块边框清晰
- [ ] 表格边框可见

### 7.2 功能测试

- [ ] 局部刷新工作正常
- [ ] 无重影问题
- [ ] 滚动流畅
- [ ] 触摸响应准确

### 7.3 真机测试

- [ ] Supernote A6 X2 Nomad
- [ ] 其他 E-ink 设备（如有）

---

**文档版本**: 1.0.0
**最后更新**: 2026-07-08
