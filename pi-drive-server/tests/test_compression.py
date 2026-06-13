"""
L1 unit tests for request-body decoding (app/compression.py).

Exercises `read_request_body` inside a test request context: plain passthrough,
zstd and gzip round-trips, the decompressed-size bound (zip-bomb guard), and
corrupt-encoding rejection.
"""

from __future__ import annotations

import gzip
import json

import pytest
import zstandard
from werkzeug.exceptions import HTTPException

from app.compression import read_request_body


def _read(app, data: bytes, encoding: str | None = None) -> bytes:
    """Run read_request_body inside a request context carrying `data`."""
    headers = {"Content-Encoding": encoding} if encoding else {}
    with app.test_request_context("/telemetry", method="POST", data=data, headers=headers):
        from flask import request

        return read_request_body(request)


def test_plain_body_passes_through(app):
    body = json.dumps({"vin": "X"}).encode()
    assert _read(app, body) == body


def test_zstd_round_trips(app):
    original = json.dumps({"vin": "X", "obd": {"rpm": 2400}}).encode()
    compressed = zstandard.ZstdCompressor().compress(original)
    assert _read(app, compressed, "zstd") == original


def test_gzip_round_trips(app):
    original = json.dumps({"vin": "X"}).encode()
    assert _read(app, gzip.compress(original), "gzip") == original


def test_oversized_raw_body_413(app):
    max_bytes = app.config["MAX_BODY_BYTES"]
    with pytest.raises(HTTPException) as exc_info:
        _read(app, b"x" * (max_bytes + 1))
    assert exc_info.value.code == 413


def test_oversized_decompressed_zstd_413(app):
    """A small compressed body expanding past the limit is rejected (zip bomb)."""
    max_bytes = app.config["MAX_BODY_BYTES"]
    bomb = zstandard.ZstdCompressor().compress(b"\x00" * (max_bytes + 1024))
    assert len(bomb) < max_bytes  # the attack: tiny on the wire
    with pytest.raises(HTTPException) as exc_info:
        _read(app, bomb, "zstd")
    assert exc_info.value.code == 413


def test_corrupt_zstd_400(app):
    with pytest.raises(HTTPException) as exc_info:
        _read(app, b"not zstd at all", "zstd")
    assert exc_info.value.code == 400


def test_corrupt_gzip_400(app):
    with pytest.raises(HTTPException) as exc_info:
        _read(app, b"not gzip at all", "gzip")
    assert exc_info.value.code == 400
