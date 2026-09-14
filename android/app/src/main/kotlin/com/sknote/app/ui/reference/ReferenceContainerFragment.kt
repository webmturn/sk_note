package com.sknote.app.ui.reference

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.sknote.app.R
import com.sknote.app.databinding.FragmentReferenceContainerBinding
import com.sknote.app.ui.snippet.SnippetListFragment
import com.sknote.app.util.slideNavOptions

/**
 * 「学习资源」容器：标题行 + [参考手册 / 代码片段] 两个 tab。
 *
 * 搜索入口的现状（第三十三批改的）：
 * - 旧的两个入口都删了 —— 参考手册页那条 56dp 的假搜索框（点了跳页）、
 *   代码片段页的行内搜索框；
 * - 现在只有标题行右端一个图标，行为按当前 tab 分：
 *   · 参考手册 tab → 跳 `referenceSearchFragment`（与旧假框同一目的地，内容不变）
 *   · 代码片段 tab → 本行原地展开成输入框，输入即过滤片段列表（走 [LearningSearchViewModel]）
 * - 为什么两个 tab 行为不同：两边的搜索本来就**不是一回事** —— 参考手册是 510 条纯本地数据、
 *   搜索页带历史与热门标签；片段是网络接口、今天就是「输入即过滤列表」。合并入口、保留各自
 *   熟悉的行为，比强行做成一个覆盖层更稳。
 */
class ReferenceContainerFragment : Fragment() {

    private var _binding: FragmentReferenceContainerBinding? = null
    private val binding get() = _binding!!

    private val searchViewModel: LearningSearchViewModel by viewModels()

    /** 展开态是否处于激活。用来避免重复注册返回拦截、也用于 tab 切换时判断要不要收起。 */
    private var searchExpanded = false
    private var backCallback: OnBackPressedCallback? = null

    private val searchDebounce = Handler(Looper.getMainLooper())
    private val searchDebounceRunnable = Runnable { pushQuery() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReferenceContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ReferencePagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 1

        val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView
        recyclerView?.overScrollMode = View.OVER_SCROLL_NEVER
        recyclerView?.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_DEFAULT)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = "参考手册"
                    tab.setIcon(R.drawable.ic_nav_reference)
                }
                1 -> {
                    tab.text = "代码片段"
                    tab.setIcon(R.drawable.ic_nav_code)
                }
            }
        }.attach()

        setupSearchEntry()
    }

    /**
     * 搜索入口。收起态只有一个图标；展开态是同一行换成 [btnSearchBack] + 输入框 + 清除。
     * 展开态只服务代码片段 tab —— 参考手册 tab 点图标是跳页，不展开。
     */
    private fun setupSearchEntry() {
        binding.btnSearchToggle.setOnClickListener {
            if (binding.viewPager.currentItem == 1) {
                expandSearch()
            } else {
                findNavController().navigate(R.id.referenceSearchFragment, null, slideNavOptions())
            }
        }

        // 收起但保留输入：用户点返回箭头多半是想看看列表，不是想清掉关键词
        binding.btnSearchBack.setOnClickListener { collapseSearch(clearQuery = false) }

        binding.btnSearchClear.setOnClickListener {
            binding.etHeaderSearch.setText("")
            searchViewModel.setQuery("")
        }

        binding.etHeaderSearch.doAfterTextChanged { text ->
            val value = text?.toString().orEmpty()
            binding.btnSearchClear.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
            // 输入即过滤，但压 300ms：每敲一个字都发一次请求没必要
            searchDebounce.removeCallbacks(searchDebounceRunnable)
            searchDebounce.postDelayed(searchDebounceRunnable, 300)
        }

        binding.etHeaderSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchDebounce.removeCallbacks(searchDebounceRunnable)
                pushQuery()
                hideKeyboard()
                true
            } else false
        }

        // tab 切换时收起并清空：片段的关键词只属于片段 tab，带到参考手册页会让列表莫名其妙变短
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (searchExpanded) collapseSearch(clearQuery = true)
                binding.etHeaderSearch.hint =
                    if (position == 1) "搜索代码片段..." else "搜索参考内容..."
            }
        })
    }

    private fun pushQuery() {
        if (_binding == null) return
        searchViewModel.setQuery(binding.etHeaderSearch.text?.toString()?.trim().orEmpty())
    }

    private fun expandSearch() {
        if (searchExpanded) return
        searchExpanded = true
        binding.layoutHeaderTitle.visibility = View.GONE
        binding.layoutHeaderSearch.visibility = View.VISIBLE
        val restored = searchViewModel.query.value.orEmpty()
        binding.etHeaderSearch.setText(restored)
        binding.etHeaderSearch.setSelection(binding.etHeaderSearch.text?.length ?: 0)
        binding.btnSearchClear.visibility = if (restored.isEmpty()) View.GONE else View.VISIBLE
        binding.etHeaderSearch.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etHeaderSearch, InputMethodManager.SHOW_IMPLICIT)

        // 展开时先收键盘、再退出页面：免得一按返回就把整页关掉
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                collapseSearch(clearQuery = false)
            }
        }
        backCallback = callback
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun collapseSearch(clearQuery: Boolean) {
        if (!searchExpanded) return
        searchExpanded = false
        hideKeyboard()
        searchDebounce.removeCallbacks(searchDebounceRunnable)
        binding.layoutHeaderSearch.visibility = View.GONE
        binding.layoutHeaderTitle.visibility = View.VISIBLE
        binding.etHeaderSearch.clearFocus()
        backCallback?.let {
            it.isEnabled = false
            it.remove()
        }
        backCallback = null
        if (clearQuery) {
            binding.etHeaderSearch.setText("")
            binding.btnSearchClear.visibility = View.GONE
            searchViewModel.setQuery("")
        } else {
            // 收起但不清词：把已输入的内容落定，列表保持在过滤结果上
            pushQuery()
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etHeaderSearch.windowToken, 0)
    }

    override fun onDestroyView() {
        searchDebounce.removeCallbacks(searchDebounceRunnable)
        backCallback?.remove()
        backCallback = null
        searchExpanded = false
        binding.viewPager.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private inner class ReferencePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount() = 2
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> ReferenceFragment()
            1 -> SnippetListFragment()
            else -> ReferenceFragment()
        }
    }
}
