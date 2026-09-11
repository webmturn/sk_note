package com.sknote.app.ui.manage.user

import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.User
import com.sknote.app.databinding.FragmentUserManageBinding
import com.sknote.app.util.requireRolesOrExit
import kotlinx.coroutines.flow.first

class UserManageFragment : Fragment() {

    private var _binding: FragmentUserManageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserManageViewModel by viewModels()
    private lateinit var adapter: UserManageAdapter
    private var currentUsername: String? = null
    private var currentQuery: String = ""
    private var listState: Parcelable? = null

    companion object {
        private const val STATE_QUERY = "state_query"
        private const val STATE_LIST = "state_list"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        savedInstanceState?.let {
            currentQuery = it.getString(STATE_QUERY).orEmpty()
            listState = it.getParcelable(STATE_LIST)
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            if (!requireRolesOrExit(setOf("admin"), "仅管理员可管理用户")) {
                return@launch
            }
            setupManageUi()
        }
    }

    private suspend fun setupManageUi() {

        adapter = UserManageAdapter { user ->
            showUserMenu(user)
        }
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = adapter
        if (currentQuery.isNotEmpty()) {
            binding.etSearch.setText(currentQuery)
            binding.etSearch.setSelection(currentQuery.length)
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearch.text?.toString()?.trim()
                viewModel.loadUsers(force = true, search = query?.ifEmpty { null })
                true
            } else false
        }

        binding.swipeRefresh.setOnRefreshListener {
            val query = binding.etSearch.text?.toString()?.trim()
            viewModel.loadUsers(force = true, search = query?.ifEmpty { null })
        }

        // 必须在建 adapter 之前取到自身标识：异步填充会让 isSelf() 在首帧恒为 false，
        // 从而给自己那行错误地显示"删除用户"（后端 auth.ts 已有护栏，这里只保证 UI 正确）
        currentUsername = ApiClient.getTokenManager().getUsername().first()

        observeData()
        viewModel.loadUsers(search = currentQuery.ifEmpty { null })
    }

    private fun observeData() {
        viewModel.users.observe(viewLifecycleOwner) { users ->
            adapter.submitList(users) {
                val pendingListState = listState
                if (pendingListState != null) {
                    binding.rvUsers.layoutManager?.onRestoreInstanceState(pendingListState)
                    listState = null
                }
            }
            binding.tvEmpty.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    private fun isSelf(user: User): Boolean = user.username == currentUsername

    private fun showUserMenu(user: User) {
        val isSelfUser = isSelf(user)
        // Dialog 而非 PopupMenu：锚点 btnMore 在列表复用时会被回收，且弹窗期间一直持有该 View
        val actions = buildList {
            add("设为管理员" to { confirmRoleChange(user, "admin") })
            add("设为编辑" to { confirmRoleChange(user, "editor") })
            add("设为普通用户" to { confirmRoleChange(user, "user") })
            add("重置密码" to { showResetPasswordDialog(user) })
            if (!isSelfUser) {
                add("删除用户" to { showDeleteConfirm(user) })
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(user.username)
            .setItems(actions.map { it.first }.toTypedArray()) { _, index ->
                actions[index].second()
            }
            .show()
    }

    private fun confirmRoleChange(user: User, newRole: String) {
        if (isSelf(user) && newRole != "admin") {
            Snackbar.make(binding.root, "不能降级自己的角色", Snackbar.LENGTH_SHORT).show()
            return
        }
        val roleName = when (newRole) {
            "admin" -> "管理员"
            "editor" -> "编辑"
            else -> "普通用户"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("更改角色")
            .setMessage("确定将用户「${user.username}」的角色设为「$roleName」吗？")
            .setPositiveButton("确定") { _, _ -> viewModel.updateRole(user.id, newRole) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showResetPasswordDialog(user: User) {
        val density = resources.displayMetrics.density
        val corner = 12f * density
        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = "新密码（至少 6 位）"
            setBoxCornerRadii(corner, corner, corner, corner)
            setPadding(dp(24), dp(16), dp(24), 0)
        }
        val input = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        inputLayout.addView(input)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("重置密码")
            .setMessage("为用户「${user.username}」设置新密码")
            .setView(inputLayout)
            .setPositiveButton("重置", null)
            .setNegativeButton("取消", null)
            .create()
            .also { dialog ->
                // 用字段级 error 而非 Snackbar：校验不通过时不关闭对话框，用户可直接修正
                dialog.setOnShowListener {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                        val pwd = input.text.toString().trim()
                        if (pwd.length < 6) {
                            inputLayout.error = "密码至少 6 位"
                        } else {
                            inputLayout.error = null
                            viewModel.resetPassword(user.id, pwd)
                            dialog.dismiss()
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showDeleteConfirm(user: User) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除用户")
            .setMessage("确定删除用户「${user.username}」吗？\n将同时删除该用户的所有讨论、评论、代码片段等数据。\n此操作不可撤销！")
            .setPositiveButton("删除") { _, _ -> viewModel.deleteUser(user.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun captureUiState() {
        val currentBinding = _binding ?: return
        currentQuery = currentBinding.etSearch.text?.toString().orEmpty()
        listState = currentBinding.rvUsers.layoutManager?.onSaveInstanceState()
    }

    override fun onPause() {
        captureUiState()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureUiState()
        super.onSaveInstanceState(outState)
        outState.putString(STATE_QUERY, currentQuery)
        outState.putParcelable(STATE_LIST, listState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
