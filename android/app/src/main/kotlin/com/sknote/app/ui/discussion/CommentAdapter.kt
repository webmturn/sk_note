package com.sknote.app.ui.discussion

import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
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
 * 列表行模型。折叠入口不是独立行，而是根评论上的楼层信息（[threadReplyCount] /
 * [threadExpanded]）—— 早先的 32dp 整行入口横向几乎全空，既占空间又不像操作项。
 *
 * [CommentRow] 的包装仍有必要：DiffUtil 只比较 item 自身，若 item 仍是 [Comment]，
 * 父评论被编辑（正文变了、子评论对象没变）时子项不会重绑，引用块会一直显示旧正文。
 * 包一层之后「引用内容」和楼层折叠状态也参与比较，状态一变根评论就会重绑。
 */
sealed class CommentRow {
    /** DiffUtil 的身份键：评论 id 在本篇内唯一。 */
    abstract val key: Long

    data class Item(
        val comment: Comment,
        val parent: Comment?,
        /** 这一楼的回复总数；仅根评论且确有回复时非 0。 */
        val threadReplyCount: Int = 0,
        val threadExpanded: Boolean = false
    ) : CommentRow() {
        override val key: Long get() = comment.id
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
    private var discussionAuthorId: Long = -1L

    /** 被跳转命中的评论 id：短暂高亮，由调用方定时清掉（见 highlight()）。 */
    private var highlightedId: Long? = null

    /** 长评论展开状态只影响当前适配器实例；列表重建后回到默认折叠。 */
    private val expandedCommentIds = mutableSetOf<Long>()
    private val longCommentIds = mutableSetOf<Long>()

    class CommentViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)

    companion object DiffCallback : DiffUtil.ItemCallback<CommentRow>() {
        override fun areItemsTheSame(a: CommentRow, b: CommentRow) = a.key == b.key
        override fun areContentsTheSame(a: CommentRow, b: CommentRow) = a == b

        private const val MENU_COPY = 1
        private const val MENU_EDIT = 2
        private const val MENU_DELETE = 3
        private const val COLLAPSED_CONTENT_LINES = 6
        private const val LONG_TEXT_CHARS = 180
        private const val LONG_TEXT_NEWLINES = 5
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return CommentViewHolder(ItemCommentBinding.inflate(inflater, parent, false))
    }

    fun updateUserContext(userId: Long, userRole: String) {
        val role = userRole.ifBlank { "user" }
        if (currentUserId == userId && currentUserRole == role) return
        currentUserId = userId
        currentUserRole = role
        notifyDataSetChanged()
    }

    /** 返回是否发生变化；调用方负责通过根 ConcatAdapter 刷新，单独 notify 在这里不生效。 */
    fun updateDiscussionAuthor(authorId: Long): Boolean {
        if (discussionAuthorId == authorId) return false
        discussionAuthorId = authorId
        return true
    }

    fun expandedCommentState(): LongArray = expandedCommentIds.toLongArray()

    fun restoreExpandedComments(ids: LongArray) {
        expandedCommentIds.clear()
        longCommentIds.clear()
        expandedCommentIds.addAll(ids.toList())
        longCommentIds.addAll(ids.toList())
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
        bindComment(holder as CommentViewHolder, getItem(position) as CommentRow.Item)
    }

