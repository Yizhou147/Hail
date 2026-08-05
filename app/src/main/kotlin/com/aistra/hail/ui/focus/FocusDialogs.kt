package com.aistra.hail.ui.focus

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aistra.hail.R
import com.aistra.hail.app.FocusData
import com.aistra.hail.utils.AppIconCache
import com.aistra.hail.utils.FuzzySearch
import com.aistra.hail.utils.HPackages
import com.aistra.hail.utils.HUI
import com.aistra.hail.utils.PinyinSearch
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** 开始专注的时间设置对话框：数字输入 + 预设快捷选择 + 保存/管理预设 */
@Composable
fun FocusTimeDialog(
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showSavePreset by remember { mutableStateOf(false) }
    var showManagePresets by remember { mutableStateOf(false) }

    if (showSavePreset) {
        PresetSaveDialog(minutes = input.toIntOrNull() ?: 0, onDismiss = { showSavePreset = false })
    }
    if (showManagePresets) {
        PresetManageDialog(onDismiss = { showManagePresets = false })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.action_start_focus)) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit).take(3) },
                    label = { Text(text = stringResource(R.string.focus_time_label)) },
                    placeholder = { Text(text = stringResource(R.string.focus_time_hint)) },
                    suffix = { Text(text = "min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (FocusData.presets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.focus_empty_presets),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FocusPresetChips(input = input, onSelect = { input = it.toString() })
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    TextButton(onClick = { showSavePreset = true }) {
                        Text(text = stringResource(R.string.action_save_preset))
                    }
                    TextButton(onClick = { showManagePresets = true }) {
                        Text(text = stringResource(R.string.action_manage_presets))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = input.toIntOrNull()
                if (minutes != null && minutes in FocusData.MIN_MINUTES..FocusData.MAX_MINUTES) onStart(minutes)
                else HUI.showToast(R.string.focus_time_invalid)
            }) { Text(text = stringResource(R.string.action_start_focus)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(android.R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FocusPresetChips(input: String, onSelect: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FocusData.presets.forEach { preset ->
            FilterChip(
                selected = preset.minutes.toString() == input,
                onClick = { onSelect(preset.minutes) },
                label = { Text(text = "${preset.name} ${preset.minutes}") }
            )
        }
    }
}

/** 保存为预设 */
@Composable
private fun PresetSaveDialog(minutes: Int, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.action_save_preset)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.focus_preset_name)) },
                    placeholder = { Text(text = stringResource(R.string.focus_preset_name_hint)) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$minutes min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    minutes !in FocusData.MIN_MINUTES..FocusData.MAX_MINUTES ->
                        HUI.showToast(R.string.focus_time_invalid)
                    name.isBlank() -> HUI.showToast(R.string.focus_preset_name_required)
                    FocusData.presets.size >= FocusData.MAX_PRESETS -> HUI.showToast(R.string.focus_preset_limit)
                    else -> {
                        FocusData.presets.add(FocusData.Preset(FocusData.nextPresetId(), name, minutes))
                        FocusData.savePresets()
                        HUI.showToast(R.string.focus_preset_saved)
                        onDismiss()
                    }
                }
            }) { Text(text = stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(android.R.string.cancel)) }
        }
    )
}

