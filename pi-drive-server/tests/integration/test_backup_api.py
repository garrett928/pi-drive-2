"""
L2 integration tests for DB backup/restore + retention (Phase 6, §8 / §6.2).

Backup/restore shells out to the real `pg_dump`/`pg_restore`. These resolve
from PATH or the `PG_BIN_DIR` config; if neither finds the pg client, the whole
module skips (never a false green). Because `pg_restore --clean` and retention
pruning are destructive at the database level, the round-trip and retention
tests run against **disposable databases** created in the same Timescale
container, so the shared session DB is untouched.
"""

from __future__ import annotations

import io
import os
import shutil

import pytest
from sqlalchemy import create_engine, text
from sqlalchemy.engine.url import make_url

from app import create_app
from app.config import AppTestConfig

pytestmark = pytest.mark.integration


def _find_pg_bin() -> str | None:
    """
    Directory containing pg_dump, or None.

    Prefers a pg16 client (matching the timescale/timescaledb:latest-pg16
    server) — a newer pg_dump emits GUCs the pg16 server rejects on restore
    (e.g. `transaction_timeout`), which would fail a restore that is actually
    fine in production (where the image ships a version-matched client).
    """
    for candidate in (
        "/opt/homebrew/opt/postgresql@16/bin",
        "/usr/local/opt/postgresql@16/bin",
    ):
        if os.path.exists(os.path.join(candidate, "pg_dump")):
            return candidate
    found = shutil.which("pg_dump")
    if found:
        return os.path.dirname(found)
    for candidate in ("/opt/homebrew/opt/libpq/bin", "/usr/local/opt/libpq/bin"):
        if os.path.exists(os.path.join(candidate, "pg_dump")):
            return candidate
    return None


_PG_BIN = _find_pg_bin()
_skip_no_pg = pytest.mark.skipif(
    _PG_BIN is None, reason="pg_dump/pg_restore not available (install postgresql client)"
)

API_KEY = "test-api-key"


# ── Fixtures ───────────────────────────────────────────────────────────────────


def _make_app(database_url: str):
    cfg = AppTestConfig()
    cfg.database_url = database_url
    cfg.env = "dev"
    cfg.pg_bin_dir = _PG_BIN or ""
    app = create_app(cfg)
    app.config["TESTING"] = True
    return app


@pytest.fixture(scope="session")
def backup_app(db_url, migrated_db):
    """Backup-enabled app against the shared DB (non-destructive tests only)."""
    return _make_app(db_url)


@pytest.fixture()
def backup_client(backup_app):
    with backup_app.test_client() as c:
        yield c


@pytest.fixture()
def disposable_db(db_url):
    """
    A freshly-created, migrated database in the same container, dropped on
    teardown. Isolates destructive restore/retention from the shared session DB.
    """
    base = make_url(db_url)
    admin_url = base  # the existing DB serves as the maintenance connection
    name = f"pidrive_disp_{os.getpid()}_{abs(hash(base)) % 100000}"

    admin = create_engine(admin_url, isolation_level="AUTOCOMMIT", future=True)
    with admin.connect() as conn:
        conn.execute(text(f'DROP DATABASE IF EXISTS "{name}"'))
        conn.execute(text(f'CREATE DATABASE "{name}"'))
    admin.dispose()

    # render_as_string(hide_password=False): str(URL) masks the password as ***.
    new_url = base.set(database=name).render_as_string(hide_password=False)

    # Migrate the disposable DB to the full production schema.
    from pathlib import Path

    from alembic import command
    from alembic.config import Config as AlembicConfig

    root = Path(__file__).resolve().parent.parent.parent
    cfg = AlembicConfig(str(root / "alembic.ini"))
    cfg.set_main_option("script_location", str(root / "migrations"))
    cfg.set_main_option("sqlalchemy.url", new_url)
    command.upgrade(cfg, "head")

    try:
        yield new_url
    finally:
        admin = create_engine(admin_url, isolation_level="AUTOCOMMIT", future=True)
        with admin.connect() as conn:
            conn.execute(
                text(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                    "WHERE datname = :n AND pid <> pg_backend_pid()"
                ),
                {"n": name},
            )
            conn.execute(text(f'DROP DATABASE IF EXISTS "{name}"'))
        admin.dispose()


def _headers():
    return {"Authorization": f"Bearer {API_KEY}"}


def _seed(client, vin: str, n: int = 2) -> None:
    body = [
        {"vin": vin, "timestamp": f"2026-05-01T08:{i:02d}:00Z",
         "obd": {"speed_kmh": 50.0 + i, "rpm": 2200 + i}}
        for i in range(n)
    ]
    resp = client.post("/telemetry", json=body, headers=_headers())
    assert resp.status_code == 200, resp.get_json()


