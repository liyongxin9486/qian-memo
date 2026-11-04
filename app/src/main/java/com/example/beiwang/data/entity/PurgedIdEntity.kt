package com.example.beiwang.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已永久删除（清空回收站 / 永久删除单条）的条目 id 记录。
 * 同步协议（服务端只有 INSERT/UPDATE，无永久删除）无法把"彻底删除"传到服务端，
 * 服务端会保留软删墓碑并在下次 PULL 重新下发，导致清空回收站后又复活。
 * 因此本地持久化这些 id：拉取合并时命中本表的 DTO 一律跳过，不再插入。
 *
 * type 取值：memo / shop / tag / chk（分别对应四类数据的 id，避免跨类型 id 碰撞）。
 */
@Entity(tableName = "purged_ids")
data class PurgedIdEntity(
    @PrimaryKey val id: String,
    val type: String,
    val purgedAt: Long
)
