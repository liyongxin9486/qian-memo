package com.example.beiwang.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 后端 REST 接口。Base URL 来自 BuildConfig.API_BASE_URL。
 *
 * 路由（经 Nginx 反代，域名/路径透传）：
 *   POST /api/auth/register   注册
 *   POST /api/portal/login     登录（统一入口，App 与后台共用，兼容 email）
 *   GET  /api/sync/pull?since 拉取增量
 *   POST /api/sync/push       推送本地变更
 *   GET  /health              健康检查
 *
 * 注意：原 /api/auth/login 已下线，登录统一走 /api/portal/login。
 */
interface ApiService {

    @POST("api/auth/register")
    suspend fun register(@Body body: AuthRequest): AuthResponse

    @POST("api/portal/login")
    suspend fun portalLogin(@Body body: AuthRequest): AuthResponse

    @POST("api/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): RefreshResponse

    @GET("api/sync/pull")
    suspend fun pull(
        @Query("since") since: Long,
        @Query("sid") sid: String? = null
    ): SyncPullResponse

    @POST("api/sync/push")
    suspend fun push(
        @Body body: SyncPushRequest,
        @Query("sid") sid: String? = null
    ): SyncPushResponse

    @GET("health")
    suspend fun health()
}
