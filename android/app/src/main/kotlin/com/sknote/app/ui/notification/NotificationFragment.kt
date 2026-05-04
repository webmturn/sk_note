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
import com.sknote.app.util.slideNavOptions
import com.sknote.app.util.fadeIn
import com.sknote.app.util.fadeOut

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
                    true
                }
                R.id.action_delete_all -> {
                    confirmDeleteAllNotifications()
                    true
                }
                else -> false
            }
        }

        binding.btnReadAll.setOnClickListener {
            viewModel.markAllRead()
        }

        binding.btnDeleteAll.setOnClickListener {
            confirmDeleteAllNotifications()
        }

        adapter = NotificationAdapter(
            onClick = { notification ->
                handleNotificationClick(notification)
            },
            onDeleteClick = { notification ->
                confirmDeleteNotification(notification)
            },
            onLongClick = { notification ->
                confirmDeleteNotification(notification)
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
            if (list.isEmpty()) { binding.layoutEmpty.fadeIn(); binding.layoutActionBar.fadeOut() }
            else { binding.layoutEmpty.fadeOut(); binding.layoutActionBar.fadeIn() }
            updateQuickActionState()
        }

        viewModel.unreadCount.observe(viewLifecycleOwner) {
            updateQuickActionState()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { binding.swipeRefresh.isRefreshing = it }

        viewModel.isActionLoading.observe(viewLifecycleOwner) {
            updateQuickActionState()
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.tvError.text = error
                binding.layoutError.fadeIn()
            } else {
                binding.layoutError.fadeOut()
            }
        }

        viewModel.actionEvent.observe(viewLifecycleOwner) { event ->
            event ?: return@observe
            Snackbar.make(
                binding.root,
                event.message,
                if (event.isError) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
            ).show()
            viewModel.onActionEventHandled()
        }

        binding.btnRetry.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            viewModel.loadNotifications()
        }
    }

    private fun updateQuickActionState() {
        val notifications = viewModel.notifications.value.orEmpty()
        val unreadCount = viewModel.unreadCount.value ?: 0
        val actionLoading = viewModel.isActionLoading.value == true
        val hasItems = notifications.isNotEmpty()

        binding.layoutActionBar.visibility = if (hasItems) View.VISIBLE else View.GONE
        binding.tvUnreadSummary.text = if (unreadCount > 0) {
            "还有 $unreadCount 条未读通知"
        } else {
            "全部通知已读"
        }
        binding.btnReadAll.isEnabled = hasItems && unreadCount > 0 && !actionLoading
        binding.btnDeleteAll.isEnabled = hasItems && !actionLoading
    }

    private fun handleNotificationClick(notification: com.sknote.app.data.model.Notification) {
        if (notification.isRead == 0) {
            viewModel.markRead(notification.id)
        }

        val relatedId = notification.relatedId
        when (notification.relatedType) {
            "discussion" -> if (relatedId != null) {
                val bundle = Bundle().apply { putLong("discussion_id", relatedId) }
                findNavController().navigate(R.id.discussionDetailFragment, bundle, slideNavOptions())
            }
            "article" -> if (relatedId != null) {
                val bundle = Bundle().apply { putLong("article_id", relatedId) }
                findNavController().navigate(R.id.articleDetailFragment, bundle, slideNavOptions())
            }
            "share" -> if (relatedId != null) {
                val bundle = Bundle().apply { putLong("share_id", relatedId) }
                findNavController().navigate(R.id.shareDetailFragment, bundle, slideNavOptions())
            }
            "snippet" -> if (relatedId != null) {
                val bundle = Bundle().apply { putLong("snippet_id", relatedId) }
                findNavController().navigate(R.id.snippetDetailFragment, bundle, slideNavOptions())
            }
            "user" -> if (relatedId != null) {
                val bundle = Bundle().apply { putLong("user_id", relatedId) }
                findNavController().navigate(R.id.publicProfileFragment, bundle, slideNavOptions())
            }
        }
    }

    private fun confirmDeleteNotification(notification: com.sknote.app.data.model.Notification) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除通知")
            .setMessage("确定删除这条通知吗？")
            .setPositiveButton("删除") { _, _ -> viewModel.deleteNotification(notification.id) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteAllNotifications() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除全部通知")
            .setMessage("确定删除所有通知吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteAllNotifications()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
