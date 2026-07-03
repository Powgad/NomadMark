# Android SDK 镜像源配置指南

中国用户推荐使用镜像源加速 SDK/NDK 下载。

---

## 方法一：腾讯云镜像（推荐）

### HTTP Proxy 配置

**Android Studio 设置：**

1. 打开 Android Studio
2. `File` → `Settings` (或 `Configure` → `Settings`)
3. 左侧菜单选择 `Appearance & Behavior` → `System Settings` → `HTTP Proxy`
4. 选择 `Manual proxy configuration`
5. 填写：
   - **Host name**: `mirrors.cloud.tencent.com`
   - **Port number**: `80`
6. 点击 `Check connection` 测试连接
7. 点击 `Apply` → `OK`

### 验证镜像源

在 Android Studio 的 SDK Manager 中，下载地址应显示为：
```
https://mirrors.cloud.tencent.com/Android/android-sdk/dl/
```

---

## 方法二：清华大学镜像

### 配置 repositories.cfg

创建或编辑文件：
```
%USERPROFILE%\.android\repositories.cfg
```

添加以下内容：

```ini
# 清华大学镜像
plugin.vias=https://mirrors.tuna.tsinghua.edu.cn/AndroidStudio/plugins
sdk.cli=https://mirrors.tuna.tsinghua.edu.cn/Android/android-sdk/cli
sdk.ndk=https://mirrors.tuna.tsinghua.edu.cn/Android/android-sdk/ndk
sdk.platform=https://mirrors.tuna.tsinghua.edu.cn/Android/android-sdk/platform
sdk.tools=https://mirrors.tuna.tsinghua.edu.cn/Android/android-sdk/tools
sdk.addons=https://mirrors.tuna.tsinghua.edu.cn/Android/android-sdk/addons
sdk.extras=https://mirrors.tuna.tsinghua.edu.cn/Android/android-sdk/extras
```

---

## 方法三：阿里云镜像

### HTTP Proxy 配置

1. Android Studio → `Settings` → `HTTP Proxy`
2. 选择 `Manual proxy configuration`
3. 填写：
   - **Host name**: `mirrors.aliyun.com`
   - **Port number**: `80`
---

## 镜像源对比

| 镜像源 | 地址 | 同步频率 | 推荐度 |
|:------:|------|:--------:|:------:|
| 腾讯云 | mirrors.cloud.tencent.com | 每日 | ⭐⭐⭐⭐⭐ |
| 清华大学 | mirrors.tuna.tsinghua.edu.cn | 每日 | ⭐⭐⭐⭐⭐ |
| 阿里云 | mirrors.aliyun.com | 每日 | ⭐⭐⭐⭐ |

---

## 安装 NDK（使用镜像源）

### 步骤 1: 配置镜像源
按上述方法选择一个镜像源并配置。

### 步骤 2: 打开 SDK Manager
1. Android Studio → `Tools` → `SDK Manager`
2. 切换到 `SDK Tools` 选项卡

### 步骤 3: 选择 NDK
1. 勾选 `Show Package Details`
2. 展开 `NDK (Side by side)`
3. 选择版本：`26.1.10909125` 或更高

### 步骤 4: 开始安装
点击 `Apply` 或 `OK`，下载将使用镜像源加速。

---

## 手动下载 NDK（备选）

如果 Android Studio 下载失败，可以手动下载：

### 腾讯云镜像下载
```
https://mirrors.cloud.tencent.com/Android/android-sdk/ndk/26.1.10909125/
```

### 下载后安装
1. 下载 Windows 版本的 NDK ZIP 文件
2. 解压到：
   ```
   %LOCALAPPDATA%\Android\Sdk\ndk\26.1.10909125
   ```
3. 或解压到自定义目录，然后设置 `ANDROID_NDK_HOME`

---

## 验证镜像源生效

### 检查下载速度
- 配置镜像源后，下载速度应显著提升
- 631 MB 的 NDK 应在几分钟内完成（取决于网络）

### 检查安装路径
安装完成后，验证：
```bash
ls "$LOCALAPPDATA/Android/Sdk/ndk/26.1.10909125"
```

应显示以下目录：
```
build.gradle  platforms  platform.properties  source.properties  toolchains
```

---

## 常见问题

### Q: 配置后仍然从官方源下载
**A:** 确保：
- HTTP Proxy 配置正确
- 点击了 `Check connection` 验证连接
- 重启 Android Studio

### Q: 手动下载后如何配置
**A:**
```bash
# 设置环境变量指向自定义 NDK 路径
export ANDROID_NDK_HOME=/path/to/your/ndk
```

### Q: 镜像源版本不全
**A:** 可以结合使用：
- 镜像源下载主要组件
- 官方源下载特定版本

---

## 快速配置命令

```bash
# 1. 创建配置目录
mkdir -p ~/.android

# 2. 创建 repositories.cfg（Git Bash）
cat > ~/.android/repositories.cfg << 'EOF'
# 腾讯云镜像
sdk.ndk=https://mirrors.cloud.tencent.com/Android/android-sdk/ndk
sdk.tools=https://mirrors.cloud.tencent.com/Android/android-sdk/tools
EOF

# 3. 验证配置
cat ~/.android/repositories.cfg
```

然后在 Android Studio 中通过 SDK Manager 安装 NDK。
