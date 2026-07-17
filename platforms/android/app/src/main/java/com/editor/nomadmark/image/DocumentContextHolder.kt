package com.editor.nomadmark.image

import java.io.File

/**
 * 文档上下文持有者（单例）
 *
 * 用于存储当前正在编辑的文档目录，以便图片加载器可以解析相对路径。
 */
object DocumentContextHolder {

    /** 当前文档所在目录 */
    private var currentDocumentDir: File? = null

    /**
     * 设置当前文档目录
     *
     * @param filePath 文档的完整文件路径
     */
    fun setCurrentDocument(filePath: String?) {
        currentDocumentDir = if (filePath != null) {
            File(filePath).parentFile
        } else {
            null
        }
    }

    /**
     * 获取当前文档目录
     *
     * @return 当前文档目录，如果未设置则返回 null
     */
    fun getCurrentDocumentDir(): File? = currentDocumentDir

    /**
     * 清除当前文档目录
     */
    fun clear() {
        currentDocumentDir = null
    }

    /**
     * 解析相对路径为完整文件路径
     *
     * @param relativePath 相对路径（如 "11.jpg" 或 "./11.jpg"）
     * @return 完整文件路径，如果无法解析则返回 null
     */
    fun resolveRelativePath(relativePath: String): File? {
        val docDir = currentDocumentDir ?: return null

        // 移除可能的 ./ 前缀
        val cleanPath = if (relativePath.startsWith("./")) {
            relativePath.substring(2)
        } else {
            relativePath
        }

        // 不处理包含 .. 的路径（安全考虑）
        if (cleanPath.contains("..")) {
            return null
        }

        val file = File(docDir, cleanPath)
        return if (file.exists()) file else null
    }
}