/** 管理预设：长按拖动排序 + 删除 */
@Composable
private fun PresetManageDialog(onDismiss: () -> Unit) {
    var presets by remember { mutableStateOf(FocusData.presets.toList()) }
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index != to.index) {
            // 更新本地列表，并同步数据源持久化
            presets = presets.toMutableList().apply { add(to.index, removeAt(from.index)) }
            FocusData.presets.clear()
            FocusData.presets.addAll(presets)
            FocusData.savePresets()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.action_manage_presets)) },
        text = {
            if (presets.isEmpty()) {
                Text(
                    text = stringResource(R.string.focus_empty_presets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    Text(
                        text = stringResource(R.string.focus_preset_drag_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    LazyColumn(state = listState, modifier = Modifier.heightIn(max = 320.dp)) {
                        items(presets, key = { it.id }) { preset ->
                            ReorderableItem(reorderableState, key = preset.id) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        .longPressDraggableHandle(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${preset.name} ${preset.minutes}",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    IconButton(onClick = {
                                        val index = presets.indexOfFirst { it.id == preset.id }
                                        if (index != -1) {
                                            FocusData.presets.removeAt(index)
                                            FocusData.savePresets()
                                            presets = FocusData.presets.toList()
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.action_remove_focus_apps)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(android.R.string.ok)) }
        }
    )
}

/**
 * 导入黑名单对话框：支持从雹现有应用列表手动选择，或从剪贴板导入
 * （兼容原版 JSON 数组格式 ["pkg1","pkg2",...]，也支持单个包名）。
 * 手动模式支持按名称/包名搜索、用户/系统应用分类筛选、全选/清空；
 * 剪贴板模式解析出的应用默认全选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportAppsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    // 0=手动选择应用，1=从剪贴板导入
    var mode by remember { mutableStateOf(0) }
    val selected = remember { mutableStateListOf<String>() }
    // 手动模式：搜索词与分类筛选（0=全部，1=用户应用，2=系统应用），默认只显示用户应用
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(1) }
    // 后台线程加载应用列表并预取显示名（loadLabel 是跨进程调用），避免主线程卡顿
    val appItems by produceState<List<Pair<ApplicationInfo, String>>?>(initialValue = null) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                HPackages.getInstalledApplications()
                    .filterNot { FocusData.isInBlacklist(it.packageName) }
                    .map { it to it.loadLabel(pm).toString() }
                    .sortedWith(compareBy(Collator.getInstance()) { it.second })
            }.getOrNull()
        }
    }
    // 手动模式可见列表：按分类与搜索词过滤（拼音搜索较重，放后台线程）
    val visibleItems by produceState<List<Pair<ApplicationInfo, String>>?>(
        initialValue = appItems, appItems, category, query
    ) {
        val items = appItems ?: return@produceState
        val q = query.trim()
        value = withContext(Dispatchers.Default) {
            items.filter { (info, name) ->
                val isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
                when (category) {
                    1 -> !isSystem
                    2 -> isSystem
                    else -> true
                } && (q.isEmpty() || FuzzySearch.search(name, q) || FuzzySearch.search(info.packageName, q)
                    || PinyinSearch.searchPinyinAll(name, q))
            }
        }
    }
    // 剪贴板解析结果（null=尚未解析/解析中）
    var clipboardPackages by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(mode) {
        // 进入剪贴板模式时解析（仅首次），并将解析出的应用默认全选
        if (mode == 1) {
            val result = clipboardPackages
                ?: withContext(Dispatchers.Default) { parseClipboardPackages() }
                    .also { clipboardPackages = it }
            selected.addAll(result)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.focus_import_apps)) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { selected.clear(); mode = 0 },
                        label = { Text(text = stringResource(R.string.focus_import_select)) }
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { selected.clear(); mode = 1 },
                        label = { Text(text = stringResource(R.string.action_import_clipboard)) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (mode == 0) {
                    // 搜索框：按名称/包名/拼音搜索
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(text = stringResource(R.string.focus_import_search_hint)) },
                        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 分类筛选
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = category == 0,
                            onClick = { category = 0 },
                            label = { Text(text = stringResource(R.string.focus_import_all)) }
                        )
                        FilterChip(
                            selected = category == 1,
                            onClick = { category = 1 },
                            label = { Text(text = stringResource(R.string.focus_import_user_apps)) }
                        )
                        FilterChip(
                            selected = category == 2,
                            onClick = { category = 2 },
                            label = { Text(text = stringResource(R.string.focus_import_system_apps)) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val list = visibleItems
                    when {
                        list == null -> Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }

                        list.isEmpty() -> Text(
                            text = stringResource(R.string.nothing_here),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        else -> Column {
                            // 全选/清空作用于当前可见列表（含筛选结果）
                            Row {
                                TextButton(onClick = {
                                    selected.clear()
                                    selected.addAll(list.map { it.first.packageName })
                                }) { Text(text = stringResource(R.string.focus_select_all)) }
                                TextButton(onClick = { selected.clear() }) {
                                    Text(text = stringResource(R.string.focus_clear_selection))
                                }
                            }
                            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                items(list, key = { it.first.packageName }) { (info, name) ->
                                    val pkg = info.packageName
                                    val icon = rememberAppIcon(context, info)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            if (pkg in selected) selected.remove(pkg) else selected.add(pkg)
                                        }.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = pkg in selected,
                                            onCheckedChange = { checked ->
                                                if (checked) selected.add(pkg) else selected.remove(pkg)
                                            }
                                        )
                                        if (icon != null) {
                                            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(36.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = name, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val list = clipboardPackages
                    when {
                        list == null -> Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }

                        list.isEmpty() -> Text(
                            text = stringResource(R.string.focus_import_clipboard_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        else -> LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(list, key = { it }) { pkg ->
                                val name = HPackages.getApplicationInfoOrNull(pkg)
                                    ?.loadLabel(pm)?.toString() ?: pkg
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        if (pkg in selected) selected.remove(pkg) else selected.add(pkg)
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = pkg in selected,
                                        onCheckedChange = { checked ->
                                            if (checked) selected.add(pkg) else selected.remove(pkg)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = name, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                selected.forEach { FocusData.addToBlacklist(it) }
                onDismiss()
            }) { Text(text = stringResource(R.string.focus_add_count, selected.size)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(android.R.string.cancel)) }
        }
    )
}

/**
 * 解析剪贴板中的包名列表：兼容原版 JSON 数组格式 ["pkg1","pkg2",...]，
 * 剪贴板内容前后可有额外文本（截取 [ 到 ]）；无数组时按单个包名处理。
 * 仅返回已安装且不在黑名单中的包名。
 */
private fun parseClipboardPackages(): List<String> {
    val str = HUI.pasteText() ?: return emptyList()
    return runCatching {
        val json = if (str.contains('[')) JSONArray(
            str.substring(str.indexOf('[')..str.indexOf(']', str.indexOf('[')))
        )
        else JSONArray().put(str.trim())
        buildList {
            for (i in 0 until json.length()) {
                val pkg = json.getString(i)
                if (HPackages.getApplicationInfoOrNull(pkg) != null && !FocusData.isInBlacklist(pkg)) add(pkg)
            }
        }
    }.getOrDefault(emptyList())
}

/** 异步加载应用图标（后台解码，走 AppIconCache 缓存） */
@Composable
private fun rememberAppIcon(context: Context, info: ApplicationInfo): ImageBitmap? {
    val sizePx = with(LocalDensity.current) { 36.dp.toPx() }.toInt()
    return produceState<ImageBitmap?>(initialValue = null, info) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                AppIconCache.getOrLoadBitmap(context, info, HPackages.myUserId, sizePx).asImageBitmap()
            }.getOrNull()
        }
    }.value
}
