package com.example.beiwang.ui.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.beiwang.data.entity.TagEntity
import com.example.beiwang.ui.theme.Blue600
import kotlin.comparisons.thenBy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManageScreen(
    onBackClick: () -> Unit,
    viewModel: TagViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<TagEntity?>(null) }
    var showTrashDialog by remember { mutableStateOf(false) }
    var showPermanentDialog by remember { mutableStateOf<TagEntity?>(null) }
    var showTrash by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("标签管理") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    if (uiState.deletedTags.isNotEmpty()) {
                        TextButton(onClick = { showTrash = !showTrash }) {
                            Text(if (showTrash) "完成" else "回收站(${uiState.deletedTags.size})", color = Blue600)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                containerColor = Blue600,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加标签", tint = Color.White)
            }
        }
    ) { paddingValues ->
        if (showTrash) {
            // 回收站：软删除的标签，可恢复或彻底删除
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.deletedTags.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
                            Text("回收站是空的", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }
                items(uiState.deletedTags, key = { it.id }) { tag ->
                    TrashTagItem(
                        tag = tag,
                        onRestore = { viewModel.restoreTag(tag) },
                        onPermanentDelete = { showPermanentDialog = tag }
                    )
                }
                if (uiState.deletedTags.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showTrashDialog = true }) {
                            Text("清空回收站", color = Color.Red)
                        }
                    }
                }
            }
        } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.tags.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize().padding(top = 24.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = "还没有标签，点右下角按钮新建",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            items(uiState.tags.sortedWith(compareBy<TagEntity> { it.sortOrder }.thenBy { it.name }), key = { it.id }) { tag ->
                TagItem(
                    tag = tag,
                    onDelete = { showDeleteDialog = tag }
                )
            }
        }
        }
    }

    if (uiState.showAddDialog) {
        AddTagDialog(
            name = uiState.newName,
            color = uiState.newColor,
            onNameChange = { viewModel.updateNewName(it) },
            onColorChange = { viewModel.updateNewColor(it) },
            onDismiss = { viewModel.hideAddDialog() },
            onConfirm = { viewModel.addTag() }
        )
    }

    showDeleteDialog?.let { tag ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除标签 \"${tag.name}\" 吗？已打在该标签下的备忘/购物项将不再显示此标签。删除后可在回收站恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTag(tag)
                        showDeleteDialog = null
                    }
                ) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showTrashDialog) {
        AlertDialog(
            onDismissRequest = { showTrashDialog = false },
            title = { Text("清空回收站") },
            text = { Text("确定要彻底删除回收站里的全部标签吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.emptyTrash()
                        showTrashDialog = false
                    }
                ) {
                    Text("清空", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrashDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    showPermanentDialog?.let { tag ->
        AlertDialog(
            onDismissRequest = { showPermanentDialog = null },
            title = { Text("彻底删除") },
            text = { Text("确定要彻底删除标签 \"${tag.name}\" 吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.permanentDeleteTag(tag)
                        showPermanentDialog = null
                    }
                ) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentDialog = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun TagItem(tag: TagEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(tag.color))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Label,
                contentDescription = null,
                tint = Color(tag.color),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = tag.name,
                fontSize = 16.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TrashTagItem(tag: TagEntity, onRestore: () -> Unit, onPermanentDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(tag.color))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Label,
                contentDescription = null,
                tint = Color(tag.color),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = tag.name,
                fontSize = 16.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            TextButton(onClick = onRestore) {
                Text("恢复", color = Blue600)
            }
            TextButton(onClick = onPermanentDelete) {
                Text("删除", color = Color.Red)
            }
        }
    }
}

@Composable
fun AddTagDialog(
    name: String,
    color: Int,
    onNameChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("标签名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("颜色", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TagColors.forEach { c ->
                        val selected = c == color
                        Box(
                            modifier = Modifier
                                .size(if (selected) 32.dp else 28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { onColorChange(c) }
                                .then(
                                    if (selected) Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        CircleShape
                                    ) else Modifier
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = name.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
