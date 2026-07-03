// =============================================================================
// 历史记录模块 - 撤销/重做功能
// =============================================================================
//
// 实现基于命令模式的撤销/重做栈。
//
// 功能:
// - EditCommand: 编辑操作命令（插入、删除、替换）
// - History: 历史记录栈管理
// - 支持撤销/重做操作
// - 支持保存点检测（判断是否有未保存的修改）
// =============================================================================

use std::collections::VecDeque;

/// 最大历史记录数量
const MAX_HISTORY: usize = 100;

// =============================================================================
// 编辑命令类型
// =============================================================================

/// 编辑操作命令
///
/// 每个命令代表一个可撤销的编辑操作。
#[derive(Clone, Debug)]
pub enum EditCommand {
    /// 插入文本
    Insert {
        /// 插入位置
        position: usize,
        /// 插入的文本
        text: String,
    },
    /// 删除文本
    Delete {
        /// 删除的起始位置
        position: usize,
        /// 被删除的文本（用于恢复）
        text: String,
    },
    /// 替换文本
    Replace {
        /// 替换的起始位置
        position: usize,
        /// 被替换的旧文本
        old_text: String,
        /// 替换后的新文本
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
        EditCommand::Replace {
            position,
            old_text,
            new_text,
        }
    }

    /// 获取反向命令（用于撤销）
    ///
    /// 撤销操作就是执行当前命令的反向命令。
    pub fn inverse(&self) -> EditCommand {
        match self {
            EditCommand::Insert { position, text } => EditCommand::Delete {
                position: *position,
                text: text.clone(),
            },
            EditCommand::Delete { position, text } => EditCommand::Insert {
                position: *position,
                text: text.clone(),
            },
            EditCommand::Replace {
                position,
                old_text,
                new_text,
            } => EditCommand::Replace {
                position: *position,
                old_text: new_text.clone(),
                new_text: old_text.clone(),
            },
        }
    }

    /// 获取命令影响的文本长度
    pub fn length(&self) -> usize {
        match self {
            EditCommand::Insert { text, .. } => text.len(),
            EditCommand::Delete { text, .. } => text.len(),
            EditCommand::Replace { new_text, .. } => new_text.len(),
        }
    }

    /// 获取命令的起始位置
    pub fn position(&self) -> usize {
        match self {
            EditCommand::Insert { position, .. } => *position,
            EditCommand::Delete { position, .. } => *position,
            EditCommand::Replace { position, .. } => *position,
        }
    }
}

// =============================================================================
// 历史记录栈
// =============================================================================

