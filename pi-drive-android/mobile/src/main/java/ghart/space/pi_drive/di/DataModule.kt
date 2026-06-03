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
import ghart.space.pi_drive.shared.detection.AlertManager
import ghart.space.pi_drive.shared.detection.GForceDetector
import ghart.space.pi_drive.shared.detection.HealthMonitor
import ghart.space.pi_drive.shared.data.db.dao.AutoTripDao
import ghart.space.pi_drive.shared.data.db.dao.DrivingEventDao
import ghart.space.pi_drive.shared.data.db.dao.ManualTripDao
import ghart.space.pi_drive.shared.trip.AutoTripManager
import ghart.space.pi_drive.shared.trip.ManualTripManager
import kotlinx.coroutines.flow.merge
import ghart.space.pi_drive.shared.obd.BluetoothTransport
import ghart.space.pi_drive.shared.obd.ConnectionManager
import ghart.space.pi_drive.shared.obd.MockTransport
import ghart.space.pi_drive.shared.obd.TcpTransport
import ghart.space.pi_drive.shared.data.db.dao.PendingUploadDao
import ghart.space.pi_drive.shared.settings.AALayoutManager
import ghart.space.pi_drive.shared.settings.DashboardLayoutManager
import ghart.space.pi_drive.shared.settings.DevSettingsManager
import ghart.space.pi_drive.shared.settings.GeneralSettingsManager
import ghart.space.pi_drive.shared.settings.ThresholdsManager
import ghart.space.pi_drive.shared.telemetry.OfflineBuffer
import ghart.space.pi_drive.shared.telemetry.TelemetryConfigRepository
import ghart.space.pi_drive.shared.telemetry.UploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        if (AppConfig.isTcpMode) {
            // TCP emulator mode: connect immediately with a pre-wired transport so the
            // dashboard starts polling without requiring the user to go through ConnectScreen.
            val transport = TcpTransport(AppConfig.tcpHost, AppConfig.tcpPort)
            return OBDVehicleDataSource(
                transport = transport,
                initialSupportedPids = setOf(0x05, 0x0C, 0x0D, 0x0F, 0x10, 0x11, 0x2F, 0x5C, 0x5E),
                coroutineScope = scope,
                adapterName = "TCP Emulator",
                protocol = "Simulated",
            )
        }
        // Bluetooth mode: transport is null until the user completes the Connect screen flow.
        // ConnectViewModel calls OBDVehicleDataSource.reconnectWith() after initialization.
        return OBDVehicleDataSource(coroutineScope = scope)
    }

    /**
     * Provides the [AccelerationDetector] that watches the live snapshot stream.
     *
     * Receives the reactive [ThresholdsManager.detectionConfig] flow so that threshold
     * changes in the UI take effect immediately without restarting the detector.
     * Event collection is managed by [AlertManager].
     */
    @Provides
    @Singleton
    fun provideAccelerationDetector(
        dataSource: VehicleDataSource,
        thresholdsManager: ThresholdsManager,
    ): AccelerationDetector = AccelerationDetector(
        snapshots = dataSource.snapshot,
        configFlow = thresholdsManager.detectionConfig,
    )

    /**
     * Provides the [AccelerometerManager] singleton.
     *
     * Not started here — [MainActivity] starts it after Hilt injection so the sensor
     * is registered only while the app is in the foreground.
     */
    @Provides
    @Singleton
    fun provideAccelerometerManager(
        @ApplicationContext context: android.content.Context,
    ): AccelerometerManager = AccelerometerManager(context)

    /**
     * Provides the [GForceDetector].
     *
     * Receives the reactive [ThresholdsManager.detectionConfig] flow so that threshold
     * changes in the UI take effect immediately without restarting the detector.
     * Event collection is managed by [AlertManager].
     */
    @Provides
    @Singleton
    fun provideGForceDetector(
        dataSource: VehicleDataSource,
        accelManager: AccelerometerManager,
        thresholdsManager: ThresholdsManager,
    ): GForceDetector = GForceDetector(
        snapshots = dataSource.snapshot,
        accelMps2Flow = accelManager.longitudinalMps2,
        configFlow = thresholdsManager.detectionConfig,
    )

    /**
     * Provides the [HealthMonitor] that watches live snapshots for vehicle health thresholds.
     *
     * Receives the reactive [ThresholdsManager.healthMonitorConfig] flow so that threshold
     * changes in the UI take effect immediately. Uses the data source's supported-PID set
     * to auto-skip checks for metrics the vehicle does not expose.
     */
    @Provides
    @Singleton
    fun provideHealthMonitor(
        dataSource: VehicleDataSource,
        thresholdsManager: ThresholdsManager,
    ): HealthMonitor = HealthMonitor(
        snapshots = dataSource.snapshot,
        supportedPids = dataSource.supportedPids,
        configFlow = thresholdsManager.healthMonitorConfig,
    )

    /**
     * Provides the [ManualTripManager] singleton.
     *
     * Feeds snapshots and connection state from the [VehicleDataSource] and persists
     * accumulated state to [ManualTripDao] every 10 seconds. The user resets the trip
     * from the dashboard "Reset" button, which calls [ManualTripManager.reset].
     */
    @Provides
    @Singleton
    fun provideManualTripManager(
        dataSource: VehicleDataSource,
        dao: ManualTripDao,
        @ApplicationScope scope: CoroutineScope,
    ): ManualTripManager = ManualTripManager(
        snapshots = dataSource.snapshot,
        connectionState = dataSource.connectionState,
        dao = dao,
        scope = scope,
    )

    /**
     * Provides the [AutoTripManager] singleton.
     *
     * Feeds snapshots and connection state from the [VehicleDataSource] and persists
     * trip boundaries to [AutoTripDao]. Trip detection runs for the lifetime of [scope].
     */
    @Provides
    @Singleton
    fun provideAutoTripManager(
        dataSource: VehicleDataSource,
        dao: AutoTripDao,
        @ApplicationScope scope: CoroutineScope,
    ): AutoTripManager = AutoTripManager(
        snapshots = dataSource.snapshot,
        connectionState = dataSource.connectionState,
        dao = dao,
        scope = scope,
    )

    /**
     * Provides the [GeneralSettingsManager] that persists user appearance and behaviour preferences.
     *
     * Constructed with the app's [SharedPreferences] file named [GeneralSettingsManager.PREFS_NAME]
     * so preferences survive across process restarts.
     */
    @Provides
    @Singleton
    fun provideGeneralSettingsManager(@ApplicationContext context: Context): GeneralSettingsManager =
        GeneralSettingsManager(
            context.getSharedPreferences(GeneralSettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        )

    /**
     * Provides the [ThresholdsManager] that persists detection thresholds and health alert settings.
     *
     * Stored in its own SharedPreferences file so threshold changes don't interfere with
     * general or layout settings. All three detectors consume the reactive flows it exposes.
     */
    @Provides
    @Singleton
    fun provideThresholdsManager(@ApplicationContext context: Context): ThresholdsManager =
        ThresholdsManager(
            context.getSharedPreferences(ThresholdsManager.PREFS_NAME, Context.MODE_PRIVATE)
        )

    /**
     * Provides the [AALayoutManager] that persists the Android Auto screen layout configuration.
     *
     * Stored in its own SharedPreferences file so AA layout changes don't interfere with
     * the phone dashboard or general settings. Both [AALayoutViewModel] and [AADataBridge]
     * use this singleton for reactive updates.
     */
    @Provides
    @Singleton
    fun provideAALayoutManager(@ApplicationContext context: Context): AALayoutManager =
        AALayoutManager(
            context.getSharedPreferences(AALayoutManager.PREFS_NAME, Context.MODE_PRIVATE)
        )

    /**
     * Provides the [DevSettingsManager] that persists developer settings to SharedPreferences.
     *
     * Developer settings allow switching between TCP emulator mode, demo mode, and Bluetooth
     * without re-launching via adb. The developer screen is hidden behind a 7-tap unlock.
     * [MainActivity] reads from this same SharedPreferences file (before Hilt injection) to
     * apply the settings to [AppConfig] on the next launch.
     */
    @Provides
    @Singleton
    fun provideDevSettingsManager(@ApplicationContext context: Context): DevSettingsManager =
        DevSettingsManager(
            context.getSharedPreferences(DevSettingsManager.PREFS_NAME, Context.MODE_PRIVATE)
        )

    /**
     * Provides the [DashboardLayoutManager] that persists the phone dashboard tile configuration.
     *
     * Stored in its own SharedPreferences file so layout changes don't interfere with
     * general settings. Both [LiveDashboardViewModel] and the layout editor use this singleton.
     */
    @Provides
    @Singleton
    fun provideDashboardLayoutManager(@ApplicationContext context: Context): DashboardLayoutManager =
        DashboardLayoutManager(
            context.getSharedPreferences(DashboardLayoutManager.PREFS_NAME, Context.MODE_PRIVATE)
        )

    /**
     * Provides the [TelemetryConfigRepository] that persists [TelemetryConfig] to SharedPreferences.
     *
     * [TelemetryService] depends on this to load the server URL, API key, and device ID at startup.
     */
    @Provides
    @Singleton
    fun provideTelemetryConfigRepository(
        @ApplicationContext context: Context,
    ): TelemetryConfigRepository = TelemetryConfigRepository(context)

    /**
     * Provides the [OfflineBuffer] that queues failed uploads for later retry.
     *
     * Wraps [PendingUploadDao] with serialization and exponential back-off logic.
     * Both [TelemetryUploadController] and [UploadWorker] use this buffer.
     */
    @Provides
    @Singleton
    fun provideOfflineBuffer(dao: PendingUploadDao): OfflineBuffer = OfflineBuffer(dao)

    /**
     * Provides the [UploadWorker.Factory] registered as the app's [androidx.work.WorkerFactory].
     *
     * [PiDriveApplication] implements [androidx.work.Configuration.Provider] and returns a
     * [androidx.work.DelegatingWorkerFactory] that includes this factory, so WorkManager can
     * construct [UploadWorker] with its [OfflineBuffer] and [TelemetryConfigRepository] deps.
     */
    @Provides
    @Singleton
    fun provideUploadWorkerFactory(
        offlineBuffer: OfflineBuffer,
        configRepository: TelemetryConfigRepository,
    ): UploadWorker.Factory = UploadWorker.Factory(offlineBuffer, configRepository)

    /**
     * Provides the [AlertManager] and starts its event-collection loops.
     *
     * Merges both detector event flows into a single [drivingEvents] stream and passes
     * the health monitor's alert flow directly. All driving events are persisted to Room
     * and filtered through cooldown logic before reaching the UI.
     */
    @Provides
    @Singleton
    fun provideAlertManager(
        accelDetector: AccelerationDetector,
        gForceDetector: GForceDetector,
        healthMonitor: HealthMonitor,
        eventDao: DrivingEventDao,
        @ApplicationScope scope: CoroutineScope,
    ): AlertManager = AlertManager(
        drivingEvents = merge(accelDetector.events(), gForceDetector.events()),
        healthAlerts = healthMonitor.alerts(),
        eventDao = eventDao,
        scope = scope,
    )
}
