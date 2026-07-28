package com.editor.nomadmark.music

import android.util.Log

/**
 * 简谱到 ABC 记谱法转换器
 *
 * 支持的简谱格式：
 * - 调号：1=C, 1=D, 1=Bb 等
 * - 拍号：4/4, 3/4, 2/4, 6/8 等
 * - 音符：1 2 3 4 5 6 7
 * - 高八度：1̇ 2̇（加点）或 1' 2'（单引号）
 * - 低八度：Ϳ1 Ϳ2（下加点）或 1, 2,（逗号）
 * - 时值：- 表示延长（如 5- 表示二分音符）
 * - 休止符：0 或 0-
 * - 小节线：|
 * - 反复记号：|: 和 :|
 * - 连音符：1-2-（延音线）
 *
 * 示例：
 * ```
 * 简谱: 1=C 4/4
 *       1 1 5 5 | 6 6 5 - |
 *
 * ABC:  X: 1
 *       M: 4/4
 *       K: C
 *       C C G G | A A G2 |
 * ```
 */
object JianpuConverter {

    private const val TAG = "JianpuConverter"

    /**
     * 简谱音符到 ABC 音符映射
     */
    private val NOTE_MAP = mapOf(
        '1' to 'C',
        '2' to 'D',
        '3' to 'E',
        '4' to 'F',
        '5' to 'G',
        '6' to 'A',
        '7' to 'B'
    )

    /**
     * 调号映射：简谱的 "1=调名" → ABC 的 "K:调名"
     */
    private val KEY_MAP = mapOf(
        "C" to "C",
        "D" to "D",
        "E" to "E",
        "F" to "F",
        "G" to "G",
        "A" to "A",
        "B" to "B",
        "Bb" to "Bb",
        "Eb" to "Eb",
        "Ab" to "Ab",
        "Db" to "Db",
        "Gs" to "G#",
        "Cs" to "C#",
        "Fs" to "F#",
        "As" to "A#",
        "Ds" to "D#",
        "Es" to "E#",
        "Bs" to "B#",
        "Am" to "Am",
        "Dm" to "Dm",
        "Em" to "Em",
        "Fm" to "Fm",
        "Gm" to "Gm",
        "Bm" to "Bm"
    )

    /**
     * 转换结果
     */
    data class ConversionResult(
        val abc: String,
        val title: String? = null,
        val warnings: List<String> = emptyList()
    )

