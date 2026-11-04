package com.example.beiwang.data.dao

import androidx.room.*
import com.example.beiwang.data.entity.MemoAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoAttachmentDao {
    @Query("SELECT * FROM memo_attachments WHERE memoId = :memoId ORDER BY createdAt ASC")
    fun getAttachmentsByMemoId(memoId: String): Flow<List<MemoAttachmentEntity>>

    @Query("SELECT * FROM memo_attachments WHERE memoId = :memoId ORDER BY createdAt ASC")
    suspend fun getAttachmentsByMemoIdOnce(memoId: String): List<MemoAttachmentEntity>

    @Query("SELECT * FROM memo_attachments")
    suspend fun getAllOnce(): List<MemoAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: MemoAttachmentEntity): Long

    @Update
    suspend fun update(attachment: MemoAttachmentEntity)

    @Delete
    suspend fun delete(attachment: MemoAttachmentEntity)

    @Query("DELETE FROM memo_attachments WHERE memoId = :memoId")
    suspend fun deleteByMemoId(memoId: String)
}
