---
description: Launch Pi Drive on the Android emulator in demo mode. Use when starting development, testing a feature, running the app for the first time in a session, or when asked to "run the app", "start the emulator", or "launch Pi Drive". After a successful launch, always capture a screenshot as proof the app started correctly.
---

Launch Pi Drive on the Android emulator in demo mode.

Arguments (optional): a demo scenario name — CRUISE, CITY, HIGHWAY, HARD_BRAKE, COLD_START, LOW_FUEL, OVERSPEED, or DISCONNECT. Defaults to CRUISE if omitted.

Scenario: $ARGUMENTS

Do the following steps in order. Stop and report the error clearly if any step fails.

## Step 1 — Check for a running device

```bash
~/Library/Android/sdk/platform-tools/adb devices
```

Parse the output. If a line ends with `device` (not `offline`), a device is available — skip to Step 3. If the list is empty or only shows the header, proceed to Step 2.

## Step 2 — Boot the emulator headlessly

```bash
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_36.0 -no-window -no-audio -no-boot-anim &
```

Then wait for it to finish booting. Poll until `sys.boot_completed` is 1:

```bash
~/Library/Android/sdk/platform-tools/adb wait-for-device
```

Then run this in a loop until it returns `1`:
```bash
~/Library/Android/sdk/platform-tools/adb shell getprop sys.boot_completed
```

Sleep 3 seconds between attempts. Timeout after 90 seconds and report failure if boot never completes.

## Step 3 — Build and install

From the `pi-drive-android/` directory:

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-android
./gradlew :mobile:installDebug
```

If `BUILD FAILED` appears in the output, stop and print the full error. Do not proceed.

## Step 4 — Clear old logs

```bash
~/Library/Android/sdk/platform-tools/adb logcat -c
```

## Step 5 — Launch in demo mode

Use the scenario from $ARGUMENTS, defaulting to CRUISE:

```bash
~/Library/Android/sdk/platform-tools/adb shell am start \
  -n ghart.space.pi_drive/.MainActivity \
  --ez demo_mode true \
  --es demo_scenario "CRUISE"
```

Replace CRUISE with the requested scenario if one was provided.

## Step 6 — Confirm launch

Wait 4 seconds, then check logcat for the startup confirmation:

```bash
~/Library/Android/sdk/platform-tools/adb logcat -d -s PiDrive:V AndroidRuntime:E | tail -20
```

Look for `Demo mode active` to confirm success. Look for `FATAL EXCEPTION` to detect a crash.

Report the outcome: which scenario is running, whether the app started successfully, and any relevant log lines.

## Step 7 — Capture launch screenshot (required)

Always take a screenshot after launch as proof the app rendered correctly:

```bash
~/Library/Android/sdk/platform-tools/adb shell screencap -p /sdcard/pidrive_launch.png
~/Library/Android/sdk/platform-tools/adb pull /sdcard/pidrive_launch.png /tmp/pidrive_launch.png
```

Use the Read tool to open `/tmp/pidrive_launch.png`. Confirm:
- The app is showing (not a blank screen or crash dialog)
- The correct scenario data is visible (values are updating, not all zeros)
- No error banners or crash overlays are present

Include the screenshot observation in your final report. This image is the proof of launch.
