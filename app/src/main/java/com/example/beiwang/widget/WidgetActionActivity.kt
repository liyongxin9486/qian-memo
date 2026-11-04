package com.example.beiwang.widget

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.beiwang.data.AuthManager
import com.example.beiwang.data.database.AppDatabase
import com.example.beiwang.data.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 透明桥接 Activity —— 解决桌面组件 "强制停止(Force-stop)后点不动" 的问题。
 *
 * RemoteViews 组件自身只能挂 PendingIntent，而这个 PendingIntent 若指向广播/Service，
 * 应用被强制停止进入 stopped 状态后会被系统拦截。"启动 Activity 的 PendingIntent" 不受
 * stopped 状态限制——系统桌面持有的 PendingIntent 在 Force-stop 后依然能 startActivity
 * 拉起本进程，因此所有组件交互（勾选购物/待办、打开备忘、新建）都经此 Activity：
 *  写 Room -> 触发同步 -> 重绘组件 -> finish()。
 *
 * 本 Activity 完全透明、无 UI、不进最近任务、不驻留返回栈(taskAffinity="")，对用户体验无感。
 *
 * 参数就是普通 Intent extras（RemoteViews 的 setOnClickFillInIntent / PendingIntent 直接带），
 * key 见下方 EXTRA_* 常量。
 */
class WidgetActionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ACTION = "widget_action"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_MEMO_ID = "memo_id"
        const val EXTRA_PAGE = "page"

        const val ACTION_TOGGLE_SHOPPING = "toggle_shopping"
        const val ACTION_TOGGLE_TODO = "toggle_todo"
        const val ACTION_OPEN_MEMO = "open_memo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent()
    }

    private fun handleIntent() {
        val action = intent?.getStringExtra(EXTRA_ACTION)
        when (action) {
            ACTION_TOGGLE_SHOPPING -> handleToggleShopping(intent?.getStringExtra(EXTRA_ITEM_ID))
            ACTION_TOGGLE_TODO -> handleToggleTodo(intent?.getStringExtra(EXTRA_ITEM_ID))
            ACTION_OPEN_MEMO -> handleOpenMemo(intent?.getStringExtra(EXTRA_MEMO_ID))
            else -> finish()
        }
    }

    private fun handleToggleShopping(itemId: String?) {
        if (itemId.isNullOrEmpty()) return finish()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(this@WidgetActionActivity)
                    val item = db.shoppingItemDao().getItemById(itemId) ?: return@withContext false
                    db.shoppingItemDao().updateCompletionStatus(itemId, !item.isCompleted)
                    true
                } catch (e: Exception) {
                    Log.e("WidgetActionActivity", "toggle shopping failed", e)
                    false
                }
            }
            if (ok) {
                if (AuthManager.isLoggedIn) {
                    SyncManager.requestSync(this@WidgetActionActivity)
                } else {
                    WidgetNotifier.notify(
                        this@WidgetActionActivity,
                        "勾选未同步",
                        "您当前未登录，勾选状态仅保存在本机、不会同步到云端，且重新登录时会被清空。请先打开App登录。"
                    )
                }
                WidgetUpdater.refresh(this@WidgetActionActivity)
                keepAliveAndFinish()
            } else {
                finish()
            }
        }
    }

    private fun handleToggleTodo(todoId: String?) {
        if (todoId.isNullOrEmpty()) return finish()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(this@WidgetActionActivity)
                    val todo = db.todoDao().getTodoById(todoId) ?: return@withContext false
                    db.todoDao().updateCompletionStatus(todoId, !todo.isCompleted)
                    true
                } catch (e: Exception) {
                    Log.e("WidgetActionActivity", "toggle todo failed", e)
                    false
                }
            }
            if (ok) {
                if (AuthManager.isLoggedIn) {
                    SyncManager.requestSync(this@WidgetActionActivity)
                } else {
                    WidgetNotifier.notify(
                        this@WidgetActionActivity,
                        "勾选未同步",
                        "您当前未登录，勾选状态仅保存在本机、不会同步到云端，且重新登录时会被清空。请先打开App登录。"
                    )
                }
                WidgetUpdater.refresh(this@WidgetActionActivity)
                keepAliveAndFinish()
            } else {
                finish()
            }
        }
    }

    /**
     * 查看备忘：直接在桌面弹出备忘录弹窗（EditMemoActivity，对话框主题、独立浮层），
     * 不再跳进 App 主界面——用户在桌面就能看到完整标题与正文，需要改再点「编辑」。
     */
    private fun handleOpenMemo(memoId: String?) {
        if (memoId.isNullOrEmpty()) return finish()
        startActivity(
            Intent(this, EditMemoActivity::class.java).apply {
                putExtra(EditMemoActivity.KEY_MEMO_ID_GLANCE, memoId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        finish()
    }



    /** 保活前台约 1.5s：让组件重绘与后台同步在进程存活期内完成，避免刚 finish 就被回收。 */
    private fun keepAliveAndFinish() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish() }, 1500)
    }
}
