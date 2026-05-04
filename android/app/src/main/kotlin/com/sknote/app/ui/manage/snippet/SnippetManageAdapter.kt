package com.sknote.app.ui.manage.snippet

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import com.sknote.app.data.model.Snippet
import com.sknote.app.databinding.ItemSnippetManageBinding
import com.sknote.app.util.TimeUtil

class SnippetManageAdapter(
    private val onView: (Snippet) -> Unit,
    private val onDelete: (Snippet) -> Unit
) : ListAdapter<Snippet, SnippetManageAdapter.ViewHolder>(DiffCallback()) {

    private var currentUserId: Long = -1L
    private var currentRole: String = "user"

    fun updateUserContext(userId: Long, role: String) {
        val effective = role.ifBlank { "user" }
        if (currentUserId == userId && currentRole == effective) return
        currentUserId = userId
        currentRole = effective
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemSnippetManageBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<Snippet>() {
        override fun areItemsTheSame(oldItem: Snippet, newItem: Snippet) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Snippet, newItem: Snippet) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSnippetManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.tvTitle.text = item.title
        holder.binding.tvMeta.text = "${item.authorName.orEmpty()} · ${item.language.orEmpty()} · ${item.category.orEmpty()} · ❤ ${item.likeCount} · ${TimeUtil.formatRelative(item.createdAt)}"

        // Backend snippet DELETE is author-or-admin only; hide delete on rows the current editor cannot remove.
        val canDelete = item.authorId == currentUserId || currentRole == "admin"
        holder.binding.btnDelete.visibility = if (canDelete) View.VISIBLE else View.GONE

        holder.binding.btnView.setOnClickListener { onView(item) }
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
    }
}
