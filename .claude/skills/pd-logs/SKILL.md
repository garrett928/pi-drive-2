---
description: Dump and filter Pi Drive logcat output. Use when debugging unexpected behavior, after a crash, when a feature isn't working as expected, or to confirm a feature is emitting the expected log lines. Use before or after taking screenshots to correlate what the UI shows with what the app logged.
---

Dump and filter Pi Drive logcat output to understand what the app is doing.

Arguments (optional): a filter keyword to narrow output — e.g. `obd`, `trip`, `alert`, `upload`, `crash`, or a specific log tag name. Defaults to all Pi Drive tags.

Filter: $ARGUMENTS

## Step 1 — Check for a connected device

```bash
~/Library/Android/sdk/platform-tools/adb devices
```

If no device is available, stop and say so.

## Step 2 — Determine which tags to show

Pi Drive log tags and what they cover:

| Tag | Covers |
|---|---|
| `PiDrive` | App lifecycle, demo mode, mode switches |
| `OBDTransport` | Raw AT commands sent and responses received |
| `VehicleData` | Parsed PID values, snapshot emissions |
| `TripAccumulator` | Trip start/stop/pause, distance and duration updates |
| `AccelDetector` | Speed delta calculations, hard accel/brake events |
| `GForceDetector` | G-force sensor fusion, calibration, events |
| `TelemetryUploader` | HTTP POST status, retry attempts, queue depth |
| `AndroidRuntime` | Fatal crashes and uncaught exceptions |

Map $ARGUMENTS to tags:
- `obd` → `OBDTransport:V VehicleData:V`
- `trip` → `TripAccumulator:V`
- `alert` → `AccelDetector:V GForceDetector:V`
- `upload` or `server` → `TelemetryUploader:V`
- `crash` → `AndroidRuntime:E`
- anything else → use as a literal tag name: `ARGUMENT:V`
- no argument → all: `PiDrive:V OBDTransport:V VehicleData:V TripAccumulator:V AccelDetector:V GForceDetector:V TelemetryUploader:V AndroidRuntime:E`

## Step 3 — Dump recent logs

```bash
~/Library/Android/sdk/platform-tools/adb logcat -d -s TAGS | tail -100
```

Replace `TAGS` with the tag string determined in Step 2.

## Step 4 — Highlight key patterns

After printing the raw log, call out any of these if present:

| Pattern | Meaning |
|---|---|
| `FATAL EXCEPTION` | App crashed — print the full stack trace |
| `Demo mode active` | Demo mode confirmed running |
| `TCP transport connected` | ELM327 emulator connection succeeded |
| `NO DATA` in OBDTransport | A PID is unsupported — check graceful handling |
| `HARD_BRAKE` or `HARD_ACCEL` | Alert fired — show the mph/s or g value |
| `POST 4xx` or `POST 5xx` | Server upload failing — show the status code |
| `queue depth:` | Offline buffer growing — uploads may be falling behind |

## Step 5 — Live tail (optional)

If $ARGUMENTS contains `live` or `watch`, run a live tail instead of a dump:

```bash
~/Library/Android/sdk/platform-tools/adb logcat -s TAGS
```

This will stream indefinitely. Tell the user to press Ctrl+C to stop.
