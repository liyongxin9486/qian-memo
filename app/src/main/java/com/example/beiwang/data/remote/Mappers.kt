package com.example.beiwang.data.remote

import com.example.beiwang.data.entity.MemoChecklistItemEntity
import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.entity.ShoppingItemEntity
import com.example.beiwang.data.entity.SyncStatus
import com.example.beiwang.data.entity.TagEntity
import com.example.beiwang.data.entity.TodoEntity

/**
 * 本地实体 <-> 后端 DTO 的互转。
 * 拉取回来的数据统一标记为 SYNCED；本地 userId 写回当前登录用户，
 * 保证单设备多账号场景下本地查询一致。
 */

fun MemoEntity.toDto() = MemoDto(
    id = id, userId = userId, title = title, content = content,
    isCompleted = isCompleted, isFavorite = isFavorite, isPinned = isPinned,
    tagIds = tagIds, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt
)

fun MemoDto.toEntity(uid: String) = MemoEntity(
    id = id, userId = uid, title = title, content = content,
    isCompleted = isCompleted, isFavorite = isFavorite, isPinned = isPinned,
    tagIds = tagIds, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    syncStatus = SyncStatus.SYNCED.name
)

fun ShoppingItemEntity.toDto() = ShoppingItemDto(
    id = id, userId = userId, name = name, quantity = quantity, unit = unit,
    priority = priority, sortOrder = sortOrder, tagIds = tagIds, isCompleted = isCompleted,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt
)

fun ShoppingItemDto.toEntity(uid: String) = ShoppingItemEntity(
    id = id, userId = uid, name = name, quantity = quantity, unit = unit,
    priority = priority, sortOrder = sortOrder, tagIds = tagIds, isCompleted = isCompleted,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    syncStatus = SyncStatus.SYNCED.name
)

fun TodoEntity.toDto() = TodoDto(
    id = id, userId = userId, title = title, note = note, priority = priority,
    tagIds = tagIds, sortOrder = sortOrder, isCompleted = isCompleted,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt
)

fun TodoDto.toEntity(uid: String) = TodoEntity(
    id = id, userId = uid, title = title, note = note, priority = priority,
    tagIds = tagIds, sortOrder = sortOrder, isCompleted = isCompleted,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    syncStatus = SyncStatus.SYNCED.name
)

fun TagEntity.toDto() = TagDto(
    id = id, userId = "", name = name, color = color,
    sortOrder = sortOrder, createdAt = createdAt, updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun TagDto.toEntity(uid: String) = TagEntity(
    id = id, name = name, color = color, sortOrder = sortOrder,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    syncStatus = SyncStatus.SYNCED.name
)

fun MemoChecklistItemEntity.toDto() = ChecklistItemDto(
    id = id, userId = "", memoId = memoId, content = content,
    isChecked = isChecked, position = position, updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun ChecklistItemDto.toEntity() = MemoChecklistItemEntity(
    id = id, memoId = memoId, content = content,
    isChecked = isChecked, position = position, updatedAt = updatedAt, deletedAt = deletedAt,
    syncStatus = SyncStatus.SYNCED.name
)
