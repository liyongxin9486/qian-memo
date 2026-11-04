package com.example.beiwang.ui.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.entity.ShoppingItemEntity
import com.example.beiwang.data.entity.TodoEntity
import com.example.beiwang.data.repository.MemoRepository
import com.example.beiwang.data.repository.ShoppingRepository
import com.example.beiwang.data.repository.TodoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TrashViewModel(application: Application) : AndroidViewModel(application) {
    private val memoRepository: MemoRepository
    private val shoppingRepository: ShoppingRepository
    private val todoRepository: TodoRepository
    
    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()
    
    init {
        val db = AppDatabase.getDatabase(application)
        memoRepository = MemoRepository(db.memoDao(), application)
        shoppingRepository = ShoppingRepository(db.shoppingItemDao(), application)
        todoRepository = TodoRepository(db.todoDao(), application)
        
        viewModelScope.launch {
            memoRepository.getDeletedMemos().collect { memos ->
                _uiState.update { it.copy(deletedMemos = memos) }
            }
        }

        viewModelScope.launch {
            shoppingRepository.getDeletedItems().collect { items ->
                _uiState.update { it.copy(deletedShoppingItems = items) }
            }
        }

        viewModelScope.launch {
            todoRepository.getDeletedTodos().collect { todos ->
                _uiState.update { it.copy(deletedTodos = todos) }
            }
        }
    }

    fun restoreMemo(memoId: String) {
        viewModelScope.launch {
            memoRepository.restoreMemo(memoId)
        }
    }

    fun permanentDeleteMemo(memo: MemoEntity) {
        viewModelScope.launch {
            memoRepository.permanentDeleteMemo(memo)
        }
    }

    fun restoreItem(itemId: String) {
        viewModelScope.launch {
            shoppingRepository.restoreItem(itemId)
        }
    }

    fun permanentDeleteItem(item: ShoppingItemEntity) {
        viewModelScope.launch {
            shoppingRepository.permanentDeleteItem(item)
        }
    }

    fun restoreTodo(todoId: String) {
        viewModelScope.launch {
            todoRepository.restoreTodo(todoId)
        }
    }

    fun permanentDeleteTodo(todo: TodoEntity) {
        viewModelScope.launch {
            todoRepository.permanentDeleteTodo(todo)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            memoRepository.emptyTrash()
            shoppingRepository.emptyTrash()
            todoRepository.emptyTrash()
        }
    }
}

data class TrashUiState(
    val deletedMemos: List<MemoEntity> = emptyList(),
    val deletedShoppingItems: List<ShoppingItemEntity> = emptyList(),
    val deletedTodos: List<TodoEntity> = emptyList(),
    val isLoading: Boolean = false
) {
    val totalCount: Int
        get() = deletedMemos.size + deletedShoppingItems.size + deletedTodos.size
}
