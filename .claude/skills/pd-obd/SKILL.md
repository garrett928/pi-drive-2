---
description: Start the ELM327 OBD-II emulator over TCP (Phase 2 testing). Use when implementing or testing OBD parsing, AT command sequences, PID formulas, protocol handling, or anything that touches the OBD layer. Use instead of demo mode when the feature being tested requires real OBD protocol behavior. After launch, capture a screenshot as proof of connection.
---

Start the ELM327 OBD-II emulator and connect Pi Drive to it over TCP. This is Phase 2 testing — it exercises real AT command parsing and OBD protocol handling without a physical dongle.

Arguments (optional): a PID override in the format `PID=VALUE`, e.g. `0105=96` to set coolant temp to 110°C. Multiple overrides separated by spaces.

Overrides: $ARGUMENTS

Do the following steps in order.

## Step 1 — Check Python and the emulator package

```bash
python3 -m elm --version 2>/dev/null || python3 -c "import elm" 2>/dev/null
```

If the package is not installed:
```bash
pip3 install ELM327-emulator
```

## Step 2 — Kill any existing emulator instance

```bash
pkill -f "python3 -m elm" 2>/dev/null || true
sleep 1
```

## Step 3 — Start the ELM327 emulator on TCP port 35000

```bash
python3 -m elm -n 35000 -s car > /tmp/elm327.log 2>&1 &
echo $! > /tmp/elm327.pid
sleep 2
```

Verify it started by checking the log:
```bash
cat /tmp/elm327.log
```

If the port is already in use or the emulator failed to start, report the error and stop.

## Step 4 — Apply PID overrides (if any were provided in $ARGUMENTS)

For each override in the format `PID=VALUE` from $ARGUMENTS, send it to the emulator via a Python one-liner:

```bash
python3 -c "
import socket, time
s = socket.socket()
s.connect(('localhost', 35000))
time.sleep(0.5)
s.send(b'edit 01PID=41 PID VALUE\r')
s.close()
"
```

Replace `PID` and `VALUE` with the actual values from each override. If no overrides were given, skip this step.

Example for coolant temp 110°C (0105, value 0x96 = 150):
```
edit 0105=41 05 96
```

## Step 5 — Set up ADB port forwarding

Check if a device is available:
```bash
~/Library/Android/sdk/platform-tools/adb devices
```

If no device: suggest running `/pd-run` first, then re-run `/pd-obd`. Stop here.

Forward the port so the phone/emulator can reach localhost:35000 on the Mac:
```bash
~/Library/Android/sdk/platform-tools/adb reverse tcp:35000 tcp:35000
```

Note: if using the Android emulator (not a physical phone), the app should use `10.0.2.2` as host instead of `localhost`.

## Step 6 — Build and install (if not already installed)

```bash
cd /Users/ghart/Documents/garrett-files/projects/pi-drive-2/pi-drive-android
./gradlew :mobile:installDebug
```

## Step 7 — Launch in TCP mode

```bash
~/Library/Android/sdk/platform-tools/adb logcat -c
~/Library/Android/sdk/platform-tools/adb shell am start \
  -n ghart.space.pi_drive/.MainActivity \
  --ez tcp_mode true \
  --es tcp_host "localhost" \
  --ei tcp_port 35000
```

## Step 8 — Confirm connection

Wait 5 seconds, then check logs:
```bash
~/Library/Android/sdk/platform-tools/adb logcat -d -s OBDTransport:V PiDrive:V AndroidRuntime:E | tail -30
```

Look for:
- `TCP transport connected to localhost:35000` — success
- `ATZ → ELM327` — initialization working
- `010D → 410D` — PID polling active
- `FATAL EXCEPTION` — crash

Report the connection status, what PIDs are being polled, and any applied overrides.

## Stopping the emulator

When done testing, run:
```bash
pkill -f "python3 -m elm" 2>/dev/null || true
~/Library/Android/sdk/platform-tools/adb reverse --remove tcp:35000
```
