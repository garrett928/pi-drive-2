"""
L2 integration tests for CSV import/export (REQUIREMENTS.md §7).

Run through the Flask test client against the real migrated TimescaleDB.
Covers: valid import (counts, vehicle auto-registration, type fidelity),
partial success with per-row errors, idempotent re-import, header validation,
streamed export, and the §7 round-trip guarantee (export → re-import without
loss). Each test uses unique VINs and cleans up via the cascade DELETE.
"""

from __future__ import annotations

import io

import pytest

pytestmark = pytest.mark.integration


def _upload(client, headers, csv_text: str, filename: str = "rows.csv"):
    """POST a CSV string as a multipart file to the import endpoint."""
    return client.post(
        "/api/v1/telemetry/import",
        headers=headers,
        data={"file": (io.BytesIO(csv_text.encode("utf-8")), filename)},
        content_type="multipart/form-data",
    )


def _cleanup(client, headers, *vins: str) -> None:
    for vin in vins:
        client.delete(f"/api/v1/vehicles/{vin}?confirm=true", headers=headers)


GOOD_CSV = (
    "vin,time,rpm,speed_kmh,throttle_pct\n"
    "CSVIMPORT00000001,2026-04-02T10:00:00Z,2100,55.5,21.3\n"
    "CSVIMPORT00000001,2026-04-02T10:00:01Z,2150,56.0,22.0\n"
    "CSVIMPORT00000001,2026-04-02T10:00:02Z,2200,56.5,22.7\n"
)


def test_import_valid_csv(integration_client, api_headers):
    vin = "CSVIMPORT00000001"
    try:
        resp = _upload(integration_client, api_headers, GOOD_CSV)
        assert resp.status_code == 200, resp.get_json()
        body = resp.get_json()
        assert body == {"imported": 3, "skipped": 0, "errors": []}

        # Vehicle auto-registered with first/last seen from the file's rows.
        veh = integration_client.get(f"/api/v1/vehicles/{vin}", headers=api_headers)
        assert veh.status_code == 200
        assert veh.get_json()["sample_count"] == 3

        # Type fidelity + provenance: rpm int, floats not truncated, source=csv.
        rows = integration_client.get(
            f"/api/v1/telemetry?vin={vin}&order=asc", headers=api_headers
        ).get_json()["rows"]
        assert rows[0]["rpm"] == 2100
        assert rows[0]["speed_kmh"] == 55.5
        assert rows[0]["throttle_pct"] == 21.3
        assert all(r["source"] == "csv" for r in rows)
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_import_partial_success_reports_bad_rows(integration_client, api_headers):
    vin = "CSVIMPORT00000002"
    csv_text = (
        "vin,time,rpm\n"
        f"{vin},2026-04-02T11:00:00Z,1500\n"
        ",2026-04-02T11:00:01Z,1501\n"  # line 3: missing vin
        f"{vin},not-a-timestamp,1502\n"  # line 4: bad time
        f"{vin},2026-04-02T11:00:03Z,1503\n"
    )
    try:
        resp = _upload(integration_client, api_headers, csv_text)
        assert resp.status_code == 200
        body = resp.get_json()
        assert body["imported"] == 2
        assert body["skipped"] == 2
        rows_with_errors = {err["row"] for err in body["errors"]}
        assert rows_with_errors == {3, 4}
        assert all(err["reason"] for err in body["errors"])

        count = integration_client.get(
            f"/api/v1/telemetry?vin={vin}", headers=api_headers
        ).get_json()["rows"]
        assert len(count) == 2
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_reimport_is_idempotent(integration_client, api_headers):
    vin = "CSVIMPORT00000001"
    try:
        first = _upload(integration_client, api_headers, GOOD_CSV).get_json()
        second = _upload(integration_client, api_headers, GOOD_CSV).get_json()
        assert first["imported"] == 3
        assert second["imported"] == 3  # upserts — re-reported as imported

        rows = integration_client.get(
            f"/api/v1/telemetry?vin={vin}", headers=api_headers
        ).get_json()["rows"]
        assert len(rows) == 3  # but never duplicated
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_import_header_validation(integration_client, api_headers):
    # Missing required column
    resp = _upload(integration_client, api_headers, "vin,rpm\nX,1\n")
    assert resp.status_code == 400
    assert "time" in resp.get_json()["error"]

    # Unknown column
    resp = _upload(integration_client, api_headers, "vin,time,warp_factor\nX,2026-04-02T10:00:00Z,9\n")
    assert resp.status_code == 400
    assert "warp_factor" in resp.get_json()["error"]

    # Not a CSV at all
    resp = _upload(integration_client, api_headers, "hello", filename="notes.txt")
    assert resp.status_code == 415

    # No file field
    resp = integration_client.post(
        "/api/v1/telemetry/import",
        headers=api_headers,
        data={},
        content_type="multipart/form-data",
    )
    assert resp.status_code == 400


