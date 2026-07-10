// =============================================================================
// Markdown 扩展语法解析
// =============================================================================
//
// 支持标准 Markdown 之外的扩展语法：
// - 下划线: <u>text</u>
// - 高亮: ==text==
// - Callout: > [!INFO] 或 > [!TIP] 等
// =============================================================================

use super::ast::{InlineNode, BlockNode, CalloutKind};
use crate::parser::streaming::{is_blockquote_line, count_blockquote_level};

/// 查找行内数学公式 $...$
/// 返回 (结束位置, 公式内容)
pub fn find_inline_math(text: &str) -> Option<(usize, String)> {
    if !text.starts_with('$') {
        return None;
    }

    // 跳过开头的 $
    let remaining = &text[1..];

    // 查找结束的 $
    if let Some(end_pos) = remaining.find('$') {
        let formula = &remaining[..end_pos];
        if !formula.is_empty() {
            return Some((end_pos + 1, formula.to_string()));
        }
    }

    None
}

/// 查找链接 [text](url "title")
/// 返回 (结束位置, 文本内容, URL, 标题)
pub fn find_link(text: &str) -> Option<(usize, String, String, Option<String>)> {
    if !text.starts_with('[') {
        return None;
    }

    // 查找链接文本结束的 ]
    let bracket_end = text.find(']')?;
    let link_text = &text[1..bracket_end];

    // 检查后面是否跟着 (
    let after_bracket = text.get(bracket_end + 1..)?;
    if !after_bracket.starts_with('(') {
        return None;
    }

    // 查找 URL 结束的 )
    let paren_end = after_bracket.find(')')?;
    let url_part = &after_bracket[1..paren_end];

    // 解析 URL 和可选的标题
    let (url, title) = if let Some(quote_pos) = url_part.find('"') {
        // 有标题: url "title"
        let url = &url_part[..quote_pos];
        let title_part = &url_part[quote_pos + 1..];
        if let Some(title_end) = title_part.find('"') {
            (url.trim(), Some(title_part[..title_end].to_string()))
        } else {
            (url.trim(), None)
        }
    } else {
        (url_part.trim(), None)
    };

    if !url.is_empty() {
        // 返回 (结束位置, 文本, URL, 标题)
        let total_end = bracket_end + 1 + paren_end + 1;
        Some((total_end, link_text.to_string(), url.to_string(), title))
    } else {
        None
    }
}

/// 查找图片 ![alt](url "title")
/// 返回 (结束位置, alt文本, URL, 标题)
pub fn find_image(text: &str) -> Option<(usize, String, String, Option<String>)> {
    if !text.starts_with("![") {
        return None;
    }

    // 查找 alt 文本结束的 ]
    let bracket_end = text.find(']')?;
    let alt_text = &text[2..bracket_end];

    // 检查后面是否跟着 (
    let after_bracket = text.get(bracket_end + 1..)?;
    if !after_bracket.starts_with('(') {
        return None;
    }

    // 查找 URL 结束的 )
    let paren_end = after_bracket.find(')')?;
    let url_part = &after_bracket[1..paren_end];

    // 解析 URL 和可选的标题
    let (url, title) = if let Some(quote_pos) = url_part.find('"') {
        // 有标题: url "title"
        let url = &url_part[..quote_pos];
        let title_part = &url_part[quote_pos + 1..];
        if let Some(title_end) = title_part.find('"') {
            (url.trim(), Some(title_part[..title_end].to_string()))
        } else {
            (url.trim(), None)
        }
    } else {
        (url_part.trim(), None)
    };

    if !url.is_empty() {
        // 返回 (结束位置, alt, URL, 标题)
        let total_end = bracket_end + 1 + paren_end + 1;
        Some((total_end, alt_text.to_string(), url.to_string(), title))
    } else {
        None
    }
}

