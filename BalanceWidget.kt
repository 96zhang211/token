package com.whalechan.balance

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 桌面小组件：一眼看见 DeepSeek 余额。
 * API Key 存在 SharedPreferences("whale", "api_key")，在 MainActivity 里填。
 * 点一下小组件 = 立刻刷新。
 */
class BalanceWidget : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> refresh(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BalanceWidget::class.java)
            )
            ids.forEach { id -> refresh(context, manager, id) }
        }
    }

    private fun refresh(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_balance)
        views.setTextViewText(R.id.tv_title, "DeepSeek 余额")
        views.setTextViewText(R.id.tv_balance, "...")

        // 点一下立刻刷新
        val tapIntent = Intent(context, BalanceWidget::class.java)
            .setAction(ACTION_REFRESH)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

        val prefs: SharedPreferences =
            context.getSharedPreferences("whale", Context.MODE_PRIVATE)
        val key = prefs.getString("api_key", "").orEmpty()

        if (key.isBlank()) {
            views.setTextViewText(R.id.tv_balance, "还没填 Key")
            views.setTextViewText(R.id.tv_sub, "点开 App 设置一下")
            manager.updateAppWidget(widgetId, views)
            return
        }

        // 广播的生命周期撑不住网络请求，得用 goAsync 报备一下
        val pending = goAsync()
        scope.launch {
            try {
                val text = fetchBalance(key)
                views.setTextViewText(R.id.tv_balance, text.first)
                views.setTextViewText(R.id.tv_sub, text.second)
            } catch (e: Exception) {
                views.setTextViewText(R.id.tv_balance, "拉取失败")
                views.setTextViewText(R.id.tv_sub, e.message ?: e.javaClass.simpleName)
            } finally {
                manager.updateAppWidget(widgetId, views)
                pending.finish()
            }
        }
    }

    private suspend fun fetchBalance(key: String): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val conn = (URL("https://api.deepseek.com/user/balance")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            try {
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    return@withContext "HTTP $code" to body.take(60)
                }

                val root = JSONObject(body)
                val infos = root.optJSONArray("balance_infos")
                if (infos == null || infos.length() == 0) {
                    return@withContext "无数据" to "还没充值过吧"
                }
                val info = infos.getJSONObject(0)
                val symbol =
                    if (info.optString("currency").uppercase() == "CNY") "¥" else "$"
                val total = info.optString("total_balance", "?")
                val granted = info.optString("granted_balance", "0")
                val topped = info.optString("topped_up_balance", "0")

                ("$symbol$total") to ("赠 $granted ｜ 充 $topped")
            } finally {
                conn.disconnect()
            }
        }

    companion object {
        const val ACTION_REFRESH = "com.whalechan.balance.ACTION_REFRESH"
    }
}
