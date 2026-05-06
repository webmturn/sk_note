package com.sknote.app.ui.manage.release

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
import com.sknote.app.data.model.AppRelease
import com.sknote.app.databinding.FragmentReleaseManageBinding
import com.sknote.app.util.requireRolesOrExit
import com.sknote.app.util.slideNavOptions
import kotlinx.coroutines.launch

class ReleaseManageFragment : Fragment() {

    private var _binding: FragmentReleaseManageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReleaseManageViewModel by viewModels()
    private lateinit var adapter: ReleaseManageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReleaseManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            if (!requireRolesOrExit(setOf("admin"), "仅管理员可发布版本")) {
                return@launch
            }
            setupManageUi()
        }
    }

    private fun setupManageUi() {
        adapter = ReleaseManageAdapter(
            onDelete = { release -> showDeleteConfirm(release) },
            onToggleActive = { release -> showToggleActiveConfirm(release) }
        )
        binding.rvReleases.layoutManager = LinearLayoutManager(context)
        binding.rvReleases.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadReleases()
        }

        binding.fabPublish.setOnClickListener {
            findNavController().navigate(R.id.releaseEditorFragment, null, slideNavOptions())
        }

        val navHandle = findNavController().currentBackStackEntry?.savedStateHandle
        navHandle?.getLiveData<Boolean>("refresh_releases")
            ?.observe(viewLifecycleOwner) { shouldRefresh ->
                if (shouldRefresh == true) {
                    viewModel.loadReleases()
                    navHandle.remove<Boolean>("refresh_releases")
                }
            }

        observeData()
        viewModel.loadReleases()
    }

    private fun observeData() {
        viewModel.releases.observe(viewLifecycleOwner) { releases ->
            adapter.submitList(releases)
            binding.tvEmpty.visibility = if (releases.isEmpty()) View.VISIBLE else View.GONE
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

    private fun showDeleteConfirm(release: AppRelease) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除版本")
            .setMessage("确定删除 v${release.versionName} 吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ -> viewModel.deleteRelease(release.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showToggleActiveConfirm(release: AppRelease) {
        val newActive = release.isActive != 1
        val title = if (newActive) "上架版本" else "下架版本"
        val message = if (newActive) {
            "上架后客户端将能检测到 v${release.versionName} 的更新提示。"
        } else {
            "下架后客户端不再收到 v${release.versionName} 的更新提示，记录保留以便回滚。"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定") { _, _ -> viewModel.setReleaseActive(release.id, newActive) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
