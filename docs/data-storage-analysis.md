# NomadMark Android 数据存储机制分析

## 存储类型概览

```
┌─────────────────────────────────────────────────────────────────┐
│                    数据存储分类                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. 用户文档存储                                               │
│     ├─ 文件路径模式 (File API)                                 │
│     └─ URI 模式 (Storage Access Framework)                    │
│                                                                 │
│  2. 自动保存临时存储                                            │
│     └─ cache/autosave/ (临时文件 + 元数据)                      │
│                                                                 │
│  3. 配置数据存储                                               │
│     └─ SharedPreferences (用户偏好、按键绑定)                     │
│                                                                 │
│  4. 崩溃日志存储                                               │
│     └─ cache/crashes/ (崩溃报告)                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 1. 用户文档存储

### 1.1 文件路径模式 (传统 File API)

**位置**：
```
/sdcard/Document/              (Supernote 设备优先)
或
/sdcard/Documents/NomadMark/    (标准 Android)
```

**代码实现**：[MarkdownEditorActivity.kt:1251](platforms/android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt#L1251)

```kotlin
// 保存
File(path).writeText(content)

// 读取
val content = File(path).readText()
```

**优点**：
- 直接文件访问，性能好
- 支持相对路径图片引用

**缺点**：
- 需要 `MANAGE_EXTERNAL_STORAGE` 权限
- Android 11+ 受限

---

### 1.2 URI 模式 (Storage Access Framework)

**位置**：用户通过 SAF 选择的位置

**代码实现**：[MarkdownEditorActivity.kt:1296](platforms/android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt#L1296)

```kotlin
// 保存
contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
    writer.write(content)
}

// 读取
val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }

// 持久化权限
contentResolver.takePersistableUriPermission(uri, flags)
```

**优点**：
- 无需特殊权限
- 支持 Google Drive 等云存储

**缺点**：
- 性能略低于文件 API
- URI 可能在重启后失效
- 图片相对路径解析困难

---

## 2. 自动保存临时存储

### 目录结构

```
/data/data/com.editor.nomadmark/cache/autosave/
├── meta_<uuid>.properties          # 元数据
├── content_<uuid>.md                # 内容
└── .last_access_<uuid>             # 访问时间
```

### 元数据格式 (Properties)

**文件**：`meta_<uuid>.properties`

```properties
# AutoSave Metadata
version=1
id=a5b42119-70d4-40af-82fc-a5e1c44e6aba
timestamp=1737543169000
originalFilePath=/sdcard/Document/test.md
originalFileUri=
fileName=test
contentLength=14
lastSavedHash=1234567890
isModified=true
appVersion=1.0.0
deviceInfo=Supernote A6 X2 Nomad
```

### 核心类

| 类 | 职责 |
|---|------|
| [AutoSaveSession](platforms/android/app/src/main/java/com/editor/nomadmark/autosave/AutoSaveSession.kt) | 管理单个会话的临时文件 |
| [AutoSaveManager](platforms/android/app/src/main/java/com/editor/nomadmark/autosave/AutoSaveManager.kt) | 扫描、清理、管理所有会话 |

### 生命周期

```
用户编辑 → 3秒防抖 → 自动保存到 cache/autosave/
    │
    ├─ 正常保存 → 临时文件删除
    │
    ├─ onPause → 触发保存
    │
    └─ 崩溃 → 临时文件保留 → 下次启动恢复
```

---

## 3. SharedPreferences 配置存储

### 存储位置

```
/data/data/com.editor.nomadmark/shared_prefs/
├── NomadMarkPrefs.xml              # 主配置
├── autosave_prefs.xml              # 自动保存状态
├── crash_handler_prefs.xml         # 崩溃处理状态
└── keybinding_prefs.xml            # 按键绑定
```

### 3.1 NomadMarkPrefs.xml - 主配置

**使用者**：[MarkdownEditorActivity](platforms/android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt)、[MainActivity](platforms/android/app/src/main/java/com/editor/nomadmark/MainActivity.kt)

| 键名 | 类型 | 用途 |
|------|------|------|
| `has_shown_sample` | boolean | 是否已展示示例文件 |

### 3.2 autosave_prefs.xml - 自动保存状态

**使用者**：[AutoSaveManager](platforms/android/app/src/main/java/com/editor/nomadmark/autosave/AutoSaveManager.kt)

| 键名 | 类型 | 用途 |
|------|------|------|
| `has_autosave` | boolean | 是否有自动保存 |
| `last_session_id` | String | 上次会话 ID |
| `last_timestamp` | long | 上次保存时间戳 |

### 3.3 crash_handler_prefs.xml - 崩溃处理

**使用者**：[CrashHandler](platforms/android/app/src/main/java/com/editor/nomadmark/CrashHandler.kt)

| 键名 | 类型 | 用途 |
|------|------|------|
| `has_crash` | boolean | 是否上次崩溃 |
| `crash_file` | String | 崩溃日志文件路径 |
| `crash_time` | long | 崩溃时间 |

### 3.4 keybinding_prefs.xml - 按键绑定

**使用者**：[KeyBindingManager](platforms/android/app/src/main/java/com/editor/nomadmark/KeyBindingManager.kt)

| 键名 | 类型 | 用途 |
|------|------|------|
| `custom_bindings` | JSON 字符串 | 自定义按键绑定 |

**JSON 格式**：
```json
[
  {
    "keyCode": 113,
    "ctrl": true,
    "shift": false,
    "alt": false,
    "actionId": "save"
  }
]
```

---

## 4. 崩溃日志存储

### 目录结构

```
/data/data/com.editor.nomadmark/cache/crashes/
├── crash_20250121_153042.md      # 崩溃报告
└── .last_access_<timestamp>       # 访问时间
```

### 崩溃报告格式

**使用者**：[CrashHandler](platforms/android/app/src/main/java/com/editor/nomadmark/CrashHandler.kt)

```markdown
# Crash Report

