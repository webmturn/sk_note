package com.sknote.app.ui.snippet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.sknote.app.R
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.CreateSnippetRequest
import com.sknote.app.data.model.UpdateSnippetRequest
import com.sknote.app.databinding.FragmentCreateSnippetBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CreateSnippetFragment : Fragment() {

    private var _binding: FragmentCreateSnippetBinding? = null
    private val binding get() = _binding!!
    private var snippetId: Long? = null
    private var isLoggedIn: Boolean = false
    private var hasLoadedInitialData = false

    private fun isFragmentUsable(): Boolean {
        return _binding != null && isAdded && context != null
    }

    private val categoryKeys get() = SnippetCategories.keys
    private val categoryLabels get() = SnippetCategories.labels
    private var selectedCategory = "general"

    private val languageKeys = listOf("java", "xml", "json", "kotlin", "python", "javascript", "html", "css", "other")
    private val languageLabels = listOf("Java", "XML", "JSON", "Kotlin", "Python", "JavaScript", "HTML", "CSS", "其他")
    private var selectedLanguage = "java"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateSnippetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        snippetId = arguments?.getLong("snippet_id")?.takeIf { it > 0L }
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.title = if (snippetId == null) "分享代码片段" else "编辑代码片段"
        binding.btnGoLogin.setOnClickListener {
            findNavController().navigate(R.id.loginFragment)
        }

        setupForm()
        refreshAuthState()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            refreshAuthState()
        }
    }

    private fun setupForm() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categoryLabels)
        binding.spinnerCategory.setAdapter(adapter)
        binding.spinnerCategory.setText(categoryLabels[0], false)
        binding.spinnerCategory.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = categoryKeys[position]
        }

        val langAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, languageLabels)
        binding.spinnerLanguage.setAdapter(langAdapter)
        binding.spinnerLanguage.setText(languageLabels[0], false)
        binding.spinnerLanguage.setOnItemClickListener { _, _, position, _ ->
            selectedLanguage = languageKeys[position]
        }

        binding.btnSubmit.setOnClickListener { submit() }
    }

    private fun refreshAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            if (!isFragmentUsable()) return@launch
            renderAuthState()
            if (isLoggedIn && !hasLoadedInitialData) {
                snippetId?.let { loadSnippet(it) }
                hasLoadedInitialData = true
            }
        }
    }

    private fun renderAuthState() {
        binding.layoutAuthRequired.visibility = if (isLoggedIn) View.GONE else View.VISIBLE
        binding.scrollContent.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
        binding.layoutSubmitBar.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
    }

    private fun loadSnippet(id: Long) {
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "加载中..."
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getSnippet(id)
                if (!isFragmentUsable()) return@launch
                if (response.isSuccessful) {
                    val snippet = response.body()?.snippet ?: return@launch
                    binding.etTitle.setText(snippet.title)
                    binding.etDescription.setText(snippet.description.orEmpty())
                    binding.etCode.setText(snippet.code.orEmpty())
                    binding.etTags.setText(snippet.tags.orEmpty())

                    val categoryIndex = categoryKeys.indexOf(snippet.category.orEmpty()).takeIf { it >= 0 } ?: 0
                    selectedCategory = categoryKeys[categoryIndex]
                    binding.spinnerCategory.setText(categoryLabels[categoryIndex], false)

                    val languageIndex = languageKeys.indexOf(snippet.language.orEmpty()).takeIf { it >= 0 } ?: 0
                    selectedLanguage = languageKeys[languageIndex]
                    binding.spinnerLanguage.setText(languageLabels[languageIndex], false)
                } else {
                    Snackbar.make(binding.root, "加载失败: ${response.code()}", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!isFragmentUsable()) return@launch
                Snackbar.make(binding.root, "网络错误: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                _binding?.btnSubmit?.isEnabled = true
                _binding?.btnSubmit?.text = if (snippetId == null) "发布代码片段" else "保存修改"
            }
        }
    }

    private fun submit() {
        val title = binding.etTitle.text.toString().trim()
        val code = binding.etCode.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val tags = binding.etTags.text.toString().trim()

        if (title.isEmpty()) {
            Snackbar.make(binding.root, "请输入标题", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (code.isEmpty()) {
            Snackbar.make(binding.root, "请输入代码", Snackbar.LENGTH_SHORT).show()
            return
        }

        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = if (snippetId == null) "发布中..." else "保存中..."

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val editingId = snippetId
                val response = if (editingId == null) {
                    ApiClient.getService().createSnippet(
                        CreateSnippetRequest(
                            title = title,
                            description = description,
                            code = code,
                            language = selectedLanguage,
                            category = selectedCategory,
                            tags = tags
                        )
                    )
                } else {
                    ApiClient.getService().updateSnippet(
                        editingId,
                        UpdateSnippetRequest(
                            title = title,
                            description = description,
                            code = code,
                            language = selectedLanguage,
                            category = selectedCategory,
                            tags = tags
                        )
                    )
                }
                if (!isFragmentUsable()) return@launch
                if (response.isSuccessful) {
                    val message = if (editingId == null) "发布成功" else "更新成功"
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_snippets", true)
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_snippet_detail", true)
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("snippet_result_message", message)
                    findNavController().navigateUp()
                } else {
                    Snackbar.make(binding.root, if (editingId == null) "发布失败: ${response.code()}" else "保存失败: ${response.code()}", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!isFragmentUsable()) return@launch
                Snackbar.make(binding.root, "网络错误: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                _binding?.btnSubmit?.isEnabled = true
                _binding?.btnSubmit?.text = if (snippetId == null) "发布代码片段" else "保存修改"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
