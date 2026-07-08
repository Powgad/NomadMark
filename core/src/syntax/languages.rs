// =============================================================================
// 支持的编程语言
// =============================================================================

use std::fmt;

/// 支持的编程语言列表
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum SupportedLanguage {
    // 脚本语言
    JavaScript,
    TypeScript,
    Python,
    Ruby,
    PHP,

    // 系统语言
    Rust,
    C,
    Cpp,
    Go,
    Swift,
    Java,
    Kotlin,

    // 标记语言
    Html,
    Css,
    Scss,
    Xml,

    // 配置语言
    Json,
    Yaml,
    Toml,
    Ini,

    // Shell
    Bash,
    Shell,
    PowerShell,

    // 数据库
    Sql,
    Markdown,

    // 其他
    PlainText,
    Unknown,
}

impl SupportedLanguage {
    /// 从语言标识符（如 "rs", "py", "javascript"）解析语言
    pub fn from_extension(ext: &str) -> Self {
        match ext.to_lowercase().as_str() {
            // Rust
            "rs" => SupportedLanguage::Rust,

            // C/C++
            "c" | "h" => SupportedLanguage::C,
            "cpp" | "cc" | "cxx" | "hpp" | "hxx" => SupportedLanguage::Cpp,

            // JavaScript/TypeScript
            "js" | "jsx" | "mjs" => SupportedLanguage::JavaScript,
            "ts" | "tsx" => SupportedLanguage::TypeScript,

            // Python
            "py" | "pyw" | "pyi" => SupportedLanguage::Python,

            // Web
            "html" | "htm" | "xhtml" => SupportedLanguage::Html,
            "css" => SupportedLanguage::Css,
            "scss" | "sass" => SupportedLanguage::Scss,
            "xml" => SupportedLanguage::Xml,

            // Go
            "go" => SupportedLanguage::Go,

            // Java/Kotlin
            "java" => SupportedLanguage::Java,
            "kt" | "kts" => SupportedLanguage::Kotlin,

            // Swift
            "swift" => SupportedLanguage::Swift,

            // Ruby
            "rb" => SupportedLanguage::Ruby,

            // PHP
            "php" => SupportedLanguage::PHP,

            // Shell
            "sh" | "bash" => SupportedLanguage::Bash,
            "shell" => SupportedLanguage::Shell,
            "ps1" | "psm1" => SupportedLanguage::PowerShell,

            // 配置
            "json" => SupportedLanguage::Json,
            "yml" | "yaml" => SupportedLanguage::Yaml,
            "toml" => SupportedLanguage::Toml,
            "ini" | "cfg" | "conf" => SupportedLanguage::Ini,

            // 数据库
            "sql" => SupportedLanguage::Sql,

            // Markdown
            "md" | "markdown" => SupportedLanguage::Markdown,

            // 默认
            _ => SupportedLanguage::Unknown,
        }
    }

    /// 从语言名称（如 "rust", "python", "javascript"）解析语言
    pub fn from_name(name: &str) -> Self {
        match name.to_lowercase().as_str() {
            "rust" | "rs" => SupportedLanguage::Rust,
            "c" => SupportedLanguage::C,
            "c++" | "cpp" | "cxx" => SupportedLanguage::Cpp,
            "javascript" | "js" | "jsx" => SupportedLanguage::JavaScript,
            "typescript" | "ts" | "tsx" => SupportedLanguage::TypeScript,
            "python" | "py" => SupportedLanguage::Python,
            "go" | "golang" => SupportedLanguage::Go,
            "java" => SupportedLanguage::Java,
            "kotlin" | "kt" => SupportedLanguage::Kotlin,
            "swift" => SupportedLanguage::Swift,
            "ruby" | "rb" => SupportedLanguage::Ruby,
            "php" => SupportedLanguage::PHP,
            "html" => SupportedLanguage::Html,
            "css" => SupportedLanguage::Css,
            "scss" | "sass" => SupportedLanguage::Scss,
            "xml" => SupportedLanguage::Xml,
            "bash" | "sh" => SupportedLanguage::Bash,
            "shell" => SupportedLanguage::Shell,
            "powershell" | "ps1" => SupportedLanguage::PowerShell,
            "json" => SupportedLanguage::Json,
            "yaml" | "yml" => SupportedLanguage::Yaml,
            "toml" => SupportedLanguage::Toml,
            "ini" => SupportedLanguage::Ini,
            "sql" => SupportedLanguage::Sql,
            "markdown" | "md" => SupportedLanguage::Markdown,
            "text" | "txt" | "plaintext" => SupportedLanguage::PlainText,
            _ => SupportedLanguage::Unknown,
        }
    }

