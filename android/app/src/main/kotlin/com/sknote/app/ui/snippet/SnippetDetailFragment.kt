package com.sknote.app.ui.snippet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.data.api.ApiClient
import com.sknote.app.databinding.FragmentSnippetDetailBinding
import com.sknote.app.util.requireLoggedIn
import com.sknote.app.util.SyntaxHighlightUtil
import com.sknote.app.util.TimeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SnippetDetailFragment : Fragment() {

    private var _binding: FragmentSnippetDetailBinding? = null
    private val binding get() = _binding!!
    private var snippetId: Long = 0
    private var codeText: String = ""
    private var isLiking = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSnippetDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        snippetId = arguments?.getLong("snippet_id", 0L) ?: 0L
        if (snippetId <= 0L) return
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnCopy.setOnClickListener { copyCode() }

        binding.btnLike.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                if (isLiking) return@launch
                if (!requireLoggedIn(binding.root)) {
                    return@launch
                }
                try {
                    isLiking = true
                    binding.btnLike.isEnabled = false
                    val response = ApiClient.getService().likeSnippet(snippetId)
                    if (_binding == null) return@launch
                    if (response.isSuccessful) {
                        val body = response.body()
                        val liked = body?.liked ?: false
                        Snackbar.make(binding.root, if (liked) "已点赞" else "已取消点赞", Snackbar.LENGTH_SHORT).show()
                        loadSnippet(showProgress = false)
                    }
                } catch (e: Exception) {
                    if (_binding == null) return@launch
                    Snackbar.make(binding.root, "操作失败", Snackbar.LENGTH_SHORT).show()
                } finally {
                    isLiking = false
                    _binding?.btnLike?.isEnabled = true
                }
            }
        }

        loadSnippet()
    }

    private fun loadSnippet(showProgress: Boolean = true) {
        if (showProgress) binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getSnippet(snippetId)
                if (_binding == null) return@launch
                if (response.isSuccessful) {
                    val snippet = response.body()?.snippet ?: return@launch
                    codeText = snippet.code.orEmpty()

                    binding.tvTitle.text = snippet.title
                    if (snippet.description.orEmpty().isNotEmpty()) {
                        binding.tvDescription.text = snippet.description.orEmpty()
                        binding.tvDescription.visibility = View.VISIBLE
                    } else {
                        binding.tvDescription.visibility = View.GONE
                    }
                    val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    binding.tvCode.text = SyntaxHighlightUtil.highlight(snippet.code.orEmpty(), snippet.language.orEmpty(), isDark)
                    binding.tvLanguage.text = snippet.language.orEmpty().uppercase()
                    binding.tvCategory.text = SnippetCategories.getLabel(snippet.category.orEmpty())
                    binding.tvAuthor.text = snippet.authorName.orEmpty().ifEmpty { "系统" }
                    binding.tvTime.text = TimeUtil.formatRelative(snippet.createdAt)
                    binding.tvViews.text = "${snippet.viewCount} 浏览"
                    binding.btnLike.text = "点赞 (${snippet.likeCount})"
                    bindTags(snippet.tags.orEmpty())

                    binding.toolbar.title = snippet.title
                } else {
                    Snackbar.make(binding.root, "加载失败", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                Snackbar.make(binding.root, "网络错误: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun bindTags(rawTags: String) {
        val tags = rawTags
            .split(Regex("[,，\\s]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (tags.isEmpty()) {
            binding.layoutTags.visibility = View.GONE
            binding.chipGroupTags.removeAllViews()
            return
        }

        binding.layoutTags.visibility = View.VISIBLE
        binding.chipGroupTags.removeAllViews()
        tags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = if (tag.startsWith("#")) tag else "#$tag"
                isCheckable = false
                isClickable = false
                isCloseIconVisible = false
                setEnsureMinTouchTargetSize(false)
            }
            binding.chipGroupTags.addView(chip)
        }
    }

    private fun copyCode() {
        if (codeText.isEmpty()) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("code", codeText))
        Snackbar.make(binding.root, "代码已复制到剪贴板", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
