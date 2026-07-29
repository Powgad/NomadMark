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
    val noteIndex: Int,

    /**
     * 音符在乐谱 Bitmap 上的位置（归一化坐标 0-1）
     * 用于绘制高亮指示器
     */
    val position: NotePosition? = null
) {
    /**
     * 音符位置信息（归一化坐标）
     */
    data class NotePosition(
        val x: Float,      // 0-1，相对于 Bitmap 宽度
        val y: Float,      // 0-1，相对于 Bitmap 高度
        val width: Float,  // 0-1，音符宽度占 Bitmap 宽度的比例
        val height: Float  // 0-1，音符高度占 Bitmap 高度的比例
    )
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
        /**
         * 从 JavaScript 返回的 JSON 数组创建 NoteEvent 列表
         *
         * JavaScript 端应返回格式：
         * [
         *   {"time": 0, "id": "abcjs-l1-m1", "index": 0, "x": 0.1, "y": 0.2, "w": 0.05, "h": 0.05},
         *   {"time": 500, "id": "abcjs-l1-m2", "index": 1, "x": 0.15, "y": 0.2, "w": 0.05, "h": 0.05},
         *   ...
         * ]
         */
        fun fromJsonArray(jsonString: String): List<NoteEvent> {
            if (jsonString.isBlank() || jsonString == "null" || jsonString == "[]") {
                return emptyList()
            }

            return try {
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
                            // 解析位置信息（可选）
                            val xMatch = Regex(""""x"\s*:\s*([\d.]+)""").find(cleanItem)
                            val yMatch = Regex(""""y"\s*:\s*([\d.]+)""").find(cleanItem)
                            val wMatch = Regex(""""w"\s*:\s*([\d.]+)""").find(cleanItem)
                            val hMatch = Regex(""""h"\s*:\s*([\d.]+)""").find(cleanItem)

                            val position = if (xMatch != null && yMatch != null) {
                                NotePosition(
                                    x = xMatch.groupValues[1].toFloat(),
                                    y = yMatch.groupValues[1].toFloat(),
                                    width = wMatch?.groupValues?.get(1)?.toFloat() ?: 0.05f,
                                    height = hMatch?.groupValues?.get(1)?.toFloat() ?: 0.05f
                                )
                            } else null

                            result.add(NoteEvent(
                                timeMillis = timeMatch.groupValues[1].toInt(),
                                elementId = idMatch.groupValues[1],
                                noteIndex = index++,
                                position = position
                            ))
                        }
                    }
                }
                result
            } catch (e: Exception) {
                android.util.Log.e("NoteEvent", "解析音符事件失败", e)
                emptyList()
            }
        }
    }
}
