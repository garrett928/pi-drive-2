# Phase 0: Bootstrap

**Goal:** Convert the bare scaffold into a modern Android project with Compose, Hilt, Room, Coroutines, theming, and navigation. After this phase the app launches, shows a dark-themed shell with 3 bottom tabs, and all future phases can focus on features.

**Depends on:** Nothing (first phase).

---

## Step 0.1 -- Build System + Kotlin + Compose + Hilt + Room

**What to build:**

1. **Version catalog** (`gradle/libs.versions.toml`): Add versions and libraries for:
   - Kotlin (2.1.x) + `kotlin-android` plugin + `kotlin-compose` plugin
   - Compose BOM (2025.x), Compose UI, Compose Material3, Compose Foundation, Compose Runtime
   - Hilt (2.56+) + `hilt-android-compiler` (KSP), `hilt-navigation-compose`
   - Room (2.7+) + `room-compiler` (KSP), `room-ktx`
   - KSP plugin
   - Coroutines (`kotlinx-coroutines-core`, `kotlinx-coroutines-android`)
   - Lifecycle (`lifecycle-runtime-ktx`, `lifecycle-viewmodel-compose`)
   - Navigation Compose (`navigation-compose`)
   - Activity Compose (`activity-compose`)
   - OkHttp + kotlinx-serialization-json (for later, add now to avoid version churn)
   - Testing: `kotlinx-coroutines-test`, `turbine` (for Flow testing), `mockk`

2. **Root `build.gradle.kts`**: Add Kotlin, Hilt, KSP plugins (apply false).

3. **`shared/build.gradle.kts`** (convert from `.gradle` to `.kts`):
   - Apply `kotlin-android`, `com.android.library`
   - Add `kotlinOptions { jvmTarget = "11" }`
   - Add dependencies: coroutines, Room (runtime + KSP compiler), Car App Library, kotlinx-serialization

4. **`mobile/build.gradle.kts`**:
   - Apply `kotlin-android`, `kotlin-compose`, `dagger.hilt.android.plugin`, `com.google.devtools.ksp`
   - Enable `buildFeatures { compose = true }`
   - Add dependencies: Compose BOM + UI + Material3, Hilt, Navigation Compose, Lifecycle, Activity Compose
   - Remove: `constraintlayout`, `activity` (replaced by activity-compose)

5. **`mobile/src/main/java/.../MainActivity.kt`**: Replace with:
   ```kotlin
   @AndroidEntryPoint
   class MainActivity : ComponentActivity() {
       override fun onCreate(savedInstanceState: Bundle?) {
           super.onCreate(savedInstanceState)
           enableEdgeToEdge()
           setContent {
               PiDriveTheme {
                   Text("Pi Drive")
               }
           }
       }
   }
   ```

6. **`mobile/src/main/java/.../PiDriveApplication.kt`**: Create `@HiltAndroidApp` Application class. Register in manifest.

7. **`mobile/src/main/java/.../ui/theme/PiDriveTheme.kt`**: Minimal Compose theme (just dark colors, will flesh out in 0.2).

8. **Delete** `activity_main.xml` (no longer needed with Compose).

9. **Update `AndroidManifest.xml`**: Add `android:name=".PiDriveApplication"` to `<application>`.

**Test criteria:**
- `./gradlew :mobile:assembleDebug` succeeds
- `./gradlew :shared:assembleDebug` succeeds
- `/pd-run` -> app launches showing "Pi Drive" text on dark background
- `/pd-screenshot` -> confirm Compose content renders

**Estimated size:** ~2k lines (mostly version catalog and build file changes)

---

## Step 0.2 -- Design System + Theme Tokens

**What to build:**

1. **`shared/src/main/java/.../shared/ui/theme/`**:
   - `PiDriveColors.kt`: All oklch color tokens from `ui-handoff/pi-drive/project/pd-tokens.jsx`, converted to Compose `Color` values. Include:
     - Dark palette: background, card surface, elevated surface, input/hover, borders, text (primary/muted/dim)
     - Semantic colors: danger, success, warn
     - 4 accent options: warm orange (default), red, yellow, blue-teal, each with base/soft/strong
   - `PiDriveTypography.kt`: Typography scale using system-ui (sans-serif) as primary, monospace for metrics. Define text styles for: large metric value (76px mono), section header, body, caption, label.
   - `PiDriveTheme.kt`: `CompositionLocal` for colors + typography. `PiDriveTheme` composable wrapping `MaterialTheme` with custom color mapping. Accent color selection via `CompositionLocal`.

2. **`shared/src/main/java/.../shared/ui/components/`**: Basic primitives:
   - `PDCard.kt`: Rounded card with surface color, border
   - `PDPill.kt`: Status pill badge (LIVE, RECORDING, QUEUED, etc.)
   - `PDRow.kt`: Settings row (icon, title, subtitle, trailing content)
   - `PDToggle.kt`: Styled toggle switch
   - `PDButton.kt`: Primary/secondary/danger button styles

3. **Move theme from mobile to shared** so both modules can use it.

**Reference:** Read `ui-handoff/pi-drive/project/pd-tokens.jsx` for exact color values and `pd-primitives.jsx` for component specs.

**Note on oklch:** Android Compose uses sRGB. Convert oklch values to sRGB hex/RGB at build time (or hardcode the converted values). The oklch values in REQUIREMENTS.md are the source of truth for designers; the Compose code stores the converted sRGB equivalents. Include the oklch value as a comment next to each color definition for traceability.

**Test criteria:**
- `./gradlew :shared:test :mobile:test` passes
- `/pd-run` -> app shows themed "Pi Drive" text in warm-orange accent on near-black background
- `/pd-screenshot` -> verify dark theme colors match spec
- Unit tests: verify accent color variants produce distinct Color instances

**Estimated size:** ~1.5k lines

---

## Step 0.3 -- Navigation + App Shell

**What to build:**

1. **`mobile/src/main/java/.../ui/navigation/`**:
   - `PiDriveNavigation.kt`: Bottom tab navigation with 3 tabs:
     - Live (icon: speedometer or activity)
     - Trips (icon: route/map)
     - Settings (icon: gear)
   - `NavRoutes.kt`: Sealed class/object defining all routes:
     ```
     home, connect/scan, connect/pair, connect/done,
     trips,
     settings, settings/server, settings/home-layout, settings/aa-layout, settings/thresholds
     ```
   - `PiDriveNavHost.kt`: NavHost composable with placeholder screens for each route

2. **`mobile/src/main/java/.../ui/screens/`**: Create placeholder composables:
   - `LiveDashboardScreen.kt` -- "Live Dashboard" centered text
   - `TripHistoryScreen.kt` -- "Trip History" centered text
   - `SettingsScreen.kt` -- "Settings" centered text
   - (Other screens will be created in later phases)

3. **`mobile/src/main/java/.../ui/components/PiDriveTopBar.kt`**:
   - Title text (screen name)
   - Back button on sub-screens
   - Right-side context action slot (LIVE pill on dashboard, filter on trips)

4. **`mobile/src/main/java/.../ui/components/PiDriveScaffold.kt`**:
   - Scaffold composable wrapping top bar + bottom nav + content
   - Hide bottom nav on connect/* screens

5. **Update `MainActivity.kt`**: Wire PiDriveScaffold as root.

**Test criteria:**
- `/pd-run` -> app launches with 3 bottom tabs
- Tapping each tab navigates to the correct placeholder screen
- `/pd-screenshot` -> all 3 tabs visible, correct tab highlighted
- Navigate to settings sub-route programmatically, verify back button appears

**Estimated size:** ~1.5k lines
