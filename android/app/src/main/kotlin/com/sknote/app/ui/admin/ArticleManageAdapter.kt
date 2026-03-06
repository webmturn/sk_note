package com.sknote.app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Article
import com.sknote.app.databinding.ItemArticleManageBinding
import com.sknote.app.util.TimeUtil

class ArticleManageAdapter(
    private val onEdit: (Article) -> Unit,
    private val onDelete: (Article) -> Unit
) : ListAdapter<Article, ArticleManageAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemArticleManageBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<Article>() {
        override fun areItemsTheSame(oldItem: Article, newItem: Article) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Article, newItem: Article) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemArticleManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val article = getItem(position)
        holder.binding.tvTitle.text = article.title
        holder.binding.tvMeta.text = "${article.categoryName ?: "未分类"} · ${article.viewCount} 阅读 · ${TimeUtil.formatRelative(article.createdAt)}"
        holder.binding.btnEdit.setOnClickListener { onEdit(article) }
        holder.binding.btnDelete.setOnClickListener { onDelete(article) }
    }
}
