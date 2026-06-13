"""
Shared serialization helpers for the API blueprints.

Centralizes ISO-8601 handling so every endpoint speaks the exact dialect the
Android app does: UTC with a `Z` suffix, milliseconds only when sub-second
precision exists (`2026-05-24T22:15:30.123Z`, `2026-05-25T14:30:00Z`).
"""

from __future__ import annotations

import datetime as dt

from flask import abort


def iso_z(value: dt.datetime | None) -> str | None:
    """
    Format a datetime as RFC3339 with a Z suffix, matching the app's format.

    Uses milliseconds when sub-second precision is present and whole seconds
    otherwise — so a stored `…30.123Z` round-trips back as `…30.123Z`, not
    `…30.123000Z`.
    """
    if value is None:
        return None
    spec = "milliseconds" if value.microsecond else "seconds"
    return value.isoformat(timespec=spec).replace("+00:00", "Z")


def parse_iso(value: str, *, param: str) -> dt.datetime:
    """
    Parse an ISO-8601 query/path value into an aware datetime (naive → UTC).
    Aborts 400 naming the offending parameter on failure.
    """
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        abort(400, description=f"{param} must be an ISO 8601 timestamp")
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=dt.UTC)
