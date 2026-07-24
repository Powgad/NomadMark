package com.editor.nomadmark.music

import android.text.Spanned
import android.util.Log

/**
 * 乐谱块检测器
 *
 * 检测 Markdown 中的 ```music 和 ```简谱 代码块
 */
object MusicSheetDetector {

    private const val TAG = "MusicSheetDetector"

    /**
     * 乐谱块信息
     */
    data class MusicBlock(
        val musicData: MusicData,
        val blockStart: Int,
        val blockEnd: Int
    )

    /**
     * 从原始 Markdown 文本中检测乐谱块
     */
    fun detectMusicSheetsFromMarkdown(markdown: String): List<MusicBlock> {
        val musicSheets = mutableListOf<MusicBlock>()

        // 检测 ```music 或 ```abc 或 ```简谱 代码块
        val fencedCodePattern = """```(music|abc|简谱|jianpu)\s*\n([\s\S]*?)```""".toRegex(RegexOption.IGNORE_CASE)

        fencedCodePattern.findAll(markdown).forEach { match ->
            val language = match.groupValues[1].lowercase()
            val content = match.groupValues[2].trim()

            // 跳过空内容
            if (content.isEmpty()) return@forEach

            val type = when (language) {
                "music", "abc" -> MusicType.ABC
                "简谱", "jianpu" -> MusicType.JIANPU
                else -> MusicType.UNKNOWN
            }

            if (type == MusicType.UNKNOWN) return@forEach

            // 解析 ABC 元数据
            val metadata = parseAbcMetadata(content)

            val musicData = MusicData(
                type = type,
                content = content,
                title = metadata.title,
                composer = metadata.composer,
                tempo = metadata.tempo,
                key = metadata.key,
                sourceRange = match.range.first..match.range.last
            )

            musicSheets.add(MusicBlock(
                musicData = musicData,
                blockStart = match.range.first,
                blockEnd = match.range.last + 1
            ))

            Log.d(TAG, "检测到乐谱块: [${match.range.first}-${match.range.last}], type=$type, title=${metadata.title}")
        }

        Log.d(TAG, "共检测到 ${musicSheets.size} 个乐谱块")
        return musicSheets
    }

    /**
     * 检测文本中的所有乐谱块
     */
    fun detectMusicSheets(spanned: Spanned): List<MusicBlock> {
        val musicSheets = mutableListOf<MusicBlock>()
        val text = spanned.toString()

        // 检测 ```music 或 ```abc 或 ```简谱 代码块
        val fencedCodePattern = """```(music|abc|简谱|jianpu)\s*\n([\s\S]*?)```""".toRegex(RegexOption.IGNORE_CASE)

        fencedCodePattern.findAll(text).forEach { match ->
            val language = match.groupValues[1].lowercase()
            val content = match.groupValues[2].trim()

            // 跳过空内容
            if (content.isEmpty()) return@forEach

            val type = when (language) {
                "music", "abc" -> MusicType.ABC
                "简谱", "jianpu" -> MusicType.JIANPU
                else -> MusicType.UNKNOWN
            }

            if (type == MusicType.UNKNOWN) return@forEach

            // 解析 ABC 元数据
            val metadata = parseAbcMetadata(content)

            val musicData = MusicData(
                type = type,
                content = content,
                title = metadata.title,
                composer = metadata.composer,
                tempo = metadata.tempo,
                key = metadata.key,
                sourceRange = match.range.first..match.range.last
            )

            musicSheets.add(MusicBlock(
                musicData = musicData,
                blockStart = match.range.first,
                blockEnd = match.range.last + 1
            ))

            Log.d(TAG, "检测到乐谱块: [${match.range.first}-${match.range.last}], type=$type, title=${metadata.title}")
        }

        Log.d(TAG, "共检测到 ${musicSheets.size} 个乐谱块")
        return musicSheets
    }

    /**
     * 解析 ABC 元数据
     */
    private data class AbcMetadata(
        val title: String? = null,
        val composer: String? = null,
        val tempo: Int = 120,
        val key: String? = null
    )

    private fun parseAbcMetadata(content: String): AbcMetadata {
        var title: String? = null
        var composer: String? = null
        var tempo = 120
        var key: String? = null

        content.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("T:", ignoreCase = true) -> title = trimmed.substring(2).trim()
                trimmed.startsWith("C:", ignoreCase = true) -> composer = trimmed.substring(2).trim()
                trimmed.startsWith("Q:", ignoreCase = true) -> {
                    val tempoStr = trimmed.substring(2).trim().split(" ").first()
                    tempo = tempoStr.toIntOrNull() ?: 120
                }
                trimmed.startsWith("K:", ignoreCase = true) -> key = trimmed.substring(2).trim()
            }
        }

        return AbcMetadata(title, composer, tempo, key)
    }
}
