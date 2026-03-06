package com.sknote.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.User
import com.sknote.app.databinding.FragmentUserManageBinding
import kotlinx.coroutines.flow.first

class UserManageFragment : Fragment() {

    private var _binding: FragmentUserManageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserManageViewModel by viewModels()
    private lateinit var adapter: UserManageAdapter
    private var currentUsername: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = UserManageAdapter { user, anchorView ->
            showUserMenu(user, anchorView)
        }
        binding.rvUsers.layoutManager = LinearLayoutManager(context)
        binding.rvUsers.adapter = adapter

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

        viewLifecycleOwner.lifecycleScope.launch {
            currentUsername = ApiClient.getTokenManager().getUsername().first()
        }

        observeData()
        viewModel.loadUsers()
    }

    private fun observeData() {
        viewModel.users.observe(viewLifecycleOwner) { users ->
            adapter.submitList(users)
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

    private fun showUserMenu(user: User, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menu.add(0, 1, 0, "设为管理员")
        popup.menu.add(0, 2, 0, "设为编辑")
        popup.menu.add(0, 3, 0, "设为普通用户")
        popup.menu.add(0, 4, 0, "重置密码")
        if (!isSelf(user)) {
            popup.menu.add(0, 5, 0, "删除用户")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> confirmRoleChange(user, "admin")
                2 -> confirmRoleChange(user, "editor")
                3 -> confirmRoleChange(user, "user")
                4 -> showResetPasswordDialog(user)
                5 -> showDeleteConfirm(user)
            }
            true
        }
        popup.show()
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
        val input = EditText(requireContext()).apply {
            hint = "输入新密码（至少6位）"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 32, 48, 16)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("重置密码")
            .setMessage("为用户「${user.username}」设置新密码")
            .setView(input)
            .setPositiveButton("重置") { _, _ ->
                val pwd = input.text.toString().trim()
                if (pwd.length < 6) {
                    Snackbar.make(binding.root, "密码至少6位", Snackbar.LENGTH_SHORT).show()
                } else {
                    viewModel.resetPassword(user.id, pwd)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteConfirm(user: User) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除用户")
            .setMessage("确定删除用户「${user.username}」吗？\n将同时删除该用户的所有讨论、评论、代码片段等数据。\n此操作不可撤销！")
            .setPositiveButton("删除") { _, _ -> viewModel.deleteUser(user.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
