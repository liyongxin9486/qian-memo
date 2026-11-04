package com.example.beiwang.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.beiwang.data.remote.AuthRequest
import com.example.beiwang.data.remote.NetworkModule
import kotlinx.coroutines.flow.MutableSharedFlow
import retrofit2.HttpException

/**
 * 登录态与同步元数据的持久化存储。
 *
 * 使用 EncryptedSharedPreferences（AndroidX security-crypto），token / 密码等敏感信息
 * 以加密形式落盘，优于明文 SharedPreferences。App 绝不直连数据库，所有请求经后端 API。
 *
 * 设计要点：
 *  - token / refreshToken 为「会话态」，由服务器控制有效期；过期后被服务器拒绝时
 *    （refreshToken 失效 / WS 被踢 / WS 关闭码 4001）触发 [logoutSignal]，UI 跳回登录页。
 *  - 「记住我」会把邮箱/密码也加密持久化（与 token 同处加密存储）。当会话态因服务器
 *    过期而失效时，[tryAutoLogin] 用保存的凭证静默重新登录，使「长时间不用 / 后台被杀」
 *    后用户依然处于登录态——这正是此前「记住我也无效、账户自动退出」的根因修复。
 *  - [clear] 只清会话态（保留记住我凭证），用于被动登出（可被自动重登挽回）；
 *    [forgetLogin] 连凭证一起清，用于用户主动「退出登录」。
 */
object AuthManager {
    private const val FILE = "auth_secure"
    private const val KEY_TOKEN = "token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_LAST_SYNC = "last_sync"

    // 「记住我」持久化：默认开启，使已有用户在服务器会话过期后能被自动重登挽回。
    private const val KEY_REMEMBER_ME = "remember_me"
    private const val KEY_SAVED_EMAIL = "saved_email"
    private const val KEY_SAVED_PASSWORD = "saved_password"

    private lateinit var prefs: SharedPreferences

    /** 强制登出信号：token 刷新彻底失败（且无可用凭证自动重登）时发射，UI 据此跳回登录页。 */
    val logoutSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 登录成功信号：手动登录与自动重登成功后都发射，UI 据此切回主界面并建立同步。 */
    val loginSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * 主动「退出账号」标志（内存态，不持久化）。置位期间抑制 [tryAutoLogin]，
     * 使「退出账号」能真正停留在登录页、不被自动重登抵消。
     * 进程被系统回收重建后该标志自然复位（默认 false），冷启动时 [tryAutoLogin]
     * 即可用已保存的凭证静默重登，落实「记住我 / 保持登录」。
     * 主动退出只清会话态、保留「记住我」凭证，正是此前「记住我也无效」的修复点。
     */
    var isManualLogout: Boolean = false

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val isLoggedIn: Boolean
        get() = ::prefs.isInitialized && token.isNotEmpty()

    val token: String
        get() = if (::prefs.isInitialized) prefs.getString(KEY_TOKEN, "") ?: "" else ""

    val refreshToken: String
        get() = if (::prefs.isInitialized) prefs.getString(KEY_REFRESH, "") ?: "" else ""

    val userId: String
        get() = if (::prefs.isInitialized) prefs.getString(KEY_USER_ID, "") ?: "" else ""

    val email: String
        get() = if (::prefs.isInitialized) prefs.getString(KEY_EMAIL, "") ?: "" else ""

    // ---------- 「记住我」状态与凭证 ----------

    /** 是否勾选了「记住我」。默认 true，让已有用户天然享受到会话自动恢复。 */
    val isRememberMe: Boolean
        get() = if (::prefs.isInitialized) prefs.getBoolean(KEY_REMEMBER_ME, true) else true

    /** 返回已保存的登录凭证（邮箱, 密码）；未保存/未勾选记住我则返回 null。 */
    fun getSavedCredentials(): Pair<String, String>? {
        if (!::prefs.isInitialized || !isRememberMe) return null
        val e = prefs.getString(KEY_SAVED_EMAIL, "") ?: ""
        val p = prefs.getString(KEY_SAVED_PASSWORD, "") ?: ""
        if (e.isEmpty() || p.isEmpty()) return null
        return e to p
    }

