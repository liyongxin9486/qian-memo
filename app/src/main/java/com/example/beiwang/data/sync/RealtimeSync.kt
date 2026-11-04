package com.example.beiwang.data.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.beiwang.BuildConfig
import com.example.beiwang.data.AuthManager
import com.example.beiwang.data.remote.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * 实时同步客户端：与后端 `/ws?token=<JWT>` 建立长连接（复用 NetworkModule 的证书锁定 OkHttp 客户端）。
 *
 * - 收到 `{type:"sync"}` 即经 [triggerSync] 直接调用 [SyncManager.runSync]（带 600ms 防抖、不走 WorkManager），实现
 *   "网页端改动 → 手机亚秒级刷新 / 手机改动 → 网页即时刷新"。
 * - 自动重连（指数退避）；token 失效（服务端关闭码 4001）时停止重连。
 * - 本机主动上传数据后，服务端会把回声广播回来；回声抑制由 [SyncManager.runSync]
 *   在确有上传时调用 [noteLocalSync] 完成，网页侧的连续改动不会再被误吞。
 */
object RealtimeSync {
    private const val TAG = "RealtimeSync"
    private const val COOLDOWN_MS = 2000L
    private const val REALTIME_DEBOUNCE_MS = 600L

    private var webSocket: WebSocket? = null
    private var appContext: Context? = null
    private var reconnectJob: Job? = null
    private var realtimeJob: Job? = null
    private var stopped = false
    private var isConnecting = false
    private var lastLocalSync = 0L

    /** 复用的协程作用域：triggerSync / scheduleReconnect 都在此启动，disconnect 时取消其子任务，
     *  避免「每次触发都 new CoroutineScope」造成的作用域泄漏。SupervisorJob 保证单任务失败不影响其他。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 已登录时调用，建立实时连接。重复调用安全（已连接/正在连接则直接返回）。 */
    fun connect(context: Context) {
        appContext = context.applicationContext
        stopped = false
        ensureConnect()
    }

    /** 确保已建立连接：已连接或正在连接则直接返回，避免重复建连造成多 socket 竞态。 */
    private fun ensureConnect() {
        if (webSocket != null || isConnecting || stopped) return
        if (AuthManager.token.isEmpty()) {
            Log.d(TAG, "connect skipped: no token")
            return
        }
        isConnecting = true
        try {
            open()
        } finally {
            isConnecting = false
        }
    }

    /** 登出/销毁时调用，断开连接并停止重连。 */
    fun disconnect() {
        stopped = true
        reconnectJob?.cancel()
        reconnectJob = null
        realtimeJob?.cancel()
        realtimeJob = null
        runCatching { webSocket?.close(1000, "bye") }
        webSocket = null
    }

    /** 本机主动触发同步（登录、手动刷新等）后调用，标记时间戳以忽略自己的回声。 */
    fun noteLocalSync() {
        lastLocalSync = System.currentTimeMillis()
    }

    /**
     * 网络恢复等场景主动触发一次「即时」同步，绕过 WorkManager 的调度延迟。
     * 用于：断网期间新增的本地数据，在重新联网的瞬间即可秒级推送到后台，
     * 而不必等 15 分钟周期同步或 WorkManager 的联网约束延迟（此前重连后需 ~5s 才同步）。
     * 内部复用 [triggerSync] 的 600ms 防抖 + [SyncManager.runSync] 的串行锁，并发安全。
     */
    fun requestImmediateSync(context: Context, forceFull: Boolean = false) {
        if (AuthManager.token.isEmpty()) return
        triggerSync(context.applicationContext, forceFull)
    }

