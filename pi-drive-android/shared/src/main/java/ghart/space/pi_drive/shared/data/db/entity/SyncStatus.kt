package ghart.space.pi_drive.shared.data.db.entity

/**
 * Synchronization status of an [AutoTripEntity] with the remote telemetry server.
 *
 * - [PENDING]: Trip data has not yet been successfully uploaded.
 * - [SYNCED]: Trip data was successfully uploaded to the server.
 * - [FAILED]: Upload failed after the maximum retry limit; requires manual retry.
 */
enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED,
}
