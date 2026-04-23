package com.sknote.app.ui.profile

import android.os.Bundle
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.sknote.app.SkNoteApp
import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.local.TokenManager
import com.sknote.app.databinding.FragmentThemeSelectionBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ThemeSelectionFragment : Fragment() {

    private var _binding: FragmentThemeSelectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentThemeSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        // Shape filter
        val appPrefs = requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        binding.switchShapeFilter.isChecked = appPrefs.getBoolean("shape_filter_enabled", false)
        binding.switchShapeFilter.setOnCheckedChangeListener { _, isChecked ->
            appPrefs.edit().putBoolean("shape_filter_enabled", isChecked).apply()
        }

        // Load current theme and update UI
        viewLifecycleOwner.lifecycleScope.launch {
            val currentMode = ApiClient.getTokenManager().getThemeMode().first()
            updateSelection(currentMode)
        }

        binding.cardSystem.setOnClickListener { selectTheme(TokenManager.THEME_SYSTEM) }
        binding.cardLight.setOnClickListener { selectTheme(TokenManager.THEME_LIGHT) }
        binding.cardDark.setOnClickListener { selectTheme(TokenManager.THEME_DARK) }
    }

    private fun selectTheme(mode: String) {
        SkNoteApp.applyThemeMode(requireActivity().application, mode)
        updateSelection(mode)

        viewLifecycleOwner.lifecycleScope.launch {
            ApiClient.getTokenManager().setThemeMode(mode)
        }
    }

    private fun updateSelection(mode: String) {
        val isSystem = mode == TokenManager.THEME_SYSTEM
        val isLight = mode == TokenManager.THEME_LIGHT
        val isDark = mode == TokenManager.THEME_DARK

        binding.checkSystem.visibility = if (isSystem) View.VISIBLE else View.GONE
        binding.checkLight.visibility = if (isLight) View.VISIBLE else View.GONE
        binding.checkDark.visibility = if (isDark) View.VISIBLE else View.GONE

        updateCardStroke(binding.cardSystem, isSystem)
        updateCardStroke(binding.cardLight, isLight)
        updateCardStroke(binding.cardDark, isDark)

        // Update status text
        val label = SkNoteApp.themeModeLabel(mode)
        binding.tvCurrentTheme.text = if (isSystem) {
            val effectiveMode = if (isSystemInDarkMode()) "深色" else "浅色"
            "当前主题：$label（当前生效：$effectiveMode）"
        } else {
            "当前主题：$label"
        }
    }

    private fun updateCardStroke(card: com.google.android.material.card.MaterialCardView, selected: Boolean) {
        val context = card.context
        if (selected) {
            val color = com.google.android.material.color.MaterialColors.getColor(
                context, com.google.android.material.R.attr.colorPrimary, 0
            )
            card.strokeColor = color
            card.strokeWidth = (3 * resources.displayMetrics.density).toInt()
        } else {
            val color = com.google.android.material.color.MaterialColors.getColor(
                context, com.google.android.material.R.attr.colorOutlineVariant, 0
            )
            card.strokeColor = color
            card.strokeWidth = (2 * resources.displayMetrics.density).toInt()
        }
    }

    private fun isSystemInDarkMode(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
