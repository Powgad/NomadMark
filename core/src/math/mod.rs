//! 数学公式解析和渲染模块
//!
//! 支持 LaTeX 数学公式语法：
//! - 行内公式：`$E=mc^2$`
//! - 块级公式：`$$\frac{-b \pm \sqrt{b^2-4ac}}{2a}$$`

use thiserror::Error;

/// 数学公式解析错误
#[derive(Error, Debug)]
pub enum MathParseError {
    #[error("未闭合的公式分隔符")]
    UnclosedDelimiter,

    #[error("无效的公式语法: {0}")]
    InvalidSyntax(String),

    #[error("公式内容为空")]
    EmptyContent,
}

/// 数学公式类型
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum MathMode {
    /// 行内模式 `$...$`
    Inline,
    /// 块级模式 `$$...$$`
    Display,
}

/// 解析的数学公式
#[derive(Clone, Debug)]
pub struct MathFormula {
    /// 公式类型
    pub mode: MathMode,
    /// LaTeX 内容（不含分隔符）
    pub content: String,
    /// 原始文本（含分隔符）
    pub raw: String,
}

impl MathFormula {
    /// 创建新的数学公式
    pub fn new(mode: MathMode, content: String, raw: String) -> Self {
        Self { mode, content, raw }
    }

    /// 创建行内公式
    pub fn inline(content: String, raw: String) -> Self {
        Self::new(MathMode::Inline, content, raw)
    }

    /// 创建块级公式
    pub fn display(content: String, raw: String) -> Self {
        Self::new(MathMode::Display, content, raw)
    }

    /// 检查是否为块级模式
    pub fn is_display(&self) -> bool {
        self.mode == MathMode::Display
    }
}

/// LaTeX 数学公式解析器
pub struct MathParser;

impl MathParser {
    /// 从文本中提取数学公式
    ///
    /// 语法规则：
    /// - `$...$` 表示行内公式
    /// - `$$...$$` 表示块级公式
    ///
    /// # 示例
    /// ```
    /// use nomadmark_core::math::{MathParser, MathMode};
    ///
    /// let text = "这是 $E=mc^2$ 和 $$\\frac{1}{2}$$ 的例子";
    /// let formulas = MathParser::parse_all(text).unwrap();
    /// assert_eq!(formulas.len(), 2);
    /// assert_eq!(formulas[0].mode, MathMode::Inline);
    /// assert_eq!(formulas[1].mode, MathMode::Display);
    /// ```
    pub fn parse_all(text: &str) -> Result<Vec<MathFormula>, MathParseError> {
        let mut formulas = Vec::new();
        let mut chars = text.char_indices().peekable();

        while let Some((pos, ch)) = chars.next() {
            match ch {
                '$' => {
                    // 检查是否是块级分隔符 $$
                    let is_display = if let Some(&(_, next_ch)) = chars.peek() {
                        next_ch == '$'
                    } else {
                        false
                    };

                    let start = if is_display {
                        // 跳过第二个 $
                        chars.next();
                        pos + 2
                    } else {
                        pos + 1
                    };

                    // 查找闭合分隔符
                    let delimiter = if is_display { "$$" } else { "$" };

                    let mut found = false;
                    while let Some((end_pos, end_ch)) = chars.next() {
                        if end_ch == '$' {
                            if is_display {
                                // 检查是否是 $$
                                if let Some(&(_, next_end)) = chars.peek() {
                                    if next_end == '$' {
                                        chars.next();
                                        let content = &text[start..end_pos];
                                        if content.trim().is_empty() {
                                            return Err(MathParseError::EmptyContent);
                                        }
                                        formulas.push(MathFormula::display(
                                            content.to_string(),
                                            format!("{}{}{}", delimiter, content, delimiter),
                                        ));
                                        found = true;
                                        break;
                                    }
                                }
                            } else {
                                let content = &text[start..end_pos];
                                if content.trim().is_empty() {
                                    return Err(MathParseError::EmptyContent);
                                }
                                formulas.push(MathFormula::inline(
                                    content.to_string(),
                                    format!("{}{}{}", delimiter, content, delimiter),
                                ));
                                found = true;
                                break;
                            }
                        }
                    }

                    if !found {
                        return Err(MathParseError::UnclosedDelimiter);
                    }
                }
                _ => {}
            }
        }

        Ok(formulas)
    }

