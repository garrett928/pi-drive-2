package ghart.space.pi_drive

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import ghart.space.pi_drive.di.AppConfig
import ghart.space.pi_drive.shared.data.model.DemoScenario
import ghart.space.pi_drive.shared.detection.AccelerationDetector
import ghart.space.pi_drive.shared.detection.AccelerometerManager
import ghart.space.pi_drive.shared.detection.AlertManager
import ghart.space.pi_drive.shared.detection.GForceDetector
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.components.PiDriveScaffold
import javax.inject.Inject

/**
 * Main entry point for the Pi Drive phone app.
 *
 * Reads launch-intent extras to configure [AppConfig] **before** calling
 * `super.onCreate()`, so that Hilt's [SingletonComponent] sees the correct
 * mode flags when it lazily constructs the dependency graph on first access.
 *
 * Supported intent extras:
 * - `demo_mode` (Boolean) — use [DemoVehicleDataSource] instead of live OBD
 * - `demo_scenario` (String) — which [DemoScenario] to run (default: CRUISE)
 * - `tcp_mode` (Boolean) — route OBD traffic through a TCP ELM327 emulator
 * - `tcp_host` (String) — emulator host (default: 10.0.2.2 = localhost in emulator)
 * - `tcp_port` (Int) — emulator port (default: 35000)
 *
 * [AccelerationDetector] is injected here (rather than in [PiDriveApplication]) so that
 * [AppConfig] flags are set before the Hilt singleton graph is first accessed, ensuring
 * the detector collects from the correct [VehicleDataSource] implementation.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var accelDetector: AccelerationDetector
    @Inject lateinit var gForceDetector: GForceDetector
    @Inject lateinit var accelManager: AccelerometerManager
    @Inject lateinit var alertManager: AlertManager

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
        enableEdgeToEdge()
        setContent {
            PiDriveTheme {
                PiDriveScaffold()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        accelManager.stop()
    }
}
