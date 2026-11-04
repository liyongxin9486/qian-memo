package com.example.beiwang.ui.shopping

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.entity.ShoppingItemEntity
import com.example.beiwang.data.entity.TagEntity
import com.example.beiwang.data.repository.ShoppingRepository
import com.example.beiwang.data.repository.TagRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ShoppingViewModel(application: Application) : AndroidViewModel(application) {
    private val shoppingRepository: ShoppingRepository
    private val tagRepository: TagRepository

    private val _uiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = _uiState.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        shoppingRepository = ShoppingRepository(db.shoppingItemDao(), application)
        tagRepository = TagRepository(db.tagDao())

        viewModelScope.launch {
            shoppingRepository.getAllItems().collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }

        viewModelScope.launch {
            tagRepository.getAllTags().collect { tags ->
                _uiState.update { it.copy(allTags = tags) }
            }
        }
        
        viewModelScope.launch {
            shoppingRepository.getTotalCount().collect { count ->
                _uiState.update { it.copy(totalCount = count) }
            }
        }
        viewModelScope.launch {
            shoppingRepository.getPendingCount().collect { count ->
                _uiState.update { it.copy(pendingCount = count) }
            }
        }
        viewModelScope.launch {
            shoppingRepository.getCompletedCount().collect { count ->
                _uiState.update { it.copy(completedCount = count) }
            }
        }
        viewModelScope.launch {
            shoppingRepository.getUnsyncedItems().collect { items ->
                _uiState.update { it.copy(unsyncedCount = items.size) }
            }
        }
    }
    
    fun toggleItemCompletion(itemId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            shoppingRepository.updateItemCompletionStatus(itemId, isCompleted)
        }
    }
    
    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            shoppingRepository.softDeleteItem(itemId)
        }
    }

    fun deleteItems(ids: List<String>) {
        viewModelScope.launch {
            shoppingRepository.softDeleteItems(ids)
        }
    }

    /** 手动排序结果：按传入顺序重写 sortOrder，并推送到服务端（换设备顺序一致）。 */
    fun reorderItems(orderedIds: List<String>) {
        viewModelScope.launch {
            shoppingRepository.applySortOrder(orderedIds)
        }
    }
}

data class ShoppingUiState(
    val items: List<ShoppingItemEntity> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val completedCount: Int = 0,
    val unsyncedCount: Int = 0,
    val isLoading: Boolean = false
)
