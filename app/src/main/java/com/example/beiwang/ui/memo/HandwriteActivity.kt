package com.example.beiwang.ui.memo

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beiwang.ui.theme.BeiwangTheme
import com.example.beiwang.ui.theme.Blue600
import com.example.beiwang.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class HandwriteActivity : ComponentActivity() {
    companion object {
        const val EXTRA_URI = "handwrite_uri"              // 已存在手写图片路径（用于续画）
        const val EXTRA_ATTACHMENT_ID = "handwrite_att_id" // 已存在附件 id（续画时回写）
        const val RESULT_PATH = "result_path"
        const val RESULT_ATTACHMENT_ID = "result_att_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val existingUri = intent.getStringExtra(EXTRA_URI)
        val existingId = intent.getStringExtra(EXTRA_ATTACHMENT_ID)

        setContent {
            val themeMode by ThemeManager.collectAsState()
            BeiwangTheme(themeMode = themeMode) {
                HandwriteScreen(
                    existingUri = existingUri,
                    onDone = { path ->
                        val result = Intent().apply {
                            putExtra(RESULT_PATH, path)
                            putExtra(RESULT_ATTACHMENT_ID, existingId)
                        }
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

private enum class Tool { PEN, HIGHLIGHTER, ERASER }

private data class DrawStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val alpha: Float
)

// 色相调色板（H 从 0 到 360，S/V 取满）

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandwriteScreen(
    existingUri: String?,
    onDone: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var tool by remember { mutableStateOf(Tool.PEN) }
    // 颜色用 HSV 表示：色相 hue + 亮度 value（饱和度固定满）
    var hue by remember { mutableStateOf(0f) }
    var value by remember { mutableStateOf(1f) }
    val currentColor = Color.hsv(hue, 1f, value)
    val strokes = remember { mutableStateListOf<DrawStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(existingUri) {
        baseBitmap = withContext(Dispatchers.IO) {
            existingUri?.let { AttachmentStorage.loadSampledBitmap(it) }
        }
    }

    fun currentStroke(): DrawStroke? {
        if (currentPoints.size < 2) return null
        // 直接读委托属性 hue/value/tool，确保 pointerInput 闭包（只创建一次）也能拿到最新颜色，
        // 否则会捕获组合时的 currentColor 快照，导致抬手后笔画恢复初始色。
        val (color, width, alpha) = when (tool) {
            Tool.PEN -> Triple(Color.hsv(hue, 1f, value), 4f, 1f)
            Tool.HIGHLIGHTER -> Triple(Color.hsv(hue, 1f, value), 20f, 0.35f)
            Tool.ERASER -> Triple(Color.White, 36f, 1f)
        }
        return DrawStroke(currentPoints, color, width, alpha)
    }

    fun saveDrawing() {
        val size = canvasSize
        if (size.width == 0 || size.height == 0 || saving) return
        saving = true
        // 位图创建/绘制/压缩较重，放后台线程执行，避免主线程卡顿；期间禁止重复保存
        scope.launch {
            val path = withContext(Dispatchers.Default) {
                val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(bmp)
                c.drawColor(AndroidColor.WHITE)
                baseBitmap?.let {
                    c.drawBitmap(it, null, RectF(0f, 0f, size.width.toFloat(), size.height.toFloat()), null)
                }
                val paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                (strokes + listOfNotNull(currentStroke())).forEach { s ->
                    paint.color = s.color.toArgb()
                    paint.alpha = (s.alpha * 255).roundToInt().coerceIn(0, 255)
                    paint.strokeWidth = s.width
                    val p = AndroidPath()
                    if (s.points.isNotEmpty()) {
                        p.moveTo(s.points[0].x, s.points[0].y)
                        for (i in 1 until s.points.size) p.lineTo(s.points[i].x, s.points[i].y)
                    }
                    c.drawPath(p, paint)
                }
                val file = AttachmentStorage.newFile(context, "png")
                runCatching { bmp.compress(Bitmap.CompressFormat.PNG, 100, file.outputStream()) }
                file.absolutePath
            }
            onDone(path)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("手写") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "取消")
                    }
                },
                actions = {
                    IconButton(onClick = { strokes.removeAt(strokes.lastIndex) },
                        enabled = strokes.isNotEmpty()) {
                        Icon(Icons.Default.Undo, contentDescription = "撤销")
                    }
                    IconButton(onClick = { strokes.clear() },
                        enabled = strokes.isNotEmpty()) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空")
                    }
                    IconButton(onClick = { saveDrawing() }) {
                        Icon(Icons.Default.Check, contentDescription = "完成", tint = Blue600)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(8.dp)
        ) {
            // 工具条
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolButton(icon = Icons.Default.Create, label = "笔", selected = tool == Tool.PEN) { tool = Tool.PEN }
                ToolButton(icon = Icons.Default.Brush, label = "荧光笔", selected = tool == Tool.HIGHLIGHTER) { tool = Tool.HIGHLIGHTER }
                ToolButton(icon = Icons.Default.Backspace, label = "橡皮", selected = tool == Tool.ERASER) { tool = Tool.ERASER }

                Spacer(modifier = Modifier.width(8.dp))

                if (tool != Tool.ERASER) {
                    // 颜色条（连续色相）
                    Text("颜色", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    HueBar(
                        hue = hue,
                        onHueChange = { hue = it },
                        modifier = Modifier.width(180.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // 亮度条
                    Icon(
                        Icons.Default.BrightnessMedium,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.width(120.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = currentColor,
                            activeTrackColor = currentColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(Color.White)
                    .clip(RoundedCornerShape(8.dp))
                    .onPlaced { canvasSize = it.size }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { currentPoints = listOf(it) },
                            onDrag = { change, _ -> currentPoints = currentPoints + change.position },
                            onDragEnd = {
                                currentStroke()?.let { strokes.add(it) }
                                currentPoints = emptyList()
                            }
                        )
                    }
            ) {
                drawRect(Color.White, size = size)
                baseBitmap?.let {
                    drawImage(
                        it.asImageBitmap(),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )
                }
                (strokes + listOfNotNull(currentStroke())).forEach { s ->
                    val path = Path().apply {
                        if (s.points.isNotEmpty()) {
                            moveTo(s.points[0].x, s.points[0].y)
                            for (i in 1 until s.points.size) lineTo(s.points[i].x, s.points[i].y)
                        }
                    }
                    drawPath(
                        path,
                        color = s.color,
                        alpha = s.alpha,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = s.width,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Blue600,
                contentColor = Color.White
            )
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HueBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val hueColors = remember {
        (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) }
    }
    // 用真实像素宽度计算色相，避免 BoxWithConstraints + density 换算在横屏下取不到正确约束
    var boxWidth by remember { mutableStateOf(1f) }
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .onSizeChanged { boxWidth = it.width.toFloat().coerceAtLeast(1f) }
    ) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(hueColors)))
        val frac = (hue / 360f).coerceIn(0f, 1f)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = with(LocalDensity.current) { (boxWidth * frac).toDp() } - 2.dp)
                .width(4.dp)
                .height(28.dp)
                .background(Color.White.copy(alpha = 0.9f))
        )
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onHueChange((it.x / boxWidth * 360f).coerceIn(0f, 360f)) },
                        onDrag = { change, _ ->
                            onHueChange((change.position.x / boxWidth * 360f).coerceIn(0f, 360f))
                        }
                    )
                }
        )
    }
}
