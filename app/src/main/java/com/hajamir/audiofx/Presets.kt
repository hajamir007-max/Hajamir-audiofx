package com.hajamir.audiofx

import org.json.JSONObject

/**
 * Real numeric parameter presets, unlike v2.0 which only toggled effects
 * on/off with no values. All gains are in millibel (mB) = 1/100 dB, matching
 * the units the Android AudioEffect subclasses use natively.
 *
 * "eq_shape" is 5 relative points from low->high frequency, on a -1..1
 * scale, where 1.0 = the fixed ±6dB cap applied in EffectChainManager (see
 * eqMaxAbsMb) — NOT the device's raw max range, since that varies wildly
 * per device. So e.g. 0.33 always means "~+2dB", on every phone.
 *
 * The numbers below are picked from conventional, well-documented consumer
 * audio ranges rather than arbitrary guesses:
 *   - EQ presets: kept within +/-3dB per band. Broad research on listener
 *     preference (e.g. Harman target curve studies) finds most people
 *     prefer a mild bass-forward, slightly-bright tilt of a few dB, not a
 *     dramatic V-shape. Aggressive multi-band boosts are what most commonly
 *     causes audible distortion/clipping on phone DACs and speakers.
 *   - BassBoost / Virtualizer strength: Android's own range is 0-1000, but
 *     values above ~500 are widely reported (XDA/AudioEffect discussions)
 *     as producing boomy, one-note bass and phasey/hollow virtualization.
 *     Kept at or below 300 here.
 *   - LoudnessEnhancer target gain: kept to +1-1.5dB. This effect is a
 *     straight gain stage with no dynamics awareness, so it's the most
 *     clip-prone knob in the chain; the limiter is the safety net, not an
 *     excuse to push this harder.
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
        // Safety limiter always on, even in "no coloring" mode, in case the
        // source itself is hot.
        put("limiter", obj(false))
    }

    fun music(): JSONObject = JSONObject().apply {
        put("preset", "music")
        // Mild bass-forward/slightly-bright tilt, ~+2dB low, ~-0.3dB
        // low-mid dip to avoid boxiness, flat mid, ~+0.5dB hi-mid,
        // ~+1.5dB high. All well inside +/-3dB.
        put("equalizer", obj(true, "eq_shape" to listOf(0.33, -0.05, 0.0, 0.08, 0.25)))
        put("bassboost", obj(true, "strength" to 250))        // 0..1000, moderate
        put("virtualizer", obj(true, "strength" to 200))      // subtle widening
        put("loudness_enhancer", obj(true, "target_gain_mb" to 150)) // +1.5dB
        put("reverb", obj(false, "preset" to "none"))
        put("limiter", obj(false))
    }

    fun movie(): JSONObject = JSONObject().apply {
        put("preset", "movie")
        // Slight sub boost for impact, small presence bump for dialogue.
        put("equalizer", obj(true, "eq_shape" to listOf(0.25, 0.0, -0.05, 0.17, 0.1)))
        put("bassboost", obj(false, "strength" to 0))
        put("virtualizer", obj(true, "strength" to 300))
        put("loudness_enhancer", obj(true, "target_gain_mb" to 150))
        // Deliberately off by default: film mixes already carry their own
        // room ambience/reverb baked in, so adding more tends to blur
        // dialogue rather than help. Leave this for the rare dry/mono
        // source, turned on manually.
        put("reverb", obj(false, "preset" to "smallroom"))
        put("limiter", obj(false))
    }

    fun voice(): JSONObject = JSONObject().apply {
        put("preset", "voice")
        // Cut a bit of sub-bass rumble, modest presence bump (2-4kHz
        // region) for intelligibility, keep highs restrained to avoid
        // emphasizing sibilance. No bassboost/virtualizer: voice should
        // stay centered/mono-compatible, not widened.
        put("equalizer", obj(true, "eq_shape" to listOf(-0.15, -0.05, 0.1, 0.3, 0.12)))
        put("bassboost", obj(false, "strength" to 0))
        put("virtualizer", obj(false, "strength" to 0))
        put("loudness_enhancer", obj(true, "target_gain_mb" to 100)) // +1dB
        put("reverb", obj(false, "preset" to "none"))
        put("limiter", obj(false))
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
