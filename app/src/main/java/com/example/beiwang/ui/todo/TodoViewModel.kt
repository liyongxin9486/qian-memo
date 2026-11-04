package com.example.beiwang.ui.todo

import kotlinx.coroutines.flow.asStateFlow
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.entity.TagEntity
import com.example.beiwang.data.entity.TodoEntity
import com.example.beiwang.data.repository.TagRepository
import com.example.beiwang.data.repository.TodoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val todoRepository: TodoRepository
    private val tagRepository: TagRepository

    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        todoRepository = TodoRepository(db.todoDao(), application)
        tagRepository = TagRepository(db.tagDao())

        viewModelScope.launch {
            todoRepository.getAllTodos().collect { todos ->
                _uiState.update { it.copy(todos = todos) }
            }
        }
        viewModelScope.launch {
            tagRepository.getAllTags().collect { tags ->
                _uiState.update { it.copy(allTags = tags) }
            }
        }
        viewModelScope.launch {
            todoRepository.getTotalCount().collect { count ->
                _uiState.update { it.copy(totalCount = count) }
            }
        }
        viewModelScope.launch {
            todoRepository.getPendingCount().collect { count ->
                _uiState.update { it.copy(pendingCount = count) }
            }
        }
        viewModelScope.launch {
            todoRepository.getCompletedCount().collect { count ->
                _uiState.update { it.copy(completedCount = count) }
            }
        }
        viewModelScope.launch {
            todoRepository.getUnsyncedTodos().collect { todos ->
                _uiState.update { it.copy(unsyncedCount = todos.size) }
            }
        }
    }

    fun toggleTodoCompletion(todoId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            todoRepository.updateTodoCompletionStatus(todoId, isCompleted)
        }
    }

    fun deleteTodo(todoId: String) {
        viewModelScope.launch {
            todoRepository.softDeleteTodo(todoId)
        }
    }

    fun deleteTodos(ids: List<String>) {
        viewModelScope.launch {
            todoRepository.softDeleteTodos(ids)
        }
    }

    /** 手动排序：与购物清单共用提交逻辑，重写 sortOrder 并推送服务端。 */
    fun reorderTodos(orderedIds: List<String>) {
        viewModelScope.launch {
            todoRepository.applySortOrder(orderedIds)
        }
    }
}

data class TodoUiState(
    val todos: List<TodoEntity> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val completedCount: Int = 0,
    val unsyncedCount: Int = 0,
    val isLoading: Boolean = false
)
