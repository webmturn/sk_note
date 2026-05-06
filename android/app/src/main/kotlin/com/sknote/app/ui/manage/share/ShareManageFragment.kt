package com.sknote.app.ui.manage.share

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Share
import com.sknote.app.databinding.FragmentShareManageBinding
import com.sknote.app.util.requireRolesOrExit
import com.sknote.app.util.slideNavOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShareManageFragment : Fragment() {

    private var _binding: FragmentShareManageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ShareManageViewModel by viewModels()
    private lateinit var adapter: ShareManageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShareManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            if (!requireRolesOrExit(setOf("admin"), "仅管理员可管理分享")) {
                return@launch
            }
            setupManageUi()
        }
    }

    private fun setupManageUi() {
        adapter = ShareManageAdapter(
            onView = { share ->
                val bundle = Bundle().apply { putLong("share_id", share.id) }
                findNavController().navigate(R.id.shareDetailFragment, bundle, slideNavOptions())
            },
            onApprove = { share -> showApproveConfirm(share) },
            onDelete = { share -> showDeleteConfirm(share) }
        )
        binding.rvShares.layoutManager = LinearLayoutManager(context)
        binding.rvShares.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            val tokenManager = ApiClient.getTokenManager()
            val userId = tokenManager.getUserId().first() ?: -1L
            val role = tokenManager.getUserRole().first() ?: "user"
            if (_binding == null) return@launch
            adapter.updateUserContext(userId, role)
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadShares()
        }

        binding.tabFilter.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                val status = when (tab.position) {
                    1 -> ShareManageViewModel.FilterStatus.PENDING
                    2 -> ShareManageViewModel.FilterStatus.APPROVED
                    else -> ShareManageViewModel.FilterStatus.ALL
                }
                viewModel.setFilter(status)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        observeData()
        viewModel.loadShares()
    }

    private fun observeData() {
        viewModel.shares.observe(viewLifecycleOwner) { shares ->
            adapter.submitList(shares)
            binding.tvEmpty.visibility = if (shares.isEmpty()) View.VISIBLE else View.GONE
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

    private fun showApproveConfirm(share: Share) {
        val approve = share.isApproved != 1
        val title = if (approve) "批准分享" else "取消批准"
        val message = if (approve) "确认批准「${share.title}」公开展示？" else "取消批准后将从公开列表隐藏。"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定") { _, _ -> viewModel.approveShare(share.id, approve) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteConfirm(share: Share) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除分享")
            .setMessage("确定删除「${share.title}」吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ -> viewModel.deleteShare(share.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
