"""
L3 contract tests — the Android wire contract over a REAL socket (TESTING.md §5).

These boot the actual Flask app on a TCP port and POST the exact payloads the
Android app sends (tests/fixtures/, drift-guarded against REQUIREMENTS.md §5.5),
including zstd-on-the-wire, idempotent re-upload, batch shapes, and every error
status in the contract table. Stored rows are verified through the database —
not through the same code path that wrote them.
"""

from __future__ import annotations

import pytest
import requests
from sqlalchemy import text

from tests.fixtures import compression, payloads

pytestmark = pytest.mark.e2e

VIN = payloads.CANONICAL_VIN


@pytest.fixture(autouse=True)
def _clean_canonical_vin(db_engine):
    """Each contract test starts and ends with no rows for the canonical VIN."""

    def wipe():
        with db_engine.begin() as conn:
            conn.execute(text("DELETE FROM driving_events WHERE vin = :v"), {"v": VIN})
            conn.execute(text("DELETE FROM telemetry WHERE vin = :v"), {"v": VIN})
            conn.execute(text("DELETE FROM vehicles WHERE vin = :v"), {"v": VIN})

    wipe()
    yield
    wipe()


def _counts(db_engine) -> tuple[int, int]:
    """(telemetry_rows, event_rows) stored for the canonical VIN."""
    with db_engine.connect() as conn:
        t = conn.execute(
            text("SELECT count(*) FROM telemetry WHERE vin = :v"), {"v": VIN}
        ).scalar()
        e = conn.execute(
            text("SELECT count(*) FROM driving_events WHERE vin = :v"), {"v": VIN}
        ).scalar()
    return int(t), int(e)


# ── Contract row 1: the canonical §5.5 payload ─────────────────────────────────


def test_single_payload_full_contract(live_server, api_headers, db_engine):
    resp = requests.post(
        f"{live_server}/telemetry",
        json=payloads.single_payload(),
        headers={**api_headers, "X-Device-Id": payloads.CANONICAL_DEVICE_ID},
        timeout=10,
    )
    assert resp.status_code == 200
    assert resp.json() == {"ok": True, "accepted": 1, "vehicles": [VIN]}

    telemetry_rows, event_rows = _counts(db_engine)
    assert telemetry_rows == 1
    assert event_rows == 2

    with db_engine.connect() as conn:
        vehicle = conn.execute(
            text("SELECT device_id FROM vehicles WHERE vin = :v"), {"v": VIN}
        ).first()
    assert vehicle is not None  # auto-registered
    assert vehicle[0] == payloads.CANONICAL_DEVICE_ID


# ── Row 2: zstd on the wire ────────────────────────────────────────────────────


def test_zstd_compressed_payload(live_server, api_headers, db_engine):
    body = compression.zstd_compress(
        compression.to_json_bytes(payloads.single_payload())
    )
    resp = requests.post(
        f"{live_server}/telemetry",
        data=body,
        headers={
            **api_headers,
            "Content-Type": "application/json",
            "Content-Encoding": "zstd",
        },
        timeout=10,
    )
    assert resp.status_code == 200
    assert resp.json()["accepted"] == 1
    assert _counts(db_engine)[0] == 1


def test_gzip_compressed_payload(live_server, api_headers, db_engine):
    body = compression.gzip_compress(
        compression.to_json_bytes(payloads.single_payload())
    )
    resp = requests.post(
        f"{live_server}/telemetry",
        data=body,
        headers={
            **api_headers,
            "Content-Type": "application/json",
            "Content-Encoding": "gzip",
        },
        timeout=10,
    )
    assert resp.status_code == 200
    assert _counts(db_engine)[0] == 1


# ── Row 3: idempotent re-upload ────────────────────────────────────────────────


def test_reupload_is_idempotent(live_server, api_headers, db_engine):
    for _ in range(2):
        resp = requests.post(
            f"{live_server}/telemetry",
            json=payloads.single_payload(),
            headers=api_headers,
            timeout=10,
        )
        assert resp.status_code == 200
        assert resp.json()["accepted"] == 1

    telemetry_rows, event_rows = _counts(db_engine)
    assert telemetry_rows == 1  # the idempotency contract
    assert event_rows == 2  # events deduped too


# ── Row 4: batch shapes ────────────────────────────────────────────────────────


