"""
L1 unit tests for the API-key decorator (app/auth.py).

Uses a fresh app with a small protected route so the decorator is exercised
through the full request cycle, including the 401 JSON error handler.
"""

from __future__ import annotations

import pytest

from app import create_app
from app.config import AppTestConfig

KEY = "test-api-key"  # AppTestConfig's api_key


@pytest.fixture(scope="module")
def auth_app():
    """A fresh app with a protected probe route registered before first request."""
    from app.auth import require_api_key

    application = create_app(AppTestConfig())
    application.config["TESTING"] = True

    @application.route("/protected")
    @require_api_key
    def protected():
        return {"ok": True}

    return application


@pytest.fixture()
def auth_client(auth_app):
    with auth_app.test_client() as c:
        yield c


def test_valid_bearer_allowed(auth_client):
    resp = auth_client.get("/protected", headers={"Authorization": f"Bearer {KEY}"})
    assert resp.status_code == 200


def test_valid_x_api_key_allowed(auth_client):
    resp = auth_client.get("/protected", headers={"X-API-Key": KEY})
    assert resp.status_code == 200


def test_missing_key_rejected(auth_client):
    resp = auth_client.get("/protected")
    assert resp.status_code == 401
    assert "error" in resp.get_json()


def test_wrong_key_rejected(auth_client):
    resp = auth_client.get("/protected", headers={"Authorization": "Bearer nope"})
    assert resp.status_code == 401


def test_blank_bearer_rejected(auth_client):
    resp = auth_client.get("/protected", headers={"Authorization": "Bearer "})
    assert resp.status_code == 401


def test_wrong_x_api_key_rejected(auth_client):
    resp = auth_client.get("/protected", headers={"X-API-Key": "nope"})
    assert resp.status_code == 401


def test_health_stays_open(auth_client):
    """Health endpoints are exempt — probes must not need a key."""
    resp = auth_client.get("/health")
    assert resp.status_code == 200
