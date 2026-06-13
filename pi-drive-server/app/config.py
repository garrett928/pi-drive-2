"""
Configuration for Pi Drive Server.

Loaded via pydantic-settings from environment variables (or a .env file).
Missing DATABASE_URL, API_KEY/API_KEY_FILE, or SECRET_KEY raises at construction
time with a clear error — the server refuses to start with incomplete config.
"""

from __future__ import annotations

from pathlib import Path

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Config(BaseSettings):
    """
    Runtime configuration loaded from the environment.

    Required vars: DATABASE_URL, SECRET_KEY, and either API_KEY or API_KEY_FILE.
    All others have safe defaults.
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # ── Required ─────────────────────────────────────────────────────────────

    database_url: str = ""
    """PostgreSQL connection string, e.g. postgresql+psycopg://user:pass@host/db."""

    secret_key: str = ""
    """Flask session signing key. Must be a long random string in production."""

    # ── API key — one of api_key or api_key_file must be supplied ─────────

    api_key: str = ""
    """Static API key accepted in Authorization: Bearer and X-API-Key headers."""

    api_key_file: str = ""
    """
    Path to a file holding the API key (for Docker/K8s secrets).
    Mutually exclusive with api_key; one of the two must be set.
    """

    # ── Optional with defaults ─────────────────────────────────────────────

    env: str = "prod"
    """Runtime environment label (dev/prod). Used as the 'env' log label."""

    log_level: str = "INFO"
    """Python logging level: DEBUG, INFO, WARNING, ERROR."""

    log_format: str = ""
    """
    'json' emits one JSON object per line (for Alloy/Loki).
    'text' is human-readable (default in dev, json default in prod).
    Resolved in __init__ if left blank.
    """

    ui_require_auth: bool = False
    """
    Opt-in: gate UI pages behind the API-key login form. Default false — the
    UI serves a trusted network and the operator does not want to log in
    (decision 2026-06-11). The REST API stays key-guarded regardless.
    """

    max_body_bytes: int = 10 * 1024 * 1024
    """Maximum allowed decompressed request body size (default 10 MB)."""

    telemetry_retention_days: int | None = None
    """TimescaleDB retention policy in days. None = keep forever."""

    gunicorn_workers: int = 2
    """Number of gunicorn worker processes."""

    pg_bin_dir: str = ""
    """
    Directory holding `pg_dump`/`pg_restore` (the §8 backup feature). Empty =
    resolve them on PATH (the prod image installs the pg16 client). Set this
    when the client tools live outside PATH (e.g. a Homebrew keg in dev).
    """

    @model_validator(mode="after")
    def _validate_required_fields(self) -> Config:
        """Raise early with a clear message if any required field is missing."""
        errors: list[str] = []

        if not self.database_url:
            errors.append("DATABASE_URL is required but not set.")

        if not self.secret_key:
            errors.append("SECRET_KEY is required but not set.")

        # Resolve API key from file if api_key is not set directly.
        if not self.api_key:
            if self.api_key_file:
                path = Path(self.api_key_file)
                if not path.exists():
                    errors.append(
                        f"API_KEY_FILE '{self.api_key_file}' does not exist."
                    )
                else:
                    self.api_key = path.read_text().strip()
                    if not self.api_key:
                        errors.append(
                            f"API_KEY_FILE '{self.api_key_file}' is empty."
                        )
            else:
                errors.append(
                    "Either API_KEY or API_KEY_FILE must be set."
                )

        if errors:
            raise ValueError(
                "Pi Drive Server cannot start — missing required config:\n"
                + "\n".join(f"  • {e}" for e in errors)
            )

        # Default log_format based on environment if not explicitly set.
        if not self.log_format:
            self.log_format = "text" if self.env == "dev" else "json"

        return self


class AppTestConfig(Config):
    """
    Config subclass for the pytest suite.

    Sets safe in-memory/stub values so tests never need real env vars
    (unless they opt into integration tests via TEST_DATABASE_URL).

    Named AppTestConfig (not TestConfig) to avoid pytest mistakenly treating
    it as a test class and failing on the pydantic __init__ signature.
    """

    model_config = SettingsConfigDict(
        env_file=None,
        case_sensitive=False,
        extra="ignore",
    )

    database_url: str = "sqlite:///:memory:"
    api_key: str = "test-api-key"
    secret_key: str = "test-secret-key-do-not-use-in-prod"
    env: str = "dev"
    log_format: str = "text"
    log_level: str = "DEBUG"


