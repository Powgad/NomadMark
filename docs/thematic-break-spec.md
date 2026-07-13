# NomadMark 分隔线（Thematic Break）设计规范

## 1. 规范层设计

### 1.1 唯一合法语法规则

```markdown
# 规范定义

分隔线必须满足以下所有条件，否则视为普通文本：

1. 符号：`---`（三个连续减号）
2. 前置空行：分隔线前必须有一个空行
3. 后置空行：分隔线后必须有一个空行
4. 行首行尾：`---` 前后不能有任何非空白字符
5. 空白容忍：`---` 前后允许最多 3 个空格（用于缩进）

# 合法示例

文本内容

---
← 三个减号，前后各一个空行

更多内容

# 非法示例

文本---
← 前置缺少空行，渲染为普通文本

---
← 文档开头缺少前置空行，渲染为普通文本

---后置内容
← 后置缺少空行，渲染为普通文本

not ---
← 前面有非空白字符，渲染为普通文本
```

### 1.2 为什么此规则彻底消除 Setext 歧义

**Setext 标题语法**（CommonMark 规范）：
```markdown
This is a heading
=================
```

**歧义场景**：
```markdown
Some text
---
← 在 CommonMark 中，这会被解析为二级标题（Setext H2）
```

**我们的解决方法**：
- **强制前置空行**：`---` 前必须有空行
- 因此 `text\n---` 永远不会解析为分隔线
- 用户若要插入分隔线，必须输入 `text\n\n---\n`（两个换行符）

### 1.3 CommonMark 规范对应条款

根据 [CommonMark Spec 0.30 - Thematic Breaks](https://spec.commonmark.org/0.30/#thematic-breaks)：

> A line consisting of 0-3 spaces of indentation, followed by a sequence of three or more matching `-`, `_`, or `*` characters, each followed optionally by any number of spaces or tabs, forms a **thematic break**.

**我们与 CommonMark 的差异**：
| 项目 | CommonMark | NomadMark | 说明 |
|------|-----------|-----------|------|
| 符号 | `-`, `_`, `*` | 仅 `-` | 简化输入，降低歧义 |
| 数量 | ≥ 3 | 恰好 3 | Editor 层规范化 |
| 前置空行 | 不要求 | **必须** | 消除 Setext 歧义 |
| 后置空行 | 不要求 | **必须** | 视觉分隔效果 |
| 缩进容忍 | 0-3 spaces | 0-3 spaces | 一致 |

**兼容性说明**：我们的规范比 CommonMark 更严格。不符合我们规范的输入会渲染为普通文本（而非标题），这是有意设计的行为。

---

## 2. Editor 层设计（核心）

### 2.1 自动修正逻辑

当用户在编辑器中输入 `---` 并触发以下条件时，自动修正文本：

```kotlin
// 触发条件
1. 用户输入了 `---`（三个连续减号）
2. 当前行除了 `---` 和空白字符外无其他内容
3. 用户按下 Enter 键（或检测到换行）

// 自动修正行为
插入一个前置空行（如果不存在）
插入一个后置空行
将光标定位到后置空行之后
```

### 2.2 光标位置处理

```kotlin
// 场景 1：用户在 "text\n---" 后按 Enter
// 原始文本：
text
---| ← 光标位置

// 修正后：
text

---

| ← 光标位置（新增空行后）


// 场景 2：用户在空行输入 "---" 后按 Enter
// 原始文本：
| ← 光标在空行
--- ← 用户输入

// 修正后（如果前面没有空行）：

---

| ← 光标位置
```

### 2.3 避免误触发的边界检查

在执行自动修正前，必须检查以下条件：

```kotlin
fun shouldAutoFormatThematicBreak(
    text: Editable,
    position: Int
): Boolean {
    // 1. 检查是否在代码块内（``` 或 ~~~）
    if (isInsideCodeBlock(text, position)) return false

    // 2. 检查是否在行内代码内（`...`）
    if (isInsideInlineCode(text, position)) return false

    // 3. 检查是否在数学公式内（$...$ 或 $$...$$）
    if (isInsideMathBlock(text, position)) return false

    // 4. 检查当前行内容是否仅为 "---" 和空白
    val line = getCurrentLine(text, position)
    val trimmed = line.trim()
    if (trimmed != "---") return false

    // 5. 检查缩进不超过 3 个空格
    val leadingSpaces = line.takeWhile { it == ' ' }.length
    if (leadingSpaces > 3) return false

    return true
}
```

### 2.4 完整 Kotlin 实现（基于 TextWatcher）

```kotlin
/**
 * 分隔线自动格式化 TextWatcher
 *
 * 职责：
 * - 检测用户输入 "---"
 * - 自动添加前后空行
 * - 正确定位光标
 */
