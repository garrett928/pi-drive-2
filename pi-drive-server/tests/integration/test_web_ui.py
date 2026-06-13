"""
L2 integration tests for the web UI (REQUIREMENTS.md §9, Phase 5).

Drive the real Jinja pages through the Flask test client against the real
migrated TimescaleDB: dashboard + vehicle detail (5.2), telemetry
browser/manual-entry/edit/delete (5.3), CSV upload + admin (5.4).

**Auth default is OFF** (decision 2026-06-11): pages open with no login. The
opt-in login/CSRF path (`UI_REQUIRE_AUTH=true`) is exercised separately via
the `auth_client` fixture. Data is seeded through the real ingest API and
cleaned up via cascade DELETE.
"""

from __future__ import annotations

import io
import re

import pytest

from app import create_app
from app.config import AppTestConfig

pytestmark = pytest.mark.integration

API_KEY = "test-api-key"  # AppTestConfig's key


# ── Fixtures ───────────────────────────────────────────────────────────────────


@pytest.fixture(scope="session")
def auth_app(db_url, migrated_db):
    """A second app instance with the opt-in UI login enabled."""
    cfg = AppTestConfig()
    cfg.database_url = db_url
    cfg.env = "dev"
    cfg.ui_require_auth = True
    flask_app = create_app(cfg)
    flask_app.config["TESTING"] = True
    return flask_app


@pytest.fixture()
def auth_client(auth_app):
    """Test client for the auth-enabled app (login required)."""
    with auth_app.test_client() as c:
        yield c


# ── Helpers ────────────────────────────────────────────────────────────────────


def _csrf_from(html: str) -> str:
    match = re.search(r'name="csrf_token" value="([^"]+)"', html)
    assert match, "no CSRF token found in page"
    return match.group(1)


def _login(client) -> str:
    """Log an auth-enabled client's session in. Returns the CSRF token."""
    token = _csrf_from(client.get("/login").get_data(as_text=True))
    resp = client.post("/login", data={"api_key": API_KEY, "csrf_token": token})
    assert resp.status_code == 302, "login should redirect to the dashboard"
    return token


def _seed(client, headers, vin: str, n: int = 1) -> None:
    body = [
        {"vin": vin, "timestamp": f"2026-04-05T10:{i:02d}:00Z",
         "obd": {"speed_kmh": 40.0 + i, "rpm": 2000 + i}}
        for i in range(n)
    ]
    resp = client.post("/telemetry", json=body, headers=headers)
    assert resp.status_code == 200, resp.get_json()


def _cleanup(client, headers, *vins: str) -> None:
    for vin in vins:
        client.delete(f"/api/v1/vehicles/{vin}?confirm=true", headers=headers)


def _form_csrf(client, path: str) -> str:
    """Fetch a page and scrape its CSRF token (forms render one even sans auth)."""
    return _csrf_from(client.get(path).get_data(as_text=True))


# ── Step 5.1: default no-auth, opt-in auth, CSRF, error pages ───────────────────


def test_pages_open_without_login_by_default(integration_client):
    """The default config requires no login — the dashboard is directly reachable."""
    resp = integration_client.get("/")
    assert resp.status_code == 200
    assert "Fleet dashboard" in resp.get_data(as_text=True)
    # No Login/Logout chrome when auth is off.
    assert "Logout" not in resp.get_data(as_text=True)


def test_auth_enabled_redirects_to_login(auth_client):
    resp = auth_client.get("/")
    assert resp.status_code == 302
    assert "/login" in resp.headers["Location"]


def test_auth_enabled_wrong_key_reprompts(auth_client):
    token = _csrf_from(auth_client.get("/login").get_data(as_text=True))
    resp = auth_client.post(
        "/login", data={"api_key": "wrong-key", "csrf_token": token}
    )
    assert resp.status_code == 401
    assert "Invalid API key" in resp.get_data(as_text=True)
    assert auth_client.get("/").status_code == 302  # still locked out


def test_auth_enabled_correct_key_grants_access(auth_client):
    _login(auth_client)
    resp = auth_client.get("/")
    assert resp.status_code == 200
    assert "Fleet dashboard" in resp.get_data(as_text=True)
    auth_client.get("/logout")
    assert auth_client.get("/").status_code == 302  # logout re-locks


