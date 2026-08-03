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

/**
 * 专注模式前台服务：常驻通知显示剩余时间（HyperOS 超级岛会自动呈现），
 * 每秒检查是否到点，到点后按快照恢复并结束。
 */
class FocusService : Service() {
    private val channelID = javaClass.simpleName
    private val handler = Handler(Looper.getMainLooper())

    private val tickRunnable = object : Runnable {
        private var lastNotificationUpdate = 0L
        override fun run() {
            val now = System.currentTimeMillis()
            val remaining = FocusData.endTime - now
            if (!FocusData.isActive || remaining <= 0) {
                // 到点：后台恢复并结束服务
                Thread { FocusManager.restoreAndEnd() }.start()
                return
            }
            // 每 30 秒刷新一次通知，避免频繁刷新耗电
            if (now - lastNotificationUpdate >= 30_000L) {
                updateNotification(remaining)
                lastNotificationUpdate = now
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(100, buildNotification(FocusData.remainingMillis))
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
        return NotificationCompat.Builder(this, channelID)
            .setSmallIcon(R.drawable.ic_outline_timer)
            .setContentTitle(getString(R.string.focus_notification_title))
            .setContentText(
                getString(R.string.focus_notification_text, FocusData.formatDuration(remaining))
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            channelID, NotificationManagerCompat.IMPORTANCE_LOW
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