def test_import_requires_auth(integration_client):
    resp = integration_client.post(
        "/api/v1/telemetry/import",
        data={"file": (io.BytesIO(b"vin,time\n"), "rows.csv")},
        content_type="multipart/form-data",
    )
    assert resp.status_code == 401


def test_export_streams_csv_with_canonical_header(integration_client, api_headers):
    vin = "CSVEXPORT00000001"
    try:
        _upload(integration_client, api_headers, GOOD_CSV.replace("CSVIMPORT00000001", vin))
        resp = integration_client.get(
            f"/api/v1/telemetry/export?vin={vin}", headers=api_headers
        )
        assert resp.status_code == 200
        assert resp.mimetype == "text/csv"
        assert "attachment" in resp.headers["Content-Disposition"]
        assert vin in resp.headers["Content-Disposition"]

        lines = resp.get_data(as_text=True).strip().splitlines()
        from app.services.csv_service import CSV_COLUMNS

        assert lines[0] == ",".join(CSV_COLUMNS)
        assert len(lines) == 1 + 3  # header + 3 data rows
        assert lines[1].startswith(f"{vin},2026-04-02T10:00:00Z")
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_export_unknown_vin_404(integration_client, api_headers):
    resp = integration_client.get(
        "/api/v1/telemetry/export?vin=NOSUCHVIN12345678", headers=api_headers
    )
    assert resp.status_code == 404


def test_export_respects_time_bounds(integration_client, api_headers):
    vin = "CSVEXPORT00000002"
    try:
        _upload(integration_client, api_headers, GOOD_CSV.replace("CSVIMPORT00000001", vin))
        resp = integration_client.get(
            f"/api/v1/telemetry/export?vin={vin}"
            "&start=2026-04-02T10:00:01Z&end=2026-04-02T10:00:01Z",
            headers=api_headers,
        )
        lines = resp.get_data(as_text=True).strip().splitlines()
        assert len(lines) == 2  # header + exactly the one bounded row
        assert "2026-04-02T10:00:01Z" in lines[1]
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_round_trip_export_reimport_without_loss(integration_client, api_headers):
    """§7's core guarantee: export → wipe → re-import reproduces the data."""
    vin = "CSVROUNDTRIP00001"
    try:
        # Seed through the *wire* path so nested objects + extra are exercised.
        payload = {
            "vin": vin,
            "timestamp": "2026-04-03T08:00:00.123Z",
            "device_id": "pd-test-0001",
            "location": {"lat": 36.1627, "lng": -86.7816, "speed_gps": 31.4},
            "obd": {"rpm": 3175, "speed_kmh": 88.5, "throttle_pct": 33.7,
                    "custom_pid": 7},
            "calculated": {"fuel_economy_mpg": 28.6},
            "accel_mps2": 1.2,
        }
        resp = integration_client.post("/telemetry", json=payload, headers=api_headers)
        assert resp.status_code == 200

        exported = integration_client.get(
            f"/api/v1/telemetry/export?vin={vin}", headers=api_headers
        ).get_data(as_text=True)

        # Wipe everything for the VIN, then re-import the exported file.
        _cleanup(integration_client, api_headers, vin)
        resp = _upload(integration_client, api_headers, exported)
        assert resp.status_code == 200
        assert resp.get_json()["imported"] == 1

        rows = integration_client.get(
            f"/api/v1/telemetry?vin={vin}", headers=api_headers
        ).get_json()["rows"]
        assert len(rows) == 1
        row = rows[0]
        assert row["time"] == "2026-04-03T08:00:00.123Z"  # ms precision survives
        assert row["rpm"] == 3175
        assert row["speed_kmh"] == 88.5
        assert row["throttle_pct"] == 33.7
        assert row["lat"] == 36.1627
        assert row["fuel_economy_mpg"] == 28.6
        assert row["accel_mps2"] == 1.2
        assert row["device_id"] == "pd-test-0001"
        assert row["extra"] == {"obd": {"custom_pid": 7}}  # JSONB survives the cell
        assert row["source"] == "csv"  # provenance records this copy's origin
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_manual_entry_appears_in_export(integration_client, api_headers):
    """Phase 4.2 confirmation: a hand-entered row shows up in a later export."""
    vin = "CSVMANUAL00000001"
    try:
        resp = integration_client.post(
            "/api/v1/telemetry",
            json={"vin": vin, "timestamp": "2026-04-03T09:00:00Z",
                  "obd": {"rpm": 900}},
            headers=api_headers,
        )
        assert resp.status_code == 201

        exported = integration_client.get(
            f"/api/v1/telemetry/export?vin={vin}", headers=api_headers
        ).get_data(as_text=True)
        lines = exported.strip().splitlines()
        assert len(lines) == 2
        assert lines[1].startswith(f"{vin},2026-04-03T09:00:00Z")
        assert lines[1].rstrip().endswith("manual")  # source column round-trips
    finally:
        _cleanup(integration_client, api_headers, vin)
