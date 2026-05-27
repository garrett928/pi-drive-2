package ghart.space.pi_drive.shared.data.model

/**
 * Named driving scenarios emulated by [DemoVehicleDataSource].
 *
 * Each scenario produces a distinct pattern of [VehicleSnapshot] values so
 * that UI developers and testers can exercise every metric and edge case
 * without a physical OBD adapter.
 */
enum class DemoScenario {
    /** Steady 60 mph highway cruising with small sine-wave oscillations. */
    CRUISE,

    /** City driving: 0–50 km/h oscillation with frequent stops and idle periods. */
    CITY,

    /** Highway driving: 65–80 mph with minimal variation. */
    HIGHWAY,

    /**
     * Normal cruising with a hard-brake event every ~20 seconds.
     * Speed drops from 96 km/h to 20 km/h in ~2 seconds, then recovers.
     * Useful for testing the event-detection and alert UI.
     */
    HARD_BRAKE,

    /**
     * Cold engine start: coolant climbs from 20 °C to 92 °C over 5 minutes,
     * idle RPM drops from 1 200 to 750, vehicle begins moving after warmup.
     */
    COLD_START,

    /** Normal cruising but fuel level drains from 15 % → 5 % over ~2.5 minutes. */
    LOW_FUEL,

    /** Normal cruising with periodic speed bursts above 75 mph every ~30 seconds. */
    OVERSPEED,

    /**
     * Connection drops every 30 seconds for 30 seconds, then recovers.
     * Tests the connection-loss banner and reconnect UI.
     */
    DISCONNECT,
}
