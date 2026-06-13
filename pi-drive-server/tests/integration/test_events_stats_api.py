"""
L2 integration tests for the events (/api/v1/events) and stats (/api/v1/stats)
APIs against the real migrated TimescaleDB.

The stats tests assert against hand-computed values over a small seeded dataset
and check that the daily series (served from the `telemetry_daily` continuous
aggregate, real-time mode) is consistent with the raw row count.
"""

from __future__ import annotations

import pytest

pytestmark = pytest.mark.integration


def _seed(client, headers, vin: str) -> None:
    """
    Two days of data: 3 snapshots on day 1 (speeds 40/60/80), 1 on day 2
    (speed 100), plus two HARD_BRAKE events and one HARD_ACCEL.
    """
    body = [
        {
            "vin": vin,
            "timestamp": "2026-04-10T08:00:00Z",
            "obd": {"speed_kmh": 40.0},
            "calculated": {"fuel_economy_mpg": 30.0},
            "events": [
                {
                    "strategy": "ACCELERATION",
                    "type": "HARD_BRAKE",
                    "timestamp": "2026-04-10T08:00:10Z",
                },
                {
                    "strategy": "G_FORCE",
                    "type": "HARD_BRAKE",
                    "timestamp": "2026-04-10T08:00:20Z",
                },
            ],
        },
        {"vin": vin, "timestamp": "2026-04-10T09:00:00Z", "obd": {"speed_kmh": 60.0}},
        {
            "vin": vin,
            "timestamp": "2026-04-10T10:00:00Z",
            "obd": {"speed_kmh": 80.0},
            "events": [
                {
                    "strategy": "ACCELERATION",
                    "type": "HARD_ACCEL",
                    "timestamp": "2026-04-10T10:00:05Z",
                }
            ],
        },
        {"vin": vin, "timestamp": "2026-04-11T08:00:00Z", "obd": {"speed_kmh": 100.0}},
    ]
    resp = client.post("/telemetry", json=body, headers=headers)
    assert resp.status_code == 200, resp.get_json()


def _cleanup(client, headers, vin: str) -> None:
    client.delete(f"/api/v1/vehicles/{vin}?confirm=true", headers=headers)


# ── Events ─────────────────────────────────────────────────────────────────────


def test_events_list_and_filters(integration_client, api_headers):
    vin = "EVT00000000000001"
    try:
        _seed(integration_client, api_headers, vin)
        body = integration_client.get(
            f"/api/v1/events?vin={vin}", headers=api_headers
        ).get_json()
        assert body["total"] == 3
        assert len(body["events"]) == 3
        # Newest first.
        times = [e["time"] for e in body["events"]]
        assert times == sorted(times, reverse=True)

        bounded = integration_client.get(
            f"/api/v1/events?vin={vin}&start=2026-04-10T09:00:00Z",
            headers=api_headers,
        ).get_json()
        assert bounded["total"] == 1
        assert bounded["events"][0]["type"] == "HARD_ACCEL"
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_events_paginate(integration_client, api_headers):
    vin = "EVT00000000000002"
    try:
        _seed(integration_client, api_headers, vin)
        page = integration_client.get(
            f"/api/v1/events?vin={vin}&limit=2", headers=api_headers
        ).get_json()
        assert len(page["events"]) == 2
        assert page["total"] == 3
        rest = integration_client.get(
            f"/api/v1/events?vin={vin}&limit=2&offset=2", headers=api_headers
        ).get_json()
        assert len(rest["events"]) == 1
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_event_delete(integration_client, api_headers):
    vin = "EVT00000000000003"
    try:
        _seed(integration_client, api_headers, vin)
        events = integration_client.get(
            f"/api/v1/events?vin={vin}", headers=api_headers
        ).get_json()["events"]
        resp = integration_client.delete(
            f"/api/v1/events/{events[0]['id']}", headers=api_headers
        )
        assert resp.status_code == 204
        remaining = integration_client.get(
            f"/api/v1/events?vin={vin}", headers=api_headers
        ).get_json()
        assert remaining["total"] == 2

        # Deleting it again → 404.
        assert (
            integration_client.delete(
                f"/api/v1/events/{events[0]['id']}", headers=api_headers
            ).status_code
            == 404
        )
    finally:
        _cleanup(integration_client, api_headers, vin)


# ── Stats ──────────────────────────────────────────────────────────────────────


def test_vehicle_stats_hand_computed(integration_client, api_headers):
    vin = "STAT0000000000001"
    try:
        _seed(integration_client, api_headers, vin)
        stats = integration_client.get(
            f"/api/v1/stats/{vin}", headers=api_headers
        ).get_json()

        assert stats["sample_count"] == 4
        assert stats["avg_speed_kmh"] == pytest.approx(70.0)  # (40+60+80+100)/4
        assert stats["max_speed_kmh"] == 100.0
        assert stats["avg_fuel_economy_mpg"] == pytest.approx(30.0)  # one sample
        assert stats["first_time"] == "2026-04-10T08:00:00Z"
        assert stats["last_time"] == "2026-04-11T08:00:00Z"
        assert stats["events_by_type"] == {"HARD_BRAKE": 2, "HARD_ACCEL": 1}

        # Daily series from the continuous aggregate (real-time mode):
        # consistent with raw counts without an explicit refresh.
        daily = {d["bucket"][:10]: d for d in stats["daily"]}
        assert daily["2026-04-10"]["sample_count"] == 3
        assert daily["2026-04-10"]["max_speed_kmh"] == 80.0
        assert daily["2026-04-11"]["sample_count"] == 1
        assert sum(d["sample_count"] for d in stats["daily"]) == stats["sample_count"]
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_vehicle_stats_time_bounded(integration_client, api_headers):
    vin = "STAT0000000000002"
    try:
        _seed(integration_client, api_headers, vin)
        stats = integration_client.get(
            f"/api/v1/stats/{vin}?start=2026-04-11T00:00:00Z", headers=api_headers
        ).get_json()
        assert stats["sample_count"] == 1
        assert stats["avg_speed_kmh"] == 100.0
        assert stats["events_by_type"] == {}  # all events were on day 1
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_vehicle_stats_unknown_vin_404(integration_client, api_headers):
    resp = integration_client.get("/api/v1/stats/NOSUCHVIN", headers=api_headers)
    assert resp.status_code == 404


def test_fleet_stats_includes_seeded_vehicle(integration_client, api_headers):
    vin = "STAT0000000000003"
    try:
        _seed(integration_client, api_headers, vin)
        fleet = integration_client.get("/api/v1/stats", headers=api_headers).get_json()

        assert fleet["total_vehicles"] >= 1
        assert fleet["total_samples"] >= 4
        assert fleet["total_events"] >= 3
        assert fleet["oldest"] is not None and fleet["newest"] is not None
        mine = next(v for v in fleet["vehicles"] if v["vin"] == vin)
        assert mine["sample_count"] == 4
        assert mine["event_count"] == 3
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_stats_require_auth(integration_client):
    assert integration_client.get("/api/v1/stats").status_code == 401
