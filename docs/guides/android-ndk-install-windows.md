# Windows 上安装和配置 Android NDK

## 步骤 1: 安装 Android Studio

1. 下载 Android Studio：
   - 访问：https://developer.android.com/studio
   - 下载 Windows 版本（.exe 安装程序）

2. 运行安装程序并完成安装

## 步骤 2: 安装 Android NDK

### 方法 A: 通过 Android Studio（推荐）

1. 打开 Android Studio
2. 点击 `Tools` → `SDK Manager`
3. 切换到 `SDK Tools` 选项卡
4. 勾选以下项目：
   - `Show Package Details`（显示包详情）
   - `NDK (Side by side)`
   - 选择版本 `26.1.10909303` 或更高
5. 点击 `Apply` 或 `OK` 开始下载和安装

### 方法 B: 手动下载

1. 访问：https://developer.android.com/ndk/downloads
2. 下载 Windows 版本的 NDK
3. 解压到以下位置之一：
   - `%LOCALAPPDATA%\Android\Sdk\ndk\版本号`
   - `C:\Android\Sdk\ndk\版本号`

## 步骤 3: 配置环境变量

### 查找 NDK 路径

安装完成后，NDK 通常位于：
```
%LOCALAPPDATA%\Android\Sdk\ndk\26.1.10909303
```
或
```
C:\Users\你的用户名\AppData\Local\Android\Sdk\ndk\26.1.10909303
```

### 设置环境变量

#### 临时设置（当前会话）

```bash
# Git Bash / WSL
export ANDROID_NDK_HOME="$LOCALAPPDATA/Android/Sdk/ndk/26.1.10909303"

# PowerShell
$env:ANDROID_NDK_HOME = "$env:LOCALAPPDATA\Android\Sdk\ndk\26.1.10909303"

# CMD
set ANDROID_NDK_HOME=%LOCALAPPDATA%\Android\Sdk\ndk\26.1.10909303
```

#### 永久设置

**PowerShell（管理员）：**
```powershell
# 添加到用户环境变量
[System.Environment]::SetEnvironmentVariable('ANDROID_NDK_HOME', 
    "$env:LOCALAPPDATA\Android\Sdk\ndk\26.1.10909303", 
    [System.EnvironmentVariableTarget]::User)

# 验证设置
[System.Environment]::GetEnvironmentVariable('ANDROID_NDK_HOME', [System.EnvironmentVariableTarget]::User)
```

**CMD（管理员）：**
```cmd
setx ANDROID_NDK_HOME "%LOCALAPPDATA%\Android\Sdk\ndk\26.1.10909303"
```

## 步骤 4: 验证安装

```bash
# 验证 NDK 路径
ls "$ANDROID_NDK_HOME"

# 应该看到以下目录：
# build.gradle  platform.properties  source.properties  toolchains  etc.
```

## 步骤 5: 安装 cargo-ndk

```bash
cargo install cargo-ndk
```

## 步骤 6: 添加 Android Rust 目标

```bash
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
```

## 步骤 7: 测试构建

```bash
cd e:/Projects/NomadMark
./scripts/build/build-android.sh
```

---

## 常见问题

### Q: 不想安装 Android Studio，只想快速测试

您可以：
1. 手动下载 NDK ZIP 文件
2. 解压到任意目录（如 `E:\android-ndk`）
3. 设置 `ANDROID_NDK_HOME=E:\android-ndk`
4. 继续步骤 5-7

### Q: cargo-ndk 安装失败

确保：
- Rust 工具链已正确安装
- 有网络连接
- 或使用预编译二进制

### Q: 构建时出现链接器错误

检查：
- `ANDROID_NDK_HOME` 是否正确设置
- NDK 版本是否兼容（建议 26.x 或更高）
- Rust 目标是否已正确添加

---

## 快速参考

```bash
# 一键设置（Git Bash）
export ANDROID_NDK_HOME="$LOCALAPPDATA/Android/Sdk/ndk/26.1.10909303"
cargo install cargo-ndk
rustup target add aarch64-linux-android armv7-linux-androideabi

# 测试构建
cd e:/Projects/NomadMark && bash scripts/build/build-android.sh
```
