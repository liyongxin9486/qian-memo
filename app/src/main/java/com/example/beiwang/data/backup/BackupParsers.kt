package com.example.beiwang.data.backup

import android.graphics.Color
import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.entity.ShoppingItemEntity
import com.example.beiwang.data.entity.SyncStatus
import com.example.beiwang.data.entity.TagEntity
import java.io.ByteArrayInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * 备份文件解析。支持四种格式，按内容（而非扩展名）自动识别：
 * - JSON（完整备份，含 id/删除状态，可完整还原）
 * - Excel(.xlsx) / Markdown(.md) / 纯文本(.txt)
 *
 * 关键约束（避免「导出→再导入」越导越多）：
 * 自家导出会在文件里带上每条记录的主键 id（xlsx 首列「标识」、md 的 <!--id:...--> 注释、
 * txt 的「标识：」行）。导入时**优先保留原 id**，按主键 REPLACE 实现幂等——同一份备份
 * 重复导入只会原地覆盖，条数恒定、不会重复。
 * 对于不含 id 的第三方文件（如用户自己做的表格），按原逻辑生成新 id。
 * 标签在可读格式里仍按「名称」存储，导入时用本次标签表的 id（已保留）做名称→id 映射。
 */
object BackupParsers {

    fun parse(bytes: ByteArray): BackupData {
        // .xlsx 本质是 ZIP 包，以 "PK" 开头
        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            return fromXlsx(bytes)
        }
        val text = bytes.toString(Charsets.UTF_8)
        val trimmed = text.trimStart()
        return when {
            trimmed.startsWith("{") -> BackupJson.fromJson(text)
            text.contains("## 备忘录") || text.contains("##购物清单") -> fromMarkdown(text)
            text.contains("===== 备忘录 =====") -> fromText(text)
            else -> throw IOException("无法识别的备份文件格式（仅支持 JSON / Markdown / 文本 / Excel 备份）")
        }
    }

    // ---------------- Excel(.xlsx) ----------------

    private fun fromXlsx(bytes: ByteArray): BackupData {
        val sheetXml = mutableMapOf<Int, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val m = Regex("xl/worksheets/sheet(\\d+)\\.xml$").find(e.name)
                if (m != null) sheetXml[m.groupValues[1].toInt()] = zis.readBytes().toString(Charsets.UTF_8)
                e = zis.nextEntry
            }
        }
        val memoRows = sheetXml[1]?.let { parseSheetRows(it) } ?: emptyList()
        val shopRows = sheetXml[2]?.let { parseSheetRows(it) } ?: emptyList()
        val tagRows = sheetXml[3]?.let { parseSheetRows(it) } ?: emptyList()

        // 首列是否为「标识」(id)：自家导出带 id（各列整体右移一位），第三方文件不带。
        // 带 id 时按原 id 还原，保证「导出 → 再导入」按主键原地覆盖、不重复。
        val tagHasId = tagRows.firstOrNull()?.firstOrNull()?.trim() == XLSX_ID_HEADER
        val memoHasId = memoRows.firstOrNull()?.firstOrNull()?.trim() == XLSX_ID_HEADER
        val shopHasId = shopRows.firstOrNull()?.firstOrNull()?.trim() == XLSX_ID_HEADER

        val nameToId = mutableMapOf<String, String>()
        val tags = tagRows.drop(1).mapNotNull { row ->
            val name = row.getOrNull(idx(tagHasId, 0))?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val id = if (tagHasId) row.getOrNull(0)?.takeIf { it.isNotBlank() } ?: newId() else newId()
            nameToId[name] = id
            val tagDeletedAt = parseTime(row.getOrNull(idx(tagHasId, 4)))
            TagEntity(
                id = id, name = name, color = parseColor(row.getOrNull(idx(tagHasId, 1))),
                sortOrder = row.getOrNull(idx(tagHasId, 2))?.toIntOrNull() ?: 0,
                createdAt = parseTime(row.getOrNull(idx(tagHasId, 3))) ?: now(),
                deletedAt = tagDeletedAt
            )
        }
        val memos = memoRows.drop(1).mapNotNull { row ->
            val title = row.getOrNull(idx(memoHasId, 0))?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val id = if (memoHasId) row.getOrNull(0)?.takeIf { it.isNotBlank() } ?: newId() else newId()
            val memoDeletedAt = parseTime(row.getOrNull(idx(memoHasId, 7)))
            MemoEntity(
                id = id, title = title, content = row.getOrNull(idx(memoHasId, 1)) ?: "",
                tagIds = toTagIds(row.getOrNull(idx(memoHasId, 2)), nameToId),
                isCompleted = row.getOrNull(idx(memoHasId, 3)) == "1", isFavorite = row.getOrNull(idx(memoHasId, 4)) == "1",
                createdAt = parseTime(row.getOrNull(idx(memoHasId, 5))) ?: now(),
                updatedAt = parseTime(row.getOrNull(idx(memoHasId, 6))) ?: now(),
                deletedAt = memoDeletedAt
            )
        }
        val shopping = shopRows.drop(1).mapNotNull { row ->
            val name = row.getOrNull(idx(shopHasId, 0))?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val id = if (shopHasId) row.getOrNull(0)?.takeIf { it.isNotBlank() } ?: newId() else newId()
            val shopDeletedAt = parseTime(row.getOrNull(idx(shopHasId, 7)))
            ShoppingItemEntity(
                id = id, name = name, quantity = row.getOrNull(idx(shopHasId, 1))?.toIntOrNull() ?: 1,
                unit = row.getOrNull(idx(shopHasId, 2))?.takeIf { it.isNotBlank() } ?: "个",
                tagIds = toTagIds(row.getOrNull(idx(shopHasId, 3)), nameToId),
                isCompleted = row.getOrNull(idx(shopHasId, 4)) == "1",
                createdAt = parseTime(row.getOrNull(idx(shopHasId, 5))) ?: now(),
                updatedAt = parseTime(row.getOrNull(idx(shopHasId, 6))) ?: now(),
                deletedAt = shopDeletedAt
            )
        }
        return BackupData(memos = memos, shoppingItems = shopping, tags = tags)
    }

    /** 解析一张工作表 XML，返回行列表，每行是按列索引(0 起)排列的单元格文本（空单元格为 ""）。 */
    private fun parseSheetRows(xml: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<c r=\"([A-Z]+)\\d+\"[^>]*>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
        val tRegex = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
        val vRegex = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
        for (rm in rowRegex.findAll(xml)) {
            val rowInner = rm.groupValues[1]
            val cells = mutableMapOf<Int, String>()
            for (cm in cellRegex.findAll(rowInner)) {
                val col = colIndex(cm.groupValues[1])
                val inner = cm.groupValues[2]
                val value = when {
                    inner.contains("<is>") -> unescapeXml(tRegex.find(inner)?.groupValues?.get(1) ?: "")
                    inner.contains("<v>") -> (vRegex.find(inner)?.groupValues?.get(1) ?: "")
                    else -> ""
                }
                cells[col] = value
            }
            val maxCol = cells.keys.maxOrNull() ?: -1
            val row = MutableList(maxCol + 1) { "" }
            cells.forEach { (k, v) -> row[k] = v }
            result.add(row)
        }
        return result
    }

    // ---------------- Markdown(.md) ----------------

    private fun fromMarkdown(text: String): BackupData {
        val nameToId = mutableMapOf<String, String>()
        val tags = mutableListOf<TagEntity>()
        val memos = mutableListOf<MemoEntity>()
        val shopping = mutableListOf<ShoppingItemEntity>()

        val lines = text.split("\n")
        var section: String? = null
        var curTitle: String? = null
        var curId: String? = null
        var curMeta: String? = null
        var curBody = StringBuilder()
        var inBody = false

        fun flushMemo() {
            curTitle?.let { title ->
                val (created, updated, done, fav, tagNames, isDeleted) = parseMarkdownMeta(curMeta)
                memos.add(
                    MemoEntity(
                        id = curId ?: newId(), title = title, content = curBody.toString().trim(),
                        tagIds = toTagIds(tagNames, nameToId), isCompleted = done, isFavorite = fav,
                        createdAt = created ?: now(), updatedAt = updated ?: now(),
                        deletedAt = if (isDeleted) now() else null
                    )
                )
            }
            curTitle = null; curId = null; curMeta = null; curBody = StringBuilder(); inBody = false
        }

        for (raw in lines) {
            val line = raw.trimEnd()
            when {
                line.startsWith("## 备忘录") -> { flushMemo(); section = "memo" }
                line.startsWith("## 购物清单") -> { flushMemo(); section = "shop" }
                line.startsWith("## 标签") -> { flushMemo(); section = "tag" }
                line.startsWith("# ") -> { /* 标题，忽略 */ }
                else -> when (section) {
                    "memo" -> {
                        if (line.startsWith("### ")) {
                            flushMemo()
                            val (titleLine, id) = stripId(line.removePrefix("### ").trim())
                            curTitle = titleLine
                            curId = id
                        } else if (line.startsWith("*") && line.endsWith("*")) {
                            curMeta = line
                        } else if (line == "---") {
                            flushMemo()
                        } else if (line.isNotBlank() && curTitle != null) {
                            inBody = true
                            curBody.append(if (curBody.isEmpty()) line else "\n$line")
                        }
                    }
                    "shop" -> {
                        val (clean, id) = stripId(line)
                        val m = Regex("^- \\[( |x)\\] (.+)$").find(clean)
                        if (m != null) {
                            val done = m.groupValues[1] == "x"
                            var rest = m.groupValues[2].trim()
                            val deleted = "[已删除]" in rest
                            if (deleted) rest = rest.replace("[已删除]", "").trim()
                            val tagPart = if ("`" in rest) rest.substringAfterLast("`").substringBefore("`").takeIf { it.isNotBlank() } else null
                            val main = if ("`" in rest) rest.substringBefore("`").trim() else rest
                            val name = main.substringBefore("　×").substringBefore(" ×").trim()
                            val qtyUnit = if ("×" in main) main.substringAfter("×") else ""
                            val q = qtyUnit.takeWhile { it.isDigit() }.toIntOrNull() ?: 1
                            val unit = qtyUnit.dropWhile { it.isDigit() }.trim().ifBlank { "个" }
                            shopping.add(
                                ShoppingItemEntity(
                                    id = id ?: newId(), name = name, quantity = q, unit = unit,
                                    tagIds = toTagIds(tagPart, nameToId), isCompleted = done,
                                    deletedAt = if (deleted) now() else null
                                )
                            )
                        }
                    }
                    "tag" -> {
                        val (clean, id) = stripId(line)
                        val deleted = "[已删除]" in clean
                        val clean2 = if (deleted) clean.replace("[已删除]", "").trim() else clean
                        val m = Regex("^- (.+?)（(#?[0-9A-Fa-f]+)）").find(clean2)
                        if (m != null) {
                            val name = m.groupValues[1].trim()
                            val tagId = id ?: newId()
                            nameToId[name] = tagId
                            tags.add(TagEntity(id = tagId, name = name, color = parseColor(m.groupValues[2]), deletedAt = if (deleted) now() else null))
                        }
                    }
                }
            }
        }
        flushMemo()
        return BackupData(memos = memos, shoppingItems = shopping, tags = tags)
    }

    private fun parseMarkdownMeta(meta: String?): MetaResult {
        if (meta == null) return MetaResult(null, null, false, false, null, false)
        val m = meta.trim().removeSurrounding("*")
        // 导出端（BackupViewModel.buildMarkdown）用全角空格「　」连接各元信息段，
        // 这里必须按全角空格切分，按关键字取回标签/时间/状态（BUG-修复：
        // 原先按半角空格切分导致标签、修改时间整体粘连、往返丢失）。
        val parts = m.split("　").map { it.trim() }.filter { it.isNotBlank() }
        val tagNames = parts.firstOrNull { it.startsWith("标签：") }?.removePrefix("标签：")?.trim()?.takeIf { it.isNotBlank() }
        val created = parts.firstOrNull { it.startsWith("创建：") }?.removePrefix("创建：")?.trim()?.let { parseTime(it) }
        val updated = parts.firstOrNull { it.startsWith("修改：") }?.removePrefix("修改：")?.trim()?.let { parseTime(it) }
        val done = parts.any { it == "状态：已完成" }
        val fav = parts.any { it.contains("★") }
        val isDeleted = parts.any { it == "状态：已删除" }
        return MetaResult(created, updated, done, fav, tagNames, isDeleted)
    }

    private data class MetaResult(
        val created: Long?, val updated: Long?, val isCompleted: Boolean,
        val isFavorite: Boolean, val tagNames: String?, val isDeleted: Boolean
    )

    // ---------------- 纯文本(.txt) ----------------

    private fun fromText(text: String): BackupData {
        val nameToId = mutableMapOf<String, String>()
        val tags = mutableListOf<TagEntity>()
        val memos = mutableListOf<MemoEntity>()
        val shopping = mutableListOf<ShoppingItemEntity>()

        val lines = text.split("\n")
        var section: String? = null
        var i = 0
        while (i < lines.size) {
            val t = lines[i].trim()
            when {
                t.startsWith("===== 备忘录 =====") -> section = "memo"
                t.startsWith("===== 购物清单 =====") -> section = "shop"
                t.startsWith("===== 标签 =====") -> section = "tag"
                else -> when (section) {
                    "tag" -> {
                        val idM = Regex("标识：([0-9a-fA-F-]{36})").find(t)
                        val id = idM?.groupValues?.get(1)
                        val raw = if (idM != null) t.replace(idM.value, "").trim() else t
                        val tagDeleted = "[已删除]" in raw
                        val raw2 = if (tagDeleted) raw.replace("[已删除]", "").trim() else raw
                        val m = Regex("^- (.+?)（(#?[0-9A-Fa-f]+)）").find(raw2)
                        if (m != null) {
                            val name = m.groupValues[1].trim()
                            val tagId = id ?: newId()
                            nameToId[name] = tagId
                            tags.add(TagEntity(id = tagId, name = name, color = parseColor(m.groupValues[2]), deletedAt = if (tagDeleted) now() else null))
                        }
                    }
                    "memo" -> {
                        val mt = Regex("^【\\d+】(.*)$").find(t)
                        if (mt != null) {
                            val title = mt.groupValues[1].trim()
                            var body = StringBuilder()
                            var created: Long? = null; var updated: Long? = null
                            var done = false; var fav = false; var tagNames: String? = null
                            var deleted = false
                            var memoId: String? = null
                            var inBody = false
                            var j = i + 1
                            while (j < lines.size) {
                                val l2 = lines[j].trim()
                                if (l2.startsWith("【") || l2.startsWith("=====")) break
                                when {
                                    l2.startsWith("标识：") -> memoId = l2.removePrefix("标识：").trim().takeIf { it.isNotBlank() }
                                    l2.startsWith("标签：") -> tagNames = l2.removePrefix("标签：").trim().takeIf { it != "（无）" }
                                    l2.contains("创建：") -> created = parseTime(l2.substringAfter("创建：").substringBefore("　修改：").substringBefore(" ").trim())
                                    l2.contains("修改：") -> updated = parseTime(l2.substringAfter("修改：").trim())
                                    l2.contains("★收藏") -> fav = true
                                    l2.startsWith("状态：") -> { done = l2.contains("已完成"); deleted = l2.contains("已删除") }
                                    l2.startsWith("正文：") -> {
                                        inBody = true
                                        // 正文内容既可能在「正文：」同行（正文：xxx），也可能在下一行，
                                        // 这里捕获同行部分，后续行由下方 inBody 分支继续拼接。
                                        val b = l2.removePrefix("正文：").trim()
                                        if (b.isNotBlank()) body.append(b)
                                    }
                                    l2.isNotBlank() && inBody -> body.append(if (body.isEmpty()) l2 else "\n$l2")
                                }
                                j++
                            }
                            i = j - 1
                            memos.add(
                                MemoEntity(
                                    id = memoId ?: newId(), title = title, content = body.toString().trim(),
                                    tagIds = toTagIds(tagNames, nameToId), isCompleted = done, isFavorite = fav,
                                    createdAt = created ?: now(), updatedAt = updated ?: now(),
                                    deletedAt = if (deleted) now() else null
                                )
                            )
                        }
                    }
                    "shop" -> {
                        val mt = Regex("^【\\d+】(.*)$").find(t)
                        if (mt != null) {
                            var rest = mt.groupValues[1].trim()
                            var done = false
                            if (rest.startsWith("[已完成]")) { done = true; rest = rest.removePrefix("[已完成]").trim() }
                            val shopDeleted = "[已删除]" in rest
                            if (shopDeleted) rest = rest.replace("[已删除]", "").trim()
                            val idM = Regex("标识：([0-9a-fA-F-]{36})").find(rest)
                            val id = idM?.groupValues?.get(1)
                            if (idM != null) rest = rest.replace(idM.value, "").trim()
                            val tagPart = if ("（标签：" in rest) rest.substringAfter("（标签：").substringBefore("）").takeIf { it.isNotBlank() } else null
                            val main = if ("（标签：" in rest) rest.substringBefore("（标签：") else rest
                            val name = main.substringBefore(" ×").substringBefore(" ×").trim()
                            val qtyUnit = if ("×" in main) main.substringAfter("×") else ""
                            val q = qtyUnit.takeWhile { it.isDigit() }.toIntOrNull() ?: 1
                            val unit = qtyUnit.dropWhile { it.isDigit() }.trim().ifBlank { "个" }
                            shopping.add(
                                ShoppingItemEntity(
                                    id = id ?: newId(), name = name, quantity = q, unit = unit,
                                    tagIds = toTagIds(tagPart, nameToId), isCompleted = done,
                                    deletedAt = if (shopDeleted) now() else null
                                )
                            )
                        }
                    }
                }
            }
            i++
        }
        return BackupData(memos = memos, shoppingItems = shopping, tags = tags)
    }

    // ---------------- 工具 ----------------

    /** xlsx 导出时放在每张表首列的「标识」(id) 表头。检测到即按原 id 还原。 */
    private const val XLSX_ID_HEADER = "标识"

    /** 带 id 列时，逻辑列索引在原基础上 +1（第 0 列是 id）。 */
    private fun idx(hasId: Boolean, logical: Int) = if (hasId) logical + 1 else logical

    /**
     * 从 Markdown 行中提取 `<!--id:UUID-->` 注释，返回（去注释后的干净文本, id?）。
     * 自家 Markdown 导出会在每条记录写入该注释以保留主键，保证再导入幂等。
     */
    private fun stripId(line: String): Pair<String, String?> {
        val m = Regex("<!--\\s*id:\\s*([0-9a-fA-F-]{36})\\s*-->").find(line)
        return if (m != null) (line.replace(m.value, "").trim() to m.groupValues[1]) else (line to null)
    }

    private fun newId(): String = UUID.randomUUID().toString()
    private fun now(): Long = System.currentTimeMillis()

    private fun parseTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).parse(s.trim())?.time
        }.getOrNull()
    }

    private fun parseColor(hex: String?): Int {
        if (hex.isNullOrBlank()) return 0xFF2196F3.toInt()
        val h = if (hex.startsWith("#")) hex else "#$hex"
        return runCatching { Color.parseColor(h) }.getOrDefault(0xFF2196F3.toInt())
    }

    private fun toTagIds(names: String?, nameToId: Map<String, String>): String {
        if (names.isNullOrBlank()) return ""
        return names.split("、", ",").map { it.trim() }.filter { it.isNotBlank() }
            .mapNotNull { nameToId[it] }
            .joinToString(",")
    }

    private fun colIndex(letters: String): Int {
        var n = 0
        for (c in letters) n = n * 26 + (c - 'A' + 1)
        return n - 1
    }

    private fun unescapeXml(s: String): String {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
    }
}
