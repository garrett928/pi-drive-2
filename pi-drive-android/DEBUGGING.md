# Pi Drive — On-device debugging & log capture

This guide explains how to capture a debug session **on the phone, in the car, with no laptop**,
bring the phone back inside, and get the logs into the repo for later review.

It exists to debug two specific problems:

1. **Live dials are empty** even though the app connects to the OBDLink adapter and looks like
   it is pulling data.
2. **The Android Auto app crashes** (it renders on the Desktop Head Unit / DHU but crashes, and
   does not appear at all on a real Android Auto head unit).

Both are hard to diagnose without logs from the actual car + adapter + head unit, which is exactly
what this system captures.

---

## 1. TL;DR — the in-car workflow

1. **Before driving** (once): open Pi Drive → **Settings → (tap the version row 7×) → Developer
   settings → Diagnostics / logs** and make sure **"Save logs to file"** is ON (it is on by
   default).
2. **In the car:** open Pi Drive, connect to the adapter, reproduce the problem (watch the empty
   dials; start Android Auto and let it crash). You don't need to look at anything — it's all being
   logged.
3. **Back inside:** open **Developer settings → Diagnostics / logs → Share logs**. Send the `.zip`
   to yourself (Gmail/Drive/etc.).
4. **At your computer:** download the zip, drop the files into [`debug-logs/`](debug-logs/), and
   commit. See [§4](#4-getting-logs-into-the-repo).

That's it. The rest of this document explains what is captured, the other ways to retrieve logs,
and how to read them.

---

## 2. What gets captured and how

On‑device logging is implemented by
[`FileLogger`](shared/src/main/java/ghart/space/pi_drive/shared/diag/FileLogger.kt), started from
[`PiDriveApplication.onCreate()`](mobile/src/main/java/ghart/space/pi_drive/PiDriveApplication.kt).
It uses two independent mechanisms so nothing is lost:

| Mechanism | What it captures | Why it's reliable |
|---|---|---|
| **logcat pump** — a background thread runs `logcat --pid=<our process>` and appends every line to a file | Every `android.util.Log` call the app already makes (tags `PiDrive`, `VehicleData`, `OBDTransport`, …), plus framework/Car‑App‑Library output for our process | Reads the system log buffer, so it survives even if a thread dies. Filtered to our own PID, so no noise and **no special permission needed**. |
| **uncaught‑exception handler** — `Thread.setDefaultUncaughtExceptionHandler` | The full stack trace of a fatal crash, written **synchronously** to a dedicated `*-crash-*.log` before the process dies | Guaranteed to flush the crash even if the logcat pump is torn down first. Then it delegates to the normal handler so the system crash dialog still appears. |

**Important for Android Auto:** the Car App service
([`PiDriveCarAppService`](shared/src/main/java/ghart/space/pi_drive/shared/auto/PiDriveCarAppService.kt))
runs **in the same process** as the phone app. When the Android Auto host binds it, our
`Application.onCreate()` runs first, so the pump and crash handler are live and the AA crash lands
in the same log files — even if you never opened the phone UI.

### Where the files live

```
Android/data/ghart.space.pi_drive/files/logs/
├── pidrive-20260608-141233.log        ← rolling session log (logcat pump)
├── pidrive-20260608-142010.log        ← next segment (files roll at 4 MB)
└── pidrive-crash-20260608-142048.log  ← one file per fatal crash
```

This is the app's *external files directory*. It needs no storage permission, is visible over USB,
and is wiped when the app is uninstalled. Files roll at 4 MB and the newest 10 are kept.

---

## 3. Three ways to get the logs off the phone

### A. In‑app "Share logs" (recommended — no cable)

**Developer settings → Diagnostics / logs → Share logs.** This zips every log + crash file and opens
the Android share sheet. Email it to yourself, save to Drive, etc. Served via the app's existing
`FileProvider`, so no permission prompt.

### B. USB file transfer (MTP)

Plug the phone into a computer, set USB mode to **File Transfer**, then browse to:

```
<Phone>/Android/data/ghart.space.pi_drive/files/logs/
```

Copy the `.log` files off. (On macOS you may need **Android File Transfer**.)

### C. `adb pull` (when you do have a cable + SDK)

```bash
adb pull /sdcard/Android/data/ghart.space.pi_drive/files/logs ./debug-logs/
```

Or capture a fresh live trace directly (the same tags the file pump uses):

```bash
adb logcat -v threadtime PiDrive:V VehicleData:V OBDTransport:V '*:S'
```

---

## 4. Getting logs into the repo

A folder is reserved for this: **[`debug-logs/`](debug-logs/)**.

```bash
# from the zip you shared to yourself, or from an adb pull:
unzip ~/Downloads/pidrive-logs-*.zip -d pi-drive-android/debug-logs/
git add pi-drive-android/debug-logs/
git commit -m "debug-logs: empty dials + AA crash session 2026-06-08"
```

Name the commit/files with the date and what you were doing. Logs are plain text; review them or
hand them back to Claude Code for analysis.

> **Privacy note:** logs include your VIN (if the adapter returns it), adapter MAC address, and
> phone model. Nothing else identifying. Strip those lines first if the repo is public.

---

## 5. Reading the logs

### 5a. Empty dials

The data pipeline is now instrumented end‑to‑end. Search the log for **`VehicleData`** and read in
this order:

1. **`InitSequence: …`** lines (from
   [`InitializationSequence`](shared/src/main/java/ghart/space/pi_drive/shared/obd/InitializationSequence.kt)).
   The decisive line is:

   ```
   InitSequence: COMPLETE — supportedPids=0 [] protocol=… errors={…}
   InitSequence: supportedPids is EMPTY — OBDPollScheduler will request no PIDs …
   ```

   **If `supportedPids=0`, that is the empty‑dials root cause.** The poll scheduler filters every
   PID against this set
   ([`OBDPollScheduler`](shared/src/main/java/ghart/space/pi_drive/shared/data/OBDPollScheduler.kt)),
   so an empty set means *nothing* is ever requested and every gauge stays blank — even though the
   connection banner says "live."

   Then look just above at the **`PID range 0x00 …`** line to see *why* the scan failed. The raw
   response is printed with control characters escaped, e.g.:

   ```
   InitSequence: PID range 0x00 did NOT parse as a Success frame → parsed=NoData raw="SEARCHING...\r41 00 …"
   ```

   Common real‑world causes the raw bytes will reveal: headers not stripped (`ATH0` didn't take),
   a `SEARCHING...`/`BUS INIT` prefix, multi‑frame CAN responses, or echo residue.

2. **`pollingLoop: started — supportedPids=…`** then **`poll[n] 0100 → …`** lines for the first 20
   cycles show the live request/response for each PID, with decoded bytes. A `NO DATA` or error here
   means the ECU isn't answering that PID.

3. **`snapshot[n]: speed=… rpm=… battery=…`** heartbeat (every 5 s). If every field reads `null`
   while the banner shows "live," the gauges are blank because nothing is decoding — *not* a UI bug.

> The wire‑level bytes (what was literally sent/received over Bluetooth) are under the
> **`OBDTransport`** tag.

### 5b. Android Auto crash

The three AA screens now build their templates inside a guard
([`safeAATemplate`](shared/src/main/java/ghart/space/pi_drive/shared/auto/AAScreenGuard.kt)). When a
Car App Library constraint is violated, instead of crashing the session it:

- logs the **full stack trace** under tag **`PiDrive`**:

  ```
  AA DialsScreen.onGetTemplate FAILED — IllegalStateException: <the exact constraint> …
  ```

- shows a readable error screen on the head unit instead of dying.

Also useful, logged every render:

```
DialsScreen.onGetTemplate: slots=6 streaming=true snapshot=[speed=… rpm=… battery=…]
```

If `slots=0`, the grid is empty (a `GridTemplate` requires a non‑empty list) — that alone crashes
the screen, and the cause is a corrupted/empty saved AA layout.

A fatal crash also writes a dedicated `pidrive-crash-*.log` with the trace at the top.

---

## 6. Android Auto: why it shows in the DHU but not on the real head unit

This is almost always a **developer‑mode gate, not a bug**. Android Auto only loads third‑party apps
that are either published & approved by Google, **or** explicitly allowed in developer mode. The
DHU bypasses this; a real head unit does not. To allow Pi Drive on a real head unit:

1. On the phone, open the **Android Auto** settings (Settings → Connected devices → Android Auto, or
   the standalone app on older phones).
2. Scroll to **Version**, tap it ~10× to unlock **Developer settings** (top‑right ⋮ → Developer
   settings).
3. Enable **"Add new cars to Android Auto"** / **"Unknown sources"**.
4. Reconnect the phone to the car. Pi Drive should now appear in the AA app launcher.

If it appears and then crashes, that's the [§5b](#5b-android-auto-crash) render crash — capture the
log and read the stack trace. With the guard in place it should now show the error screen instead of
disappearing, and the trace tells you the exact constraint.

### Testing AA without the car: the Desktop Head Unit (DHU)

```bash
# one-time: install the DHU from the SDK Manager (SDK Tools → "Android Auto Desktop Head Unit")
adb forward tcp:5277 tcp:5277
~/Library/Android/sdk/extras/google/auto/desktop-head-unit
```

Enable **"Start head unit server"** in the phone's Android Auto developer settings first. The DHU
runs the same `PiDriveCarAppService`, so the guard + logs behave identically.

---

## 7. Turning capture off

Logging is lightweight (it just tees logcat to a file) and on by default. To disable it:
**Developer settings → Diagnostics / logs → "Save logs to file"** OFF. The crash handler stays
installed regardless. **Clear logs** deletes everything in the logs directory.

---

## 8. Where it all lives (for maintainers)

| File | Role |
|---|---|
| [`shared/.../diag/FileLogger.kt`](shared/src/main/java/ghart/space/pi_drive/shared/diag/FileLogger.kt) | logcat pump, crash handler, zip/share, clear |
| [`mobile/.../PiDriveApplication.kt`](mobile/src/main/java/ghart/space/pi_drive/PiDriveApplication.kt) | `FileLogger.init(this)` at process start |
| [`mobile/.../res/xml/file_paths.xml`](mobile/src/main/res/xml/file_paths.xml) | FileProvider paths (cache zip + external logs dir) |
| [`mobile/.../settings/SettingsDevScreen.kt`](mobile/src/main/java/ghart/space/pi_drive/ui/screens/settings/SettingsDevScreen.kt) | "Diagnostics / logs" UI (toggle / Share / Clear) |
| [`shared/.../obd/InitializationSequence.kt`](shared/src/main/java/ghart/space/pi_drive/shared/obd/InitializationSequence.kt) | PID‑support scan diagnostics (empty‑dials root cause) |
| [`shared/.../data/OBDVehicleDataSource.kt`](shared/src/main/java/ghart/space/pi_drive/shared/data/OBDVehicleDataSource.kt) | poll warmup detail + snapshot heartbeat |
| [`shared/.../auto/AAScreenGuard.kt`](shared/src/main/java/ghart/space/pi_drive/shared/auto/AAScreenGuard.kt) | `safeAATemplate` guard used by all 3 AA screens |
