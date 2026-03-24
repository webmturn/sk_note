package com.sknote.app.ui.manage.discussioncategory
 
 import android.os.Bundle
 import android.view.LayoutInflater
 import android.view.View
 import android.view.ViewGroup
 import android.widget.FrameLayout
 import androidx.core.os.bundleOf
 import androidx.fragment.app.Fragment
 import androidx.navigation.fragment.findNavController
 import com.sknote.app.R
 import com.sknote.app.ui.manage.category.CategoryManageFragment
 
 class DiscussionCategoryManageFragment : Fragment() {
 
     override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
         return FrameLayout(requireContext())
     }
 
     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
         super.onViewCreated(view, savedInstanceState)
         val navController = findNavController()
         navController.popBackStack()
         navController.navigate(
             R.id.categoryManageFragment,
             bundleOf(CategoryManageFragment.ARG_INITIAL_TAB to 1)
         )
     }
 }