    /**
     * 将简谱转换为 ABC 记谱法
     *
     * @param jianpuCode 简谱代码
     * @param id 乐谱 ID（用于 ABC X: 字段）
     * @return ConversionResult 包含转换后的 ABC 代码和警告信息
     */
    fun convert(jianpuCode: String, id: String = "1"): ConversionResult {
        val lines = jianpuCode.lines()
        val warnings = mutableListOf<String>()
        val abcLines = mutableListOf<String>()

        // ABC 头部
        abcLines.add("X: $id")

        var title: String? = null
        var keyAdded = false
        var meterAdded = false
        var tempo = 120

        // 第一行：解析调号和拍号
        val firstLine = lines.firstOrNull { it.trim().isNotEmpty() } ?: ""
        if (firstLine.contains("1=")) {
            val key = parseKey(firstLine)
            if (key != null) {
                abcLines.add("K: $key")
                keyAdded = true
            }
        }
        if (firstLine.contains("/") && !firstLine.contains("1=")) {
            // 拍号格式: 4/4, 3/4 等
            val meter = parseMeter(firstLine)
            if (meter != null) {
                abcLines.add("M: $meter")
                meterAdded = true
            }
        } else if (firstLine.contains("1=") && firstLine.contains("/")) {
            // 同一行包含调号和拍号: "1=C 4/4"
            val meter = parseMeter(firstLine.substringAfter("1="))
            if (meter != null) {
                abcLines.add("M: $meter")
                meterAdded = true
            }
        }

        // 如果没有调号，默认 C 大调
        if (!keyAdded) {
            abcLines.add("K: C")
        }

        // 如果没有拍号，默认 4/4
        if (!meterAdded) {
            abcLines.add("M: 4/4")
        }

        // 添加默认速度
        abcLines.add("Q: $tempo")
        abcLines.add("L: 1/4")

        // 解析标题（如果有 T: 开头的行）
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("T:", ignoreCase = true)) {
                title = trimmed.substring(2).trim()
            }
        }

        if (title != null) {
            abcLines.add("T: $title")
        }

        // 转换音符行
        val melodyLines = if (lines.size > 1 && lines.first().contains("1=")) {
            lines.drop(1)
        } else {
            lines
        }

        melodyLines.forEach { line ->
            val trimmed = line.trim()
            // 跳过空行和标题行
            if (trimmed.isEmpty() || trimmed.startsWith("T:", ignoreCase = true) ||
                trimmed.startsWith("C:", ignoreCase = true) || trimmed.startsWith("Q:", ignoreCase = true)) {
                return@forEach
            }
            val abcLine = convertLine(trimmed, warnings)
            abcLines.add(abcLine)
        }

        val abc = abcLines.joinToString("\n")
        Log.d(TAG, "简谱转换完成: ${abc.lines().size} 行, warnings: ${warnings.size}")

        return ConversionResult(abc, title, warnings)
    }

    /**
     * 解析调号
     * 输入: "1=C", "1=Bb", "1=Am" 等
     * 输出: "C", "Bb", "Am" 等
     */
    private fun parseKey(line: String): String? {
        val match = Regex("""1\s*=\s*([A-G][b#]?m?)""").find(line)
        return match?.groupValues?.get(1)?.let { KEY_MAP[it] } ?: run {
            Log.w(TAG, "无法解析调号: $line")
            null
        }
    }

    /**
     * 解析拍号
     * 输入: "4/4", "3/4", "6/8" 等
     * 输出: "4/4", "3/4", "6/8" 等
     */
    private fun parseMeter(line: String): String? {
        val match = Regex("""(\d+)/(\d+)""").find(line)
        return match?.groupValues?.get(0)
    }

    /**
     * 转换单行简谱为 ABC
     */
    private fun convertLine(line: String, warnings: MutableList<String>): String {
        val result = StringBuilder()
        var i = 0

        while (i < line.length) {
            val char = line[i]

            when (char) {
                // 跳过空格
                ' ' -> {
                    // 保留一个空格用于分隔音符
                    if (result.isNotEmpty() && result.last() != ' ') {
                        result.append(' ')
                    }
                }
                // 小节线保留
                '|' -> {
                    // 处理反复记号
                    if (i + 1 < line.length && line[i + 1] == ':') {
                        result.append("|:")
                        i++
                    } else if (i > 0 && line[i - 1] == ':') {
                        // 已经处理过了
                        result.append("|")
                    } else {
                        result.append("|")
                    }
                }
                ':' -> {
                    // 处理反复记号的起始
                    if (i + 1 < line.length && line[i + 1] == '|') {
                        result.append("|:")
                        i++
                    }
                }
                // 休止符
                '0' -> {
                    result.append('z')
                    // 检查后续的时值标记
                    if (i + 1 < line.length && line[i + 1] == '-') {
                        result.append('2')
                        i++
                    }
                }
                // 音符数字 1-7
                in '1'..'7' -> {
                    // 检查八度标记
                    var octaveShift = 0

                    // 检查前面的加点（高八度）
                    if (i > 0 && line[i - 1] == '̇' || i > 0 && line[i - 1] == '\'') {
                        octaveShift = 1
                    }
                    // 检查前面的下加点（低八度）
                    if (i > 0 && line[i - 1] == 'Ϳ' || i > 0 && line[i - 1] == ',') {
                        octaveShift = -1
                    }

                    // 转换为 ABC 音符
                    val abcNote = NOTE_MAP[char]
                    if (abcNote != null) {
                        when (octaveShift) {
                            1 -> result.append(abcNote.lowercaseChar())  // 高八度用小写
                            -1 -> {
                                result.append(abcNote)
                                result.append(',')  // 低八度加逗号
                            }
                            else -> result.append(abcNote)
                        }

                        // 检查时值标记（减号表示延长）
                        var duration = 0
                        while (i + 1 < line.length && line[i + 1] == '-') {
                            duration++
                            i++
                        }

                        // ABC 记谱法中，数字表示音符长度
                        // 默认四分音符（L:1/4），减号数量 +1 表示长度倍数
                        if (duration > 0) {
                            result.append(duration + 1)
                        }
                    }
                }
                // 节奏型符号（下划线表示缩短）
                '_' -> {
                    // 下划线表示半拍，在 ABC 中用 / 表示
                    if (result.isNotEmpty() && result.last() in 'A'..'G' || result.last() in 'a'..'g') {
                        // 在音符后插入 /
                        val lastChar = result.last()
                        result.deleteCharAt(result.length - 1)
                        result.append(lastChar)
                        result.append("/2")
                    }
                }
                // 延音线
                '-' -> {
                    // 如果在音符后面，且不是时值标记
                    if (result.isNotEmpty() && result.last() !in '0'..'9') {
                        // ABC 中用 - 表示连音
                        result.append(" -")
                    } else if (result.isEmpty() || result.last() in '0'..'9') {
                        // 这是时值标记的一部分，前面已处理
                    }
                }
                else -> {
                    // 其他字符保留或警告
                    if (char !in listOf('\n', '\r', '\t')) {
                        warnings.add("未识别的字符: '$char' (行: ${line.take(20)})")
                    }
                }
            }

            i++
        }

        return result.toString().trim()
    }
}
