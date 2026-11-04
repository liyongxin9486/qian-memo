package com.example.beiwang.ui.shopping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.beiwang.ui.tag.TagSelector
import com.example.beiwang.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingDetailScreen(
    itemId: String,
    onBackClick: () -> Unit,
    viewModel: ShoppingDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }
    
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onBackClick()
        }
    }
    
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 统一"返回/保存"逻辑：新建且内容为空时直接返回（不保存）；否则保存。
    // 修复：左上角返回键原先直接调 saveItem()，而 saveItem 在空白时会提前 return，
    // 于是"内容留空点返回"表现为点了没反应。这里与系统返回手势共用同一逻辑。
    val handleBack: () -> Unit = {
        if (uiState.isNew && uiState.name.isBlank()) {
            onBackClick()
        } else {
            viewModel.saveItem()
        }
    }

    BackHandler { handleBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isNew) "添加商品" else "编辑商品") },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!uiState.isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("商品名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.quantity,
                    onValueChange = { viewModel.updateQuantity(it) },
                    label = { Text("数量") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                
                OutlinedTextField(
                    value = uiState.unit,
                    onValueChange = { viewModel.updateUnit(it) },
                    label = { Text("单位") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Text("优先级", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1 to "高", 2 to "中", 3 to "低").forEach { (value, label) ->
                    val selected = uiState.priority == value
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.updatePriority(value) },
                        label = { Text(label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Blue600.copy(alpha = 0.15f),
                            selectedLabelColor = Blue600,
                            selectedLeadingIconColor = Blue600
                        )
                    )
                }
            }

            Text("标签", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TagSelector(
                allTags = uiState.allTags,
                selectedTagIds = uiState.selectedTagIds,
                onToggle = { viewModel.toggleTag(it) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = handleBack,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue600)
            ) {
                Text("保存")
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这个商品吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteItem()
                    showDeleteDialog = false
                }) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
