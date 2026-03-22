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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val navOptions = androidx.navigation.NavOptions.Builder()
            .setEnterAnim(R.anim.slide_in_right)
            .setExitAnim(R.anim.slide_out_left)
            .setPopEnterAnim(R.anim.slide_in_left)
            .setPopExitAnim(R.anim.slide_out_right)
            .build()

        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.profileEditFragment, null, navOptions)
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

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                loadContent()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadData()
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

            binding.tvUsername.text = nickname.ifEmpty { username }
            binding.tvHandle.text = "@$username"
            binding.chipRole.text = if (role == "admin") "管理员" else "普通用户"

            // 立即加载Tab内容（不等其他请求）
            loadContent()

            // 各请求独立launch，互不阻塞
            launch {
                try {
                    val response = ApiClient.getService().getMe()
                    if (_binding == null) return@launch
                    if (response.isSuccessful) {
                        val user = response.body()?.get("user") ?: return@launch
                        binding.tvUsername.text = user.displayName
                        binding.tvHandle.text = "@${user.username}"
                        ApiClient.getTokenManager().updateNickname(user.displayName)
                        binding.chipRole.text = if (user.role == "admin") "管理员" else "普通用户"
                        if (user.bio.isNotEmpty()) {
                            binding.tvBio.text = user.bio
                            binding.tvBio.visibility = View.VISIBLE
                        } else {
                            binding.tvBio.visibility = View.GONE
                        }
                        if (user.avatarUrl.isNotEmpty()) {
                            Glide.with(this@UserProfileFragment)
                                .load(user.avatarUrl)
                                .circleCrop()
                                .placeholder(R.drawable.ic_account_circle)
                                .into(binding.ivAvatar)
                        }
                        user.createdAt?.let {
                            binding.tvJoinDate.text = "注册于 ${TimeUtil.formatRelative(it)}"
                            binding.tvJoinDate.visibility = View.VISIBLE
                        }
                    }
                } catch (_: Exception) { }
            }

            if (myId > 0) launch {
                try {
                    val response = ApiClient.getService().getPublicProfile(myId)
                    if (_binding == null) return@launch
                    if (response.isSuccessful) {
                        val pStats = response.body()?.stats ?: return@launch
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
                    val bundle = Bundle().apply { putLong("discussion_id", (item as Discussion).id) }
                    findNavController().navigate(R.id.discussionDetailFragment, bundle, navOptions)
                }
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
                    val bundle = Bundle().apply { putLong("snippet_id", (item as Snippet).id) }
                    findNavController().navigate(R.id.snippetDetailFragment, bundle, navOptions)
                }
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
                    val bundle = Bundle().apply { putLong("share_id", (item as Share).id) }
                    findNavController().navigate(R.id.shareDetailFragment, bundle, navOptions)
                }
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
