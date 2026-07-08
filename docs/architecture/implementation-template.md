# 实现代码模板

本文档提供 Rust Core 渲染功能的标准实现模板。

---

## 一、添加新的块级元素

### 1.1 定义 AST 节点

```rust
// core/src/parser/ast.rs

pub enum BlockNode {
    // 现有节点...

    /// 新增：Callout 提示框
    Callout {
        kind: CalloutKind,
        title: Option<String>,
        children: Vec<BlockNode>,
    },
}

/// Callout 类型
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CalloutKind {
    Info,
    Warning,
    Note,
    Tip,
    Important,
    Caution,
}

impl CalloutKind {
    /// 获取对应的图标
    pub fn icon(self) -> IconType {
        match self {
            Self::Info => IconType::Info,
            Self::Warning => IconType::Warning,
            Self::Note => IconType::Note,
            Self::Tip => IconType::Tip,
            Self::Important => IconType::Important,
            Self::Caution => IconType::Caution,
        }
    }

    /// 获取对应的颜色
    pub fn color(self) -> Color {
        match self {
            Self::Info => Color::rgb(0x55, 0x55, 0x55),
            Self::Warning => Color::rgb(0x88, 0x88, 0x88),
            Self::Note => Color::rgb(0x66, 0x66, 0x66),
            Self::Tip => Color::rgb(0x44, 0x44, 0x44),
            Self::Important => Color::rgb(0x33, 0x33, 0x33),
            Self::Caution => Color::rgb(0x22, 0x22, 0x22),
        }
    }
}
```

### 1.2 解析器实现

```rust
// core/src/parser/callout.rs

use crate::parser::ast::{BlockNode, CalloutKind};
use crate::parser::error::ParseError;

/// 解析 Callout 块
///
/// 语法：
/// :::kind
/// 内容
/// :::
pub fn parse_callout(lines: &[&str], offset: usize) -> Result<(BlockNode, usize), ParseError> {
    let first_line = lines.first().ok_or(ParseError::UnexpectedEOF)?;
    
    // 检查是否是 Callout 开始标记
    if !first_line.starts_with(":::") {
        return Err(ParseError::InvalidSyntax("Expected :::kind"));
    }

    // 解析类型
    let kind_str = &first_line[3..];
    let kind = match kind_str.to_lowercase().as_str() {
        "info" => CalloutKind::Info,
        "warning" => CalloutKind::Warning,
        "note" => CalloutKind::Note,
        "tip" => CalloutKind::Tip,
        "important" => CalloutKind::Important,
        "caution" => CalloutKind::Caution,
        _ => return Err(ParseError::InvalidSyntax("Unknown callout kind")),
    };

    // 查找结束标记
    let end_index = lines.iter()
        .position(|line| *line == ":::")
        .ok_or(ParseError::UnexpectedEOF)?;

    // 解析内容
    let content_lines = &lines[1..end_index];
    let mut children = Vec::new();
    
    // TODO: 解析子块（段落、列表等）
    // for block in parse_blocks(content_lines) {
    //     children.push(block);
    // }

    let node = BlockNode::Callout {
        kind,
        title: None,  // 可选：从第一行解析标题
        children,
    };

    Ok((node, end_index + 1))
}
```

### 1.3 布局器实现

```rust
// core/src/layout/callout.rs

use crate::parser::ast::BlockNode;
use crate::render::commands::{RenderCommand, RenderCommandType};

/// 计算 Callout 布局并生成渲染命令
pub fn layout_callout(
    node: &BlockNode,
    x: f32,
    y: f32,
    width: f32,
) -> (Vec<RenderCommand>, f32) {
    let mut commands = Vec::new();
    let mut current_y = y;

    if let BlockNode::Callout { kind, title, children } = node {
        // 获取样式
        let bg_color = Color::rgb(0xF5, 0xF5, 0xF5);
        let border_color = kind.color();
        let icon_type = kind.icon();

        // 绘制背景
        let height = 100.0;  // TODO: 计算实际高度
        commands.push(RenderCommand::fill_rect(
            x, current_y, width, height,
            bg_color
        ));

        // 绘制左边框
        commands.push(RenderCommand::fill_rect(
            x, current_y, 4.0, height,
            border_color
        ));

        // 绘制图标
        let icon_x = x + 12.0;
        let icon_y = current_y + 8.0;
        commands.push(RenderCommand::draw_icon(
            icon_x, icon_y, 20.0, icon_type
        ));

        // 绘制标题
        if let Some(title_text) = title {
            let title_x = icon_x + 24.0;
            commands.push(RenderCommand::draw_text(
                title_x, icon_y, title_text,
                FontSpec { size_pt: 14.0, bold: true, ..default() },
                Color::rgb(0x1A, 0x1A, 0x1A)
            ));
        }

        // 绘制子块
        let content_x = x + 12.0;
        let content_y = current_y + 36.0;
        // TODO: 递归布局子块

        current_y += height + 8.0;  // 下边距
    }

    (commands, current_y - y)
}
```

