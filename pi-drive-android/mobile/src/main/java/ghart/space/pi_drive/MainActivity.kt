package ghart.space.pi_drive

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import ghart.space.pi_drive.di.AppConfig
import ghart.space.pi_drive.shared.auto.AADataBridge
import ghart.space.pi_drive.shared.data.VehicleDataSource
import ghart.space.pi_drive.shared.data.db.dao.AutoTripDao
import ghart.space.pi_drive.shared.data.model.DemoScenario
import ghart.space.pi_drive.shared.detection.AccelerationDetector
import ghart.space.pi_drive.shared.detection.AccelerometerManager
import ghart.space.pi_drive.shared.detection.AlertManager
import ghart.space.pi_drive.shared.detection.GForceDetector
import ghart.space.pi_drive.shared.settings.AALayoutManager
import ghart.space.pi_drive.shared.settings.GeneralSettingsManager
import ghart.space.pi_drive.shared.settings.ThresholdsManager
import ghart.space.pi_drive.shared.trip.AutoTripManager
import ghart.space.pi_drive.shared.trip.ManualTripManager
import ghart.space.pi_drive.shared.ui.theme.AccentOptions
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.components.PiDriveScaffold
import ghart.space.pi_drive.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Main entry point for the Pi Drive phone app.
 *
 * Reads launch-intent extras to configure [AppConfig] **before** calling
 * `super.onCreate()`, so that Hilt's [SingletonComponent] sees the correct
 * mode flags when it lazily constructs the dependency graph on first access.
 *
 * On startup, this activity also:
 * - Registers the accelerometer for G-force detection.
 * - Runs the data-retention job to delete auto trips older than the user's
 *   configured retention period.
 *
 * Supported intent extras:
 * - `demo_mode` (Boolean) — use [DemoVehicleDataSource] instead of live OBD
 * - `demo_scenario` (String) — which [DemoScenario] to run (default: CRUISE)
 * - `tcp_mode` (Boolean) — route OBD traffic through a TCP ELM327 emulator
 * - `tcp_host` (String) — emulator host (default: 10.0.2.2 = localhost in emulator)
 * - `tcp_port` (Int) — emulator port (default: 35000)
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var accelDetector: AccelerationDetector
    @Inject lateinit var gForceDetector: GForceDetector
    @Inject lateinit var accelManager: AccelerometerManager
    @Inject lateinit var alertManager: AlertManager
    @Inject lateinit var generalSettingsManager: GeneralSettingsManager
    @Inject lateinit var thresholdsManager: ThresholdsManager
    @Inject lateinit var aaLayoutManager: AALayoutManager
    @Inject lateinit var vehicleDataSource: VehicleDataSource
    @Inject lateinit var manualTripManager: ManualTripManager
    @Inject lateinit var autoTripManager: AutoTripManager
    @Inject lateinit var autoTripDao: AutoTripDao
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        // Parse extras BEFORE super.onCreate() so Hilt reads AppConfig when
        // building the DI graph — injection happens during super.onCreate().
        AppConfig.isDemoMode  = intent.getBooleanExtra("demo_mode", false)
        AppConfig.demoScenario = intent.getStringExtra("demo_scenario")
            ?.let { name -> DemoScenario.entries.find { it.name == name.uppercase() } }
            ?: DemoScenario.CRUISE
        AppConfig.isTcpMode  = intent.getBooleanExtra("tcp_mode", false)
        AppConfig.tcpHost    = intent.getStringExtra("tcp_host") ?: "10.0.2.2"
        AppConfig.tcpPort    = intent.getIntExtra("tcp_port", 35000)

        if (AppConfig.isDemoMode) {
            Log.d("PiDrive", "Demo mode active, scenario: ${AppConfig.demoScenario}")
        }

        super.onCreate(savedInstanceState)
        accelManager.start()
        runDataRetentionJob()
        bindAADataBridge()
        enableEdgeToEdge()
        setContent {
            // Collect general settings so theme and accent update reactively.
            val settings by generalSettingsManager.settings.collectAsStateWithLifecycle()
            PiDriveTheme(
                darkTheme = settings.isDarkTheme,
                accent = AccentOptions.all.getOrElse(settings.accentIndex) { AccentOptions.WarmOrange },
            ) {
                PiDriveScaffold()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        accelManager.stop()
    }

    /**
     * Connects [AADataBridge] to the Hilt-managed data layer.
     *
     * Safe to call once from [onCreate]; the application-scoped [CoroutineScope] keeps
     * the collection loops alive even after the Activity is destroyed, so the Android Auto
     * head unit continues receiving data when the phone screen is off.
     */
    private fun bindAADataBridge() {
        AADataBridge.bind(
            vehicleDataSource = vehicleDataSource,
            manualTripManager = manualTripManager,
            autoTripManager = autoTripManager,
            alertManager = alertManager,
            thresholdsManager = thresholdsManager,
            aaLayoutManager = aaLayoutManager,
            scope = applicationScope,
        )
        Log.d("PiDrive", "AADataBridge: bound to live data sources")
    }

    /**
     * Deletes completed auto trips older than the user's configured retention period.
     *
     * Runs once per launch in [lifecycleScope]. Trips with `retentionDays == -1` (unlimited)
     * are never deleted. Active trips (those with no end time) are also never deleted — the
     * Room query only targets rows with a non-null `endTime`.
     */
    private fun runDataRetentionJob() {
        val retentionDays = generalSettingsManager.settings.value.dataRetentionDays
        if (retentionDays < 0) return // unlimited — nothing to delete

        lifecycleScope.launch {
            val cutoff = Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS)
            autoTripDao.deleteOlderThan(cutoff)
            Log.d("PiDrive", "Data retention: deleted trips older than $retentionDays days")
        }
    }
}
