package com.sknote.app.ui.manage.release

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.BuildConfig
import com.sknote.app.data.model.AppRelease
import com.sknote.app.databinding.ItemReleaseManageBinding
import com.sknote.app.util.AppUpdateManager
import com.sknote.app.util.TimeUtil

class ReleaseManageAdapter(
    private val onDelete: (AppRelease) -> Unit,
    private val onToggleActive: (AppRelease) -> Unit = {}
) : ListAdapter<AppRelease, ReleaseManageAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemReleaseManageBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<AppRelease>() {
        override fun areItemsTheSame(oldItem: AppRelease, newItem: AppRelease) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AppRelease, newItem: AppRelease) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReleaseManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val isCurrent = item.versionName == BuildConfig.VERSION_NAME
        val isActive = item.isActive == 1

        holder.binding.tvVersion.text = "v${item.versionName} (code ${item.versionCode})"
        holder.binding.tvBadgeCurrent.visibility = if (isCurrent) View.VISIBLE else View.GONE
        holder.binding.tvBadgeInactive.visibility = if (isActive) View.GONE else View.VISIBLE

        val sizeLabel = if (item.fileSize > 0) AppUpdateManager.formatFileSize(item.fileSize) else "未知大小"
        val time = TimeUtil.formatRelative(item.createdAt)
        holder.binding.tvMeta.text = "$sizeLabel · $time"

        val changelog = item.changelog.orEmpty().trim()
        holder.binding.tvChangelog.visibility = if (changelog.isEmpty()) View.GONE else View.VISIBLE
        holder.binding.tvChangelog.text = changelog

        holder.binding.btnToggleActive.text = if (isActive) "下架" else "上架"
        holder.binding.btnToggleActive.setOnClickListener { onToggleActive(item) }
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
    }
}
