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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ghart.space.pi_drive.shared.data.model.MetricId
import ghart.space.pi_drive.shared.settings.AASlotConfig
import ghart.space.pi_drive.shared.settings.AAWidgetType
import ghart.space.pi_drive.shared.ui.components.PDCard
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme
import ghart.space.pi_drive.ui.viewmodel.AALayoutViewModel
import kotlinx.coroutines.launch

// ── Screen tabs ───────────────────────────────────────────────────────────────

private enum class AALayoutTab(val label: String) {
    DIALS("Dials"),
    GRAPHS("Graphs"),
    SPLIT("Split"),
}

// ── Root composable ───────────────────────────────────────────────────────────

/**
 * Android Auto layout settings editor.
 *
 * Lets the user configure which metrics appear on each of the three AA screens
 * (Dials, Graphs, and Split Panel) and in what order. Changes are applied live
 * to [AADataBridge] via the reactive [AALayoutManager] flow.
 *
 * Navigation: reached from Settings > Data & Display > Android Auto layout.
 */
@Composable
fun SettingsAALayoutScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: AALayoutViewModel = hiltViewModel(),
) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    var selectedTab by remember { mutableIntStateOf(0) }

    // Split-panel sub-page state (page 1 = Hero, page 2 = Tiles)
    var splitPage by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Tab row ───────────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = colors.bg,
            contentColor = colors.accent.base,
        ) {
            AALayoutTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = tab.label,
                            style = type.bodySmall,
                            fontWeight = if (selectedTab == index) FontWeight.W600 else FontWeight.Normal,
                            color = if (selectedTab == index) colors.accent.base else colors.fgMuted,
                        )
                    },
                )
            }
        }

        // ── Tab content ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (AALayoutTab.entries[selectedTab]) {
                AALayoutTab.DIALS -> DialsTabContent(
                    slots = layout.dialsSlots,
                    onUpdateSlots = viewModel::updateDialsSlots,
                    onReset = viewModel::resetDials,
                )
                AALayoutTab.GRAPHS -> GraphsTabContent(
                    slots = layout.graphsSlots,
                    onUpdateSlots = viewModel::updateGraphsSlots,
                    onReset = viewModel::resetGraphs,
                )
                AALayoutTab.SPLIT -> SplitTabContent(
                    page1Slots = layout.splitPage1Slots,
                    page2Slots = layout.splitPage2Slots,
                    selectedPage = splitPage,
                    onSelectPage = { splitPage = it },
                    onUpdatePage1Slots = viewModel::updateSplitPage1Slots,
                    onUpdatePage2Slots = viewModel::updateSplitPage2Slots,
                    onResetPage1 = viewModel::resetSplitPage1,
                    onResetPage2 = viewModel::resetSplitPage2,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Dials tab ─────────────────────────────────────────────────────────────────

@Composable
private fun DialsTabContent(
    slots: List<AASlotConfig>,
    onUpdateSlots: (List<AASlotConfig>) -> Unit,
    onReset: () -> Unit,
) {
    var editingIndex by remember { mutableIntStateOf(-1) }

    DialsPreview(
        slots = slots,
        selectedIndex = editingIndex,
        onSelectSlot = { editingIndex = it },
    )

    AATabDescription("Full-screen grid showing 6 metric dials (2 rows × 3 columns).")

    SlotsEditorCard(
        slots = slots,
        onMoveUp = { i -> swapSlots(slots, i, i - 1, onUpdateSlots) },
        onMoveDown = { i -> swapSlots(slots, i, i + 1, onUpdateSlots) },
        onEdit = { editingIndex = it },
    )

    ResetButton("Reset Dials to defaults", onReset)

    // ── Slot editor bottom sheet ──────────────────────────────────────────
    if (editingIndex in slots.indices) {
        SlotEditorSheet(
            slot = slots[editingIndex],
            onSave = { updated ->
                val mutable = slots.toMutableList()
                mutable[editingIndex] = updated
                onUpdateSlots(mutable)
                editingIndex = -1
            },
            onDismiss = { editingIndex = -1 },
        )
    }
}

// ── Graphs tab ────────────────────────────────────────────────────────────────

@Composable
private fun GraphsTabContent(
    slots: List<AASlotConfig>,
    onUpdateSlots: (List<AASlotConfig>) -> Unit,
    onReset: () -> Unit,
) {
    var editingIndex by remember { mutableIntStateOf(-1) }

    GraphsPreview(
        slots = slots,
        selectedIndex = editingIndex,
        onSelectSlot = { editingIndex = it },
    )

    AATabDescription("List view with 4 metric rows, each showing a value and optional trend arrow.")

    SlotsEditorCard(
        slots = slots,
        onMoveUp = { i -> swapSlots(slots, i, i - 1, onUpdateSlots) },
        onMoveDown = { i -> swapSlots(slots, i, i + 1, onUpdateSlots) },
        onEdit = { editingIndex = it },
    )

    ResetButton("Reset Graphs to defaults", onReset)

    if (editingIndex in slots.indices) {
        SlotEditorSheet(
            slot = slots[editingIndex],
            onSave = { updated ->
                val mutable = slots.toMutableList()
                mutable[editingIndex] = updated
                onUpdateSlots(mutable)
                editingIndex = -1
            },
            onDismiss = { editingIndex = -1 },
        )
    }
}

// ── Split panel tab ───────────────────────────────────────────────────────────

@Composable
private fun SplitTabContent(
    page1Slots: List<AASlotConfig>,
    page2Slots: List<AASlotConfig>,
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
    onUpdatePage1Slots: (List<AASlotConfig>) -> Unit,
    onUpdatePage2Slots: (List<AASlotConfig>) -> Unit,
    onResetPage1: () -> Unit,
    onResetPage2: () -> Unit,
) {
    var editingIndex by remember(selectedPage) { mutableIntStateOf(-1) }

    val activeSlotsForEditing = if (selectedPage == 0) page1Slots else page2Slots

    SplitPreview(
        page1Slots = page1Slots,
        page2Slots = page2Slots,
        activePage = selectedPage,
        selectedIndex = editingIndex,
        onSelectSlot = { editingIndex = it },
    )

    // Page selector tabs
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("Page 1 — Hero", "Page 2 — Tiles").forEachIndexed { index, label ->
            val isSelected = selectedPage == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) PiDriveTheme.colors.accent.soft
                        else PiDriveTheme.colors.surface2
                    )
                    .border(
                        width = if (isSelected) 1.dp else 0.dp,
                        color = if (isSelected) PiDriveTheme.colors.accent.base else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelectPage(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = PiDriveTheme.typography.bodySmall,
                    color = if (isSelected) PiDriveTheme.colors.accent.base else PiDriveTheme.colors.fgMuted,
                    fontWeight = if (isSelected) FontWeight.W600 else FontWeight.Normal,
                )
            }
        }
    }

    val pageDesc = if (selectedPage == 0)
        "Hero value (slot 1) + 4 stat pills below it."
    else
        "Six compact metric tiles in a 2×3 grid."
    AATabDescription(pageDesc)

    SlotsEditorCard(
        slots = activeSlotsForEditing,
        onMoveUp = { i ->
            swapSlots(activeSlotsForEditing, i, i - 1) { updated ->
                if (selectedPage == 0) onUpdatePage1Slots(updated)
                else onUpdatePage2Slots(updated)
            }
        },
        onMoveDown = { i ->
            swapSlots(activeSlotsForEditing, i, i + 1) { updated ->
                if (selectedPage == 0) onUpdatePage1Slots(updated)
                else onUpdatePage2Slots(updated)
            }
        },
        onEdit = { editingIndex = it },
    )

    ResetButton(
        label = if (selectedPage == 0) "Reset Page 1 to defaults" else "Reset Page 2 to defaults",
        onClick = if (selectedPage == 0) onResetPage1 else onResetPage2,
    )

    if (editingIndex in activeSlotsForEditing.indices) {
        SlotEditorSheet(
            slot = activeSlotsForEditing[editingIndex],
            onSave = { updated ->
                val mutable = activeSlotsForEditing.toMutableList()
                mutable[editingIndex] = updated
                if (selectedPage == 0) onUpdatePage1Slots(mutable)
                else onUpdatePage2Slots(mutable)
                editingIndex = -1
            },
            onDismiss = { editingIndex = -1 },
        )
    }
}

