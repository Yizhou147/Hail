package com.aistra.hail.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aistra.hail.R
import com.aistra.hail.app.FocusData
import com.aistra.hail.app.FocusManager
import com.aistra.hail.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 专注模式的全屏锁定倒计时界面：每秒刷新剩余时间，到点后按快照恢复并回调解除锁定。
 */
@Composable
fun FocusLockScreen(onFinished: () -> Unit) {
    var remaining by remember { mutableLongStateOf(FocusData.remainingMillis) }

    LaunchedEffect(Unit) {
        while (FocusData.isActive) {
            remaining = FocusData.remainingMillis
            if (remaining <= 0) break
            delay(1000L)
        }
        // 到点：后台恢复快照并结束会话
        withContext(Dispatchers.IO) { FocusManager.restoreAndEnd() }
        onFinished()
    }

    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 不透明背景遮住下层内容
                .background(MaterialTheme.colorScheme.background)
                // 消费所有触摸事件，阻止点击穿透到下层页面
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                }
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.focus_mode_active),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = FocusData.formatDuration(remaining),
                style = MaterialTheme.typography.displayLarge,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.focus_apps_paused, FocusData.blacklist.size),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
