# 乐谱渲染测试文档

本文档用于测试 NomadMark 的乐谱渲染功能。

## ABC 记谱法测试

### 基础 ABC 乐谱

```music
X:1
T:小星星
C:传统民谣
Q:120
K:C
C C G G | A A G2 |
F F E E | D D C2 |
```

### 带音频的 ABC 乐谱

```music
title: "生日快乐歌"
composer: "Patty Hill, Mildred J. Hill"
tempo: 100
key: C Major
audio: "music_sheets/happy_birthday.mp3"

X:2
T:Happy Birthday
C:Patty Hill, Mildred J. Hill
Q:100
K:C
CCDCFE | CCDCGF |
FFGGC2 | FFCED2 |
```

## 简谱测试

### 基础简谱

```简谱
1=C 4/4
5 5 6 6 | 5 4 3 2 | 1 1 3 3 | 2 2 1 -
```

### 带元数据的简谱

```简谱
title: "茉莉花"
composer: "传统民歌"
tempo: 80
key: F Major

1=F 4/4
3 5 6 5 | 1 6 5 3 | 5 6 5 3 | 2 3 2 1 |
```

## 混合内容测试

### 普通代码块（不应被识别为乐谱）

```rust
fn main() {
    println!("Hello, World!");
}
```

```python
def hello():
    print("Hello, World!")
```

### 不支持的语言标识符

```unknown
This is an unknown language block
and should NOT be rendered as music.
```

## 边缘情况测试

### 空乐谱块

```music

```

### 只有元数据的乐谱块

```music
title: "空乐谱"
composer: "未知"
```

### 多行 ABC 乐谱

```music
X:1
T:Twinkle Twinkle Little Star
C:Traditional
Q:1/4=140
K:C
C|C3 G3 G3|A3 A3 G2|F3 F3 E3 E3|D3 D3 C2|
G|G3 F3 F3|E3 E3 D2|G3 G3 F3 F3|E3 E3 D2|
C|C3 G3 G3|A3 A3 G2|F3 F3 E3 E3|D3 D3 C2|]
```

### 中文歌名测试

```music
title: "月亮代表我的心"
composer: "翁清溪"
tempo: 90
key: C Major

X:1
T:月亮代表我的心
M:4/4
L:1/8
K:C
E6 G6 A6 G6 | E6 C6 D4 C4 |
```

## 测试说明

**预期效果：**

1. **ABC 记谱法块** 应显示为：
   - 浅灰色背景
   - 深色边框
   - 显示 "ABC 记谱法" 标签
   - 显示标题（如果有）
   - 显示内容预览（前几行）
   - 如果有音频，显示播放按钮

2. **简谱块** 应显示为：
   - 浅灰色背景
   - 深色边框
   - 显示 "简谱" 标签
   - 显示标题（如果有）
   - 显示内容预览

3. **普通代码块** 应保持原样，不被识别为乐谱

**验证步骤：**

1. 在编辑器中输入上述测试内容
2. 切换到预览模式
3. 检查乐谱块的渲染效果
4. 检查普通代码块不受影响
5. （如果有音频）点击播放按钮，验证播放功能

---

## 语法参考

### ABC 记谱法

```music
X:编号           # 乐曲编号
T:标题           # 乐曲标题
C:作曲者         # 作曲者
Q:速度           # 速度（BPM 或 Q:1/4=120）
K:调性           # 调性（K:C, K:Am, K:D 等）
L:音符长度       # 默认音符长度（L:1/8, L:1/4）
M:拍号           # 拍号（M:4/4, M:3/4）

音符格式：
C D E F G A B    # C 大调音阶
c d e f g a b    # 高八度
C' D' E'        # 更高八度
C, D, E,        # 低八度

修饰符：
2 4 8           # 音符时值（2分音符、4分音符、8分音符）
-               # 连音符
|               | 小节线
|:              :| 重复开始和结束
```

### 简谱

```简谱
1=C 或 1=F      # 调性（1=C 表示 C 大调）
4/4 或 2/4     # 拍号

数字表示音高：
1 2 3 4 5 6 7  # do re mi fa sol la si
•               # 升半音
b               # 降半音

修饰符：
-               # 减时线
_               | 增时线
|               | 小节线
:               | 重复记号
```
