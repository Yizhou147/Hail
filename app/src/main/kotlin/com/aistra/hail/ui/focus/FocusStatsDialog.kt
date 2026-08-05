package com.aistra.hail.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.R
import com.aistra.hail.app.FocusData
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 专注统计详情页：日/周/月/年聚合、日均/最长/目标进度、近 7/30 天柱状图、
 * 月历热力图与逐次会话明细。以覆盖层 + Compose 单窗口展示（与导入对话框一致）。
 */
@Composable
fun FocusStatsDialog(onDismiss: () -> Unit) {
    var chartDays by remember { mutableStateOf(7) }
    var calOffset by remember { mutableStateOf(0) }
    var showTargetDialog by remember { mutableStateOf(false) }

    val target = FocusData.targetMinutes
    val today = focusMinutesInRange(dayBounds(0))
    val week = focusMinutesInRange(weekBounds())
    val month = focusMinutesInRange(monthBounds(0))
    val year = focusMinutesInRange(yearBounds())
    val total = FocusData.totalMinutes
    val avg = dailyAverageMinutes()
    val longest = FocusData.sessions.maxOfOrNull { it.minutes } ?: 0

    if (showTargetDialog) {
        FocusTargetDialog(
            initial = target,
            onDismiss = { showTargetDialog = false },
            onSave = { input ->
                input.toIntOrNull()?.takeIf { it in 1..1440 }?.let { FocusData.targetMinutes = it }
                showTargetDialog = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.focus_stats_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.focus_stats_close)
                        )
                    }
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp)
                ) {
                    StatCards(
                        today = today, week = week, month = month, year = year,
                        total = total, avg = avg, longest = longest
                    )
                    TargetProgress(
                        today = today, target = target,
                        onClick = { showTargetDialog = true }
                    )
                    ChartSection(
                        chartDays = chartDays,
                        onChartDaysChange = { chartDays = it }
                    )
                    CalendarSection(
                        offsetMonths = calOffset,
                        onOffsetChange = { calOffset = it }
                    )
                    SessionsSection()
                }
            }
        }
    }
}

