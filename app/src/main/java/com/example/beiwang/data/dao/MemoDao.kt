package com.example.beiwang.data.dao

import androidx.room.*
import com.example.beiwang.data.entity.MemoChecklistItemEntity
import com.example.beiwang.data.entity.MemoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos WHERE deletedAt IS NULL ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllMemos(): Flow<List<MemoEntity>>
    
    @Query("SELECT * FROM memos WHERE deletedAt IS NULL AND isCompleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getPendingMemos(): Flow<List<MemoEntity>>
    
    @Query("SELECT * FROM memos WHERE deletedAt IS NULL AND isCompleted = 1 ORDER BY isPinned DESC, updatedAt DESC")
    fun getCompletedMemos(): Flow<List<MemoEntity>>
    
    @Query("SELECT * FROM memos WHERE deletedAt IS NULL AND syncStatus = 'UNSYNCED'")
    fun getUnsyncedMemos(): Flow<List<MemoEntity>>
    
    @Query("SELECT * FROM memos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeletedMemos(): Flow<List<MemoEntity>> // 回收站
    
    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getMemoById(id: String): MemoEntity?
    
    @Query("SELECT * FROM memos WHERE deletedAt IS NULL AND (title LIKE '%' || :query || '%' ESCAPE '\\' OR content LIKE '%' || :query || '%' ESCAPE '\\')")
    fun searchMemos(query: String): Flow<List<MemoEntity>>
    
    @Query("SELECT COUNT(*) FROM memos WHERE deletedAt IS NULL")
    fun getTotalCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM memos WHERE deletedAt IS NULL AND isCompleted = 0")
    fun getPendingCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM memos WHERE deletedAt IS NULL AND isCompleted = 1")
    fun getCompletedCount(): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memo: MemoEntity): Long

    @Query("SELECT * FROM memos")
    suspend fun getAllForBackup(): List<MemoEntity>

    // 增量同步：仅取待推送（含软删除墓碑，softDelete/restore 会将其标为 UNSYNCED）
    @Query("SELECT * FROM memos WHERE syncStatus = 'UNSYNCED'")
    suspend fun getUnsyncedForSync(): List<MemoEntity>

    // push 成功后把这些记录标记为已同步
    @Query("UPDATE memos SET syncStatus = 'SYNCED' WHERE syncStatus = 'UNSYNCED'")
    suspend fun markAllSynced()

    // push 成功后仅把服务端 applied 的记录标记为已同步（BUG-4：LWW 落败的保持 UNSYNCED 下次重试）
    @Query("UPDATE memos SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMemos(list: List<MemoEntity>)

    @Update
    suspend fun update(memo: MemoEntity)
    
    @Query("UPDATE memos SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE memos SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id IN (:ids)")
    suspend fun softDeleteMemos(ids: List<String>, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE memos SET deletedAt = NULL, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE memos SET isCompleted = :isCompleted, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun updateCompletionStatus(id: String, isCompleted: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE memos SET isPinned = :isPinned, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun updatePin(id: String, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE memos SET isFavorite = :isFavorite, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean, updatedAt: Long = System.currentTimeMillis())
    
    @Delete
    suspend fun permanentDelete(memo: MemoEntity)
    
    @Query("DELETE FROM memos WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun deleteOldTrashItems(threshold: Long) // 30天自动清理
    
    @Query("DELETE FROM memos WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("SELECT id FROM memos WHERE deletedAt IS NOT NULL")
    suspend fun getDeletedMemoIds(): List<String>

    @Query("SELECT id FROM memos WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun getDeletedMemoIdsOlderThan(threshold: Long): List<String>

    // 备忘录清单项
    @Query("SELECT * FROM memo_checklist_items WHERE memoId = :memoId AND deletedAt IS NULL ORDER BY position ASC")
    fun getChecklistItems(memoId: String): Flow<List<MemoChecklistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklistItem(item: MemoChecklistItemEntity)

    @Update
    suspend fun updateChecklistItem(item: MemoChecklistItemEntity)

    @Query("UPDATE memo_checklist_items SET content = :content, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun updateChecklistItemContent(id: String, content: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE memo_checklist_items SET isChecked = :isChecked, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun updateChecklistItemChecked(id: String, isChecked: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteChecklistItem(item: MemoChecklistItemEntity)

    @Query("DELETE FROM memo_checklist_items WHERE memoId = :memoId")
    suspend fun deleteChecklistItemsByMemo(memoId: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM memo_checklist_items WHERE memoId = :memoId")
    suspend fun getNextChecklistPosition(memoId: String): Int

    // 同步用：全量读取/写入清单项
    @Query("SELECT * FROM memo_checklist_items")
    suspend fun getAllChecklistItems(): List<MemoChecklistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllChecklistItems(list: List<MemoChecklistItemEntity>)

    // ---- 清单项同步 / 软删除 ----
    @Query("SELECT * FROM memo_checklist_items WHERE syncStatus = 'UNSYNCED'")
    suspend fun getUnsyncedChecklistItems(): List<MemoChecklistItemEntity>

    @Query("UPDATE memo_checklist_items SET syncStatus = 'SYNCED' WHERE syncStatus = 'UNSYNCED'")
    suspend fun markAllChecklistSynced()

    @Query("UPDATE memo_checklist_items SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markChecklistSynced(ids: List<String>)

    @Query("UPDATE memo_checklist_items SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun softDeleteChecklistItem(id: String, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE memo_checklist_items SET deletedAt = NULL, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun restoreChecklistItem(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE memo_checklist_items SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE memoId = :memoId")
    suspend fun softDeleteChecklistItemsByMemo(memoId: String, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM memo_checklist_items WHERE id IN (:ids)")
    suspend fun hardDeleteChecklistByIds(ids: List<String>)
}
