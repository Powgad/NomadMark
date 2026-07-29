package com.editor.nomadmark.music

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 乐谱播放控制器（简化版）
 *
 * 使用固定间隔播放，不需要分析音符时间轴
 */
class MusicPlaybackController(
    private val musicData: MusicData
) {

    companion object {
        private const val TAG = "MusicPlaybackController"
        // 每秒显示的音符数（默认）
        private const val NOTES_PER_SECOND = 2
    }

    /**
     * 播放进度监听器
     */
    interface OnPlaybackProgressListener {
        /**
         * 当播放进度更新时调用
         * @param progress 进度（0.0 到 1.0）
         * @param isPlaying 是否正在播放
         */
        fun onProgressUpdate(progress: Float, isPlaying: Boolean)

        /**
         * 当播放完成时调用
         */
        fun onPlaybackComplete()
    }

    // =========================================================================
    // 播放状态
    // =========================================================================

    /** 估算的总音符数（用于计算进度） */
    private var totalNotes: Int = 0

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

    /** 公共属性：检查是否已暂停 */
    val isPaused: Boolean
        get() = _isPaused

    /** 乐谱是否在屏幕上可见 */
    var isVisible: Boolean = true
        set(value) {
            val wasVisible = field
            field = value
            if (!value && wasVisible && isPlaying) {
                // 离开屏幕时自动暂停
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

    /** 播放速度（每秒音符数） */
    private var notesPerSecond: Float = NOTES_PER_SECOND.toFloat()

    // =========================================================================
    // 播放控制
    // =========================================================================

    /**
     * 开始播放
     *
     * @param totalNotes 估算的总音符数
     * @param tempo 播放速度（BPM）
     * @param listener 进度监听器
     */
    fun startPlayback(
        totalNotes: Int,
        tempo: Int,
        listener: OnPlaybackProgressListener
    ) {
        this.totalNotes = totalNotes.coerceAtLeast(1)
        this.listener = listener

        // 根据 tempo 计算每秒音符数
        // tempo = BPM（每分钟拍数），假设每拍约 4 个音符
        this.notesPerSecond = (tempo / 60f) * 4

        Log.d(TAG, "开始播放: $totalNotes 个音符, tempo=$tempo, notesPerSec=$notesPerSecond")

        // 重置状态
        currentNoteIndex = 0
        _isPaused = false
        _isPlaying.set(true)
        playbackStartTime = System.currentTimeMillis()

        // 开始播放循环
        scheduleNextNote(0)
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
        if (!_isPaused || currentNoteIndex >= totalNotes) return

        Log.d(TAG, "恢复播放, 从音符: $currentNoteIndex")
        _isPaused = false
        _isPlaying.set(true)

        // 从当前音符继续
        scheduleNextNote(currentNoteIndex)
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
    }

    /**
     * 切换播放/暂停
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
     * 调度下一个音符的进度更新
     */
    private fun scheduleNextNote(noteIndex: Int) {
        if (noteIndex >= totalNotes) {
            // 播放完成
            Log.d(TAG, "播放完成")
            _isPlaying.set(false)
            listener?.onPlaybackComplete()
            return
        }

        currentNoteIndex = noteIndex

        // 计算延迟：1000ms / 每秒音符数
        val delayMillis = (1000f / notesPerSecond).toLong().coerceAtLeast(100)

        val runnable = object : Runnable {
            override fun run() {
                if (!isPlaying || _isPaused) return

                val progress = noteIndex.toFloat() / totalNotes
                Log.d(TAG, "播放进度: $noteIndex/$totalNotes = $progress")

                listener?.onProgressUpdate(progress, true)

                // 调度下一个音符
                scheduleNextNote(noteIndex + 1)
            }
        }

        currentRunnable = runnable
        handler.postDelayed(runnable, delayMillis)
    }

    /**
     * 获取当前播放进度（0.0 到 1.0）
     */
    fun getProgress(): Float {
        if (totalNotes == 0) return 0f
        return currentNoteIndex.toFloat() / totalNotes
    }

    /**
     * 清理资源（在 Span 被回收时调用）
     */
    fun cleanup() {
        stopPlayback()
        handler.removeCallbacksAndMessages(null)
    }
}
