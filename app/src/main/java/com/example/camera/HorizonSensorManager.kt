package com.example.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

data class HorizonState(
    val rollDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val isLevel: Boolean = true,
    val isSteady: Boolean = true,
    val deviceRotationDeg: Int = 0,          // 0, 90, 180, 270 snapped with hysteresis
    val targetSurfaceRotation: Int = Surface.ROTATION_0
)

class HorizonSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _horizonState = MutableStateFlow(HorizonState())
    val horizonState: StateFlow<HorizonState> = _horizonState.asStateFlow()

    private var smoothRoll = 0f
    private var smoothPitch = 0f
    private var lastMag = 9.81f
    private var currentSnapDeg = 0

    fun start() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        val rawRoll = Math.toDegrees(atan2(ax.toDouble(), ay.toDouble())).toFloat()
        val rawPitch = Math.toDegrees(atan2(az.toDouble(), sqrt((ax * ax + ay * ay).toDouble()))).toFloat()

        smoothRoll = smoothRoll * 0.82f + rawRoll * 0.18f
        smoothPitch = smoothPitch * 0.82f + rawPitch * 0.18f

        val mag = sqrt(ax * ax + ay * ay + az * az)
        val deltaMag = abs(mag - lastMag)
        lastMag = mag

        val normalizedRoll = ((smoothRoll + 180f) % 360f) - 180f

        // Orientation snapping with 15-degree hysteresis when phone isn't lying completely flat
        if (abs(smoothPitch) < 68f) {
            val absRoll = abs(normalizedRoll)
            currentSnapDeg = when (currentSnapDeg) {
                0 -> when {
                    normalizedRoll in 60f..125f -> 90
                    normalizedRoll in -125f..-60f -> 270
                    absRoll > 140f -> 180
                    else -> 0
                }
                90 -> when {
                    absRoll < 45f -> 0
                    absRoll > 140f -> 180
                    normalizedRoll < -60f -> 270
                    else -> 90
                }
                270 -> when {
                    absRoll < 45f -> 0
                    absRoll > 140f -> 180
                    normalizedRoll > 60f -> 90
                    else -> 270
                }
                180 -> when {
                    normalizedRoll in 45f..125f -> 90
                    normalizedRoll in -125f..-45f -> 270
                    absRoll < 50f -> 0
                    else -> 180
                }
                else -> 0
            }
        }

        // Relative horizon roll inside the current portrait/landscape orientation frame
        val relativeRoll = when (currentSnapDeg) {
            90 -> normalizedRoll - 90f
            270 -> normalizedRoll + 90f
            180 -> ((normalizedRoll + 360f) % 360f) - 180f
            else -> normalizedRoll
        }

        val surfaceRot = when (currentSnapDeg) {
            90 -> Surface.ROTATION_270
            270 -> Surface.ROTATION_90
            180 -> Surface.ROTATION_180
            else -> Surface.ROTATION_0
        }

        _horizonState.value = HorizonState(
            rollDegrees = relativeRoll,
            pitchDegrees = smoothPitch,
            isLevel = abs(relativeRoll) < 1.8f,
            isSteady = deltaMag < 0.35f,
            deviceRotationDeg = currentSnapDeg,
            targetSurfaceRotation = surfaceRot
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