    private fun bindComment(holder: CommentViewHolder, row: CommentRow.Item) {
        val comment = row.comment
        val parent = row.parent
        val b = holder.binding
        b.tvAuthor.text = comment.authorName ?: "匿名"
        b.tvOpBadge.visibility = if (
            discussionAuthorId > 0L && comment.authorId == discussionAuthorId
        ) View.VISIBLE else View.GONE
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
        bindExpandableText(b, comment)
        b.tvTime.text = TimeUtil.formatRelative(comment.createdAt)
        b.ivLikeIcon.isSelected = comment.liked
        b.tvLikeCount.isSelected = comment.liked
        if (comment.likeCount > 0) {
            b.tvLikeCount.text = "${comment.likeCount}"
            b.tvLikeCount.visibility = View.VISIBLE
        } else {
            // INVISIBLE 而不是 GONE：固定槽位要一直占宽，否则点赞数 0↔1 时会把回复图标挤走。
            b.tvLikeCount.text = ""
            b.tvLikeCount.visibility = View.INVISIBLE
        }
        b.btnLike.contentDescription = when {
            comment.liked && comment.likeCount > 0 -> "取消点赞，${comment.likeCount} 个赞"
            comment.liked -> "取消点赞"
            comment.likeCount > 0 -> "点赞，${comment.likeCount} 个赞"
            else -> "点赞"
        }
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

        if (row.threadReplyCount > 0) {
            b.btnThread.visibility = View.VISIBLE
            b.btnThread.text = if (row.threadExpanded) "收起回复" else "${row.threadReplyCount} 条回复"
            b.btnThread.contentDescription = b.btnThread.text
            b.btnThread.setOnClickListener { onExpandClick(comment.id, row.threadExpanded) }
        } else {
            b.btnThread.visibility = View.GONE
            b.btnThread.setOnClickListener(null)
        }

        val canEdit = comment.authorId == currentUserId || currentUserRole == "admin"
        val canDelete = comment.authorId == currentUserId || currentUserRole == "admin" || currentUserRole == "editor"
        b.btnMore.setOnClickListener { showActionsMenu(b.btnMore, comment, canEdit, canDelete) }
    }

    private fun showActionsMenu(
        anchor: View,
        comment: Comment,
        canEdit: Boolean,
        canDelete: Boolean
    ) {
        val popup = PopupMenu(anchor.context, anchor).apply { setForceShowIcon(true) }
        popup.menu.add(Menu.NONE, MENU_COPY, 0, "复制").setIcon(R.drawable.ic_copy)
        if (canEdit) {
            popup.menu.add(Menu.NONE, MENU_EDIT, 1, "编辑").setIcon(R.drawable.ic_edit)
        }
        if (canDelete) {
            popup.menu.add(Menu.NONE, MENU_DELETE, 2, "删除").setIcon(R.drawable.ic_delete)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_COPY -> onCopyClick(comment)
                MENU_EDIT -> onEditClick(comment)
                MENU_DELETE -> onDeleteClick(comment)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
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

    /**
     * 长评论默认收成 6 行，避免一条几百字的评论吞掉整屏。列表复用下用
     * `Layout.getEllipsisCount()` 判断会漏掉首次布局，因此采用保守的内容阈值：
     * 180 字或 5 个换行以上才出现“展开全文”，短评论完全不增加控件。
     * 展开状态存在适配器内，点击时就地改 TextView，不需要刷新列表。
     */
    private fun bindExpandableText(b: ItemCommentBinding, comment: Comment) {
        val id = comment.id
        val content = comment.content.orEmpty()
        val longByContent = content.length > LONG_TEXT_CHARS ||
            content.count { it == '\n' } >= LONG_TEXT_NEWLINES
        if (longByContent) {
            longCommentIds.add(id)
        } else {
            // 评论被编辑成短文本时，不能留下过期的展开状态。
            longCommentIds.remove(id)
            expandedCommentIds.remove(id)
        }
        val expanded = id in expandedCommentIds
        val showToggle = expanded || id in longCommentIds
        b.tvContent.maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_CONTENT_LINES
        b.tvContent.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
        b.btnExpandText.visibility = if (showToggle) View.VISIBLE else View.GONE
        b.btnExpandText.text = if (expanded) "收起" else "展开全文"
        b.btnExpandText.contentDescription = b.btnExpandText.text
        b.btnExpandText.setOnClickListener {
            val nowExpanded = id !in expandedCommentIds
            if (nowExpanded) expandedCommentIds.add(id) else expandedCommentIds.remove(id)
            applyTextExpansion(b, nowExpanded)
        }
    }

    private fun applyTextExpansion(b: ItemCommentBinding, expanded: Boolean) {
        b.tvContent.maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_CONTENT_LINES
        b.tvContent.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
        b.btnExpandText.text = if (expanded) "收起" else "展开全文"
        b.btnExpandText.contentDescription = b.btnExpandText.text
    }

    private fun bindHighlight(holder: CommentViewHolder, comment: Comment) {
        val card = holder.binding.root
        val attr = if (comment.id == highlightedId) {
            com.google.android.material.R.attr.colorSecondaryContainer
        } else {
            com.google.android.material.R.attr.colorSurfaceContainerHigh
        }
        card.setCardBackgroundColor(MaterialColors.getColor(card, attr))
    }
}