    private fun wsUrl(): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val scheme = if (base.startsWith("https://")) "wss://" else "ws://"
        val host = base.removePrefix("https://").removePrefix("http://")
        return "$scheme$host/ws?token=${Uri.encode(AuthManager.token)}"
    }

    private fun open() {
        val ctx = appContext ?: return
        if (AuthManager.token.isEmpty()) return
        val client = NetworkModule.webSocketClient()
        val request = Request.Builder().url(wsUrl()).build()
        webSocket = client.newWebSocket(request, listener)
    }

    /**
     * 收到实时事件后触发一次同步。用短期防抖（[REALTIME_DEBOUNCE_MS]）把"短时间内连续多条变更"
     * 合并成一次拉取，避免每条都打一次网络；同时绕开 WorkManager 的调度延迟，
     * 让网页端的改动在亚秒级反映到手机（直接走 [SyncManager.runSync]，与后台同步共用同一逻辑）。
     * [SyncManager.runSync] 自带串行锁，并发触发也不会重复拉取或竞争数据库。
     */
    private fun triggerSync(context: Context, forceFull: Boolean = false) {
        realtimeJob?.cancel()
        realtimeJob = scope.launch {
            delay(REALTIME_DEBOUNCE_MS)
            SyncManager.runSync(context, forceFull)
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WS opened")
            // WS（重）连成功后做一次全量对账（forceFull=true, since=0）：
            // 断连期间可能漏掉服务端广播（尤其是删除墓碑），增量水位无法补回，
            // 必须全量拉一次。首连/重连都安全（拉取幂等、本地 UNSYNCED 受保护）。
            appContext?.let { triggerSync(it, forceFull = true) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // 不打印完整报文（可能含备忘内容等隐私），仅调试期记录收到事件的长度。
            if (BuildConfig.DEBUG) Log.d(TAG, "WS message received (len=${text.length})")
            // 账号被服务器强制下线（如管理员删除账户）：立即登出，无需等待下一次 API 401。
            // 注：AuthManager.clear() 会经 logoutSignal 触发 UI 跳登录页 + RealtimeSync.disconnect() 关闭连接。
            if (text.contains("\"type\"") && text.contains("\"kicked\"")) {
                Log.w(TAG, "kicked by server -> force logout")
                stopped = true
                AuthManager.clear()
                return
            }
            if (text.contains("\"type\"") && text.contains("\"sync\"")) {
                // 回声抑制：lastLocalSync 只在"本机确实上传了数据"时由 SyncManager.runSync
                // 通过 noteLocalSync() 置位，因此这里只会吞掉本机自己的回声广播，
                // 不会再误吞网页侧的连续改动。
                // 旧实现会在「每条收到的广播」上都刷新 lastLocalSync，导致"短时间内连续两条
                // 网页改动"里第二条被误判成自己的回声而丢弃 —— 表现为删除/新增在手机端漏同步。
                val now = System.currentTimeMillis()
                if (now - lastLocalSync < COOLDOWN_MS) {
                    Log.d(TAG, "ignore own echo (cooldown)")
                    return
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "sync broadcast received -> triggerSync")
                appContext?.let { triggerSync(it) }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // 服务端仅发送文本帧，二进制忽略
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WS closed code=$code reason=$reason")
            if (code == 4001) {
                // 服务端判定 token 失效 / 账号被删除（4001）：强制登出，避免「已删除账号还停留在登录态」。
                Log.w(TAG, "WS closed 4001 -> force logout")
                stopped = true
                AuthManager.clear()
                return
            }
            scheduleReconnect(code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "WS failure: ${t.message}")
            scheduleReconnect(-1)
        }
    }

    private fun scheduleReconnect(lastCode: Int) {
        if (stopped) return
        if (lastCode == 4001) {
            Log.d(TAG, "token invalid (4001), stop reconnect")
            stopped = true
            return
        }
        if (AuthManager.token.isEmpty()) {
            Log.d(TAG, "no token, skip reconnect")
            return
        }
        webSocket = null
        reconnectJob?.cancel()
        var attempt = 0
        reconnectJob = scope.launch {
            // 真正的退避重试循环：连接成功（webSocket 非空）或停止/登出才退出；
            // 单次 open 失败由 onFailure 再次进入本循环，避免"只试一次就放弃"。
            while (isActive && !stopped && AuthManager.token.isNotEmpty()) {
                attempt++
                delay(minOf(2000L * attempt, 20000L))
                if (stopped || AuthManager.token.isEmpty()) break
                if (BuildConfig.DEBUG) Log.d(TAG, "reconnect attempt $attempt")
                ensureConnect()
                if (webSocket != null) break
            }
        }
    }
}
