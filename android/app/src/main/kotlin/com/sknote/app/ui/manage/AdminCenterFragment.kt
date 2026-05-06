package com.sknote.app.ui.manage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.sknote.app.R
import com.sknote.app.databinding.FragmentAdminCenterBinding
import com.sknote.app.util.requireRolesOrExit
import com.sknote.app.util.slideNavOptions
import kotlinx.coroutines.launch

class AdminCenterFragment : Fragment() {

    private var _binding: FragmentAdminCenterBinding? = null
    private val binding get() = _binding!!
    private var currentScrollY = 0

    companion object {
        private const val STATE_SCROLL_Y = "state_scroll_y"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminCenterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y) ?: currentScrollY

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            if (!requireRolesOrExit(setOf("admin"), "仅管理员可进入管理中心")) {
                return@launch
            }
            setupAdminEntries()
            restoreUiState()
        }
    }

    override fun onPause() {
        captureUiState()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureUiState()
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SCROLL_Y, currentScrollY)
    }

    private fun setupAdminEntries() {
        binding.cardUsers.setOnClickListener {
            findNavController().navigate(R.id.userManageFragment, null, slideNavOptions())
        }

        binding.cardCategories.setOnClickListener {
            findNavController().navigate(R.id.categoryManageFragment, null, slideNavOptions())
        }

        binding.cardArticles.setOnClickListener {
            findNavController().navigate(R.id.articleManageFragment, null, slideNavOptions())
        }

        binding.cardDiscussions.setOnClickListener {
            findNavController().navigate(R.id.discussionManageFragment, null, slideNavOptions())
        }

        binding.cardSnippets.setOnClickListener {
            findNavController().navigate(R.id.snippetManageFragment, null, slideNavOptions())
        }

        binding.cardShares.setOnClickListener {
            findNavController().navigate(R.id.shareManageFragment, null, slideNavOptions())
        }

        binding.cardReleases.setOnClickListener {
            findNavController().navigate(R.id.releaseManageFragment, null, slideNavOptions())
        }
    }

    private fun captureUiState() {
        val currentBinding = _binding ?: return
        currentScrollY = currentBinding.root.scrollY
    }

    private fun restoreUiState() {
        if (currentScrollY == 0) return
        val currentBinding = _binding ?: return
        val targetScrollY = currentScrollY
        currentBinding.root.post {
            currentBinding.root.scrollTo(0, targetScrollY)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
