# Phase 11: Release Readiness

**Goal:** Prepare the app for production distribution. Covers the app icon + splash screen, runtime permissions onboarding flow, and release build configuration. After this phase the app is shippable.

**Depends on:** Phase 10 (all features + CI must be passing).

---

## Step 11.1 -- App Icon + Splash Screen

**What to build:**

1. **Adaptive app icon** (`mobile/src/main/res/`):
   - `mipmap-anydpi-v26/ic_launcher.xml` — adaptive icon pointing at foreground/background drawables
   - `drawable/ic_launcher_foreground.xml` — π glyph SVG (vector drawable, accent orange `#E8762A`, bold, centered on 108dp canvas with safe zone)
   - `drawable/ic_launcher_background.xml` — solid near-black background (`#282018`, matches app dark bg token `oklch(0.195 0.005 60)` ≈ `#272217`)
   - Legacy bitmap icons (mipmap-mdpi through mipmap-xxxhdpi) — generate via Android Studio or use `ic_launcher_foreground` + background via `mipmap-anydpi-v26`
   - Round icon: same foreground + circular background via `ic_launcher_round.xml`
   - `AndroidManifest.xml` updated: `android:icon="@mipmap/ic_launcher"`, `android:roundIcon="@mipmap/ic_launcher_round"`

2. **Splash screen** (`mobile/src/main/res/values/themes.xml`):
   - Use `androidx.core:core-splashscreen` library (already part of `core-ktx` or add explicitly)
   - `postSplashScreenTheme` = `Theme.PiDrive` (app theme)
   - Splash screen icon: `@drawable/ic_launcher_foreground` (the π glyph)
   - Splash screen icon background: match dark bg (`#282018`)
   - Set `windowSplashScreenBackground` to dark bg color
   - In `MainActivity.onCreate()`: call `installSplashScreen()` before `super.onCreate()`
   - Optionally add a 400ms keep-visible condition while Hilt finishes injecting

3. **Splash screen theme wiring**:
   - `mobile/src/main/res/values/themes.xml`: define `Theme.PiDrive.Splash` that extends `Theme.SplashScreen`
   - `AndroidManifest.xml`: set `android:theme="@style/Theme.PiDrive.Splash"` on `<activity>`

**Unit tests:** None required (icon/splash is visual — verified by screenshot).

**Verify:**
- `/pd-run` → cold launch shows splash screen with π icon on dark background, fades into the app
- Screenshot: `screenshots/pidrive-splash.png` → read image: π glyph visible, no ugly white flash
- Launcher icon visible on home screen (if possible via ADB shell)

**Estimated size:** ~200 lines (XML + 1 Kotlin change)

---

## Step 11.2 -- Runtime Permissions Onboarding

**What to build:**

1. **`PermissionState.kt`** in `mobile/src/main/java/.../ui/permissions/`:
   ```kotlin
   sealed class PermissionState {
       object Granted : PermissionState()
       object ShowRationale : PermissionState()   // user denied once
       object PermanentlyDenied : PermissionState() // user denied + "don't ask again"
       object NotRequested : PermissionState()
   }
   ```

2. **`BluetoothPermissionManager.kt`** in same package:
   - Checks `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` (API 31+)
   - Returns combined `PermissionState` (all-granted vs. any-denied)
   - Provides `requestPermissions()` using `ActivityResultContracts.RequestMultiplePermissions`

3. **`PermissionGate.kt`** — Composable that conditionally shows:
   - `content` — when permissions are granted
   - An `ExplainPermissionsSheet` — when rationale is needed
   - A `GoToSettingsCard` — when permanently denied
   
4. **`ExplainPermissionsSheet.kt`** — Bottom sheet explaining why BT permissions are needed:
   - Bluetooth icon + "Pi Drive needs Bluetooth to connect to your OBD adapter"
   - "Allow" primary button, "Not now" text button
   
5. **Wire into `ConnectScanScreen.kt`**:
   - Wrap the scan content in `PermissionGate`
   - If permissions not granted, show the explanation instead of the device list

**Unit tests:**
- `PermissionStateTest.kt`: verify `allGranted`, `anyDenied`, `isPermanentlyDenied` logic

**Verify:**
- `/pd-run` → navigate to Connect → first time: Bluetooth permission sheet appears
- Allow → scan proceeds normally
- `/pd-screenshot` → `screenshots/pidrive-permission-sheet.png`

**Estimated size:** ~400 lines

---

## Step 11.3 -- Release Build Configuration

**What to build:**

1. **`mobile/proguard-rules.pro`** — R8 keep rules:
   ```
   # OkHttp + Serialization
   -dontwarn okhttp3.**
   -keep class kotlinx.serialization.** { *; }
   -keepattributes *Annotation*, Signature, Exceptions
   # Room entities and DAOs
   -keep class ghart.space.pi_drive.shared.data.db.** { *; }
   # Hilt-generated classes
   -keep class **_HiltModules { *; }
   -keep class **_MembersInjector { *; }
   ```

2. **`mobile/build.gradle.kts`** — update release build type:
   ```kotlin
   release {
       isMinifyEnabled = true
       isShrinkResources = true
       proguardFiles(
           getDefaultProguardFile("proguard-android-optimize.txt"),
           "proguard-rules.pro"
       )
       // Signing placeholder — developers fill in keystore before publishing
       // signingConfig = signingConfigs.getByName("release")
   }
   ```

3. **`mobile/build.gradle.kts`** — add a `releaseSmoke` test task:
   ```kotlin
   // Ensures the release APK assembles cleanly without actually signing
   tasks.register("releaseSmoke") {
       dependsOn("assembleRelease")
       doLast { println("Release APK assembled at: mobile/build/outputs/apk/release/") }
   }
   ```

4. **Update CI** (`.github/workflows/test.yml`):
   - Add a `release-build` job that runs `:mobile:assembleRelease`
   - Upload release APK as an artifact (non-signed debug)

5. **`SIGNING.md`** (new file in repo root):
   - Documents how to create a keystore and configure signing for release builds
   - References the `release` signingConfig placeholder in build.gradle.kts

**Unit tests:** None (build configuration — verified by `assembleRelease` succeeding).

**Verify:**
- `./gradlew :mobile:assembleRelease` succeeds in the terminal
- Release APK size is reasonable (< 30 MB)
- Run `aapt2 dump badging mobile/build/outputs/apk/release/*.apk` to confirm package name and version

**Estimated size:** ~300 lines (ProGuard rules + YAML + docs)
