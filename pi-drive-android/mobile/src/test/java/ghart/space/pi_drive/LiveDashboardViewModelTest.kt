package ghart.space.pi_drive

import android.content.SharedPreferences
import ghart.space.pi_drive.di.AppConfig
import ghart.space.pi_drive.shared.data.DemoVehicleDataSource
import ghart.space.pi_drive.shared.data.db.dao.DrivingEventDao
import ghart.space.pi_drive.shared.data.db.dao.AutoTripDao
import ghart.space.pi_drive.shared.data.db.dao.ManualTripDao
import ghart.space.pi_drive.shared.data.db.entity.AutoTripEntity
import ghart.space.pi_drive.shared.data.db.entity.DrivingEventEntity
import ghart.space.pi_drive.shared.data.db.entity.ManualTripEntity
import ghart.space.pi_drive.shared.data.model.ConnectionState
import ghart.space.pi_drive.shared.data.model.DemoScenario
import ghart.space.pi_drive.shared.data.model.EventType
import ghart.space.pi_drive.shared.data.model.MetricId
import ghart.space.pi_drive.shared.data.model.VehicleSnapshot
import ghart.space.pi_drive.shared.detection.AlertManager
import ghart.space.pi_drive.shared.obd.ConnectionManager
import ghart.space.pi_drive.shared.obd.MockTransport
import ghart.space.pi_drive.shared.settings.DashboardLayout
import ghart.space.pi_drive.shared.settings.DashboardLayoutManager
import ghart.space.pi_drive.shared.trip.AutoTripManager
import ghart.space.pi_drive.shared.trip.ManualTripManager
import ghart.space.pi_drive.ui.viewmodel.LiveDashboardViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

// ── Fakes ─────────────────────────────────────────────────────────────────────

/**
 * In-memory [SharedPreferences] used by [DashboardLayoutManager] in tests.
 * Same structure as GeneralSettingsManagerTest.FakeSharedPreferences.
 */
private class FakeSharedPreferences : SharedPreferences {
    private val store = mutableMapOf<String, Any?>()

    inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearPending = false

        override fun putString(k: String, v: String?) = apply { pending[k] = v }
        override fun putStringSet(k: String, v: Set<String>?) = apply { pending[k] = v }
        override fun putInt(k: String, v: Int) = apply { pending[k] = v }
        override fun putLong(k: String, v: Long) = apply { pending[k] = v }
        override fun putFloat(k: String, v: Float) = apply { pending[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { pending[k] = v }
        override fun remove(k: String) = apply { pending[k] = null }
        override fun clear() = apply { clearPending = true }

        override fun commit(): Boolean { apply(); return true }

        override fun apply() {
            if (clearPending) store.clear()
            pending.forEach { (k, v) -> if (v == null) store.remove(k) else store[k] = v }
        }
    }

    override fun getAll(): Map<String, *> = store.toMap()
    override fun getString(k: String, def: String?): String? = (store[k] as? String) ?: def
    override fun getStringSet(k: String, def: Set<String>?): Set<String>? =
        (store[k] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: def
    override fun getInt(k: String, def: Int): Int = (store[k] as? Int) ?: def
    override fun getLong(k: String, def: Long): Long = (store[k] as? Long) ?: def
    override fun getFloat(k: String, def: Float): Float = (store[k] as? Float) ?: def
    override fun getBoolean(k: String, def: Boolean): Boolean = (store[k] as? Boolean) ?: def
    override fun contains(k: String): Boolean = store.containsKey(k)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {}
}

/** Builds a no-op [ManualTripManager] suitable for ViewModel tests that don't test trip behavior. */
private fun noOpManualTripManager(scope: CoroutineScope): ManualTripManager {
    val fakeDao = object : ManualTripDao {
        override suspend fun insert(trip: ManualTripEntity) = 0L
        override suspend fun update(trip: ManualTripEntity) {}
        override suspend fun getActive(): ManualTripEntity? = null
        override fun getAll(): Flow<List<ManualTripEntity>> = emptyFlow()
    }
    return ManualTripManager(
        snapshots = MutableStateFlow(VehicleSnapshot.EMPTY),
        connectionState = MutableStateFlow(ConnectionState.Disconnected()),
        dao = fakeDao,
        scope = scope,
    )
}

/** Builds a no-op [AutoTripManager] suitable for ViewModel tests that don't test trip behavior. */
private fun noOpAutoTripManager(scope: CoroutineScope): AutoTripManager {
    val fakeDao = object : AutoTripDao {
        override suspend fun insert(trip: AutoTripEntity) = 0L
        override suspend fun update(trip: AutoTripEntity) {}
        override suspend fun getActive(): AutoTripEntity? = null
        override fun getAll() = kotlinx.coroutines.flow.emptyFlow<List<AutoTripEntity>>()
        override suspend fun getByDateRange(from: java.time.Instant, to: java.time.Instant) = emptyList<AutoTripEntity>()
        override suspend fun delete(trip: AutoTripEntity) {}
        override suspend fun deleteOlderThan(before: java.time.Instant) {}
    }
    return AutoTripManager(
        snapshots = MutableStateFlow(VehicleSnapshot.EMPTY),
        connectionState = MutableStateFlow(ConnectionState.Disconnected()),
        dao = fakeDao,
        scope = scope,
    )
}

/** Builds a no-op [AlertManager] suitable for ViewModel tests that don't test alert behavior. */
private fun noOpAlertManager(scope: CoroutineScope): AlertManager {
    val fakeDao = object : DrivingEventDao {
        override suspend fun insert(event: DrivingEventEntity) = 0L
        override suspend fun getByTimeRange(from: Instant, to: Instant) = emptyList<DrivingEventEntity>()
        override suspend fun getByTripId(tripId: Long) = emptyList<DrivingEventEntity>()
        override suspend fun countByTypeAndTimeRange(type: EventType, from: Instant, to: Instant) = 0
    }
    return AlertManager(
        drivingEvents = kotlinx.coroutines.flow.emptyFlow(),
        healthAlerts = kotlinx.coroutines.flow.emptyFlow(),
        eventDao = fakeDao,
        scope = scope,
    )
}

/** Default [DashboardLayoutManager] backed by a [FakeSharedPreferences] instance. */
private fun defaultLayoutManager(): DashboardLayoutManager =
    DashboardLayoutManager(FakeSharedPreferences())

/** [DashboardLayoutManager] initialised with [layout] already persisted. */
private fun layoutManagerWith(layout: DashboardLayout): DashboardLayoutManager =
    DashboardLayoutManager(FakeSharedPreferences()).also { it.update(layout) }

@OptIn(ExperimentalCoroutinesApi::class)
class LiveDashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AppConfig.isDemoMode = true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AppConfig.isDemoMode = false
    }

