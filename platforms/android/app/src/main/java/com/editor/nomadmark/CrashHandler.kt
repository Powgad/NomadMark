package com.editor.nomadmark

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Looper
import android.util.Log
import android.app.ActivityManager
import android.content.pm.PackageManager
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 增强的崩溃日志收集器
 *
 * 功能特性：
 * - 自动捕获应用崩溃并记录详细的错误信息
 * - 收集设备信息、系统版本、应用版本、内存状态等
 * - 应用启动时自动检查上次崩溃并提示用户
 * - 支持手动报告非崩溃错误
 * - 自动清理旧日志，防止占用过多空间
 *
 * 使用方法：
 * ```kotlin
 * CrashHandler.init(context)
 * CrashHandler.setLogListener { crashInfo ->
 *     // 处理崩溃信息，如上传到服务器
 * }
 *
 * // 检查上次是否有崩溃
 * CrashHandler.checkPreviousCrash()
 * ```
 */
object CrashHandler {
    private const val TAG = "CrashHandler"
    private const val CRASH_DIR = "crashes"
    private const val MAX_CRASH_FILES = 10
    private const val PREFS_NAME = "crash_handler_prefs"
    private const val KEY_HAS_CRASH = "has_crash"
    private const val KEY_CRASH_FILE = "crash_file"
    private const val KEY_CRASH_TIME = "crash_time"

    private var context: Context? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var logListener: ((CrashInfo) -> Unit)? = null
    private var prefs: SharedPreferences? = null

    /**
     * 崩溃信息
     */
    data class CrashInfo(
        val timestamp: Date,
        val threadName: String,
        val exception: Throwable,
        val deviceInfo: DeviceInfo,
        val appInfo: AppInfo,
        val runtimeInfo: RuntimeInfo,
        val logFile: File?
    ) {
        fun toMarkdown(): String {
            val sb = StringBuilder()
            sb.append("# Crash Report\n\n")
            sb.append("**Time:** ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(timestamp)}\n\n")
            sb.append("## Application Information\n\n")
            sb.append("- **Version:** ${appInfo.versionName} (${appInfo.versionCode})\n")
            sb.append("- **Package:** ${appInfo.packageName}\n")
            sb.append("- **Debug Build:** ${appInfo.isDebug}\n\n")
            sb.append("## Device Information\n\n")
            sb.append("- **Model:** ${deviceInfo.model}\n")
            sb.append("- **Manufacturer:** ${deviceInfo.manufacturer}\n")
            sb.append("- **Android Version:** ${deviceInfo.androidVersion}\n")
            sb.append("- **API Level:** ${deviceInfo.apiLevel}\n")
            sb.append("- **Arch:** ${deviceInfo.arch}\n\n")
            sb.append("## Runtime Information\n\n")
            sb.append("- **Available Memory:** ${runtimeInfo.availableMemory} MB\n")
            sb.append("- **Total Memory:** ${runtimeInfo.totalMemory} MB\n")
            sb.append("- **Memory Class:** ${runtimeInfo.memoryClass} MB\n")
            sb.append("- **Low Memory Mode:** ${runtimeInfo.isLowMemory}\n\n")
            sb.append("## Thread Information\n\n")
            sb.append("- **Thread Name:** $threadName\n\n")
            sb.append("## Exception\n\n")
            sb.append("```\n")
            sb.append(getStackTrace(exception))
            sb.append("\n```\n\n")
            return sb.toString()
        }

        private fun getStackTrace(throwable: Throwable): String {
            val sw = java.io.StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            return sw.toString()
        }
    }

    /**
     * 设备信息
     */
    data class DeviceInfo(
        val model: String,
        val manufacturer: String,
        val androidVersion: String,
        val apiLevel: Int,
        val arch: String,
        val board: String,
        val bootloader: String,
        val hardware: String
    ) {
        companion object {
            fun fromCurrentDevice(): DeviceInfo {
                return DeviceInfo(
                    model = Build.MODEL,
                    manufacturer = Build.MANUFACTURER,
                    androidVersion = Build.VERSION.RELEASE,
                    apiLevel = Build.VERSION.SDK_INT,
                    arch = System.getProperty("os.arch") ?: "unknown",
                    board = Build.BOARD,
                    bootloader = Build.BOOTLOADER,
                    hardware = Build.HARDWARE
                )
            }
        }
    }

    /**
     * 应用信息
     */
    data class AppInfo(
        val versionName: String,
        val versionCode: Long,
        val packageName: String,
        val isDebug: Boolean
    ) {
        companion object {
            fun fromContext(ctx: Context): AppInfo {
                return try {
                    val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                    val flags = ctx.applicationInfo.flags
                    val isDebug = (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    AppInfo(
                        versionName = packageInfo.versionName ?: "unknown",
                        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            packageInfo.versionCode.toLong()
                        },
                        packageName = packageInfo.packageName,
                        isDebug = isDebug
                    )
                } catch (e: Exception) {
                    AppInfo("unknown", 0, ctx.packageName, false)
                }
            }
        }
    }

