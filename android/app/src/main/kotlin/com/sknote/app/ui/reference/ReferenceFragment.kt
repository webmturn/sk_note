package com.sknote.app.ui.reference

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.sknote.app.R
import com.sknote.app.databinding.FragmentReferenceBinding

class ReferenceFragment : Fragment() {

    private var _binding: FragmentReferenceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReferenceViewModel by viewModels()
    private lateinit var adapter: ReferenceAdapter
    private var currentType: String? = null
    private var currentCategory: String? = null
    private var currentShape: String? = null
    private var showingBookmarks = false
    private var statsExpanded = false
    private val bookmarkPrefs by lazy {
        requireContext().getSharedPreferences("reference_bookmarks", Context.MODE_PRIVATE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReferenceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ReferenceAdapter(onClick = { item ->
            val bundle = Bundle().apply { putLong("reference_id", item.id) }
            Navigation.findNavController(requireParentFragment().requireView()).navigate(R.id.referenceDetailFragment, bundle)
        })

        binding.rvReferences.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ReferenceFragment.adapter
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadReferences(currentType, currentCategory)
        }

        binding.layoutSearchBar.setOnClickListener {
            Navigation.findNavController(requireParentFragment().requireView()).navigate(R.id.referenceSearchFragment)
        }

        ReferenceData.init(requireContext())
        setupChipFilters()
        updateChipCounts()
        setupScrollToTop()
        setupStatsPanel()
        setupSortButton()
        setupGuideButton()
        observeData()
        updateSubtitle()

        // 恢复 chip 选中状态（返回时 fragment 实例保留 currentType）
        if (showingBookmarks) {
            binding.chipBookmark.isChecked = true
            loadBookmarks()
        } else {
            when (currentType) {
                "block" -> binding.chipBlock.isChecked = true
                "component" -> binding.chipComponent.isChecked = true
                "widget" -> binding.chipWidget.isChecked = true
                "event" -> binding.chipEvent.isChecked = true
                else -> binding.chipAll.isChecked = true
            }
            if (currentType != null) {
                updateSubCategories(currentType)
            }
            viewModel.loadReferences(currentType, currentCategory)
        }
    }

    private fun updateChipCounts() {
        val bmCount = getBookmarkedIds().size
        binding.chipAll.text = "全部 ${ReferenceData.getItemCount()}"
        binding.chipBlock.text = "积木块 ${ReferenceData.getItemCount("block")}"
        binding.chipComponent.text = "组件 ${ReferenceData.getItemCount("component")}"
        binding.chipWidget.text = "控件 ${ReferenceData.getItemCount("widget")}"
        binding.chipEvent.text = "事件 ${ReferenceData.getItemCount("event")}"
        binding.chipBookmark.text = "收藏 $bmCount"
        adapter.updateBookmarks(getBookmarkedIds())
    }

    private fun updateSubtitle() {
        val total = ReferenceData.getItemCount()
        val bm = getBookmarkedIds().size
        binding.tvSubtitle.text = "共 $total 项参考 · 已收藏 $bm 项"
    }

    private fun setupStatsPanel() {
        binding.tvStatTotal.text = ReferenceData.getItemCount().toString()
        binding.tvStatBlocks.text = ReferenceData.getItemCount("block").toString()
        binding.tvStatComps.text = ReferenceData.getItemCount("component").toString()
        binding.tvStatWidgets.text = ReferenceData.getItemCount("widget").toString()
        binding.tvStatEvents.text = ReferenceData.getItemCount("event").toString()

        if (statsExpanded) binding.cardStats.visibility = View.VISIBLE

        binding.btnToggleStats.setOnClickListener {
            statsExpanded = !statsExpanded
            if (statsExpanded) {
                binding.cardStats.visibility = View.VISIBLE
                binding.cardStats.alpha = 0f
                binding.cardStats.animate().alpha(1f).setDuration(200).start()
            } else {
                binding.cardStats.animate().alpha(0f).setDuration(200).withEndAction {
                    binding.cardStats.visibility = View.GONE
                }.start()
            }
        }
    }

