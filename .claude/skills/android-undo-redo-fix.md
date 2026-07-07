# Android 撤销重做功能问题修复记录

## 问题现象

撤销重做功能无法正常使用，点击按钮始终提示"没有可撤销的操作"。

---

## 问题 1：undoStack 从未被填充

**现象**：`undoStack` 定义后始终为空，导致撤销功能无法使用。

**根因**：
- `undoStack` 和 `redoStack` 定义在 `MarkdownEditorActivity.kt` 第 189-190 行
- 整个代码中没有任何地方向 `undoStack.add()` 添加内容
- 只在 `performLocalUndo()` 和 `performLocalRedo()` 中互相添加，但此时栈已经为空

**代码位置**：`MarkdownEditorActivity.kt`

**修复方法**：
1. ✅ 在 `loadFile()` 完成后，保存初始状态到 `undoStack`
2. ✅ 在 `createNewFile()` 完成后，保存初始状态到 `undoStack`
3. ✅ 在 `textWatcher.afterTextChanged()` 中保存变化前的状态到 `undoStack`
4. ✅ 添加 `isUndoingOrRedoing` 标志防止撤销/重做时重复保存

**修复状态**：✅ 已完成

---

## 问题 2：textWatcher 缺少撤销状态保存

**现象**：文本变化监听器只标记修改状态，不保存撤销历史。

**根因**：
- `textWatcher.onTextChanged()` 只调用了 `markAsModified()` 和 `updatePreview()`
- 没有在文本变化前保存当前状态到 `undoStack`

**代码位置**：`MarkdownEditorActivity.kt` 第 1571-1580 行

**修复方法**：
- ✅ 在 `beforeTextChanged()` 中保存变化前的内容
- ✅ 在 `afterTextChanged()` 中将变化前的状态保存到 `undoStack`
- ✅ 添加 `isUndoingOrRedoing` 标志防止撤销/重做触发保存

**修复状态**：✅ 已完成

---

## 问题 3：初始状态未保存

**现象**：加载文件后没有保存初始状态，无法撤销到加载时的状态。

**根因**：
- `loadFile()` 函数加载文件内容后，没有保存到 `undoStack`

**代码位置**：`MarkdownEditorActivity.kt` 第 502-520 行

**修复方法**：
- ✅ 在 `loadFile()` 成功后，清空栈并保存初始内容到 `undoStack`
- ✅ 在 `createNewFile()` 成功后，清空栈并保存初始内容到 `undoStack`

**修复状态**：✅ 已完成

---

## 验证方法

1. 打开一个 Markdown 文件
2. 输入一些文本
3. 点击撤销按钮，应该能撤销到上一步
4. 点击重做按钮，应该能重做被撤销的操作
5. 多次编辑和撤销/重做，验证功能正常