    /// 获取 syntect 语法名称
    pub fn syntect_name(&self) -> Option<&'static str> {
        match self {
            SupportedLanguage::Rust => Some("Rust"),
            SupportedLanguage::C => Some("C"),
            SupportedLanguage::Cpp => Some("C++"),
            SupportedLanguage::JavaScript => Some("JavaScript (Babel)"),
            SupportedLanguage::TypeScript => Some("TypeScript/TSX"),
            SupportedLanguage::Python => Some("Python"),
            SupportedLanguage::Go => Some("Go"),
            SupportedLanguage::Java => Some("Java"),
            SupportedLanguage::Kotlin => Some("Kotlin"),
            SupportedLanguage::Swift => Some("Swift"),
            SupportedLanguage::Ruby => Some("Ruby"),
            SupportedLanguage::PHP => Some("PHP"),
            SupportedLanguage::Html => Some("HTML"),
            SupportedLanguage::Css => Some("CSS"),
            SupportedLanguage::Scss => Some("SCSS"),
            SupportedLanguage::Xml => Some("XML"),
            SupportedLanguage::Bash => Some("Bash"),
            SupportedLanguage::Shell => Some("Shell Script"),
            SupportedLanguage::PowerShell => Some("PowerShell"),
            SupportedLanguage::Json => Some("JSON"),
            SupportedLanguage::Yaml => Some("YAML"),
            SupportedLanguage::Toml => Some("TOML"),
            SupportedLanguage::Ini => Some("INI"),
            SupportedLanguage::Sql => Some("SQL"),
            SupportedLanguage::Markdown => Some("Markdown"),
            SupportedLanguage::PlainText => Some("Plain Text"),
            SupportedLanguage::Unknown => Some("Plain Text"),
        }
    }

    /// 是否支持该语言
    pub fn is_supported(&self) -> bool {
        !matches!(self, SupportedLanguage::Unknown)
    }
}

impl fmt::Display for SupportedLanguage {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let name = match self {
            SupportedLanguage::Rust => "Rust",
            SupportedLanguage::C => "C",
            SupportedLanguage::Cpp => "C++",
            SupportedLanguage::JavaScript => "JavaScript",
            SupportedLanguage::TypeScript => "TypeScript",
            SupportedLanguage::Python => "Python",
            SupportedLanguage::Go => "Go",
            SupportedLanguage::Java => "Java",
            SupportedLanguage::Kotlin => "Kotlin",
            SupportedLanguage::Swift => "Swift",
            SupportedLanguage::Ruby => "Ruby",
            SupportedLanguage::PHP => "PHP",
            SupportedLanguage::Html => "HTML",
            SupportedLanguage::Css => "CSS",
            SupportedLanguage::Scss => "SCSS",
            SupportedLanguage::Xml => "XML",
            SupportedLanguage::Bash => "Bash",
            SupportedLanguage::Shell => "Shell",
            SupportedLanguage::PowerShell => "PowerShell",
            SupportedLanguage::Json => "JSON",
            SupportedLanguage::Yaml => "YAML",
            SupportedLanguage::Toml => "TOML",
            SupportedLanguage::Ini => "INI",
            SupportedLanguage::Sql => "SQL",
            SupportedLanguage::Markdown => "Markdown",
            SupportedLanguage::PlainText => "Plain Text",
            SupportedLanguage::Unknown => "Unknown",
        };
        write!(f, "{}", name)
    }
}

