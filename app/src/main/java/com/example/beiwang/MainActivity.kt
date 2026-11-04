package com.example.beiwang

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.beiwang.data.AuthManager
import com.example.beiwang.data.sync.RealtimeSync
import com.example.beiwang.ui.navigation.MainNavigation
import com.example.beiwang.ui.theme.BeiwangTheme
import com.example.beiwang.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {
    // 用 State 持有深链参数，onNewIntent 更新它即可触发重组并导航，
    // 避免 recreate() 重建整个 Activity、丢失内存状态（BUG-8）。
    // 值为 (memoId, seq)：同一条备忘被重复点击时 seq 递增，保证第二次点击也触发重组导航
    private val openMemoIdState = mutableStateOf<Pair<String, Int>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeManager.init(applicationContext)

        // 桌面组件在写入失败时需要发通知提醒用户；Android 13+ 需先授予通知权限。
        // 在首屏申请一次，保证后续组件异常提醒能正常弹出（未授权时 WidgetNotifier 会降级为 Toast）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        openMemoIdState.value = intent?.getStringExtra("OPEN_MEMO_ID")?.let { it to 1 }

        setContent {
            val themeMode by ThemeManager.collectAsState()
            BeiwangTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(openMemoId = openMemoIdState.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newId = intent.getStringExtra("OPEN_MEMO_ID")
        if (newId != null) {
            val seq = (openMemoIdState.value?.second ?: 0) + 1
            openMemoIdState.value = newId to seq
        }
    }

    // 回到前台时主动拉取一次，使后台（管理员）改动即时可见，
    // 不必等 15 分钟周期同步。仅在已登录时触发。
    override fun onResume() {
        super.onResume()
        if (AuthManager.isLoggedIn) {
            // 前台时确保 WS 实时通道存活：若此前因网络切换/Doze/服务端重启而断开，
            // 这里重建连接（已连接则 no-op），使网页后台改动能实时推到手机。
            RealtimeSync.connect(this)
            val now = System.currentTimeMillis()
            // 前台返回最多每 30s 触发一次同步，避免每次切回都全量空转。
            // 改用即时同步（绕过 WorkManager 调度延迟），切回前台可秒级拉取后台改动；
            // 断网恢复也已由 BeiwangApp 的网络回调接管，这里仅兜底"无网络变化"的切回场景。
            if (now - lastSyncAttempt > 30_000) {
                lastSyncAttempt = now
                // 回到前台做一次全量对账（forceFull）：App 在后台期间 WS 可能被系统回收，
                // 期间网页后台的删除/改动若漏掉广播，增量水位无法补回，必须全量拉一次。
                RealtimeSync.requestImmediateSync(this, forceFull = true)
            }
        }
    }

    companion object {
        private var lastSyncAttempt = 0L
    }
}
