package com.hajamir.audiofx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import org.json.JSONObject

/**
 * Global AudioEffect instances (session 0) are released as soon as the
 * process that created them dies. So this has to be a real, resident
 * foreground service - not a one-shot broadcast handler - or the effects
 * detach silently the moment Android kills the app in the background,
 * which is exactly the kind of "no effect" failure v2.0 had, just for a
 * different reason.
 */
class MainService : Service() {

    private val chain = EffectChainManager()

    companion object {
        private const val CHANNEL_ID = "hajamir_audiofx"
        private const val NOTIF_ID = 1
        /** Full custom JSON config (advanced / future per-band UI). */
        const val EXTRA_CONFIG_JSON = "config_json"
        /** Shortcut: one of "precise" | "music" | "movie" | "voice". */
        const val EXTRA_PRESET_KEY = "preset"
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        applyStoredConfig()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val presetKey = intent?.getStringExtra(EXTRA_PRESET_KEY)
        val json = intent?.getStringExtra(EXTRA_CONFIG_JSON)
        val config = when {
            presetKey != null -> Presets.byKey(presetKey)
            json != null -> try {
                JSONObject(json)
            } catch (e: Exception) {
                null // malformed config from the shell side; keep previous state
            }
            else -> null
        }
        if (config != null) {
            ConfigStore.save(this, config)
            chain.apply(config)
        } else if (presetKey == null && json == null) {
            applyStoredConfig()
        }
        return START_STICKY
    }

    private fun applyStoredConfig() {
        chain.apply(ConfigStore.load(this))
    }

    override fun onDestroy() {
        chain.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID, "Hajamir Audio FX", NotificationManager.IMPORTANCE_MIN
            )
            mgr.createNotificationChannel(channel)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Hajamir Audio FX")
            .setContentText("افکت‌های صدا فعال")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }
}
