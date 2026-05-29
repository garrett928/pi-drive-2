package ghart.space.pi_drive.ui.screens.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.ui.components.PDButtonPrimary
import ghart.space.pi_drive.shared.ui.components.PDButtonSecondary
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.navigation.NavRoutes
import ghart.space.pi_drive.ui.viewmodel.ConnectViewModel

import ghart.space.pi_drive.ui.viewmodel.InitStepItem
import ghart.space.pi_drive.ui.viewmodel.InitStepStatus

/**
 * Step 2 of the connect flow — displays the 6-step initialization checklist and
 * a progress bar while [ConnectViewModel] runs the [ghart.space.pi_drive.shared.obd.InitializationSequence].
 *
 * Automatically navigates to [NavRoutes.CONNECT_DONE] when all steps complete successfully.
 * Shows a "Retry" button if any step ends in [InitStepStatus.ERROR].
 *
 * @param navController Used to navigate to the done screen on success.
 * @param viewModel     Hilt-injected; shared across the connect flow.
 */
@Composable
fun ConnectPairScreen(
    navController: NavController,
    viewModel: ConnectViewModel = hiltViewModel(
        remember(navController) { navController.getBackStackEntry(NavRoutes.CONNECT_GRAPH) }
    ),
) {
    val steps by viewModel.initSteps.collectAsStateWithLifecycle()
    val initResult by viewModel.initResult.collectAsStateWithLifecycle()

    val completedCount = steps.count { it.status == InitStepStatus.SUCCESS }
    val totalCount = steps.size
    val hasError = steps.any { it.status == InitStepStatus.ERROR }
    val allDone = completedCount == totalCount

    // Navigate to done screen when all steps succeed
    LaunchedEffect(allDone) {
        if (allDone && initResult != null) {
            navController.navigate(NavRoutes.CONNECT_DONE) {
                popUpTo(NavRoutes.CONNECT_PAIR) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        // Progress bar
        Text(
            text = "Initializing adapter…",
            style = PiDriveTheme.typography.titleMedium,
            color = PiDriveTheme.colors.fg,
        )

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { if (totalCount > 0) completedCount / totalCount.toFloat() else 0f },
            modifier = Modifier.fillMaxWidth(),
            color = PiDriveTheme.colors.accent.base,
            trackColor = PiDriveTheme.colors.surface2,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Step checklist
        steps.forEach { step ->
            InitStepRow(step = step)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Error/retry area
        if (hasError) {
            val errorStep = steps.firstOrNull { it.status == InitStepStatus.ERROR }
            if (errorStep?.errorMessage != null) {
                Text(
                    text = errorStep.errorMessage,
                    style = PiDriveTheme.typography.bodySmall,
                    color = PiDriveTheme.colors.danger,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PDButtonSecondary(
                    text = "Back",
                    onClick = { navController.navigateUp() },
                    modifier = Modifier.weight(1f),
                )
                PDButtonPrimary(
                    text = "Retry",
                    onClick = { viewModel.retryInitialization() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ── Private composables ──────────────────────────────────────────────────────

@Composable
private fun InitStepRow(step: InitStepItem) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Status icon / spinner
        when (step.status) {
            InitStepStatus.PENDING -> Icon(
                imageVector = Icons.Filled.RadioButtonUnchecked,
                contentDescription = "pending",
                tint = colors.fgDim,
                modifier = Modifier.size(22.dp),
            )
            InitStepStatus.IN_PROGRESS -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = colors.accent.base,
                strokeWidth = 2.dp,
            )
            InitStepStatus.SUCCESS -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "success",
                tint = colors.success,
                modifier = Modifier.size(22.dp),
            )
            InitStepStatus.ERROR -> Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "error",
                tint = colors.danger,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.label,
                style = type.bodyMedium,
                color = when (step.status) {
                    InitStepStatus.PENDING     -> colors.fgDim
                    InitStepStatus.IN_PROGRESS -> colors.fg
                    InitStepStatus.SUCCESS     -> colors.fg
                    InitStepStatus.ERROR       -> colors.danger
                },
            )
            if (step.status == InitStepStatus.ERROR && step.errorMessage != null) {
                Spacer(modifier = Modifier.width(0.dp))
                Text(
                    text = step.errorMessage,
                    style = type.bodySmall,
                    color = colors.danger,
                )
            }
        }
    }
}
