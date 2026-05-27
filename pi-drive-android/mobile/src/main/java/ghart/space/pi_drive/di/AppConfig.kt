package ghart.space.pi_drive.di

import ghart.space.pi_drive.shared.data.model.DemoScenario

/**
 * App-level configuration flags read from the launch [android.content.Intent] and
 * made available to Hilt modules before the dependency graph is first accessed.
 *
 * [MainActivity] writes to this object early in [MainActivity.onCreate] —
 * before `super.onCreate()` triggers Hilt injection — so that the [DataModule]
 * can read the correct mode when constructing [VehicleDataSource].
 *
 * All fields are marked `@Volatile` for safe publication across threads.
 *
 * In production builds, [isDemoMode] and [isTcpMode] remain `false` and these
 * extras are simply absent from the launch intent.
 */
internal object AppConfig {
    /** True if the app was launched with `--ez demo_mode true`. */
    @Volatile var isDemoMode: Boolean = false

    /** The demo scenario to run when [isDemoMode] is true. */
    @Volatile var demoScenario: DemoScenario = DemoScenario.CRUISE

    /** True if the app was launched with `--ez tcp_mode true` (ELM327 TCP emulator). */
    @Volatile var isTcpMode: Boolean = false

    /** ELM327 emulator host (used when [isTcpMode] is true). */
    @Volatile var tcpHost: String = "10.0.2.2"  // localhost from emulator

    /** ELM327 emulator port (used when [isTcpMode] is true). */
    @Volatile var tcpPort: Int = 35000
}
