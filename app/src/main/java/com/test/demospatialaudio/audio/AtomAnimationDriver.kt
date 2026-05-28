package com.test.demospatialaudio.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class AtomAnimationDriver(private val engine: SpatialAudioEngine) {

    data class LivePosition(val x: Double, val y: Double, val z: Double)

    private data class Anchor(
        val anchorX: Double,
        val anchorY: Double,
        val anchorZ: Double,
        val mode: Int,
        val speed: Double,
        val depth: Double
    )

    private data class LastSample(
        val position: LivePosition,
        val time: Double
    )

    var livePositions: Map<String, LivePosition> = emptyMap()
        private set
    var atomLevels: Map<String, Float> = emptyMap()
        private set
    var history: Map<String, List<LivePosition>> = emptyMap()
        private set

    private val anchors = mutableMapOf<String, Anchor>()
    private val lastSamples = mutableMapOf<String, LastSample>()
    private val historyDeque = mutableMapOf<String, ArrayDeque<LivePosition>>()
    private var startTime = System.nanoTime()
    private var tickJob: Job? = null
    private val dopplerScale = 0.03f
    private val maxHistory = 12

    companion object {
        const val MODE_NONE = 0
        const val MODE_ORBIT = 1
        const val MODE_PULSE = 2
        const val MODE_DRIFT = 3
    }

    fun startTicker(scope: CoroutineScope) {
        startTime = System.nanoTime()
        tickJob?.cancel()
        tickJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                tick()
                delay(33)
            }
        }
    }

    fun stopTicker() {
        tickJob?.cancel()
        tickJob = null
    }

    fun register(
        id: String,
        anchorX: Double, anchorY: Double, anchorZ: Double,
        mode: Int, speed: Double, depth: Double
    ) {
        if (mode == MODE_NONE) {
            anchors.remove(id)
            livePositions = livePositions - id
            lastSamples.remove(id)
            historyDeque.remove(id)
            history = history - id
            engine.setAtomRate(id, 1f)
            return
        }
        anchors[id] = Anchor(anchorX, anchorY, anchorZ, mode, speed, depth)
    }

    fun unregister(id: String) {
        anchors.remove(id)
        livePositions = livePositions - id
        lastSamples.remove(id)
        atomLevels = atomLevels - id
        historyDeque.remove(id)
        history = history - id
    }

    fun clear() {
        anchors.clear()
        livePositions = emptyMap()
        lastSamples.clear()
        atomLevels = emptyMap()
        historyDeque.clear()
        history = emptyMap()
    }

    private fun tick() {
        val now = (System.nanoTime() - startTime) / 1_000_000_000.0
        val newPositions = HashMap(livePositions)
        val newHistory = HashMap(history)

        for ((id, anchor) in anchors) {
            val position = computePosition(anchor, now)
            newPositions[id] = position
            val rate = computeDopplerRate(id, position, now)
            lastSamples[id] = LastSample(position, now)

            engine.updateAtomPosition(id, position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
            engine.setAtomRate(id, rate)

            val deque = historyDeque.getOrPut(id) { ArrayDeque() }
            deque.addLast(position)
            while (deque.size > maxHistory) deque.removeFirst()
            newHistory[id] = deque.toList()
        }

        livePositions = newPositions
        history = newHistory
        atomLevels = engine.levels.snapshot()
    }

    private fun computePosition(anchor: Anchor, elapsed: Double): LivePosition {
        val speed = max(0.0, anchor.speed)
        val depth = max(0.0, min(1.0, anchor.depth))
        val planarRadius = sqrt(anchor.anchorX * anchor.anchorX + anchor.anchorY * anchor.anchorY)
        val baseAngle = atan2(anchor.anchorY, anchor.anchorX)

        return when (anchor.mode) {
            MODE_ORBIT -> {
                val angle = baseAngle + elapsed * speed * 2.0 * Math.PI
                val r = max(planarRadius, 0.05)
                LivePosition(cos(angle) * r, sin(angle) * r, anchor.anchorZ)
            }
            MODE_PULSE -> {
                val phase = sin(elapsed * speed * 2.0 * Math.PI)
                val baseR = max(planarRadius, 0.15)
                val r = clampPlanar(baseR + phase * depth)
                LivePosition(cos(baseAngle) * r, sin(baseAngle) * r, anchor.anchorZ)
            }
            MODE_DRIFT -> {
                val t = elapsed * speed
                val rWobble = (sin(t * 0.71 + baseAngle) + 0.6 * sin(t * 1.37 + baseAngle * 2.1)) / 1.6
                val aWobble = (sin(t * 0.53 + baseAngle * 1.7) + 0.7 * cos(t * 1.13)) / 1.7
                val baseR = max(planarRadius, 0.2)
                val r = clampPlanar(baseR + rWobble * depth * 0.5)
                val angle = baseAngle + aWobble * depth * 0.9
                LivePosition(cos(angle) * r, sin(angle) * r, anchor.anchorZ)
            }
            else -> LivePosition(anchor.anchorX, anchor.anchorY, anchor.anchorZ)
        }
    }

    private fun computeDopplerRate(id: String, position: LivePosition, time: Double): Float {
        val prev = lastSamples[id] ?: return 1f
        val dt = time - prev.time
        if (dt < 0.0001) return 1f

        val dx = position.x - prev.position.x
        val dy = position.y - prev.position.y
        val dz = position.z - prev.position.z

        val r = sqrt(position.x * position.x + position.y * position.y + position.z * position.z)
        if (r < 0.01) return 1f

        val radialSpeed = (dx * position.x + dy * position.y + dz * position.z) / (r * dt)
        val rate = 1f - radialSpeed.toFloat() * dopplerScale
        return rate.coerceIn(0.7f, 1.4f)
    }

    private fun clampPlanar(value: Double): Double = max(0.05, min(1.0, value))
}
