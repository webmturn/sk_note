package com.sknote.app.ui.snippet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.CreateSnippetRequest
import com.sknote.app.databinding.FragmentCreateSnippetBinding
import kotlinx.coroutines.launch

class CreateSnippetFragment : Fragment() {

    private var _binding: FragmentCreateSnippetBinding? = null
    private val binding get() = _binding!!

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

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

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
        binding.btnSubmit.text = "发布中..."

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().createSnippet(
                    CreateSnippetRequest(
                        title = title,
                        description = description,
                        code = code,
                        language = selectedLanguage,
                        category = selectedCategory,
                        tags = tags
                    )
                )
                if (response.isSuccessful) {
                    Snackbar.make(binding.root, "发布成功", Snackbar.LENGTH_SHORT).show()
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_snippets", true)
                    findNavController().navigateUp()
                } else {
                    Snackbar.make(binding.root, "发布失败: ${response.code()}", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "网络错误: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                binding.btnSubmit.isEnabled = true
                binding.btnSubmit.text = "发布代码片段"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
