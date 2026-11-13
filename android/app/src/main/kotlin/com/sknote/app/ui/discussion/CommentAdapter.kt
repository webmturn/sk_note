package com.sknote.app.ui.discussion

import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.sknote.app.R
import com.sknote.app.data.model.Comment
import com.sknote.app.databinding.ItemCommentBinding
import com.sknote.app.databinding.ItemCommentExpandBinding
import com.sknote.app.util.TimeUtil

/**
 * 列表行：要么是一条评论，要么是「展开其余 N 条回复 / 收起回复」的折叠行。
 *
 * 行模型从 `Comment` 换成这一层，是因为折叠功能让列表里出现了**非评论的行**；
 * [CommentRow] 的包装本身也是必要的：DiffUtil 只比较 item 自身，若 item 仍是
 * [Comment]，父评论被编辑（正文变了、子评论对象没变）时子项不会重绑，引用块会一直
 * 显示旧正文。包一层之后「引用内容」也参与比较，父评论一变、引用它的行自动重绑。
 */
sealed class CommentRow {
    /** DiffUtil 的身份键：评论用真实 id，折叠行用负的根评论 id（不会与真实 id 冲突）。 */
    abstract val key: Long

    data class Item(val comment: Comment, val parent: Comment?) : CommentRow() {
        override val key: Long get() = comment.id
    }

    /**
     * [totalCount] 是这一楼的回复**总数**（B站 的 `rcount`，文案「共 N 条回复」用的就是它）；
     * [expanded] 为 true 时这一行是「收起回复」。
     */
    data class Expand(
        val rootId: Long,
        val totalCount: Int,
        val expanded: Boolean
    ) : CommentRow() {
        override val key: Long get() = -rootId
    }
}

