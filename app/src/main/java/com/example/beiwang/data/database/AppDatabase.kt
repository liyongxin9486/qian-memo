package com.example.beiwang.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.beiwang.data.dao.*
import com.example.beiwang.data.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Database(
    entities = [
        MemoEntity::class,
        ShoppingItemEntity::class,
        TagEntity::class,
        MemoAttachmentEntity::class,
        MemoHistoryEntity::class,
        MemoChecklistItemEntity::class,
        PurgedIdEntity::class,
        TodoEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun tagDao(): TagDao
    abstract fun memoAttachmentDao(): MemoAttachmentDao
    abstract fun memoHistoryDao(): MemoHistoryDao
    abstract fun purgedIdDao(): PurgedIdDao
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 注：priority 列改由 MIGRATION_8_9 统一添加，这里不再添加，
                // 避免老版本(v1/v2)升级到 v9 时重复加列导致 Room 迁移崩溃。
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Index added to memo_sub_tasks.memoId to back the foreign key (onDelete = CASCADE)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memo_sub_tasks_memoId ON memo_sub_tasks(memoId)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 早期版本包含 tags / sync_queue 等已删除实体的表。
                // 当前 schema 只保留这四张业务表；自动清理掉所有不属于当前 schema 的表。
                val keepTables = setOf(
                    "memos", "memo_sub_tasks", "shopping_items", "categories",
                    "room_master_table", "sqlite_sequence"
                )
                val cursor = db.query("SELECT name FROM sqlite_master WHERE type = 'table'")
                val toDrop = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    if (name !in keepTables) {
                        toDrop.add(name)
                    }
                }
                cursor.close()
                toDrop.forEach { table ->
                    db.execSQL("DROP TABLE IF EXISTS `$table`")
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 重建 memos：去掉已移除的 categoryId/priority/dueDate/reminderTime/repeatRule，
                // 仅保留实体声明的列并新增 tagIds。Room 会按实体校验迁移后的 schema，
                // 表中不能残留实体未声明的列，因此必须重建而非仅 ADD COLUMN。
                db.execSQL(
                    """CREATE TABLE memos_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL DEFAULT '',
                        title TEXT NOT NULL,
                        content TEXT NOT NULL DEFAULT '',
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        tagIds TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL DEFAULT 'UNSYNCED'
                    )"""
                )
                db.execSQL(
                    """INSERT INTO memos_new (id, userId, title, content, isCompleted, isFavorite, tagIds, createdAt, updatedAt, deletedAt, syncStatus)
                       SELECT id, userId, title, content, isCompleted, isFavorite, '', createdAt, updatedAt, deletedAt, syncStatus FROM memos"""
                )
                db.execSQL("DROP TABLE memos")
                db.execSQL("ALTER TABLE memos_new RENAME TO memos")

                // 重建 shopping_items：去掉已移除的 categoryId/priority，新增 tagIds。
                db.execSQL(
                    """CREATE TABLE shopping_items_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL DEFAULT '',
                        name TEXT NOT NULL,
                        quantity INTEGER NOT NULL DEFAULT 1,
                        unit TEXT NOT NULL DEFAULT '个',
                        tagIds TEXT NOT NULL DEFAULT '',
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL DEFAULT 'UNSYNCED'
                    )"""
                )
                db.execSQL(
                    """INSERT INTO shopping_items_new (id, userId, name, quantity, unit, tagIds, isCompleted, createdAt, updatedAt, deletedAt, syncStatus)
                       SELECT id, userId, name, quantity, unit, '', isCompleted, createdAt, updatedAt, deletedAt, syncStatus FROM shopping_items"""
                )
                db.execSQL("DROP TABLE shopping_items")
                db.execSQL("ALTER TABLE shopping_items_new RENAME TO shopping_items")

                // 删除不再使用的子任务表与分类表（优先级/分类功能已移除）。
                db.execSQL("DROP TABLE IF EXISTS memo_sub_tasks")
                db.execSQL("DROP TABLE IF EXISTS categories")

                // 新建 v5 引入的三张表（Room 在手动迁移中不会自动建表，必须显式创建且列定义与实体一致）。
                db.execSQL(
                    """CREATE TABLE `tags` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `color` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE `memo_attachments` (
                        `id` TEXT NOT NULL,
                        `memoId` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `uri` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`memoId`) REFERENCES `memos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX `index_memo_attachments_memoId` ON `memo_attachments` (`memoId`)")
                db.execSQL(
                    """CREATE TABLE `memo_history` (
                        `id` TEXT NOT NULL,
                        `memoId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `snapshotAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )"""
                )
                db.execSQL("CREATE INDEX `index_memo_history_memoId` ON `memo_history` (`memoId`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 备忘录新增置顶字段 isPinned。
                db.execSQL("ALTER TABLE memos ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 备忘录新增清单项表（详情页内可勾选的待办清单）。
                // 列定义须与 MemoChecklistItemEntity 完全一致：实体未声明外键/索引/默认值，
                // 因此此处不能添加，否则 Room 的 schema 校验会报 Migration 不完整。
                db.execSQL(
                    """CREATE TABLE `memo_checklist_items` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `memoId` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `isChecked` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v8：为支持云端双向同步，标签与清单项补充 updatedAt 时间戳（LWW 冲突解决）。
                db.execSQL("ALTER TABLE `tags` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `memo_checklist_items` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v9：购物项新增优先级字段（1=P1 最高、2=P2、3=P3 最低），用于列表/组件内排序。
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN priority INTEGER NOT NULL DEFAULT 3")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v10：标签与清单项支持「待上传标记 + 软删除墓碑」，
                // 使它们的增删改能像备忘/购物一样跨设备实时同步。
                // 注意：列名必须用 camelCase（syncStatus/deletedAt），与 Entity 字段默认列名一致，
                // 否则 Room 打开时 schema 校验失败导致升级用户崩溃（已修复）。
                db.execSQL("ALTER TABLE tags ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE tags ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE memo_checklist_items ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE memo_checklist_items ADD COLUMN deletedAt INTEGER")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 修正 v10 误用 snake_case 列名(sync_status/deleted_at)的问题：
                // 已升级到 v10 的用户 tags/memo_checklist_items 表列是 snake_case，
                // 与 Entity 默认 camelCase 列名不一致会导致 Room 校验崩溃。用重建表方式改名为 camelCase
                // （兼容所有 Android API，避免依赖 RENAME COLUMN）。
                // 健壮性：仅当表中确实存在 snake_case 旧列时才重建；若已是 camelCase
                // （如从 v9 经本版 v9_10 直接得到 camelCase 的设备，或全新安装跳过重迁移），
                // 则跳过——保证任意升级链路（v9→v11 / v10→v11 / 全新）都不会因缺列而崩溃。
                normalizeSyncColumns(
                    db, "tags",
                    """`id` TEXT NOT NULL PRIMARY KEY, `name` TEXT NOT NULL, `color` INTEGER NOT NULL,
                       `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                       `syncStatus` TEXT NOT NULL DEFAULT 'SYNCED', `deletedAt` INTEGER""",
                    "SELECT id, name, color, sortOrder, createdAt, updatedAt, COALESCE(sync_status, 'SYNCED'), deleted_at FROM tags"
                )
                normalizeSyncColumns(
                    db, "memo_checklist_items",
                    """`id` TEXT NOT NULL PRIMARY KEY, `memoId` TEXT NOT NULL, `content` TEXT NOT NULL,
                       `isChecked` INTEGER NOT NULL, `position` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                       `syncStatus` TEXT NOT NULL DEFAULT 'SYNCED', `deletedAt` INTEGER""",
                    "SELECT id, memoId, content, isChecked, position, updatedAt, COALESCE(sync_status, 'SYNCED'), deleted_at FROM memo_checklist_items"
                )
            }
        }

        // v12：新增 purged_ids 表，记录已永久删除（清空回收站 / 永久删除单条）的条目 id。
        // 同步协议无服务端永久删除，服务端墓碑会在 PULL 时复活；本地持久化这些 id，
        // 拉取合并时命中即跳过，彻底阻止"清空回收站后又被同步拉回"。
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `purged_ids` (" +
                        "`id` TEXT NOT NULL, `type` TEXT NOT NULL, `purgedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        // v13：新增待办模块（todos），并给购物项加手动排序位 sortOrder。
        //  - todos 表与 TodoEntity 逐列对齐（camelCase，Room 校验用），服务端已有同名表。
        //  - shopping_items.sortOrder：用户长按拖动/上下移动后的排序位，跨设备同步。
        //    存量数据默认 0，此时排序退化为「优先级 + 名称」，与升级前表现一致。
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `todos` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `tagIds` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )"""
                )
                db.execSQL(
                    "ALTER TABLE shopping_items ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** 仅当表中存在 snake_case 旧列(sync_status/deleted_at)时才重建为 camelCase，否则跳过（幂等）。 */
        private fun normalizeSyncColumns(
            db: SupportSQLiteDatabase,
            table: String,
            newColumnsSql: String,
            selectSql: String
        ) {
            val cursor = db.query("PRAGMA table_info($table)")
            var hasSnake = false
            while (cursor.moveToNext()) {
                val col = cursor.getString(1) // PRAGMA table_info: 第 1 列是字段名
                if (col == "sync_status" || col == "deleted_at") { hasSnake = true; break }
            }
            cursor.close()
            if (!hasSnake) return
            db.execSQL("CREATE TABLE ${table}_new ($newColumnsSql)")
            db.execSQL("INSERT INTO ${table}_new $selectSql")
            db.execSQL("DROP TABLE $table")
            db.execSQL("ALTER TABLE ${table}_new RENAME TO $table")
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "beiwang_database"
                )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                    MIGRATION_12_13
                )
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * 高危修复（多账号数据隔离）：登出/切换账号时清空本地全部数据。
         * 本工程 DAO 查询不做 userId 过滤，同一设备换号登录会看到上一个账号的
         * 本地缓存，并在新账号下把旧数据推送到新账号的服务端账户（数据串污染）。
         * 统一在登出与登录成功时清库（云端数据重新登录后由全量对账拉回）。
         *
         * 例外：purged_ids（已永久删除的 id 记录）必须保留。它是防「回收站复活」的守卫，
         * 且这些 id 在服务端已被真删，与账号无关。若连同清掉，登录后的全量对账会把
         * 服务端仍残留的墓碑重新写回本地，用户会看到「彻底删掉的东西又回到回收站」。
         * 调用方应在 IO 线程执行；失败静默（不影响登出/登录主流程）。
         */
        fun clearAll(context: Context) {
            try {
                val db = getDatabase(context.applicationContext)
                val purged = runBlocking { db.purgedIdDao().getAll() }
                db.clearAllTables()
                if (purged.isNotEmpty()) {
                    runBlocking { db.purgedIdDao().insertAll(purged) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
