package com.example.beiwang.data.sync

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.beiwang.BuildConfig
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.atomic.AtomicLong
import com.example.beiwang.data.AuthManager
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.remote.NetworkModule
import com.example.beiwang.data.remote.SyncPushRequest
import com.example.beiwang.data.remote.toDto
import com.example.beiwang.data.remote.toEntity
import com.example.beiwang.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

/**
 * 同步调度器：
 *  - schedulePeriodic：每 15 分钟在联网时做一次后台同步（WorkManager 最低间隔）。
 *  - requestSync：经 WorkManager 立即触发一次单次同步（注册/登录后、本地数据变更时调用，
 *    适合"上传"方向，且在 App 退到后台也能被调度执行）。
 *  - runSync：核心双向同步逻辑（推送本地未同步项 + 拉取增量写回），suspend 函数。
 *    供 [SyncWorker]（后台/周期）与 [RealtimeSync]（WebSocket 实时触发）共用，保证两条路径逻辑一致。
 */
object SyncManager {
    private const val PERIODIC = "beiwang_sync_periodic"
    private const val ONE_TIME = "beiwang_sync_onetime"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** 串行锁：避免 WebSocket 实时触发与 WorkManager 周期/手动触发并发读写同一份数据。 */
    private val syncMutex = Mutex()

    /**
     * 全量对账补偿标记：forceFull(断连恢复全量拉取)请求若因锁被跳过，置此标记；
     * 当前同步结束后补跑一次，确保断连期间漏掉的删除/改动不丢失（否则会与增量同步
     * 竞争锁被静默丢弃，正是「网页删了手机还在」的残余根因）。
     */
    @Volatile
    private var pendingForceSid: String? = null

    /**
     * 普通增量同步补偿标记：与 [pendingForceSid] 同理，但用于「非全量」的同步请求
     * （本地增删触发的上传、前台 30s 兜底轮询等）。这些请求若因锁被跳过，记下其 sid，
     * 待当前同步结束后补跑一次（仍用同一 sid），确保本地未同步项不会因被静默丢弃而延迟到
     * 下一轮周期/轮询（此前表现为「手机新增/删除要等最长 30s 才同步到网页后台」）。
     * 补偿重跑沿用同一 sid，服务端会把同一次「同步意图」合并为 1 条计数，避免重复计数。
     */
    @Volatile
    private var pendingSid: String? = null

    /** 为每个「同步意图」生成唯一 sid，由请求端（requestSync/WS 事件）创建，
     *  同一次意图内的多次 runSync（即时协程 + WorkManager 兜底 + 锁补偿重跑）共用，
     *  服务端据 sid 把 push/pull/重跑合并为 1 条「今日同步/累计同步」计数。 */
    private val sidCounter = AtomicLong(0)
    fun newSyncId(): String = "s" + System.currentTimeMillis() + "_" + sidCounter.incrementAndGet()

    /** 补偿重跑专用作用域（应用生命周期单例，无需取消）。 */
    private val retryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 即时同步专用作用域（前台本地改动走此路径，绕过 WorkManager 调度延迟）。 */
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var immediateJob: Job? = null
    private const val IMMEDIATE_DEBOUNCE_MS = 300L

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    /**
     * 即时同步快路径：前台本地增删改后调用，直接经协程跑 [runSync]（带 300ms 去抖、绕过
     * WorkManager 调度延迟），让改动基本秒级推送到网页后台；同时保留下方 WorkManager 的
     * 持久路径作为兜底（App 被杀也由系统重传），[runSync] 幂等 + 串行锁保证二者安全。
     */
    fun requestImmediate(context: Context, sid: String) {
        if (AuthManager.token.isEmpty()) return
        immediateJob?.cancel()
        immediateJob = syncScope.launch {
            delay(IMMEDIATE_DEBOUNCE_MS)
            runSync(context.applicationContext, sid = sid)
        }
    }

