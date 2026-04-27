package com.sknote.app.ui.discussion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sknote.app.R
import com.sknote.app.data.model.Discussion
import com.sknote.app.databinding.ItemDiscussionBinding
import com.sknote.app.util.DiscussionCategoryDefaults
import com.sknote.app.util.TimeUtil
import com.sknote.app.util.DiscussionIconResolver

class DiscussionAdapter(
    private val onClick: (Discussion) -> Unit,
    private val onAuthorClick: (Discussion) -> Unit = {}
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
            binding.tvTitle.text = discussion.title
            if (discussion.isPinned == 1) {
                binding.ivPinned.visibility = View.VISIBLE
                binding.ivPinned.setImageResource(DiscussionIconResolver.pinned())
            } else {
                binding.ivPinned.visibility = View.GONE
            }
            // Show clean preview without block/palette data
            val hasShare = BlockShareHelper.containsAnyShare(discussion.content.orEmpty())
            val preview = if (hasShare) {
                val clean = BlockShareHelper.getCleanContent(discussion.content.orEmpty()).take(120)
                val isPalette = BlockShareHelper.containsPalette(discussion.content.orEmpty())
                clean.ifEmpty {
                    if (isPalette) "调色板分享，点击查看详情"
                    else "积木块分享，点击查看详情"
                }
            } else {
                discussion.content.orEmpty().take(120)
            }
            if (hasShare) {
                binding.ivPreviewType.visibility = View.VISIBLE
                binding.ivPreviewType.setImageResource(
                    DiscussionIconResolver.sharePreview(
                        isPalette = BlockShareHelper.containsPalette(discussion.content.orEmpty())
                    )
                )
            } else {
                binding.ivPreviewType.visibility = View.GONE
            }
            binding.tvPreview.text = preview
            binding.tvAuthor.text = discussion.authorName ?: "Anonymous"
            if (!discussion.authorAvatar.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(discussion.authorAvatar)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person)
                    .into(binding.ivAvatar)
            } else {
                Glide.with(binding.root.context).clear(binding.ivAvatar)
                binding.ivAvatar.setImageResource(R.drawable.ic_person)
            }
            binding.tvCategory.text = discussion.categoryName.orEmpty().ifEmpty {
                DiscussionCategoryDefaults.label(discussion.category)
            }
            binding.tvReplies.text = "${discussion.replyCount} 回复"
            binding.tvViews.text = "${discussion.viewCount} 阅读"
            binding.tvTime.text = TimeUtil.formatRelative(discussion.createdAt)
            binding.root.setOnClickListener { onClick(discussion) }
            binding.tvAuthor.setOnClickListener { onAuthorClick(discussion) }
            binding.ivAvatar.setOnClickListener { onAuthorClick(discussion) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Discussion>() {
        override fun areItemsTheSame(a: Discussion, b: Discussion) = a.id == b.id
        override fun areContentsTheSame(a: Discussion, b: Discussion) = a == b
    }
}