/// 解析行内扩展语法（删除线、下划线、高亮）
pub fn parse_inline_extensions(text: &str) -> Vec<InlineNode> {
    let mut nodes = Vec::new();
    let mut i = 0;
    let bytes = text.as_bytes();
    let mut text_buffer = String::new();

    while i < bytes.len() {
        let remaining = &text[i..];

        // 检测图片 ![alt](url)
        if let Some((end_offset, alt, url, title)) = find_image(remaining) {
            // 添加缓冲区中的文本（如果有）
            if !text_buffer.is_empty() {
                nodes.push(InlineNode::Text(std::mem::take(&mut text_buffer)));
            }

            // 添加图片节点
            nodes.push(InlineNode::Image {
                dest: url,
                title,
                alt: vec![InlineNode::Text(alt)],
            });

            // 跳过整个 ![alt](url)
            i += end_offset;
            continue;
        }

        // 检测链接 [text](url)
        if let Some((end_offset, link_text, url, title)) = find_link(remaining) {
            // 添加缓冲区中的文本（如果有）
            if !text_buffer.is_empty() {
                nodes.push(InlineNode::Text(std::mem::take(&mut text_buffer)));
            }

            // 添加链接节点（链接文本可能包含嵌套格式，这里简化处理）
            nodes.push(InlineNode::Link {
                dest: url,
                title,
                children: vec![InlineNode::Text(link_text)],
            });

            // 跳过整个 [text](url)
            i += end_offset;
            continue;
        }

        // 检测行内数学公式 $...$
        if let Some((end_offset, formula)) = find_inline_math(remaining) {
            // 添加缓冲区中的文本（如果有）
            if !text_buffer.is_empty() {
                nodes.push(InlineNode::Text(std::mem::take(&mut text_buffer)));
            }

            // 添加数学公式节点
            nodes.push(InlineNode::Math {
                display_mode: false,
                latex: formula,
            });

            // 跳过整个 $...$
            i += end_offset + 1; // +1 for closing $
            continue;
        }

        // 检测删除线 ~~text~~
        if let Some((end_offset, content)) = find_strikethrough(remaining) {
            // 添加缓冲区中的文本（如果有）
            if !text_buffer.is_empty() {
                nodes.push(InlineNode::Text(std::mem::take(&mut text_buffer)));
            }

            // 添加删除线节点
            nodes.push(InlineNode::Strikethrough {
                children: vec![InlineNode::Text(content)],
            });

            // 跳过整个 ~~text~~
            i += end_offset + 2; // +2 for closing ~~
            continue;
        }

        // 检测下划线 <u>text</u>
        if let Some((end_offset, content)) = find_underline(remaining) {
            // 添加缓冲区中的文本（如果有）
            if !text_buffer.is_empty() {
                nodes.push(InlineNode::Text(std::mem::take(&mut text_buffer)));
            }

            // 添加下划线节点
            nodes.push(InlineNode::Underline {
                children: vec![InlineNode::Text(content)],
            });

            // 跳过整个 <u>text</u>
            i += end_offset + 4; // +4 for </u>
            continue;
        }

        // 检测高亮 ==text==
        if let Some((end_offset, content)) = find_highlight(remaining) {
            // 添加缓冲区中的文本（如果有）
            if !text_buffer.is_empty() {
                nodes.push(InlineNode::Text(std::mem::take(&mut text_buffer)));
            }

            // 添加高亮节点
            nodes.push(InlineNode::Highlight {
                children: vec![InlineNode::Text(content)],
            });

            // 跳过整个 ==text==
            i += end_offset + 2; // +2 for closing ==
            continue;
        }

        // 没有找到扩展语法，添加当前字符到缓冲区
        let ch = text[i..].chars().next().unwrap();
        text_buffer.push(ch);
        i += ch.len_utf8();
    }

    // 添加缓冲区中剩余的文本
    if !text_buffer.is_empty() {
        nodes.push(InlineNode::Text(text_buffer));
    }

    // 如果没有找到任何扩展语法且只有一个文本节点，保持原样
    if nodes.len() == 1 {
        if let InlineNode::Text(_) = &nodes[0] {
            return nodes;
        }
    }

    nodes
}

