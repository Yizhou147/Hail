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
import com.aistra.hail.app.HailData
import com.aistra.hail.ui.main.MainActivity
import com.aistra.hail.utils.HLog
import com.aistra.hail.utils.MiuiIsland

/**
 * 专注模式前台服务：常驻通知显示剩余时间；是否注入超级岛参数由设置
 * HailData.FOCUS_ISLAND 控制。每秒检查是否到点，到点后按快照恢复并结束。
 */
class FocusService : Service() {
    private val channelID = javaClass.simpleName
    private val handler = Handler(Looper.getMainLooper())
    // 超级岛开关（实时读取设置，中途切换立即生效）
    private val islandEnabled get() = HailData.focusIsland

    private val tickRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val remaining = FocusData.endTime - now
            if (!FocusData.isActive || remaining <= 0) {
                // 到点：后台恢复并结束服务
                Thread { FocusManager.restoreAndEnd() }.start()
                return
            }
            // 每秒刷新通知：倒计时读秒，按设置决定是否注入超级岛参数
            updateNotification(remaining)
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        HLog.i("Hail", "FocusService onStartCommand at ${System.currentTimeMillis()}")
        createNotificationChannel()
        val notification = buildNotification(FocusData.remainingMillis)
        // HyperOS 智能省电会延迟受限应用 FGS（startForeground）的首发通知约 10 秒才发布，
        // 而普通 notify 发布/更新已存在的通知是即时的。因此先 notify 发布普通通知，再
        // startForeground 将同 id 通知标记为前台通知，最后再 notify 一次覆盖可能被延迟的
        // FGS 通知，使通知立刻出现。
        NotificationManagerCompat.from(this).notify(100, notification)
        HLog.i("Hail", "FocusService notify first at ${System.currentTimeMillis()}")
        startForeground(100, notification)
        HLog.i("Hail", "FocusService startForeground done at ${System.currentTimeMillis()}")
        updateNotification(FocusData.remainingMillis)
        HLog.i("Hail", "FocusService notify redelivery done at ${System.currentTimeMillis()}")
        handler.post(tickRunnable)
        return START_STICKY
    }

    private fun updateNotification(remaining: Long, island: Boolean = islandEnabled) {
        val manager = NotificationManagerCompat.from(this)
        runCatching { manager.notify(100, buildNotification(remaining, island)) }
    }

    private fun buildNotification(remaining: Long, island: Boolean = islandEnabled): android.app.Notification {
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
        // HyperOS 超级岛：仅当设置开启时才注入岛参数。
        if (island) {
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