---

## 二、添加新的行内元素

### 2.1 定义 AST 节点

```rust
// core/src/parser/ast.rs

pub enum InlineNode {
    // 现有节点...

    /// 新增：高亮文本
    Highlight {
        children: Vec<InlineNode>,
    },
}
```

### 2.2 解析器实现

```rust
// core/src/parser/inline.rs

use crate::parser::ast::InlineNode;

/// 解析高亮语法 ==text==
pub fn parse_highlight(input: &str) -> Option<(InlineNode, usize)> {
    if !input.starts_with("==") {
        return None;
    }

    let end_index = input[2..].find("==")? + 2;
    let content = &input[2..end_index];

    // 递归解析内容（支持嵌套）
    let children = parse_inline(content)?;

    Some((InlineNode::Highlight { children }, end_index + 2))
}
```

### 2.3 渲染实现

```rust
// core/src/render/inline.rs

use crate::parser::ast::InlineNode;
use crate::render::commands::RenderCommand;

/// 渲染行内节点
pub fn render_inline(
    node: &InlineNode,
    x: f32,
    y: f32,
    font: &FontSpec,
) -> (Vec<RenderCommand>, f32) {
    let mut commands = Vec::new();
    let mut current_x = x;

    match node {
        InlineNode::Highlight { children } => {
            // 先渲染子节点获取总宽度
            let child_results: Vec<_> = children.iter()
                .map(|child| render_inline(child, current_x, y, font))
                .collect();

            let total_width = child_results.iter()
                .map(|(_, w)| *w)
                .sum::<f32>();

            // 绘制高亮背景
            let line_height = font.size_pt as f32 * 1.2;
            let bg_y = y - line_height + 2.0;  // 稍微上移
            commands.push(RenderCommand::fill_rect(
                current_x, bg_y, total_width, line_height,
                Color::rgb(0xFF, 0xFF, 0x00)  // 黄色高亮
            ));

            // 添加子节点命令
            for (mut cmds, _) in child_results {
                commands.append(&mut cmds);
            }

            current_x += total_width;
        }

        // 其他节点类型...
        _ => {}
    }

    (commands, current_x - x)
}
```

---

## 三、添加新的渲染命令

### 3.1 定义命令类型

```rust
// core/src/render/commands.rs

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RenderCommandType {
    DrawText = 0,
    FillRect = 1,
    DrawLine = 2,
    DrawImage = 3,
    
    // 新增
    DrawIcon = 4,        // 绘制图标
}

#[repr(C)]
#[derive(Clone, Copy)]
pub union RenderCommandData {
    pub text: TextData,
    pub line: LineData,
    pub image: ImageData,
    pub icon: IconData,   // 新增
    pub raw: [u8; 24],
}

/// 图标数据
#[repr(C)]
#[derive(Clone, Copy)]
pub struct IconData {
    pub icon_type: IconType,
    pub size: f32,
    pub color: Color,
    pub _pad: [u8; 8],
}

/// 图标类型
#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum IconType {
    CheckboxUnchecked = 0,
    CheckboxChecked = 1,
    Bullet = 2,
    Info = 3,
    Warning = 4,
}
```

### 3.2 命令构建器

```rust
// core/src/render/commands.rs

impl RenderCommand {
    /// 创建 DrawIcon 命令
    pub fn draw_icon(
        x: f32,
        y: f32,
        size: f32,
        icon_type: IconType,
    ) -> Self {
        Self {
            cmd_type: RenderCommandType::DrawIcon,
            x, y,
            width: size,
            height: size,
            color: Color::rgb(0x1A, 0x1A, 0x1A),
            data: RenderCommandData {
                icon: IconData {
                    icon_type,
                    size,
                    color: Color::rgb(0x1A, 0x1A, 0x1A),
                    _pad: [0; 8],
                },
            },
        }
    }
}
```

