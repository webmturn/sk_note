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
import com.sknote.app.util.slideNavOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log
import java.io.File

private const val TAG = "FLICKER_DBG"

class AdminFragment : Fragment() {

    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!
    private var isInitialLoad = true
    private var isFirstRefresh = true
    private var currentScrollY = 0

    companion object {
        private const val STATE_SCROLL_Y = "state_scroll_y"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Log.d(TAG, "AdminFragment.onCreateView")
        isInitialLoad = true
        isFirstRefresh = true
        _binding = FragmentAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "AdminFragment.onViewCreated savedState=${savedInstanceState != null}")

        currentScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y) ?: currentScrollY

        binding.cardUserProfile.setOnClickListener {
            findNavController().navigate(R.id.userProfileFragment, null, slideNavOptions())
        }

        binding.btnGoLogin.setOnClickListener {
            findNavController().navigate(R.id.loginFragment, null, slideNavOptions())
        }

        binding.cardNotifications.setOnClickListener {
            findNavController().navigate(R.id.notificationFragment, null, slideNavOptions())
        }

        binding.cardBookmarks.setOnClickListener {
            findNavController().navigate(R.id.bookmarkListFragment, null, slideNavOptions())
        }

        binding.cardHistory.setOnClickListener {
            findNavController().navigate(R.id.readingHistoryFragment, null, slideNavOptions())
        }

        binding.cardProfileEdit.setOnClickListener {
            findNavController().navigate(R.id.profileEditFragment, null, slideNavOptions())
        }

        binding.cardShares.setOnClickListener {
            findNavController().navigate(R.id.shareListFragment, null, slideNavOptions())
        }

        binding.rowAdminCenter.setOnClickListener {
            findNavController().navigate(R.id.adminCenterFragment, null, slideNavOptions())
        }

        binding.rowSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment, null, slideNavOptions())
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

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "AdminFragment.onStart")
    }

    override fun onPause() {
        Log.d(TAG, "AdminFragment.onPause")
        captureUiState()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureUiState()
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SCROLL_Y, currentScrollY)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "AdminFragment.onResume isInitialLoad=$isInitialLoad")
        if (isInitialLoad) {
            isInitialLoad = false
            return
        }
        refreshLoginState()
    }

    private fun roleLabel(role: String?): String {
        return when (role) {
            "admin" -> "管理员"
            "editor" -> "编辑"
            else -> "普通用户"
        }
    }

    private fun renderAvatar(avatarUrl: String?) {
        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_account_circle)
                .into(binding.ivAvatar)
        } else {
            Glide.with(this).clear(binding.ivAvatar)
            binding.ivAvatar.setImageResource(R.drawable.ic_account_circle)
        }
    }

    private fun refreshLoginState() {
        Log.d(TAG, "AdminFragment.refreshLoginState START isFirstRefresh=$isFirstRefresh")
        viewLifecycleOwner.lifecycleScope.launch {
            val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            val animate = !isFirstRefresh
            isFirstRefresh = false
            Log.d(TAG, "AdminFragment.refreshLoginState isLoggedIn=$isLoggedIn animate=$animate")
            if (isLoggedIn) {
                val nickname = ApiClient.getTokenManager().getNickname().first() ?: ""
                val username = ApiClient.getTokenManager().getUsername().first() ?: ""
                val role = ApiClient.getTokenManager().getUserRole().first() ?: "user"
                val avatarUrl = ApiClient.getTokenManager().getAvatarUrl().first()

                binding.tvUsername.text = nickname.ifEmpty { username }
                binding.tvRole.text = roleLabel(role)
                renderAvatar(avatarUrl)

                // 先立即显示所有卡片（不等网络）
                val isAdmin = role == "admin"
                setVisibility(binding.cardGuestLogin, false, animate)
                setVisibility(binding.cardUserProfile, true, animate)
                setVisibility(binding.cardFunctionGroup, true, animate)
                setVisibility(binding.cardLogout, true, animate)
                setVisibility(binding.cardAdminGroup, isAdmin, animate)

                loadStats()
                restoreUiState()

                // 异步加载头像和最新用户名（不阻塞UI）
                launch {
                    try {
                        val response = ApiClient.getService().getMe()
                        if (response.isSuccessful && _binding != null) {
                            val user = response.body()?.get("user")
                            if (user != null) {
                                binding.tvUsername.text = user.displayName
                                ApiClient.getTokenManager().updateCurrentUser(user.id, user.username, user.displayName, user.role, user.avatarUrl)
                                binding.tvRole.text = roleLabel(user.role)
                                val latestIsAdmin = user.role == "admin"
                                setVisibility(binding.cardAdminGroup, latestIsAdmin, true)
                                renderAvatar(user.avatarUrl)
                                restoreUiState()
                            }
                        }
                    } catch (_: Exception) { }
                }
            } else {
                renderAvatar(null)
                setVisibility(binding.cardUserProfile, false, animate)
                setVisibility(binding.cardFunctionGroup, false, animate)
                setVisibility(binding.cardAdminGroup, false, animate)
                setVisibility(binding.cardLogout, false, animate)
                setVisibility(binding.cardGuestLogin, true, animate)
                restoreUiState()
            }
        }
    }

    private fun captureUiState() {
        val currentBinding = _binding ?: return
        currentScrollY = currentBinding.root.scrollY
    }

    private fun restoreUiState() {
        if (currentScrollY == 0) return
        val currentBinding = _binding ?: return
        val targetScrollY = currentScrollY
        currentBinding.root.post {
            currentBinding.root.scrollTo(0, targetScrollY)
        }
    }

    private fun setVisibility(view: View, show: Boolean, animate: Boolean) {
        val target = if (show) View.VISIBLE else View.GONE
        if (view.visibility == target) return
        if (!animate) {
            view.alpha = 1f
            view.visibility = target
            return
        }
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

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "AdminFragment.onStop")
    }

    override fun onDestroyView() {
        Log.d(TAG, "AdminFragment.onDestroyView")
        super.onDestroyView()
        _binding = null
    }
}
