# 链接语法测试

本文档用于测试 NomadMark 的链接渲染功能。

## 基础链接

### 行内链接

这是 [Markdown 官方文档](https://markdown.github.io/) 的链接。

访问 [GitHub](https://github.com) 查看更多信息。

### 带 Title 的链接

[GitHub](https://github.com "Git Hub") 是一个代码托管平台。

[NomadMark](https://github.com/Powgad/NomadMark "NomadMark 项目") 是一个 Markdown 编辑器。

## URL 链接

直接使用 URL：https://www.google.com

带尖括号的 URL：<https://www.google.com>

## 图片链接

### 基础图片

![NomadMark Logo](https://example.com/logo.png)

### 带 Title 的图片

![风景图](https://example.com/image.jpg "美丽的风景")

### 带尺寸的图片（HTML）

<img src="https://example.com/image.jpg" width="200" height="150">

## 参考式链接

### 定义参考链接

这是 [GitHub][1] 的链接，也是 [Stack Overflow][2] 的链接。

[1]: https://github.com
[2]: https://stackoverflow.com

### 简写参考链接

这是 [GitHub][] 的链接。

[GitHub]: https://github.com

### 带 Title 的参考链接

[Markdown][md]

[md]: https://markdown.github.io/ "Markdown 官网"

## 自动链接

### URL 自动链接

https://www.example.com

https://github.com/Powgad/NomadMark

### 邮箱自动链接

<user@example.com>

<admin@nomadmark.com>

<support+help@service.com>

## 图片参考式链接

![Logo][logo]

[logo]: https://example.com/logo.png "NomadMark Logo"

## 锚点链接（页内跳转）

### 跳转到标题

跳转到 [基础链接](#基础链接)

跳转到 [图片链接](#图片链接)

### 自定义锚点（如果支持）

[回到顶部](#top)

## 链接中的格式化

### 链接中的粗体

[**粗体链接文本**](https://example.com)

### 链接中的斜体

[*斜体链接文本*](https://example.com)

### 链接中的代码

[`printf()` 函数](https://example.com)

## 嵌套链接

点击 [这个链接](https://example.com) 中的 **[子链接](https://example.com/page)** 。

## 链接与文本混合

访问 [NomadMark 项目](https://github.com/Powgad/NomadMark) 获取更多信息。

这是一个包含 `代码` 和 [链接](https://example.com) 的段落。

## 相对路径链接

访问 [项目首页](../index.md)

查看 [API 文档](./docs/api.md)

打开 [图片资源](./assets/image.png)

## 协议相对链接

访问 [jQuery](//jquery.com)

## 无效链接测试

### 空链接

[空的链接]()

### 缺少 URL 的链接

[缺少 URL]

### 格式错误的链接

[格式错误](

## 特殊字符链接

### 带查询参数

[搜索结果](https://www.google.com/search?q=markdown&hl=zh-CN)

### 带锚点

[文档片段](https://example.com/docs#section1)

### 带端口号

[本地服务](http://localhost:8080)

## 中文链接

访问 [百度](https://www.baidu.com)

查看 [中文文档](https://example.com/zh-CN/docs)

## 链接中的转义

链接中包含特殊字符：[测试链接](https://example.com/path?param=value&other=123)

## 邮件链接（非自动链接）

发送邮件到 [联系我们](mailto:contact@example.com)

[技术支持](mailto:support@example.com?subject=NomadMark)

## 电话链接（如果支持）

[客服热线](tel:400-123-4567)

## 文件下载链接

下载 [PDF 文档](https://example.com/document.pdf)

下载 [安装包](https://example.com/app.apk)

## 视频链接

观看 [演示视频](https://www.youtube.com/watch?v=dQw4w9WgXcQ)

## 链接列表

### 有用链接

- [Markdown 官网](https://markdown.github.io/)
- [GitHub](https://github.com)
- [Stack Overflow](https://stackoverflow.com)
- [NomadMark](https://github.com/Powgad/NomadMark)

### 资源链接

1. [Rust 官网](https://www.rust-lang.org/)
2. [Kotlin 官网](https://kotlinlang.org/)
3. [Android 开发者](https://developer.android.com/)

## 引用中的链接

> 推荐阅读：
>
> [The Markdown Guide](https://www.markdownguide.org/)
>
> [CommonMark Spec](https://spec.commonmark.org/)

## 代码块中的链接

```markdown
这是代码中的链接：[示例](https://example.com)
```

## 图片中的链接（图片链接）

点击图片跳转：

[![点击这里](https://example.com/button.jpg)](https://example.com/target)

## 长链接测试

[这是一个非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常非常长的链接文本](https://www.example.com/very/long/path/to/some/resource?param1=value1&param2=value2&param3=value3)

## 链接目标属性（HTML）

在**新窗口**打开：<a href="https://example.com" target="_blank">新窗口打开</a>

## 国际化链接

- 中文：[百度百科](https://baike.baidu.com)
- 日文：[ウィキペディア](https://ja.wikipedia.org)
- 韩文：[위키백과](https://ko.wikipedia.org)

## 社交媒体链接

- Twitter: [@username](https://twitter.com/username)
- GitHub: [@username](https://github.com/username)
- Weibo: [@username](https://weibo.com/u/username)

## 边缘情况

### 连续多个链接

[链接1](https://example1.com) [链接2](https://example2.com) [链接3](https://example3.com)

### 链接中的空格

[带 空格 的链接](https://example.com)

### 链接中的换行

[多行
链接文本](https://example.com)

---

## 测试说明

请检查以下内容：

1. ✓ 链接颜色是否合适（墨水屏友好）
2. ✓ 链接下划线是否清晰可见
3. ✓ 点击链接是否能正确跳转
4. ✓ 图片链接是否能正确显示和跳转
5. ✓ 长链接文本的显示是否正常
6. ✓ 链接在各种元素（列表、引用、表格）中的显示
7. ✓ 邮件和电话链接是否能正确识别
8. ✓ 相对路径链接是否能正确解析
9. ✓ 特殊字符（查询参数、锚点）是否正确处理
10. ✓ 中外文链接混合显示是否正常
