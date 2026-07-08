// =============================================================================
// 代码高亮主题（E-ink 灰度方案）
// =============================================================================
//
// 针对 Supernote 墨水屏优化的代码高亮配色方案。
// 使用高对比度的灰度颜色，确保在各种灰度级别下可读。
// =============================================================================

use crate::bridge::types::Color;

/// 代码语法高亮配色（E-ink 灰度方案）
#[derive(Debug, Clone, Copy)]
pub struct CodeHighlightTheme {
    /// 关键字颜色（纯黑，最高权重）
    pub keyword: Color,
    /// 字符串颜色
    pub string: Color,
    /// 注释颜色（浅灰，低调显示）
    pub comment: Color,
    /// 函数名颜色
    pub function: Color,
    /// 变量名颜色
    pub variable: Color,
    /// 常量颜色
    pub constant: Color,
    /// 类型名颜色
    pub type_name: Color,
    /// 数字颜色
    pub number: Color,
    /// 操作符颜色
    pub operator: Color,
    /// 代码块背景颜色
    pub background: Color,
    /// 特殊符号颜色
    pub special_symbol: Color,
    /// 宏/装饰器颜色
    pub macro_name: Color,
    /// 标签/属性名颜色
    pub tag: Color,
    /// 属性值颜色
    pub attribute: Color,
}

impl Default for CodeHighlightTheme {
    fn default() -> Self {
        Self::eink()
    }
}

impl CodeHighlightTheme {
    /// E-ink 墨水屏主题（灰度方案）
    ///
    /// 设计原则：
    /// - 使用纯黑 (#000000) 作为关键字颜色（最高权重）
    /// - 浅灰 (#999999) 作为注释颜色（低调显示）
    /// - 中等灰度用于其他语法元素
    /// - 背景 #F5F5F5 提供轻微的视觉区分
    pub fn eink() -> Self {
        Self {
            keyword: Color::rgb(0x00, 0x00, 0x00),      // #000000 - 纯黑
            string: Color::rgb(0x44, 0x44, 0x44),       // #444444 - 深灰
            comment: Color::rgb(0x99, 0x99, 0x99),      // #999999 - 浅灰
            function: Color::rgb(0x22, 0x22, 0x22),    // #222222 - 接近纯黑
            variable: Color::rgb(0x33, 0x33, 0x33),     // #333333 - 中灰
            constant: Color::rgb(0x55, 0x55, 0x55),     // #555555 - 中灰
            type_name: Color::rgb(0x44, 0x44, 0x44),    // #444444 - 深灰
            number: Color::rgb(0x55, 0x55, 0x55),       // #555555 - 中灰
            operator: Color::rgb(0x66, 0x66, 0x66),     // #666666 - 中灰
            background: Color::rgb(0xF5, 0xF5, 0xF5),    // #F5F5F5 - 浅灰背景
            special_symbol: Color::rgb(0x55, 0x55, 0x55), // #555555 - 中灰
            macro_name: Color::rgb(0x44, 0x44, 0x44),   // #444444 - 深灰
            tag: Color::rgb(0x33, 0x33, 0x33),          // #333333 - 中灰
            attribute: Color::rgb(0x55, 0x55, 0x55),    // #555555 - 中灰
        }
    }

    /// 获取 token 对应的颜色
    ///
    /// 根据 token 类型返回对应的颜色。
    /// 如果 token 类型不匹配任何已知类型，返回默认文本颜色。
    pub fn color_for_token(&self, token_type: &str) -> Color {
        // 转换为小写进行匹配
        let token_lower = token_type.to_lowercase();

        match token_lower.as_str() {
            // 关键字
            "keyword" | "keyword.control" | "keyword.declaration" => self.keyword,

            // 字符串
            "string" | "string.quoted" | "string.single" | "string.double" | "string.template" => self.string,

            // 注释
            "comment" | "comment.line" | "comment.block" | "comment.line.documentation" => self.comment,

            // 函数
            "entity.name.function" | "support.function" | "meta.function" => self.function,

            // 变量
            "variable" | "variable.other" | "variable.parameter" => self.variable,

            // 常量（包括数字）
            "constant" | "constant.language" | "constant.numeric" | "constant.character" | "constant.integer" | "constant.float" => self.constant,

            // 类型
            "entity.name.type" | "entity.name.class" | "entity.name.struct" | "entity.name.enum"
            | "storage.type" | "support.type" => self.type_name,

            // 操作符
            "keyword.operator" | "punctuation.operator" => self.operator,

            // 特殊符号
            "punctuation" | "punctuation.bracket" | "punctuation.separator"
            | "punctuation.terminator" | "punctuation.definition" => self.special_symbol,

            // 宏/装饰器
            "entity.name.function.decorator" | "storage.modifier" => self.macro_name,

            // 标签/属性
            "entity.name.tag" | "meta.tag" => self.tag,
            "entity.other.attribute-name" => self.attribute,

            // 默认：使用中等灰度
            _ => Color::rgb(0x33, 0x33, 0x33),
        }
    }

    /// 获取默认文本颜色（用于未高亮的文本）
    pub fn default_text(&self) -> Color {
        Color::rgb(0x33, 0x33, 0x33) // #333333
    }
}

/// E-ink 高亮主题别名（用于语义化引用）
pub type EinkHighlightTheme = CodeHighlightTheme;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_eink_theme() {
        let theme = CodeHighlightTheme::eink();

        // 验证关键字颜色为纯黑
        assert_eq!(theme.keyword, Color::rgb(0x00, 0x00, 0x00));

        // 验证注释颜色为浅灰
        assert_eq!(theme.comment, Color::rgb(0x99, 0x99, 0x99));

        // 验证背景颜色
        assert_eq!(theme.background, Color::rgb(0xF5, 0xF5, 0xF5));
    }

    #[test]
    fn test_color_for_token() {
        let theme = CodeHighlightTheme::eink();

        // 关键字
        assert_eq!(theme.color_for_token("keyword"), Color::rgb(0x00, 0x00, 0x00));
        assert_eq!(theme.color_for_token("KEYWORD"), Color::rgb(0x00, 0x00, 0x00)); // 大小写不敏感

        // 字符串
        assert_eq!(theme.color_for_token("string"), Color::rgb(0x44, 0x44, 0x44));

        // 注释
        assert_eq!(theme.color_for_token("comment"), Color::rgb(0x99, 0x99, 0x99));

        // 默认（未知 token）
        assert_eq!(theme.color_for_token("unknown_token"), Color::rgb(0x33, 0x33, 0x33));
    }

    #[test]
    fn test_default_trait() {
        let theme1 = CodeHighlightTheme::default();
        let theme2 = CodeHighlightTheme::eink();

        assert_eq!(theme1.keyword, theme2.keyword);
        assert_eq!(theme1.comment, theme2.comment);
    }
}
