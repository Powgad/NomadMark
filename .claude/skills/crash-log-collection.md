# 错误日志收集功能

## 问题

需要实现一个自动错误日志收集系统，每次应用崩溃时自动收集详细的错误信息，并在下次启动时提示用户。

## 解决方案

### 已实现的功能

1. **CrashHandler.kt** - 增强的崩溃日志收集器
   - 自动捕获未处理的异常（崩溃）
   - 收集设备信息（型号、制造商、Android 版本、API 级别、架构等）
   - 收集应用信息（版本号、包名、是否为 Debug 构建）
   - 收集运行时信息（可用内存、总内存、是否低内存模式）
   - 收集最近的 logcat 日志
   - 保存到 `externalCacheDir/crashes/` 目录
   - 自动清理旧的崩溃文件（最多保留 10 个）

2. **NomadMarkApplication.kt** - 应用启动时自动检查
   - 使用 SharedPreferences 记录崩溃状态
   - 应用启动时检查是否有上次崩溃
   - 发送通知提醒用户
   - 提供崩溃报告上传接口（预留）

3. **AndroidManifest.xml** - 权限配置
   - 添加 POST_NOTIFICATIONS 权限（API 33+）
   - 添加 READ_LOGS 权限（API 15 及以下）

## 核心功能

### 自动收集崩溃日志

应用崩溃时自动收集以下信息：

```kotlin
// 崩溃日志包含
- 时间戳
- 线程信息
- 异常堆栈
- 设备信息（型号、制造商、Android 版本、架构）
- 应用信息（版本号、包名）
- 运行时信息（内存状态）
- 最近的 logcat 日志
```

### 应用启动时自动检查

```kotlin
// 在 NomadMarkApplication.onCreate() 中
CrashHandler.init(this)
checkPreviousCrash() // 自动检查上次崩溃
```

### 手动报告错误

```kotlin
// 用于非崩溃的错误
CrashHandler.reportError("TAG", "Error message", exception)
```

## 崩溃日志存储位置

```
{externalCacheDir}/crashes/crash_yyyyMMdd_HHmmss.log
```

例如：`/storage/emulated/0/Android/data/com.editor.nomadmark/cache/crashes/crash_20250110_143022.log`

## 如何使用

### 查看崩溃日志

```kotlin
// 获取所有崩溃文件
val crashFiles = CrashHandler.getCrashFiles()

// 读取崩溃内容
val content = CrashHandler.readCrashFile(crashFile)

// 获取崩溃日志总大小
val size = CrashHandler.getCrashLogSize()
```

### 清理崩溃日志

```kotlin
// 删除单个文件
CrashHandler.deleteCrashFile(file)

// 清除所有崩溃日志
CrashHandler.clearAllCrashes()
```

## 崩溃日志格式

```markdown
# Crash Report

**Time:** 2025-01-10 14:30:22

## Application Information

- **Version:** 1.0.0 (1)
- **Package:** com.editor.nomadmark
- **Debug Build:** true

## Device Information

- **Model:** A6 X2
- **Manufacturer:** Supernote
- **Android Version:** 13
- **API Level:** 33
- **Arch:** aarch64

## Runtime Information

- **Available Memory:** 512 MB
- **Total Memory:** 4096 MB
- **Memory Class:** 192 MB
- **Low Memory Mode:** false

## Thread Information

- **Thread Name:** main

## Exception

```
java.lang.NullPointerException: Attempt to invoke virtual method...
    at com.editor.nomadmark.MainActivity.onCreate(MainActivity.java:123)
    ...
```

## Recent Logs

```
01-10 14:30:20.123  4567  4567 E NomadMark: Error loading file
...
```
```

## 上传到服务器

在 `NomadMarkApplication.kt` 中预留了 `uploadCrashReport()` 函数，可以添加网络上传逻辑：

```kotlin
private fun uploadCrashReport(crashInfo: CrashHandler.CrashInfo) {
    // 使用 Retrofit 或 OkHttp 上传崩溃报告
    // 示例代码已在注释中提供
}
```

## 验证方法

1. 触发一次崩溃（例如抛出异常）
2. 应用会自动收集崩溃日志
3. 重启应用
4. 应该看到通知："应用上次意外关闭"
5. 崩溃日志保存在 `crashes/` 目录

## 注意事项

1. 崩溃日志文件最大数量为 10 个，超过会自动删除最旧的
2. logcat 收集可能失败（权限限制），不会影响崩溃日志保存
3. POST_NOTIFICATIONS 权限在 API 33+ 需要运行时请求