package ghart.space.pi_drive.shared.detection

import ghart.space.pi_drive.shared.data.db.dao.DrivingEventDao
import ghart.space.pi_drive.shared.data.db.entity.DrivingEventEntity
import ghart.space.pi_drive.shared.data.model.AlertAction
import ghart.space.pi_drive.shared.data.model.DetectionStrategy
import ghart.space.pi_drive.shared.data.model.DrivingEvent
import ghart.space.pi_drive.shared.data.model.EventType
import ghart.space.pi_drive.shared.data.model.HealthAlertType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for [AlertManager].
 *
 * Uses fake [Flow] sources (MutableSharedFlow) so tests emit events directly without
 * going through the full detector pipeline. This keeps the tests focused on AlertManager's
 * coordination logic: DB persistence, cooldown suppression, alert forwarding, and isSevere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertManagerTest {

    private val dispatcher = StandardTestDispatcher()

    // ── Fakes ─────────────────────────────────────────────────────────────────

    /** Records every [DrivingEventEntity] passed to [DrivingEventDao.insert]. */
    private val insertedEvents = mutableListOf<DrivingEventEntity>()

    private val fakeDao = object : DrivingEventDao {
        override suspend fun insert(event: DrivingEventEntity): Long {
            insertedEvents.add(event)
            return insertedEvents.size.toLong()
        }
        override suspend fun getByTimeRange(from: Instant, to: Instant) =
            emptyList<DrivingEventEntity>()
        override suspend fun getByTripId(tripId: Long) =
            emptyList<DrivingEventEntity>()
        override suspend fun countByTypeAndTimeRange(
            type: EventType,
            from: Instant,
            to: Instant,
        ) = 0
    }

    private val fakeDrivingEvents = MutableSharedFlow<DrivingEvent>()
    private val fakeHealthAlerts = MutableSharedFlow<AlertAction.HealthAlert>()

    /**
     * Builds an [AlertManager] backed by [fakeDrivingEvents] and [fakeHealthAlerts].
     *
     * Uses [backgroundScope] so the infinite-running collector coroutines do not trigger
     * [kotlinx.coroutines.test.UncompletedCoroutinesError] after the test body completes.
     * [backgroundScope] shares the same [TestCoroutineScheduler] as the test body, so
     * [advanceUntilIdle] drives all emissions end-to-end.
     */
    private fun TestScope.buildAlertManager(
        config: AlertManagerConfig = AlertManagerConfig(),
        clock: () -> Long = { 0L },
    ) = AlertManager(
        drivingEvents = fakeDrivingEvents,
        healthAlerts = fakeHealthAlerts,
        eventDao = fakeDao,
        scope = backgroundScope,
        config = config,
        clock = clock,
    )

    private fun fakeBrakeEvent(
        type: EventType = EventType.HARD_BRAKE,
        peakG: Float? = null,
    ) = DrivingEvent(
        strategy = DetectionStrategy.ACCELERATION,
        type = type,
        timestamp = Instant.EPOCH,
        durationMs = 500,
        rateMphS = 12f,
        peakG = peakG,
        peakAccelMps2 = 5.4f,
        startSpeedMph = 60f,
        endSpeedMph = 40f,
        location = null,
        sources = emptySet(),
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * A hard-brake event emitted on [fakeDrivingEvents] should be persisted to the DB
     * AND produce an [AlertAction.DrivingEventAlert] on [AlertManager.alerts].
     */
    @Test
    fun `hard brake event is logged to DB and emits alert action`() = runTest(dispatcher) {
        val alertManager = buildAlertManager()
        val received = mutableListOf<AlertAction>()
        val job = launch { alertManager.alerts.collect { received.add(it) } }
        advanceUntilIdle()

        fakeDrivingEvents.emit(fakeBrakeEvent())
        advanceUntilIdle()

        job.cancel()

        assertEquals("Event must be inserted into DB", 1, insertedEvents.size)
        assertEquals("AlertAction must be emitted", 1, received.size)
        val alert = received[0] as AlertAction.DrivingEventAlert
        assertEquals(EventType.HARD_BRAKE, alert.event.type)
    }

    /**
     * Two hard-brake events 30 s apart with a 60 s alert cooldown should produce:
     * - Two DB insertions (all events are always logged).
     * - Only one AlertAction (the second is suppressed by the cooldown).
     */
    @Test
    fun `two events 30s apart with 60s cooldown suppresses second alert but logs both`() =
        runTest(dispatcher) {
            var wallClock = 0L
            val alertManager = buildAlertManager(
                config = AlertManagerConfig(drivingEventCooldownMs = 60_000),
                clock = { wallClock },
            )
            val received = mutableListOf<AlertAction>()
            val job = launch { alertManager.alerts.collect { received.add(it) } }
            advanceUntilIdle()

            // First event at t=0
            fakeDrivingEvents.emit(fakeBrakeEvent())
            advanceUntilIdle()

            // Second event at t=30s (still within 60s cooldown)
            wallClock = 30_000L
            fakeDrivingEvents.emit(fakeBrakeEvent())
            advanceUntilIdle()

            job.cancel()

            assertEquals("Both events must be logged to DB", 2, insertedEvents.size)
            assertEquals("Only first alert emits within 60 s cooldown", 1, received.size)
        }

    /**
     * Once the alert cooldown expires, a subsequent event of the same type should fire again.
     */
    @Test
    fun `alert fires again after cooldown expires`() = runTest(dispatcher) {
        var wallClock = 0L
        val alertManager = buildAlertManager(
            config = AlertManagerConfig(drivingEventCooldownMs = 60_000),
            clock = { wallClock },
        )
        val received = mutableListOf<AlertAction>()
        val job = launch { alertManager.alerts.collect { received.add(it) } }
        advanceUntilIdle()

        // First event at t=0
        fakeDrivingEvents.emit(fakeBrakeEvent())
        advanceUntilIdle()

        // Second event past the 60 s cooldown
        wallClock = 61_000L
        fakeDrivingEvents.emit(fakeBrakeEvent())
        advanceUntilIdle()

        job.cancel()

        assertEquals("Both alerts should emit after cooldown expires", 2, received.size)
    }

    /**
     * A [AlertAction.HealthAlert] emitted on [fakeHealthAlerts] must be forwarded
     * through [AlertManager.alerts].
     */
    @Test
    fun `health alert is forwarded to alerts flow`() = runTest(dispatcher) {
        val alertManager = buildAlertManager()
        val received = mutableListOf<AlertAction>()
        val job = launch { alertManager.alerts.collect { received.add(it) } }
        advanceUntilIdle()

        fakeHealthAlerts.emit(AlertAction.HealthAlert(
            type = HealthAlertType.HIGH_COOLANT,
            message = "Coolant 115 °C",
            value = 115f,
        ))
        advanceUntilIdle()

        job.cancel()

        assertEquals("Health alert must be forwarded", 1, received.size)
        val alert = received[0] as AlertAction.HealthAlert
        assertEquals(HealthAlertType.HIGH_COOLANT, alert.type)
        assertEquals(115f, alert.value)
    }

    /**
     * ACCELERATION strategy events have no [DrivingEvent.peakG], so [AlertAction.DrivingEventAlert.isSevere]
     * must always be false for them.
     */
    @Test
    fun `acceleration event has isSevere false since no peakG`() = runTest(dispatcher) {
        val alertManager = buildAlertManager()
        val received = mutableListOf<AlertAction>()
        val job = launch { alertManager.alerts.collect { received.add(it) } }
        advanceUntilIdle()

        fakeDrivingEvents.emit(fakeBrakeEvent(peakG = null))
        advanceUntilIdle()

        job.cancel()

        assertEquals(1, received.size)
        val alert = received[0] as AlertAction.DrivingEventAlert
        assertFalse("ACCELERATION events have no peakG so isSevere must be false", alert.isSevere)
    }

    /**
     * When [DrivingEvent.peakG] meets the severity threshold, [AlertAction.DrivingEventAlert.isSevere]
     * must be true.
     */
    @Test
    fun `severe G-force event sets isSevere true`() = runTest(dispatcher) {
        val alertManager = buildAlertManager(
            config = AlertManagerConfig(severeBrakeThresholdG = 0.50f),
        )
        val received = mutableListOf<AlertAction>()
        val job = launch { alertManager.alerts.collect { received.add(it) } }
        advanceUntilIdle()

        fakeDrivingEvents.emit(fakeBrakeEvent(peakG = 0.65f))
        advanceUntilIdle()

        job.cancel()

        assertEquals(1, received.size)
        val alert = received[0] as AlertAction.DrivingEventAlert
        assertTrue("peakG above threshold must set isSevere", alert.isSevere)
    }
}
