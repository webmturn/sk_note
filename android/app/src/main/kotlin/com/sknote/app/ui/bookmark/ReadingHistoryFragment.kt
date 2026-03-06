package com.sknote.app.ui.bookmark

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.HistoryItem
import com.sknote.app.databinding.FragmentReadingHistoryBinding
import com.sknote.app.databinding.ItemArticleSimpleBinding
import com.sknote.app.util.ErrorUtil
import com.sknote.app.util.TimeUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ReadingHistoryFragment : Fragment() {

    private var _binding: FragmentReadingHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReadingHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_clear) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("清空阅读历史")
                    .setMessage("确定清空所有阅读记录吗？")
                    .setPositiveButton("清空") { _, _ -> clearHistory() }
                    .setNegativeButton("取消", null)
                    .show()
                true
            } else false
        }

        adapter = HistoryAdapter { item ->
            val bundle = Bundle().apply { putLong("article_id", item.articleId) }
            findNavController().navigate(R.id.articleDetailFragment, bundle)
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(context)
        binding.rvHistory.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadHistory() }
        binding.errorState.btnRetry.setOnClickListener { loadHistory() }
        loadHistory()
    }

    private fun loadHistory() {
        binding.swipeRefresh.isRefreshing = true
        binding.errorState.layoutError.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getReadingHistory(limit = 50)
                if (response.isSuccessful) {
                    val list = response.body()?.history ?: emptyList()
                    adapter.submitList(list)
                    binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                binding.errorState.tvError.text = ErrorUtil.friendlyMessage(e)
                binding.errorState.layoutError.visibility = View.VISIBLE
            }
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun clearHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().clearHistory()
                if (response.isSuccessful) {
                    adapter.submitList(emptyList())
                    binding.layoutEmpty.visibility = View.VISIBLE
                    Snackbar.make(binding.root, "已清空", Snackbar.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Snackbar.make(binding.root, "操作失败", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class HistoryAdapter(
    private val onClick: (HistoryItem) -> Unit
) : ListAdapter<HistoryItem, HistoryAdapter.VH>(object : DiffUtil.ItemCallback<HistoryItem>() {
    override fun areItemsTheSame(a: HistoryItem, b: HistoryItem) = a.id == b.id
    override fun areContentsTheSame(a: HistoryItem, b: HistoryItem) = a == b
}) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemArticleSimpleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemArticleSimpleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryItem) {
            binding.tvTitle.text = item.title
            binding.tvSummary.text = item.summary
            binding.tvAuthor.text = item.authorName ?: ""
            binding.tvViews.text = "${item.viewCount} 阅读"
            binding.tvTimestamp.text = TimeUtil.formatRelative(item.readAt)
            if (!item.categoryName.isNullOrEmpty()) {
                binding.tvCategory.text = item.categoryName
                binding.tvCategory.visibility = View.VISIBLE
            } else {
                binding.tvCategory.visibility = View.GONE
            }
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
