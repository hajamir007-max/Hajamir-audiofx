package com.hajamir.audiofx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Triggered from the root shell (webroot/apply.sh), either with a preset key:
 *   am broadcast -a com.hajamir.audiofx.ACTION_APPLY \
 *     -n com.hajamir.audiofx/.ApplyReceiver --es preset music
 *
 * or (future / advanced) a full custom config:
 *   am broadcast -a com.hajamir.audiofx.ACTION_APPLY \
 *     -n com.hajamir.audiofx/.ApplyReceiver --es config_json '<json>'
 */
class ApplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val preset = intent.getStringExtra(MainService.EXTRA_PRESET_KEY)
        val json = intent.getStringExtra(MainService.EXTRA_CONFIG_JSON)
        if (preset == null && json == null) return

        val svc = Intent(context, MainService::class.java)
        if (preset != null) svc.putExtra(MainService.EXTRA_PRESET_KEY, preset)
        if (json != null) svc.putExtra(MainService.EXTRA_CONFIG_JSON, json)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
