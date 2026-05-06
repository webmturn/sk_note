package com.sknote.app.ui.manage.share

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Share
import com.sknote.app.databinding.ItemShareManageBinding
import com.sknote.app.util.TimeUtil

class ShareManageAdapter(
    private val onView: (Share) -> Unit,
    private val onApprove: (Share) -> Unit,
    private val onDelete: (Share) -> Unit
) : ListAdapter<Share, ShareManageAdapter.ViewHolder>(DiffCallback()) {

    private var currentUserId: Long = -1L
    private var currentRole: String = "user"

    fun updateUserContext(userId: Long, role: String) {
        val effective = role.ifBlank { "user" }
        if (currentUserId == userId && currentRole == effective) return
        currentUserId = userId
        currentRole = effective
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemShareManageBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<Share>() {
        override fun areItemsTheSame(oldItem: Share, newItem: Share) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Share, newItem: Share) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShareManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.tvTitle.text = item.title

        val approved = item.isApproved == 1
        holder.binding.tvBadgeStatus.text = if (approved) "已发布" else "待审核"
        holder.binding.tvBadgeStatus.visibility = View.VISIBLE

        val authorLabel = item.authorName.orEmpty().ifEmpty { "匿名" }
        val categoryLabel = item.category.orEmpty().ifEmpty { "general" }
        holder.binding.tvMeta.text = "$authorLabel · $categoryLabel · ↓ ${item.downloadCount} · ❤ ${item.likeCount} · ${TimeUtil.formatRelative(item.createdAt)}"

        holder.binding.btnApprove.text = if (approved) "取消批准" else "批准"
        // 只有管理员可以审核（这里页面已限制 admin 才能进入）
        holder.binding.btnApprove.visibility = if (currentRole == "admin") View.VISIBLE else View.GONE

        // 后端 share DELETE 是 author-or-admin；列表已经按 admin 角色登录，可全删
        val canDelete = item.authorId == currentUserId || currentRole == "admin"
        holder.binding.btnDelete.visibility = if (canDelete) View.VISIBLE else View.GONE

        holder.binding.btnView.setOnClickListener { onView(item) }
        holder.binding.btnApprove.setOnClickListener { onApprove(item) }
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
    }
}
