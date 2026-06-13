"""
Compression helpers for building L3 request bodies.

The Android app may send telemetry with `Content-Encoding: zstd` (or gzip).
These helpers produce compressed bodies so e2e tests can assert the server
decodes them on the wire (REQUIREMENTS.md §5.2).
"""

from __future__ import annotations

import gzip
import json

import zstandard as zstd


def to_json_bytes(payload) -> bytes:
    """Serialize a payload (dict or list) to compact UTF-8 JSON bytes."""
    return json.dumps(payload, separators=(",", ":")).encode("utf-8")


def zstd_compress(data: bytes) -> bytes:
    """Compress bytes with zstd (matches Content-Encoding: zstd)."""
    return zstd.ZstdCompressor().compress(data)


def gzip_compress(data: bytes) -> bytes:
    """Compress bytes with gzip (matches Content-Encoding: gzip)."""
    return gzip.compress(data)