    /** 登录成功时按「记住我」保存或清除凭证：勾选则加密保存，取消勾选则彻底清除。 */
    fun saveCredentials(email: String, password: String, rememberMe: Boolean) {
        if (!::prefs.isInitialized) return
        prefs.edit().apply {
            putBoolean(KEY_REMEMBER_ME, rememberMe)
            if (rememberMe) {
                putString(KEY_SAVED_EMAIL, email)
                putString(KEY_SAVED_PASSWORD, password)
            } else {
                remove(KEY_SAVED_EMAIL)
                remove(KEY_SAVED_PASSWORD)
            }
        }.apply()
    }

    fun saveAuth(token: String, refreshToken: String, userId: String, email: String) {
        // 任何一次登录成功（手动/自动重登）都复位「主动退出」标志，恢复自动重登能力。
        isManualLogout = false
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_REFRESH, refreshToken)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .apply()
        loginSignal.tryEmit(Unit)
    }

    /** 刷新成功后仅更新 access token，保留 refreshToken 与登录态。 */
    fun updateToken(token: String) {
        if (::prefs.isInitialized) prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getLastSync(): Long =
        if (::prefs.isInitialized) prefs.getLong(KEY_LAST_SYNC, 0L) else 0L

    fun setLastSync(ts: Long) {
        if (::prefs.isInitialized) prefs.edit().putLong(KEY_LAST_SYNC, ts).apply()
    }

    /**
     * 被动/自动登出（服务器会话失效）：只清会话态，保留「记住我」凭证，
     * 以便上层 [tryAutoLogin] 用凭证静默重登挽回（同账号不会误清本地库）。
     * 注意：这与用户主动「退出登录」不同——后者应用 [forgetLogin]。
     */
    fun clear() {
        if (::prefs.isInitialized) {
            prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_REFRESH)
                .remove(KEY_USER_ID)
                .remove(KEY_EMAIL)
                .apply()
            logoutSignal.tryEmit(Unit)
        }
    }

    /** 用户主动退出登录：连「记住我」凭证一起清除，下次必须手动登录。 */
    fun forgetLogin() {
        if (::prefs.isInitialized) {
            prefs.edit().clear().apply()
        }
        logoutSignal.tryEmit(Unit)
    }

    /**
     * 用保存的凭证静默重新登录（在 IO 线程调用）。
     * 成功：写入新会话态并发 [loginSignal]，返回 true。
     * 失败分两类：
     *  - 凭证确已失效（服务端拒绝/返回空令牌）：清除凭证，返回 false，避免反复重试；
     *  - 网络/超时等瞬时异常：保留凭证，返回 false（下次联网再试，不误删用户登录态）。
     * 前置：调用方需先确保 [init] 已完成。仅当「记住我」且有可用凭证时才尝试。
     */
    suspend fun tryAutoLogin(): Boolean {
        if (!::prefs.isInitialized) return false
        // 主动退出期间不自动重登，停留在登录页（进程重建后此标志自动复位）。
        if (isManualLogout) return false
        val creds = getSavedCredentials() ?: return false
        return try {
            val resp = NetworkModule.api.portalLogin(AuthRequest(creds.first, creds.second))
            if (resp.token.isNotEmpty()) {
                val uid = resp.user?.id?.toString().orEmpty()
                saveAuth(resp.token, resp.refreshToken, uid, creds.first)
                true
            } else {
                // 服务端未返回令牌：凭证已不可用的特征之一，清除避免下次再试。
                forgetCredentials()
                false
            }
        } catch (e: retrofit2.HttpException) {
            // 服务端明确拒绝（如 401 凭证失效/账号被删）：凭证不可用，清除，避免反复静默重试。
            forgetCredentials()
            false
        } catch (e: Exception) {
            // 网络异常/超时等瞬时错误：保留凭证，待联网后重试，绝不因一次离线就让用户重新登录。
            false
        }
    }

    /** 仅清凭证、保留当前会话态（自动重登失败时使用）。 */
    private fun forgetCredentials() {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .remove(KEY_REMEMBER_ME)
            .remove(KEY_SAVED_EMAIL)
            .remove(KEY_SAVED_PASSWORD)
            .apply()
    }
}
