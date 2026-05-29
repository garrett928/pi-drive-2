package ghart.space.pi_drive.shared.detection

import android.util.Log
import ghart.space.pi_drive.shared.data.db.dao.DrivingEventDao
import ghart.space.pi_drive.shared.data.db.entity.DrivingEventEntity
import ghart.space.pi_drive.shared.data.model.AlertAction
import ghart.space.pi_drive.shared.data.model.DrivingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Central coordinator for all driving and vehicle-health alerts.
 *
 * Subscribes to both detection pipelines and the health monitor, then for each event:
 * 1. **Logs** every [DrivingEvent] to Room via [DrivingEventDao] (unconditional — the trip
 *    history depends on a complete event log regardless of whether the UI was alerted).
 * 2. **Emits** an [AlertAction] to UI subscribers, subject to a per-type cooldown so the
 *    driver is not buzzed repeatedly for the same event type during an ongoing incident.
 *
 * Health alerts are forwarded directly from [healthAlerts], which manages its own
 * per-type cooldown internally.
 *
 * This class is a singleton; its collection loops start in [init] and run for the
 * lifetime of [scope].
 *
 * @param drivingEvents  Merged stream of [DrivingEvent]s from all active detectors.
 * @param healthAlerts   Stream of [AlertAction.HealthAlert]s from the vehicle health monitor.
 * @param eventDao       Room DAO for persisting driving events.
 * @param scope          Application-scoped coroutine scope; keeps collection alive.
 * @param config         Alert cooldown and severity configuration.
 * @param clock          Returns epoch-milliseconds; overridable in tests.
 */
class AlertManager(
    private val drivingEvents: Flow<DrivingEvent>,
    private val healthAlerts: Flow<AlertAction.HealthAlert>,
    private val eventDao: DrivingEventDao,
    private val scope: CoroutineScope,
    private val config: AlertManagerConfig = AlertManagerConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    companion object {
        private const val TAG = "PiDrive"
    }

    private val _alerts = MutableSharedFlow<AlertAction>(extraBufferCapacity = 16)

    /**
     * Hot stream of [AlertAction]s. Replay = 0: UI receives only live alerts; subscribers
     * that were not active during a transient alert will not see it after the fact.
     */
    val alerts: SharedFlow<AlertAction> = _alerts.asSharedFlow()

    /** Epoch-ms timestamp of the last alert emitted per [EventType.name] key. */
    private val lastAlertFireTime = mutableMapOf<String, Long>()

    init {
        scope.launch { collectDrivingEvents() }
        scope.launch { collectHealthAlerts() }
    }

    private suspend fun collectDrivingEvents() {
        drivingEvents.collect { event ->
            persistEvent(event)
            maybeEmitAlert(event)
        }
    }

    private suspend fun persistEvent(event: DrivingEvent) {
        try {
            eventDao.insert(event.toEntity())
            Log.i(
                TAG,
                "HARD_${event.type.name} event persisted: " +
                    "strategy=${event.strategy.name}, " +
                    "rateMphS=${event.rateMphS?.let { "%.1f".format(it) } ?: "n/a"}, " +
                    "peakG=${event.peakG?.let { "%.3f".format(it) } ?: "n/a"}",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist driving event", e)
        }
    }

    private suspend fun maybeEmitAlert(event: DrivingEvent) {
        val key = event.type.name
        val now = clock()
        val last = lastAlertFireTime[key]
        // first-ever fire has no prior timestamp — always allow
        if (last != null && now - last < config.drivingEventCooldownMs) {
            Log.d(TAG, "Alert suppressed for ${event.type.name} (within cooldown)")
            return
        }
        lastAlertFireTime[key] = now
        val isSevere = event.peakG?.let { it >= config.severeBrakeThresholdG } ?: false
        _alerts.emit(AlertAction.DrivingEventAlert(event = event, isSevere = isSevere))
        Log.i(TAG, "AlertAction emitted: ${event.type.name}${if (isSevere) " [SEVERE]" else ""}")
    }

    private suspend fun collectHealthAlerts() {
        healthAlerts.collect { alert ->
            _alerts.emit(alert)
        }
    }
}

/**
 * Configuration for [AlertManager] alert cooldowns and severity thresholds.
 *
 * @param drivingEventCooldownMs Minimum milliseconds between UI alerts for the same
 *                               [EventType]. Events are still written to DB during cooldown.
 * @param severeBrakeThresholdG  G-force magnitude at which a G_FORCE event is tagged severe
 *                               in [AlertAction.DrivingEventAlert.isSevere].
 */
data class AlertManagerConfig(
    val drivingEventCooldownMs: Long = 60_000L,
    val severeBrakeThresholdG: Float = 0.50f,
)

/** Converts a domain [DrivingEvent] to its Room [DrivingEventEntity] representation. */
private fun DrivingEvent.toEntity(): DrivingEventEntity = DrivingEventEntity(
    strategy = strategy,
    type = type,
    timestamp = timestamp,
    durationMs = durationMs,
    rateMphS = rateMphS,
    peakG = peakG,
    peakAccelMps2 = peakAccelMps2,
    startSpeedMph = startSpeedMph,
    endSpeedMph = endSpeedMph,
    locationLat = location?.lat,
    locationLng = location?.lng,
    sources = sources,
)
