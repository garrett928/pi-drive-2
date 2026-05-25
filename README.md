# Pi Drive

Car metric collecting, logging, and display application for Android and Android Auto.

Pi Drive is a lightweight, modular Android application that connects to an **OBDLink LX** Bluetooth OBD-II adapter, reads live vehicle data (speed, GPS location, engine/coolant temperature, oil pressure, fuel economy), and displays it on both a phone and an Android Auto head unit. The Android Auto interface supports **half-screen (split-screen) mode** so Google Maps can run alongside the app. Pi Drive can also POST collected metrics to a configurable remote server for centralized data collection.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Module Structure](#module-structure)
- [OBDLink LX Adapter](#obdlink-lx-adapter)
- [Bluetooth Connection](#bluetooth-connection)
- [OBD-II Protocol Primer](#obd-ii-protocol-primer)
- [ELM327 / STN Command Set](#elm327--stn-command-set)
- [Target PIDs](#target-pids)
- [Android Auto Integration](#android-auto-integration)
- [Server Telemetry Upload](#server-telemetry-upload)
- [Project Setup](#project-setup)
- [Development Without a Car](#development-without-a-car)
- [Reference Documentation](#reference-documentation)

---

## Architecture Overview

```
+-------------------+        Bluetooth SPP        +----------------+       OBD Bus       +-------+
|   Android Phone   | <-------------------------> |  OBDLink LX    | <------------------> | Car   |
|   (Pi Drive App)  |      RFCOMM serial stream   |  (STN1155)     |    CAN / ISO / J1850 | ECU   |
+-------------------+                             +----------------+                      +-------+
        |
        |  Android for Cars App Library
        v
+-------------------+
| Android Auto HU   |   (half-screen alongside Google Maps)
| (GridTemplate /   |
|  MapWithContent)  |
+-------------------+
        |
        |  HTTP POST (JSON)
        v
+-------------------+
|  Remote Server    |   (configurable URL, metrics ingestion)
+-------------------+
```

**Data flow:**
1. The phone pairs with the OBDLink LX over Bluetooth Classic (SPP).
2. The app opens an RFCOMM socket and sends AT/ST initialization commands followed by OBD Mode 01 PID requests.
3. Raw hex responses are parsed into human-readable values (RPM, speed, temps, etc.).
4. Values are displayed on-phone and projected to Android Auto via the Car App Library.
5. Optionally, a background service batches readings and POSTs them as JSON to a server URL stored in app preferences.

---

## Module Structure

The project is a multi-module Gradle build:

| Module | Purpose |
|---|---|
| **`:mobile`** | Phone app: main activity, Bluetooth connection management, settings UI, background data service |
| **`:automotive`** | Android Automotive OS target (embedded head units running AAOS natively) |
| **`:shared`** | Car App Library service, session, and screen classes shared between mobile and automotive. Also contains OBD communication logic and data models. |

- **Package namespace:** `ghart.space.pi_drive`
- **Min SDK:** 34 (Android 14)
- **Target/Compile SDK:** 36
- **Language:** Kotlin

---

## OBDLink LX Adapter

The **OBDLink LX** (device ID: **STN1155**) is a Bluetooth Classic OBD-II adapter manufactured by OBD Solutions.

| Spec | Value |
|---|---|
| Bluetooth | 3.0 Classic (SPP profile) |
| Encryption | 128-bit |
| Chip | STN1155 (OBD Solutions proprietary, ELM327-compatible) |
| Operating voltage | 8 -- 18V DC |
| Dimensions | 51 x 45 x 23 mm |
| Weight | 33 g |
| Sleep | BatterySaver automatic sleep/wake on ignition off/on |
| Pairing | Physical button required; secure pairing only |

### Supported OBD-II protocols

The LX supports all five legislated OBD-II signaling protocols:

| Protocol | Standard | Typical vehicles |
|---|---|---|
| CAN (500/250 kbps) | ISO 15765-4 | 2008+ all US vehicles (mandatory) |
| KWP2000 | ISO 14230-4 | European, Asian |
| ISO 9141-2 | ISO 9141-2 | European, Asian, Chrysler |
| VPW | SAE J1850 VPW | GM |
| PWM | SAE J1850 PWM | Ford |

The adapter auto-detects the correct protocol via `ATSP 0` (automatic search).

### Key difference from generic ELM327 clones

The OBDLink LX uses OBD Solutions' STN1155 chip, not a genuine or cloned ELM327. It is **fully backwards-compatible** with the ELM327 AT command set but adds a parallel **ST command set** with superior features: faster protocol detection, batched commands, configurable Bluetooth settings, and better power management. Always prefer ST commands where available; fall back to AT for third-party library compatibility.

**Product page:** https://www.obdlink.com/products/obdlink-lx/

---

## Bluetooth Connection

The OBDLink LX uses **Bluetooth Classic** with the **Serial Port Profile (SPP)**. On Android, this means connecting via an `android.bluetooth.BluetoothSocket` over RFCOMM.

### Connection steps

1. **Pair the adapter.** The user presses the physical Pair button on the LX, then pairs via Android Bluetooth settings. The adapter name will appear as `OBDLink LX XXXXX`.
2. **Discover the device.** Use `BluetoothAdapter.getBondedDevices()` and filter by name prefix `"OBDLink"`.
3. **Open an RFCOMM socket** using the standard SPP UUID:
   ```
   00001101-0000-1000-8000-00805F9B34FB
   ```
   ```kotlin
   val device: BluetoothDevice = // from bonded devices
   val socket = device.createRfcommSocketToServiceRecord(
       UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
   )
   socket.connect()
   val input  = socket.inputStream
   val output = socket.outputStream
   ```
4. **Send initialization commands** (see [ELM327 / STN Command Set](#elm327--stn-command-set) below).
5. **Read responses.** The adapter terminates every response with `>` (the prompt character). Read bytes until you see `>`, then parse the response. Responses are ASCII hex with optional spaces and `\r\n` line endings.

### Required Android permissions

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

On API 31+ (Android 12+), `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` are runtime permissions that must be requested at runtime.

---

## OBD-II Protocol Primer

OBD-II communication uses a request/response model. The phone (tester) sends a **service (mode) + PID** request, and the vehicle ECU responds with the requested data.

### Key services (modes)

| Service | Purpose |
|---|---|
| `01` | Show current (live) data |
| `02` | Freeze frame data |
| `03` | Read confirmed DTCs |
| `04` | Clear DTCs and MIL |
| `09` | Vehicle information (VIN, calibration ID) |

### How a request works

To read **engine RPM** (PID `0C`):

```
Request:  01 0C          (Service 01, PID 0C)
Response: 41 0C 1A F8    (41 = response to service 01; 1A F8 = raw value)
RPM = ((0x1A * 256) + 0xF8) / 4 = 1726 RPM
```

The `41` prefix means "response to service `01`" (`40 + service`). Each PID has its own formula defined in SAE J1979 / ISO 15031-5.

### Checking supported PIDs

Before requesting a PID, query what the vehicle supports:

```
01 00  -> returns a 4-byte bitmask of PIDs 01-20
01 20  -> returns a 4-byte bitmask of PIDs 21-40
01 40  -> returns a 4-byte bitmask of PIDs 41-60
```

Always check support before polling a PID, especially for optional parameters like oil pressure.

---

## ELM327 / STN Command Set

All commands are sent as ASCII text terminated with `\r` (carriage return `0x0D`). Wait for the `>` prompt before sending the next command.

### Initialization sequence

Send these commands after connecting to prepare the adapter:

```
ATZ          Reset the adapter (returns "ELM327 v1.4b" -- this is the compatibility string)
ATE0         Turn off command echo (less data to parse)
ATL0         Turn off line feeds (responses use \r only)
ATS0         Turn off spaces in responses (e.g., "410C1AF8" instead of "41 0C 1A F8")
ATH0         Turn off headers (return data bytes only)
ATSP 0       Set protocol to automatic (adapter tries all protocols)
```

After initialization, verify the connection by requesting supported PIDs:

```
01 00        Request supported PIDs (should return 41 00 ...)
```

### Useful AT commands

| Command | Description |
|---|---|
| `ATZ` | Reset (cold start) |
| `ATWS` | Warm start (reset without full reboot) |
| `ATI` | Print version string |
| `ATE 0/1` | Echo off/on |
| `ATL 0/1` | Line feeds off/on |
| `ATS 0/1` | Spaces off/on |
| `ATH 0/1` | Headers off/on |
| `ATSP n` | Set protocol (`0` = auto, `6` = CAN 11-bit 500k, etc.) |
| `ATDPN` | Describe current protocol number |
| `ATRV` | Read battery voltage |
| `ATST hh` | Set response timeout (hh x 4 ms) |
| `ATAT 1/2` | Adaptive timing (auto-adjusts timeout between frames) |

### Useful ST commands (OBDLink-specific)

| Command | Description |
|---|---|
| `STI` | Print full device/firmware ID |
| `STDI` | Print device hardware ID |
| `STIX` | Print extended device info |
| `STSBR baud` | Set UART baud rate |
| `STPTO ms` | Set protocol timeout in milliseconds |
| `STSLEEP` | Enter low-power sleep |
| `STSLLT` | Read battery voltage (more precise than ATRV) |
| `STBTIX` | Print extended Bluetooth info (address, name, CoD) |
| `STBTDN name` | Set Bluetooth broadcast name |

### Batched commands (performance optimization)

The OBDLink supports **batched commands** that let you queue multiple PID requests into a single round-trip, reducing latency. See Section 16 of the FRPM for details. This can significantly improve polling rate for multi-PID dashboards.

### Full reference

The complete AT and ST command set is documented in the **OBDLink Family Reference and Programming Manual (FRPM)**:
- Included in this repo: `OBDLink® Family Reference and Programming Manual (FRPM) - obdlink_frpm_f.pdf`
- Latest version online: http://www.obdsol.com

---

## Target PIDs

These are the OBD-II Mode 01 PIDs the app should poll. All formulas follow SAE J1979.

| PID (hex) | Name | Bytes | Formula | Unit | Notes |
|---|---|---|---|---|---|
| `05` | Engine coolant temp | 1 | `A - 40` | C | |
| `0C` | Engine RPM | 2 | `((A*256)+B) / 4` | RPM | |
| `0D` | Vehicle speed | 1 | `A` | km/h | |
| `0F` | Intake air temp | 1 | `A - 40` | C | |
| `10` | MAF air flow rate | 2 | `((A*256)+B) / 100` | g/s | Used for fuel economy calc |
| `11` | Throttle position | 1 | `(A * 100) / 255` | % | |
| `2F` | Fuel tank level | 1 | `(A * 100) / 255` | % | |
| `5C` | Engine oil temp | 1 | `A - 40` | C | Not all vehicles |
| `5E` | Fuel consumption rate | 2 | `((A*256)+B) / 20` | L/h | Not all vehicles |

### Oil pressure

Oil pressure is **not a standard OBD-II PID**. It is manufacturer-specific and usually available only through enhanced/proprietary diagnostics (Mode 22). Availability depends on the vehicle make, model, and year. The app should:
- Check for PID support via `01 00` / `01 20` / `01 40` bitmasks
- Gracefully hide the oil pressure gauge if unsupported
- Consider Mode 22 (enhanced diagnostics) for specific vehicle makes if oil pressure is a priority

### Fuel economy calculation

If PID `5E` (fuel consumption rate) is not supported, fuel economy can be estimated from the MAF sensor (PID `10`) and vehicle speed (PID `0D`):

```
Fuel rate (L/h) = MAF (g/s) / (air-fuel ratio * fuel density)
                = MAF / (14.7 * 820)    [for gasoline]
                = MAF / 12054

Fuel economy (km/L) = speed (km/h) / fuel rate (L/h)
MPG = km/L * 2.352
```

### GPS location

GPS is read from the Android phone's location services, **not** from OBD-II. Use `FusedLocationProviderClient` from Google Play Services for battery-efficient location polling.

---

## Android Auto Integration

Pi Drive uses the **Android for Cars App Library** (`androidx.car.app`) to project a dashboard UI onto the car's head unit.

### App category

The app registers as an **IoT** category app. This is the correct category for vehicle data display apps that don't provide navigation or media. IoT apps use the `GridTemplate` to present data as a grid of items, which maps well to a gauge dashboard.

```xml
<!-- AndroidManifest.xml -->
<service android:name=".shared.MyCarAppService" android:exported="true">
    <intent-filter>
        <action android:name="androidx.car.app.CarAppService" />
        <category android:name="androidx.car.app.category.IOT" />
    </intent-filter>
</service>
```

### Half-screen / split-screen mode

Android Auto natively supports split-screen on displays 6 inches and larger. When the user has Google Maps (or another navigation app) active, your IoT app can appear in the remaining screen portion. This is handled automatically by the Android Auto host -- **no special code is required.** The Car App Library templates are responsive and adapt to the available screen space.

To ensure your app coexists well with navigation:
- Use `GridTemplate` with concise labels and clear icons
- Keep grid items to 6 or fewer for readability at small sizes
- Update grid items by calling `invalidate()` on the Screen when new OBD data arrives

### Key classes (existing scaffold)

| File | Class | Purpose |
|---|---|---|
| `shared/.../MyCarAppService.kt` | `MyCarAppService` | Entry point; creates sessions |
| `shared/.../MyCarAppSession.kt` | `MyCarAppSession` | Manages session lifecycle |
| `shared/.../MyCarAppScreen.kt` | `MyCarAppScreen` | Renders the template UI (currently a placeholder `MessageTemplate`) |

### GridTemplate example for gauges

```kotlin
override fun onGetTemplate(): Template {
    val items = ItemList.Builder()

    items.addItem(
        GridItem.Builder()
            .setTitle("$currentSpeed km/h")
            .setText("Speed")
            .setImage(CarIcon.Builder(speedIcon).build(), GridItem.IMAGE_TYPE_ICON)
            .build()
    )

    items.addItem(
        GridItem.Builder()
            .setTitle("${currentRpm} RPM")
            .setText("Engine")
            .setImage(CarIcon.Builder(rpmIcon).build(), GridItem.IMAGE_TYPE_ICON)
            .build()
    )

    // ... add coolant temp, fuel level, etc.

    return GridTemplate.Builder()
        .setHeader(Header.Builder().setTitle("Pi Drive").setStartHeaderAction(Action.APP_ICON).build())
        .setSingleList(items.build())
        .build()
}
```

### Testing Android Auto

Use the **Desktop Head Unit (DHU)** for development without a car:

```bash
# Install DHU via Android Studio SDK Manager -> SDK Tools -> "Android Auto Desktop Head Unit Emulator"
# Start DHU
$ANDROID_HOME/extras/google/auto/desktop-head-unit
```

Official testing guide: https://developer.android.com/training/cars/testing/dhu

### Key dependencies

```kotlin
// shared/build.gradle
implementation "androidx.car.app:app:1.7.0"        // Car App Library

// mobile/build.gradle
implementation "androidx.car.app:app-projected:1.7.0"  // Android Auto projection
```

### References

- [Android for Cars App Library overview](https://developer.android.com/training/cars/apps)
- [Build an IoT app for cars](https://developer.android.com/training/cars/apps/iot)
- [Templates overview](https://developer.android.com/design/ui/cars/guides/templates/overview)
- [Car App Library samples](https://github.com/android/car-samples/tree/main/car_app_library)
- [Car App Library fundamentals codelab](https://developer.android.com/codelabs/car-app-library-fundamentals)

---

## Server Telemetry Upload

The app supports posting collected vehicle data to a configurable server URL.

### Configuration

The server URL is stored in `SharedPreferences` and configurable from the mobile app's settings screen. The app should also support an optional API key header for authenticated endpoints.

### Payload format

```json
{
  "timestamp": "2026-05-24T22:15:30.123Z",
  "vin": "1G1JC524417100001",
  "location": {
    "lat": 37.7749,
    "lng": -122.4194,
    "speed_gps": 65.2
  },
  "obd": {
    "speed_kmh": 105,
    "rpm": 2400,
    "coolant_temp_c": 92,
    "intake_air_temp_c": 35,
    "throttle_pct": 22.5,
    "fuel_level_pct": 68.0,
    "oil_temp_c": 95,
    "maf_gps": 12.5,
    "fuel_rate_lph": null,
    "battery_voltage": 14.2
  },
  "calculated": {
    "fuel_economy_mpg": 28.5,
    "fuel_economy_kml": 12.1
  }
}
```

### Upload strategy

- Buffer readings locally (Room database or in-memory queue)
- Batch POST every N seconds (configurable, default 10s)
- Retry with exponential backoff on failure
- Use `WorkManager` for reliable delivery when the app goes to the background
- Use OkHttp or Ktor for the HTTP client

---

## Project Setup

### Prerequisites

- Android Studio Ladybug (2024.2+) or newer
- JDK 11+
- Android SDK 36
- An OBDLink LX adapter (or the ELM327 emulator for testing)
- A vehicle with an OBD-II port (1996+ US, 2001+ EU petrol, 2004+ EU diesel)

### Build & run

```bash
cd pi-drive-android
./gradlew :mobile:installDebug
```

### Android Auto testing

```bash
# Install DHU
$ANDROID_HOME/extras/google/auto/desktop-head-unit

# Run the mobile app on a connected device, then start DHU
```

---

## Development Without a Car

### ELM327 Emulator

For development and testing without a physical vehicle, use the **ELM327 Emulator** project:

- **Repository:** https://github.com/Ircama/ELM327-emulator
- Simulates an ELM327-compatible adapter over TCP or serial
- Supports multi-ECU simulation
- Responds to standard OBD-II Mode 01 PID requests with configurable values
- Can be used over a local TCP socket or via a Bluetooth serial bridge

This allows full end-to-end testing of the OBD communication layer without needing a car or adapter.

### Reference OBD projects

These open-source Android OBD projects are useful as reference implementations:

| Project | Language | Notes |
|---|---|---|
| [AndroidOBD](https://github.com/barnhill/AndroidOBD) | Kotlin | Library with PID parsing and formulas. Maven: `com.pnuema.android:obd:1.9.0`. Min API 24. |
| [AndrOBD](https://github.com/fr3ts0n/AndrOBD) | Java | Full-featured app with dashboard, charts, HUD, plugin system. Active (v2.7.9). |
| [obd (Tomiwa-Ot)](https://github.com/Tomiwa-Ot/obd) | Java | Lightweight library for ELM327 Bluetooth/USB diagnostics. |
| [Elm327 (takyonxxx)](https://github.com/takyonxxx/Elm327) | Java | Complete diagnostic tool with Bluetooth connection example. |

### Garage Pi Bridge (reference architecture)

The [Garage Pi Bridge](https://github.com/devsinsera/garage-pi-bridge) project demonstrates a Raspberry Pi 5 + OBDLink MX+ + Supabase architecture for live OBD-II data streaming. While it targets a different platform (Python on Pi), its data pipeline and Supabase schema design are useful references for the server telemetry feature.

---

## Reference Documentation

### Included in this repo

| File | Description |
|---|---|
| `OBDLink® Family Reference and Programming Manual (FRPM) - obdlink_frpm_f.pdf` | Complete AT and ST command reference for all OBDLink devices (78 pages). **Start here for command details.** |
| `OBDLink® CX Adapter Notes _ OBD Solutions.pdf` | Adapter-specific notes (CX is BLE; LX is Classic, but shared firmware architecture) |
| `Intro to OBDlink development.pdf` | Getting started guide for OBDLink app development |
| `ELM327 - Wikipedia.pdf` | Background on the ELM327 chip and its protocol |
| `OBD-II PIDs - Wikipedia.pdf` | Comprehensive PID list with formulas |
| `On-board diagnostics - Wikipedia.pdf` | OBD-II history, connector pinout, signal protocols |

### Online resources

**OBD & adapter:**
- [OBDLink LX product page](https://www.obdlink.com/products/obdlink-lx/)
- [OBDLink support / troubleshooting](https://support.obdlink.com/support/solutions/articles/43000715570-troubleshoot-connection-issues)
- [OBDLink FRPM (latest)](http://www.obdsol.com)
- [SAE J1979 (OBD test modes) -- SAE International](https://www.sae.org/standards/content/j1979_201702/)

**Android Auto / Car App Library:**
- [Android for Cars App Library](https://developer.android.com/training/cars/apps)
- [Build an IoT app](https://developer.android.com/training/cars/apps/iot)
- [Templates overview](https://developer.android.com/design/ui/cars/guides/templates/overview)
- [GridTemplate API reference](https://developer.android.com/reference/androidx/car/app/model/GridTemplate)
- [Desktop Head Unit (DHU) testing](https://developer.android.com/training/cars/testing/dhu)
- [Car App Library samples on GitHub](https://github.com/android/car-samples/tree/main/car_app_library)
- [Car App Library fundamentals codelab](https://developer.android.com/codelabs/car-app-library-fundamentals)

**Android Bluetooth:**
- [Bluetooth overview (Android)](https://developer.android.com/develop/connectivity/bluetooth)
- [BluetoothDevice.createRfcommSocketToServiceRecord](https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createRfcommSocketToServiceRecord(java.util.UUID))

---

## License

TBD
