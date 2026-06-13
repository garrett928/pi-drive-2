"""
Error handlers for Pi Drive Server.

Content negotiation (Phase 5): browsers (Accept prefers text/html) get the
friendly `error.html` page; API clients (the app, curl, scripts) get the
consistent JSON body:
  { "error": "<message>", "request_id": "<id>" }

500 errors log the full traceback (with request_id for correlation) but return
a generic message to the client — no internal details leak.
"""

from __future__ import annotations

import logging

from flask import Flask, g, jsonify, render_template, request

logger = logging.getLogger("PiDriveServer")


def _request_id() -> str:
    try:
        return g.get("request_id", "")
    except RuntimeError:
        return ""


def _wants_html() -> bool:
    """
    True when the client is a browser (its Accept header explicitly prefers
    text/html). The Android app and scripts send */* or application/json, so
    they keep getting JSON.
    """
    best = request.accept_mimetypes.best_match(["application/json", "text/html"])
    return (
        best == "text/html"
        and request.accept_mimetypes["text/html"]
        > request.accept_mimetypes["application/json"]
    )


def _json_error(message: str, status: int):
    """Build an error response: HTML for browsers, JSON for everyone else."""
    if _wants_html():
        return render_template("error.html", status=status, message=message), status
    body = {"error": message, "request_id": _request_id()}
    return jsonify(body), status


def register_error_handlers(app: Flask) -> None:
    """Register all HTTP error handlers on the Flask application."""

    @app.errorhandler(400)
    def bad_request(exc):
        return _json_error(str(exc.description) if hasattr(exc, "description") else "Bad request", 400)

    @app.errorhandler(401)
    def unauthorized(exc):
        return _json_error("Unauthorized", 401)

    @app.errorhandler(403)
    def forbidden(exc):
        detail = getattr(exc, "description", "Forbidden")
        return _json_error(str(detail), 403)

    @app.errorhandler(404)
    def not_found(exc):
        return _json_error("Not found", 404)

    @app.errorhandler(405)
    def method_not_allowed(exc):
        return _json_error("Method not allowed", 405)

    @app.errorhandler(409)
    def conflict(exc):
        return _json_error(str(exc.description) if hasattr(exc, "description") else "Conflict", 409)

    @app.errorhandler(413)
    def payload_too_large(exc):
        return _json_error("Request body too large", 413)

    @app.errorhandler(415)
    def unsupported_media_type(exc):
        detail = getattr(exc, "description", "Unsupported media type")
        return _json_error(str(detail), 415)

    @app.errorhandler(422)
    def unprocessable_entity(exc):
        detail = getattr(exc, "description", "Validation failed")
        return _json_error(str(detail), 422)

    @app.errorhandler(503)
    def service_unavailable(exc):
        detail = getattr(exc, "description", "Service unavailable")
        return _json_error(str(detail), 503)

    @app.errorhandler(500)
    def internal_server_error(exc):
        # exc_info=True so the JSON formatter emits the traceback (searchable in
        # Loki); request_id/endpoint are already bound to the log context.
        logger.error("event=unhandled_exception path=%s", request.path, exc_info=True)
        return _json_error("Internal server error", 500)

    @app.errorhandler(Exception)
    def unhandled_exception(exc):
        logger.error("event=unhandled_exception path=%s", request.path, exc_info=True)
        return _json_error("Internal server error", 500)
