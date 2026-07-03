# Claude Skills Index

本项目定义的 Claude Code skills，用于处理特定任务。

---

## Available Skills

| Skill | File | Description |
|-------|------|-------------|
| **android-build-troubleshooting** | [.claude/skills/android-build-troubleshooting.md](android-build-troubleshooting.md) | Windows 上编译 Android Rust Core 项目的常见问题与解决方法 |
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
