# 恢复对话框逻辑修复

## 问题描述

**修复前**：退出应用再打开会同时弹出恢复会话弹窗和新建文件弹窗

**期望效果**：
1. 先弹出恢复对话框
2. 用户选择"恢复" → 进入恢复的文件
3. 用户选择"放弃" → 弹出新建文件对话框
4. 用户选择"稍后" → 弹出新建文件对话框

---

## 修复内容

### 修改位置：`MarkdownEditorActivity.onCreate()`

**修复前**：
```kotlin
// 检查是否有可恢复的自动保存内容
val recoverableSessions = AutoSaveManager.scanRecoverableSessions(this)
if (recoverableSessions.isNotEmpty()) {
    showRecoveryDialog(recoverableSessions.first())
    // 注意：恢复后会继续启动流程，所以这里不需要 return
}
// 继续执行 handleOpenIntent，导致新建文件对话框也弹出
handleOpenIntent(intent)
```

**修复后**：
```kotlin
// 检查是否有可恢复的自动保存内容
val recoverableSessions = AutoSaveManager.scanRecoverableSessions(this)
if (recoverableSessions.isNotEmpty()) {
    showRecoveryDialog(recoverableSessions.first())
    return // 停止继续执行，等待用户选择
}
// 没有可恢复内容时才执行
handleOpenIntent(intent)
```

### 恢复对话框回调逻辑

```kotlin
.setNegativeButton("放弃") { _, _ ->
    session.delete()
    AutoSaveManager.clearAutosaveFlag(this)
    // 用户放弃恢复，继续正常启动流程（会触发新建文件对话框）
    handleOpenIntent(intent)
}
.setNeutralButton("稍后") { _, _ ->
    // 保留临时文件，继续正常启动流程（会触发新建文件对话框）
    handleOpenIntent(intent)
}
```

---

## 验证方法

### 手动测试步骤

1. **创建测试内容**：
   ```
   启动应用 → 点击"新建" → 输入文件名 → 输入内容 → 等待 8 秒
   ```

2. **模拟崩溃**：
   ```bash
   adb shell am force-stop com.editor.nomadmark
   ```

3. **重新启动**：
   ```bash
   adb shell am start -n com.editor.nomadmark/.MainActivity
   ```

4. **观察结果**：
   - ✅ **只显示恢复对话框**
   - ✅ **不显示新建文件对话框**

5. **测试各选项**：
   - 点击"恢复" → 内容恢复，进入编辑状态
   - 点击"放弃" → 临时文件删除，弹出新建文件对话框 ✅
   - 点击"稍后" → 临时文件保留，弹出新建文件对话框 ✅

---

## 流程图

```
应用启动 (onCreate)
    │
    ├─→ 清理过期文件
    │
    ├─→ 扫描可恢复会话
    │        │
    │        ├─ 有可恢复会话?
    │        │    │
    │        │    ├─ YES → 显示恢复对话框 ──→ 等待用户选择
    │        │    │         │                         │
    │        │    │         │              ┌─────────┼─────────┐
    │        │    │         │              ▼         ▼         ▼
    │        │    │         │           [恢复]    [放弃]    [稍后]
    │        │    │         │              │         │         │
    │        │    │         │         恢复内容   删除会话   保留会话
    │        │    │         │              │         │         │
    │        │    │         │         进入编辑   新建文件   新建文件
    │        │    │         │
    │        │    └─ NO → handleOpenIntent → 新建文件
    │        │
    └─→ 检测键盘状态
```

---

## 状态

✅ 修复已完成
✅ APK 已重新编译并安装
✅ 等待手动验证

---

## 相关文件

- [MarkdownEditorActivity.kt:339-355](platforms/android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt#L339-L355) - onCreate 修复
- [MarkdownEditorActivity.kt:4455-4468](platforms/android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt#L4455-L4468) - 恢复对话框回调