class ThematicBreakFormatter : TextWatcher {
    private var isFormatting = false
    private var lastChangeStart = 0
    private var lastChangeCount = 0

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        lastChangeStart = start
        lastChangeCount = count
    }

    override fun afterTextChanged(s: Editable?) {
        if (isFormatting || s == null) return

        val position = lastChangeStart + lastChangeCount
        if (shouldAutoFormatThematicBreak(s, position)) {
            isFormatting = true
            try {
                formatThematicBreak(s, position)
            } finally {
                isFormatting = false
            }
        }
    }

    /**
     * 格式化分隔线
     */
    private fun formatThematicBreak(text: Editable, position: Int) {
        // 获取当前行的起始位置
        val lineStart = findLineStart(text, position)
        val lineEnd = findLineEnd(text, position)

        // 检查并添加前置空行
        var cursorPosition = lineEnd
        if (lineStart > 0) {
            val prevChar = text[lineStart - 1]
            if (prevChar != '\n') {
                // 前面不是换行符，插入一个
                text.insert(lineStart, "\n")
                cursorPosition += 1
            } else if (lineStart > 1 && text[lineStart - 2] != '\n') {
                // 前面有一个换行符，但没有两个（即没有空行）
                text.insert(lineStart, "\n")
                cursorPosition += 1
            }
        }

        // 重新计算位置（因为可能插入了字符）
        val newLineEnd = cursorPosition

        // 检查并添加后置空行
        val endPosition = newLineEnd + 1
        if (endPosition >= text.length || text[endPosition] != '\n') {
            text.insert(newLineEnd, "\n\n")
        } else if (endPosition + 1 >= text.length || text[endPosition + 1] != '\n') {
            // 有一个换行符，但需要两个（空行）
            text.insert(newLineEnd + 1, "\n")
        }

        // 将光标移动到分隔线后的空行之后
        val editor = getCurrentEditor() // 需要从外部获取
        val newCursorPosition = newLineEnd + 2
        editor.setSelection(newCursorPosition.coerceAtMost(text.length))
    }

    /**
     * 查找行起始位置
     */
    private fun findLineStart(text: Editable, position: Int): Int {
        var pos = position.coerceAtMost(text.length - 1)
        while (pos > 0 && text[pos - 1] != '\n') {
            pos--
        }
        return pos
    }

    /**
     * 查找行结束位置
     */
    private fun findLineEnd(text: Editable, position: Int): Int {
        var pos = position.coerceAtMost(text.length - 1)
        while (pos < text.length && text[pos] != '\n') {
            pos++
        }
        return pos
    }

    /**
     * 检查是否应该在代码块内
     */
    private fun isInsideCodeBlock(text: CharSequence, position: Int): Boolean {
        // 向前搜索最近的代码块标记
        var i = 0
        var inCodeBlock = false
        var codeBlockStart = -1

        while (i < position) {
            if (i + 2 < text.length && text[i] == '`' && text[i + 1] == '`' && text[i + 2] == '`') {
                if (inCodeBlock) {
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                    codeBlockStart = i
                }
                i += 3
            } else {
                i++
            }
        }

        // 检查是否在代码块内
        return inCodeBlock && codeBlockStart < position
    }

    /**
     * 检查是否在行内代码内
     */
    private fun isInsideInlineCode(text: CharSequence, position: Int): Boolean {
        var i = 0
        var inInlineCode = false

        while (i < position) {
            if (text[i] == '`' && (i == 0 || text[i - 1] != '`')) {
                inInlineCode = !inInlineCode
            }
            i++
        }

        return inInlineCode
    }

    /**
     * 检查是否在数学公式块内
     */
    private fun isInsideMathBlock(text: CharSequence, position: Int): Boolean {
        // 检查 $$...$$ 块级公式
        var i = 0
        var inMathBlock = false

        while (i < position) {
            if (i + 1 < text.length && text[i] == '$' && text[i + 1] == '$') {
                inMathBlock = !inMathBlock
                i += 2
            } else {
                i++
            }
        }

        if (inMathBlock) return true

        // 检查 $...$ 行内公式
        i = 0
        var inInlineMath = false
        while (i < position) {
            if (text[i] == '$' && (i == 0 || text[i - 1] != '$')) {
                inInlineMath = !inInlineMath
            }
            i++
        }

        return inInlineMath
    }

    /**
     * 获取当前行内容
     */
    private fun getCurrentLine(text: Editable, position: Int): String {
        val lineStart = findLineStart(text, position)
        val lineEnd = findLineEnd(text, position)
        return text.subSequence(lineStart, lineEnd).toString()
    }

    // 需要从外部注入
    private fun getCurrentEditor(): EditText {
        // 实现在 Activity 中
        throw NotImplementedError()
    }
}
```

### 2.5 在 MarkdownEditorActivity 中集成

```kotlin
// 在 initViews() 中添加
private val thematicBreakFormatter = ThematicBreakFormatter()

