package com.example.beiwang.data.repository

import android.content.Context
import com.example.beiwang.data.dao.TagDao
import com.example.beiwang.data.entity.SyncStatus
import com.example.beiwang.data.entity.TagEntity
import com.example.beiwang.data.sync.PurgedIds
import com.example.beiwang.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class TagRepository(
    private val tagDao: TagDao,
    private val context: Context? = null
) {
    fun getAllTags(): Flow<List<TagEntity>> = tagDao.getAllTags()

    suspend fun getAllTagsOnce(): List<TagEntity> = tagDao.getAllTagsOnce()

    suspend fun getTagById(id: String): TagEntity? = tagDao.getTagById(id)

    suspend fun insertTag(tag: TagEntity) {
        // 标记为本机未同步并立即触发同步，保证手机端新建的标签实时推送到后台。
        tagDao.insert(tag.copy(updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.UNSYNCED.name))
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun updateTag(tag: TagEntity) {
        val existing = tagDao.getTagById(tag.id)
        val changed = existing == null ||
            existing.copy(updatedAt = 0, syncStatus = "", deletedAt = null) !=
            tag.copy(updatedAt = 0, syncStatus = "", deletedAt = null)
        tagDao.update(
            tag.copy(
                updatedAt = System.currentTimeMillis(),
                syncStatus = if (changed) SyncStatus.UNSYNCED.name else existing?.syncStatus ?: SyncStatus.UNSYNCED.name
            )
        )
        context?.let { SyncManager.requestSync(it) }
    }

    /** 软删除：置 deletedAt 墓碑 + UNSYNCED，跨设备同步删除。 */
    suspend fun deleteTag(tag: TagEntity) {
        tagDao.softDelete(tag.id)
        context?.let { SyncManager.requestSync(it) }
    }

    // ---- 回收站 ----
    fun getDeletedTags(): Flow<List<TagEntity>> = tagDao.getDeletedTags()

    suspend fun softDeleteTag(id: String) {
        tagDao.softDelete(id)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun restoreTag(id: String) {
        PurgedIds.remove(context, listOf(id), "tag")
        tagDao.restore(id)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun permanentDeleteTags(ids: List<String>) {
        PurgedIds.markAll(context, ids, "tag")
        tagDao.hardDeleteByIds(ids)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun emptyTrash() {
        PurgedIds.markAll(context, tagDao.getDeletedTagIds(), "tag")
        tagDao.emptyTrash()
        context?.let { SyncManager.requestSync(it) }
    }
}
