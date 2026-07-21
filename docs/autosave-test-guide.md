# 自动保存功能测试指南

## 设备信息
- 设备: Supernote Nomad (SN078C10000377)
- 应用: NomadMark-debug-1.0.0.apk ✅ 已安装

## 测试前准备
1. 应用已安装并可以正常启动
2. 单元测试全部通过 (8/8) ✅
3. 编译成功无错误 ✅

---

## 手动测试步骤

### 测试 1: 自动保存基础功能 ✅

**步骤**:
1. 启动应用（会显示数学公式示例）
2. 点击"新建"按钮
3. 输入文件名（如 "test-autosave"）
4. 在编辑器中输入测试内容：`# 自动保存测试\n\n这是测试内容。`
5. **等待 5 秒**（3秒防抖 + 最小间隔）
6. 观察日志：`adb logcat -s AutoSaveSession:*`

**预期结果**:
- 日志显示：`AutoSaveSession: 自动保存成功: test-autosave (XX 字符)`
- 临时目录被创建：`cache/autosave/`
- 包含文件：`content_<uuid>.md` 和 `meta_<uuid>.properties`

**验证命令**:
```bash
adb shell run-as com.editor.nomadmark ls -la cache/autosave/
```

---

### 测试 2: 崩溃恢复功能 ✅

**步骤**:
1. 按照"测试 1"创建新文件并输入内容
2. **等待 8 秒**确保自动保存完成
3. 强制停止应用（模拟崩溃）：
   ```bash
   adb shell am force-stop com.editor.nomadmark
   ```
4. 重新启动应用：
   ```bash
   adb shell am start -n com.editor.nomadmark/.MainActivity
   ```

**预期结果**:
- 显示"发现未保存的内容"对话框
- 对话框内容：
  ```
  发现未保存的内容
  检测到上次的编辑未保存，是否恢复？
  
  文件：test-autosave
  时间：2025-01-21 15:30
  大小：1.2 KB
  
  [恢复] [放弃] [稍后]
  ```

---

### 测试 3: 恢复对话框选项 ✅

**测试 3.1: 点击"恢复"**
- 预期：内容恢复到编辑器，临时文件被删除
- 可以继续编辑

**测试 3.2: 点击"放弃"**
- 预期：临时文件被删除，显示新建文件对话框
- 内容丢失（符合预期）

**测试 3.3: 点击"稍后"**
- 预期：临时文件保留，正常启动
- 下次启动仍会提示恢复

---

### 测试 4: onPause 自动保存 ✅

**步骤**:
1. 创建新文件并输入内容
2. 按 Home 键使应用进入后台
3. 查看日志：`adb logcat -s MarkdownEditorActivity:* | grep paused`

**预期结果**:
- 日志显示：`Activity paused, auto-saved changes`

---

### 测试 5: 正常保存后清理 ✅

**步骤**:
1. 编辑内容触发自动保存
2. 点击"保存"按钮
3. 检查临时文件是否被清理

**预期结果**:
- 临时文件被删除
- SharedPreferences 标志被清除

---

## 自动保存配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 防抖延迟 | 3000ms | 用户停止输入后多久保存 |
| 最小间隔 | 5000ms | 两次保存最少间隔 |
| 过期时间 | 7天 | 临时文件保留时间 |

---

## 日志监控命令

```bash
# 监控所有自动保存相关日志
adb logcat -s AutoSaveSession:* AutoSaveManager:* MarkdownEditorActivity:*

# 只监控自动保存事件
adb logcat -s AutoSaveSession:* | grep -E "自动保存|初始化"

# 监控恢复相关日志
adb logcat -s AutoSaveManager:* MarkdownEditorActivity:* | grep -E "恢复|会话|可恢复"
```

---

## 已知限制

1. **示例文件不支持自动保存** - 数学公式示例是只读模式，不会触发自动保存
2. **需要创建新文件** - 必须通过"新建"按钮创建文件才能测试
3. **手动操作** - 当前测试需要手动点击按钮，无法完全自动化

---

## 测试清单

- [ ] 自动保存基础功能
- [ ] 崩溃后恢复对话框显示
- [ ] 点击"恢复"按钮
- [ ] 点击"放弃"按钮
- [ ] 点击"稍后"按钮
- [ ] onPause 自动保存
- [ ] 正常保存后清理临时文件
- [ ] 过期文件清理（7天后）

---

## 快速测试脚本

```bash
# 1. 安装 APK
adb install -r NomadMark-debug-1.0.0.apk

# 2. 启动应用
adb shell am start -n com.editor.nomadmark/.MainActivity

# 3. 监控日志
adb logcat -s AutoSaveSession:* AutoSaveManager:* MarkdownEditorActivity:*

# 4. 模拟崩溃
adb shell am force-stop com.editor.nomadmark

# 5. 重新启动（测试恢复）
adb shell am start -n com.editor.nomadmark/.MainActivity
```

---

## 问题排查

**问题**: 没有看到自动保存日志

**解决**: 
- 确保创建了新文件（不是示例文件）
- 等待至少 8 秒（3秒防抖 + 5秒最小间隔）
- 检查日志是否正确过滤

**问题**: 没有显示恢复对话框

**解决**:
- 确保在强制停止前等待了足够时间
- 检查临时文件是否存在：`adb shell run-as com.editor.nomadmark ls cache/autosave/`
- 查看完整日志排查问题
