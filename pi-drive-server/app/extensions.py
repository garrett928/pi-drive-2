"""
Convenience re-exports for the database layer.

The engine + session live in `app.db.session` (plain SQLAlchemy 2.x, one engine
per Flask app stored on `app.extensions`). This module re-exports the common
helpers so callers can `from app.extensions import get_session`.
"""

from app.db.session import (  # noqa: F401
    get_engine,
    get_session,
    init_engine,
    register_session_teardown,
)
