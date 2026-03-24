package com.sknote.app.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.BuildConfig
import com.sknote.app.SkNoteApp
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.local.TokenManager
import com.sknote.app.databinding.FragmentAdminBinding
import com.sknote.app.util.AppUpdateManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class AdminFragment : Fragment() {

    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!
    private var isInitialLoad = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navOptions = androidx.navigation.NavOptions.Builder()
            .setEnterAnim(R.anim.slide_in_right)
            .setExitAnim(R.anim.slide_out_left)
            .setPopEnterAnim(R.anim.slide_in_left)
            .setPopExitAnim(R.anim.slide_out_right)
            .build()

        binding.cardUserProfile.setOnClickListener {
            findNavController().navigate(R.id.userProfileFragment, null, navOptions)
        }

        binding.btnGoLogin.setOnClickListener {
            findNavController().navigate(R.id.loginFragment, null, navOptions)
        }

        binding.cardNotifications.setOnClickListener {
            findNavController().navigate(R.id.notificationFragment, null, navOptions)
        }

        binding.cardBookmarks.setOnClickListener {
            findNavController().navigate(R.id.bookmarkListFragment, null, navOptions)
        }

        binding.cardHistory.setOnClickListener {
            findNavController().navigate(R.id.readingHistoryFragment, null, navOptions)
        }

        binding.cardProfileEdit.setOnClickListener {
            findNavController().navigate(R.id.profileEditFragment, null, navOptions)
        }

        binding.cardShares.setOnClickListener {
            findNavController().navigate(R.id.shareListFragment, null, navOptions)
        }

        binding.rowAdminCenter.setOnClickListener {
            findNavController().navigate(R.id.adminCenterFragment, null, navOptions)
        }

        binding.rowSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment, null, navOptions)
        }

        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        ApiClient.getTokenManager().clearAuth()
                        Snackbar.make(binding.root, "已退出登录", Snackbar.LENGTH_SHORT).show()
                        refreshLoginState()
                    }
                }
                .show()
        }

        refreshLoginState()
    }

    override fun onResume() {
        super.onResume()
        if (isInitialLoad) {
            isInitialLoad = false
            return
        }
        refreshLoginState()
    }

    private fun refreshLoginState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            if (isLoggedIn) {
                val nickname = ApiClient.getTokenManager().getNickname().first() ?: ""
                val username = ApiClient.getTokenManager().getUsername().first() ?: ""
                val role = ApiClient.getTokenManager().getUserRole().first() ?: "user"

                binding.tvUsername.text = nickname.ifEmpty { username }
                binding.tvRole.text = if (role == "admin") "管理员" else "普通用户"

                // 先立即显示所有卡片（不等网络）
                val isAdmin = role == "admin"
                animateVisibility(binding.cardGuestLogin, false)
                animateVisibility(binding.cardUserProfile, true)
                animateVisibility(binding.tvFunctionHeader, true)
                animateVisibility(binding.cardFunctionGroup, true)
                animateVisibility(binding.cardLogout, true)
                animateVisibility(binding.tvAdminHeader, isAdmin)
                animateVisibility(binding.cardAdminGroup, isAdmin)

                loadStats()

                // 异步加载头像和最新用户名（不阻塞UI）
                launch {
                    try {
                        val response = ApiClient.getService().getMe()
                        if (response.isSuccessful && _binding != null) {
                            val user = response.body()?.get("user")
                            if (user != null) {
                                binding.tvUsername.text = user.displayName
                                ApiClient.getTokenManager().updateNickname(user.displayName)
                                ApiClient.getTokenManager().updateUserRole(user.role)
                                binding.tvRole.text = if (user.role == "admin") "管理员" else "普通用户"
                                val latestIsAdmin = user.role == "admin"
                                animateVisibility(binding.tvAdminHeader, latestIsAdmin)
                                animateVisibility(binding.cardAdminGroup, latestIsAdmin)
                                if (!user.avatarUrl.isNullOrEmpty()) {
                                    Glide.with(this@AdminFragment)
                                        .load(user.avatarUrl.orEmpty())
                                        .circleCrop()
                                        .placeholder(R.drawable.ic_account_circle)
                                        .into(binding.ivAvatar)
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
            } else {
                animateVisibility(binding.cardUserProfile, false)
                animateVisibility(binding.tvFunctionHeader, false)
                animateVisibility(binding.cardFunctionGroup, false)
                animateVisibility(binding.tvAdminHeader, false)
                animateVisibility(binding.cardAdminGroup, false)
                animateVisibility(binding.cardLogout, false)
                animateVisibility(binding.cardGuestLogin, true)
            }
        }
    }

    private fun animateVisibility(view: View, show: Boolean) {
        val target = if (show) View.VISIBLE else View.GONE
        if (view.visibility == target) return
        if (show) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate().alpha(1f).setDuration(200).start()
        } else {
            view.animate().alpha(0f).setDuration(150).withEndAction {
                view.visibility = View.GONE
            }.start()
        }
    }

    private fun loadStats() {
        // 先显示本地缓存的统计值（避免闪烁"0"）
        val prefs = requireContext().getSharedPreferences("stats_cache", android.content.Context.MODE_PRIVATE)
        val cachedArticles = prefs.getInt("articles", -1)
        if (cachedArticles >= 0) {
            binding.tvStatArticles.text = "${cachedArticles}"
            binding.tvStatDiscussions.text = "${prefs.getInt("discussions", 0)}"
            binding.tvStatNotifications.text = "${prefs.getInt("notifications", 0)}"
            val n = prefs.getInt("notifications", 0)
            binding.tvNotificationDesc.text = if (n > 0) "$n 条未读" else ""
        }

        // 后台刷新真实数据
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getStats()
                if (response.isSuccessful) {
                    val stats = response.body() ?: return@launch
                    binding.tvStatNotifications.text = "${stats.unreadNotifications}"
                    binding.tvNotificationDesc.text = if (stats.unreadNotifications > 0) "${stats.unreadNotifications} 条未读" else ""
                    binding.tvStatArticles.text = "${stats.totalArticles}"
                    binding.tvStatDiscussions.text = "${stats.totalDiscussions}"
                    // 缓存到本地
                    prefs.edit()
                        .putInt("articles", stats.totalArticles)
                        .putInt("discussions", stats.totalDiscussions)
                        .putInt("notifications", stats.unreadNotifications)
                        .apply()
                }
            } catch (_: Exception) { }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
