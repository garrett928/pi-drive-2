package ghart.space.pi_drive.shared.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads the phone's linear-acceleration sensor and exposes the calibrated
 * longitudinal component as a [StateFlow<Float?>].
 *
 * ## Calibration
 * The longitudinal axis and sign are determined by [CalibrationManager.identify] during
 * a calibration drive and persisted via [applyCalibration]. Until calibrated, the flow
 * emits `null`.
 *
 * ## Low-pass filter
 * Raw sensor readings are smoothed with an exponential moving average
 * (alpha = [LOW_PASS_ALPHA]). A lower alpha produces more smoothing.
 *
 * ## Lifecycle
 * Call [start] when the app enters the foreground (or when detection begins) and
 * [stop] on the way to the background to avoid unnecessary battery drain.
 */
class AccelerometerManager(
    private val context: Context,
) : SensorEventListener {

    companion object {
        private const val TAG = "AccelDetector"

        private const val PREFS_NAME = "accel_calibration"
        private const val KEY_AXIS = "cal_axis"
        private const val KEY_SIGN = "cal_sign"

        /**
         * Low-pass filter smoothing factor. 0.8 retains 80 % of the previous value per
         * sample, producing moderate smoothing at ~50 Hz.
         */
        const val LOW_PASS_ALPHA = 0.8f

        /**
         * Exponential moving average: `filtered = alpha * prev + (1 - alpha) * raw`.
         *
         * Exposed as `internal` for unit-testability without Android instrumentation.
         */
        internal fun lowPass(prev: Float, raw: Float, alpha: Float = LOW_PASS_ALPHA): Float =
            alpha * prev + (1f - alpha) * raw
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val prefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f

    private val _longitudinalMps2 = MutableStateFlow<Float?>(null)

    /**
     * Calibrated longitudinal acceleration in m/s².
     * Positive = forward acceleration, negative = braking.
     * Emits `null` until [start] is called and calibration has been applied.
     */
    val longitudinalMps2: StateFlow<Float?> = _longitudinalMps2.asStateFlow()

    /** Stored calibration: axis index (0=X, 1=Y, 2=Z) or -1 if uncalibrated. */
    private var calAxis: Int = prefs.getInt(KEY_AXIS, -1)

    /** Stored calibration: sign (+1 or -1) mapping sensor axis to forward direction. */
    private var calSign: Int = prefs.getInt(KEY_SIGN, 1)

    /** True when axis and sign have been calibrated (from persistence or [applyCalibration]). */
    val isCalibrated: Boolean get() = calAxis >= 0

    /**
     * Registers the linear-acceleration sensor listener.
     *
     * If [TYPE_LINEAR_ACCELERATION] is not available (e.g. emulator), logs a warning
     * and returns — [longitudinalMps2] will remain `null`.
     */
    fun start() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) ?: run {
            Log.w(TAG, "TYPE_LINEAR_ACCELERATION not available on this device")
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        Log.d(TAG, "Accelerometer registered (calibrated=$isCalibrated, axis=$calAxis, sign=$calSign)")
    }

    /** Unregisters the sensor listener to stop consuming CPU and battery. */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Applies a calibration result from [CalibrationManager] and persists it in
     * SharedPreferences so it survives app restarts.
     *
     * @param axis  Axis index of the longitudinal accelerometer axis (0=X, 1=Y, 2=Z).
     * @param sign  +1 if forward acceleration is positive on that axis, -1 if negative.
     */
    fun applyCalibration(axis: Int, sign: Int) {
        calAxis = axis
        calSign = sign
        prefs.edit()
            .putInt(KEY_AXIS, axis)
            .putInt(KEY_SIGN, sign)
            .apply()
        Log.i(TAG, "Calibration applied: axis=$axis, sign=$sign")
    }

    override fun onSensorChanged(event: SensorEvent) {
        filteredX = lowPass(filteredX, event.values[0])
        filteredY = lowPass(filteredY, event.values[1])
        filteredZ = lowPass(filteredZ, event.values[2])

        if (!isCalibrated) return

        val raw = when (calAxis) {
            0    -> filteredX
            1    -> filteredY
            2    -> filteredZ
            else -> return
        }
        _longitudinalMps2.value = raw * calSign
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
}
