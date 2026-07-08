// =============================================================================
// 插入模块 - Markdown 语法快捷插入
// =============================================================================
//
// 提供便捷的 API 用于插入各种 Markdown 语法格式。
//
// 功能:
// - insert_heading: 插入标题 (H1-H6)
// - insert_bold/italic/strikethrough: 文本格式化
// - insert_code/code_block: 代码插入
// - insert_link/image: 链接和图片
// - insert_list: 无序/有序/任务列表
// - insert_table: 表格
// - insert_horizontal_rule: 水平分隔线
// =============================================================================

/// 插入指定级别的标题
///
/// # 参数
/// - `text`: 标题文本
/// - `level`: 标题级别 (1-6)
///
/// # 返回
/// Markdown 格式的标题字符串
///
/// # 示例
/// ```
/// assert_eq!(insert_heading("你好", 1), "# 你好\n");
/// assert_eq!(insert_heading("世界", 2), "## 世界\n");
/// ```
pub fn insert_heading(text: &str, level: u8) -> String {
    let hashes = "#".repeat(level.min(6) as usize);
    format!("{} {}\n", hashes, text)
}

/// 插入粗体文本
///
/// # 示例
/// ```
/// assert_eq!(insert_bold("文本"), "**文本**");
/// ```
pub fn insert_bold(text: &str) -> String {
    format!("**{}**", text)
}

/// 插入斜体文本
///
/// # 示例
/// ```
/// assert_eq!(insert_italic("文本"), "*文本*");
/// ```
pub fn insert_italic(text: &str) -> String {
    format!("*{}*", text)
}

/// 插入删除线文本
///
/// # 示例
/// ```
/// assert_eq!(insert_strikethrough("文本"), "~~文本~~");
/// ```
pub fn insert_strikethrough(text: &str) -> String {
    format!("~~{}~~", text)
}

/// 插入行内代码
///
/// # 示例
/// ```
/// assert_eq!(insert_inline_code("代码"), "`代码`");
/// ```
pub fn insert_inline_code(text: &str) -> String {
    format!("`{}`", text)
}

/// 插入代码块
///
/// # 参数
/// - `language`: 编程语言（用于语法高亮，空字符串表示无语言）
/// - `code`: 代码内容
///
/// # 示例
/// ```
/// assert_eq!(insert_code_block("rust", "fn main() {}"), "```rust\nfn main() {}\n```\n");
/// ```
pub fn insert_code_block(language: &str, code: &str) -> String {
    if language.is_empty() {
        format!("```\n{}\n```\n", code)
    } else {
        format!("```{}\n{}\n```\n", language, code)
    }
}

/// 插入链接
///
/// # 参数
/// - `text`: 链接文本
/// - `url`: 链接地址
///
/// # 示例
/// ```
/// assert_eq!(insert_link("百度", "https://baidu.com"), "[百度](https://baidu.com)");
/// ```
pub fn insert_link(text: &str, url: &str) -> String {
    format!("[{}]({})", text, url)
}

/// 插入图片
///
/// # 参数
/// - `alt`: 图片替代文本
/// - `url`: 图片 URL 或路径
///
/// # 示例
/// ```
/// assert_eq!(insert_image("标志", "logo.png"), "![标志](logo.png)");
/// ```
pub fn insert_image(alt: &str, url: &str) -> String {
    format!("![{}]({})", alt, url)
}

/// 插入无序列表
///
/// # 参数
/// - `items`: 列表项数组
///
/// # 示例
/// ```
/// assert_eq!(insert_bullet_list(&["苹果", "香蕉"]), "- 苹果\n- 香蕉\n");
/// ```
pub fn insert_bullet_list(items: &[&str]) -> String {
    items.iter()
        .map(|item| format!("- {}", item))
        .collect::<Vec<_>>()
        .join("\n")
        + "\n"
}

/// 插入有序列表
///
/// # 参数
/// - `items`: 列表项数组
///
/// # 示例
/// ```
/// assert_eq!(insert_ordered_list(&["第一", "第二"]), "1. 第一\n2. 第二\n");
/// ```
pub fn insert_ordered_list(items: &[&str]) -> String {
    items.iter()
        .enumerate()
        .map(|(i, item)| format!("{}. {}", i + 1, item))
        .collect::<Vec<_>>()
        .join("\n")
        + "\n"
}

