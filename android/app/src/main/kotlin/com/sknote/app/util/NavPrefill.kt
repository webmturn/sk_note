package com.sknote.app.util

import android.os.Bundle
import com.google.gson.Gson

/**
 * 列表 → 详情之间传递「点击那一刻已经拿到的完整对象」。
 *
 * 为什么需要：详情页普遍只接收一个 id，于是必须等第二次请求回来才能渲染，页面
 * 只能先显示整页骨架再交叉淡出到真实内容，两者高度不一致就会跳变。而列表接口
 * 返回的往往就是完整对象（`articles.ts` 的列表查询带 `a.*`，`shares.ts` 用
 * `SELECT *`），数据在点击时已经全在手上，只是没有传过去。
 *
 * 为什么用 Gson 而不是 Parcelable：本工程未启用 `kotlin-parcelize`，且该插件的
 * Gradle marker 不在本机缓存中（只有 `kotlin-parcelize-runtime`），启用它需要联网
 * 拉取插件。Gson 本就是本工程的序列化方式——这些模型在生产里一直由它反序列化，
 * 往返无损（`Gson.toJson` 默认跳过 null 字段，而 Kotlin 可空字段的默认值恰好也是
 * null；基本类型字段不受跳过影响）。
 *
 * 走 Bundle 而不是进程内缓存：Bundle 会被系统持久化，进程被杀后重建仍能立即出内容，
 * 不需要额外的失效逻辑。
 */
object NavPrefill {

    /**
     * 各详情页各自的载荷键。刻意按类型分开命名：这样「收到了错误类型的载荷」会
     * 表现为键不存在（返回 null，退回骨架路径），而不是反序列化出一个字段全错的
     * 对象。
     */
    const val KEY_ARTICLE = "prefill_article"
    const val KEY_SHARE = "prefill_share"
    const val KEY_DISCUSSION = "prefill_discussion"

    private val gson = Gson()

    fun encode(value: Any?): String = gson.toJson(value)

    fun <T> decode(json: String?, type: Class<T>): T? =
        json?.let { runCatching { gson.fromJson(it, type) }.getOrNull() }
}

/** 把 [value] 序列化进导航参数。 */
inline fun <reified T> Bundle.putPrefill(key: String, value: T) {
    putString(key, NavPrefill.encode(value))
}

/** 读取预填对象；键不存在或解码失败一律返回 null，调用方据此退回骨架路径。 */
inline fun <reified T> Bundle.prefill(key: String): T? =
    NavPrefill.decode(getString(key), T::class.java)
