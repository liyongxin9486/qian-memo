package com.example.beiwang.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "memos")
data class MemoEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val title: String,
    val content: String = "",
    val isCompleted: Boolean = false,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false, // 置顶
    val tagIds: String = "", // 逗号分隔的标签 ID
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null, // 软删除时间，用于回收站
    val syncStatus: String = SyncStatus.UNSYNCED.name
)

enum class SyncStatus {
    SYNCED,
    UNSYNCED,
    PENDING,
    SYNCING,
    FAILED
}
