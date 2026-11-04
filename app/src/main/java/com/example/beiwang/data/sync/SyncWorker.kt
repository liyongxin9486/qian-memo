package com.example.beiwang.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.beiwang.data.AuthManager

/**
 * 后台/周期同步 Worker：仅作为 WorkManager 的载体，核心逻辑在 [SyncManager.runSync]。
 * 这样 WebSocket 实时触发（[RealtimeSync]）与后台调度共用同一份双向同步实现，避免逻辑分叉。
 */
class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!AuthManager.isLoggedIn) return Result.success()
        // 与发起本次同步的 requestSync 共用同一 sid，确保一次「同步意图」的多次 runSync
        // （即时协程 + 本 Worker 兜底 + 锁补偿重跑）在服务端合并为 1 条计数。
        val sid = inputData.getString("sid")
        val ok = if (sid != null) SyncManager.runSync(applicationContext, sid = sid)
        else SyncManager.runSync(applicationContext)
        return if (ok) Result.success() else Result.retry()
    }
}
