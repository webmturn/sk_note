package com.sknote.app.util

import com.sknote.app.data.api.ApiClient
import com.sknote.app.data.model.Article
import com.sknote.app.data.model.Category
import com.sknote.app.data.model.Share
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object StartupWarmupManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val HOME_CACHE_DURATION_MS = 60_000L

    data class WarmHomeData(
        val categories: List<Category>,
        val articles: List<Article>,
        val latestShares: List<Share>,
        val fetchedAt: Long
    )

    @Volatile
    private var currentUserWarmupJob: Job? = null

    @Volatile
    private var homeWarmupJob: Job? = null

    @Volatile
    private var warmHomeData: WarmHomeData? = null

    @Synchronized
    fun prefetchCurrentUser() {
        val runningJob = currentUserWarmupJob
        if (runningJob != null && runningJob.isActive) return

        currentUserWarmupJob = scope.launch {
            try {
                val tokenManager = ApiClient.getTokenManager()
                tokenManager.preloadToken()
                val isLoggedIn = tokenManager.isLoggedIn().first()
                if (!isLoggedIn) return@launch

                val response = withTimeoutOrNull(4000) {
                    ApiClient.getService().getMe()
                } ?: return@launch

                if (!response.isSuccessful) return@launch
                val user = response.body()?.get("user") ?: return@launch
                tokenManager.updateCurrentUser(
                    userId = user.id,
                    username = user.username,
                    nickname = user.nickname.orEmpty(),
                    role = user.role,
                    avatarUrl = user.avatarUrl
                )
            } catch (_: Exception) {
            } finally {
                currentUserWarmupJob = null
            }
        }
    }

    @Synchronized
    fun prefetchHomeData() {
        val runningJob = homeWarmupJob
        if (runningJob != null && runningJob.isActive) return

        homeWarmupJob = scope.launch {
            try {
                val response = withTimeoutOrNull(4000) {
                    ApiClient.getService().getHomeData(limit = 10)
                } ?: return@launch

                if (!response.isSuccessful) return@launch
                val data = response.body() ?: return@launch
                warmHomeData = WarmHomeData(
                    categories = data.categories,
                    articles = data.articles,
                    latestShares = data.latestShares ?: emptyList(),
                    fetchedAt = System.currentTimeMillis()
                )
            } catch (_: Exception) {
            } finally {
                homeWarmupJob = null
            }
        }
    }

    /**
     * 等待两个预热任务结束，最多等 [timeoutMs]。
     *
     * 供启动页使用：原先启动页只能死等一个固定时长（1800ms），无论预热是 100ms
     * 完成还是没完成。有了这个入口，预热一结束就能进主界面。
     *
     * 两个任务内部已各带 4s 超时；这里再加一道总时长上限，避免网络慢时把启动页拖住——
     * 超时后照常进入主界面，未完成的预热继续在后台跑，首页到时由骨架屏兜底。
     */
    suspend fun awaitWarmup(timeoutMs: Long) {
        val jobs = synchronized(this) { listOfNotNull(currentUserWarmupJob, homeWarmupJob) }
        if (jobs.isEmpty()) return
        withTimeoutOrNull(timeoutMs) { jobs.forEach { it.join() } }
    }

    fun getWarmHomeData(maxAgeMs: Long = HOME_CACHE_DURATION_MS): WarmHomeData? {
        val cached = warmHomeData ?: return null
        return if (System.currentTimeMillis() - cached.fetchedAt <= maxAgeMs) cached else null
    }

    fun updateWarmHomeData(
        categories: List<Category>,
        articles: List<Article>,
        latestShares: List<Share>
    ) {
        warmHomeData = WarmHomeData(
            categories = categories,
            articles = articles,
            latestShares = latestShares,
            fetchedAt = System.currentTimeMillis()
        )
    }
}
