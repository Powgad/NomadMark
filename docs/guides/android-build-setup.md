# Android 构建环境配置指南

本文档描述如何配置 Android 构建环境以构建 NomadMark Android 应用。

---

## 前置条件

### 1. Rust 工具链

```bash
# 安装 Rust (如果尚未安装)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# 验证安装
rustc --version
cargo --version
```

### 2. Android NDK

Android NDK 是用于交叉编译 Rust 代码到 Android 平台的工具链。

#### 下载和安装

1. 通过 Android Studio 安装：
   - 打开 Android Studio
   - Tools → SDK Manager → SDK Tools
   - 勾选 "NDK (Side by side)"
   - 选择版本 r26.1.10909303 或更高版本
   - 点击 Apply

2. 或手动下载：
   - 访问 https://developer.android.com/ndk/downloads
   - 下载适合您平台的 NDK
   - 解压到任意目录

#### 配置环境变量

```bash
# Linux/macOS
export ANDROID_NDK_HOME=/path/to/android-ndk
export PATH=$ANDROID_NDK_HOME:$PATH

# Windows (PowerShell)
$env:ANDROID_NDK_HOME = "C:\path\to\android-ndk"
$env:PATH = "$env:ANDROID_NDK_HOME;$env:PATH"

# 永久设置 (添加到 ~/.bashrc 或 ~/.zshrc)
echo 'export ANDROID_NDK_HOME=/path/to/android-ndk' >> ~/.bashrc
echo 'export PATH=$ANDROID_NDK_HOME:$PATH' >> ~/.bashrc
```

### 3. Cargo-NDK

cargo-ndk 是用于简化 Android NDK 交叉编译的 Cargo 插件。

```bash
cargo install cargo-ndk
```

### 4. Android 目标架构

添加 Android Rust 目标：

```bash
# arm64-v8a (64位，推荐)
rustup target add aarch64-linux-android

# armeabi-v7a (32位，兼容)
rustup target add armv7-linux-androideabi

# x86_64 (模拟器)
rustup target add x86_64-linux-android
```

---

## 构建流程

### 步骤 1: 构建 Core 库

使用提供的构建脚本：

```bash
# Linux/macOS
./scripts/build/build-android.sh

# Windows (Git Bash)
bash scripts/build/build-android.sh
```

或手动构建：

```bash
# 进入 Core 目录
cd core

# 构建 arm64-v8a
cargo ndk -t arm64-v8a -o ../platforms/android/app/src/main/jniLibs/arm64-v8a build --release

# 构建 armeabi-v7a
cargo ndk -t armeabi-v7a -o ../platforms/android/app/src/main/jniLibs/armeabi-v7a build --release
```

### 步骤 2: 构建 Android APK

```bash
# 进入 Android 目录
cd platforms/android

# 清理旧构建
./gradlew clean

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

生成的 APK 位于：
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

---

## CI/CD 自动构建

项目已配置 GitHub Actions 工作流：

### Core CI (`.github/workflows/core-ci.yml`)
- 跨平台构建和测试
- Lint 检查
- 安全审计

### Android Build (`.github/workflows/android-build.yml`)
- Core 层交叉编译 (所有架构)
- 自动 APK 构建
- Android Lint 检查

触发条件：
- Push 到 main/develop 分支
- Pull Request
- 手动触发

---

## 常见问题

### 问题: cargo-ndk 找不到 NDK

**错误信息**: `Error: NDK not found. Please set ANDROID_NDK_HOME`

**解决方法**:
```bash
# 确保 ANDROID_NDK_HOME 正确设置
echo $ANDROID_NDK_HOME  # 应显示 NDK 路径

# 如果未设置，按上述步骤配置环境变量
```

### 问题: 链接器错误

**错误信息**: `linker `aarch64-linux-android-link` not found`

**解决方法**:
```bash
# 确保已添加 Android 目标
rustup target add aarch64-linux-android

# 确保 NDK 路径正确
ls $ANDROID_NDK_HOME/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android*-clang
```

### 问题: JNI 符号未找到

**错误信息**: `java.lang.UnsatisfiedLinkError: dlopen failed: cannot locate symbol "Java_com_editor_MarkdownCore_nativeCreate"`

**解决方法**:
- 确保 Core 库使用 `--features jni` 构建
- 检查 JNI 函数命名是否正确
- 验证包名与 Java 包名一致

### 问题: Windows 上构建失败

**解决方法**:
- 使用 WSL (Windows Subsystem for Linux)
- 或使用 Git Bash 而非 PowerShell
- 确保 `bash` 可用

---

## 验证构建

### 检查 .so 文件

```bash
# 检查 .so 文件是否存在
ls platforms/android/app/src/main/jniLibs/arm64-v8a/libmarkdown_core.so

# 检查符号表 (Linux)
nm -D platforms/android/app/src/main/jniLibs/arm64-v8a/libmarkdown_core.so | grep Java

# 或使用 readelf
readelf -d platforms/android/app/src/main/jniLibs/arm64-v8a/libmarkdown_core.so
```

### 测试 JNI 导出

在 Android Studio 中：
1. 打开项目
2. 等待 Gradle 同步完成
3. 运行 `./gradlew assembleDebug`
4. 检查构建输出

---

## 调试技巧

### 吵杂的构建输出

```bash
# 启用详细输出
cargo ndk -t arm64-v8a build --release --verbose
```

### 检查链接命令

```bash
# 显示完整的链接命令
cargo rustc --lib -- --crate-type=cdylib --print-link-args
```

### 检查目标架构

```bash
# 检查 .so 文件架构
file platforms/android/app/src/main/jniLibs/arm64-v8a/libmarkdown_core.so
# 输出应为: ELF 64-bit LSB shared object, ARM aarch64
```

---

## 目录结构

```
NomadMark/
├── core/                              # Rust Core 库
│   ├── src/
│   │   ├── lib.rs                     # FFI 导出
│   │   └── bridge/
│   │       └── jni.rs                 # JNI 桥接
│   ├── Cargo.toml                     # [lib] crate-type = ["cdylib", "staticlib"]
│   └── target/
│       └── aarch64-linux-android/
│           └── release/
│               └── libmarkdown_core.so
│
├── platforms/android/
│   ├── app/
│   │   ├── build.gradle               # Android 构建配置
│   │   └── src/main/
│   │       ├── jniLibs/               # 预编译 .so 存放位置
│   │       │   ├── arm64-v8a/
│   │       │   │   └── libmarkdown_core.so
│   │       │   └── armeabi-v7a/
│   │       │       └── libmarkdown_core.so
│   │       └── java/com/editor/nomadmark/
│   │           └── MarkdownCore.java  # JNI 声明
│   └── build.gradle
│
└── scripts/build/
    ├── build-android.sh               # Linux/macOS 构建脚本
    └── build-android.bat              # Windows 构建脚本
```

---

## 快速开始

```bash
# 1. 设置环境变量
export ANDROID_NDK_HOME=/path/to/ndk

# 2. 安装 cargo-ndk
cargo install cargo-ndk

# 3. 添加 Android 目标
rustup target add aarch64-linux-android armv7-linux-androideabi

# 4. 构建 Core
./scripts/build/build-android.sh

# 5. 构建 APK
cd platforms/android
./gradlew assembleDebug
```

---

## 参考资源

- [Rust Android NDK](https://github.com/rust-mobile/cargo-ndk)
- [Android NDK Guide](https://developer.android.com/ndk/guides)
- [JNI Specification](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/)
- [NomadMark Architecture](../architecture/detailed-design.md)
