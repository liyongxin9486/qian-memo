package com.example.beiwang.ui.shopping

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.entity.ShoppingItemEntity
import com.example.beiwang.data.entity.splitTagIds
import com.example.beiwang.data.entity.joinTagIds
import com.example.beiwang.data.repository.ShoppingRepository
import com.example.beiwang.data.repository.TagRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ShoppingDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ShoppingRepository
    private val tagRepository: TagRepository

    private val _uiState = MutableStateFlow(ShoppingDetailUiState())
    val uiState: StateFlow<ShoppingDetailUiState> = _uiState.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ShoppingRepository(db.shoppingItemDao(), application)
        tagRepository = TagRepository(db.tagDao(), application)

        viewModelScope.launch {
            tagRepository.getAllTags().collect { tags ->
                _uiState.update { it.copy(allTags = tags) }
            }
        }
    }

    fun loadItem(itemId: String) {
        if (itemId == "new") {
            _uiState.update { it.copy(isNew = true) }
            return
        }

        viewModelScope.launch {
            val item = repository.getItemById(itemId)
            item?.let {
                _uiState.update { state ->
                    state.copy(
                        item = it,
                        isNew = false,
                        name = it.name,
                        quantity = it.quantity.toString(),
                        unit = it.unit,
                        priority = it.priority,
                        selectedTagIds = it.tagIds.splitTagIds().toSet()
                    )
                }
            }
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun updateQuantity(quantity: String) {
        _uiState.update { it.copy(quantity = quantity) }
    }

    fun updateUnit(unit: String) {
        _uiState.update { it.copy(unit = unit) }
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

    fun saveItem() {
        val state = _uiState.value
        if (state.name.isBlank()) return

        viewModelScope.launch {
            val tagIds = state.selectedTagIds.joinTagIds()
            if (state.isNew) {
                val newItem = ShoppingItemEntity(
                    name = state.name,
                    quantity = state.quantity.toIntOrNull() ?: 1,
                    unit = state.unit,
                    priority = state.priority,
                    tagIds = tagIds
                )
                repository.insertItem(newItem)
            } else {
                state.item?.let { existingItem ->
                    val updatedItem = existingItem.copy(
                        name = state.name,
                        quantity = state.quantity.toIntOrNull() ?: 1,
                        unit = state.unit,
                        priority = state.priority,
                        tagIds = tagIds,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateItem(updatedItem)
                }
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun deleteItem() {
        val itemId = _uiState.value.item?.id ?: return
        viewModelScope.launch {
            repository.softDeleteItem(itemId)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}

data class ShoppingDetailUiState(
    val item: ShoppingItemEntity? = null,
    val isNew: Boolean = false,
    val name: String = "",
    val quantity: String = "1",
    val unit: String = "个",
    val priority: Int = 3,
    val selectedTagIds: Set<String> = emptySet(),
    val allTags: List<com.example.beiwang.data.entity.TagEntity> = emptyList(),
    val isSaved: Boolean = false
)
