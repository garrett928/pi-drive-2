"""
L2 integration tests for the telemetry management API (/api/v1/telemetry):
query + pagination + field subsetting, single read, manual entry, edit, and
single/bulk delete. Real migrated TimescaleDB; tests own unique VINs and clean
up via the cascade delete.
"""

from __future__ import annotations

import pytest

pytestmark = pytest.mark.integration


def _seed_rows(client, headers, vin: str, n: int) -> list[str]:
    """Ingest n snapshots a minute apart; returns their timestamps (Z form)."""
    stamps = [f"2026-04-02T{8 + i // 60:02d}:{i % 60:02d}:00Z" for i in range(n)]
    body = [
        {"vin": vin, "timestamp": ts, "obd": {"speed_kmh": float(i), "rpm": 1000 + i}}
        for i, ts in enumerate(stamps)
    ]
    resp = client.post("/telemetry", json=body, headers=headers)
    assert resp.status_code == 200, resp.get_json()
    return stamps


def _cleanup(client, headers, vin: str) -> None:
    client.delete(f"/api/v1/vehicles/{vin}?confirm=true", headers=headers)


def test_query_paginates_and_walks(integration_client, api_headers):
    vin = "TELQ0000000000001"
    try:
        _seed_rows(integration_client, api_headers, vin, 12)

        first = integration_client.get(
            f"/api/v1/telemetry?vin={vin}&limit=5", headers=api_headers
        ).get_json()
        assert len(first["rows"]) == 5
        assert first["has_more"] is True
        assert first["limit"] == 5 and first["offset"] == 0

        collected = []
        offset = 0
        while True:
            page = integration_client.get(
                f"/api/v1/telemetry?vin={vin}&limit=5&offset={offset}",
                headers=api_headers,
            ).get_json()
            collected += page["rows"]
            if not page["has_more"]:
                break
            offset += 5
        assert len(collected) == 12
        assert len({r["time"] for r in collected}) == 12  # no dup/missed rows
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_query_orders_and_bounds(integration_client, api_headers):
    vin = "TELQ0000000000002"
    try:
        stamps = _seed_rows(integration_client, api_headers, vin, 10)

        asc = integration_client.get(
            f"/api/v1/telemetry?vin={vin}&order=asc", headers=api_headers
        ).get_json()["rows"]
        assert [r["time"] for r in asc] == stamps  # ascending == insertion order

        desc = integration_client.get(
            f"/api/v1/telemetry?vin={vin}&order=desc", headers=api_headers
        ).get_json()["rows"]
        assert [r["time"] for r in desc] == list(reversed(stamps))

        bounded = integration_client.get(
            f"/api/v1/telemetry?vin={vin}&start={stamps[2]}&end={stamps[5]}&order=asc",
            headers=api_headers,
        ).get_json()["rows"]
        assert [r["time"] for r in bounded] == stamps[2:6]  # inclusive bounds
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_query_field_subset(integration_client, api_headers):
    vin = "TELQ0000000000003"
    try:
        _seed_rows(integration_client, api_headers, vin, 2)
        rows = integration_client.get(
            f"/api/v1/telemetry?vin={vin}&fields=speed_kmh,rpm", headers=api_headers
        ).get_json()["rows"]
        # Key columns always present; only the requested signals beyond that.
        assert set(rows[0].keys()) == {"vin", "time", "speed_kmh", "rpm"}
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_query_unknown_field_400(integration_client, api_headers):
    resp = integration_client.get(
        "/api/v1/telemetry?vin=X&fields=warp_factor", headers=api_headers
    )
    assert resp.status_code == 400


def test_query_requires_vin(integration_client, api_headers):
    assert (
        integration_client.get("/api/v1/telemetry", headers=api_headers).status_code
        == 400
    )


def test_single_read_and_404(integration_client, api_headers):
    vin = "TELS0000000000001"
    try:
        stamps = _seed_rows(integration_client, api_headers, vin, 3)
        resp = integration_client.get(
            f"/api/v1/telemetry/{vin}/{stamps[1]}", headers=api_headers
        )
        assert resp.status_code == 200
        row = resp.get_json()
        assert row["time"] == stamps[1]
        assert row["rpm"] == 1001

        missing = integration_client.get(
            f"/api/v1/telemetry/{vin}/2030-01-01T00:00:00Z", headers=api_headers
        )
        assert missing.status_code == 404
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_manual_entry_creates_with_manual_source(integration_client, api_headers):
    vin = "TELM0000000000001"
    try:
        resp = integration_client.post(
            "/api/v1/telemetry",
            json={
                "vin": vin,
                "timestamp": "2026-04-03T12:00:00Z",
                "obd": {"speed_kmh": 88.5, "rpm": 2200},
            },
            headers=api_headers,
        )
        assert resp.status_code == 201
        row = resp.get_json()
        assert row["source"] == "manual"
        assert row["speed_kmh"] == 88.5

        # The vehicle was auto-registered by the manual path too.
        assert (
            integration_client.get(f"/api/v1/vehicles/{vin}", headers=api_headers).status_code
            == 200
        )
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_patch_edits_one_field(integration_client, api_headers):
    vin = "TELP0000000000001"
    try:
        stamps = _seed_rows(integration_client, api_headers, vin, 1)
        resp = integration_client.patch(
            f"/api/v1/telemetry/{vin}/{stamps[0]}",
            json={"speed_kmh": 123.4},
            headers=api_headers,
        )
        assert resp.status_code == 200
        assert resp.get_json()["speed_kmh"] == 123.4
        assert resp.get_json()["rpm"] == 1000  # untouched fields remain

        # PATCHing identity or unknown columns is rejected.
        bad = integration_client.patch(
            f"/api/v1/telemetry/{vin}/{stamps[0]}",
            json={"vin": "HIJACK"},
            headers=api_headers,
        )
        assert bad.status_code == 400
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_delete_one(integration_client, api_headers):
    vin = "TELD0000000000001"
    try:
        stamps = _seed_rows(integration_client, api_headers, vin, 2)
        resp = integration_client.delete(
            f"/api/v1/telemetry/{vin}/{stamps[0]}", headers=api_headers
        )
        assert resp.status_code == 204
        assert (
            integration_client.get(
                f"/api/v1/telemetry/{vin}/{stamps[0]}", headers=api_headers
            ).status_code
            == 404
        )
        # The other row survives.
        rows = integration_client.get(
            f"/api/v1/telemetry?vin={vin}", headers=api_headers
        ).get_json()["rows"]
        assert len(rows) == 1
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_bulk_delete_range(integration_client, api_headers):
    vin = "TELD0000000000002"
    try:
        stamps = _seed_rows(integration_client, api_headers, vin, 10)
        # Delete the middle four (inclusive bounds).
        resp = integration_client.delete(
            f"/api/v1/telemetry?vin={vin}&start={stamps[3]}&end={stamps[6]}&confirm=true",
            headers=api_headers,
        )
        assert resp.status_code == 200
        assert resp.get_json()["deleted"] == 4

        remaining = integration_client.get(
            f"/api/v1/telemetry?vin={vin}", headers=api_headers
        ).get_json()["rows"]
        assert len(remaining) == 6
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_bulk_delete_requires_confirm(integration_client, api_headers):
    resp = integration_client.delete(
        "/api/v1/telemetry?vin=ANYVIN", headers=api_headers
    )
    assert resp.status_code == 400
