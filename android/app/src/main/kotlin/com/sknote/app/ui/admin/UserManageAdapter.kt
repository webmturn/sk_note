package com.sknote.app.ui.admin

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.User
import com.sknote.app.databinding.ItemUserManageBinding
import com.sknote.app.util.TimeUtil

class UserManageAdapter(
    private val onMoreClick: (User, android.view.View) -> Unit
) : ListAdapter<User, UserManageAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemUserManageBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position)

        holder.binding.tvAvatar.text = user.username.firstOrNull()?.uppercase() ?: "?"
        holder.binding.tvUsername.text = user.username
        holder.binding.tvMeta.text = buildString {
            append("ID: ${user.id}")
            user.email?.let { append(" · $it") }
            user.createdAt?.let { append(" · ${TimeUtil.formatRelative(it)}") }
        }

        val badge = holder.binding.tvRoleBadge
        when (user.role) {
            "admin" -> {
                badge.text = "管理员"
                badge.setTextColor(Color.WHITE)
                val bg = GradientDrawable().apply {
                    cornerRadius = 8f
                    setColor(0xFFE53935.toInt())
                }
                badge.background = bg
            }
            "editor" -> {
                badge.text = "编辑"
                badge.setTextColor(Color.WHITE)
                val bg = GradientDrawable().apply {
                    cornerRadius = 8f
                    setColor(0xFF1E88E5.toInt())
                }
                badge.background = bg
            }
            else -> {
                badge.text = "用户"
                badge.setTextColor(0xFF666666.toInt())
                val bg = GradientDrawable().apply {
                    cornerRadius = 8f
                    setColor(0xFFE0E0E0.toInt())
                }
                badge.background = bg
            }
        }

        holder.binding.btnMore.setOnClickListener { view ->
            onMoreClick(user, view)
        }
    }
}
