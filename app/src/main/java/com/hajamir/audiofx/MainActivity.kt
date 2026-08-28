package com.hajamir.audiofx

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var statusView: TextView

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            startServiceAndUpdateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        val title = TextView(this).apply {
            text = "Hajamir Audio FX"
            textSize = 22f
        }
        statusView = TextView(this).apply {
            text = "در حال راه‌اندازی سرویس…"
            textSize = 16f
            setPadding(0, 32, 0, 0)
        }
        layout.addView(title)
        layout.addView(statusView)
        setContentView(layout)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startServiceAndUpdateStatus()
        }
    }

    private fun startServiceAndUpdateStatus() {
        val intent = Intent(this, MainService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        statusView.text =
            "سرویس روشن شد ✔\n\nحالا می‌تونی این صفحه رو ببندی. از WebUI ماژول " +
            "می‌تونی پریست‌ها رو عوض کنی، سرویس در پس‌زمینه فعال می‌مونه."
    }
}
