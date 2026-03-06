package com.sknote.app.ui.discussion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Discussion
import com.sknote.app.databinding.ItemDiscussionBinding
import com.sknote.app.util.TimeUtil

class DiscussionAdapter(
    private val onClick: (Discussion) -> Unit
) : ListAdapter<Discussion, DiscussionAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDiscussionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDiscussionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(discussion: Discussion) {
            binding.tvTitle.text = if (discussion.isPinned == 1) "📌 ${discussion.title}" else discussion.title
            // Show clean preview without block/palette data
            val preview = if (BlockShareHelper.containsAnyShare(discussion.content)) {
                val clean = BlockShareHelper.getCleanContent(discussion.content).take(120)
                clean.ifEmpty {
                    if (BlockShareHelper.containsPalette(discussion.content)) "🎨 调色板分享，点击查看详情"
                    else "🧩 积木块分享，点击查看详情"
                }
            } else {
                discussion.content.take(120)
            }
            binding.tvPreview.text = preview
            binding.tvAuthor.text = discussion.authorName ?: "Anonymous"
            binding.tvCategory.text = getCategoryLabel(discussion.category)
            binding.tvReplies.text = "${discussion.replyCount} 回复"
            binding.tvViews.text = "${discussion.viewCount} 阅读"
            binding.tvTime.text = TimeUtil.formatRelative(discussion.createdAt)
            binding.root.setOnClickListener { onClick(discussion) }
        }
    }

    private fun getCategoryLabel(category: String): String = when (category) {
        "general" -> "综合"
        "question" -> "提问"
        "feedback" -> "反馈"
        "bug" -> "Bug"
        "feature" -> "功能建议"
        else -> category
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Discussion>() {
        override fun areItemsTheSame(a: Discussion, b: Discussion) = a.id == b.id
        override fun areContentsTheSame(a: Discussion, b: Discussion) = a == b
    }
}