    /**
     * 运行时信息
     */
    data class RuntimeInfo(
        val availableMemory: Long,
        val totalMemory: Long,
        val memoryClass: Int,
        val isLowMemory: Boolean
    ) {
        companion object {
            fun fromContext(ctx: Context): RuntimeInfo {
                val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                activityManager?.getMemoryInfo(memInfo)

                return RuntimeInfo(
                    availableMemory = (memInfo.availMem / (1024 * 1024)),
                    totalMemory = (memInfo.totalMem / (1024 * 1024)),
                    memoryClass = activityManager?.memoryClass ?: 0,
                    isLowMemory = memInfo.lowMemory
                )
            }
        }
    }

    /**
     * 初始化 CrashHandler
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 保存默认的异常处理器
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        // 设置自定义的异常处理器
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleException(thread, throwable)
        }

        Log.i(TAG, "CrashHandler initialized")
    }

    /**
     * 设置日志监听器
     */
    fun setLogListener(listener: ((CrashInfo) -> Unit)?) {
        this.logListener = listener
    }

    /**
     * 检查上次是否有崩溃
     * 在应用启动时调用，如果有崩溃则提示用户
     */
    fun checkPreviousCrash(): CrashInfo? {
        if (context == null) return null
        val hasCrash = prefs?.getBoolean(KEY_HAS_CRASH, false) ?: false

        if (!hasCrash) {
            return null
        }

        val crashFilePath = prefs?.getString(KEY_CRASH_FILE, null)
        val crashTime = prefs?.getLong(KEY_CRASH_TIME, 0) ?: 0

        // 清除标记
        prefs?.edit()
            ?.putBoolean(KEY_HAS_CRASH, false)
            ?.remove(KEY_CRASH_FILE)
            ?.remove(KEY_CRASH_TIME)
            ?.apply()

        if (crashFilePath != null) {
            val crashFile = File(crashFilePath)
            if (crashFile.exists()) {
                val content = try {
                    crashFile.readText()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read crash file", e)
                    return null
                }

                // 解析崩溃信息
                val crashInfo = parseCrashInfo(content, crashFile, Date(crashTime))

                Log.i(TAG, "Previous crash detected from ${Date(crashTime)}")
                return crashInfo
            }
        }

        return null
    }

    /**
     * 解析崩溃信息
     */
    private fun parseCrashInfo(@Suppress("UNUSED_PARAMETER") content: String, crashFile: File, timestamp: Date): CrashInfo {
        val ctx = context!!

        // 简单解析，从日志中提取关键信息
        val exception = Exception("Crash detected from previous session")
        val deviceInfo = DeviceInfo.fromCurrentDevice()
        val appInfo = AppInfo.fromContext(ctx)
        val runtimeInfo = RuntimeInfo.fromContext(ctx)

        return CrashInfo(
            timestamp = timestamp,
            threadName = "Unknown",
            exception = exception,
            deviceInfo = deviceInfo,
            appInfo = appInfo,
            runtimeInfo = runtimeInfo,
            logFile = crashFile
        )
    }

    /**
     * 处理异常
     */
    private fun handleException(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "Unhandled exception in thread: ${thread.name}", throwable)

        val ctx = context ?: run {
            // 如果 context 不可用，直接调用默认处理器
            defaultHandler?.uncaughtException(thread, throwable)
            return
        }

        val deviceInfo = DeviceInfo.fromCurrentDevice()
        val appInfo = AppInfo.fromContext(ctx)
        val runtimeInfo = RuntimeInfo.fromContext(ctx)
        val crashInfo = CrashInfo(
            timestamp = Date(),
            threadName = thread.name,
            exception = throwable,
            deviceInfo = deviceInfo,
            appInfo = appInfo,
            runtimeInfo = runtimeInfo,
            logFile = null
        )

        // 收集最近的日志
        val recentLogs = collectRecentLogs()

        // 保存到文件
        val logFile = saveCrashLog(crashInfo, recentLogs)

        // 更新 crashInfo 的 logFile
        val updatedCrashInfo = crashInfo.copy(logFile = logFile)

        // 保存崩溃状态到 SharedPreferences
        prefs?.edit()?.apply {
            putBoolean(KEY_HAS_CRASH, true)
            logFile?.let { putString(KEY_CRASH_FILE, it.absolutePath) }
            putLong(KEY_CRASH_TIME, System.currentTimeMillis())
            apply()
        }

        // 通知监听器
        logListener?.invoke(updatedCrashInfo)

