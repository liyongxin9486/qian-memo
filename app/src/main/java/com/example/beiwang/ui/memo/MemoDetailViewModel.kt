package com.example.beiwang.ui.memo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.beiwang.data.AuthManager
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.entity.MemoAttachmentEntity
import com.example.beiwang.data.entity.MemoChecklistItemEntity
import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.entity.MemoHistoryEntity
import com.example.beiwang.data.entity.splitTagIds
import com.example.beiwang.data.entity.joinTagIds
import com.example.beiwang.data.repository.MemoRepository
import com.example.beiwang.data.repository.TagRepository
import com.example.beiwang.data.sync.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MemoDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MemoRepository
    private val tagRepository: TagRepository

    private val _uiState = MutableStateFlow(MemoDetailUiState())
    val uiState: StateFlow<MemoDetailUiState> = _uiState.asStateFlow()

    private val _checklistItems = MutableStateFlow<List<MemoChecklistItemEntity>>(emptyList())
    val checklistItems: StateFlow<List<MemoChecklistItemEntity>> = _checklistItems.asStateFlow()

    // 以 memoId 为键驱动附件/历史/清单三类数据流：flatMapLatest 在切换备忘时自动取消旧收集器，
    // 避免 loadMemo 被重复调用时 collector 叠加、旧备忘数据残留。
    private val _memoId = MutableStateFlow<String?>(null)

    init {
        val db = AppDatabase.getDatabase(application)
        repository = MemoRepository(db.memoDao(), application, db.memoAttachmentDao(), db.memoHistoryDao())
        tagRepository = TagRepository(db.tagDao(), application)

        viewModelScope.launch {
            tagRepository.getAllTags().collect { tags ->
                _uiState.update { it.copy(allTags = tags) }
            }
        }

        // 三类清单数据流：随 memoId 切换自动取消/重建收集器（flatMapLatest），
        // 修复「每次 loadMemo 都新开 collector」导致的叠加与旧数据残留。
        viewModelScope.launch {
            _memoId.filterNotNull().flatMapLatest { id ->
                combine(
                    repository.getAttachmentsByMemoId(id),
                    repository.getHistoryByMemoId(id),
                    repository.getChecklistItems(id)
                ) { attachments, history, items -> Triple(attachments, history, items) }
            }.collect { (attachments, history, items) ->
                _uiState.update { it.copy(attachments = attachments, history = history) }
                _checklistItems.value = items
            }
        }
    }

    fun loadMemo(memoId: String) {
        // 新备忘的草稿 id 由导航参数携带（route 为 "new" 或 "new_<uuid>"）。Navigation 返回栈
        // 在屏幕旋转/进程重建后保留该参数，因此重建后仍用同一 id —— 附件/清单继续关联到同一
        // 备忘，杜绝「旋转一次出现两份数据、附件大量丢失」。
        val isNew = memoId == "new" || memoId.startsWith("new_")
        val id = if (isNew) {
            val newId = if (memoId == "new") UUID.randomUUID().toString() else memoId.removePrefix("new_")
            _uiState.update { it.copy(isNew = true, currentMemoId = newId) }
            newId
        } else {
            memoId
        }

        // 仅对已有备忘做一次主体加载（getMemoById 为一次性查询，无 collector 叠加问题）；
        // 新备忘主体为空，保留草稿态（isNew=true），待用户保存时落库。
        if (!isNew) {
            viewModelScope.launch {
                val memo = repository.getMemoById(memoId)
                memo?.let {
                    _uiState.update { state ->
                        state.copy(
                            memo = it,
                            isNew = false,
                            currentMemoId = it.id,
                            title = it.title,
                            content = it.content,
                            isFavorite = it.isFavorite,
                            isPinned = it.isPinned,
                            selectedTagIds = it.tagIds.splitTagIds().toSet()
                        )
                    }
                }
            }
        }
        // 以 memoId 驱动附件/历史/清单的响应式收集（见 init 中的 flatMapLatest）。
        _memoId.value = id
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateContent(content: String) {
        _uiState.update { it.copy(content = content) }
    }

    // 清单项：点击右上角「清单」按钮，在末尾追加一条可勾选待办项（先空行，随后直接输入文字）
    fun addChecklistItem(content: String = "") {
        viewModelScope.launch {
            val memoId = ensureMemoPersisted()
            // 末尾追加：position 取当前最大 position + 1
            val position = repository.getNextChecklistPosition(memoId)
            repository.insertChecklistItem(
                MemoChecklistItemEntity(
                    id = UUID.randomUUID().toString(),
                    memoId = memoId,
                    content = content,
                    isChecked = false,
                    position = position
                )
            )
        }
    }

    fun updateChecklistItemContent(id: String, content: String) {
        viewModelScope.launch {
            val current = _checklistItems.value.find { it.id == id } ?: return@launch
            repository.updateChecklistItem(current.copy(content = content))
        }
    }

    fun toggleChecklistItem(item: MemoChecklistItemEntity) {
        viewModelScope.launch {
            repository.setChecklistItemChecked(item.id, !item.isChecked)
        }
    }

    fun deleteChecklistItem(item: MemoChecklistItemEntity) {
        viewModelScope.launch {
            repository.deleteChecklistItem(item)
        }
    }

    fun toggleFavorite() {
        _uiState.update { it.copy(isFavorite = !it.isFavorite) }
    }

    fun togglePin() {
        _uiState.update { it.copy(isPinned = !it.isPinned) }
    }

    fun toggleTag(tagId: String) {
        _uiState.update { state ->
            val set = state.selectedTagIds.toMutableSet()
            if (set.contains(tagId)) set.remove(tagId) else set.add(tagId)
            state.copy(selectedTagIds = set)
        }
    }

    /**
     * 确保备忘录已落库并返回其 id。新建备忘录在添加附件前必须先落库，
     * 否则附件外键（memoId 引用 memos.id）会因父行不存在而插入失败。
     */
    private suspend fun ensureMemoPersisted(): String {
        val state = _uiState.value
        if (state.isNew) {
            val id = state.currentMemoId
            val memo = MemoEntity(
                id = id,
                title = state.title.ifBlank { "未命名备忘录" },
                content = state.content,
                isFavorite = state.isFavorite,
                isPinned = state.isPinned,
                tagIds = state.selectedTagIds.joinTagIds()
            )
            repository.insertMemo(memo)
            _uiState.update { it.copy(isNew = false, memo = memo, title = memo.title) }
            return id
        }
        return state.memo?.id ?: state.currentMemoId
    }

    fun saveMemo() {
        val state = _uiState.value
        if (state.isNew && state.title.isBlank() && state.attachments.isEmpty()) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (state.isNew) {
                val id = state.currentMemoId
                val newMemo = MemoEntity(
                    id = id,
                    title = state.title.ifBlank { "未命名备忘录" },
                    content = state.content,
                    isFavorite = state.isFavorite,
                    isPinned = state.isPinned,
                    tagIds = state.selectedTagIds.joinTagIds(),
                    createdAt = now,
                    updatedAt = now
                )
                repository.insertMemo(newMemo)
                // 新建即一次真实写入，记录历史快照
                repository.insertHistory(
                    MemoHistoryEntity(memoId = id, title = newMemo.title, content = newMemo.content, snapshotAt = now)
                )
                _uiState.update { it.copy(isNew = false, memo = newMemo, isSaved = true) }
                triggerSync()
            } else {
                state.memo?.let { existingMemo ->
                    val tagIds = state.selectedTagIds.joinTagIds()
                    // 内容与标签变化才记历史快照；纯「收藏/置顶」切换属于状态变更，
                    // 只更新记录不入历史，避免历史列表被置顶/收藏操作刷屏。
                    val contentChanged = existingMemo.title != state.title
                            || existingMemo.content != state.content
                            || existingMemo.tagIds != tagIds
                    val metaChanged = existingMemo.isFavorite != state.isFavorite
                            || existingMemo.isPinned != state.isPinned
                    if (contentChanged || metaChanged) {
                        val updatedMemo = existingMemo.copy(
                            title = state.title,
                            content = state.content,
                            isFavorite = state.isFavorite,
                            isPinned = state.isPinned,
                            tagIds = tagIds,
                            updatedAt = now
                        )
                        repository.updateMemo(updatedMemo)
                        if (contentChanged) {
                            repository.insertHistory(
                                MemoHistoryEntity(memoId = updatedMemo.id, title = updatedMemo.title, content = updatedMemo.content, snapshotAt = now)
                            )
                        }
                    }
                }
                // 已有备忘录保存时一律触发一次后台同步（覆盖清单项/附件等改动），
                // 与内容是否变化无关；空白新建提前返回的情形已在上面拦截。
                triggerSync()
                _uiState.update { it.copy(isSaved = true) }
            }
        }
    }

    /** 数据变更后（保存/删除/恢复）立即触发一次后台同步，把变更推送到服务端。 */
    private fun triggerSync() {
        if (AuthManager.isLoggedIn) {
            SyncManager.requestSync(getApplication())
        }
    }

    fun addAttachment(type: String, uri: String, durationMs: Long = 0) {
        viewModelScope.launch {
            val memoId = ensureMemoPersisted()
            repository.insertAttachment(
                MemoAttachmentEntity(
                    memoId = memoId,
                    type = type,
                    uri = uri,
                    durationMs = durationMs
                )
            )
        }
    }

    fun removeAttachment(attachment: MemoAttachmentEntity) {
        viewModelScope.launch {
            // 附件文件存放在应用私有目录，删除记录时一并删除磁盘文件，避免存储只增不减
            runCatching { java.io.File(attachment.uri).delete() }
            repository.deleteAttachment(attachment)
        }
    }

    /** 续画手写：回写同一附件的图片路径，避免重复产生附件行。 */
    fun updateAttachmentUri(id: String, uri: String) {
        viewModelScope.launch {
            val existing = _uiState.value.attachments.find { it.id == id }
            existing?.let {
                repository.updateAttachment(it.copy(uri = uri, createdAt = System.currentTimeMillis()))
            }
        }
    }

    fun deleteMemo() {
        val memoId = _uiState.value.memo?.id ?: _uiState.value.currentMemoId
        viewModelScope.launch {
            // 删除前记录一条快照，便于在历史的「增删改」中追溯
            repository.getMemoById(memoId)?.let { memo ->
                repository.insertHistory(
                    MemoHistoryEntity(
                        memoId = memoId,
                        title = memo.title,
                        content = memo.content,
                        snapshotAt = System.currentTimeMillis()
                    )
                )
            }
            repository.deleteChecklistItemsByMemo(memoId)
            repository.softDeleteMemo(memoId)
            triggerSync()
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    /** 将某条历史快照覆盖写回当前备忘录，并生成一条新备份。 */
    fun restoreFromHistory(history: MemoHistoryEntity) {
        val state = _uiState.value
        val id = state.memo?.id ?: state.currentMemoId
        if (state.isNew || id.isBlank()) return
        viewModelScope.launch {
            val memo = repository.getMemoById(id)
            memo?.let {
                val now = System.currentTimeMillis()
                val restored = it.copy(
                    title = history.title,
                    content = history.content,
                    updatedAt = now
                )
                repository.updateMemo(restored)
                repository.insertHistory(
                    MemoHistoryEntity(memoId = id, title = history.title, content = history.content, snapshotAt = now)
                )
                triggerSync()
                _uiState.update { s ->
                    s.copy(title = history.title, content = history.content, memo = restored)
                }
            }
        }
    }
}

data class MemoDetailUiState(
    val memo: MemoEntity? = null,
    val isNew: Boolean = false,
    val currentMemoId: String = "",
    val title: String = "",
    val content: String = "",
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val selectedTagIds: Set<String> = emptySet(),
    val allTags: List<com.example.beiwang.data.entity.TagEntity> = emptyList(),
    val attachments: List<com.example.beiwang.data.entity.MemoAttachmentEntity> = emptyList(),
    val history: List<com.example.beiwang.data.entity.MemoHistoryEntity> = emptyList(),
    val isSaved: Boolean = false,
    val isLoading: Boolean = false
)