/** 时段聚合卡片：今日/本周/本月/本年 + 累计/日均/最长 */
@Composable
private fun StatCards(today: Int, week: Int, month: Int, year: Int, total: Long, avg: Int, longest: Int) {
    val primary = listOf(
        stringResource(R.string.focus_stats_today) to today,
        stringResource(R.string.focus_stats_week) to week,
        stringResource(R.string.focus_stats_month) to month,
        stringResource(R.string.focus_stats_year) to year,
    )
    val secondary = listOf(
        stringResource(R.string.focus_stats_total) to total,
        stringResource(R.string.focus_stats_daily_avg) to avg.toLong(),
        stringResource(R.string.focus_stats_longest) to longest.toLong(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        primary.forEach { (label, value) ->
            StatCard(label = label, minutes = value.toLong(), modifier = Modifier.weight(1f))
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        secondary.forEach { (label, value) ->
            StatCard(label = label, minutes = value, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, minutes: Long, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatMinutes(minutes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

/** 每日目标进度，点击可重新设置目标 */
@Composable
private fun TargetProgress(today: Int, target: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.focus_stats_target),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.focus_stats_target_progress, today, target),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val fraction = if (target > 0) (today.toFloat() / target).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/** 近 7/30 天柱状图 */
@Composable
private fun ChartSection(chartDays: Int, onChartDaysChange: (Int) -> Unit) {
    val days = chartDays
    val values = (days - 1 downTo 0).map { offset -> focusMinutesInRange(dayBounds(-offset)) }.reversed()
    val dayFormat = SimpleDateFormat(if (days == 7) "d" else "M/d", Locale.getDefault())
    val labels = values.indices.map { i ->
        if (days == 30 && i % 5 != 0) "" else dayFormat.format(Date(dayBounds(-(days - 1 - i))[0]))
    }

    Text(
        text = stringResource(R.string.focus_stats_chart_recent),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7 to R.string.focus_stats_chart_7d, 30 to R.string.focus_stats_chart_30d).forEach { (daysValue, resId) ->
            FilterChip(
                selected = days == daysValue,
                onClick = { onChartDaysChange(daysValue) },
                label = { Text(text = stringResource(resId)) }
            )
        }
    }
    FocusBarChart(values = values, labels = labels, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun FocusBarChart(values: List<Int>, labels: List<String>, color: Color) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val slotWidth = maxWidth / values.size
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            values.forEachIndexed { i, v ->
                if (v > 0) {
                    val barWidth = slotWidth.toPx() * 0.55f
                    val barHeight = size.height * v / max
                    drawRoundRect(
                        color = color.copy(alpha = 0.85f),
                        topLeft = Offset(i * slotWidth.toPx() + (slotWidth.toPx() - barWidth) / 2, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 104.dp)) {
            labels.forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 月历热力图：当月每天一个格子，深浅表示专注时长，支持翻月 */
@Composable
private fun CalendarSection(offsetMonths: Int, onOffsetChange: (Int) -> Unit) {
    val cal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, offsetMonths)
    }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH)
    val firstDow = cal.get(Calendar.DAY_OF_WEEK)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    // 当月每天专注分钟数（Monday 为第一天，前导空位用于对齐星期）
    val dayMinutes = (1..daysInMonth).associateWith { day ->
        val c = Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
        focusMinutesInRange(longArrayOf(start, c.timeInMillis))
    }
    val monthMax = (dayMinutes.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val leading = (firstDow + 5) % 7 // 周一起始的空位数

    Text(
        text = stringResource(R.string.focus_stats_calendar),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { onOffsetChange(offsetMonths - 1) }) {
            Text(text = "‹", fontSize = 20.sp)
        }
        Text(
            text = stringResource(R.string.focus_stats_calendar_title, year, month + 1),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { if (offsetMonths < 0) onOffsetChange(offsetMonths + 1) }) {
            Text(text = "›", fontSize = 20.sp)
        }
    }

    val weekdayNames = DateFormatSymbols.getInstance().shortWeekdays
    val headers = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY).map { weekdayNames[it] }
    val cellSize = 34.dp
    val color = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            headers.forEach { header ->
                Box(modifier = Modifier.width(cellSize), contentAlignment = Alignment.Center) {
                    Text(text = header, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val cells: List<Int?> = List(leading) { null } + (1..daysInMonth).map { it }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { index ->
                    val day = week.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (day == null) Color.Transparent
                                else {
                                    val m = dayMinutes[day] ?: 0
                                    if (m <= 0) MaterialTheme.colorScheme.surfaceContainerHighest
                                    else {
                                        val intensity = (m.toFloat() / monthMax).coerceIn(0f, 1f)
                                        color.copy(alpha = 0.2f + 0.8f * intensity)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            val m = dayMinutes[day] ?: 0
                            val strong = m > 0 && (m.toFloat() / monthMax) > 0.5f
                            Text(
                                text = day.toString(),
                                fontSize = 10.sp,
                                color = if (strong) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 逐次会话明细（按开始时间倒序） */
@Composable
private fun SessionsSection() {
    Text(
        text = stringResource(R.string.focus_stats_sessions),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
    val sessions = FocusData.sessions.sortedByDescending { it.start }
    if (sessions.isEmpty()) {
        Text(
            text = stringResource(R.string.focus_stats_no_sessions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        return
    }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sessions.forEach { session ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = dateFormat.format(Date(session.start)), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(
                                R.string.focus_stats_session_time,
                                timeFormat.format(Date(session.start)),
                                timeFormat.format(Date(session.end))
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatMinutes(session.minutes.toLong()),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** 设置每日目标的输入对话框 */
@Composable
private fun FocusTargetDialog(initial: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var input by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.focus_stats_target_set)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(4) },
                label = { Text(text = stringResource(R.string.focus_stats_target_hint)) },
                suffix = { Text(text = "min") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(input) }) { Text(text = stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(android.R.string.cancel)) }
        }
    )
}

// ---------- 时间计算与格式化（系统时区，兼容 minSdk 23） ----------

/** 偏移 N 天的起止时间戳（[start, end)） */
private fun dayBounds(offsetDays: Int): LongArray {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.DAY_OF_YEAR, offsetDays)
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, 1)
    return longArrayOf(start, cal.timeInMillis)
}

/** 本周（周一起）的起止时间戳 */
private fun weekBounds(): LongArray {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.DAY_OF_YEAR, -((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7))
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, 7)
    return longArrayOf(start, cal.timeInMillis)
}

/** 偏移 N 个月的起止时间戳（当月起） */
private fun monthBounds(offsetMonths: Int): LongArray {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.MONTH, offsetMonths)
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    return longArrayOf(start, cal.timeInMillis)
}

/** 本年的起止时间戳 */
private fun yearBounds(): LongArray {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_YEAR, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val start = cal.timeInMillis
    cal.add(Calendar.YEAR, 1)
    return longArrayOf(start, cal.timeInMillis)
}

/** 时段内的专注分钟数（会话按开始时间归属时段） */
private fun focusMinutesInRange(bounds: LongArray): Int = FocusData.minutesInRange(bounds[0], bounds[1])

/** 日均专注分钟数：自首次会话以来每天平均 */
private fun dailyAverageMinutes(): Int {
    val sessions = FocusData.sessions
    if (sessions.isEmpty()) return 0
    val first = sessions.minOf { it.start }
    val todayStart = dayBounds(0)[0]
    val days = ((todayStart - first) / 86_400_000L + 1).coerceAtLeast(1L)
    return (sessions.sumOf { it.minutes } / days).toInt()
}

/** 时长格式化：>=1 小时显示「X小时Y分」，否则「Y分钟」 */
private fun formatMinutes(minutes: Long): String =
    if (minutes >= 60) app.getString(R.string.focus_stats_hours_minutes, minutes / 60, minutes % 60)
    else app.getString(R.string.focus_stats_minutes, minutes)
