package com.example.beiwang.ui.todo

import kotlinx.coroutines.flow.asStateFlow
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.entity.TagEntity
import com.example.beiwang.data.entity.TodoEntity
import com.example.beiwang.data.entity.joinTagIds
import com.example.beiwang.data.entity.splitTagIds
import com.example.beiwang.data.repository.TagRepository
import com.example.beiwang.data.repository.TodoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TodoDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TodoRepository
    private val tagRepository: TagRepository

    private val _uiState = MutableStateFlow(TodoDetailUiState())
    val uiState: StateFlow<TodoDetailUiState> = _uiState.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = TodoRepository(db.todoDao(), application)
        tagRepository = TagRepository(db.tagDao(), application)

        viewModelScope.launch {
            tagRepository.getAllTags().collect { tags ->
                _uiState.update { it.copy(allTags = tags) }
            }
        }
    }

    fun loadTodo(todoId: String) {
        if (todoId == "new") {
            _uiState.update { it.copy(isNew = true) }
            return
        }
        viewModelScope.launch {
            val todo = repository.getTodoById(todoId)
            todo?.let {
                _uiState.update { state ->
                    state.copy(
                        todo = it,
                        isNew = false,
                        title = it.title,
                        note = it.note,
                        priority = it.priority,
                        selectedTagIds = it.tagIds.splitTagIds().toSet()
                    )
                }
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun updatePriority(priority: Int) {
        _uiState.update { it.copy(priority = priority) }
    }

    fun toggleTag(tagId: String) {
        _uiState.update { state ->
            val set = state.selectedTagIds.toMutableSet()
            if (set.contains(tagId)) set.remove(tagId) else set.add(tagId)
            state.copy(selectedTagIds = set)
        }
    }

    fun saveTodo() {
        val state = _uiState.value
        if (state.title.isBlank()) return

        viewModelScope.launch {
            val tagIds = state.selectedTagIds.joinTagIds()
            if (state.isNew) {
                repository.insertTodo(
                    TodoEntity(
                        title = state.title,
                        note = state.note,
                        priority = state.priority,
                        tagIds = tagIds
                    )
                )
            } else {
                state.todo?.let { existing ->
                    repository.updateTodo(
                        existing.copy(
                            title = state.title,
                            note = state.note,
                            priority = state.priority,
                            tagIds = tagIds,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun deleteTodo() {
        val id = _uiState.value.todo?.id ?: return
        viewModelScope.launch {
            repository.softDeleteTodo(id)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}

data class TodoDetailUiState(
    val todo: TodoEntity? = null,
    val isNew: Boolean = false,
    val title: String = "",
    val note: String = "",
    val priority: Int = 3,
    val selectedTagIds: Set<String> = emptySet(),
    val allTags: List<TagEntity> = emptyList(),
    val isSaved: Boolean = false
)
