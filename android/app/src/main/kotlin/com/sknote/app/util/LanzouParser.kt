package com.sknote.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

/**
 * 蓝奏云链接解析器
 * 解析蓝奏云分享链接，获取真实下载地址
 */
object LanzouParser {

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .build()

    private val LANZOU_DOMAINS = listOf(
        "lanzou.com", "lanzoui.com", "lanzoux.com", "lanzouy.com",
        "lanzouv.com", "lanzouj.com", "lanzouq.com", "lanzoup.com"
    )

    private val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    data class ParseResult(
        val success: Boolean,
        val downloadUrl: String = "",
        val fileName: String = "",
        val error: String = ""
    )

    /**
     * 判断是否为蓝奏云链接
     */
    fun isLanzouUrl(url: String): Boolean {
        return LANZOU_DOMAINS.any { url.contains(it) }
    }

    /**
     * 标准化蓝奏云链接（统一域名）
     */
    fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http")) {
            normalized = "https://$normalized"
        }
        // 统一使用 lanzoui.com
        LANZOU_DOMAINS.forEach { domain ->
            if (normalized.contains(domain)) {
                normalized = normalized.replace(domain, "lanzoui.com")
            }
        }
        return normalized
    }

    /**
     * 解析蓝奏云链接获取真实下载地址
     * @param shareUrl 分享链接
     * @param password 提取密码（可为空）
     */
    suspend fun parse(shareUrl: String, password: String = ""): ParseResult = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(shareUrl)

            // 第一步：请求分享页面
            val pageRequest = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()

            val pageResponse = client.newCall(pageRequest).execute()
            val pageHtml = pageResponse.body?.string() ?: return@withContext ParseResult(false, error = "无法访问页面")

            // 检查是否需要密码
            if (pageHtml.contains("输入密码") || pageHtml.contains("请输入密码")) {
                if (password.isEmpty()) {
                    return@withContext ParseResult(false, error = "需要密码")
                }
                return@withContext parseWithPassword(url, password, pageHtml)
            }

            // 无密码情况：从页面中提取 iframe 地址
            return@withContext parseWithoutPassword(url, pageHtml)
        } catch (e: Exception) {
            ParseResult(false, error = "解析失败: ${e.message}")
        }
    }

    private suspend fun parseWithoutPassword(baseUrl: String, pageHtml: String): ParseResult = withContext(Dispatchers.IO) {
        try {
            // 提取 iframe src
            val iframePattern = Pattern.compile("src=\"(/fn\\?[^\"]+)\"")
            val iframeMatcher = iframePattern.matcher(pageHtml)

            if (!iframeMatcher.find()) {
                // 尝试提取 ajaxm.php 的参数
                return@withContext extractAjaxDownload(baseUrl, pageHtml)
            }

            val iframeSrc = iframeMatcher.group(1) ?: return@withContext ParseResult(false, error = "无法提取下载页")
            val domain = baseUrl.substringBefore("/", "https://lanzoui.com").let {
                val idx = it.indexOf("//")
                if (idx >= 0) {
                    val slashIdx = it.indexOf("/", idx + 2)
                    if (slashIdx >= 0) it.substring(0, slashIdx) else it
                } else it
            }
            val iframeUrl = "$domain$iframeSrc"

            // 请求 iframe 页面
            val iframeRequest = Request.Builder()
                .url(iframeUrl)
                .header("User-Agent", UA)
                .header("Referer", baseUrl)
                .build()

            val iframeResponse = client.newCall(iframeRequest).execute()
            val iframeHtml = iframeResponse.body?.string() ?: return@withContext ParseResult(false, error = "无法获取下载页")

            return@withContext extractAjaxDownload(domain, iframeHtml)
        } catch (e: Exception) {
            ParseResult(false, error = "解析失败: ${e.message}")
        }
    }

    private suspend fun parseWithPassword(baseUrl: String, password: String, pageHtml: String): ParseResult = withContext(Dispatchers.IO) {
        try {
            // 提取 sign 参数
            val signPattern = Pattern.compile("'sign'\\s*:\\s*'([^']+)'")
            val signMatcher = signPattern.matcher(pageHtml)
            val sign = if (signMatcher.find()) signMatcher.group(1) ?: "" else ""

            if (sign.isEmpty()) {
                return@withContext ParseResult(false, error = "无法提取签名参数")
            }

            val domain = extractDomain(baseUrl)

            // 发送密码验证请求
            val formBody = FormBody.Builder()
                .add("action", "downprocess")
                .add("sign", sign)
                .add("p", password)
                .build()

            val ajaxRequest = Request.Builder()
                .url("$domain/ajaxm.php")
                .header("User-Agent", UA)
                .header("Referer", baseUrl)
                .post(formBody)
                .build()

            val ajaxResponse = client.newCall(ajaxRequest).execute()
            val ajaxJson = ajaxResponse.body?.string() ?: return@withContext ParseResult(false, error = "密码验证失败")

            // 解析 JSON 响应: {"zt":1,"dom":"xxx","url":"xxx"}
            return@withContext extractDownloadFromJson(ajaxJson)
        } catch (e: Exception) {
            ParseResult(false, error = "密码验证失败: ${e.message}")
        }
    }

    private suspend fun extractAjaxDownload(domain: String, html: String): ParseResult = withContext(Dispatchers.IO) {
        try {
            // 提取 ajax 参数
            val signPattern = Pattern.compile("'sign'\\s*:\\s*'([^']+)'")
            val signMatcher = signPattern.matcher(html)
            val sign = if (signMatcher.find()) signMatcher.group(1) ?: "" else ""

            val signtsPattern = Pattern.compile("'signs'\\s*:\\s*'([^']*)'")
            val signtsMatcher = signtsPattern.matcher(html)
            val signs = if (signtsMatcher.find()) signtsMatcher.group(1) ?: "" else ""

            val websignPattern = Pattern.compile("'websign'\\s*:\\s*'([^']*)'")
            val websignMatcher = websignPattern.matcher(html)
            val websign = if (websignMatcher.find()) websignMatcher.group(1) ?: "" else ""

            val websignkeyPattern = Pattern.compile("'websignkey'\\s*:\\s*'([^']*)'")
            val websignkeyMatcher = websignkeyPattern.matcher(html)
            val websignkey = if (websignkeyMatcher.find()) websignkeyMatcher.group(1) ?: "" else ""

            val baseDomain = extractDomain(domain)

            val formBuilder = FormBody.Builder()
                .add("action", "downprocess")
                .add("sign", sign)
                .add("signs", signs)
                .add("websign", websign)
                .add("websignkey", websignkey)

            val ajaxRequest = Request.Builder()
                .url("$baseDomain/ajaxm.php")
                .header("User-Agent", UA)
                .header("Referer", domain)
                .post(formBuilder.build())
                .build()

            val ajaxResponse = client.newCall(ajaxRequest).execute()
            val ajaxJson = ajaxResponse.body?.string() ?: return@withContext ParseResult(false, error = "无法获取下载链接")

            return@withContext extractDownloadFromJson(ajaxJson)
        } catch (e: Exception) {
            ParseResult(false, error = "提取下载链接失败: ${e.message}")
        }
    }

    private fun extractDownloadFromJson(json: String): ParseResult {
        try {
            // 简单的 JSON 解析（避免引入 JSON 库依赖）
            val ztPattern = Pattern.compile("\"zt\"\\s*:\\s*(\\d+)")
            val ztMatcher = ztPattern.matcher(json)
            val zt = if (ztMatcher.find()) ztMatcher.group(1)?.toIntOrNull() ?: 0 else 0

            if (zt != 1) {
                // 检查是否密码错误
                if (json.contains("密码不正确") || json.contains("密码错误")) {
                    return ParseResult(false, error = "密码错误")
                }
                return ParseResult(false, error = "下载链接获取失败")
            }

            val domPattern = Pattern.compile("\"dom\"\\s*:\\s*\"([^\"]+)\"")
            val domMatcher = domPattern.matcher(json)
            val dom = if (domMatcher.find()) domMatcher.group(1)?.replace("\\/", "/") ?: "" else ""

            val urlPattern = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"")
            val urlMatcher = urlPattern.matcher(json)
            val urlPath = if (urlMatcher.find()) urlMatcher.group(1)?.replace("\\/", "/") ?: "" else ""

            if (dom.isEmpty() || urlPath.isEmpty()) {
                return ParseResult(false, error = "无法提取下载地址")
            }

            val downloadUrl = "$dom/file/$urlPath"
            return ParseResult(true, downloadUrl = downloadUrl)
        } catch (e: Exception) {
            return ParseResult(false, error = "JSON解析失败: ${e.message}")
        }
    }

    private fun extractDomain(url: String): String {
        val protocolEnd = url.indexOf("//")
        if (protocolEnd < 0) return url
        val pathStart = url.indexOf("/", protocolEnd + 2)
        return if (pathStart >= 0) url.substring(0, pathStart) else url
    }
}
