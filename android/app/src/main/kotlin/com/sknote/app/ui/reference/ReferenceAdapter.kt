package com.sknote.app.ui.reference

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.ReferenceItem
import com.sknote.app.databinding.ItemReferenceBinding

class ReferenceAdapter(
    private val onClick: (ReferenceItem) -> Unit,
    private var bookmarkedIds: Set<Long> = emptySet()
) : ListAdapter<ReferenceItem, ReferenceAdapter.ViewHolder>(DiffCallback) {

    fun updateBookmarks(ids: Set<Long>) {
        bookmarkedIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReferenceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemReferenceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ReferenceItem) {
            binding.tvName.text = item.name
            binding.tvDescription.text = item.description.ifEmpty { "暂无描述" }
            binding.tvType.text = ReferenceIcons.getTypeLabel(item.type)
            binding.tvCategory.text = item.category

            binding.blockShape.visibility = View.GONE
            binding.iconCard.visibility = View.VISIBLE
            binding.ivIcon.setImageResource(ReferenceIcons.getIconRes(item))

            // Set accent strip color from block color
            val blockColor = try {
                Color.parseColor(item.color)
            } catch (_: Exception) {
                Color.parseColor("#FF1976D2")
            }
            binding.accentStrip.setBackgroundColor(blockColor)

            // Tint icon card background with a subtle version of the block color
            val tintedBg = Color.argb(25, Color.red(blockColor), Color.green(blockColor), Color.blue(blockColor))
            binding.iconCard.setCardBackgroundColor(tintedBg)
            binding.ivIcon.setColorFilter(blockColor)

            binding.ivBookmark.visibility = if (bookmarkedIds.contains(item.id)) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onClick(item) }
        }
    }


    companion object DiffCallback : DiffUtil.ItemCallback<ReferenceItem>() {
        override fun areItemsTheSame(a: ReferenceItem, b: ReferenceItem) = a.id == b.id
        override fun areContentsTheSame(a: ReferenceItem, b: ReferenceItem) = a == b
    }
}
