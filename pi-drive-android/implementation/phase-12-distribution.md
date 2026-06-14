# Phase 12: Distribution & Monitoring

**Goal:** Prepare for public release on the Play Store. Covers store listing assets, app distribution channels, error monitoring, and beta testing infrastructure. After this phase the app is ready for users.

**Depends on:** Phase 11 (release build must be configured and signing set up).

---

## Step 12.1 -- Play Store Listing Preparation

**What to build:**

1. **`store/`** directory at repo root:
   - `store/listing-en.md` — short description (80 chars max) and full description (4000 chars)
   - `store/whats-new-en.md` — release notes template for changelogs
   - `store/screenshots/` — placeholder directory for 6 phone screenshots + 3 tablet screenshots

2. **`store/graphics/`**:
   - `feature-graphic.xml` or `feature-graphic.svg` — 1024×500 feature banner (π icon + "Pi Drive" text on dark bg)
   - `icon-512.xml` — 512×512 hi-res app icon for Play Store listing

3. **`fastlane/`** (optional) — Fastlane Deliver config for automated store listing updates:
   - `fastlane/Fastfile` with `desc "Upload metadata"` lane
   - `fastlane/metadata/android/en-US/` with `title.txt`, `short_description.txt`, `full_description.txt`

4. **`store/LAUNCH_CHECKLIST.md`**:
   - Content rating questionnaire complete
   - Privacy policy URL configured
   - Target audience set (17+, vehicle tracking)
   - Data safety form filled (Bluetooth, location, telemetry upload)
   - Review all required store listing fields

**Unit tests:** None (content/assets).

**Verify:**
- `store/listing-en.md` exists and has short + full description
- Feature graphic dimensions correct: `file store/graphics/feature-graphic.*`

**Estimated size:** ~500 lines (copy + YAML + docs)

---

## Step 12.2 -- Firebase Crashlytics Integration

**What to build:**

1. **Add Firebase to the project** (`mobile/build.gradle.kts`):
   - Add `google-services` plugin
   - Add `firebase-bom` + `firebase-crashlytics` + `firebase-analytics` dependencies

2. **`CrashReporter.kt`** in `mobile/src/main/java/.../di/`:
   - Thin wrapper around `FirebaseCrashlytics.getInstance()`
   - `logException(Throwable)`, `setUserId(String)`, `log(String)`
   - Returns a no-op implementation in debug builds

3. **Wire OBD error reporting**:
   - In `OBDVehicleDataSource` catch blocks: log non-timeout exceptions via `CrashReporter`
   - In `ConnectionManager` error handler: log connection failures

4. **User opt-out**:
   - Add "Share crash reports" toggle in Settings > General
   - Persisted via `GeneralSettingsManager`
   - Wire toggle to `FirebaseCrashlytics.setCrashlyticsCollectionEnabled()`

**Unit tests:**
- `CrashReporterTest.kt` — verify no-op in debug, real calls forwarded in release

**Estimated size:** ~400 lines

---

## Step 12.3 -- Beta Testing Setup

**What to build:**

1. **`mobile/build.gradle.kts`** — add `beta` build variant:
   ```kotlin
   buildTypes {
       create("beta") {
           initWith(getByName("release"))
           applicationIdSuffix = ".beta"
           versionNameSuffix = "-beta"
           isDebuggable = false
       }
   }
   ```

2. **Firebase App Distribution** — distribute beta builds:
   - Add `firebase-appdistribution` Gradle plugin
   - CI job: on tag push to `beta/*`, build + upload to Firebase App Distribution tester group

3. **In-app feedback** (optional):
   - `FeedbackButton` composable in Settings > About
   - Opens email intent to `feedback@example.com` with device info pre-filled

**Unit tests:** None (distribution config).

**Estimated size:** ~200 lines
