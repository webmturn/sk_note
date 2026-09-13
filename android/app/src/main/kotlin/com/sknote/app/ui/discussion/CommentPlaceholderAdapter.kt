package com.sknote.app.ui.discussion

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sknote.app.databinding.LayoutSkeletonCommentRowBinding

/**
 * 评论区的「占位即真实 item」。
 *
 * 官方做法（AndroidX Paging 的 placeholder）是让占位与真实数据走同一套 item 结构：
 * `PagingDataAdapter` 的文档明确要求 ViewHolder「必须支持绑定 null item 作为占位」，
 * 这样占位行与真实行共享同一份布局，位置也就一致。
 *
 * 这里把它用在评论列表上——讨论正文/作者/浏览数等页头字段已经由导航参数预填成真实内容，
 * 真正未知的只剩评论；而评论是结构固定的 item，占位行因此可以做到与真实行等高
 * （等高由 layout_skeleton_comment_row.xml 保证，见该文件注释）。
 *
 * 行数由调用方决定：预填数据里带了真实评论数，取它与一屏可容纳行数的较小值即可，
 * 多出来的部分本来就在屏幕之外，多少都不影响观感。
 */
class CommentPlaceholderAdapter : RecyclerView.Adapter<CommentPlaceholderAdapter.ViewHolder>() {

    class ViewHolder(val binding: LayoutSkeletonCommentRowBinding) : RecyclerView.ViewHolder(binding.root)

    private var count = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = LayoutSkeletonCommentRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // 纯静态占位，没有可绑定的数据。
    }

    override fun getItemCount(): Int = count

    fun show(placeholderCount: Int) {
        val target = placeholderCount.coerceAtLeast(0)
        if (target == count) return
        count = target
        notifyDataSetChanged()
    }

    fun clear() = show(0)
}
