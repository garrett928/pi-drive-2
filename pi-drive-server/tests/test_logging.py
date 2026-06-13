"""
L1 unit tests for structured logging + request instrumentation (Phase 8.1).

No database: these drive the Flask test client (unit `app`/`client` fixtures)
and inspect the JSON log lines the handler emits. They assert the §10.4 shared
schema (one JSON object per line; request_id/component/endpoint/status present)
and the access-log contract (exactly one `http_request` line per request,
level by status class, ingest logged as a batch summary — never per record).
"""

from __future__ import annotations

import json
import logging

import pytest

from app import create_app
from app.config import AppTestConfig
from app.logging_config import _JsonFormatter, bind

# ── JSON formatter ──────────────────────────────────────────────────────────────


def test_formatter_emits_valid_one_line_json():
    formatter = _JsonFormatter(app_env="prod")
    record = logging.LogRecord(
        "Ingest", logging.INFO, __file__, 10, "hello %s", ("world",), None
    )
    line = formatter.format(record)
    assert "\n" not in line
    obj = json.loads(line)
    assert obj["level"] == "INFO"
    assert obj["logger"] == "Ingest"
    assert obj["message"] == "hello world"
    assert obj["app"] == "pi-drive-server"
    assert obj["env"] == "prod"
    assert obj["timestamp"].endswith("Z")


def test_formatter_includes_exception_text():
    formatter = _JsonFormatter()
    try:
        raise ValueError("boom")
    except ValueError:
        import sys

        record = logging.LogRecord(
            "X", logging.ERROR, __file__, 1, "failed", (), sys.exc_info()
        )
    obj = json.loads(formatter.format(record))
    assert "boom" in obj["exc_info"]


def test_formatter_merges_bound_context(app_ctx):
    """Fields bound via log_ctx.bind appear on the line (structured metadata)."""
    formatter = _JsonFormatter()
    bind(vin="1G1JC524417100001", endpoint="/telemetry", status=200)
    record = logging.LogRecord("Ingest", logging.INFO, __file__, 1, "x", (), None)
    obj = json.loads(formatter.format(record))
    assert obj["vin"] == "1G1JC524417100001"
    assert obj["endpoint"] == "/telemetry"
    assert obj["status"] == 200


# ── Request middleware ──────────────────────────────────────────────────────────


class _JsonSink(logging.Handler):
    """A handler that formats each record with the JSON formatter at emit time
    (so request-scoped context vars like `component` are captured) and keeps the
    decoded dicts for inspection."""

    def __init__(self) -> None:
        super().__init__(level=logging.DEBUG)
        self.setFormatter(_JsonFormatter(app_env="dev"))
        self.lines: list[dict] = []

    def emit(self, record: logging.LogRecord) -> None:
        self.lines.append(json.loads(self.format(record)))

    def access_lines(self) -> list[dict]:
        return [d for d in self.lines if "event=http_request" in d.get("message", "")]


def _json_app():
    cfg = AppTestConfig()
    cfg.log_format = "json"
    app = create_app(cfg)
    app.config["TESTING"] = True
    return app


# The access log + error handlers both emit on the "PiDriveServer" logger.
# Attaching the sink there (not root) is robust across the whole suite:
# create_app's setup_logging replaces *root* handlers, but never touches a
# named logger's own handlers, so the sink survives and captures directly.
_ACCESS_LOGGER = "PiDriveServer"


def _attach_sink(app) -> _JsonSink:
    sink = _JsonSink()
    logger = logging.getLogger(_ACCESS_LOGGER)
    logger.addHandler(sink)
    logger.setLevel(logging.DEBUG)
    return sink


@pytest.fixture(autouse=True)
def _detach_sinks():
    """Remove any sinks added to the access logger during a test."""
    logger = logging.getLogger(_ACCESS_LOGGER)
    before = list(logger.handlers)
    yield
    for h in list(logger.handlers):
        if isinstance(h, _JsonSink) and h not in before:
            logger.removeHandler(h)


