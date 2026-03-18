package com.sknote.app.ui.manage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.sknote.app.R
import com.sknote.app.databinding.FragmentAdminCenterBinding

class AdminCenterFragment : Fragment() {

    private var _binding: FragmentAdminCenterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminCenterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val navOptions = androidx.navigation.NavOptions.Builder()
            .setEnterAnim(R.anim.slide_in_right)
            .setExitAnim(R.anim.slide_out_left)
            .setPopEnterAnim(R.anim.slide_in_left)
            .setPopExitAnim(R.anim.slide_out_right)
            .build()

        binding.cardUsers.setOnClickListener {
            findNavController().navigate(R.id.userManageFragment, null, navOptions)
        }

        binding.cardCategories.setOnClickListener {
            findNavController().navigate(R.id.categoryManageFragment, null, navOptions)
        }

        binding.cardArticles.setOnClickListener {
            findNavController().navigate(R.id.articleManageFragment, null, navOptions)
        }

        binding.cardDiscussions.setOnClickListener {
            findNavController().navigate(R.id.discussionManageFragment, null, navOptions)
        }

        binding.cardSnippets.setOnClickListener {
            findNavController().navigate(R.id.snippetManageFragment, null, navOptions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
