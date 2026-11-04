package com.example.beiwang.data.backup

import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.entity.ShoppingItemEntity
import com.example.beiwang.data.entity.TagEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 备份数据 <-> JSON 的序列化。使用 Android 内置的 org.json，避免引入第三方库。
 * 所有字段均按名称读写，可容忍未来新增/缺失字段。
 */
object BackupJson {

    fun toJson(data: BackupData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("exportedAt", data.exportedAt)

        root.put("memos", JSONArray().apply {
            data.memos.forEach { put(memoToJson(it)) }
        })
        root.put("shoppingItems", JSONArray().apply {
            data.shoppingItems.forEach { put(shoppingToJson(it)) }
        })
        root.put("tags", JSONArray().apply {
            data.tags.forEach { put(tagToJson(it)) }
        })
        root.put("attachments", JSONArray().apply {
            data.attachments.forEach { put(attachmentToJson(it)) }
        })
        return root.toString(2)
    }

    fun fromJson(json: String): BackupData {
        val root = JSONObject(json)
        return BackupData(
            version = root.optInt("version", 1),
            exportedAt = if (root.has("exportedAt") && !root.isNull("exportedAt")) root.getLong("exportedAt") else System.currentTimeMillis(),
            memos = root.optJSONArray("memos").toEntityList { memoFromJson(it) },
            shoppingItems = root.optJSONArray("shoppingItems").toEntityList { shoppingFromJson(it) },
            tags = root.optJSONArray("tags").toEntityList { tagFromJson(it) },
            attachments = root.optJSONArray("attachments").toEntityList { attachmentFromJson(it) }
        )
    }

    // ----- 单实体序列化 -----

    private fun memoToJson(m: MemoEntity) = JSONObject().apply {
        put("id", m.id)
        put("userId", m.userId)
        put("title", m.title)
        put("content", m.content)
        put("isCompleted", m.isCompleted)
        put("isFavorite", m.isFavorite)
        put("tagIds", m.tagIds)
        put("createdAt", m.createdAt)
        put("updatedAt", m.updatedAt)
        putOpt("deletedAt", m.deletedAt)
        put("syncStatus", m.syncStatus)
    }

    private fun memoFromJson(o: JSONObject) = MemoEntity(
        id = idOrNew(o),
        userId = o.optString("userId", ""),
        title = o.optString("title", ""),
        content = o.optString("content", ""),
        isCompleted = o.optBoolean("isCompleted", false),
        isFavorite = o.optBoolean("isFavorite", false),
        tagIds = o.optString("tagIds", ""),
        createdAt = if (o.has("createdAt")) o.getLong("createdAt") else System.currentTimeMillis(),
        updatedAt = if (o.has("updatedAt")) o.getLong("updatedAt") else System.currentTimeMillis(),
        deletedAt = optLongOrNull(o, "deletedAt"),
        syncStatus = o.optString("syncStatus", "UNSYNCED")
    )

    private fun shoppingToJson(s: ShoppingItemEntity) = JSONObject().apply {
        put("id", s.id)
        put("userId", s.userId)
        put("name", s.name)
        put("quantity", s.quantity)
        put("unit", s.unit)
        put("tagIds", s.tagIds)
        put("isCompleted", s.isCompleted)
        put("createdAt", s.createdAt)
        put("updatedAt", s.updatedAt)
        putOpt("deletedAt", s.deletedAt)
        put("syncStatus", s.syncStatus)
    }

    private fun shoppingFromJson(o: JSONObject) = ShoppingItemEntity(
        id = idOrNew(o),
        userId = o.optString("userId", ""),
        name = o.optString("name", ""),
        quantity = o.optInt("quantity", 1),
        unit = o.optString("unit", "个"),
        tagIds = o.optString("tagIds", ""),
        isCompleted = o.optBoolean("isCompleted", false),
        createdAt = if (o.has("createdAt")) o.getLong("createdAt") else System.currentTimeMillis(),
        updatedAt = if (o.has("updatedAt")) o.getLong("updatedAt") else System.currentTimeMillis(),
        deletedAt = optLongOrNull(o, "deletedAt"),
        syncStatus = o.optString("syncStatus", "UNSYNCED")
    )

    private fun tagToJson(c: TagEntity) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("color", c.color)
        put("sortOrder", c.sortOrder)
        put("createdAt", c.createdAt)
    }

    private fun tagFromJson(o: JSONObject) = TagEntity(
        id = idOrNew(o),
        name = o.optString("name", ""),
        color = o.optInt("color", 0xFF2196F3.toInt()),
        sortOrder = o.optInt("sortOrder", 0),
        createdAt = if (o.has("createdAt")) o.getLong("createdAt") else System.currentTimeMillis()
    )

    private fun attachmentToJson(a: MemoAttachmentBackup) = JSONObject().apply {
        put("id", a.id)
        put("memoId", a.memoId)
        put("type", a.type)
        put("durationMs", a.durationMs)
        put("ext", a.ext)
        put("base64", a.base64)
    }

    private fun attachmentFromJson(o: JSONObject) = MemoAttachmentBackup(
        id = idOrNew(o),
        memoId = o.optString("memoId", ""),
        type = o.optString("type", "IMAGE"),
        durationMs = o.optLong("durationMs", 0),
        ext = o.optString("ext", "jpg"),
        base64 = o.optString("base64", "")
    )

    // ----- 工具方法 -----

    /** 优先使用文件中的 id，缺失（如手工编辑的 JSON）时生成新 id，避免崩溃。 */
    private fun idOrNew(o: JSONObject): String =
        o.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

    private fun optStringOrNull(o: JSONObject, key: String): String? =
        if (o.has(key) && !o.isNull(key)) o.getString(key) else null

    private fun optLongOrNull(o: JSONObject, key: String): Long? =
        if (o.has(key) && !o.isNull(key)) o.getLong(key) else null

    private fun <T> JSONArray?.toEntityList(map: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val result = mutableListOf<T>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            runCatching { map(item) }.onSuccess { result.add(it) }
        }
        return result
    }
}
