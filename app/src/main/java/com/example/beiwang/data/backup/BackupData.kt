package com.example.beiwang.data.backup

import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.entity.ShoppingItemEntity
import com.example.beiwang.data.entity.TagEntity

/**
 * 一条附件的备份存档：文件内容以 base64 内嵌，导入时可原样还原到应用私有目录。
 * 文件过大（>8MB）的附件在导出时会跳过，避免备份文件膨胀。
 */
data class MemoAttachmentBackup(
    val id: String,
    val memoId: String,
    val type: String,
    val durationMs: Long = 0,
    val ext: String,
    val base64: String
)

/**
 * 一次完整备份的快照。包含所有表数据（含回收站中已软删除的记录），
 * 以便导入后可完整还原。
 */
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val memos: List<MemoEntity> = emptyList(),
    val shoppingItems: List<ShoppingItemEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val attachments: List<MemoAttachmentBackup> = emptyList()
)
