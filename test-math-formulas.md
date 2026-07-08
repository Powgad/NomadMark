# 数学公式渲染测试

本文档用于测试 NomadMark 的数学公式渲染功能。

## 行内公式

爱因斯坦质能方程 $$E = mc^2$$ 是著名的物理公式。

勾股定理：$$a^2 + b^2 = c^2$$

圆的面积公式：$$A = \pi r^2$$

## 块级公式

一元二次方程求根公式：

$$
x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
$$

## 分数和根号

分数示例：

$$
\frac{a + b}{c - d}
$$

根号示例：

$$
\sqrt{x^2 + y^2}
$$

嵌套示例：

$$
\frac{\sqrt{a^2 + b^2}}{c}
$$

## 求和与积分

求和符号：

$$
\sum_{i=1}^{n} i = \frac{n(n+1)}{2}
$$

积分：

$$
\int_{0}^{\infty} e^{-x} dx = 1
$$

定积分：

$$
\int_{a}^{b} f(x) dx
$$

## 矩阵

矩阵示例：

$$
\begin{pmatrix}
1 & 2 & 3 \\
4 & 5 & 6 \\
7 & 8 & 9
\end{pmatrix}
$$

单位矩阵：

$$
I = \begin{bmatrix}
1 & 0 & 0 \\
0 & 1 & 0 \\
0 & 0 & 1
\end{bmatrix}
$$

## 极限

极限示例：

$$
\lim_{x \to \infty} \frac{1}{x} = 0
$$

导数定义：

$$
f'(x) = \lim_{h \to 0} \frac{f(x+h) - f(x)}{h}
$$

## 上标和下标

化学式：$$H_2O$$、$$CO_2$$

上标：$$x^{2n} + y^{2n}$$

组合：$$a_{i,j}^{n}$$

## 希腊字母

常用希腊字母：

- $$\alpha$$、$$\beta$$、$$\gamma$$、$$\delta$$
- $$\theta$$、$$\lambda$$、$$\mu$$、$$\sigma$$、$$\phi$$、$$\omega$$
- $$\Delta$$、$$\Sigma$$、$$\Pi$$、$$\Omega$$

## 复杂公式

正态分布概率密度函数：

$$
f(x) = \frac{1}{\sigma\sqrt{2\pi}} e^{-\frac{(x-\mu)^2}{2\sigma^2}}
$$

欧拉公式：

$$
e^{i\pi} + 1 = 0
$$

泰勒展开：

$$
e^x = \sum_{n=0}^{\infty} \frac{x^n}{n!} = 1 + x + \frac{x^2}{2!} + \frac{x^3}{3!} + \cdots
$$

## 混合内容测试

这是一段包含数学公式的段落。我们知道，对于任何实数 $$x$$，都有 $$e^{ix} = \cos x + i\sin x$$。当 $$x = \pi$$ 时，就得到了著名的欧拉恒等式 $$e^{i\pi} + 1 = 0$$。

---

**注意**：如果公式无法正确渲染，请检查：
1. JLatexMathPlugin 已正确添加到依赖
2. MarkwonInlineParserPlugin 已启用（用于行内公式）
3. 重新编译并安装应用
