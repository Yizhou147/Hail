package com.aistra.hail.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aistra.hail.app.FocusData
import com.aistra.hail.app.FocusManager
import com.aistra.hail.services.FocusService
import com.aistra.hail.utils.HLog

/**
 * 开机 / 应用更新后恢复专注倒计时：未到点则重启前台服务，已到点则补执行恢复。
 */
class FocusBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!FocusData.isActive) return
        if (FocusData.endTime > System.currentTimeMillis()) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, FocusService::class.java))
            }.onFailure { HLog.e(it) }
        } else {
            // 已到点但未解冻：补执行快照恢复
            Thread { FocusManager.restoreAndEnd() }.start()
        }
    }
}
