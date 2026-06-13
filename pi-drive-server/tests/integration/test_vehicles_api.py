"""
L2 integration tests for the vehicles management API (/api/v1/vehicles).

Run through the Flask test client against the real migrated TimescaleDB. Routes
commit, so every test uses its own unique VINs and removes them afterwards via
the DELETE endpoint itself (which also exercises the cascade).
"""

from __future__ import annotations

import pytest

pytestmark = pytest.mark.integration


def _snapshot(vin: str, ts: str, **obd) -> dict:
    return {"vin": vin, "timestamp": ts, "obd": obd or {"speed_kmh": 50}}


def _seed(client, headers, vin: str, n: int = 1, events: bool = False) -> None:
    """Ingest n snapshots (and optionally 2 events) for `vin` via the real API."""
    body = [
        _snapshot(vin, f"2026-04-01T10:{i:02d}:00Z", speed_kmh=40 + i) for i in range(n)
    ]
    if events:
        body[0]["events"] = [
            {
                "strategy": "ACCELERATION",
                "type": "HARD_BRAKE",
                "timestamp": "2026-04-01T10:00:30Z",
                "duration_ms": 900,
            },
            {
                "strategy": "G_FORCE",
                "type": "HARD_ACCEL",
                "timestamp": "2026-04-01T10:00:45Z",
                "peak_g": 0.4,
            },
        ]
    resp = client.post("/telemetry", json=body, headers=headers)
    assert resp.status_code == 200, resp.get_json()


def _cleanup(client, headers, *vins: str) -> None:
    for vin in vins:
        client.delete(f"/api/v1/vehicles/{vin}?confirm=true", headers=headers)


def test_list_includes_seeded_vehicle_with_counts(integration_client, api_headers):
    vin = "VEHLIST0000000001"
    try:
        _seed(integration_client, api_headers, vin, n=2, events=True)
        resp = integration_client.get("/api/v1/vehicles?limit=500", headers=api_headers)
        assert resp.status_code == 200
        body = resp.get_json()
        assert body["total"] >= 1
        mine = next(v for v in body["vehicles"] if v["vin"] == vin)
        assert mine["sample_count"] == 2
        assert mine["event_count"] == 2
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_list_paginates(integration_client, api_headers):
    vins = [f"VEHPAGE000000000{i}" for i in range(3)]
    try:
        for vin in vins:
            _seed(integration_client, api_headers, vin)
        # Walk the whole collection one vehicle per page; all three must appear.
        seen: set[str] = set()
        offset = 0
        total = None
        while total is None or offset < total:
            resp = integration_client.get(
                f"/api/v1/vehicles?limit=1&offset={offset}", headers=api_headers
            )
            body = resp.get_json()
            total = body["total"]
            assert len(body["vehicles"]) <= 1
            seen.update(v["vin"] for v in body["vehicles"])
            offset += 1
        assert set(vins) <= seen
    finally:
        _cleanup(integration_client, api_headers, *vins)


def test_get_single_vehicle(integration_client, api_headers):
    vin = "VEHGET00000000001"
    try:
        _seed(integration_client, api_headers, vin, n=3)
        resp = integration_client.get(f"/api/v1/vehicles/{vin}", headers=api_headers)
        assert resp.status_code == 200
        body = resp.get_json()
        assert body["vin"] == vin
        assert body["sample_count"] == 3
        assert body["first_seen"] == "2026-04-01T10:00:00Z"
        assert body["last_seen"] == "2026-04-01T10:02:00Z"
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_get_unknown_vehicle_404(integration_client, api_headers):
    resp = integration_client.get("/api/v1/vehicles/NOSUCHVIN", headers=api_headers)
    assert resp.status_code == 404


def test_patch_metadata_persists(integration_client, api_headers):
    vin = "VEHPATCH000000001"
    try:
        _seed(integration_client, api_headers, vin)
        resp = integration_client.patch(
            f"/api/v1/vehicles/{vin}",
            json={"nickname": "Daily Driver", "make": "Chevrolet", "year": 2001},
            headers=api_headers,
        )
        assert resp.status_code == 200
        assert resp.get_json()["nickname"] == "Daily Driver"

        again = integration_client.get(f"/api/v1/vehicles/{vin}", headers=api_headers)
        assert again.get_json()["make"] == "Chevrolet"
        assert again.get_json()["year"] == 2001
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_patch_vin_rejected(integration_client, api_headers):
    vin = "VEHPATCH000000002"
    try:
        _seed(integration_client, api_headers, vin)
        resp = integration_client.patch(
            f"/api/v1/vehicles/{vin}",
            json={"vin": "HIJACKED000000001"},
            headers=api_headers,
        )
        assert resp.status_code == 400
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_delete_requires_confirm(integration_client, api_headers):
    vin = "VEHDEL00000000001"
    try:
        _seed(integration_client, api_headers, vin)
        resp = integration_client.delete(f"/api/v1/vehicles/{vin}", headers=api_headers)
        assert resp.status_code == 400
        # Still there.
        assert (
            integration_client.get(f"/api/v1/vehicles/{vin}", headers=api_headers).status_code
            == 200
        )
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_delete_cascades(integration_client, api_headers):
    vin = "VEHDEL00000000002"
    _seed(integration_client, api_headers, vin, n=2, events=True)

    resp = integration_client.delete(
        f"/api/v1/vehicles/{vin}?confirm=true", headers=api_headers
    )
    assert resp.status_code == 204

    assert (
        integration_client.get(f"/api/v1/vehicles/{vin}", headers=api_headers).status_code
        == 404
    )
    rows = integration_client.get(
        f"/api/v1/telemetry?vin={vin}", headers=api_headers
    ).get_json()["rows"]
    assert rows == []  # telemetry cascaded
    events = integration_client.get(
        f"/api/v1/events?vin={vin}", headers=api_headers
    ).get_json()["events"]
    assert events == []  # events cascaded


def test_vehicles_require_auth(integration_client):
    assert integration_client.get("/api/v1/vehicles").status_code == 401
