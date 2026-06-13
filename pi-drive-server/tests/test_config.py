"""
Tests for app/config.py.

Verifies that:
  - Config constructs when all required env vars are present.
  - Config reads the API key from API_KEY_FILE when API_KEY is absent.
  - Config raises at construction with a clear message when vars are missing.
  - TestConfig constructs with no env vars at all.
"""

from __future__ import annotations

import pytest

from app.config import AppTestConfig, Config


def _env(overrides: dict) -> dict:
    """Build a minimal valid env dict with the given overrides applied."""
    base = {
        "DATABASE_URL": "postgresql+psycopg://user:pass@localhost/pidrive",
        "API_KEY": "test-key",
        "SECRET_KEY": "test-secret",
    }
    base.update(overrides)
    return {k: v for k, v in base.items() if v is not None}


class TestConfigConstruction:
    def test_constructs_with_all_required_env(self, monkeypatch):
        """Config builds successfully when all three required vars are set."""
        env = _env({})
        for k, v in env.items():
            monkeypatch.setenv(k, v)

        cfg = Config()

        assert cfg.database_url == env["DATABASE_URL"]
        assert cfg.api_key == env["API_KEY"]
        assert cfg.secret_key == env["SECRET_KEY"]

    def test_reads_api_key_from_file(self, monkeypatch, tmp_path):
        """API_KEY_FILE is read from disk when API_KEY is absent."""
        key_file = tmp_path / "api_key.txt"
        key_file.write_text("file-based-key\n")

        monkeypatch.setenv("DATABASE_URL", "postgresql+psycopg://u:p@h/db")
        monkeypatch.setenv("SECRET_KEY", "s3cr3t")
        monkeypatch.delenv("API_KEY", raising=False)
        monkeypatch.setenv("API_KEY_FILE", str(key_file))

        cfg = Config()

        assert cfg.api_key == "file-based-key"

    def test_raises_when_database_url_missing(self, monkeypatch):
        """Missing DATABASE_URL raises ValueError at construction."""
        monkeypatch.setenv("API_KEY", "k")
        monkeypatch.setenv("SECRET_KEY", "s")
        monkeypatch.delenv("DATABASE_URL", raising=False)

        with pytest.raises(ValueError, match="DATABASE_URL"):
            Config()

    def test_raises_when_api_key_missing(self, monkeypatch):
        """Missing API_KEY and API_KEY_FILE raises ValueError."""
        monkeypatch.setenv("DATABASE_URL", "postgresql+psycopg://u:p@h/db")
        monkeypatch.setenv("SECRET_KEY", "s")
        monkeypatch.delenv("API_KEY", raising=False)
        monkeypatch.delenv("API_KEY_FILE", raising=False)

        with pytest.raises(ValueError, match="API_KEY"):
            Config()

    def test_raises_when_secret_key_missing(self, monkeypatch):
        """Missing SECRET_KEY raises ValueError."""
        monkeypatch.setenv("DATABASE_URL", "postgresql+psycopg://u:p@h/db")
        monkeypatch.setenv("API_KEY", "k")
        monkeypatch.delenv("SECRET_KEY", raising=False)

        with pytest.raises(ValueError, match="SECRET_KEY"):
            Config()

    def test_raises_when_api_key_file_missing(self, monkeypatch, tmp_path):
        """API_KEY_FILE pointing at a non-existent path raises ValueError."""
        monkeypatch.setenv("DATABASE_URL", "postgresql+psycopg://u:p@h/db")
        monkeypatch.setenv("SECRET_KEY", "s")
        monkeypatch.delenv("API_KEY", raising=False)
        monkeypatch.setenv("API_KEY_FILE", str(tmp_path / "no-such-file.txt"))

        with pytest.raises(ValueError, match="does not exist"):
            Config()

    def test_default_log_format_json_in_prod(self, monkeypatch):
        """log_format defaults to 'json' when ENV is not 'dev'."""
        monkeypatch.setenv("DATABASE_URL", "postgresql+psycopg://u:p@h/db")
        monkeypatch.setenv("API_KEY", "k")
        monkeypatch.setenv("SECRET_KEY", "s")
        monkeypatch.setenv("ENV", "prod")
        monkeypatch.delenv("LOG_FORMAT", raising=False)

        cfg = Config()
        assert cfg.log_format == "json"

    def test_default_log_format_text_in_dev(self, monkeypatch):
        """log_format defaults to 'text' when ENV=dev."""
        monkeypatch.setenv("DATABASE_URL", "postgresql+psycopg://u:p@h/db")
        monkeypatch.setenv("API_KEY", "k")
        monkeypatch.setenv("SECRET_KEY", "s")
        monkeypatch.setenv("ENV", "dev")
        monkeypatch.delenv("LOG_FORMAT", raising=False)

        cfg = Config()
        assert cfg.log_format == "text"


class TestAppTestConfig:
    def test_constructs_without_env_vars(self):
        """AppTestConfig requires no env vars — safe for the pytest suite."""
        cfg = AppTestConfig()

        assert cfg.api_key == "test-api-key"
        assert cfg.secret_key == "test-secret-key-do-not-use-in-prod"
        assert "sqlite" in cfg.database_url
