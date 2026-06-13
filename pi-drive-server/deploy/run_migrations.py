"""
Apply Alembic migrations to `head`, guarded by a Postgres advisory lock.

Why the lock: when several server replicas boot simultaneously they would all
try to run `alembic upgrade head` at once, which can deadlock or double-apply.
`pg_advisory_lock` lets exactly one replica migrate while the others block;
once it finishes and releases, they acquire the lock and find nothing to do.

This is the in-container migration path (called by `entrypoint.sh`). The
Kubernetes `migration-job.yaml` is the alternative one-shot path; set
`RUN_MIGRATIONS=0` to use it instead.

Run standalone:  DATABASE_URL=… python deploy/run_migrations.py
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

from sqlalchemy import create_engine, text

# A fixed 64-bit key, unique to Pi Drive migrations, so the advisory lock never
# collides with application-level locks.
_LOCK_KEY = 7_27274_2026

_SERVER_ROOT = Path(__file__).resolve().parent.parent


def _database_url() -> str:
    url = os.environ.get("DATABASE_URL", "")
    if not url:
        print("run_migrations: DATABASE_URL is not set", file=sys.stderr)
        sys.exit(1)
    return url


def main() -> None:
    from alembic import command
    from alembic.config import Config as AlembicConfig

    url = _database_url()
    engine = create_engine(url, future=True)

    # Hold one dedicated connection for the duration so the session-level
    # advisory lock stays held across the whole migration.
    with engine.connect() as conn:
        conn.execution_options(isolation_level="AUTOCOMMIT")
        print(f"run_migrations: acquiring advisory lock {_LOCK_KEY}…")
        conn.execute(text("SELECT pg_advisory_lock(:k)"), {"k": _LOCK_KEY})
        try:
            cfg = AlembicConfig(str(_SERVER_ROOT / "alembic.ini"))
            cfg.set_main_option("script_location", str(_SERVER_ROOT / "migrations"))
            cfg.set_main_option("sqlalchemy.url", url)
            command.upgrade(cfg, "head")
            print("run_migrations: upgrade head complete.")
        finally:
            conn.execute(text("SELECT pg_advisory_unlock(:k)"), {"k": _LOCK_KEY})
    engine.dispose()


if __name__ == "__main__":
    main()
