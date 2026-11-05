package com.whalechan.balance

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 两件事：
 * 1. 把 DeepSeek 的 API Key 存进 SharedPreferences("whale", "api_key")
 * 2. 召唤 / 收回桌面上的鲸鱼娘
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvTip: TextView
    private lateinit var btnPet: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("whale", Context.MODE_PRIVATE)
        val etKey = findViewById<EditText>(R.id.etKey)
        tvTip = findViewById<TextView>(R.id.tvTip)
        val btnSave = findViewById<Button>(R.id.btnSave)
        btnPet = findViewById<Button>(R.id.btnPet)

        etKey.setText(prefs.getString("api_key", "").orEmpty())

        btnSave.setOnClickListener {
            val key = etKey.text.toString().trim()
            prefs.edit().putString("api_key", key).apply()
            if (key.isBlank()) {
                tvTip.text = "清掉了，小组件会显示还没填 Key"
            } else {
                tvTip.text = "存好啦，小组件这就刷新"
            }
            sendBroadcast(
                Intent(this, BalanceWidget::class.java)
                    .setAction(BalanceWidget.ACTION_REFRESH)
            )
        }

        btnPet.setOnClickListener { togglePet() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIF
            )
        }
    }

    override fun onResume() {
        super.onResume()
        btnPet.text = if (WhalePetService.running) "收回鲸鱼娘" else "召唤鲸鱼娘到桌面"
    }

    private fun togglePet() {
        if (WhalePetService.running) {
            startService(
                Intent(this, WhalePetService::class.java)
                    .setAction(WhalePetService.ACTION_STOP)
            )
            WhalePetService.running = false
            btnPet.text = "召唤鲸鱼娘到桌面"
            tvTip.text = "收回去啦，想她了再点一下"
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            tvTip.text = "得先允许「显示在其他应用上层」，回来再点一次就好"
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, WhalePetService::class.java)
            )
            tvTip.text = "上桌啦，可以拖着走，戳一下会说话"
        } catch (e: Exception) {
            tvTip.text = "没召唤出来：${e.message ?: e.javaClass.simpleName}"
        }
    }

    companion object {
        private const val REQ_NOTIF = 20
    }
}
