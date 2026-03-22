package com.sknote.app.ui.share

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Share
import com.sknote.app.databinding.ItemShareBinding
import com.sknote.app.util.TimeUtil

class ShareAdapter(
    private val onClick: (Share) -> Unit
) : ListAdapter<Share, ShareAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Share>() {
            override fun areItemsTheSame(a: Share, b: Share) = a.id == b.id
            override fun areContentsTheSame(a: Share, b: Share) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShareBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemShareBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(share: Share) {
            binding.tvTitle.text = share.title
            binding.tvDescription.text = share.description.orEmpty().ifEmpty { "暂无描述" }
            binding.tvCategory.text = ShareCategories.getLabel(share.category.orEmpty())
            binding.tvAuthor.text = share.authorName.orEmpty().ifEmpty { "匿名" }
            binding.tvLikes.text = "${share.likeCount}"
            binding.tvDownloads.text = "${share.downloadCount}"
            binding.tvFileSize.text = share.fileSize.orEmpty().ifEmpty { "未知大小" }
            binding.tvTime.text = TimeUtil.formatRelative(share.createdAt)
            binding.root.setOnClickListener { onClick(share) }
        }
    }
}
