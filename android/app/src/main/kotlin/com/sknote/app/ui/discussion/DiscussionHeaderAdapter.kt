package com.sknote.app.ui.discussion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.databinding.LayoutDiscussionDetailHeaderBinding

class DiscussionHeaderAdapter : RecyclerView.Adapter<DiscussionHeaderAdapter.ViewHolder>() {

    private var _binding: LayoutDiscussionDetailHeaderBinding? = null
    val headerBinding get() = _binding

    class ViewHolder(val binding: LayoutDiscussionDetailHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = LayoutDiscussionDetailHeaderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        _binding = b
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Binding is managed externally by the Fragment
    }

    override fun getItemCount() = 1
}