def test_post_without_csrf_rejected(integration_client):
    """CSRF protection is unconditional — even with auth off, a tokenless POST fails."""
    resp = integration_client.post(
        "/telemetry/new", data={"vin": "X", "time": "2026-01-01T00:00:00Z"}
    )
    assert resp.status_code == 403


def test_browser_gets_html_error_api_gets_json(integration_client, api_headers):
    html = integration_client.get(
        "/vehicles/NOSUCHVIN123456789", headers={"Accept": "text/html"}
    )
    assert html.status_code == 404
    assert b"<html" in html.data
    json_resp = integration_client.get(
        "/api/v1/vehicles/NOSUCHVIN123456789", headers=api_headers
    )
    assert json_resp.status_code == 404
    assert json_resp.get_json()["error"] == "Not found"


# ── Step 5.2: dashboard + vehicle detail ───────────────────────────────────────


def test_dashboard_shows_seeded_vehicle(integration_client, api_headers):
    vin = "WEBDASH0000000001"
    try:
        _seed(integration_client, api_headers, vin, n=2)
        html = integration_client.get("/").get_data(as_text=True)
        assert vin in html
        assert "2 samples" in html
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_vehicle_detail_metadata_edit_persists(integration_client, api_headers):
    vin = "WEBDETAIL00000001"
    try:
        _seed(integration_client, api_headers, vin)
        token = _form_csrf(integration_client, f"/vehicles/{vin}")
        resp = integration_client.post(
            f"/vehicles/{vin}",
            data={"csrf_token": token, "nickname": "Garage Queen",
                  "make": "Chevrolet", "model": "Cavalier", "year": "2001"},
        )
        assert resp.status_code == 302
        html = integration_client.get(f"/vehicles/{vin}").get_data(as_text=True)
        assert "Garage Queen" in html
        body = integration_client.get(
            f"/api/v1/vehicles/{vin}", headers=api_headers
        ).get_json()
        assert body["nickname"] == "Garage Queen"
        assert body["year"] == 2001
    finally:
        _cleanup(integration_client, api_headers, vin)


# ── Step 5.3: browser, manual entry, edit, delete ─────────────────────────────


def test_browser_paginates_and_filters(integration_client, api_headers):
    vin = "WEBBROWSE00000001"
    try:
        _seed(integration_client, api_headers, vin, n=3)
        page = integration_client.get(
            f"/vehicles/{vin}/telemetry?limit=2"
        ).get_data(as_text=True)
        assert page.count("btn-danger") == 2  # one Delete button per row
        assert "Older" in page  # has_more pager link

        filtered = integration_client.get(
            f"/vehicles/{vin}/telemetry"
            "?start=2026-04-05T10:01:00Z&end=2026-04-05T10:01:00Z"
        ).get_data(as_text=True)
        assert filtered.count("btn-danger") == 1
        assert "2026-04-05T10:01:00Z" in filtered
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_manual_entry_creates_row(integration_client, api_headers):
    vin = "WEBMANUAL00000001"
    try:
        token = _form_csrf(integration_client, "/telemetry/new")
        resp = integration_client.post(
            "/telemetry/new",
            data={"csrf_token": token, "vin": vin,
                  "time": "2026-04-05T12:00:00Z", "rpm": "1850",
                  "speed_kmh": "62.5"},
        )
        assert resp.status_code == 302

        rows = integration_client.get(
            f"/api/v1/telemetry?vin={vin}", headers=api_headers
        ).get_json()["rows"]
        assert len(rows) == 1
        assert rows[0]["rpm"] == 1850
        assert rows[0]["speed_kmh"] == 62.5
        assert rows[0]["source"] == "manual"
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_manual_entry_validation_rerenders_with_error(integration_client):
    token = _form_csrf(integration_client, "/telemetry/new")
    resp = integration_client.post(
        "/telemetry/new",
        data={"csrf_token": token, "vin": "WEBMANUAL00000002",
              "time": "not-a-time", "rpm": "1850"},
    )
    assert resp.status_code == 400
    html = resp.get_data(as_text=True)
    assert "time" in html  # error flash names the field
    assert 'value="WEBMANUAL00000002"' in html  # submitted values preserved


