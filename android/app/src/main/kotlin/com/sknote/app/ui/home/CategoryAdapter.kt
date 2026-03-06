package com.sknote.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.data.model.Category
import com.sknote.app.databinding.ItemCategoryBinding

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
            binding.tvIcon.text = category.icon.ifEmpty { "📁" }
            binding.tvCategoryName.text = category.name
            binding.tvCategoryDesc.text = category.description.ifEmpty { "" }
            binding.root.setOnClickListener { onClick(category) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(a: Category, b: Category) = a.id == b.id
        override fun areContentsTheSame(a: Category, b: Category) = a == b
    }
}
