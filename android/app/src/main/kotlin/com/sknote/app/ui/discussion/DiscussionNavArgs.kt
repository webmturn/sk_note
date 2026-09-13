package com.sknote.app.ui.discussion

import android.os.Bundle
import com.google.gson.Gson
import com.sknote.app.data.model.Discussion

/**
 * 讨论列表 → 详情页之间传递「已知的讨论数据」。
 *
 * 为什么需要它：详情页此前只预填了标题/作者/分类/时间四项，正文、浏览数、评论数
 * 都要等详情接口回来，于是页头在骨架 → 真实内容之间发生高度跳变。但**列表接口
 * 本来就返回完整正文**（`backend/src/routes/discussions.ts` 的列表查询带 `d.content`、
 * `d.view_count`、`d.reply_count`；用户主页用的是 `SELECT d.*`，同样完整）。也就是说
 * 页头所需数据在点击的那一刻已经全部在手上，只是没有传过来。
 *
 * 为什么用 Gson 而不是 Parcelable：本工程未启用 `kotlin-parcelize` 插件，而该插件的
 * Gradle marker 并不在本机缓存中（只有 `kotlin-parcelize-runtime`），启用它需要联网
 * 拉取插件，构建风险高。Gson 本来就是本工程的序列化方式——`Discussion` 这个类在
 * 生产里一直是由 Gson 反序列化出来的，往返无损（`Gson.toJson` 默认跳过 null 字段，
 * 而 Kotlin 可空字段的默认值恰好也是 null；基本类型字段则不受跳过影响）。
 *
 * 走 Bundle 而不是进程内缓存：Bundle 会被系统持久化，进程被杀后重建仍能立即出内容，
 * 不需要额外的失效逻辑。
 */

/** 详情页唯一必填参数：讨论 ID。 */
const val KEY_DISCUSSION_ID = "discussion_id"

private const val KEY_PREFILL = "prefill_discussion"

private val gson = Gson()

/**
 * 把点击时已经拿到的完整 [Discussion] 放进导航参数。
 * 讨论正文上限 20000 字符（`backend/src/routes/discussions.ts:128`），JSON 后
 * 仍远小于 Binder 事务上限。
 */
fun Bundle.putPrefillDiscussion(discussion: Discussion) {
    putString(KEY_PREFILL, gson.toJson(discussion))
}

/**
 * 读取预填数据。解码失败一律返回 null —— 此时详情页退回骨架路径，
 * 表现与改造前一致，不会崩。
 */
fun Bundle.prefillDiscussion(): Discussion? {
    val json = getString(KEY_PREFILL) ?: return null
    return runCatching { gson.fromJson(json, Discussion::class.java) }.getOrNull()
}
