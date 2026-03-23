package com.sknote.app.ui.manage.discussioncategory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.DiscussionCategory
import com.sknote.app.databinding.ItemCategoryManageBinding
import com.sknote.app.util.CategoryIconResolver
import com.sknote.app.util.IconViewBinder

class DiscussionCategoryManageAdapter(
    private val onEdit: (DiscussionCategory) -> Unit,
    private val onDelete: (DiscussionCategory) -> Unit,
) : ListAdapter<DiscussionCategory, DiscussionCategoryManageAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemCategoryManageBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<DiscussionCategory>() {
        override fun areItemsTheSame(oldItem: DiscussionCategory, newItem: DiscussionCategory) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DiscussionCategory, newItem: DiscussionCategory) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = getItem(position)
        holder.binding.tvName.text = category.name
        holder.binding.tvDescription.text = buildString {
            append("slug: ${category.slug}")
            val description = category.description.orEmpty().trim()
            if (description.isNotEmpty()) {
                append("\n")
                append(description)
            }
        }
        val iconSpec = CategoryIconResolver.resolve(category.icon, category.name)
        IconViewBinder.bind(iconSpec, holder.binding.ivIcon, holder.binding.tvIcon)
        holder.binding.root.setOnClickListener { onEdit(category) }
        holder.binding.btnEdit.setOnClickListener { onEdit(category) }
        holder.binding.btnDelete.setOnClickListener { onDelete(category) }
    }
}
