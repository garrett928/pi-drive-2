"""
Pi Drive Server — application factory.

Usage::

    # Production (wsgi.py / gunicorn)
    from app import create_app
    app = create_app()

    # Tests
    from app.config import TestConfig
    app = create_app(TestConfig())

The factory loads config first and fails immediately if required env vars are
missing — the server never starts in a misconfigured state.
"""

from __future__ import annotations

import logging

from flask import Flask

from app.config import Config

logger = logging.getLogger("PiDriveServer")


def create_app(config: Config | None = None) -> Flask:
    """
    Create and configure the Flask application.

    :param config: a Config (or TestConfig) instance. If None, Config() is
        constructed from the environment, which will raise if required env
        vars are missing.
    :returns: configured Flask application.
    """
    if config is None:
        config = Config()

    app = Flask(__name__, instance_relative_config=False)

    # ── Push config values into Flask's config dict ────────────────────────
    # Flask's own mechanisms (e.g. app.testing) rely on uppercase keys.
    app.config.update(
        SECRET_KEY=config.secret_key,
        DATABASE_URL=config.database_url,
        API_KEY=config.api_key,
        ENV=config.env,
        LOG_LEVEL=config.log_level,
        LOG_FORMAT=config.log_format,
        UI_REQUIRE_AUTH=config.ui_require_auth,
        MAX_BODY_BYTES=config.max_body_bytes,
        TELEMETRY_RETENTION_DAYS=config.telemetry_retention_days,
        PG_BIN_DIR=config.pg_bin_dir,
        TESTING=(config.env == "dev"),
    )
    # Keep the typed config object accessible for code that prefers it.
    app.config["PIDRIVE_CONFIG"] = config

    # ── Database (plain SQLAlchemy: engine + session per app) ──────────────────
    from app.extensions import init_engine, register_session_teardown

    engine = init_engine(app)
    register_session_teardown(app)

    # ── Data lifecycle: apply the Timescale retention policy if configured (§6.2) ─
    if config.telemetry_retention_days:
        from app.db.timescale import ensure_retention_policy

        ensure_retention_policy(engine, config.telemetry_retention_days)

    # ── Logging ───────────────────────────────────────────────────────────────
    from app.logging_config import setup_logging

    setup_logging(app)

    # ── Blueprints ────────────────────────────────────────────────────────────
    from app.api.admin import admin_bp
    from app.api.events import events_bp
    from app.api.health import health_bp
    from app.api.stats import stats_bp
    from app.api.telemetry import ingest_bp, telemetry_api_bp
    from app.api.vehicles import vehicles_bp

    app.register_blueprint(health_bp)
    app.register_blueprint(ingest_bp)
    app.register_blueprint(telemetry_api_bp)
    app.register_blueprint(vehicles_bp)
    app.register_blueprint(events_bp)
    app.register_blueprint(stats_bp)
    app.register_blueprint(admin_bp)

    from app.web.routes import web_bp

    app.register_blueprint(web_bp)

    # ── Error handlers ────────────────────────────────────────────────────────
    from app.errors import register_error_handlers

    register_error_handlers(app)

    # Startup config summary — no secrets (never log API_KEY / SECRET_KEY / the
    # DB password). Useful for confirming what a deployed instance is running.
    logger.info(
        "event=startup Pi Drive Server started "
        "env=%s log_format=%s ui_require_auth=%s retention_days=%s workers=%s",
        config.env,
        config.log_format,
        config.ui_require_auth,
        config.telemetry_retention_days,
        config.gunicorn_workers,
    )

    return app
