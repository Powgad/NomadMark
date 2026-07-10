// =============================================================================
// 搜索模块 - 全文搜索功能
// =============================================================================
//
// 提供全文搜索功能，支持：
// - 普通文本搜索
// - 正则表达式搜索
// - 大小写敏感/不敏感
// - 全词匹配
// - 搜索结果替换
// =============================================================================

// =============================================================================
// 搜索选项
// =============================================================================

/// 搜索选项
#[derive(Clone, Debug, Default)]
pub struct SearchOptions {
    /// 是否区分大小写
    pub case_sensitive: bool,
    /// 是否使用正则表达式
    pub regex: bool,
    /// 是否全词匹配
    pub whole_word: bool,
}

impl SearchOptions {
    /// 创建默认搜索选项（不区分大小写，普通搜索）
    pub fn new() -> Self {
        Self::default()
    }

    /// 设置大小写敏感
    pub fn with_case_sensitive(mut self, value: bool) -> Self {
        self.case_sensitive = value;
        self
    }

    /// 设置正则表达式模式
    pub fn with_regex(mut self, value: bool) -> Self {
        self.regex = value;
        self
    }

    /// 设置全词匹配
    pub fn with_whole_word(mut self, value: bool) -> Self {
        self.whole_word = value;
        self
    }
}

// =============================================================================
// 搜索结果
// =============================================================================

/// 搜索结果
///
/// 表示文档中一个匹配的位置和内容。
#[derive(Clone, Debug)]
pub struct SearchResult {
    /// 匹配的起始位置（字节偏移）
    pub start: usize,
    /// 匹配的结束位置（字节偏移）
    pub end: usize,
    /// 匹配的文本
    pub text: String,
    /// 所在行号（从 0 开始）
    pub line_number: usize,
}

impl SearchResult {
    /// 创建新的搜索结果
    pub fn new(start: usize, end: usize, text: String, line_number: usize) -> Self {
        Self {
            start,
            end,
            text,
            line_number,
        }
    }

    /// 获取匹配的长度
    pub fn len(&self) -> usize {
        self.end - self.start
    }
}

// =============================================================================
// 搜索器
// =============================================================================

/// 搜索器
///
/// 用于在文档内容中执行搜索操作。
pub struct Searcher {
    /// 文档内容
    content: String,
    /// 行起始位置索引（用于快速查找行号）
    line_offsets: Vec<usize>,
}

impl Searcher {
    /// 创建新的搜索器
    ///
    /// # 参数
    /// - `content`: 要搜索的文档内容
    pub fn new(content: String) -> Self {
        let mut line_offsets = Vec::new();
        line_offsets.push(0); // 第一行从位置 0 开始

        // 记录每行的起始位置
        for (i, c) in content.char_indices() {
            if c == '\n' {
                line_offsets.push(i + 1); // 下一行从换行符之后开始
            }
        }

        Self {
            content,
            line_offsets,
        }
    }

    /// 根据字节位置获取所在行号
    fn get_line_number(&self, position: usize) -> usize {
        match self.line_offsets.binary_search(&position) {
            Ok(line) => line,
            Err(line) => line.saturating_sub(1),
        }
    }

    /// 执行搜索
    ///
    /// # 参数
    /// - `query`: 搜索查询字符串
    /// - `options`: 搜索选项
    ///
    /// # 返回
    /// 搜索结果向量（按出现位置排序）
    pub fn search(&self, query: &str, options: &SearchOptions) -> Vec<SearchResult> {
        if query.is_empty() {
            return Vec::new();
        }

        if options.regex {
            // 正则表达式搜索
            self.search_regex(query, options)
        } else {
            // 普通文本搜索
            self.search_text(query, options)
        }
    }

