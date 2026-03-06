package com.sknote.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Article
import com.sknote.app.databinding.ItemArticleBinding
import com.sknote.app.util.TimeUtil

class ArticleAdapter(
    private val onClick: (Article) -> Unit
) : ListAdapter<Article, ArticleAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemArticleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemArticleBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(article: Article) {
            binding.tvTitle.text = article.title
            binding.tvSummary.text = article.summary.ifEmpty { article.content.take(100) }
            binding.tvAuthor.text = article.authorName ?: "Unknown"
            binding.tvCategory.text = article.categoryName ?: ""
            binding.tvViews.text = "${article.viewCount} 阅读"
            binding.tvLikes.text = "${article.likeCount} 赞"
            binding.tvTime.text = TimeUtil.formatRelative(article.createdAt)
            binding.root.setOnClickListener { onClick(article) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Article>() {
        override fun areItemsTheSame(a: Article, b: Article) = a.id == b.id
        override fun areContentsTheSame(a: Article, b: Article) = a == b
    }
}