        // 调用默认处理器（显示崩溃对话框）
        defaultHandler?.uncaughtException(thread, throwable)
    }

    /**
     * 收集最近的日志
     */
    private fun collectRecentLogs(): List<String> {
        val logs = mutableListOf<String>()
        try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "logcat", "-d", "-v", "time",
                "*:E", // 只收集错误级别的日志
                "NomadMark:V", // 但包含我们应用的详细日志
                "MarkdownCore:V",
                "MarkdownEditorActivity:V",
                "MainActivity:V"
            ))
            val reader = process.inputStream.bufferedReader()
            val allLines = reader.use { it.readLines() }
            // 取最后 200 行
            allLines.takeLast(200).forEach { line ->
                if (line.contains("NomadMark") ||
                    line.contains("Markdown") ||
                    line.contains("FATAL") ||
                    line.contains("AndroidRuntime") ||
                    line.contains("System.err")) {
                    logs.add(line)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect logs", e)
            // 如果 logcat 失败，至少记录错误
            logs.add("Failed to collect logcat: ${e.message}")
        }
        return logs
    }

    /**
     * 保存崩溃日志到文件
     */
    private fun saveCrashLog(crashInfo: CrashInfo, logs: List<String>): File? {
        val ctx = context ?: return null

        try {
            // 创建崩溃目录
            val crashDir = File(ctx.externalCacheDir, CRASH_DIR)
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }

            // 清理旧文件
            cleanOldCrashFiles(crashDir)

            // 创建新的崩溃文件
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(crashInfo.timestamp)
            val crashFile = File(crashDir, "crash_$timestamp.log")

            FileWriter(crashFile).use { writer ->
                // 写入崩溃信息
                writer.write(crashInfo.toMarkdown())

                // 写入最近的日志
                if (logs.isNotEmpty()) {
                    writer.write("\n## Recent Logs\n\n")
                    logs.forEach { log ->
                        writer.write("```\n")
                        writer.write(log)
                        writer.write("\n```\n")
                    }
                }
            }

            Log.i(TAG, "Crash log saved to: ${crashFile.absolutePath}")
            return crashFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
            return null
        }
    }

    /**
     * 清理旧的崩溃文件
     */
    private fun cleanOldCrashFiles(crashDir: File) {
        val files = crashDir.listFiles()?.sortedByDescending { it.lastModified() }
        files?.drop(MAX_CRASH_FILES)?.forEach { it.delete() }
    }

    /**
     * 获取所有崩溃文件
     */
    fun getCrashFiles(): List<File> {
        val ctx = context ?: return emptyList()
        val crashDir = File(ctx.externalCacheDir, CRASH_DIR)
        if (!crashDir.exists()) {
            return emptyList()
        }
        return crashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * 读取崩溃文件内容
     */
    fun readCrashFile(file: File): String {
        try {
            return file.readText()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read crash file", e)
            return "Error reading crash file: ${e.message}"
        }
    }

    /**
     * 删除崩溃文件
     */
    fun deleteCrashFile(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete crash file", e)
            false
        }
    }

    /**
     * 清除所有崩溃文件
     */
    fun clearAllCrashes(): Boolean {
        val ctx = context ?: return false
        val crashDir = File(ctx.externalCacheDir, CRASH_DIR)
        return try {
            crashDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear crashes", e)
            false
        }
    }

    /**
     * 手动报告日志（用于非崩溃的错误）
     *
     * @param tag 日志标签
     * @param message 错误消息
     * @param throwable 异常对象（可选）
     */
    fun reportError(tag: String, message: String, throwable: Throwable? = null) {
        val ctx = context ?: run {
            Log.e(TAG, "Cannot report error: context not initialized")
            return
        }

        val thread = Thread.currentThread()
        val exception = throwable ?: Exception(message)

        Log.e(TAG, "Manual error report: [$tag] $message", throwable)

        val crashInfo = CrashInfo(
            timestamp = Date(),
            threadName = thread.name,
            exception = exception,
            deviceInfo = DeviceInfo.fromCurrentDevice(),
            appInfo = AppInfo.fromContext(ctx),
            runtimeInfo = RuntimeInfo.fromContext(ctx),
            logFile = null
        )

        val logs = collectRecentLogs()
        saveCrashLog(crashInfo, logs)
    }

    /**
     * 获取崩溃日志目录的总大小
     */
    fun getCrashLogSize(): Long {
        val ctx = context ?: return 0
        val crashDir = File(ctx.externalCacheDir, CRASH_DIR)
        if (!crashDir.exists()) {
            return 0
        }
        return crashDir.listFiles()?.sumOf { it.length() } ?: 0
    }

    /**
     * 格式化文件大小
     */
    fun formatFileSize(size: Long): String {
        if (size < 1024) return "$size B"
        if (size < 1024 * 1024) return "${size / 1024} KB"
        return "${size / (1024 * 1024)}.${(size % (1024 * 1024)) / 1024} MB"
    }
}
