---
name: implement-feature
description: Implement the next feature in the Pi Drive implementation plan. Reads progress, loads the right phase file, implements the step, runs tests, takes screenshots, and updates progress.
---

# Implement Feature

Implement a feature step from the Pi Drive implementation plan.

## Procedure

### 1. Determine what to implement

Read `implementation/PROGRESS.md` to find the current active step. If the user specifies a step (e.g., "implement step 2.1"), use that instead.

### 2. Load the phase plan

Read only the specific phase file needed: `implementation/phase-NN-*.md`. Do NOT read all phase files -- only the one for the current step. The phase file contains everything you need: what to build, file paths, test criteria, and verify commands.

### 3. Check prerequisites

The phase file lists dependencies. Verify prerequisite phases are marked DONE in `PROGRESS.md`. If not, inform the user and suggest implementing prerequisites first.

### 4. Implement

Build exactly what the step specifies. Follow the project conventions from `CLAUDE.md`:
- Package root: `ghart.space.pi_drive` (mobile), `ghart.space.pi_drive.shared` (shared)
- Gradle project root: `pi-drive-android/`
- Document all public classes and interfaces with KDoc
- Use named parameters, sealed classes, exhaustive `when`
- Use consistent log tags: `PiDrive`, `OBDTransport`, `VehicleData`, `TripAccumulator`, `AccelDetector`, `GForceDetector`, `TelemetryUploader`

### 5. Write tests

Every step specifies unit tests. Write them. The test file names and test cases are listed in the phase plan. Place tests in:
- `shared/src/test/java/ghart/space/pi_drive/shared/` -- JVM unit tests (no Android needed)
- `mobile/src/test/java/ghart/space/pi_drive/` -- JVM unit tests
- `mobile/src/androidTest/java/ghart/space/pi_drive/` -- instrumented tests (need device)

### 6. Build and test

Run in order, stop on failure:

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-android

# Build
./gradlew :mobile:assembleDebug 2>&1 | tail -20
# Must show: BUILD SUCCESSFUL

# Unit tests
./gradlew :shared:test :mobile:test 2>&1 | tail -30
# Must show: BUILD SUCCESSFUL, no FAILED tests
```

If build or tests fail, fix the issue and re-run. Do not proceed to verification with failing tests.

### 7. Verify on device

If the step has UI (any step in Phases 3, 4, 7.3, 8, 9), run the `/pd-verify` skill or manually:

```bash
ADB=~/Library/Android/sdk/platform-tools/adb

# Install
./gradlew :mobile:installDebug

# Launch in demo mode
$ADB shell am start -n ghart.space.pi_drive/.MainActivity --ez demo_mode true --es demo_scenario "CRUISE"

# Wait, then screenshot
sleep 4
$ADB shell screencap -p /sdcard/screen.png && $ADB pull /sdcard/screen.png /tmp/pidrive-verify.png
```

Read the screenshot with the `Read` tool. Verify the UI matches expectations from the phase plan.

Check logcat:
```bash
$ADB logcat -d -s PiDrive:V OBDTransport:V VehicleData:V AndroidRuntime:E | tail -30
```

### 8. Update progress

After successful implementation and verification, update `implementation/PROGRESS.md`:
- Change the step's status from `NOT STARTED` to `DONE`
- Add the commit hash or PR number in the PR/Commit column
- Update "Active step" to the next step
- Update "Current State" description
- Add the step to the "Completed" section with a one-line summary

### 9. Report

Tell the user:
- What was implemented (files created/modified)
- Test results (pass count)
- Screenshot verification result
- What the next step is
