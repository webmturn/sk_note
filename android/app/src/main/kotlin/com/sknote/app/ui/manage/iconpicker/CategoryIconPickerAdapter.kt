package com.sknote.app.ui.manage.iconpicker

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.sknote.app.databinding.ItemCategoryIconPickerBinding
import com.sknote.app.util.CategoryIconOption

class CategoryIconPickerAdapter(
    private val options: List<CategoryIconOption>,
    private val selectedKey: String,
    private val onSelected: (CategoryIconOption) -> Unit,
) : RecyclerView.Adapter<CategoryIconPickerAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCategoryIconPickerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryIconPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = options.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val option = options[position]
        val isSelected = option.key == selectedKey
        val binding = holder.binding
        val strokeColorAttr = if (isSelected) com.google.android.material.R.attr.colorPrimary else com.google.android.material.R.attr.colorOutlineVariant
        val iconColorAttr = if (isSelected) com.google.android.material.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnSurfaceVariant
        val backgroundColorAttr = if (isSelected) com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorSurface

        binding.ivIcon.setImageResource(option.drawableRes)
        binding.ivIcon.imageTintList = ColorStateList.valueOf(MaterialColors.getColor(binding.root, iconColorAttr))
        binding.cardRoot.strokeColor = MaterialColors.getColor(binding.root, strokeColorAttr)
        binding.cardRoot.setCardBackgroundColor(MaterialColors.getColor(binding.root, backgroundColorAttr))
        binding.cardRoot.strokeWidth = if (isSelected) 2 else 1
        binding.cardRoot.setOnClickListener { onSelected(option) }
    }
}
