# Markdown 渲染引擎功能清单

## 已实现功能

### 1. 核心 Markwon 引擎
- **版本**: 4.6.2
- **功能**: 完整的 Markdown 解析和渲染支持

### 2. 代码语法高亮
- **库**: Prism4j 2.0.0
- **支持语言**: 通过 `Prism4j.GrammarLocator.ALL` 自动加载所有语法
  - JavaScript/TypeScript
  - Python
  - Java/Kotlin
  - C/C++
  - Rust
  - Go
  - HTML/CSS
  - JSON/YAML
  - Shell
  - 以及更多...

### 3. 表格渲染
- **插件**: `TablePlugin`
- **支持功能**:
  - 标准 Markdown 表格语法
  - 对齐方式 (左对齐、居中、右对齐)
  - 自动列宽调整
  - E-ink 优化的表格样式

### 4. 任务列表
- **插件**: `TaskListPlugin`
- **支持功能**:
  - `[ ]` 未完成任务
  - `[x]` 已完成任务
  - 支持嵌套任务列表

### 5. 删除线文本
- **插件**: `StrikethroughPlugin`
- **语法**: `~~删除的文本~~`

### 6. 图片支持
- **插件**: `ImagesPlugin`
- **支持功能**:
  - 标准 Markdown 图片语法 `![alt](url)`
  - 自动图片加载
  - 图片尺寸适配

### 7. 内联解析器
- **插件**: `InlineParserPlugin`
- **增强功能**:
  - 更精确的链接解析
  - 改进的图片 URL 处理
  - 自动链接识别

## 完整的 Markdown 语法支持

| 语法元素 | 支持状态 | 示例 |
|---------|---------|------|
| 标题 | ✅ | `# H1` 到 `###### H6` |
| 粗体 | ✅ | `**粗体**` 或 `__粗体__` |
| 斜体 | ✅ | `*斜体*` 或 `_斜体_` |
| 删除线 | ✅ | `~~删除~~` |
| 代码块 | ✅ | ``` ``` ``` |
| 行内代码 | ✅ | `` `代码` `` |
| 链接 | ✅ | `[文本](url)` |
| 图片 | ✅ | `![alt](url)` |
| 无序列表 | ✅ | `- 项目` 或 `* 项目` |
| 有序列表 | ✅ | `1. 项目` |
| 任务列表 | ✅ | `- [ ] 任务` |
| 表格 | ✅ | ` \| 列 \|` |
| 引用块 | ✅ | `> 引用` |
| 分隔线 | ✅ | `---` 或 `***` |
| 代码高亮 | ✅ | 支持多种语言 |

## E-ink 优化

### 渲染优化
- 高对比度灰度配色
- 减少不必要的刷新
- 清晰的代码块背景
- 适合电子墨水屏的字体大小

### 配色方案
```
- 代码块背景: #F5F5F5 (浅灰)
- 正文文本: #333333 (深灰)
- 代码高亮: 使用灰度值区分不同元素
  - 关键字: 黑色
  - 字符串: 深灰
  - 注释: 浅灰
  - 数字: 中灰
```

## 依赖库版本

```gradle
// build.gradle 配置
def markwonVersion = '4.6.2'

implementation "io.noties.markwon:core:$markwonVersion"
implementation "io.noties.markwon:ext-strikethrough:$markwonVersion"
implementation "io.noties.markwon:ext-tables:$markwonVersion"
implementation "io.noties.markwon:ext-tasklist:$markwonVersion"
implementation "io.noties.markwon:image:$markwonVersion"
implementation "io.noties.markwon:inline-parser:$markwonVersion"
implementation "io.noties.markwon:syntax-highlight:$markwonVersion"

// Prism4j for syntax highlighting
implementation "io.noties:prism4j:2.0.0"
implementation "io.noties.prism4j:languages:2.0.0"
```

## 使用示例

### 在代码中使用

```kotlin
// 初始化 Markwon
private fun initMarkwon() {
    val prism4j = Prism4j(Prism4j.GrammarLocator.ALL)
    
    markwon = Markwon.builder(this)
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(this))
        .usePlugin(TaskListPlugin.create(this))
        .usePlugin(ImagesPlugin.create())
        .usePlugin(InlineParserPlugin.create())
        .usePlugin(Prism4jSyntaxHighlight.create(prism4j))
        .build()
}

// 渲染 Markdown
private fun updatePreview() {
    val content = getCurrentContent()
    markwon.setMarkdown(previewText, content)
}
```

### Markdown 示例

```markdown
# 欢迎使用 NomadMark

这是一个功能丰富的 Markdown 编辑器，支持：

- **语法高亮** - 代码块自动高亮
- **表格渲染** - 完整的表格支持
- **任务列表** - 跟踪待办事项

## 代码示例

```python
def hello():
    print("Hello, World!")
```

## 表格示例

| 功能 | 状态 |
|------|------|
| 代码高亮 | ✅ |
| 表格 | ✅ |
| 图片 | ✅ |

## 任务列表

- [ ] 待完成
- [x] 已完成
```

## 待扩展功能

以下功能可以继续添加：

1. **数学公式** - 使用 JLatexMath 或 KaTeX
2. **脚注** - 扩展 Markwon 插件
3. **Emoji** - Emoji 短代码支持
4. **自定义主题** - 更多 E-ink 主题选项
5. **导出功能** - 导出为 HTML/PDF

## 文件位置

- **构建配置**: [android/app/build.gradle](android/app/build.gradle)
- **渲染实现**: [android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt](android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt)
