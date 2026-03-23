package com.sknote.app.ui.manage.discussioncategory

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.CreateDiscussionCategoryRequest
import com.sknote.app.databinding.FragmentCategoryEditorBinding
import com.sknote.app.ui.manage.iconpicker.CategoryIconPickerFragment
import com.sknote.app.util.CategoryIconCatalog
import com.sknote.app.util.ErrorUtil
import kotlinx.coroutines.launch

class DiscussionCategoryEditorFragment : Fragment() {

    private var _binding: FragmentCategoryEditorBinding? = null
    private val binding get() = _binding!!
    private var selectedIconKey: String = "default"
    private val categoryId: Long by lazy { arguments?.getLong(ARG_CATEGORY_ID) ?: 0L }
    private var slugEditedManually = categoryId != 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tilSlug.visibility = View.VISIBLE
        binding.toolbar.title = if (categoryId == 0L) "添加讨论分类" else "编辑讨论分类"
        binding.btnSave.text = if (categoryId == 0L) "创建讨论分类" else "保存讨论分类"
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.cardIconPreview.setOnClickListener {
            findNavController().navigate(R.id.categoryIconPickerFragment, CategoryIconPickerFragment.createArgs(selectedIconKey))
        }
        binding.btnSave.setOnClickListener { saveCategory() }

        binding.etSlug.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (binding.etSlug.hasFocus()) {
                    slugEditedManually = true
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (slugEditedManually) return
                val generated = slugify(s?.toString().orEmpty())
                binding.etSlug.setText(generated)
                binding.etSlug.setSelection(generated.length)
            }
        })

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

    private fun slugify(input: String): String {
        val normalized = buildString {
            input.lowercase().forEach { ch ->
                when {
                    ch in 'a'..'z' || ch in '0'..'9' -> append(ch)
                    ch == ' ' || ch == '-' || ch == '_' -> append('-')
                }
            }
        }
        return normalized.replace(Regex("-+"), "-").trim('-')
    }

    private fun loadCategory() {
        binding.btnSave.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getDiscussionCategory(categoryId)
                if (response.isSuccessful) {
                    val category = response.body()?.category
                    if (category == null) {
                        Snackbar.make(binding.root, "讨论分类不存在", Snackbar.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                        return@launch
                    }
                    binding.etName.setText(category.name)
                    binding.etSlug.setText(category.slug)
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
        val slug = binding.etSlug.text.toString().trim().lowercase()
        binding.tilName.error = null
        binding.tilSlug.error = null
        if (name.isEmpty()) {
            binding.tilName.error = "分类名称不能为空"
            return
        }
        if (slug.isEmpty() || !slug.matches(Regex("^[a-z0-9_-]+$"))) {
            binding.tilSlug.error = "Slug 只能包含小写字母、数字、下划线和中划线"
            return
        }

        binding.btnSave.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = CreateDiscussionCategoryRequest(
                    slug = slug,
                    name = name,
                    description = binding.etDescription.text.toString().trim(),
                    icon = selectedIconKey,
                )
                val response = if (categoryId == 0L) {
                    ApiClient.getService().createDiscussionCategory(request)
                } else {
                    ApiClient.getService().updateDiscussionCategory(categoryId, request)
                }
                if (response.isSuccessful) {
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(RESULT_REFRESH_KEY, true)
                    findNavController().previousBackStackEntry?.savedStateHandle?.set(
                        RESULT_MESSAGE_KEY,
                        if (categoryId == 0L) "讨论分类创建成功" else "讨论分类更新成功"
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
        const val RESULT_REFRESH_KEY = "refresh_discussion_categories"
        const val RESULT_MESSAGE_KEY = "discussion_category_manage_message"
    }
}