# ── Step 6.1: backup / restore ─────────────────────────────────────────────────


@_skip_no_pg
def test_backup_streams_pg_dump_archive(backup_client):
    resp = backup_client.get("/api/v1/admin/backup", headers=_headers())
    assert resp.status_code == 200
    assert resp.mimetype == "application/octet-stream"
    assert "attachment" in resp.headers["Content-Disposition"]
    data = resp.get_data()
    assert len(data) > 0
    # pg_dump custom format starts with the magic bytes "PGDMP".
    assert data[:5] == b"PGDMP"


@_skip_no_pg
def test_backup_requires_auth(backup_client):
    assert backup_client.get("/api/v1/admin/backup").status_code == 401


@_skip_no_pg
def test_restore_without_confirm_rejected(backup_client):
    resp = backup_client.post(
        "/api/v1/admin/restore",
        headers=_headers(),
        data={"file": (io.BytesIO(b"PGDMP-not-real"), "x.dump")},
        content_type="multipart/form-data",
    )
    assert resp.status_code == 400
    assert "confirm" in resp.get_json()["error"]


@_skip_no_pg
def test_backup_restore_round_trip(disposable_db):
    """Seed → backup → wipe → restore → data matches (on a disposable DB)."""
    app = _make_app(disposable_db)
    vin = "BACKUPVIN00000001"
    with app.test_client() as client:
        _seed(client, vin, n=3)

        # Back the database up.
        backup = client.get("/api/v1/admin/backup", headers=_headers()).get_data()
        assert backup[:5] == b"PGDMP"

        # Wipe: delete the vehicle (cascades telemetry).
        client.delete(f"/api/v1/vehicles/{vin}?confirm=true", headers=_headers())
        assert client.get(
            f"/api/v1/telemetry?vin={vin}", headers=_headers()
        ).get_json()["rows"] == []

        # Restore from the dump.
        resp = client.post(
            "/api/v1/admin/restore?confirm=true",
            headers=_headers(),
            data={"file": (io.BytesIO(backup), "backup.dump")},
            content_type="multipart/form-data",
        )
        assert resp.status_code == 200, resp.get_json()
        assert resp.get_json() == {"restored": True}

        # Data is back, intact.
        rows = client.get(
            f"/api/v1/telemetry?vin={vin}&order=asc", headers=_headers()
        ).get_json()["rows"]
        assert len(rows) == 3
        assert rows[0]["rpm"] == 2200
        assert rows[0]["speed_kmh"] == 50.0


# ── Step 6.2: retention policy ─────────────────────────────────────────────────


@pytest.mark.integration
def test_retention_policy_registered_and_drops_old_chunks(disposable_db):
    """
    Apply a 30-day retention policy on a disposable DB, then run the job and
    assert an ancient chunk is dropped while a recent row survives.
    """
    from app.db.timescale import ensure_retention_policy

    engine = create_engine(disposable_db, future=True)
    try:
        with engine.begin() as conn:
            conn.execute(
                text(
                    "INSERT INTO vehicles (vin, first_seen, last_seen) "
                    "VALUES ('RETENTIONVIN00001', now(), now())"
                )
            )
            # One ancient row (year 2000 → an old chunk) and one fresh row.
            conn.execute(
                text(
                    "INSERT INTO telemetry (vin, time, speed_kmh, source) VALUES "
                    "('RETENTIONVIN00001', TIMESTAMPTZ '2000-01-01 00:00:00Z', 10, 'device'), "
                    "('RETENTIONVIN00001', now(), 20, 'device')"
                )
            )

        ensure_retention_policy(engine, days=30)

        # The policy job exists.
        with engine.connect() as conn:
            job_id = conn.execute(
                text(
                    "SELECT job_id FROM timescaledb_information.jobs "
                    "WHERE proc_name = 'policy_retention' AND hypertable_name = 'telemetry'"
                )
            ).scalar_one()

        # Force the background job to run now (AUTOCOMMIT — run_job can't be in a tx).
        with engine.connect() as conn:
            conn.execution_options(isolation_level="AUTOCOMMIT")
            conn.execute(text("CALL run_job(:jid)"), {"jid": job_id})

        with engine.connect() as conn:
            remaining = conn.execute(
                text("SELECT count(*) FROM telemetry WHERE vin = 'RETENTIONVIN00001'")
            ).scalar_one()
        assert remaining == 1  # ancient chunk dropped, fresh row kept
    finally:
        engine.dispose()
