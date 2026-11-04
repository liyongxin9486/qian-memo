package com.example.beiwang.ui.trash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.entity.ShoppingItemEntity
import com.example.beiwang.data.entity.TodoEntity
import com.example.beiwang.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBackClick: () -> Unit,
    viewModel: TrashViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showEmptyDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回收站") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.totalCount > 0) {
                        IconButton(onClick = { showEmptyDialog = true }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "清空回收站")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 提示信息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "删除的项目保留 30 天",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            // 回收站列表
            if (uiState.totalCount == 0) {
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
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "回收站为空",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 备忘录
                    if (uiState.deletedMemos.isNotEmpty()) {
                        item {
                            Text(
                                text = "备忘录",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Blue600,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        
                        items(uiState.deletedMemos, key = { it.id }) { memo ->
                            TrashMemoItem(
                                memo = memo,
                                onRestore = { viewModel.restoreMemo(memo.id) },
                                onPermanentDelete = { viewModel.permanentDeleteMemo(memo) }
                            )
                        }
                    }

                    // 购物清单
                    if (uiState.deletedShoppingItems.isNotEmpty()) {
                        item {
                            Text(
                                text = "购物清单",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Blue600,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(uiState.deletedShoppingItems, key = { it.id }) { shoppingItem ->
                            TrashShoppingItem(
                                shoppingItem = shoppingItem,
                                onRestore = { viewModel.restoreItem(shoppingItem.id) },
                                onPermanentDelete = { viewModel.permanentDeleteItem(shoppingItem) }
                            )
                        }
                    }

                    // 待办
                    if (uiState.deletedTodos.isNotEmpty()) {
                        item {
                            Text(
                                text = "待办",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Blue600,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(uiState.deletedTodos, key = { it.id }) { todo ->
                            TrashTodoItem(
                                todo = todo,
                                onRestore = { viewModel.restoreTodo(todo.id) },
                                onPermanentDelete = { viewModel.permanentDeleteTodo(todo) }
                            )
                        }
                    }
                }
            }
            
            // 清空回收站按钮
            if (uiState.totalCount > 0) {
                Button(
                    onClick = { showEmptyDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("清空回收站")
                }
            }
        }
    }
    
    // 清空确认对话框
    if (showEmptyDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyDialog = false },
            title = { Text("确认清空") },
            text = { Text("确定要清空回收站吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.emptyTrash()
                        showEmptyDialog = false
                    }
                ) {
                    Text("清空", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun TrashMemoItem(
    memo: MemoEntity,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = memo.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    memo.deletedAt?.let { deletedAt ->
                        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                        Text(
                            text = "删除于 ${sdf.format(Date(deletedAt))}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRestore) {
                    Text("恢复", color = Blue600)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onPermanentDelete) {
                    Text("彻底删除", color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun TrashShoppingItem(
    shoppingItem: ShoppingItemEntity,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    val qtyText = buildString {
                        append(shoppingItem.name)
                        if (shoppingItem.quantity > 1) append(" ×${shoppingItem.quantity}${shoppingItem.unit}")
                    }
                    Text(
                        text = qtyText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val deletedAt = shoppingItem.deletedAt
                    if (deletedAt != null) {
                        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                        Text(
                            text = "删除于 ${sdf.format(Date(deletedAt))}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRestore) {
                    Text("恢复", color = Blue600)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onPermanentDelete) {
                    Text("彻底删除", color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun TrashTodoItem(
    todo: TodoEntity,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = todo.title.ifBlank { "（无标题）" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    todo.deletedAt?.let { deletedAt ->
                        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                        Text(
                            text = "删除于 ${sdf.format(Date(deletedAt))}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRestore) {
                    Text("恢复", color = Blue600)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onPermanentDelete) {
                    Text("彻底删除", color = Color.Red)
                }
            }
        }
    }
}
