package ghart.space.pi_drive.ui.navigation

/**
 * All navigation routes in the Pi Drive app.
 *
 * Centralised here so routes are never duplicated as bare strings.
 * Top-level routes map directly to bottom navigation tabs.
 * Sub-routes nest under their parent using "/" separators.
 */
object NavRoutes {
    // ── Bottom tab roots ───────────────────────────────────────────────────
    /** Live dashboard — default landing screen. */
    const val HOME = "home"

    /** Trip history list. */
    const val TRIPS = "trips"

    /** Settings root screen. */
    const val SETTINGS = "settings"

    // ── Connect flow (no bottom nav) ──────────────────────────────────────
    /** Step 1 — Bluetooth scan + device list. */
    const val CONNECT_SCAN = "connect/scan"

    /** Step 2 — Initialization checklist. */
    const val CONNECT_PAIR = "connect/pair"

    /** Step 3 — Success / vehicle info. */
    const val CONNECT_DONE = "connect/done"

    // ── Settings sub-screens ──────────────────────────────────────────────
    /** Server URL, API key, streaming config, signal selection. */
    const val SETTINGS_SERVER = "settings/server"

    /** Choose featured metric + tile grid editor. */
    const val SETTINGS_HOME_LAYOUT = "settings/home-layout"

    /** Dials / graphs / split-screen widget assignment. */
    const val SETTINGS_AA_LAYOUT = "settings/aa-layout"

    /** Acceleration, G-Force, speed/RPM thresholds. */
    const val SETTINGS_THRESHOLDS = "settings/thresholds"

    /** Trip detail with CSV export (tripId argument). */
    const val TRIP_DETAIL = "trips/{tripId}"

    /** Returns the trip detail route for a specific trip. */
    fun tripDetail(tripId: Long): String = "trips/$tripId"

    // ── Bottom tab destinations (for navigation logic) ────────────────────
    /** Routes that show the bottom navigation bar. */
    val bottomNavRoutes: Set<String> = setOf(HOME, TRIPS, SETTINGS)
}
