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
import com.google.android.material.tabs.TabLayout
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.databinding.FragmentFollowListBinding
import kotlinx.coroutines.launch

class FollowListFragment : Fragment() {

    private var _binding: FragmentFollowListBinding? = null
    private val binding get() = _binding!!
    private var userId: Long = 0
    private var currentTab = 0
    private lateinit var adapter: FollowUserAdapter
    private var followingListState: Parcelable? = null
    private var followerListState: Parcelable? = null

    companion object {
        private const val STATE_CURRENT_TAB = "state_current_tab"
        private const val STATE_FOLLOWING_LIST = "state_following_list"
        private const val STATE_FOLLOWER_LIST = "state_follower_list"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFollowListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userId = arguments?.getLong("user_id", 0) ?: 0
        followingListState = savedInstanceState?.getParcelable(STATE_FOLLOWING_LIST) ?: followingListState
        followerListState = savedInstanceState?.getParcelable(STATE_FOLLOWER_LIST) ?: followerListState
        currentTab = savedInstanceState?.getInt(STATE_CURRENT_TAB)
            ?: arguments?.getInt("tab", 0)
            ?: 0

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

        adapter = FollowUserAdapter { user ->
            val bundle = Bundle().apply { putLong("user_id", user.id) }
            findNavController().navigate(R.id.publicProfileFragment, bundle, navOptions)
        }

        binding.rvUsers.layoutManager = LinearLayoutManager(context)
        binding.rvUsers.adapter = adapter

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("关注"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("粉丝"))

        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(currentTab))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                captureCurrentListState()
                currentTab = tab?.position ?: 0
                loadData()
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
        outState.putParcelable(STATE_FOLLOWING_LIST, followingListState)
        outState.putParcelable(STATE_FOLLOWER_LIST, followerListState)
    }

    override fun onPause() {
        captureCurrentListState()
        super.onPause()
    }

    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = if (currentTab == 0) {
                    ApiClient.getService().getFollowing(userId)
                } else {
                    ApiClient.getService().getFollowers(userId)
                }

                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val users = response.body()?.users ?: emptyList()
                    adapter.submitList(users) {
                        restoreListStateForCurrentTab()
                    }
                    binding.tvEmpty.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvEmpty.text = if (currentTab == 0) "暂无关注" else "暂无粉丝"
                } else {
                    adapter.submitList(emptyList())
                    binding.tvEmpty.text = "加载失败: ${response.message()}"
                    binding.tvEmpty.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                if (_binding != null) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.text = "加载失败"
                    binding.tvEmpty.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun captureCurrentListState() {
        val currentBinding = _binding ?: return
        val state = currentBinding.rvUsers.layoutManager?.onSaveInstanceState()
        if (currentTab == 0) {
            followingListState = state
        } else {
            followerListState = state
        }
    }

    private fun restoreListStateForCurrentTab() {
        val pendingState = (if (currentTab == 0) followingListState else followerListState) ?: return
        binding.rvUsers.post {
            binding.rvUsers.layoutManager?.onRestoreInstanceState(pendingState)
            if (currentTab == 0) {
                followingListState = null
            } else {
                followerListState = null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
