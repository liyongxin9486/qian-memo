package com.example.beiwang.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.beiwang.R

/**
 * 桌面组件（RemoteViews 版）：顶部三个看板标签 + 可滚动列表。
 *
 * 结构
 *  - 标签「备忘录 / 购物清单 / 待办」切换当前看板（高亮当前项），列表随之换成对应数据；
 *  - 右侧「＋」的语义跟随当前看板：备忘录→新建备忘、购物清单→快速加购物、待办→新建待办；
 *  - 列表可滚动，条目数不再受限（此前 flipper 版每页只渲染 6 行，31 条购物项会被裁）。
 *
 * 为什么不是左右滑动：App Widget 只能通过 PendingIntent 响应**点击**，RemoteViews 无法给控件挂
 * OnTouchListener，因此滑动这类连续手势在组件里做不到。曾试过 StackView（自带 fling），但它是
 * 「卡片堆叠」语义，会把多个看板同时绘制并叠在一起，视觉上不可用。故采用标签切换。
 *
 * 所有条目点击都走 [WidgetActionActivity] 透明桥接，保证 Force-stop 后依然可用。
 */
class BeiwangAppWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val PREFS = "beiwang_widget_prefs"
        private const val KEY_BOARD = "board"

        const val BOARD_MEMO = 0
        const val BOARD_SHOPPING = 1
        const val BOARD_TODO = 2
        private const val BOARD_COUNT = 3
        private const val STEP_NEXT = "next"
        private const val STEP_PREV = "prev"

        // 看板编号编码进 action：只靠 extras 区分时宿主会按 Intent 等价性去重
        //（extras 不参与 filterEquals），会表现为"点第一个标签有效、后面的没反应"。
        const val ACTION_SWITCH_BOARD = "com.example.beiwang.SWITCH_BOARD"
        const val ACTION_STEP_BOARD = "com.example.beiwang.STEP_BOARD"
        const val EXTRA_BOARD = "board"

        fun componentName(context: Context): ComponentName =
            ComponentName(context, BeiwangAppWidgetProvider::class.java)

        fun getBoard(context: Context): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_BOARD, BOARD_MEMO)
                .coerceIn(BOARD_MEMO, BOARD_TODO)

        fun setBoard(context: Context, board: Int) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_BOARD, board.coerceIn(BOARD_MEMO, BOARD_TODO))
                .apply()
        }

        /** 触发所有实例重绘（同步完成、数据变更后调用）。 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(componentName(context))
            if (ids.isEmpty()) return
            val intent = Intent(context, BeiwangAppWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context))
            appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        when {
            action == null -> super.onReceive(context, intent)
            action.startsWith(ACTION_SWITCH_BOARD) -> {
                val board = intent.getIntExtra(
                    EXTRA_BOARD,
                    action.substringAfterLast('.').toIntOrNull() ?: BOARD_MEMO
                )
                setBoard(context, board)
                refresh(context)
            }
            action.startsWith(ACTION_STEP_BOARD) -> {
                // 左右箭头：循环切换看板（备忘录 → 购物清单 → 待办 → 备忘录）
                val delta = if (action.endsWith(STEP_NEXT)) 1 else -1
                setBoard(context, (getBoard(context) + delta + BOARD_COUNT) % BOARD_COUNT)
                refresh(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    /** 切看板：重建外框（更新标签高亮与 ＋ 的语义）+ 让列表重新取数。 */
    private fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(componentName(context))
        ids.forEach { id ->
            manager.updateAppWidget(id, buildViews(context))
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_container)
        val board = getBoard(context)

        views.setRemoteAdapter(
            R.id.widget_list,
            Intent(context, BeiwangWidgetService::class.java)
        )

        // 条目点击模板（必须 MUTABLE，否则 setOnClickFillInIntent 填不进参数）
        val template = PendingIntent.getActivity(
            context,
            0,
            Intent(context, WidgetActionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.widget_list, template)

        // 看板标签 + 高亮
        highlight(views, context, R.id.tab_memo, board == BOARD_MEMO)
        highlight(views, context, R.id.tab_shop, board == BOARD_SHOPPING)
        highlight(views, context, R.id.tab_todo, board == BOARD_TODO)
        views.setOnClickPendingIntent(R.id.tab_memo, boardPendingIntent(context, BOARD_MEMO))
        views.setOnClickPendingIntent(R.id.tab_shop, boardPendingIntent(context, BOARD_SHOPPING))
        views.setOnClickPendingIntent(R.id.tab_todo, boardPendingIntent(context, BOARD_TODO))

        views.setTextViewText(R.id.widget_title, "七安备忘")
        views.setInt(R.id.btn_add, "setBackgroundResource", R.drawable.widget_btn_bg)
        // ＋：打开添加弹窗（先选类型，再填内容），保存后立即同步到 App 与网页后台
        views.setOnClickPendingIntent(R.id.btn_add, addPendingIntent(context))
        // 左右箭头：循环切换看板
        views.setOnClickPendingIntent(R.id.btn_prev, stepPendingIntent(context, false))
        views.setOnClickPendingIntent(R.id.btn_next, stepPendingIntent(context, true))
        return views
    }

    /** 当前看板高亮：实心主题色 + 白字；非当前项：浅灰底 + 次要字色。 */
    private fun highlight(views: RemoteViews, context: Context, viewId: Int, active: Boolean) {
        views.setInt(
            viewId,
            "setBackgroundColor",
            if (active) context.getColor(R.color.w_accent) else context.getColor(R.color.w_tab_bg)
        )
        views.setTextColor(
            viewId,
            if (active) context.getColor(android.R.color.white) else context.getColor(R.color.w_text_secondary)
        )
    }

    private fun boardPendingIntent(context: Context, board: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            200 + board,
            Intent(context, BeiwangAppWidgetProvider::class.java).apply {
                action = "$ACTION_SWITCH_BOARD.$board"
                putExtra(EXTRA_BOARD, board)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** 左右箭头：循环切换看板。方向编进 action，避免宿主按 Intent 等价性去重。 */
    private fun stepPendingIntent(context: Context, next: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            if (next) 230 else 231,
            Intent(context, BeiwangAppWidgetProvider::class.java).apply {
                action = "$ACTION_STEP_BOARD.${if (next) STEP_NEXT else STEP_PREV}"
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** 「＋」统一打开添加弹窗：弹窗内先选类型（备忘录/购物清单/待办）再填内容。 */
    private fun addPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            1,
            Intent(context, WidgetAddActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
