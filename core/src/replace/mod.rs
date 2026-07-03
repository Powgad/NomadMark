// =============================================================================
// 替换模块 - 文本替换功能
// =============================================================================
//
// 提供文本替换功能，依赖 search 模块和 history 模块。
//
// 功能:
// - 单个替换
// - 全部替换
// - 正则表达式替换
// - 替换操作记录到历史（支持撤销）
// =============================================================================

use crate::search::{Searcher, SearchOptions};
use crate::history::{History, EditCommand};

// =============================================================================
// 替换选项
// =============================================================================

/// 替换选项
#[derive(Clone, Debug)]
pub struct ReplaceOptions {
    /// 搜索选项
    pub search_options: SearchOptions,
    /// 是否记录到历史（用于撤销）
    pub record_history: bool,
}

impl Default for ReplaceOptions {
    fn default() -> Self {
        Self {
            search_options: SearchOptions::default(),
            record_history: true,
        }
    }
}

impl ReplaceOptions {
    /// 创建默认替换选项
    pub fn new() -> Self {
        Self::default()
    }

    /// 设置是否记录历史
    pub fn with_history(mut self, record: bool) -> Self {
        self.record_history = record;
        self
    }

    /// 设置搜索选项
    pub fn with_search_options(mut self, options: SearchOptions) -> Self {
        self.search_options = options;
        self
    }
}

// =============================================================================
// 替换操作结果
// =============================================================================

/// 替换操作结果
#[derive(Clone, Debug)]
pub struct ReplaceResult {
    /// 替换的数量
    pub count: usize,
    /// 新的内容
    pub new_content: String,
    /// 是否有任何替换
    pub has_changes: bool,
}

impl ReplaceResult {
    /// 创建无变化的结果
    pub fn no_change(content: String) -> Self {
        Self {
            count: 0,
            new_content: content,
            has_changes: false,
        }
    }

    /// 创建有变化的结果
    pub fn changed(count: usize, content: String) -> Self {
        Self {
            count,
            new_content: content,
            has_changes: true,
        }
    }
}

// =============================================================================
// 替换器
// =============================================================================

/// 替换器
///
/// 用于执行文本替换操作。
pub struct Replacer {
    /// 当前文档内容
    content: String,
}

impl Replacer {
    /// 创建新的替换器
    ///
    /// # 参数
    /// - `content`: 要操作的文档内容
    pub fn new(content: String) -> Self {
        Self { content }
    }

    /// 替换第一个匹配项
    ///
    /// # 参数
    /// - `query`: 搜索字符串
    /// - `replacement`: 替换字符串
    /// - `options`: 替换选项
    /// - `history`: 可选的历史记录引用
    ///
    /// # 返回
    /// 替换操作结果
    pub fn replace_first(
        &mut self,
        query: &str,
        replacement: &str,
        options: &ReplaceOptions,
        history: Option<&mut History>,
    ) -> ReplaceResult {
        let searcher = Searcher::new(self.content.clone());
        let results = searcher.search(query, &options.search_options);

        if let Some(result) = results.first() {
            let old_text = self.content[result.start..result.end].to_string();

            // 记录到历史
            if options.record_history {
                if let Some(h) = history {
                    h.push(EditCommand::replace(result.start, old_text.clone(), replacement.to_string()));
                }
            }

            // 执行替换
            self.content.replace_range(result.start..result.end, replacement);

            ReplaceResult::changed(1, self.content.clone())
        } else {
            ReplaceResult::no_change(self.content.clone())
        }
    }

    /// 替换所有匹配项
    ///
    /// # 参数
    /// - `query`: 搜索字符串
    /// - `replacement`: 替换字符串
    /// - `options`: 替换选项
    /// - `history`: 可选的历史记录引用
    ///
    /// # 返回
    /// 替换操作结果
    pub fn replace_all(
        &mut self,
        query: &str,
        replacement: &str,
        options: &ReplaceOptions,
        history: Option<&mut History>,
    ) -> ReplaceResult {
        let searcher = Searcher::new(self.content.clone());
        let results = searcher.search(query, &options.search_options);

        if results.is_empty() {
            return ReplaceResult::no_change(self.content.clone());
        }

        let old_content = self.content.clone();
        let mut offset = 0i32;

        for result in &results {
            let start = (result.start as i32 + offset) as usize;
            let end = (result.end as i32 + offset) as usize;

            self.content.replace_range(start..end, replacement);
            offset += replacement.len() as i32 - (result.end - result.start) as i32;
        }

        // 记录到历史（作为一次批量操作）
        if options.record_history {
            if let Some(h) = history {
                h.push(EditCommand::replace(0, old_content, self.content.clone()));
            }
        }

        ReplaceResult::changed(results.len(), self.content.clone())
    }

    /// 获取当前内容
    pub fn content(&self) -> &str {
        &self.content
    }

    /// 设置内容
    pub fn set_content(&mut self, content: String) {
        self.content = content;
    }

    /// 检查是否包含指定的文本
    ///
    /// # 参数
    /// - `query`: 搜索字符串
    /// - `options`: 搜索选项（使用默认选项）
    pub fn contains(&self, query: &str) -> bool {
        let searcher = Searcher::new(self.content.clone());
        searcher.contains(query, &SearchOptions::default())
    }

    /// 统计匹配数量
    ///
    /// # 参数
    /// - `query`: 搜索字符串
    /// - `options`: 搜索选项（使用默认选项）
    pub fn count_matches(&self, query: &str) -> usize {
        let searcher = Searcher::new(self.content.clone());
        searcher.count_matches(query, &SearchOptions::default())
    }
}

// =============================================================================
// 辅助函数
// =============================================================================

