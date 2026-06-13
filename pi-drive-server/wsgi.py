"""
WSGI entrypoint for gunicorn (production) and `flask run` (development).

Usage::

    # Development
    flask --app wsgi run --port 8080

    # Production (gunicorn)
    gunicorn wsgi:app --bind 0.0.0.0:8080 --workers 2
"""

from app import create_app

app = create_app()
