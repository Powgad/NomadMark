// =============================================================================
// 代码语法高亮器
// =============================================================================
//
// 使用 syntect 库进行代码语法分析，为代码块生成高亮 token。
// 支持多种编程语言，针对 E-ink 墨水屏优化。
// =============================================================================

use crate::syntax::theme::CodeHighlightTheme;
use crate::syntax::languages::{SupportedLanguage, LanguageSelector};
use syntect::parsing::{SyntaxSet, SyntaxReference, ParseState};
use std::sync::OnceLock;

// 全局语法集（懒加载）
static SYNTAX_SET: OnceLock<SyntaxSet> = OnceLock::new();

/// 获取全局语法集
fn get_syntax_set() -> &'static SyntaxSet {
    SYNTAX_SET.get_or_init(|| {
        // 使用内置的语法集
        SyntaxSet::load_defaults_newlines()
    })
}

/// 高亮 token
#[derive(Debug, Clone)]
pub struct HighlightToken {
    /// token 文本
    pub text: String,
    /// token 类型（用于着色）
    pub token_type: TokenType,
    /// 在原始文本中的起始位置
    pub offset: usize,
}

/// Token 类型（简化版，用于 E-ink 主题映射）
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum TokenType {
    /// 关键字
    Keyword,
    /// 字符串
    String,
    /// 注释
    Comment,
    /// 函数名
    Function,
    /// 变量名
    Variable,
    /// 常量（包括数字）
    Constant,
    /// 类型名
    Type,
    /// 操作符
    Operator,
    /// 标签（HTML/XML 标签）
    Tag,
    /// 属性名
    Attribute,
    /// 普通文本
    Text,
    /// 宏/装饰器
    Macro,
    /// 特殊符号（括号、分号等）
    Special,
}

impl TokenType {
    /// 从 scope 字符串解析 token 类型
    fn from_scope_str(scope_str: &str) -> Self {
        let parts: Vec<&str> = scope_str.split('.').collect();

        for part in parts {
            let part_lower = part.to_lowercase();

            match part_lower.as_str() {
                // 关键字
                s if s.contains("keyword") => return TokenType::Keyword,

                // 字符串
                s if s.contains("string") => return TokenType::String,

                // 注释
                s if s.contains("comment") => return TokenType::Comment,

                // 函数
                s if s.contains("entity.name.function") || s.contains("support.function") => return TokenType::Function,

                // 变量
                s if s.contains("variable.other") => return TokenType::Variable,

                // 常量
                s if s.contains("constant") => return TokenType::Constant,

                // 类型
                s if s.contains("entity.name.type") || s.contains("entity.name.class")
                    || s.contains("entity.name.struct") || s.contains("entity.name.enum")
                    || s.contains("storage.type") || s.contains("support.type") => return TokenType::Type,

                // 操作符
                s if s.contains("keyword.operator") || s.contains("punctuation.operator") => return TokenType::Operator,

                // 标签
                s if s.contains("entity.name.tag") || s.contains("meta.tag") => return TokenType::Tag,

                // 属性
                s if s.contains("entity.other.attribute-name") => return TokenType::Attribute,

                // 宏
                s if s.contains("entity.name.function.decorator") || s.contains("storage.modifier") => return TokenType::Macro,

                // 标点符号
                s if s.contains("punctuation") => return TokenType::Special,

                _ => continue,
            }
        }

        TokenType::Text
    }

    /// 获取 E-ink 主题颜色名称
    pub fn color_name(&self) -> &'static str {
        match self {
            TokenType::Keyword => "keyword",
            TokenType::String => "string",
            TokenType::Comment => "comment",
            TokenType::Function => "function",
            TokenType::Variable => "variable",
            TokenType::Constant => "constant",
            TokenType::Type => "type_name",
            TokenType::Operator => "operator",
            TokenType::Tag => "tag",
            TokenType::Attribute => "attribute",
            TokenType::Text => "variable",
            TokenType::Macro => "macro_name",
            TokenType::Special => "special_symbol",
        }
    }
}

/// 代码高亮器
pub struct CodeHighlighter {
    /// 语法集
    syntax_set: SyntaxSet,
    /// 语言选择器
    language_selector: LanguageSelector,
    /// 高亮主题
    theme: CodeHighlightTheme,
}

impl CodeHighlighter {
    /// 创建新的高亮器
    pub fn new() -> Self {
        Self {
            syntax_set: get_syntax_set().clone(),
            language_selector: LanguageSelector::new(),
            theme: CodeHighlightTheme::eink(),
        }
    }

    /// 使用自定义主题创建高亮器
    pub fn with_theme(theme: CodeHighlightTheme) -> Self {
        Self {
            syntax_set: get_syntax_set().clone(),
            language_selector: LanguageSelector::new(),
            theme,
        }
    }

    /// 获取当前主题
    pub fn theme(&self) -> &CodeHighlightTheme {
        &self.theme
    }

