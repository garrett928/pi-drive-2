# Pi Drive — canonical LogQL queries

The server ships structured JSON logs to Loki via Alloy (REQUIREMENTS.md §10.4).
These are the go-to queries for "is it healthy, and if not, why". They use the
shared label/metadata schema, so the same patterns work for the Android app's
logs (`app="pi-drive-app"`) in the same Grafana.

**Schema recap**
- **Labels** (indexed, low cardinality): `app`, `component` (`api`|`web`|`ingest`), `level`, `env`
- **Structured metadata** (per line, filter with `|`): `request_id`, `vin`, `device_id`, `endpoint`, `status`, `logger`

## Health & errors

```logql
# All server logs
{app="pi-drive-server"}

# All errors
{app="pi-drive-server", level="error"}

# 5xx responses by endpoint
{app="pi-drive-server"} | status=~"5.." | endpoint!=""

# 4xx responses (client errors)
{app="pi-drive-server"} | status=~"4.."

# Unhandled exceptions (with tracebacks)
{app="pi-drive-server", level="error"} |= "event=unhandled_exception"
```

## Tracing a single request

```logql
# Every line for one request_id (end-to-end trace)
{app="pi-drive-server"} | request_id="<paste-from-X-Request-Id-header>"
```

The server echoes `X-Request-Id` on every response, so grab it from a failing
client call and paste it here.

## Ingest health

```logql
# Ingest batch summaries (one per upload, never per record)
{app="pi-drive-server", component="ingest"} |= "event=ingest_batch"

# Slow ingest batches (>500ms) — duration_ms is structured metadata
{app="pi-drive-server", component="ingest"} |= "event=ingest_batch" | duration_ms > 500

# Rejected uploads (auth or validation)
{app="pi-drive-server", component="ingest"} | level=~"warning|error"
```

## Per-vehicle / per-device

```logql
# Everything for one vehicle
{app="pi-drive-server"} | vin="1G1JC524417100001"

# Errors for one vehicle
{app="pi-drive-server", level="error"} | vin="1G1JC524417100001"

# A specific device's traffic
{app="pi-drive-server"} | device_id="pd-rxv7a3-k9892"
```

## Auth & security

```logql
# Rejected API keys (never logs the attempted key)
{app="pi-drive-server"} | logger="Auth" |= "auth_failed"

# UI login attempts
{app="pi-drive-server", component="web"} |= "UI login"
```

## Backup / lifecycle

```logql
# Backup & restore activity
{app="pi-drive-server", logger="BackupService"}

# CSV imports (with skipped-row counts)
{app="pi-drive-server"} | logger="CsvService" |= "event=csv_import"

# Startup config summaries (no secrets)
{app="pi-drive-server"} |= "event=startup"
```

## Rates (for dashboards / alerts)

```logql
# 5xx rate per minute
sum(rate({app="pi-drive-server"} | status=~"5.." [1m]))

# Request rate by component
sum by (component) (rate({app="pi-drive-server"} |= "event=http_request" [1m]))
```
