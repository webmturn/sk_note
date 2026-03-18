package com.sknote.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.databinding.FragmentUserProfileBinding
import com.sknote.app.util.TimeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UserProfileFragment : Fragment() {

    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!

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

        binding.rowBookmarks.setOnClickListener {
            findNavController().navigate(R.id.bookmarkListFragment, null, navOptions)
        }
        binding.rowHistory.setOnClickListener {
            findNavController().navigate(R.id.readingHistoryFragment, null, navOptions)
        }
        binding.rowEditProfile.setOnClickListener {
            findNavController().navigate(R.id.profileEditFragment, null, navOptions)
        }

        loadProfile()
        loadStats()
    }

    private fun loadProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            val tokenManager = ApiClient.getTokenManager()
            val username = tokenManager.getUsername().first() ?: ""
            val role = tokenManager.getUserRole().first() ?: "user"

            binding.tvUsername.text = username
            binding.tvHandle.text = "@$username"
            binding.chipRole.text = if (role == "admin") "管理员" else "普通用户"

            try {
                val response = ApiClient.getService().getMe()
                if (response.isSuccessful) {
                    val user = response.body()?.get("user") ?: return@launch
                    binding.tvUsername.text = user.username
                    binding.tvHandle.text = "@${user.username}"
                    binding.chipRole.text = if (user.role == "admin") "管理员" else "普通用户"
                    user.createdAt?.let {
                        binding.tvJoinDate.text = "注册于 ${TimeUtil.formatRelative(it)}"
                        binding.tvJoinDate.visibility = View.VISIBLE
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun loadStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getStats()
                if (response.isSuccessful) {
                    val stats = response.body() ?: return@launch
                    binding.tvStatArticles.text = "${stats.totalArticles}"
                    binding.tvStatDiscussions.text = "${stats.totalDiscussions}"
                    binding.tvStatNotifications.text = "${stats.unreadNotifications}"
                    binding.tvStatSnippets.text = "${stats.totalSnippets}"
                }
            } catch (_: Exception) { }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
