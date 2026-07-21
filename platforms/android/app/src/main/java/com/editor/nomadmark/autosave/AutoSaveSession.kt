package com.editor.nomadmark.autosave

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.Properties
import java.util.UUID

/**
 * 自动保存会话
 *
 * 管理单个编辑会话的临时文件存储和恢复。
 * 每个会话包含：
 * - 元数据文件 (.properties)：存储文件信息
 * - 内容文件 (.md)：存储编辑内容
 * - 访问时间文件：记录最后访问时间
 *
 * @property context 上下文
 * @property originalFilePath 原始文件路径（可能为 null）
 * @property originalFileUri 原始文件 URI（可能为 null）
 */
class AutoSaveSession(
    private val context: Context,
    private val originalFilePath: String? = null,
    private val originalFileUri: Uri? = null
) {
    companion object {
        private const val TAG = "AutoSaveSession"
    }

    /** 唯一标识符 */
    val sessionId: String = UUID.randomUUID().toString()

    /** 临时文件目录 */
    private val autosaveDir: File = File(context.cacheDir, "autosave")

    /** 元数据文件 */
    private val metaFile: File = File(autosaveDir, "meta_$sessionId.properties")

    /** 内容文件 */
    val contentFile: File = File(autosaveDir, "content_$sessionId.md")

    /** 最后访问时间戳文件 */
    private val lastAccessFile: File = File(autosaveDir, ".last_access_$sessionId")

    /** 从文件加载的元数据缓存 */
    private var cachedMetadata: AutoSaveMetadata? = null

    /**
     * 保存内容到临时文件
     *
     * @param content 当前编辑内容
     * @param lastSavedContent 上次保存的内容
     * @param isModified 是否有修改
     * @return 是否保存成功
     */
    fun save(content: String, lastSavedContent: String, isModified: Boolean): Boolean {
        return try {
            // 确保目录存在
            if (!autosaveDir.exists()) {
                autosaveDir.mkdirs()
            }

            // 保存内容
            if (!contentFile.writeTextQuietly(content)) {
                Log.e(TAG, "保存内容文件失败")
                return false
            }

            // 保存元数据
            val metadata = createMetadata(content, lastSavedContent, isModified)
            if (!writeMetadata(metadata)) {
                Log.e(TAG, "保存元数据失败")
                return false
            }

            // 更新访问时间
            updateLastAccessTime()

            Log.d(TAG, "自动保存成功: ${metadata.fileName} (${content.length} 字符)")
            cachedMetadata = metadata
            true
        } catch (e: Exception) {
            Log.e(TAG, "自动保存异常", e)
            false
        }
    }

    /**
     * 创建元数据对象
     */
    private fun createMetadata(
        content: String,
        lastSavedContent: String,
        isModified: Boolean
    ): AutoSaveMetadata {
        return AutoSaveMetadata(
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
    }

    /**
     * 写入元数据到 Properties 文件
     */
    private fun writeMetadata(metadata: AutoSaveMetadata): Boolean {
        return try {
            val props = Properties().apply {
                setProperty(AutoSaveMetadata.KEY_VERSION, AutoSaveMetadata.VERSION)
                setProperty(AutoSaveMetadata.KEY_ID, metadata.id)
                setProperty(AutoSaveMetadata.KEY_TIMESTAMP, metadata.timestamp.toString())
                setProperty(AutoSaveMetadata.KEY_ORIGINAL_PATH, metadata.originalFilePath ?: "")
                setProperty(AutoSaveMetadata.KEY_ORIGINAL_URI, metadata.originalFileUri ?: "")
                setProperty(AutoSaveMetadata.KEY_FILE_NAME, metadata.fileName)
                setProperty(AutoSaveMetadata.KEY_CONTENT_LENGTH, metadata.contentLength.toString())
                setProperty(AutoSaveMetadata.KEY_LAST_SAVED_HASH, metadata.lastSavedHash.toString())
                setProperty(AutoSaveMetadata.KEY_IS_MODIFIED, metadata.isModified.toString())
                setProperty(AutoSaveMetadata.KEY_APP_VERSION, metadata.appVersion)
                setProperty(AutoSaveMetadata.KEY_DEVICE_INFO, metadata.deviceInfo)
            }

            metaFile.bufferedWriter().use { writer ->
                props.store(writer, "AutoSave Metadata")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "写入元数据文件失败", e)
            false
        }
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
            ).let { metadata ->
                if (metadata.isValid()) {
                    cachedMetadata = metadata
                    metadata
                } else {
                    Log.w(TAG, "无效的元数据: $metadata")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取元数据失败", e)
            null
        }
    }

    /**
     * 更新访问时间（用于清理判断）
     */
    fun updateLastAccessTime() {
        lastAccessFile.writeTextQuietly(System.currentTimeMillis().toString())
    }

    /**
     * 删除临时文件
     */
    fun delete() {
        metaFile.deleteQuietly()
        contentFile.deleteQuietly()
        lastAccessFile.deleteQuietly()
        cachedMetadata = null
        Log.d(TAG, "临时文件已删除: $sessionId")
    }

    /**
     * 读取保存的内容
     */
    fun loadContent(): String? {
        return contentFile.readTextQuietly()
    }

    /**
     * 获取元数据属性
     */

    /** 文件名 */
    val fileName: String
        get() = readMetadata()?.fileName ?: "unknown"

    /** 时间戳 */
    val timestamp: Long
        get() = readMetadata()?.timestamp ?: 0L

    /** 内容长度 */
    val contentLength: Int
        get() = readMetadata()?.contentLength ?: 0

    /** 原始文件路径 */
    val originalFilePathValue: String?
        get() = readMetadata()?.originalFilePath

    /** 原始文件 URI */
    val originalFileUriValue: String?
        get() = readMetadata()?.originalFileUri?.let { Uri.parse(it).toString() }

    /** 是否有修改 */
    val isModified: Boolean
        get() = readMetadata()?.isModified ?: false

    /** 上次保存哈希 */
    val lastSavedHash: Int?
        get() = readMetadata()?.lastSavedHash

    /**
     * 辅助方法：获取应用版本
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            Log.w(TAG, "获取应用版本失败", e)
            "unknown"
        }
    }

    /**
     * 辅助方法：获取设备信息
     */
    private fun getDeviceInfo(): String {
        return "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    }
}