def test_edit_changes_value(integration_client, api_headers):
    vin = "WEBEDIT0000000001"
    ts = "2026-04-05T10:00:00Z"
    try:
        _seed(integration_client, api_headers, vin)
        form = integration_client.get(f"/telemetry/{vin}/{ts}/edit")
        assert form.status_code == 200
        assert 'value="2000"' in form.get_data(as_text=True)  # prefilled rpm
        token = _csrf_from(form.get_data(as_text=True))

        resp = integration_client.post(
            f"/telemetry/{vin}/{ts}/edit",
            data={"csrf_token": token, "rpm": "3333", "speed_kmh": "99.9"},
        )
        assert resp.status_code == 302

        row = integration_client.get(
            f"/api/v1/telemetry/{vin}/{ts}", headers=api_headers
        ).get_json()
        assert row["rpm"] == 3333
        assert row["speed_kmh"] == 99.9
        assert row["source"] == "device"  # provenance untouched by the form
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_delete_removes_row_with_flash(integration_client, api_headers):
    vin = "WEBDELETE00000001"
    ts = "2026-04-05T10:00:00Z"
    try:
        _seed(integration_client, api_headers, vin)
        token = _form_csrf(integration_client, f"/vehicles/{vin}/telemetry")
        resp = integration_client.post(
            f"/telemetry/{vin}/{ts}/delete",
            data={"csrf_token": token},
            follow_redirects=True,
        )
        assert resp.status_code == 200
        assert "Deleted snapshot" in resp.get_data(as_text=True)
        assert (
            integration_client.get(
                f"/api/v1/telemetry/{vin}/{ts}", headers=api_headers
            ).status_code
            == 404
        )
    finally:
        _cleanup(integration_client, api_headers, vin)


# ── Step 5.4: CSV upload + admin ───────────────────────────────────────────────


def test_import_page_good_csv(integration_client, api_headers):
    vin = "WEBIMPORT00000001"
    csv_text = (
        "vin,time,rpm\n"
        f"{vin},2026-04-06T08:00:00Z,1500\n"
        f"{vin},2026-04-06T08:00:01Z,1501\n"
    )
    try:
        token = _form_csrf(integration_client, "/import")
        resp = integration_client.post(
            "/import",
            data={"csrf_token": token,
                  "file": (io.BytesIO(csv_text.encode()), "rows.csv")},
            content_type="multipart/form-data",
        )
        assert resp.status_code == 200
        assert "Imported 2 row(s)" in resp.get_data(as_text=True)
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_import_page_reports_bad_rows(integration_client, api_headers):
    vin = "WEBIMPORT00000002"
    csv_text = (
        "vin,time,rpm\n"
        f"{vin},2026-04-06T09:00:00Z,1500\n"
        f"{vin},broken,1501\n"
    )
    try:
        token = _form_csrf(integration_client, "/import")
        html = integration_client.post(
            "/import",
            data={"csrf_token": token,
                  "file": (io.BytesIO(csv_text.encode()), "rows.csv")},
            content_type="multipart/form-data",
        ).get_data(as_text=True)
        assert "Imported 1 row(s), skipped 1" in html
        assert "Skipped rows" in html
        assert "<td>3</td>" in html  # bad row's file line number
    finally:
        _cleanup(integration_client, api_headers, vin)


def test_admin_page_and_export_download(integration_client, api_headers):
    vin = "WEBADMIN000000001"
    try:
        _seed(integration_client, api_headers, vin)
        admin = integration_client.get("/admin").get_data(as_text=True)
        assert vin in admin
        assert "configured" in admin
        # Phase 6 sections render on the admin shell.
        assert "Database backup" in admin
        assert "Retention" in admin

        download = integration_client.get(f"/admin/export?vin={vin}")
        assert download.status_code == 200
        assert download.mimetype == "text/csv"
        assert vin in download.get_data(as_text=True)
    finally:
        _cleanup(integration_client, api_headers, vin)
