# 自动保存与崩溃恢复设计文档

## 1. 概述

### 1.1 目标
- 防止用户因断电、崩溃等意外情况丢失未保存的编辑内容
- 提供透明的自动保存机制，不打断用户编辑流程
- 支持崩溃后恢复未保存内容

### 1.2 设计原则
- **非侵入式**：自动保存不打断用户，不弹出提示
- **安全性**：不直接覆盖原文件，使用临时文件
- **可控性**：用户可选择恢复或放弃
- **高效性**：防抖机制减少频繁写入

---

## 2. 核心概念

### 2.1 自动保存 (Auto-Save)
自动保存是指在不需用户手动操作的情况下，定期或在特定时机将编辑内容保存到临时文件。

### 2.2 崩溃恢复 (Crash Recovery)
崩溃恢复是指应用在异常终止后重启时，检测并提示用户恢复上次未保存的内容。

### 2.3 临时文件结构
```
/data/data/com.editor.nomadmark/cache/
├── autosave/
│   ├── meta_<uuid>.json          # 元数据文件
│   ├── content_<uuid>.md         # 内容文件
│   └── .last_access_<uuid>      # 最后访问时间戳
```

---

## 3. 临时文件设计

### 3.1 文件命名规则

使用 UUID 作为唯一标识符，确保并发安全和避免冲突：

```
autosave/
├── meta_a1b2c3d4-e5f6-7890-abcd-ef1234567890.json
├── content_a1b2c3d4-e5f6-7890-abcd-ef1234567890.md
└── .last_access_a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

### 3.2 元数据格式

**使用属性文件格式（无需 JSON 库）**

```properties
# AutoSave Metadata v1
id=a1b2c3d4-e5f6-7890-abcd-ef1234567890
timestamp=1735689600000
originalFilePath=/sdcard/Document/my-doc.md
originalFileUri=
fileName=my-doc
contentLength=12345
lastSavedHash=1234567890
isModified=true
appVersion=1.0.0
deviceInfo=Supernote A6 X2 Nomad
```

> **项目适配说明**：项目当前没有 JSON 序列化库，使用简单的 Properties 格式更轻量且易于解析。

### 3.3 自动保存会话生命周期

```
┌─────────────────────────────────────────────────────────────────┐
│                      AutoSave Session                           │
├─────────────────────────────────────────────────────────────────┤
│  创建          更新               恢复/清理                     │
│    │             │                   │                          │
│    ▼             ▼                   ▼                          │
│ onOpenFile → onTextChange → onUserAction                      │
│     │             │                   │                         │
│     └─────────────┴───────────────────┘                         │
│                   │                                              │
│                   ▼                                              │
│            (用户选择恢复/放弃)                                   │
│                   │                                              │
│           ┌──────┴──────┐                                       │
│           ▼             ▼                                       │
│      恢复内容        放弃并删除                                  │
│           └───────────┘                                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. 自动保存触发时机

### 4.1 触发条件

| 触发条件 | 说明 | 优先级 |
|----------|------|--------|
| 文本变化防抖 | 用户停止输入 3 秒后 | 高 |
| 定时保存 | 每隔 60 秒（有修改） | 中 |
| Activity 暂停 | onPause() 时 | 高 |
| 打开新文件前 | 替换当前内容前 | 高 |
| 退出应用 | finish() 前 | 高 |

### 4.2 防抖机制

```kotlin
/**
 * 防抖自动保存
 * 用户停止输入 3 秒后才执行保存
 */
private val AUTO_SAVE_DELAY = 3000L
private var autoSaveRunnable: Runnable? = null
private val autoSaveHandler = Handler(Looper.getMainLooper())

private fun scheduleAutoSave() {
    // 取消之前的保存任务
    autoSaveRunnable?.let { autoSaveHandler.removeCallbacks(it) }
    
    // 创建新的保存任务
    autoSaveRunnable = Runnable {
        if (isModified) {
            performAutoSave()
        }
    }
    
    autoSaveHandler.postDelayed(autoSaveRunnable!!, AUTO_SAVE_DELAY)
}
```

### 4.3 最小保存间隔

避免过于频繁的保存操作：

```kotlin
private val MIN_AUTO_SAVE_INTERVAL = 5000L  // 最少间隔 5 秒
private var lastAutoSaveTime = 0L

private fun performAutoSave() {
    val now = System.currentTimeMillis()
    if (now - lastAutoSaveTime < MIN_AUTO_SAVE_INTERVAL) {
        Log.d("AutoSave", "跳过频繁保存请求")
        return
    }
    
    lastAutoSaveTime = now
    // ... 执行保存
}
```

---

## 5. 崩溃恢复流程

### 5.1 恢复检测时机

