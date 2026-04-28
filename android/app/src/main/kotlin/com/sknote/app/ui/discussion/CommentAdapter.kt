package com.sknote.app.ui.discussion

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sknote.app.R
import com.sknote.app.data.model.Comment
import com.sknote.app.databinding.ItemCommentBinding
import com.sknote.app.util.TimeUtil

class CommentAdapter(
    private val onReplyClick: (Comment) -> Unit = {},
    private val onCopyClick: (Comment) -> Unit = {},
    private val onDeleteClick: (Comment) -> Unit = {},
    private val onLikeClick: (Comment) -> Unit = {},
    private val onAvatarClick: (Comment) -> Unit = {}
) : ListAdapter<Comment, CommentAdapter.ViewHolder>(DiffCallback) {

    private var currentUserId: Long = -1L
    private var currentUserRole: String = "user"

    class ViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)

    companion object DiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(a: Comment, b: Comment) = a.id == b.id
        override fun areContentsTheSame(a: Comment, b: Comment) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    fun updateUserContext(userId: Long, userRole: String) {
        val role = userRole.ifBlank { "user" }
        if (currentUserId == userId && currentUserRole == role) return
        currentUserId = userId
        currentUserRole = role
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = getItem(position)
        holder.binding.tvAuthor.text = comment.authorName ?: "匿名"
        val placeholderPadding = (4 * holder.binding.root.context.resources.displayMetrics.density).toInt()
        if (!comment.authorAvatar.isNullOrEmpty()) {
            holder.binding.ivAvatar.imageTintList = null
            holder.binding.ivAvatar.setPadding(0, 0, 0, 0)
            Glide.with(holder.itemView.context)
                .load(comment.authorAvatar)
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .into(holder.binding.ivAvatar)
        } else {
            Glide.with(holder.itemView.context).clear(holder.binding.ivAvatar)
            holder.binding.ivAvatar.setImageResource(R.drawable.ic_person)
            holder.binding.ivAvatar.setPadding(
                placeholderPadding,
                placeholderPadding,
                placeholderPadding,
                placeholderPadding
            )
        }
        holder.binding.tvContent.text = comment.content.orEmpty()
        holder.binding.tvTime.text = TimeUtil.formatRelative(comment.createdAt)
        holder.binding.tvLikeCount.text = if (comment.likeCount > 0) "${comment.likeCount}" else ""
        // 显示回复目标
        if (!comment.parentAuthorName.isNullOrEmpty()) {
            holder.binding.tvReplyTo.text = "→ @${comment.parentAuthorName}"
            holder.binding.tvReplyTo.visibility = android.view.View.VISIBLE
        } else {
            holder.binding.tvReplyTo.visibility = android.view.View.GONE
        }
        // 回复评论左侧缩进
        val lp = holder.binding.root.layoutParams as? android.view.ViewGroup.MarginLayoutParams
        if (lp != null) {
            val density = holder.binding.root.context.resources.displayMetrics.density
            lp.marginStart = if (comment.parentId != null) (28 * density).toInt() else 0
            holder.binding.root.layoutParams = lp
        }
        holder.binding.ivAvatar.setOnClickListener { onAvatarClick(comment) }
        holder.binding.tvAuthor.setOnClickListener { onAvatarClick(comment) }
        holder.binding.btnLike.setOnClickListener { onLikeClick(comment) }
        holder.binding.btnReply.setOnClickListener { onReplyClick(comment) }
        holder.binding.btnCopy.setOnClickListener { onCopyClick(comment) }

        val canDelete = comment.authorId == currentUserId || currentUserRole == "admin" || currentUserRole == "editor"
        holder.binding.btnDelete.visibility = if (canDelete) View.VISIBLE else View.GONE
        holder.binding.btnDelete.setOnClickListener { onDeleteClick(comment) }
    }
}
