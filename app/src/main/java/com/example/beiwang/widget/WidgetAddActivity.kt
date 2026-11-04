package com.example.beiwang.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beiwang.data.AuthManager
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.entity.ShoppingItemEntity
import com.example.beiwang.data.entity.SyncStatus
import com.example.beiwang.data.entity.TodoEntity
import com.example.beiwang.data.sync.SyncManager
import com.example.beiwang.ui.theme.BeiwangTheme
import com.example.beiwang.ui.theme.Blue600
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 桌面组件「＋」弹出的添加窗口。
 *
 * 两步式：先选类型（备忘录 / 购物清单 / 待办），再在同一个对话框里填内容。
 * 保存后立刻做三件事：写本地库 → 触发同步（App 内通过 WebSocket 收到广播即时刷新）→
 * 刷新桌面组件，因此“添加后立即同步给 App 与网页后台”。
 *
 * 之所以用 Activity 而不是在组件里弹菜单：RemoteViews 只能挂 PendingIntent 响应点击，
 * 无法弹出下拉菜单或对话框，所有弹窗类交互都必须借一个对话框主题的 Activity 承载。
 */
class WidgetAddActivity : ComponentActivity() {

    private enum class Kind(val label: String) {
        MEMO("备忘录"),
        SHOPPING("购物清单"),
        TODO("待办")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeiwangTheme {
                AddDialog(
                    onDismiss = { finish() },
                    onSave = { kind, title, body, qty, unit, priority ->
                        save(kind, title, body, qty, unit, priority)
                    }
                )
            }
        }
    }

    @Composable
    private fun AddDialog(
        onDismiss: () -> Unit,
        onSave: (Kind, String, String, Int, String, Int) -> Unit
    ) {
        var kind by remember { mutableStateOf<Kind?>(null) }
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var quantity by remember { mutableStateOf("1") }
        var unit by remember { mutableStateOf("个") }
        // 优先级：购物清单与待办都支持（P1 最高 / P2 / P3 默认），添加时即可指定
        var priority by remember { mutableStateOf(3) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (kind == null) "添加到七安备忘" else "新建${kind!!.label}") },
            text = {
                if (kind == null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        KindOption("备忘录", "标题 + 正文") { kind = Kind.MEMO }
                        KindOption("购物清单", "名称 + 数量单位") { kind = Kind.SHOPPING }
                        KindOption("待办", "标题 + 备注") { kind = Kind.TODO }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = {
                                Text(
                                    when (kind) {
                                        Kind.SHOPPING -> "商品名称"
                                        Kind.TODO -> "待办标题"
                                        else -> "备忘标题"
                                    }
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (kind != Kind.MEMO) {
                            // 购物清单 / 待办支持优先级，添加时就能定好，不必回头再编辑
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "优先级",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(1 to "P1", 2 to "P2", 3 to "P3").forEach { (value, label) ->
                                    FilterChip(
                                        selected = priority == value,
                                        onClick = { priority = value },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Blue600.copy(alpha = 0.15f),
                                            selectedLabelColor = Blue600
                                        )
                                    )
                                }
                            }
                        }
                        if (kind == Kind.SHOPPING) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = quantity,
                                    onValueChange = { quantity = it },
                                    label = { Text("数量") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = unit,
                                    onValueChange = { unit = it },
                                    label = { Text("单位") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = body,
                                onValueChange = { body = it },
                                label = { Text(if (kind == Kind.TODO) "备注" else "正文") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (kind == null) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                } else {
                    TextButton(
                        enabled = title.isNotBlank(),
                        onClick = {
                            onSave(
                                kind!!,
                                title.trim(),
                                body.trim(),
                                quantity.toIntOrNull() ?: 1,
                                unit.ifBlank { "个" },
                                priority
                            )
                        }
                    ) {
                        Text("添加", color = Blue600, fontWeight = FontWeight.Medium)
                    }
                }
            },
            dismissButton = if (kind == null) {
                null
            } else {
                { TextButton(onClick = { kind = null }) { Text("返回") } }
            }
        )
    }

    @Composable
    private fun KindOption(label: String, hint: String, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column {
                Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
    }

    /** 写库 + 触发同步 + 刷新组件；完成后关闭对话框。 */
    private fun save(
        kind: Kind,
        title: String,
        body: String,
        quantity: Int,
        unit: String,
        priority: Int
    ) {
        val appContext = applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val now = System.currentTimeMillis()
            val ok = runCatching {
                val db = AppDatabase.getDatabase(appContext)
                when (kind) {
                    Kind.MEMO -> db.memoDao().insert(
                        MemoEntity(
                            title = title, content = body,
                            updatedAt = now, syncStatus = SyncStatus.UNSYNCED.name
                        )
                    )
                    Kind.SHOPPING -> db.shoppingItemDao().insert(
                        ShoppingItemEntity(
                            name = title, quantity = quantity, unit = unit, priority = priority,
                            updatedAt = now, syncStatus = SyncStatus.UNSYNCED.name
                        )
                    )
                    Kind.TODO -> db.todoDao().insert(
                        TodoEntity(
                            title = title, note = body, priority = priority,
                            updatedAt = now, syncStatus = SyncStatus.UNSYNCED.name
                        )
                    )
                }
            }.isSuccess

            withContext(Dispatchers.Main) {
                if (!ok) {
                    Toast.makeText(appContext, "写入本地数据失败，请重试", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                if (AuthManager.isLoggedIn) {
                    // 立即上行同步：App 内（WebSocket 广播）与网页后台都会很快看到
                    SyncManager.requestSync(appContext)
                } else {
                    Toast.makeText(
                        appContext,
                        "未登录：内容只存在本机，登录后才会同步到云端",
                        Toast.LENGTH_LONG
                    ).show()
                }
                WidgetUpdater.refresh(appContext)
                finish()
            }
        }
    }
}