**Time:** 2025-01-21 15:30:42

## Application Information
- **Version:** 1.0.0 (1)
- **Package:** com.editor.nomadmark
- **Debug Build:** true

## Device Information
- **Model:** Supernote A6 X2 Nomad
- **Manufacturer:** Ratta
- **Android Version:** 11
- **API Level:** 30

## Exception
```
（异常堆栈）
```
```

---

## 存储流程图

```
┌─────────────────────────────────────────────────────────────────┐
│                       用户操作                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  用户编辑文档                                                   │
│      │                                                         │
│      ├─ 实时内容保存在内存中 (EditText.text)                   │
│      │                                                         │
│      └─ 3秒无输入 → 自动保存到 cache/autosave/                 │
│                                                                 │
│  用户点击"保存"                                                 │
│      │                                                         │
│      ├─ 文件路径模式 → File(path).writeText(content)           │
│      │                                                         │
│      └─ URI 模式 → contentResolver.openOutputStream(uri)        │
│                                                                 │
│  保存成功 → 删除 cache/autosave/ 临时文件                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 配置数据流向

```
┌─────────────────────────────────────────────────────────────────┐
│                    配置数据存储                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  用户设置按键绑定                                               │
│      │                                                         │
│      ├─ SharedPreferences 存储 (keybinding_prefs.xml)             │
│      │         └─ JSON 格式序列化                               │
│      │                                                         │
│      └─ 下次启动 → KeyBindingManager 加载                        │
│                                                                 │
│  应用首次启动                                                   │
│      │                                                         │
│      ├─ MainActivity 检查 has_shown_sample                     │
│      │                                                         │
│      └─ false → 显示示例，设置 has_shown_sample = true           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 数据清理策略

### 自动清理机制

| 类型 | 清理时机 | 实现位置 |
|------|----------|----------|
| 自动保存临时文件 | 应用启动时 (7天前) | [AutoSaveManager.cleanupExpiredSessions()](platforms/android/app/src/main/java/com/editor/nomadmark/autosave/AutoSaveManager.kt) |
| 自动保存临时文件 | 正常保存成功后 | [MarkdownEditorActivity.saveFile()](platforms/android/app/src/main/java/com/editor/nomadmark/MarkdownEditorActivity.kt) |
| 崩溃日志 | 应用启动时 (10个文件限制) | [CrashHandler](platforms/android/app/src/main/java/com/editor/nomadmark/CrashHandler.kt) |

### 手动清理

用户可以通过以下方式清理：
```kotlin
// 清理所有自动保存
AutoSaveManager.clearAll(context)

// 清理所有崩溃日志
CrashHandler.clearOldCrashes(context)
```

---

## 存储限制与注意事项

### 1. cache 目录特性

- 系统可能在空间不足时清理 cache 目录
- 应用卸载时 cache 目录会被删除
- 不应存储关键用户数据

### 2. URI 权限持久化

```kotlin
// 获取持久化 URI 权限
contentResolver.takePersistableUriPermission(uri,
    FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION)
```

注意：权限可能在以下情况失效：
- 应用被重新安装（签名不同）
- 用户手动撤销权限

### 3. 文件路径模式限制

Android 11+ 需要 `MANAGE_EXTERNAL_STORAGE` 权限：
```xml
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

---

## 存储使用建议

| 数据类型 | 推荐存储方式 | 理由 |
|----------|--------------|------|
| 用户文档 | File API 或 URI | 用户可访问、可备份 |
| 自动保存 | cache/autosave/ | 临时性质，可清理 |
| 用户偏好 | SharedPreferences | 轻量键值对存储 |
| 崩溃日志 | cache/crashes/ | 临时诊断数据 |
| 图片引用 | 相对路径 | 便于文档迁移 |

---

## 相关文件

| 文件 | 功能 |
|------|------|
| [FileOperationHelper.kt](platforms/android/app/src/main/java/com/editor/nomadmark/FileOperationHelper.kt) | 文件目录选择 |
| [AutoSaveSession.kt](platforms/android/app/src/main/java/com/editor/nomadmark/autosave/AutoSaveSession.kt) | 自动保存会话 |
| [AutoSaveManager.kt](platforms/android/app/src/main/java/com/editor/nomadmark/autosave/AutoSaveManager.kt) | 自动保存管理 |
| [KeyBindingManager.kt](platforms/android/app/src/main/java/com/editor/nomadmark/KeyBindingManager.kt) | 按键绑定存储 |
| [CrashHandler.kt](platforms/android/app/src/main/java/com/editor/nomadmark/CrashHandler.kt) | 崩溃日志存储 |
