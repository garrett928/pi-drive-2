package ghart.space.pi_drive.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ghart.space.pi_drive.shared.ui.components.PDPill
import ghart.space.pi_drive.shared.ui.components.PillStyle
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/**
 * Three-column MPG summary row shown below the featured metric card.
 *
 * Columns left to right: instant MPG (accent color), trip-average MPG, and
 * manual-trip average MPG. The manual column has a "Reset" pill button.
 * Trip and manual MPG are null until the trip accumulator (Phase 6) is wired.
 *
 * @param instantMpg      Current instantaneous fuel economy, or null if unavailable.
 * @param tripMpg         Trip-average fuel economy from the accumulator, or null.
 * @param manualMpg       Manual-trip average fuel economy, or null.
 * @param onResetManual   Invoked when the user taps "Reset" in the manual column.
 * @param modifier        Applied to the outer row.
 */
@Composable
fun MpgRow(
    instantMpg: Float?,
    tripMpg: Float?,
    manualMpg: Float?,
    onResetManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MpgColumn(
            label = "NOW",
            value = instantMpg.formatMpgOrDash(),
            note = "Instant",
            isAccent = true,
            modifier = Modifier.weight(1f),
        )
        MpgColumn(
            label = "TRIP AVG",
            value = tripMpg.formatMpgOrDash(),
            note = "Since start",
            modifier = Modifier.weight(1f),
        )
        MpgColumnWithReset(
            label = "MANUAL AVG",
            value = manualMpg.formatMpgOrDash(),
            note = "Manual trip",
            onReset = onResetManual,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MpgColumn(
    label: String,
    value: String,
    note: String,
    isAccent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Column(modifier = modifier) {
        Text(
            text = label,
            style = type.labelSmall,
            color = colors.fgDim,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = type.metricMedium,
            color = if (isAccent) colors.accent.base else colors.fg,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = note,
            style = type.bodySmall,
            color = colors.fgDim,
        )
    }
}

@Composable
private fun MpgColumnWithReset(
    label: String,
    value: String,
    note: String,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Column(modifier = modifier) {
        Text(
            text = label,
            style = type.labelSmall,
            color = colors.fgDim,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = type.metricMedium,
            color = colors.fg,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = note,
            style = type.bodySmall,
            color = colors.fgDim,
        )
        Spacer(modifier = Modifier.height(4.dp))
        PDPill(
            text = "Reset",
            style = PillStyle.NEUTRAL,
            modifier = Modifier.clickable(onClick = onReset),
        )
    }
}

/** Formats [Float] to one decimal place for MPG display, e.g. "25.3". */
internal fun Float.formatMpg(): String = "%.1f".format(this)

/** Returns [formatMpg] for non-null values, or "—" for null. */
internal fun Float?.formatMpgOrDash(): String = this?.formatMpg() ?: "—"
