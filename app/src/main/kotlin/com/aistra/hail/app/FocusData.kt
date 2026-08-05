package com.aistra.hail.app

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.utils.HFiles
import org.json.JSONArray
import org.json.JSONObject

/**
 * 专注模式的数据层：黑名单、预设、会话状态（是否进行中/结束时间）、快照。
 * 黑名单与预设存储在 files 目录 JSON（与 HailData 的 apps.json/tags.json 风格一致），
 * 会话状态存 SharedPreferences（需要跨进程、频繁读取）。
 */
object FocusData {
    const val MAX_PRESETS = 20
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 240

    const val KEY_FOCUS_ACTIVE = "focus_active"
    const val KEY_FOCUS_END_TIME = "focus_end_time"
    const val KEY_FOCUS_START_TIME = "focus_start_time"
    const val KEY_FOCUS_TOTAL_MINUTES = "focus_total_minutes"

    private val sp = PreferenceManager.getDefaultSharedPreferences(app)
    private val dir = "${app.filesDir.path}/v1"
    private val blacklistPath = "$dir/focus_blacklist.json"
    private val presetsPath = "$dir/focus_presets.json"
    private val snapshotPath = "$dir/focus_snapshot.json"
    private val sessionsPath = "$dir/focus_sessions.json"

    /** 会话记录最多保留条数，防止文件无限增长 */
    private const val MAX_SESSIONS = 1000

    /** 专注是否进行中 */
    var isActive
        get() = sp.getBoolean(KEY_FOCUS_ACTIVE, false)
        private set(value) = sp.edit { putBoolean(KEY_FOCUS_ACTIVE, value) }

    /** 专注结束时间戳（毫秒） */
    var endTime
        get() = sp.getLong(KEY_FOCUS_END_TIME, 0L)
        private set(value) = sp.edit { putLong(KEY_FOCUS_END_TIME, value) }

    /** 本次专注开始时间戳（毫秒），用于统计实际坚持时长 */
    var startTime
        get() = sp.getLong(KEY_FOCUS_START_TIME, 0L)
        private set(value) = sp.edit { putLong(KEY_FOCUS_START_TIME, value) }

    /** 累计专注总时长（分钟） */
    var totalMinutes
        get() = sp.getLong(KEY_FOCUS_TOTAL_MINUTES, 0L)
        private set(value) = sp.edit { putLong(KEY_FOCUS_TOTAL_MINUTES, value) }

    /** 剩余毫秒 */
    val remainingMillis get() = (endTime - System.currentTimeMillis()).coerceAtLeast(0L)

    /** 一次专注会话记录（专注一旦开始不可中途退出，会话必定完整完成） */
    data class FocusSession(val start: Long, val end: Long) {
        /** 时长（分钟），向下取整 */
        val minutes: Int get() = ((end - start) / 60_000L).toInt()
    }

