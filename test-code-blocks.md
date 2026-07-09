# 代码块语法测试

本文档用于测试 NomadMark 的代码高亮功能。

## 行内代码

这是行内代码：`const x = 42;` 和 `` `echo "hello"` ``。

## Rust 代码

```rust
fn main() {
    let name = "NomadMark";
    println!("Hello, {}!", name);

    // 数学公式示例
    let formula = "E = mc^2";

    // 闭包
    let add = |a, b| a + b;
    let result = add(1, 2);
}
```

## Python 代码

```python
import numpy as np

def calculate_fibonacci(n):
    """计算斐波那契数列"""
    if n <= 1:
        return n
    return calculate_fibonacci(n-1) + calculate_fibonacci(n-2)

# 列表推导式
squares = [x**2 for x in range(10)]

# 数学运算
result = np.sqrt(16) + np.pi
```

## JavaScript/TypeScript

```javascript
function greet(name) {
    const message = `Hello, ${name}!`;
    console.log(message);
    return message;
}

// 箭头函数
const add = (a, b) => a + b;

// Promise
fetch('/api/data')
    .then(response => response.json())
    .then(data => console.log(data))
    .catch(error => console.error(error));
```

```typescript
interface User {
    id: number;
    name: string;
    email: string;
}

class UserService {
    async getUserById(id: number): Promise<User> {
        const response = await fetch(`/api/users/${id}`);
        return response.json();
    }
}
```

## Java 代码

```java
public class Calculator {
    private int result;

    public Calculator() {
        this.result = 0;
    }

    public int add(int a, int b) {
        this.result = a + b;
        return this.result;
    }

    public static void main(String[] args) {
        Calculator calc = new Calculator();
        System.out.println(calc.add(5, 3));
    }
}
```

## C/C++ 代码

```c
#include <stdio.h>
#include <stdlib.h>

int main() {
    int *arr = (int*)malloc(5 * sizeof(int));
    
    for (int i = 0; i < 5; i++) {
        arr[i] = i * i;
    }

    printf("First element: %d\n", arr[0]);
    free(arr);
    return 0;
}
```

```cpp
#include <iostream>
#include <vector>
#include <algorithm>

template<typename T>
T max(T a, T b) {
    return (a > b) ? a : b;
}

int main() {
    std::vector<int> nums = {1, 2, 3, 4, 5};
    std::sort(nums.begin(), nums.end());
    
    for (int n : nums) {
        std::cout << n << " ";
    }
    return 0;
}
```

## Go 代码

```go
package main

import "fmt"

func fibonacci(n int) int {
    if n <= 1 {
        return n
    }
    return fibonacci(n-1) + fibonacci(n-2)
}

func main() {
    for i := 0; i < 10; i++ {
        fmt.Printf("fib(%d) = %d\n", i, fibonacci(i))
    }
}
```

## Shell/Bash 脚本

```bash
#!/bin/bash

# 变量定义
NAME="NomadMark"
VERSION="1.0.0"

# 函数
greet() {
    echo "Hello, $1!"
}

# 循环
for file in *.md; do
    echo "Processing: $file"
done

# 条件判断
if [ -f "README.md" ]; then
    cat README.md
fi
```

## SQL 代码

```sql
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

SELECT u.username, COUNT(o.id) as order_count
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE u.created_at > '2024-01-01'
GROUP BY u.username
HAVING COUNT(o.id) > 5
ORDER BY order_count DESC;
```

## HTML/CSS

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>NomadMark</title>
    <style>
        .container {
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
        }
        h1 {
            color: #333;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>Welcome to NomadMark</h1>
        <p>A Markdown editor for E-ink devices.</p>
    </div>
</body>
</html>
```

```css
/* 墨水屏友好的配色 */
:root {
    --bg-color: #FFFFFF;
    --text-color: #000000;
    --code-bg: #F5F5F5;
    --border-color: #E0E0E0;
}

body {
    background-color: var(--bg-color);
    color: var(--text-color);
    font-size: 14pt;
    line-height: 1.6;
}

code {
    background-color: var(--code-bg);
    padding: 2px 6px;
    border-radius: 3px;
}
```

## JSON/YAML

```json
{
    "name": "nomadmark",
    "version": "1.0.0",
    "description": "A Markdown editor for E-ink devices",
    "repository": {
        "type": "git",
        "url": "https://github.com/Powgad/NomadMark.git"
    },
    "keywords": ["markdown", "editor", "eink", "supernote"],
    "platforms": ["android", "desktop"]
}
```

```yaml
name: NomadMark
version: 1.0.0
description: Markdown editor for E-ink devices

author:
  name: NomadMark Team
  email: team@nomadmark.com

platforms:
  - name: Android
    minSdk: 26
    targetSdk: 34
  - name: Desktop
    framework: Tauri

features:
  - markdown rendering
  - syntax highlighting
  - math formulas
  - gesture editing
```

## 其他语言

### Ruby
```ruby
class Greeter
  def initialize(name)
    @name = name
  end

  def greet
    puts "Hello, #{@name}!"
  end
end

greeter = Greeter.new("World")
greeter.greet
```

### PHP
```php
<?php
function calculateTax($amount, $rate) {
    return $amount * ($rate / 100);
}

$price = 100;
$taxRate = 0.08;
$total = $price + calculateTax($price, $taxRate);

echo "Total: $" . number_format($total, 2);
?>
```

### Swift
```swift
struct Person {
    let name: String
    let age: Int
    
    func greet() -> String {
        return "Hello, I'm \(name), \(age) years old."
    }
}

let person = Person(name: "Alice", age: 30)
print(person.greet())
```

## 无语言标识的代码块

```
这是没有指定语言的代码块
应该以纯文本形式显示
没有语法高亮
```

## 边缘情况测试

### 空代码块
```rust
// 空内容
```

### 只有注释的代码块
```python
# 这是一个只有注释的代码块
# TODO: 添加实现
```

### 包含特殊字符的代码
```bash
echo "Test: \n \t $VAR @ # % ^ & *"
regex_pattern = '[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}'
```

### 嵌套代码块
```markdown
这是如何在 Markdown 中写代码块：

```rust
fn main() {
    println!("Hello!");
}
```

注意缩进和转义。
```

## 混合内容测试

这是一个段落，其中包含 `行内代码`，下面是一个代码块：

```go
func main() {
    fmt.Println("Hello, World!")
}
```

继续正文内容...

---

**测试说明**：
- 检查各种语言的语法高亮是否正确
- 验证代码块的背景色和边框
- 确认墨水屏显示效果（高对比度）
- 测试长代码块的滚动显示
