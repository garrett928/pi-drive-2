"""
Canonical telemetry payloads — the single source of truth for tests.

These mirror REQUIREMENTS.md §5.5 (which is itself copied from the Android
client contract in ../pi-drive-android/implementation/phase-07-telemetry.md).
The companion `sample_payload.json` is the verbatim §5.5 example for curl/scripts;
`test_fixtures.py` asserts `single_payload()` equals it field-for-field so the
fixtures and the documented contract can never silently drift apart.

All keys are snake_case. `vin` and `timestamp` are always present; nested
objects/fields may be absent when a signal is unsupported.
"""

from __future__ import annotations

import copy

# ── Canonical anchors (match REQUIREMENTS.md §5.5) ─────────────────────────────

CANONICAL_VIN = "1G1JC524417100001"
CANONICAL_TIMESTAMP = "2026-05-24T22:15:30.123Z"
CANONICAL_DEVICE_ID = "pd-rxv7a3-k9892"
_EVENT_TIMESTAMP = "2026-05-24T22:15:29.500Z"


def single_payload() -> dict:
    """
    One full TelemetryPayload exactly as the Android app emits it (§5.5).

    Includes the two events (ACCELERATION + G_FORCE strategies) and a mix of
    int and float OBD signals so type-fidelity can be asserted downstream.
    Returns a fresh deep copy each call so callers may mutate freely.
    """
    return copy.deepcopy(
        {
            "timestamp": CANONICAL_TIMESTAMP,
            "device_id": CANONICAL_DEVICE_ID,
            "vin": CANONICAL_VIN,
            "location": {"lat": 37.7749, "lng": -122.4194, "speed_gps": 65.2},
            "obd": {
                "speed_kmh": 105,
                "rpm": 2400,
                "coolant_temp_c": 92,
                "intake_air_temp_c": 35,
                "throttle_pct": 22.5,
                "fuel_level_pct": 68.0,
                "oil_temp_c": 95,
                "maf_gps": 12.5,
                "fuel_rate_lph": None,
                "battery_voltage": 14.2,
            },
            "calculated": {"fuel_economy_mpg": 28.5, "fuel_economy_kml": 12.1},
            "accel_mps2": 0.45,
            "events": [
                {
                    "strategy": "ACCELERATION",
                    "type": "HARD_BRAKE",
                    "timestamp": _EVENT_TIMESTAMP,
                    "duration_ms": 1200,
                    "rate_mph_s": -11.2,
                    "peak_accel_mps2": -5.0,
                    "start_speed_mph": 59,
                    "end_speed_mph": 38,
                    "sources": ["OBD", "GPS"],
                },
                {
                    "strategy": "G_FORCE",
                    "type": "HARD_BRAKE",
                    "timestamp": _EVENT_TIMESTAMP,
                    "duration_ms": 1200,
                    "peak_g": 0.51,
                    "peak_accel_mps2": -5.0,
                    "start_speed_mph": 59,
                    "end_speed_mph": 38,
                    "sources": ["OBD", "GPS", "ACCELEROMETER"],
                },
            ],
        }
    )


def _with_timestamp(seq: int) -> dict:
    """A copy of the canonical snapshot with a distinct timestamp (for batches)."""
    p = single_payload()
    # Vary only the seconds so each (vin, time) is a distinct row.
    p["timestamp"] = f"2026-05-24T22:15:{30 + seq:02d}.000Z"
    # Drop events from batch members to keep batch assertions focused on rows.
    p.pop("events", None)
    return p


def batch_bare_array(n: int = 3) -> list[dict]:
    """A batch as a bare JSON array of n snapshots with distinct timestamps."""
    return [_with_timestamp(i) for i in range(n)]


def batch_wrapped(n: int = 3) -> dict:
    """A batch in the {"snapshots": [...]} wrapper shape (§5.2)."""
    return {"snapshots": batch_bare_array(n)}


def payload_with_extra_fields() -> dict:
    """
    A snapshot carrying unknown top-level and obd keys.

    The server must preserve these in the `extra` JSONB column rather than
    rejecting them (forward-compat as the app adds PIDs).
    """
    p = single_payload()
    p["some_future_top_level"] = "preserve-me"
    p["obd"]["future_pid_x"] = 999
    return p


def payload_type_fidelity() -> dict:
    """
    A snapshot whose int signals must stay ints and float signals stay floats.

    Guards against the Java server's blanket long-truncation (Known Bug #2):
    rpm/speed_kmh are ints; throttle_pct/battery_voltage are floats.
    """
    p = single_payload()
    p["obd"]["rpm"] = 3175  # must remain int
    p["obd"]["speed_kmh"] = 88  # must remain int
    p["obd"]["throttle_pct"] = 33.7  # must remain float
    p["obd"]["battery_voltage"] = 14.27  # must remain float
    p.pop("events", None)
    return p


def payload_missing_vin() -> dict:
    """A snapshot with no vin — the server must reject with 400."""
    p = single_payload()
    p.pop("vin", None)
    return p
