"""
Health check endpoints.

  GET /health   — used by the Android app's "Test" button and simple probes.
  GET /healthz  — Kubernetes liveness probe (always 200 if the process is up).
  GET /readyz   — Kubernetes readiness probe; checks DB connectivity in Phase 1.
                  Returns 503 when the DB is unreachable so K8s withholds traffic.

All three are auth-exempt — probes must not need an API key.
"""

from __future__ import annotations

import logging

from flask import Blueprint, jsonify
from sqlalchemy import text

from app.db.session import get_engine

logger = logging.getLogger("PiDriveServer")

health_bp = Blueprint("health", __name__)


@health_bp.get("/health")
def health():
    """
    Basic liveness / connection test.

    Returns 200 with {"status": "ok"}. Used by the Android settings "Test"
    button to confirm the server URL is reachable and the server is running.
    """
    return jsonify({"status": "ok"}), 200


@health_bp.get("/healthz")
def healthz():
    """
    Kubernetes liveness probe.

    Always returns 200 as long as the process is running. K8s restarts the
    pod if this endpoint stops responding.
    """
    return jsonify({"status": "ok"}), 200


@health_bp.get("/readyz")
def readyz():
    """
    Kubernetes readiness probe.

    Pings the database with `SELECT 1`. Returns 200 when the DB is reachable so
    K8s sends traffic; returns 503 when it is not so K8s withholds traffic until
    the database recovers.
    """
    try:
        with get_engine().connect() as conn:
            conn.execute(text("SELECT 1"))
    except Exception as exc:  # DB unreachable / not ready
        logger.warning("Readiness check failed: DB unreachable (%s)", exc)
        return jsonify({"status": "error", "db": "unreachable"}), 503
    return jsonify({"status": "ok", "db": "ok"}), 200
