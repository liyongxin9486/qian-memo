package com.example.beiwang.data.dao

import androidx.room.*
import com.example.beiwang.data.entity.MemoHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoHistoryDao {
    @Query("SELECT * FROM memo_history WHERE memoId = :memoId ORDER BY snapshotAt DESC")
    fun getHistoryByMemoId(memoId: String): Flow<List<MemoHistoryEntity>>

    @Query("SELECT * FROM memo_history WHERE memoId = :memoId ORDER BY snapshotAt DESC")
    suspend fun getHistoryByMemoIdOnce(memoId: String): List<MemoHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: MemoHistoryEntity): Long

    @Query("DELETE FROM memo_history WHERE memoId = :memoId")
    suspend fun deleteByMemoId(memoId: String)
}
