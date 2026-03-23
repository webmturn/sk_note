package com.sknote.app.ui.manage.category

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Category
import com.sknote.app.databinding.ItemCategoryManageBinding
import com.sknote.app.util.CategoryIconResolver
import com.sknote.app.util.IconViewBinder

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
        holder.binding.tvDescription.text = category.description.orEmpty().ifEmpty { "无描述" }
        val iconSpec = CategoryIconResolver.resolve(category.icon, category.name)
        IconViewBinder.bind(iconSpec, holder.binding.ivIcon, holder.binding.tvIcon)
        holder.binding.root.setOnClickListener { onEdit(category) }
        holder.binding.btnEdit.setOnClickListener { onEdit(category) }
        holder.binding.btnDelete.setOnClickListener { onDelete(category) }
    }
}
