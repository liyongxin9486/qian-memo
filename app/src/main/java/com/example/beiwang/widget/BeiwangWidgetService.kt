package com.example.beiwang.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.beiwang.R
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.entity.MemoEntity
import com.example.beiwang.data.entity.ShoppingItemEntity
import com.example.beiwang.data.entity.TodoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 桌面组件（RemoteViews 版）的数据源。
 *
 * 结构：顶部三个看板标签（备忘录 / 购物清单 / 待办）+ 一个 **可滚动 ListView**。
 * 当前看板由 [BeiwangAppWidgetProvider.getBoard] 决定，切标签即换数据源。
 *
 * 为什么不再是 AdapterViewFlipper 的三页静态看板：那种做法每页只能预置固定行数
 * （原先 6 行），购物清单 31 条时会被裁掉且无法查看。ListView 可滚动，行数不受限。
 */
class BeiwangWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        BeiwangWidgetFactory(applicationContext)
}

class BeiwangWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var memos: List<MemoEntity> = emptyList()
    private var shoppingItems: List<ShoppingItemEntity> = emptyList()
    private var todos: List<TodoEntity> = emptyList()

    override fun onCreate() {}

    /** 在 Binder 线程调用，允许阻塞；直接读与 App 同一份 Room 库。 */
    override fun onDataSetChanged() {
        val db = AppDatabase.getDatabase(context)
        memos = runCatching {
            runBlocking(Dispatchers.IO) { db.memoDao().getAllMemos().first() }
        }.getOrDefault(emptyList())
        shoppingItems = runCatching {
            runBlocking(Dispatchers.IO) { db.shoppingItemDao().getAllItems().first() }
        }.getOrDefault(emptyList())
        todos = runCatching {
            runBlocking(Dispatchers.IO) { db.todoDao().getAllTodos().first() }
        }.getOrDefault(emptyList())
    }

    override fun onDestroy() {}

    private fun board(): Int = BeiwangAppWidgetProvider.getBoard(context)

    /** 空列表也返回 1 行，用来显示「暂无…」占位（RemoteViews 没有 setEmptyView）。 */
    override fun getCount(): Int = when (board()) {
        BeiwangAppWidgetProvider.BOARD_SHOPPING -> shoppingItems.size.coerceAtLeast(1)
        BeiwangAppWidgetProvider.BOARD_TODO -> todos.size.coerceAtLeast(1)
        else -> memos.size.coerceAtLeast(1)
    }

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_item)
        when (board()) {
            BeiwangAppWidgetProvider.BOARD_SHOPPING -> bindShopping(views, position)
            BeiwangAppWidgetProvider.BOARD_TODO -> bindTodo(views, position)
            else -> bindMemo(views, position)
        }
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private fun showEmpty(views: RemoteViews, text: String) {
        views.setViewVisibility(R.id.item_box, View.GONE)
        views.setTextViewText(R.id.item_text, text)
    }

    private fun fillIn(action: String, extraKey: String, extraValue: String): Intent =
        Intent().apply {
            putExtra(WidgetActionActivity.EXTRA_ACTION, action)
            putExtra(extraKey, extraValue)
        }

    private fun bindMemo(views: RemoteViews, position: Int) {
        val list = memos.sortedWith(
            compareByDescending<MemoEntity> { it.isPinned }.thenByDescending { it.updatedAt }
        )
        val memo = list.getOrNull(position)
        if (memo == null) {
            showEmpty(views, "暂无备忘录")
            return
        }
        views.setTextViewText(R.id.item_box, "•")
        views.setTextViewText(R.id.item_text, memo.title.ifBlank { "（无标题）" })
        views.setOnClickFillInIntent(
            R.id.item_row,
            fillIn(WidgetActionActivity.ACTION_OPEN_MEMO, WidgetActionActivity.EXTRA_MEMO_ID, memo.id)
        )
    }

    private fun bindShopping(views: RemoteViews, position: Int) {
        // 与 App 内 ShoppingListScreen 的呈现顺序一致：未勾选在前、打勾的沉底；
        // 组内先按用户手动排序位，再按优先级、名称。
        val list = shoppingItems.sortedWith(
            compareBy<ShoppingItemEntity> { it.isCompleted }
                .thenBy { it.sortOrder }
                .thenBy { it.priority }
                .thenBy { it.name }
        )
        val item = list.getOrNull(position)
        if (item == null) {
            showEmpty(views, "暂无商品")
            return
        }
        views.setTextViewText(R.id.item_box, if (item.isCompleted) "☑" else "☐")
        views.setTextViewText(R.id.item_text, "P${item.priority} ${item.name} ×${item.quantity}")
        views.setOnClickFillInIntent(
            R.id.item_row,
            fillIn(WidgetActionActivity.ACTION_TOGGLE_SHOPPING, WidgetActionActivity.EXTRA_ITEM_ID, item.id)
        )
    }

    private fun bindTodo(views: RemoteViews, position: Int) {
        // 与 App 内 TodoListScreen 一致：未完成在前、已勾选沉底；组内按排序位、优先级、标题。
        val list = todos.sortedWith(
            compareBy<TodoEntity> { it.isCompleted }
                .thenBy { it.sortOrder }
                .thenBy { it.priority }
                .thenBy { it.title }
        )
        val todo = list.getOrNull(position)
        if (todo == null) {
            showEmpty(views, "暂无待办")
            return
        }
        views.setTextViewText(R.id.item_box, if (todo.isCompleted) "☑" else "☐")
        views.setTextViewText(R.id.item_text, "P${todo.priority} ${todo.title.ifBlank { "（无标题）" }}")
        views.setOnClickFillInIntent(
            R.id.item_row,
            fillIn(WidgetActionActivity.ACTION_TOGGLE_TODO, WidgetActionActivity.EXTRA_ITEM_ID, todo.id)
        )
    }
}
