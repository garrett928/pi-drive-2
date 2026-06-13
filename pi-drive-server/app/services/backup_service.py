"""
Database backup / restore service (REQUIREMENTS.md §8).

This is a **full-database** dump/restore via `pg_dump`/`pg_restore` — distinct
from telemetry CSV (§7, `csv_service`). It is an operator tool, guarded by the
API key at the route layer.

Security:
  - Connection arguments are passed to the client tools as an **argv list**,
    never interpolated into a shell string. The password is passed via the
    `PGPASSWORD` environment variable, never on the command line (where it
    would show up in `ps`).
  - The binaries are resolved from `PG_BIN_DIR` (config) or PATH — never from
    request input.

TimescaleDB restore procedure (this is the part naïve `pg_restore` gets wrong):
a hypertable's chunks and the continuous aggregate cannot simply be recreated
by a plain restore. Timescale requires the database be put into *restoring*
mode around the restore — `timescaledb_pre_restore()` before and
`timescaledb_post_restore()` after — which we run on the app's own engine. The
flag is set at the database level, so the separate `pg_restore` connection
inherits it.
"""

from __future__ import annotations

import datetime as dt
import logging
import os
import shutil
import subprocess
import tempfile
from collections.abc import Iterator
from typing import IO

from sqlalchemy import text
from sqlalchemy.engine import Engine
from sqlalchemy.engine.url import make_url

from app.db.session import get_engine

logger = logging.getLogger("BackupService")

# Stream pg_dump's stdout in 64 KiB chunks — bounds memory regardless of dump size.
_CHUNK = 64 * 1024


class BackupError(RuntimeError):
    """A pg_dump/pg_restore invocation failed; message carries the tool's stderr."""


class PgToolsUnavailable(RuntimeError):
    """`pg_dump`/`pg_restore` could not be found (resolve via PG_BIN_DIR or PATH)."""


def _resolve(tool: str) -> str:
    """
    Locate `pg_dump`/`pg_restore` — `PG_BIN_DIR` if configured, else PATH.
    Raises `PgToolsUnavailable` so the route can answer 503 cleanly.
    """
    from flask import current_app

    bin_dir = current_app.config.get("PG_BIN_DIR") or ""
    candidate = os.path.join(bin_dir, tool) if bin_dir else shutil.which(tool)
    if candidate and os.path.exists(candidate):
        return candidate
    if not bin_dir and candidate:  # shutil.which already verified existence
        return candidate
    raise PgToolsUnavailable(
        f"{tool} not found (set PG_BIN_DIR or install the postgresql client)"
    )


def pg_tools_available() -> bool:
    """True if both pg client tools resolve (for the admin page to disable the
    buttons with a clear message rather than failing on click)."""
    try:
        _resolve("pg_dump")
        _resolve("pg_restore")
        return True
    except PgToolsUnavailable:
        return False


def _conn_args() -> tuple[list[str], dict[str, str]]:
    """
    Build the shared libpq connection argv (`-h -p -U -d`) and an env dict
    carrying `PGPASSWORD`, from the app's DATABASE_URL.

    The SQLAlchemy URL's `+psycopg` driver suffix is irrelevant to the C tools —
    we read the discrete host/port/user/db components, so it is simply ignored.
    """
    from flask import current_app

    url = make_url(current_app.config["DATABASE_URL"])
    args: list[str] = []
    if url.host:
        args += ["-h", url.host]
    if url.port:
        args += ["-p", str(url.port)]
    if url.username:
        args += ["-U", url.username]
    if url.database:
        args += ["-d", url.database]

    env = dict(os.environ)
    if url.password:
        env["PGPASSWORD"] = url.password
    return args, env


def backup_filename(now: dt.datetime | None = None) -> str:
    """A timestamped download name, e.g. `pidrive-backup-20260611T161200Z.dump`."""
    now = now or dt.datetime.now(dt.UTC)
    return f"pidrive-backup-{now.strftime('%Y%m%dT%H%M%SZ')}.dump"


