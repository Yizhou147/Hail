package com.aistra.hail.ui.focus

import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import androidx.appcompat.widget.SearchView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.R
import com.aistra.hail.app.FocusData
import com.aistra.hail.app.FocusManager
import com.aistra.hail.databinding.FragmentFocusBinding
import com.aistra.hail.extensions.*
import com.aistra.hail.ui.main.MainActivity
import com.aistra.hail.ui.main.MainFragment
import com.aistra.hail.ui.theme.AppTheme
import com.aistra.hail.utils.FuzzySearch
import com.aistra.hail.utils.HPackages
import com.aistra.hail.utils.HUI
import com.aistra.hail.utils.PinyinSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 专注页：黑名单应用列表 + 导入 + 开始专注（右下角 FAB）。
 */
class FocusFragment : MainFragment(), FocusAdapter.OnItemClickListener, FocusAdapter.OnItemLongClickListener,
    MenuProvider {

    private var _binding: FragmentFocusBinding? = null
    private val binding get() = _binding!!
    private lateinit var focusAdapter: FocusAdapter
    private val selectedList = mutableListOf<String>()
    private var query: String = String()

    private var multiselect: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val menuHost = requireActivity() as MenuHost
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        _binding = FragmentFocusBinding.inflate(inflater, container, false)
        focusAdapter = FocusAdapter(selectedList).apply {
            onItemClickListener = this@FocusFragment
            onItemLongClickListener = this@FocusFragment
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = focusAdapter
            applyDefaultInsetter { paddingRelative(isRtl, bottom = isLandscape) }
        }
        binding.refresh.apply {
            setOnRefreshListener {
                updateCurrentList()
                isRefreshing = false
            }
            applyDefaultInsetter { marginRelative(isRtl, start = !isLandscape, end = true) }
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        updateCurrentList()
        activity.appbar.setLiftOnScrollTargetView(binding.recyclerView)
        setupFab()
    }

    override fun onPause() {
        super.onPause()
        // 清除 FAB 文字，避免影响其他页面
        activity.fab.text = null
    }

    private fun setupFab() {
        val fab = activity.fab
        fab.text = getString(R.string.action_start_focus)
        fab.setIconResource(R.drawable.ic_outline_timer)
        fab.setOnClickListener { showFocusTimeDialog() }
    }

    private fun updateCurrentList() {
        // 黑名单较大时，逐个 getApplicationInfo + loadLabel 是跨进程 binder 调用，
        // 放后台线程执行，避免阻塞主线程造成卡顿/卡死
        val query = this.query
        val packages = FocusData.blacklist.toList()
        lifecycleScope.launch {
            val display = withContext(Dispatchers.Default) {
                packages.mapNotNull { pkg ->
                    val name = HPackages.getApplicationInfoOrNull(pkg)?.loadLabel(app.packageManager)?.toString() ?: pkg
                    if (query.isEmpty() || FuzzySearch.search(pkg, query) || FuzzySearch.search(name, query)
                        || PinyinSearch.searchPinyinAll(name, query)
                    ) pkg to name else null
                }.sortedBy { it.second }.map { it.first }
            }
            binding.empty.isVisible = packages.isEmpty()
            focusAdapter.submitList(display)
        }
    }

    private fun updateBarTitle() {
        activity.supportActionBar?.title =
            if (multiselect) getString(R.string.msg_selected, selectedList.size.toString())
            else getString(R.string.title_focus)
    }

    override fun onItemClick(packageName: String) {
        if (!multiselect) return
        if (packageName in selectedList) selectedList.remove(packageName)
        else selectedList.add(packageName)
        updateCurrentList()
        updateBarTitle()
    }

    override fun onItemLongClick(packageName: String): Boolean {
        if (!multiselect) {
            multiselect = true
            focusAdapter.multiselect = true
            selectedList.clear()
            selectedList.add(packageName)
            updateCurrentList()
            updateBarTitle()
            activity.invalidateOptionsMenu()
        } else {
            if (packageName in selectedList) selectedList.remove(packageName)
            else selectedList.add(packageName)
            updateCurrentList()
            updateBarTitle()
        }
        return true
    }

    private fun exitMultiselect() {
        multiselect = false
        focusAdapter.multiselect = false
        selectedList.clear()
        activity.invalidateOptionsMenu()
        updateBarTitle()
    }

    /**
     * 当前叠加的弹窗 ComposeView。弹窗采用"addView 覆盖层 + Compose 单窗口"方案：
     * 相比"Android Dialog + Compose AlertDialog"的双层窗口，可避免双窗口渲染冻结、
     * 图形 buffer 堆积导致的内存暴涨与系统卡死；
     * 配合 DisposeOnDetachedFromWindow，关闭后立即销毁 Composition，杜绝泄漏。
     */
    private var dialogOverlay: ComposeView? = null

    private fun showFocusTimeDialog() {
        val overlay = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                AppTheme {
                    FocusTimeDialog(
                        onDismiss = { dismissOverlay() },
                        onStart = { minutes ->
                            dismissOverlay()
                            startFocus(minutes)
                        }
                    )
                }
            }
        }
        dialogOverlay = overlay
        (requireView() as ViewGroup).addView(
            overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun showImportDialog() {
        val overlay = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                AppTheme {
                    ImportAppsDialog(
                        onDismiss = {
                            dismissOverlay()
                            updateCurrentList()
                        }
                    )
                }
            }
        }
        dialogOverlay = overlay
        (requireView() as ViewGroup).addView(
            overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun dismissOverlay() {
        dialogOverlay?.let { (requireView() as ViewGroup).removeView(it) }
        dialogOverlay = null
    }

    private fun startFocus(minutes: Int) {
        lifecycleScope.launch {
            val error = FocusManager.startFocus(minutes)
            if (error == null) {
                HUI.showToast(R.string.focus_started)
                (activity as MainActivity).updateFocusLock()
            } else {
                HUI.showToast(error, true)
            }
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_focus, menu)
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                query = newText
                updateCurrentList()
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean = true
        })
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        menu.findItem(R.id.action_remove_focus_apps).isVisible = multiselect
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_multiselect -> {
                if (multiselect) exitMultiselect()
                else {
                    multiselect = true
                    focusAdapter.multiselect = true
                    activity.invalidateOptionsMenu()
                    updateBarTitle()
                }
                updateCurrentList()
            }

            R.id.action_remove_focus_apps -> {
                selectedList.forEach { FocusData.removeFromBlacklist(it) }
                exitMultiselect()
                updateCurrentList()
            }

            R.id.action_import_focus_apps -> showImportDialog()
        }
        return false
    }

    override fun onDestroyView() {
        // 移除残留的弹窗覆盖层，触发 DisposeOnDetachedFromWindow 销毁 Composition
        (view as? ViewGroup)?.let { root -> dialogOverlay?.let { root.removeView(it) } }
        dialogOverlay = null
        focusAdapter.onDestroy()
        super.onDestroyView()
        _binding = null
    }
}
