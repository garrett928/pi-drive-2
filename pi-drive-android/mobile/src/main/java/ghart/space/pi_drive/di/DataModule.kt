package ghart.space.pi_drive.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ghart.space.pi_drive.shared.data.DemoVehicleDataSource
import ghart.space.pi_drive.shared.data.VehicleDataSource
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
     * Provides the [VehicleDataSource] that drives all telemetry consumers.
     *
     * The correct implementation is chosen at first-access time by reading
     * [AppConfig] — which [MainActivity] populates from the launch intent.
     */
    @Provides
    @Singleton
    fun provideVehicleDataSource(
        @ApplicationScope scope: CoroutineScope,
    ): VehicleDataSource {
        // Phase 4 will add: tcp_mode → TcpTransport + OBDVehicleDataSource
        //                   production → BluetoothTransport + OBDVehicleDataSource
        return DemoVehicleDataSource(
            scenario        = AppConfig.demoScenario,
            coroutineScope  = scope,
        )
    }
}
