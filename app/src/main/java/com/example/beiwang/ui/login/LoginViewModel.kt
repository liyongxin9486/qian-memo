package com.example.beiwang.ui.login

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.beiwang.data.AuthManager
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.remote.AuthRequest
import com.example.beiwang.data.remote.NetworkModule
import com.example.beiwang.data.sync.RealtimeSync
import com.example.beiwang.data.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 登录/注册 ViewModel：调用后端 API 完成真实认证，成功后持久化 token 并触发首次同步。
 * UI 观察 [success] 来决定跳转，[error] 展示失败原因，[loading] 控制按钮状态。
 */
class LoginViewModel(app: Application) : AndroidViewModel(app) {

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isRegister = MutableStateFlow(false)
    val isRegister: StateFlow<Boolean> = _isRegister.asStateFlow()

    /** 「记住我」：默认开启（与 AuthManager.isRememberMe 一致），登录成功时决定是否持久化凭证。 */
    private val _rememberMe = MutableStateFlow(AuthManager.isRememberMe)
    val rememberMe: StateFlow<Boolean> = _rememberMe.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    fun setEmail(v: String) { _email.value = v }
    fun setPassword(v: String) { _password.value = v }
    fun setRememberMe(v: Boolean) { _rememberMe.value = v }

    /**
     * 登录/注册成功后由 UI 消费一次 [success] 并复位，
     * 避免 ViewModel 因挂在 Activity 作用域被保留时，
     * 退出登录再次进入登录页触发 [success] 又立刻弹回主页。
     */
    fun consumeSuccess() { _success.value = false }

    fun toggleMode() {
        _isRegister.value = !_isRegister.value
        _error.value = null
    }

    fun submit() {
        val em = _email.value.trim()
        val pw = _password.value
        when {
            em.isEmpty() || pw.isEmpty() -> {
                _error.value = "请输入邮箱和密码"
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(em).matches() -> {
                _error.value = "邮箱格式不正确"
                return
            }
            pw.length < 6 -> {
                _error.value = "密码至少需要 6 位"
                return
            }
        }

        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val resp = if (_isRegister.value) {
                    NetworkModule.api.register(AuthRequest(em, pw))
                } else {
                    // 登录统一走 /api/portal/login（/api/auth/login 已下线）
                    NetworkModule.api.portalLogin(AuthRequest(em, pw))
                }
                if (resp.token.isNotEmpty()) {
                    val uid = resp.user?.id?.toString().orEmpty()
                    // 高危修复（多账号隔离）：登录前清空本地库。登出时的清库是异步的，
                    // 可能尚未完成（或登录不经登出流程），此处兜底再清一次，确保新账号
                    // 不会看到/误上传上一个账号的本地缓存；本轮云端数据随后全量拉回。
                    // 幂等：空库清库无副作用。
                    withContext(Dispatchers.IO) { AppDatabase.clearAll(getApplication()) }
                    AuthManager.saveAuth(resp.token, resp.refreshToken, uid, em)
                    // 「记住我」：按勾选状态加密保存或清除登录凭证（含密码），
                    // 供后续服务器会话过期时静默自动重登，避免「记住我也无效、账户自动退出」。
                    AuthManager.saveCredentials(em, _password.value, _rememberMe.value)
                    SyncManager.requestSync(getApplication())
                    RealtimeSync.connect(getApplication())
                    RealtimeSync.noteLocalSync()
                    _success.value = true
                } else {
                    _error.value = "认证失败：服务端未返回令牌"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "网络异常，请稍后重试"
            } finally {
                _loading.value = false
            }
        }
    }
}
