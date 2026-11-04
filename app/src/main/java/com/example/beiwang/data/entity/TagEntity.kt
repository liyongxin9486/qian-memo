package com.example.beiwang.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 标签。备忘录与购物清单共用同一套标签。
 * color 以 ARGB Int 存储，便于在 chips 上直接着色。
 */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Int = 0xFF2196F3.toInt(),
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = SyncStatus.SYNCED.name,
    val deletedAt: Long? = null
)
