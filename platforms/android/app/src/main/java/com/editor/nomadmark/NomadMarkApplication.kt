package com.editor.nomadmark

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * NomadMark 应用程序入口
 *
 * 功能特性：
 * - 初始化崩溃日志收集器
 * - 自动检测上次崩溃并通知用户
 * - 设置崩溃监听器
 * - 创建通知渠道
 */
class NomadMarkApplication : Application() {

    companion object {
        private const val TAG = "NomadMark"
        private const val CHANNEL_ID = "crash_notification_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化崩溃日志收集器
        CrashHandler.init(this)

        // 创建通知渠道
        createNotificationChannel()

        // 检查上次是否有崩溃
        checkPreviousCrash()

        // 设置崩溃监听器
        CrashHandler.setLogListener { crashInfo ->
            // 在发生崩溃时执行的操作
            Log.e(TAG, "Crash detected: ${crashInfo.exception.message}")
            Log.e(TAG, "Device: ${crashInfo.deviceInfo.manufacturer} ${crashInfo.deviceInfo.model}")
            Log.e(TAG, "App Version: ${crashInfo.appInfo.versionName}")
            Log.e(TAG, "Available Memory: ${crashInfo.runtimeInfo.availableMemory} MB")

            // 这里可以添加上传到服务器的逻辑
            uploadCrashReport(crashInfo)
        }

        Log.i(TAG, "NomadMarkApplication initialized - Version: ${getAppVersion()}")

        // 检测 Explorer 系统库状态
        detectExplorerStatus()

        // 测试 Document 目录访问
        testDocumentAccess()
    }

    /**
     * 检查上次是否有崩溃
     */
    private fun checkPreviousCrash() {
        val crashInfo = CrashHandler.checkPreviousCrash()

        if (crashInfo != null) {
            Log.w(TAG, "Previous crash detected from ${crashInfo.timestamp}")

            // 显示通知
            showCrashNotification(crashInfo)

            // 记录崩溃信息
            crashInfo.logFile?.let { file ->
                Log.w(TAG, "Crash log available at: ${file.absolutePath}")
            }
        } else {
            Log.i(TAG, "No previous crash detected")
        }
    }

    /**
     * 显示崩溃通知
     */
    @Suppress("UNUSED_PARAMETER")
    private fun showCrashNotification(crashInfo: CrashHandler.CrashInfo) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("应用上次意外关闭")
            .setContentText("点击查看崩溃详情")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show crash notification", e)
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "崩溃通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "应用崩溃时显示通知"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 上传崩溃报告（占位函数）
     *
     * TODO: 实现将崩溃报告上传到服务器的逻辑
     */
    @Suppress("UNUSED_PARAMETER")
    private fun uploadCrashReport(crashInfo: CrashHandler.CrashInfo) {
        // 这里可以实现上传到服务器的逻辑
        // 例如：
        // - 使用 Retrofit 或 OkHttp 发送 POST 请求
        // - 将崩溃信息作为 JSON 发送
        // - 也可以将 logFile 作为 multipart 上传

        Log.d(TAG, "Upload crash report to server (not implemented yet)")

        // 示例代码（需要添加网络库依赖）：
        /*
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("timestamp", crashInfo.timestamp.time)
            put("thread", crashInfo.threadName)
            put("exception", crashInfo.exception.stackTraceToString())
            put("device", JSONObject().apply {
                put("model", crashInfo.deviceInfo.model)
                put("manufacturer", crashInfo.deviceInfo.manufacturer)
                put("android_version", crashInfo.deviceInfo.androidVersion)
                put("api_level", crashInfo.deviceInfo.apiLevel)
            })
            put("app", JSONObject().apply {
                put("version", crashInfo.appInfo.versionName)
                put("version_code", crashInfo.appInfo.versionCode)
                put("package", crashInfo.appInfo.packageName)
            })
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://your-server.com/api/crash-reports")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to upload crash report", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                Log.i(TAG, "Crash report uploaded successfully")
            }
        })
        */
    }

    /**
     * 获取应用版本
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * 检测 Explorer 系统库状态
     */
    private fun detectExplorerStatus() {
        try {
            ExplorerUtils.logFullStatus(this)

            val status = ExplorerUtils.checkStatus(this)
            if (status.canInvoke) {
                Log.i(TAG, "✅ Explorer 系统库可用，可以通过 Intent 调用文件选择功能")
            } else if (status.exists) {
                Log.w(TAG, "⚠️ Explorer 存在但可能无法正常调用")
            } else {
                Log.w(TAG, "⚠️ Explorer 系统库不存在，文件选择功能可能不可用")
            }
        } catch (e: Exception) {
            Log.e(TAG, "检测 Explorer 状态时出错", e)
        }
    }

    /**
     * 测试 Document 目录访问
     */
    private fun testDocumentAccess() {
        try {
            Log.i(TAG, "========== Document 目录访问测试 ==========")
            val testResult = StorageTest.testDocumentAccess()
            Log.i(TAG, testResult)
            Log.i(TAG, "===========================================")

            // 检查最佳路径
            val bestPath = StorageTest.getBestDocumentPath()
            if (bestPath != null) {
                Log.i(TAG, "✅ 找到可访问的 Document 目录: ${bestPath.absolutePath}")
            } else {
                Log.w(TAG, "⚠️ 未找到可访问的 Document 目录")
            }
        } catch (e: Exception) {
            Log.e(TAG, "测试 Document 访问时出错", e)
        }
    }
}