class CommentAdapter(
    private val onReplyClick: (Comment) -> Unit = {},
    private val onCopyClick: (Comment) -> Unit = {},
    private val onDeleteClick: (Comment) -> Unit = {},
    private val onLikeClick: (Comment) -> Unit = {},
    private val onAvatarClick: (Comment) -> Unit = {},
    private val onEditClick: (Comment) -> Unit = {},
    private val onQuoteClick: (Comment) -> Unit = {},
    private val onExpandClick: (rootId: Long, expanded: Boolean) -> Unit = { _, _ -> }
) : ListAdapter<CommentRow, RecyclerView.ViewHolder>(DiffCallback) {

    private var currentUserId: Long = -1L
    private var currentUserRole: String = "user"

    /** 被跳转命中的评论 id：短暂高亮，由调用方定时清掉（见 highlight()）。 */
    private var highlightedId: Long? = null

    class CommentViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)
    class ExpandViewHolder(val binding: ItemCommentExpandBinding) : RecyclerView.ViewHolder(binding.root)

    companion object DiffCallback : DiffUtil.ItemCallback<CommentRow>() {
        override fun areItemsTheSame(a: CommentRow, b: CommentRow) = a.key == b.key
        override fun areContentsTheSame(a: CommentRow, b: CommentRow) = a == b

        private const val TYPE_COMMENT = 0
        private const val TYPE_EXPAND = 1
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is CommentRow.Expand) TYPE_EXPAND else TYPE_COMMENT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_EXPAND) {
            ExpandViewHolder(ItemCommentExpandBinding.inflate(inflater, parent, false))
        } else {
            CommentViewHolder(ItemCommentBinding.inflate(inflater, parent, false))
        }
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
     * 子适配器的手动 `notifyItemChanged` / `notifyDataSetChanged` 都不会让列表重绑 ——
     * 设备日志显示状态已按 18 -> null 正确翻转、视觉却不变。刷新因此交给调用方用
     * **根适配器**广播（`rvMain.adapter.notifyDataSetChanged()`），它一定生效。
     * （注意：DiffUtil 经 `submitList` 发出的范围通知是生效的，不生效的是手动通知。）
     *
     * 高亮本身刻意只做「开 / 关」两次重绑，不做渐变：卡片底色是 MaterialCardView 自己
     * 画的，想渐变就得换掉它的 background（会破坏圆角与描边），或者让动画持有 itemView
     * 引用（列表滚动回收时会闪到别的行上）。
     */
    fun highlight(commentId: Long?) {
        if (highlightedId == commentId) return
        highlightedId = commentId
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is CommentRow.Expand -> bindExpand(holder as ExpandViewHolder, row)
            is CommentRow.Item -> bindComment(holder as CommentViewHolder, row)
        }
    }

    private fun bindExpand(holder: ExpandViewHolder, row: CommentRow.Expand) {
        val b = holder.binding
        // 文案对齐 B站 的 sub_reply_entry_text：「共 xx 条回复」（总数口径）
        b.tvExpandLabel.text = if (row.expanded) "收起回复" else "共 ${row.totalCount} 条回复"
        b.ivExpandIcon.setImageResource(
            if (row.expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
        )
        b.layoutExpand.contentDescription = b.tvExpandLabel.text
        b.layoutExpand.setOnClickListener { onExpandClick(row.rootId, row.expanded) }
        // 与回复卡片左对齐（回复 = 卡片边距 + 回复缩进），读起来才算「这一楼的一部分」
        val lp = b.root.layoutParams as? ViewGroup.MarginLayoutParams
        if (lp != null) {
            val res = b.root.resources
            lp.marginStart = res.getDimensionPixelSize(R.dimen.comment_card_inset) +
                res.getDimensionPixelSize(R.dimen.comment_reply_indent)
            lp.marginEnd = res.getDimensionPixelSize(R.dimen.comment_card_inset)
            b.root.layoutParams = lp
        }
    }

    private fun bindComment(holder: CommentViewHolder, row: CommentRow.Item) {
        val comment = row.comment
        val parent = row.parent
        val b = holder.binding
        b.tvAuthor.text = comment.authorName ?: "匿名"
        val placeholderPadding = (4 * b.root.context.resources.displayMetrics.density).toInt()
        if (!comment.authorAvatar.isNullOrEmpty()) {
            b.ivAvatar.imageTintList = null
            b.ivAvatar.setPadding(0, 0, 0, 0)
            Glide.with(holder.itemView.context)
                .load(comment.authorAvatar)
                .circleCrop()
                .placeholder(R.drawable.ic_person)
                .into(b.ivAvatar)
        } else {
            Glide.with(holder.itemView.context).clear(b.ivAvatar)
            b.ivAvatar.setImageResource(R.drawable.ic_person)
            b.ivAvatar.setPadding(placeholderPadding, placeholderPadding, placeholderPadding, placeholderPadding)
        }
        bindLinkifiedText(b.tvContent, comment.content.orEmpty())
        b.tvTime.text = TimeUtil.formatRelative(comment.createdAt)
        b.tvLikeCount.text = if (comment.likeCount > 0) "${comment.likeCount}" else ""
        bindQuote(holder, comment, parent)
        bindHighlight(holder, comment)
        // 回复评论左侧缩进。注意必须在 comment_card_inset 的基础上【叠加】，
        // 而不是直接覆盖 marginStart——后者会把 item_comment.xml 里的水平边距
        // 一起清掉，导致评论卡通栏贴住屏幕两侧。
        val lp = b.root.layoutParams as? ViewGroup.MarginLayoutParams
        if (lp != null) {
            val res = b.root.resources
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
            b.root.layoutParams = lp
        }
        b.ivAvatar.setOnClickListener { onAvatarClick(comment) }
        b.tvAuthor.setOnClickListener { onAvatarClick(comment) }
        b.btnLike.setOnClickListener { onLikeClick(comment) }
        b.btnReply.setOnClickListener { onReplyClick(comment) }
        b.btnCopy.setOnClickListener { onCopyClick(comment) }

        val canEdit = comment.authorId == currentUserId || currentUserRole == "admin"
        b.btnEdit.visibility = if (canEdit) View.VISIBLE else View.GONE
        b.btnEdit.setOnClickListener { onEditClick(comment) }

        val canDelete = comment.authorId == currentUserId || currentUserRole == "admin" || currentUserRole == "editor"
        b.btnDelete.visibility = if (canDelete) View.VISIBLE else View.GONE
        b.btnDelete.setOnClickListener { onDeleteClick(comment) }
    }

    /**
     * 引用块：作者行取本地父评论（权威）→ 服务端 parent_author_name（父评论已被删除时
     * 服务端仍会带上作者名）→ 两者都没有则整块隐藏。
     *
     * 父评论不在列表里（已删除）时显示「该评论已删除」且**不可点击** —— 点了也跳不到
     * 任何地方，留着可点状态只会让人以为点击没生效。
     */
    private fun bindQuote(holder: CommentViewHolder, comment: Comment, parent: Comment?) {
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
        bindLinkifiedText(b.tvQuoteContent, if (alive) body else "该评论已删除")
        b.viewQuoteBar.alpha = if (alive) 0.45f else 0.25f
        b.tvQuoteContent.alpha = if (alive) 1f else 0.6f
        b.layoutQuote.isClickable = alive
        b.layoutQuote.setOnClickListener(if (alive) View.OnClickListener { onQuoteClick(comment) } else null)
        b.layoutQuote.contentDescription = if (alive) "查看 @$authorName 的评论" else null
    }

    private fun bindLinkifiedText(textView: TextView, content: String) {
        textView.text = content
        textView.movementMethod = if (Linkify.addLinks(textView, Linkify.WEB_URLS)) {
            LinkMovementMethod.getInstance()
        } else {
            null
        }
    }

    private fun bindHighlight(holder: CommentViewHolder, comment: Comment) {
        val card = holder.binding.root
        val attr = if (comment.id == highlightedId) {
            com.google.android.material.R.attr.colorSecondaryContainer
        } else {
            com.google.android.material.R.attr.colorSurfaceVariant
        }
        card.setCardBackgroundColor(MaterialColors.getColor(card, attr))
    }
}