fun initViews() {
    // ... 现有代码

    // 添加分隔线格式化监听器
    editorText.addTextChangedListener(thematicBreakFormatter)
    splitEditorText.addTextChangedListener(thematicBreakFormatter)
}

// 为 ThematicBreakFormatter 提供编辑器引用
class ThematicBreakFormatter(private val editorProvider: () -> EditText) : TextWatcher {
    // ... 修改上面的实现，使用 editorProvider() 获取当前编辑器
}
```

---

## 3. 渲染层设计（Markwon 配置）

### 3.1 禁用 Setext 标题解析

Markwon 基于 commonmark-java，默认支持 Setext 标题。我们需要禁用它：

```kotlin
import io.noties.markwon.AbstractMarkwonPlugin
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.block.BlockStartFinder
import org.commonmark.parser.block.BlockParser
import org.commonmark.parser.block.BlockParserFactory

/**
 * 创建严格的分隔线解析插件
 *
 * 职责：
 * - 仅解析符合规范的分隔线（前后必须有空行）
 * - 禁用 Setext 标题解析
 */
fun createStrictThematicBreakPlugin(): AbstractMarkwonPlugin {
    return object : AbstractMarkwonPlugin() {
        override fun configureParser(builder: org.commonmark.parser.Parser.Builder) {
            // 覆盖默认的分隔线解析器
            builder.customBlockParserFactory(StrictThematicBreakParserFactory())
        }

        override fun configureTheme(builder: MarkwonTheme.Builder) {
            // 自定义分隔线渲染样式
            builder.thematicBreakColor(Color.GRAY)
            builder.thematicBreakHeight(2) // 2px 高度
            builder.thematicBreakPadding(32, 32) // 上下各 32dp 间距
        }
    }
}

/**
 * 严格的分隔线块解析器
 */
class StrictThematicBreakParser(
    private val hasBlankLineBefore: Boolean
) : BlockParser() {

    private var block = ThematicBreak()

    override fun getBlock(): ThematicBreak = block

    override fun parseBlockBlock(line: String, nextLine: String?): BlockContinue {
        // 分隔线只能占一行
        return BlockContinue.none()
    }

    override fun isClosed(nextLine: String?): Boolean {
        // 必须后跟空行
        return nextLine == null || nextLine.trim().isEmpty()
    }

    override fun canHaveLazyContinuationLines(): Boolean = false

    class Factory : BlockParserFactory {
        override fun create(parserState: BlockParserState): BlockParser? {
            val line = parserState.line
            val lineIndex = parserState.lineIndex
            val lineStart = parserState.lineStart

            // 检查缩进（最多 3 个空格）
            val indent = lineStart - lineStart.trimStart { it == ' ' }.length
            if (indent > 3) return null

            val trimmed = line.substring(indent).trim()
            if (trimmed != "---") return null

            // 检查前置空行
            val hasBlankLineBefore = if (lineIndex == 0) {
                false // 文档开头没有前置空行
            } else {
                val prevLine = parserState.lines[lineIndex - 1]
                prevLine.trim().isEmpty()
            }

            if (!hasBlankLineBefore) return null

            return StrictThematicBreakParser(hasBlankLineBefore)
        }

        override fun tryStart(parserState: BlockParserState, blockParser: BlockParser?): BlockStart {
            val line = parserState.line
            val lineIndex = parserState.lineIndex
            val lineStart = parserState.lineStart

            // 检查缩进
            val indent = lineStart - lineStart.trimStart { it == ' ' }.length
            if (indent > 3) return BlockStart.none()

            val trimmed = line.substring(indent).trim()
            if (trimmed != "---") return BlockStart.none()

            // 检查前置空行
            val hasBlankLineBefore = if (lineIndex == 0) {
                return BlockStart.none() // 文档开头必须有空行
            } else {
                val prevLine = parserState.lines[lineIndex - 1]
                prevLine.trim().isEmpty()
            }

            if (!hasBlankLineBefore) return BlockStart.none()

            return BlockStart.of(StrictThematicBreakParser(hasBlankLineBefore))
        }
    }
}

