package ghart.space.pi_drive.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ghart.space.pi_drive.shared.ui.theme.PiDriveTheme

/**
 * Pi Drive top app bar.
 *
 * Shows a screen [title] with an optional back button (for sub-screens)
 * and an optional trailing [actions] slot for context-specific controls
 * (e.g. the LIVE pill on the dashboard or a filter icon on trips).
 *
 * The bottom navigation bar is hidden during the connect flow — those screens
 * use a full-screen layout with their own header instead of this bar.
 *
 * @param title       The screen name to display.
 * @param onBack      If non-null, a back arrow icon is shown and calls this lambda.
 * @param actions     Optional composable placed at the trailing end of the bar.
 * @param modifier    Applied to the [TopAppBar] container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiDriveTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = PiDriveTheme.colors
    val type = PiDriveTheme.typography

    TopAppBar(
        title = {
            Text(
                text = title,
                style = type.titleMedium,
                color = colors.fg,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = colors.fg,
                    )
                }
            }
        },
        actions = actions,
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.bg,
            titleContentColor = colors.fg,
            navigationIconContentColor = colors.fg,
            actionIconContentColor = colors.fg,
        ),
    )
}
