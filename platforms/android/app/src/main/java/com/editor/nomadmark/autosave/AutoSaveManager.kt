package com.editor.nomadmark.autosave

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.Properties

/**
 * 自动保存管理器
 *
 * 单例模式，全局管理所有自动保存会话。
 * 功能：
 * - 创建新会话
 * - 扫描可恢复的会话
 * - 清理过期会话
 * - 记录自动保存状态
 */
object AutoSaveManager {

    private const val TAG = "AutoSaveManager"

    /** SharedPreferences 名称 */
    private const val PREFS_NAME = "autosave_prefs"

    /** SharedPreferences 键 */
    private const val KEY_HAS_AUTOSAVE = "has_autosave"
    private const val KEY_LAST_SESSION_ID = "last_session_id"
    private const val KEY_LAST_TIMESTAMP = "last_timestamp"

    /** 当前活动的会话 */
    private var currentSession: AutoSaveSession? = null

    /**
     * 创建新会话
     *
     * @param context 上下文
     * @param filePath 文件路径（可能为 null）
     * @param fileUri 文件 URI（可能为 null）
     * @return 新创建的会话
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

        Log.d(TAG, "创建新会话: ${session.sessionId}")
        return session
    }

    /**
     * 获取当前会话
     */
    fun getCurrentSession(): AutoSaveSession? = currentSession

    /**
     * 设置当前会话
     */
    fun setCurrentSession(session: AutoSaveSession?) {
        currentSession = session
    }