    fun requestSync(context: Context) {
        if (AuthManager.token.isEmpty()) return
        // 一次「同步意图」只生成一个 sid，前台即时快路径与后台 WorkManager 兜底共用，
        // 服务端据此把本次的 push/pull/补偿重跑合并为 1 条计数，避免一次操作被记成多条。
        val sid = newSyncId()
        requestImmediate(context, sid)
        val builder = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("sid" to sid))
        // 加急执行：桌面组件点击等"本地改动需即时上传"的场景，即便 App 已退出后台/被系统回收，
        // WorkManager 也会以独立调度尽快把 UNSYNCED 项推送到网页后台（不依赖 App 进程存活）。
        // 配额耗尽时回退为普通约束任务（仍带联网约束），保证最终一致。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        val request = builder.build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME, ExistingWorkPolicy.REPLACE, request
        )
    }

    /**
     * 防回收站复活：本地已软删除（deletedAt 非 0/非 null）时，服务端可能返回"未删除"版本
     * （删除因 LWW 冲突未被应用、或删除时存在时钟偏差导致服务端仍视为活跃）。此时仅当
     * 服务端版本时间【严格新于】本机删除时间（视为另一设备/后台在更晚时间还原了该条）
     * 才接受复活；否则返回 true 跳过该 DTO，保留本地删除态。
     *
     * 背景：本地软删除经推送后会被标为 SYNCED，而下方拉取合并只保护 UNSYNCED 本地改动，
     * 对"已删除(SYNCED)"的行没有保护。未被服务端应用的删除经 @Insert(REPLACE) 写回即复活，
     * 表现为「一手动同步，上次删的又都回来了」。此守卫在本地删除时间不旧于服务端版本时阻止复活。
     */
    private fun keepLocalDeletion(
        localDeletedAt: Long?,
        dtoDeletedAt: Long?,
        dtoUpdatedAt: Long
    ): Boolean {
        if (localDeletedAt == null || localDeletedAt == 0L) return false
        if (dtoDeletedAt != null && dtoDeletedAt != 0L) return false
        // 本地已删除而服务端返回"未删除"：一律保留本机删除态，阻止回收站复活。
        // 不再用时间比较——删除推送可能因 LWW 冲突/设备时钟偏差被服务端拒绝（未落库），
        // 此时服务端仍是"较新的未删除版本"，时间比较会误判为"另一设备还原"而放行复活。
        // 本地未删除、服务端说已删除（别的设备删除）的情况不在此分支，仍正常应用服务端删除。
        return true
    }

    /**
     * 核心同步（推送本地未同步项 + 拉取增量写回）。
     * 串行执行；若已有同步在跑则直接返回 true（正在跑的那次会覆盖最新 since，无需重复拉取）。
     * @return true=成功（含未登录的无操作、token 失效的停止）；false=需重试（网络等可恢复异常）。
     */
    suspend fun runSync(context: Context, forceFull: Boolean = false, sid: String? = null): Boolean {
        if (!AuthManager.isLoggedIn) return true
        // 同一次「同步意图」共用 sid；未显式传入（如周期同步/独立触发）则生成新 sid。
        val mySid = sid ?: newSyncId()
        // 用 tryLock 真正互斥：原「先 isLocked 判断再 withLock」存在竞态——并发触发
        // （WebSocket 实时 + WorkManager 周期/手动）时，后者可能既没被跳过又重复拉取，造成双重同步。
        // tryLock 立即返回：已锁则直接跳过，未锁则拿到锁并在 finally 中释放。
        // 跳过时若是全量对账请求(forceFull)，记下其 sid，待当前同步结束后补跑（仍用同一 sid），
        // 避免被静默丢弃，且因 sid 相同服务端合并为 1 条计数。
        if (!syncMutex.tryLock()) {
            // 普通请求记 pendingSid，全量对账记 pendingForceSid；当前同步结束后补跑，
            // 避免本地增删的上传请求被静默丢弃（见 [pendingSid] 注释）。二者沿用同一 sid。
            if (forceFull) pendingForceSid = mySid else pendingSid = mySid
            return true
        }
        val ok: Boolean = try {
            withContext(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val memoDao = db.memoDao()
                    val shoppingDao = db.shoppingItemDao()
                    val tagDao = db.tagDao()
                    val todoDao = db.todoDao()
                    val uid = AuthManager.userId

                    // 1) 增量推送（仅待同步项，含软删除墓碑）
                    val unsyncedMemos = memoDao.getUnsyncedForSync()
                    val unsyncedShopping = shoppingDao.getUnsyncedForSync()
                    val unsyncedTodos = todoDao.getUnsyncedForSync()
                    // 标签与清单项现在也按 syncStatus 增量推送（不再用 hasUserChanges 门控）。
                    // 之前用「全量」推送会触发服务端 LWW 回声死循环；改为「仅推送脏数据」后，
                    // push 成功即标记已同步，下一次不再重复推送，循环被彻底断开（见 BUG-1 修复）。
                    val unsyncedTags = tagDao.getUnsyncedForSync()
                    val unsyncedChecklist = memoDao.getUnsyncedChecklistItems()
                    // 已永久删除（清空回收站 / 永久删除单条）的 id 集合：随 push 发给服务端真删墓碑，
                    // 也用于下方 PULL 合并时跳过，双重阻止复活（purged_ids 本地永久保留，不再清理）。
                    val purgedMemos = PurgedIds.memoIds(context)
                    val purgedShopping = PurgedIds.shopIds(context)
                    val purgedTags = PurgedIds.tagIds(context)
                    val purgedTodos = PurgedIds.todoIds(context)
                    val push = SyncPushRequest(
                        memos = unsyncedMemos.map { it.toDto() },
                        shoppingItems = unsyncedShopping.map { it.toDto() },
                        tags = unsyncedTags.map { it.toDto() },
                        checklistItems = unsyncedChecklist.map { it.toDto() },
                        todos = unsyncedTodos.map { it.toDto() },
                        purgedMemoIds = purgedMemos.toList(),
                        purgedShoppingIds = purgedShopping.toList(),
                        purgedTagIds = purgedTags.toList(),
                        purgedTodoIds = purgedTodos.toList()
                    )
                    val resp = NetworkModule.api.push(push, mySid)
                    // 仅把服务端真正 applied 的本地项标记为已同步（BUG-4：LWW 冲突落败的项
                    // 仍在 UNSYNCED，下次重试，避免被静默丢弃）。applied 形如 "memo:<id>"。
                    val applied = resp.applied.toSet()
                    memoDao.markSynced(unsyncedMemos.filter { "memo:${it.id}" in applied }.map { it.id })
                    shoppingDao.markSynced(unsyncedShopping.filter { "shop:${it.id}" in applied }.map { it.id })
                    tagDao.markSynced(unsyncedTags.filter { "tag:${it.id}" in applied }.map { it.id })
                    todoDao.markSynced(unsyncedTodos.filter { "todo:${it.id}" in applied }.map { it.id })
                    memoDao.markChecklistSynced(unsyncedChecklist.filter { "chk:${it.id}" in applied }.map { it.id })
                    // 本机确有上传内容时，忽略服务端的回声广播，避免冗余拉取（含标签/清单项/待办）。
                    if (push.memos.isNotEmpty() || push.shoppingItems.isNotEmpty() ||
                        push.tags.isNotEmpty() || push.checklistItems.isNotEmpty() ||
                        push.todos.isNotEmpty()
                    ) {
                        RealtimeSync.noteLocalSync()
                    }

                    // 2) 拉取并写回
                    // 普通增量：since = 上次水位 - 60s 容差；forceFull（WS 重连 / 回到前台 / 联网恢复时）
                    // 时 since=0，做一次全量对账，把断连期间可能漏掉的广播（尤其是删除墓碑）补回来。
                    // 背景：增量水位是「updated_at > 上次同步时间」，一旦某条删除的广播在 WS 断档时漏掉，
                    // 它的 updated_at 就永久低于水位、再也不会被增量拉到 → 表现为「网页删了手机还在」。
                    // 因此断连恢复必须全量对账。下方循环在 hasMore 时按 serverTime 推进水位继续拉，
                    // 直至排空（普通账号单次即返回，大数据量分多页），确保不漏。
                    var since = if (forceFull) 0L
                        else (AuthManager.getLastSync() - 60_000L).coerceAtLeast(0L)
                    var finalServerTime = since
                    // 本地快照：同一 runSync 内串行，库不会被并发改动，页面间可复用。
                    val localMemos = memoDao.getAllForBackup().associateBy { it.id }
                    val localShoppingAll = shoppingDao.getAllForBackup().associateBy { it.id }
                    val localTags = tagDao.getAllForBackup().associateBy { it.id }
                    val localChecklist = memoDao.getAllChecklistItems().associateBy { it.id }
                    val localTodos = todoDao.getAllForBackup().associateBy { it.id }
                    // 已永久删除（清空回收站 / 永久删除单条）的 id 集合 purgedMemos/purgedShopping/
                    // purgedTags 已在上方 push 前读取，此处直接复用：命中即跳过该 DTO，
                    // 阻止服务端墓碑在 PULL 时复活（同步协议无服务端永久删除，删除仅落本地，
                    // 故 purge 时客户端已把 id 推给服务端真删，双保险）。
                    var hasMore = true
                    var page = 0
                    while (hasMore && page < 50) {
                        page++
                        val pull = NetworkModule.api.pull(since, mySid)
                        finalServerTime = pull.serverTime

                        // 拉回数据按 LWW 合并：
                        //  - 本地已同步(SYNCED)项：以服务端为准，直接 REPLACE 写回（通常是另一端的改动）。
                        //  - 本地仍有未同步(UNSYNCED)改动且本机 updatedAt 不旧于服务端：保留本机编辑，
                        //    不覆盖（下次重试上传），避免「离线编辑被服务端旧版本静默覆盖」导致数据丢失。
                        //  - 本地 UNSYNCED 但服务端版本更新（updatedAt 更大，如另一设备/后台改得更晚）：
                        //    接受服务端，覆盖本机。
                        val mergedMemos = pull.memos.mapNotNull { dto ->
                            // 已永久删除（清空回收站/永久删除）：跳过，阻止服务端墓碑复活。
                            if (dto.id in purgedMemos) {
                                Log.d("SyncDebug", "memo ${dto.id} SKIP(purged)")
                                return@mapNotNull null
                            }
                            val local = localMemos[dto.id]
                            if (local != null) {
                                // 本地未同步改动且本机不旧：保留本机编辑（不覆盖）。
                                if (local.syncStatus == "UNSYNCED" && dto.updatedAt <= local.updatedAt) {
                                    Log.d("SyncDebug", "memo ${dto.id} keepLocal(UNSYNCED) loc.del=${local.deletedAt} dto.del=${dto.deletedAt} dto.upd=${dto.updatedAt}")
                                    return@mapNotNull null
                                }
                                // 防回收站复活：本地已删除而服务端返回"未删除"且非更晚还原时，保留删除态。
                                if (keepLocalDeletion(local.deletedAt, dto.deletedAt, dto.updatedAt)) {
                                    Log.d("SyncDebug", "memo ${dto.id} KEEP_DELETED loc.del=${local.deletedAt} dto.del=${dto.deletedAt} dto.upd=${dto.updatedAt}")
                                    return@mapNotNull null
                                }
                                if (local.deletedAt != null && local.deletedAt != 0L) {
                                    Log.d("SyncDebug", "memo ${dto.id} RESURRECT? loc.del=${local.deletedAt} dto.del=${dto.deletedAt} dto.upd=${dto.updatedAt} loc.upd=${local.updatedAt}")
                                }
                            }
                            dto.toEntity(uid)
                        }
                        memoDao.insertAllMemos(mergedMemos)

                        // 购物项优先级合并：服务端返回 1..3 以服务端为准（跨设备一致）；
                        // 哨兵 0（旧库无 priority 列）沿用本地已设优先级，避免「手机设了 P1 一同步被重置成 P3」。
                        val mergedShopping = pull.shoppingItems.mapNotNull { dto ->
                            // 已永久删除：跳过，阻止服务端墓碑复活。
                            if (dto.id in purgedShopping) return@mapNotNull null
                            val local = localShoppingAll[dto.id]
                            val entity = if (dto.priority in 1..3) dto.toEntity(uid)
                            else dto.toEntity(uid).copy(priority = local?.priority ?: 3)
                            if (local != null) {
                                if (local.syncStatus == "UNSYNCED" && dto.updatedAt <= local.updatedAt) return@mapNotNull null
                                if (keepLocalDeletion(local.deletedAt, dto.deletedAt, dto.updatedAt)) return@mapNotNull null
                            }
                            entity
                        }
                        shoppingDao.insertAllItems(mergedShopping)

                        // 标签/清单项以 REPLACE 写回（pull 已含软删除墓碑 deletedAt）：
                        // 服务端删了的带 deletedAt 回来 → 本地同步隐藏，实现跨设备/后台删除。
                        val mergedTags = pull.tags.mapNotNull { dto ->
                            // 已永久删除：跳过，阻止服务端墓碑复活。
                            if (dto.id in purgedTags) return@mapNotNull null
                            val local = localTags[dto.id]
                            if (local != null) {
                                if (local.syncStatus == "UNSYNCED" && dto.updatedAt <= local.updatedAt) return@mapNotNull null
                                if (keepLocalDeletion(local.deletedAt, dto.deletedAt, dto.updatedAt)) return@mapNotNull null
                            }
                            dto.toEntity(uid)
                        }
                        tagDao.insertAllTags(mergedTags)

                        val mergedChecklist = pull.checklistItems.mapNotNull { dto ->
                            // 父备忘被永久删除时其清单项成孤儿，一并丢弃，阻止服务端墓碑复活。
                            if (dto.memoId in purgedMemos) return@mapNotNull null
                            val local = localChecklist[dto.id]
                            if (local != null) {
                                if (local.syncStatus == "UNSYNCED" && dto.updatedAt <= local.updatedAt) return@mapNotNull null
                                if (keepLocalDeletion(local.deletedAt, dto.deletedAt, dto.updatedAt)) return@mapNotNull null
                            }
                            dto.toEntity()
                        }
                        memoDao.insertAllChecklistItems(mergedChecklist)

                        // 待办：与备忘/购物同一套 LWW 规则（本地 UNSYNCED 且本机不旧则保留；
                        // 永久删除/回收站复活守卫同样生效）。
                        val mergedTodos = pull.todos.mapNotNull { dto ->
                            if (dto.id in purgedTodos) return@mapNotNull null
                            val local = localTodos[dto.id]
                            if (local != null) {
                                if (local.syncStatus == "UNSYNCED" && dto.updatedAt <= local.updatedAt) return@mapNotNull null
                                if (keepLocalDeletion(local.deletedAt, dto.deletedAt, dto.updatedAt)) return@mapNotNull null
                            }
                            dto.toEntity(uid)
                        }
                        todoDao.insertAllTodos(mergedTodos)

                        // 推进水位继续拉下一页；空页且仍 hasMore 说明已无更多，跳出避免死循环。
                        hasMore = pull.hasMore
                        since = pull.serverTime
                        if (pull.memos.isEmpty() && pull.shoppingItems.isEmpty() &&
                            pull.tags.isEmpty() && pull.checklistItems.isEmpty() &&
                            pull.todos.isEmpty()
                        ) break
                    }

                    // 3) 记录同步水位：用服务端返回的 serverTime，与后台/管理员编辑保持同一时钟基准；
                    //    全量对账后水位亦设为本次拉到的最大 updated_at，后续增量从此继续，不会重复全量。
                    //    拉取本身幂等（REPLACE + 本地 UNSYNCED 保护），重复拉取安全。
                    AuthManager.setLastSync(finalServerTime)
                    true
                } catch (e: Exception) {
                    // 401 说明 token 已无法恢复（Authenticator 已尝试刷新并失败/已登出），
                    // 不再无限重试，避免空转耗电；其余异常返回 false 让调用方退避重试。
                    val code = (e as? HttpException)?.code()
                    if (code == 401) {
                        true
                    } else {
                        e.printStackTrace()
                        false
                    }
                }
            }
        } finally {
            syncMutex.unlock()
        }
        // 断连恢复的全量对账请求若在竞争中被跳过，这里补偿重跑一次，确保不漏删除。
        // 普通同步（本地增删上传 / 前台轮询）被跳过则补跑一次，消除「最长等 30s 才同步」的延迟。
        // 二者优先级：forceFull 优先（它已含推送本地未同步项），否则补跑普通同步。
        if (ok && pendingForceSid != null) {
            val s = pendingForceSid!!
            pendingForceSid = null
            retryScope.launch { runSync(context.applicationContext, forceFull = true, sid = s) }
        } else if (ok && pendingSid != null) {
            val s = pendingSid!!
            pendingSid = null
            retryScope.launch { runSync(context.applicationContext, forceFull = false, sid = s) }
        }
        // 网页后台/另一设备的改动已写回本地库，但桌面组件（Glance）不会因数据库变化
        // 自动重绘——它只在被显式 update() 时才刷新。因此同步成功后主动刷新组件，
        // 使"网页后台增删改查 → 桌面组件"实时生效（App 内能看到，组件此前看不到）。
        if (ok) {
            WidgetUpdater.refresh(context.applicationContext)
        }
        return ok
    }
}
