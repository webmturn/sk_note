package com.sknote.app.ui.discussion

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.sknote.app.R
import com.sknote.app.databinding.FragmentPaletteDetailBinding
import org.json.JSONArray
import org.json.JSONObject

class PaletteDetailFragment : Fragment() {

    private var _binding: FragmentPaletteDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPaletteDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val jsonStr = arguments?.getString("palette_json", "") ?: ""
        if (jsonStr.isEmpty()) {
            findNavController().navigateUp()
            return
        }

        val paletteJson: JSONObject
        try {
            paletteJson = JSONObject(jsonStr)
        } catch (_: Exception) {
            findNavController().navigateUp()
            return
        }

        val palName = paletteJson.optString("palette_name", "调色板")
        val palColor = paletteJson.optString("palette_color", "#9E9E9E")
        val blocksArr = paletteJson.optJSONArray("blocks") ?: JSONArray()

        // Header
        binding.toolbar.title = palName
        binding.tvPaletteName.text = palName
        binding.tvBlockCount.text = "${blocksArr.length()} 个积木块"
        try { binding.paletteColorBar.setBackgroundColor(Color.parseColor(palColor)) }
        catch (_: Exception) { binding.paletteColorBar.setBackgroundColor(Color.GRAY) }

        // Import all button
        binding.btnImportAll.setOnClickListener {
            BlockShareHelper.importPalettePublic(requireContext(), paletteJson, binding.root)
        }

        // Add all block cards
        val container = binding.blockCardsContainer
        container.removeAllViews()
        for (i in 0 until blocksArr.length()) {
            val block = blocksArr.optJSONObject(i) ?: continue
            val card = BlockShareHelper.createPreviewView(requireContext(), container, block, showActions = true)

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (12 * resources.displayMetrics.density).toInt()
            card.layoutParams = lp

            container.addView(card)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
