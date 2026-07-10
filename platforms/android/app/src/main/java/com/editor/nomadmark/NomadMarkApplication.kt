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
}
