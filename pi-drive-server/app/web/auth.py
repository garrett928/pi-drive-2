"""
Web UI authentication + CSRF protection (REQUIREMENTS.md §9.2).

**The UI requires no login by default** (`UI_REQUIRE_AUTH=false` — the
operator runs it on a trusted network and does not want to log in; decision
2026-06-11). Everything in this module is the *opt-in* path for exposed
deployments that set `UI_REQUIRE_AUTH=true`.

There are no user accounts: when enabled, the UI is guarded by the same
single API key as the REST API. `GET /login` shows a form; on a correct key
an authenticated marker is stored in the signed session cookie (Flask signs
it with SECRET_KEY). The key itself is never stored in the session or
rendered.

Form POSTs are always CSRF-protected, independent of the auth flag.

CSRF uses the classic session-token pattern (no extra dependency): a random
token is minted into the session, every form embeds it as a hidden input, and
every web-blueprint POST must echo it back. API endpoints authenticate by
header token and are exempt by construction (they live on other blueprints).
"""

from __future__ import annotations

import functools
import hmac
import logging
import secrets
from collections.abc import Callable
from typing import Any

from flask import abort, current_app, redirect, request, session, url_for

logger = logging.getLogger("Auth")

_SESSION_AUTH_KEY = "ui_authenticated"
_SESSION_CSRF_KEY = "csrf_token"


# ── Login session ──────────────────────────────────────────────────────────────


def is_logged_in() -> bool:
    """True when this browser session has presented the API key (or auth is off)."""
    if not current_app.config["UI_REQUIRE_AUTH"]:
        return True
    return bool(session.get(_SESSION_AUTH_KEY))


def try_login(provided_key: str) -> bool:
    """
    Attempt a UI login with the submitted key. Constant-time comparison, same
    as the API path. On success the session is marked authenticated.
    """
    expected: str = current_app.config["API_KEY"]
    if provided_key and hmac.compare_digest(
        provided_key.encode("utf-8"), expected.encode("utf-8")
    ):
        session[_SESSION_AUTH_KEY] = True
        logger.info("UI login succeeded")
        return True
    logger.warning("UI login failed: wrong key")  # never log the attempted key
    return False


def logout() -> None:
    """Clear the authenticated marker (the CSRF token rotates too)."""
    session.clear()


def ui_login_required(view: Callable[..., Any]) -> Callable[..., Any]:
    """
    Decorator for UI pages: redirect to /login (preserving the destination)
    unless the session is authenticated or UI_REQUIRE_AUTH is false.
    """

    @functools.wraps(view)
    def wrapper(*args: Any, **kwargs: Any) -> Any:
        if not is_logged_in():
            return redirect(url_for("web.login", next=request.path))
        return view(*args, **kwargs)

    return wrapper


# ── CSRF ───────────────────────────────────────────────────────────────────────


def csrf_token() -> str:
    """
    The session's CSRF token, minted on first use. Exposed to templates via a
    context processor so every form can embed it.
    """
    token = session.get(_SESSION_CSRF_KEY)
    if not token:
        token = secrets.token_hex(16)
        session[_SESSION_CSRF_KEY] = token
    return token


def validate_csrf() -> None:
    """
    Abort 403 unless the form's `csrf_token` matches the session's. Called by
    the web blueprint's before_request hook for every POST (including /login —
    a session exists before authentication does).
    """
    expected = session.get(_SESSION_CSRF_KEY)
    provided = request.form.get("csrf_token")
    if (
        not expected
        or not provided
        or not hmac.compare_digest(expected.encode(), provided.encode())
    ):
        logger.warning("Rejected UI POST to %s: CSRF token mismatch", request.path)
        abort(403, description="CSRF validation failed — reload the page and retry")