    /// 普通文本搜索
    fn search_text(&self, query: &str, options: &SearchOptions) -> Vec<SearchResult> {
        let mut results = Vec::new();

        // 根据大小写选项准备搜索内容
        let content = if options.case_sensitive {
            self.content.clone()
        } else {
            self.content.to_lowercase()
        };

        let query = if options.case_sensitive {
            query.to_string()
        } else {
            query.to_lowercase()
        };

        let mut start = 0;
        while let Some(found) = content[start..].find(&query) {
            let absolute_start = start + found;
            let absolute_end = absolute_start + query.len();

            // 全词匹配检查
            if options.whole_word && !self.is_whole_word(absolute_start, absolute_end) {
                start = absolute_end;
                continue;
            }

            let line_number = self.get_line_number(absolute_start);
            let matched_text = self.content[absolute_start..absolute_end].to_string();

            results.push(SearchResult::new(
                absolute_start,
                absolute_end,
                matched_text,
                line_number,
            ));

            start = absolute_end;
        }

        results
    }

    /// 正则表达式搜索
    #[cfg(feature = "regex")]
    fn search_regex(&self, pattern: &str, options: &SearchOptions) -> Vec<SearchResult> {
        use regex::Regex;

        let mut results = Vec::new();

        // 构建正则表达式
        let re = if options.case_sensitive {
            Regex::new(pattern)
        } else {
            Regex::new(&format!("(?i){}", pattern))
        };

        let re = match re {
            Ok(r) => r,
            Err(_) => return results, // 正则表达式无效，返回空结果
        };

        for mat in re.find_iter(&self.content) {
            let line_number = self.get_line_number(mat.start());
            results.push(SearchResult::new(
                mat.start(),
                mat.end(),
                mat.as_str().to_string(),
                line_number,
            ));
        }

        results
    }

    /// 正则表达式搜索（无 regex 特性时的回退实现）
    #[cfg(not(feature = "regex"))]
    fn search_regex(&self, _pattern: &str, _options: &SearchOptions) -> Vec<SearchResult> {
        // 未启用 regex 特性，返回空结果
        Vec::new()
    }

    /// 检查指定范围是否构成完整的词
    ///
    /// 用于全词匹配模式。
    fn is_whole_word(&self, start: usize, end: usize) -> bool {
        let content = self.content.as_bytes();

        // 检查前面是否是词边界
        let prev_is_boundary = if start > 0 {
            let prev_char = self.content.chars().nth(start.saturating_sub(1)).unwrap_or(' ');
            Self::is_word_boundary(prev_char)
        } else {
            true // 字符串开头是边界
        };

        // 检查后面是否是词边界
        let next_is_boundary = if end < content.len() {
            let next_char = self.content.chars().nth(end).unwrap_or(' ');
            Self::is_word_boundary(next_char)
        } else {
            true // 字符串结尾是边界
        };

        prev_is_boundary && next_is_boundary
    }

    /// 判断字符是否是词边界
    fn is_word_boundary(c: char) -> bool {
        !c.is_alphanumeric() && c != '_'
    }

    /// 替换第一个匹配项
    ///
    /// # 参数
    /// - `query`: 搜索字符串
    /// - `replacement`: 替换字符串
    /// - `options`: 搜索选项
    ///
    /// # 返回
    /// 替换后的新内容
    pub fn replace_first(&self, query: &str, replacement: &str, options: &SearchOptions) -> String {
        if let Some(result) = self.search(query, options).first() {
            let mut new_content = self.content.clone();
            new_content.replace_range(result.start..result.end, replacement);
            new_content
        } else {
            self.content.clone()
        }
    }

    /// 替换所有匹配项
    ///
    /// # 参数
    /// - `query`: 搜索字符串
    /// - `replacement`: 替换字符串
    /// - `options`: 搜索选项
    ///
    /// # 返回
    /// 替换后的新内容
    pub fn replace_all(&self, query: &str, replacement: &str, options: &SearchOptions) -> String {
        let results = self.search(query, options);
        if results.is_empty() {
            return self.content.clone();
        }

        let mut new_content = self.content.clone();
        let mut offset = 0i32;

        for result in &results {
            let start = (result.start as i32 + offset) as usize;
            let end = (result.end as i32 + offset) as usize;
            new_content.replace_range(start..end, replacement);
            offset += replacement.len() as i32 - (result.end - result.start) as i32;
        }

        new_content
    }

