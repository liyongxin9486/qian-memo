package com.example.beiwang.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memo_checklist_items")
data class MemoChecklistItemEntity(
    @PrimaryKey val id: String,
    val memoId: String,
    val content: String,
    val isChecked: Boolean = false,
    val position: Int = 0,
    val updatedAt: Long = 0,
    val syncStatus: String = SyncStatus.SYNCED.name,
    val deletedAt: Long? = null
)
