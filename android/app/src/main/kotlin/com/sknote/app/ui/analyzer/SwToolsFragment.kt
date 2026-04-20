package com.sknote.app.ui.analyzer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.databinding.FragmentSwToolsBinding
import com.sknote.app.util.slideNavOptions

class SwToolsFragment : Fragment() {

    private var _binding: FragmentSwToolsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSwToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.cardAnalyzer.setOnClickListener {
            findNavController().navigate(R.id.projectAnalyzerFragment, null, slideNavOptions())
        }

        binding.cardLogicVisualizer.setOnClickListener {
            findNavController().navigate(R.id.logicVisualizerFragment, null, slideNavOptions())
        }

        binding.cardCustomBlocks.setOnClickListener {
            findNavController().navigate(R.id.customBlocksFragment, null, slideNavOptions())
        }

        binding.cardCustomComponents.setOnClickListener {
            findNavController().navigate(R.id.customComponentsFragment, null, slideNavOptions())
        }

        binding.cardCustomEvents.setOnClickListener {
            findNavController().navigate(R.id.customEventsFragment, null, slideNavOptions())
        }

        binding.cardGlobalStats.setOnClickListener {
            findNavController().navigate(R.id.projectAnalyzerFragment,
                Bundle().apply { putBoolean("show_global_stats", true) }, slideNavOptions())
        }

        binding.cardResources.setOnClickListener {
            findNavController().navigate(R.id.projectResourceFragment, null, slideNavOptions())
        }

        binding.cardBackup.setOnClickListener {
            findNavController().navigate(R.id.projectAnalyzerFragment,
                Bundle().apply { putBoolean("show_backup", true) }, slideNavOptions())
        }

        binding.cardLaunchSW.setOnClickListener {
            launchSketchware()
        }
    }

    private fun launchSketchware() {
        val packages = listOf(
            "pro.sketchware",
            "com.besome.sketch.pro",
            "com.besome.sketch",
            "mod.agus.jcoderz.editor"
        )
        for (pkg in packages) {
            val intent = requireContext().packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                startActivity(intent)
                return
            }
        }
        Snackbar.make(binding.root, "未找到 Sketchware Pro", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
