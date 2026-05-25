---
description: Verify that a Pi Drive feature is fully working end-to-end. Use after implementing a feature, when asked to "verify the feature", "check that it works", "confirm the implementation", "does this work", or "make sure it works". Orchestrates build → unit tests → launch → navigate → screenshot → logcat → comparison to spec. Do NOT skip any step. Screenshots are mandatory proof.
argument-hint: [feature-name] [screen-to-check]
---

End-to-end verification of a Pi Drive feature. Verifies the implementation is correct by running tests, launching the app, exercising the feature, and capturing screenshot proof.

Feature: $ARGUMENTS

Do the following steps in order. Do not skip steps. Each step has a required artifact.

---

## Step 1 — Run unit tests

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-android
./gradlew :shared:test :mobile:test 2>&1 | tail -40
```

**Required artifact:** pass/fail count. If any test fails, stop, report the failure, and do not continue until tests pass.

---

## Step 2 — Build

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-android
./gradlew :mobile:assembleDebug 2>&1 | tail -20
```

**Required artifact:** `BUILD SUCCESSFUL`. If `BUILD FAILED`, stop, report the compiler error, fix it, and re-run this step.

---

## Step 3 — Check for a running device

```bash
~/Library/Android/sdk/platform-tools/adb devices
```

If no device shows `device` status, start the emulator:

```bash
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.0 -no-window -no-audio -no-boot-anim &
~/Library/Android/sdk/platform-tools/adb wait-for-device
```

Poll until booted:
```bash
~/Library/Android/sdk/platform-tools/adb shell getprop sys.boot_completed
```

Wait for `1`. Timeout after 90 seconds.

---

## Step 4 — Install and launch

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-android
./gradlew :mobile:installDebug
~/Library/Android/sdk/platform-tools/adb logcat -c

~/Library/Android/sdk/platform-tools/adb shell am start \
  -n ghart.space.pi_drive/.MainActivity \
  --ez demo_mode true \
  --es demo_scenario "CRUISE"
```

Choose a scenario that exercises the feature being verified. For UI/layout features, `CRUISE` is fine. For event detection features, use `HARD_BRAKE`. For temperature features, use `COLD_START`.

Wait 4 seconds for the app to render.

---

## Step 5 — Screenshot: initial state (required)

```bash
~/Library/Android/sdk/platform-tools/adb shell screencap -p /sdcard/verify_step5.png
~/Library/Android/sdk/platform-tools/adb pull /sdcard/verify_step5.png /tmp/pidrive_verify_step5.png
```

**Required artifact:** Read `/tmp/pidrive_verify_step5.png` with the Read tool. Describe what is visible. The app must be showing, not blank or crashed.

---

## Step 6 — Navigate to the feature's screen (if needed)

If the feature is on a screen other than the home/dashboard:

```bash
~/Library/Android/sdk/platform-tools/adb shell uiautomator dump /sdcard/ui.xml
~/Library/Android/sdk/platform-tools/adb pull /sdcard/ui.xml /tmp/ui.xml
```

Read `/tmp/ui.xml` to find the tappable element for the target screen. Then tap it:

```bash
~/Library/Android/sdk/platform-tools/adb shell input tap <x> <y>
```

Wait 2 seconds, then take another screenshot:

```bash
~/Library/Android/sdk/platform-tools/adb shell screencap -p /sdcard/verify_step6.png
~/Library/Android/sdk/platform-tools/adb pull /sdcard/verify_step6.png /tmp/pidrive_verify_step6.png
```

**Required artifact:** Read `/tmp/pidrive_verify_step6.png`. Confirm the correct screen is showing.

---

## Step 7 — Exercise the feature (if it requires interaction)

If the feature requires user input (button tap, scroll, slider drag, text input), simulate it:

```bash
# Tap a specific element
~/Library/Android/sdk/platform-tools/adb shell input tap <x> <y>

# Scroll down
~/Library/Android/sdk/platform-tools/adb shell input swipe 540 1200 540 600

