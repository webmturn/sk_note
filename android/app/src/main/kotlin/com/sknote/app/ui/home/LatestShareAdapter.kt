package com.sknote.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Share
import com.sknote.app.databinding.ItemShareCompactBinding
import com.sknote.app.ui.share.ShareCategories

class LatestShareAdapter(
    private val onClick: (Share) -> Unit
) : ListAdapter<Share, LatestShareAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Share>() {
            override fun areItemsTheSame(a: Share, b: Share) = a.id == b.id
            override fun areContentsTheSame(a: Share, b: Share) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShareCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemShareCompactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(share: Share) {
            binding.tvTitle.text = share.title
            binding.tvCategory.text = ShareCategories.getLabel(share.category.orEmpty())
            binding.tvFileSize.text = share.fileSize.orEmpty().ifEmpty { "未知大小" }
            binding.tvDownloads.text = "${share.downloadCount}"
            binding.tvAuthor.text = share.authorName.orEmpty().ifEmpty { "匿名" }
            binding.root.setOnClickListener { onClick(share) }
        }
    }
}
