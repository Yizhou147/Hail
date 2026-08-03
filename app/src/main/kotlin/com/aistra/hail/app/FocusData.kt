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

    private val sp = PreferenceManager.getDefaultSharedPreferences(app)
    private val dir = "${app.filesDir.path}/v1"
    private val blacklistPath = "$dir/focus_blacklist.json"
    private val presetsPath = "$dir/focus_presets.json"
    private val snapshotPath = "$dir/focus_snapshot.json"

    /** 专注是否进行中 */
    var isActive
        get() = sp.getBoolean(KEY_FOCUS_ACTIVE, false)
        private set(value) = sp.edit { putBoolean(KEY_FOCUS_ACTIVE, value) }

    /** 专注结束时间戳（毫秒） */
    var endTime
        get() = sp.getLong(KEY_FOCUS_END_TIME, 0L)
        private set(value) = sp.edit { putLong(KEY_FOCUS_END_TIME, value) }

    /** 剩余毫秒 */
    val remainingMillis get() = (endTime - System.currentTimeMillis()).coerceAtLeast(0L)

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

    /** 预设（名称 -> 分钟数） */
    val presets: MutableList<Pair<String, Int>> by lazy {
        mutableListOf<Pair<String, Int>>().apply {
            runCatching {
                val json = JSONArray(HFiles.read(presetsPath))
                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)
                    add(obj.getString("name") to obj.getInt("minutes"))
                }
            }
        }
    }

    fun savePresets() {
        if (!HFiles.exists(dir)) HFiles.createDirectories(dir)
        HFiles.write(presetsPath, JSONArray().apply {
            presets.forEach { (name, minutes) ->
                put(JSONObject().put("name", name).put("minutes", minutes))
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
        isActive = true
        endTime = System.currentTimeMillis() + durationMinutes * 60_000L
    }

    /** 结束当前专注会话（保留黑名单与预设） */
    fun endSession() {
        isActive = false
        endTime = 0L
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
