package com.aistra.hail.services

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.aistra.hail.R
import com.aistra.hail.app.FocusData
import com.aistra.hail.app.FocusManager
import com.aistra.hail.ui.main.MainActivity
import com.aistra.hail.utils.HLog
import com.aistra.hail.utils.MiuiIsland

/**
 * 专注模式前台服务：常驻通知显示剩余时间；仅当系统授予 HyperOS 焦点通知
 * 权限时才注入超级岛参数（miui.focus.param），否则退化为普通通知。
 * 每秒检查是否到点，到点后按快照恢复并结束。
 */
class FocusService : Service() {
    private val channelID = javaClass.simpleName
    private val handler = Handler(Looper.getMainLooper())
    // 系统是否放行本应用的焦点通知（服务运行期间一般不变，启动时查询一次）
    private var islandPermission = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val remaining = FocusData.endTime - now
            if (!FocusData.isActive || remaining <= 0) {
                // 到点：后台恢复并结束服务
                Thread { FocusManager.restoreAndEnd() }.start()
                return
            }
            // 每秒刷新通知：倒计时读秒 + 超级岛胶囊实时更新
            updateNotification(remaining)
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        HLog.i("Hail", "FocusService onStartCommand at ${System.currentTimeMillis()}")
        createNotificationChannel()
        // 先以普通样式立即弹出通知，避免主线程阻塞导致通知延迟显示
        startForeground(100, buildNotification(FocusData.remainingMillis))
        HLog.i("Hail", "FocusService startForeground done at ${System.currentTimeMillis()}")
        // 后台查询 HyperOS 焦点通知权限（跨进程调用可能较慢），
        // 通过后立即以岛参数重新发布，让通知上岛
        Thread {
            val t0 = System.currentTimeMillis()
            val granted = runCatching { MiuiIsland.hasFocusPermission(this) }.getOrDefault(false)
            HLog.i("Hail", "FocusService island permission = $granted, query took ${System.currentTimeMillis() - t0} ms")
            islandPermission = granted
            if (granted) {
                HLog.i("Hail", "FocusService posting island update at ${System.currentTimeMillis()}")
                handler.post { updateNotification(FocusData.remainingMillis) }
            }
        }.start()
        handler.post(tickRunnable)
        return START_STICKY
    }

    private fun updateNotification(remaining: Long) {
        val manager = NotificationManagerCompat.from(this)
        runCatching { manager.notify(100, buildNotification(remaining)) }
    }

    private fun buildNotification(remaining: Long): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val frontTitle = getString(R.string.focus_notification_title)
        val durationText = FocusData.formatDuration(remaining)
        val contentText = getString(R.string.focus_notification_text, durationText)
        val builder = NotificationCompat.Builder(this, channelID)
            .setSmallIcon(R.drawable.ic_outline_timer)
            .setContentTitle(frontTitle)
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        // HyperOS 超级岛：仅当系统授予焦点通知权限时才注入岛参数。
        // 无权限时反复注入会被系统不断尝试上岛并拒绝，导致通知闪烁，因此退化为普通通知。
        if (islandPermission) {
            builder.addExtras(
                MiuiIsland.buildIslandExtras(this, frontTitle, durationText, contentText)
            )
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        // 重要度 HIGH：HyperOS 超级岛仅对高重要度通知呈现，且首次只 alert 一次
        val channel = NotificationChannelCompat.Builder(
            channelID, NotificationManagerCompat.IMPORTANCE_HIGH
        ).setName(getString(R.string.title_focus)).build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
