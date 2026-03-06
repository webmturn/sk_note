package com.sknote.app.ui.snippet

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Snippet
import com.sknote.app.databinding.ItemSnippetBinding

class SnippetAdapter(
    private val onClick: (Snippet) -> Unit
) : ListAdapter<Snippet, SnippetAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Snippet>() {
            override fun areItemsTheSame(a: Snippet, b: Snippet) = a.id == b.id
            override fun areContentsTheSame(a: Snippet, b: Snippet) = a == b
        }

    
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSnippetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSnippetBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(snippet: Snippet) {
            binding.tvTitle.text = snippet.title
            binding.tvDescription.text = snippet.description
            binding.tvCodePreview.text = snippet.code.take(200)
            binding.tvLanguage.text = snippet.language.uppercase()
            binding.tvCategory.text = SnippetCategories.getLabel(snippet.category)
            binding.tvAuthor.text = snippet.authorName.ifEmpty { "系统" }
            binding.tvLikes.text = "${snippet.likeCount}"
            binding.root.setOnClickListener { onClick(snippet) }
        }
    }
}
