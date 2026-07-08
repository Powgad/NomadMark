# Markwon 依赖分析报告

**分析日期**: 2026-07-08
**分析范围**: Android 端 Markwon 使用情况
**目标**: 评估移除 Markwon 的可行性

---

## 一、当前依赖状态

### 1.1 Gradle 依赖

**文件**: `platforms/android/app/build.gradle`

```gradle
dependencies {
    // Markwon - Markdown rendering engine with full feature support
    def markwonVersion = '4.6.2'
    implementation "io.noties.markwon:core:$markwonVersion"
    implementation "io.noties.markwon:ext-strikethrough:$markwonVersion"
    implementation "io.noties.markwon:ext-tables:$markwonVersion"
    implementation "io.noties.markwon:ext-tasklist:$markwonVersion"
    implementation "io.noties.markwon:image:$markwonVersion"
    implementation "io.noties.markwon:inline-parser:$markwonVersion"
    implementation "io.noties.markwon:ext-latex:$markwonVersion"
}
```

**依赖模块**:
- `core` - 核心渲染功能
- `ext-strikethrough` - 删除线支持
- `ext-tables` - 表格支持
- `ext-tasklist` - 任务列表支持
- `image` - 图片支持
- `inline-parser` - 行内解析器
- `ext-latex` - 数学公式支持

---

## 二、使用情况分析

### 2.1 主要使用位置

**文件**: `MarkdownEditorActivity.kt`

| 代码位置 | 功能 | 说明 |
|---------|------|------|
| 第 27-37 行 | 导入语句 | 导入所有 Markwon 相关类 |
| 第 162-165 行 | 变量声明 | `markwon: Markwon` 实例 |
| 第 217-218 行 | 初始化 | `initMarkwon()` |
| 第 260-302 行 | 配置 | Markwon 插件和主题配置 |
| 第 1522-1532 行 | 渲染 | `markwon.setMarkdown()` 预览渲染 |

### 2.2 核心功能

**Markwon 当前负责**:
- ✅ 预览模式渲染
- ✅ 分屏模式渲染
- ✅ 数学公式显示
- ✅ 表格渲染
- ✅ 任务列表渲染
- ✅ 删除线渲染
- ✅ 图片支持
- ✅ 链接处理

---

## 三、Rust Core 覆盖情况

### 3.1 已实现功能

| 功能 | Rust Core 状态 | Markwon 状态 |
|------|---------------|--------------|
| 标题 (H1-H6) | ✅ 已实现 | ✅ 支持 |
| 粗体/斜体 | ✅ 已实现 | ✅ 支持 |
| 删除线 | ✅ 已实现 | ✅ 支持 |
| 代码块 | ✅ 已实现 | ✅ 支持 |
| 行内代码 | ✅ 已实现 | ✅ 支持 |
| 链接 | ✅ 已实现 | ✅ 支持 |
| 图片 | ✅ 已实现 | ✅ 支持 |
| 无序列表 | ✅ 已实现 | ✅ 支持 |
| 有序列表 | ✅ 已实现 | ✅ 支持 |
| 任务列表 | ✅ 已实现 | ✅ 支持 |
| 表格 | ✅ 已实现 | ✅ 支持 |
| 引用块 | ✅ 已实现 | ✅ 支持 |
| 分割线 | ✅ 已实现 | ✅ 支持 |
| 数学公式 | ✅ 已实现 | ✅ 支持 |
| 代码高亮 | ✅ 已实现 | ❌ 不支持 |
| Callout | ✅ 已实现 | ❌ 不支持 |
| 下划线/高亮 | ✅ 已实现 | ❌ 不支持 |
| 目录 (TOC) | ✅ 已实现 | ❌ 不支持 |

### 3.2 功能对比

**Rust Core 优势**:
- ✅ 跨平台支持
- ✅ 更好的性能
- ✅ 统一的渲染逻辑
- ✅ E-ink 优化

**Markwon 优势**:
- ✅ 成熟稳定
- ✅ 丰富的插件生态
- ✅ Android 原生集成

---

## 四、迁移方案

### 4.1 迁移条件

| 条件 | 状态 | 说明 |
|------|------|------|
| 核心渲染功能 | ✅ 满足 | 所有基础功能已实现 |
| 数学公式支持 | ✅ 满足 | KaTeX 集成完成 |
| 性能要求 | ✅ 满足 | Rust 性能优于 Java |
| FFI 接口 | ✅ 满足 | JNI 接口已完成 |
| 渲染命令 | ✅ 满足 | RenderCommand 已定义 |

### 4.2 迁移步骤

#### 第一阶段：双引擎并存
- [ ] 保留 Markwon 作为备用
- [ ] 添加功能开关
- [ ] 对比测试渲染效果

#### 第二阶段：逐步迁移
- [ ] 使用 Rust Core 替换预览渲染
- [ ] 保留 Markwon 处理特殊情况
- [ ] 性能监控

#### 第三阶段：完全移除
- [ ] 移除 Markwon 依赖
- [ ] 清理相关代码
- [ ] 最终测试验证

### 4.3 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 渲染效果不一致 | 中 | 双引擎对比测试 |
| 性能回归 | 低 | 性能基准测试 |
| 兼容性问题 | 中 | 保留回退机制 |
| 测试覆盖不足 | 中 | 增加集成测试 |

---

## 五、建议

### 5.1 短期 (Phase 5)

1. **功能开关实现**
   - 添加配置项选择渲染引擎
   - 默认使用 Rust Core
   - 保留 Markwon 作为备选

2. **效果对比测试**
   - 创建测试用例集
   - 对比两种引擎输出
   - 记录差异

### 5.2 中期 (Phase 5 后)

1. **性能测试**
   - 测量渲染速度
   - 内存使用对比
   - 大文件测试

2. **真机验证**
   - Supernote 设备测试
   - E-ink 显示效果
   - 用户反馈收集

### 5.3 长期

1. **完全迁移**
   - 移除 Markwon 依赖
   - 清理相关代码
   - 更新文档

2. **功能完善**
   - 优化渲染效果
   - 支持更多扩展语法
   - 性能优化

---

## 六、结论

**可行性评估**: ✅ 可以安全移除 Markwon

**前提条件**:
- 完成功能开关实现
- 完成效果对比测试
- 真机验证通过

**建议行动**:
1. 先实现功能开关
2. 进行充分测试
3. 逐步迁移
4. 最后移除 Markwon

---

**报告人**: Claude (AI Assistant)
**文档版本**: 1.0