    /**
     * 扫描可恢复的会话
     *
     * @param context 上下文
     * @return 可恢复的会话列表（按时间倒序）
     */
    fun scanRecoverableSessions(context: Context): List<RecoverableSession> {
        val autosaveDir = File(context.cacheDir, "autosave")
        if (!autosaveDir.exists()) {
            Log.d(TAG, "临时目录不存在")
            return emptyList()
        }

        val sessions = mutableListOf<RecoverableSession>()

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
                val sessionId = metaFile.name
                    .removePrefix("meta_")
                    .removeSuffix(".properties")

                // 验证完整性
                val contentFile = File(autosaveDir, "content_$sessionId.md")
                if (!contentFile.exists()) {
                    Log.w(TAG, "内容文件缺失，清理无效会话: $sessionId")
                    cleanupSession(sessionId, context)
                    continue
                }

                // 读取元数据
                val metadata = readMetadata(metaFile)
                if (metadata == null || !metadata.isValid()) {
                    Log.w(TAG, "无效元数据，清理会话: $sessionId")
                    cleanupSession(sessionId, context)
                    continue
                }

                // 创建可恢复会话信息
                sessions.add(
                    RecoverableSession(
                        sessionId = sessionId,
                        metadata = metadata,
                        contentFile = contentFile,
                        autosaveDir = autosaveDir
                    )
                )

            } catch (e: Exception) {
                Log.e(TAG, "解析元数据失败: ${metaFile.name}", e)
                // 清理损坏的文件
                metaFile.deleteQuietly()
            }
        }

        Log.d(TAG, "扫描到 ${sessions.size} 个可恢复会话")

        // 按时间倒序排列
        return sessions.sortedByDescending { it.metadata.timestamp }
    }

    /**
     * 从 Properties 文件读取元数据
     */
    private fun readMetadata(metaFile: File): AutoSaveMetadata? {
        return try {
            val props = Properties()
            metaFile.bufferedReader().use { reader ->
                props.load(reader)
            }

            AutoSaveMetadata(
                id = props.getProperty(AutoSaveMetadata.KEY_ID) ?: "",
                timestamp = props.getProperty(AutoSaveMetadata.KEY_TIMESTAMP)?.toLong() ?: 0L,
                originalFilePath = props.getProperty(AutoSaveMetadata.KEY_ORIGINAL_PATH)
                    ?.takeIf { it.isNotEmpty() },
                originalFileUri = props.getProperty(AutoSaveMetadata.KEY_ORIGINAL_URI)
                    ?.takeIf { it.isNotEmpty() },
                fileName = props.getProperty(AutoSaveMetadata.KEY_FILE_NAME, "untitled"),
                contentLength = props.getProperty(AutoSaveMetadata.KEY_CONTENT_LENGTH, "0").toInt(),
                lastSavedHash = props.getProperty(AutoSaveMetadata.KEY_LAST_SAVED_HASH, "0").toInt(),
                isModified = props.getProperty(AutoSaveMetadata.KEY_IS_MODIFIED, "false").toBoolean(),
                appVersion = props.getProperty(AutoSaveMetadata.KEY_APP_VERSION, "unknown"),
                deviceInfo = props.getProperty(AutoSaveMetadata.KEY_DEVICE_INFO, "unknown")
            )
        } catch (e: Exception) {
            Log.e(TAG, "读取元数据失败: ${metaFile.absolutePath}", e)
            null
        }
    }

    /**
     * 清理过期会话
     *
     * @param context 上下文
     * @param maxAgeDays 最大保留天数（默认 7 天）
     * @return 清理的会话数量
     */
    fun cleanupExpiredSessions(context: Context, maxAgeDays: Int = 7): Int {
        val sessions = scanRecoverableSessions(context)
        val maxAgeMillis = maxAgeDays * 24L * 60L * 60L * 1000L
        val cutoffTime = System.currentTimeMillis() - maxAgeMillis

        val expiredSessions = sessions.filter { it.metadata.timestamp < cutoffTime }

        if (expiredSessions.isNotEmpty()) {
            Log.d(TAG, "清理 ${expiredSessions.size} 个过期会话（超过 $maxAgeDays 天）")
            expiredSessions.forEach { session ->
                cleanupSession(session.sessionId, context)
            }
        }

        return expiredSessions.size
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
        Log.d(TAG, "已清理会话: $sessionId")
    }

    /**
     * 清理所有会话
     */
    fun clearAll(context: Context) {
        val autosaveDir = File(context.cacheDir, "autosave")
        if (autosaveDir.exists()) {
            val deleted = autosaveDir.deleteRecursively()
            Log.d(TAG, "清理所有临时文件: $deleted")
            clearAutosaveFlag(context)
        }
    }

    /**
     * 记录有自动保存状态
     */
    fun markHasAutosave(context: Context, sessionId: String) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            putBoolean(KEY_HAS_AUTOSAVE, true)
            putString(KEY_LAST_SESSION_ID, sessionId)
            putLong(KEY_LAST_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
        Log.d(TAG, "标记自动保存状态: $sessionId")
    }

    /**
     * 清除自动保存状态
     */
    fun clearAutosaveFlag(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            remove(KEY_HAS_AUTOSAVE)
            remove(KEY_LAST_SESSION_ID)
            remove(KEY_LAST_TIMESTAMP)
            apply()
        }
        Log.d(TAG, "清除自动保存状态")
    }

    /**
     * 检查是否有自动保存
     */
    fun hasAutosave(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.getBoolean(KEY_HAS_AUTOSAVE, false)
    }

    /**
     * 获取上次会话 ID
     */
    fun getLastSessionId(context: Context): String? {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_LAST_SESSION_ID, null)
    }

    /**
     * 获取 SharedPreferences
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}

/**
 * 可恢复会话信息
 *
 * 用于恢复对话框显示和用户选择。
 */
data class RecoverableSession(
    val sessionId: String,
    val metadata: AutoSaveMetadata,
    private val contentFile: File,
    private val autosaveDir: File
) {
    /**
     * 读取内容
     */
    fun loadContent(): String? {
        return contentFile.readTextQuietly()
    }

    /**
     * 删除会话文件
     */
    fun delete() {
        arrayOf(
            "meta_$sessionId.properties",
            "content_$sessionId.md",
            ".last_access_$sessionId"
        ).forEach { name ->
            File(autosaveDir, name).deleteQuietly()
        }
    }

    /**
     * 格式化时间
     */
    fun getFormattedTime(): String {
        val date = java.util.Date(metadata.timestamp)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }

    /**
     * 格式化大小
     */
    fun getFormattedSize(): String {
        return contentFile.getFormattedSize()
    }
}
