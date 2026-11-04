package com.example.beiwang.ui.tag

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.entity.TagEntity
import com.example.beiwang.data.repository.TagRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class TagViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TagRepository

    private val _uiState = MutableStateFlow(TagUiState())
    val uiState: StateFlow<TagUiState> = _uiState.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = TagRepository(db.tagDao(), application)

        viewModelScope.launch {
            repository.getAllTags().collect { tags ->
                _uiState.update { it.copy(tags = tags) }
            }
        }
        viewModelScope.launch {
            // 回收站：软删除的标签（deletedAt 非空）
            repository.getDeletedTags().collect { deleted ->
                _uiState.update { it.copy(deletedTags = deleted) }
            }
        }
    }

    fun updateNewName(name: String) {
        _uiState.update { it.copy(newName = name) }
    }

    fun updateNewColor(color: Int) {
        _uiState.update { it.copy(newColor = color) }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, newName = "", newColor = TagColors.first()) }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun addTag() {
        val name = _uiState.value.newName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val tag = TagEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                color = _uiState.value.newColor,
                sortOrder = _uiState.value.tags.size
            )
            repository.insertTag(tag)
            _uiState.update { it.copy(showAddDialog = false) }
        }
    }

    fun deleteTag(tag: TagEntity) {
        viewModelScope.launch {
            // 软删除：进入回收站，可恢复（与备忘/购物一致）
            repository.deleteTag(tag)
        }
    }

    fun restoreTag(tag: TagEntity) {
        viewModelScope.launch {
            repository.restoreTag(tag.id)
        }
    }

    fun permanentDeleteTag(tag: TagEntity) {
        viewModelScope.launch {
            repository.permanentDeleteTags(listOf(tag.id))
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }
}

/** 标签可选颜色（Material 风格）。 */
val TagColors = listOf(
    0xFF1E88E5.toInt(), // 蓝
    0xFF4CAF50.toInt(), // 绿
    0xFF9C27B0.toInt(), // 紫
    0xFFE53935.toInt(), // 红
    0xFFFF9800.toInt(), // 橙
    0xFF009688.toInt(), // 青
    0xFF795548.toInt(), // 棕
    0xFF607D8B.toInt()  // 蓝灰
)

data class TagUiState(
    val tags: List<TagEntity> = emptyList(),
    val deletedTags: List<TagEntity> = emptyList(),
    val newName: String = "",
    val newColor: Int = TagColors.first(),
    val showAddDialog: Boolean = false,
    val showTrash: Boolean = false,
    val isLoading: Boolean = false
)
