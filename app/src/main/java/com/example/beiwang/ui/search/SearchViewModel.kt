package com.example.beiwang.ui.search

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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val memoRepository: MemoRepository
    private val shoppingRepository: ShoppingRepository
    private val todoRepository: TodoRepository
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    // 用查询流驱动搜索：flatMapLatest 在查询变化时自动取消上一个(旧查询)的 collector，
    // 避免每敲一个字符都叠加一个新的永久 collector（旧查询的 Flow 永不取消 → 泄漏 + 旧结果覆盖新结果）。
    // 旧实现每次 search() 都 launch 一个新的 collect 且从不取消，输入越长泄漏越多，且任一 DB 变更时
    // 所有历史 collector 都会把各自(旧查询)的结果写回 uiState，造成结果闪烁/错乱（BUG-10）。
    private val searchQuery = MutableStateFlow("")

    init {
        val db = AppDatabase.getDatabase(application)
        memoRepository = MemoRepository(db.memoDao(), application)
        shoppingRepository = ShoppingRepository(db.shoppingItemDao(), application)
        todoRepository = TodoRepository(db.todoDao(), application)

        viewModelScope.launch {
            searchQuery
                .flatMapLatest { query ->
                    if (query.isBlank()) flowOf(emptyList())
                    else memoRepository.searchMemos(query)
                }
                .collect { memos -> _uiState.update { it.copy(memos = memos) } }
        }

        viewModelScope.launch {
            searchQuery
                .flatMapLatest { query ->
                    if (query.isBlank()) flowOf(emptyList())
                    else shoppingRepository.searchItems(query)
                }
                .collect { items -> _uiState.update { it.copy(shoppingItems = items) } }
        }

        // 待办：标题与备注都参与匹配（TodoDao.searchTodos 里已覆盖两列）
        viewModelScope.launch {
            searchQuery
                .flatMapLatest { query ->
                    if (query.isBlank()) flowOf(emptyList())
                    else todoRepository.searchTodos(query)
                }
                .collect { todos -> _uiState.update { it.copy(todos = todos) } }
        }
    }

    fun search(query: String) {
        searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }
}

data class SearchUiState(
    val searchQuery: String = "",
    val memos: List<MemoEntity> = emptyList(),
    val shoppingItems: List<ShoppingItemEntity> = emptyList(),
    val todos: List<TodoEntity> = emptyList(),
    val isLoading: Boolean = false
) {
    val totalCount: Int get() = memos.size + shoppingItems.size + todos.size
}
