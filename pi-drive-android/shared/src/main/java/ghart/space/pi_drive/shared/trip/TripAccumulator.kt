package ghart.space.pi_drive.shared.trip

/**
 * Stateful integrator that accumulates speed samples into trip statistics.
 *
 * ## Accumulation rules
 * - **Distance:** `speed_mph × (dt_ms / 3_600_000)` added whenever speed > 0.
 * - **Duration:** The elapsed interval is counted only when speed > 0, so stops are excluded.
 * - **Max speed:** Tracks the highest instantaneous speed seen across all samples.
 * - **Fuel:** Delegated to the embedded [fuelTracker]; call [fuelTracker.update] from the
 *   same snapshot loop to populate [TripSummary.avgMpg].
 *
 * ## Lifecycle
 * ```
 * accumulator.reset()       // start a new trip
 * // on each VehicleSnapshot:
 * accumulator.update(speedMph, timestampMs)
 * accumulator.fuelTracker.update(mafGps, fuelRateLph, speedKmh, dtMs)
 * // on ignition off / OBD disconnect:
 * accumulator.pause()
 * // on reconnect:
 * accumulator.resume()
 * // snapshot at any time:
 * val summary = accumulator.toSummary()
 * ```
 */
class TripAccumulator {

    /** Embedded fuel tracker; update it alongside [update] for MPG in [toSummary]. */
    val fuelTracker = FuelTracker()

    private var isPaused = false
    private var prevTimestampMs: Long? = null

    /** Cumulative distance while moving, in US miles. */
    var distanceMiles: Float = 0f
        private set

    /** Wall-clock time elapsed while speed > 0, in milliseconds. */
    var movingDurationMs: Long = 0L
        private set

    /** Peak instantaneous speed observed, in mph. */
    var maxSpeedMph: Float = 0f
        private set

    /**
     * Average speed over the moving portion of the trip: distance ÷ moving hours.
     * Zero when [movingDurationMs] is zero (vehicle has not moved yet).
     */
    val avgSpeedMph: Float
        get() = if (movingDurationMs == 0L) 0f else distanceMiles / (movingDurationMs / 3_600_000f)

    /**
     * Integrates a new speed sample into all accumulators.
     *
     * No-op when [pause]d. The first sample after construction or after [pause] establishes
     * the time origin without contributing distance or duration (there is no prior Δt).
     *
     * @param speedMph    Current vehicle speed in miles per hour (convert from km/h if needed).
     * @param timestampMs Epoch milliseconds from [VehicleSnapshot.timestamp].
     */
    fun update(speedMph: Float, timestampMs: Long) {
        if (isPaused) return

        val prev = prevTimestampMs
        prevTimestampMs = timestampMs

        if (prev == null) return // first sample — no Δt to integrate

        val dtMs = timestampMs - prev
        if (dtMs <= 0L) return

        if (speedMph > 0f) {
            distanceMiles += speedMph * (dtMs / 3_600_000f)
            movingDurationMs += dtMs
            if (speedMph > maxSpeedMph) maxSpeedMph = speedMph
        }
    }

    /**
     * Suspends accumulation.
     *
     * Clears the previous-timestamp anchor so that when [resume] is called and [update]
     * is next invoked, the resumption sample does not carry the paused interval as Δt.
     * Subsequent [update] calls are silently ignored until [resume].
     */
    fun pause() {
        isPaused = true
        prevTimestampMs = null
    }

    /**
     * Resumes accumulation after a [pause].
     *
     * The first [update] call after resuming establishes the new time origin without
     * adding distance or duration.
     */
    fun resume() {
        isPaused = false
    }

    /**
     * Resets all accumulators to zero and un-pauses.
     *
     * Also resets the embedded [fuelTracker].
     */
    fun reset() {
        distanceMiles = 0f
        movingDurationMs = 0L
        maxSpeedMph = 0f
        prevTimestampMs = null
        isPaused = false
        fuelTracker.reset()
    }

    /**
     * Returns an immutable snapshot of all current trip statistics.
     *
     * [TripSummary.avgMpg] is null if [fuelTracker] has received no fuel data or if
     * [distanceMiles] is zero.
     */
    fun toSummary(): TripSummary = TripSummary(
        distanceMiles = distanceMiles,
        durationMs = movingDurationMs,
        avgSpeedMph = avgSpeedMph,
        maxSpeedMph = maxSpeedMph,
        avgMpg = fuelTracker.tripAverageMpg(distanceMiles),
    )
}
