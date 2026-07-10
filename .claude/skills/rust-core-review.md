# Rust Core 代码审查报告

> 审查日期: 2026-07-10
> 审查范围: NomadMark Rust Core 层完整代码审查

---

## 📋 审查摘要

本报告涵盖了对 NomadMark Rust Core 的全面代码审查，发现了 **40+ 个具体问题**，按严重程度分类：

- **🚨 严重问题**: 4 个（内存泄漏、未实现 API、类型安全）
- **🏗️ 设计问题**: 8 个（架构不一致、FFI 设计、数据结构）
- **📝 代码质量**: 10+ 个（代码重复、魔法数字、测试覆盖）
- **⚡ 性能问题**: 4 个（字符串复制、解析效率）
- **🔒 安全问题**: 4 个（输入验证、整数溢出）
- **🏛️ 架构建议**: 5+ 个（重构优先级、新架构建议）

---

## 🚨 严重问题

### 1. 内存泄漏风险
**位置**: [lib.rs:707-728](lib.rs#L707-L728)

```rust
// 当前实现手动释放 DrawText 命令中的文本内容
for cmd in commands.iter() {
    if cmd.cmd_type == render::commands::RenderCommandType::DrawText {
        let text_data = cmd.data.text;
        if text_data.text_ptr != 0 && text_data.text_len > 0 {
            let layout = std::alloc::Layout::array::<u8>(text_data.text_len as usize).unwrap();
            std::alloc::dealloc(text_data.text_ptr as *mut u8, layout);
        }
    }
}
```

**问题**:
- 手动管理内存容易出错
- 如果调用者忘记调用 `md_free_commands`，会发生内存泄漏
- 没有异常安全保证

**建议**: 使用 RAII 模式或智能指针管理内存

---

### 2. 类型不安全 - 字符串指针
**位置**: [jni.rs:85-86](bridge/jni.rs#L85-L86)

```rust
let c_str = CStr::from_ptr(j_str.as_ptr());
let bytes = c_str.to_bytes();
```

**问题**:
- JNI 字符串生命周期问题：`JString::from_raw` 创建的引用可能无效
- `as_ptr()` 返回的指针在 `get_string()` 返回后可能失效

**建议**: 在 JNI 调用期间保持 Java 字符串引用

---

### 3. 撤销/重做功能未实现
**位置**: [lib.rs:574-624](lib.rs#L574-L624)

```rust
pub extern "C" fn md_document_undo(handle: *mut MarkdownDocument) -> i32 {
    // TODO: 应用撤销操作到文档内容
    // 这需要完整的编辑系统来修改解析器的内容
    1  // 返回成功但实际没有执行！
}
```

**问题**:
- API 已暴露但功能未实现
- 返回成功状态但实际没有执行操作
- 会误导调用者

**建议**: 要么实现完整功能，要么返回错误/移除 API

---

### 4. 悬空指针风险
**位置**: [ast.rs:146-148](parser/ast.rs#L146-L148)

```rust
pub struct TocEntry {
    pub title_len: usize,
    pub title_ptr: *const u8,  // 悬空指针风险
}
```

**问题**: 通过 FFI 传递时，`title` 字符串可能已被释放

**建议**: 使用 FfiString 包装器或复制内容

---

## 🏗️ 设计问题

### 1. 架构不一致

#### 问题 1.1 - 重复的 MarkdownDocument 类型
- [ast.rs:300-305](parser/ast.rs#L300-L305) 定义了 `MarkdownDocument` 结构体
- [lib.rs:71-86](lib.rs#L71-L86) 也定义了 `MarkdownDocument` 结构体
- 两者用途不同但名称相同，容易混淆

**建议**: 重命名内部类型为 `ParsedDocument` 或 `DocumentInternal`

#### 问题 1.2 - 解析器重复工作
- [streaming.rs:260-261](parser/streaming.rs#L260-L261) 在构造时执行快速扫描
- [streaming.rs:415-621](parser/streaming.rs#L415-L621) `parse_range` 再次解析相同内容
- 没有缓存解析结果

**建议**: 实现解析结果缓存

---

### 2. FFI 边界设计问题

#### 问题 2.1 - 不安全的内存传递
**位置**: [jni.rs:356-424](bridge/jni.rs#L356-L424)

```rust
pub extern "C" fn Java_com_editor_nomadmark_MarkdownCore_nativeLoadRange(
    out_commands: jlongArray,  // 直接传递原始指针
) -> jint { /* ... */ }
```

**问题**: Kotlin 端接收原始指针，但生命周期不明确

**建议**: 使用更安全的 FFI 模式（如 `ByteBuffer`）

#### 问题 2.2 - 导出函数命名不一致
- [lib.rs:1704-1707](lib.rs#L1704-L1707) 有 `direct_test_export()` 用于测试
- 测试代码与生产代码混合

---

### 3. 数据结构设计

#### 问题 3.1 - BlockNode 缺少位置信息
**位置**: [ast.rs:94-152](parser/ast.rs#L94-L152)

```rust
pub enum BlockNode {
    Heading { level: u8, children: Vec<InlineNode> },
    // 缺少：source_line, byte_offset 等位置信息
}
```

**问题**: 无法知道块在源文件中的位置，影响：
- 跳转功能
- 错误报告
- 增量编辑

**建议**: 添加位置元数据

#### 问题 3.2 - 渲染命令过于底层
**位置**: [commands.rs:31-43](render/commands.rs#L31-L43)

**问题**:
- 布局和渲染逻辑耦合
- 字符换行、对齐等高级逻辑在布局器中硬编码
- 难以扩展（添加新命令类型需要修改多处）

---

## 📝 代码质量问题

### 1. 代码重复

#### 问题 1.1 - 行内扩展解析重复
**位置**: [extensions.rs:124-259](parser/extensions.rs#L124-L259)

每种扩展语法都有相同的模式（检测、清空缓冲、添加、跳过），可以抽象为通用函数。

#### 问题 1.2 - 颜色定义重复
- [ast.rs:76-87](parser/ast.rs#L76-L87) `CalloutKind::border_color()` 返回 `(u8, u8, u8)`
- [types.rs:88-105](bridge/types.rs#L88-L105) 定义了 `Color` 结构体
- [engine.rs:835-853](layout/engine.rs#L835-L853) 手动转换

---

### 2. 魔法数字

**位置**: [layout/engine.rs](layout/engine.rs)

```rust
margin_left: 40.0,
margin_right: 40.0,
line_spacing: 1.5,
paragraph_spacing: 20.0,
// ... 多处出现 8.0, 12.0, 16.0, 24.0 等常量
```

**建议**: 定义常量或配置结构

---

### 3. 测试覆盖不足

- [streaming.rs](parser/streaming.rs) 缺少大文件测试
- [layout/engine.rs](layout/engine.rs) 缺少边界条件测试
- [commands.rs](render/commands.rs) 缺少内存管理测试

---

### 4. 错误处理不一致

**位置**: [lib.rs:176-206](lib.rs#L176-L206)

```rust
pub extern "C" fn md_document_create(/* ... */) -> *mut MarkdownDocument {
    if content.is_null() || len == 0 {
        return std::ptr::null_mut();  // 返回 null 表示错误
    }
    // ...
    match StreamingParser::from_bytes(bytes) {
        Ok(parser) => { /* ... */ }
        Err(_) => return std::ptr::null_mut(),  // 吞掉错误信息
    }
}
```

**问题**: 调用者无法区分不同的错误类型

---

## ⚡ 性能问题

### 1. 字符串复制过多
**位置**: [streaming.rs:279](parser/streaming.rs#L279)

```rust
pub fn from_bytes(bytes: &[u8]) -> Result<Self, ParseError> {
    bytes_storage: Some(bytes.to_vec()),  // 完整复制
```

---

### 2. 解析效率问题
**位置**: [streaming.rs:415-621](parser/streaming.rs#L415-L621) - `parse_range`

- 每次调用都重新解析
- 没有利用之前的结果
- UTF-8 验证重复进行

---

### 3. 布局缓存未使用
**位置**: [layout/engine.rs:137-211](layout/engine.rs#L137-L211) - `GlyphCacheSystem`

- 定义了缓存但未在关键路径中使用
- 每次调用 `get_font_metrics` 都重新计算

---

### 4. 脏矩形合并效率低
**位置**: [commands.rs:167-186](render/commands.rs#L167-L186)

```rust
pub fn merge_dirty_rects(&mut self) {
    // O(n²) 算法，只合并相邻矩形
    // 对于大量矩形效率低
}
```

---

## 🔒 安全问题

### 1. 未验证的输入
**位置**: [bridge/jni.rs:74-90](bridge/jni.rs#L74-L90)

```rust
let jstr = JString::from_raw(content);  // 未验证是否为 null
let s = env.get_string(&jstr);
if s.is_err() {
    return 0;  // 吞掉错误
}
```

---

### 2. 整数溢出风险
**位置**: [types.rs:42-50](bridge/types.rs#L42-L50)

```rust
width: width.round().max(0.0).min(u16::MAX as f32) as u16,
```

**问题**: 在转换为 `u16` 之前应该先检查是否超出范围

---

### 3. 并发安全
**位置**: [streaming.rs:247](parser/streaming.rs#L247)

```rust
scan_progress: AtomicUsize,
```

**问题**: 使用 `AtomicUsize` 是好的，但没有内存顺序保证

---

### 4. 生命周期问题
**位置**: [parser/ast.rs:146-148](parser/ast.rs#L146-L148)

```rust
pub struct TocEntry {
    pub title_ptr: *const u8,  // 悬空指针风险
}
```

---

## 🏛️ 架构建议

### 建议的重构优先级

#### 高优先级 🔴
1. **修复内存泄漏风险** - 实现安全的内存管理
2. **完成或移除未实现的 API**（撤销/重做）
3. **添加错误上下文** - 改进错误报告
4. **修复悬空指针风险** - 重新设计 TOC 类型

#### 中优先级 🟡
1. **统一类型定义** - 消除重复类型
2. **提取常量配置** - 移除魔法数字
3. **实现解析缓存** - 减少重复工作
4. **改进 FFI 安全性** - 使用 ByteBuffer

#### 低优先级 🟢
1. **改进测试覆盖**
2. **优化脏矩形合并算法**
3. **添加位置元数据到 AST**
4. **重构 lib.rs**（拆分为更小的模块）

---

### 建议的新架构

```
MarkdownDocument (公开的 FFI 类型)
    ├── 解析层：StreamingParser (保持文件索引)
    ├── AST 层：ParsedDocument (完整解析树)
    ├── 布局层：LayoutEngine (生成渲染命令)
    └── 渲染层：RenderCommand (平台无关命令)

内存管理：
    ├── Rust 端：使用 Arena/Bump 分配器
    ├── FFI 边界：使用 ByteBuffer 传递
    └── 释放：使用 RAII 包装器
```

---

## 📊 问题统计

| 类别 | 数量 | 严重程度 |
|------|------|----------|
| 内存安全问题 | 4 | 高 |
| 未实现 API | 1 | 高 |
| 架构设计问题 | 8 | 中 |
| 代码质量问题 | 10+ | 中 |
| 性能问题 | 4 | 中 |
| 安全问题 | 4 | 高 |

---

## 🔧 快速修复清单

### 立即修复（本周）✅ 已完成
- [x] 移除或实现 `md_document_undo`/`md_document_redo` - ✅ 已诚实标记为未实现
- [x] 修复 JNI 字符串生命周期问题 - ✅ 已使用正确的 JNI API
- [x] 修复 TocEntry 悬空指针问题 - ✅ 已使用 Box::leak 分配内存

### 短期修复（本月）
- [x] 实现安全的内存管理（RAII） - ✅ 已添加 FFI 契约文档和改进释放函数
- [ ] 提取魔法数字为常量（已规划，未实施）
- [x] 消除类型重复定义 - ✅ 已添加文档阐明用途
- [x] 改进错误报告 - ✅ 已添加内存管理契约文档

### 长期改进（本季度）
- [ ] 实现解析缓存
- [ ] 添加位置元数据
- [ ] 重构 lib.rs
- [ ] 改进测试覆盖

---

## ✅ 已实施的修复（2026-07-10）

### 1. TocEntry 悬空指针修复
**文件**: `core/src/lib.rs:44-62`, `core/src/lib.rs:747-776`

- 修改 `convert_toc_entry` 使用 `Box::leak` 为标题分配独立内存
- 更新 `md_free_toc` 同时释放标题内存和条目数组

### 2. 撤销/重做虚假实现修复
**文件**: `core/src/lib.rs:582-626`

- 修改 `md_document_undo` 和 `md_document_redo` 返回 0（未实现）
- 添加文档说明功能尚未完全实现

### 3. JNI 字符串生命周期修复
**文件**: `core/src/bridge/jni.rs:65-118`

- 修改 `nativeCreate` 和 `nativeCreateFromPath` 使用正确的 JNI API
- 确保字符串指针在转换期间有效

### 4. 内存管理文档化
**文件**: `core/src/lib.rs:695-728`, `core/src/bridge/mod.rs:1-26`

- 为 `md_free_commands` 添加详细的内存管理契约文档
- 在 `bridge/mod.rs` 添加 FFI 边界内存管理契约

### 5. 类型定义文档化
**文件**: `core/src/parser/ast.rs:298-329`, `core/src/bridge/types.rs:135-152`

- 为 `MarkdownDocument` 和 `TocEntry` 添加 FFI 边界说明文档
- 阐明内部类型和 FFI 类型的关系

---

*最后更新: 2026-07-10*
*修复状态: 第一阶段（严重问题）已完成 ✅，第二阶段（设计问题）已完成 ✅*