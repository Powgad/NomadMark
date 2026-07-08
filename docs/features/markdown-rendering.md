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

## 数学公式渲染

### 技术选型

使用 **Markwon + KaTeX** 实现数学公式渲染：

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| KaTeX | 性能优秀、无外部依赖、渲染快速 | 功能覆盖略少于 MathJax | ✅ 采用 |
| MathJax | 功能最完整、兼容性好 | 体积大、性能较差 | ❌ 放弃 |
| JLatexMath | 原生 Android、离线支持 | 渲染质量一般 | 备选方案 |

### 支持的语法

#### 行内公式
使用单个 `$` 符号包裹：

```markdown
爱因斯坦质能方程 $E = mc^2$ 是著名的物理公式。
```

#### 块级公式
使用双 `$$` 符号包裹：

```markdown
$$
\frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
$$
```

### 依赖配置

在 `build.gradle` 中添加：

```gradle
def markwonVersion = '4.6.2'

// KaTeX 数学公式支持
implementation "io.noties.markwon:ext-latex:$markwonVersion"
```

### 代码实现

#### 初始化 Markwon

```kotlin
private fun initMarkwon() {
    val prism4j = Prism4j(Prism4j.GrammarLocator.ALL)

    markwon = Markwon.builder(this)
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(this))
        .usePlugin(TaskListPlugin.create(this))
        .usePlugin(ImagesPlugin.create())
        .usePlugin(InlineParserPlugin.create())
        .usePlugin(Prism4jSyntaxHighlight.create(prism4j))
        .usePlugin(LatexPlugin.create())  // 添加数学公式插件
        .build()
}
```

### E-ink 优化策略

数学公式在墨水屏上需要特殊处理：

1. **字体大小优化**
   - 行内公式：使用稍大字号避免过小
   - 块级公式：居中显示，适当留白

2. **对比度调整**
   - 确保公式与背景有足够对比度
   - 使用纯黑 (#000000) 渲染公式符号

3. **刷新控制**
   - 公式渲染完成后触发局部刷新
   - 避免全屏刷新造成闪烁

### 支持的 LaTeX 语法

#### 常用符号

| 类型 | 语法 | 渲染效果 |
|------|------|----------|
| 上标 | `x^2` | $x^2$ |
| 下标 | `H_2O` | $H_2O$ |
| 分数 | `\frac{a}{b}` | $\frac{a}{b}$ |
| 根号 | `\sqrt{x}` | $\sqrt{x}$ |
| 求和 | `\sum_{i=1}^{n}` | $\sum_{i=1}^{n}$ |
| 积分 | `\int_{a}^{b}` | $\int_{a}^{b}$ |
| 极限 | `\lim_{x \to \infty}` | $\lim_{x \to \infty}$ |

#### 矩阵与表格

```latex
$$
\begin{pmatrix}
a & b \\
c & d
\end{pmatrix}
$$
```

#### 常用示例

```markdown
# 数学公式示例

行内公式：勾股定理 $a^2 + b^2 = c^2$

块级公式：
$$
x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
$$

矩阵：
$$
A = \begin{bmatrix}
1 & 2 & 3 \\
4 & 5 & 6 \\
7 & 8 & 9
\end{bmatrix}
$$

积分：
$$
\int_{-\infty}^{\infty} e^{-x^2} dx = \sqrt{\pi}
$$
```

### 实现清单

- [ ] 添加 KaTeX 依赖到 build.gradle
- [ ] 在 MarkdownEditorView 中初始化 LatexPlugin
- [ ] 测试行内公式渲染
- [ ] 测试块级公式渲染
- [ ] E-ink 显示效果调优
- [ ] 添加公式渲染错误处理
- [ ] 性能测试（复杂公式渲染速度）

### 已知限制

1. **KaTeX 不支持的 LaTeX 功能**
   - 某些高级宏包（如 TikZ 图形）
   - 自定义命令定义
   - 部分化学公式符号

2. **性能考虑**
   - 复杂公式（如大型矩阵）渲染时间较长
   - 建议对超长公式添加加载提示

### 备选方案

如果 KaTeX 渲染效果不理想，可考虑：

**JLatexMath 原生渲染**

```gradle
implementation 'ru.notext:jlaticmath:0.1.2'
```

优点：
- 完全离线
- 原生 Android 渲染
- 对墨水屏更友好

缺点：
- 库维护较少
- LaTeX 语法支持不如 KaTeX 完善

## 待扩展功能

以下功能可以继续添加：

1. ~~**数学公式** - 使用 JLatexMath 或 KaTeX~~ ✅ 已设计
2. **脚注** - 扩展 Markwon 插件
3. **Emoji** - Emoji 短代码支持
4. **自定义主题** - 更多 E-ink 主题选项
5. **导出功能** - 导出为 HTML/PDF

## 文件位置

- **构建配置**: [android/app/build.gradle](android/app/build.gradle)
- **渲染实现**: [android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt](android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt)
