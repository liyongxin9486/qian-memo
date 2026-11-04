@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.beiwang.ui.memo

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本机相册图片选择器：仅查询设备 MediaStore 中的本地图片（不含云端相册）。
 * 交互分两级，与系统相册一致：列表页顶部为「全部图片」总览，其下按相册(BUCKET)分组；
 * 点「全部图片」或某个相册进入对应图片网格选图。
 * 选中的 Uri 指向本机文件，copyImageUri 可直接读取，避免系统 PhotoPicker 把云端图
 * 混入导致 ENOENT 的问题。
 */
private const val ALL_BUCKET = "__all__"

private data class AlbumInfo(
    val bucketId: String,
    val name: String,
    val coverUri: Uri,
    val images: List<Uri>
)

@Composable
fun LocalImagePicker(onPicked: (Uri) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasMediaPermission(context)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    val neededPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var albums by remember { mutableStateOf<List<AlbumInfo>?>(null) }
            var allImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
            var selectedAlbum by remember { mutableStateOf<AlbumInfo?>(null) }

            LaunchedEffect(hasPermission) {
                if (hasPermission) {
                    val result = withContext(Dispatchers.IO) { queryLocalAlbums(context) }
                    albums = result.albums
                    allImages = result.allImages
                }
            }

            Column {
                TopAppBar(
                    title = { Text(selectedAlbum?.name ?: "本机相册") },
                    navigationIcon = {
                        if (selectedAlbum == null) {
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭") }
                        } else {
                            IconButton(onClick = { selectedAlbum = null }) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                        }
                    }
                )
                if (!hasPermission) {
                    Column(Modifier.padding(24.dp)) {
                        Text("需要读取本机相册权限才能选择照片。")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { permLauncher.launch(neededPermission) }) { Text("授予权限") }
                    }
                } else if (selectedAlbum == null) {
                    AlbumList(
                        albums = albums,
                        allImages = allImages,
                        onAlbumClick = { selectedAlbum = it }
                    )
                } else {
                    AlbumGrid(
                        album = selectedAlbum!!,
                        onPicked = onPicked,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumList(albums: List<AlbumInfo>?, allImages: List<Uri>, onAlbumClick: (AlbumInfo) -> Unit) {
    val context = LocalContext.current
    when {
        albums == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        albums.isEmpty() && allImages.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("本机相册没有图片") }
        else -> {
            val display = if (allImages.isNotEmpty())
                listOf(AlbumInfo(ALL_BUCKET, "全部图片", allImages.first(), allImages)) + albums
            else albums
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(display) { album ->
                    var cover by remember(album.coverUri) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(album.coverUri) {
                        cover = withContext(Dispatchers.IO) {
                            runCatching { loadThumb(context, album.coverUri) }.getOrNull()
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { onAlbumClick(album) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(56.dp)) {
                            cover?.let {
                                Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(album.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${album.images.size} 张",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalContentColor.current.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(album: AlbumInfo, onPicked: (Uri) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(album.images.chunked(3)) { rowUris ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowUris.forEach { uri ->
                    var thumb by remember(uri) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(uri) {
                        thumb = withContext(Dispatchers.IO) {
                            runCatching { loadThumb(context, uri) }.getOrNull()
                        }
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clickable {
                            onPicked(uri)
                            onDismiss()
                        }
                    ) {
                        thumb?.let { b ->
                            Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                repeat(3 - rowUris.size) {
                    Spacer(Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

private fun hasMediaPermission(context: Context): Boolean {
    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
}

/**
 * 一次性查询本机全部图片，按相册(BUCKET)分组，每组取最新一张做封面。
 * 返回按图片数量倒序的相册列表 + 保持时间倒序的全量图片列表。
 */
private data class AlbumQueryResult(val albums: List<AlbumInfo>, val allImages: List<Uri>)

private fun queryLocalAlbums(context: Context): AlbumQueryResult {
    val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )
    val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    val entries = mutableListOf<Pair<String, Uri>>()       // bucketId -> imageUri
    val bucketName = mutableMapOf<String, String>()
    context.contentResolver.query(base, proj, null, null, sort)?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
        val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        while (c.moveToNext()) {
            val id = c.getLong(idCol)
            val bucketId = c.getLong(bucketCol).toString()
            val name = c.getString(nameCol)?.takeIf { it.isNotBlank() } ?: "未知相册"
            bucketName[bucketId] = name
            entries.add(bucketId to ContentUris.withAppendedId(base, id))
        }
    }
    val allImages = entries.map { it.second }
    val albums = entries.groupBy({ it.first }, { it.second })
        .map { (bucketId, uris) ->
            AlbumInfo(
                bucketId = bucketId,
                name = bucketName[bucketId] ?: "未知相册",
                coverUri = uris.first(),
                images = uris
            )
        }
        .sortedByDescending { it.images.size }
    return AlbumQueryResult(albums, allImages)
}

private fun loadThumb(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Thumbnails.getThumbnail(
                context.contentResolver, ContentUris.parseId(uri), MediaStore.Images.Thumbnails.MINI_KIND, null
            )
        }
    }.getOrNull()
}
