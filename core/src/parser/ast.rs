// =============================================================================
// Markdown AST Definition
// =============================================================================
//
// Internal representation for parsed Markdown content.
// NOT exposed via FFI - used only within Rust Core.
// =============================================================================

use std::collections::HashMap;

// -----------------------------------------------------------------------------
// Block Level Nodes
// -----------------------------------------------------------------------------

/// Block-level node (paragraph, heading, list, etc.)
#[derive(Debug, Clone)]
pub enum BlockNode {
    /// Heading (level 1-6)
    Heading {
        level: u8,
        children: Vec<InlineNode>,
    },
    /// Paragraph
    Paragraph {
        children: Vec<InlineNode>,
    },
    /// Code block (fenced or indented)
    CodeBlock {
        language: Option<String>,
        content: String,
    },
    /// Block quote
    BlockQuote {
        children: Vec<BlockNode>,
    },
    /// Ordered or unordered list
    List {
        ordered: bool,
        start_number: Option<usize>,
        items: Vec<ListItem>,
    },
    /// Table
    Table {
        headers: Vec<Vec<InlineNode>>,
        rows: Vec<Vec<Vec<InlineNode>>>,
        alignments: Vec<TableCellAlignment>,
    },
    /// Thematic break (horizontal rule)
    ThematicBreak,
    /// HTML block
    HtmlBlock {
        content: String,
    },
    /// Reference definition
    ReferenceDef {
        label: String,
        dest: String,
        title: Option<String>,
    },
}

impl BlockNode {
    /// Get the bounding box for this block (after layout)
    pub fn bounding_box(&self) -> (f32, f32, f32, f32) {
        // (x, y, width, height)
        // This will be computed by the Layouter
        (0.0, 0.0, 0.0, 0.0)
    }
}

// -----------------------------------------------------------------------------
// List Items
// -----------------------------------------------------------------------------

/// List item (can contain nested blocks)
#[derive(Debug, Clone)]
pub struct ListItem {
    pub marker: ListMarker,
    pub content: Vec<BlockNode>,
}

#[derive(Debug, Clone)]
pub enum ListMarker {
    /// Unordered (bullet: -, *, +)
    Unordered(char),
    /// Ordered (1., 2., 3., ...)
    Ordered { number: usize, delimiter: char },
    /// Task list item [ ] or [x]
    Task { checked: bool },
}

// -----------------------------------------------------------------------------
// Table
// -----------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum TableCellAlignment {
    Left,
    Center,
    Right,
    Unspecified,
}

// -----------------------------------------------------------------------------
// Inline Level Nodes
// -----------------------------------------------------------------------------

/// Inline-level node (text within blocks)
#[derive(Debug, Clone)]
pub enum InlineNode {
    /// Plain text
    Text(String),
    /// Soft line break (render as space)
    SoftBreak,
    /// Hard line break (render as <br>)
    HardBreak,
    /// Emphasized text (italic)
    Emphasis {
        children: Vec<InlineNode>,
        level: u8,  // 1 = italic, 2 = bold, 3 = italic+bold
    },
    /// Strong text (bold)
    Strong {
        children: Vec<InlineNode>,
    },
    /// Strikethrough
    Strikethrough {
        children: Vec<InlineNode>,
    },
    /// Code (inline)
    Code(String),
    /// Link
    Link {
        dest: String,
        title: Option<String>,
        children: Vec<InlineNode>,
    },
    /// Image
    Image {
        dest: String,
        title: Option<String>,
        alt: Vec<InlineNode>,
    },
    /// HTML
    Html(String),
}

impl InlineNode {
    /// Get plain text content (recursively)
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
        }
    }

    /// Check if node is empty
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
        }
    }
}

// -----------------------------------------------------------------------------
// Document Root
// -----------------------------------------------------------------------------

/// Complete Markdown document
#[derive(Debug, Clone)]
pub struct MarkdownDocument {
    /// Root block nodes
    pub blocks: Vec<BlockNode>,
    /// Metadata
    pub metadata: DocumentMetadata,
}

/// Document metadata
#[derive(Debug, Clone)]
pub struct DocumentMetadata {
    /// Total character count
    pub total_chars: usize,
    /// Total line count
    pub total_lines: usize,
    /// Table of contents (headings)
    pub toc: Vec<TocEntry>,
    /// Reference definitions ([label]: url "title")
    pub refs: HashMap<String, (String, Option<String>)>,
    /// Last modification offset (for incremental parsing)
    pub last_modified_offset: usize,
}

/// Table of contents entry
#[derive(Debug, Clone)]
pub struct TocEntry {
    pub level: u8,
    pub title: String,
    pub byte_offset: usize,
    pub line_number: usize,
}

// -----------------------------------------------------------------------------
// Parse Position
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
