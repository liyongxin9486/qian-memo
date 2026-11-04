package com.example.beiwang.data.repository

import com.example.beiwang.widget.WidgetUpdater
import android.content.Context
import com.example.beiwang.data.dao.ShoppingItemDao
import com.example.beiwang.data.entity.ShoppingItemEntity
import com.example.beiwang.data.entity.SyncStatus
import com.example.beiwang.data.sync.PurgedIds
import com.example.beiwang.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class ShoppingRepository(
    private val shoppingItemDao: ShoppingItemDao,
    private val context: Context? = null
) {
    private suspend fun refreshWidget() {
        context?.let { WidgetUpdater.refresh(it) }
    }
    fun getAllItems(): Flow<List<ShoppingItemEntity>> = shoppingItemDao.getAllItems()
    
    fun getPendingItems(): Flow<List<ShoppingItemEntity>> = shoppingItemDao.getPendingItems()
    
    fun getCompletedItems(): Flow<List<ShoppingItemEntity>> = shoppingItemDao.getCompletedItems()
    
    fun getUnsyncedItems(): Flow<List<ShoppingItemEntity>> = shoppingItemDao.getUnsyncedItems()

    fun getDeletedItems(): Flow<List<ShoppingItemEntity>> = shoppingItemDao.getDeletedItems()
    
    suspend fun getItemById(id: String): ShoppingItemEntity? = shoppingItemDao.getItemById(id)
    
    fun searchItems(query: String): Flow<List<ShoppingItemEntity>> = shoppingItemDao.searchItems(escapeLike(query))
    
    fun getTotalCount(): Flow<Int> = shoppingItemDao.getTotalCount()
    
    fun getPendingCount(): Flow<Int> = shoppingItemDao.getPendingCount()
    
    fun getCompletedCount(): Flow<Int> = shoppingItemDao.getCompletedCount()
    
    suspend fun insertItem(item: ShoppingItemEntity) {
        // 标记为本机未同步（实体默认 UNSYNCED，这里显式置位 + 更新 updatedAt 供 LWW 合并），
        // 并立即触发一次同步，保证手机端新建的购物项实时推送到后台。
        shoppingItemDao.insert(item.copy(
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.UNSYNCED.name
        ))
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }
    
    suspend fun updateItem(item: ShoppingItemEntity) {
        val existing = shoppingItemDao.getItemById(item.id)
        val changed = existing == null ||
            existing.copy(updatedAt = 0, syncStatus = "") != item.copy(updatedAt = 0, syncStatus = "")
        shoppingItemDao.update(item.copy(
            updatedAt = System.currentTimeMillis(),
            syncStatus = if (changed) SyncStatus.UNSYNCED.name else existing?.syncStatus ?: SyncStatus.UNSYNCED.name
        ))
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }
    
    /**
     * 提交手动排序结果：按传入顺序重写 sortOrder（0..n-1）并逐条置为待同步，
     * 使「本机的拖动顺序」能推送到服务端、再被其他设备拉取到。
     */
    suspend fun applySortOrder(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id ->
            shoppingItemDao.updateSortOrder(id, index, now)
        }
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun softDeleteItem(id: String) {
        shoppingItemDao.softDelete(id)
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }

    // 多选删除：批量软删除（进入回收站），并回写服务端。
    suspend fun softDeleteItems(ids: List<String>) {
        if (ids.isEmpty()) return
        shoppingItemDao.softDeleteItems(ids)
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun restoreItem(id: String) {
        PurgedIds.remove(context, listOf(id), "shop")
        shoppingItemDao.restore(id)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun updateItemCompletionStatus(id: String, isCompleted: Boolean) {
        shoppingItemDao.updateCompletionStatus(id, isCompleted)
        context?.let { SyncManager.requestSync(it) }
    }
    
    suspend fun permanentDeleteItem(item: ShoppingItemEntity) {
        PurgedIds.mark(context, item.id, "shop")
        shoppingItemDao.permanentDelete(item)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun deleteOldTrashItems(daysOld: Int = 30) {
        val threshold = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        PurgedIds.markAll(context, shoppingItemDao.getDeletedShoppingIdsOlderThan(threshold), "shop")
        shoppingItemDao.deleteOldTrashItems(threshold)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun emptyTrash() {
        PurgedIds.markAll(context, shoppingItemDao.getDeletedShoppingIds(), "shop")
        shoppingItemDao.emptyTrash()
        context?.let { SyncManager.requestSync(it) }
    }

    /**
     * 转义 LIKE 通配符：用户输入中的 % _ \ 按普通字符匹配
     * （DAO 侧已声明 ESCAPE '\'）。
     */
    private fun escapeLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
