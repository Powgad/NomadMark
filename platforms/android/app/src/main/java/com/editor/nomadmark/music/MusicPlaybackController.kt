package com.editor.nomadmark.music

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 乐谱播放控制器
 *
 * 基于音符时间轴实现逐个高亮播放
 */
class MusicPlaybackController(
    private val musicData: MusicData
) {

    companion object {
        private const val TAG = "MusicPlaybackController"
    }

    /**
     * 播放进度监听器
     */
    interface OnPlaybackProgressListener {
        /**
         * 当播放到某个音符时调用
         * @param noteEvent 当前音符事件
         * @param progress 播放进度（0.0 到 1.0）
         * @param isPlaying 是否正在播放
         */
        fun onNoteHighlight(noteEvent: NoteEvent, progress: Float, isPlaying: Boolean)

        /**
         * 当播放完成时调用
         */
        fun onPlaybackComplete()
    }

    // =========================================================================
    // 播放状态
    // =========================================================================

    /** 音符时间轴 */
    private var noteEvents: List<NoteEvent> = emptyList()

    /** 当前播放到的音符索引 */
    private var currentNoteIndex: Int = 0

    /** 播放开始时间（毫秒） */
    private var playbackStartTime: Long = 0

    /** 是否正在播放 */
    private val _isPlaying = AtomicBoolean(false)

    val isPlaying: Boolean
        get() = _isPlaying.get()

    /** 是否已暂停（但未停止） */
    private var _isPaused: Boolean = false

    val isPaused: Boolean
        get() = _isPaused

    /** 乐谱是否在屏幕上可见 */
    var isVisible: Boolean = true
        set(value) {
            val wasVisible = field
            field = value
            if (!value && wasVisible && isPlaying) {
                Log.d(TAG, "乐谱离开屏幕，自动暂停")
                pausePlayback()
            }
        }

    /** 监听器 */
    private var listener: OnPlaybackProgressListener? = null

    /** Handler 用于定时触发 */
    private val handler = Handler(Looper.getMainLooper())

    /** 当前待执行的 Runnable */
    private var currentRunnable: Runnable? = null

    // =========================================================================
    // 播放控制
    // =========================================================================

    /**
     * 开始播放
     *
     * @param noteEvents 音符时间轴列表
     * @param listener 进度监听器
     */
    fun startPlayback(
        noteEvents: List<NoteEvent>,
        listener: OnPlaybackProgressListener
    ) {
        if (noteEvents.isEmpty()) {
            Log.w(TAG, "音符事件列表为空，无法播放")
            return
        }

        this.noteEvents = noteEvents
        this.listener = listener

        Log.d(TAG, "开始播放: ${noteEvents.size} 个音符")

        // 重置状态
        currentNoteIndex = 0
        _isPaused = false
        _isPlaying.set(true)
        playbackStartTime = System.currentTimeMillis()

        // 立即高亮第一个音符
        highlightCurrentNote()
    }

    /**
     * 暂停播放
     */
    fun pausePlayback() {
        if (!isPlaying || _isPaused) return

        Log.d(TAG, "暂停播放, 当前音符: $currentNoteIndex")
        _isPaused = true
        _isPlaying.set(false)

        // 取消所有待执行的任务
        currentRunnable?.let { handler.removeCallbacks(it) }
        currentRunnable = null
    }

    /**
     * 恢复播放
     */
    fun resumePlayback() {
        if (!_isPaused || currentNoteIndex >= noteEvents.size) return

        Log.d(TAG, "恢复播放, 从音符: $currentNoteIndex")
        _isPaused = false
        _isPlaying.set(true)

        // 从当前音符继续
        scheduleNextNote()
    }

    /**
     * 停止播放（完全重置）
     */
    fun stopPlayback() {
        Log.d(TAG, "停止播放")
        _isPlaying.set(false)
        _isPaused = false
        currentNoteIndex = 0
        currentRunnable?.let { handler.removeCallbacks(it) }
        currentRunnable = null
        listener = null
        noteEvents = emptyList()
    }

    /**
     * 切换播放/暂停
     * @return 是否正在播放
     */
    fun togglePlayback(): Boolean {
        return if (isPlaying) {
            pausePlayback()
            false // 已暂停
        } else if (_isPaused) {
            resumePlayback()
            true // 已恢复
        } else {
            // 未开始播放，返回 false 让外部调用 startPlayback
            false
        }
    }

    // =========================================================================
    // 内部逻辑
    // =========================================================================

    /**
     * 高亮当前音符并调度下一个
     */
    private fun highlightCurrentNote() {
        if (currentNoteIndex >= noteEvents.size) {
            // 播放完成
            Log.d(TAG, "播放完成")
            _isPlaying.set(false)
            listener?.onPlaybackComplete()
            return
        }

        val currentNote = noteEvents[currentNoteIndex]
        val progress = if (noteEvents.isNotEmpty()) {
            currentNoteIndex.toFloat() / noteEvents.size
        } else {
            0f
        }

        Log.d(TAG, "高亮音符 [$currentNoteIndex/${noteEvents.size}]: id=${currentNote.elementId}, progress=$progress")

        // 通知监听器高亮当前音符
        listener?.onNoteHighlight(currentNote, progress, true)

        // 调度下一个音符
        scheduleNextNote()
    }

    /**
     * 调度下一个音符的高亮
     */
    private fun scheduleNextNote() {
        if (!isPlaying || _isPaused) return

        // 计算到下一个音符的延迟
        val currentIndex = currentNoteIndex
        if (currentIndex + 1 >= noteEvents.size) {
            // 最后一个音符，等待一小段时间后结束
            handler.postDelayed({
                if (isPlaying && !_isPaused) {
                    currentNoteIndex++
                    highlightCurrentNote()
                }
            }, 500)
            return
        }

        val currentNote = noteEvents[currentIndex]
        val nextNote = noteEvents[currentIndex + 1]

        // 计算延迟：下一个音符时间 - 当前音符时间
        val delayMillis = (nextNote.timeMillis - currentNote.timeMillis).toLong()
            .coerceAtLeast(100) // 至少 100ms

        val runnable = object : Runnable {
            override fun run() {
                if (!isPlaying || _isPaused) return

                currentNoteIndex++
                highlightCurrentNote()
            }
        }

        currentRunnable = runnable
        handler.postDelayed(runnable, delayMillis)
    }

    /**
     * 获取当前播放进度（0.0 到 1.0）
     */
    fun getProgress(): Float {
        if (noteEvents.isEmpty()) return 0f
        return currentNoteIndex.toFloat() / noteEvents.size
    }

    /**
     * 获取当前高亮的音符事件
     */
    fun getCurrentNoteEvent(): NoteEvent? {
        if (noteEvents.isEmpty() || currentNoteIndex >= noteEvents.size) return null
        return noteEvents[currentNoteIndex]
    }

    /**
     * 清理资源（在 Span 被回收时调用）
     */
    fun cleanup() {
        stopPlayback()
        handler.removeCallbacksAndMessages(null)
    }
}
