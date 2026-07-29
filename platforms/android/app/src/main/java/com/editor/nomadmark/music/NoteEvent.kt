package com.editor.nomadmark.music

/**
 * 音符事件数据（用于视觉播放）
 *
 * 表示在乐谱播放过程中某个音符的时间位置和对应的 SVG 元素信息
 */
data class NoteEvent(
    /**
     * 从开始到该音符的时间（毫秒）
     */
    val timeMillis: Int,

    /**
     * SVG 元素 ID（用于高亮显示）
     * 格式通常为 "abcjs-l{n}-m{i}-k{n}" 或类似的 abcjs 生成的 ID
     */
    val elementId: String,

    /**
     * 音符序号（0-based）
     */
    val noteIndex: Int
) {
    companion object {
        /**
         * 从 JavaScript 返回的 JSON 数组创建 NoteEvent 列表
         *
         * JavaScript 端应返回格式：
         * [
         *   {"time": 0, "id": "abcjs-l1-m1", "index": 0},
         *   {"time": 500, "id": "abcjs-l1-m2", "index": 1},
         *   ...
         * ]
         */
        fun fromJsonArray(jsonString: String): List<NoteEvent> {
            if (jsonString.isBlank() || jsonString == "null" || jsonString == "[]") {
                return emptyList()
            }

            return try {
                // 简单的 JSON 解析（不依赖外部库）
                val result = mutableListOf<NoteEvent>()
                val content = jsonString.trim()
                if (content.startsWith("[") && content.endsWith("]")) {
                    val items = content.substring(1, content.length - 1)
                        .split("},")
                    var index = 0
                    for (item in items) {
                        val cleanItem = item.trim().trimEnd('}')
                        val timeMatch = Regex(""""time"\s*:\s*(\d+)""").find(cleanItem)
                        val idMatch = Regex(""""id"\s*:\s*"([^"]+)""").find(cleanItem)
                        if (timeMatch != null && idMatch != null) {
                            result.add(NoteEvent(
                                timeMillis = timeMatch.groupValues[1].toInt(),
                                elementId = idMatch.groupValues[1],
                                noteIndex = index++
                            ))
                        }
                    }
                }
                result
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
