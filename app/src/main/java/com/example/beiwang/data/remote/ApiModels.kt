package com.example.beiwang.data.remote

/**
 * 后端通信的数据模型（DTO）。
 *
 * 字段命名与后端 REST 契约保持一致（camelCase），Retrofit + Gson 直接序列化。
 * 所有字段带默认值，避免后端缺字段时反序列化失败（参考 android-native-dev 规范：
 * 服务端返回字段应可空/有默认）。
 */

// ---------- 认证 ----------

data class AuthRequest(
    val email: String,
    val password: String
)

data class UserDto(
    val id: Int = 0,
    val email: String? = null
)

data class AuthResponse(
    val token: String = "",
    val refreshToken: String = "",
    val user: UserDto? = null
)

// 刷新令牌请求/响应：用 refreshToken 换发新的 access token（绕过 2h 过期）
data class RefreshRequest(
    val refreshToken: String = ""
)

data class RefreshResponse(
    val token: String = ""
)

// ---------- 同步 ----------

data class MemoDto(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val isCompleted: Boolean = false,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val tagIds: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null
)

data class ShoppingItemDto(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val unit: String = "个",
    val priority: Int = 0, // 哨兵 0 = 服务端未返回优先级（旧库无该列），拉取时沿用本地值
    val tagIds: String = "",
    // 手动排序位。哨兵 0 表示服务端尚未下发过排序（旧库无该列），拉取时沿用本地值
    val sortOrder: Int = 0,
    val isCompleted: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null
)

/** 待办：结构与购物清单同构，独立表 todos，同样参与 LWW 双向同步。 */
data class TodoDto(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val note: String = "",
    val priority: Int = 3,
    val tagIds: String = "",
    val sortOrder: Int = 0,
    val isCompleted: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null
)

data class TagDto(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val color: Int = 0,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null
)

data class ChecklistItemDto(
    val id: String = "",
    val userId: String = "",
    val memoId: String = "",
    val content: String = "",
    val isChecked: Boolean = false,
    val position: Int = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null
)

/**
 * 上传到服务端的同步包：四类数据增量推送，服务端按 updatedAt 做 LWW 合并（幂等）。
 * purged*Ids：本机已永久删除（清空回收站 / 永久删除单条）的 id 集合，每次推送都带上，
 * 服务端据此真删对应墓碑——因服务端原本只软删、墓碑永留，若不反向告知，任意端都会
 * 把墓碑重新拉回回收站（即「清空后又复活」）。purged_ids 在本地永久保留（不再清理），
 * 故本集合稳定收敛：服务端删完后，后续推送为幂等 no-op。
 */
data class SyncPushRequest(
    val memos: List<MemoDto> = emptyList(),
    val shoppingItems: List<ShoppingItemDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val checklistItems: List<ChecklistItemDto> = emptyList(),
    val todos: List<TodoDto> = emptyList(),
    val purgedMemoIds: List<String> = emptyList(),
    val purgedShoppingIds: List<String> = emptyList(),
    val purgedTagIds: List<String> = emptyList(),
    val purgedTodoIds: List<String> = emptyList()
)

/** 从服务端拉取的增量数据。 */
data class SyncPullResponse(
    val memos: List<MemoDto> = emptyList(),
    val shoppingItems: List<ShoppingItemDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val checklistItems: List<ChecklistItemDto> = emptyList(),
    val todos: List<TodoDto> = emptyList(),
    val serverTime: Long = 0,
    val hasMore: Boolean = false
)

/** 上传响应：applied 为服务端已应用记录的主键列表（如 "memo:<id>"）。 */
data class SyncPushResponse(
    val applied: List<String> = emptyList()
)
