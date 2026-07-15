package com.editor.nomadmark

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

/**
 * Explorer 系统库检测工具
 *
 * 用于检测 Supernote 设备上的 explorer 系统库状态
 * Explorer 可能集成在不同的应用包中（如 inbox、document 等）
 */
object ExplorerUtils {

    private const val TAG = "ExplorerUtils"

    /**
     * Explorer 可能所在的包名（按优先级排序）
     */
    private val EXPLORER_PACKAGES = listOf(
        "com.ratta.supernote.inbox",      // 实际集成位置
        "com.ratta.supernote.explorer",   // 原始包名（可能不存在）
        "com.supernote.document"          // 另一个可能的位置
    )

    /**
     * Explorer 包信息
     */
    data class ExplorerPackageInfo(
        val packageName: String,
        val exists: Boolean,
        val hasExplorerActivity: Boolean,
        val activities: List<String>
    )

    /**
     * Explorer 状态数据类
     */
    data class ExplorerStatus(
        val exists: Boolean,
        val packageName: String = "",
        val isSystemApp: Boolean = false,
        val installPath: String = "",
        val isSystemPartition: Boolean = false,
        val version: String = "",
        val uid: Int = -1,
        val targetSdkVersion: Int = 0,
        val availablePackages: List<ExplorerPackageInfo> = emptyList()
    ) {
        /**
         * 是否可以通过 Intent 调用
         */
        val canInvoke: Boolean
            get() = exists && packageName.isNotEmpty() && (isSystemApp || isSystemPartition)

        /**
         * 获取状态描述
         */
        fun getDescription(): String {
            return if (!exists) {
                """❌ Explorer 功能不可用
                   |
                   |检查的包:
                   |${availablePackages.joinToString("\n") { "  - ${it.packageName}: ${if (it.exists) "存在" else "不存在"}${if (it.hasExplorerActivity) " (✅有Activity)" else ""}" }}
                   |
                   |可用活动: ${availablePackages.firstOrNull { it.hasExplorerActivity }?.activities?.joinToString(", ") ?: "无"}
                """.trimMargin()
            } else {
                """Explorer 状态: ✅ 可用
                   |
                   |  - 包名: $packageName
                   |  - 系统应用: $isSystemApp
                   |  - 安装路径: $installPath
                   |  - 系统分区: $isSystemPartition
                   |  - 版本: $version
                   |  - UID: $uid
                   |  - 目标 SDK: $targetSdkVersion
                   |  - 可通过 Intent 调用: $canInvoke
                """.trimMargin()
            }
        }

        /**
         * 获取 SelectFileActivity 的完整类名
         */
        fun getSelectFileActivityClass(): String {
            return if (canInvoke) {
                "$packageName.explorer.SelectFileActivity"
            } else {
                ""
            }
        }

        /**
         * 获取 FileManagerMainActivity 的完整类名
         */
        fun getFileManagerActivityClass(): String {
            return if (canInvoke) {
                "$packageName.explorer.FileManagerMainActivity"
            } else {
                ""
            }
        }
    }

