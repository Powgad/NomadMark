package com.editor.nomadmark.autosave

/**
 * 自动保存元数据
 *
 * 存储自动保存会话的所有元信息，用于恢复和清理判断。
 * 数据通过 Properties 格式持久化到文件。
 *
 * @property id 唯一标识符（UUID）
 * @property timestamp 保存时间戳（毫秒）
 * @property originalFilePath 原始文件路径（可能为 null）
 * @property originalFileUri 原始文件 URI（可能为 null）
 * @property fileName 文件名（不含扩展名）
 * @property contentLength 内容长度（字符数）
 * @property lastSavedHash 上次保存内容的哈希值（用于变更检测）
 * @property isModified 是否有未保存的修改
 * @property appVersion 应用版本号
 * @property deviceInfo 设备信息
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
) {
    companion object {
        /** 元数据格式版本 */
        const val VERSION = "1"

        /** Properties 文件中的键名常量 */
        const val KEY_VERSION = "version"
        const val KEY_ID = "id"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_ORIGINAL_PATH = "originalFilePath"
        const val KEY_ORIGINAL_URI = "originalFileUri"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_CONTENT_LENGTH = "contentLength"
        const val KEY_LAST_SAVED_HASH = "lastSavedHash"
        const val KEY_IS_MODIFIED = "isModified"
        const val KEY_APP_VERSION = "appVersion"
        const val KEY_DEVICE_INFO = "deviceInfo"
    }

    /**
     * 判断元数据是否有效
     */
    fun isValid(): Boolean {
        return id.isNotEmpty() &&
                timestamp > 0 &&
                fileName.isNotEmpty() &&
                contentLength >= 0
    }

    /**
     * 判断是否过期
     * @param maxAgeMillis 最大保留时长（毫秒）
     */
    fun isExpired(maxAgeMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        return (now - timestamp) > maxAgeMillis
    }
}
