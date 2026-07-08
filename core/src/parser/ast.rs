// =============================================================================
// Markdown AST 定义
// =============================================================================
//
// 解析后的 Markdown 内容的内部表示。
// 不通过 FFI 暴露 - 仅在 Rust Core 内部使用。
// =============================================================================

use std::collections::HashMap;

// -----------------------------------------------------------------------------
// 块级节点
// -----------------------------------------------------------------------------

/// 块级节点（段落、标题、列表等）
#[derive(Debug, Clone)]
pub enum BlockNode {
    /// 标题（级别 1-6）
    Heading {
        level: u8,
        children: Vec<InlineNode>,
    },
    /// 段落
    Paragraph {
        children: Vec<InlineNode>,
    },
    /// 代码块（围栏式或缩进式）
    CodeBlock {
        language: Option<String>,
        content: String,
    },
    /// 有序或无序列表
    List {
        ordered: bool,
        start_number: Option<usize>,
        items: Vec<ListItem>,
    },
    /// 表格
    Table {
        headers: Vec<Vec<InlineNode>>,
        rows: Vec<Vec<Vec<InlineNode>>>,
        alignments: Vec<TableCellAlignment>,
    },
    /// 主题分隔线（水平线）
    ThematicBreak,
    /// HTML 块
    HtmlBlock {
        content: String,
    },
    /// 引用定义
    ReferenceDef {
        label: String,
        dest: String,
        title: Option<String>,
    },
    /// 引用块（blockquote）
    Blockquote {
        level: u8,
        children: Vec<BlockNode>,
    },
    /// 数学公式块（块级显示）
    MathBlock {
        latex: String,
    },
}

impl BlockNode {
    /// 获取此块的边界框（布局后）
    pub fn bounding_box(&self) -> (f32, f32, f32, f32) {
        // (x, y, 宽度, 高度)
        // 这将由布局器计算
        (0.0, 0.0, 0.0, 0.0)
    }
}

// -----------------------------------------------------------------------------
// 列表项
// -----------------------------------------------------------------------------

/// 列表项（可包含嵌套块）
#[derive(Debug, Clone)]
pub struct ListItem {
    pub marker: ListMarker,
    pub content: Vec<BlockNode>,
}

#[derive(Debug, Clone)]
pub enum ListMarker {
    /// 无序（项目符号：-, *, +）
    Unordered(char),
    /// 有序（1., 2., 3., ...）
    Ordered { number: usize, delimiter: char },
    /// 任务列表项 [ ] 或 [x]
    Task { checked: bool },
}

// -----------------------------------------------------------------------------
// 表格
// -----------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum TableCellAlignment {
    Left,
    Center,
    Right,
    Unspecified,
}

// -----------------------------------------------------------------------------
// 行内节点
// -----------------------------------------------------------------------------

/// 行内节点（块内的文本）
#[derive(Debug, Clone)]
pub enum InlineNode {
    /// 纯文本
    Text(String),
    /// 软换行（渲染为空格）
    SoftBreak,
    /// 硬换行（渲染为 <br>）
    HardBreak,
    /// 强调文本（斜体）
    Emphasis {
        children: Vec<InlineNode>,
        level: u8,  // 1 = 斜体, 2 = 粗体, 3 = 斜体+粗体
    },
    /// 粗体文本
    Strong {
        children: Vec<InlineNode>,
    },
    /// 删除线
    Strikethrough {
        children: Vec<InlineNode>,
    },
    /// 行内代码
    Code(String),
    /// 链接
    Link {
        dest: String,
        title: Option<String>,
        children: Vec<InlineNode>,
    },
    /// 图片
    Image {
        dest: String,
        title: Option<String>,
        alt: Vec<InlineNode>,
    },
    /// HTML
    Html(String),
    /// 行内数学公式
    Math {
        display_mode: bool,  // true = 块级显示, false = 行内显示
        latex: String,
    },
}

impl InlineNode {
    /// 获取纯文本内容（递归）
    pub fn text_content(&self) -> String {
        match self {
            InlineNode::Text(s) => s.clone(),
            InlineNode::SoftBreak => " ".to_string(),
            InlineNode::HardBreak => "\n".to_string(),
            InlineNode::Emphasis { children, .. }
            | InlineNode::Strong { children }
            | InlineNode::Strikethrough { children }
            | InlineNode::Link { children, .. }
            | InlineNode::Image { alt: children, .. } => {
                children.iter().map(|c| c.text_content()).collect()
            }
            InlineNode::Code(s) | InlineNode::Html(s) => s.clone(),
            InlineNode::Math { latex, .. } => latex.clone(),
        }
    }

    /// 检查节点是否为空
    pub fn is_empty(&self) -> bool {
        match self {
            InlineNode::Text(s) => s.is_empty(),
            InlineNode::SoftBreak | InlineNode::HardBreak => false,
            InlineNode::Emphasis { children, .. }
            | InlineNode::Strong { children }
            | InlineNode::Strikethrough { children }
            | InlineNode::Link { children, .. }
            | InlineNode::Image { alt: children, .. } => {
                children.iter().all(|c| c.is_empty())
            }
            InlineNode::Code(s) | InlineNode::Html(s) => s.is_empty(),
            InlineNode::Math { latex, .. } => latex.is_empty(),
        }
    }
}

// -----------------------------------------------------------------------------
// 文档根节点
// -----------------------------------------------------------------------------

/// 完整的 Markdown 文档
#[derive(Debug, Clone)]
pub struct MarkdownDocument {
    /// 根块节点
    pub blocks: Vec<BlockNode>,
    /// 元数据
    pub metadata: DocumentMetadata,
}

/// 文档元数据
#[derive(Debug, Clone)]
pub struct DocumentMetadata {
    /// 总字符数
    pub total_chars: usize,
    /// 总行数
    pub total_lines: usize,
    /// 目录（标题）
    pub toc: Vec<TocEntry>,
    /// 引用定义 ([label]: url "title")
    pub refs: HashMap<String, (String, Option<String>)>,
    /// 最后修改偏移量（用于增量解析）
    pub last_modified_offset: usize,
}

/// 目录条目
#[derive(Debug, Clone)]
pub struct TocEntry {
    pub level: u8,
    pub title: String,
    pub byte_offset: usize,
    pub line_number: usize,
}

// -----------------------------------------------------------------------------
// 解析位置
// -----------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct ParsePosition {
    pub byte_offset: usize,
    pub line_number: usize,
    pub column: usize,
}

impl ParsePosition {
    pub fn new(byte_offset: usize, line_number: usize, column: usize) -> Self {
        Self {
            byte_offset,
            line_number,
            column,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_inline_text_content() {
        let node = InlineNode::Text("hello".to_string());
        assert_eq!(node.text_content(), "hello");

        let bold = InlineNode::Strong {
            children: vec![
                InlineNode::Text("world".to_string()),
            ],
        };
        assert_eq!(bold.text_content(), "world");
    }

    #[test]
    fn test_inline_is_empty() {
        assert!(InlineNode::Text("".to_string()).is_empty());
        assert!(!InlineNode::Text("a".to_string()).is_empty());
        assert!(!InlineNode::HardBreak.is_empty());
    }
}
