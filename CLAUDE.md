# NomadMark 项目指南

## 项目概述

NomadMark 是一个专为 Supernote 墨水屏设备设计的 Markdown 编辑器，支持 Android 和 Desktop 平台。

---

## 开发规范

### 问题解决流程

**当遇到需要思考两次以上的问题时，必须执行以下流程：**

1. **首先读取 skills 查找解决方案**
   ```bash
   # 优先查看是否有现成的解决方法
   Read .claude/skills/android-build-troubleshooting.md
   ```

2. **如果找到解决方案**
   - 按照 skill 中的步骤执行
   - 验证解决效果
   - 如果方案有效，确认 skill 记录准确
   - 如果方案无效或需调整，更新 skill 记录

3. **如果没有找到解决方案**
   - 解决问题后，**必须**创建新的 skill 记录
   - 记录内容必须包含：
     - 问题现象（错误信息、症状）
     - 根本原因
     - 完整的解决方法
     - 验证方法
     - 预防措施
   - 更新 `.claude/SKILLS.md` 索引

### 可用 Skills

- **android-build-troubleshooting** - Windows 上 Android Rust Core 编译问题
- **supernote-device-dev** - Supernote 墨水屏设备开发指南

详见：[.claude/SKILLS.md](.claude/SKILLS.md)

---

## 项目结构

```
NomadMark/
├── core/                    # Rust Core 层（Markdown 解析、渲染）
├── platforms/
│   ├── android/            # Android 平台
│   └── desktop/            # Desktop 平台（Tauri）
├── docs/                   # 项目文档
├── scripts/
│   └── build/             # 构建脚本
└── .claude/
    └── skills/            # 故障排查知识库
```

---

## 快速参考

### Android 编译
```bash
# 设置环境
export ANDROID_NDK_HOME="D:/sdk_android/ndk/29.0.13846066"

# 编译 Rust Core
cd core
cargo ndk -t arm64-v8a -o "../platforms/android/app/src/main/jniLibs" build --release
cargo ndk -t armeabi-v7a -o "../platforms/android/app/src/main/jniLibs" build --release

# 编译 APK
cd ../platforms/android
./gradlew.bat assembleDebug
```

### 常见问题快速索引
- Cargo config 路径转义 → [android-build-troubleshooting](.claude/skills/android-build-troubleshooting.md#问题-1-cargo-configtoml-路径转义错误)
- NDK 环境变量未设置 → [android-build-troubleshooting](.claude/skills/android-build-troubleshooting.md#问题-2-android_ndk_home-环境变量未设置)
- .gitignore .so 文件问题 → [android-build-troubleshooting](.claude/skills/android-build-troubleshooting.md#问题-4-gitignore-错误忽略-jnilibs-so-文件)
