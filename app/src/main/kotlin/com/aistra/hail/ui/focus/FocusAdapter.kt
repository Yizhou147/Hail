package com.aistra.hail.ui.focus

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aistra.hail.R
import com.aistra.hail.app.FocusData
import com.aistra.hail.app.HailData
import com.aistra.hail.utils.AppIconCache
import com.aistra.hail.utils.HPackages
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Job

class FocusAdapter(
    private val selectedList: MutableList<String>
) : ListAdapter<String, FocusAdapter.ViewHolder>(
    // 必须是实例级（而非 companion object 静态），才能感知 selectedList 的选中状态变化，
    // 否则多选时勾选/取消不会触发 item 刷新，用户看不到选中反馈
    object : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean =
            oldItem in FocusData.blacklist == newItem in FocusData.blacklist
                && oldItem in selectedList == newItem in selectedList
    }
) {
    private var loadIconJob: Job? = null
    lateinit var onItemClickListener: OnItemClickListener
    lateinit var onItemLongClickListener: OnItemLongClickListener
    var multiselect: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_focus, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pkg = currentList[position]
        holder.bind(pkg)
    }

    fun onDestroy() {
        if (loadIconJob?.isActive == true) loadIconJob?.cancel()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val appIcon: ImageView = view.findViewById(R.id.app_icon)
        private val appName: TextView = view.findViewById(R.id.app_name)
        private val appDesc: TextView = view.findViewById(R.id.app_desc)
        private val checkBox: com.google.android.material.checkbox.MaterialCheckBox = view.findViewById(R.id.check_box)
        private lateinit var pkg: String

        init {
            view.setOnClickListener { onItemClickListener.onItemClick(pkg) }
            view.setOnLongClickListener { onItemLongClickListener.onItemLongClick(pkg) }
        }

        fun bind(packageName: String) {
            pkg = packageName
            val info = HPackages.getApplicationInfoOrNull(pkg)
            if (info != null) {
                loadIconJob = AppIconCache.loadIconBitmapAsync(
                    itemView.context, info, HPackages.myUserId, appIcon, false
                )
            } else {
                appIcon.setImageDrawable(itemView.context.packageManager.defaultActivityIcon)
                appIcon.colorFilter = null
            }
            val name = info?.loadLabel(itemView.context.packageManager) ?: pkg
            appName.text = name
            appName.setTextColor(
                if (pkg in selectedList) MaterialColors.getColor(appName, androidx.appcompat.R.attr.colorPrimary)
                else {
                    appName.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    MaterialColors.getColor(appName, com.google.android.material.R.attr.colorOnSurface)
                }
            )
            appDesc.text = pkg
            checkBox.isVisible = multiselect
            checkBox.isChecked = pkg in selectedList
        }
    }

    interface OnItemClickListener {
        fun onItemClick(packageName: String)
    }

    interface OnItemLongClickListener {
        fun onItemLongClick(packageName: String): Boolean
    }
}
