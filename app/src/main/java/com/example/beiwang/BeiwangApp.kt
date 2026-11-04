package com.example.beiwang

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.InvalidationTracker
import com.example.beiwang.data.AuthManager
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.remote.NetworkModule
import com.example.beiwang.data.sync.RealtimeSync
import com.example.beiwang.data.sync.SyncManager
import com.example.beiwang.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 应用入口。比任何 Activity 先创建，负责：
 *  - 初始化 AuthManager（加密登录态存储）；
 *  - 注册周期性后台同步；
 *  - 已登录时拉起一次即时同步 + 建立实时同步 WebSocket；
 *  - 登出时断开实时连接；
 *  - 监听 App 前后台切换：回到前台立即重连 WS + 全量对账，并在前台期间做安全轮询，
 *    保证「网页后台增删改 → 手机 App 内部与桌面组件」实时同步（核心修复点）；
 *  - 监听 Room 数据库变更：任意写库（本地增删 / 同步写回）都自动刷新桌面组件，
 *    不再依赖各自写路径手动调用（彻底终结「组件漏刷新」）。
 */
class BeiwangApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        AuthManager.init(this)
        NetworkModule.init(this)
        SyncManager.schedulePeriodic(this)
        if (AuthManager.isLoggedIn) {
            SyncManager.requestSync(this)
            RealtimeSync.connect(this)
        } else {
            // 冷启动未登录（如服务器会话已过期）但「记住我」有凭证：后台静默自动重登，
            // 让用户无需手动登录即可恢复（loginSignal 会驱动 UI 切回主界面）。
            appScope.launch {
                runCatching { AuthManager.tryAutoLogin() }
            }
        }
        // 登出信号（含 refreshToken 失效 / WS 被踢 / 关闭码 4001）触发时：
        //  1) 断开实时连接；
        //  2) 若「记住我」有可用凭证，尝试静默自动重登挽回（成功则重建同步/WS）。
        appScope.launch {
            AuthManager.logoutSignal.collect {
                RealtimeSync.disconnect()
                val restored = runCatching { AuthManager.tryAutoLogin() }.getOrDefault(false)
                if (restored && AuthManager.isLoggedIn) {
                    SyncManager.requestSync(applicationContext)
                    RealtimeSync.connect(applicationContext)
                    RealtimeSync.noteLocalSync()
                }
            }
        }
        // 登录成功信号（手动或自动重登）：建立同步与实时连接（冷启动自动重登的路径也会经此补齐）。
        appScope.launch {
            AuthManager.loginSignal.collect {
                if (AuthManager.isLoggedIn) {
                    SyncManager.requestSync(applicationContext)
                    RealtimeSync.connect(applicationContext)
                    RealtimeSync.noteLocalSync()
                }
            }
        }
        // 网络恢复时立即同步：断网期间新增的本地数据，重连瞬间即可秒级推送到后台，
        // 无需等待 WorkManager 的联网约束延迟（此前重连后需 ~5s 才同步）。
        registerNetworkSync()
        // 核心修复 1：App 回到前台就追上后台改动（重连 WS + 立即全量对账），
        // 前台期间再兜底轮询，避免 WS 在真机后台/Doze 下被静默掐掉后长期失联。
        registerForegroundSync()
        // 核心修复 2：数据库任意变更都自动刷新桌面组件（本地增删 + 同步写回全覆盖）。
        registerWidgetRefreshOnDbChange()
    }

    /**
     * 前后台生命周期驱动的后台同步：
     *  - ON_START（App 进入/回到前台）：先重连 WS（复活被系统掐掉的死连接），再立即做一次
     *    全量对账(forceFull)，让「网页后台的增删改」在用户睁眼看手机的瞬间（~1s 内）反映到
     *    App 内部列表与桌面组件；随后启动前台安全轮询。
     *  - ON_STOP（App 退到后台）：取消前台轮询（省电；后台靠 WS + 15 分钟周期同步兜底）。
     * 说明：RealtimeSync.connect / requestImmediateSync 在「未登录(无 token)」时是安全空操作，
     * 不会因本地登录态而误触发网络。
     */
    private fun registerForegroundSync() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var foregroundPoll: kotlinx.coroutines.Job? = null

            override fun onStart(owner: LifecycleOwner) {
                if (!AuthManager.isLoggedIn) return
                // 复活可能在后台被 Doze/省电静默掐死的 WS；connect 幂等，已连则直接返回。
                RealtimeSync.connect(applicationContext)
                // 立即全量对账：把退到后台期间漏掉的广播（尤其删除墓碑）一次性补回。
                RealtimeSync.requestImmediateSync(applicationContext, forceFull = true)
                // 前台安全轮询：即便 WS 实时通道在中途静默死亡（真机 Doze / 运营商 NAT
                // 掐断长连接且重连间隙），后台的删除/改动也靠此兜底在数秒内显现，用户感知即「实时」。
                // 5s 仍远小于增量水位容差 60s，保证相邻两次轮询的 since 窗口必然重叠、不漏增量。
                foregroundPoll?.cancel()
                foregroundPoll = appScope.launch {
                    while (isActive) {
                        delay(5_000L)
                        if (!AuthManager.isLoggedIn) break
                        RealtimeSync.requestImmediateSync(applicationContext, forceFull = false)
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                foregroundPoll?.cancel()
                foregroundPoll = null
            }
        })
    }

    /**
     * 桌面组件自动刷新：监听 Room 的 [InvalidationTracker]，凡 [TABLES] 表发生写操作
     * （本地增删改、或后台同步写回）即去抖后调用 [WidgetUpdater.refresh]。
     *
     * 为什么需要它：Glance 组件只在被显式 [androidx.glance.appwidget.GlanceAppWidget.update]
     * 时才重绘，不会因 Room Flow 变化自动重组；此前刷新只挂在 [SyncManager.runSync]
     * （网页同步路径）一处，本地增删完全没触发——表现为「App 里删了/加了，组件还停在原处」。
     * 在此统一兜底后，无论哪条写路径（组件内勾选、App 内增删、后台同步），组件都实时跟上。
     */
    private fun registerWidgetRefreshOnDbChange() {
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val observer = object : InvalidationTracker.Observer(TABLES) {
                override fun onInvalidated(tables: Set<String>) {
                    scheduleWidgetRefresh()
                }
            }
            db.invalidationTracker.addObserver(observer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 去抖：把一次操作里连续多张表的写入合并成一次刷新，避免批量写时反复重绘组件。 */
    private var widgetRefreshScheduled = false
    private fun scheduleWidgetRefresh() {
        if (widgetRefreshScheduled) return
        widgetRefreshScheduled = true
        appScope.launch {
            delay(250L)
            widgetRefreshScheduled = false
            WidgetUpdater.refresh(applicationContext)
        }
    }

    private fun registerNetworkSync() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                if (AuthManager.isLoggedIn) {
                    // 网络恢复时不仅重建 WS 实时通道，还要做一次全量对账（forceFull）：
                    // 断网期间断开的 WS 不会自动复活，期间网页后台的删除/改动若漏掉广播，
                    // 增量水位无法补回，必须全量拉一次，确保「网页删了手机也删」不再发生。
                    RealtimeSync.connect(applicationContext)
                    RealtimeSync.requestImmediateSync(applicationContext, forceFull = true)
                }
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        /**
         * 需要驱动桌面组件刷新的数据表。组件渲染标签颜色/名称与备忘录清单项，
         * 故 tags / memo_checklist_items 的增删改也必须触发组件重绘，
         * 否则「手机/网页改了标签或清单，桌面组件不变」。
         */
        private val TABLES = arrayOf("memos", "shopping_items", "tags", "memo_checklist_items")
    }
}
