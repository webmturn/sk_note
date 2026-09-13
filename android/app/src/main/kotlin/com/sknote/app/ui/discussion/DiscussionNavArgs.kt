package com.sknote.app.ui.discussion

import android.os.Bundle
import com.sknote.app.data.model.Discussion
import com.sknote.app.util.NavPrefill
import com.sknote.app.util.prefill
import com.sknote.app.util.putPrefill

/**
 * 讨论列表 → 详情页之间传递「已知的讨论数据」。
 *
 * 机制（Gson 序列化进 Bundle、不用 Parcelable 的原因、为何不走进程内缓存）见
 * [NavPrefill]。这里只保留讨论页自己的参数名与两个薄封装，调用点因此不必关心
 * 承载细节。
 *
 * 详情页此前只预填了标题/作者/分类/时间四项，正文、浏览数、评论数都要等详情接口
 * 回来，页头于是在骨架与真实内容之间跳变。但列表接口本来就返回完整正文
 * （`discussions.ts` 的列表查询带 `d.content`/`d.view_count`/`d.reply_count`；
 * 用户主页用 `SELECT d.*`，同样完整），页头所需数据在点击那一刻已全部在手上。
 */

/** 详情页唯一必填参数：讨论 ID。 */
const val KEY_DISCUSSION_ID = "discussion_id"

fun Bundle.putPrefillDiscussion(discussion: Discussion) =
    putPrefill(NavPrefill.KEY_DISCUSSION, discussion)

fun Bundle.prefillDiscussion(): Discussion? =
    prefill(NavPrefill.KEY_DISCUSSION)