    /** 专注历史会话（按开始时间升序存储） */
    val sessions: MutableList<FocusSession> by lazy {
        mutableListOf<FocusSession>().apply {
            runCatching {
                val json = JSONArray(HFiles.read(sessionsPath))
                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)
                    add(FocusSession(obj.getLong("start"), obj.getLong("end")))
                }
            }
        }
    }

    fun saveSessions() {
        if (!HFiles.exists(dir)) HFiles.createDirectories(dir)
        HFiles.write(sessionsPath, JSONArray().apply {
            sessions.forEach { put(JSONObject().put("start", it.start).put("end", it.end)) }
        }.toString())
    }

    /** 记录一次已完成的专注会话，超出上限时丢弃最旧的记录 */
    fun recordSession(start: Long, end: Long) {
        if (start <= 0 || end <= start) return
        sessions.add(FocusSession(start, end))
        while (sessions.size > MAX_SESSIONS) sessions.removeAt(0)
        saveSessions()
    }

    /** [from, to) 时段内的专注分钟数（会话按开始时间归属时段） */
    fun minutesInRange(from: Long, to: Long): Int =
        sessions.filter { it.start >= from && it.start < to }.sumOf { it.minutes }

    /** 黑名单（包名列表） */
    val blacklist: MutableList<String> by lazy {
        mutableListOf<String>().apply {
            runCatching {
                val json = JSONArray(HFiles.read(blacklistPath))
                for (i in 0 until json.length()) add(json.getString(i))
            }
        }
    }

    fun isInBlacklist(packageName: String): Boolean = blacklist.any { it == packageName }

    fun addToBlacklist(packageName: String, save: Boolean = true) {
        if (!isInBlacklist(packageName)) {
            blacklist.add(packageName)
            if (save) saveBlacklist()
        }
    }

    fun removeFromBlacklist(packageName: String, save: Boolean = true) {
        blacklist.removeAll { it == packageName }
        if (save) saveBlacklist()
    }

    fun saveBlacklist() {
        if (!HFiles.exists(dir)) HFiles.createDirectories(dir)
        HFiles.write(blacklistPath, JSONArray().apply {
            blacklist.forEach { put(it) }
        }.toString())
    }

    /** 预设（id 用于列表稳定 key，支持拖拽排序） */
    data class Preset(val id: Long, val name: String, val minutes: Int)

    /** 预设列表 */
    val presets: MutableList<Preset> by lazy {
        mutableListOf<Preset>().apply {
            runCatching {
                val json = JSONArray(HFiles.read(presetsPath))
                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)
                    // 兼容旧数据：无 id 字段时按索引生成唯一 id
                    val id = if (obj.has("id")) obj.getLong("id") else System.currentTimeMillis() + i
                    add(Preset(id, obj.getString("name"), obj.getInt("minutes")))
                }
            }
        }
    }

    /** 下一个可用的预设 id（取现有最大值 + 1，保证单调递增不冲突） */
    fun nextPresetId(): Long = (presets.maxOfOrNull { it.id } ?: 0L) + 1

    fun savePresets() {
        if (!HFiles.exists(dir)) HFiles.createDirectories(dir)
        HFiles.write(presetsPath, JSONArray().apply {
            presets.forEach { (id, name, minutes) ->
                put(JSONObject().put("id", id).put("name", name).put("minutes", minutes))
            }
        }.toString())
    }

    /** 快照：开始专注前各黑名单应用的冻结状态（包名 -> 是否冻结） */
    val snapshot: MutableMap<String, Boolean> by lazy {
        mutableMapOf<String, Boolean>().apply {
            runCatching {
                val json = JSONObject(HFiles.read(snapshotPath))
                json.keys().forEach { put(it, json.getBoolean(it)) }
            }
        }
    }

    fun saveSnapshot(snapshot: Map<String, Boolean>) {
        this.snapshot.clear()
        this.snapshot.putAll(snapshot)
        if (!HFiles.exists(dir)) HFiles.createDirectories(dir)
        HFiles.write(snapshotPath, JSONObject().apply {
            snapshot.forEach { (pkg, frozen) -> put(pkg, frozen) }
        }.toString())
    }

    /** 开始一次专注会话 */
    fun beginSession(durationMinutes: Int) {
        val now = System.currentTimeMillis()
        isActive = true
        startTime = now
        endTime = now + durationMinutes * 60_000L
    }

    /** 结束当前专注会话（保留黑名单与预设）。先按实际坚持时长累计统计并记录会话，再重置会话状态。 */
    fun endSession() {
        if (isActive) {
            val start = startTime
            val end = minOf(endTime, System.currentTimeMillis())
            // 向下取整到分钟；自然结束=完整预设时长，提前结束=已坚持时长
            if (start > 0 && end > start) {
                totalMinutes += (end - start) / 60_000L
                recordSession(start, end)
            }
        }
        isActive = false
        endTime = 0L
        startTime = 0L
    }

    /** 剩余时间格式化：HH:MM:SS，不足 1 小时用 MM:SS */
    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }
}