// ── Preview composables ───────────────────────────────────────────────────────

/**
 * Simulated 16:9 Dials screen preview with a 2×3 slot grid.
 *
 * Tapping a slot selects it for editing. The selected slot is highlighted
 * with the accent color.
 */
@Composable
private fun DialsPreview(
    slots: List<AASlotConfig>,
    selectedIndex: Int,
    onSelectSlot: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF08080F))
            .border(1.dp, PiDriveTheme.colors.borderS, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            (0 until 2).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    (0 until 3).forEach { col ->
                        val index = row * 3 + col
                        val slot = slots.getOrNull(index)
                        PreviewSlotBox(
                            label = slot?.displayLabel ?: "",
                            isSelected = index == selectedIndex,
                            onClick = { if (slot != null) onSelectSlot(index) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simulated Graphs screen preview with 4 list rows.
 */
@Composable
private fun GraphsPreview(
    slots: List<AASlotConfig>,
    selectedIndex: Int,
    onSelectSlot: (Int) -> Unit,
) {
    val colors = PiDriveTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF08080F))
            .border(1.dp, colors.borderS, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slots.take(4).forEachIndexed { index, slot ->
                val isSelected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) colors.accent.soft.copy(alpha = 0.3f)
                            else colors.surface2.copy(alpha = 0.4f)
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) colors.accent.base else Color.Transparent,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .clickable { onSelectSlot(index) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = slot.displayLabel,
                        style = PiDriveTheme.typography.labelSmall,
                        color = if (isSelected) colors.accent.base else colors.fg,
                        fontWeight = if (isSelected) FontWeight.W600 else FontWeight.Normal,
                    )
                    Text(
                        text = "—",
                        style = PiDriveTheme.typography.labelSmall,
                        color = if (isSelected) colors.accent.base else colors.fgMuted,
                    )
                }
            }
        }
    }
}

