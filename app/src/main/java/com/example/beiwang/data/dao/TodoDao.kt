package com.example.beiwang.data.dao

import androidx.room.*
import com.example.beiwang.data.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE deletedAt IS NULL ORDER BY sortOrder, priority, title")
    fun getAllTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL AND isCompleted = 0 ORDER BY sortOrder, priority, title")
    fun getPendingTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL AND isCompleted = 1 ORDER BY sortOrder, priority, title")
    fun getCompletedTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL AND syncStatus = 'UNSYNCED'")
    fun getUnsyncedTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeletedTodos(): Flow<List<TodoEntity>> // 回收站

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getTodoById(id: String): TodoEntity?

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL AND (title LIKE '%' || :query || '%' ESCAPE '\\' OR note LIKE '%' || :query || '%' ESCAPE '\\')")
    fun searchTodos(query: String): Flow<List<TodoEntity>>

    @Query("SELECT COUNT(*) FROM todos WHERE deletedAt IS NULL")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM todos WHERE deletedAt IS NULL AND isCompleted = 0")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM todos WHERE deletedAt IS NULL AND isCompleted = 1")
    fun getCompletedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity): Long

    @Query("SELECT * FROM todos")
    suspend fun getAllForBackup(): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE syncStatus = 'UNSYNCED'")
    suspend fun getUnsyncedForSync(): List<TodoEntity>

    @Query("UPDATE todos SET syncStatus = 'SYNCED' WHERE syncStatus = 'UNSYNCED'")
    suspend fun markAllSynced()

    @Query("UPDATE todos SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTodos(list: List<TodoEntity>)

    @Update
    suspend fun update(todo: TodoEntity)

    @Query(
        "UPDATE todos SET sortOrder = :sortOrder, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' " +
            "WHERE id = :id"
    )
    suspend fun updateSortOrder(
        id: String,
        sortOrder: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE todos SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id"
    )
    suspend fun softDelete(
        id: String,
        deletedAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE todos SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id IN (:ids)"
    )
    suspend fun softDeleteTodos(
        ids: List<String>,
        deletedAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE todos SET deletedAt = NULL, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        "UPDATE todos SET isCompleted = :isCompleted, updatedAt = :updatedAt, syncStatus = 'UNSYNCED' WHERE id = :id"
    )
    suspend fun updateCompletionStatus(
        id: String,
        isCompleted: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun permanentDelete(todo: TodoEntity)

    @Query("DELETE FROM todos WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun deleteOldTrashTodos(threshold: Long)

    @Query("DELETE FROM todos WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("SELECT id FROM todos WHERE deletedAt IS NOT NULL")
    suspend fun getDeletedTodoIds(): List<String>

    @Query("SELECT id FROM todos WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun getDeletedTodoIdsOlderThan(threshold: Long): List<String>
}