def test_request_emits_one_http_request_line():
    app = _json_app()
    sink = _attach_sink(app)
    resp = app.test_client().get("/health")
    assert resp.status_code == 200
    assert resp.headers.get("X-Request-Id")  # echoed for client correlation

    access = sink.access_lines()
    assert len(access) == 1
    line = access[0]
    assert "GET /health -> 200" in line["message"]
    assert line["status"] == 200
    assert line["endpoint"] == "/health"
    assert line["component"] == "api"
    assert "duration_ms" in line
    assert line["request_id"]


def test_inbound_request_id_is_honored():
    resp = _json_app().test_client().get(
        "/health", headers={"X-Request-Id": "trace-abc-123"}
    )
    assert resp.headers["X-Request-Id"] == "trace-abc-123"


def test_4xx_logs_at_warning_and_5xx_at_error():
    app = _json_app()
    # Let the 500 error handler run instead of the test client re-raising.
    app.config["TESTING"] = False
    app.config["PROPAGATE_EXCEPTIONS"] = False

    @app.route("/boom-test")
    def _boom():
        raise RuntimeError("kaboom")

    sink = _attach_sink(app)
    client = app.test_client()
    headers = {"Authorization": f"Bearer {app.config['API_KEY']}"}
    client.get("/api/v1/telemetry", headers=headers)  # 400 (missing vin) → WARNING
    client.get("/boom-test")                          # 500 → ERROR

    by_status = {d["status"]: d["level"] for d in sink.access_lines()}
    assert by_status.get(400) == "WARNING"
    assert by_status.get(500) == "ERROR"
    # The 500 logs a traceback (exc_info) with a request_id for Loki correlation.
    tracebacks = [
        d for d in sink.lines
        if d["level"] == "ERROR" and "exc_info" in d and d.get("request_id")
    ]
    assert tracebacks, "expected an ERROR line carrying a traceback + request_id"
    assert "RuntimeError" in tracebacks[0]["exc_info"]


def test_web_request_tagged_component_web():
    """A web page request carries component=web; the API carries component=api."""
    app = _json_app()
    sink = _attach_sink(app)
    app.test_client().get("/login")  # web blueprint, no DB needed
    web = [d for d in sink.access_lines() if d["endpoint"] == "/login"]
    assert web and web[0]["component"] == "web"


# ── Ingest batch-summary contract ───────────────────────────────────────────────


def test_ingest_logs_one_batch_summary_not_per_record(monkeypatch):
    """
    An N-snapshot batch logs exactly one `ingest_batch` summary line, never N
    (§10.4). Unit-level: the DB writes are stubbed and `ingest_batch` is driven
    directly, with a sink on the "Ingest" logger (robust across the suite).
    """
    from app.schemas.telemetry import TelemetryIn
    from app.services import telemetry_service, vehicle_service

    # Stub the DB-touching calls so we exercise only the orchestration + logging.
    monkeypatch.setattr(vehicle_service, "upsert_vehicle", lambda *a, **k: None)
    monkeypatch.setattr(telemetry_service, "upsert_snapshot", lambda *a, **k: None)
    monkeypatch.setattr(telemetry_service, "insert_events", lambda *a, **k: 0)

    items = [
        TelemetryIn.model_validate(
            {"vin": "LOGBATCH000000001", "timestamp": f"2026-07-01T10:{i:02d}:00Z",
             "obd": {"speed_kmh": 40 + i, "rpm": 2000 + i}}
        )
        for i in range(5)
    ]

    sink = _JsonSink()
    ingest_logger = logging.getLogger("Ingest")
    ingest_logger.addHandler(sink)
    ingest_logger.setLevel(logging.INFO)
    try:
        accepted, _vins = telemetry_service.ingest_batch(items, session=object())
    finally:
        ingest_logger.removeHandler(sink)

    assert accepted == 5
    batch = [d for d in sink.lines if "event=ingest_batch" in d.get("message", "")]
    assert len(batch) == 1            # one summary, not five
    assert "accepted=5" in batch[0]["message"]
