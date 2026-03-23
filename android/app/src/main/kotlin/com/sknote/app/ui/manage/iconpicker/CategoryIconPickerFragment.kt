package com.sknote.app.ui.manage.iconpicker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.sknote.app.R
import com.sknote.app.databinding.FragmentCategoryIconPickerBinding
import com.sknote.app.util.CategoryIconCatalog

class CategoryIconPickerFragment : Fragment() {

    private var _binding: FragmentCategoryIconPickerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryIconPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.subtitle = "共 ${CategoryIconCatalog.options.size} 个图标"
        binding.rvIcons.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.rvIcons.adapter = CategoryIconPickerAdapter(
            options = CategoryIconCatalog.options,
            selectedKey = arguments?.getString(ARG_SELECTED_KEY).orEmpty(),
        ) { option ->
            findNavController().previousBackStackEntry?.savedStateHandle?.set(RESULT_SELECTED_ICON_KEY, option.key)
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_SELECTED_KEY = "selected_key"
        const val RESULT_SELECTED_ICON_KEY = "selected_icon_key"

        fun createArgs(selectedKey: String?): Bundle = bundleOf(ARG_SELECTED_KEY to selectedKey.orEmpty())
    }
}
