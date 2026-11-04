package com.example.beiwang.data.dao

import androidx.room.*
import com.example.beiwang.data.entity.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingItemDao {
    @Query("SELECT * FROM shopping_items WHERE deletedAt IS NULL ORDER BY sortOrder, priority, name")
    fun getAllItems(): Flow<List<ShoppingItemEntity>>
    
    @Query("SELECT * FROM shopping_items WHERE deletedAt IS NULL AND isCompleted = 0 ORDER BY sortOrder, priority, name")
    fun getPendingItems(): Flow<List<ShoppingItemEntity>>
    
    @Query("SELECT * FROM shopping_items WHERE deletedAt IS NULL AND isCompleted = 1 ORDER BY sortOrder, priority, name")
    fun getCompletedItems(): Flow<List<ShoppingItemEntity>>
    
    @Query("SELECT * FROM shopping_items WHERE deletedAt IS NULL AND syncStatus = 'UNSYNCED'")
    fun getUnsyncedItems(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeletedItems(): Flow<List<ShoppingItemEntity>> // 回收站
    
    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun getItemById(id: String): ShoppingItemEntity?
    
    @Query("SELECT * FROM shopping_items WHERE deletedAt IS NULL AND name LIKE '%' || :query || '%' ESCAPE '\\'")
    fun searchItems(query: String): Flow<List<ShoppingItemEntity>>
    
    @Query("SELECT COUNT(*) FROM shopping_items WHERE deletedAt IS NULL")
    fun getTotalCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM shopping_items WHERE deletedAt IS NULL AND isCompleted = 0")
    fun getPendingCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM shopping_items WHERE deletedAt IS NULL AND isCompleted = 1")
    fun getCompletedCount(): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ShoppingItemEntity): Long

    @Query("SELECT * FROM shopping_items")
    suspend fun getAllForBackup(): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE syncStatus = 'UNSYNCED'")
    suspend fun getUnsyncedForSync(): List<ShoppingItemEntity>

    @Query("UPDATE shopping_items SET syncStatus = 'SYNCED' WHERE syncStatus = 'UNSYNCED'")
    suspend fun markAllSynced()

    @Query("UPDATE shopping_items SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllItems(list: List<ShoppingItemEntity>)

    @Update
    suspend fun update(item: ShoppingItemEntity)

    /**
     * 手动排序位写入。排序变化也要同步到服务端（否则换设备顺序丢失），
     * 故与内容更新一样置 UNSYNCED 并刷新 updatedAt（LWW 以时间较新者胜）。
     */
    @Query(
        "UPDATE shopping_items SET sortOrder = :sortOrder, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' " +
            "WHERE id = :id"
    )
    suspend fun updateSortOrder(
        id: String,
        sortOrder: Int,
        updatedAt: Long = System.currentTimeMillis()
    )
    
    @Query("UPDATE shopping_items SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE shopping_items SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id IN (:ids)")
    suspend fun softDeleteItems(ids: List<String>, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE shopping_items SET deletedAt = NULL, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE shopping_items SET isCompleted = :isCompleted, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun updateCompletionStatus(id: String, isCompleted: Boolean, updatedAt: Long = System.currentTimeMillis())
    
    @Delete
    suspend fun permanentDelete(item: ShoppingItemEntity)
    
    @Query("DELETE FROM shopping_items WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun deleteOldTrashItems(threshold: Long)

    @Query("DELETE FROM shopping_items WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("SELECT id FROM shopping_items WHERE deletedAt IS NOT NULL")
    suspend fun getDeletedShoppingIds(): List<String>

    @Query("SELECT id FROM shopping_items WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun getDeletedShoppingIdsOlderThan(threshold: Long): List<String>
}