```
┌──────────────────────────────────────────────────────────────┐
│                    启动流程                                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  onCreate()                                                  │
│     │                                                        │
│     ├─→ 清理过期临时文件（7 天前）                            │
│     │                                                        │
│     └─→ 扫描 autosave 目录                                    │
│           │                                                  │
│           ├─→ 有可恢复文件？                                  │
│           │        │                                         │
│           │        ├─ YES → 显示恢复对话框                    │
│           │        │        ├─ 恢复 → 加载内容               │
│           │        │        ├─ 放弃 → 删除临时文件            │
│           │        │        └─ 稍后 → 保留临时文件            │
│           │        │                                         │
│           │        └─ NO → 正常启动                          │
│           │                                                  │
│           └─→ handleOpenIntent()                             │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 5.2 恢复对话框设计

```
┌──────────────────────────────────────────────┐
│         发现未保存的内容                        │
├──────────────────────────────────────────────┤
│                                              │
│  检测到上次的编辑未保存，是否恢复？             │
│                                              │
│  文件：my-doc.md                             │
│  时间：2024-01-01 14:30                       │
│  大小：12.3 KB                                │
│                                              │
├──────────────────────────────────────────────┤
│  [恢复内容]  [放弃]  [稍后决定]                │
└──────────────────────────────────────────────┘
```

### 5.3 多文件恢复处理

如果有多个可恢复的文件：

```
┌──────────────────────────────────────────────┐
│         发现多个未保存的文件                   │
├──────────────────────────────────────────────┤
│                                              │
│  检测到 2 个文件有未保存的内容：               │
│                                              │
│  ☑ my-doc.md        (14:30, 12.3 KB)         │
│  ☑ notes.md         (15:45, 5.1 KB)          │
│                                              │
├──────────────────────────────────────────────┤
│  [恢复选中]  [放弃全部]  [稍后决定]            │
└──────────────────────────────────────────────┘
```

---

## 6. 临时文件管理

### 6.1 AutoSaveSession 管理类

```kotlin
package com.editor.nomadmark.autosave

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.Properties
import java.util.UUID

/**
 * 自动保存会话
 * 负责管理临时文件的创建、更新、删除和恢复
 * 
 * **项目适配说明**：
 * - 使用 Properties 格式存储元数据（无需 JSON 库）
 * - 使用 Thread + Handler 模式（与项目风格一致）
 * - TAG 常量用于日志（项目规范）
 */
