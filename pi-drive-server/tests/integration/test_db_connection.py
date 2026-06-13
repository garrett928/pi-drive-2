"""
L2 integration tests — prove a REAL TimescaleDB is reachable.

These run against an actual database (testcontainers or TEST_DATABASE_URL) and
skip cleanly when none is available. For Phase 0 they assert the harness itself:
the DB connects and it really is TimescaleDB (not vanilla Postgres). Phase 1
builds migration/service tests on top of the same fixtures.
"""

from __future__ import annotations

import pytest

pytestmark = pytest.mark.integration


def test_database_connects(db_engine):
    """A trivial SELECT 1 succeeds against the real database."""
    from sqlalchemy import text

    with db_engine.connect() as conn:
        assert conn.execute(text("SELECT 1")).scalar() == 1


def test_timescaledb_extension_available(db_engine):
    """
    The provisioned database is TimescaleDB, not plain Postgres.

    Asserting the extension is available (in pg_available_extensions) proves the
    integration layer exercises Timescale-specific behavior — the whole reason
    we use a real container instead of sqlite.
    """
    from sqlalchemy import text

    with db_engine.connect() as conn:
        version = conn.execute(
            text(
                "SELECT default_version FROM pg_available_extensions "
                "WHERE name = 'timescaledb'"
            )
        ).scalar()
    assert version is not None, "timescaledb extension not available on this database"


def test_integration_app_uses_real_db(integration_app, db_url):
    """The integration Flask app is configured against the real DB URL."""
    assert integration_app.config["DATABASE_URL"] == db_url
