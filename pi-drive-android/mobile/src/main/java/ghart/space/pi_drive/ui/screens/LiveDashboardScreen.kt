package ghart.space.pi_drive.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.data.model.MetricId
import ghart.space.pi_drive.shared.data.model.extractMetricValue
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.shared.ui.components.PDPill
import ghart.space.pi_drive.shared.ui.components.PillStyle
import ghart.space.pi_drive.ui.components.ConnectionBanner
import ghart.space.pi_drive.ui.components.FeaturedMetric
import ghart.space.pi_drive.ui.components.MpgRow
import ghart.space.pi_drive.ui.components.SparklineGraph
import ghart.space.pi_drive.ui.components.StatusBanner
import ghart.space.pi_drive.ui.components.TileGrid
import ghart.space.pi_drive.ui.navigation.NavRoutes
import ghart.space.pi_drive.ui.viewmodel.LiveDashboardViewModel

/**
 * Live vehicle data dashboard — the app's primary screen.
 *
 * Layout (top to bottom, all scrollable):
 * 1. [ConnectionBanner] — adapter state + tap-to-connect
 * 2. [FeaturedCard] — hero metric + sparkline + LIVE pill
 * 3. MPG row card
 * 4. [TileGrid] — 2-column metric tiles
 * 5. [StatusBanner] — recording / sync status (bottom of scroll content)
 *
 * Phase 3.1: Featured metric + sparkline.
 * Phase 3.2: MPG row + tile grid.
 * Phase 3.3: Connection banner + status bar.
 *
 * @param navController Used to navigate to the connect flow from the banner.
 * @param viewModel     Hilt-injected; provides live metric state flows.
 */
@Composable
fun LiveDashboardScreen(
    navController: NavController,
    viewModel: LiveDashboardViewModel = hiltViewModel(),
) {
    val featuredValue by viewModel.featuredValue.collectAsStateWithLifecycle()
    val sparklineData by viewModel.sparklineData.collectAsStateWithLifecycle()
    val isLive by viewModel.isLive.collectAsStateWithLifecycle()
    val snapshot by viewModel.currentSnapshot.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    val instantMpg = snapshot.extractMetricValue(MetricId.MPG_INSTANT).raw

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        ConnectionBanner(
            connectionState = connectionState,
            onTap = { navController.navigate(NavRoutes.CONNECT_SCAN) },
            onReconnectNow = { viewModel.reconnectNow() },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeaturedCard(
            value = featuredValue,
            unit = viewModel.featuredUnit,
            label = viewModel.featuredLabel,
            sparklineData = sparklineData,
            isLive = isLive,
        )

        Spacer(modifier = Modifier.height(12.dp))

        PDCard(
            contentPadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            MpgRow(
                instantMpg = instantMpg,
                tripMpg = null,
                manualMpg = null,
                onResetManual = { /* Phase 6 */ },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TileGrid(
            snapshot = snapshot,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        StatusBanner(
            connectionState = connectionState,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * Hero card containing the featured metric value, sparkline, and LIVE pill.
 */
@Composable
private fun FeaturedCard(
    value: String,
    unit: String,
    label: String,
    sparklineData: List<Float>,
    isLive: Boolean,
) {
    PDCard(
        cornerRadius = 18.dp,
        contentPadding = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                FeaturedMetric(
                    value = value,
                    unit = unit,
                    label = label,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SparklineGraph(
                    data = sparklineData,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PDPill(
                text = "LIVE",
                style = if (isLive) PillStyle.LIVE else PillStyle.NEUTRAL,
                showDot = isLive,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}