/// 语言选择器（用于匹配代码块语言标识符）
#[derive(Debug, Clone)]
pub struct LanguageSelector {
    /// 语言列表
    languages: Vec<SupportedLanguage>,
}

impl Default for LanguageSelector {
    fn default() -> Self {
        Self {
            languages: vec![
                SupportedLanguage::Rust,
                SupportedLanguage::JavaScript,
                SupportedLanguage::TypeScript,
                SupportedLanguage::Python,
                SupportedLanguage::Go,
                SupportedLanguage::Java,
                SupportedLanguage::Kotlin,
                SupportedLanguage::Swift,
                SupportedLanguage::Cpp,
                SupportedLanguage::C,
                SupportedLanguage::Html,
                SupportedLanguage::Css,
                SupportedLanguage::Json,
                SupportedLanguage::Yaml,
                SupportedLanguage::Bash,
                SupportedLanguage::Sql,
                SupportedLanguage::Markdown,
            ],
        }
    }
}

impl LanguageSelector {
    pub fn new() -> Self {
        Self::default()
    }

    /// 从代码块语言标识符解析语言
    ///
    /// 支持多种格式：
    /// - 扩展名：```rs, ```py, ```js
    /// - 名称：```rust, ```python, ```javascript
    /// - 小写：```Rust, ```Python
    pub fn parse(&self, lang_str: Option<&str>) -> SupportedLanguage {
        match lang_str {
            Some(lang) if !lang.is_empty() => {
                // 首先尝试作为名称匹配
                let by_name = SupportedLanguage::from_name(lang);
                if by_name.is_supported() {
                    return by_name;
                }

                // 尝试作为扩展名匹配
                let by_ext = SupportedLanguage::from_extension(lang);
                if by_ext.is_supported() {
                    return by_ext;
                }

                SupportedLanguage::Unknown
            }
            _ => SupportedLanguage::PlainText,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_from_extension() {
        assert_eq!(SupportedLanguage::from_extension("rs"), SupportedLanguage::Rust);
        assert_eq!(SupportedLanguage::from_extension("py"), SupportedLanguage::Python);
        assert_eq!(SupportedLanguage::from_extension("js"), SupportedLanguage::JavaScript);
        assert_eq!(SupportedLanguage::from_extension("unknown"), SupportedLanguage::Unknown);
    }

    #[test]
    fn test_from_name() {
        assert_eq!(SupportedLanguage::from_name("rust"), SupportedLanguage::Rust);
        assert_eq!(SupportedLanguage::from_name("Rust"), SupportedLanguage::Rust);
        assert_eq!(SupportedLanguage::from_name("python"), SupportedLanguage::Python);
        assert_eq!(SupportedLanguage::from_name("javascript"), SupportedLanguage::JavaScript);
        assert_eq!(SupportedLanguage::from_name("unknown"), SupportedLanguage::Unknown);
    }

    #[test]
    fn test_selector_parse() {
        let selector = LanguageSelector::new();

        assert_eq!(selector.parse(Some("rust")), SupportedLanguage::Rust);
        assert_eq!(selector.parse(Some("rs")), SupportedLanguage::Rust);
        assert_eq!(selector.parse(Some("python")), SupportedLanguage::Python);
        assert_eq!(selector.parse(Some("py")), SupportedLanguage::Python);
        assert_eq!(selector.parse(Some("js")), SupportedLanguage::JavaScript);
        assert_eq!(selector.parse(Some("javascript")), SupportedLanguage::JavaScript);
        assert_eq!(selector.parse(Some("")), SupportedLanguage::PlainText);
        assert_eq!(selector.parse(None), SupportedLanguage::PlainText);
    }

    #[test]
    fn test_display() {
        assert_eq!(SupportedLanguage::Rust.to_string(), "Rust");
        assert_eq!(SupportedLanguage::Python.to_string(), "Python");
        assert_eq!(SupportedLanguage::Unknown.to_string(), "Unknown");
    }
}
