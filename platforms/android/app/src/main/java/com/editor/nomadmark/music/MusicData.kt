package com.editor.nomadmark.music

import java.io.Serializable

/**
 * 乐谱数据模型
 */
data class MusicData(
    val id: String = generateId(),
    val type: MusicType,
    val content: String,
    val title: String? = null,
    val composer: String? = null,
    val tempo: Int = 120,
    val key: String? = null,
    val sourceRange: IntRange = 0..0  // 在原文中的位置
) : Serializable {

    companion object {
        fun generateId(): String = "music_${System.currentTimeMillis()}"
    }

    /**
     * 获取缓存键
     *
     * @param width 渲染宽度，纳入 key 以区分不同屏宽（预览/分屏）下的渲染结果
     */
    fun getCacheKey(width: Int): String {
        // 添加版本号以在渲染参数变化后使缓存失效
        val version = "v2"  // 修改此值可强制所有缓存失效
        return "${type.name}_${content.hashCode()}_${width}_$version"
    }
}

/**
 * 乐谱类型
 */
enum class MusicType {
    ABC,        // ABC 记谱法 (```music 或 ```abc)
    JIANPU,     // 简谱 (```简谱)
    UNKNOWN;

    companion object {
        fun fromLanguage(lang: String?): MusicType {
            return when (lang?.lowercase()) {
                "music", "abc" -> ABC
                "简谱", "jianpu", "numbered" -> JIANPU
                else -> UNKNOWN
            }
        }
    }
}
