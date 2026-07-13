# Markdown 语法完整测试文件

这是一份包含所有常用 Markdown 语法的测试文件，用于验证渲染引擎的功能。

---

## 1. 标题 (Headings)

```markdown
# 一级标题 (H1)
## 二级标题 (H2)
### 三级标题 (H3)
#### 四级标题 (H4)
##### 五级标题 (H5)
###### 六级标题 (H6)
```

---

## 2. 文本强调 (Text Emphasis)

**粗体文本** (使用双星号)

__粗体文本__ (使用双下划线)

*斜体文本* (使用单星号)

_斜体文本_ (使用单下划线)

***粗斜体文本*** (粗体+斜体)

~~删除线文本~~

==高亮文本== (部分渲染器支持)

---

## 3. 段落与换行 (Paragraphs & Line Breaks)

这是第一段。段落之间用空行分隔。

这是第二段。  
行末使用两个空格+回车强制换行（软换行）。

这是第三段，在同一个段落内。

---

## 4. 列表 (Lists)

### 4.1 无序列表 (Unordered Lists)

- 项目一
- 项目二
  - 嵌套项目 A
  - 嵌套项目 B
- 项目三

+ 使用加号
* 使用星号

### 4.2 有序列表 (Ordered Lists)

1. 第一项
2. 第二项
3. 第三项
   1. 嵌套有序项
   2. 另一个嵌套项
4. 第四项

### 4.3 任务列表 (Task Lists)

- [x] 已完成的任务
- [ ] 未完成的任务
- [ ] 待办事项 A
- [ ] 待办事项 B
  - [x] 子任务已完成
  - [ ] 子任务未完成

---

## 5. 代码 (Code)

### 5.1 行内代码 (Inline Code)

使用反引号创建 `行内代码`，例如 `const x = 10;`。

### 5.2 代码块 (Code Blocks)

```
普通代码块
没有语法高亮
```

### 5.3 带语法高亮的代码块 (Fenced Code Blocks)

#### Rust
```rust
fn main() {
    let greeting = "Hello, NomadMark!";
    println!("{}", greeting);

    // 结构体示例
    struct Document {
        title: String,
        content: Vec<u8>,
    }

    let doc = Document {
        title: String::from("测试文档"),
        content: vec![],
    };
}
```

#### Kotlin
```kotlin
class MarkdownRenderer {
    fun render(content: String): String {
        return content
            .split("\n")
            .joinToString("\n") { line ->
                "  $line"
            }
    }
}
```

#### Python
```python
def parse_markdown(text: str) -> dict:
    """解析 Markdown 文本"""
    lines = text.split('\n')
    headers = [line for line in lines if line.startswith('#')]
    return {
        'total_lines': len(lines),
        'headers': headers
    }
```

#### JavaScript/TypeScript
```typescript
interface MarkdownConfig {
    enableGFM: boolean;
    sanitize: boolean;
}

class Parser {
    constructor(private config: MarkdownConfig) {}
    
    parse(src: string): string {
        return src.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    }
}
```

#### CSS
```css
.markdown-body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
    line-height: 1.6;
}

.markdown-body code {
    background-color: #f6f8fa;
    padding: 0.2em 0.4em;
    border-radius: 3px;
}
```

#### Bash
```bash
#!/bin/bash
# 构建脚本
echo "Building NomadMark..."
cargo build --release
./gradlew assembleDebug
```

---

## 6. 引用 (Blockquotes)

> 这是一段引用文本。
> 
> 可以跨越多行。
>
> > 嵌套引用
> >
> > 可以继续嵌套

引用中可以包含其他元素：

> ### 引用中的标题
>
> - 列表项
> - 另一个列表项
>
> `行内代码` 也可以使用。

---

## 7. 链接 (Links)

### 7.1 行内链接