def test_bare_array_batch(live_server, api_headers, db_engine):
    resp = requests.post(
        f"{live_server}/telemetry",
        json=payloads.batch_bare_array(3),
        headers=api_headers,
        timeout=10,
    )
    assert resp.status_code == 200
    assert resp.json()["accepted"] == 3
    assert _counts(db_engine)[0] == 3


def test_wrapped_batch(live_server, api_headers, db_engine):
    resp = requests.post(
        f"{live_server}/telemetry",
        json=payloads.batch_wrapped(2),
        headers=api_headers,
        timeout=10,
    )
    assert resp.status_code == 200
    assert resp.json()["accepted"] == 2


# ── Rows 5–9: error statuses ───────────────────────────────────────────────────


def test_missing_vin_400(live_server, api_headers):
    resp = requests.post(
        f"{live_server}/telemetry",
        json=payloads.payload_missing_vin(),
        headers=api_headers,
        timeout=10,
    )
    assert resp.status_code == 400
    assert "error" in resp.json()


def test_bad_key_401(live_server):
    resp = requests.post(
        f"{live_server}/telemetry",
        json=payloads.single_payload(),
        headers={"Authorization": "Bearer wrong-key"},
        timeout=10,
    )
    assert resp.status_code == 401


def test_oversized_decompressed_413(live_server, api_headers, integration_app):
    max_bytes = integration_app.config["MAX_BODY_BYTES"]
    bomb = compression.zstd_compress(b"\x00" * (max_bytes + 1024))
    resp = requests.post(
        f"{live_server}/telemetry",
        data=bomb,
        headers={**api_headers, "Content-Encoding": "zstd"},
        timeout=30,
    )
    assert resp.status_code == 413


def test_malformed_json_422(live_server, api_headers):
    resp = requests.post(
        f"{live_server}/telemetry",
        data=b"{not json",
        headers={**api_headers, "Content-Type": "application/json"},
        timeout=10,
    )
    assert resp.status_code == 422


def test_schema_violation_422_with_detail(live_server, api_headers):
    bad = payloads.single_payload()
    bad["obd"]["rpm"] = "definitely-not-a-number"
    resp = requests.post(
        f"{live_server}/telemetry", json=bad, headers=api_headers, timeout=10
    )
    assert resp.status_code == 422
    assert "rpm" in resp.json()["error"]  # Pydantic detail included


def test_device_id_mismatch_400(live_server, api_headers):
    resp = requests.post(
        f"{live_server}/telemetry",
        json=payloads.single_payload(),
        headers={**api_headers, "X-Device-Id": "pd-SOMETHING-ELSE"},
        timeout=10,
    )
    assert resp.status_code == 400


# ── Row 10: /telemetry/latest ──────────────────────────────────────────────────


def test_latest_after_ingest(live_server, api_headers):
    requests.post(
        f"{live_server}/telemetry",
        json=payloads.single_payload(),
        headers=api_headers,
        timeout=10,
    )
    resp = requests.get(
        f"{live_server}/telemetry/latest",
        params={"vin": VIN},
        headers=api_headers,
        timeout=10,
    )
    assert resp.status_code == 200
    assert resp.json() == {
        "vin": VIN,
        "latest_timestamp": payloads.CANONICAL_TIMESTAMP,
    }


def test_latest_unknown_vin_404(live_server, api_headers):
    resp = requests.get(
        f"{live_server}/telemetry/latest",
        params={"vin": "NOSUCHVIN000000"},
        headers=api_headers,
        timeout=10,
    )
    assert resp.status_code == 404


def test_latest_requires_auth(live_server):
    resp = requests.get(
        f"{live_server}/telemetry/latest", params={"vin": VIN}, timeout=10
    )
    assert resp.status_code == 401


# ── Type fidelity over the full wire path (Java bug #2 guard) ──────────────────


def test_type_fidelity_end_to_end(live_server, api_headers, db_engine):
    requests.post(
        f"{live_server}/telemetry",
        json=payloads.payload_type_fidelity(),
        headers=api_headers,
        timeout=10,
    )
    with db_engine.connect() as conn:
        rpm, speed, throttle, voltage = conn.execute(
            text(
                "SELECT rpm, speed_kmh, throttle_pct, battery_voltage "
                "FROM telemetry WHERE vin = :v"
            ),
            {"v": VIN},
        ).one()
    assert rpm == 3175
    assert speed == 88.0
    assert throttle == 33.7  # float fidelity through JSON → Pydantic → Postgres
    assert voltage == 14.27
