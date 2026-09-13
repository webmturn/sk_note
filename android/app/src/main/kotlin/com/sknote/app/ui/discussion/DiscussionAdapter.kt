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

    /**
     * 未读表：discussionId -> 上次读过之后新增的回复数。
     * 不属于 Discussion 本身（那是接口数据），所以放在适配器上；
     * 变化时用 notifyItemChanged 精确刷新，避免整表重绑闪一下。
     */
    private var unread: Map<Long, Int> = emptyMap()

    fun setUnread(map: Map<Long, Int>) {
        if (map == unread) return
        val changed = mutableListOf<Int>()
        val old = unread
        unread = map
        currentList.forEachIndexed { index, discussion ->
            val before = old[discussion.id] ?: 0
            val after = map[discussion.id] ?: 0
            if (before != after) changed.add(index)
        }
        changed.forEach { notifyItemChanged(it) }
    }

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
            // 摘要与标题完全相同、或正文本来就空时，整行隐藏：
            // 否则卡片会把同一句话显示两遍（真机上 10 条里有 1 条如此），
            // 或者留一行空白。只做「全等」判断 —— 只是前缀相同的情况往往还带着
            // 链接等额外信息（如标题是摘要的第一句），那种要保留。
            val showPreview = preview.isNotBlank() && preview.trim() != discussion.title.trim()
            binding.tvPreview.text = if (showPreview) preview else ""
            binding.tvPreview.visibility = if (showPreview) View.VISIBLE else View.GONE
            if (hasShare && showPreview) {
                binding.ivPreviewType.visibility = View.VISIBLE
                binding.ivPreviewType.setImageResource(
                    DiscussionIconResolver.sharePreview(
                        isPalette = BlockShareHelper.containsPalette(discussion.content.orEmpty())
                    )
                )
            } else {
                binding.ivPreviewType.visibility = View.GONE
            }
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

            // 未读：标题前一个小圆点（Discourse 的位置）+ 底部一行「N 条新回复」。
            // 读完之后有新回复才出现；从没打开过的不算（后端只返回有阅读记录的）。
            val newReplies = unread[discussion.id] ?: 0
            binding.ivUnreadDot.visibility = if (newReplies > 0) View.VISIBLE else View.GONE
            binding.tvUnread.visibility = if (newReplies > 0) View.VISIBLE else View.GONE
            binding.tvUnread.text = "$newReplies 条新回复"

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