    // ── Metadata tests ────────────────────────────────────────────────────────

    @Test
    fun `featuredMetricId defaults to SPEED`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            autoTripManager = noOpAutoTripManager(backgroundScope),
            dashboardLayoutManager = defaultLayoutManager(),
        )
        assertEquals(MetricId.SPEED, viewModel.featuredMetricId.value)
    }

    @Test
    fun `featuredUnit and featuredLabel match SPEED defaults`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            autoTripManager = noOpAutoTripManager(backgroundScope),
            dashboardLayoutManager = defaultLayoutManager(),
        )
        assertEquals("mph", viewModel.featuredUnit.value)
        assertEquals("SPEED", viewModel.featuredLabel.value)
    }

    @Test
    fun `layout manager can override featured metric to RPM`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val layoutManager = layoutManagerWith(DashboardLayout(featuredMetricId = MetricId.RPM))
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            autoTripManager = noOpAutoTripManager(backgroundScope),
            dashboardLayoutManager = layoutManager,
        )
        assertEquals(MetricId.RPM, viewModel.featuredMetricId.value)
        assertEquals("rpm", viewModel.featuredUnit.value)
        assertEquals("RPM", viewModel.featuredLabel.value)
    }

    @Test
    fun `changing layout manager featured metric updates ViewModel`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val layoutManager = defaultLayoutManager()
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            autoTripManager = noOpAutoTripManager(backgroundScope),
            dashboardLayoutManager = layoutManager,
        )

        layoutManager.updateFeaturedMetric(MetricId.COOLANT)
        advanceTimeBy(100)

        assertEquals(MetricId.COOLANT, viewModel.featuredMetricId.value)
    }

    // ── Live data tests ───────────────────────────────────────────────────────

    @Test
    fun `featuredValue emits non-dash speed values after CRUISE starts`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
            tickIntervalMs = 100L,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            autoTripManager = noOpAutoTripManager(backgroundScope),
            dashboardLayoutManager = defaultLayoutManager(),
        )

        val values = mutableListOf<String>()
        val collectJob = launch { viewModel.featuredValue.take(6).toList(values) }
        advanceTimeBy(2_000)
        collectJob.join()

        assertTrue("Expected non-dash speed values, got: $values", values.any { it != "—" })
    }

    @Test
    fun `featuredValue changes over time in CRUISE scenario`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
            tickIntervalMs = 100L,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            autoTripManager = noOpAutoTripManager(backgroundScope),
            dashboardLayoutManager = defaultLayoutManager(),
        )

        val values = mutableListOf<String>()
        val collectJob = launch { viewModel.featuredValue.take(8).toList(values) }
        advanceTimeBy(3_000)
        collectJob.join()

        val nonDash = values.filter { it != "—" }
        assertTrue("Expected at least 2 distinct speed values, got: $nonDash", nonDash.toSet().size >= 2)
    }

    @Test
    fun `sparklineData accumulates values from snapshot emissions`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
            tickIntervalMs = 100L,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            autoTripManager = noOpAutoTripManager(backgroundScope),
            dashboardLayoutManager = defaultLayoutManager(),
        )

        val sparklineLists = mutableListOf<List<Float>>()
        val collectJob = launch { viewModel.sparklineData.take(10).toList(sparklineLists) }
        advanceTimeBy(3_000)
        collectJob.join()

        val lastList = sparklineLists.lastOrNull() ?: emptyList()
        assertTrue("Sparkline should have accumulated data, got ${lastList.size}", lastList.isNotEmpty())
    }

    @Test
    fun `isLive becomes true after CRUISE polling starts`() = runTest {
        val dataSource = DemoVehicleDataSource(
            scenario = DemoScenario.CRUISE,
            coroutineScope = backgroundScope,
            tickIntervalMs = 100L,
        )
        val stubManager = ConnectionManager(scope = backgroundScope, transportFactory = { MockTransport() })
        val viewModel = LiveDashboardViewModel(
            dataSource = dataSource,
            connectionManager = stubManager,
            alertManager = noOpAlertManager(backgroundScope),
            manualTripManager = noOpManualTripManager(backgroundScope),
            autoTripManager = noOpAutoTripManager(backgroundScope),
            dashboardLayoutManager = defaultLayoutManager(),
        )

        val liveValues = mutableListOf<Boolean>()
        val collectJob = launch { viewModel.isLive.take(2).toList(liveValues) }

        advanceTimeBy(1_500)
        collectJob.join()

        assertTrue("isLive should become true after demo connects, got: $liveValues", liveValues.contains(true))
    }
}
