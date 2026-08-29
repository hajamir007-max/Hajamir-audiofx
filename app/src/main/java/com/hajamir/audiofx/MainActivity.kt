package com.hajamir.audiofx

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * This screen exists for two reasons:
 * 1. Android 12+ refuses to start a foreground service purely from a
 *    background broadcast (see MainService's doc comment) - opening this
 *    Activity gives the OS a legitimate user-initiated moment to start it.
 * 2. Many OEM Android skins (MIUI, ColorOS, FuntouchOS, EMUI, etc.)
 *    aggressively kill background/foreground services regardless of the
 *    ongoing notification, unless the app is exempted from battery
 *    optimization. Requesting that exemption here is the real fix for
 *    "effect stopped working after a while" reports - not something we can
 *    code around inside the service itself.
 */
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
            "برنامه فعال شد ✔\n\n" +
            "برای استفاده فقط این برنامه رو ببند، از طریق WebUI ماژول Audiofx " +
            "از کیفیت صدا لذت ببر👍"

        requestBatteryOptimizationExemption()
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Some OEM skins block this intent outright; nothing more we
            // can do from code - user has to whitelist manually in their
            // battery settings.
        }
    }
}
