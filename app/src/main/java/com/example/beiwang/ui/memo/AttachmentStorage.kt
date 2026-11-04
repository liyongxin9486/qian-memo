package com.example.beiwang.ui.memo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.UUID

/**
 * 附件落盘到应用私有目录，避免依赖 SAF 返回的 content:// Uri 在重新打开时失效。
 * 目录：<filesDir>/memo_attachments/
 */
object AttachmentStorage {
    private const val DIR = "memo_attachments"

    fun dir(context: Context): File {
        val d = File(context.filesDir, DIR)
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** 为附件生成一个新的目标文件（绝对路径）。ext 不含点，如 "jpg" / "m4a"。 */
    fun newFile(context: Context, ext: String): File {
        return File(dir(context), "${UUID.randomUUID()}.$ext")
    }

    /** 将 SAF 选中的图片 Uri 拷贝到应用私有目录。成功返回目标文件路径(Result.success)，失败返回异常(Result.failure)。 */
    fun copyImageUri(context: Context, uri: Uri): Result<String> {
        return runCatching {
            val target = newFile(context, "jpg")
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("openInputStream 返回 null，Uri 无法读取: $uri")
            input.use { inp ->
                target.outputStream().use { out -> inp.copyTo(out) }
            }
            if (target.length() == 0L) throw IllegalStateException("复制后文件为空: ${target.absolutePath}")
            target.absolutePath
        }.onFailure { android.util.Log.e("AttachmentStorage", "copyImageUri failed for $uri", it) }
    }

    /** 将拍照/绘制的 Bitmap 压缩保存到应用私有目录，返回文件路径；失败返回 null。 */
    fun saveBitmap(context: Context, bitmap: Bitmap, ext: String = "jpg"): String? {
        return runCatching {
            val target = newFile(context, ext)
            target.outputStream().use { out ->
                bitmap.compress(
                    if (ext == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    90, out
                )
            }
            if (target.length() == 0L) throw IllegalStateException("保存后文件为空: ${target.absolutePath}")
            target.absolutePath
        }.onFailure { android.util.Log.e("AttachmentStorage", "saveBitmap failed", it) }
            .getOrNull()
    }

    /** 毫秒格式化为 m:ss。 */
    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "$min:${sec.toString().padStart(2, '0')}"
    }

    /** 从文件解码并采样到不超过 maxSize 像素，避免大图 OOM。返回 null 表示解码失败。 */
    fun loadSampledBitmap(path: String, maxSize: Int = 1024): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val src = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    var sample = 1
                    val longer = maxOf(w, h)
                    while (longer / sample > maxSize) sample *= 2
                    decoder.setTargetSampleSize(sample)
                }
            }.onFailure { android.util.Log.e("AttachmentStorage", "ImageDecoder decode failed: $path", it) }
                .getOrNull()
        } else {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val (w, h) = bounds.outWidth to bounds.outHeight
                var sample = 1
                if (w > 0 && h > 0) {
                    val longer = maxOf(w, h)
                    while (longer / sample > maxSize) sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(path, opts)
            }.getOrNull()
        }
    }
}
