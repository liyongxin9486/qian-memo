package com.example.beiwang.ui.memo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.beiwang.data.entity.MemoAttachmentEntity
import com.example.beiwang.ui.memo.HandwriteActivity
import com.example.beiwang.ui.tag.TagSelector
import com.example.beiwang.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoDetailScreen(
    memoId: String,
    onBackClick: () -> Unit,
    viewModel: MemoDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val checklistItems by viewModel.checklistItems.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(memoId) {
        viewModel.loadMemo(memoId)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onBackClick()
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // 图片/手写预览
    var previewPath by remember { mutableStateOf<String?>(null) }
    var previewOnEdit by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 历史快照详情
    var historyDetail by remember { mutableStateOf<com.example.beiwang.data.entity.MemoHistoryEntity?>(null) }

    // 录音状态
    var isRecording by remember { mutableStateOf(false) }
    var recordingPath by remember { mutableStateOf<String?>(null) }
    var recordStartMs by remember { mutableStateOf(0L) }
    val recorder = remember { mutableStateOf<MediaRecorder?>(null) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }

    fun startRecording() {
        val file = AttachmentStorage.newFile(context, "m4a")
        runCatching {
            val r = MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder.value = r
            recordingPath = file.absolutePath
            recordStartMs = System.currentTimeMillis()
            isRecording = true
        }
    }

    fun stopRecording() {
        val r = recorder.value
        val path = recordingPath
        // stop 失败（权限被拒/录音太短等）视为无效录音，不落附件；文件一并清理
        val stopped = runCatching { r?.stop() }.isSuccess
        runCatching { r?.release() }
        recorder.value = null
        isRecording = false
        recordingPath = null
        val duration = System.currentTimeMillis() - recordStartMs
        if (path != null && stopped && duration >= 300) {
            viewModel.addAttachment("AUDIO", path, duration)
        } else if (path != null) {
            runCatching { java.io.File(path).delete() }
        }
    }

    fun playAudio(att: MemoAttachmentEntity) {
        // 切换：正在播放则停止
        if (playingId == att.id) {
            runCatching { player.value?.stop() }
            runCatching { player.value?.release() }
            player.value = null
            playingId = null
            return
        }
        runCatching { player.value?.stop() }
        runCatching { player.value?.release() }
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(att.uri)
            mp.setOnCompletionListener {
                runCatching { player.value?.release() }
                player.value = null
                playingId = null
            }
            mp.prepare()
            mp.start()
            player.value = mp
            playingId = att.id
        }
    }

    fun stopAudio() {
        runCatching { player.value?.stop() }
        runCatching { player.value?.release() }
        player.value = null
        playingId = null
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder.value?.release() }
            runCatching { player.value?.release() }
        }
    }

    var showLocalPicker by remember { mutableStateOf(false) }

    fun pickLocalImage(uri: Uri) {
        // 大图拷贝落 IO 线程，避免主线程卡顿/ANR
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                AttachmentStorage.copyImageUri(context, uri)
            }
            result.fold(
                onSuccess = {
                    viewModel.addAttachment("IMAGE", it)
                    Toast.makeText(context, "图片已添加", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    android.util.Log.e("MemoDetail", "本地图片复制失败：${it.message}")
                    val msg = if (it.message?.contains("ENOENT", ignoreCase = true) == true)
                        "本机找不到该图片" else "图片读取失败：${it.message}"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        // 拍照位图压缩落 IO 线程，避免主线程卡顿/ANR
        scope.launch {
            val path = withContext(Dispatchers.IO) { AttachmentStorage.saveBitmap(context, bitmap) }
            if (path != null) {
                viewModel.addAttachment("IMAGE", path)
                Toast.makeText(context, "图片已添加", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "拍照保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val recordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else Unit
    }

    val handwriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val path = result.data?.getStringExtra(HandwriteActivity.RESULT_PATH)
            val existingId = result.data?.getStringExtra(HandwriteActivity.RESULT_ATTACHMENT_ID)
            if (path != null) {
                if (existingId != null) viewModel.updateAttachmentUri(existingId, path)
                else viewModel.addAttachment("HANDWRITE", path)
            }
        }
    }

    fun launchHandwrite(existingId: String?, existingUri: String?) {
        val intent = Intent(context, HandwriteActivity::class.java).apply {
            putExtra(HandwriteActivity.EXTRA_URI, existingUri)
            putExtra(HandwriteActivity.EXTRA_ATTACHMENT_ID, existingId)
        }
        handwriteLauncher.launch(intent)
    }

    BackHandler {
        if (uiState.isNew && uiState.title.isBlank() && uiState.attachments.isEmpty()) {
            onBackClick()
        } else {
            viewModel.saveMemo()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isNew) "新建备忘录" else "编辑备忘录") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isNew && uiState.title.isBlank() && uiState.attachments.isEmpty()) onBackClick()
                        else viewModel.saveMemo()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.togglePin() }) {
                        Icon(
                            imageVector = if (uiState.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (uiState.isPinned) "取消置顶" else "置顶",
                            tint = if (uiState.isPinned) Blue600 else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (uiState.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "收藏",
                            tint = if (uiState.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { viewModel.addChecklistItem() }) {
                        Icon(
                            imageVector = Icons.Default.CheckBox,
                            contentDescription = "添加清单项",
                            tint = Blue600
                        )
                    }

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
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.updateTitle(it) },
                placeholder = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("标签", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TagSelector(
                allTags = uiState.allTags,
                selectedTagIds = uiState.selectedTagIds,
                onToggle = { viewModel.toggleTag(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (showLocalPicker) {
                LocalImagePicker(
                    onPicked = { pickLocalImage(it) },
                    onDismiss = { showLocalPicker = false }
                )
            }

            if (uiState.attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.attachments, key = { it.id }) { att ->
                        AttachmentCard(
                            attachment = att,
                            isPlaying = playingId == att.id,
                            onRemove = {
                                if (playingId == att.id) stopAudio()
                                viewModel.removeAttachment(att)
                            },
                            onTogglePlay = { playAudio(att) },
                            onOpen = if (att.type == "HANDWRITE") {
                                { launchHandwrite(att.id, att.uri) }
                            } else null,
                            onPreview = if (att.type == "IMAGE" || att.type == "HANDWRITE") {
                                {
                                    previewOnEdit = if (att.type == "HANDWRITE") {
                                        { launchHandwrite(att.id, att.uri) }
                                    } else null
                                    previewPath = att.uri
                                }
                            } else null
                        )
                    }
                }
            }

            // 正文：保持原样（单个多行文本框）
            OutlinedTextField(
                value = uiState.content,
                onValueChange = { viewModel.updateContent(it) },
                placeholder = { Text("记录此刻的想法…") },
                minLines = 8,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                )
            )

            // 附件工具条
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showLocalPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("图片")
                }
                OutlinedButton(
                    onClick = { cameraLauncher.launch(null) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("拍照")
                }
                OutlinedButton(
                    onClick = {
                        if (isRecording) stopRecording()
                        else recordPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (isRecording) ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Red
                    ) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isRecording) "停止录音" else "录音")
                }
                OutlinedButton(
                    onClick = { launchHandwrite(null, null) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Brush, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("手写")
                }
            }

            // 清单：在正文下方末尾渲染，点右上角「清单」按钮追加一行
            if (checklistItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    checklistItems.forEach { item ->
                        ChecklistRow(
                            item = item,
                            onToggle = { viewModel.toggleChecklistItem(item) },
                            onContentChange = { viewModel.updateChecklistItemContent(item.id, it) },
                            onDelete = { viewModel.deleteChecklistItem(item) }
                        )
                    }
                }
            }

            // 创建 / 修改时间
            if (!uiState.isNew && uiState.memo != null) {
                val memo = uiState.memo!!
                Text(
                    "创建：${formatDateTime(memo.createdAt)}　修改：${formatDateTime(memo.updatedAt)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { showHistoryDialog = true }) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("历史记录 (${uiState.history.size})", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    if (uiState.isNew && uiState.title.isBlank() && uiState.attachments.isEmpty()) onBackClick()
                    else viewModel.saveMemo()
                }) {
                    Text("保存", color = Blue600)
                }
            }
        }
    }

    if (showHistoryDialog) {
        HistoryDialog(
            history = uiState.history,
            onDismiss = { showHistoryDialog = false },
            onItemClick = { historyDetail = it }
        )
    }

    if (previewPath != null) {
        ImagePreviewDialog(
            path = previewPath!!,
            onDismiss = { previewPath = null },
            onContinueEdit = previewOnEdit
        )
    }

    if (historyDetail != null) {
        val h = historyDetail!!
        HistoryDetailDialog(
            history = h,
            onDismiss = { historyDetail = null },
            onOverwrite = {
                viewModel.restoreFromHistory(h)
                historyDetail = null
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这个备忘录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMemo()
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

@Composable
private fun ChecklistRow(
    item: com.example.beiwang.data.entity.MemoChecklistItemEntity,
    onToggle: () -> Unit,
    onContentChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(28.dp),
            colors = CheckboxDefaults.colors(checkedColor = Blue600)
        )
        OutlinedTextField(
            value = item.content,
            onValueChange = onContentChange,
            placeholder = { Text("清单内容") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            ),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent
            )
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除清单项",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AttachmentCard(
    attachment: MemoAttachmentEntity,
    isPlaying: Boolean,
    onRemove: () -> Unit,
    onTogglePlay: () -> Unit,
    onOpen: (() -> Unit)? = null,
    onPreview: (() -> Unit)? = null
) {
    val cardSize = when (attachment.type) {
        "HANDWRITE" -> Modifier.size(168.dp, 110.dp)
        else -> Modifier.size(120.dp)
    }
    Box(
        modifier = cardSize
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        when (attachment.type) {
            "IMAGE", "HANDWRITE" -> {
                AttachmentImage(
                    path = attachment.uri,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (onPreview != null) Modifier.clickable { onPreview.invoke() } else Modifier)
                )
            }
            "AUDIO" -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint = Blue600
                        )
                    }
                    Text(
                        AttachmentStorage.formatDuration(attachment.durationMs),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            else -> {
                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.align(Alignment.Center))
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "移除",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AttachmentImage(path: String, modifier: Modifier = Modifier) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) { AttachmentStorage.loadSampledBitmap(path) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Column(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Text(
                "图片加载失败",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    path: String,
    onDismiss: () -> Unit,
    onContinueEdit: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
            // 双手缩放 / 平移状态
            var scale by remember(path) { mutableStateOf(1f) }
            var offset by remember(path) { mutableStateOf(Offset.Zero) }
            var boxSize by remember(path) { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

            // 根据图片实际 Fit 尺寸计算可平移的最大范围（避免图片被拖出框外/露出黑边）
            fun maxPan(bmp: Bitmap?, s: Float): Offset {
                if (bmp == null || bmp.width == 0 || bmp.height == 0 ||
                    boxSize.width == 0 || boxSize.height == 0
                ) return Offset.Zero
                val fit = minOf(
                    boxSize.width.toFloat() / bmp.width,
                    boxSize.height.toFloat() / bmp.height
                )
                val contentW = bmp.width * fit * s
                val contentH = bmp.height * fit * s
                return Offset(
                    maxOf(0f, (contentW - boxSize.width) / 2f),
                    maxOf(0f, (contentH - boxSize.height) / 2f)
                )
            }

            val maxScale = 3f
            LaunchedEffect(path) {
                bitmap = withContext(Dispatchers.IO) { AttachmentStorage.loadSampledBitmap(path) }
            }
            if (bitmap != null) {
                // 父层裁剪框：clipToBounds 在子项缩放/平移之后裁剪，保证图片永远不出框
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .clipToBounds()
                        .onSizeChanged { boxSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        // 双击切换：1x 放大到 2x，已放大则复位
                                        scale = if (scale > 1f) 1f else 2f
                                        offset = Offset.Zero
                                        // 缩放后重新钳制，确保不越界
                                        val m = maxPan(bitmap, scale)
                                        offset = Offset(
                                            offset.x.coerceIn(-m.x, m.x),
                                            offset.y.coerceIn(-m.y, m.y)
                                        )
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures(
                                    panZoomLock = true
                                ) { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, maxScale)
                                    val m = maxPan(bitmap, scale)
                                    offset = Offset(
                                        (offset.x + pan.x).coerceIn(-m.x, m.x),
                                        (offset.y + pan.y).coerceIn(-m.y, m.y)
                                    )
                                }
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }
            if (onContinueEdit != null) {
                Button(
                    onClick = { onDismiss(); onContinueEdit() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                ) {
                    Icon(Icons.Default.Brush, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("继续编辑")
                }
            }
        }
    }
}

@Composable
private fun HistoryDialog(
    history: List<com.example.beiwang.data.entity.MemoHistoryEntity>,
    onDismiss: () -> Unit,
    onItemClick: (com.example.beiwang.data.entity.MemoHistoryEntity) -> Unit
) {
    // 按时间倒序（最新在前）
    val items = remember(history) { history.sortedByDescending { it.snapshotAt } }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = { Text("历史记录") },
        text = {
            if (items.isEmpty()) {
                Text("暂无历史记录，保存后会自动记录。")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { h ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onItemClick(h) }
                                .padding(10.dp)
                        ) {
                            Text(
                                formatDateTime(h.snapshotAt),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(h.title.ifBlank { "(无标题)" }, style = MaterialTheme.typography.titleSmall)
                            if (h.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    h.content,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun HistoryDetailDialog(
    history: com.example.beiwang.data.entity.MemoHistoryEntity,
    onDismiss: () -> Unit,
    onOverwrite: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onOverwrite) {
                Text("覆盖", color = Blue600)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("不覆盖") }
        },
        title = { Text(formatDateTime(history.snapshotAt)) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        history.title.ifBlank { "(无标题)" },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                item {
                    Text(
                        history.content.ifBlank { "（无正文）" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text(
                        "提示：点「覆盖」将把该时间点的内容写回当前备忘录，并生成一条新备份。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

private fun formatDateTime(ms: Long): String {
    if (ms <= 0) return "-"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(ms))
}