/// 合并相邻的文本节点
fn coalesce_text_nodes(nodes: Vec<InlineNode>) -> Vec<InlineNode> {
    let mut result = Vec::new();
    let mut text_buffer = String::new();

    for node in nodes {
        match node {
            InlineNode::Text(s) => {
                text_buffer.push_str(&s);
            }
            _ => {
                if !text_buffer.is_empty() {
                    result.push(InlineNode::Text(std::mem::take(&mut text_buffer)));
                }
                result.push(node);
            }
        }
    }

    if !text_buffer.is_empty() {
        result.push(InlineNode::Text(text_buffer));
    }

    result
}

/// 查找删除线语法 ~~text~~
pub fn find_strikethrough(text: &str) -> Option<(usize, String)> {
    if !text.starts_with("~~") {
        return None;
    }

    // 安全地跳过开头的 "~~"
    let remaining = text.get(2..)?;

    // 查找结束 ~~
    if let Some(end_pos) = remaining.find("~~") {
        let content = remaining.get(..end_pos)?;
        if !content.is_empty() {
            return Some((2 + end_pos, content.to_string()));
        }
    }

    None
}

/// 查找高亮语法 ==text==
pub fn find_highlight(text: &str) -> Option<(usize, String)> {
    if !text.starts_with("==") {
        return None;
    }

    // 安全地跳过开头的 "=="
    let remaining = text.get(2..)?;

    // 查找结束 ==
    if let Some(end_pos) = remaining.find("==") {
        let content = remaining.get(..end_pos)?;
        if !content.is_empty() {
            return Some((2 + end_pos, content.to_string()));
        }
    }

    None
}

/// 查找下划线语法 <u>text</u>
pub fn find_underline(text: &str) -> Option<(usize, String)> {
    if !text.starts_with("<u>") {
        return None;
    }

    // 安全地跳过开头的 "<u>"
    let remaining = text.get(3..)?;

    // 查找结束 </u>
    if let Some(end_pos) = remaining.find("</u>") {
        let content = remaining.get(..end_pos)?;
        if !content.is_empty() {
            return Some((3 + end_pos, content.to_string()));
        }
    }

    None
}

/// 检测 Callout 块
///
/// 支持的格式：
/// - > [!INFO] 内容
/// - > [!TIP] 内容
/// - > [!WARNING] 内容
/// 等
pub fn is_callout_block(line: &[u8]) -> Option<CalloutKind> {
    let text = std::str::from_utf8(line).ok()?;

    // Callout 必须以 > 开头
    if !is_blockquote_line(line) {
        return None;
    }

    let level = count_blockquote_level(line);
    if level == 0 {
        return None;
    }

    // 安全地跳过引用标记
    let content = text.get(level as usize..)?.trim();

    // 检测 [!TYPE] 格式
    if content.starts_with("[!") {
        let end_bracket = content.find(']')?;
        // 安全地提取类型字符串
        let kind_str = content.get(2..end_bracket)?;
        CalloutKind::from_str(kind_str)
    } else {
        None
    }
}

/// 解析 Callout 块
///
/// 输入格式：
/// ```markdown
/// > [!INFO]
/// > 内容行 1
/// > 内容行 2
/// ```
pub fn parse_callout_block(lines: &[&str], kind: CalloutKind) -> BlockNode {
    // 跳过第一行的 [!TYPE] 标记
    let _content_lines: Vec<String> = lines.iter()
        .skip(1)
        .map(|s| s.to_string())
        .collect();

    BlockNode::Callout {
        kind,
        title: Some(kind.default_title().to_string()),
        children: vec![], // 简化：不解析嵌套块
    }
}