def dump() -> Iterator[bytes]:
    """
    Stream a full-database dump (`pg_dump -Fc`, the custom compressed format)
    as bytes chunks for a download response.

    Raises `PgToolsUnavailable` up front if pg_dump is missing. If pg_dump exits
    non-zero mid-stream, its stderr is logged (the partial download is the
    operator's signal something went wrong) and `BackupError` is raised after
    the stream ends.
    """
    pg_dump = _resolve("pg_dump")
    conn, env = _conn_args()
    # -Fc custom format (compressed, selective restore); -Z handled by format.
    cmd = [pg_dump, *conn, "-Fc", "--no-owner", "--no-privileges"]

    logger.info("event=backup_start tool=%s", pg_dump)
    proc = subprocess.Popen(
        cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env
    )
    assert proc.stdout is not None
    try:
        while True:
            chunk = proc.stdout.read(_CHUNK)
            if not chunk:
                break
            yield chunk
    finally:
        proc.stdout.close()
        returncode = proc.wait()
        stderr = (proc.stderr.read().decode("utf-8", "replace") if proc.stderr else "")
        if proc.stderr:
            proc.stderr.close()
        if returncode != 0:
            logger.error("event=backup_failed code=%d stderr=%s", returncode, stderr.strip())
            raise BackupError(f"pg_dump exited {returncode}: {stderr.strip()}")
        logger.info("event=backup_done")


def restore(upload: IO[bytes], *, engine: Engine | None = None) -> None:
    """
    Restore the database from a `pg_dump -Fc` archive (DESTRUCTIVE — replaces
    existing data). The route requires explicit `?confirm=true`.

    Wraps `pg_restore --clean --if-exists` in the TimescaleDB restoring
    procedure so hypertable chunks and the continuous aggregate restore
    correctly. Raises `BackupError` on failure.
    """
    pg_restore = _resolve("pg_restore")
    conn, env = _conn_args()
    engine = engine if engine is not None else get_engine()

    # Spool the upload to a temp file: pg_restore needs a seekable archive for
    # the custom format, and we want the whole upload on disk before we touch
    # the live database.
    with tempfile.NamedTemporaryFile(suffix=".dump", delete=False) as tmp:
        shutil.copyfileobj(upload, tmp)
        archive_path = tmp.name

    try:
        _set_restoring(engine, True)
        cmd = [
            pg_restore, *conn,
            "--clean", "--if-exists", "--no-owner", "--no-privileges",
            archive_path,
        ]
        logger.info("event=restore_start tool=%s", pg_restore)
        result = subprocess.run(cmd, capture_output=True, env=env)
        if result.returncode != 0:
            stderr = result.stderr.decode("utf-8", "replace").strip()
            logger.error("event=restore_failed code=%d stderr=%s", result.returncode, stderr)
            raise BackupError(f"pg_restore exited {result.returncode}: {stderr}")
        logger.info("event=restore_done")
    finally:
        # Always leave restoring mode, even if pg_restore failed — otherwise the
        # database stays in a degraded state.
        try:
            _set_restoring(engine, False)
        finally:
            os.unlink(archive_path)
            # The restore dropped and recreated every table, so pooled
            # connections now hold cached plans bound to the old table OIDs
            # ("cached plan must not change result type" on next use). Recycle
            # the pool so subsequent requests get fresh connections.
            engine.dispose()


def _set_restoring(engine: Engine, on: bool) -> None:
    """
    Enter/leave TimescaleDB restoring mode via `timescaledb_pre_restore()` /
    `timescaledb_post_restore()`. These set a database-level flag and pause/
    resume background workers, so they must run in AUTOCOMMIT (not a tx block).
    """
    func = "timescaledb_pre_restore" if on else "timescaledb_post_restore"
    with engine.connect() as connection:
        connection.execution_options(isolation_level="AUTOCOMMIT")
        connection.execute(text(f"SELECT {func}();"))
