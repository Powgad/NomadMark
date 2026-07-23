package com.editor.nomadmark.markwon

import android.graphics.Color
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.core.MarkwonTheme

/**
 * 乐谱渲染插件（简化版）
 *
 * 功能：
 * - 识别 ```music 和 ```简谱 代码块
 * - 为乐谱块添加特殊渲染
 * - 支持音频播放按钮（未来功能）
 *
 * 注意：
 * 由于 commonmark 库的 API 兼容性问题，此插件目前不注册自定义 BlockParser。
 * 乐谱块的识别和渲染通过后处理实现（在 MusicSheetRenderer.kt 中）。
 *
 * 支持语法：
 * ```markdown
 * ```music
 * title: "乐曲名称"
 * composer: "作曲者"
 * tempo: 120
 * key: C Major
 * ... ABC 记谱法内容 ...
 * ```
 *
 * ```简谱
 * 1=C 4/4
 * 5 5 6 6 | 5 4 3 2
 * ... 简谱内容 ...
 * ```
 * ```
 *
 * 使用方式：
 * ```kotlin
 * markwon = Markwon.builder(context)
 *     .usePlugin(CorePlugin.create())
 *     .usePlugin(MusicSheetPlugin.create())
 *     .build()
 * ```
 */
class MusicSheetPlugin private constructor(
    private val config: Config
) : AbstractMarkwonPlugin() {

    companion object {
        /**
         * 创建默认配置的插件
         */
        @JvmStatic
        fun create(): MusicSheetPlugin {
            return create(Config())
        }

        /**
         * 创建自定义配置的插件
         */
        @JvmStatic
        fun create(config: Config): MusicSheetPlugin {
            return MusicSheetPlugin(config)
        }

        /**
         * 使用构建器创建插件
         */
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }

    /**
     * 配置主题
     *
     * 设置乐谱相关的主题样式
     */
    override fun configureTheme(builder: MarkwonTheme.Builder) {
        // 乐谱容器的背景色（浅灰，墨水屏友好）
        // 边框颜色（深灰）
        // 这些配置将在 MusicSheetSpan 中使用
    }

    /**
     * 获取配置
     */
    fun getConfig(): Config = config

    /**
     * 配置类
     */
    data class Config(
        /** 默认速度（BPM） */
        val defaultTempo: Int = 120,

        /** 默认调性 */
        val defaultKey: String = "C Major",

        /** 是否启用播放按钮 */
        val enablePlayback: Boolean = true,

        /** 乐谱背景色 */
        val backgroundColor: Int = Color.rgb(245, 245, 245),

        /** 边框颜色 */
        val borderColor: Int = Color.rgb(80, 80, 80),

        /** 边框宽度 */
        val borderWidth: Int = 2
    )

    /**
     * 构建器类
     */
    class Builder {
        private var defaultTempo: Int = 120
        private var defaultKey: String = "C Major"
        private var enablePlayback: Boolean = true
        private var backgroundColor: Int = Color.rgb(245, 245, 245)
        private var borderColor: Int = Color.rgb(80, 80, 80)
        private var borderWidth: Int = 2

        fun defaultTempo(tempo: Int): Builder {
            this.defaultTempo = tempo
            return this
        }

        fun defaultKey(key: String): Builder {
            this.defaultKey = key
            return this
        }

        fun enablePlayback(enable: Boolean): Builder {
            this.enablePlayback = enable
            return this
        }

        fun backgroundColor(color: Int): Builder {
            this.backgroundColor = color
            return this
        }

        fun borderColor(color: Int): Builder {
            this.borderColor = color
            return this
        }

        fun borderWidth(width: Int): Builder {
            this.borderWidth = width
            return this
        }

        fun build(): MusicSheetPlugin {
            return create(
                Config(
                    defaultTempo = defaultTempo,
                    defaultKey = defaultKey,
                    enablePlayback = enablePlayback,
                    backgroundColor = backgroundColor,
                    borderColor = borderColor,
                    borderWidth = borderWidth
                )
            )
        }
    }
}
