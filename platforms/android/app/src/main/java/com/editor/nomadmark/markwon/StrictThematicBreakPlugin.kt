package com.editor.nomadmark.markwon

import android.graphics.Color
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.core.MarkwonTheme

/**
 * 严格的分隔线渲染插件
 *
 * 功能：
 * - 自定义分隔线的渲染样式（颜色、高度、间距）
 *
 * 注意：由于 Markwon 基于 commonmark-java，完全自定义分隔线解析逻辑
 * 需要修改 BlockParser。此插件仅处理渲染样式，解析行为由 Editor 层的
 * ThematicBreakFormatter 自动规范化控制。
 *
 * 使用方式：
 * ```kotlin
 * markwon = Markwon.builder(context)
 *     .usePlugin(CorePlugin.create())
 *     .usePlugin(StrictThematicBreakPlugin.create())
 *     .build()
 * ```
 */
class StrictThematicBreakPlugin private constructor(
    private val config: Config
) : AbstractMarkwonPlugin() {

    companion object {
        /**
         * 创建默认配置的插件
         */
        @JvmStatic
        fun create(): StrictThematicBreakPlugin {
            return create(Config())
        }

        /**
         * 创建自定义配置的插件
         */
        @JvmStatic
        fun create(config: Config): StrictThematicBreakPlugin {
            return StrictThematicBreakPlugin(config)
        }

        /**
         * 使用构建器创建插件
         */
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }

    override fun configureTheme(builder: MarkwonTheme.Builder) {
        // 设置分隔线颜色（深灰色，墨水屏友好）
        builder.thematicBreakColor(config.color)

        // 设置分隔线高度
        builder.thematicBreakHeight(config.height)

        // 移除标题下方的分隔线（Setext 标题的下划线）
        builder.headingBreakHeight(0)

        // 注意：Markwon 4.6.2 可能不支持直接设置分隔线上下间距
        // 需要通过自定义 Span 实现，这里暂时跳过
        // 如需设置间距，可以在自定义 ThematicBreakSpan 中处理
    }

    /**
     * 配置类
     */
    data class Config(
        val color: Int = Color.rgb(80, 80, 80),
        val height: Int = 2,
        val paddingTop: Int = 24,
        val paddingBottom: Int = 24
    )

    /**
     * 构建器类
     */
    class Builder {
        private var color: Int = Color.rgb(80, 80, 80)
        private var height: Int = 2
        private var paddingTop: Int = 24
        private var paddingBottom: Int = 24

        fun color(color: Int): Builder {
            this.color = color
            return this
        }

        fun color(rgb: String): Builder {
            this.color = Color.parseColor(rgb)
            return this
        }

        fun height(height: Int): Builder {
            this.height = height
            return this
        }

        fun paddingTop(padding: Int): Builder {
            this.paddingTop = padding
            return this
        }

        fun paddingBottom(padding: Int): Builder {
            this.paddingBottom = padding
            return this
        }

        fun padding(top: Int, bottom: Int): Builder {
            this.paddingTop = top
            this.paddingBottom = bottom
            return this
        }

        fun build(): StrictThematicBreakPlugin {
            return create(Config(color, height, paddingTop, paddingBottom))
        }
    }
}
