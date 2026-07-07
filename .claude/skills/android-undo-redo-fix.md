# Android 问题修复记录

---

## 标题渲染后出现下划线问题

### 问题现象

预览模式下，标题文本下方显示类似下划线的分隔线。

### 问题排查过程

#### 第一轮排查：URLSpan 处理 ❌

**尝试**：修改 `removeUnderlines()` 函数，处理 `URLSpan` 并创建不带下划线的自定义版本。

**结果**：问题依然存在。

**原因**：Markwon 使用的不是标准的 `android.text.style.URLSpan`。

---

#### 第二轮排查：LinkSpan 处理 ❌

**尝试**：通过反射处理 Markwon 的 `io.noties.markwon.core.span.LinkSpan`。

**结果**：问题依然存在。

**原因**：`LinkSpan` 不是导致"下划线"的真正原因。

---

#### 第三轮排查：深度分析 ✅

**发现**：通过深入分析 Markwon 4.6.2 的文档和源码，找到真正原因。

**真正原因**：**Markwon 默认主题的 `headingBreakHeight` 属性**

这是 Markwon 为 H1 和 H2 标题添加的**视觉分隔线**（位于标题下方），看起来像下划线。

| Markwon 主题属性 | 默认值 | 说明 |
|------------------|--------|------|
| `headingBreakHeight` | 描边宽度 | H1/H2 标题下方的分隔线高度 |
| `headingBreakColor` | 文本颜色 + 75 alpha | 分隔线颜色 |
| `isLinkUnderlined` | true | 链接是否带下划线 |

---

### 最终解决方案

**修改文件**：`MarkdownEditorActivity.kt`

**修改内容**：

1. **新增导入**：
```kotlin
import io.noties.markwon.AbstractMarkwonPlugin
```

2. **修改 `initMarkwon()` 函数**（第 256-277 行）：
```kotlin
private fun initMarkwon() {
    markwon = Markwon.builder(this)
        .usePlugin(CorePlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(this))
        .usePlugin(TaskListPlugin.create(this))
        .usePlugin(ImagesPlugin.create())
        // 添加自定义主题配置，移除标题下划线和链接下划线
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                // 移除 H1 和 H2 标题下方的分隔线（"下划线"效果）
                builder.headingBreakHeight(0)
                // 禁用链接下划线
                builder.isLinkUnderlined(false)
            }
        })
        .build()
}
```

**关键点**：
- 使用 `AbstractMarkwonPlugin` 而不是直接实现 `MarkwonPlugin` 接口
- 重写 `configureTheme()` 方法
- 设置 `headingBreakHeight(0)` 移除分隔线
- 设置 `isLinkUnderlined(false)` 禁用链接下划线

---

### 验证方法

1. 打开包含 H1/H2 标题的 Markdown 文件
2. 切换到预览模式
3. 确认标题下方没有分隔线/下划线
4. 确认链接也没有下划线

---

### 相关文档

- [Markwon Theme Documentation (v4)](https://noties.io/Markwon/docs/v4/core/theme.html)
- [Markwon Configuration Documentation (v4)](https://noties.io/Markwon/docs/v4/core/configuration.html)
- [Markwon Plugins Documentation (v4)](https://noties.io/Markwon/docs/v4/core/plugins.html)

**修复状态**：✅ 已完成

---

## 撤销重做功能问题

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
