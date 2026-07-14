package com.editor.nomadmark

/**
 * TocEntry - 目录条目数据类
 *
 * @property level 标题级别 (1-6, 对应 H1-H6)
 * @property title 标题文本
 * @property byteOffset 标题在文档中的字节偏移量 (用于滚动定位)
 * @property lineNumber 标题所在行号 (0-based, 可选用于显示)
 */
data class TocEntry(
    val level: Int,
    val title: String,
    val byteOffset: Long,
    val lineNumber: Int = 0
)
