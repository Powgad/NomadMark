package com.editor.nomadmark.markwon

/**
 * 乐谱数据模型
 *
 * 用于存储乐谱的内容和元数据，支持 ABC 记谱法和简谱两种格式。
 *
 * @property id 唯一标识符
 * @property type 乐谱类型（ABC 或简谱）
 * @property content 乐谱原始内容
 * @property title 标题（可选）
 * @property composer 作曲者（可选）
 * @property tempo 速度（BPM），默认 120
 * @property key 调性（如 C Major、A Minor）
 * @property audioPath 关联的音频文件路径（可选）
 */
data class MusicData(
    val id: String,
    val type: MusicType,
    val content: String,
    val title: String? = null,
    val composer: String? = null,
    val tempo: Int = 120,
    val key: String? = null,
    val audioPath: String? = null
) {
    /**
     * 是否有关联的音频文件
     */
    val hasAudio: Boolean
        get() = !audioPath.isNullOrEmpty()

    companion object {
        /**
         * 生成唯一 ID
         */
        fun generateId(): String {
            return "music_${System.currentTimeMillis()}"
        }
    }
}

/**
 * 乐谱类型枚举
 */
enum class MusicType {
    /** ABC 记谱法 */
    ABC,

    /** 简谱（数字谱） */
    JIANPU;

    /**
     * 获取代码块语言标识符
     */
    fun toLanguageIdentifier(): String {
        return when (this) {
            ABC -> "music"
            JIANPU -> "简谱"
        }
    }

    companion object {
        /**
         * 从语言标识符解析类型
         */
        fun fromLanguage(lang: String?): MusicType {
            return when (lang?.lowercase()) {
                "abc", "music" -> ABC
                "简谱", "jianpu", "numbered" -> JIANPU
                else -> ABC  // 默认为 ABC
            }
        }

        /**
         * 从类型字符串创建 MusicType
         */
        fun fromTypeString(type: String): MusicType? {
            return when (type.lowercase()) {
                "abc", "music" -> ABC
                "简谱", "jianpu", "numbered" -> JIANPU
                else -> null
            }
        }
    }
}
