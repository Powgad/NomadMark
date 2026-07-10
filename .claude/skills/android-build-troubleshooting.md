# Android Build Troubleshooting

## 问题排查与解决指南

此 skill 记录了在 Windows 上编译 Android Rust Core 项目时遇到的常见问题及解决方法。

---

## 问题 1: Cargo config.toml 路径转义错误

### 症状
```
error: could not parse TOML configuration in `C:\Users\Administrator\.cargo\config.toml`
TOML parse error at line 14, column 11
invalid escape sequence
```

### 原因
Windows 路径中的单个反斜杠 `\` 在 TOML 中被解析为转义字符。

### 解决方法
将 `~/.cargo/config.toml` 中的路径反斜杠替换为双反斜杠 `\\` 或正斜杠 `/`：

**错误示例：**
```toml
[target.aarch64-linux-android]
ar = "D:\sdk_android\ndk\25.1.8937393\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-ar.exe"
```

**正确示例：**
```toml
[target.aarch64-linux-android]
ar = "D:\\sdk_android\\ndk\\25.1.8937393\\toolchains\\llvm\\prebuilt\\windows-x86_64\\bin\\llvm-ar.exe"
```

或使用正斜杠：
```toml
ar = "D:/sdk_android/ndk/25.1.8937393/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-ar.exe"
```

---

## 问题 2: ANDROID_NDK_HOME 环境变量未设置

### 症状
```
❌ ANDROID_NDK_HOME not set
```

### 解决方法

#### 1. 查找 NDK 路径

常见 NDK 安装位置：
- `C:\Users\用户名\AppData\Local\Android\Sdk\ndk\版本号`
- `D:\sdk_android\ndk\版本号`

通过 SDK Manager 查看具体路径。

#### 2. 临时设置（当前会话）
```bash
# Git Bash
export ANDROID_NDK_HOME="/d/sdk_android/ndk/29.0.13846066"

# PowerShell
$env:ANDROID_NDK_HOME = "D:\sdk_android\ndk\29.0.13846066"
```

#### 3. 永久设置（PowerShell 管理员）
```powershell
[System.Environment]::SetEnvironmentVariable('ANDROID_NDK_HOME',
    "D:\sdk_android\ndk\29.0.13846066",
    [System.EnvironmentVariableTarget]::User)
```

---

## 问题 3: cargo-ndk .so 文件输出位置

### 症状
编译成功但找不到 `.so` 文件。

### 原因
`cargo-ndk` 默认不自动将 `.so` 文件复制到 jniLibs 目录。

### 解决方法
使用 `-o` 参数指定输出目录：

```bash
cd core
cargo ndk -t arm64-v8a -o "../platforms/android/app/src/main/jniLibs" build --release
cargo ndk -t armeabi-v7a -o "../platforms/android/app/src/main/jniLibs" build --release
```

---

## 问题 5: JNI 函数未找到 (UnsatisfiedLinkError)

### 症状
```
java.lang.UnsatisfiedLinkError: No implementation found for long com.editor.nomadmark.MarkdownCore.nativeCreate(java.lang.String)
(tried Java_com_editor_nomadmark_MarkdownCore_nativeCreate and Java_com_editor_nomadmark_MarkdownCore_nativeCreate__Ljava_lang_String_2)
```

### 原因
**最常见原因**: `core/Cargo.toml` 中 jni 依赖被限定在 `target.'cfg(target_os = "android")'` 下，导致在 Windows 上交叉编译时 jni 依赖不可用。

**错误配置**:
```toml
[target.'cfg(target_os = "android")'.dependencies]
jni = { workspace = true, optional = true }
```

### 解决方法

#### 1. 修复 Cargo.toml

将 jni 依赖移到顶层 dependencies：

```toml
# JNI dependencies (needed for Android build even when cross-compiling)
jni = { workspace = true, optional = true }

[features]
android = ["jni"]
jni = ["dep:jni"]
```

#### 2. 编译时启用 android feature

```bash
cd core
CARGO_ENCODED_ARGS='["--features","android"]' cargo ndk -t arm64-v8a -o "../platforms/android/app/src/main/jniLibs" build --release
CARGO_ENCODED_ARGS='["--features","android"]' cargo ndk -t armeabi-v7a -o "../platforms/android/app/src/main/jniLibs" build --release
```

#### 3. 验证 jni 是否被启用

```bash
cd core
cargo tree -f "{p} {f}" --features android | grep jni
# 应该看到 jni 被启用
```

### 验证方法
1. 检查日志确认没有 `UnsatisfiedLinkError`
2. 安装 APK 后测试预览功能

---

## 问题 6: .gitignore 错误忽略 jniLibs .so 文件

### 症状
预编译的 `.so` 文件被 Git 忽略。

### 原因
`.gitignore` 中的 `*.so` 规则忽略了所有 `.so` 文件。

### 解决方法
在 `.gitignore` 中添加例外规则：

```gitignore
# 忽略构建中间产物 .so
*.so

# 但保留 jniLibs 中的预编译库
!**/jniLibs/**/*.so
!platforms/android/app/src/main/jniLibs/**/*.so
```

### 验证
```bash
# 检查文件是否被忽略
git check-ignore -v platforms/android/app/src/main/jniLibs/arm64-v8a/libmarkdown_core.so
```

---

## 问题 5: Gradle 构建脚本被 .gitignore 忽略

### 症状
```
The following paths are ignored by one of your .gitignore files:
scripts/build
```

### 原因
`.gitignore` 中的 `/build/` 规则影响了所有包含 `build` 的路径。

### 解决方法
使用 `-f` 强制添加：
```bash
git add -f scripts/build/
```

---

## 快速参考：完整编译流程

### 1. 设置环境
```bash
export ANDROID_NDK_HOME="/d/sdk_android/ndk/29.0.13846066"
```

### 2. 编译 Rust Core（重要：启用 android feature）
```bash
cd e:/Projects/NomadMark/core
CARGO_ENCODED_ARGS='["--features","android"]' cargo ndk -t arm64-v8a -o "../platforms/android/app/src/main/jniLibs" build --release
CARGO_ENCODED_ARGS='["--features","android"]' cargo ndk -t armeabi-v7a -o "../platforms/android/app/src/main/jniLibs" build --release
```

### 3. 编译 Android APK
```bash
cd e:/Projects/NomadMark/platforms/android
./gradlew.bat assembleDebug
# 或 release 版本
./gradlew.bat assembleRelease
```

### 4. 验证输出
```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
ls -lh ../app/src/main/jniLibs/*/*.so
```

---

## 必要工具安装

```bash
# Rust 目标
rustup target add aarch64-linux-android armv7-linux-androideabi

# cargo-ndk
cargo install cargo-ndk
```

---

## 文件结构参考

```
project/
├── core/
│   ├── Cargo.toml
│   └── src/
├── platforms/
│   └── android/
│       └── app/
│           └── src/
│               └── main/
│                   └── jniLibs/
│                       ├── arm64-v8a/libmarkdown_core.so
│                       └── armeabi-v7a/libmarkdown_core.so
└── scripts/
    └── build/
        └── build-android.sh
```
