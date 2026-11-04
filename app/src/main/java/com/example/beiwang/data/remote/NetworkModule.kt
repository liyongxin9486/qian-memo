package com.example.beiwang.data.remote

import android.content.Context
import android.util.Log
import com.example.beiwang.R
import com.example.beiwang.BuildConfig
import com.example.beiwang.data.AuthManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * 网络层单例：构建 OkHttp + Retrofit。
 *
 * - 自动在请求头注入 `Authorization: Bearer <token>`（从 AuthManager 读取）。
 * - 通过证书锁定（certificate pinning）信任打包进 App 的服务器自签证书
 *   （res/raw/server_cert），不再信任任意证书，杜绝中间人窃取 JWT。
 *   debug / release 统一走此路径；证书由 init(context) 在 Application 启动时加载。
 * - access token 过期(401)时由 OkHttp Authenticator 自动用 refreshToken 换发并重试；
 *   refreshToken 也失效则清空登录态并发 logoutSignal 让 UI 跳回登录页。
 */
object NetworkModule {
    private const val TAG = "NetworkModule"

    private var appContext: Context? = null

    /** 必须在首次使用 [api] 前调用（在 BeiwangApp.onCreate 中执行）。 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // 主 API（带鉴权拦截 + 自动刷新）
    val api: ApiService by lazy { buildRetrofit(buildOkHttp()).create(ApiService::class.java) }

    // 刷新专用 API：独立 OkHttp（仅证书锁定，无鉴权/无自动刷新），避免 Authenticator 死循环
    val refreshApi: ApiService by lazy { buildRetrofit(buildRefreshOkHttp()).create(ApiService::class.java) }

    private fun buildOkHttp(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = AuthManager.token
                val request = if (token.isNotEmpty()) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            // 401 自动刷新：access token 过期时，用 refreshToken 换发新 token 并重试原请求；
            // 仅当 refresh 端点明确拒绝（401，refreshToken 已失效）时才清空登录态并发
            // logoutSignal 让 UI 跳回登录页；网络异常（超时/断网等）不登出，保留登录态，
            // 让请求方自然失败重试，避免「断网/弱网抖动就把用户踢下线」。
            .authenticator { _: Route?, response: Response ->
                val rt = AuthManager.refreshToken
                if (rt.isEmpty()) return@authenticator null
                try {
                    val newToken = runBlocking { refreshApi.refresh(RefreshRequest(rt)).token }
                    if (newToken.isNotEmpty()) {
                        AuthManager.updateToken(newToken)
                        return@authenticator response.request.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                    }
                } catch (e: retrofit2.HttpException) {
                    // refresh 端点明确拒绝：仅 401（refreshToken 失效/被吊销）才强制登出；
                    // 其余 4xx/5xx 视为服务端暂不可用，保留登录态。
                    if (e.code() == 401) {
                        // 「记住我」且保存了凭证：用账号密码静默重新登录，绕过 refreshToken 过期，
                        // 让「长时间未用 / 后台被杀」后用户仍保持登录，避免被误踢下线。
                        // 自动重登成功则拿新 token 重试原请求；失败（密码已改/账号删除）才真正登出。
                        val creds = AuthManager.getSavedCredentials()
                        if (creds != null) {
                            try {
                                val resp2 = runBlocking {
                                    refreshApi.portalLogin(AuthRequest(creds.first, creds.second))
                                }
                                if (resp2.token.isNotEmpty()) {
                                    AuthManager.saveAuth(
                                        resp2.token,
                                        resp2.refreshToken,
                                        resp2.user?.id?.toString().orEmpty(),
                                        creds.first
                                    )
                                    return@authenticator response.request.newBuilder()
                                        .header("Authorization", "Bearer ${resp2.token}")
                                        .build()
                                }
                            } catch (re: Exception) {
                                Log.w(TAG, "auto re-login failed, force logout", re)
                            }
                        }
                        AuthManager.clear()
                    } else {
                        Log.w(TAG, "refresh rejected (${e.code()}), keep login state")
                    }
                } catch (e: Exception) {
                    // 网络异常：保留登录态，仅让本次请求失败
                    Log.w(TAG, "refresh network error, keep login state", e)
                }
                null
            }

        // 注入打包的服务器证书，作为唯一受信任的 TLS 锚点（保留默认主机名校验）。
        installPinnedCert(builder)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor { Log.d(TAG, it) }
            logging.level = HttpLoggingInterceptor.Level.BASIC
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    /** 仅证书锁定、无鉴权拦截/自动刷新的轻量客户端，专供 refresh 端点使用。 */
    private fun buildRefreshOkHttp(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        installPinnedCert(builder)
        return builder.build()
    }

    /**
     * 实时同步 WebSocket 专用客户端：仅证书锁定（与 REST 共用同一 server_cert 锚点），
     * 带 30s 心跳维持长连接；token 以 URL 查询参数传递，无需鉴权拦截器。
     */
    fun webSocketClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // 长连接：不读超时，靠心跳保活
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
        installPinnedCert(builder)
        return builder.build()
    }

    /**
     * 仅信任 res/raw/server_cert 中的证书，保留默认主机名校验。
     * 服务器证书 SAN 含 IP Address:81.70.63.171，与 baseUrl 一致即可通过校验。
     */
    private fun installPinnedCert(builder: OkHttpClient.Builder) {
        try {
            val ctx = appContext
                ?: throw IllegalStateException("NetworkModule.init(context) 未调用")
            val cf = CertificateFactory.getInstance("X.509")
            val certInput: InputStream = ctx.resources.openRawResource(R.raw.server_cert)
            val cert = certInput.use { cf.generateCertificate(it) as X509Certificate }

            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            keyStore.setCertificateEntry("server", cert)

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)

            val ssl = SSLContext.getInstance("TLS")
            ssl.init(null, tmf.trustManagers, null)
            builder.sslSocketFactory(ssl.socketFactory, tmf.trustManagers[0] as X509TrustManager)
            // 不覆盖 hostnameVerifier，沿用默认（校验 81.70.63.171 与证书 SAN 匹配）
        } catch (e: Exception) {
            Log.e(TAG, "证书锁定初始化失败", e)
            throw e
        }
    }

    private fun buildRetrofit(client: OkHttpClient): Retrofit {
        val gson: Gson = GsonBuilder().create()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
}
