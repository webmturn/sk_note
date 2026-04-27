package com.sknote.app.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sknote.app.R
import com.sknote.app.data.model.FollowUser
import com.sknote.app.databinding.ItemFollowUserBinding

class FollowUserAdapter(
    private val onClick: (FollowUser) -> Unit
) : ListAdapter<FollowUser, FollowUserAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FollowUser>() {
            override fun areItemsTheSame(a: FollowUser, b: FollowUser) = a.id == b.id
            override fun areContentsTheSame(a: FollowUser, b: FollowUser) = a == b
        }
    }

    inner class ViewHolder(val binding: ItemFollowUserBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFollowUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position)
        holder.binding.tvUsername.text = user.displayName
        holder.binding.tvRole.text = when (user.role) {
            "admin" -> "管理员"
            "editor" -> "编辑"
            else -> "用户"
        }
        if (!user.avatarUrl.isNullOrEmpty()) {
            Glide.with(holder.binding.root.context)
                .load(user.avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_account_circle)
                .into(holder.binding.ivAvatar)
        } else {
            Glide.with(holder.binding.root.context).clear(holder.binding.ivAvatar)
            holder.binding.ivAvatar.setImageResource(R.drawable.ic_account_circle)
        }
    }
}
