package com.example.beiwang.data.dao

import androidx.room.*
import com.example.beiwang.data.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    // 仅返回未删除的标签（软删除的进入回收站，不在此列）
    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY sortOrder, name")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY sortOrder, name")
    suspend fun getAllTagsOnce(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTagById(id: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM tags")
    suspend fun getAllForBackup(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeletedTags(): Flow<List<TagEntity>>

    // 同步用：仅取待推送（含软删除墓碑，softDelete 会将其标为 UNSYNCED）
    @Query("SELECT * FROM tags WHERE syncStatus = 'UNSYNCED'")
    suspend fun getUnsyncedForSync(): List<TagEntity>

    @Query("UPDATE tags SET syncStatus = 'SYNCED' WHERE syncStatus = 'UNSYNCED'")
    suspend fun markAllSynced()

    @Query("UPDATE tags SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("UPDATE tags SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tags SET deletedAt = NULL, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM tags WHERE id IN (:ids)")
    suspend fun hardDeleteByIds(ids: List<String>)

    @Query("DELETE FROM tags WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("SELECT id FROM tags WHERE deletedAt IS NOT NULL")
    suspend fun getDeletedTagIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTags(list: List<TagEntity>)
}
