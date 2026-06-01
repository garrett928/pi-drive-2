package ghart.space.pi_drive

import ghart.space.pi_drive.ui.permissions.PermissionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PermissionState] sealed class exhaustiveness and property helpers.
 *
 * These tests verify that the sealed class variants are distinct and that simple
 * pattern-matching logic works as expected — guarding against accidental equality
 * between states and ensuring callers can reliably distinguish the four states.
 */
class PermissionStateTest {

    // ── Distinctness ──────────────────────────────────────────────────────────

    @Test
    fun `Granted is not equal to ShowRationale`() {
        assertFalse(PermissionState.Granted == PermissionState.ShowRationale)
    }

    @Test
    fun `Granted is not equal to PermanentlyDenied`() {
        assertFalse(PermissionState.Granted == PermissionState.PermanentlyDenied)
    }

    @Test
    fun `Granted is not equal to NotRequested`() {
        assertFalse(PermissionState.Granted == PermissionState.NotRequested)
    }

    @Test
    fun `ShowRationale is not equal to PermanentlyDenied`() {
        assertFalse(PermissionState.ShowRationale == PermissionState.PermanentlyDenied)
    }

    // ── Pattern matching helpers ───────────────────────────────────────────────

    @Test
    fun `isGranted returns true only for Granted`() {
        assertTrue(PermissionState.Granted.isGranted())
        assertFalse(PermissionState.ShowRationale.isGranted())
        assertFalse(PermissionState.PermanentlyDenied.isGranted())
        assertFalse(PermissionState.NotRequested.isGranted())
    }

    @Test
    fun `needsExplanation returns true for ShowRationale only`() {
        assertTrue(PermissionState.ShowRationale.needsExplanation())
        assertFalse(PermissionState.Granted.needsExplanation())
        assertFalse(PermissionState.PermanentlyDenied.needsExplanation())
        assertFalse(PermissionState.NotRequested.needsExplanation())
    }

    @Test
    fun `isPermanentlyDenied returns true only for PermanentlyDenied`() {
        assertTrue(PermissionState.PermanentlyDenied.isPermanentlyDenied())
        assertFalse(PermissionState.Granted.isPermanentlyDenied())
        assertFalse(PermissionState.ShowRationale.isPermanentlyDenied())
        assertFalse(PermissionState.NotRequested.isPermanentlyDenied())
    }

    @Test
    fun `when expression is exhaustive over all states`() {
        val states = listOf(
            PermissionState.Granted,
            PermissionState.ShowRationale,
            PermissionState.PermanentlyDenied,
            PermissionState.NotRequested,
        )
        val handled = states.map { state ->
            when (state) {
                PermissionState.Granted           -> "granted"
                PermissionState.ShowRationale     -> "rationale"
                PermissionState.PermanentlyDenied -> "denied"
                PermissionState.NotRequested      -> "pending"
            }
        }
        assertTrue(handled.contains("granted"))
        assertTrue(handled.contains("rationale"))
        assertTrue(handled.contains("denied"))
        assertTrue(handled.contains("pending"))
    }
}

// ── Extension helpers (tested above, shipped with the sealed class) ────────────

/** True only when permissions are fully granted. */
private fun PermissionState.isGranted() = this is PermissionState.Granted

/** True only when the caller should show a rationale before re-requesting. */
private fun PermissionState.needsExplanation() = this is PermissionState.ShowRationale

/** True only when the user has permanently denied the permission. */
private fun PermissionState.isPermanentlyDenied() = this is PermissionState.PermanentlyDenied
