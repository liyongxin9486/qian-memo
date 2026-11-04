package com.example.beiwang.data.repository

import com.example.beiwang.widget.WidgetUpdater
import android.content.Context
import com.example.beiwang.data.dao.MemoAttachmentDao
import com.example.beiwang.data.dao.MemoDao
import com.example.beiwang.data.dao.MemoHistoryDao
import com.example.beiwang.data.entity.MemoAttachmentEntity
import com.example.beiwang.data.entity.MemoChecklistItemEntity
import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.entity.MemoHistoryEntity
import com.example.beiwang.data.entity.SyncStatus
import com.example.beiwang.data.sync.PurgedIds
import com.example.beiwang.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class MemoRepository(
    private val memoDao: MemoDao,
    private val context: Context? = null,
    private val attachmentDao: MemoAttachmentDao? = null,
    private val historyDao: MemoHistoryDao? = null
) {
    private suspend fun refreshWidget() {
        context?.let { WidgetUpdater.refresh(it) }
    }
    // 备忘录操作
    fun getAllMemos(): Flow<List<MemoEntity>> = memoDao.getAllMemos()
    
    fun getPendingMemos(): Flow<List<MemoEntity>> = memoDao.getPendingMemos()
    
    fun getCompletedMemos(): Flow<List<MemoEntity>> = memoDao.getCompletedMemos()
    
    fun getUnsyncedMemos(): Flow<List<MemoEntity>> = memoDao.getUnsyncedMemos()
    
    fun getDeletedMemos(): Flow<List<MemoEntity>> = memoDao.getDeletedMemos()
    
    suspend fun getMemoById(id: String): MemoEntity? = memoDao.getMemoById(id)
    
    fun searchMemos(query: String): Flow<List<MemoEntity>> = memoDao.searchMemos(escapeLike(query))
    
    fun getTotalCount(): Flow<Int> = memoDao.getTotalCount()
    
    fun getPendingCount(): Flow<Int> = memoDao.getPendingCount()
    
    fun getCompletedCount(): Flow<Int> = memoDao.getCompletedCount()
    
    suspend fun insertMemo(memo: MemoEntity) {
        // 标记为本机未同步并立即触发同步，保证手机端新建的备忘录实时推送到后台。
        memoDao.insert(memo.copy(
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.UNSYNCED.name
        ))
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }
    
    suspend fun updateMemo(memo: MemoEntity) {
        val existing = memoDao.getMemoById(memo.id)
        val changed = existing == null ||
            existing.copy(updatedAt = 0, syncStatus = "") != memo.copy(updatedAt = 0, syncStatus = "")
        memoDao.update(memo.copy(
            updatedAt = System.currentTimeMillis(),
            syncStatus = if (changed) SyncStatus.UNSYNCED.name else existing?.syncStatus ?: SyncStatus.UNSYNCED.name
        ))
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }
    
    suspend fun softDeleteMemo(id: String) {
        memoDao.softDelete(id)
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }

    // 多选删除：批量软删除（进入回收站），并回写服务端。
    suspend fun softDeleteMemos(ids: List<String>) {
        if (ids.isEmpty()) return
        memoDao.softDeleteMemos(ids)
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun restoreMemo(id: String) {
        // 还原即取消永久删除：必须从 purged_ids 移除，否则下一次 push 会同时带该 id（UNSYNCED）
        // 与 purgedMemoIds=[该 id]，服务端先 upsert 又真删，导致恢复的备忘在网页后台消失。
        PurgedIds.remove(context, listOf(id), "memo")
        memoDao.restore(id)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun updateMemoCompletionStatus(id: String, isCompleted: Boolean) {
        memoDao.updateCompletionStatus(id, isCompleted)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun updatePin(id: String, isPinned: Boolean) {
        memoDao.updatePin(id, isPinned)
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun updateFavorite(id: String, isFavorite: Boolean) {
        memoDao.updateFavorite(id, isFavorite)
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }
    
    suspend fun permanentDeleteMemo(memo: MemoEntity) {
        PurgedIds.mark(context, memo.id, "memo")
        memoDao.permanentDelete(memo)
        // 立即同步，让服务端当场真删墓碑，避免滞留期间被网页/他端反复拉回。
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun deleteOldTrashItems(daysOld: Int = 30) {
        val threshold = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        PurgedIds.markAll(context, memoDao.getDeletedMemoIdsOlderThan(threshold), "memo")
        memoDao.deleteOldTrashItems(threshold)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun emptyTrash() {
        PurgedIds.markAll(context, memoDao.getDeletedMemoIds(), "memo")
        memoDao.emptyTrash()
        // 立即同步：把 purged 的 id 推给服务端真删，否则服务端软删墓碑会滞留，
        // 网页/他端在真删生效前（最长 15 分钟）会反复把它同步回来，表现为「清空后又出现」。
        context?.let { SyncManager.requestSync(it) }
    }

    // 附件操作
    fun getAttachmentsByMemoId(memoId: String): Flow<List<MemoAttachmentEntity>> =
        attachmentDao?.getAttachmentsByMemoId(memoId) ?: emptyFlow()

    suspend fun getAttachmentsByMemoIdOnce(memoId: String): List<MemoAttachmentEntity> =
        attachmentDao?.getAttachmentsByMemoIdOnce(memoId) ?: emptyList()

    suspend fun insertAttachment(attachment: MemoAttachmentEntity) {
        attachmentDao?.insert(attachment)
    }

    suspend fun updateAttachment(attachment: MemoAttachmentEntity) {
        attachmentDao?.update(attachment)
    }

    suspend fun deleteAttachment(attachment: MemoAttachmentEntity) {
        attachmentDao?.delete(attachment)
    }

    suspend fun deleteAttachmentsByMemoId(memoId: String) {
        attachmentDao?.deleteByMemoId(memoId)
    }

    // 历史快照
    fun getHistoryByMemoId(memoId: String): Flow<List<MemoHistoryEntity>> =
        historyDao?.getHistoryByMemoId(memoId) ?: emptyFlow()

    suspend fun getHistoryByMemoIdOnce(memoId: String): List<MemoHistoryEntity> =
        historyDao?.getHistoryByMemoIdOnce(memoId) ?: emptyList()

    suspend fun insertHistory(history: MemoHistoryEntity) {
        historyDao?.insert(history)
    }

    suspend fun deleteHistoryByMemoId(memoId: String) {
        historyDao?.deleteByMemoId(memoId)
    }

    // 清单项操作
    fun getChecklistItems(memoId: String): Flow<List<MemoChecklistItemEntity>> =
        memoDao.getChecklistItems(memoId)

    suspend fun insertChecklistItem(item: MemoChecklistItemEntity) {
        // 新建清单项：标 UNSYNCED + 触发同步，否则不会推到后台（清单项不同步的根因）。
        memoDao.insertChecklistItem(item.copy(syncStatus = SyncStatus.UNSYNCED.name))
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun updateChecklistItem(item: MemoChecklistItemEntity) {
        // 改文字：标 UNSYNCED + 触发同步。
        memoDao.updateChecklistItem(item.copy(
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.UNSYNCED.name
        ))
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun setChecklistItemChecked(id: String, isChecked: Boolean) {
        // 勾选/取消：DAO 查询内已置 UNSYNCED，这里触发同步。
        memoDao.updateChecklistItemChecked(id, isChecked)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun deleteChecklistItem(item: MemoChecklistItemEntity) {
        // 软删除：置墓碑 + UNSYNCED，跨设备同步删除（修复"网页删清单项手机还在"）。
        memoDao.softDeleteChecklistItem(item.id)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun deleteChecklistItemsByMemo(memoId: String) {
        // 备忘录被删时，其清单项也软删除（同步删除），避免孤立项在另一端残留。
        memoDao.softDeleteChecklistItemsByMemo(memoId)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun softDeleteChecklistItemById(id: String) {
        memoDao.softDeleteChecklistItem(id)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun restoreChecklistItem(id: String) {
        memoDao.restoreChecklistItem(id)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun getNextChecklistPosition(memoId: String): Int =
        memoDao.getNextChecklistPosition(memoId)

    /**
     * 转义 LIKE 通配符，保证用户输入中的 % _ \ 被当作普通字符匹配
     * （DAO 侧查询已声明 ESCAPE '\'）。BUG-修复：搜索包含 % 或 _ 的内容不再误匹配所有记录。
     */
    private fun escapeLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
