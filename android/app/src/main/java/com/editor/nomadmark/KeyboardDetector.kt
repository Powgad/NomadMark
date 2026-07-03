package com.editor.nomadmark

import android.content.Context
import android.content.res.Configuration
import android.view.inputmethod.InputMethodManager

/**
 * 键盘类型
 */
enum class KeyboardType {
    NONE,           // 无键盘
    SOFT_KEYBOARD,  // 软键盘
    F11_PHYSICAL    // F11 物理键盘
}

/**
 * 键盘检测器
 *
 * 功能：
 * - 检测外接键盘状态
 * - 检测软键盘状态
 * - 获取最优分屏比例
 */
class KeyboardDetector(private val context: Context) {

    companion object {
        /** F11 键盘标识码 */
        const val F11_KEYBOARD_ID = "f11"

        /** 软键盘分屏比例 (编辑区:预览区) */
        const val SOFT_KEYBOARD_SPLIT_RATIO = 0.5f

        /** F11 键盘分屏比例 */
        const val F11_KEYBOARD_SPLIT_RATIO = 0.4f
    }

    /**
     * 检测键盘类型
     */
    fun detectKeyboardType(): KeyboardType {
        // 检测是否有物理键盘
        if (hasPhysicalKeyboard()) {
            // 进一步检测是否是 F11 键盘
            if (isF11Keyboard()) {
                return KeyboardType.F11_PHYSICAL
            }
            return KeyboardType.F11_PHYSICAL // 暂时假设都是 F11
        }

        // 检测软键盘
        if (isSoftKeyboardShown()) {
            return KeyboardType.SOFT_KEYBOARD
        }

        return KeyboardType.NONE
    }

    /**
     * 检测是否有物理键盘
     */
    fun hasPhysicalKeyboard(): Boolean {
        val config = context.resources.configuration
        return config.keyboard != Configuration.KEYBOARD_NOKEYS
    }

    /**
     * 检测软键盘是否显示
     */
    fun isSoftKeyboardShown(): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.isAcceptingText
    }

    /**
     * 检测是否是 F11 键盘
     *
     * 注意：这需要 Ratta SDK 支持，或者通过按键事件统计来推断
     */
    private fun isF11Keyboard(): Boolean {
        // TODO: 与 Ratta SDK 集成
        // 暂时返回 true，假设检测到的物理键盘就是 F11
        return true
    }

    /**
     * 获取最优分屏比例
     *
     * @return 编辑区占比 (0.0 - 1.0)
     */
    fun getOptimalSplitRatio(): Float {
        return when (detectKeyboardType()) {
            KeyboardType.F11_PHYSICAL -> F11_KEYBOARD_SPLIT_RATIO
            KeyboardType.SOFT_KEYBOARD -> SOFT_KEYBOARD_SPLIT_RATIO
            KeyboardType.NONE -> SOFT_KEYBOARD_SPLIT_RATIO
        }
    }

    /**
     * 获取键盘显示文本
     */
    fun getKeyboardLabelText(): String {
        return when (detectKeyboardType()) {
            KeyboardType.F11_PHYSICAL -> "F11"
            KeyboardType.SOFT_KEYBOARD -> "软键盘"
            KeyboardType.NONE -> ""
        }
    }

    /**
     * 是否应该显示键盘标识
     */
    fun shouldShowIndicator(): Boolean {
        return detectKeyboardType() != KeyboardType.NONE
    }
}
