package com.sknote.app.ui.reference

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.sknote.app.R
import com.sknote.app.data.model.ReferenceItem

class BlockCompareDialog : BottomSheetDialogFragment() {

    private var blockA: ReferenceItem? = null
    private var blockB: ReferenceItem? = null

    companion object {
        fun newInstance(blockAId: Long): BlockCompareDialog {
            return BlockCompareDialog().apply {
                arguments = Bundle().apply { putLong("block_a_id", blockAId) }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_block_compare, container, false)
    }

    override fun onStart() {
        super.onStart()
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.9).toInt()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ReferenceData.init(requireContext())

        val blockAId = arguments?.getLong("block_a_id", 0L) ?: 0L
        if (blockAId <= 0L) return
        blockA = ReferenceData.getById(blockAId) ?: return

        view.findViewById<View>(R.id.btnCloseCompare).setOnClickListener { dismiss() }

        // Show block A shape
        setupBlockShape(view.findViewById(R.id.blockShapeA), view.findViewById(R.id.tvNameA), blockA!!)

        // Show search panel
        val layoutSelect = view.findViewById<LinearLayout>(R.id.layoutSelectBlock)
        layoutSelect.visibility = View.VISIBLE
        view.findViewById<View>(R.id.scrollCompare).visibility = View.GONE

        val etSearch = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearchBlock)
        val rv = view.findViewById<RecyclerView>(R.id.rvSearchResults)
        rv.layoutManager = LinearLayoutManager(requireContext())

        // Simple adapter for search results
        val resultAdapter = CompareSearchAdapter { selectedBlock ->
            blockB = selectedBlock
            layoutSelect.visibility = View.GONE
            view.findViewById<View>(R.id.scrollCompare).visibility = View.VISIBLE
            setupBlockShape(view.findViewById(R.id.blockShapeB), view.findViewById(R.id.tvNameB), blockB!!)
            buildComparison(view)
        }
        rv.adapter = resultAdapter

