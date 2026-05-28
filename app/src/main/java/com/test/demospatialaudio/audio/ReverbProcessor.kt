package com.test.demospatialaudio.audio

import com.test.demospatialaudio.domain.model.ReverbPreset

class ReverbProcessor {

    data class Config(
        val size: Float,
        val damping: Float,
        val width: Float
    )

    private val combDelays = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val allpassDelays = intArrayOf(225, 556, 441, 341)
    private val stereoSpread = 23

    private val combL = combDelays.map { FloatArray(it) }
    private val combR = combDelays.map { FloatArray(it + stereoSpread) }
    private val combLIdx = IntArray(combDelays.size)
    private val combRIdx = IntArray(combDelays.size)
    private val combLLp = FloatArray(combDelays.size)
    private val combRLp = FloatArray(combDelays.size)

    private val apL = allpassDelays.map { FloatArray(it) }
    private val apR = allpassDelays.map { FloatArray(it + stereoSpread) }
    private val apLIdx = IntArray(allpassDelays.size)
    private val apRIdx = IntArray(allpassDelays.size)

    @Volatile private var feedback = 0.84f
    @Volatile private var damp = 0.2f
    @Volatile private var levelGain = 0.4f

    fun configure(size: Float, damping: Float, levelDb: Float) {
        val s = size.coerceIn(0f, 1f)
        feedback = 0.7f + s * 0.28f
        damp = damping.coerceIn(0f, 1f)
        levelGain = dbToGain(levelDb.coerceIn(-60f, 6f))
    }

    fun reset() {
        combL.forEach { it.fill(0f) }
        combR.forEach { it.fill(0f) }
        apL.forEach { it.fill(0f) }
        apR.forEach { it.fill(0f) }
        combLLp.fill(0f)
        combRLp.fill(0f)
    }

    fun processBlock(inL: FloatArray, inR: FloatArray, outL: FloatArray, outR: FloatArray, length: Int) {
        for (n in 0 until length) {
            val xL = inL[n]
            val xR = inR[n]

            var sL = 0f
            var sR = 0f
            for (i in combDelays.indices) {
                val bL = combL[i]
                val iL = combLIdx[i]
                val rL = bL[iL]
                combLLp[i] = rL * (1f - damp) + combLLp[i] * damp
                bL[iL] = xL + combLLp[i] * feedback
                combLIdx[i] = (iL + 1) % bL.size
                sL += rL

                val bR = combR[i]
                val iR = combRIdx[i]
                val rR = bR[iR]
                combRLp[i] = rR * (1f - damp) + combRLp[i] * damp
                bR[iR] = xR + combRLp[i] * feedback
                combRIdx[i] = (iR + 1) % bR.size
                sR += rR
            }
            sL /= combDelays.size.toFloat()
            sR /= combDelays.size.toFloat()

            for (i in allpassDelays.indices) {
                val bL = apL[i]
                val iL = apLIdx[i]
                val rL = bL[iL]
                bL[iL] = sL + rL * 0.5f
                sL = rL - sL
                apLIdx[i] = (iL + 1) % bL.size

                val bR = apR[i]
                val iR = apRIdx[i]
                val rR = bR[iR]
                bR[iR] = sR + rR * 0.5f
                sR = rR - sR
                apRIdx[i] = (iR + 1) % bR.size
            }

            outL[n] = sL * levelGain
            outR[n] = sR * levelGain
        }
    }

    companion object {
        fun configFor(preset: ReverbPreset): Config = when (preset) {
            ReverbPreset.SMALL_ROOM -> Config(0.20f, 0.55f, 1.0f)
            ReverbPreset.MEDIUM_ROOM -> Config(0.40f, 0.45f, 1.0f)
            ReverbPreset.LARGE_ROOM -> Config(0.55f, 0.40f, 1.0f)
            ReverbPreset.MEDIUM_HALL -> Config(0.65f, 0.30f, 1.0f)
            ReverbPreset.LARGE_HALL -> Config(0.80f, 0.20f, 1.0f)
            ReverbPreset.PLATE -> Config(0.50f, 0.05f, 1.0f)
            ReverbPreset.MEDIUM_CHAMBER -> Config(0.55f, 0.35f, 1.0f)
            ReverbPreset.LARGE_CHAMBER -> Config(0.70f, 0.30f, 1.0f)
            ReverbPreset.CATHEDRAL -> Config(0.95f, 0.10f, 1.0f)
        }

        private fun dbToGain(db: Float): Float {
            return Math.pow(10.0, db.toDouble() / 20.0).toFloat()
        }
    }
}
