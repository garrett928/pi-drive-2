package ghart.space.pi_drive.di

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ghart.space.pi_drive.shared.data.DemoVehicleDataSource
import ghart.space.pi_drive.shared.data.OBDVehicleDataSource
import ghart.space.pi_drive.shared.data.VehicleDataSource
import ghart.space.pi_drive.shared.detection.AccelerationDetector
import ghart.space.pi_drive.shared.detection.AccelerometerManager
import ghart.space.pi_drive.shared.detection.DetectionConfig
import ghart.space.pi_drive.shared.detection.GForceDetector
import ghart.space.pi_drive.shared.obd.BluetoothTransport
import ghart.space.pi_drive.shared.obd.ConnectionManager
import ghart.space.pi_drive.shared.obd.MockTransport
import ghart.space.pi_drive.shared.obd.TcpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Singleton

/**
 * Hilt module that provides the application-scoped [VehicleDataSource] and the
 * coroutine scope it runs on.
 *
 * Which implementation is provided depends on [AppConfig] flags written by
 * [MainActivity] before Hilt first accesses the [SingletonComponent]:
 *
 * | `AppConfig.isDemoMode` | `AppConfig.isTcpMode` | Implementation          |
 * |------------------------|-----------------------|-------------------------|
 * | true                   | —                     | [DemoVehicleDataSource] |
 * | false                  | true                  | placeholder (Phase 4)   |
 * | false                  | false                 | placeholder (Phase 4)   |
 *
 * Phase 4 will replace the placeholder with [BluetoothTransport] +
 * [OBDVehicleDataSource] once those classes are implemented.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * Provides the application-lifetime coroutine scope used by long-running
     * [Singleton]-scoped data sources and services.
     *
     * Uses [SupervisorJob] so that a child failure does not cancel siblings.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Provides the [ConnectionManager] that manages Bluetooth reconnection.
     *
     * The transport factory switches between [MockTransport], [TcpTransport], and
     * [BluetoothTransport] based on [AppConfig] flags.
     */
    @Provides
    @Singleton
    fun provideConnectionManager(
        @ApplicationScope scope: CoroutineScope,
        @ApplicationContext context: Context,
    ): ConnectionManager = ConnectionManager(
        scope = scope,
        transportFactory = { address ->
            @SuppressLint("MissingPermission")
            when {
                AppConfig.isDemoMode -> MockTransport()
                AppConfig.isTcpMode  -> TcpTransport(AppConfig.tcpHost, AppConfig.tcpPort)
                else -> {
                    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    BluetoothTransport(btManager.adapter.getRemoteDevice(address))
                }
            }
        },
    )

    /**
     * Provides the [VehicleDataSource] that drives all telemetry consumers.
     *
     * The correct implementation is chosen at first-access time by reading
     * [AppConfig] — which [MainActivity] populates from the launch intent.
     *
     * | `isDemoMode` | `isTcpMode` | Implementation                         |
     * |--------------|-------------|----------------------------------------|
     * | true         | —           | [DemoVehicleDataSource]                |
     * | false        | true        | [OBDVehicleDataSource] + TcpTransport (Phase 4) |
     * | false        | false       | [OBDVehicleDataSource] + MockTransport (Phase 4 → BluetoothTransport) |
     *
     * Phase 4 will replace [MockTransport] with [TcpTransport] / [BluetoothTransport]
     * once those classes are implemented and the initialization handshake is wired in.
     */
    @Provides
    @Singleton
    fun provideVehicleDataSource(
        @ApplicationScope scope: CoroutineScope,
    ): VehicleDataSource {
        if (AppConfig.isDemoMode) {
            return DemoVehicleDataSource(
                scenario = AppConfig.demoScenario,
                coroutineScope = scope,
            )
        }
        // Phase 4: swap MockTransport for TcpTransport or BluetoothTransport,
        // and run InitializationSequence to populate real supported PIDs.
        val transport = MockTransport()
        return OBDVehicleDataSource(
            transport = transport,
            initialSupportedPids = setOf(0x05, 0x0C, 0x0D, 0x0F, 0x10, 0x11, 0x2F, 0x5C, 0x5E),
            coroutineScope = scope,
            adapterName = "Mock Adapter",
            protocol = "Simulated",
        )
    }

    /**
     * Provides and starts the [AccelerationDetector] that watches the live snapshot stream.
     *
     * The detector's event flow is launched immediately in [scope] so that events are
     * logged even before the alert system (Phase 5.3) subscribes. Phase 5.3 will replace
     * this launch with a proper [AlertManager] subscription.
     */
    @Provides
    @Singleton
    fun provideAccelerationDetector(
        dataSource: VehicleDataSource,
        @ApplicationScope scope: CoroutineScope,
    ): AccelerationDetector {
        val detector = AccelerationDetector(
            snapshots = dataSource.snapshot,
            config = DetectionConfig(),
        )
        scope.launch { detector.events().collect { /* AlertManager subscribes in Phase 5.3 */ } }
        return detector
    }

    /**
     * Provides the [AccelerometerManager] singleton.
     *
     * The manager is not started here — it is started by [MainActivity] after it injects
     * it alongside [AccelerationDetector], so that the sensor is registered only when the
     * app is in the foreground.
     */
    @Provides
    @Singleton
    fun provideAccelerometerManager(
        @ApplicationContext context: android.content.Context,
    ): AccelerometerManager = AccelerometerManager(context)

    /**
     * Provides the [GForceDetector] and launches its event collection loop.
     *
     * [gForceEnabled] is enabled here so that cross-validation logging runs even without
     * a calibrated accelerometer — on the emulator or an uncalibrated device, only the
     * OBD and GPS sources vote.  Phase 5.3 will route emitted events through [AlertManager].
     */
    @Provides
    @Singleton
    fun provideGForceDetector(
        dataSource: VehicleDataSource,
        accelManager: AccelerometerManager,
        @ApplicationScope scope: CoroutineScope,
    ): GForceDetector {
        val detector = GForceDetector(
            snapshots = dataSource.snapshot,
            accelMps2Flow = accelManager.longitudinalMps2,
            config = DetectionConfig(gForceEnabled = true),
        )
        scope.launch { detector.events().collect { /* AlertManager subscribes in Phase 5.3 */ } }
        return detector
    }
}