/// 插入任务（复选框）列表
///
/// # 参数
/// - `items`: (是否完成, 文本) 元组数组
///
/// # 示例
/// ```
/// assert_eq!(insert_task_list(&[(true, "完成"), (false, "未完成")]), "[x] 完成\n[ ] 未完成\n");
/// ```
pub fn insert_task_list(items: &[(bool, &str)]) -> String {
    items.iter()
        .map(|(checked, item)| {
            let marker = if *checked { "[x]" } else { "[ ]" };
            format!("{} {}", marker, item)
        })
        .collect::<Vec<_>>()
        .join("\n")
        + "\n"
}

/// 插入表格
///
/// # 参数
/// - `headers`: 表头列数组
/// - `rows`: 表格数据行（每行是一个列数组）
///
/// # 示例
/// ```
/// let headers = vec!["姓名", "年龄"];
/// let rows = vec![vec!["张三", "30"], vec!["李四", "25"]];
/// let table = insert_table(&headers, &rows);
/// ```
pub fn insert_table(headers: &[&str], rows: &[Vec<&str>]) -> String {
    let mut result = String::new();

    // 表头行
    result.push_str("| ");
    result.push_str(&headers.join(" | "));
    result.push_str(" |\n");

    // 分隔行
    result.push_str("| ");
    result.push_str(&headers.iter().map(|_| "-----").collect::<Vec<_>>().join(" | "));
    result.push_str(" |\n");

    // 数据行
    for row in rows {
        result.push_str("| ");
        result.push_str(&row.join(" | "));
        result.push_str(" |\n");
    }

    result.push('\n');
    result
}

/// 插入水平分隔线
///
/// # 示例
/// ```
/// assert_eq!(insert_horizontal_rule(), "---\n");
/// ```
pub fn insert_horizontal_rule() -> String {
    "---\n".to_string()
}

/// 插入换行符（硬换行）
///
/// # 示例
/// ```
/// assert_eq!(insert_line_break(), "  \n");
/// ```
pub fn insert_line_break() -> String {
    "  \n".to_string()
}

/// 插入粗斜体组合
///
/// # 示例
/// ```
/// assert_eq!(insert_bold_italic("文本"), "***文本***");
/// ```
pub fn insert_bold_italic(text: &str) -> String {
    format!("***{}***", text)
}

// =============================================================================
// 单元测试
// =============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_insert_heading() {
        assert_eq!(insert_heading("测试", 1), "# 测试\n");
        assert_eq!(insert_heading("测试", 2), "## 测试\n");
        assert_eq!(insert_heading("测试", 6), "###### 测试\n");
        assert_eq!(insert_heading("测试", 10), "###### 测试\n"); // 限制最大为 6
    }

    #[test]
    fn test_text_formatting() {
        assert_eq!(insert_bold("文本"), "**文本**");
        assert_eq!(insert_italic("文本"), "*文本*");
        assert_eq!(insert_strikethrough("文本"), "~~文本~~");
        assert_eq!(insert_bold_italic("文本"), "***文本***");
    }

    #[test]
    fn test_code() {
        assert_eq!(insert_inline_code("代码"), "`代码`");
        assert_eq!(insert_code_block("rust", "fn main() {}"), "```rust\nfn main() {}\n```\n");
        assert_eq!(insert_code_block("", "文本"), "```\n文本\n```\n");
    }

    #[test]
    fn test_links() {
        assert_eq!(insert_link("百度", "https://baidu.com"), "[百度](https://baidu.com)");
        assert_eq!(insert_image("标志", "logo.png"), "![标志](logo.png)");
    }

    #[test]
    fn test_lists() {
        assert_eq!(insert_bullet_list(&["A", "B"]), "- A\n- B\n");
        assert_eq!(insert_ordered_list(&["X", "Y"]), "1. X\n2. Y\n");
        assert_eq!(insert_task_list(&[(true, "完成"), (false, "未完成")]), "[x] 完成\n[ ] 未完成\n");
    }

#[test]
    fn test_table() {
        let headers = vec!["列A", "列B"];
        let rows = vec![vec!["1", "2"]];
        let result = insert_table(&headers, &rows);

        assert!(result.contains("| 列A | 列B |"));
        // 分隔行实际格式：| ----- | ----- |
        assert!(result.contains("| ----- | ----- |"));
        assert!(result.contains("| 1 | 2 |"));
    }

    #[test]
    fn test_other() {
        assert_eq!(insert_horizontal_rule(), "---\n");
        assert_eq!(insert_line_break(), "  \n");
    }
}
