"""
L3 end-to-end tests — a REAL Flask server over a REAL socket + REAL TimescaleDB.

Unlike the unit tests (Flask test client, in-process), these boot the actual
WSGI app on a werkzeug server bound to a TCP port and hit it with the `requests`
library — the same path the Android app travels. For Phase 0 the only endpoints
that exist are the health checks; this suite proves the entire e2e pipeline
(testcontainers DB → live server → real HTTP → response inspection) works before
any business endpoints land. Phase 2 adds the POST /telemetry contract suite here.
"""

from __future__ import annotations

import requests

pytestmark = __import__("pytest").mark.e2e


def test_health_over_socket(live_server):
    """GET /health over a real socket returns 200 {"status":"ok"}."""
    resp = requests.get(f"{live_server}/health", timeout=5)
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


def test_healthz_over_socket(live_server):
    """GET /healthz (k8s liveness) returns 200."""
    resp = requests.get(f"{live_server}/healthz", timeout=5)
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def test_readyz_over_socket(live_server):
    """GET /readyz (k8s readiness) returns 200 with db: ok against the real DB."""
    resp = requests.get(f"{live_server}/readyz", timeout=5)
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    # The live server is wired to the real TimescaleDB, so the SELECT 1 succeeds.
    assert body["db"] == "ok"


def test_unknown_route_returns_json_404(live_server):
    """A missing route returns the JSON error shape over the wire."""
    resp = requests.get(f"{live_server}/no-such-endpoint", timeout=5)
    assert resp.status_code == 404
    assert "error" in resp.json()


def test_response_carries_request_id_header(live_server):
    """Every response exposes X-Request-Id for log correlation."""
    resp = requests.get(f"{live_server}/health", timeout=5)
    assert resp.headers.get("X-Request-Id")