    /**
     * 获取所有 explorer 包的状态信息
     */
    private fun getAllPackageInfo(context: Context): List<ExplorerPackageInfo> {
        val pm = context.packageManager
        return EXPLORER_PACKAGES.map { packageName ->
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)

                // 查找 explorer 相关的 Activity
                val explorerActivities = packageInfo.activities?.mapNotNull { activityInfo ->
                    activityInfo.name.takeIf { it.contains("explorer", ignoreCase = true) }
                } ?: emptyList()

                ExplorerPackageInfo(
                    packageName = packageName,
                    exists = true,
                    hasExplorerActivity = explorerActivities.isNotEmpty(),
                    activities = explorerActivities
                )
            } catch (e: Exception) {
                ExplorerPackageInfo(
                    packageName = packageName,
                    exists = false,
                    hasExplorerActivity = false,
                    activities = emptyList()
                )
            }
        }
    }

    /**
     * 获取实际可用的 explorer 包名
     */
    private fun getAvailableExplorerPackage(context: Context): String? {
        val pm = context.packageManager
        for (packageName in EXPLORER_PACKAGES) {
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)

                // 检查是否有 SelectFileActivity
                val hasSelectFileActivity = packageInfo.activities?.any { activityInfo ->
                    activityInfo.name == "$packageName.explorer.SelectFileActivity" ||
                    activityInfo.name.endsWith(".explorer.SelectFileActivity")
                } == true

                if (hasSelectFileActivity) {
                    Log.d(TAG, "找到可用的 Explorer 包: $packageName")
                    return packageName
                }
            } catch (e: Exception) {
                // 继续检查下一个
            }
        }
        return null
    }

    /**
     * 检查 Explorer 状态
     */
    fun checkStatus(context: Context): ExplorerStatus {
        val pm = context.packageManager
        val allPackageInfo = getAllPackageInfo(context)
        val availablePackage = getAvailableExplorerPackage(context)

        return if (availablePackage != null) {
            try {
                val appInfo = pm.getApplicationInfo(availablePackage, 0)
                val packageInfo = pm.getPackageInfo(availablePackage, 0)

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val sourceDir = appInfo.sourceDir
                val isSystemPartition = sourceDir.startsWith("/system/") ||
                                       sourceDir.startsWith("/system/priv-app/") ||
                                       sourceDir.startsWith("/system_ext/") ||
                                       sourceDir.startsWith("/vendor/app/")

                ExplorerStatus(
                    exists = true,
                    packageName = availablePackage,
                    isSystemApp = isSystem || isUpdatedSystemApp,
                    installPath = sourceDir,
                    isSystemPartition = isSystemPartition,
                    version = packageInfo.versionName ?: "unknown",
                    uid = packageInfo.applicationInfo.uid,
                    targetSdkVersion = appInfo.targetSdkVersion,
                    availablePackages = allPackageInfo
                )
            } catch (e: Exception) {
                Log.e(TAG, "获取 Explorer 详细信息时出错", e)
                ExplorerStatus(
                    exists = false,
                    availablePackages = allPackageInfo
                )
            }
        } else {
            Log.w(TAG, "未找到可用的 Explorer 包")
            ExplorerStatus(
                exists = false,
                availablePackages = allPackageInfo
            )
        }
    }

    /**
     * 检查是否是系统应用（快速检查）
     */
    fun isSystemApp(context: Context): Boolean {
        return checkStatus(context).isSystemApp
    }

    /**
     * 检查是否存在（快速检查）
     */
    fun exists(context: Context): Boolean {
        return checkStatus(context).exists
    }

    /**
     * 获取 SelectFileActivity 的 Intent
     */
    fun getSelectFileIntent(context: Context, selectedPaths: ArrayList<String>? = null): android.content.Intent? {
        val status = checkStatus(context)
        if (!status.canInvoke) {
            Log.w(TAG, "Explorer 不可用，无法创建 Intent")
            return null
        }

        return android.content.Intent().apply {
            setClassName(status.packageName, status.getSelectFileActivityClass())
            if (selectedPaths != null && selectedPaths.isNotEmpty()) {
                putStringArrayListExtra("select_path", selectedPaths)
            }
        }
    }

    /**
     * 检查 SelectFileActivity 是否可用
     */
    fun isSelectFileActivityAvailable(context: Context): Boolean {
        return checkStatus(context).canInvoke
    }

    /**
     * 记录完整状态到日志
     */
    fun logFullStatus(context: Context) {
        val status = checkStatus(context)

        Log.i(TAG, "========== Explorer 状态检测 ==========")
        Log.i(TAG, status.getDescription())

        if (status.exists) {
            Log.i(TAG, "✅ Explorer 可用:")
            Log.i(TAG, "  - 包名: ${status.packageName}")
            Log.i(TAG, "  - SelectFileActivity: ${status.getSelectFileActivityClass()}")
            Log.i(TAG, "  - FileManagerMainActivity: ${status.getFileManagerActivityClass()}")
        } else {
            Log.w(TAG, "⚠️ Explorer 不可用，检查了 ${status.availablePackages.size} 个可能的包")
        }

        Log.i(TAG, "========================================")
    }

    /**
     * 启动 SelectFileActivity
     * @return 是否成功启动
     */
    fun startSelectFileActivity(
        context: Context,
        selectedPaths: ArrayList<String>? = null,
        requestCode: Int = 201
    ): Boolean {
        val intent = getSelectFileIntent(context, selectedPaths)
        return if (intent != null && context is android.app.Activity) {
            try {
                context.startActivityForResult(intent, requestCode)
                true
            } catch (e: Exception) {
                Log.e(TAG, "启动 SelectFileActivity 失败", e)
                false
            }
        } else {
            Log.w(TAG, "无法启动 SelectFileActivity: Explorer 不可用或 Context 不是 Activity")
            false
        }
    }
}
