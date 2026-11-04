package com.example.beiwang.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.beiwang.data.AuthManager
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.sync.SyncManager
import com.example.beiwang.data.entity.SyncStatus
import com.example.beiwang.ui.theme.BeiwangTheme
import com.example.beiwang.widget.WidgetNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditMemoActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MEMO_ID = "EDIT_MEMO_ID"
        // 桌面组件（RemoteViews）启动本 Activity 时，memoId 经此 extra key 传递。
        const val KEY_MEMO_ID_GLANCE = "memo_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 窗口高度交给内容自适应（WRAP_CONTENT），上限由 Compose 侧的「最多半屏」约束控制：
        // 内容短 → 弹窗只有内容那么高；内容长 → 撑到半屏后正文区内部滚动。
        runCatching {
            val dm = resources.displayMetrics
            window?.setLayout(
                (dm.widthPixels * 0.92f).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 兼容两种启动方式：
        //  - 原生 App 用标准 Intent extra (EXTRA_MEMO_ID) 启动；
        //  - 桌面组件（RemoteViews）启动，memoId 以 "memo_id" 作为 extra key 放进 Intent。
        val memoId = intent?.getStringExtra(KEY_MEMO_ID_GLANCE)
            ?: intent?.getStringExtra(EXTRA_MEMO_ID)
        if (memoId == null) {
            finish()
            return
        }

        setContent {
            BeiwangTheme {
                var original by remember { mutableStateOf<MemoEntity?>(null) }
                var title by remember { mutableStateOf("") }
                var content by remember { mutableStateOf("") }
                var loaded by remember { mutableStateOf(false) }
                // 默认「查看」态：弹窗内直接展示完整标题与正文；点「编辑」才切到可编辑界面。
                var editing by remember { mutableStateOf(false) }

                // Load memo from Room once.
                LaunchedEffect(memoId) {
                    val memo = withContext(Dispatchers.IO) {
                        AppDatabase.getDatabase(this@EditMemoActivity)
                            .memoDao().getMemoById(memoId)
                    }
                    if (memo == null) {
                        // Memo was deleted / not found — nothing to edit.
                        finish()
                        return@LaunchedEffect
                    }
                    original = memo
                    title = memo.title
                    content = memo.content
                    loaded = true
                }

                if (loaded && original != null) {
                    if (editing) {
                        EditMemoDialog(
                            title = title,
                            content = content,
                            onTitleChange = { title = it },
                            onContentChange = { content = it },
                            onSave = { saveAndFinish(original!!, title, content) },
                            onCancel = { finish() }
                        )
                    } else {
                        ViewMemoDialog(
                            title = title,
                            content = content,
                            onEdit = { editing = true },
                            onClose = { finish() }
                        )
                    }
                }
            }
        }
    }

    private fun saveAndFinish(memo: MemoEntity, title: String, content: String) {
        // 未做任何修改时直接关闭：避免无谓改写 updatedAt 并触发多余的同步
        if (title == memo.title && content == memo.content) {
            finish()
            return
        }
        // 未登录时：编辑仅落本机，不会同步，且下次登录清库会被清空，提醒用户。
        if (!AuthManager.isLoggedIn) {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    val updated = memo.copy(
                        title = title,
                        content = content,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = SyncStatus.UNSYNCED.name
                    )
                    AppDatabase.getDatabase(this@EditMemoActivity).memoDao().update(updated)
                }
                // 仍保存本地改动（避免用户已输入的内容直接丢失），但提醒不会同步。
                WidgetNotifier.notify(
                    this@EditMemoActivity,
                    "备忘未同步",
                    "您当前未登录，这次编辑仅保存在本机、不会同步到云端，且重新登录时会被清空。请先打开App登录。"
                )
                withContext(Dispatchers.Main) { finish() }
            }
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            var saved = true
            try {
                val updated = memo.copy(
                    title = title,
                    content = content,
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.UNSYNCED.name
                )
                AppDatabase.getDatabase(this@EditMemoActivity).memoDao().update(updated)
            } catch (e: Exception) {
                saved = false
                e.printStackTrace()
            }
            // Refresh every widget instance so the change shows immediately.
            WidgetUpdater.refresh(this@EditMemoActivity)
            if (saved) {
                // 关键修复：与软件内 MemoRepository.updateMemo 保持一致，编辑后主动触发后台同步。
                // 否则 App 退出后台/被杀时，组件里改的备忘不会推送到网页后台（仅落本地）。
                SyncManager.requestSync(this@EditMemoActivity)
            } else {
                // 写入异常：明确告知用户具体原因，避免内容无声丢失。
                WidgetNotifier.notify(
                    this@EditMemoActivity,
                    "保存备忘失败",
                    "本地数据库写入异常，内容未能保存。请检查设备存储空间或重启App后重试。"
                )
            }
            // Only close the dialog AFTER the DB write + widget refresh finished.
            // Calling finish() right after launch() would let onDestroy cancel this
            // coroutine, so on slower devices the widget never refreshed.
            withContext(Dispatchers.Main) {
                finish()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewMemoDialog(
    title: String,
    content: String,
    onEdit: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    // 最多半屏：内容短时弹窗按内容高度自适应，长了最多占半屏
    val halfScreen = (LocalConfiguration.current.screenHeightDp / 2).dp

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = halfScreen)
                .padding(16.dp)
        ) {
            Text(
                text = title.ifBlank { "（无标题）" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(10.dp))

            // weight(fill = false)：高度按正文内容走，但最多吃掉「标题/按钮之外」的剩余空间。
            //  - 内容短 → 只有内容那么高，弹窗跟着变矮（自适应）
            //  - 内容长 → 被限制在剩余空间内，用 verticalScroll 上下滑动看全
            // 若用 fill = true，短内容也会被撑满剩余空间，弹窗就白白变高了。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = content.ifBlank { "（无正文）" },
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClose) { Text("关闭") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onEdit) { Text("编辑") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMemoDialog(
    title: String,
    content: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    // Save when the user presses system back, matching the app's edit screen behavior.
    BackHandler { onSave() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "编辑备忘录",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                placeholder = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                placeholder = { Text("输入正文...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 360.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSave) {
                    Text("保存")
                }
            }
        }
    }
}
