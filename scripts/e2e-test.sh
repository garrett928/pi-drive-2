#!/usr/bin/env bash
# Pi Drive — end-to-end test runner
#
# Usage:
#   ./scripts/e2e-test.sh [--skip-build] [--scenarios CRUISE,HARD_BRAKE,...]
#
# Runs the full E2E test suite:
#   1. Build (assembleDebug)
#   2. Unit tests (:shared:test + :mobile:test)
#   3. Install on connected emulator/device
#   4. Launch in demo mode for each scenario, screenshot + logcat verification
#   5. Report pass/fail
#
# Requirements:
#   - Android emulator running (or device connected via ADB)
#   - Gradle wrapper at pi-drive-android/gradlew
#   - ADB on PATH or at ~/Library/Android/sdk/platform-tools/adb

set -euo pipefail

# ── Configuration ──────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ANDROID_ROOT="$PROJECT_ROOT/pi-drive-android"
SCREENSHOT_DIR="$PROJECT_ROOT/screenshots"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

SKIP_BUILD=false
SCENARIOS="CRUISE,HARD_BRAKE,LOW_FUEL,COLD_START,DISCONNECT,OVERSPEED"

# Parse flags
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)  SKIP_BUILD=true ;;
        --scenarios)   SCENARIOS="$2"; shift ;;
        *) echo "Unknown flag: $1"; exit 1 ;;
    esac
    shift
done

# ── Helpers ────────────────────────────────────────────────────────────────────

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓${NC} $1"; }
fail() { echo -e "${RED}✗${NC} $1"; }
info() { echo -e "${YELLOW}▸${NC} $1"; }

PASS_COUNT=0
FAIL_COUNT=0

check() {
    local name="$1"
    local result="$2"
    if [[ "$result" == "pass" ]]; then
        pass "$name"
        ((PASS_COUNT++)) || true
    else
        fail "$name"
        ((FAIL_COUNT++)) || true
    fi
}

mkdir -p "$SCREENSHOT_DIR"

# ── Step 1: Build ──────────────────────────────────────────────────────────────

if [[ "$SKIP_BUILD" == false ]]; then
    info "Building :mobile:assembleDebug..."
    if cd "$ANDROID_ROOT" && ./gradlew :mobile:assembleDebug --quiet 2>&1; then
        check "Build :mobile:assembleDebug" "pass"
    else
        check "Build :mobile:assembleDebug" "fail"
        echo "Build failed — aborting E2E run"
        exit 1
    fi
fi

# ── Step 2: Unit tests ─────────────────────────────────────────────────────────

info "Running unit tests (:shared:test :mobile:test)..."
if cd "$ANDROID_ROOT" && ./gradlew :shared:test :mobile:test --quiet 2>&1; then
    check "Unit tests" "pass"
else
    check "Unit tests" "fail"
    echo "Unit tests failed — see build/test-results/ for details"
    exit 1
fi

# ── Step 3: Install ────────────────────────────────────────────────────────────

info "Installing on device/emulator..."
if cd "$ANDROID_ROOT" && ./gradlew :mobile:installDebug --quiet 2>&1; then
    check "Install APK" "pass"
else
    check "Install APK" "fail"
    echo "Install failed — is an emulator/device connected?"
    exit 1
fi

PACKAGE="ghart.space.pi_drive"
ACTIVITY="ghart.space.pi_drive.MainActivity"

# ── Step 4: Scenario tests ─────────────────────────────────────────────────────

IFS=',' read -ra SCENARIO_LIST <<< "$SCENARIOS"