/**
 * Simulated 1/3-width Split Panel preview, switching between page 1 (hero) and page 2 (tiles).
 */
@Composable
private fun SplitPreview(
    page1Slots: List<AASlotConfig>,
    page2Slots: List<AASlotConfig>,
    activePage: Int,
    selectedIndex: Int,
    onSelectSlot: (Int) -> Unit,
) {
    val colors = PiDriveTheme.colors
    val slots = if (activePage == 0) page1Slots else page2Slots

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)  // simulate 1/3 width side panel
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF08080F))
                .border(1.dp, colors.borderS, RoundedCornerShape(8.dp))
                .padding(6.dp),
        ) {
            if (activePage == 0) {
                // Hero + pills layout
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Hero (slot 0) — larger
                    slots.getOrNull(0)?.let { hero ->
                        PreviewSlotBox(
                            label = hero.displayLabel,
                            isSelected = selectedIndex == 0,
                            onClick = { onSelectSlot(0) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(2f),
                        )
                    }
                    // Pills (slots 1-4)
                    slots.drop(1).take(4).forEachIndexed { idx, slot ->
                        PreviewSlotBox(
                            label = slot.displayLabel,
                            isSelected = selectedIndex == idx + 1,
                            onClick = { onSelectSlot(idx + 1) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            } else {
                // 3×2 tile grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    (0 until 2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            (0 until 3).forEach { col ->
                                val index = row * 3 + col
                                val slot = slots.getOrNull(index)
                                PreviewSlotBox(
                                    label = slot?.displayLabel ?: "",
                                    isSelected = index == selectedIndex,
                                    onClick = { if (slot != null) onSelectSlot(index) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A single slot box in the AA screen preview with selection highlight. */
@Composable
private fun PreviewSlotBox(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = PiDriveTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isSelected) colors.accent.soft.copy(alpha = 0.3f)
                else colors.surface2.copy(alpha = 0.3f),
            )
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) colors.accent.base else Color.Transparent,
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "—",
                style = PiDriveTheme.typography.bodySmall,
                color = if (isSelected) colors.accent.base else colors.fg,
                fontWeight = FontWeight.W600,
            )
            Text(
                text = label,
                style = PiDriveTheme.typography.labelSmall,
                color = if (isSelected) colors.accent.base else colors.fgMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Slot list editor ──────────────────────────────────────────────────────────

/**
 * Card listing all slots with up/down reorder controls.
 *
 * Tapping a row opens the [SlotEditorSheet].
 */
@Composable
private fun SlotsEditorCard(
    slots: List<AASlotConfig>,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onEdit: (Int) -> Unit,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    Text(
        text = "SLOTS",
        style = type.labelSmall,
        color = colors.fgDim,
        fontWeight = FontWeight.W600,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    PDCard(contentPadding = 0.dp) {
        slots.forEachIndexed { index, slot ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(index) }
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = slot.metricId.displayLabel,
                        style = type.bodyMedium,
                        color = colors.fg,
                    )
                    Text(
                        text = buildString {
                            append(slot.widgetType.name.lowercase().replaceFirstChar { it.uppercase() })
                            if (slot.label != null) append(" · \"${slot.label}\"")
                        },
                        style = type.bodySmall,
                        color = colors.fgMuted,
                    )
                }

                IconButton(onClick = { onMoveUp(index) }, enabled = index > 0) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = if (index > 0) colors.fgMuted else colors.fgDim,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { onMoveDown(index) }, enabled = index < slots.lastIndex) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = if (index < slots.lastIndex) colors.fgMuted else colors.fgDim,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (index < slots.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    color = colors.borderS,
                )
            }
        }
    }
}

// ── Slot editor bottom sheet ──────────────────────────────────────────────────

/**
 * Bottom sheet for editing a single [AASlotConfig].
 *
 * Lets the user pick a [MetricId], a [AAWidgetType], and an optional custom label.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SlotEditorSheet(
    slot: AASlotConfig,
    onSave: (AASlotConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    var selectedMetric by remember { mutableStateOf(slot.metricId) }
    var selectedWidget by remember { mutableStateOf(slot.widgetType) }
    var customLabel by remember { mutableStateOf(slot.label ?: "") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Edit Slot",
                style = type.bodyMedium,
                fontWeight = FontWeight.W600,
                color = colors.fg,
            )

            // Metric picker
            Text("Metric", style = type.labelSmall, color = colors.fgDim, fontWeight = FontWeight.W600)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricId.entries.forEach { metricId ->
                    AAMetricChip(
                        label = metricId.displayLabel,
                        selected = selectedMetric == metricId,
                        onClick = { selectedMetric = metricId },
                    )
                }
            }

            // Widget type picker
            Text("Style", style = type.labelSmall, color = colors.fgDim, fontWeight = FontWeight.W600)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AAWidgetType.entries.forEach { wt ->
                    AAMetricChip(
                        label = wt.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = selectedWidget == wt,
                        onClick = { selectedWidget = wt },
                    )
                }
            }

            // Custom label (optional)
            Text(
                "Label (optional)",
                style = type.labelSmall,
                color = colors.fgDim,
                fontWeight = FontWeight.W600,
            )
            OutlinedTextField(
                value = customLabel,
                onValueChange = { customLabel = it },
                placeholder = {
                    Text(
                        selectedMetric.displayLabel.uppercase(),
                        style = type.bodySmall,
                        color = colors.fgDim,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

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
                        .clickable { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
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
                        .clickable {
                            val updated = AASlotConfig(
                                metricId = selectedMetric,
                                widgetType = selectedWidget,
                                label = customLabel.trim().ifBlank { null },
                            )
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onSave(updated) }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Save",
                        style = type.bodyMedium,
                        color = colors.bg,
                        fontWeight = FontWeight.W600,
                    )
                }
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

/** One-line description shown below the preview for each tab. */
@Composable
private fun AATabDescription(text: String) {
    Text(
        text = text,
        style = PiDriveTheme.typography.bodySmall,
        color = PiDriveTheme.colors.fgDim,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Secondary button row for resetting a screen's layout to defaults. */
@Composable
private fun ResetButton(label: String, onClick: () -> Unit) {
    val colors = PiDriveTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = null,
            tint = colors.fgMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            style = PiDriveTheme.typography.bodySmall,
            color = colors.fgMuted,
        )
    }
}

/**
 * Selectable pill chip used in the metric and widget-type pickers.
 *
 * Highlighted with accent border and soft background when [selected].
 */
@Composable
private fun AAMetricChip(
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
                if (selected) Modifier.border(1.dp, colors.accent.base, shape) else Modifier
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

/** Swaps elements at [fromIndex] and [toIndex] in [slots] and emits the updated list. */
private fun swapSlots(
    slots: List<AASlotConfig>,
    fromIndex: Int,
    toIndex: Int,
    onUpdate: (List<AASlotConfig>) -> Unit,
) {
    if (fromIndex !in slots.indices || toIndex !in slots.indices) return
    val mutable = slots.toMutableList()
    val tmp = mutable[fromIndex]
    mutable[fromIndex] = mutable[toIndex]
    mutable[toIndex] = tmp
    onUpdate(mutable)
}
