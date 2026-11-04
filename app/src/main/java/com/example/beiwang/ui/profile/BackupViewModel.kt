package com.example.beiwang.ui.profile

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import com.example.beiwang.data.backup.BackupData
import com.example.beiwang.data.backup.BackupJson
import com.example.beiwang.data.backup.BackupParsers
import com.example.beiwang.data.backup.BackupRepository
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.entity.SyncStatus
import com.example.beiwang.data.entity.splitTagIds
import com.example.beiwang.data.sync.PurgedIds
import com.example.beiwang.data.export.XlsxWriter
import com.example.beiwang.data.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BackupRepository

    init {
        val db = AppDatabase.getDatabase(application)
        repository = BackupRepository(
            db.memoDao(),
            db.shoppingItemDao(),
            db.tagDao(),
            db.memoAttachmentDao(),
            application
        )
    }

    /** 导出到用户通过 SAF 选择的文件（可保存到任意位置/云盘）。 */
    suspend fun exportToUri(uri: Uri) {
        val json = buildJson()
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("无法写入目标文件")
        }
    }

    /** 导出为 Excel (.xlsx)。自写 ZIP+XML，不依赖第三方库。 */
    suspend fun exportXlsxToUri(uri: Uri) {
        val data = repository.exportAll()
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { os ->
                XlsxWriter.write(os, buildXlsxSheets(data))
            } ?: throw IOException("无法写入目标文件")
        }
    }

    /** 导出为 Markdown (.md)。 */
    suspend fun exportMdToUri(uri: Uri) {
        val data = repository.exportAll()
        val md = buildMarkdown(data)
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { os ->
                os.write(md.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("无法写入目标文件")
        }
    }

    /** 导出为纯文本 (.txt)。 */
    suspend fun exportTxtToUri(uri: Uri) {
        val data = repository.exportAll()
        val txt = buildPlainText(data)
        withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { os ->
                os.write(txt.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("无法写入目标文件")
        }
    }

    /** 从用户通过 SAF 选择的备份文件恢复。 */
    suspend fun importFromUri(uri: Uri) {
        val data = withContext(Dispatchers.IO) {
            val cr = getApplication<Application>().contentResolver
            // 申请持久读取权限，规避部分机型（如 OPPO/ColorOS）对 OpenDocument 返回的
            // content:// URI 读取受限、读到的流为空的问题。失败可忽略（一次性授权通常已足够）。
            runCatching { cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            // 优先用 openFileDescriptor + FileInputStream 读取，比 openInputStream 在各类文档
            // provider（media.documents / externalstorage.documents）上更可靠；失败再回退。
            val bytes = runCatching {
                cr.openFileDescriptor(uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
                }
            }.getOrNull() ?: cr.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("无法读取备份文件")
            if (bytes.isEmpty()) {
                throw IOException("备份文件内容为空，请确认选择了正确的备份文件（而不是文件夹或其它格式）")
            }
            // 按内容识别格式并解析（JSON / Markdown / 文本 / Excel）
            BackupParsers.parse(bytes)
        }
        restoreData(data)
    }

    /** 一键本地备份：写入应用私有外部存储（无需权限），返回文件路径用于提示。 */
    suspend fun backupToLocal(): File {
        val json = buildJson()
        return withContext(Dispatchers.IO) {
            val dir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: getApplication<Application>().filesDir
            dir.mkdirs()
            val fileName = "beiwang_backup_${timeStamp()}.json"
            val file = File(dir, fileName)
            file.writeText(json, Charsets.UTF_8)
            file
        }
    }

    /** 列出应用私有目录下已有的本地备份文件，供“恢复本地备份”选择。 */
    fun listLocalBackups(): List<File> {
        val dir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: getApplication<Application>().filesDir
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /** 从应用私有目录中的本地备份文件恢复（不经过 SAF，应用可直接读取自身目录）。 */
    suspend fun importFromFile(file: File) {
        val json = withContext(Dispatchers.IO) {
            if (!file.exists()) throw IOException("备份文件不存在")
            file.readText(Charsets.UTF_8)
        }
        val data = BackupJson.fromJson(json)
        restoreData(data)
    }

    /**
     * 写入恢复数据并立即同步到服务端。
     *
     * 关键修复：备份文件里保存的是「备份时刻」的 updatedAt（通常比服务端当前数据更旧）。
     * 若原样导入，下一次双向同步（按 updated_at 做 LWW 合并）会用服务端「更新」的版本
     * 覆盖掉刚恢复的内容，表现为「恢复后几秒内容又消失，且网页后台也看不到」。
     * 这里把每条记录的 updatedAt 刷新为当前时间、syncStatus 置为 UNSYNCED，使恢复的数据
     * 在 LWW 中胜出并回写服务端，从而稳定保留在 App 与网页后台。
     * deletedAt 保持备份原值（真实还原备份时刻的删除/未删除状态）。
     */
    private suspend fun restoreData(data: BackupData) {
        val now = System.currentTimeMillis()
        val prepared = data.copy(
            memos = data.memos.map { it.copy(updatedAt = now, syncStatus = SyncStatus.UNSYNCED.name) },
            shoppingItems = data.shoppingItems.map { it.copy(updatedAt = now, syncStatus = SyncStatus.UNSYNCED.name) },
            tags = data.tags.map { it.copy(updatedAt = now, syncStatus = SyncStatus.UNSYNCED.name) }
        )
        repository.importAll(prepared)
        // 被主动恢复的 id 必须从 purged_ids 移除：否则本次推送会同时带该 id（UNSYNCED）
        // 与 purgedMemoIds=[该 id]，服务端先 upsert 又真删，网页后台永远收不到。
        val ctx = getApplication<Application>().applicationContext
        PurgedIds.remove(ctx, prepared.memos.map { it.id }, "memo")
        PurgedIds.remove(ctx, prepared.shoppingItems.map { it.id }, "shop")
        PurgedIds.remove(ctx, prepared.tags.map { it.id }, "tag")
        SyncManager.requestSync(ctx)
    }

    private suspend fun buildJson(): String {
        val data: BackupData = repository.exportAll()
        return BackupJson.toJson(data)
    }

    private fun timeStamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    // ——— 导出 xlsx / md / txt 的内容构造 ———

    /** 把标签 id 列表解析成可读的名称串（用逗号连接）。 */
    private fun tagNames(tagIds: String, map: Map<String, String>): String {
        return tagIds.splitTagIds().mapNotNull { map[it] }.joinToString("、")
    }

    private fun formatDateTime(ms: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ms))
    }

    private fun formatDate(ms: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ms))
    }

    private fun colorHex(color: Int): String {
        return String.format("#%08X", color)
    }

    /** 构造三张工作表：备忘录 / 购物清单 / 标签。 */
    private fun buildXlsxSheets(data: BackupData): List<Pair<String, List<List<XlsxWriter.Cell>>>> {
        val tagMap = data.tags.associate { it.id to it.name }

        val memoHeader = listOf(
            XlsxWriter.Cell("标识"), XlsxWriter.Cell("标题"), XlsxWriter.Cell("正文"), XlsxWriter.Cell("标签"),
            XlsxWriter.Cell("是否完成"), XlsxWriter.Cell("是否收藏"),
            XlsxWriter.Cell("创建时间"), XlsxWriter.Cell("修改时间"), XlsxWriter.Cell("删除时间")
        )
        val memoRows = listOf(memoHeader) + data.memos.map { m ->
            listOf(
                XlsxWriter.Cell(m.id),
                XlsxWriter.Cell(m.title),
                XlsxWriter.Cell(m.content),
                XlsxWriter.Cell(tagNames(m.tagIds, tagMap)),
                XlsxWriter.Cell(if (m.isCompleted) 1L else 0L),
                XlsxWriter.Cell(if (m.isFavorite) 1L else 0L),
                XlsxWriter.Cell(formatDateTime(m.createdAt)),
                XlsxWriter.Cell(formatDateTime(m.updatedAt)),
                XlsxWriter.Cell(if (m.deletedAt != null) formatDateTime(m.deletedAt) else "")
            )
        }

        val shopHeader = listOf(
            XlsxWriter.Cell("标识"), XlsxWriter.Cell("名称"), XlsxWriter.Cell("数量"), XlsxWriter.Cell("单位"),
            XlsxWriter.Cell("标签"), XlsxWriter.Cell("是否已购买"),
            XlsxWriter.Cell("创建时间"), XlsxWriter.Cell("修改时间"), XlsxWriter.Cell("删除时间")
        )
        val shopRows = listOf(shopHeader) + data.shoppingItems.map { s ->
            listOf(
                XlsxWriter.Cell(s.id),
                XlsxWriter.Cell(s.name),
                XlsxWriter.Cell(s.quantity),
                XlsxWriter.Cell(s.unit),
                XlsxWriter.Cell(tagNames(s.tagIds, tagMap)),
                XlsxWriter.Cell(if (s.isCompleted) 1L else 0L),
                XlsxWriter.Cell(formatDateTime(s.createdAt)),
                XlsxWriter.Cell(formatDateTime(s.updatedAt)),
                XlsxWriter.Cell(if (s.deletedAt != null) formatDateTime(s.deletedAt) else "")
            )
        }

        val tagHeader = listOf(
            XlsxWriter.Cell("标识"), XlsxWriter.Cell("名称"), XlsxWriter.Cell("颜色"),
            XlsxWriter.Cell("排序"), XlsxWriter.Cell("创建时间"), XlsxWriter.Cell("删除时间")
        )
        val tagRows = listOf(tagHeader) + data.tags.map { t ->
            listOf(
                XlsxWriter.Cell(t.id),
                XlsxWriter.Cell(t.name),
                XlsxWriter.Cell(colorHex(t.color)),
                XlsxWriter.Cell(t.sortOrder.toLong()),
                XlsxWriter.Cell(formatDateTime(t.createdAt)),
                XlsxWriter.Cell(if (t.deletedAt != null) formatDateTime(t.deletedAt) else "")
            )
        }

        return listOf(
            "备忘录" to memoRows,
            "购物清单" to shopRows,
            "标签" to tagRows
        )
    }

    private fun buildMarkdown(data: BackupData): String {
        val tagMap = data.tags.associate { it.id to it.name }
        val sb = StringBuilder()
        sb.appendLine("# 七安备忘 导出")
        sb.appendLine()
        sb.appendLine("> 导出时间：${formatDateTime(System.currentTimeMillis())}")
        sb.appendLine()

        sb.appendLine("## 备忘录")
        sb.appendLine()
        if (data.memos.isEmpty()) {
            sb.appendLine("_（暂无备忘录）_")
        } else {
            data.memos.forEach { m ->
                if (m.title.isNotBlank()) sb.appendLine("### ${m.title} <!--id:${m.id}-->")
                val tags = tagNames(m.tagIds, tagMap)
                val meta = buildList {
                    if (tags.isNotBlank()) add("标签：$tags")
                    add("创建：${formatDateTime(m.createdAt)}")
                    add("修改：${formatDateTime(m.updatedAt)}")
                    if (m.deletedAt != null) add("状态：已删除")
                    else if (m.isCompleted) add("状态：已完成")
                    if (m.isFavorite) add("★ 收藏")
                }
                if (meta.isNotEmpty()) sb.appendLine("*${meta.joinToString("　")}*")
                if (m.content.isNotBlank()) {
                    sb.appendLine()
                    sb.appendLine(m.content)
                }
                sb.appendLine()
                sb.appendLine("---")
                sb.appendLine()
            }
        }

        sb.appendLine("## 购物清单")
        sb.appendLine()
        if (data.shoppingItems.isEmpty()) {
            sb.appendLine("_（暂无购物项）_")
        } else {
            data.shoppingItems.forEach { s ->
                val box = if (s.isCompleted) "[x]" else "[ ]"
                val base = "- $box ${s.name}　×${s.quantity}${s.unit}"
                val tags = tagNames(s.tagIds, tagMap)
                val delMark = if (s.deletedAt != null) "　[已删除]" else ""
                val withTags = if (tags.isNotBlank()) "$base　`$tags`" else base
                sb.appendLine("$withTags$delMark <!--id:${s.id}-->")
            }
        }
        sb.appendLine()

        sb.appendLine("## 标签")
        sb.appendLine()
        if (data.tags.isEmpty()) {
            sb.appendLine("_（暂无标签）_")
        } else {
            data.tags.forEach { t ->
                val delMark = if (t.deletedAt != null) "　[已删除]" else ""
                sb.appendLine("- ${t.name}（${colorHex(t.color)}）$delMark <!--id:${t.id}-->")
            }
        }
        sb.appendLine()
        return sb.toString()
    }

    private fun buildPlainText(data: BackupData): String {
        val tagMap = data.tags.associate { it.id to it.name }
        val sb = StringBuilder()
        sb.appendLine("七安备忘 导出")
        sb.appendLine("导出时间：${formatDateTime(System.currentTimeMillis())}")
        sb.appendLine()
        sb.appendLine("===== 备忘录 =====")
        if (data.memos.isEmpty()) {
            sb.appendLine("（暂无备忘录）")
        } else {
            data.memos.forEachIndexed { i, m ->
                sb.appendLine("【${i + 1}】${m.title}")
                val tags = tagNames(m.tagIds, tagMap)
                sb.appendLine("标签：${if (tags.isNotBlank()) tags else "（无）"}")
                sb.appendLine("创建：${formatDateTime(m.createdAt)}　修改：${formatDateTime(m.updatedAt)}")
                val memoStatus = when {
                    m.deletedAt != null -> "已删除（回收站）"
                    m.isCompleted -> "已完成"
                    else -> "未完成"
                }
                sb.appendLine("状态：$memoStatus${if (m.isFavorite && m.deletedAt == null) "　★收藏" else ""}")
                sb.appendLine("标识：${m.id}")
                if (m.content.isNotBlank()) {
                    sb.appendLine("正文：")
                    sb.appendLine(m.content)
                }
                sb.appendLine()
            }
        }

        sb.appendLine("===== 购物清单 =====")
        if (data.shoppingItems.isEmpty()) {
            sb.appendLine("（暂无购物项）")
        } else {
            data.shoppingItems.forEachIndexed { i, s ->
                val tags = tagNames(s.tagIds, tagMap)
                val delMark = if (s.deletedAt != null) "　[已删除]" else ""
                sb.appendLine("【${i + 1}】${if (s.isCompleted) "[已完成] " else ""}${s.name} ×${s.quantity}${s.unit}${if (tags.isNotBlank()) "（标签：$tags）" else ""}$delMark 标识：${s.id}")
            }
        }
        sb.appendLine()

        sb.appendLine("===== 标签 =====")
        if (data.tags.isEmpty()) {
            sb.appendLine("（暂无标签）")
        } else {
            data.tags.forEach { t ->
                val delMark = if (t.deletedAt != null) "　[已删除]" else ""
                sb.appendLine("- ${t.name}（${colorHex(t.color)}）$delMark 标识：${t.id}")
            }
        }
        sb.appendLine()
        return sb.toString()
    }
}
