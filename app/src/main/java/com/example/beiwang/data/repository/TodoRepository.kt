package com.example.beiwang.data.repository

import android.content.Context
import com.example.beiwang.data.dao.TodoDao
import com.example.beiwang.data.entity.SyncStatus
import com.example.beiwang.data.entity.TodoEntity
import com.example.beiwang.data.sync.PurgedIds
import com.example.beiwang.data.sync.SyncManager
import com.example.beiwang.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow

/**
 * 待办仓库。写法与 [com.example.beiwang.data.repository.ShoppingRepository] 保持一致：
 * 写操作统一置 UNSYNCED + 刷新 updatedAt（LWW），并触发一次同步与桌面组件刷新。
 */
class TodoRepository(
    private val todoDao: TodoDao,
    private val context: Context? = null
) {
    private suspend fun refreshWidget() {
        context?.let { WidgetUpdater.refresh(it) }
    }

    fun getAllTodos(): Flow<List<TodoEntity>> = todoDao.getAllTodos()

    fun getPendingTodos(): Flow<List<TodoEntity>> = todoDao.getPendingTodos()

    fun getCompletedTodos(): Flow<List<TodoEntity>> = todoDao.getCompletedTodos()

    fun getUnsyncedTodos(): Flow<List<TodoEntity>> = todoDao.getUnsyncedTodos()

    fun getDeletedTodos(): Flow<List<TodoEntity>> = todoDao.getDeletedTodos()

    suspend fun getTodoById(id: String): TodoEntity? = todoDao.getTodoById(id)

    fun searchTodos(query: String): Flow<List<TodoEntity>> = todoDao.searchTodos(escapeLike(query))

    fun getTotalCount(): Flow<Int> = todoDao.getTotalCount()

    fun getPendingCount(): Flow<Int> = todoDao.getPendingCount()

    fun getCompletedCount(): Flow<Int> = todoDao.getCompletedCount()

    suspend fun insertTodo(todo: TodoEntity) {
        todoDao.insert(
            todo.copy(
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.UNSYNCED.name
            )
        )
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun updateTodo(todo: TodoEntity) {
        val existing = todoDao.getTodoById(todo.id)
        val changed = existing == null ||
            existing.copy(updatedAt = 0, syncStatus = "") != todo.copy(updatedAt = 0, syncStatus = "")
        todoDao.update(
            todo.copy(
                updatedAt = System.currentTimeMillis(),
                syncStatus = if (changed) SyncStatus.UNSYNCED.name
                else existing?.syncStatus ?: SyncStatus.UNSYNCED.name
            )
        )
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }

    /**
     * 提交手动排序结果：按传入顺序重写 sortOrder（0..n-1）并逐条置为待同步。
     * 整段排序一次性重写，避免「拖动后部分条目顺序冲突」。
     */
    suspend fun applySortOrder(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id ->
            todoDao.updateSortOrder(id, index, now)
        }
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun softDeleteTodo(id: String) {
        todoDao.softDelete(id)
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun softDeleteTodos(ids: List<String>) {
        if (ids.isEmpty()) return
        todoDao.softDeleteTodos(ids)
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun restoreTodo(id: String) {
        PurgedIds.remove(context, listOf(id), "todo")
        todoDao.restore(id)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun updateTodoCompletionStatus(id: String, isCompleted: Boolean) {
        todoDao.updateCompletionStatus(id, isCompleted)
        refreshWidget()
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun permanentDeleteTodo(todo: TodoEntity) {
        PurgedIds.mark(context, todo.id, "todo")
        todoDao.permanentDelete(todo)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun deleteOldTrashTodos(daysOld: Int = 30) {
        val threshold = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        PurgedIds.markAll(context, todoDao.getDeletedTodoIdsOlderThan(threshold), "todo")
        todoDao.deleteOldTrashTodos(threshold)
        context?.let { SyncManager.requestSync(it) }
    }

    suspend fun emptyTrash() {
        PurgedIds.markAll(context, todoDao.getDeletedTodoIds(), "todo")
        todoDao.emptyTrash()
        context?.let { SyncManager.requestSync(it) }
    }

    private fun escapeLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
