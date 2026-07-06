package com.editor.nomadmark

import android.widget.ScrollView

/**
 * 滚动同步管理器
 *
 * 功能：
 * - 编辑区滚动时同步预览区
 * - 预览区滚动时同步编辑区
 * - 计算滚动位置对应关系
 *
 * 改进：使用 ObservableScrollView 的 onScrollChanged 回调，
 * 确保鼠标滚轮事件能够被正确捕获。
 */
class ScrollSyncManager(
    private val editorScrollView: ScrollView,
    private val previewScrollView: ScrollView
) {

    private var isSyncing = false
    private var syncEnabled = true

    /**
     * 启用滚动同步
     */
    fun enable() {
        syncEnabled = true
        setupListeners()
    }

    /**
     * 禁用滚动同步
     */
    fun disable() {
        syncEnabled = false
    }

    /**
     * 设置监听器
     * 使用 ObservableScrollView 的 onScrollChanged 回调，确保捕获所有滚动事件
     */
    private fun setupListeners() {
        // 尝试将 ScrollView 转换为 ObservableScrollView
        if (editorScrollView is ObservableScrollView) {
            editorScrollView.onScrollChangedCallback = { scrollX, scrollY, oldScrollX, oldScrollY ->
                if (syncEnabled && !isSyncing) {
                    isSyncing = true
                    syncEditorToPreview()
                    isSyncing = false
                }
            }
        } else {
            // 降级到 viewTreeObserver (可能无法捕获鼠标滚轮事件)
            editorScrollView.viewTreeObserver?.addOnScrollChangedListener {
                if (syncEnabled && !isSyncing) {
                    isSyncing = true
                    syncEditorToPreview()
                    isSyncing = false
                }
            }
        }

        if (previewScrollView is ObservableScrollView) {
            previewScrollView.onScrollChangedCallback = { scrollX, scrollY, oldScrollX, oldScrollY ->
                if (syncEnabled && !isSyncing) {
                    isSyncing = true
                    syncPreviewToEditor()
                    isSyncing = false
                }
            }
        } else {
            // 降级到 viewTreeObserver (可能无法捕获鼠标滚轮事件)
            previewScrollView.viewTreeObserver?.addOnScrollChangedListener {
                if (syncEnabled && !isSyncing) {
                    isSyncing = true
                    syncPreviewToEditor()
                    isSyncing = false
                }
            }
        }
    }

    /**
     * 编辑区滚动同步到预览区
     */
    private fun syncEditorToPreview() {
        val editorScrollY = editorScrollView.scrollY
        val previewScrollY = calculatePreviewPosition(editorScrollY)
        previewScrollView.smoothScrollTo(0, previewScrollY)
    }

    /**
     * 预览区滚动同步到编辑区
     */
    private fun syncPreviewToEditor() {
        val previewScrollY = previewScrollView.scrollY
        val editorScrollY = calculateEditorPosition(previewScrollY)
        editorScrollView.smoothScrollTo(0, editorScrollY)
    }

    /**
     * 计算编辑区滚动位置对应的预览区位置
     */
    private fun calculatePreviewPosition(editorScrollY: Int): Int {
        // 简化处理：按比例计算
        val editorTotalHeight = editorScrollView.getChildAt(0)?.height ?: 0
        val previewTotalHeight = previewScrollView.getChildAt(0)?.height ?: 0

        if (editorTotalHeight > 0 && previewTotalHeight > 0) {
            return (editorScrollY.toFloat() / editorTotalHeight * previewTotalHeight).toInt()
        }

        return editorScrollY
    }

    /**
     * 计算预览区滚动位置对应的编辑区位置
     */
    private fun calculateEditorPosition(previewScrollY: Int): Int {
        // 简化处理：按比例计算
        val editorTotalHeight = editorScrollView.getChildAt(0)?.height ?: 0
        val previewTotalHeight = previewScrollView.getChildAt(0)?.height ?: 0

        if (previewTotalHeight > 0 && editorTotalHeight > 0) {
            return (previewScrollY.toFloat() / previewTotalHeight * editorTotalHeight).toInt()
        }

        return previewScrollY
    }

    /**
     * 检查是否已启用同步
     */
    fun isEnabled(): Boolean = syncEnabled
}
