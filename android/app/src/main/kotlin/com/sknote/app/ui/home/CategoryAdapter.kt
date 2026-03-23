package com.sknote.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Category
import com.sknote.app.databinding.ItemCategoryBinding
import com.sknote.app.util.CategoryIconResolver
import com.sknote.app.util.IconViewBinder

class CategoryAdapter(
    private val onClick: (Category) -> Unit
) : ListAdapter<Category, CategoryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(category: Category) {
            val iconSpec = CategoryIconResolver.resolve(category.icon, category.name)
            IconViewBinder.bind(iconSpec, binding.ivIcon, binding.tvIcon)
            binding.tvCategoryName.text = category.name
            binding.tvCategoryDesc.text = category.description.orEmpty().ifEmpty { "" }
            binding.root.setOnClickListener { onClick(category) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(a: Category, b: Category) = a.id == b.id
        override fun areContentsTheSame(a: Category, b: Category) = a == b
    }
}
