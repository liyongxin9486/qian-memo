package com.example.beiwang.ui.memo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.entity.TagEntity
import com.example.beiwang.data.repository.MemoRepository
import com.example.beiwang.data.repository.TagRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class MemoFilter {
    ALL,      // 全部
    SYNC      // 同步状态（仅看待同步项）
}

class MemoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MemoRepository
    private val tagRepository: TagRepository

    private val _uiState = MutableStateFlow(MemoUiState())
    val uiState: StateFlow<MemoUiState> = _uiState.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = MemoRepository(db.memoDao(), application)
        tagRepository = TagRepository(db.tagDao())

        // 一次 combine 同时收集备忘与标签：总数/待办/已完成/待同步计数都由 memos 列表直接派生，
        // 避免原先 6 个独立 Flow 各自查库、各自发 StateFlow——备忘一变就触发 5 次冗余 DB 查询与重组。
        viewModelScope.launch {
            combine(repository.getAllMemos(), tagRepository.getAllTags()) { memos, tags ->
                _uiState.value.copy(
                    memos = memos,
                    allTags = tags,
                    totalCount = memos.size,
                    pendingCount = memos.count { !it.isCompleted },
                    completedCount = memos.count { it.isCompleted },
                    unsyncedCount = memos.count { it.syncStatus == "UNSYNCED" }
                )
            }.collect { _uiState.value = it }
        }
    }
    
    fun deleteMemo(memoId: String) {
        viewModelScope.launch {
            repository.softDeleteMemo(memoId)
        }
    }

    fun deleteMemos(ids: List<String>) {
        viewModelScope.launch {
            repository.softDeleteMemos(ids)
        }
    }

    fun togglePin(memoId: String) {
        val current = _uiState.value.memos.find { it.id == memoId }?.isPinned ?: false
        viewModelScope.launch {
            repository.updatePin(memoId, !current)
        }
    }

    fun toggleFavorite(memoId: String) {
        val current = _uiState.value.memos.find { it.id == memoId }?.isFavorite ?: false
        viewModelScope.launch {
            repository.updateFavorite(memoId, !current)
        }
    }

    fun setFilter(filter: MemoFilter) {
        _uiState.update { it.copy(filter = filter) }
    }
}

data class MemoUiState(
    val memos: List<MemoEntity> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val completedCount: Int = 0,
    val unsyncedCount: Int = 0,
    val filter: MemoFilter = MemoFilter.ALL,
    val isLoading: Boolean = false
)
