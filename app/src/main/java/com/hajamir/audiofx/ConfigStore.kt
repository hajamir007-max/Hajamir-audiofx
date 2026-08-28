package com.hajamir.audiofx

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Config lives in the app's own external files dir:
 *   /sdcard/Android/data/com.hajamir.audiofx/files/config.json
 *
 * This directory needs no runtime permission for the app itself (it's the
 * app's own external storage sandbox), and is still writable by the root
 * shell running from the Magisk/KernelSU module's webroot/apply.sh via `su`.
 * That's the bridge between the WebUI and this app.
 */
object ConfigStore {
    private const val TAG = "HajamirConfigStore"
    private const val FILE_NAME = "config.json"

    fun path(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, FILE_NAME)
    }

    fun load(context: Context): JSONObject {
        val f = path(context)
        return try {
            if (f.exists()) JSONObject(f.readText()) else Presets.default()
        } catch (e: Exception) {
            Log.w(TAG, "failed to read config, falling back to default preset", e)
            Presets.default()
        }
    }

    fun save(context: Context, config: JSONObject) {
        try {
            path(context).writeText(config.toString())
        } catch (e: Exception) {
            Log.e(TAG, "failed to persist config", e)
        }
    }
}
