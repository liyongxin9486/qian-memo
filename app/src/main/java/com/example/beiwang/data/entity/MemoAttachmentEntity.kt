package com.example.beiwang.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 备忘录附件：图片 / 语音 / 手写。
 * type: "IMAGE" | "AUDIO" | "HANDWRITE"
 * uri:  ContentProvider Uri 或应用私有目录下的文件路径（手写/语音均落盘到应用目录）。
 */
@Entity(
    tableName = "memo_attachments",
    indices = [Index(value = ["memoId"])],
    foreignKeys = [
        ForeignKey(
            entity = MemoEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MemoAttachmentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val memoId: String,
    val type: String,
    val uri: String,
    val durationMs: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
