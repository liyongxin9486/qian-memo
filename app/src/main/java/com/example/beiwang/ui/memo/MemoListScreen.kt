package com.example.beiwang.ui.memo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.entity.SyncStatus
import com.example.beiwang.data.entity.TagEntity
import com.example.beiwang.data.entity.splitTagIds
import com.example.beiwang.ui.tag.TagChip
import com.example.beiwang.ui.tag.resolveTags
import com.example.beiwang.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoListScreen(
    onSearchClick: () -> Unit,
    onMemoClick: (String) -> Unit,
    onNewMemoClick: () -> Unit,
    viewModel: MemoViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }

    fun toggle(id: String) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
    }
    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
    }

    // 根据筛选维度算出实际展示的列表（选择态的「全选」也基于它）。
    val displayedMemos = if (uiState.filter == MemoFilter.SYNC) {
        uiState.memos.filter { it.syncStatus == SyncStatus.UNSYNCED.name }
    } else {
        uiState.memos
    }

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
                                val allIds = displayedMemos.map { it.id }
                                if (selectedIds.containsAll(allIds) && allIds.isNotEmpty()) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    selectedIds.addAll(allIds)
                                }
                            }
                        ) {
                            Text(
                                if (selectedIds.size == displayedMemos.size && displayedMemos.isNotEmpty()) "取消全选"
                                else "全选"
                            )
                        }
                        IconButton(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                viewModel.deleteMemos(selectedIds.toList())
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
                    title = { Text("备忘录") },
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
                com.example.beiwang.ui.common.CreateButton("新建备忘录", onNewMemoClick)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 筛选：全部 / 同步状态
            MemoFilterRow(
                selected = uiState.filter,
                totalCount = uiState.totalCount,
                unsyncedCount = uiState.unsyncedCount,
                onSelect = { viewModel.setFilter(it) }
            )

            // 备忘录列表
            if (displayedMemos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.filter == MemoFilter.SYNC) "没有待同步的备忘录" else "暂无备忘录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp
                        )
                        Text("点击右下角按钮创建", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(
                        displayedMemos,
                        key = { it.id },
                        contentType = { "memo" }
                    ) { memo ->
                        MemoItem(
                            memo = memo,
                            allTags = uiState.allTags,
                            selectionMode = selectionMode,
                            isSelected = selectedIds.contains(memo.id),
                            onClick = { onMemoClick(memo.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(memo.id) },
                            onPin = { viewModel.togglePin(memo.id) },
                            onDelete = { viewModel.deleteMemo(memo.id) },
                            onToggleSelection = { toggle(memo.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoFilterRow(
    selected: MemoFilter,
    totalCount: Int,
    unsyncedCount: Int,
    onSelect: (MemoFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == MemoFilter.ALL,
            onClick = { onSelect(MemoFilter.ALL) },
            label = { Text("全部 $totalCount") },
            modifier = Modifier.weight(1f),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        FilterChip(
            selected = selected == MemoFilter.SYNC,
            onClick = { onSelect(MemoFilter.SYNC) },
            label = { Text("待同步 $unsyncedCount") },
            modifier = Modifier.weight(1f),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}

@Composable
fun MemoItem(
    memo: MemoEntity,
    allTags: List<TagEntity>,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onToggleSelection: () -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }
    val handleClick = if (selectionMode) onToggleSelection else onClick
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = handleClick),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.size(24.dp).padding(start = 4.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = memo.title.ifBlank { "（无标题）" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (memo.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (memo.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )

                if (memo.content.isNotBlank()) {
                    Text(memo.content, maxLines = 2, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
                if (memo.isPinned || memo.isFavorite) {
                    Text(listOfNotNull(if (memo.isPinned) "置顶" else null, if (memo.isFavorite) "收藏" else null).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                val tags = remember(memo.tagIds, allTags) {
                    resolveTags(allTags, memo.tagIds.splitTagIds().toSet())
                }
                if (tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { TagChip(it) }
                    }
                }
            }

            if (!selectionMode) {
                if (memo.syncStatus == SyncStatus.UNSYNCED.name) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "未同步",
                        modifier = Modifier.padding(start = 8.dp).size(16.dp),
                        tint = Unsynced
                    )
                }

                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "更多操作") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(if (memo.isFavorite) "取消收藏" else "收藏") }, onClick = { menuOpen = false; onToggleFavorite() })
                        DropdownMenuItem(text = { Text(if (memo.isPinned) "取消置顶" else "置顶") }, onClick = { menuOpen = false; onPin() })
                        DropdownMenuItem(text = { Text("移至回收站") }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
        }
    }
}
