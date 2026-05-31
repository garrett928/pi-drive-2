package ghart.space.pi_drive.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ghart.space.pi_drive.shared.settings.DevSettingsManager
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel for the developer settings screen.
 *
 * Delegates all reads and writes to [DevSettingsManager]. The developer settings
 * screen is hidden behind a 7-tap unlock on the version row in Settings > App.
 *
 * @param devSettingsManager Persists TCP/demo mode overrides to SharedPreferences.
 */
@HiltViewModel
class DevSettingsViewModel @Inject constructor(
    private val devSettingsManager: DevSettingsManager,
) : ViewModel() {

    /** Current developer settings as a reactive [StateFlow]. */
    val settings: StateFlow<DevSettingsManager.DevSettings> = devSettingsManager.settings

    /** Persists [settings] and emits the new value on [settings]. */
    fun update(settings: DevSettingsManager.DevSettings) = devSettingsManager.update(settings)

    /** Unlocks the developer settings screen permanently. */
    fun unlock() = devSettingsManager.unlock()

    /** Resets all developer settings to defaults and disables dev mode. */
    fun reset() = devSettingsManager.reset()
}