# For a slider at position (x,y), drag it right
~/Library/Android/sdk/platform-tools/adb shell input swipe <x> <y> <x+200> <y>
```

Wait 2 seconds after each interaction.

---

## Step 8 — Screenshot: feature state (required)

```bash
~/Library/Android/sdk/platform-tools/adb shell screencap -p /sdcard/verify_step8.png
~/Library/Android/sdk/platform-tools/adb pull /sdcard/verify_step8.png /tmp/pidrive_verify_step8.png
```

**Required artifact:** Read `/tmp/pidrive_verify_step8.png`. This is the primary proof screenshot. It must show the feature in the expected state.

For multi-step flows (e.g., connect flow, trip recording), take a screenshot at each significant state transition, incrementing the filename: `verify_step8a.png`, `verify_step8b.png`, etc.

---

## Step 9 — Screen recording for interaction-heavy flows (optional but recommended)

For features that require showing animated transitions, scrolling behavior, or a multi-step interaction flow, capture a screen recording instead of (or in addition to) static screenshots:

```bash
# Start recording (max 3 minutes; stop manually or it auto-stops)
~/Library/Android/sdk/platform-tools/adb shell screenrecord /sdcard/verify_recording.mp4 &
RECORD_PID=$!

# ... perform the interactions here (taps, scrolls, waits) ...

# Stop recording
sleep 1 && ~/Library/Android/sdk/platform-tools/adb shell pkill -l SIGINT screenrecord
sleep 2

# Pull the recording
~/Library/Android/sdk/platform-tools/adb pull /sdcard/verify_recording.mp4 /tmp/pidrive_verify_recording.mp4
```

The recording file at `/tmp/pidrive_verify_recording.mp4` is the interaction proof artifact. Note its path in your final report.

---

## Step 10 — Check logcat (required)

```bash
~/Library/Android/sdk/platform-tools/adb logcat -d \
  -s PiDrive:V OBDTransport:V VehicleData:V TripAccumulator:V AccelDetector:V GForceDetector:V TelemetryUploader:V AndroidRuntime:E \
  | tail -60
```

**Required artifact:** Log output. Check for:
- `FATAL EXCEPTION` → crash. Stop and report.
- Feature-specific log tags confirming the new code ran (e.g. `TripAccumulator: trip started`, `AccelDetector: HARD_BRAKE event`)
- Any error or warning lines relevant to the feature

---

## Step 11 — Compare to design spec (for UI features)

Read the relevant section of the design file and compare against the Step 8 screenshot:

| Screen | Design file | Section |
|---|---|---|
| Home/dashboard | `ui-handoff/pi-drive/project/pd-screens-phone.jsx` | `ScreenHome` |
| Trips | `pd-screens-phone.jsx` | `ScreenTrips` |
| Connect | `pd-screens-phone.jsx` | `ScreenConnect` |
| Settings | `pd-screens-settings.jsx` | `ScreenSettings` |
| AA Dials | `ui-handoff/pi-drive/project/pd-screens-aa.jsx` | `AAScreenDials` |
| AA Graphs | `pd-screens-aa.jsx` | `AAScreenGraphs` |

Color tokens: `ui-handoff/pi-drive/project/pd-tokens.jsx`

---

## Step 12 — Final report

Summarize:

```
FEATURE: <feature name>
STATUS: PASS / FAIL / PARTIAL

Tests: X passed, Y failed
Build: SUCCESS / FAILED

Screenshots:
  - /tmp/pidrive_verify_step5.png — initial state: [description]
  - /tmp/pidrive_verify_step8.png — feature state: [description]
  [additional screenshots if taken]

Screen recording: /tmp/pidrive_verify_recording.mp4 [if taken]

Logcat: [key log lines confirming feature ran, or crash details]

Design comparison: [MATCHES / DISCREPANCIES: list specific differences]

Next: [what to fix if status is not PASS, or "ready to commit" if PASS]
```

Do not report PASS unless:
1. All unit tests passed
2. Build succeeded
3. At least one screenshot shows the feature in the expected state
4. Logcat shows no crashes and confirms the feature code ran
