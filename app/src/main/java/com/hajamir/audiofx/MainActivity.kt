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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * This screen exists for one reason: Android 12+ refuses to start a
 * foreground service purely from a background broadcast (see MainService's
 * doc comment). Opening this Activity gives the OS a legitimate
 * user-initiated moment to start it. After that, the running service can be
 * reconfigured freely via ApplyReceiver's broadcasts from the Magisk/KernelSU
 * module's WebUI - no need to reopen this screen for every preset change.
 */
class MainActivity : ComponentActivity() {

    private lateinit var statusView: TextView

    private val notifPermLauncher = registerForActivityResultContracts()

    private fun registerForActivityResultContracts() =
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
            "برنامه فعال شد ✔\n\n" +
            "برای استفاده فقط این برنامه رو ببند، از طریق WebUI ماژول Audiofx " +
            "از کیفیت صدا لذت ببر👍"
    }
}