class StrictThematicBreakParserFactory : BlockParserFactory {
    override fun create(state: BlockParserState): BlockParser? {
        return StrictThematicBreakParser.Factory().create(state)
    }
}
```

### 3.2 简化方案：使用 Markwon 默认解析 + 后处理

如果上述方案过于复杂，可以使用简化方案：

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
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                // 自定义分隔线样式
                builder.headingBreakHeight(0) // 移除标题下方的分隔线

                // 分隔线颜色（深灰色，墨水屏友好）
                builder.thematicBreakColor(Color.rgb(80, 80, 80))

                // 分隔线高度（2dp）
                builder.thematicBreakHeight(2)

                // 分隔线上下间距（各 24dp）
                builder.thematicBreakPadding(24, 24)
            }
        })
        // 添加后处理：移除 Setext 标题的下划线
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun beforeRender(node: org.commonmark.node.Node) {
                removeSetextHeadingsUnderline(node)
            }

            private fun removeSetextHeadingsUnderline(node: org.commonmark.node.Node) {
                // 递归遍历节点树，查找并移除 Setext 标题的下划线
                var current: org.commonmark.node.Node? = node
                while (current != null) {
                    if (current is org.commonmark.node.Heading) {
                        // 检查是否为 Setext 标题（通过检查是否有底部的 ThematicBreak）
                        // 实际上 commonmark-java 不会将 Setext 标题解析为带下划线的节点
                        // Setext 标题会被解析为普通 Heading 节点
                    }
                    current = current.next
                }
            }
        })
        .build()
}
```

**注意**：由于 commonmark-java 的解析逻辑在 `CorePlugin` 中，完全禁用 Setext 标题需要更底层的修改。简化方案是：
1. 接受 Markwon 的默认解析行为
2. 依赖 Editor 层的自动规范化，确保用户输入符合规范
3. 如果用户手动输入 `text\n---`，会被渲染为二级标题（这是 CommonMark 的行为，但我们不鼓励这种用法）

### 3.3 推荐方案：使用自定义 Span 渲染

```kotlin
/**
 * 自定义分隔线渲染 Span
 */
class CustomThematicBreakSpan(
    private val height: Int,
    private val color: Int,
    private val topPadding: Int,
    private val bottomPadding: Int
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            fm.top = -topPadding - height
            fm.bottom = bottomPadding
        }
        return 0 // 不占宽度
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val baseline = y
        val lineHeight = paint.descent() - paint.ascent()

        // 计算分隔线的垂直位置
        val lineTop = baseline + paint.ascent() - topPadding
        val lineBottom = lineTop + height

        // 绘制分隔线
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRect(x, lineTop.toFloat(), x + canvas.width, lineBottom.toFloat(), paint)
    }
}
```

---

## 4. 边界情况与坑位清单

### 4.1 列表项内插入 `---`

```markdown
- 列表项内容

---

- 下一个列表项

# 规范行为
✅ 列表项内的 `---` 如果满足分隔线规范（前后有空行），应解析为分隔线
✅ 分隔线会中断列表
❌ 如果列表项内 `---` 前后没有空行，渲染为普通文本
```

**实现要点**：在列表解析器中，遇到空行后检查下一行是否为分隔线。

### 4.2 BlockQuote 内插入 `---`

```markdown
> 引用内容
>
> ---
>
> 更多引用

# 规范行为
✅ 引用块内的分隔线（前后有空行）渲染为分隔线
✅ 分隔线保持在引用块内
❌ 引用块外的分隔线不会影响引用块
```