    /// 高亮代码块
    ///
    /// # 参数
    /// - `code`: 代码文本
    /// - `lang_str`: 语言标识符（如 "rust", "python", "js"）
    ///
    /// # 返回
    /// 高亮 token 列表
    pub fn highlight(&self, code: &str, lang_str: Option<&str>) -> Vec<HighlightToken> {
        // 解析语言
        let language = self.language_selector.parse(lang_str);

        // 如果是纯文本或不支持的语言，返回单个文本 token
        if !language.is_supported() {
            return vec![HighlightToken {
                text: code.to_string(),
                token_type: TokenType::Text,
                offset: 0,
            }];
        }

        // 获取语法引用
        let syntax = match self.get_syntax_for_language(language) {
            Some(s) => s,
            None => {
                // 如果找不到语法，返回纯文本
                return vec![HighlightToken {
                    text: code.to_string(),
                    token_type: TokenType::Text,
                    offset: 0,
                }];
            }
        };

        // 使用解析器进行高亮
        let mut tokens = Vec::new();
        let mut parse_state = ParseState::new(syntax);
        let mut char_offset = 0;

        for line in code.lines() {
            let ops = match parse_state.parse_line(line, &self.syntax_set) {
                Ok(ops) => ops,
                Err(_) => {
                    // 解析失败，添加整行作为文本
                    tokens.push(HighlightToken {
                        text: format!("{}\n", line),
                        token_type: TokenType::Text,
                        offset: char_offset,
                    });
                    char_offset += line.len() + 1;
                    continue;
                }
            };

            // 处理操作序列
            let mut line_offset = 0;
            for (idx, scope_stack) in ops {
                // 获取 scope 字符串（使用 debug 格式化）
                let scope_str = format!("{:?}", scope_stack);

                // 解析 token 类型
                let token_type = TokenType::from_scope_str(&scope_str);

                // 计算当前 token 的文本
                // 注意：syntect 返回的是 (usize, ScopeStack) 对
                // usize 表示该 token 在行中的起始位置
                let start = idx;
                let next_start = if line_offset < line.len() {
                    // 简单处理：每个 token 至少一个字符
                    start + 1
                } else {
                    start
                };

                let text = if start < line.len() {
                    &line[start..next_start.min(line.len())]
                } else {
                    ""
                };

                if !text.is_empty() {
                    tokens.push(HighlightToken {
                        text: text.to_string(),
                        token_type,
                        offset: char_offset + start,
                    });
                }

                line_offset = next_start;
            }

            // 添加换行符
            tokens.push(HighlightToken {
                text: "\n".to_string(),
                token_type: TokenType::Text,
                offset: char_offset + line.len(),
            });

            char_offset += line.len() + 1;
        }

        // 如果没有任何 token，返回原始代码作为文本
        if tokens.is_empty() {
            vec![HighlightToken {
                text: code.to_string(),
                token_type: TokenType::Text,
                offset: 0,
            }]
        } else {
            tokens
        }
    }

    /// 高亮单行代码（用于行内代码）
    pub fn highlight_line(&self, line: &str, lang_str: Option<&str>) -> Vec<HighlightToken> {
        self.highlight(line, lang_str)
    }

    /// 获取语言的语法引用
    fn get_syntax_for_language<'a>(&'a self, language: SupportedLanguage) -> Option<&'a SyntaxReference> {
        let syntax_name = language.syntect_name()?;

        // 首先尝试通过名称查找
        if let Some(syntax) = self.syntax_set.find_syntax_by_name(syntax_name) {
            return Some(syntax);
        }

        // 尝试通过扩展名查找
        let ext = match language {
            SupportedLanguage::Rust => "rs",
            SupportedLanguage::Python => "py",
            SupportedLanguage::JavaScript => "js",
            SupportedLanguage::TypeScript => "ts",
            SupportedLanguage::Go => "go",
            SupportedLanguage::Java => "java",
            SupportedLanguage::Kotlin => "kt",
            SupportedLanguage::Swift => "swift",
            SupportedLanguage::Ruby => "rb",
            SupportedLanguage::PHP => "php",
            SupportedLanguage::Html => "html",
            SupportedLanguage::Css => "css",
            SupportedLanguage::Json => "json",
            SupportedLanguage::Yaml => "yaml",
            SupportedLanguage::Bash => "sh",
            SupportedLanguage::Sql => "sql",
            _ => return None,
        };

        self.syntax_set.find_syntax_by_extension(ext)
    }
}

impl Default for CodeHighlighter {
    fn default() -> Self {
        Self::new()
    }
}

/// 简化的别名
pub type Highlighter = CodeHighlighter;

#[cfg(test)]
mod tests {
    use super::*;
    use crate::bridge::types::Color;

    #[test]
    fn test_highlighter_creation() {
        let highlighter = CodeHighlighter::new();
        // 验证主题已设置
        assert_eq!(highlighter.theme().keyword, Color::rgb(0x00, 0x00, 0x00));
    }

    #[test]
    fn test_token_type_from_scope() {
        // 测试 scope 解析
        let token_type = TokenType::from_scope_str("keyword.control.rust");
        assert_eq!(token_type, TokenType::Keyword);

        let token_type2 = TokenType::from_scope_str("string.quoted.double");
        assert_eq!(token_type2, TokenType::String);

        let token_type3 = TokenType::from_scope_str("comment.line");
        assert_eq!(token_type3, TokenType::Comment);
    }

    #[test]
    fn test_highlight_simple_text() {
        let highlighter = CodeHighlighter::new();
        let code = "simple text";
        let tokens = highlighter.highlight(code, None);

        // 无语言标识符应该返回纯文本
        assert!(tokens.len() >= 1);
        // 验证至少有一个 token
        assert!(!tokens.is_empty());
    }

    #[test]
    fn test_token_type_color_name() {
        assert_eq!(TokenType::Keyword.color_name(), "keyword");
        assert_eq!(TokenType::String.color_name(), "string");
        assert_eq!(TokenType::Comment.color_name(), "comment");
        assert_eq!(TokenType::Text.color_name(), "variable");
    }
}