/// 历史记录栈
///
/// 管理撤销/重做操作，支持：
/// - 添加新操作
/// - 撤销上一个操作
/// - 重做已撤销的操作
/// - 保存点管理（检测未保存的修改）
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
            saved_index: Some(0),
        }
    }

    /// 添加一个操作到历史记录
    ///
    /// 有新操作时，清空重做栈（因为新操作会使重做失效）。
    pub fn push(&mut self, command: EditCommand) {
        // 清空重做栈
        self.redo_stack.clear();

        // 如果撤销栈已满，移除最旧的记录
        if self.undo_stack.len() >= MAX_HISTORY {
            self.undo_stack.pop_front();
            // 调整保存点索引
            if let Some(idx) = self.saved_index {
                if idx > 0 {
                    self.saved_index = Some(idx - 1);
                }
            }
        }

        self.undo_stack.push_back(command);
    }

    /// 撤销上一个操作
    ///
    /// # 返回
    /// 如果成功撤销，返回被撤销的命令；如果没有可撤销的操作，返回 None。
    pub fn undo(&mut self) -> Option<&EditCommand> {
        if let Some(command) = self.undo_stack.pop_back() {
            // 将反向命令加入重做栈
            self.redo_stack.push_back(command.inverse());
            self.redo_stack.back()
        } else {
            None
        }
    }

    /// 重做上一个撤销的操作
    ///
    /// # 返回
    /// 如果成功重做，返回被重做的命令；如果没有可重做的操作，返回 None。
    pub fn redo(&mut self) -> Option<&EditCommand> {
        if let Some(command) = self.redo_stack.pop_back() {
            // 将反向命令加入撤销栈
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

    /// 获取撤销栈大小
    pub fn undo_count(&self) -> usize {
        self.undo_stack.len()
    }

    /// 获取重做栈大小
    pub fn redo_count(&self) -> usize {
        self.redo_stack.len()
    }

    /// 设置保存点
    ///
    /// 保存点表示文档已保存的状态，用于检测是否有未保存的修改。
    pub fn set_saved(&mut self) {
        self.saved_index = Some(self.undo_stack.len());
    }

    /// 检查是否有未保存的修改
    ///
    /// 如果当前撤销栈的大小与保存点索引不同，说明有未保存的修改。
    pub fn is_modified(&self) -> bool {
        self.saved_index != Some(self.undo_stack.len())
    }

    /// 清空历史记录
    pub fn clear(&mut self) {
        self.undo_stack.clear();
        self.redo_stack.clear();
        self.saved_index = Some(0);
    }

    /// 获取最后一个操作（不修改栈）
    pub fn last_command(&self) -> Option<&EditCommand> {
        self.undo_stack.back()
    }

    /// 合并连续的相同类型操作
    ///
    /// 用于合并连续的字符输入，避免撤销时一个字符一个字符地撤销。
    pub fn try_merge_last(&mut self, new_command: EditCommand) -> bool {
        let last_command = self.undo_stack.back_mut();

        if let Some(cmd) = last_command {
            // 只合并连续的插入操作，且位置连续
            match (cmd, &new_command) {
                (
                    EditCommand::Insert {
                        position: pos1,
                        text: text1,
                    },
                    EditCommand::Insert {
                        position: pos2,
                        text: text2,
                    },
                ) => {
                    // 检查位置是否连续（新操作的开始位置 = 旧操作的结束位置）
                    if *pos2 == *pos1 + text1.len() {
                        // 合并文本
                        *text1 = format!("{}{}", text1, text2);
                        return true;
                    }
                }
                _ => {}
            }
        }

        false
    }
}

impl Default for History {
    fn default() -> Self {
        Self::new()
    }
}

// =============================================================================
// 单元测试
// =============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_insert_and_undo() {
        let mut history = History::new();
        let cmd = EditCommand::insert(0, "你好".to_string());

        history.push(cmd.clone());
        assert!(history.can_undo());
        assert_eq!(history.undo_count(), 1);

        history.undo();
        assert!(!history.can_undo());
        assert!(history.can_redo());
        assert_eq!(history.redo_count(), 1);
    }

    #[test]
    fn test_undo_redo_cycle() {
        let mut history = History::new();

        history.push(EditCommand::insert(0, "A".to_string()));
        history.push(EditCommand::insert(1, "B".to_string()));
        history.push(EditCommand::insert(2, "C".to_string()));

        assert_eq!(history.undo_count(), 3);

        // 撤销一次
        history.undo();
        assert_eq!(history.undo_count(), 2);
        assert_eq!(history.redo_count(), 1);

        // 重做
        history.redo();
        assert_eq!(history.undo_count(), 3);
        assert_eq!(history.redo_count(), 0);
    }

    #[test]
    fn test_modified_flag() {
        let mut history = History::new();
        history.set_saved();
        assert!(!history.is_modified());

        history.push(EditCommand::insert(0, "文本".to_string()));
        assert!(history.is_modified());

        history.set_saved();
        assert!(!history.is_modified());

        history.undo();
        assert!(history.is_modified());
    }

    #[test]
    fn test_max_history_limit() {
        let mut history = History::new();

        // 添加超过最大限制的操作
        for i in 0..=MAX_HISTORY {
            history.push(EditCommand::insert(i, "x".to_string()));
        }

        // 验证栈大小不超过限制
        assert_eq!(history.undo_count(), MAX_HISTORY);
    }

    #[test]
    fn test_new_operation_clears_redo() {
        let mut history = History::new();

        history.push(EditCommand::insert(0, "A".to_string()));
        history.undo();
        assert_eq!(history.redo_count(), 1);

        // 新操作会清空重做栈
        history.push(EditCommand::insert(0, "B".to_string()));
        assert_eq!(history.redo_count(), 0);
    }

    #[test]
    fn test_command_inverse() {
        let insert_cmd = EditCommand::insert(5, "文本".to_string());
        let inverse = insert_cmd.inverse();
        assert!(matches!(inverse, EditCommand::Delete { .. }));

        let delete_cmd = EditCommand::delete(5, "文本".to_string());
        let inverse = delete_cmd.inverse();
        assert!(matches!(inverse, EditCommand::Insert { .. }));

        let replace_cmd = EditCommand::replace(5, "旧".to_string(), "新".to_string());
        let inverse = replace_cmd.inverse();
        match inverse {
            EditCommand::Replace { old_text, new_text, .. } => {
                assert_eq!(old_text, "新");
                assert_eq!(new_text, "旧");
            }
            _ => panic!("反向命令应该是 Replace"),
        }
    }

    #[test]
    fn test_merge_inserts() {
        let mut history = History::new();

        history.push(EditCommand::insert(0, "A".to_string()));

        // 尝试合并连续的插入
        let merged = history.try_merge_last(EditCommand::insert(1, "B".to_string()));
        assert!(merged);

        // 验证合并后只有一个命令
        assert_eq!(history.undo_count(), 1);

        // 验证合并后的内容
        if let Some(EditCommand::Insert { text, .. }) = history.last_command() {
            assert_eq!(text, "AB");
        } else {
            panic!("应该是 Insert 命令");
        }
    }

    #[test]
    fn test_clear() {
        let mut history = History::new();

        history.push(EditCommand::insert(0, "A".to_string()));
        history.undo();
        assert!(history.can_redo());

        history.clear();
        assert!(!history.can_undo());
        assert!(!history.can_redo());
        assert!(!history.is_modified()); // clear 后重置为未修改状态
    }
}
