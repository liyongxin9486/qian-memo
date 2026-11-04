package com.example.beiwang.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.beiwang.MainActivity
import com.example.beiwang.R

/**
 * 桌面组件写入失败的统一提醒。
 *
 * 场景：用户在桌面组件的「＋备忘 / ＋购物 / 编辑 / 勾选」里写入内容时，
 * 可能因「未登录」「数据库异常」等原因写不进去（写不进去会被下次登录清库吞掉，造成数据丢失）。
 * 此时用通知告诉用户具体原因，点击通知直接打开 App 处理；若系统禁止通知，则降级为 Toast，
 * 保证警告一定可见。
 */
object WidgetNotifier {
    private const val CHANNEL_ID = "beiwang_widget_channel"
    private const val CHANNEL_NAME = "桌面组件提醒"
    private const val CHANNEL_DESC = "桌面组件新建/编辑内容失败时的异常提醒"
    private const val NOTIFY_ID = 1001

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = CHANNEL_DESC }
                nm.createNotificationChannel(channel)
            }
        }
    }

    /**
     * 发出一条异常提醒通知。
     * @param title   标题，如「新建备忘失败」
     * @param message 具体原因，如「您当前未登录，内容不会同步且在重新登录时会被清空」
     */
    fun notify(context: Context, title: String, message: String) {
        // 系统禁止通知或创建/发送抛异常时，降级为 Toast，确保用户一定能看到警告。
        val canNotify = runCatching {
            ensureChannel(context)
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(false)

        if (canNotify) {
            runCatching {
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val pi = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                NotificationManagerCompat.from(context).notify(NOTIFY_ID, notif)
            }.onFailure {
                Toast.makeText(context, "$title：$message", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "$title：$message", Toast.LENGTH_LONG).show()
        }
    }
}
