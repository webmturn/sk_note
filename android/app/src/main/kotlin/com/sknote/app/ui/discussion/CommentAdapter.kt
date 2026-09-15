package com.sknote.app.ui.discussion

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.sknote.app.R
import com.sknote.app.data.model.Comment
import com.sknote.app.databinding.ItemCommentBinding
import com.sknote.app.util.TimeUtil

/**
 * 一行评论 = 评论本体 + 它引用的那条评论（可能不在列表里：父评论已被删除）。
 *
 * 为什么把父评论包进来、而不是在 adapter 里按 id 查表：
 * [ListAdapter] 的 DiffUtil 只比较 item 本身，若 item 仍是 [Comment]，父评论被编辑
 * （正文变了、子评论对象没变）时子项不会重绑，引用块会一直显示旧正文。包一层之后
 * 「引用内容」也参与比较，父评论一变、所有引用它的行自动重绑。
 */
data class CommentRow(val comment: Comment, val parent: Comment?)

class CommentAdapter(
    private val onReplyClick: (Comment) -> Unit = {},
    private val onCopyClick: (Comment) -> Unit = {},
    private val onDeleteClick: (Comment) -> Unit = {},
    private val onLikeClick: (Comment) -> Unit = {},
    private val onAvatarClick: (Comment) -> Unit = {},
    private val onEditClick: (Comment) -> Unit = {},
    private val onQuoteClick: (Comment) -> Unit = {}
) : ListAdapter<CommentRow, CommentAdapter.ViewHolder>(DiffCallback) {

    private var currentUserId: Long = -1L
    private var currentUserRole: String = "user"

    /** 被跳转命中的评论 id：短暂高亮，由调用方定时清掉（见 highlight()）。 */
    private var highlightedId: Long? = null

    class ViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)

    companion object DiffCallback : DiffUtil.ItemCallback<CommentRow>() {
        override fun areItemsTheSame(a: CommentRow, b: CommentRow) = a.comment.id == b.comment.id
        override fun areContentsTheSame(a: CommentRow, b: CommentRow) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    fun updateUserContext(userId: Long, userRole: String) {
        val role = userRole.ifBlank { "user" }
        if (currentUserId == userId && currentUserRole == role) return
        currentUserId = userId
        currentUserRole = role
        notifyDataSetChanged()
    }

    /**
     * 设置高亮目标（**只改状态，不自己刷新**），传 null 清除。
     *
     * 为什么刷新不在这里做：本列表挂在 `ConcatAdapter(页头, 占位行, 评论)` 下，实测
     * 子适配器的 `notifyItemChanged` / `notifyDataSetChanged` **都不会让列表重绑** ——
     * 设备日志显示状态已按 18 -> null 正确翻转、视觉却不变。刷新因此交给调用方用
     * **根适配器**广播（`rvMain.adapter.notifyDataSetChanged()`），它一定生效。
     *
     * 高亮本身刻意只做「开 / 关」两次重绑，不做渐变：卡片底色是 MaterialCardView 自己
     * 画的，想渐变就得换掉它的 background（会破坏圆角与描边），或者让动画持有 itemView
     * 引用（列表滚动回收时会闪到别的行上）。
     */
    fun highlight(commentId: Long?) {
        if (highlightedId == commentId) return
        highlightedId = commentId
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        val comment = row.comment
        val parent = row.parent
        holder.binding.tvAuthor.text = comment.authorName ?: "匿名"
        val placeholderPadding = (4 * holder.binding.root.context.resources.displayMetrics.density).toInt()
        if (!comment.authorAvatar.isNullOrEmpty()) {
            holder.binding.ivAvatar.imageTintList = null
            holder.binding.ivAvatar.setPadding(0, 0, 0, 0)
            Glide.with(holder.itemView.context)
                .load(comment.authorAvatar)
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .into(holder.binding.ivAvatar)
        } else {
            Glide.with(holder.itemView.context).clear(holder.binding.ivAvatar)
            holder.binding.ivAvatar.setImageResource(R.drawable.ic_person)
            holder.binding.ivAvatar.setPadding(
                placeholderPadding,
                placeholderPadding,
                placeholderPadding,
                placeholderPadding
            )
        }
        holder.binding.tvContent.text = comment.content.orEmpty()
        holder.binding.tvTime.text = TimeUtil.formatRelative(comment.createdAt)
        holder.binding.tvLikeCount.text = if (comment.likeCount > 0) "${comment.likeCount}" else ""
        bindQuote(holder, comment, parent)
        bindHighlight(holder, comment)
        // 回复评论左侧缩进。注意必须在 comment_card_inset 的基础上【叠加】，
        // 而不是直接覆盖 marginStart——后者会把 item_comment.xml 里的水平边距
        // 一起清掉，导致评论卡通栏贴住屏幕两侧。
        val lp = holder.binding.root.layoutParams as? android.view.ViewGroup.MarginLayoutParams
        if (lp != null) {
            val res = holder.binding.root.resources
            val baseInset = res.getDimensionPixelSize(R.dimen.comment_card_inset)
            val replyIndent = if (comment.parentId != null) {
                res.getDimensionPixelSize(R.dimen.comment_reply_indent)
            } else {
                0
            }
            // marginStart/marginEnd 必须同时设置：只设其中一个会让另一个的相对边距
            // 解析失效（实测只设 marginStart 时右边距会变成 0，卡片右缘仍贴屏幕）。
            lp.marginStart = baseInset + replyIndent
            lp.marginEnd = baseInset
            holder.binding.root.layoutParams = lp
        }
        holder.binding.ivAvatar.setOnClickListener { onAvatarClick(comment) }
        holder.binding.tvAuthor.setOnClickListener { onAvatarClick(comment) }
        holder.binding.btnLike.setOnClickListener { onLikeClick(comment) }
        holder.binding.btnReply.setOnClickListener { onReplyClick(comment) }
        holder.binding.btnCopy.setOnClickListener { onCopyClick(comment) }

        val canEdit = comment.authorId == currentUserId || currentUserRole == "admin"
        holder.binding.btnEdit.visibility = if (canEdit) View.VISIBLE else View.GONE
        holder.binding.btnEdit.setOnClickListener { onEditClick(comment) }

        val canDelete = comment.authorId == currentUserId || currentUserRole == "admin" || currentUserRole == "editor"
        holder.binding.btnDelete.visibility = if (canDelete) View.VISIBLE else View.GONE
        holder.binding.btnDelete.setOnClickListener { onDeleteClick(comment) }
    }

    /**
     * 引用块：作者行取本地父评论（权威）→ 服务端 parent_author_name（父评论已被删除时
     * 服务端仍会带上作者名）→ 两者都没有则整块隐藏。
     *
     * 父评论不在列表里（已删除）时显示「该评论已删除」且**不可点击** —— 点了也跳不到
     * 任何地方，留着可点状态只会让人以为点击没生效。
     */
    private fun bindQuote(holder: ViewHolder, comment: Comment, parent: Comment?) {
        val b = holder.binding
        val authorName = parent?.authorName?.takeIf { it.isNotBlank() }
            ?: comment.parentAuthorName?.takeIf { it.isNotBlank() }
        if (comment.parentId == null || authorName == null) {
            b.layoutQuote.visibility = View.GONE
            b.layoutQuote.setOnClickListener(null)
            return
        }
        val body = parent?.content?.trim().orEmpty()
        val alive = body.isNotEmpty()
        b.layoutQuote.visibility = View.VISIBLE
        b.tvQuoteAuthor.text = "@$authorName"
        b.tvQuoteContent.text = if (alive) body else "该评论已删除"
        b.viewQuoteBar.alpha = if (alive) 0.45f else 0.25f
        b.tvQuoteContent.alpha = if (alive) 1f else 0.6f
        b.layoutQuote.isClickable = alive
        b.layoutQuote.setOnClickListener(if (alive) View.OnClickListener { onQuoteClick(comment) } else null)
        b.layoutQuote.contentDescription = if (alive) "查看 @$authorName 的评论" else null
    }

    private fun bindHighlight(holder: ViewHolder, comment: Comment) {
        val card = holder.binding.root
        val attr = if (comment.id == highlightedId) {
            com.google.android.material.R.attr.colorSecondaryContainer
        } else {
            com.google.android.material.R.attr.colorSurfaceVariant
        }
        card.setCardBackgroundColor(MaterialColors.getColor(card, attr))
    }
}
