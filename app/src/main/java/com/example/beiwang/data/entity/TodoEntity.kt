package com.example.beiwang.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 待办条目。结构与购物清单同构（标题/备注/完成态/优先级/标签/手动排序），
 * 独立成表并与服务端 todos 表对齐，参与 push/pull 双向同步。
 *
 * sortOrder：用户手动排序位（长按拖动或上移/下移后重写），跨设备同步。
 * 旧数据升级后该列默认 0，此时排序退化为「优先级 + 标题」的既有规则。
 */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val title: String = "",
    val note: String = "",
    val priority: Int = 3, // 1=P1(最高) 2=P2 3=P3(最低)，默认 P3
    val tagIds: String = "", // 逗号分隔的标签 ID
    val sortOrder: Int = 0, // 手动排序位
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.UNSYNCED.name
)
