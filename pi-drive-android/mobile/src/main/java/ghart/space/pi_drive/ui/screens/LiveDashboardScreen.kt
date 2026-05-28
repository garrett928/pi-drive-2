package ghart.space.pi_drive.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.shared.ui.components.PDPill
import ghart.space.pi_drive.shared.ui.components.PillStyle
import ghart.space.pi_drive.ui.components.FeaturedMetric
import ghart.space.pi_drive.ui.components.SparklineGraph
import ghart.space.pi_drive.ui.viewmodel.LiveDashboardViewModel

/**
 * Live vehicle data dashboard — the app's primary screen.
 *
 * Phase 3.1: Featured metric hero card with sparkline and LIVE pill.
 * Phase 3.2 will add the MPG row and metric tile grid below.
 * Phase 3.3 will add the connection banner and status bar.
 *
 * @param navController Used by future phases for navigation to connect/settings.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        FeaturedCard(
            value = featuredValue,
            unit = viewModel.featuredUnit,
            label = viewModel.featuredLabel,
            sparklineData = sparklineData,
            isLive = isLive,
        )
    }
}

/**
 * Hero card containing the featured metric value, sparkline, and LIVE pill.
 *
 * Layout: PDCard with 18dp corner radius. Inside: a Box that overlays the LIVE
 * pill in the top-right corner over a Column of [FeaturedMetric] + [SparklineGraph].
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
