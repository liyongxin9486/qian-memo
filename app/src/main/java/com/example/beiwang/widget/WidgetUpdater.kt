package com.example.beiwang.widget

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 桌面组件刷新助手：主动触发所有已放置的组件实例重新渲染。
 *
 * 为什么需要它：RemoteViews 组件不会因 Room 数据库变化而自动重绘，只在被显式
 * update 时才刷新。同步核心 [com.example.beiwang.data.sync.SyncManager.runSync] 把
 * 网页后台/另一设备的改动写回本地库后必须显式刷新，否则「网页后台增删改查 → 桌面组件」
 * 这条反向链路毫无反应（App 内能看到，组件看不到）。
 *
 * 实现：广播 ACTION_APPWIDGET_UPDATE 给 [BeiwangAppWidgetProvider]，由系统回调 onUpdate
 * 重建 RemoteViews 并 notifyAppWidgetViewDataChanged 重新绑定三看板数据。
 * 组件未放置时 getAppWidgetIds 返回空，直接返回，不会报错。
 */
object WidgetUpdater {
    suspend fun refresh(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                BeiwangAppWidgetProvider.refreshAll(context.applicationContext)
            }
        }
    }
}