    /// 解析单个数学公式（从字符串开头）
    pub fn parse_one(text: &str) -> Result<Option<MathFormula>, MathParseError> {
        let text = text.trim_start();

        if !text.starts_with('$') {
            return Ok(None);
        }

        // 检查是行内还是块级
        let (is_display, delimiter, start_offset) = if text.starts_with("$$") {
            (true, "$$", 2)
        } else {
            (false, "$", 1)
        };

        // 检查字符串长度
        if text.len() <= start_offset {
            return Err(MathParseError::EmptyContent);
        }

        // 查找闭合分隔符
        let end_pos = if is_display {
            // 查找 $$
            let mut i = start_offset;
            let mut found = false;
            let chars = text.char_indices();
            for (pos, ch) in chars {
                if pos < i {
                    continue;
                }
                if ch == '$' {
                    // 检查下一个字符是否也是 $
                    let remaining = &text[pos..];
                    if remaining.starts_with("$$") && pos >= start_offset {
                        found = true;
                        i = pos;
                        break;
                    }
                }
                i = pos + 1;
            }
            if !found {
                return Err(MathParseError::UnclosedDelimiter);
            }
            i
        } else {
            // 查找单个 $
            match text[start_offset..].find('$') {
                Some(pos) => start_offset + pos,
                None => return Err(MathParseError::UnclosedDelimiter),
            }
        };

        let content = &text[start_offset..end_pos];
        if content.trim().is_empty() {
            return Err(MathParseError::EmptyContent);
        }

        let raw = if is_display {
            format!("{}{}{}", delimiter, content, delimiter)
        } else {
            format!("{}{}{}", delimiter, content, delimiter)
        };

        let formula = if is_display {
            MathFormula::display(content.to_string(), raw)
        } else {
            MathFormula::inline(content.to_string(), raw)
        };

        Ok(Some(formula))
    }

    /// 检查文本是否以数学公式开头
    pub fn starts_with_formula(text: &str) -> bool {
        let text = text.trim_start();
        text.starts_with('$') || text.starts_with("$$")
    }

    /// 从文本中移除所有数学公式标记，返回纯文本
    pub fn strip_formulas(text: &str) -> String {
        let mut result = String::new();
        let mut chars = text.char_indices().peekable();
        let mut copy_until = 0;

        while let Some((pos, ch)) = chars.next() {
            match ch {
                '$' => {
                    // 检查是否是块级分隔符 $$
                    let is_display = if let Some(&(_, next_ch)) = chars.peek() {
                        next_ch == '$'
                    } else {
                        false
                    };

                    // 复制公式前的文本
                    if pos > copy_until {
                        result.push_str(&text[copy_until..pos]);
                    }

                    if is_display {
                        chars.next(); // 跳过第二个 $
                    }

                    // 查找闭合分隔符
                    let mut found = false;
                    while let Some((end_pos, end_ch)) = chars.next() {
                        if end_ch == '$' {
                            if is_display {
                                if let Some(&(_, next_end)) = chars.peek() {
                                    if next_end == '$' {
                                        chars.next();
                                        copy_until = end_pos + 2;
                                        found = true;
                                        break;
                                    }
                                }
                            } else {
                                copy_until = end_pos + 1;
                                found = true;
                                break;
                            }
                        }
                    }

                    if !found {
                        // 未找到闭合，保留原始文本
                        result.push(ch);
                        copy_until = pos + 1;
                    }
                    // 跳过公式内容，不添加到结果
                }
                _ => {}
            }
        }

        // 添加剩余文本
        if copy_until < text.len() {
            result.push_str(&text[copy_until..]);
        }

        result
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_inline_formula() {
        let formula = MathParser::parse_one("$x^2$").unwrap().unwrap();
        assert_eq!(formula.mode, MathMode::Inline);
        assert_eq!(formula.content, "x^2");
        assert_eq!(formula.raw, "$x^2$");
    }

    #[test]
    fn test_parse_display_formula() {
        let formula = MathParser::parse_one("$$x^2$$").unwrap().unwrap();
        assert_eq!(formula.mode, MathMode::Display);
        assert_eq!(formula.content, "x^2");
        assert_eq!(formula.raw, "$$x^2$$");
    }

    #[test]
    fn test_parse_complex_formula() {
        let formula = MathParser::parse_one(r"\frac{1}{2}").unwrap();
        assert!(formula.is_none());
    }

    #[test]
    fn test_parse_unclosed_formula() {
        let result = MathParser::parse_one("$x^2");
        assert!(matches!(result, Err(MathParseError::UnclosedDelimiter)));
    }

    #[test]
    fn test_parse_empty_formula() {
        let result = MathParser::parse_one("$$");
        assert!(matches!(result, Err(MathParseError::EmptyContent)));
    }

    #[test]
    fn test_parse_all_formulas() {
        let text = "这是 $x^2$ 和 $$\\frac{1}{2}$$ 的例子";
        let formulas = MathParser::parse_all(text).unwrap();
        assert_eq!(formulas.len(), 2);
        assert_eq!(formulas[0].mode, MathMode::Inline);
        assert_eq!(formulas[1].mode, MathMode::Display);
    }

    #[test]
    fn test_starts_with_formula() {
        assert!(MathParser::starts_with_formula("$x^2$"));
        assert!(MathParser::starts_with_formula("$$x^2$$"));
        assert!(!MathParser::starts_with_formula("text $x^2$"));
    }

    #[test]
    fn test_strip_formulas() {
        let text = "这是 $x^2$ 和 $$\\frac{1}{2}$$ 的例子";
        let stripped = MathParser::strip_formulas(text);
        assert_eq!(stripped, "这是  和  的例子");
    }
}
