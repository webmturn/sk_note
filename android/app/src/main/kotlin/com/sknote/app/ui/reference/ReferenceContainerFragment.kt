package com.sknote.app.ui.reference

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.sknote.app.R
import com.sknote.app.databinding.FragmentReferenceContainerBinding
import com.sknote.app.ui.snippet.SnippetListFragment

class ReferenceContainerFragment : Fragment() {

    private var _binding: FragmentReferenceContainerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReferenceContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ReferencePagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 1

        // 减少 ViewPager2 灵敏度，避免与子 Fragment 水平滚动冲突
        val recyclerView = binding.viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        recyclerView?.overScrollMode = View.OVER_SCROLL_NEVER

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
    }

    override fun onDestroyView() {
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
