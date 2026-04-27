package com.sknote.app.data.api

import com.sknote.app.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ============ 合并接口 ============

    @GET("api/home")
    suspend fun getHomeData(
        @Query("limit") limit: Int = 10
    ): Response<HomeResponse>

    @GET("api/stats")
    suspend fun getStats(): Response<StatsResponse>

    // ============ 认证 ============

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("api/auth/me")
    suspend fun getMe(): Response<Map<String, User>>

    // ============ 分类 ============

    @GET("api/categories")
    suspend fun getCategories(): Response<CategoriesResponse>

    @GET("api/categories/{id}")
    suspend fun getCategory(@Path("id") id: Long): Response<CategoryResponse>

    @POST("api/categories")
    suspend fun createCategory(@Body request: CreateCategoryRequest): Response<MessageResponse>

    @PUT("api/categories/{id}")
    suspend fun updateCategory(@Path("id") id: Long, @Body request: CreateCategoryRequest): Response<MessageResponse>

    @DELETE("api/categories/{id}")
    suspend fun deleteCategory(@Path("id") id: Long): Response<MessageResponse>

    @GET("api/discussion-categories")
    suspend fun getDiscussionCategories(): Response<DiscussionCategoriesResponse>

    @GET("api/discussion-categories/{id}")
    suspend fun getDiscussionCategory(@Path("id") id: Long): Response<DiscussionCategoryResponse>

    @POST("api/discussion-categories")
    suspend fun createDiscussionCategory(@Body request: CreateDiscussionCategoryRequest): Response<MessageResponse>

    @PUT("api/discussion-categories/{id}")
    suspend fun updateDiscussionCategory(
        @Path("id") id: Long,
        @Body request: CreateDiscussionCategoryRequest
    ): Response<MessageResponse>

    @DELETE("api/discussion-categories/{id}")
    suspend fun deleteDiscussionCategory(@Path("id") id: Long): Response<MessageResponse>

    // ============ 文章 ============

    @GET("api/articles")
    suspend fun getArticles(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("category_id") categoryId: Long? = null,
        @Query("search") search: String? = null
    ): Response<ArticlesResponse>

    @GET("api/articles/{id}")
    suspend fun getArticle(@Path("id") id: Long): Response<ArticleResponse>

    @POST("api/articles")
    suspend fun createArticle(@Body request: CreateArticleRequest): Response<MessageResponse>

    @PUT("api/articles/{id}")
    suspend fun updateArticle(@Path("id") id: Long, @Body request: UpdateArticleRequest): Response<MessageResponse>

    @DELETE("api/articles/{id}")
    suspend fun deleteArticle(@Path("id") id: Long): Response<MessageResponse>

    @POST("api/articles/{id}/like")
    suspend fun likeArticle(@Path("id") id: Long): Response<LikeResponse>

    // ============ 讨论 ============

    @GET("api/discussions")
    suspend fun getDiscussions(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("category") category: String? = null,
        @Query("article_id") articleId: Long? = null,
        @Query("search") search: String? = null
    ): Response<DiscussionsResponse>

    @GET("api/discussions/{id}")
    suspend fun getDiscussion(@Path("id") id: Long): Response<DiscussionDetailResponse>

    @POST("api/discussions")
    suspend fun createDiscussion(@Body request: CreateDiscussionRequest): Response<MessageResponse>

    @PUT("api/discussions/{id}")
    suspend fun updateDiscussion(
        @Path("id") id: Long,
        @Body request: UpdateDiscussionRequest
    ): Response<MessageResponse>

    @POST("api/discussions/{id}/comments")
    suspend fun createComment(
        @Path("id") discussionId: Long,
        @Body request: CreateCommentRequest
    ): Response<MessageResponse>

    @DELETE("api/discussions/{id}")
    suspend fun deleteDiscussion(@Path("id") id: Long): Response<MessageResponse>

    @DELETE("api/discussions/{discussionId}/comments/{commentId}")
    suspend fun deleteComment(
        @Path("discussionId") discussionId: Long,
        @Path("commentId") commentId: Long
    ): Response<MessageResponse>

    // ============ 参考手册 ============

    @GET("api/references")
    suspend fun getReferences(
        @Query("type") type: String? = null,
        @Query("category") category: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<ReferenceListResponse>

    @GET("api/references/{id}")
    suspend fun getReference(@Path("id") id: Long): Response<ReferenceDetailResponse>

    // ============ 评论点赞 ============

    @POST("api/discussions/{discussionId}/comments/{commentId}/like")
    suspend fun likeComment(
        @Path("discussionId") discussionId: Long,
        @Path("commentId") commentId: Long
    ): Response<LikeResponse>

    // ============ 用户操作 ============

    @PUT("api/auth/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<MessageResponse>

    @PUT("api/auth/me")
    suspend fun updateProfile(@Body request: Map<String, String>): Response<MessageResponse>

    @Multipart
    @POST("api/auth/avatar/upload")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): Response<ImageUploadResponse>

    // ============ 管理员：用户管理 ============

    @GET("api/auth/users")
    suspend fun getUsers(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("search") search: String? = null
    ): Response<UsersResponse>

    @PUT("api/auth/users/{id}/role")
    suspend fun updateUserRole(
        @Path("id") userId: Long,
        @Body request: UpdateRoleRequest
    ): Response<MessageResponse>

    @DELETE("api/auth/users/{id}")
    suspend fun deleteUser(@Path("id") userId: Long): Response<MessageResponse>

    @PUT("api/auth/users/{id}/password")
    suspend fun resetUserPassword(
        @Path("id") userId: Long,
        @Body request: ResetPasswordRequest
    ): Response<MessageResponse>

    // ============ 通知 ============

    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("unread") unread: String? = null
    ): Response<NotificationsResponse>

    @GET("api/notifications/unread-count")
    suspend fun getUnreadCount(): Response<UnreadCountResponse>

    @PUT("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: Long): Response<MessageResponse>

    @PUT("api/notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<MessageResponse>

    @DELETE("api/notifications/all")
    suspend fun deleteAllNotifications(): Response<MessageResponse>

    @DELETE("api/notifications/{id}")
    suspend fun deleteNotification(@Path("id") id: Long): Response<MessageResponse>

    // ============ 代码片段 ============

    @GET("api/snippets")
    suspend fun getSnippets(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("category") category: String? = null,
        @Query("search") search: String? = null
    ): Response<SnippetsResponse>

    @GET("api/snippets/categories")
    suspend fun getSnippetCategories(): Response<SnippetCategoriesResponse>

    @GET("api/snippets/{id}")
    suspend fun getSnippet(@Path("id") id: Long): Response<SnippetDetailResponse>

    @POST("api/snippets")
    suspend fun createSnippet(@Body request: CreateSnippetRequest): Response<MessageResponse>

    @PUT("api/snippets/{id}")
    suspend fun updateSnippet(@Path("id") id: Long, @Body request: UpdateSnippetRequest): Response<MessageResponse>

    @DELETE("api/snippets/{id}")
    suspend fun deleteSnippet(@Path("id") id: Long): Response<MessageResponse>

    @POST("api/snippets/{id}/like")
    suspend fun likeSnippet(@Path("id") id: Long): Response<LikeResponse>

    // ============ 文件分享 ============

    @GET("api/shares")
    suspend fun getShares(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("category") category: String? = null,
        @Query("search") search: String? = null
    ): Response<SharesResponse>

    @GET("api/shares/categories")
    suspend fun getShareCategories(): Response<ShareCategoriesResponse>

    @GET("api/shares/{id}")
    suspend fun getShare(@Path("id") id: Long): Response<ShareDetailResponse>

    @POST("api/shares")
    suspend fun createShare(@Body request: CreateShareRequest): Response<MessageResponse>

    @PUT("api/shares/{id}")
    suspend fun updateShare(@Path("id") id: Long, @Body request: UpdateShareRequest): Response<MessageResponse>

    @DELETE("api/shares/{id}")
    suspend fun deleteShare(@Path("id") id: Long): Response<MessageResponse>

    @POST("api/shares/{id}/like")
    suspend fun likeShare(@Path("id") id: Long): Response<LikeResponse>

    @POST("api/shares/{id}/download")
    suspend fun recordShareDownload(@Path("id") id: Long): Response<MessageResponse>

    // ============ 收藏 & 阅读历史 ============

    @GET("api/bookmarks")
    suspend fun getBookmarks(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<BookmarksResponse>

    @GET("api/bookmarks/check/{articleId}")
    suspend fun checkBookmark(@Path("articleId") articleId: Long): Response<BookmarkCheckResponse>

    @POST("api/bookmarks/{articleId}")
    suspend fun toggleBookmark(@Path("articleId") articleId: Long): Response<BookmarkToggleResponse>

    @GET("api/bookmarks/history")
    suspend fun getReadingHistory(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<HistoryResponse>

    @POST("api/bookmarks/history/{articleId}")
    suspend fun recordHistory(@Path("articleId") articleId: Long): Response<MessageResponse>

    @DELETE("api/bookmarks/history")
    suspend fun clearHistory(): Response<MessageResponse>

    // ============ 关注 ============

    @POST("api/follows/{userId}")
    suspend fun toggleFollow(@Path("userId") userId: Long): Response<FollowToggleResponse>

    @GET("api/follows/check/{userId}")
    suspend fun checkFollow(@Path("userId") userId: Long): Response<FollowCheckResponse>

    @GET("api/follows/{userId}/following")
    suspend fun getFollowing(
        @Path("userId") userId: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<FollowListResponse>

    @GET("api/follows/{userId}/followers")
    suspend fun getFollowers(
        @Path("userId") userId: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<FollowListResponse>

    // ============ 公开资料 ============

    @GET("api/follows/profile/{userId}")
    suspend fun getPublicProfile(@Path("userId") userId: Long): Response<PublicProfileResponse>

    @GET("api/follows/profile/{userId}/discussions")
    suspend fun getUserDiscussions(
        @Path("userId") userId: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<DiscussionsResponse>

    @GET("api/follows/profile/{userId}/snippets")
    suspend fun getUserSnippets(
        @Path("userId") userId: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<SnippetsResponse>

    @GET("api/follows/profile/{userId}/shares")
    suspend fun getUserShares(
        @Path("userId") userId: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<SharesResponse>
}
