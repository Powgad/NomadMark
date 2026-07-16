package com.editor.nomadmark

import android.view.KeyEvent

/**
 * 按键绑定数据类
 *
 * 表示一个按键组合与编辑器功能的映射关系。
 *
 * @param keyCode 主键码 (KeyEvent.KEYCODE_*)
 * @param ctrl Ctrl 修饰键
 * @param shift Shift 修饰键
 * @param alt Alt 修饰键
 * @param actionId 功能ID
 */
data class KeyBinding(
    val keyCode: Int,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val actionId: String
) {
    /**
     * 唯一标识符，用于存储键
     * 格式: "keyCode:ctrl:shift:alt:actionId"
     */
    val key: String
        get() = "$keyCode:$ctrl:$shift:$alt:$actionId"

    /**
     * 获取按键组合的显示文本
     * 例如: "Ctrl+Shift+B"
     */
    val displayText: String
        get() = buildString {
            if (ctrl) append("Ctrl+")
            if (shift) append("Shift+")
            if (alt) append("Alt+")
            append(getLabel(keyCode))
        }

    /**
     * 判断此绑定是否与给定的按键参数匹配
     */
    fun matches(keyCode: Int, ctrl: Boolean, shift: Boolean, alt: Boolean): Boolean {
        return this.keyCode == keyCode &&
                this.ctrl == ctrl &&
                this.shift == shift &&
                this.alt == alt
    }

    companion object {
        /**
         * 从存储键反序列化 KeyBinding
         */
        fun fromKey(key: String): KeyBinding? {
            val parts = key.split(":")
            if (parts.size != 5) return null

            return try {
                KeyBinding(
                    keyCode = parts[0].toInt(),
                    ctrl = parts[1].toBoolean(),
                    shift = parts[2].toBoolean(),
                    alt = parts[3].toBoolean(),
                    actionId = parts[4]
                )
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 获取按键的显示名称
         */
        fun getLabel(keyCode: Int): String {
            return when (keyCode) {
                KeyEvent.KEYCODE_A -> "A"
                KeyEvent.KEYCODE_B -> "B"
                KeyEvent.KEYCODE_C -> "C"
                KeyEvent.KEYCODE_D -> "D"
                KeyEvent.KEYCODE_E -> "E"
                KeyEvent.KEYCODE_F -> "F"
                KeyEvent.KEYCODE_G -> "G"
                KeyEvent.KEYCODE_H -> "H"
                KeyEvent.KEYCODE_I -> "I"
                KeyEvent.KEYCODE_J -> "J"
                KeyEvent.KEYCODE_K -> "K"
                KeyEvent.KEYCODE_L -> "L"
                KeyEvent.KEYCODE_M -> "M"
                KeyEvent.KEYCODE_N -> "N"
                KeyEvent.KEYCODE_O -> "O"
                KeyEvent.KEYCODE_P -> "P"
                KeyEvent.KEYCODE_Q -> "Q"
                KeyEvent.KEYCODE_R -> "R"
                KeyEvent.KEYCODE_S -> "S"
                KeyEvent.KEYCODE_T -> "T"
                KeyEvent.KEYCODE_U -> "U"
                KeyEvent.KEYCODE_V -> "V"
                KeyEvent.KEYCODE_W -> "W"
                KeyEvent.KEYCODE_X -> "X"
                KeyEvent.KEYCODE_Y -> "Y"
                KeyEvent.KEYCODE_Z -> "Z"
                KeyEvent.KEYCODE_0 -> "0"
                KeyEvent.KEYCODE_1 -> "1"
                KeyEvent.KEYCODE_2 -> "2"
                KeyEvent.KEYCODE_3 -> "3"
                KeyEvent.KEYCODE_4 -> "4"
                KeyEvent.KEYCODE_5 -> "5"
                KeyEvent.KEYCODE_6 -> "6"
                KeyEvent.KEYCODE_7 -> "7"
                KeyEvent.KEYCODE_8 -> "8"
                KeyEvent.KEYCODE_9 -> "9"
                KeyEvent.KEYCODE_F1 -> "F1"
                KeyEvent.KEYCODE_F2 -> "F2"
                KeyEvent.KEYCODE_F3 -> "F3"
                KeyEvent.KEYCODE_F4 -> "F4"
                KeyEvent.KEYCODE_F5 -> "F5"
                KeyEvent.KEYCODE_F6 -> "F6"
                KeyEvent.KEYCODE_F7 -> "F7"
                KeyEvent.KEYCODE_F8 -> "F8"
                KeyEvent.KEYCODE_F9 -> "F9"
                KeyEvent.KEYCODE_F10 -> "F10"
                KeyEvent.KEYCODE_F11 -> "F11"
                KeyEvent.KEYCODE_F12 -> "F12"
                KeyEvent.KEYCODE_MINUS -> "-"
                KeyEvent.KEYCODE_PLUS -> "+"
                KeyEvent.KEYCODE_EQUALS -> "="
                else -> "Key($keyCode)"
            }
        }

        /**
         * 获取功能的显示名称
         */
        fun getActionLabel(actionId: String): String {
            return EditorAction.fromString(actionId)?.label ?: actionId
        }
    }
}

/**
 * 编辑器功能枚举
 *
 * 定义所有可通过快捷键触发的编辑器操作。
 */
enum class EditorAction(
    val id: String,
    val label: String,
    val category: ActionCategory = ActionCategory.OTHER
) {
    // 文本格式
    BOLD("bold", "加粗", ActionCategory.FORMAT),
    ITALIC("italic", "斜体", ActionCategory.FORMAT),
    UNDERLINE("underline", "删除线", ActionCategory.FORMAT),

    // 编辑操作
    SAVE("save", "保存", ActionCategory.EDIT),
    UNDO("undo", "撤销", ActionCategory.EDIT),
    REDO("redo", "重做", ActionCategory.EDIT),
    SEARCH("search", "搜索", ActionCategory.EDIT),
    REPLACE("replace", "搜索并替换", ActionCategory.EDIT),

    // 标题
    HEADING_1("heading1", "一级标题", ActionCategory.FORMAT),
    HEADING_2("heading2", "二级标题", ActionCategory.FORMAT),
    HEADING_3("heading3", "三级标题", ActionCategory.FORMAT),
    HEADING_4("heading4", "四级标题", ActionCategory.FORMAT),
    HEADING_5("heading5", "五级标题", ActionCategory.FORMAT),
    HEADING_6("heading6", "六级标题", ActionCategory.FORMAT),
    HEADING_CLEAR("headingClear", "清除标题", ActionCategory.FORMAT),
    HEADING_UP("headingUp", "提升标题", ActionCategory.FORMAT),
    HEADING_DOWN("headingDown", "降低标题", ActionCategory.FORMAT),

    // 插入元素
    LINK("link", "链接", ActionCategory.INSERT),
    IMAGE("image", "图片", ActionCategory.INSERT),
    CODE_INLINE("codeInline", "行内代码", ActionCategory.INSERT),
    CODE_BLOCK("codeBlock", "代码块", ActionCategory.INSERT),
    FORMULA_INLINE("formulaInline", "行内公式", ActionCategory.INSERT),
    FORMULA_BLOCK("formulaBlock", "公式块", ActionCategory.INSERT),
    LIST_UNORDERED("listUnordered", "无序列表", ActionCategory.INSERT),
    LIST_ORDERED("listOrdered", "有序列表", ActionCategory.INSERT),
    QUOTE("quote", "引用", ActionCategory.INSERT),
    TABLE("table", "表格", ActionCategory.INSERT),
    THEMATIC_BREAK("thematicBreak", "分隔线", ActionCategory.INSERT),

    // 视图操作
    TOGGLE_PREVIEW("togglePreview", "切换预览", ActionCategory.VIEW),
    TOGGLE_SPLIT("toggleSplit", "切换分屏", ActionCategory.VIEW),
    TOGGLE_REVISION("toggleRevision", "切换修订模式", ActionCategory.VIEW),
    CYCLE_DISPLAY_MODE("cycleDisplayMode", "循环显示模式", ActionCategory.VIEW),
    TOGGLE_TOC("toggleToc", "目录", ActionCategory.VIEW),
    TOGGLE_TOOLBAR("toggleToolbar", "切换快捷栏", ActionCategory.VIEW),

    // 其他
    NONE("none", "无");

    companion object {
        fun fromString(id: String): EditorAction? {
            return values().find { it.id == id }
        }
    }
}

/**
 * 功能分类
 */
enum class ActionCategory {
    FORMAT,   // 文本格式
    EDIT,     // 编辑操作
    INSERT,   // 插入元素
    VIEW,     // 视图操作
    OTHER     // 其他
}
