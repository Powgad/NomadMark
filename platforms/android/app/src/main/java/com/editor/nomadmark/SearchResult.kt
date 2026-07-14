package com.editor.nomadmark

/**
 * SearchResult - 搜索结果数据类
 *
 * @property line 匹配行号 (0-based)
 * @property context 匹配行的上下文预览文本
 * @property startColumn 匹配内容在行中的起始列位置
 * @property endColumn 匹配内容在行中的结束列位置
 */
data class SearchResult(
    val line: Int,
    val context: String,
    val startColumn: Int = 0,
    val endColumn: Int = context.length
)
