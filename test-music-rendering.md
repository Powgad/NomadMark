# 乐谱渲染测试文件

这个文件用于测试 NomadMark 的乐谱渲染功能。

---

## 示例 1: 简单的旋律

一个简单的 C 大调旋律：

```music
X: 1
T: Simple Melody
M: 4/4
L: 1/4
K: C
C D E F | G A B c | c B A G | F E D C
```

---

## 示例 2: 爱尔兰传统乐曲

Cooley's - 爱尔兰传统 reels：

```music
X: 1
T: Cooley's
M: 4/4
L: 1/8
K: Emin
|:D2|"Em"EBBA B2 EB|B2 AB dBAG|"D"FDAD BDAD|FDAD dAFD:|
|:d2|"Am"AcAc BGBA|"Em"BEBE dBGB|"Am"AcAc BGBA|"D"FDAD D2 FD:|
```

---

## 示例 3: 苏格兰民歌

苏格兰民歌 "Skye Boat Song"：

```music
X: 1
T: Skye Boat Song
M: 3/4
L: 1/8
K: G
G2 D G2 E | D4 B,2 | C2 D E2 G | F4 D2 |
G2 D G2 E | D4 B,2 | C2 A,2 G,2 | G,4 :|
```

---

## 示例 4: 民谣音阶

使用五声音阶的简单曲调：

```music
X: 1
T: Pentatonic Tune
M: 2/4
L: 1/8
K: D
A2 F2 D2 F2 | A4 d2 | c2 A2 F2 A2 | F4 :|
```

---

## 示例 5: 带和弦的曲子

```music
X: 1
T: With Chords
M: 4/4
L: 1/4
K: C
"C"E G "G"G B | "F"A C "C"E G | "G"D G "C"C E | "G"G4 :|
```

---

## 示例 6: 美国传统民歌

Oh! Susanna - 美国传统民歌：

```music
X: 1
T: Oh! Susanna
C: Stephen Foster
M: 2/4
L: 1/8
K: F
|: c2 c2 | A2 A2 | c2 c2 | G4 | g2 g2 | e2 e2 | d2 d2 | c4 :|
|: f2 f2 | d2 d2 | f2 f2 | c4 | e2 e2 | c2 c2 | d2 d2 | c4 :|
```

---

## 示例 7: 小星星

一闪一闪小星星：

```music
X: 1
T: Twinkle Twinkle Little Star
M: 4/4
L: 1/4
K: C
C C G G | A A G2 | F F E E | D D C2 |
G G F F | E E D2 | G G F F | E E D2 |
C C G G | A A G2 | F F E E | D D C2 |]
```

---

## 示例 8: 多声部

```music
X: 1
T: Two Part Harmony
M: 4/4
L: 1/4
K: G
V: 1 name="Treble"
V: 2 name="Bass"
% 1
[V: 1] B2 d2 g2 | g4 d2 | B2 d2 g2 | g4 :|
[V: 2] G2 G2 D2 | G4 G2 | G2 G2 D2 | G4 :|
```

---

## 示例 9: 装饰音

```music
X: 1
T: With Ornaments
M: 6/8
L: 1/8
K: D
{A}Bcd {g}f2e | {d}cBA {G}F2D | {A}Bcd {g}f2e | d4 :|
```

---

## 示例 10: 完整的乐曲结构

```music
X: 101
T: The Gospel Train
C: Traditional
M: 2/4
L: 1/8
K: Ador
P:A A B B D
A: e2 ec | dc cB | A2 AG | E4 |
A: e2 ec | dc cB | AG EG | D4 :|
B: a2 ag | fd dc | Bc dB | AG EG |
B: A2 AG | E2 EG | A2 AG | F4 :|
D: "D"A2 A2 | "A"A2 AB | "D"c2 cB | "A"A4 :|
```

---

## 示例 11: 简谱 - 小星星

```简谱
1=C 4/4
1 1 5 5 | 6 6 5 - | 4 4 3 3 | 2 2 1 -
```

---

## 示例 12: 简谱 - 两只老虎

```简谱
1=C 2/4
1 2 3 1 | 1 2 3 1 | 3 4 5 - | 3 4 5 -
```

---

## 示例 13: 简谱 - 生日快乐

```简谱
1=F 3/4
5 5 6 5 | 1 7 - | 5 5 6 5 | 2 1 -
```

---

## 如何测试

1. 将此文件保存为 `.md` 文件
2. 在 NomadMark 中打开
3. 切换到预览模式或分屏模式
4. 应该能看到乐谱被渲染为可视化图片

---

## 支持的 ABC 记谱法功能

- ✅ 调号 (K: Key)
- ✅ 拍号 (M: Meter)
- ✅ 音符时值 (L: Length)
- ✅ 重复记号 (:| 和 |:)
- ✅ 和弦 ("Chord"音符)
- ✅ 装饰音 ({note})
- ✅ 多声部 (V: Voice)
- ✅ 标题 (T: Title)
- ✅ 作曲者 (C: Composer)

## 支持的简谱功能

- ✅ 调号 (1=C, 1=D, 1=Bb 等)
- ✅ 拍号 (4/4, 3/4, 6/8 等)
- ✅ 基本音符 (1 2 3 4 5 6 7)
- ✅ 音符延长 (5- 表示二分音符)
- ✅ 小节线 (|)
- ✅ 休止符 (0)

---

**文档创建日期**: 2026-07-24
**测试版本**: NomadMark v1.0.0+