### 4.3 连续多个 `---`

```markdown
---

---

---

# 规范行为
✅ 每个 `---` 之间有空行，渲染为 3 个分隔线
❌ 如果 `---` 之间没有空行，CommonMark 会将其解析为更长的分隔线（但我们的 Editor 层会自动添加空行）
```

### 4.4 与主流编辑器的行为对比

| 场景 | GitHub | Obsidian | Typora | NomadMark |
|------|--------|----------|--------|-----------|
| `text\n---` | H2 标题 | H2 标题 | H2 标题 | 普通文本 |
| `text\n\n---` | 分隔线 | 分隔线 | 分隔线 | 分隔线 |
| `---\n` (文档开头) | 分隔线 | 分隔线 | 分隔线 | 普通文本 |
| 列表内的 `---` | 分隔线+中断列表 | 分隔线+中断列表 | 分隔线+中断列表 | 分隔线+中断列表 |
| `***` / `___` | 分隔线 | 分隔线 | 分隔线 | 普通文本 |

**设计决策**：NomadMark 采用**最严格的分隔线规范**，确保无歧义，但牺牲了一定的兼容性。

---

## 5. 可选增强功能

### 5.1 支持 `***` / `___` 作为分隔线

**建议**：暂不支持，理由：
1. `---` 已经足够表达分隔线
2. 减少用户记忆负担
3. 避免与粗体（`***text***`）混淆

如需支持，可在 Editor 层添加：

```kotlin
private fun isThematicBreakCandidate(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed == "---" || trimmed == "***" || trimmed == "___"
}
```

### 5.2 工具栏按钮

项目已有 `btnHr` 按钮（[MarkdownEditorActivity.kt:741,872](platforms/android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt#L741)），实现为：

```kotlin
btnHr.setOnClickListener {
    insertThematicBreak()
}

private fun insertThematicBreak() {
    val editor = getCurrentEditor()
    val position = editor.selectionStart

    // 检查前面是否需要添加空行
    val needsLeadingBlank = if (position == 0) {
        false
    } else {
        val prevChar = editor.text[position - 1]
        prevChar != '\n'
    }

    // 构建分隔线文本
    val sb = StringBuilder()
    if (needsLeadingBlank) {
        sb.append("\n")
    }
    sb.append("---\n\n")

    // 插入文本
    editor.text.insert(position, sb.toString())

    // 设置光标位置
    editor.setSelection(position + sb.length)

    markAsModified()
}
```

### 5.3 分隔线样式配置

为用户提供分隔线样式选项：

```kotlin
enum class ThematicBreakStyle(val displayName: String) {
    SOLID("实线"),
    DASHED("虚线"),
    DOTTED("点线")
}

// 在设置中添加
private fun showThematicBreakStyleDialog() {
    val styles = ThematicBreakStyle.values()
    val styleNames = styles.map { it.displayName }.toTypedArray()

    AlertDialog.Builder(this)
        .setTitle("选择分隔线样式")
        .setSingleChoiceItems(styleNames, currentStyle.ordinal) { dialog, which ->
            currentStyle = styles[which]
            updatePreview()
            dialog.dismiss()
        }
        .show()
}
```

---

## 6. 总结

### 规范层
- ✅ 唯一合法语法：`---` 前后各有一个空行
- ✅ 彻底消除 Setext 歧义（强制前置空行）
- ✅ 比 CommonMark 更严格，确保无歧义

### Editor 层
- ✅ 自动格式化：检测 `---` 输入，自动添加空行
- ✅ 光标管理：正确定位到新行
- ✅ 边界检查：代码块、行内代码、数学公式不触发

### 渲染层
- ✅ Markwon 配置：自定义分隔线样式
- ⚠️ 完全禁用 Setext 需要 custom BlockParser
- ⚠️ 简化方案：依赖 Editor 层规范化

### 边界情况
- ✅ 列表内的分隔线：中断列表
- ✅ 引用块内的分隔线：保持引用块内
- ✅ 连多个分隔线：每个独立渲染
- ⚠️ 与主流编辑器不完全兼容（有意设计）

### 可选增强
- ❌ 暂不支持 `***` / `___`
- ✅ 工具栏按钮（已有 `btnHr`）
- 🔄 分隔线样式配置（待实现）
