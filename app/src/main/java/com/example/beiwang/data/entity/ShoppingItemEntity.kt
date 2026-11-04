package com.example.beiwang.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val name: String,
    val quantity: Int = 1,
    val unit: String = "个",
    val priority: Int = 3, // 优先级：1=P1(最高) 2=P2 3=P3(最低)，默认 P3
    val sortOrder: Int = 0, // 手动排序位（长按拖动/上移下移后重写），跨设备同步
    val tagIds: String = "", // 逗号分隔的标签 ID
    val isCompleted: Boolean = false, // 已购买
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.UNSYNCED.name
)
