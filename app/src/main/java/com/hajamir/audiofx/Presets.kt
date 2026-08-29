package com.hajamir.audiofx

import org.json.JSONObject

/**
 * Real numeric parameter presets, unlike v2.0 which only toggled effects
 * on/off with no values. All gains are in millibel (mB) = 1/100 dB, matching
 * the units the Android AudioEffect subclasses use natively.
 *
 * "eq_shape" is 5 relative points from low->high frequency, on a -1..1
 * scale, where 1.0 = the fixed +/-10dB cap applied in EffectChainManager
 * (see eqMaxAbsMb) - NOT the device's raw max range, since that varies
 * wildly per device.
 *
 * v1.1 update: pushed EQ/bassboost/virtualizer intensity noticeably higher
 * after feedback that the effect was too subtle on some devices, and
 * re-enabled the safety limiter with gentle-but-real settings (see
 * EffectChainManager.applyLimiter) to catch clipping from the stronger
 * gains. This is intentionally on the aggressive side - if it sounds bad
 * on a given device (distortion, pumping), pull the numbers back down
 * rather than assuming it's a bug.
 */
object Presets {

    fun default(): JSONObject = music()

    fun byKey(key: String): JSONObject = when (key) {
        "precise" -> precise()
        "music" -> music()
        "movie" -> movie()
        "voice" -> voice()
        else -> music()
    }

    fun precise(): JSONObject = JSONObject().apply {
        put("preset", "precise")
        put("equalizer", obj(true, "eq_shape" to listOf(0.0, 0.0, 0.0, 0.0, 0.0)))
        put("bassboost", obj(false, "strength" to 0))
        put("virtualizer", obj(false, "strength" to 0))
        put("loudness_enhancer", obj(false, "target_gain_mb" to 0))
        put("reverb", obj(false, "preset" to "none"))
        put("limiter", obj(true))
    }

    fun music(): JSONObject = JSONObject().apply {
        put("preset", "music")
        put("equalizer", obj(true, "eq_shape" to listOf(0.65, -0.1, 0.0, 0.18, 0.5)))
        put("bassboost", obj(true, "strength" to 550))
        put("virtualizer", obj(true, "strength" to 450))
        put("loudness_enhancer", obj(true, "target_gain_mb" to 700))
        put("reverb", obj(false, "preset" to "none"))
        put("limiter", obj(true))
    }

    fun movie(): JSONObject = JSONObject().apply {
        put("preset", "movie")
        put("equalizer", obj(true, "eq_shape" to listOf(0.5, 0.0, -0.1, 0.32, 0.2)))
        put("bassboost", obj(false, "strength" to 0))
        put("virtualizer", obj(true, "strength" to 550))
        put("loudness_enhancer", obj(true, "target_gain_mb" to 500))
        put("reverb", obj(false, "preset" to "smallroom"))
        put("limiter", obj(true))
    }

    fun voice(): JSONObject = JSONObject().apply {
        put("preset", "voice")
        put("equalizer", obj(true, "eq_shape" to listOf(-0.3, -0.1, 0.2, 0.55, 0.22)))
        put("bassboost", obj(false, "strength" to 0))
        put("virtualizer", obj(false, "strength" to 0))
        put("loudness_enhancer", obj(true, "target_gain_mb" to 250))
        put("reverb", obj(false, "preset" to "none"))
        put("limiter", obj(true))
    }

    private fun obj(enabled: Boolean, vararg extra: Pair<String, Any>): JSONObject {
        val o = JSONObject()
        o.put("enabled", enabled)
        for ((k, v) in extra) {
            when (v) {
                is List<*> -> {
                    val arr = org.json.JSONArray()
                    v.forEach { arr.put(it) }
                    o.put(k, arr)
                }
                else -> o.put(k, v)
            }
        }
        return o
    }
}
