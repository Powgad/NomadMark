# NomadMark Deployment Guide
## Supernote A6 X2 Nomad 部署指南

---

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Building Rust Core](#building-rust-core)
3. [Building Android APK](#building-android-apk)
4. [Installation](#installation)
5. [Testing](#testing)
6. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

| 软件 | 版本 | 用途 |
|------|------|------|
| Rust | 1.70+ | 编译 Core |
| Cargo | 最新 | Rust 包管理 |
| Android NDK | r21e+ | Android 交叉编译 |
| Android Studio | Hedgehog+ | APK 构建 |
| JDK | 17+ | Android 构建 |

### Environment Variables

```bash
# Linux/macOS
export ANDROID_NDK_HOME=/path/to/android-ndk
export PATH=$PATH:$ANDROID_NDK_HOME

# Windows
set ANDROID_NDK_HOME=C:\path\to\android-ndk
```

### Installing cargo-ndk

```bash
cargo install cargo-ndk
```

---

## Building Rust Core

### Option 1: Using Build Script

**Linux/macOS:**
```bash
chmod +x build-android.sh
./build-android.sh
```

**Windows:**
```cmd
build-android.bat
```

### Option 2: Manual Build

```bash
cd core

# Build for arm64-v8a (推荐)
cargo ndk -t arm64-v8a build --release

# Build for armeabi-v7a (兼容旧设备)
cargo ndk -t armeabi-v7a build --release
```

### Output Files

```
core/target/
├── aarch64-linux-android/release/
│   └── libmarkdown_core.so          # arm64-v8a
└── armv7-linux-androideabi/release/
    └── libmarkdown_core.so          # armeabi-v7a
```

---

## Building Android APK

### Method 1: Android Studio (推荐)

1. 打开 Android Studio
2. 选择 "Open an Existing Project"
3. 导航到 `android/` 目录
4. 等待 Gradle 同步完成
5. 选择 "Build > Build Bundle(s) / APK(s) > Build APK(s)"

### Method 2: Command Line

```bash
cd android

# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease
```

### Output Location

```
android/app/build/outputs/apk/
├── debug/app-debug.apk
└── release/app-release.apk
```

---

## Installation

### Via ADB

```bash
# 安装 APK
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# 启动应用
adb shell am start -n com.editor.nomadmark/.MainActivity
```

### Via File Manager

1. 将 APK 复制到 Supernote
2. 使用内置文件管理器打开 APK
3. 点击"安装"

---

## Testing

### Automated Tests

```bash
# 运行单元测试
cd android
./gradlew test

# 运行仪器测试
./gradlew connectedAndroidTest
```

### Manual Testing

参见 [TESTING_CHECKLIST.md](TESTING_CHECKLIST.md)

---

## Troubleshooting

### Build Issues

| 问题 | 解决方案 |
|------|---------|
| `cargo-ndk not found` | `cargo install cargo-ndk` |
| `ANDROID_NDK_HOME not set` | 设置环境变量 |
| NDK 版本不兼容 | 使用 r21e 或更高版本 |
| Gradle 同步失败 | 检查网络，更新 Gradle |

### Runtime Issues

| 问题 | 解决方案 |
|------|---------|
| ` UnsatisfiedLinkError` | 检查 .so 文件是否正确复制 |
| 应用崩溃 | 检查 logcat: `adb logcat` |
| 刷新问题 | 确认设备支持 E-ink API |
| 手写笔不工作 | 确认 Ratta 笔驱动正常 |

### Debug Commands

```bash
# 查看 logcat
adb logcat | grep -E "NomadMark|MainActivity|MarkdownCore"

# 查看内存使用
adb shell dumpsys meminfo com.editor.nomadmark

# 查看进程状态
adb shell ps -A | grep nomadmark
```

---

## Version Compatibility

| 组件 | 版本要求 |
|------|---------|
| Supernote OS | 1.8+ |
| Android API Level | 28+ (Android 9) |
| Target SDK | 34 |
| NDK | 21e+ |

---

## Release Checklist

- [ ] 所有单元测试通过
- [ ] 所有仪器测试通过
- [ ] 真机测试通过
- [ ] 性能指标达标
- [ ] 内存使用正常
- [ ] 已签名 Release APK
- [ ] 版本号已更新
- [ ] 发布说明已准备

---

## Support

- GitHub Issues: https://github.com/nomadmark/nomadmark/issues
- 文档: 见项目根目录
- 邮件: support@nomadmark.dev (示例)
