package com.sknote.app.ui.profile

import android.os.Bundle
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
import com.sknote.app.databinding.FragmentPublicProfileBinding
import com.sknote.app.util.TimeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PublicProfileFragment : Fragment() {

    private var _binding: FragmentPublicProfileBinding? = null
    private val binding get() = _binding!!
    private var userId: Long = 0
    private var isFollowing = false
    private var currentTab = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPublicProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userId = arguments?.getLong("user_id", 0) ?: 0
        if (userId == 0L) {
            findNavController().popBackStack()
            return
        }

        val navOptions = androidx.navigation.NavOptions.Builder()
            .setEnterAnim(R.anim.slide_in_right)
            .setExitAnim(R.anim.slide_out_left)
            .setPopEnterAnim(R.anim.slide_in_left)
            .setPopExitAnim(R.anim.slide_out_right)
            .build()

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val navHandle = findNavController().currentBackStackEntry?.savedStateHandle
        navHandle?.getLiveData<Boolean>("refresh_shares")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    loadProfile()
                    loadContent()
                    navHandle.remove<Boolean>("refresh_shares")
                }
            }

        navHandle?.getLiveData<Boolean>("refresh_snippets")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    loadProfile()
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

        binding.rvContent.layoutManager = LinearLayoutManager(context)

        binding.btnFollow.setOnClickListener { toggleFollow() }

        binding.layoutFollowing.setOnClickListener {
            val bundle = Bundle().apply {
                putLong("user_id", userId)
                putInt("tab", 0)
            }
            findNavController().navigate(R.id.followListFragment, bundle, navOptions)
        }

        binding.layoutFollowers.setOnClickListener {
            val bundle = Bundle().apply {
                putLong("user_id", userId)
                putInt("tab", 1)
            }
            findNavController().navigate(R.id.followListFragment, bundle, navOptions)
        }

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("讨论"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("代码片段"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("分享"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                loadContent()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadProfile()
        checkFollowStatus()
        loadContent()
    }

    private fun loadProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getPublicProfile(userId)
                if (_binding == null) return@launch
                if (response.isSuccessful) {
                    val data = response.body() ?: return@launch
                    binding.tvUsername.text = data.user.displayName
                    binding.tvHandle.text = "@${data.user.username}"
                    binding.chipRole.text = when (data.user.role) {
                        "admin" -> "管理员"
                        "editor" -> "编辑"
                        else -> "用户"
                    }
                    binding.toolbar.title = data.user.displayName

                    if (!data.user.bio.isNullOrEmpty()) {
                        binding.tvBio.text = data.user.bio.orEmpty()
                        binding.tvBio.visibility = View.VISIBLE
                    } else {
                        binding.tvBio.visibility = View.GONE
                    }

                    if (!data.user.avatarUrl.isNullOrEmpty()) {
                        Glide.with(this@PublicProfileFragment)
                            .load(data.user.avatarUrl.orEmpty())
                            .circleCrop()
                            .placeholder(R.drawable.ic_account_circle)
                            .into(binding.ivAvatar)
                    }

                    data.user.createdAt?.let {
                        binding.tvJoinDate.text = "注册于 ${TimeUtil.formatRelative(it)}"
                        binding.tvJoinDate.visibility = View.VISIBLE
                    }

                    binding.tvFollowingCount.text = "${data.stats.following}"
                    binding.tvFollowersCount.text = "${data.stats.followers}"
                    binding.tvDiscussionCount.text = "${data.stats.discussions}"
                    binding.tvSnippetCount.text = "${data.stats.snippets}"
                }
            } catch (e: Exception) {
                if (_binding != null) {
                    Snackbar.make(binding.root, "加载失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkFollowStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val loggedIn = ApiClient.getTokenManager().isLoggedIn().first()
                if (_binding == null) return@launch
                if (!loggedIn) {
                    binding.btnFollow.visibility = View.GONE
                    return@launch
                }

                val myId = ApiClient.getTokenManager().getUserId().first() ?: 0
                if (_binding == null) return@launch
                if (myId == userId) {
                    binding.btnFollow.visibility = View.GONE
                    return@launch
                }

                val response = ApiClient.getService().checkFollow(userId)
                if (_binding == null) return@launch
                if (response.isSuccessful) {
                    isFollowing = response.body()?.following ?: false
                    updateFollowButton()
                }
            } catch (_: Exception) { }
        }
    }

    private fun toggleFollow() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val loggedIn = ApiClient.getTokenManager().isLoggedIn().first()
                if (_binding == null) return@launch
                if (!loggedIn) {
                    Snackbar.make(binding.root, "请先登录", Snackbar.LENGTH_SHORT)
                        .setAction("去登录") { findNavController().navigate(R.id.loginFragment) }
                        .show()
                    return@launch
                }

                binding.btnFollow.isEnabled = false
                val response = ApiClient.getService().toggleFollow(userId)
                if (_binding == null) return@launch
                if (response.isSuccessful) {
                    isFollowing = response.body()?.following ?: !isFollowing
                    updateFollowButton()
                    loadProfile()
                } else {
                    Snackbar.make(binding.root, "操作失败: ${response.message()}", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (_binding != null) {
                    Snackbar.make(binding.root, "操作失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            } finally {
                if (_binding != null) binding.btnFollow.isEnabled = true
            }
        }
    }

    private fun updateFollowButton() {
        if (isFollowing) {
            binding.btnFollow.text = "已关注"
            binding.btnFollow.setIconResource(R.drawable.ic_check)
            val outlineColor = com.google.android.material.color.MaterialColors.getColor(
                requireContext(), com.google.android.material.R.attr.colorOutline, 0
            )
            binding.btnFollow.strokeColor = android.content.res.ColorStateList.valueOf(outlineColor)
            binding.btnFollow.strokeWidth = (1 * resources.displayMetrics.density).toInt()
            val surfaceColor = com.google.android.material.color.MaterialColors.getColor(
                requireContext(), com.google.android.material.R.attr.colorSurface, 0
            )
            binding.btnFollow.setBackgroundColor(surfaceColor)
            val onSurfaceColor = com.google.android.material.color.MaterialColors.getColor(
                requireContext(), com.google.android.material.R.attr.colorOnSurface, 0
            )
            binding.btnFollow.setTextColor(onSurfaceColor)
            binding.btnFollow.iconTint = android.content.res.ColorStateList.valueOf(onSurfaceColor)
        } else {
            binding.btnFollow.text = "关注"
            binding.btnFollow.setIconResource(R.drawable.ic_person_add)
            binding.btnFollow.strokeWidth = 0
            val primaryColor = com.google.android.material.color.MaterialColors.getColor(
                requireContext(), com.google.android.material.R.attr.colorPrimary, 0
            )
            binding.btnFollow.setBackgroundColor(primaryColor)
            val onPrimaryColor = com.google.android.material.color.MaterialColors.getColor(
                requireContext(), com.google.android.material.R.attr.colorOnPrimary, 0
            )
            binding.btnFollow.setTextColor(onPrimaryColor)
            binding.btnFollow.iconTint = android.content.res.ColorStateList.valueOf(onPrimaryColor)
        }
    }

    private fun loadContent() {
        binding.progressContent.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (currentTab) {
                    0 -> loadDiscussions()
                    1 -> loadSnippets()
                    2 -> loadShares()
                }
            } catch (e: Exception) {
                if (_binding != null) {
                    binding.progressContent.visibility = View.GONE
                    binding.tvEmpty.text = "加载失败"
                    binding.tvEmpty.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun loadDiscussions() {
        val response = ApiClient.getService().getUserDiscussions(userId)
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
                val adapter = ProfileContentAdapter<Discussion>(list, ProfileContentAdapter.TYPE_DISCUSSION) { item ->
                    val bundle = Bundle().apply { putLong("discussion_id", (item as Discussion).id) }
                    findNavController().navigate(R.id.discussionDetailFragment, bundle)
                }
                binding.rvContent.adapter = adapter
            }
        } else {
            binding.rvContent.adapter = null
            binding.tvEmpty.text = "加载讨论失败"
            binding.tvEmpty.visibility = View.VISIBLE
        }
    }

    private suspend fun loadSnippets() {
        val response = ApiClient.getService().getUserSnippets(userId)
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
                val adapter = ProfileContentAdapter<Snippet>(list, ProfileContentAdapter.TYPE_SNIPPET) { item ->
                    val bundle = Bundle().apply { putLong("snippet_id", (item as Snippet).id) }
                    findNavController().navigate(R.id.snippetDetailFragment, bundle)
                }
                binding.rvContent.adapter = adapter
            }
        } else {
            binding.rvContent.adapter = null
            binding.tvEmpty.text = "加载代码片段失败"
            binding.tvEmpty.visibility = View.VISIBLE
        }
    }

    private suspend fun loadShares() {
        val response = ApiClient.getService().getUserShares(userId)
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
                val adapter = ProfileContentAdapter<Share>(list, ProfileContentAdapter.TYPE_SHARE) { item ->
                    val bundle = Bundle().apply { putLong("share_id", (item as Share).id) }
                    findNavController().navigate(R.id.shareDetailFragment, bundle)
                }
                binding.rvContent.adapter = adapter
            }
        } else {
            binding.rvContent.adapter = null
            binding.tvEmpty.text = "加载分享失败"
            binding.tvEmpty.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
