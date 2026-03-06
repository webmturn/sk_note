package com.sknote.app.ui.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Notification
import com.sknote.app.databinding.ItemNotificationBinding
import com.sknote.app.util.TimeUtil

class NotificationAdapter(
    private val onClick: (Notification) -> Unit = {},
    private val onLongClick: (Notification) -> Unit = {}
) : ListAdapter<Notification, NotificationAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

    companion object DiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(a: Notification, b: Notification) = a.id == b.id
        override fun areContentsTheSame(a: Notification, b: Notification) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.tvTitle.text = item.title
        holder.binding.tvContent.text = item.content
        holder.binding.tvTime.text = TimeUtil.formatRelative(item.createdAt)
        holder.binding.dotUnread.visibility = if (item.isRead == 0) View.VISIBLE else View.GONE

        val isUnread = item.isRead == 0
        holder.binding.tvTitle.alpha = if (isUnread) 1f else 0.6f
        holder.binding.tvContent.alpha = if (isUnread) 1f else 0.6f

        val context = holder.itemView.context
        val cardColor = if (isUnread) {
            com.google.android.material.R.attr.colorSurfaceVariant
        } else {
            com.google.android.material.R.attr.colorSurface
        }
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(cardColor, typedValue, true)
        holder.binding.cardNotification.setCardBackgroundColor(typedValue.data)

        holder.binding.root.setOnClickListener { onClick(item) }
        holder.binding.root.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }
}