/// 检测目录标记 [TOC]
pub fn is_toc_marker(line: &str) -> bool {
    let trimmed = line.trim();
    trimmed == "[TOC]" || trimmed == "[toc]" || trimmed == "[tableofcontents]"
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_find_strikethrough() {
        // 返回值: (内容结束位置, 内容)
        // "~~hello~~" -> 位置7是"hello"结束位置（从开头算起）
        assert_eq!(find_strikethrough("~~hello~~"), Some((7, "hello".to_string())));
        assert_eq!(find_strikethrough("~~world~~ text"), Some((7, "world".to_string())));
        assert_eq!(find_strikethrough("~~~"), None); // 空内容
        assert_eq!(find_strikethrough("~~test"), None); // 没有结束
    }

    #[test]
    fn test_find_highlight() {
        // 返回值: (内容结束位置, 内容)
        // "==hello==" -> 位置7是"hello"结束位置（从开头算起）
        assert_eq!(find_highlight("==hello=="), Some((7, "hello".to_string())));
        assert_eq!(find_highlight("==world== text"), Some((7, "world".to_string())));
        assert_eq!(find_highlight("==="), None); // 空内容
        assert_eq!(find_highlight("==test"), None); // 没有结束
    }

    #[test]
    fn test_parse_inline_extensions_strikethrough() {
        let nodes = parse_inline_extensions("这是~~删除线~~文本");
        assert_eq!(nodes.len(), 3); // "这是", Strikethrough, "文本"

        match &nodes[0] {
            InlineNode::Text(s) => assert_eq!(s, "这是"),
            _ => panic!("Expected Text node"),
        }

        match &nodes[1] {
            InlineNode::Strikethrough { children } => {
                assert_eq!(children.len(), 1);
                match &children[0] {
                    InlineNode::Text(s) => assert_eq!(s, "删除线"),
                    _ => panic!("Expected Text child"),
                }
            }
            _ => panic!("Expected Strikethrough node"),
        }

        match &nodes[2] {
            InlineNode::Text(s) => assert_eq!(s, "文本"),
            _ => panic!("Expected Text node"),
        }
    }

    #[test]
    fn test_parse_inline_extensions_underline() {
        let nodes = parse_inline_extensions("这是<u>下划线</u>文本");
        assert_eq!(nodes.len(), 3); // "这是", Underline, "文本"

        match &nodes[1] {
            InlineNode::Underline { children } => {
                assert_eq!(children.len(), 1);
                match &children[0] {
                    InlineNode::Text(s) => assert_eq!(s, "下划线"),
                    _ => panic!("Expected Text child"),
                }
            }
            _ => panic!("Expected Underline node"),
        }
    }

    #[test]
    fn test_find_link() {
        // 简单链接
        let result = find_link("[text](url)");
        assert_eq!(result, Some((11, "text".to_string(), "url".to_string(), None)));

        // 带标题的链接
        let result = find_link("[text](url \"title\")");
        assert_eq!(result, Some((19, "text".to_string(), "url".to_string(), Some("title".to_string()))));

        // 空URL
        let result = find_link("[text]()");
        assert_eq!(result, None);

        // 不是链接
        let result = find_link("text");
        assert_eq!(result, None);
    }

    #[test]
    fn test_find_image() {
        // 简单图片
        let result = find_image("![alt](url)");
        assert_eq!(result, Some((11, "alt".to_string(), "url".to_string(), None)));

        // 带标题的图片
        let result = find_image("![alt](url \"title\")");
        assert_eq!(result, Some((19, "alt".to_string(), "url".to_string(), Some("title".to_string()))));

        // 空URL
        let result = find_image("![alt]()");
        assert_eq!(result, None);

        // 不是图片
        let result = find_image("text");
        assert_eq!(result, None);
    }

    #[test]
    fn test_parse_inline_extensions_link() {
        let nodes = parse_inline_extensions("这是[链接](https://example.com)文本");
        assert_eq!(nodes.len(), 3); // "这是", Link, "文本"

        match &nodes[1] {
            InlineNode::Link { dest, children, .. } => {
                assert_eq!(dest, "https://example.com");
                assert_eq!(children.len(), 1);
                match &children[0] {
                    InlineNode::Text(s) => assert_eq!(s, "链接"),
                    _ => panic!("Expected Text child"),
                }
            }
            _ => panic!("Expected Link node"),
        }
    }

    #[test]
    fn test_parse_inline_extensions_image() {
        let nodes = parse_inline_extensions("这是![图片](https://example.com/img.png)文本");
        assert_eq!(nodes.len(), 3); // "这是", Image, "文本"

        match &nodes[1] {
            InlineNode::Image { dest, alt, .. } => {
                assert_eq!(dest, "https://example.com/img.png");
                assert_eq!(alt.len(), 1);
                match &alt[0] {
                    InlineNode::Text(s) => assert_eq!(s, "图片"),
                    _ => panic!("Expected Text in alt"),
                }
            }
            _ => panic!("Expected Image node"),
        }
    }

    #[test]
    fn test_parse_inline_extensions_mixed() {
        let nodes = parse_inline_extensions("~~删除~~<u>下划</u>==高亮==");
        assert_eq!(nodes.len(), 3); // Strikethrough, Underline, Highlight

        match &nodes[0] {
            InlineNode::Strikethrough { .. } => {}
            _ => panic!("Expected Strikethrough node"),
        }

        match &nodes[1] {
            InlineNode::Underline { .. } => {}
            _ => panic!("Expected Underline node"),
        }

        match &nodes[2] {
            InlineNode::Highlight { .. } => {}
            _ => panic!("Expected Highlight node"),
        }
    }

    #[test]
    fn test_find_underline() {
        // 返回值: (内容结束位置, 内容)
        // "<u>hello</u>" -> 位置8是"hello"结束位置（从开头算起）
        assert_eq!(find_underline("<u>hello</u>"), Some((8, "hello".to_string())));
        assert_eq!(find_underline("<u>world</u> text"), Some((8, "world".to_string())));
        assert_eq!(find_underline("<u></u>"), None); // 空内容
        assert_eq!(find_underline("<u>test"), None); // 没有结束
    }

    #[test]
    fn test_is_callout_block() {
        assert!(is_callout_block(b"> [!INFO]").is_some());
        assert!(is_callout_block(b"> [!TIP]").is_some());
        assert!(is_callout_block(b"> [!WARNING]").is_some());
        assert!(is_callout_block(b"> [!CAUTION]").is_some());
        assert!(is_callout_block(b"> [!SUCCESS]").is_some());
        assert!(is_callout_block(b"> [!UNKNOWN]").is_none());
        assert!(is_callout_block(b"> normal text").is_none());
    }

    #[test]
    fn test_is_toc_marker() {
        assert!(is_toc_marker("[TOC]"));
        assert!(is_toc_marker("[toc]"));
        assert!(is_toc_marker("[tableofcontents]"));
        assert!(!is_toc_marker("[TODO]"));
        assert!(!is_toc_marker("normal text"));
    }

    #[test]
    fn test_callout_kind_from_str() {
        assert_eq!(CalloutKind::from_str("info"), Some(CalloutKind::Info));
        assert_eq!(CalloutKind::from_str("warning"), Some(CalloutKind::Warning));
        assert_eq!(CalloutKind::from_str("tip"), Some(CalloutKind::Tip));
        assert_eq!(CalloutKind::from_str("unknown"), None);
    }

    #[test]
    fn test_callout_kind_properties() {
        let kind = CalloutKind::Info;
        assert_eq!(kind.default_title(), "信息");
        assert_eq!(kind.icon(), "ℹ️");

        let kind = CalloutKind::Warning;
        assert_eq!(kind.default_title(), "警告");
        assert_eq!(kind.icon(), "⚠️");
    }
}
