package com.telemetryoverlay.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Running cadence (steps per minute, both feet combined) derived from the
 * phone's hardware step-detector. Steps are bucketed into a rolling 10-second
 * window and scaled to spm so the number responds quickly at the start of a
 * run instead of averaging over the whole activity.
 */
class CadenceTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val windowMillis = 10_000L
    private val stepTimestamps = ArrayDeque<Long>()

    var onCadenceUpdate: ((Int) -> Unit)? = null

    fun start() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        stepTimestamps.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return
        val now = System.currentTimeMillis()
        stepTimestamps.addLast(now)
        while (stepTimestamps.isNotEmpty() && now - stepTimestamps.first() > windowMillis) {
            stepTimestamps.removeFirst()
        }
        val stepsPerMinute = (stepTimestamps.size * (60_000f / windowMillis)).toInt()
        onCadenceUpdate?.invoke(stepsPerMinute)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun isAvailable(): Boolean = stepSensor != null
}
