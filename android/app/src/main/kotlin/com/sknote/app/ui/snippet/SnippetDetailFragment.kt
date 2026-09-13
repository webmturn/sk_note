package com.sknote.app.ui.snippet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Snippet
import com.sknote.app.databinding.FragmentSnippetDetailBinding
import com.sknote.app.util.SkeletonAnimator
import com.sknote.app.util.NavPrefill
import com.sknote.app.util.hideSkeletonAndShow
import com.sknote.app.util.prefill
import com.sknote.app.util.requireLoggedIn
import com.sknote.app.util.showSkeleton
import com.sknote.app.util.slideNavOptions
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
    private var currentSnippet: Snippet? = null
    private var currentUserId: Long = -1
    private var currentUserRole: String = "user"
    private var skeletonAnimator: SkeletonAnimator? = null
    private var contentShown: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSnippetDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navOptions = slideNavOptions()

        snippetId = arguments?.getLong("snippet_id", 0L) ?: 0L
        if (snippetId <= 0L) {
            Toast.makeText(requireContext(), "无效的代码片段ID", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.menu_snippet_detail)

        val navHandle = findNavController().currentBackStackEntry?.savedStateHandle
        navHandle?.getLiveData<Boolean>("refresh_snippet_detail")
            ?.observe(viewLifecycleOwner) { refresh ->
                if (refresh == true) {
                    loadSnippet(showProgress = false)
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_snippets", true)
                    navHandle.remove<Boolean>("refresh_snippet_detail")
                }
            }

        navHandle?.getLiveData<String>("snippet_result_message")
            ?.observe(viewLifecycleOwner) { message ->
                if (!message.isNullOrEmpty()) {
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    navHandle.remove<String>("snippet_result_message")
                }
            }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    val bundle = Bundle().apply { putLong("snippet_id", snippetId) }
                    findNavController().navigate(R.id.createSnippetFragment, bundle, navOptions)
                    true
                }
                else -> false
            }
        }

        binding.btnCopy.setOnClickListener { copyCode() }

        // 有预填数据时首帧就是真实内容，不使用整页骨架。这一页的全部内容（标题、
        // 描述、代码、语言、分类、标签、作者、计数）都来自 Snippet 对象本身，而片段
        // 列表接口已带上 code，因此可以整页预填。没有预填数据（通知入口只带
        // snippet_id）时才退回骨架路径。
        val prefill = arguments?.prefill<Snippet>(NavPrefill.KEY_SNIPPET)
        if (prefill != null) {
            binding.skeletonContainer.root.visibility = View.GONE
            binding.scrollView.visibility = View.VISIBLE
            contentShown = true
            renderSnippet(prefill)
        } else {
            showSkeleton(binding.skeletonContainer.root, binding.scrollView)
            skeletonAnimator = SkeletonAnimator.start(viewLifecycleOwner, binding.skeletonContainer.root)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            if (isLoggedIn) {
                currentUserId = ApiClient.getTokenManager().getUserId().first() ?: -1
                currentUserRole = ApiClient.getTokenManager().getUserRole().first() ?: "user"
            }
            binding.toolbar.menu.findItem(R.id.action_edit)?.isVisible = false
            loadSnippet(showProgress = false)
        }

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

    }

    private fun loadSnippet(showProgress: Boolean = true) {
        if (showProgress && contentShown) binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getSnippet(snippetId)
                if (_binding == null) return@launch
                if (response.isSuccessful) {
                    val snippet = response.body()?.snippet ?: return@launch
                    revealContent()
                    renderSnippet(snippet)
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

    /**
     * 把一个 [Snippet] 渲染到页面上。预填路径与详情接口路径共用这一份，
     * 避免出现「预填一套 / 接口一套」两段渲染代码。
     *
     * 注意代码高亮依赖当前昼夜模式（`resources.configuration`），标签是动态生成的
     * Chip —— 两者在预填路径下同样可用，因为渲染发生在 Fragment 已 attach 之后。
     */
    private fun renderSnippet(snippet: Snippet) {
        currentSnippet = snippet
        codeText = snippet.code.orEmpty()
        val displayLanguage = snippet.language.orEmpty().trim().ifEmpty { "other" }
        val displayCode = codeText.ifEmpty { "暂无代码" }

        binding.tvTitle.text = snippet.title
        if (snippet.description.orEmpty().isNotEmpty()) {
            binding.tvDescription.text = snippet.description.orEmpty()
            binding.tvDescription.visibility = View.VISIBLE
        } else {
            binding.tvDescription.visibility = View.GONE
        }
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        binding.tvCode.text = if (codeText.isEmpty()) {
            displayCode
        } else {
            SyntaxHighlightUtil.highlight(codeText, displayLanguage, isDark)
        }
        binding.tvLanguage.text = displayLanguage.uppercase()
        binding.tvCategory.text = SnippetCategories.getLabel(snippet.category.orEmpty())
        binding.tvAuthor.text = snippet.authorName.orEmpty().ifEmpty { "系统" }
        binding.tvTime.text = TimeUtil.formatRelative(snippet.createdAt)
        binding.tvViews.text = "${snippet.viewCount} 浏览"
        binding.btnLike.text = "点赞 (${snippet.likeCount})"
        binding.btnCopy.isEnabled = codeText.isNotEmpty()
        binding.btnCopy.alpha = if (codeText.isNotEmpty()) 1f else 0.5f
        bindTags(snippet.tags.orEmpty())

        binding.toolbar.title = snippet.title
        val canEdit = snippet.authorId == currentUserId || currentUserRole == "admin"
        binding.toolbar.menu.findItem(R.id.action_edit)?.isVisible = canEdit
    }

    /**
     * 首次显示真实内容。两条路径的进入方式不同，因此收敛到这里：
     * 有预填数据时首帧就是正确的，直接显示；没有预填数据时整页骨架正在呼吸，交叉淡出。
     */
    private fun revealContent() {
        if (contentShown) return
        contentShown = true
        val animator = skeletonAnimator
        if (animator == null) {
            binding.scrollView.visibility = View.VISIBLE
        } else {
            animator.stop()
            skeletonAnimator = null
            hideSkeletonAndShow(binding.skeletonContainer.root, binding.scrollView)
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
        if (codeText.isEmpty()) {
            Snackbar.make(binding.root, "暂无可复制内容", Snackbar.LENGTH_SHORT).show()
            return
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("code", codeText))
        Snackbar.make(binding.root, "代码已复制到剪贴板", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        skeletonAnimator?.stop()
        skeletonAnimator = null
        contentShown = false
        _binding = null
    }
}
