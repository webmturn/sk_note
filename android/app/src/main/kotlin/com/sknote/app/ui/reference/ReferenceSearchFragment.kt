package com.sknote.app.ui.reference

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.sknote.app.R
import com.sknote.app.databinding.FragmentReferenceSearchBinding

class ReferenceSearchFragment : Fragment() {

    private var _binding: FragmentReferenceSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReferenceViewModel by viewModels()
    private lateinit var adapter: ReferenceAdapter
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val historyPrefs by lazy {
        requireContext().getSharedPreferences("search_history", Context.MODE_PRIVATE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReferenceSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ReferenceData.init(requireContext())

        adapter = ReferenceAdapter(onClick = { item ->
            val bundle = Bundle().apply { putLong("reference_id", item.id) }
            findNavController().navigate(R.id.referenceDetailFragment, bundle)
        })
        binding.rvResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ReferenceSearchFragment.adapter
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnSearch.setOnClickListener { performSearch() }

        binding.etSearch.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                updateSearchActionState(query)
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    if (query.isNotEmpty()) {
                        binding.layoutHistory.visibility = View.GONE
                        binding.layoutHotTags.visibility = View.GONE
                        showSuggestions(query)
                    } else {
                        showIdleState()
                    }
                }
                searchHandler.postDelayed(searchRunnable!!, 200)
            }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                performSearch()
                true
            } else false
        }

        binding.btnClear.setOnClickListener {
            binding.etSearch.text?.clear()
            showIdleState()
        }

        binding.btnClearHistory.setOnClickListener {
            historyPrefs.edit().remove("queries").apply()
            showHistory()
        }

        showHistory()
        showHotTags()
        updateSearchActionState("")
        observeData()
    }

    private fun saveSearchQuery(query: String) {
        val history = getHistory().toMutableList()
        history.remove(query)
        history.add(0, query)
        val trimmed = history.take(10)
        historyPrefs.edit().putString("queries", trimmed.joinToString("\n")).apply()
    }

    private fun getHistory(): List<String> {
        val raw = historyPrefs.getString("queries", "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }
    }

    private fun showHistory() {
        val history = getHistory()
        if (history.isEmpty() || binding.etSearch.text?.isNotEmpty() == true) {
            binding.layoutHistory.visibility = View.GONE
            return
        }
        binding.layoutHistory.visibility = View.VISIBLE
        binding.chipGroupHistory.removeAllViews()
        history.forEach { query ->
            val chip = Chip(requireContext()).apply {
                text = query
                isCloseIconVisible = true
                isCheckable = false
                setOnClickListener {
                    binding.etSearch.setText(query)
                    binding.etSearch.setSelection(query.length)
                    performSearch()
                }
                setOnCloseIconClickListener {
                    val updated = getHistory().toMutableList()
                    updated.remove(query)
                    historyPrefs.edit().putString("queries", updated.joinToString("\n")).apply()
                    showHistory()
                }
            }
            binding.chipGroupHistory.addView(chip)
        }
    }

    private fun performSearch(queryOverride: String? = null) {
        val query = queryOverride?.trim().orEmpty().ifBlank { binding.etSearch.text.toString().trim() }
        if (query.isEmpty()) return
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        saveSearchQuery(query)
        binding.layoutHistory.visibility = View.GONE
        binding.layoutHotTags.visibility = View.GONE
        binding.layoutSuggestions.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.tvResultCount.visibility = View.GONE
        viewModel.searchReferences(query)
    }

    private fun showSuggestions(query: String) {
        val suggestions = ReferenceData.getSuggestions(query)
        binding.layoutSuggestions.removeAllViews()
        adapter.submitList(emptyList())
        binding.tvResultCount.visibility = View.GONE
        if (suggestions.isEmpty()) {
            binding.layoutSuggestions.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = "点击搜索查看「$query」的完整结果"
            return
        }
        binding.layoutSuggestions.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        for (item in suggestions) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(10))
                val outValue = android.util.TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
            }

            val tvName = TextView(requireContext()).apply {
                val q = query.lowercase()
                val name = item.name
                val lower = name.lowercase()
                val idx = lower.indexOf(q)
                if (idx >= 0) {
                    val span = SpannableString(name)
                    span.setSpan(StyleSpan(Typeface.BOLD), idx, idx + q.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text = span
                } else {
                    text = name
                }
                textSize = 14f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(tvName)

            val tvType = TextView(requireContext()).apply {
                text = ReferenceIcons.getTypeLabel(item.type)
                textSize = 11f
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
            row.addView(tvType)

            row.setOnClickListener {
                binding.etSearch.setText(item.name)
                binding.etSearch.setSelection(item.name.length)
                performSearch(item.name)
            }

            binding.layoutSuggestions.addView(row)
        }

        // "Search all" button at bottom
        val searchAll = TextView(requireContext()).apply {
            text = "搜索全部 \"$query\"  →"
            textSize = 13f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener {
                performSearch(query)
            }
        }
        binding.layoutSuggestions.addView(searchAll)
    }

    private fun updateSearchActionState(query: String) {
        binding.btnClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
        binding.btnSearch.isEnabled = query.isNotEmpty()
        binding.btnSearch.alpha = if (query.isNotEmpty()) 1f else 0.4f
    }

    private fun showIdleState() {
        adapter.submitList(emptyList())
        binding.layoutSuggestions.visibility = View.GONE
        binding.tvResultCount.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = "输入关键词搜索参考文档"
        showHistory()
        showHotTags()
    }

    private val hotTags = listOf(
        "onClick", "setText", "Intent", "Toast", "if", "Timer",
        "ListView", "SharedPreferences", "Firebase", "Dialog",
        "EditText", "ImageView", "getString", "setVisible"
    )

    private fun showHotTags() {
        if (binding.etSearch.text?.isNotEmpty() == true) {
            binding.layoutHotTags.visibility = View.GONE
            return
        }
        binding.layoutHotTags.visibility = View.VISIBLE
        binding.chipGroupHotTags.removeAllViews()
        hotTags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag
                isCheckable = false
                textSize = 12f
                setOnClickListener {
                    binding.etSearch.setText(tag)
                    binding.etSearch.setSelection(tag.length)
                    performSearch()
                }
            }
            binding.chipGroupHotTags.addView(chip)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun resolveColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun observeData() {
        viewModel.references.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            val query = binding.etSearch.text.toString().trim()
            if (list.isNotEmpty()) {
                binding.tvEmpty.visibility = View.GONE
                binding.tvResultCount.visibility = View.VISIBLE
                binding.tvResultCount.text = "找到 ${list.size} 项结果"
            } else {
                binding.tvResultCount.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = if (query.isEmpty()) "输入关键词搜索参考文档" else "未找到「$query」相关参考"
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        _binding = null
    }
}
