package com.editor.nomadmark.image

import android.content.Context
import android.util.Log
import coil.map.Mapper
import coil.request.Options
import java.io.File

/**
 * Coil 文件路径 Mapper
 *
 * 将 markdown_images/xxx.jpg 格式的相对路径映射到实际文件路径
 */
class CoilFileMapper(private val context: Context) : Mapper<String, File> {

    override fun map(data: String, options: Options): File? {
        Log.d("CoilFileMapper", "映射路径: $data")

        return when {
            // 处理相对路径: markdown_images/xxx.jpg
            data.contains("markdown_images") -> {
                val fileName = data.substringAfterLast("/")
                val file = File(context.filesDir, "markdown_images/$fileName")
                if (file.exists()) {
                    Log.d("CoilFileMapper", "找到文件: ${file.absolutePath}")
                    file
                } else {
                    Log.w("CoilFileMapper", "文件不存在: ${file.absolutePath}")
                    null
                }
            }
            // 处理 file:// 协议
            data.startsWith("file://") -> {
                val filePath = data.removePrefix("file://")
                val file = File(filePath)
                if (file.exists()) {
                    Log.d("CoilFileMapper", "找到文件: ${file.absolutePath}")
                    file
                } else {
                    Log.w("CoilFileMapper", "文件不存在: ${file.absolutePath}")
                    null
                }
            }
            else -> null
        }
    }
}
