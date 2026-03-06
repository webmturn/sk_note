package com.sknote.app.ui.notification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.databinding.FragmentNotificationsBinding

class NotificationFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotificationViewModel by viewModels()
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.menu_notifications)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_read_all -> {
                    viewModel.markAllRead()
                    Snackbar.make(binding.root, "已全部标为已读", Snackbar.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        adapter = NotificationAdapter(
            onClick = { notification ->
                viewModel.markRead(notification.id)
                if (notification.relatedType == "discussion" && notification.relatedId != null) {
                    val bundle = Bundle().apply { putLong("discussion_id", notification.relatedId) }
                    findNavController().navigate(R.id.discussionDetailFragment, bundle)
                }
            },
            onLongClick = { notification ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("删除通知")
                    .setMessage("确定删除这条通知吗？")
                    .setPositiveButton("删除") { _, _ -> viewModel.deleteNotification(notification.id) }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )

        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@NotificationFragment.adapter
        }

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadNotifications() }

        observeData()
        viewModel.loadNotifications()
    }

    private fun observeData() {
        viewModel.notifications.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { binding.swipeRefresh.isRefreshing = it }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.layoutError.visibility = View.VISIBLE
                binding.tvError.text = error
            } else {
                binding.layoutError.visibility = View.GONE
            }
        }

        binding.btnRetry.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            viewModel.loadNotifications()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