/// 快捷函数：替换第一个匹配项（不记录历史）
///
/// # 参数
/// - `content`: 原始内容
/// - `query`: 搜索字符串
/// - `replacement`: 替换字符串
///
/// # 返回
/// 替换后的新内容
pub fn replace_first_quick(content: &str, query: &str, replacement: &str) -> String {
    let mut replacer = Replacer::new(content.to_string());
    let options = ReplaceOptions {
        search_options: SearchOptions::default(),
        record_history: false,
    };
    replacer.replace_first(query, replacement, &options, None).new_content
}

/// 快捷函数：替换所有匹配项（不记录历史）
///
/// # 参数
/// - `content`: 原始内容
/// - `query`: 搜索字符串
/// - `replacement`: 替换字符串
///
/// # 返回
/// 替换后的新内容
pub fn replace_all_quick(content: &str, query: &str, replacement: &str) -> String {
    let mut replacer = Replacer::new(content.to_string());
    let options = ReplaceOptions {
        search_options: SearchOptions::default(),
        record_history: false,
    };
    replacer.replace_all(query, replacement, &options, None).new_content
}

// =============================================================================
// 单元测试
// =============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_replace_first() {
        let mut replacer = Replacer::new("你好世界，你好宇宙".to_string());
        let options = ReplaceOptions::new();
        let mut history = History::new();

        let result = replacer.replace_first("你好", "您好", &options, Some(&mut history));

        assert_eq!(result.count, 1);
        assert!(result.has_changes);
        assert_eq!(replacer.content(), "您好世界，你好宇宙");
        assert!(history.can_undo());
    }

    #[test]
    fn test_replace_all() {
        let mut replacer = Replacer::new("你好世界，你好宇宙".to_string());
        let options = ReplaceOptions::new();
        let mut history = History::new();

        let result = replacer.replace_all("你好", "您好", &options, Some(&mut history));

        assert_eq!(result.count, 2);
        assert!(result.has_changes);
        assert_eq!(replacer.content(), "您好世界，您好宇宙");
        assert!(history.can_undo());
    }

    #[test]
    fn test_replace_no_matches() {
        let mut replacer = Replacer::new("测试内容".to_string());
        let options = ReplaceOptions::new();
        let mut history = History::new();

        let result = replacer.replace_first("不存在", "替换", &options, Some(&mut history));

        assert_eq!(result.count, 0);
        assert!(!result.has_changes);
        assert_eq!(replacer.content(), "测试内容");
        assert!(!history.can_undo());
    }

    #[test]
    fn test_replace_without_history() {
        let mut replacer = Replacer::new("A B A".to_string());
        let options = ReplaceOptions {
            search_options: SearchOptions::default(),
            record_history: false,
        };

        let result = replacer.replace_all("A", "X", &options, None);

        assert_eq!(result.count, 2);
        assert_eq!(replacer.content(), "X B X");
    }

    #[test]
    fn test_replace_case_sensitive() {
        let mut replacer = Replacer::new("Hello hello HELLO".to_string());
        let mut search_options = SearchOptions::new();
        search_options.case_sensitive = true;

        let options = ReplaceOptions {
            search_options,
            record_history: false,
        };

        let result = replacer.replace_all("Hello", "Hi", &options, None);

        // 只替换大写开头的 Hello
        assert_eq!(result.count, 1);
        assert_eq!(replacer.content(), "Hi hello HELLO");
    }

    #[test]
    fn test_replace_with_offset() {
        let mut replacer = Replacer::new("cat category concatenate".to_string());
        let options = ReplaceOptions::new();

        let result = replacer.replace_all("cat", "dog", &options, None);

        assert_eq!(result.count, 3);
        // cat -> dog
        // category -> dogegory
        // concatenate -> condogenate (con+cat+enate -> con+dog+enate)
        assert_eq!(replacer.content(), "dog dogegory condogenate");
    }

    #[test]
    fn test_quick_functions() {
        // 测试快捷函数
        let content = "one two one";

        let result1 = replace_first_quick(content, "one", "1");
        assert_eq!(result1, "1 two one");

        let result2 = replace_all_quick(content, "one", "1");
        assert_eq!(result2, "1 two 1");
    }

    #[test]
    fn test_replacer_set_content() {
        let mut replacer = Replacer::new("原始内容".to_string());
        replacer.set_content("新内容".to_string());
        assert_eq!(replacer.content(), "新内容");
    }

    #[test]
    fn test_contains() {
        let replacer = Replacer::new("Hello World".to_string());
        assert!(replacer.contains("Hello"));
        assert!(replacer.contains("World"));
        assert!(!replacer.contains("Goodbye"));
    }

    #[test]
    fn test_count_matches() {
        let replacer = Replacer::new("one two three four one five".to_string());
        assert_eq!(replacer.count_matches("one"), 2);
        assert_eq!(replacer.count_matches("two"), 1);
        assert_eq!(replacer.count_matches("six"), 0);
    }

    #[test]
    fn test_replace_with_chinese() {
        let mut replacer = Replacer::new("测试中文，测试内容".to_string());
        let options = ReplaceOptions::new();

        let result = replacer.replace_all("测试", "验证", &options, None);

        assert_eq!(result.count, 2);
        assert_eq!(replacer.content(), "验证中文，验证内容");
    }

    #[test]
    fn test_replace_empty_query() {
        let mut replacer = Replacer::new("测试内容".to_string());
        let options = ReplaceOptions::new();

        let result = replacer.replace_all("", "替换", &options, None);

        assert_eq!(result.count, 0);
        assert!(!result.has_changes);
        assert_eq!(replacer.content(), "测试内容");
    }
}
