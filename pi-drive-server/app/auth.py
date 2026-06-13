"""
API-key authentication (REQUIREMENTS.md §5.1).

A single static key guards all write and management endpoints. The key is
accepted two ways:

  - ``Authorization: Bearer <key>``  — primary; what the Android app sends.
  - ``X-API-Key: <key>``             — convenience for curl/scripts/UI.

Comparison is constant-time (`hmac.compare_digest`) so the key cannot be probed
byte-by-byte via response timing. Health endpoints are exempt (they simply do
not apply the decorator). Failures return 401 with the JSON error body from
`app/errors.py`.
"""

from __future__ import annotations

import functools
import hmac
import logging
from collections.abc import Callable
from typing import Any

from flask import abort, current_app, request

logger = logging.getLogger("Auth")


def _provided_key() -> str | None:
    """Extract the API key from the request headers, or None if absent."""
    auth_header = request.headers.get("Authorization", "")
    if auth_header.startswith("Bearer "):
        token = auth_header[len("Bearer "):].strip()
        if token:
            return token
    return request.headers.get("X-API-Key") or None


def require_api_key(view: Callable[..., Any]) -> Callable[..., Any]:
    """
    Decorator: reject the request with 401 unless it carries the valid API key.

    Apply to every ingest/management route. Health probes stay undecorated —
    Kubernetes probes and the app's "Test" button must not need a key.
    """

    @functools.wraps(view)
    def wrapper(*args: Any, **kwargs: Any) -> Any:
        expected: str = current_app.config["API_KEY"]
        provided = _provided_key()
        if not provided or not hmac.compare_digest(
            provided.encode("utf-8"), expected.encode("utf-8")
        ):
            # Log the rejection (never the attempted key) for the Auth tag.
            logger.warning(
                "Rejected request to %s: missing or invalid API key", request.path
            )
            abort(401)
        return view(*args, **kwargs)

    return wrapper
