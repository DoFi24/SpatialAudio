package com.test.demospatialaudio.audio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.atan2

class HeadTrackingService(
    private val context: Context,
    private val engine: SpatialAudioEngine
) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    var isTracking = false
        private set
    private var referenceYaw: Float? = null

    fun start() {
        if (isTracking) return
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationSensor == null) {
            Log.i(TAG, "Rotation sensor not available")
            return
        }

        sensorManager?.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        isTracking = true
        referenceYaw = null
    }

    fun stop() {
        if (!isTracking) return
        sensorManager?.unregisterListener(this)
        isTracking = false
        referenceYaw = null
        engine.updateListenerOrientation(0f)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        val yawRad = orientation[0]
        val yawDeg = Math.toDegrees(yawRad.toDouble()).toFloat()

        if (referenceYaw == null) {
            referenceYaw = yawDeg
        }

        val relativeYaw = normalizeYaw(yawDeg - (referenceYaw ?: 0f))
        engine.updateListenerOrientation(relativeYaw)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun normalizeYaw(value: Float): Float {
        var v = value % 360f
        if (v > 180f) v -= 360f
        if (v < -180f) v += 360f
        return v
    }

    companion object {
        private const val TAG = "HeadTrackingService"
    }
}
