package com.sknote.app.ui.manage.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.CreateCategoryRequest
import com.sknote.app.databinding.FragmentCategoryEditorBinding
import com.sknote.app.ui.manage.iconpicker.CategoryIconPickerFragment
import com.sknote.app.util.CategoryIconCatalog
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class CategoryEditorFragment : Fragment() {

    private var _binding: FragmentCategoryEditorBinding? = null
    private val binding get() = _binding!!
    private var selectedIconKey: String = "default"
    private val categoryId: Long by lazy { arguments?.getLong(ARG_CATEGORY_ID) ?: 0L }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tilSlug.visibility = View.GONE
        binding.toolbar.title = if (categoryId == 0L) "添加分类" else "编辑分类"
        binding.btnSave.text = if (categoryId == 0L) "创建分类" else "保存分类"
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.cardIconPreview.setOnClickListener {
            findNavController().navigate(R.id.categoryIconPickerFragment, CategoryIconPickerFragment.createArgs(selectedIconKey))
        }
        binding.btnSave.setOnClickListener { saveCategory() }

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<String>(CategoryIconPickerFragment.RESULT_SELECTED_ICON_KEY)
            ?.observe(viewLifecycleOwner) { selectedKey ->
                selectedIconKey = selectedKey
                updateIconPreview()
                findNavController().currentBackStackEntry?.savedStateHandle?.remove<String>(CategoryIconPickerFragment.RESULT_SELECTED_ICON_KEY)
            }

        if (categoryId == 0L) {
            updateIconPreview()
        } else {
            loadCategory()
        }
    }

    private fun loadCategory() {
        binding.btnSave.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getCategory(categoryId)
                if (response.isSuccessful) {
                    val category = response.body()?.category
                    if (category == null) {
                        Snackbar.make(binding.root, "分类不存在", Snackbar.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                        return@launch
                    }
                    binding.etName.setText(category.name)
                    binding.etDescription.setText(category.description.orEmpty())
                    selectedIconKey = CategoryIconCatalog.normalizeKey(category.icon, category.name)
                    updateIconPreview()
                } else {
                    Snackbar.make(binding.root, "加载失败: ${response.code()}", Snackbar.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, ErrorUtil.friendlyMessage(e), Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } finally {
                binding.btnSave.isEnabled = true
            }
        }
    }

    private fun updateIconPreview() {
        val option = CategoryIconCatalog.findByKey(selectedIconKey) ?: CategoryIconCatalog.options.first()
        binding.ivIconPreview.setImageResource(option.drawableRes)
        binding.tvIconPreviewLabel.text = option.label
    }

    private fun saveCategory() {
        val name = binding.etName.text.toString().trim()
        binding.tilName.error = null
        if (name.isEmpty()) {
            binding.tilName.error = "分类名称不能为空"
            return
        }

        binding.btnSave.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = CreateCategoryRequest(
                    name = name,
                    description = binding.etDescription.text.toString().trim(),
                    icon = selectedIconKey,
                )
                val response = if (categoryId == 0L) {
                    ApiClient.getService().createCategory(request)
                } else {
                    ApiClient.getService().updateCategory(categoryId, request)
                }
                if (response.isSuccessful) {
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(RESULT_REFRESH_KEY, true)
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(
                        RESULT_MESSAGE_KEY,
                        if (categoryId == 0L) "分类创建成功" else "分类更新成功"
                    )
                    findNavController().navigateUp()
                } else {
                    Snackbar.make(binding.root, "保存失败: ${response.code()}", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, ErrorUtil.friendlyMessage(e), Snackbar.LENGTH_SHORT).show()
            } finally {
                binding.btnSave.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_CATEGORY_ID = "category_id"
        const val RESULT_REFRESH_KEY = "refresh_categories"
        const val RESULT_MESSAGE_KEY = "category_manage_message"
    }
}