### 3.3 Android 端执行器

```kotlin
// platforms/android/app/src/main/java/com/editor/nomadmark/render/RenderCommandExecutor.kt

fun execute(commandsPtr: Long, commandsCount: Int, canvas: Canvas) {
    val commands = readCommands(commandsPtr, commandsCount)
    
    for (cmd in commands) {
        when (cmd.cmdType) {
            RenderCommandType.DrawText -> drawText(cmd, canvas)
            RenderCommandType.FillRect -> fillRect(cmd, canvas)
            RenderCommandType.DrawLine -> drawLine(cmd, canvas)
            RenderCommandType.DrawImage -> drawImage(cmd, canvas)
            RenderCommandType.DrawIcon -> drawIcon(cmd, canvas)  // 新增
        }
    }
}

private fun drawIcon(cmd: RenderCommand, canvas: Canvas) {
    val iconData = cmd.data.icon
    val iconType = IconType.fromValue(iconData.icon_type)
    
    when (iconType) {
        IconType.CheckboxChecked -> drawCheckbox(canvas, cmd.x, cmd.y, cmd.width, true)
        IconType.CheckboxUnchecked -> drawCheckbox(canvas, cmd.x, cmd.y, cmd.width, false)
        IconType.Bullet -> drawBullet(canvas, cmd.x, cmd.y, cmd.width)
        IconType.Info -> drawInfoIcon(canvas, cmd.x, cmd.y, cmd.width)
        IconType.Warning -> drawWarningIcon(canvas, cmd.x, cmd.y, cmd.width)
    }
}

private fun drawCheckbox(
    canvas: Canvas, x: Float, y: Float, size: Float, checked: Boolean
) {
    val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    val rect = RectF(x, y, x + size, y + size)
    canvas.drawRect(rect, paint)
    
    if (checked) {
        val path = Path().apply {
            moveTo(x + size * 0.2f, y + size * 0.5f)
            lineTo(x + size * 0.4f, y + size * 0.7f)
            lineTo(x + size * 0.8f, y + size * 0.3f)
        }
        paint.style = Paint.Style.STROKE
        canvas.drawPath(path, paint)
    }
}
```

---

## 四、FFI 绑定模板

### 4.1 Rust 端

```rust
// core/src/bridge/jni.rs

use crate::render::RenderResult;

#[no_mangle]
pub extern "C" fn rust_render_document(
    handle: u64,
    start_line: i32,
    line_count: i32,
    out_commands: *mut *mut RenderCommand,
    out_count: *mut i32,
) -> i32 {
    // 验证输入
    if handle == 0 {
        return -1;
    }

    // 执行渲染
    let result = match render_document(handle, start_line, line_count) {
        Ok(r) => r,
        Err(_) => return -1,
    };

    // 分配内存并复制命令
    let commands = result.commands;
    let count = commands.len() as i32;

    let ptr = unsafe {
        let layout = std::alloc::Layout::array::<RenderCommand>(count as usize).unwrap();
        std::alloc::alloc(layout) as *mut RenderCommand
    };

    unsafe {
        std::ptr::copy_nonoverlapping(commands.as_ptr(), ptr, count as usize);
        out_commands.write(ptr);
        out_count.write(count);
    }

    0  // 成功
}

#[no_mangle]
pub extern "C" fn rust_free_commands(ptr: *mut RenderCommand, count: i32) {
    if ptr.is_null() || count <= 0 {
        return;
    }

    unsafe {
        let layout = std::alloc::Layout::array::<RenderCommand>(count as usize).unwrap();
        std::alloc::dealloc(ptr as *mut u8, layout);
    }
}
```

### 4.2 Android 端

```kotlin
// platforms/android/app/src/main/java/com/editor/nomadmark/MarkdownCore.kt

companion object {
    init {
        System.loadLibrary("markdown_core")
    }

    external fun rustRenderDocument(
        handle: Long,
        startLine: Int,
        lineCount: Int,
        outCommands: LongArray,  // [ptr, count]
    ): Int

    external fun rustFreeCommands(ptr: Long, count: Int)
}

fun renderRange(
    handle: Long,
    startLine: Int,
    lineCount: Int
): RenderResult {
    val outCommands = LongArray(2)  // [ptr, count]
    
    val result = rustRenderDocument(
        handle,
        startLine,
        lineCount,
        outCommands
    )
    
    if (result != 0) {
        throw RenderException("Render failed with code $result")
    }
    
    val ptr = outCommands[0]
    val count = outCommands[1].toInt()
    
    // 读取命令
    val commands = readCommands(ptr, count)
    
    // 释放 Rust 端内存
    rustFreeCommands(ptr, count)
    
    return RenderResult(commands)
}
```

