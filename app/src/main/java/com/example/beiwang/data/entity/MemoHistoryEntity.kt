package com.example.beiwang.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 备忘录历史快照：每次保存时记录当时的标题与正文，供查看历史记录。
 */
@Entity(
    tableName = "memo_history",
    indices = [Index(value = ["memoId"])]
)
data class MemoHistoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val memoId: String,
    val title: String,
    val content: String,
    val snapshotAt: Long = System.currentTimeMillis()
)
