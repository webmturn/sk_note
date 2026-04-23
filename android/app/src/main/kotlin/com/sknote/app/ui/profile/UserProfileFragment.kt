package com.sknote.app.ui.profile

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Discussion
import com.sknote.app.data.model.Share
import com.sknote.app.data.model.Snippet
import com.sknote.app.databinding.FragmentUserProfileBinding
import com.sknote.app.util.TimeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UserProfileFragment : Fragment() {

    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!
    private var myId: Long = 0
    private var currentTab = 0
    private var isInitialLoad = true
    private var discussionListState: Parcelable? = null
    private var snippetListState: Parcelable? = null
    private var shareListState: Parcelable? = null

    companion object {
        private const val STATE_CURRENT_TAB = "state_current_tab"
        private const val STATE_DISCUSSION_LIST = "state_discussion_list"
        private const val STATE_SNIPPET_LIST = "state_snippet_list"
        private const val STATE_SHARE_LIST = "state_share_list"
    }

    private fun roleLabel(role: String?): String {
        return when (role) {
            "admin" -> "管理员"
            "editor" -> "编辑"
            else -> "普通用户"
        }
    }

    private fun isFragmentUsable(): Boolean {
        return isAdded && _binding != null && view != null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)

        // 同步预填充缓存数据，避免布局跳动
        val tokenManager = ApiClient.getTokenManager()
        val cachedNickname = tokenManager.cachedNickname ?: ""
        val cachedUsername = tokenManager.cachedUsername ?: ""
        val cachedRole = tokenManager.cachedRole ?: "user"
        val cachedCreatedAt = tokenManager.cachedCreatedAt
        binding.tvUsername.text = cachedNickname.ifEmpty { cachedUsername }
        binding.tvHandle.text = "@$cachedUsername"
        binding.chipRole.text = "· ${roleLabel(cachedRole)}"
        if (!cachedCreatedAt.isNullOrEmpty()) {
            binding.tvJoinDate.text = "注册于 ${TimeUtil.formatRelative(cachedCreatedAt)}"
            binding.tvJoinDate.visibility = View.VISIBLE
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentTab = savedInstanceState?.getInt(STATE_CURRENT_TAB) ?: currentTab
        discussionListState = savedInstanceState?.getParcelable(STATE_DISCUSSION_LIST) ?: discussionListState
        snippetListState = savedInstanceState?.getParcelable(STATE_SNIPPET_LIST) ?: snippetListState
        shareListState = savedInstanceState?.getParcelable(STATE_SHARE_LIST) ?: shareListState

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val navHandle = findNavController().currentBackStackEntry?.savedStateHandle
        navHandle?.getLiveData<Boolean>("refresh_shares")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    loadContent()
                    navHandle.remove<Boolean>("refresh_shares")
                }
            }

        navHandle?.getLiveData<Boolean>("refresh_snippets")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    loadContent()
                    navHandle.remove<Boolean>("refresh_snippets")
                }
            }

        navHandle?.getLiveData<String>("share_result_message")
            ?.observe(viewLifecycleOwner) { message ->
                if (!message.isNullOrEmpty()) {
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    navHandle.remove<String>("share_result_message")
                }
            }

        navHandle?.getLiveData<String>("snippet_result_message")
            ?.observe(viewLifecycleOwner) { message ->
                if (!message.isNullOrEmpty()) {
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    navHandle.remove<String>("snippet_result_message")
                }
            }

        val navOptions = androidx.navigation.NavOptions.Builder()
            .setEnterAnim(R.anim.slide_in_right)
            .setExitAnim(R.anim.slide_out_left)
            .setPopEnterAnim(R.anim.slide_in_left)
            .setPopExitAnim(R.anim.slide_out_right)
            .build()

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit_profile -> {
                    findNavController().navigate(R.id.profileEditFragment, null, navOptions)
                    true
                }
                else -> false
            }
        }

        binding.layoutFollowing.setOnClickListener {
            if (myId > 0) {
                val bundle = Bundle().apply {
                    putLong("user_id", myId)
                    putInt("tab", 0)
                }
                findNavController().navigate(R.id.followListFragment, bundle, navOptions)
            }
        }

        binding.layoutFollowers.setOnClickListener {
            if (myId > 0) {
                val bundle = Bundle().apply {
                    putLong("user_id", myId)
                    putInt("tab", 1)
                }
                findNavController().navigate(R.id.followListFragment, bundle, navOptions)
            }
        }

        binding.rvContent.layoutManager = LinearLayoutManager(context)

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("讨论"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("代码片段"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("分享"))

        binding.tabLayout.getTabAt(currentTab)?.select()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                captureCurrentListState()
                currentTab = tab?.position ?: 0
                loadContent()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadData()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureCurrentListState()
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_TAB, currentTab)
        outState.putParcelable(STATE_DISCUSSION_LIST, discussionListState)
        outState.putParcelable(STATE_SNIPPET_LIST, snippetListState)
        outState.putParcelable(STATE_SHARE_LIST, shareListState)
    }

    override fun onPause() {
        captureCurrentListState()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (isInitialLoad) {
            isInitialLoad = false
            return
        }
        if (myId > 0) loadData()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val tokenManager = ApiClient.getTokenManager()
            val nickname = tokenManager.getNickname().first() ?: ""
            val username = tokenManager.getUsername().first() ?: ""
            val role = tokenManager.getUserRole().first() ?: "user"
            myId = tokenManager.getUserId().first() ?: 0

            if (!isFragmentUsable()) return@launch

            binding.tvUsername.text = nickname.ifEmpty { username }
            binding.tvHandle.text = "@$username"
            binding.chipRole.text = "· ${roleLabel(role)}"

            // 立即加载Tab内容（不等其他请求）
            loadContent()

            // 各请求独立launch，互不阻塞
            launch loadMe@{
                try {
                    val response = ApiClient.getService().getMe()
                    if (_binding == null) return@loadMe
                    if (response.isSuccessful) {
                        val user = response.body()?.get("user") ?: return@loadMe
                        binding.tvUsername.text = user.displayName
                        binding.tvHandle.text = "@${user.username}"
                        ApiClient.getTokenManager().updateNickname(user.displayName)
                        binding.chipRole.text = "· ${roleLabel(user.role)}"
                        if (!user.bio.isNullOrEmpty()) {
                            binding.tvBio.text = user.bio.orEmpty()
                            binding.tvBio.visibility = View.VISIBLE
                        } else {
                            binding.tvBio.visibility = View.GONE
                        }
                        if (!user.avatarUrl.isNullOrEmpty()) {
                            Glide.with(this@UserProfileFragment)
                                .load(user.avatarUrl.orEmpty())
                                .circleCrop()
                                .placeholder(R.drawable.ic_account_circle)
                                .into(binding.ivAvatar)
                        }
                        user.createdAt?.let {
                            ApiClient.getTokenManager().updateCreatedAt(it)
                            binding.tvJoinDate.text = "注册于 ${TimeUtil.formatRelative(it)}"
                            binding.tvJoinDate.visibility = View.VISIBLE
                        }
                    }
                } catch (_: Exception) { }
            }

            if (myId > 0) launch loadStats@{
                try {
                    val response = ApiClient.getService().getPublicProfile(myId)
                    if (_binding == null) return@loadStats
                    if (response.isSuccessful) {
                        val pStats = response.body()?.stats ?: return@loadStats
                        binding.tvFollowingCount.text = "${pStats.following}"
                        binding.tvFollowersCount.text = "${pStats.followers}"
                        binding.tvDiscussionCount.text = "${pStats.discussions}"
                        binding.tvSnippetCount.text = "${pStats.snippets}"
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private fun loadContent() {
        if (_binding == null || myId <= 0) return
        binding.progressContent.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (currentTab) {
                    0 -> loadDiscussions()
                    1 -> loadSnippets()
                    2 -> loadShares()
                }
            } catch (_: Exception) {
                if (_binding != null) {
                    binding.progressContent.visibility = View.GONE
                    binding.tvEmpty.text = "加载失败"
                    binding.tvEmpty.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun loadDiscussions() {
        val response = ApiClient.getService().getUserDiscussions(myId)
        if (_binding == null) return
        binding.progressContent.visibility = View.GONE
        if (response.isSuccessful) {
            val list = response.body()?.discussions ?: emptyList()
            if (list.isEmpty()) {
                binding.tvEmpty.text = "暂无讨论"
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvContent.adapter = null
            } else {
                binding.tvEmpty.visibility = View.GONE
                val navOptions = androidx.navigation.NavOptions.Builder()
                    .setEnterAnim(R.anim.slide_in_right).setExitAnim(R.anim.slide_out_left)
                    .setPopEnterAnim(R.anim.slide_in_left).setPopExitAnim(R.anim.slide_out_right).build()
                binding.rvContent.adapter = ProfileContentAdapter<Discussion>(list, ProfileContentAdapter.TYPE_DISCUSSION) { item ->
                    val bundle = Bundle().apply {
                        putLong("discussion_id", item.id)
                        putString("prefill_title", item.title)
                        putString("prefill_author_name", item.authorName)
                        putString("prefill_category_name", item.categoryName)
                        putString("prefill_created_at", item.createdAt)
                    }
                    findNavController().navigate(R.id.discussionDetailFragment, bundle, navOptions)
                }
                restoreListStateForTab(0)
            }
        } else {
            binding.rvContent.adapter = null
            binding.tvEmpty.text = "加载讨论失败"
            binding.tvEmpty.visibility = View.VISIBLE
        }
    }

    private suspend fun loadSnippets() {
        val response = ApiClient.getService().getUserSnippets(myId)
        if (_binding == null) return
        binding.progressContent.visibility = View.GONE
        if (response.isSuccessful) {
            val list = response.body()?.snippets ?: emptyList()
            if (list.isEmpty()) {
                binding.tvEmpty.text = "暂无代码片段"
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvContent.adapter = null
            } else {
                binding.tvEmpty.visibility = View.GONE
                val navOptions = androidx.navigation.NavOptions.Builder()
                    .setEnterAnim(R.anim.slide_in_right).setExitAnim(R.anim.slide_out_left)
                    .setPopEnterAnim(R.anim.slide_in_left).setPopExitAnim(R.anim.slide_out_right).build()
                binding.rvContent.adapter = ProfileContentAdapter<Snippet>(list, ProfileContentAdapter.TYPE_SNIPPET) { item ->
                    val bundle = Bundle().apply { putLong("snippet_id", item.id) }
                    findNavController().navigate(R.id.snippetDetailFragment, bundle, navOptions)
                }
                restoreListStateForTab(1)
            }
        } else {
            binding.rvContent.adapter = null
            binding.tvEmpty.text = "加载代码片段失败"
            binding.tvEmpty.visibility = View.VISIBLE
        }
    }

    private suspend fun loadShares() {
        val response = ApiClient.getService().getUserShares(myId)
        if (_binding == null) return
        binding.progressContent.visibility = View.GONE
        if (response.isSuccessful) {
            val list = response.body()?.shares ?: emptyList()
            if (list.isEmpty()) {
                binding.tvEmpty.text = "暂无分享"
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvContent.adapter = null
            } else {
                binding.tvEmpty.visibility = View.GONE
                val navOptions = androidx.navigation.NavOptions.Builder()
                    .setEnterAnim(R.anim.slide_in_right).setExitAnim(R.anim.slide_out_left)
                    .setPopEnterAnim(R.anim.slide_in_left).setPopExitAnim(R.anim.slide_out_right).build()
                binding.rvContent.adapter = ProfileContentAdapter<Share>(list, ProfileContentAdapter.TYPE_SHARE) { item ->
                    val bundle = Bundle().apply { putLong("share_id", item.id) }
                    findNavController().navigate(R.id.shareDetailFragment, bundle, navOptions)
                }
                restoreListStateForTab(2)
            }
        } else {
            binding.rvContent.adapter = null
            binding.tvEmpty.text = "加载分享失败"
            binding.tvEmpty.visibility = View.VISIBLE
        }
    }

    private fun captureCurrentListState() {
        val currentBinding = _binding ?: return
        val state = currentBinding.rvContent.layoutManager?.onSaveInstanceState()
        when (currentTab) {
            0 -> discussionListState = state
            1 -> snippetListState = state
            2 -> shareListState = state
        }
    }

    private fun restoreListStateForTab(tab: Int) {
        val pendingState = when (tab) {
            0 -> discussionListState
            1 -> snippetListState
            2 -> shareListState
            else -> null
        } ?: return
        binding.rvContent.post {
            binding.rvContent.layoutManager?.onRestoreInstanceState(pendingState)
            when (tab) {
                0 -> discussionListState = null
                1 -> snippetListState = null
                2 -> shareListState = null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