    /// 统计匹配次数
    ///
    /// # 参数
    /// - `query`: 搜索字符串
    /// - `options`: 搜索选项
    pub fn count_matches(&self, query: &str, options: &SearchOptions) -> usize {
        self.search(query, options).len()
    }

    /// 检查是否存在匹配
    pub fn contains(&self, query: &str, options: &SearchOptions) -> bool {
        self.count_matches(query, options) > 0
    }
}

// =============================================================================
// 单元测试
// =============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_basic_search() {
        let searcher = Searcher::new("你好世界，你好宇宙".to_string());
        let options = SearchOptions::new();

        let results = searcher.search("你好", &options);
        assert_eq!(results.len(), 2); // 不区分大小写
    }

    #[test]
    fn test_case_sensitive() {
        let searcher = Searcher::new("Hello World".to_string());
        let mut options = SearchOptions::new();
        options.case_sensitive = true;

        let results = searcher.search("hello", &options);
        assert_eq!(results.len(), 0);

        let results = searcher.search("Hello", &options);
        assert_eq!(results.len(), 1);
    }

    #[test]
    fn test_case_insensitive() {
        let searcher = Searcher::new("Hello HELLO hello".to_string());
        let options = SearchOptions::new();

        let results = searcher.search("hello", &options);
        assert_eq!(results.len(), 3);
    }

    #[test]
    fn test_whole_word() {
        let searcher = Searcher::new("cat category concatenate".to_string());
        let mut options = SearchOptions::new();
        options.whole_word = true;

        let results = searcher.search("cat", &options);
        // 只有独立的 "cat" 被匹配
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].text, "cat");
    }

    #[test]
    fn test_line_number() {
        let searcher = Searcher::new("第一行\n第二行\n第三行".to_string());
        let options = SearchOptions::new();

        let results = searcher.search("第二行", &options);
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].line_number, 1);
    }

    #[test]
    fn test_empty_query() {
        let searcher = Searcher::new("内容".to_string());
        let options = SearchOptions::new();

        let results = searcher.search("", &options);
        assert_eq!(results.len(), 0);
    }

    #[test]
    fn test_replace_first() {
        let searcher = Searcher::new("你好世界，你好宇宙".to_string());
        let options = SearchOptions::new();

        let result = searcher.replace_first("你好", "您好", &options);
        assert_eq!(result, "您好世界，你好宇宙");
    }

    #[test]
    fn test_replace_all() {
        let searcher = Searcher::new("你好世界，你好宇宙".to_string());
        let options = SearchOptions::new();

        let result = searcher.replace_all("你好", "您好", &options);
        assert_eq!(result, "您好世界，您好宇宙");
    }

    #[test]
    fn test_count_matches() {
        let searcher = Searcher::new("one two three four one five".to_string());
        let options = SearchOptions::new();

        assert_eq!(searcher.count_matches("one", &options), 2);
        assert_eq!(searcher.count_matches("two", &options), 1);
        assert_eq!(searcher.count_matches("six", &options), 0);
    }

    #[test]
    fn test_contains() {
        let searcher = Searcher::new("Hello World".to_string());
        let options = SearchOptions::new();

        assert!(searcher.contains("hello", &options));
        assert!(searcher.contains("World", &options));
        assert!(!searcher.contains("Goodbye", &options));
    }

    #[test]
    fn test_chinese_search() {
        let searcher = Searcher::new("测试中文搜索功能，中文很重要".to_string());
        let options = SearchOptions::new();

        let results = searcher.search("中文", &options);
        assert_eq!(results.len(), 2);
    }

    #[test]
    fn test_search_options_builder() {
        let options = SearchOptions::new()
            .with_case_sensitive(true)
            .with_whole_word(true)
            .with_regex(false);

        assert!(options.case_sensitive);
        assert!(options.whole_word);
        assert!(!options.regex);
    }
}