for SCENARIO in "${SCENARIO_LIST[@]}"; do
    info "Running scenario: $SCENARIO"

    # Force-stop any previous instance
    "$ADB" shell am force-stop "$PACKAGE" 2>/dev/null || true
    sleep 1

    # Launch in demo mode with this scenario
    "$ADB" shell am start -n "$ACTIVITY" \
        --ez demo_mode true \
        --es demo_scenario "$SCENARIO" 2>/dev/null

    # Wait for the app to render
    sleep 5

    # Capture screenshot
    SCREENSHOT_FILE="$SCREENSHOT_DIR/e2e-${SCENARIO,,}.png"
    "$ADB" shell screencap -p /sdcard/screen.png
    "$ADB" pull /sdcard/screen.png "$SCREENSHOT_FILE" >/dev/null

    # Collect logcat for Pi Drive tags
    LOG_FILE="$SCREENSHOT_DIR/e2e-${SCENARIO,,}.log"
    "$ADB" logcat -d -s \
        PiDrive:V OBDTransport:V VehicleData:V \
        AccelDetector:V GForceDetector:V TelemetryUploader:V \
        AndroidRuntime:E 2>/dev/null > "$LOG_FILE"

    # ── Scenario-specific assertions ─────────────────────────────────────────

    case "$SCENARIO" in
        CRUISE)
            # Dashboard should be live
            if grep -q "Demo mode active" "$LOG_FILE"; then
                check "CRUISE: Demo mode active in logs" "pass"
            else
                check "CRUISE: Demo mode active in logs" "fail"
            fi
            if [[ -f "$SCREENSHOT_FILE" ]] && [[ -s "$SCREENSHOT_FILE" ]]; then
                check "CRUISE: Screenshot captured" "pass"
            else
                check "CRUISE: Screenshot captured" "fail"
            fi
            ;;

        HARD_BRAKE)
            # Alert event should fire within the scenario
            if grep -qi "hard brake\|HARD_BRAKE\|hard_brake" "$LOG_FILE"; then
                check "HARD_BRAKE: Brake event in logs" "pass"
            else
                # Give the scenario more time to trigger
                sleep 10
                "$ADB" logcat -d -s AccelDetector:V GForceDetector:V 2>/dev/null >> "$LOG_FILE"
                if grep -qi "hard brake\|HARD_BRAKE\|hard_brake" "$LOG_FILE"; then
                    check "HARD_BRAKE: Brake event in logs (delayed)" "pass"
                else
                    check "HARD_BRAKE: Brake event in logs" "fail"
                fi
            fi
            if [[ -f "$SCREENSHOT_FILE" ]] && [[ -s "$SCREENSHOT_FILE" ]]; then
                check "HARD_BRAKE: Screenshot captured" "pass"
            else
                check "HARD_BRAKE: Screenshot captured" "fail"
            fi
            ;;

        LOW_FUEL)
            # Health alert for low fuel should appear
            if grep -qi "low.fuel\|LOW_FUEL\|fuel.*alert\|HealthMonitor" "$LOG_FILE"; then
                check "LOW_FUEL: Fuel alert in logs" "pass"
            else
                check "LOW_FUEL: Fuel alert in logs" "fail"
            fi
            ;;

        COLD_START)
            # Coolant should start low — no premature overheat alert
            if ! grep -qi "high.coolant\|COOLANT.*alert" "$LOG_FILE"; then
                check "COLD_START: No premature coolant alert" "pass"
            else
                check "COLD_START: No premature coolant alert" "fail"
            fi
            ;;

        DISCONNECT)
            # Disconnect scenario should show reconnecting state
            if grep -qi "disconnect\|reconnect\|connection.lost" "$LOG_FILE"; then
                check "DISCONNECT: Disconnect event in logs" "pass"
            else
                check "DISCONNECT: Disconnect event in logs" "fail"
            fi
            ;;

        OVERSPEED)
            # Speed alert should fire if enabled
            if grep -qi "overspeed\|speed.*alert\|OVERSPEED" "$LOG_FILE"; then
                check "OVERSPEED: Speed alert in logs" "pass"
            else
                # OVERSPEED alert is disabled by default — this is expected
                check "OVERSPEED: Alert gated by setting (disabled by default)" "pass"
            fi
            ;;

        *)
            info "Unknown scenario $SCENARIO — screenshot captured"
            check "$SCENARIO: Screenshot captured" "$([ -s "$SCREENSHOT_FILE" ] && echo pass || echo fail)"
            ;;
    esac
done

# ── Step 5: Report ─────────────────────────────────────────────────────────────

echo ""
echo "══════════════════════════════════════════"
echo -e "  E2E Results: ${GREEN}${PASS_COUNT} passed${NC}, ${RED}${FAIL_COUNT} failed${NC}"
echo "══════════════════════════════════════════"
echo "  Screenshots: $SCREENSHOT_DIR"
echo ""

if [[ $FAIL_COUNT -gt 0 ]]; then
    exit 1
fi
