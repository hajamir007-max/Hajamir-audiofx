package com.hajamir.audiofx

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Owns the actual AudioEffect instances attached to session 0 (the global
 * output mix, i.e. every app's playback — this is what a device's built-in
 * "sound enhancer" app does too). Unlike v2.0's audio_effects.xml approach,
 * every value set here is a real parameter sent through the platform
 * AudioEffect API, not just "this effect is present in the chain".
 *
 * Creation order below is deliberate: gain-adding stages (EQ, bass, virtual,
 * loudness) come first, DynamicsProcessing (used purely as a safety limiter)
 * comes LAST so it actually catches clipping from the stages before it. This
 * fixes the ordering bug from v2.0, where the limiter ran before the loudness
 * gain and therefore protected nothing.
 */
class EffectChainManager {
    companion object {
        private const val TAG = "HajamirEffectChain"
        private const val SESSION_0 = 0 // global output mix
    }

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var presetReverb: PresetReverb? = null

    /**
     * Absolute safety cap for EQ boosts/cuts, regardless of what range the
     * device's Equalizer implementation reports. Some devices report EQ
     * ranges as wide as ±24dB — mapping a "gentle" relative shape value of
     * e.g. 0.3 onto that full range would produce a ~7dB boost, which is not
     * gentle. Capped at ±10dB (bumped from ±6dB/±8dB after feedback that
     * default presets felt too subtle on some devices) — the gentle safety
     * limiter (see applyLimiter) is what keeps this from causing clipping.
     */
    private val eqMaxAbsMb = 1000

    fun apply(config: JSONObject) {
        release()
        try {
            applyEqualizer(config.optJSONObject("equalizer"))
            applyBassBoost(config.optJSONObject("bassboost"))
            applyVirtualizer(config.optJSONObject("virtualizer"))
            applyLoudness(config.optJSONObject("loudness_enhancer"))
            applyReverb(config.optJSONObject("reverb"))
            applyLimiter(config.optJSONObject("limiter"))
        } catch (e: Exception) {
            Log.e(TAG, "apply failed, tearing down partial chain", e)
            release()
        }
    }

    private fun applyEqualizer(cfg: JSONObject?) {
        if (cfg == null || !cfg.optBoolean("enabled", false)) return
        val eq = Equalizer(0, SESSION_0)
        eq.enabled = true

        val shapeArr = cfg.optJSONArray("eq_shape")
        if (shapeArr != null) {
            val shape = DoubleArray(shapeArr.length()) { shapeArr.getDouble(it) }
            val bands = eq.numberOfBands
            val range = eq.bandLevelRange // [minMb, maxMb], device-reported
            val minMb = range[0].toInt().coerceAtLeast(-eqMaxAbsMb)
            val maxMb = range[1].toInt().coerceAtMost(eqMaxAbsMb)
            for (b in 0 until bands) {
                // map this device's band index (0..bands-1) to a position
                // 0..1 across the shape control points, linear-interpolated
                val pos = if (bands <= 1) 0.0 else b.toDouble() / (bands - 1)
                val relGain = interpolate(shape, pos) // -1..1
                val mb = (relGain * eqMaxAbsMb).roundToInt()
                eq.setBandLevel(b.toShort(), mb.coerceIn(minMb, maxMb).toShort())
            }
        }
        equalizer = eq
    }

    private fun interpolate(points: DoubleArray, pos: Double): Double {
        if (points.isEmpty()) return 0.0
        if (points.size == 1) return points[0]
        val scaled = pos * (points.size - 1)
        val lo = scaled.toInt().coerceIn(0, points.size - 1)
        val hi = (lo + 1).coerceAtMost(points.size - 1)
        val frac = scaled - lo
        return points[lo] + (points[hi] - points[lo]) * frac
    }

    private fun applyBassBoost(cfg: JSONObject?) {
        if (cfg == null || !cfg.optBoolean("enabled", false)) return
        val bb = BassBoost(0, SESSION_0)
        if (bb.strengthSupported) {
            bb.setStrength(cfg.optInt("strength", 0).coerceIn(0, 1000).toShort())
        }
        bb.enabled = true
        bassBoost = bb
    }

    private fun applyVirtualizer(cfg: JSONObject?) {
        if (cfg == null || !cfg.optBoolean("enabled", false)) return
        val vr = Virtualizer(0, SESSION_0)
        if (vr.strengthSupported) {
            vr.setStrength(cfg.optInt("strength", 0).coerceIn(0, 1000).toShort())
        }
        vr.enabled = true
        virtualizer = vr
    }

    private fun applyLoudness(cfg: JSONObject?) {
        if (cfg == null || !cfg.optBoolean("enabled", false)) return
        val le = LoudnessEnhancer(SESSION_0)
        le.setTargetGain(cfg.optInt("target_gain_mb", 0))
        le.enabled = true
        loudnessEnhancer = le
    }

    private fun applyReverb(cfg: JSONObject?) {
        if (cfg == null || !cfg.optBoolean("enabled", false)) return
        val preset = when (cfg.optString("preset", "none")) {
            "smallroom" -> PresetReverb.PRESET_SMALLROOM
            "mediumroom" -> PresetReverb.PRESET_MEDIUMROOM
            "largeroom" -> PresetReverb.PRESET_LARGEROOM
            "mediumhall" -> PresetReverb.PRESET_MEDIUMHALL
            "largehall" -> PresetReverb.PRESET_LARGEHALL
            "plate" -> PresetReverb.PRESET_PLATE
            else -> PresetReverb.PRESET_NONE
        }
        if (preset == PresetReverb.PRESET_NONE) return
        val rv = PresetReverb(0, SESSION_0)
        rv.preset = preset.toShort()
        rv.enabled = true
        presetReverb = rv
    }

    /**
     * DynamicsProcessing used purely as a brick-wall-ish safety limiter, run
     * LAST in the chain so it actually sees the post-gain signal. This is
     * the fix for the v2.0 ordering bug.
     */
    private fun applyLimiter(cfg: JSONObject?) {
        if (cfg == null || !cfg.optBoolean("enabled", true)) return
        val channelCount = 2
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            channelCount,
            false, 0,  // preEQ off
            false, 0,  // MBC off
            false, 0,  // postEQ off
            true       // limiter on
        )
        val config = builder.build()
        for (ch in 0 until channelCount) {
            val limiter = DynamicsProcessing.Limiter(
                /* inUse = */ true,
                /* enabled = */ true,
                /* linkGroup = */ 0,
                /* attackTime = */ 5f,
                /* releaseTime = */ 150f,
                /* ratio = */ 4f,   // gentle — the earlier 20:1 @ -1dB caused audible
                                    // pumping/quietness. Slightly firmer than 3:1 since
                                    // v1.1 pushed default gains higher.
                /* threshold = */ -0.5f, // dB, barely below full scale
                /* postGain = */ 0f
            )
            config.setLimiterByChannelIndex(ch, limiter)
        }
        val dp = DynamicsProcessing(0, SESSION_0, config)
        dp.enabled = true
        dynamicsProcessing = dp
    }

    fun release() {
        equalizer?.release(); equalizer = null
        bassBoost?.release(); bassBoost = null
        virtualizer?.release(); virtualizer = null
        loudnessEnhancer?.release(); loudnessEnhancer = null
        dynamicsProcessing?.release(); dynamicsProcessing = null
        presetReverb?.release(); presetReverb = null
    }
}
