package com.sknote.app.ui.discussion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Comment
import com.sknote.app.databinding.ItemCommentBinding
import com.sknote.app.util.TimeUtil

class CommentAdapter(
    private val onLongClick: (Comment) -> Unit = {},
    private val onLikeClick: (Comment) -> Unit = {}
) : ListAdapter<Comment, CommentAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)

    companion object DiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(a: Comment, b: Comment) = a.id == b.id
        override fun areContentsTheSame(a: Comment, b: Comment) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = getItem(position)
        holder.binding.tvAuthor.text = comment.authorName ?: "匿名"
        holder.binding.tvContent.text = comment.content
        holder.binding.tvTime.text = TimeUtil.formatRelative(comment.createdAt)
        holder.binding.tvLikeCount.text = if (comment.likeCount > 0) "${comment.likeCount}" else ""
        holder.binding.btnLike.setOnClickListener { onLikeClick(comment) }
        holder.binding.root.setOnLongClickListener {
            onLongClick(comment)
            true
        }
    }
}