        // Pre-populate with similar blocks (same category)
        val similar = ReferenceData.getByType("block")
            .filter { it.id != blockAId && it.category == blockA!!.category }
            .take(20)
        resultAdapter.submitList(similar)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                if (q.isEmpty()) {
                    resultAdapter.submitList(similar)
                } else {
                    val results = ReferenceData.getByType("block")
                        .filter { it.id != blockAId && (it.name.lowercase().contains(q.lowercase()) || it.spec.orEmpty().lowercase().contains(q.lowercase())) }
                        .take(20)
                    resultAdapter.submitList(results)
                }
            }
        })
    }

    private fun setupBlockShape(shapeView: BlockShapeView, nameView: TextView, item: ReferenceItem) {
        try {
            shapeView.blockColor = if (item.color.isNotEmpty()) Color.parseColor(item.color) else 0xFFE1A92A.toInt()
        } catch (_: Exception) { shapeView.blockColor = 0xFFE1A92A.toInt() }
        shapeView.blockShape = item.shape?.ifEmpty { "s" } ?: "s"
        val spec = item.spec ?: ""
        if (spec.isNotEmpty()) { shapeView.blockSpec = spec; shapeView.blockLabel = "" }
        else { shapeView.blockSpec = ""; shapeView.blockLabel = item.name ?: "" }
        nameView.text = item.name ?: ""
    }

    private fun buildComparison(view: View) {
        val a = blockA ?: return
        val b = blockB ?: return
        val container = view.findViewById<LinearLayout>(R.id.layoutCompareRows)
        container.removeAllViews()

        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }

        fun addRow(label: String, valA: String, valB: String) {
            val same = valA.trim() == valB.trim()

            // Section label
            val tvLabel = TextView(ctx).apply {
                text = label
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                setPadding(0, dp(12), 0, dp(4))
            }
            container.addView(tvLabel)

            // Two-column row
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val cardA = createCompareCard(valA.ifEmpty { "—" }, same, true)
            val cardB = createCompareCard(valB.ifEmpty { "—" }, same, false)

            row.addView(cardA, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
            row.addView(cardB, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
            container.addView(row)
        }

        fun addCodeRow(label: String, codeA: String, codeB: String) {
            val same = codeA.trim() == codeB.trim()

            val tvLabel = TextView(ctx).apply {
                text = label
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(resolveColor(com.google.android.material.R.attr.colorPrimary))
                setPadding(0, dp(12), 0, dp(4))
            }
            container.addView(tvLabel)

            if (same) {
                val tvSame = TextView(ctx).apply {
                    text = "✓ 相同"
                    textSize = 12f
                    setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    setPadding(0, dp(2), 0, dp(2))
                }
                container.addView(tvSame)
                return
            }

            // Block A code
            val headerA = TextView(ctx).apply {
                text = "▸ ${a.name}"
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, dp(4), 0, dp(2))
            }
            container.addView(headerA)
            container.addView(createCodeCard(codeA.ifEmpty { "—" }))

            // Block B code
            val headerB = TextView(ctx).apply {
                text = "▸ ${b.name}"
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, dp(8), 0, dp(2))
            }
            container.addView(headerB)
            container.addView(createCodeCard(codeB.ifEmpty { "—" }))
        }

        // Summary badge
        val diffs = mutableListOf<String>()
        if (a.shape != b.shape) diffs.add("形状")
        if (a.color != b.color) diffs.add("颜色")
        if (a.category != b.category) diffs.add("分类")
        if (a.description != b.description) diffs.add("描述")
        if (a.parameters != b.parameters) diffs.add("参数")
        if (a.code.orEmpty() != b.code.orEmpty()) diffs.add("代码")
        if (a.example != b.example) diffs.add("示例")

        val summary = TextView(ctx).apply {
            text = if (diffs.isEmpty()) "✓ 这两个积木块完全相同"
                   else "共 ${diffs.size} 处差异：${diffs.joinToString("、")}"
            textSize = 13f
            setTextColor(if (diffs.isEmpty()) resolveColor(com.google.android.material.R.attr.colorPrimary)
                         else resolveColor(com.google.android.material.R.attr.colorError))
            setPadding(0, 0, 0, dp(8))
        }
        container.addView(summary)

        // Divider
        container.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(resolveColor(com.google.android.material.R.attr.colorOutlineVariant))
            alpha = 0.3f
        })

        // Compare rows
        addRow("分类", a.category, b.category)
        addRow("形状", ReferenceData.shapeLabels[a.shape] ?: a.shape, ReferenceData.shapeLabels[b.shape] ?: b.shape)
        addRow("描述", a.description, b.description)
        addRow("参数", a.parameters, b.parameters)
        addCodeRow("Java 代码", a.code.orEmpty(), b.code.orEmpty())
        addRow("示例", a.example, b.example)
    }

    private fun createCompareCard(text: String, same: Boolean, isLeft: Boolean): MaterialCardView {
        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        val card = MaterialCardView(ctx).apply {
            radius = dp(8).toFloat()
            strokeWidth = if (same) 0 else dp(1)
            strokeColor = if (same) 0 else resolveColor(com.google.android.material.R.attr.colorOutlineVariant)
            setCardBackgroundColor(resolveColor(
                if (same) com.google.android.material.R.attr.colorSurfaceVariant
                else if (isLeft) com.google.android.material.R.attr.colorSecondaryContainer
                else com.google.android.material.R.attr.colorTertiaryContainer
            ))
            cardElevation = 0f
        }
        val tv = TextView(ctx).apply {
            this.text = text
            textSize = 12f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            lineHeight = (textSize * 1.5).toInt()
        }
        card.addView(tv)
        return card
    }

    private fun createCodeCard(code: String): MaterialCardView {
        val ctx = requireContext()
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        val card = MaterialCardView(ctx).apply {
            radius = dp(8).toFloat()
            strokeWidth = 0
            setCardBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurfaceVariant))
            cardElevation = 0f
        }
        val hsv = android.widget.HorizontalScrollView(ctx)
        val tv = TextView(ctx).apply {
            text = code
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        hsv.addView(tv)
        card.addView(hsv)
        return card
    }

    private fun resolveColor(attr: Int): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    // Simple adapter for block search results in compare dialog
    inner class CompareSearchAdapter(
        private val onSelect: (ReferenceItem) -> Unit
    ) : RecyclerView.Adapter<CompareSearchAdapter.VH>() {

        private var items = listOf<ReferenceItem>()

        fun submitList(list: List<ReferenceItem>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                id = android.R.id.text1
                textSize = 14f
                val dp8 = (8 * resources.displayMetrics.density).toInt()
                val dp14 = (14 * resources.displayMetrics.density).toInt()
                setPadding(dp14, dp8, dp14, dp8)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                isClickable = true
                isFocusable = true
            }
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = "${item.name}  (${item.category})"
            holder.itemView.setOnClickListener { onSelect(item) }
        }

        override fun getItemCount() = items.size
    }
}
