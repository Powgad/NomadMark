package com.editor.nomadmark

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * 快捷键管理器
 *
 * 负责管理快捷键的读取、写入、匹配和持久化存储。
 */
class KeyBindingManager(private val context: Context) {

    companion object {
        private const val TAG = "KeyBindingManager"
        private const val PREFS_NAME = "NomadMarkPrefs"
        private const val KEY_CUSTOM_BINDINGS = "custom_keybindings"

        /**
         * 默认快捷键配置
         * 基于现有 onKeyDown 方法中的快捷键定义
         */
        fun getDefaultBindings(): List<KeyBinding> {
            return listOf(
                // ========== 文本格式 ==========
                KeyBinding(KeyEvent.KEYCODE_B, ctrl = true, actionId = EditorAction.BOLD.id),
                KeyBinding(KeyEvent.KEYCODE_I, ctrl = true, actionId = EditorAction.ITALIC.id),
                KeyBinding(KeyEvent.KEYCODE_U, ctrl = true, actionId = EditorAction.UNDERLINE.id),

                // ========== 编辑操作 ==========
                KeyBinding(KeyEvent.KEYCODE_S, ctrl = true, actionId = EditorAction.SAVE.id),
                KeyBinding(KeyEvent.KEYCODE_Z, ctrl = true, shift = false, actionId = EditorAction.UNDO.id),
                KeyBinding(KeyEvent.KEYCODE_Z, ctrl = true, shift = true, actionId = EditorAction.REDO.id),
                KeyBinding(KeyEvent.KEYCODE_Y, ctrl = true, actionId = EditorAction.REDO.id),
                KeyBinding(KeyEvent.KEYCODE_F, ctrl = true, actionId = EditorAction.SEARCH.id),
                KeyBinding(KeyEvent.KEYCODE_H, ctrl = true, actionId = EditorAction.REPLACE.id),

                // ========== 标题 ==========
                KeyBinding(KeyEvent.KEYCODE_1, ctrl = true, actionId = EditorAction.HEADING_1.id),
                KeyBinding(KeyEvent.KEYCODE_2, ctrl = true, actionId = EditorAction.HEADING_2.id),
                KeyBinding(KeyEvent.KEYCODE_3, ctrl = true, actionId = EditorAction.HEADING_3.id),
                KeyBinding(KeyEvent.KEYCODE_4, ctrl = true, actionId = EditorAction.HEADING_4.id),
                KeyBinding(KeyEvent.KEYCODE_5, ctrl = true, actionId = EditorAction.HEADING_5.id),
                KeyBinding(KeyEvent.KEYCODE_6, ctrl = true, actionId = EditorAction.HEADING_6.id),
                KeyBinding(KeyEvent.KEYCODE_0, ctrl = true, actionId = EditorAction.HEADING_CLEAR.id),

                // ========== 插入元素 ==========
                KeyBinding(KeyEvent.KEYCODE_K, ctrl = true, shift = false, actionId = EditorAction.LINK.id),
                KeyBinding(KeyEvent.KEYCODE_K, ctrl = true, shift = true, actionId = EditorAction.FORMULA_INLINE.id),
                KeyBinding(KeyEvent.KEYCODE_C, ctrl = true, shift = true, actionId = EditorAction.CODE_BLOCK.id),
                KeyBinding(KeyEvent.KEYCODE_X, ctrl = true, shift = true, actionId = EditorAction.CODE_INLINE.id),
                KeyBinding(KeyEvent.KEYCODE_L, ctrl = true, shift = true, alt = false, actionId = EditorAction.LIST_UNORDERED.id),
                KeyBinding(KeyEvent.KEYCODE_L, ctrl = true, shift = false, alt = true, actionId = EditorAction.LIST_ORDERED.id),
                KeyBinding(KeyEvent.KEYCODE_T, ctrl = true, shift = false, actionId = EditorAction.TOGGLE_TOC.id),
                KeyBinding(KeyEvent.KEYCODE_T, ctrl = true, shift = true, actionId = EditorAction.TABLE.id),
                KeyBinding(KeyEvent.KEYCODE_Q, ctrl = true, shift = true, actionId = EditorAction.QUOTE.id),
                KeyBinding(KeyEvent.KEYCODE_F, ctrl = true, shift = true, actionId = EditorAction.FORMULA_BLOCK.id),
                KeyBinding(KeyEvent.KEYCODE_MINUS, ctrl = true, shift = true, actionId = EditorAction.THEMATIC_BREAK.id),

                // ========== 视图操作 ==========
                KeyBinding(KeyEvent.KEYCODE_F11, ctrl = false, actionId = EditorAction.CYCLE_DISPLAY_MODE.id)
            )
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 获取所有有效的快捷键绑定
     * 优先使用自定义绑定，补充默认绑定
     * 按 actionId 去重，确保每个功能只有一个快捷键
     */
    fun getAllBindings(): List<KeyBinding> {
        val customBindings = loadCustomBindings()
        val defaultBindings = getDefaultBindings()

        // 使用 actionId 作为 key，确保每个功能只有一个绑定
        val bindingMap = mutableMapOf<String, KeyBinding>()

        // 先添加默认绑定
        for (binding in defaultBindings) {
            bindingMap[binding.actionId] = binding
        }

        // 用自定义绑定覆盖（按 actionId）
        for (binding in customBindings) {
            bindingMap[binding.actionId] = binding
        }

        return bindingMap.values.toList()
    }

    /**
     * 查找匹配的快捷键绑定
     *
     * @param keyCode 键码
     * @param ctrl Ctrl 键状态
     * @param shift Shift 键状态
     * @param alt Alt 键状态
     * @return 匹配的 KeyBinding，如果没有则返回 null
     */
    fun findBinding(keyCode: Int, ctrl: Boolean, shift: Boolean, alt: Boolean): KeyBinding? {
        val allBindings = getAllBindings()

        return allBindings.find { binding ->
            binding.matches(keyCode, ctrl, shift, alt)
        }
    }

    /**
     * 从 SharedPreferences 加载自定义快捷键
     */
    fun loadCustomBindings(): List<KeyBinding> {
        val jsonStr = prefs.getString(KEY_CUSTOM_BINDINGS, null) ?: return emptyList()

        return try {
            val jsonArray = JSONArray(jsonStr)
            val bindings = mutableListOf<KeyBinding>()

            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                val binding = KeyBinding(
                    keyCode = jsonObj.getInt("keyCode"),
                    ctrl = jsonObj.getBoolean("ctrl"),
                    shift = jsonObj.getBoolean("shift"),
                    alt = jsonObj.getBoolean("alt"),
                    actionId = jsonObj.getString("actionId")
                )
                bindings.add(binding)
            }

            bindings
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load custom bindings", e)
            emptyList()
        }
    }

    /**
     * 保存单个快捷键绑定
     *
     * @param binding 要保存的绑定
     */
    fun saveBinding(binding: KeyBinding) {
        val customBindings = loadCustomBindings().toMutableList()

        // 检查是否已存在相同的按键组合，如果存在则更新
        val existingIndex = customBindings.indexOfFirst {
            it.keyCode == binding.keyCode &&
            it.ctrl == binding.ctrl &&
            it.shift == binding.shift &&
            it.alt == binding.alt
        }

        if (existingIndex >= 0) {
            customBindings[existingIndex] = binding
        } else {
            customBindings.add(binding)
        }

        saveBindings(customBindings)
        Log.d(TAG, "Saved binding: ${binding.displayText} -> ${binding.actionId}")
    }

    /**
     * 删除指定功能的绑定
     *
     * @param actionId 功能 ID
     */
    fun removeBinding(actionId: String) {
        val customBindings = loadCustomBindings().toMutableList()

        val removed = customBindings.removeIf {
            it.actionId == actionId
        }

        if (removed) {
            saveBindings(customBindings)
            Log.d(TAG, "Removed binding for action: $actionId")
        }
    }

    /**
     * 恢复默认快捷键设置
     * 清除所有自定义绑定
     */
    fun restoreDefaults() {
        prefs.edit().remove(KEY_CUSTOM_BINDINGS).apply()
        Log.d(TAG, "Restored default bindings")
    }

    /**
     * 保存所有自定义绑定到 SharedPreferences
     */
    private fun saveBindings(bindings: List<KeyBinding>) {
        try {
            val jsonArray = JSONArray()

            for (binding in bindings) {
                val jsonObj = JSONObject().apply {
                    put("keyCode", binding.keyCode)
                    put("ctrl", binding.ctrl)
                    put("shift", binding.shift)
                    put("alt", binding.alt)
                    put("actionId", binding.actionId)
                }
                jsonArray.put(jsonObj)
            }

            prefs.edit().putString(KEY_CUSTOM_BINDINGS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bindings", e)
        }
    }

    /**
     * 检查按键组合是否已被占用
     *
     * @param keyCode 键码
     * @param ctrl Ctrl 键状态
     * @param shift Shift 键状态
     * @param alt Alt 键状态
     * @param excludeActionId 要排除的功能ID（用于编辑时排除自身）
     * @return 如果被占用返回占用的功能名称，否则返回 null
     */
    fun isBindingConflict(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        excludeActionId: String? = null
    ): String? {
        val allBindings = getAllBindings()

        val conflict = allBindings.find { binding ->
            binding.matches(keyCode, ctrl, shift, alt) &&
            binding.actionId != excludeActionId
        }

        return conflict?.let { KeyBinding.getActionLabel(it.actionId) }
    }

    /**
     * 获取指定功能的当前快捷键
     */
    fun getBindingForAction(actionId: String): KeyBinding? {
        val allBindings = getAllBindings()
        return allBindings.find { it.actionId == actionId }
    }
}
