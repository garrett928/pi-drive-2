"""
Request-body decoding: zstd / gzip / raw, with a decompressed-size bound.

The Android app may send telemetry bodies compressed with
``Content-Encoding: zstd`` (or ``gzip``). `read_request_body` decodes the body
and enforces `MAX_BODY_BYTES` on the **decompressed** size — a tiny compressed
payload must not be able to expand into gigabytes server-side (zip-bomb guard).

Aborts raised here use the JSON error handlers from `app/errors.py`:
  413 — decompressed (or raw) body exceeds MAX_BODY_BYTES
  400 — body claims a compression encoding but cannot be decoded
"""

from __future__ import annotations

import gzip
import io
import logging

import zstandard
from flask import Request, abort, current_app

logger = logging.getLogger("Ingest")

# Read decompressed output in chunks so a huge body never materializes at once.
_CHUNK_BYTES = 64 * 1024


def _bounded_read(reader, max_bytes: int) -> bytes:
    """
    Read at most `max_bytes + 1` decompressed bytes from a stream reader.

    Reading one byte past the limit lets the caller distinguish "exactly at the
    limit" (allowed) from "exceeds the limit" (413) without ever buffering more
    than limit+1 bytes.
    """
    chunks: list[bytes] = []
    remaining = max_bytes + 1
    while remaining > 0:
        chunk = reader.read(min(_CHUNK_BYTES, remaining))
        if not chunk:
            break
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def read_request_body(request: Request) -> bytes:
    """
    Return the request body as plain bytes, decoding zstd/gzip if indicated.

    Enforces MAX_BODY_BYTES on the decompressed size (413 beyond it). The
    caller is responsible for JSON parsing.
    """
    max_bytes: int = current_app.config["MAX_BODY_BYTES"]
    raw = request.get_data(cache=False)
    encoding = (request.headers.get("Content-Encoding") or "").strip().lower()

    if encoding == "zstd":
        try:
            reader = zstandard.ZstdDecompressor().stream_reader(io.BytesIO(raw))
            body = _bounded_read(reader, max_bytes)
        except zstandard.ZstdError:
            abort(400, description="Body is not valid zstd data")
    elif encoding == "gzip":
        try:
            body = _bounded_read(gzip.GzipFile(fileobj=io.BytesIO(raw)), max_bytes)
        except (OSError, EOFError):
            abort(400, description="Body is not valid gzip data")
    else:
        # No (or identity) encoding — the raw bytes are the body.
        body = raw

    if len(body) > max_bytes:
        logger.warning(
            "Rejected oversized body: >%d bytes decompressed (encoding=%s)",
            max_bytes,
            encoding or "none",
        )
        abort(413)

    return body
