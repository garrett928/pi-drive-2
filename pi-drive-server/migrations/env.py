"""
Alembic environment for Pi Drive Server.

Key design choice: migrations run on a connection set to **AUTOCOMMIT**. This is
required because TimescaleDB continuous-aggregate creation and policy/refresh
calls (migration 0003) cannot run inside a transaction block. Plain DDL (tables,
hypertable) is happy under autocommit too. The trade-off — no all-or-nothing
rollback of a failed migration — is acceptable for this single-tenant DB and is
the standard pattern for Timescale + Alembic.

URL resolution: the "sqlalchemy.url" main option (set programmatically by tests)
takes precedence; otherwise the DATABASE_URL environment variable is used.
"""

from __future__ import annotations

import os
from logging.config import fileConfig

from alembic import context
from sqlalchemy import create_engine

# Import the models so Base.metadata is populated (target for autogenerate).
from app.db.models import Base

config = context.config

if config.config_file_name is not None:
    # disable_existing_loggers=False: fileConfig defaults to True, which would
    # silence every logger already created (the app's "Ingest"/"PiDriveServer"
    # tags) when migrations run in-process — e.g. the boot migration or tests.
    fileConfig(config.config_file_name, disable_existing_loggers=False)

target_metadata = Base.metadata


def _resolve_url() -> str:
    """Return the database URL from the alembic main option or DATABASE_URL env."""
    url = config.get_main_option("sqlalchemy.url")
    if url:
        return url
    env_url = os.environ.get("DATABASE_URL")
    if env_url:
        return env_url
    raise RuntimeError(
        "No database URL: set 'sqlalchemy.url' or the DATABASE_URL environment variable."
    )


def run_migrations_offline() -> None:
    """Emit SQL to stdout without a DB connection (`alembic upgrade --sql`)."""
    context.configure(
        url=_resolve_url(),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    """Run migrations against a live DB using an AUTOCOMMIT connection."""
    engine = create_engine(_resolve_url(), future=True)
    try:
        with engine.connect() as connection:
            # AUTOCOMMIT so Timescale continuous-aggregate DDL (which forbids
            # running inside a transaction) succeeds.
            autocommit_conn = connection.execution_options(isolation_level="AUTOCOMMIT")
            context.configure(connection=autocommit_conn, target_metadata=target_metadata)
            with context.begin_transaction():
                context.run_migrations()
    finally:
        engine.dispose()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
