"""
Structured logging configuration for Pi Drive Server.

In json mode (LOG_FORMAT=json) every log record is emitted as a single-line
JSON object, ready for ingestion by Grafana Alloy → Loki (Phase 8).

In text mode (LOG_FORMAT=text) records use a human-readable format for local
development.

Field names align with the shared label/metadata schema used by both the
server and the Android app so Grafana queries can span both:
  Labels (low cardinality):  app, component, level, env
  Structured metadata:       request_id, vin, device_id, endpoint, status, logger
"""

from __future__ import annotations

import json
import logging
import time
import uuid
from contextvars import ContextVar
from typing import Any

from flask import Flask, g, request

# ── Context variables ─────────────────────────────────────────────────────────
# Values bound here are automatically included in every log record emitted
# within the same async context (request scope).

# Default None (not a shared mutable {}): each request sets its own dict; readers
# treat None as empty.
_log_ctx: ContextVar[dict[str, Any] | None] = ContextVar("_log_ctx", default=None)


def bind(**fields: Any) -> None:
    """
    Attach key-value fields to the current request's log context.

    These fields are automatically merged into every JSON log record emitted
    for the duration of this request (vin, device_id, endpoint, status, etc.).
    Call this from blueprints/services after parsing the request body.

    Example::

        log_ctx.bind(vin="1G1JC...", endpoint="/telemetry")
    """
    ctx = dict(_log_ctx.get() or {})
    ctx.update(fields)
    _log_ctx.set(ctx)


def get_ctx() -> dict[str, Any]:
    """Return a copy of the current log context dict."""
    return dict(_log_ctx.get() or {})


def get_request_id() -> str:
    """Return the request-scoped ID from Flask's g, or a fallback."""
    try:
        return g.get("request_id", "")
    except RuntimeError:
        # Outside a request context (e.g., startup logs).
        return ""


# ── JSON formatter ─────────────────────────────────────────────────────────────


class _JsonFormatter(logging.Formatter):
    """
    Formats each log record as a single-line JSON object.

    Mandatory fields on every record:
      timestamp  RFC3339 string
      level      log level name
      logger     logger name (used as the log tag, e.g. 'Ingest')
      message    the log message

    Optional contextual fields (present when bound via log_ctx.bind):
      request_id, vin, device_id, endpoint, status, event, duration_ms
    """

    def __init__(self, app_env: str = "prod") -> None:
        super().__init__()
        self._app_env = app_env

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "timestamp": self._utc_iso(record.created),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "app": "pi-drive-server",
            "env": self._app_env,
        }

        request_id = get_request_id()
        if request_id:
            payload["request_id"] = request_id

        # Merge any fields bound to the current request context.
        payload.update(get_ctx())

        if record.exc_info:
            payload["exc_info"] = self.formatException(record.exc_info)

        return json.dumps(payload, default=str)

    @staticmethod
    def _utc_iso(created: float) -> str:
        """Convert a Unix timestamp to an RFC3339 UTC string."""
        t = time.gmtime(created)
        ms = int((created % 1) * 1000)
        return (
            f"{t.tm_year:04d}-{t.tm_mon:02d}-{t.tm_mday:02d}T"
            f"{t.tm_hour:02d}:{t.tm_min:02d}:{t.tm_sec:02d}.{ms:03d}Z"
        )


# ── Text formatter ─────────────────────────────────────────────────────────────

_TEXT_FMT = "%(asctime)s [%(name)s] %(levelname)s %(message)s"
_DATE_FMT = "%Y-%m-%d %H:%M:%S"


# ── Setup ──────────────────────────────────────────────────────────────────────


def setup_logging(app: Flask) -> None:
    """
    Configure root and Flask logger with the app's format and level settings.

    Called from create_app() after config is loaded.
    """
    cfg = app.config

    level = getattr(logging, cfg.get("LOG_LEVEL", "INFO").upper(), logging.INFO)
    fmt = cfg.get("LOG_FORMAT", "json")
    env = cfg.get("ENV", "prod")

    if fmt == "json":
        formatter: logging.Formatter = _JsonFormatter(app_env=env)
    else:
        formatter = logging.Formatter(_TEXT_FMT, datefmt=_DATE_FMT)

    handler = logging.StreamHandler()
    handler.setFormatter(formatter)

    root = logging.getLogger()
    root.setLevel(level)
    # Replace any existing handlers to avoid duplicate output.
    root.handlers = [handler]

    # Suppress noisy Werkzeug access logs in json mode (Alloy handles that).
    if fmt == "json":
        logging.getLogger("werkzeug").setLevel(logging.WARNING)

    # Attach the request-id injection to the app.
    _register_request_hooks(app)


# Map a Flask blueprint name to the Loki `component` label (§10.4). Blueprints
# not listed default to "api".
_COMPONENT_BY_BLUEPRINT = {
    "web": "web",
    "ingest": "ingest",
}

_access_logger = logging.getLogger("PiDriveServer")


def _component_for_request() -> str:
    """The `component` label for the current request, from its blueprint."""
    return _COMPONENT_BY_BLUEPRINT.get(request.blueprint or "", "api")


def _register_request_hooks(app: Flask) -> None:
    """
    Per-request observability (§10.4):
      1. Adopt an inbound `X-Request-Id` (or mint one) so a trace can span the
         app and the client; echo it back on the response.
      2. Start each request with a clean log context, pre-bound with the
         component + endpoint so every line in the request carries them.
      3. Emit exactly one `http_request` access line with status + duration_ms
         (INFO for <400, WARNING for 4xx, ERROR for 5xx) — never one per record.
    """

    @app.before_request
    def _begin_request() -> None:
        # Honor an inbound request id (trace continuity), else generate one.
        # Guard against header injection by bounding length and charset.
        inbound = (request.headers.get("X-Request-Id") or "").strip()
        g.request_id = inbound[:64] if inbound.isascii() and inbound else str(uuid.uuid4())
        g.request_started = time.monotonic()
        _log_ctx.set({})
        bind(component=_component_for_request(), endpoint=request.path)

    @app.after_request
    def _end_request(response):  # type: ignore[no-untyped-def]
        response.headers["X-Request-Id"] = g.get("request_id", "")

        started = g.get("request_started")
        duration_ms = int((time.monotonic() - started) * 1000) if started else None
        status = response.status_code
        # Bind status/duration so the access line (and any later line) carries them.
        bind(status=status, duration_ms=duration_ms)

        # Skip the chatty health probes at INFO so Loki isn't flooded by k8s.
        is_probe = request.path in ("/healthz", "/readyz", "/health")
        if status >= 500:
            level = logging.ERROR
        elif status >= 400:
            level = logging.WARNING
        elif is_probe:
            level = logging.DEBUG
        else:
            level = logging.INFO

        _access_logger.log(
            level,
            "event=http_request %s %s -> %d (%sms)",
            request.method,
            request.path,
            status,
            duration_ms if duration_ms is not None else "?",
        )
        return response
