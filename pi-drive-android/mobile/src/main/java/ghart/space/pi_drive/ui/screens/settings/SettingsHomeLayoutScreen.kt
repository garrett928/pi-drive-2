package ghart.space.pi_drive.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ghart.space.pi_drive.shared.data.model.MetricId
import ghart.space.pi_drive.shared.settings.DashboardTileConfig
import ghart.space.pi_drive.shared.settings.WidgetType
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Phone home layout editor.
 *
 * Allows the user to select the featured (hero) metric and customise the 2-column
 * tile grid — adding, removing, reordering, and changing the widget type for each tile.
 *
 * All mutations are forwarded to [SettingsViewModel] which persists them via
 * [DashboardLayoutManager]. The live dashboard reacts to these changes in real time
 * because both screens observe the same [StateFlow<DashboardLayout>].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsHomeLayoutScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val layout by viewModel.dashboardLayout.collectAsStateWithLifecycle()
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    // Bottom sheet state for editing a tile
    var editingIndex by remember { mutableIntStateOf(-1) }
    var showAddSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
            // ── Featured metric selector ──────────────────────────────────
            SectionLabel("Featured Metric")
            PDCard(contentPadding = 16.dp) {
                Text(
                    "Choose the metric shown in the large hero card.",
                    style = type.bodySmall,
                    color = colors.fgMuted,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetricId.entries.forEach { metricId ->
                        MetricChip(
                            label = metricId.displayLabel,
                            selected = layout.featuredMetricId == metricId,
                            onClick = { viewModel.setFeaturedMetric(metricId) },
                        )
                    }
                }
            }

            // ── Tile grid editor ──────────────────────────────────────────
            SectionLabel("Dashboard Tiles")
            PDCard(contentPadding = 0.dp) {
                if (layout.tiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No tiles. Tap + to add one.",
                            style = type.bodySmall,
                            color = colors.fgDim,
                        )
                    }
                } else {
                    layout.tiles.forEachIndexed { index, tile ->
                        TileEditorRow(
                            tile = tile,
                            canMoveUp = index > 0,
                            canMoveDown = index < layout.tiles.lastIndex,
                            onMoveUp = { viewModel.moveDashboardTile(index, index - 1) },
                            onMoveDown = { viewModel.moveDashboardTile(index, index + 1) },
                            onEdit = { editingIndex = index },
                            onRemove = { viewModel.removeDashboardTile(index) },
                        )
                        if (index < layout.tiles.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                color = colors.borderS,
                            )
                        }
                    }
                }

                HorizontalDivider(color = colors.borderS)

                // Add tile row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAddSheet = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accent.soft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            tint = colors.accent.base,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        "Add tile",
                        style = type.bodyMedium,
                        color = colors.accent.base,
                        fontWeight = FontWeight.W500,
                    )
                }
            }

            Text(
                "Unsupported PIDs are grayed out based on your vehicle.",
                style = type.bodySmall,
                color = colors.fgDim,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(16.dp))
        }

    // ── Edit tile bottom sheet ──────────────────────────────────────────────
    if (editingIndex >= 0 && editingIndex < layout.tiles.size) {
        val editingTile = layout.tiles[editingIndex]
        ModalBottomSheet(
            onDismissRequest = { editingIndex = -1 },
            sheetState = sheetState,
            containerColor = colors.surface,
        ) {
            TileEditSheet(
                tile = editingTile,
                onSave = { updated ->
                    val tiles = layout.tiles.toMutableList()
                    tiles[editingIndex] = updated
                    viewModel.updateDashboardTiles(tiles)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { editingIndex = -1 }
                },
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { editingIndex = -1 }
                },
            )
        }
    }

    // ── Add tile bottom sheet ─────────────────────────────────────────────
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
        ) {
            TileEditSheet(
                tile = DashboardTileConfig(MetricId.SPEED, WidgetType.NUMBER),
                onSave = { newTile ->
                    viewModel.addDashboardTile(newTile)
                    showAddSheet = false
                },
                onDismiss = { showAddSheet = false },
                isAdd = true,
            )
        }
    }
}

// ── Row showing one tile in the grid editor ───────────────────────────────────

@Composable
private fun TileEditorRow(
    tile: DashboardTileConfig,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tile.metricId.displayLabel,
                style = type.bodyMedium,
                color = colors.fg,
            )
            Text(
                tile.widgetType.name.lowercase().replaceFirstChar { it.uppercase() },
                style = type.bodySmall,
                color = colors.fgMuted,
            )
        }

        // Up / Down reorder buttons
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                Icons.Rounded.KeyboardArrowUp,
                contentDescription = "Move up",
                tint = if (canMoveUp) colors.fgMuted else colors.fgDim,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Move down",
                tint = if (canMoveDown) colors.fgMuted else colors.fgDim,
                modifier = Modifier.size(20.dp),
            )
        }

        // Remove button
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove",
                tint = colors.danger,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Bottom sheet for editing / adding a tile ──────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileEditSheet(
    tile: DashboardTileConfig,
    onSave: (DashboardTileConfig) -> Unit,
    onDismiss: () -> Unit,
    isAdd: Boolean = false,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    var selectedMetric by remember { mutableStateOf(tile.metricId) }
    var selectedWidget by remember { mutableStateOf(tile.widgetType) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (isAdd) "Add Tile" else "Edit Tile",
            style = type.bodyMedium,
            fontWeight = FontWeight.W600,
            color = colors.fg,
        )

        // Metric selector
        Text("Metric", style = type.labelSmall, color = colors.fgDim, fontWeight = FontWeight.W600)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricId.entries.forEach { metricId ->
                MetricChip(
                    label = metricId.displayLabel,
                    selected = selectedMetric == metricId,
                    onClick = { selectedMetric = metricId },
                )
            }
        }

        // Widget type selector
        Text("Widget Type", style = type.labelSmall, color = colors.fgDim, fontWeight = FontWeight.W600)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetType.entries.forEach { wt ->
                MetricChip(
                    label = wt.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = selectedWidget == wt,
                    onClick = { selectedWidget = wt },
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface2)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Cancel", style = type.bodyMedium, color = colors.fgMuted)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accent.base)
                    .clickable { onSave(DashboardTileConfig(selectedMetric, selectedWidget)) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isAdd) "Add" else "Save",
                    style = type.bodyMedium,
                    color = colors.bg,
                    fontWeight = FontWeight.W600,
                )
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = PiDriveTheme.typography.labelSmall,
        color = PiDriveTheme.colors.fgDim,
        fontWeight = FontWeight.W600,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/**
 * Selectable pill chip used in the featured-metric row and the metric selector in the
 * tile edit sheet. Highlighted with accent border + soft background when [selected].
 */
@Composable
private fun MetricChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = PiDriveTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.accent.soft else colors.surface2)
            .then(
                if (selected) Modifier.border(1.dp, colors.accent.base, shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = PiDriveTheme.typography.labelSmall,
            color = if (selected) colors.accent.base else colors.fgMuted,
            fontWeight = if (selected) FontWeight.W600 else FontWeight.Normal,
        )
    }
}
