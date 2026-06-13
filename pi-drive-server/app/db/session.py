"""
Database engine and request-scoped session management (plain SQLAlchemy 2.x).

One engine + sessionmaker per Flask app, stored on `app.extensions` so multiple
apps (e.g. unit app + integration app in tests) never clash on a global. The
request-scoped session is created lazily on first `get_session()` and closed on
app-context teardown.

Architectural rule (REQUIREMENTS.md §3): only `services/` use sessions. Services
accept an optional `session` argument (defaulting to the request session) so they
are testable without a Flask context — tests pass a session directly.
"""

from __future__ import annotations

from flask import Flask, current_app, g
from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker

_ENGINE_KEY = "pidrive_db_engine"
_SESSIONMAKER_KEY = "pidrive_db_sessionmaker"


def init_engine(app: Flask) -> Engine:
    """
    Create the SQLAlchemy engine + sessionmaker for this app and stash them on
    `app.extensions`. Called once from the application factory.

    `pool_pre_ping` guards against stale pooled connections (e.g. after the DB
    restarts) — a cheap liveness check before each checkout.
    """
    config = app.config["PIDRIVE_CONFIG"]
    engine = create_engine(config.database_url, future=True, pool_pre_ping=True)
    factory = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False, future=True)

    app.extensions[_ENGINE_KEY] = engine
    app.extensions[_SESSIONMAKER_KEY] = factory
    return engine


def get_engine() -> Engine:
    """Return the current app's engine (requires an app context)."""
    return current_app.extensions[_ENGINE_KEY]


def get_session() -> Session:
    """
    Return the request-scoped session, creating it on first access.

    The session lives on Flask's `g` and is closed at app-context teardown.
    Services commit explicitly (the session is not auto-commit); the ingest
    orchestrator owns the transaction boundary for a whole batch.
    """
    if "db_session" not in g:
        factory: sessionmaker[Session] = current_app.extensions[_SESSIONMAKER_KEY]
        g.db_session = factory()
    return g.db_session


def register_session_teardown(app: Flask) -> None:
    """Close the request-scoped session when the app context tears down."""

    @app.teardown_appcontext
    def _close_session(exc: BaseException | None = None) -> None:
        session = g.pop("db_session", None)
        if session is not None:
            session.close()
