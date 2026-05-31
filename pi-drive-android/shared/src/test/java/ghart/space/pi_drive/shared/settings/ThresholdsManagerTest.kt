package ghart.space.pi_drive.shared.settings

import android.content.SharedPreferences
import ghart.space.pi_drive.shared.detection.DetectionConfig
import ghart.space.pi_drive.shared.detection.HealthMonitorConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.detection.AccelerationDetector
import ghart.space.pi_drive.shared.data.model.DrivingEvent
import java.time.Instant

/**
 * Unit tests for [ThresholdsManager].
 *
 * Uses [FakeSharedPreferences] to avoid Android/Robolectric dependency.
 * Also includes an integration test verifying that detector config changes flow through
 * to [AccelerationDetector] in real time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThresholdsManagerTest {

    // ── Fake SharedPreferences ────────────────────────────────────────────────

    private class FakeSharedPreferences : SharedPreferences {
        private val store = mutableMapOf<String, Any?>()

        inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearPending = false

            override fun putString(k: String, v: String?) = apply { pending[k] = v }
            override fun putStringSet(k: String, v: Set<String>?) = apply { pending[k] = v }
            override fun putInt(k: String, v: Int) = apply { pending[k] = v }
            override fun putLong(k: String, v: Long) = apply { pending[k] = v }
            override fun putFloat(k: String, v: Float) = apply { pending[k] = v }
            override fun putBoolean(k: String, v: Boolean) = apply { pending[k] = v }
            override fun remove(k: String) = apply { pending[k] = null }
            override fun clear() = apply { clearPending = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearPending) store.clear()
                pending.forEach { (k, v) -> if (v == null) store.remove(k) else store[k] = v }
            }
        }

        override fun getAll(): Map<String, *> = store.toMap()
        override fun getString(k: String, def: String?): String? = (store[k] as? String) ?: def
        override fun getStringSet(k: String, def: Set<String>?): Set<String>? =
            (store[k] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: def
        override fun getInt(k: String, def: Int): Int = (store[k] as? Int) ?: def
        override fun getLong(k: String, def: Long): Long = (store[k] as? Long) ?: def
        override fun getFloat(k: String, def: Float): Float = (store[k] as? Float) ?: def
        override fun getBoolean(k: String, def: Boolean): Boolean = (store[k] as? Boolean) ?: def
        override fun contains(k: String): Boolean = store.containsKey(k)
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
    }

    private fun makeManager(prefs: SharedPreferences = FakeSharedPreferences()) =
        ThresholdsManager(prefs)

    // ── Default values ────────────────────────────────────────────────────────

    @Test
    fun `default detection config matches DetectionConfig defaults`() {
        val manager = makeManager()
        assertEquals(DetectionConfig(), manager.detectionConfig.value)
    }

    @Test
    fun `default health monitor config matches HealthMonitorConfig defaults`() {
        val manager = makeManager()
        assertEquals(HealthMonitorConfig(), manager.healthMonitorConfig.value)
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Test
    fun `updateDetectionConfig persists and emits new value`() {
        val manager = makeManager()
        val updated = DetectionConfig(accelHardBrakeThreshold = 4.0f)
        manager.updateDetectionConfig(updated)

        assertEquals(4.0f, manager.detectionConfig.value.accelHardBrakeThreshold)
    }

    @Test
    fun `updateHealthMonitorConfig persists and emits new value`() {
        val manager = makeManager()
        val updated = HealthMonitorConfig(highCoolantThresholdC = 120f)
        manager.updateHealthMonitorConfig(updated)

        assertEquals(120f, manager.healthMonitorConfig.value.highCoolantThresholdC)
    }

    @Test
    fun `detection config survives manager recreation`() {
        val prefs = FakeSharedPreferences()
        makeManager(prefs).updateDetectionConfig(DetectionConfig(accelHardBrakeThreshold = 4.0f))

        val reloaded = makeManager(prefs)
        assertEquals(4.0f, reloaded.detectionConfig.value.accelHardBrakeThreshold)
    }

    @Test
    fun `health monitor config survives manager recreation`() {
        val prefs = FakeSharedPreferences()
        makeManager(prefs).updateHealthMonitorConfig(HealthMonitorConfig(lowFuelThresholdPct = 15f))

        val reloaded = makeManager(prefs)
        assertEquals(15f, reloaded.healthMonitorConfig.value.lowFuelThresholdPct)
    }

    // ── Corrupt data ──────────────────────────────────────────────────────────

    @Test
    fun `corrupt detection JSON falls back to defaults`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("detection_config", "{not valid json}").apply()

        val manager = makeManager(prefs)
        assertEquals(DetectionConfig(), manager.detectionConfig.value)
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `reset restores detection config to defaults`() {
        val manager = makeManager()
        manager.updateDetectionConfig(DetectionConfig(accelHardBrakeThreshold = 4.0f))
        manager.reset()
        assertEquals(DetectionConfig(), manager.detectionConfig.value)
    }

    @Test
    fun `reset restores health monitor config to defaults`() {
        val manager = makeManager()
        manager.updateHealthMonitorConfig(HealthMonitorConfig(highCoolantThresholdC = 120f))
        manager.reset()
        assertEquals(HealthMonitorConfig(), manager.healthMonitorConfig.value)
    }

    // ── Detector integration ──────────────────────────────────────────────────

    /**
     * Lowering the brake threshold via [ThresholdsManager] causes the next HARD_BRAKE
     * sequence to fire at the updated threshold, not the original one.
     *
     * Default threshold: 6.5 mph/s. Lowered to 4.0 mph/s.
     * Sequence rate: ~4.97 mph/s (0.8 km/h per 100ms), which would NOT fire at 6.5 but DOES at 4.0.
     */
    @Test
    fun `lowering brake threshold causes detector to fire at new threshold`() = runTest(StandardTestDispatcher()) {
        val prefs = FakeSharedPreferences()
        val manager = makeManager(prefs)
        val snapFlow = MutableStateFlow(VehicleSnapshot.EMPTY)
        val detector = AccelerationDetector(snapFlow, manager.detectionConfig)
        val events = mutableListOf<DrivingEvent>()
        val job = launch { detector.events().collect { events.add(it) } }

        // Lower brake threshold to 4.0 mph/s with short cooldown and short min duration
        manager.updateDetectionConfig(DetectionConfig(
            accelHardBrakeThreshold = 4.0f,
            minEventDurationMs = 400,
            cooldownMs = 60_000,
        ))
        advanceUntilIdle()

        // -0.8 km/h per 100ms → 0.8 * 0.621 / 0.1 ≈ 4.97 mph/s > 4.0, < 6.5
        for (i in 0..6) {
            snapFlow.value = VehicleSnapshot(
                timestamp = Instant.EPOCH.plusMillis(i * 100L),
                speedKmh = 97 - i,
            )
            advanceUntilIdle()
        }

        job.cancel()

        assertTrue(
            "Expected event at lowered 4.0 mph/s threshold (rate ~4.97), events=${events.size}",
            events.isNotEmpty(),
        )
        assertFalse(
            "Rate should be below original 6.5 threshold",
            events[0].rateMphS!! >= 6.5f,
        )
    }
}