    private fun setupGuideButton() {
        binding.btnGuide.setOnClickListener {
            BlockGuideDialog().show(childFragmentManager, "guide")
        }
    }

    private fun setupSortButton() {
        binding.btnSort.setOnClickListener { v ->
            val popup = PopupMenu(requireContext(), v)
            popup.menu.add(0, 0, 0, "默认排序")
            popup.menu.add(0, 1, 1, "按名称 A→Z")
            popup.menu.add(0, 2, 2, "按名称 Z→A")
            popup.menu.add(0, 3, 3, "按分类")
            popup.menu.add(0, 4, 4, "按ID ↑")
            popup.menu.add(0, 5, 5, "按ID ↓")
            popup.menu.add(0, 6, 6, "按颜色分组")
            // Mark current
            val currentId = when (viewModel.sortMode) {
                SortMode.DEFAULT -> 0; SortMode.NAME_ASC -> 1; SortMode.NAME_DESC -> 2
                SortMode.CATEGORY -> 3; SortMode.ID_ASC -> 4; SortMode.ID_DESC -> 5; SortMode.COLOR -> 6
            }
            popup.menu.getItem(currentId)?.let {
                it.title = "✓ ${it.title}"
            }
            popup.setOnMenuItemClickListener { item ->
                viewModel.sortMode = when (item.itemId) {
                    1 -> SortMode.NAME_ASC
                    2 -> SortMode.NAME_DESC
                    3 -> SortMode.CATEGORY
                    4 -> SortMode.ID_ASC
                    5 -> SortMode.ID_DESC
                    6 -> SortMode.COLOR
                    else -> SortMode.DEFAULT
                }
                if (showingBookmarks) {
                    loadBookmarks()
                } else {
                    viewModel.loadReferences(currentType, currentCategory)
                }
                true
            }
            popup.show()
        }
    }

