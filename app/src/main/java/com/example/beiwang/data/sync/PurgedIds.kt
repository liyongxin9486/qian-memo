package com.example.beiwang.data.sync

import android.content.Context
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.entity.PurgedIdEntity

/**
 * 已永久删除 id 的本地记录。
 * 永久删除（清空回收站 / 永久删除单条）时记入；SyncManager 拉取合并时读取，
 * 命中记录的 DTO 一律跳过，阻止服务端墓碑复活。
 *
 * 仅依赖 AppDatabase 单例，仓库与同步逻辑统一经此访问，避免在各处重复持有 dao。
 */
object PurgedIds {
    private fun dao(ctx: Context) =
        AppDatabase.getDatabase(ctx.applicationContext).purgedIdDao()

    suspend fun mark(ctx: Context?, id: String, type: String) {
        if (ctx == null) return
        runCatching { dao(ctx).insert(PurgedIdEntity(id, type, System.currentTimeMillis())) }
    }

    suspend fun markAll(ctx: Context?, ids: List<String>, type: String) {
        if (ctx == null || ids.isEmpty()) return
        runCatching {
            dao(ctx).insertAll(ids.map { PurgedIdEntity(it, type, System.currentTimeMillis()) })
        }
    }

    suspend fun memoIds(ctx: Context?): Set<String> =
        if (ctx == null) emptySet()
        else runCatching { dao(ctx).getIdsByType("memo").toSet() }.getOrDefault(emptySet())

    suspend fun shopIds(ctx: Context?): Set<String> =
        if (ctx == null) emptySet()
        else runCatching { dao(ctx).getIdsByType("shop").toSet() }.getOrDefault(emptySet())

    suspend fun tagIds(ctx: Context?): Set<String> =
        if (ctx == null) emptySet()
        else runCatching { dao(ctx).getIdsByType("tag").toSet() }.getOrDefault(emptySet())

    suspend fun todoIds(ctx: Context?): Set<String> =
        if (ctx == null) emptySet()
        else runCatching { dao(ctx).getIdsByType("todo").toSet() }.getOrDefault(emptySet())

    /**
     * 恢复备份时调用：被主动恢复的 id 应从永久删除记录中移除，
     * 否则同一次推送既带该 id（UNSYNCED）又带 purgedMemoIds=[该 id]，
     * 服务端会先 upsert 再真删，导致恢复内容永远到不了网页后台。
     */
    suspend fun remove(ctx: Context?, ids: List<String>, type: String) {
        if (ctx == null || ids.isEmpty()) return
        runCatching { dao(ctx).deleteByIds(type, ids) }
    }
}
