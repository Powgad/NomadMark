# NomadMark 缺失功能实现指南

> **版本**: 1.0
> **日期**: 2026-07-02
> **目标**: 完成阶段 1 (Core 层) 和阶段 2 (Android 平台) 的缺失功能

---

## 目录

1. [实现策略](#实现策略)
2. [阶段一：Core 层基础功能](#阶段一core-层基础功能)
3. [阶段二：Android 平台独立功能](#阶段二android-平台独立功能)
4. [阶段三：Core 层高级功能](#阶段三core-层高级功能)
5. [阶段四：Android 平台集成](#阶段四android-平台集成)
6. [阶段五：细化与优化](#阶段五细化与优化)
7. [验证清单](#验证清单)

---

## 实现策略

### 依赖关系图

```
┌─────────────────────────────────────────────────────────────────┐
│                         实现依赖关系                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  [独立基础]                                                      │
│  ├── md_document_create()                                       │
│  ├── insert.rs (快捷插入)                                       │
│  ├── KeyboardDetector.kt                                       │
│  └── 文件操作细化                                                │
│         │                                                       │
│         ▼                                                       │
│  [核心模块]                                                      │
│  ├── history/ (历史记录) ←──────┐                              │
│  ├── search/ (搜索)              │                              │
│  └── replace/ (替换) ────────────┘                              │
│         │                                                       │
│         ▼                                                       │
│  [FFI 接口]                                                      │
│  ├── md_document_search()                                       │
│  ├── md_document_undo/redo()                                    │
│  └── Core::save()                                              │
│         │                                                       │
│         ▼                                                       │
│  [集成测试]                                                      │
│  ├── Android 集成 Core                                          │
│  ├── 分屏滚动同步                                                │
│  └── 双指缩放                                                   │
│         │                                                       │
│         ▼                                                       │
│  [细化优化]                                                      │
│  ├── 修订模式细化                                                │
│  ├── 快捷工具栏动画                                              │
│  └── E-ink 刷新优化                                              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 时间线规划

| 阶段 | 内容 | 预计工时 | 累计进度 |
|------|------|----------|----------|
| 阶段一 | Core 层基础功能 | 12h | Day 1-2 |
| 阶段二 | Android 独立功能 | 10h | Day 3-4 |
| 阶段三 | Core 层高级功能 | 18h | Day 5-7 |
| 阶段四 | Android 集成 | 12h | Day 8-9 |
| 阶段五 | 细化与优化 | 14h | Day 10-12 |
| **总计** | | **66h** | **~12 工作日** |

---

## 阶段一：Core 层基础功能

> **目标**: 完成 Core 层可独立实现的基础功能
> **预计工时**: 12 小时
> **依赖**: 无

---

### 步骤 1.1：实现 md_document_create()

**文件**: `core/src/lib.rs`

**当前状态**: 函数已声明但返回空指针 (TODO)

**实现目标**: 从内存字节数组创建文档实例

#### 实现步骤

1. **创建内部辅助函数**

```rust
// 在 lib.rs 中添加
/// Create document from in-memory bytes
fn create_document_from_bytes(content: &[u8]) -> Option<MarkdownDocument> {
    // 将内容转换为字符串
    let content_str = std::str::from_utf8(content).ok()?;

    // 创建临时文件用于 StreamingParser（因为 StreamingParser 需要文件路径）
    // 或者：修改 StreamingParser 支持直接从内存创建

    // 方案：使用 memmap2 的匿名映射或临时文件
    // 暂时使用临时文件方案
    use std::io::Write;
    let temp_dir = std::env::temp_dir();
    let temp_file = temp_dir.join(format!("nomadmark_temp_{}.md", uuid::Uuid::new_v4()));

    // 写入临时文件
    if let Ok(mut file) = std::fs::File::create(&temp_file) {
        if file.write_all(content).is_ok() {
            // 使用 StreamingParser 加载
            if let Ok(parser) = StreamingParser::new(&temp_file) {
                // 清理临时文件
                let _ = std::fs::remove_file(&temp_file);
                return Some(MarkdownDocument::from_streaming(parser));
            }
        }
    }

    None
}
```

2. **修改 md_document_create()**

```rust
#[no_mangle]
pub extern "C" fn md_document_create(
    content: *const c_char,
    len: usize,
) -> *mut MarkdownDocument {
    if content.is_null() || len == 0 {
        return std::ptr::null_mut();
    }

    unsafe {
        let bytes = slice::from_raw_parts(content as *const u8, len);
        match create_document_from_bytes(bytes) {
            Some(doc) => Box::into_raw(Box::new(doc)),
            None => std::ptr::null_mut(),
        }
    }
}
```

#### 验证方法

```rust
#[cfg(test)]
mod tests {
    #[test]
    fn test_md_document_create() {
        let content = "# Test\n\nHello world";
        let ptr = md_document_create(content.as_ptr() as *const c_char, content.len());

        assert!(!ptr.is_null());

        // 清理
        md_document_release(ptr);
    }
}
```

#### 预计工时: 2 小时

---

### 步骤 1.2：创建快捷插入 API (insert.rs)

**文件**: `core/src/insert.rs` (新建)

**实现目标**: 提供快捷插入各种 Markdown 语法的 API

#### 实现步骤

1. **创建文件结构**

```rust
// core/src/insert.rs

/// 快捷插入模块 - 提供各种 Markdown 语法的快捷插入功能

/// 插入标题
pub fn insert_heading(text: &str, level: u8) -> String {
    let hashes = "#".repeat(level.min(6) as usize);
    format!("{} {}\n", hashes, text)
}

/// 插入粗体文本
pub fn insert_bold(text: &str) -> String {
    format!("**{}**", text)
}

/// 插入斜体文本
pub fn insert_italic(text: &str) -> String {
    format!("*{}*", text)
}

/// 插入删除线文本
pub fn insert_strikethrough(text: &str) -> String {
    format!("~~{}~~", text)
}

/// 插入行内代码
pub fn insert_inline_code(text: &str) -> String {
    format!("`{}`", text)
}

/// 插入代码块
pub fn insert_code_block(language: &str, code: &str) -> String {
    if language.is_empty() {
        format!("```\n{}\n```\n", code)
    } else {
        format!("```{}\n{}\n```\n", language, code)
    }
}

/// 插入链接
pub fn insert_link(text: &str, url: &str) -> String {
    format!("[{}]({})", text, url)
}

/// 插入图片
pub fn insert_image(alt: &str, url: &str) -> String {
    format!("![{}]({})", alt, url)
}

/// 插入无序列表
pub fn insert_bullet_list(items: &[&str]) -> String {
    items.iter()
        .map(|item| format!("- {}", item))
        .collect::<Vec<_>>()
        .join("\n")
        + "\n"
}

/// 插入有序列表
pub fn insert_ordered_list(items: &[&str]) -> String {
    items.iter()
        .enumerate()
        .map(|(i, item)| format!("{}. {}", i + 1, item))
        .collect::<Vec<_>>()
        .join("\n")
        + "\n"
}

/// 插入任务列表
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

/// 插入引用
pub fn insert_quote(text: &str) -> String {
    format!("> {}\n", text)
}

/// 插入表格
pub fn insert_table(headers: &[&str], rows: &[Vec<&str>]) -> String {
    let mut result = String::new();

    // 表头
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
pub fn insert_horizontal_rule() -> String {
    "---\n".to_string()
}

/// 插入换行
pub fn insert_line_break() -> String {
    "  \n".to_string()
}
```

2. **在 lib.rs 中注册模块**

```rust
// 在 lib.rs 顶部添加
pub mod insert;
```

#### 单元测试

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_insert_heading() {
        assert_eq!(insert_heading("Test", 1), "# Test\n");
        assert_eq!(insert_heading("Test", 2), "## Test\n");
    }

    #[test]
    fn test_insert_bold() {
        assert_eq!(insert_bold("text"), "**text**");
    }

    #[test]
    fn test_insert_table() {
        let headers = vec!["列1", "列2"];
        let rows = vec![vec!["A", "B"]];
        let result = insert_table(&headers, &rows);
        assert!(result.contains("| 列1 | 列2 |"));
    }
}
```

#### 预计工时: 3 小时

---

### 步骤 1.3：创建历史记录模块 (history/)

**文件**: `core/src/history/mod.rs` (新建)

**实现目标**: 实现撤销/重做栈

#### 实现步骤

1. **创建模块结构**

```rust
// core/src/history/mod.rs

/// 历史记录模块 - 支持撤销/重做功能

use std::collections::VecDeque;

/// 最大历史记录数量
const MAX_HISTORY: usize = 100;

/// 操作命令类型
#[derive(Clone, Debug)]
pub enum EditCommand {
    /// 插入文本
    Insert {
        position: usize,
        text: String,
    },
    /// 删除文本
    Delete {
        position: usize,
        text: String, // 保存被删除的文本用于恢复
    },
    /// 替换文本
    Replace {
        position: usize,
        old_text: String,
        new_text: String,
    },
}

impl EditCommand {
    /// 创建插入命令
    pub fn insert(position: usize, text: String) -> Self {
        EditCommand::Insert { position, text }
    }

    /// 创建删除命令
    pub fn delete(position: usize, text: String) -> Self {
        EditCommand::Delete { position, text }
    }

    /// 创建替换命令
    pub fn replace(position: usize, old_text: String, new_text: String) -> Self {
        EditCommand::Replace { position, old_text, new_text }
    }

    /// 获取反向命令（用于撤销）
    pub fn inverse(&self) -> EditCommand {
        match self {
            EditCommand::Insert { position, text } => {
                EditCommand::Delete {
                    position: *position,
                    text: text.clone(),
                }
            }
            EditCommand::Delete { position, text } => {
                EditCommand::Insert {
                    position: *position,
                    text: text.clone(),
                }
            }
            EditCommand::Replace { position, old_text, new_text } => {
                EditCommand::Replace {
                    position: *position,
                    old_text: new_text.clone(),
                    new_text: old_text.clone(),
                }
            }
        }
    }
}

/// 历史记录栈
#[derive(Clone, Debug)]
pub struct History {
    /// 撤销栈
    undo_stack: VecDeque<EditCommand>,
    /// 重做栈
    redo_stack: VecDeque<EditCommand>,
    /// 保存点索引（用于判断是否已修改）
    saved_index: Option<usize>,
}

impl History {
    /// 创建新的历史记录
    pub fn new() -> Self {
        Self {
            undo_stack: VecDeque::with_capacity(MAX_HISTORY),
            redo_stack: VecDeque::with_capacity(MAX_HISTORY),
            saved_index: None,
        }
    }

    /// 添加一个操作到历史记录
    pub fn push(&mut self, command: EditCommand) {
        // 清空重做栈（有新操作时重做失效）
        self.redo_stack.clear();

        // 添加到撤销栈
        if self.undo_stack.len() >= MAX_HISTORY {
            self.undo_stack.pop_front();
        }
        self.undo_stack.push_back(command);
    }

    /// 撤销上一个操作
    pub fn undo(&mut self) -> Option<&EditCommand> {
        if let Some(command) = self.undo_stack.pop_back() {
            self.redo_stack.push_back(command.inverse());
            self.redo_stack.back()
        } else {
            None
        }
    }

    /// 重做上一个撤销的操作
    pub fn redo(&mut self) -> Option<&EditCommand> {
        if let Some(command) = self.redo_stack.pop_back() {
            self.undo_stack.push_back(command.inverse());
            self.undo_stack.back()
        } else {
            None
        }
    }

    /// 检查是否可以撤销
    pub fn can_undo(&self) -> bool {
        !self.undo_stack.is_empty()
    }

    /// 检查是否可以重做
    pub fn can_redo(&self) -> bool {
        !self.redo_stack.is_empty()
    }

    /// 设置保存点
    pub fn set_saved(&mut self) {
        self.saved_index = Some(self.undo_stack.len());
    }

    /// 检查是否有未保存的修改
    pub fn is_modified(&self) -> bool {
        self.saved_index != Some(self.undo_stack.len())
    }

    /// 清空历史记录
    pub fn clear(&mut self) {
        self.undo_stack.clear();
        self.redo_stack.clear();
        self.saved_index = Some(0);
    }
}

impl Default for History {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_insert_and_undo() {
        let mut history = History::new();
        let cmd = EditCommand::insert(0, "Hello".to_string());

        history.push(cmd);
        assert!(history.can_undo());

        history.undo();
        assert!(!history.can_undo());
        assert!(history.can_redo());
    }

    #[test]
    fn test_modified_flag() {
        let mut history = History::new();
        history.set_saved();
        assert!(!history.is_modified());

        history.push(EditCommand::insert(0, "text".to_string()));
        assert!(history.is_modified());
    }
}
```

2. **在 lib.rs 中注册模块**

```rust
pub mod history;
use history::{History, EditCommand};
```

#### 预计工时: 4 小时

---

### 步骤 1.4：创建搜索模块 (search/)

**文件**: `core/src/search/mod.rs` (新建)

**实现目标**: 实现全文搜索功能

#### 实现步骤

1. **创建模块结构**

```rust
// core/src/search/mod.rs

/// 搜索模块 - 全文搜索功能

use std::ops::Range;

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

/// 搜索结果
#[derive(Clone, Debug)]
pub struct SearchResult {
    /// 匹配的起始位置
    pub start: usize,
    /// 匹配的结束位置
    pub end: usize,
    /// 匹配的文本
    pub text: String,
    /// 所在行号
    pub line_number: usize,
}

impl SearchResult {
    pub fn new(start: usize, end: usize, text: String, line_number: usize) -> Self {
        Self {
            start,
            end,
            text,
            line_number,
        }
    }
}

/// 搜索器
pub struct Searcher {
    content: String,
    /// 行起始位置索引（用于快速查找行号）
    line_offsets: Vec<usize>,
}

impl Searcher {
    /// 创建新的搜索器
    pub fn new(content: String) -> Self {
        let mut line_offsets = Vec::new();
        line_offsets.push(0);

        for (i, c) in content.char_indices() {
            if c == '\n' {
                line_offsets.push(i + 1);
            }
        }

        Self {
            content,
            line_offsets,
        }
    }

    /// 根据位置获取行号
    fn get_line_number(&self, position: usize) -> usize {
        match self.line_offsets.binary_search(&position) {
            Ok(line) => line,
            Err(line) => line.saturating_sub(1),
        }
    }

    /// 执行搜索
    pub fn search(&self, query: &str, options: &SearchOptions) -> Vec<SearchResult> {
        if query.is_empty() {
            return Vec::new();
        }

        let mut results = Vec::new();

        if options.regex {
            // 正则表达式搜索
            results = self.search_regex(query, options);
        } else {
            // 普通文本搜索
            results = self.search_text(query, options);
        }

        results
    }

    /// 普通文本搜索
    fn search_text(&self, query: &str, options: &SearchOptions) -> Vec<SearchResult> {
        let mut results = Vec::new();
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
        while let Some(found) = content[&start..].find(&query) {
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
    fn search_regex(&self, pattern: &str, options: &SearchOptions) -> Vec<SearchResult> {
        use regex::Regex;

        let mut results = Vec::new();

        let re = match Regex::new(pattern) {
            Ok(r) => r,
            Err(_) => return results,
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

    /// 检查是否是完整的词
    fn is_whole_word(&self, start: usize, end: usize) -> bool {
        let content = self.content.as_bytes();

        // 检查前面是否是词边界
        let prev_is_boundary = if start > 0 {
            let prev_char = content[start - 1] as char;
            !prev_char.is_alphanumeric() && prev_char != '_'
        } else {
            true
        };

        // 检查后面是否是词边界
        let next_is_boundary = if end < content.len() {
            let next_char = content[end] as char;
            !next_char.is_alphanumeric() && next_char != '_'
        } else {
            true
        };

        prev_is_boundary && next_is_boundary
    }

    /// 替换第一个匹配项
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
    pub fn replace_all(&self, query: &str, replacement: &str, options: &SearchOptions) -> String {
        let results = self.search(query, options);
        if results.is_empty() {
            return self.content.clone();
        }

        let mut new_content = self.content.clone();
        let mut offset = 0;

        for result in &results {
            let start = result.start + offset;
            let end = result.end + offset;
            new_content.replace_range(start..end, replacement);
            offset += replacement.len() as isize - (result.end - result.start) as isize;
        }

        new_content
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_basic_search() {
        let searcher = Searcher::new("Hello world, hello universe".to_string());
        let options = SearchOptions::default();

        let results = searcher.search("hello", &options);
        assert_eq!(results.len(), 2); // 不区分大小写
    }

    #[test]
    fn test_case_sensitive() {
        let searcher = Searcher::new("Hello world".to_string());
        let mut options = SearchOptions::default();
        options.case_sensitive = true;

        let results = searcher.search("hello", &options);
        assert_eq!(results.len(), 0);

        let results = searcher.search("Hello", &options);
        assert_eq!(results.len(), 1);
    }
}
```

2. **在 lib.rs 中注册模块**

```rust
pub mod search;
use search::{Searcher, SearchResult, SearchOptions};
```

3. **添加 regex 依赖**

```toml
# Cargo.toml
[dependencies]
regex = "1"
```

#### 预计工时: 4 小时

---

### 步骤 1.5：创建替换模块 (replace/)

**文件**: `core/src/replace/mod.rs` (新建)

**实现目标**: 实现替换功能（依赖 search 和 history 模块）

#### 实现步骤

1. **创建模块结构**

```rust
// core/src/replace/mod.rs

/// 替换模块 - 文本替换功能

use crate::search::{Searcher, SearchOptions};
use crate::history::{History, EditCommand};

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

/// 替换器
pub struct Replacer {
    content: String,
}

impl Replacer {
    /// 创建新的替换器
    pub fn new(content: String) -> Self {
        Self { content }
    }

    /// 替换第一个匹配项
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
            // 记录到历史
            if options.record_history {
                if let Some(h) = history {
                    let old_text = self.content[result.start..result.end].to_string();
                    h.push(EditCommand::replace(result.start, old_text, replacement.to_string()));
                }
            }

            // 执行替换
            self.content.replace_range(result.start..result.end, replacement);

            ReplaceResult {
                count: 1,
                new_content: self.content.clone(),
                has_changes: true,
            }
        } else {
            ReplaceResult {
                count: 0,
                new_content: self.content.clone(),
                has_changes: false,
            }
        }
    }

    /// 替换所有匹配项
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
            return ReplaceResult {
                count: 0,
                new_content: self.content.clone(),
                has_changes: false,
            };
        }

        let mut offset = 0i32;
        let old_content = self.content.clone();

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

        ReplaceResult {
            count: results.len(),
            new_content: self.content.clone(),
            has_changes: true,
        }
    }

    /// 获取当前内容
    pub fn content(&self) -> &str {
        &self.content
    }

    /// 设置内容
    pub fn set_content(&mut self, content: String) {
        self.content = content;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_replace_first() {
        let mut replacer = Replacer::new("hello world hello universe".to_string());
        let options = ReplaceOptions::default();
        let mut history = History::new();

        let result = replacer.replace_first("hello", "Hi", &options, Some(&mut history));

        assert_eq!(result.count, 1);
        assert!(result.has_changes);
        assert!(replacer.content().starts_with("Hi world"));
    }

    #[test]
    fn test_replace_all() {
        let mut replacer = Replacer::new("hello world hello universe".to_string());
        let options = ReplaceOptions::default();
        let mut history = History::new();

        let result = replacer.replace_all("hello", "Hi", &options, Some(&mut history));

        assert_eq!(result.count, 2);
        assert!(result.has_changes);
        assert_eq!(replacer.content(), "Hi world Hi universe");
    }
}
```

2. **在 lib.rs 中注册模块**

```rust
pub mod replace;
use replace::{Replacer, ReplaceOptions, ReplaceResult};
```

#### 预计工时: 3 小时

---

### 阶段一总结

完成本阶段后，Core 层将具备：

- ✅ 从内存内容创建文档
- ✅ 快捷插入各种 Markdown 语法
- ✅ 完整的撤销/重做功能
- ✅ 全文搜索功能
- ✅ 文本替换功能

**预计总工时: 16 小时 (2 工作日)**

---

## 阶段二：Android 平台独立功能

> **目标**: 完成 Android 平台可独立实现的功能
> **预计工时**: 10 小时
> **依赖**: 无

---

### 步骤 2.1：创建键盘适配模块 (KeyboardDetector.kt)

**文件**: `android/app/src/main/java/com/editor/nomadmark/KeyboardDetector.kt` (新建)

**实现目标**: 检测键盘类型，支持分屏比例切换

#### 实现步骤

1. **创建 KeyboardDetector 类**

```kotlin
package com.editor.nomadmark

import android.content.Context
import android.view.Configuration
import android.view.inputmethod.InputMethodManager

/**
 * 键盘类型
 */
enum class KeyboardType {
    NONE,           // 无键盘
    SOFT_KEYBOARD,  // 软键盘
    F11_PHYSICAL    // F11 物理键盘
}

/**
 * 键盘检测器
 *
 * 功能：
 * - 检测外接键盘状态
 * - 检测软键盘状态
 * - 获取最优分屏比例
 */
class KeyboardDetector(private val context: Context) {

    companion object {
        /** F11 键盘标识码 */
        const val F11_KEYBOARD_ID = "f11"

        /** 软键盘分屏比例 (编辑区:预览区) */
        const val SOFT_KEYBOARD_SPLIT_RATIO = 0.5f

        /** F11 键盘分屏比例 */
        const val F11_KEYBOARD_SPLIT_RATIO = 0.4f
    }

    /**
     * 检测键盘类型
     */
    fun detectKeyboardType(): KeyboardType {
        // 检测是否有物理键盘
        if (hasPhysicalKeyboard()) {
            // 进一步检测是否是 F11 键盘
            if (isF11Keyboard()) {
                return KeyboardType.F11_PHYSICAL
            }
            return KeyboardType.F11_PHYSICAL // 暂时假设都是 F11
        }

        // 检测软键盘
        if (isSoftKeyboardShown()) {
            return KeyboardType.SOFT_KEYBOARD
        }

        return KeyboardType.NONE
    }

    /**
     * 检测是否有物理键盘
     */
    fun hasPhysicalKeyboard(): Boolean {
        val config = context.resources.configuration
        return config.keyboard == Configuration.KEYBOARD_NOKEYS
    }

    /**
     * 检测软键盘是否显示
     */
    fun isSoftKeyboardShown(): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.isAcceptingText
    }

    /**
     * 检测是否是 F11 键盘
     *
     * 注意：这需要 Ratta SDK 支持，或者通过按键事件统计来推断
     */
    private fun isF11Keyboard(): Boolean {
        // TODO: 与 Ratta SDK 集成
        // 暂时返回 true，假设检测到的物理键盘就是 F11
        return true
    }

    /**
     * 获取最优分屏比例
     *
     * @return 编辑区占比 (0.0 - 1.0)
     */
    fun getOptimalSplitRatio(): Float {
        return when (detectKeyboardType()) {
            KeyboardType.F11_PHYSICAL -> F11_KEYBOARD_SPLIT_RATIO
            KeyboardType.SOFT_KEYBOARD -> SOFT_KEYBOARD_SPLIT_RATIO
            KeyboardType.NONE -> SOFT_KEYBOARD_SPLIT_RATIO
        }
    }

    /**
     * 获取键盘显示文本
     */
    fun getKeyboardLabelText(): String {
        return when (detectKeyboardType()) {
            KeyboardType.F11_PHYSICAL -> "F11"
            KeyboardType.SOFT_KEYBOARD -> "软键盘"
            KeyboardType.NONE -> ""
        }
    }

    /**
     * 是否应该显示键盘标识
     */
    fun shouldShowIndicator(): Boolean {
        return detectKeyboardType() != KeyboardType.NONE
    }
}
```

2. **在 MarkdownEditorActivity 中集成**

```kotlin
// MarkdownEditorActivity.kt

class MarkdownEditorActivity : Activity() {
    private lateinit var keyboardDetector: KeyboardDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        // ... 现有代码 ...

        // 初始化键盘检测器
        keyboardDetector = KeyboardDetector(this)

        // 更新键盘标识显示
        updateKeyboardIndicator()
    }

    private fun updateKeyboardIndicator() {
        if (keyboardDetector.shouldShowIndicator()) {
            keyboardIndicator.visibility = View.VISIBLE
            keyboardIndicator.text = keyboardDetector.getKeyboardLabelText()
        } else {
            keyboardIndicator.visibility = View.GONE
        }
    }

    private fun setOptimalSplitRatio() {
        val ratio = keyboardDetector.getOptimalSplitRatio()
        // 根据比例调整分屏布局
        val params = splitView.layoutParams as LinearLayout.LayoutParams
        params.weight = ratio / (1f - ratio)
        splitView.layoutParams = params
    }
}
```

#### 预计工时: 4 小时

---

### 步骤 2.2：文件操作细化

**文件**: `android/app/src/main/java/com/editor/nomadmark/FileOperationHelper.kt` (新建)

**实现目标**: 完善文件操作功能

#### 实现步骤

1. **创建 FileOperationHelper 类**

```kotlin
package com.editor.nomadmark

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.io.File

/**
 * 文件操作辅助类
 */
class FileOperationHelper(private val context: Context) {

    /**
     * 显示新建文件对话框
     */
    fun showNewFileDialog(onFileCreated: (String) -> Unit) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("新建文件")

        // 创建输入框
        val input = EditText(context)
        input.hint = "文件名（不含扩展名）"
        input.setSingleLine(true)

        val container = LinearLayout(context)
        container.setPadding(50, 40, 50, 0)
        container.addView(input)

        builder.setView(container)
        builder.setPositiveButton("创建") { _, _ ->
            val fileName = input.text.toString().trim()
            if (validateFileName(fileName)) {
                val fullPath = generateUniquePath(fileName)
                onFileCreated(fullPath)
            } else {
                Toast.makeText(context, "文件名无效", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("取消", null)
        builder.show()

        // 自动弹出软键盘
        input.requestFocus()
    }

    /**
     * 验证文件名
     */
    fun validateFileName(name: String): Boolean {
        if (name.isEmpty()) return false

        // 检查非法字符
        val illegalChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        if (name.any { it in illegalChars }) return false

        // 检查长度
        if (name.length > 255) return false

        // 检查是否以空格开头或结尾
        if (name.trim() != name) return false

        return true
    }

    /**
     * 生成唯一文件路径
     */
    fun generateUniquePath(baseName: String): String {
        val dir = context.filesDir
        var fileName = if (baseName.endsWith(".md")) baseName else "$baseName.md"
        var path = File(dir, fileName).absolutePath

        var index = 1
        while (File(path).exists()) {
            val nameWithoutExt = if (baseName.endsWith(".md")) {
                baseName.dropLast(3)
            } else {
                baseName
            }
            fileName = "${nameWithoutExt}_$index.md"
            path = File(dir, fileName).absolutePath
            index++
        }

        return path
    }

    /**
     * 检查文件是否已存在
     */
    fun fileExists(path: String): Boolean {
        return File(path).exists()
    }

    /**
     * 获取文件大小（可读格式）
     */
    fun getFormattedFileSize(file: File): String {
        val bytes = file.length()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * 显示保存确认对话框
     */
    fun showSaveConfirmDialog(
        fileName: String,
        onSave: () -> Unit,
        onDiscard: () -> Unit,
        onCancel: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("保存更改")
            .setMessage("文件 \"$fileName\" 有未保存的更改，是否保存？")
            .setPositiveButton("保存") { _, _ -> onSave() }
            .setNegativeButton("不保存") { _, _ -> onDiscard() }
            .setNeutralButton("取消") { _, _ -> onCancel() }
            .show()
    }
}
```

2. **在 MarkdownEditorActivity 中集成**

```kotlin
// MarkdownEditorActivity.kt

class MarkdownEditorActivity : Activity() {
    private lateinit var fileHelper: FileOperationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        // ...
        fileHelper = FileOperationHelper(this)
    }

    private fun createNewFile() {
        fileHelper.showNewFileDialog { path ->
            filePath = path
            fileName = File(path).nameWithoutExtension
            editorText.setText("# ${fileName}\n\n")
            splitEditorText.setText("# ${fileName}\n\n")
            lastSavedContent = editorText.text.toString()
            isModified = true
            updateSaveButton()
            updateFilenameDisplay()
        }
    }

    private fun showSaveBeforeExitDialog() {
        fileHelper.showSaveConfirmDialog(
            fileName ?: "未命名",
            onSave = {
                saveFile()
                finish()
            },
            onDiscard = {
                finish()
            },
            onCancel = {
                // 不做任何事
            }
        )
    }
}
```

#### 预计工时: 3 小时

---

### 步骤 2.3：提取 EinkRefreshController 为独立文件

**文件**: `android/app/src/main/java/com/editor/nomadmark/EinkRefreshController.kt` (新建)

**实现目标**: 将嵌套类提取为独立文件，便于维护

#### 实现步骤

将 `MarkdownEditorView.kt` 中的 `EinkRefreshController` 内部类提取到独立文件，并添加更多功能：

```kotlin
package com.editor.nomadmark

import android.graphics.Rect

/**
 * 刷新模式
 */
enum class RefreshMode {
    /** 全局刷新 */
    GLOBAL,
    /** 局部刷新 */
    PARTIAL,
    /** 智能刷新（自动选择） */
    SMART
}

/**
 * E-ink 刷新控制器
 *
 * 功能：
 * - 计算局部刷新区域
 * - 决定刷新模式
 * - 管理刷新策略
 */
class EinkRefreshController(private val view: MarkdownEditorView) {

    companion object {
        /** 局部刷新阈值（像素）。小于此值使用局部刷新 */
        const val PARTIAL_REFRESH_THRESHOLD = 10000

        /** 全局刷新间隔（毫秒）。定期全局刷新防止残影 */
        const val GLOBAL_REFRESH_INTERVAL = 30000L

        /** 手写操作后的延迟刷新时间 */
        const val HANDWRITING_REFRESH_DELAY = 500L
    }

    /** 当前刷新模式 */
    private var currentMode = RefreshMode.SMART

    /** 上次全局刷新时间 */
    private var lastGlobalRefreshTime = 0L

    /** 待刷新区域列表 */
    private val dirtyRects = mutableListOf<Rect>()

    /**
     * 请求刷新区域
     */
    fun requestDirtyRect(rect: Rect) {
        dirtyRects.add(rect)
        applyRefresh()
    }

    /**
     * 请求刷新多个区域
     */
    fun requestDirtyRects(rects: List<Rect>) {
        dirtyRects.addAll(rects)
        applyRefresh()
    }

    /**
     * 应用刷新
     */
    private fun applyRefresh() {
        if (dirtyRects.isEmpty()) return

        val mode = decideRefreshMode()
        when (mode) {
            RefreshMode.GLOBAL -> doGlobalRefresh()
            RefreshMode.PARTIAL -> doPartialRefresh()
            RefreshMode.SMART -> doSmartRefresh()
        }

        dirtyRects.clear()
    }

    /**
     * 决定刷新模式
     */
    private fun decideRefreshMode(): RefreshMode {
        when (currentMode) {
            RefreshMode.GLOBAL -> RefreshMode.GLOBAL
            RefreshMode.PARTIAL -> RefreshMode.PARTIAL
            RefreshMode.SMART -> {
                // 计算总刷新面积
                val totalArea = dirtyRects.sumOf { rect ->
                    rect.width() * rect.height()
                }

                if (totalArea < PARTIAL_REFRESH_THRESHOLD) {
                    RefreshMode.PARTIAL
                } else {
                    RefreshMode.GLOBAL
                }
            }
        }
    }

    /**
     * 全局刷新
     */
    private fun doGlobalRefresh() {
        view.invalidate()
        lastGlobalRefreshTime = System.currentTimeMillis()
    }

    /**
     * 局部刷新
     */
    private fun doPartialRefresh() {
        dirtyRects.forEach { rect ->
            view.invalidate(rect)
        }
    }

    /**
     * 智能刷新
     */
    private fun doSmartRefresh() {
        // 检查是否需要定期全局刷新
        val now = System.currentTimeMillis()
        if (now - lastGlobalRefreshTime > GLOBAL_REFRESH_INTERVAL) {
            doGlobalRefresh()
        } else {
            doPartialRefresh()
        }
    }

    /**
     * 设置刷新模式
     */
    fun setRefreshMode(mode: RefreshMode) {
        currentMode = mode
    }

    /**
     * 手写操作后的刷新
     */
    fun refreshAfterHandwriting(rect: Rect) {
        view.handler?.postDelayed({
            requestDirtyRect(rect)
        }, HANDWRITING_REFRESH_DELAY)
    }

    /**
     * 立即全局刷新
     */
    fun forceGlobalRefresh() {
        dirtyRects.clear()
        doGlobalRefresh()
    }
}
```

#### 预计工时: 2 小时

---

### 步骤 2.4：创建滚动同步管理器

**文件**: `android/app/src/main/java/com/editor/nomadmark/ScrollSyncManager.kt` (新建)

**实现目标**: 实现分屏模式下的编辑区和预览区滚动同步

#### 实现步骤

```kotlin
package com.editor.nomadmark

import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView

/**
 * 滚动同步管理器
 *
 * 功能：
 * - 编辑区滚动时同步预览区
 * - 预览区滚动时同步编辑区
 * - 计算滚动位置对应关系
 */
class ScrollSyncManager(
    private val editorScrollView: ScrollView,
    private val editorText: EditText,
    private val previewScrollView: ScrollView,
    private val previewText: TextView
) {

    private var isSyncing = false
    private var syncEnabled = true

    /**
     * 启用滚动同步
     */
    fun enable() {
        syncEnabled = true
        setupListeners()
    }

    /**
     * 禁用滚动同步
     */
    fun disable() {
        syncEnabled = false
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        editorScrollView.viewTreeObserver?.addOnScrollChangedListener {
            if (syncEnabled && !isSyncing) {
                isSyncing = true
                syncEditorToPreview()
                isSyncing = false
            }
        }

        previewScrollView.viewTreeObserver?.addOnScrollChangedListener {
            if (syncEnabled && !isSyncing) {
                isSyncing = true
                syncPreviewToEditor()
                isSyncing = false
            }
        }
    }

    /**
     * 编辑区滚动同步到预览区
     */
    private fun syncEditorToPreview() {
        val editorScrollY = editorScrollView.scrollY
        val previewScrollY = calculatePreviewPosition(editorScrollY)
        previewScrollView.smoothScrollTo(0, previewScrollY)
    }

    /**
     * 预览区滚动同步到编辑区
     */
    private fun syncPreviewToEditor() {
        val previewScrollY = previewScrollView.scrollY
        val editorScrollY = calculateEditorPosition(previewScrollY)
        editorScrollView.smoothScrollTo(0, editorScrollY)
    }

    /**
     * 计算编辑区滚动位置对应的预览区位置
     */
    private fun calculatePreviewPosition(editorScrollY: Int): Int {
        // 获取编辑区第一行
        val lineHeight = editorText.lineHeight
        val firstVisibleLine = editorScrollY / lineHeight

        // 获取编辑区行数对应的预览区位置
        // 简化处理：按比例计算
        val editorTotalHeight = editorText.lineCount * lineHeight
        val previewTotalHeight = previewText.height

        if (editorTotalHeight > 0) {
            return (editorScrollY.toFloat() / editorTotalHeight * previewTotalHeight).toInt()
        }

        return editorScrollY
    }

    /**
     * 计算预览区滚动位置对应的编辑区位置
     */
    private fun calculateEditorPosition(previewScrollY: Int): Int {
        // 简化处理：按比例计算
        val editorTotalHeight = editorText.lineCount * editorText.lineHeight
        val previewTotalHeight = previewText.height

        if (previewTotalHeight > 0) {
            return (previewScrollY.toFloat() / previewTotalHeight * editorTotalHeight).toInt()
        }

        return previewScrollY
    }

    /**
     * 滚动到指定行
     */
    fun scrollToLine(lineNumber: Int) {
        val lineHeight = editorText.lineHeight
        val scrollY = lineNumber * lineHeight
        editorScrollView.smoothScrollTo(0, scrollY)
    }
}
```

#### 预计工时: 3 小时

---

### 阶段二总结

完成本阶段后，Android 平台将具备：

- ✅ 键盘类型检测和分屏比例自动调整
- ✅ 完善的文件操作（新建、验证、保存确认）
- ✅ 独立的 E-ink 刷新控制器
- ✅ 分屏滚动同步功能

**预计总工时: 12 小时 (1.5 工作日)**

---

## 阶段三：Core 层高级功能

> **目标**: 完成 Core 层的 FFI 接口和集成
> **预计工时**: 14 小时
> **依赖**: 阶段一

---

### 步骤 3.1：实现搜索 FFI 接口

**文件**: `core/src/lib.rs`

**当前状态**: `md_document_search()` 已声明但返回空结果

#### 实现步骤

```rust
// 在 lib.rs 中修改 md_document_search()

#[no_mangle]
pub extern "C" fn md_document_search(
    handle: *mut MarkdownDocument,
    query: *const c_char,
    query_len: usize,
    out_results: *mut *const bridge::types::SearchResult,
    out_count: *mut usize,
) -> i32 {
    if handle.is_null() || query.is_null() || out_results.is_null() || out_count.is_null() {
        return -1;
    }

    unsafe {
        let doc = &*handle;
        let query_str = match str::from_utf8(slice::from_raw_parts(query as *const u8, query_len)) {
            Ok(s) => s,
            Err(_) => return -1,
        };

        // 获取文档内容
        let parser = doc.parser();
        let content = parser.get_content(); // 需要在 StreamingParser 中添加此方法

        // 使用 Searcher 搜索
        let searcher = search::Searcher::new(content);
        let options = search::SearchOptions::default();
        let results = searcher.search(query_str, &options);

        if results.is_empty() {
            *out_results = std::ptr::null();
            *out_count = 0;
            return 0;
        }

        // 转换为 FFI 结果
        let ffi_results: Vec<bridge::types::SearchResult> = results
            .into_iter()
            .map(|r| bridge::types::SearchResult {
                start: r.start,
                end: r.end,
                line_number: r.line_number,
            })
            .collect();

        // 返回结果
        let results_box = ffi_results.into_boxed_slice();
        *out_results = Box::leak(results_box).as_ptr();
        *out_count = ffi_results.len();

        0
    }
}
```

#### 预计工时: 2 小时

---

### 步骤 3.2：实现撤销/重做 FFI 接口

**文件**: `core/src/lib.rs`

**当前状态**: `md_document_undo()` 和 `md_document_redo()` 已声明但返回 0

#### 实现步骤

1. **在 MarkdownDocument 中添加历史记录**

```rust
// 在 lib.rs 中修改 MarkdownDocument 结构

pub struct MarkdownDocument {
    parser: Option<StreamingParser>,
    cached_render: Option<RenderResult>,
    last_viewport: Option<(f32, f32)>,
    rendered_blocks: Vec<CachedBlock>,
    cached_memory: usize,
    history: History,  // 添加历史记录
}

impl MarkdownDocument {
    fn from_streaming(parser: StreamingParser) -> Self {
        Self {
            parser: Some(parser),
            cached_render: None,
            last_viewport: None,
            rendered_blocks: Vec::new(),
            cached_memory: 0,
            history: History::new(),  // 初始化历史记录
        }
    }

    fn history(&mut self) -> &mut History {
        &mut self.history
    }
}
```

2. **实现撤销/重做 FFI**

```rust
#[no_mangle]
pub extern "C" fn md_document_undo(
    handle: *mut MarkdownDocument,
) -> i32 {
    if handle.is_null() {
        return -1;
    }

    unsafe {
        let doc = &mut *handle;
        if let Some(_command) = doc.history().undo() {
            // TODO: 应用撤销操作到文档内容
            1
        } else {
            0
        }
    }
}

#[no_mangle]
pub extern "C" fn md_document_redo(
    handle: *mut MarkdownDocument,
) -> i32 {
    if handle.is_null() {
        return -1;
    }

    unsafe {
        let doc = &mut *handle;
        if let Some(_command) = doc.history().redo() {
            // TODO: 应用重做操作到文档内容
            1
        } else {
            0
        }
    }
}

// 添加释放搜索结果的函数
#[no_mangle]
pub extern "C" fn md_free_search_results(
    ptr: *mut bridge::types::SearchResult,
    len: usize,
) {
    if ptr.is_null() || len == 0 {
        return;
    }

    unsafe {
        let _ = Box::from_raw(std::slice::from_raw_parts_mut(ptr, len));
    }
}
```

#### 预计工时: 4 小时

---

### 步骤 3.3：实现 Core::save()

**文件**: `core/src/lib.rs`

#### 实现步骤

```rust
// 在 Core impl 中添加

impl Core {
    // ... 现有代码 ...

    /// 保存文档
    pub fn save(&self, id: u64) -> Result<(), String> {
        let doc = self.get_document(id).ok_or("Document not found")?;
        let parser = doc.parser();

        // 获取文档内容
        let content = parser.get_content();

        // 获取文件路径（需要在 StreamingParser 中保存原始路径）
        let path = parser.path();

        // 写入文件
        std::fs::write(path, content).map_err(|e| e.to_string())?;

        Ok(())
    }

    /// 获取文档内容
    pub fn get_content(&self, id: u64) -> Result<String, String> {
        let doc = self.get_document(id).ok_or("Document not found")?;
        Ok(doc.parser().get_content())
    }
}
```

#### 预计工时: 2 小时

---

### 步骤 3.4：完善 StreamingParser

**文件**: `core/src/parser/streaming.rs`

#### 需要添加的方法

```rust
impl StreamingParser {
    // ... 现有代码 ...

    /// 获取文档完整内容
    pub fn get_content(&self) -> String {
        // 从 mmap 或内部缓冲区获取内容
        // 实现...
        String::new()
    }

    /// 获取原始文件路径
    pub fn path(&self) -> &Path {
        // 返回原始文件路径
        // 实现...
        &Path::new("")
    }

    /// 设置内容（用于编辑后更新）
    pub fn set_content(&mut self, content: String) {
        // 更新内部内容
        // 实现...
    }
}
```

#### 预计工时: 4 小时

---

### 步骤 3.5：添加单元测试

**文件**: `core/src/lib.rs` 和各模块

#### 实现步骤

为每个新模块添加完整的单元测试：

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_document_create_and_release() {
        let content = "# Test\n\nHello world";
        let ptr = md_document_create(
            content.as_ptr() as *const c_char,
            content.len(),
        );
        assert!(!ptr.is_null());
        md_document_release(ptr);
    }

    #[test]
    fn test_search_functionality() {
        let content = "Hello world, hello universe";
        let ptr = md_document_create(
            content.as_ptr() as *const c_char,
            content.len(),
        );
        assert!(!ptr.is_null());

        let mut results_ptr: *const SearchResult = std::ptr::null();
        let mut count: usize = 0;

        let result = md_document_search(
            ptr,
            b"hello".as_ptr() as *const c_char,
            5,
            &mut results_ptr,
            &mut count,
        );

        assert_eq!(result, 0);
        assert_eq!(count, 2);

        // 清理
        if !results_ptr.is_null() {
            md_free_search_results(results_ptr as *mut SearchResult, count);
        }
        md_document_release(ptr);
    }
}
```

#### 预计工时: 2 小时

---

### 阶段三总结

完成本阶段后，Core 层将具备完整的 FFI 接口：

- ✅ 搜索 FFI 接口
- ✅ 撤销/重做 FFI 接口
- ✅ 文档保存功能
- ✅ 完整的单元测试

**预计总工时: 14 小时 (1.5-2 工作日)**

---

## 阶段四：Android 平台集成

> **目标**: 将 Android 平台与 Core 层集成
> **预计工时**: 12 小时
> **依赖**: 阶段三

---

### 步骤 4.1：集成 Core 搜索功能

**文件**: `android/app/src/main/java/com/editor/nomadmark/MarkdownCore.kt`

#### 实现步骤

1. **添加搜索方法**

```kotlin
// MarkdownCore.kt

class MarkdownCore {
    // ... 现有代码 ...

    companion object {
        // ... 现有代码 ...

        // 加载搜索结果
        private external fun nativeSearchResultsFree(ptr: Long, count: Int)

        /**
         * 搜索文档
         */
        fun search(documentHandle: Long, query: String): List<SearchResult> {
            val results = mutableListOf<SearchResult>()
            val resultsPtr = LongArray(1) { 0 }
            val count = IntArray(1) { 0 }

            val result = nativeDocumentSearch(
                documentHandle,
                query,
                query.length,
                resultsPtr,
                count
            )

            if (result == 0 && count[0] > 0) {
                val ptr = resultsPtr[0]
                for (i in 0 until count[0]) {
                    val result = readSearchResult(ptr, i)
                    results.add(result)
                }
                nativeSearchResultsFree(ptr, count[0])
            }

            return results
        }

        private external fun nativeDocumentSearch(
            documentHandle: Long,
            query: String,
            queryLen: Int,
            outResults: LongArray,
            outCount: IntArray
        ): Int

        private fun readSearchResult(ptr: Long, index: Int): SearchResult {
            // 从 native 内存读取结果
            // 实现...
            SearchResult(0, 0, 0)
        }
    }
}

data class SearchResult(
    val start: Int,
    val end: Int,
    val lineNumber: Int
)
```

2. **在 JNI 层实现**

```rust
// core/src/bridge/jni.rs

#[no_mangle]
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeDocumentSearch(
    env: JNIEnv,
    _class: JClass,
    document_handle: jlong,
    query: JString,
    query_len: jint,
    out_results: JLongArray,
    out_count: jintArray,
) -> jint {
    // 实现...
    0
}
```

#### 预计工时: 4 小时

---

### 步骤 4.2：集成 Core 撤销/重做

**文件**: `android/app/src/main/java/com/editor/nomadmark/MarkdownCore.kt`

#### 实现步骤

```kotlin
// MarkdownCore.kt

class MarkdownCore {
    companion object {
        private external fun nativeDocumentUndo(documentHandle: Long): Int
        private external fun nativeDocumentRedo(documentHandle: Long): Int

        fun undo(documentHandle: Long): Boolean {
            return nativeDocumentUndo(documentHandle) == 1
        }

        fun redo(documentHandle: Long): Boolean {
            return nativeDocumentRedo(documentHandle) == 1
        }
    }
}
```

在 `MarkdownEditorActivity.kt` 中使用：

```kotlin
// MarkdownEditorActivity.kt

class MarkdownEditorActivity : Activity() {
    private var documentHandle: Long = 0

    private fun undo() {
        val success = MarkdownCore.undo(documentHandle)
        if (success) {
            // 刷新显示
            refreshDocument()
        } else {
            Toast.makeText(this, "没有可撤销的操作", Toast.LENGTH_SHORT).show()
        }
    }

    private fun redo() {
        val success = MarkdownCore.redo(documentHandle)
        if (success) {
            refreshDocument()
        } else {
            Toast.makeText(this, "没有可重做的操作", Toast.LENGTH_SHORT).show()
        }
    }
}
```

#### 预计工时: 3 小时

---

### 步骤 4.3：实现双指缩放

**文件**: `android/app/src/main/java/com/editor/nomadmark/MarkdownEditorView.kt`

#### 实现步骤

```kotlin
// MarkdownEditorView.kt

class MarkdownEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), ScaleGestureDetector.OnScaleGestureListener {

    private var scaleGestureDetector: ScaleGestureDetector
    private var currentScale = 1.0f
    private var minScale = 0.5f
    private var maxScale = 3.0f

    init {
        scaleGestureDetector = ScaleGestureDetector(context, this)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val scaleFactor = detector.scaleFactor
        currentScale *= scaleFactor

        // 限制缩放范围
        currentScale = currentScale.coerceIn(minScale, maxScale)

        // 应用缩放
        scaleX = currentScale
        scaleY = currentScale

        invalidate()
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        // 缩放结束，可以触发刷新
    }

    fun resetZoom() {
        currentScale = 1.0f
        scaleX = 1.0f
        scaleY = 1.0f
        invalidate()
    }
}
```

#### 预计工时: 3 小时

---

### 步骤 4.4：实现惯性滚动

**文件**: `android/app/src/main/java/com/editor/nomadmark/MarkdownEditorView.kt`

#### 实现步骤

使用 `OverScroller` 实现惯性滚动：

```kotlin
// MarkdownEditorView.kt

class MarkdownEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var scroller: OverScroller
    private var velocityTracker: VelocityTracker? = null

    init {
        scroller = OverScroller(context)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.clear()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                scroller.forceFinished(true)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)

                val velocityY = velocityTracker?.yVelocity?.toInt() ?: 0
                scroller.fling(
                    scrollX, scrollY,
                    0, velocityY,
                    0, 0,
                    Int.MIN_VALUE, Int.MAX_VALUE
                )
                postInvalidateOnAnimation()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
            postInvalidateOnAnimation()
        }
    }
}
```

#### 预计工时: 2 小时

---

### 阶段四总结

完成本阶段后，Android 平台将完全集成 Core 层：

- ✅ 使用 Core 层搜索功能
- ✅ 使用 Core 层撤销/重做
- ✅ 双指缩放支持
- ✅ 惯性滚动支持

**预计总工时: 12 小时 (1.5 工作日)**

---

## 阶段五：细化与优化

> **目标**: 完成可选功能优化
> **预计工时**: 14 小时
> **依赖**: 阶段四

---

### 步骤 5.1：快捷工具栏动画

**文件**: `android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt`

**预计工时**: 2 小时

---

### 步骤 5.2：修订模式细化

**文件**: `android/app/src/main/java/com/editor/nomadmark/GestureEditor.kt`

**预计工时**: 8 小时

---

### 步骤 5.3：E-ink 刷新策略优化

**文件**: `android/app/src/main/java/com/editor/nomadmark/EinkRefreshController.kt`

**预计工时**: 4 小时

---

## 验证清单

完成所有阶段后，使用以下清单验证：

### Core 层验证

- [ ] md_document_create() 可以从内存创建文档
- [ ] 搜索功能返回正确结果
- [ ] 替换功能正常工作
- [ ] 撤销/重做功能正常
- [ ] 快捷插入 API 生成正确格式
- [ ] 所有单元测试通过

### Android 平台验证

- [ ] 键盘检测正确识别 F11 键盘
- [ ] 新建文件对话框正常工作
- [ ] 分屏滚动同步流畅
- [ ] 双指缩放响应正常
- [ ] E-ink 刷新无残影
- [ ] 与 Core 层集成成功

---

## 附录

### 文件清单

新建文件列表：

```
core/src/
├── insert.rs
├── history/mod.rs
├── search/mod.rs
└── replace/mod.rs

android/app/src/main/java/com/editor/nomadmark/
├── KeyboardDetector.kt
├── FileOperationHelper.kt
├── EinkRefreshController.kt
└── ScrollSyncManager.kt
```

修改文件列表：

```
core/src/lib.rs
core/src/parser/streaming.rs
core/Cargo.toml
android/app/src/main/java/com/editor/nomadmark/MarkdownCore.kt
android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt
android/app/src/main/java/com/editor/nomadmark/MarkdownEditorView.kt
```

---

*文档生成时间: 2026-07-02*
*预计完成时间: 约 12 工作日*
