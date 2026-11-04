package com.example.beiwang.ui.shopping

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.beiwang.data.entity.ShoppingItemEntity
import com.example.beiwang.data.entity.TagEntity
import com.example.beiwang.data.entity.splitTagIds
import com.example.beiwang.ui.common.OrderableList
import com.example.beiwang.ui.tag.TagChip
import com.example.beiwang.ui.tag.resolveTags
import com.example.beiwang.ui.theme.*
import kotlin.comparisons.thenBy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    onSearchClick: () -> Unit,
    onItemClick: (String) -> Unit,
    onNewItemClick: () -> Unit,
    viewModel: ShoppingViewModel = viewModel()
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

    // 组内排序：未完成在上、已购买沉下；组内先按用户手动排序位，再按优先级升序，
    // 最后按名称稳定排序（存量数据 sortOrder 全为 0 时自动退化为原来的「优先级 + 名称」）。
    val pendingItems = uiState.items.filter { !it.isCompleted }
        .sortedWith(
            compareBy<ShoppingItemEntity> { it.sortOrder }
                .thenBy { it.priority }
                .thenBy { it.name }
        )
    val completedItems = uiState.items.filter { it.isCompleted }
        .sortedWith(
            compareBy<ShoppingItemEntity> { it.sortOrder }
                .thenBy { it.priority }
                .thenBy { it.name }
        )
    val allItems = pendingItems + completedItems

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
                                val allIds = allItems.map { it.id }
                                if (selectedIds.containsAll(allIds) && allIds.isNotEmpty()) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    selectedIds.addAll(allIds)
                                }
                            }
                        ) {
                            Text(
                                if (selectedIds.size == allItems.size && allItems.isNotEmpty()) "取消全选"
                                else "全选"
                            )
                        }
                        IconButton(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                viewModel.deleteItems(selectedIds.toList())
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
                    title = { Text("购物清单") },
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
                com.example.beiwang.ui.common.CreateButton("添加商品", onNewItemClick)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            com.example.beiwang.ui.common.ListFilters(listFilter,
                listOf("全部", "待购买 ${pendingItems.size}", "已完成 ${completedItems.size}"), { listFilter = it })

            if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("暂无商品", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
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
                    item {
                        ShoppingSectionHeader(title = "待购买", count = pendingItems.size, color = Blue600)
                    }
                    if (pendingItems.isEmpty()) {
                        item { ShoppingEmptyHint("还没有待购买的商品") }
                    } else {
                        item {
                            OrderableList(
                                items = pendingItems,
                                keyOf = { it.id },
                                onReorder = { reordered ->
                                    viewModel.reorderItems(reordered.map { it.id })
                                },
                            ) { item ->
                                ShoppingItemCard(
                                    item = item,
                                    allTags = uiState.allTags,
                                    selectionMode = selectionMode,
                                    isSelected = selectedIds.contains(item.id),
                                    onToggleCompletion = { viewModel.toggleItemCompletion(item.id, it) },
                                    onClick = { onItemClick(item.id) },
                                    onToggleSelection = { toggle(item.id) }
                                )
                            }
                        }
                    }

                    }
                    if (listFilter != 1) {
                    item {
                        ShoppingSectionHeader(title = "已购买", count = completedItems.size, color = Synced)
                    }
                    if (completedItems.isEmpty()) {
                        item { ShoppingEmptyHint("还没有已购买的商品") }
                    } else {
                        item {
                            OrderableList(
                                items = completedItems,
                                keyOf = { it.id },
                                onReorder = { reordered ->
                                    viewModel.reorderItems(reordered.map { it.id })
                                },
                            ) { item ->
                                ShoppingItemCard(
                                    item = item,
                                    allTags = uiState.allTags,
                                    selectionMode = selectionMode,
                                    isSelected = selectedIds.contains(item.id),
                                    onToggleCompletion = { viewModel.toggleItemCompletion(item.id, it) },
                                    onClick = { onItemClick(item.id) },
                                    onToggleSelection = { toggle(item.id) }
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
fun ShoppingStatsRow(totalCount: Int, pendingCount: Int, completedCount: Int, unsyncedCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(label = "全部", count = totalCount, color = Blue600)
        StatItem(label = "待购买", count = pendingCount, color = Unsynced)
        StatItem(label = "已购买", count = completedCount, color = Synced)
        StatItem(label = "待同步", count = unsyncedCount, color = Unsynced)
    }
}

@Composable
fun StatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ShoppingSectionHeader(title: String, count: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "($count)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ShoppingEmptyHint(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    )
}

@Composable
fun ShoppingItemCard(
    item: ShoppingItemEntity,
    allTags: List<TagEntity>,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleCompletion: (Boolean) -> Unit,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit = {}
) {
    val tags = resolveTags(allTags, item.tagIds.splitTagIds().toSet())
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
                    checked = item.isCompleted,
                    onCheckedChange = onToggleCompletion,
                    colors = CheckboxDefaults.colors(checkedColor = Blue600),
                    modifier = Modifier.size(48.dp)
                )
            }

            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Text(
                    text = item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null
                )
            }

            // P1/P2/P3 都在列表里显示：P1 红、P2 橙、P3 蓝（P3 为默认最低优先级，也要可见）。
            val (pLabel, pTextColor, pBgColor) = when (item.priority) {
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

            Text(
                text = "×${item.quantity} ${item.unit}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp)
            )

            if (!selectionMode && item.syncStatus == "UNSYNCED") {
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
