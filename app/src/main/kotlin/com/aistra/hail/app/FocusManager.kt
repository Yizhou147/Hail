package com.aistra.hail.app

import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.R
import com.aistra.hail.services.FocusService
import com.aistra.hail.utils.HLog
import com.aistra.hail.utils.HShizuku
import com.aistra.hail.utils.MiuiIsland
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * 专注模式的核心逻辑：开始（快照 + Shizuku 暂停 + 启服务）、结束（按快照恢复 + 停服务 + 通知）。
 */
object FocusManager {
    private const val FINISH_CHANNEL_ID = "focus_finished"
    private const val FINISH_NOTIFICATION_ID = 101

    /** Shizuku 是否可用 */
    fun shizukuReady(): Boolean = runCatching {
        !Shizuku.isPreV11() && Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * 开始专注。
     * 返回 null 表示成功，否则返回错误提示文案。
     */
    suspend fun startFocus(minutes: Int): String? = withContext(Dispatchers.IO) {
        if (minutes !in FocusData.MIN_MINUTES..FocusData.MAX_MINUTES) {
            return@withContext app.getString(R.string.focus_time_invalid)
        }
        if (FocusData.blacklist.isEmpty()) {
            return@withContext app.getString(R.string.focus_select_blacklist_first)
        }
        if (!shizukuReady()) {
            return@withContext app.getString(R.string.focus_shizuku_unavailable)
        }
        // 快照：记录开始前各应用冻结状态
        val snapshot = FocusData.blacklist.associateWith { AppManager.isAppFrozen(it) }
        // 先开始会话并启动服务（立即弹通知），避免逐个挂起耗时导致通知延迟十几秒才出现
        FocusData.beginSession(minutes)
        startFocusService()
        // 逐个暂停黑名单应用（系统弹窗文案定制为专注模式）
        var suspended = 0
        FocusData.blacklist.forEach {
            if (HShizuku.setAppSuspendedForFocus(it, true)) suspended++
        }
        if (suspended == 0) {
            // 全部失败：回滚会话并停止服务
            FocusData.endSession()
            app.stopService(Intent(app, FocusService::class.java))
            return@withContext app.getString(R.string.operation_failed, app.getString(R.string.permission_denied))
        }
        FocusData.saveSnapshot(snapshot)
        // 异步检测 HyperOS 超级岛权限并记录日志（诊断用：用户开启上岛但未放行时，通知不会上岛）
        Thread {
            val t0 = System.currentTimeMillis()
            val granted = MiuiIsland.hasFocusPermission(app)
            HLog.i("Hail", "Focus started, island permission = $granted, query took ${System.currentTimeMillis() - t0} ms")
        }.start()
        null
    }

    /** 启动前台专注服务（在后台被拉起时可能失败，忽略即可，由界面兜底） */
    fun startFocusService() {
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, FocusService::class.java))
        }.onFailure { HLog.e(it) }
    }

    /**
     * 结束专注：按快照恢复黑名单应用状态，清理会话，停止服务并发送结束通知。
     * 必须先将会话标记为结束（active=false），否则会被 AppManager 的专注屏蔽拦截。
     * 应在后台线程调用。
     */
    fun restoreAndEnd(): Boolean {
        if (!FocusData.isActive) return false
        FocusData.endSession()
        val snapshot = FocusData.snapshot.toMap()
        FocusData.saveSnapshot(emptyMap())
        // 开始专注时无条件挂起（setPackagesSuspended）了黑名单应用，
        // 必须先全部解除挂起，否则非 SUSPEND 工作模式下恢复会被跳过，应用保持挂起无法打开
        snapshot.keys.forEach { pkg ->
            runCatching { HShizuku.setAppSuspendedForFocus(pkg, false) }.onFailure { HLog.e(it) }
        }
        // 再按快照恢复原冻结状态
        snapshot.forEach { (pkg, frozen) ->
            if (frozen != AppManager.isAppFrozen(pkg)) {
                runCatching { AppManager.setAppFrozen(pkg, frozen) }.onFailure { HLog.e(it) }
            }
        }
        app.stopService(Intent(app, FocusService::class.java))
        showFinishNotification()
        return true
    }

    private fun showFinishNotification() {
        val manager = NotificationManagerCompat.from(app)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(FINISH_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(app.getString(R.string.title_focus)).build()
        )
        runCatching {
            manager.notify(
                FINISH_NOTIFICATION_ID,
                NotificationCompat.Builder(app, FINISH_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_outline_timer)
                    .setContentTitle(app.getString(R.string.focus_finished_title))
                    .setContentText(app.getString(R.string.focus_finished_text))
                    .setAutoCancel(true)
                    .build()
            )
        }.onFailure { HLog.e(it) }
    }
}