[GitHub](https://github.com)

[Supernote 官网](https://www.supernote.com)

### 7.2 带标题的链接

[NomadMark](https://github.com/yangyong "一个 Markdown 编辑器")

### 7.3 相对路径链接

[相关文档](./docs/README.md)

### 7.4 引用式链接

这是[引用式链接][link-reference]，可以统一管理链接地址。

[link-reference]: https://example.com

### 7.5 自动链接

<https://www.example.com>

<user@example.com>

---

## 8. 图片 (Images)

### 8.1 行内图片

![替代文本](https://via.placeholder.com/150)

### 8.2 带标题的图片

![Supernote Logo](https://via.placeholder.com/200x100 "Supernote")

### 8.3 引用式图片

![占位图][placeholder]

[placeholder]: https://via.placeholder.com/300x150

### 8.4 HTML 图片（支持尺寸控制）

<img src="https://via.placeholder.com/150x50" width="150" height="50" alt="HTML 图片">

---

## 9. 表格 (Tables)

### 9.1 基本表格

| 列1 | 列2 | 列3 |
|-----|-----|-----|
| A   | B   | C   |
| D   | E   | F   |

### 9.2 对齐方式

| 左对齐 | 居中 | 右对齐 |
|:-------|:----:|-------:|
| Left   | Center | Right |
| 100    | 250    | 300   |
| 渲染   | 测试    | 完成   |

### 9.3 复杂表格

| 功能 | 支持状态 | 备注 |
|:-----|:--------:|:-----|
| 标题 | ✅ | H1-H6 全支持 |
| 列表 | ✅ | 有序/无序/任务列表 |
| 代码 | ✅ | 语法高亮 |
| 表格 | ✅ | 支持对齐 |
| 脚注 | ⚠️ | 部分渲染器支持 |

---

## 10. 分隔线 (Horizontal Rules)

***

---

___

---

## 11. 转义字符 (Escaping)

特殊字符需要使用反斜杠转义：

\*不是斜体\*

\[不是链接\]

\(不是括号\)

可以转义的字符：\ * _ { } [ ] ( ) # + - . ! | ` ~ >

---

## 12. HTML 标签支持

<details>
<summary>点击展开折叠内容</summary>

这里是隐藏的内容，可以包含任意 HTML 和 Markdown。

- 列表项
- **粗体**
</details>

<div style="color: red;">
这是内联样式，红色文字（部分渲染器支持）
</div>

<kbd>Ctrl</kbd> + <kbd>S</kbd> 保存

<mark>高亮文本</mark>

---

## 13. 脚注 (Footnotes - 部分)

这里有一个脚注引用[^1]，还有另一个[^note]。

[^1]: 这是第一个脚注的内容。
[^note]: 这是命名脚注，可以包含更多细节。

---

## 14. 缩写定义 (Abbreviations - 部分)

HTML 是超文本标记语言的缩写。

---

## 15. 数学公式 (Math - 部分)

行内公式：$E = mc^2$

块级公式：

$$
\sum_{i=1}^{n} i = \frac{n(n+1)}{2}
$$

$$
\int_{-\infty}^{\infty} e^{-x^2} dx = \sqrt{\pi}
$$

---

## 16. Emoji 支持 :smile:

一些常用 Emoji：

:smile: :laughing: :heart: :thumbsup: :fire: :rocket: :book: :computer: :writing_hand: :memo:

---

## 17. 定义列表 (Definition Lists - 部分)

术语 1
:   定义 1 的内容
    可以有多行

术语 2
:   定义 2

---

## 18. 强调标记（部分渲染器）

==这是高亮文本==

---

## 19. 下标和上标（部分渲染器）

H~2~O (水)

E=mc^2^

---

## 20. 混合测试

### 综合示例

> **提示**：这是一个包含多种元素的综合示例。
>
> 可以在引用中使用：
> 1. 列表
> 2. `代码`
> 3. [链接](https://example.com)
>
> ```rust
> fn example() {
>     println!("Mixed content!");
> }
> ```

| 语言 | 文件扩展名 | 用途 |
|:-----|:----------|:-----|
| Rust | .rs | Core 层 |
| Kotlin | .kt | Android 层 |

---

## 21. 嵌套结构测试

### 嵌套列表

- 一级项目
  1. 二级有序项
     - 三级无序项
     - 三级无序项 B
  2. 另一个二级项
      - `代码在嵌套中`
- 一级项目 B

### 嵌套引用

> 第一层
>
> > 第二层
> >
> > > 第三层
> > > - 嵌套列表
> > > - [ ] 任务项

---

## 22. 长内容测试

### 长段落

这是一个很长的段落，用于测试渲染引擎对长文本的处理能力。在实际使用中，用户可能会编写包含大量文字的文档，因此渲染引擎需要正确处理换行、对齐等显示问题。此外，还需要考虑中英文混排的情况，比如这里插入 English text，以及「中文引号」和"英文引号"的区别。

### 长代码行

```rust
// 这是一个很长的代码行，用于测试代码块的横向滚动和换行处理
fn very_long_function_name_that_should_test_the_rendering_engine_ability_to_handle_long_code_lines_without_breaking_the_layout(input: VeryLongTypeName) -> Result<AnotherLongTypeName, Error> {
    Ok(())
}
```

---

## 测试文件结束

> **最后更新**：2026-07-13  
> **用途**：NomadMark Markdown 渲染引擎测试
