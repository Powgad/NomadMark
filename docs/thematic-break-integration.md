# 分隔线功能集成指南

## 概述

本文档说明如何在 `MarkdownEditorActivity` 中集成分隔线自动格式化功能。

## 1. 添加依赖

在 `MarkdownEditorActivity.kt` 中添加导入：

```kotlin
import com.editor.nomadmark.format.ThematicBreakFormatter
import com.editor.nomadmark.markwon.StrictThematicBreakPlugin
```

## 2. 修改 initMarkwon()

在 `initMarkwon()` 方法中添加分隔线插件：

```kotlin
private fun initMarkwon() {
    markwon = Markwon.builder(this)
        .usePlugin(CorePlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(this))
        .usePlugin(TaskListPlugin.create(this))
        .usePlugin(ImagesPlugin.create(this))
        .usePlugin(MarkwonInlineParserPlugin.create())
        .usePlugin(JLatexMathPlugin.create(32f, object : JLatexMathPlugin.BuilderConfigure {
            override fun configureBuilder(builder: JLatexMathPlugin.Builder) {
                builder.inlinesEnabled(true)
            }
        }))
        // 添加分隔线插件
        .usePlugin(StrictThematicBreakPlugin.builder()
            .color(Color.rgb(80, 80, 80))  // 深灰色，墨水屏友好
            .height(2)                        // 2dp 高度
            .padding(24, 24)                 // 上下各 24dp 间距
            .build())
        // 原有的自定义主题配置
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder.headingBreakHeight(0)
            }
        })
        .build()
}
```

## 3. 添加 TextWatcher

在 `MarkdownEditorActivity` 中添加分隔线格式化器：

```kotlin
class MarkdownEditorActivity : android.app.Activity() {

    // ... 现有代码

    // =========================================================================
    // 分隔线自动格式化
    // =========================================================================

    /** 编辑器的分隔线格式化器 */
    private val editorThematicBreakFormatter by lazy {
        ThematicBreakFormatter { getCurrentEditor() }
    }

    /** 分屏编辑器的分隔线格式化器 */
    private val splitEditorThematicBreakFormatter by lazy {
        ThematicBreakFormatter { splitEditorText }
    }

    // =========================================================================
    // 初始化
    // =========================================================================

    private fun initViews() {
        // ... 现有代码

        // 添加分隔线格式化监听器
        editorText.addTextChangedListener(editorThematicBreakFormatter)
        splitEditorText.addTextChangedListener(splitEditorThematicBreakFormatter)
    }
}
```

## 4. 改进 insertThematicBreak()

修改现有的 `insertLine("---\n")` 调用：

```kotlin
// 在 setupListeners() 中修改
btnHr.setOnClickListener { insertThematicBreak() }

/**
 * 插入分隔线
 *
 * 自动添加前后空行，并将光标定位到新行
 */
private fun insertThematicBreak() {
    val editor = getCurrentEditor()
    val position = editor.selectionStart
    val text = editor.text

    // 检查前面是否需要添加空行
    val needsLeadingBlank = if (position == 0) {
        false
    } else {
        val prevChar = text[position - 1]
        prevChar != '\n'
    }

    // 构建分隔线文本
    val sb = StringBuilder()
    if (needsLeadingBlank) {
        sb.append("\n")
    }
    sb.append("---\n\n")

    // 插入文本
    text.insert(position, sb.toString())

    // 设置光标位置
    val newPosition = position + sb.length
    editor.setSelection(newPosition.coerceAtMost(text.length))

    markAsModified()

    // 立即更新预览
    updatePreview()
}
```

## 5. 测试场景

### 5.1 基本测试

| 输入 | 预期行为 |
|------|---------|
| 输入 `---` 后按 Enter | 自动添加前后空行 |
| 在 `text\n` 后输入 `---\n` | 自动添加前置空行 |
| 在代码块内输入 `---` | 不触发自动格式化 |
| 在行内代码内输入 `---` | 不触发自动格式化 |
| 在数学公式内输入 `$---$` | 不触发自动格式化 |

### 5.2 边界测试

```markdown
# 测试文档

段落1
---
← 应该自动添加空行

段落2

列表项
---

下一个列表项

> 引用内容
>
> ---
>
> 更多引用

代码块测试
```
---
← 不应该触发格式化
```

行内代码测试
`---`
← 不应该触发格式化

数学公式测试
$---$
← 不应该触发格式化

$$
---
$$
← 不应该触发格式化
```

