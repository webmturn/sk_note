package com.sknote.app.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.sknote.app.R
import com.sknote.app.data.model.Discussion
import com.sknote.app.data.model.Share
import com.sknote.app.data.model.Snippet
import com.sknote.app.util.TimeUtil

class ProfileContentAdapter<T>(
    private val items: List<T>,
    private val type: Int,
    private val onClick: (T) -> Unit
) : RecyclerView.Adapter<ProfileContentAdapter<T>.ViewHolder>() {

    companion object {
        const val TYPE_DISCUSSION = 0
        const val TYPE_SNIPPET = 1
        const val TYPE_SHARE = 2
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val card: MaterialCardView = itemView.findViewById(R.id.card)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile_content, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        when (type) {
            TYPE_DISCUSSION -> {
                val d = item as Discussion
                holder.tvTitle.text = d.title
                holder.tvSubtitle.text = "回复 ${d.replyCount} · 浏览 ${d.viewCount}"
                holder.tvTime.text = d.createdAt?.let { TimeUtil.formatRelative(it) } ?: ""
            }
            TYPE_SNIPPET -> {
                val s = item as Snippet
                holder.tvTitle.text = s.title
                holder.tvSubtitle.text = "${s.language} · 点赞 ${s.likeCount}"
                holder.tvTime.text = s.createdAt?.let { TimeUtil.formatRelative(it) } ?: ""
            }
            TYPE_SHARE -> {
                val s = item as Share
                holder.tvTitle.text = s.title
                holder.tvSubtitle.text = "下载 ${s.downloadCount} · 点赞 ${s.likeCount}"
                holder.tvTime.text = s.createdAt?.let { TimeUtil.formatRelative(it) } ?: ""
            }
        }
        holder.card.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