    private fun getBookmarkedIds(): Set<Long> {
        return bookmarkPrefs.getStringSet("ids", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    private fun loadBookmarks() {
        val ids = getBookmarkedIds()
        var items = ReferenceData.getBookmarkedItems(ids)
        items = when (viewModel.sortMode) {
            SortMode.NAME_ASC -> items.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> items.sortedByDescending { it.name.lowercase() }
            SortMode.CATEGORY -> items.sortedWith(compareBy({ it.category }, { it.name.lowercase() }))
            SortMode.ID_ASC -> items.sortedBy { it.id }
            SortMode.ID_DESC -> items.sortedByDescending { it.id }
            SortMode.COLOR -> items.sortedWith(compareBy({ it.color }, { it.name.lowercase() }))
            SortMode.DEFAULT -> items
        }
        adapter.submitList(items)
        binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.tvEmpty.text = if (items.isEmpty()) "还没有收藏任何参考" else ""
    }

    private fun setupScrollToTop() {
        binding.rvReferences.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val show = lm.findFirstVisibleItemPosition() > 3
                binding.fabScrollTop.visibility = if (show) View.VISIBLE else View.GONE
            }
        })
        binding.fabScrollTop.setOnClickListener {
            binding.rvReferences.smoothScrollToPosition(0)
        }
    }

    private fun setupChipFilters() {
        val chipMap = mapOf(
            binding.chipAll to null,
            binding.chipBlock to "block",
            binding.chipComponent to "component",
            binding.chipWidget to "widget",
            binding.chipEvent to "event"
        )
        chipMap.forEach { (chip, type) ->
            chip.setOnClickListener {
                showingBookmarks = false
                currentType = type
                currentCategory = null
                currentShape = null
                viewModel.shapeFilter = null
                updateSubCategories(type)
                viewModel.loadReferences(type)
            }
        }
        binding.chipBookmark.setOnClickListener {
            showingBookmarks = true
            currentType = null
            currentCategory = null
            binding.subChipScroll.visibility = View.GONE
            binding.subChipGroup.removeAllViews()
            binding.shapeChipScroll.visibility = View.GONE
            binding.shapeChipGroup.removeAllViews()
            loadBookmarks()
        }
    }

    private fun updateSubCategories(type: String?) {
        binding.subChipGroup.removeAllViews()
        if (type == null) {
            binding.subChipScroll.visibility = View.GONE
            return
        }
        val subs = ReferenceData.subCategories[type]
        if (subs.isNullOrEmpty()) {
            binding.subChipScroll.visibility = View.GONE
            return
        }
        binding.subChipScroll.visibility = View.VISIBLE

        val totalCount = ReferenceData.getByType(type).size
        val allChip = Chip(requireContext()).apply {
            text = "全部 $totalCount"
            isCheckable = true
            isChecked = currentCategory == null
            isCloseIconVisible = false
            isChipIconVisible = false
            setOnClickListener {
                currentCategory = null
                currentShape = null
                viewModel.shapeFilter = null
                updateShapeChips(currentType)
                viewModel.loadReferences(currentType)
            }
        }
        binding.subChipGroup.addView(allChip)

        subs.forEach { cat ->
            val catCount = ReferenceData.getByTypeAndCategory(type, cat).size
            val chip = Chip(requireContext()).apply {
                text = "$cat $catCount"
                isCheckable = true
                isChecked = currentCategory == cat
                isCloseIconVisible = false
                isChipIconVisible = false
                setOnClickListener {
                    currentCategory = cat
                    currentShape = null
                    viewModel.shapeFilter = null
                    binding.shapeChipScroll.visibility = View.GONE
                    binding.shapeChipGroup.removeAllViews()
                    viewModel.loadReferences(currentType, cat)
                }
            }
            binding.subChipGroup.addView(chip)
        }

        // Update shape filter row
        updateShapeChips(type)
    }

    private fun updateShapeChips(type: String?) {
        binding.shapeChipGroup.removeAllViews()
        val shapeEnabled = requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
            .getBoolean("shape_filter_enabled", false)
        if (!shapeEnabled || type != "block" || currentCategory != null) {
            binding.shapeChipScroll.visibility = View.GONE
            return
        }
        val shapes = ReferenceData.getShapesForType(type)
        if (shapes.size <= 1) {
            binding.shapeChipScroll.visibility = View.GONE
            return
        }
        binding.shapeChipScroll.visibility = View.VISIBLE
        shapes.forEach { shape ->
            val label = ReferenceData.shapeLabels[shape] ?: shape
            val shapeItems = ReferenceData.getByShape(ReferenceData.getByType(type), shape)
            val shapeChip = Chip(requireContext()).apply {
                text = "$label ${shapeItems.size}"
                isCheckable = true
                isChecked = currentShape == shape
                isCloseIconVisible = false
                isChipIconVisible = false
                setOnClickListener {
                    if (currentShape == shape) {
                        currentShape = null
                        viewModel.shapeFilter = null
                        isChecked = false
                    } else {
                        currentShape = shape
                        viewModel.shapeFilter = shape
                    }
                    viewModel.loadReferences(currentType, currentCategory)
                }
            }
            binding.shapeChipGroup.addView(shapeChip)
        }
    }

    private fun observeData() {
        viewModel.references.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty() && binding.layoutError.visibility == View.GONE) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) {
            binding.swipeRefresh.isRefreshing = it
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.layoutError.visibility = View.VISIBLE
                binding.tvError.text = error
                binding.tvEmpty.visibility = View.GONE
            } else {
                binding.layoutError.visibility = View.GONE
            }
        }

        binding.btnRetry.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            viewModel.loadReferences(currentType, currentCategory)
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            updateChipCounts()
            updateSubtitle()
            if (showingBookmarks) {
                loadBookmarks()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