---

## 五、测试模板

### 5.1 单元测试

```rust
// core/src/parser/callout_tests.rs

#[cfg(test)]
mod tests {
    use super::*;
    use crate::parser::callout::parse_callout;

    #[test]
    fn test_parse_simple_callout() {
        let input = vec![
            ":::info",
            "This is info",
            ":::",
        ];
        
        let (node, consumed) = parse_callout(&input, 0).unwrap();
        
        assert!(matches!(node, BlockNode::Callout { 
            kind: CalloutKind::Info, 
            .. 
        }));
        assert_eq!(consumed, 3);
    }

    #[test]
    fn test_parse_nested_content() {
        let input = vec![
            ":::warning",
            "",
            "> This is a quote",
            "",
            ":::",
        ];
        
        let (node, _) = parse_callout(&input, 0).unwrap();
        
        if let BlockNode::Callout { children, .. } = node {
            assert_eq!(children.len(), 2);  // 空段落 + 引用块
        } else {
            panic!("Expected Callout node");
        }
    }

    #[test]
    fn test_invalid_callout_kind() {
        let input = vec![
            ":::unknown",
            "Content",
            ":::",
        ];
        
        assert!(parse_callout(&input, 0).is_err());
    }
}
```

### 5.2 集成测试

```rust
// tests/integration/render_tests.rs

#[test]
fn test_render_callout() {
    let markdown = r#"
:::info
This is an info callout with **bold** text.
:::"#;

    let doc = parse(markdown).unwrap();
    let result = render(&doc).unwrap();
    
    // 验证渲染命令
    let bg_cmd = result.commands.iter()
        .find(|c| c.cmd_type == RenderCommandType::FillRect)
        .expect("Missing background command");
    
    assert_eq!(bg_cmd.color, Color::rgb(0xF5, 0xF5, 0xF5));
}

#[test]
fn test_render_math_formula() {
    let markdown = "The formula is $E = mc^2$.";
    
    let doc = parse(markdown).unwrap();
    let result = render(&doc).unwrap();
    
    // 验证数学公式渲染
    let math_cmd = result.commands.iter()
        .find(|c| c.cmd_type == RenderCommandType::DrawImage)
        .expect("Missing math image command");
    
    // 验证尺寸合理
    assert!(math_cmd.width > 0.0);
    assert!(math_cmd.height > 0.0);
}
```

---

## 六、调试辅助

### 6.1 打印 AST

```rust
// core/src/parser/debug.rs

use crate::parser::ast::{BlockNode, InlineNode};

pub fn print_ast(node: &BlockNode, indent: usize) {
    let prefix = "  ".repeat(indent);
    
    match node {
        BlockNode::Heading { level, children } => {
            println!("{}Heading H{}", prefix, level);
            for child in children {
                print_inline(child, indent + 1);
            }
        }
        BlockNode::Callout { kind, title, children } => {
            println!("{}Callout::{:?}", prefix, kind);
            if let Some(t) = title {
                println!("{}  Title: {}", prefix, t);
            }
            for child in children {
                print_ast(child, indent + 1);
            }
        }
        // 其他节点...
        _ => {}
    }
}

pub fn print_inline(node: &InlineNode, indent: usize) {
    let prefix = "  ".repeat(indent);
    
    match node {
        InlineNode::Text(s) => println!("{}Text: {}", prefix, s),
        InlineNode::Highlight { children } => {
            println!("{}Highlight:", prefix);
            for child in children {
                print_inline(child, indent + 1);
            }
        }
        // 其他节点...
        _ => {}
    }
}
```

### 6.2 打印渲染命令

```rust
// core/src/render/debug.rs

use crate::render::commands::RenderCommand;

pub fn print_commands(commands: &[RenderCommand]) {
    for (i, cmd) in commands.iter().enumerate() {
        println!(
            "[{:02}] {:?} at ({:.1}, {:.1}) {:.1}x{:.1} color={:08X}",
            i, cmd.cmd_type, cmd.x, cmd.y, cmd.width, cmd.height, cmd.color.to_argb()
        );
    }
}
```

---

**文档版本**: 1.0.0
**最后更新**: 2026-07-08
