"""
Tests for the application factory and health endpoints.

Verifies that:
  - create_app() returns a Flask app with TESTING mode.
  - GET /health returns 200 with {"status": "ok"}.
  - GET /healthz returns 200.
  - GET /readyz returns 200 with {"db": "not_configured"}.
  - Unhandled exceptions return a JSON 500 with request_id (no stack trace).
  - Missing routes return a JSON 404.
"""

from __future__ import annotations


class TestAppFactory:
    def test_returns_flask_app(self, app):
        """create_app() with TestConfig returns a Flask application."""
        from flask import Flask

        assert isinstance(app, Flask)

    def test_testing_flag_is_set(self, app):
        """The test app has TESTING=True so Flask surfaces errors in tests."""
        assert app.config["TESTING"] is True

    def test_api_key_is_loaded(self, app):
        """The API key from TestConfig is present in the app config."""
        assert app.config["API_KEY"] == "test-api-key"


class TestHealthEndpoints:
    def test_health_returns_200(self, client):
        resp = client.get("/health")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "ok"

    def test_healthz_returns_200(self, client):
        resp = client.get("/healthz")
        assert resp.status_code == 200
        assert resp.get_json()["status"] == "ok"

    def test_readyz_returns_200_when_db_reachable(self, client):
        # The unit app's engine points at sqlite in-memory; SELECT 1 succeeds,
        # so readiness reports db: ok. (Real Timescale is exercised in the
        # integration suite's test_readyz_reports_db_up.)
        resp = client.get("/readyz")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "ok"
        assert data["db"] == "ok"

    def test_health_no_auth_required(self, client):
        """Health endpoints must be reachable without an API key (K8s probes)."""
        # No Authorization header — still 200.
        resp = client.get("/health")
        assert resp.status_code == 200


class TestErrorHandlers:
    def test_404_returns_json(self, client):
        resp = client.get("/this-route-does-not-exist")
        assert resp.status_code == 404
        data = resp.get_json()
        assert "error" in data

    def test_500_returns_json_without_traceback(self):
        """
        A route that raises an unhandled exception returns JSON 500.
        The response must NOT include the traceback — only a generic message
        and a request_id so the caller can correlate with server logs.
        Uses a fresh app instance so routes can be registered before first request.
        """
        from app import create_app
        from app.config import AppTestConfig

        fresh_app = create_app(AppTestConfig())
        fresh_app.config["TESTING"] = True

        @fresh_app.route("/test-crash")
        def crash():
            raise RuntimeError("deliberate test crash")

        with fresh_app.test_client() as c:
            resp = c.get("/test-crash")

        assert resp.status_code == 500
        data = resp.get_json()
        assert "error" in data
        assert "request_id" in data
        # The raw traceback must not appear in the client response.
        assert "Traceback" not in resp.get_data(as_text=True)
        assert "deliberate test crash" not in resp.get_data(as_text=True)

    def test_500_includes_request_id(self):
        """500 responses include a request_id for log correlation."""
        from app import create_app
        from app.config import AppTestConfig

        fresh_app = create_app(AppTestConfig())
        fresh_app.config["TESTING"] = True

        @fresh_app.route("/test-crash-id")
        def crash_id():
            raise RuntimeError("crash for id test")

        with fresh_app.test_client() as c:
            resp = c.get("/test-crash-id")

        data = resp.get_json()
        # request_id is a non-empty string (UUID).
        assert data.get("request_id", "")
