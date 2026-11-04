package com.example.beiwang.data.backup

import android.content.Context
import android.util.Base64
import com.example.beiwang.data.dao.MemoAttachmentDao
import com.example.beiwang.data.dao.MemoDao
import com.example.beiwang.data.dao.ShoppingItemDao
import com.example.beiwang.data.dao.TagDao
import com.example.beiwang.data.entity.MemoAttachmentEntity
import java.io.File

/**
 * 备份数据读写。所有列出的 DAO 均使用 @Insert(onConflict = REPLACE)，
 * 因此导入时按主键 upsert，已存在的记录会被覆盖、缺失的保留。
 * 附件文件（base64 内嵌）在导出/导入时一并处理，保证换机恢复不丢图片/语音/手写。
 */
class BackupRepository(
    private val memoDao: MemoDao,
    private val shoppingItemDao: ShoppingItemDao,
    private val tagDao: TagDao,
    private val attachmentDao: MemoAttachmentDao,
    private val context: Context
) {
    /** 单个附件文件允许进入备份的最大字节数（过大跳过，避免备份文件膨胀）。 */
    private val MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024

    /** 导出全部数据（含回收站中已软删除的记录）与附件内容。 */
    suspend fun exportAll(): BackupData {
        val attachments = attachmentDao.getAllOnce().mapNotNull { a ->
            val f = File(a.uri)
            if (!f.exists() || !f.isFile || f.length() == 0L || f.length() > MAX_ATTACHMENT_BYTES) {
                null
            } else {
                MemoAttachmentBackup(
                    id = a.id,
                    memoId = a.memoId,
                    type = a.type,
                    durationMs = a.durationMs,
                    ext = f.extension.ifBlank { "bin" },
                    base64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                )
            }
        }
        return BackupData(
            memos = memoDao.getAllForBackup(),
            shoppingItems = shoppingItemDao.getAllForBackup(),
            tags = tagDao.getAllForBackup(),
            attachments = attachments
        )
    }

    /** 导入备份（按主键合并/覆盖；附件写回文件并插入附件行）。 */
    suspend fun importAll(data: BackupData) {
        if (data.tags.isNotEmpty()) tagDao.insertAllTags(data.tags)
        if (data.memos.isNotEmpty()) memoDao.insertAllMemos(data.memos)
        if (data.shoppingItems.isNotEmpty()) shoppingItemDao.insertAllItems(data.shoppingItems)
        data.attachments.forEach { a ->
            runCatching {
                val valid = a.base64.isNotBlank() && a.memoId.isNotBlank()
                if (!valid) return@runCatching
                val bytes = Base64.decode(a.base64, Base64.DEFAULT)
                if (bytes.isEmpty()) return@runCatching
                val dir = File(context.filesDir, "memo_attachments").apply { mkdirs() }
                val file = File(dir, "${a.id}.${a.ext}")
                file.writeBytes(bytes)
                attachmentDao.insert(
                    MemoAttachmentEntity(
                        id = a.id,
                        memoId = a.memoId,
                        type = a.type,
                        uri = file.absolutePath,
                        durationMs = a.durationMs
                    )
                )
            }
        }
    }
}