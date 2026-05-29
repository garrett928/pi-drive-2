package ghart.space.pi_drive.shared.obd

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * [BroadcastReceiver] that listens for Bluetooth ACL disconnect events and
 * notifies a [ConnectionManager] when the paired OBD adapter drops its connection.
 *
 * Only fires [onDisconnected] for the device address recorded via [watchedAddress].
 * Other Bluetooth devices that disconnect are silently ignored.
 *
 * Usage (register in the lifecycle-aware component, e.g. a Service or ViewModel host):
 * ```kotlin
 * val watcher = AdapterWatcher(connectionManager::onAdapterDisconnected)
 * watcher.watchedAddress = connectedDeviceAddress
 * AdapterWatcher.register(context, watcher)
 * // … later …
 * AdapterWatcher.unregister(context, watcher)
 * ```
 *
 * @param onDisconnected Callback invoked with the disconnected device's MAC address.
 */
class AdapterWatcher(
    private val onDisconnected: (address: String) -> Unit,
) : BroadcastReceiver() {

    /**
     * MAC address of the device to watch (e.g. "AA:BB:CC:DD:EE:FF").
     * Set this to the connected OBD adapter's address before registering.
     * Null means all ACL disconnects are ignored.
     */
    var watchedAddress: String? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        val address = device?.address ?: return
        if (watchedAddress != null && address != watchedAddress) return

        onDisconnected(address)
    }

    companion object {
        /**
         * Registers [watcher] to receive [BluetoothDevice.ACTION_ACL_DISCONNECTED] broadcasts.
         * Must be balanced with a call to [unregister] to avoid leaking the receiver.
         */
        fun register(context: Context, watcher: AdapterWatcher) {
            context.registerReceiver(
                watcher,
                IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED),
            )
        }

        /** Unregisters a previously [register]ed [watcher]. */
        fun unregister(context: Context, watcher: AdapterWatcher) {
            try {
                context.unregisterReceiver(watcher)
            } catch (_: IllegalArgumentException) {
                // Receiver was not registered — safe to ignore
            }
        }
    }
}
