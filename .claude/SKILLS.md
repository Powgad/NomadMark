# Claude Skills Index

本项目定义的 Claude Code skills，用于处理特定任务。

---

## Available Skills

| Skill | File | Description |
|-------|------|-------------|
| **android-build-troubleshooting** | [.claude/skills/android-build-troubleshooting.md](android-build-troubleshooting.md) | Windows 上编译 Android Rust Core 项目的常见问题与解决方法 |
| **crash-log-collection** | [.claude/skills/crash-log-collection.md](crash-log-collection.md) | Android 错误日志自动收集系统（崩溃捕获、设备信息、日志上传） |
| **rust-core-roadmap** | [docs/rust-core-roadmap.md](../docs/rust-core-roadmap.md) | Rust Core 渲染引擎开发路线图（阶段规划、功能清单、技术架构） |
| **rust-core-rendering-troubleshooting** | [.claude/skills/rust-core-rendering-troubleshooting.md](rust-core-rendering-troubleshooting.md) | Rust Core 渲染异常排查（内存布局、JNI 接口、nativeReadBytes 问题） |
| **rust-core-review** | [.claude/skills/rust-core-review.md](rust-core-review.md) | Rust Core 代码审查报告（内存安全、设计问题、性能优化、架构建议） |
| **supernote-device-dev** | [docs/skills.md](../docs/skills.md) | Supernote/Ratta 墨水屏设备开发指南（E-Ink UI、系统签名、手写 SDK、Scoped Storage） |

---

## Usage

当处理相关任务时，Claude 会自动参考这些 skills 中的解决方案和最佳实践。

---

## Adding New Skills

1. 在 `.claude/skills/` 目录下创建新的 `.md` 文件
2. 按照标准格式编写 frontmatter：
   ```yaml
   ---
   name: skill-name
   description: 简短描述
   ---
   ```
3. 更新本索引文件

---

## Skill 记录规则

**每次遇到需要思考两次以上才能解决的问题，必须记录完全准确的解决方式到 skills 中。**

这样可以：
- 避免重复解决相同问题
- 积累项目特定知识
- 提高后续开发效率

记录内容应包含：
- **问题现象**：错误信息、症状
- **根本原因**：为什么会出现
- **解决方法**：完整可执行的步骤
- **验证方法**：如何确认问题已解决
- **预防措施**：如何避免再次出现
