package com.sknote.app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Category
import com.sknote.app.databinding.ItemCategoryManageBinding

class CategoryManageAdapter(
    private val onEdit: (Category) -> Unit,
    private val onDelete: (Category) -> Unit
) : ListAdapter<Category, CategoryManageAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemCategoryManageBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(oldItem: Category, newItem: Category) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Category, newItem: Category) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = getItem(position)
        holder.binding.tvName.text = category.name
        holder.binding.tvDescription.text = category.description.ifEmpty { "无描述" }
        holder.binding.tvIcon.text = category.icon.ifEmpty { "📁" }
        holder.binding.btnEdit.setOnClickListener { onEdit(category) }
        holder.binding.btnDelete.setOnClickListener { onDelete(category) }
    }
}
