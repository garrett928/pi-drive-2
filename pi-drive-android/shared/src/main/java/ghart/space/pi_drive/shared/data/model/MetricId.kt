package ghart.space.pi_drive.shared.data.model

/**
 * Identifies one of the 16 displayable vehicle metrics.
 *
 * Each entry carries its human-readable [displayLabel], the [unit] string shown
 * after the numeric value, and an optional Android drawable [iconRes] (0 = no icon;
 * will be populated with vector-drawable IDs when Phase 3 builds the dashboard tiles).
 *
 * Metrics that require accumulator state (MPG_TRIP, MPG_MANUAL, DISTANCE,
 * MANUAL_TRIP) return null from [VehicleSnapshot.extractMetricValue] — they are
 * computed by the trip accumulator, not from a single snapshot.
 */
enum class MetricId(
    val displayLabel: String,
    val unit: String,
    val iconRes: Int = 0,
) {
    /** OBD PID 0x0D — vehicle speed. Display unit depends on user preference (mph/km/h). */
    SPEED(displayLabel = "Speed", unit = "mph"),

    /** Instantaneous fuel economy derived from fuel rate or MAF. */
    MPG_INSTANT(displayLabel = "MPG", unit = "mpg"),

    /** Trip-average fuel economy computed by the trip accumulator. */
    MPG_TRIP(displayLabel = "Trip MPG", unit = "mpg"),

    /** Manual-trip average fuel economy. */
    MPG_MANUAL(displayLabel = "Avg MPG", unit = "mpg"),

    /** OBD PID 0x0C — engine revolutions per minute. */
    RPM(displayLabel = "RPM", unit = "rpm"),

    /** OBD PID 0x11 — absolute throttle position 0–100%. */
    THROTTLE(displayLabel = "Throttle", unit = "%"),

    /** OBD PID 0x05 — engine coolant temperature. */
    COOLANT(displayLabel = "Coolant", unit = "°C"),

    /** OBD PID 0x0F — intake air temperature. */
    INTAKE(displayLabel = "Intake Air", unit = "°C"),

    /** OBD PID 0x5C — engine oil temperature. */
    OIL_TEMP(displayLabel = "Oil Temp", unit = "°C"),

    /** OBD PID 0x42 — control module voltage (battery proxy). */
    BATTERY(displayLabel = "Battery", unit = "V"),

    /** OBD PID 0x2F — fuel tank level 0–100%. */
    FUEL(displayLabel = "Fuel", unit = "%"),

    /** OBD PID 0x10 — mass air flow rate in grams/second. */
    MAF(displayLabel = "MAF", unit = "g/s"),

    /** Computed lateral/longitudinal g-force magnitude from accelerometer fusion. */
    G_FORCE(displayLabel = "G-Force", unit = "g"),

    /** Computed acceleration/deceleration rate from OBD speed deltas. */
    ACCEL(displayLabel = "Accel Rate", unit = "mph/s"),

    /** Cumulative trip distance computed by the trip accumulator. */
    DISTANCE(displayLabel = "Distance", unit = "mi"),

    /** Manual trip distance (user-controlled start/stop). */
    MANUAL_TRIP(displayLabel = "Trip Distance", unit = "mi"),
}
