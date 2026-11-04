package com.example.beiwang.ui.todo

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.beiwang.data.entity.TodoEntity
import com.example.beiwang.data.entity.splitTagIds
import com.example.beiwang.ui.common.OrderableList
import com.example.beiwang.ui.shopping.ShoppingEmptyHint
import com.example.beiwang.ui.shopping.ShoppingSectionHeader
import com.example.beiwang.ui.shopping.StatItem
import com.example.beiwang.ui.tag.TagChip
import com.example.beiwang.ui.tag.resolveTags
import com.example.beiwang.ui.theme.Blue600
import com.example.beiwang.ui.theme.Synced
import com.example.beiwang.ui.theme.Unsynced
import kotlin.comparisons.thenBy

/**
 * 待办列表。结构与购物清单一致：统计行 + 未完成/已完成两段 + 多选删除 + 手动排序，
 * 排序交互与购物清单共用 [OrderableList]（每行左侧常驻 ↑ / ↓ 按钮）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    onSearchClick: () -> Unit,
    onTodoClick: (String) -> Unit,
    onNewTodoClick: () -> Unit,
    viewModel: TodoViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var listFilter by remember { mutableStateOf(0) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }

    fun toggle(id: String) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
    }
    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
    }

    val pendingTodos = uiState.todos.filter { !it.isCompleted }
        .sortedWith(
            compareBy<TodoEntity> { it.sortOrder }
                .thenBy { it.priority }
                .thenBy { it.title }
        )
    val completedTodos = uiState.todos.filter { it.isCompleted }
        .sortedWith(
            compareBy<TodoEntity> { it.sortOrder }
                .thenBy { it.priority }
                .thenBy { it.title }
        )
    val allTodos = pendingTodos + completedTodos

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("已选 ${selectedIds.size}") },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "取消选择")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val allIds = allTodos.map { it.id }
                                if (selectedIds.containsAll(allIds) && allIds.isNotEmpty()) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    selectedIds.addAll(allIds)
                                }
                            }
                        ) { Text(if (selectedIds.size == allTodos.size && allTodos.isNotEmpty()) "取消全选" else "全选") }
                        IconButton(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                viewModel.deleteTodos(selectedIds.toList())
                                exitSelection()
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除所选",
                                tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("待办") },
                    actions = {
                        IconButton(onClick = { selectionMode = true }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "多选")
                        }
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                com.example.beiwang.ui.common.CreateButton("添加待办", onNewTodoClick)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            com.example.beiwang.ui.common.ListFilters(listFilter,
                listOf("全部", "未完成 ${pendingTodos.size}", "已完成 ${completedTodos.size}"), { listFilter = it })

            if (uiState.todos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("暂无待办", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                        Text("点击右下角按钮添加", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (listFilter != 2) {
                    item { ShoppingSectionHeader(title = "未完成", count = pendingTodos.size, color = Blue600) }
                    if (pendingTodos.isEmpty()) {
                        item { ShoppingEmptyHint("还没有待办事项") }
                    } else {
                        item {
                            OrderableList<TodoEntity>(
                                items = pendingTodos,
                                keyOf = { it.id },
                                onReorder = { reordered -> viewModel.reorderTodos(reordered.map { it.id }) }
                            ) { todo ->
                                TodoCard(
                                    todo = todo,
                                    allTags = uiState.allTags,
                                    selectionMode = selectionMode,
                                    isSelected = selectedIds.contains(todo.id),
                                    onToggleCompletion = { viewModel.toggleTodoCompletion(todo.id, it) },
                                    onClick = { onTodoClick(todo.id) },
                                    onToggleSelection = { toggle(todo.id) }
                                )
                            }
                        }
                    }

                    }
                    if (listFilter != 1) {
                    item { ShoppingSectionHeader(title = "已完成", count = completedTodos.size, color = Synced) }
                    if (completedTodos.isEmpty()) {
                        item { ShoppingEmptyHint("还没有已完成的待办") }
                    } else {
                        item {
                            OrderableList<TodoEntity>(
                                items = completedTodos,
                                keyOf = { it.id },
                                onReorder = { reordered -> viewModel.reorderTodos(reordered.map { it.id }) }
                            ) { todo ->
                                TodoCard(
                                    todo = todo,
                                    allTags = uiState.allTags,
                                    selectionMode = selectionMode,
                                    isSelected = selectedIds.contains(todo.id),
                                    onToggleCompletion = { viewModel.toggleTodoCompletion(todo.id, it) },
                                    onClick = { onTodoClick(todo.id) },
                                    onToggleSelection = { toggle(todo.id) }
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
fun TodoCard(
    todo: TodoEntity,
    allTags: List<com.example.beiwang.data.entity.TagEntity>,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleCompletion: (Boolean) -> Unit,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit = {}
) {
    val tags = resolveTags(allTags, todo.tagIds.splitTagIds().toSet())
    val handleClick = if (selectionMode) onToggleSelection else onClick

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = handleClick),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Checkbox(
                    checked = todo.isCompleted,
                    onCheckedChange = onToggleCompletion,
                    colors = CheckboxDefaults.colors(checkedColor = Blue600),
                    modifier = Modifier.size(48.dp)
                )
            }

            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Text(
                    text = todo.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (todo.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null
                )
                if (todo.note.isNotBlank()) {
                    Text(
                        text = todo.note,
                        fontSize = 12.sp,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val (pLabel, pTextColor, pBgColor) = when (todo.priority) {
                1 -> Triple("高", Color(0xFFE53935), Color(0x1FE53935))
                2 -> Triple("中", Color(0xFFFB8C00), Color(0x1FFB8C00))
                else -> Triple("低", Color(0xFF1E88E5), Color(0x1F1E88E5))
            }
            Text(
                text = pLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = pTextColor,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .background(pBgColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )

            if (tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(start = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { TagChip(it) }
                }
            }

            if (!selectionMode && todo.syncStatus == "UNSYNCED") {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "未同步",
                    modifier = Modifier.padding(start = 6.dp).size(14.dp),
                    tint = Unsynced
                )
            }
        }
    }
}
