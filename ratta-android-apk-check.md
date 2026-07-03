# Ratta Android APK 签名与权限检查（含自动修复）

## 描述
检查并自动修复 Supernote (Ratta) Android 项目的系统签名配置，确保 APK 能以系统权限运行。
缺少任一配置都会导致应用无法获得系统 UID，从而无法访问 myScript 手写识别资源等系统级功能。

## 使用方法
在 Cursor 中调用此命令：`@ratta-android-apk-check`

## 背景知识
Supernote 设备上的系统级应用必须满足三个条件才能正常运行：
1. 使用厂商平台密钥 `ratta.jks` 签名
2. 在 AndroidManifest.xml 中声明 `android:sharedUserId="android.uid.system"`
3. 在 build.gradle 中配置 debug/release 都使用 `ratta.jks` 签名

三者缺一不可，否则因果链如下：
```
ratta.jks 缺失 / 签名配置不完整
  → APK 使用默认 debug 签名
    → 与 android.uid.system 的平台签名不匹配
      → 应用无法获得系统权限
        → 无法访问 /sdcard/.data/dictionary/conf/ 下的 myScript 语言资源
          → 手写识别等系统级功能不可用
```

## 执行流程（AI 自动执行）

> 先定位 Android 项目根目录（包含 `app/build.gradle` 的目录），以下路径均相对于该目录。

---

### 检查项 1：签名文件 `ratta.jks`

**检查**：在 `app/` 目录下搜索 `ratta.jks` 文件（与 `build.gradle` 同级）。

**如果缺失**：
- 这是厂商平台密钥，**无法自动生成**
- 输出提示：
  ```
  ❌ ratta.jks 不存在
  
  修复方法：从 Supernote 设备或厂商处获取 ratta.jks 文件，
  放置到 app/ 目录下（与 build.gradle 同级）。
  
  设备上可能的位置：
    /system/etc/security/platform.jks
    通过 Ratta 内部开发渠道获取
  ```
- **不执行自动修复**，仅报告

**如果存在**：✅ 确认路径并继续

---

### 检查项 2：AndroidManifest.xml 系统 UID 声明

**检查**：读取 `app/src/main/AndroidManifest.xml`，检查 `<manifest>` 标签是否包含 `android:sharedUserId="android.uid.system"`。

**如果缺失 → 自动修复**：
将 `<manifest>` 标签中添加 `android:sharedUserId="android.uid.system"` 属性。

修复前：
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.markdown.editor">
```

修复后：
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.markdown.editor"
    android:sharedUserId="android.uid.system">
```

⚠️ 修复前确认：`sharedUserId` 一旦设定后发布，后续不可更改（更改会导致已安装应用数据丢失）。

---

### 检查项 3：build.gradle 签名配置

**检查**：读取 `app/build.gradle`，检查以下内容：
- `android {}` 块中是否存在 `signingConfigs {}` 配置
- `signingConfigs` 中 release 和 debug 是否都引用了 `ratta.jks`
- `buildTypes` 中 release 和 debug 是否都关联了对应的 `signingConfig`

**如果缺失或不完整 → 自动修复**：

在 `android {}` 块中的 `defaultConfig {}` 之后、`buildTypes {}` 之前，插入或替换为以下配置：

```groovy
    signingConfigs {
        release {
            storeFile file("ratta.jks")
            storePassword '34537701'
            keyAlias 'ratta'
            keyPassword '34537701'
        }

        debug {
            storeFile file("ratta.jks")
            storePassword '34537701'
            keyAlias 'ratta'
            keyPassword '34537701'
        }
    }
```

同时确保 `buildTypes` 中每个类型都关联签名配置：
```groovy
    buildTypes {
        release {
            ...
            signingConfig signingConfigs.release
        }

        debug {
            ...
            signingConfig signingConfigs.debug
        }
    }
```

修复规则：
- 如果 `signingConfigs` 完全不存在 → 整块插入到 `defaultConfig {}` 之后
- 如果 `signingConfigs` 存在但内容不对 → 替换整块
- 如果 `buildTypes` 中缺少 `signingConfig` 行 → 在对应 `buildType` 块末尾的 `}` 前插入

---

### 检查项 4：.gitignore 排除签名文件

**检查**：读取项目根目录的 `.gitignore`，检查是否排除了 `ratta.jks`。

**如果缺失 → 自动修复**：
在 `.gitignore` 中追加：
```
# Ratta 平台签名密钥（厂商机密，禁止提交）
*.jks
```

---

### 输出检查报告

以表格形式汇总所有检查结果：

```
| 检查项                             | 状态 | 操作     | 说明                 |
|------------------------------------|------|----------|----------------------|
| ratta.jks 签名文件                 | ✅/❌ | -/需手动 | 路径或缺失提示       |
| sharedUserId="android.uid.system"  | ✅/🔧 | -/已修复 | AndroidManifest.xml  |
| build.gradle 签名配置              | ✅/🔧 | -/已修复 | signingConfigs 完整性|
| .gitignore 排除 *.jks              | ✅/🔧 | -/已修复 | 防止密钥泄露         |
```

图例：✅ 正常 | 🔧 已自动修复 | ❌ 需手动处理

如果执行了自动修复，最后提示：
```
已自动修复 N 项配置。请确认修改后执行构建验证：
  cd 00-PrototypeSystem/android-app && ./gradlew assembleDebug
```

## 注意事项
- `ratta.jks` 是设备厂商的平台密钥，**绝对不能提交到 Git 仓库**
- `android:sharedUserId` 一旦设定后不可更改（更改会导致已安装的应用数据丢失）
- debug 和 release 都必须使用 `ratta.jks` 签名，否则调试版本也无法以系统权限运行
- 自动修复不会处理 `ratta.jks` 文件本身，这必须从厂商处获取
