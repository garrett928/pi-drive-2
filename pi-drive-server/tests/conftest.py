"""
pytest fixtures shared across the test suite.

Three test layers (see TESTING.md):

  L1  Unit         — in-process, no DB. Uses `app`/`client` (Flask test client,
                     AppTestConfig, sqlite URL that is never connected to).
  L2  Integration  — Flask test client + a REAL TimescaleDB. Marked
                     @pytest.mark.integration. Uses `integration_app`, `db_engine`.
  L3  E2E/contract — a REAL server process over a socket + a REAL TimescaleDB.
                     Marked @pytest.mark.e2e. Uses `live_server`.

Database resolution order (`db_url`):
  1. TEST_DATABASE_URL env var, if set — point at an already-running container/CI service.
  2. else testcontainers spins up a throwaway timescale/timescaledb container.
  3. else → skip all integration/e2e tests with a clear reason (never a false green).
"""

from __future__ import annotations

import os
import threading
import time
from pathlib import Path

import pytest

from app import create_app
from app.config import AppTestConfig

_SERVER_ROOT = Path(__file__).resolve().parent.parent

# Image used for the throwaway integration database.
TIMESCALE_IMAGE = "timescale/timescaledb:latest-pg16"


# ── L1: in-process app (no real DB) ───────────────────────────────────────────


@pytest.fixture(scope="session")
def app():
    """
    Flask test application for unit tests (Flask test client, no real DB).

    Uses AppTestConfig (sqlite in-memory URL that is never actually connected
    to — unit tests never touch the database).
    """
    cfg = AppTestConfig()
    flask_app = create_app(cfg)
    flask_app.config["TESTING"] = True
    yield flask_app


@pytest.fixture()
def client(app):
    """Flask test client for in-process HTTP requests (unit tests)."""
    with app.test_client() as c:
        yield c


@pytest.fixture()
def app_ctx(app):
    """Push an application context for unit tests that need one."""
    with app.app_context():
        yield app


# ── Shared: resolve a real database URL (testcontainers or env) ────────────────


@pytest.fixture(scope="session")
def _db_provider():
    """
    Session-scoped provider that yields a real database URL, or None.

    Resolution order:
      1. TEST_DATABASE_URL env var.
      2. A throwaway TimescaleDB started via testcontainers (requires Docker).
      3. None — when neither is available (tests depending on it will skip).

    The container (if started) is stopped at session teardown.
    """
    explicit = os.environ.get("TEST_DATABASE_URL")
    if explicit:
        yield explicit
        return

    try:
        from testcontainers.postgres import PostgresContainer
    except ImportError:
        yield None
        return

    container = None
    try:
        # driver="psycopg" → SQLAlchemy URL uses postgresql+psycopg:// (psycopg v3).
        container = PostgresContainer(
            TIMESCALE_IMAGE,
            username="pidrive",
            password="pidrive",
            dbname="pidrive",
            driver="psycopg",
        )
        container.start()
    except Exception as exc:  # Docker down, image pull failed, etc.
        print(f"\n[conftest] testcontainers unavailable ({exc}); "
              f"integration/e2e tests will skip.")
        if container is not None:
            try:
                container.stop()
            except Exception:
                pass
        yield None
        return

    try:
        yield container.get_connection_url()
    finally:
        container.stop()


@pytest.fixture(scope="session")
def db_url(_db_provider):
    """
    A real database URL, or skip the test if no database is available.

    Any fixture/test that depends on db_url (directly or via integration_app,
    db_engine, or live_server) auto-skips when no DB can be provisioned.
    """
    if _db_provider is None:
        pytest.skip(
            "no database available: start Docker (for testcontainers) "
            "or set TEST_DATABASE_URL"
        )
    return _db_provider


@pytest.fixture(scope="session")
def db_engine(db_url):
    """A SQLAlchemy engine bound to the real database (L2 integration tests)."""
    from sqlalchemy import create_engine

    engine = create_engine(db_url, future=True)
    try:
        yield engine
    finally:
        engine.dispose()


@pytest.fixture(scope="session")
def migrated_db(db_url):
    """
    Apply all Alembic migrations to the real database once per session.

    Brings up the full schema + TimescaleDB hypertable + continuous aggregate so
    integration/e2e tests run against the real production DDL (not create_all).
    """
    from alembic import command
    from alembic.config import Config as AlembicConfig

    cfg = AlembicConfig(str(_SERVER_ROOT / "alembic.ini"))
    cfg.set_main_option("script_location", str(_SERVER_ROOT / "migrations"))
    cfg.set_main_option("sqlalchemy.url", db_url)
    command.upgrade(cfg, "head")
    return db_url


@pytest.fixture()
def db_session(db_engine, migrated_db):
    """
    A transaction-isolated session for service tests (L2).

    Each test runs inside a connection-level transaction that is rolled back on
    teardown, so tests are independent and leave no rows behind. Services under
    test flush (not commit), so their writes are visible within the test and
    vanish on rollback. Pass this session explicitly to service functions.
    """
    from sqlalchemy.orm import Session

    connection = db_engine.connect()
    transaction = connection.begin()
    session = Session(bind=connection, expire_on_commit=False)
    try:
        yield session
    finally:
        session.close()
        transaction.rollback()
        connection.close()


# ── L2: integration app (real DB, in-process test client) ──────────────────────


@pytest.fixture(scope="session")
def integration_app(db_url, migrated_db):
    """
    Flask application configured against the REAL TimescaleDB (schema migrated).

    Used by integration tests (Flask test client + real DB) and as the WSGI
    app served by the live_server fixture for e2e tests.
    """
    cfg = AppTestConfig()
    cfg.database_url = db_url
    cfg.env = "dev"
    flask_app = create_app(cfg)
    flask_app.config["TESTING"] = True
    return flask_app


@pytest.fixture()
def integration_client(integration_app):
    """Flask test client backed by the real database (L2)."""
    with integration_app.test_client() as c:
        yield c


# ── L3: live server over a real socket (real process, real DB) ─────────────────


def _wait_for_http(url: str, timeout: float = 10.0) -> None:
    """Poll a URL until it responds or the timeout elapses."""
    import requests

    deadline = time.time() + timeout
    last_err: Exception | None = None
    while time.time() < deadline:
        try:
            requests.get(url, timeout=1)
            return
        except Exception as exc:  # connection refused while server boots
            last_err = exc
            time.sleep(0.1)
    raise RuntimeError(f"live server did not become reachable at {url}: {last_err}")


@pytest.fixture(scope="session")
def live_server(integration_app):
    """
    Boot the real Flask app on a werkzeug server in a background thread.

    Yields the base URL (http://127.0.0.1:<ephemeral-port>). Tests hit it with
    the `requests` library over a real TCP socket — exercising the full WSGI
    path, headers, and status codes exactly as the Android app would.
    """
    from werkzeug.serving import make_server

    server = make_server("127.0.0.1", 0, integration_app, threaded=True)
    port = server.server_port
    base_url = f"http://127.0.0.1:{port}"

    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    _wait_for_http(f"{base_url}/health")

    try:
        yield base_url
    finally:
        server.shutdown()
        thread.join(timeout=5)


@pytest.fixture()
def api_headers(integration_app):
    """Authorization headers carrying the test API key (for L2/L3 requests)."""
    return {"Authorization": f"Bearer {integration_app.config['API_KEY']}"}