### 5.3 工具栏按钮测试

1. 点击 "Hr" 按钮
2. 应该在光标位置插入：
   - 前置空行（如果需要）
   - `---`
   - 后置空行
   - 光标定位到新行

## 6. 故障排查

### 6.1 自动格式化不触发

- 检查是否正确添加了 TextWatcher
- 检查 `shouldAutoFormatThematicBreak()` 的返回值
- 添加日志输出以调试

### 6.2 光标位置不正确

- 检查 `formatThematicBreak()` 返回的光标位置
- 确保在主线程中设置光标位置

### 6.3 预览渲染不正确

- 检查 Markwon 插件是否正确添加
- 检查分隔线样式配置

## 7. 完整代码示例

```kotlin
// MarkdownEditorActivity.kt

package com.editor.nomadmark

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.widget.EditText
import com.editor.nomadmark.format.ThematicBreakFormatter
import com.editor.nomadmark.markwon.StrictThematicBreakPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.image.ImagesPlugin

class MarkdownEditorActivity : Activity() {

    private lateinit var markwon: Markwon
    private lateinit var editorText: EditText
    private lateinit var splitEditorText: EditText
    private lateinit var btnHr: Button

    /** 编辑器的分隔线格式化器 */
    private val editorThematicBreakFormatter by lazy {
        ThematicBreakFormatter { getCurrentEditor() }
    }

    /** 分屏编辑器的分隔线格式化器 */
    private val splitEditorThematicBreakFormatter by lazy {
        ThematicBreakFormatter { splitEditorText }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        // 初始化 Markwon
        initMarkwon()

        // 初始化视图
        initViews()

        // 设置监听器
        setupListeners()
    }

    private fun initMarkwon() {
        markwon = Markwon.builder(this)
            .usePlugin(CorePlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(TaskListPlugin.create(this))
            .usePlugin(ImagesPlugin.create(this))
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(32f, object : JLatexMathPlugin.BuilderConfigure {
                override fun configureBuilder(builder: JLatexMathPlugin.Builder) {
                    builder.inlinesEnabled(true)
                }
            }))
            // 添加分隔线插件
            .usePlugin(StrictThematicBreakPlugin.builder()
                .color(Color.rgb(80, 80, 80))
                .height(2)
                .padding(24, 24)
                .build())
            // 自定义主题配置
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.headingBreakHeight(0)
                }
            })
            .build()
    }

    private fun initViews() {
        // 初始化视图组件
        editorText = findViewById(R.id.editor_text)
        splitEditorText = findViewById(R.id.split_editor_text)
        btnHr = findViewById(R.id.btn_hr)

        // 添加分隔线格式化监听器
        editorText.addTextChangedListener(editorThematicBreakFormatter)
        splitEditorText.addTextChangedListener(splitEditorThematicBreakFormatter)
    }

    private fun setupListeners() {
        btnHr.setOnClickListener { insertThematicBreak() }
    }

    /**
     * 插入分隔线
     */
    private fun insertThematicBreak() {
        val editor = getCurrentEditor()
        val position = editor.selectionStart
        val text = editor.text

        // 检查前面是否需要添加空行
        val needsLeadingBlank = if (position == 0) {
            false
        } else {
            val prevChar = text[position - 1]
            prevChar != '\n'
        }

        // 构建分隔线文本
        val sb = StringBuilder()
        if (needsLeadingBlank) {
            sb.append("\n")
        }
        sb.append("---\n\n")

        // 插入文本
        text.insert(position, sb.toString())

        // 设置光标位置
        val newPosition = position + sb.length
        editor.setSelection(newPosition.coerceAtMost(text.length))

        markAsModified()
        updatePreview()
    }

    private fun getCurrentEditor(): EditText {
        return if (isSplitMode) splitEditorText else editorText
    }

    private var isSplitMode = false

    private fun markAsModified() {
        // 标记文档为已修改
    }

    private fun updatePreview() {
        // 更新预览
    }
}
```

## 8. 后续优化建议

1. **性能优化**：缓存分隔线格式化器的边界检查结果
2. **可配置性**：允许用户自定义分隔线样式（颜色、高度）
3. **快捷键支持**：添加键盘快捷键（如 Ctrl+Shift+H）快速插入分隔线
4. **撤销/重做集成**：将自动格式化操作纳入撤销栈
