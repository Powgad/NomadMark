// =============================================================================
// 语法高亮模块
// =============================================================================
//
// 提供代码语法高亮功能，使用 syntect 库进行 token 分析。
// 针对 E-ink 墨水屏优化，使用灰度配色方案。
// =============================================================================

mod highlighter;
mod theme;
mod languages;

pub use highlighter::{CodeHighlighter, Highlighter, HighlightToken, TokenType};
pub use theme::{CodeHighlightTheme, EinkHighlightTheme};
pub use languages::{SupportedLanguage, LanguageSelector};

// 重新导出 syntect 的类型
pub use syntect::{
    parsing::{SyntaxSet, SyntaxReference},
    highlighting::{ThemeSet, Theme},
};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_module_exists() {
        // 确保模块可以正常导入
        assert!(true);
    }
}
