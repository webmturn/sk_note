package com.sknote.app.ui.bookmark

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.sknote.app.R
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.BookmarkItem
import com.sknote.app.databinding.FragmentBookmarkListBinding
import com.sknote.app.util.ErrorUtil
import com.sknote.app.util.TimeUtil
import com.sknote.app.databinding.ItemArticleSimpleBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class BookmarkListFragment : Fragment() {

    private var _binding: FragmentBookmarkListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: BookmarkAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBookmarkListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = BookmarkAdapter { item ->
            val bundle = Bundle().apply { putLong("article_id", item.articleId) }
            findNavController().navigate(R.id.articleDetailFragment, bundle)
        }
        binding.rvBookmarks.layoutManager = LinearLayoutManager(context)
        binding.rvBookmarks.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadBookmarks() }
        binding.errorState.btnRetry.setOnClickListener { loadBookmarks() }
        loadBookmarks()
    }

    private fun loadBookmarks() {
        binding.swipeRefresh.isRefreshing = true
        binding.errorState.layoutError.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getService().getBookmarks(limit = 50)
                if (response.isSuccessful) {
                    val list = response.body()?.bookmarks ?: emptyList()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class BookmarkAdapter(
    private val onClick: (BookmarkItem) -> Unit
) : ListAdapter<BookmarkItem, BookmarkAdapter.VH>(object : DiffUtil.ItemCallback<BookmarkItem>() {
    override fun areItemsTheSame(a: BookmarkItem, b: BookmarkItem) = a.id == b.id
    override fun areContentsTheSame(a: BookmarkItem, b: BookmarkItem) = a == b
}) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemArticleSimpleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemArticleSimpleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BookmarkItem) {
            binding.tvTitle.text = item.title
            binding.tvSummary.text = item.summary
            binding.tvAuthor.text = item.authorName ?: ""
            binding.tvViews.text = "${item.viewCount} 阅读"
            binding.tvTimestamp.text = TimeUtil.formatRelative(item.bookmarkedAt)
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
