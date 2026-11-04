package com.example.beiwang.ui.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.beiwang.data.AuthManager
import com.example.beiwang.data.sync.SyncManager
import com.example.beiwang.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onTagManageClick: () -> Unit,
    onTrashClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showLocalRestoreDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val themeMode by ThemeManager.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupViewModel: BackupViewModel = viewModel()

    // 同步状态：记录上次同步时间，点击「立即同步」后刷新展示
    var lastSync by remember { mutableStateOf(AuthManager.getLastSync()) }
    var isSyncing by remember { mutableStateOf(false) }

    // 导出：用户在系统文件选择器中选择保存位置
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { backupViewModel.exportToUri(uri) }
                .onSuccess { Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    // 导入：用户选择备份文件
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { backupViewModel.importFromUri(uri) }
                .onSuccess { Toast.makeText(context, "导入成功，数据已恢复", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    // 导出 Markdown
    val exportMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { backupViewModel.exportMdToUri(uri) }
                .onSuccess { Toast.makeText(context, "已导出 Markdown", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    // 导出纯文本
    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { backupViewModel.exportTxtToUri(uri) }
                .onSuccess { Toast.makeText(context, "已导出文本", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    // 导出 Excel（xlsx）
    val exportXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { backupViewModel.exportXlsxToUri(uri) }
                .onSuccess { Toast.makeText(context, "已导出 Excel", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
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
        ) {
            // 用户信息卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 头像
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        val email = AuthManager.email
                        Text(
                            text = email.substringBefore('@').ifEmpty { "用户" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = email.ifEmpty { "未登录" },
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            // 数据同步
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    ProfileMenuItem(
                        icon = Icons.Default.CloudUpload,
                        title = if (isSyncing) "同步中…" else "立即同步",
                        subtitle = if (lastSync > 0)
                            "上次同步：${formatSyncTime(lastSync)}"
                        else "尚未同步过（仅本地）",
                        onClick = {
                            if (!AuthManager.isLoggedIn) {
                                Toast.makeText(context, "请先登录后再同步", Toast.LENGTH_SHORT).show()
                                return@ProfileMenuItem
                            }
                            isSyncing = true
                            SyncManager.requestSync(context)
                            Toast.makeText(context, "已触发同步，将在后台进行", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                kotlinx.coroutines.delay(2000)
                                lastSync = AuthManager.getLastSync()
                                isSyncing = false
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 功能列表
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    ProfileMenuItem(
                        icon = Icons.Default.Label,
                        title = "标签管理",
                        onClick = onTagManageClick
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ProfileMenuItem(
                        icon = Icons.Default.Delete,
                        title = "回收站",
                        onClick = onTrashClick
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ProfileMenuItem(
                        icon = Icons.Default.DarkMode,
                        title = "主题",
                        subtitle = themeMode.label,
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 数据备份
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    ProfileMenuItem(
                        icon = Icons.Default.FileUpload,
                        title = "导出备份",
                        subtitle = "JSON / Markdown / 文本 / Excel",
                        onClick = { showExportDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ProfileMenuItem(
                        icon = Icons.Default.Backup,
                        title = "本地备份",
                        subtitle = "备份到应用目录",
                        onClick = {
                            scope.launch {
                                runCatching { backupViewModel.backupToLocal() }
                                    .onSuccess { file ->
                                        Toast.makeText(
                                            context,
                                            "已备份：${file.name}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    .onFailure {
                                        Toast.makeText(
                                            context,
                                            "备份失败：${it.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ProfileMenuItem(
                        icon = Icons.Default.FileDownload,
                        title = "导入备份",
                        subtitle = "从文件恢复数据",
                        onClick = { showImportConfirm = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ProfileMenuItem(
                        icon = Icons.Default.Restore,
                        title = "恢复本地备份",
                        subtitle = "从应用目录恢复",
                        onClick = { showLocalRestoreDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 关于和退出
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    ProfileMenuItem(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "退出登录",
                        titleColor = Color.Red,
                        onClick = { showLogoutDialog = true }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    // 退出登录确认对话框
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("确认退出") },
            text = { Text("退出后将返回登录页；已开启「记住我」的账号与密码会保留，下次打开 App 可自动登录，本机数据也保留。确定退出吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutClick()
                    }
                ) {
                    Text("退出", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 主题切换对话框
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("选择主题") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemeManager.setMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    ThemeManager.setMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = mode.label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // 导入确认对话框（导入会按主键覆盖已有数据）
    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("导入备份") },
            text = { Text("导入会用备份文件中的数据覆盖同名记录（按 ID 合并）。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    // 放开 MIME 过滤：部分机型（如 OPPO/ColorOS）的文件选择器会把 .json 备份
                    // 识别成 application/octet-stream 或 */*，按具体类型过滤会导致文件灰显无法选中。
                    // 用 "*/*" 让备份文件可选中，具体格式由 importFromUri 解析时再校验。
                    importLauncher.launch(arrayOf("*/*"))
                }) {
                    Text("选择文件")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 恢复本地备份：直接读取应用私有目录中的备份文件（无需 SAF 文件选择器）
    if (showLocalRestoreDialog) {
        val files = remember { backupViewModel.listLocalBackups() }
        AlertDialog(
            onDismissRequest = { showLocalRestoreDialog = false },
            title = { Text("恢复本地备份") },
            text = {
                if (files.isEmpty()) {
                    Text("应用目录中还没有本地备份文件。请先使用“本地备份”。")
                } else {
                    Column {
                        Text(
                            text = "选择要恢复的备份文件（将按 ID 合并覆盖现有数据）：",
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(modifier = Modifier.height(320.dp)) {
                            items(files) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showLocalRestoreDialog = false
                                            scope.launch {
                                                runCatching { backupViewModel.importFromFile(file) }
                                                    .onSuccess {
                                                        Toast.makeText(
                                                            context,
                                                            "导入成功：${file.name}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                    .onFailure {
                                                        Toast.makeText(
                                                            context,
                                                            "导入失败：${it.message}",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                            }
                                        }
                                        .padding(vertical = 12.dp)
                                ) {
                                    Text(text = file.name, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocalRestoreDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // 导出格式选择：点击「导出备份」后在此选择具体格式
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("选择导出格式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExportOptionRow(
                        icon = Icons.Default.FileUpload,
                        title = "JSON（完整备份）",
                        onClick = {
                            showExportDialog = false
                            exportLauncher.launch("beiwang_backup_${System.currentTimeMillis()}.json")
                        }
                    )
                    ExportOptionRow(
                        icon = Icons.Default.Description,
                        title = "Markdown（.md）",
                        onClick = {
                            showExportDialog = false
                            exportMdLauncher.launch("beiwang_${System.currentTimeMillis()}.md")
                        }
                    )
                    ExportOptionRow(
                        icon = Icons.Default.Subject,
                        title = "文本（.txt）",
                        onClick = {
                            showExportDialog = false
                            exportTxtLauncher.launch("beiwang_${System.currentTimeMillis()}.txt")
                        }
                    )
                    ExportOptionRow(
                        icon = Icons.Default.TableChart,
                        title = "Excel（.xlsx）",
                        onClick = {
                            showExportDialog = false
                            exportXlsxLauncher.launch("beiwang_${System.currentTimeMillis()}.xlsx")
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ExportOptionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = title, fontSize = 15.sp)
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = titleColor,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 将同步时间戳格式化为「yyyy-MM-dd HH:mm」展示；0 表示从未同步。 */
private fun formatSyncTime(ts: Long): String {
    if (ts <= 0) return "从未同步"
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    } catch (e: Exception) {
        ""
    }
}
