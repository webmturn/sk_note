package com.sknote.app.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Discussion
import com.sknote.app.databinding.ItemDiscussionManageBinding
import com.sknote.app.util.TimeUtil

class DiscussionManageAdapter(
    private val onView: (Discussion) -> Unit,
    private val onDelete: (Discussion) -> Unit
) : ListAdapter<Discussion, DiscussionManageAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemDiscussionManageBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<Discussion>() {
        override fun areItemsTheSame(oldItem: Discussion, newItem: Discussion) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Discussion, newItem: Discussion) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDiscussionManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.tvTitle.text = item.title
        holder.binding.tvMeta.text = "${item.authorName ?: "匿名"} · ${item.replyCount} 回复 · ${item.viewCount} 浏览 · ${TimeUtil.formatRelative(item.createdAt)}"
        holder.binding.tvBadgePinned.visibility = if (item.isPinned == 1) View.VISIBLE else View.GONE
        holder.binding.tvBadgeClosed.visibility = if (item.isClosed == 1) View.VISIBLE else View.GONE
        holder.binding.btnView.setOnClickListener { onView(item) }
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
    }
}