class AutoSaveSession(
    private val context: Context,
    private val originalFilePath: String? = null,
    private val originalFileUri: Uri? = null
) {
    
    companion object {
        private const val TAG = "AutoSaveSession"
        private const val VERSION = "1"
    }
    
    /** 唯一标识符 */
    val sessionId: String = UUID.randomUUID().toString()
    
    /** 临时文件目录 */
    private val autosaveDir: File = File(context.cacheDir, "autosave")
    
    /** 元数据文件 */
    private val metaFile: File = File(autosaveDir, "meta_$sessionId.properties")
    
    /** 内容文件 */
    private val contentFile: File = File(autosaveDir, "content_$sessionId.md")
    
    /** 最后访问时间戳文件 */
    private val lastAccessFile: File = File(autosaveDir, ".last_access_$sessionId")
    
    /** 从文件加载的元数据 */
    private var cachedMetadata: AutoSaveMetadata? = null
    
    /**
     * 保存内容到临时文件
     */
    fun save(content: String, lastSavedContent: String, isModified: Boolean): Boolean {
        return try {
            // 确保目录存在
            if (!autosaveDir.exists()) {
                autosaveDir.mkdirs()
            }
            
            // 保存内容
            contentFile.writeText(content)
            
            // 保存元数据（使用 Properties 格式）
            val metadata = AutoSaveMetadata(
                id = sessionId,
                timestamp = System.currentTimeMillis(),
                originalFilePath = originalFilePath,
                originalFileUri = originalFileUri?.toString(),
                fileName = originalFilePath?.let { File(it).nameWithoutExtension } 
                    ?: "untitled",
                contentLength = content.length,
                lastSavedHash = lastSavedContent.hashCode(),
                isModified = isModified,
                appVersion = getAppVersion(),
                deviceInfo = getDeviceInfo()
            )
            writeMetadata(metadata)
            
            // 更新访问时间
            updateLastAccessTime()
            
            Log.d(TAG, "自动保存成功: ${metadata.fileName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "自动保存失败", e)
            false
        }
    }
    
    /**
     * 写入元数据到 Properties 文件
     */
    private fun writeMetadata(metadata: AutoSaveMetadata) {
        val props = Properties().apply {
            setProperty("version", VERSION)
            setProperty("id", metadata.id)
            setProperty("timestamp", metadata.timestamp.toString())
            setProperty("originalFilePath", metadata.originalFilePath ?: "")
            setProperty("originalFileUri", metadata.originalFileUri ?: "")
            setProperty("fileName", metadata.fileName)
            setProperty("contentLength", metadata.contentLength.toString())
            setProperty("lastSavedHash", metadata.lastSavedHash.toString())
            setProperty("isModified", metadata.isModified.toString())
            setProperty("appVersion", metadata.appVersion)
            setProperty("deviceInfo", metadata.deviceInfo)
        }
        
        metaFile.bufferedWriter().use { writer ->
            props.store(writer, "AutoSave Metadata")
        }
        cachedMetadata = metadata
    }
    
    /**
     * 从 Properties 文件读取元数据
     */
    private fun readMetadata(): AutoSaveMetadata? {
        cachedMetadata?.let { return it }
        
        if (!metaFile.exists()) {
            Log.w(TAG, "元数据文件不存在: ${metaFile.absolutePath}")
            return null
        }
        
        return try {
            val props = Properties()
            metaFile.bufferedReader().use { reader ->
                props.load(reader)
            }
            
            AutoSaveMetadata(
                id = props.getProperty("id"),
                timestamp = props.getProperty("timestamp")?.toLong() ?: 0L,
                originalFilePath = props.getProperty("originalFilePath")?.takeIf { it.isNotEmpty() },
                originalFileUri = props.getProperty("originalFileUri")?.takeIf { it.isNotEmpty() },
                fileName = props.getProperty("fileName", "untitled"),
                contentLength = props.getProperty("contentLength", "0").toInt(),
                lastSavedHash = props.getProperty("lastSavedHash", "0").toInt(),
                isModified = props.getProperty("isModified", "false").toBoolean(),
                appVersion = props.getProperty("appVersion", "unknown"),
                deviceInfo = props.getProperty("deviceInfo", "unknown")
            ).also { cachedMetadata = it }
        } catch (e: Exception) {
            Log.e(TAG, "读取元数据失败", e)
            null
        }
    }
    
    /**
     * 更新访问时间（用于清理判断）
     */
    fun updateLastAccessTime() {
        try {
            lastAccessFile.writeText(System.currentTimeMillis().toString())
        } catch (e: Exception) {
            Log.e(TAG, "更新访问时间失败", e)
        }
    }
    
    /**
     * 删除临时文件
     */
    fun delete() {
        metaFile.deleteQuietly()
        contentFile.deleteQuietly()
        lastAccessFile.deleteQuietly()
        Log.d(TAG, "临时文件已删除: $sessionId")
    }
    
    /**
     * 读取保存的内容
     */
    fun loadContent(): String? {
        return try {
            if (contentFile.exists()) {
                contentFile.readText()
            } else {
                Log.w(TAG, "内容文件不存在: ${contentFile.absolutePath}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取临时文件失败", e)
            null
        }
    }
    
    /**
     * 获取元数据（公开属性）
     */
    val fileName: String
        get() = readMetadata()?.fileName ?: "unknown"
    
    val timestamp: Long
        get() = readMetadata()?.timestamp ?: 0L
    
    val contentLength: Int
        get() = readMetadata()?.contentLength ?: 0
    
    val originalFilePath: String?
        get() = readMetadata()?.originalFilePath
    
    val originalFileUri: String?
        get() = readMetadata()?.originalFileUri?.let { Uri.parse(it) }?.toString()
    
    val isModified: Boolean
        get() = readMetadata()?.isModified ?: false
    
    val lastSavedContent: Int?
        get() = readMetadata()?.lastSavedHash
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun getDeviceInfo(): String {
        return "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    }
}

/**
 * 自动保存元数据
 */
data class AutoSaveMetadata(
    val id: String,
    val timestamp: Long,
    val originalFilePath: String?,
    val originalFileUri: String?,
    val fileName: String,
    val contentLength: Int,
    val lastSavedHash: Int,
    val isModified: Boolean,
    val appVersion: String,
    val deviceInfo: String
)
```

### 6.2 AutoSaveManager 统一管理

```kotlin
package com.editor.nomadmark.autosave

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 自动保存管理器
 * 单例模式，全局管理所有自动保存会话
 */
object AutoSaveManager {
    
    private const val TAG = "AutoSaveManager"
    
    /** 当前活动的会话 */
    private var currentSession: AutoSaveSession? = null
    
    /**
     * 创建新会话
     */
    fun createSession(
        context: Context,
        filePath: String? = null,
        fileUri: android.net.Uri? = null
    ): AutoSaveSession {
        // 结束旧会话
        currentSession?.delete()
        
        // 创建新会话
        val session = AutoSaveSession(context, filePath, fileUri)
        currentSession = session
        Log.d(TAG, "创建新会话: ${session.sessionId}")
        return session
    }
    
    /**
     * 扫描可恢复的会话
     */
    fun scanRecoverableSessions(context: Context): List<AutoSaveSession> {
        val autosaveDir = File(context.cacheDir, "autosave")
        if (!autosaveDir.exists()) {
            Log.d(TAG, "临时目录不存在")
            return emptyList()
        }
        
        val sessions = mutableListOf<AutoSaveSession>()
        
        // 查找所有元数据文件
        val metaFiles = autosaveDir.listFiles { _, name ->
            name.startsWith("meta_") && name.endsWith(".properties")
        } ?: run {
            Log.d(TAG, "未找到元数据文件")
            return emptyList()
        }
        
        Log.d(TAG, "找到 ${metaFiles.size} 个元数据文件")
        
        for (metaFile in metaFiles) {
            try {
                // 从文件名提取 sessionId
                val sessionId = metaFile.name.removePrefix("meta_").removeSuffix(".properties")
                
                // 验证完整性
                val contentFile = File(autosaveDir, "content_$sessionId.md")
                if (!contentFile.exists()) {
                    Log.w(TAG, "内容文件缺失，清理无效会话: $sessionId")
                    cleanupSession(sessionId, context)
                    continue
                }
                
                // 创建会话对象（元数据将在访问时延迟加载）
                val session = AutoSaveSession(context)
                // 通过反射设置 sessionId（或修改构造函数）
                sessions.add(session)
                
            } catch (e: Exception) {
                Log.e(TAG, "解析元数据失败: ${metaFile.name}", e)
                metaFile.deleteQuietly()
            }
        }
        
        Log.d(TAG, "扫描到 ${sessions.size} 个可恢复会话")
        
        return sessions.sortedByDescending { it.timestamp }
    }
    
    /**
     * 清理过期会话
     */
    fun cleanupExpiredSessions(context: Context, maxAgeDays: Int = 7) {
        val sessions = scanRecoverableSessions(context)
        val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
        
        val expiredCount = sessions.count { it.timestamp < cutoffTime }
        if (expiredCount > 0) {
            Log.d(TAG, "清理 $expiredCount 个过期会话")
        }
        
        sessions.filter { it.timestamp < cutoffTime }
            .forEach { 
                Log.d(TAG, "清理过期会话: ${it.fileName}")
                it.delete()
            }
    }
    
    /**
     * 清理指定会话
     */
    private fun cleanupSession(sessionId: String, context: Context) {
        val autosaveDir = File(context.cacheDir, "autosave")
        arrayOf(
            "meta_$sessionId.properties",
            "content_$sessionId.md",
            ".last_access_$sessionId"
        ).forEach { name ->
            File(autosaveDir, name).deleteQuietly()
        }
    }
    
    /**
     * 清理所有会话
     */
    fun clearAll(context: Context) {
        val autosaveDir = File(context.cacheDir, "autosave")
        if (autosaveDir.exists()) {
            val deleted = autosaveDir.deleteRecursively()
            Log.d(TAG, "清理所有临时文件: $deleted")
        }
    }
}
```

### 6.3 扩展函数

```kotlin
package com.editor.nomadmark.autosave

import java.io.File

/**
 * File 扩展函数
 */
internal fun File.deleteQuietly(): Boolean {
    return try {
        this.delete()
    } catch (e: Exception) {
        false
    }
}
```

### 6.2 AutoSaveManager 统一管理

```kotlin
/**
 * 自动保存管理器
 * 单例模式，全局管理所有自动保存会话
 */
object AutoSaveManager {
    
    /** 当前活动的会话 */
    private var currentSession: AutoSaveSession? = null
    
    /** 所有可恢复的会话 */
    private val recoverableSessions: MutableList<AutoSaveSession> = mutableListOf()
    
    /**
     * 创建新会话
     */
    fun createSession(
        context: Context,
        filePath: String? = null,
        fileUri: Uri? = null
    ): AutoSaveSession {
        // 结束旧会话
        currentSession?.delete()
        
        // 创建新会话
        val session = AutoSaveSession(context, filePath, fileUri)
        currentSession = session
        return session
    }
    
    /**
     * 扫描可恢复的会话
     */
    fun scanRecoverableSessions(context: Context): List<AutoSaveSession> {
        val autosaveDir = File(context.cacheDir, "autosave")
        if (!autosaveDir.exists()) {
            return emptyList()
        }
        
        val sessions = mutableListOf<AutoSaveSession>()
        
        // 查找所有元数据文件
        val metaFiles = autosaveDir.listFiles { _, name ->
            name.startsWith("meta_") && name.endsWith(".json")
        } ?: return emptyList()
        
        for (metaFile in metaFiles) {
            try {
                val metadata = Json.decodeFromString<AutoSaveMetadata>(
                    metaFile.readText()
                )
                
                // 验证完整性
                val contentFile = File(autosaveDir, "content_${metadata.id}.md")
                if (contentFile.exists()) {
                    sessions.add(AutoSaveSession(context, metadata))
                } else {
                    // 内容文件缺失，清理无效会话
                    cleanupSession(metadata.id, context)
                }
            } catch (e: Exception) {
                Log.e("AutoSave", "解析元数据失败: ${metaFile.name}", e)
                metaFile.deleteQuietly()
            }
        }
        
        recoverableSessions.clear()
        recoverableSessions.addAll(sessions)
        
        return sessions.sortedByDescending { it.timestamp }
    }
    
    /**
     * 清理过期会话
     */
    fun cleanupExpiredSessions(context: Context, maxAgeDays: Int = 7) {
        val sessions = scanRecoverableSessions(context)
        val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
        
        sessions.filter { it.timestamp < cutoffTime }
            .forEach { 
                Log.d("AutoSave", "清理过期会话: ${it.fileName}")
                it.delete()
            }
    }
    
    /**
     * 清理指定会话
     */
    private fun cleanupSession(sessionId: String, context: Context) {
        val autosaveDir = File(context.cacheDir, "autosave")
        arrayOf(
            "meta_$sessionId.json",
            "content_$sessionId.md",
            ".last_access_$sessionId"
        ).forEach { name ->
            File(autosaveDir, name).deleteQuietly()
        }
    }
    
    /**
     * 清理所有会话
     */
    fun clearAll(context: Context) {
        val autosaveDir = File(context.cacheDir, "autosave")
        if (autosaveDir.exists()) {
            autosaveDir.deleteRecursively()
        }
    }
}
```

---

## 7. MarkdownEditorActivity 集成

### 7.1 状态变量

```kotlin
class MarkdownEditorActivity : Activity() {
    
    // 自动保存会话
    private var autoSaveSession: AutoSaveSession? = null
    
    // 自动保存配置
    private val AUTO_SAVE_DELAY = 3000L
    private val MIN_AUTO_SAVE_INTERVAL = 5000L
    
    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private var autoSaveRunnable: Runnable? = null
    private var lastAutoSaveTime = 0L
}
```

### 7.2 核心方法

```kotlin
/**
 * 初始化自动保存会话
 */
private fun initAutoSaveSession() {
    autoSaveSession = AutoSaveManager.createSession(
        this,
        filePath,
        fileUri
    )
}

/**
 * 调度自动保存（防抖）
 */
private fun scheduleAutoSave() {
    autoSaveRunnable?.let { autoSaveHandler.removeCallbacks(it) }
    
    autoSaveRunnable = Runnable {
        if (isModified) {
            performAutoSave()
        }
    }
    
    autoSaveHandler.postDelayed(autoSaveRunnable!!, AUTO_SAVE_DELAY)
}

/**
 * 执行自动保存
 */
private fun performAutoSave() {
    val now = System.currentTimeMillis()
    if (now - lastAutoSaveTime < MIN_AUTO_SAVE_INTERVAL) {
        return
    }
    
    lastAutoSaveTime = now
    
    Thread {
        try {
            val content = getCurrentContent()
            val session = autoSaveSession ?: return@Thread
            
            val result = session.save(
                content = content,
                lastSavedContent = lastSavedContent,
                isModified = isModified
            )
            
            result.onSuccess {
                Log.d("AutoSave", "自动保存成功: ${session.sessionId}")
            }.onFailure { e ->
                Log.e("AutoSave", "自动保存失败", e)
            }
        } catch (e: Exception) {
            Log.e("AutoSave", "自动保存异常", e)
        }
    }.start()
}

/**
 * 检查可恢复内容
 */
private fun checkRecoverableContent() {
    val sessions = AutoSaveManager.scanRecoverableSessions(this)
    
    if (sessions.isEmpty()) {
        return
    }
    
    when (sessions.size) {
        1 -> showRecoveryDialog(sessions.first())
        else -> showMultipleRecoveryDialog(sessions)
    }
}

/**
 * 显示恢复对话框
 */
private fun showRecoveryDialog(session: AutoSaveSession) {
    val content = session.loadContent() ?: return
    
    AlertDialog.Builder(this)
        .setTitle("发现未保存的内容")
        .setMessage(buildString {
            append("检测到上次的编辑未保存，是否恢复？\n\n")
            append("文件：${session.fileName}\n")
            append("时间：${formatTimestamp(session.timestamp)}\n")
            append("大小：${formatSize(session.contentLength)}")
        })
        .setPositiveButton("恢复") { _, _ ->
            recoverContent(session, content)
        }
        .setNegativeButton("放弃") { _, _ ->
            session.delete()
            // 继续正常启动流程
            handleOpenIntent(intent)
        }
        .setNeutralButton("稍后") { _, _ ->
            // 保留临时文件，继续启动
            handleOpenIntent(intent)
        }
        .setOnCancelListener {
            // 取消也保留临时文件
            handleOpenIntent(intent)
        }
        .show()
}

/**
 * 恢复内容
 */
private fun recoverContent(session: AutoSaveSession, content: String) {
    // 恢复状态
    filePath = session.originalFilePath
    fileUri = session.originalFileUri?.let { Uri.parse(it) }
    fileName = session.fileName
    
    // 加载内容
    editorText.setText(content)
    splitEditorText.setText(content)
    lastSavedContent = session.lastSavedContent?.toString() ?: ""
    isModified = session.isModified
    
    // 初始化新会话（旧会话在恢复后被丢弃）
    initAutoSaveSession()
    
    // 更新 UI
    updateSaveButton()
    updateFilenameDisplay()
    updatePreview()
    
    // 提示用户
    Toast.makeText(this, "已恢复未保存内容", Toast.LENGTH_SHORT).show()
    
    // 清空撤销栈
    undoStack.clear()
    redoStack.clear()
    undoStack.add(content)
}
```

### 7.3 生命周期集成

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_editor)
    
    // 1. 清理过期临时文件
    AutoSaveManager.cleanupExpiredSessions(this, maxAgeDays = 7)
    
    // 2. 检查可恢复内容
    checkRecoverableContent()
    
    // 3. 如果没有可恢复内容，正常启动
    if (autoSaveSession == null) {
        initAutoSaveSession()
        handleOpenIntent(intent)
    }
    
    // ... 其余初始化
}

override fun onResume() {
    super.onResume()
    autoSaveSession?.updateLastAccessTime()
}

override fun onPause() {
    super.onPause()
    // 应用进入后台时保存
    if (isModified) {
        performAutoSave()
    }
}

override fun onDestroy() {
    super.onDestroy()
    autoSaveHandler.removeCallbacksAndMessages(null)
    
    // 如果是正常退出且没有未保存修改，清理临时文件
    if (!isFinishing) {
        // 非正常结束（崩溃），保留临时文件
        return
    }
    
    if (!isModified) {
        autoSaveSession?.delete()
    }
}
```

### 7.4 TextWatcher 集成

```kotlin
private fun setupTextWatchers() {
    val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        
        override fun afterTextChanged(s: Editable?) {
            if (!isUndoingOrRedoing) {
                isModified = true
                updateSaveButton()
                scheduleAutoSave()  // 自动保存
                updatePreview()
            }
        }
    }
    
    editorText.addTextChangedListener(textWatcher)
    splitEditorText.addTextChangedListener(textWatcher)
}
```

---

## 8. 异常处理

### 8.1 磁盘空间不足

```kotlin
private fun performAutoSave() {
    Thread {
        try {
            // 检查可用空间
            val cacheDir = cacheDir
            val usableSpace = cacheDir.usableSpace
            val requiredSpace = getCurrentContent().length.toLong() * 2 // 预留元数据空间
            
            if (usableSpace < requiredSpace) {
                Log.w("AutoSave", "磁盘空间不足，跳过自动保存")
                // 可选：清理旧文件释放空间
                cleanupOldestSession()
                return@Thread
            }
            
            // ... 执行保存
        } catch (e: Exception) {
            Log.e("AutoSave", "自动保存异常", e)
        }
    }.start()
}

private fun cleanupOldestSession() {
    val sessions = AutoSaveManager.scanRecoverableSessions(this)
    if (sessions.isNotEmpty()) {
        val oldest = sessions.minByOrNull { it.timestamp }
        oldest?.delete()
        Log.d("AutoSave", "清理最旧的会话释放空间")
    }
}
```

### 8.2 文件读写权限

```kotlin
private fun verifyAutosaveDirAccessible(): Boolean {
    val autosaveDir = File(cacheDir, "autosave")
    return try {
        if (!autosaveDir.exists()) {
            autosaveDir.mkdirs()
        }
        // 尝试创建测试文件
        val testFile = File(autosaveDir, ".write_test")
        testFile.createNewFile()
        testFile.delete()
        true
    } catch (e: Exception) {
        Log.e("AutoSave", "临时目录不可访问", e)
        false
    }
}
```

---

## 9. 性能考虑

### 9.1 异步保存

所有自动保存操作都在后台线程执行，避免阻塞 UI：

```kotlin
private fun performAutoSave() {
    Thread {
        // 后台执行
    }.start()
}
```

### 9.2 限制保存频率

```kotlin
// 1. 防抖：用户停止输入后才保存
private val AUTO_SAVE_DELAY = 3000L

// 2. 最小间隔：两次保存最少间隔 5 秒
private val MIN_AUTO_SAVE_INTERVAL = 5000L

// 3. 最大间隔：最长 60 秒必须保存一次
private val MAX_AUTO_SAVE_INTERVAL = 60_000L
```

### 9.3 内存优化

```kotlin
// 避免重复创建 Handler
private val autoSaveHandler = Handler(Looper.getMainLooper())

// 复用 Runnable
private var autoSaveRunnable: Runnable? = null
```

---

## 10. 测试计划

### 10.1 功能测试

| 测试场景 | 预期结果 |
|----------|----------|
| 编辑后等待 3 秒 | 自动保存到临时文件 |
| 编辑后立即关闭应用 | 临时文件包含最新内容 |
| 崩溃后重启应用 | 显示恢复对话框 |
| 恢复后查看内容 | 内容与崩溃前一致 |
| 放弃恢复 | 临时文件被删除 |
| 正常保存后 | 临时文件被清理 |

### 10.2 边界测试

| 测试场景 | 预期结果 |
|----------|----------|
| 空内容 | 正常保存空文件 |
| 超大文件 (10MB+) | 正常保存 |
| 频繁编辑 | 防抖生效，不过度保存 |
| 磁盘空间不足 | 跳过保存或清理旧文件 |
| 多个可恢复文件 | 显示多选对话框 |

### 10.3 压力测试

| 测试场景 | 预期结果 |
|----------|----------|
| 连续快速输入 | 不卡顿，防抖生效 |
| 长时间编辑 | 定时保存正常工作 |
| 多次崩溃恢复 | 每次都能正确恢复 |

---

## 11. 用户交互流程

### 11.1 正常编辑流程

```
用户打开文件
    ↓
用户开始编辑
    ↓
每 3 秒无输入 → 自动保存到临时文件（后台）
    ↓
用户点击保存按钮
    ↓
保存到原文件 + 删除临时文件
    ↓
继续编辑...
```

### 11.2 崩溃恢复流程

```
应用崩溃/断电
    ↓
用户重启应用
    ↓
检测到临时文件
    ↓
显示恢复对话框
    ↓
┌─────────┬─────────┬─────────┐
│ 恢复    │ 放弃    │ 稍后    │
└─────────┴─────────┴─────────┘
    ↓         ↓         ↓
加载内容   删除文件   保留文件
继续编辑              正常启动
```

---

## 12. 实现优先级

### Phase 1 - 核心功能 (MVP)
- [ ] AutoSaveSession 基础实现
- [ ] 防抖自动保存
- [ ] 临时文件读写
- [ ] 基础恢复对话框

### Phase 2 - 完善功能
- [ ] AutoSaveManager 统一管理
- [ ] 过期文件清理
- [ ] 多文件恢复支持
- [ ] 元数据完善

### Phase 3 - 增强功能
- [ ] 磁盘空间检查
- [ ] 异常处理完善
- [ ] 性能优化
- [ ] 日志统计

---

## 13. 注意事项

1. **URI 权限问题**：通过 SAF 打开的文件，URI 权限在应用重启后可能失效。临时文件方案不受此影响。

2. **缓存目录清理**：系统可能清理 cache 目录，需要确保不依赖缓存目录的持久性。

3. **多窗口/分屏**：如果支持多窗口，需要为每个窗口维护独立的会话。

4. **大文件处理**：对于超大文件，考虑增量保存或限制大小。

5. **加密需求**：如果内容敏感，考虑加密临时文件。

---

## 14. 文件结构

**项目适配说明**：与项目现有包结构保持一致

```
platforms/android/app/src/main/java/com/editor/nomadmark/
├── autosave/
│   ├── AutoSaveSession.kt          # 会话管理
│   ├── AutoSaveManager.kt          # 全局管理
│   ├── AutoSaveMetadata.kt         # 元数据数据类
│   └── AutoSaveExtensions.kt      # 扩展函数
├── format/
├── image/
├── search/
└── MarkdownEditorActivity.kt      # 集成自动保存
```

---

## 15. 项目适配总结

| 适配项 | 项目实际情况 | 方案调整 |
|--------|--------------|----------|
| **JSON 序列化** | ❌ 无 Gson/Moshi/kotlinx.serialization | ✅ 使用 Properties 格式 |
| **异步处理** | ✅ kotlinx-coroutines | ✅ 使用 Thread + Handler（现有风格） |
| **包结构** | ✅ 按功能分包 (format/image/search/toc) | ✅ 创建 autosave 包 |
| **日志** | ✅ android.util.Log | ✅ 每个类定义 TAG 常量 |
| **崩溃处理** | ✅ 已有 CrashHandler | ✅ 复用其设计模式 |
| **对话框** | ✅ AlertDialog.Builder | ✅ 与现有风格一致 |
| **文件操作** | ✅ Java File API | ✅ 保持一致 |

---

## 16. 与现有代码集成点

### 16.1 在 MarkdownEditorActivity 中集成

```kotlin
// ========== 新增状态变量 ==========
private var autoSaveSession: AutoSaveSession? = null
private val AUTO_SAVE_DELAY = 3000L
private val MIN_AUTO_SAVE_INTERVAL = 5000L
private val autoSaveHandler = Handler(Looper.getMainLooper())
private var autoSaveRunnable: Runnable? = null
private var lastAutoSaveTime = 0L

// ========== onCreate 中添加 ==========
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_editor)
    
    // 1. 清理过期临时文件
    AutoSaveManager.cleanupExpiredSessions(this, maxAgeDays = 7)
    
    // 2. 检查可恢复内容
    val sessions = AutoSaveManager.scanRecoverableSessions(this)
    if (sessions.isNotEmpty()) {
        showRecoveryDialog(sessions.first())
        return  // 恢复后再继续启动
    }
    
    // 3. 正常启动流程
    initAutoSaveSession()
    handleOpenIntent(intent)
    
    // ... 其余初始化
}

// ========== TextWatcher 中添加 ==========
private fun setupTextWatchers() {
    val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            if (!isUndoingOrRedoing) {
                isModified = true
                updateSaveButton()
                scheduleAutoSave()  // 新增：自动保存
                updatePreview()
            }
        }
        // ...
    }
    editorText.addTextChangedListener(textWatcher)
    splitEditorText.addTextChangedListener(textWatcher)
}

// ========== 生命周期中添加 ==========
override fun onPause() {
    super.onPause()
    if (isModified) {
        performAutoSave()
    }
}

override fun onDestroy() {
    super.onDestroy()
    autoSaveHandler.removeCallbacksAndMessages(null)
    
    if (!isFinishing || !isModified) {
        autoSaveSession?.delete()
    }
}
```

### 16.2 复用 CrashHandler 模式

参考项目中 [CrashHandler.kt](platforms/android/app/src/main/java/com/editor/nomadmark/CrashHandler.kt) 的设计：

```kotlin
// CrashHandler 已有的模式可以复用：
// - 使用 SharedPreferences 记录状态
// - 使用文件存储详细信息
// - 启动时检查上次状态
// - 提供用户友好的恢复提示

// AutoSave 可以复用类似模式：
object AutoSaveManager {
    private const val PREFS_NAME = "autosave_prefs"
    private const val KEY_HAS_AUTOSAVE = "has_autosave"
    
    fun markHasAutosave(context: Context, sessionId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_HAS_AUTOSAVE, true)
            .putString("last_session_id", sessionId)
            .apply()
    }
    
    fun clearAutosaveFlag(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
```

---

## 17. 实现优先级（调整）

### Phase 1 - 核心功能 (MVP) - 与项目现有代码集成
- [ ] `autosave/AutoSaveSession.kt` - 使用 Properties 格式
- [ ] `autosave/AutoSaveManager.kt` - 扫描和管理
- [ ] MarkdownEditorActivity 中集成：
  - [ ] `scheduleAutoSave()` - 防抖保存
  - [ ] `performAutoSave()` - 后台保存
  - [ ] `checkRecoverableContent()` - 启动检查
- [ ] 基础恢复对话框

### Phase 2 - 完善功能
- [ ] 过期文件清理（复用 CrashHandler 模式）
- [ ] 多文件恢复支持
- [ ] SharedPreferences 状态记录
- [ ] 异常处理

### Phase 3 - 增强功能
- [ ] 磁盘空间检查
- [ ] 性能优化
- [ ] 日志统计

---

## 15. API 文档

### AutoSaveSession

```kotlin
class AutoSaveSession(
    context: Context,
    originalFilePath: String? = null,
    originalFileUri: Uri? = null
) {
    val sessionId: String
    val timestamp: Long
    val fileName: String
    val contentLength: Int
    
    fun save(content: String, lastSavedContent: String, isModified: Boolean): Result<Unit>
    fun loadContent(): String?
    fun delete()
    fun updateLastAccessTime()
}
```

### AutoSaveManager

```kotlin
object AutoSaveManager {
    fun createSession(context: Context, filePath: String?, fileUri: Uri?): AutoSaveSession
    fun scanRecoverableSessions(context: Context): List<AutoSaveSession>
    fun cleanupExpiredSessions(context: Context, maxAgeDays: Int = 7)
    fun clearAll(context: Context)
}
```

---

*文档版本：1.0*
*最后更新：2024-01-01*
