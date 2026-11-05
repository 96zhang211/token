package com.whalechan.balance

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * 桌宠服务：把 WhalePetView 挂成一个透明悬浮窗，可以拖着到处跑。
 * 用前台服务跑，不然切个 App 就被系统回收了。
 */
class WhalePetService : Service() {

    private var windowManager: WindowManager? = null
    private var petView: WhalePetView? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        startAsForeground()
        attachPet()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        petView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                // 已经没了就算了
            }
        }
        petView = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "桌宠运行中",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "让鲸鱼娘一直待在屏幕上" }
            manager.createNotificationChannel(channel)
        }

        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("鲸鱼娘在屏幕上躺着")
            .setContentText("点一下回 App 收回去")
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    private fun attachPet() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val size = (SIZE_DP * resources.displayMetrics.density).toInt()
        val view = WhalePetView(this)

        val layoutParams = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - (16 * resources.displayMetrics.density).toInt()
            y = (resources.displayMetrics.heightPixels * 0.55f).toInt()
        }

        view.setOnTouchListener(makeDragListener(layoutParams))

        try {
            wm.addView(view, layoutParams)
        } catch (e: Exception) {
            // 没拿到「显示在其他应用上层」权限会走到这
            stopSelf()
            return
        }

        petView = view
        params = layoutParams
    }

    private fun makeDragListener(layoutParams: WindowManager.LayoutParams) =
        View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    moved = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    if (moved) {
                        layoutParams.x = (startX + dx).toInt()
                        layoutParams.y = (startY + dy).toInt()
                        val wm = windowManager
                        val v = petView
                        if (wm != null && v != null) {
                            try {
                                wm.updateViewLayout(v, layoutParams)
                            } catch (e: Exception) {
                                // 视图已经没了，忽略
                            }
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) petView?.poke()
                }
            }
            true
        }

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false

    private val touchSlop: Int by lazy {
        ViewConfiguration.get(this).scaledTouchSlop
    }

    companion object {
        const val ACTION_STOP = "com.whalechan.balance.STOP_PET"
        private const val CHANNEL_ID = "whale_pet"
        private const val NOTIF_ID = 1001
        private const val SIZE_DP = 150

        /** 桌宠是不是正在屏幕上，MainActivity 靠它切按钮文案 */
        @Volatile
        var running: Boolean = false
    }
}
