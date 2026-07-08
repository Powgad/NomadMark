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
use std::collections::HashMap;

/// 解析行内扩展语法（下划线、高亮）
pub fn parse_inline_extensions(text: &str) -> Vec<InlineNode> {
    let mut nodes = Vec::new();
    let mut remaining = text;
    let mut offset = 0;

    while !remaining.is_empty() {
        // 检测高亮 ==text==
        if let Some((end_pos, content)) = find_highlight(remaining) {
            // 添加前面的文本
            if offset > 0 {
                nodes.push(InlineNode::Text(text[..offset].to_string()));
            }

            // 添加高亮节点
            nodes.push(InlineNode::Highlight {
                children: vec![InlineNode::Text(content)],
            });

            let consumed = end_pos + 2; // +2 for closing ==
            remaining = &remaining[consumed..];
            offset += consumed;
            continue;
        }

        // 检测下划线 <u>text</u>
        if let Some((end_pos, content)) = find_underline(remaining) {
            // 添加前面的文本
            if offset > 0 {
                nodes.push(InlineNode::Text(text[..offset].to_string()));
            }

            // 添加下划线节点
            nodes.push(InlineNode::Underline {
                children: vec![InlineNode::Text(content)],
            });

            // 跳过 </u>
            let consumed = end_pos + 4; // +4 for </u>
            remaining = &remaining[consumed..];
            offset += consumed;
            continue;
        }

        // 没有找到扩展语法，跳出
        break;
    }

    // 如果没有找到任何扩展语法，返回 None 表示未处理
    if nodes.is_empty() {
        // 返回原始文本
        vec![InlineNode::Text(text.to_string())]
    } else {
        // 添加剩余文本
        if !remaining.is_empty() {
            nodes.push(InlineNode::Text(remaining.to_string()));
        }
        nodes
    }
}

/// 查找高亮语法 ==text==
pub fn find_highlight(text: &str) -> Option<(usize, String)> {
    if !text.starts_with("==") {
        return None;
    }

    // 查找结束 ==
    if let Some(end_pos) = text[2..].find("==") {
        let content = &text[2..2 + end_pos];
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

    // 查找结束 </u>
    if let Some(end_pos) = text[3..].find("</u>") {
        let content = &text[3..3 + end_pos];
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

    let content = &text[level as usize..].trim();

    // 检测 [!TYPE] 格式
    if content.starts_with("[!") {
        let end_bracket = content.find(']')?;
        let kind_str = &content[2..end_bracket];
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
    let content_lines: Vec<String> = lines.iter()
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
    fn test_find_highlight() {
        // 返回值: (内容结束位置, 内容)
        // "==hello==" -> 位置7是"hello"结束位置（从开头算起）
        assert_eq!(find_highlight("==hello=="), Some((7, "hello".to_string())));
        assert_eq!(find_highlight("==world== text"), Some((7, "world".to_string())));
        assert_eq!(find_highlight("==="), None); // 空内容
        assert_eq!(find_highlight("==test"), None); // 没有结束
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
