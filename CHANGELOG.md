# ShimmerENACT Changelog

RFSAT Limited — ENACT Project (Horizon Europe Grant 101157151)

## v3.3.0

### Added
- **Exit button in the navigation bar** — a new "Exit" item is placed after
  "Settings". Because a bottom-bar item is easy to mis-tap, exiting is always
  confirmed by a dialog. On confirmation the sensor is disconnected and the
  activity finishes.

  While a recording is in progress the dialog blocks the exit and asks the user
  to stop the recording first. This is deliberate: `stopRecording()` is
  asynchronous, and finishing the activity immediately would tear down the
  ViewModel scope before the CSV buffers were flushed, risking data loss.

- **"Hide Log tab" option in Settings** — a switch in the Settings screen
  removes the Log tab from the bottom navigation bar. The preference is stored
  in DataStore (`hide_log_tab`) and therefore persists across restarts.
  Logging itself is unaffected and continues in the background; the tab can be
  restored from the same switch at any time. If the tab is hidden while the
  user is viewing it, the app navigates back to the Sensors tab.

- **Application version on the Sensors tab** — the version name
  (`BuildConfig.VERSION_NAME`) is displayed directly beneath the "ShimmerENACT"
  title in the header card, so the running build can be identified without
  opening the About screen.

### Notes
- With the Log tab visible the navigation bar now holds six items
  (Sensors, Live, Files, Log, Settings, Exit). Material 3 guidance recommends
  three to five; labels remain legible on typical phone widths but are tight on
  small screens. Hiding the Log tab returns the bar to five items.

## v3.2.3

### Changed — Google Play Console suggestions addressed

**(1) Edge-to-edge display** — already implemented in v3.2.0 via `enableEdgeToEdge()`;
the Play report predates that build. Hardened further in this release: the call now
passes explicit `SystemBarStyle.dark(TRANSPARENT)` for both the status and navigation
bars. ShimmerENACT is always dark-themed, so this guarantees light (white) system-bar
icons and adequate contrast regardless of the device light/dark setting, instead of
relying on the `auto` default. Inset handling is provided by the Compose `Scaffold`
in `MainActivity` and each screen.

**(2) Deprecated edge-to-edge APIs** — `android:statusBarColor` and
`android:navigationBarColor` (both deprecated in Android 15 / API 35) are removed from
`Theme.ShimmerENACT` in `themes.xml`. They were redundant once `enableEdgeToEdge()`
took over bar styling at runtime.

Note: the obfuscated call sites listed by Play (`b.o.b`, `b.q.b`, `V.v.o`) are inside
minified AndroidX library code — `androidx.activity`'s own `EdgeToEdge` helper calls
`setStatusBarColor`/`setNavigationBarColor` for backward compatibility on API < 35, and
`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` is set by the same helper. These are not
reachable from application code and resolve when the libraries themselves are updated.

**(3) Bitmap downsampling** — not actionable. Both reported call sites are inside the
third-party osmdroid library (`org.osmdroid.tileprovider.tilesource.BitmapTileSourceBase.getDrawable`),
not application code. OSM map tiles are fixed 256×256 px images decoded at native
resolution; applying `inSampleSize` would visibly degrade map quality for no practical
memory benefit at that tile size. No change made.

**(4) R8 optimisation**
- Optimised resource shrinking **enabled**: `android.r8.optimizedResourceShrinking=true`
  added to `gradle.properties`. R8 now shrinks code and resources in a single pass, so
  resources referenced only from dead code are removed as well. `minifyEnabled`,
  `shrinkResources`, and `proguard-android-optimize.txt` were already in place, and
  R8 full mode is active by default (no `android.enableR8.fullMode=false` present).
- **AGP 8.10.1 → 8.13.0** (the flag requires AGP 8.12+), with the Gradle wrapper
  raised **8.11.1 → 8.13** to meet AGP 8.13's minimum. JDK 17 and SDK Build Tools
  are unchanged; AGP 8.13 supports up to API 36.1, comfortably above our targetSdk 36.
- The AGP 9.0+ part of the suggestion is **deferred**. AGP 9 brings a new DSL,
  requires Gradle 9.x, and makes optimised resource shrinking the default — the
  benefit Play is flagging is already obtained here on AGP 8.13 with one property.
  The migration is scheduled alongside the eventual `compileSdk` 37 move.

### Fixed
- **Deprecation warning** — `LocalLifecycleOwner` in `RecordingsScreen` now imported
  from `androidx.lifecycle.compose` instead of the deprecated
  `androidx.compose.ui.platform` location.

## v3.2.2

### Changed
- **Foreground recording service removed** — the `RecordingService` introduced
  in v3.2.0 (with `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`,
  and `POST_NOTIFICATIONS` permissions) is removed. Although these are
  install-time permissions with no user-facing prompt, Google Play requires a
  foreground-service policy declaration in the Play Console for apps targeting
  Android 14+, adding review burden with limited benefit for the current
  deployment model where recording is performed with the app in the foreground.

  Removed: `RecordingService.kt`, the manifest `<service>` entry, all three
  permissions, and the ViewModel/DashboardScreen wiring (notification update
  loop and the runtime notification permission request).

  Retained from v3.2.0 (no permissions required): predictive back gesture
  (`enableOnBackInvokedCallback`) and edge-to-edge display (`enableEdgeToEdge()`).

  Operational note: long recordings should keep the app in the foreground
  (e.g. screen on or app visible). If unattended background recording becomes
  a requirement for a future measurement campaign, the v3.2.0 implementation
  can be restored from version history together with its Play Console
  declaration.

## v3.2.1

### Fixed
- **Compile error: `Unresolved reference 'enableEdgeToEdge'` (MainActivity.kt:39)** —
  `enableEdgeToEdge` is a Kotlin extension function on `ComponentActivity`
  (package `androidx.activity`); extension functions cannot be invoked via a
  fully-qualified package path. Added `import androidx.activity.enableEdgeToEdge`
  and changed the call site to the plain `enableEdgeToEdge()`.

## v3.2.0

### Added
- **Foreground recording service (`RecordingService`)** — recording sessions now
  run under a foreground service with the `connectedDevice` foreground service
  type, keeping long acquisitions alive when the app is backgrounded or the
  screen is locked. The service starts automatically when recording begins and
  stops when recording ends. Declared with `FOREGROUND_SERVICE` and
  `FOREGROUND_SERVICE_CONNECTED_DEVICE` permissions per Android 14+/16 policy.
- **Live progress notification** — an ongoing notification shows elapsed
  recording time (self-ticking chronometer) and rows written / file count
  (refreshed every 5 seconds by the ViewModel). On Android 16 (API 36+) the
  notification uses the new progress-centric `Notification.ProgressStyle` in
  indeterminate mode; on older versions a standard low-importance chronometer
  notification is used. Tapping the notification returns to the app.
- **Notification runtime permission flow** — on Android 13+ (API 33+),
  `POST_NOTIFICATIONS` is requested when the user first taps Record. Recording
  proceeds regardless of the outcome; the notification is a convenience, not
  a gate.
- **Predictive back gesture** — `android:enableOnBackInvokedCallback="true"`
  enables the system predictive-back animation for the whole app (default
  behaviour under targetSdk 36, now explicit).
- **Edge-to-edge display** — `enableEdgeToEdge()` is called in `MainActivity`,
  matching the Android 16 requirement that removes the edge-to-edge opt-out
  for apps targeting API 36. Existing Scaffold/insets handling covers the
  status and navigation bar areas.

### Notes
- Deferred: adaptive two-pane tablet layout for the Dashboard (signals beside
  chart on sw ≥ 600dp) and Health Connect export of recorded physiological
  metrics — both identified as candidate Android 16-era enhancements for a
  future release.

## v3.1.16

### Changed
- **Build toolchain upgraded — Google Play API 36 compliance** — Google Play now
  requires apps to target Android 16 (API level 36) or higher. Updated:

  | Component | Old | New |
  |-----------|-----|-----|
  | Android Gradle Plugin (AGP) | 8.9.0 | **8.10.1** |
  | `compileSdk` | 35 | **36** |
  | `targetSdk` | 35 | **36** |
  | `buildToolsVersion` | 35.0.0 | **36.0.0** |
  | Compose BOM | 2025.04.01 | **2025.10.00** |

  Kotlin (2.1.10) and Gradle wrapper (8.11.1) are unchanged — both are already
  within the compatibility range for AGP 8.10.1 (minimum Gradle 8.11.1, JDK 17).

  Compose BOM 2025.10.00 is the latest stable BOM that does not require
  `compileSdk` 37 or AGP 9. Starting from Compose 1.12.0 (BOM ≈ 2026.x),
  `compileSdk` 37 and AGP 9 will become mandatory; that upgrade is deferred
  until Google Play enforces API 37 as the minimum target.

## v3.1.15

### Changed
- **App icon — increased green border around WiFi symbol** — the white WiFi
  signal graphic is scaled down to 72 % of the icon canvas (from 100 %),
  adding uniform green padding of 14 % of the icon width on each side. All
  15 mipmap PNG assets regenerated at mdpi / hdpi / xhdpi / xxhdpi / xxxhdpi.

## v3.1.14

### Changed
- **Application icon updated** — the launcher icon is replaced with the new
  ShimmerENACT WiFi-signal icon (dark green background `#014411`, white
  signal arc graphic). PNG mipmaps generated at all five standard Android
  densities:

  | Density | Launcher icon | Adaptive foreground |
  |---------|--------------|---------------------|
  | mdpi    | 48 × 48 px   | 108 × 108 px        |
  | hdpi    | 72 × 72 px   | 162 × 162 px        |
  | xhdpi   | 96 × 96 px   | 216 × 216 px        |
  | xxhdpi  | 144 × 144 px | 324 × 324 px        |
  | xxxhdpi | 192 × 192 px | 432 × 432 px        |

  The adaptive icon XML (`mipmap-anydpi-v26/ic_launcher.xml` and
  `ic_launcher_round.xml`) now references `@mipmap/ic_launcher_foreground`
  (PNG) instead of the previous `@drawable/ic_launcher_foreground` (vector).
  The `ic_launcher_background` colour in `colors.xml` is updated to `#014411`
  to match the icon background exactly.

## v3.1.13

### Fixed
- **Compile error: `Unresolved reference 'FontWeight'` (SettingsScreen.kt lines 216, 238, 267)** —
  The `import androidx.compose.ui.text.font.FontWeight` line was accidentally
  replaced (not supplemented) when `import androidx.compose.ui.platform.LocalContext`
  was inserted in v3.1.12. Both imports are now present.

## v3.1.12

### Fixed
- **Settings screen shows wrong storage path** — the "Recording storage" info card
  displayed the hardcoded string
  `Android/data/com.rfsat.shimmerenact/files/Documents/ShimmerENACT/` (the
  Kotlin *namespace*), while recordings are actually written to
  `Android/data/com.ShimmerENACT/files/Documents/ShimmerENACT/` (the
  *applicationId* that `getExternalFilesDir()` uses). The path is now built
  dynamically from `context.packageName` so it is always accurate regardless
  of which identifier is set as `applicationId` in `build.gradle`.

### Notes
- **Google Play storage permission compliance** — the current permission set
  contains no `MANAGE_EXTERNAL_STORAGE` and no `READ_EXTERNAL_STORAGE`. Both
  were removed in v3.1.4 when the recording path was moved from the public
  `Downloads/` directory to the app-specific
  `getExternalFilesDir(DIRECTORY_DOCUMENTS)` path. The only storage permission
  remaining is `WRITE_EXTERNAL_STORAGE` scoped to `maxSdkVersion="28"` for
  Android 9 and below. This is fully compliant with Google Play policy; no
  "All files access" justification is required or requested.

  Legacy sessions in `Downloads/ShimmerENACT/` (recorded with versions prior
  to v3.1.4) are visible in the Files screen via a best-effort scan added in
  v3.1.10 (`getLegacyRootDir()`). No additional permission is required for this
  scan on Android 9 and below (covered by the legacy `WRITE_EXTERNAL_STORAGE`);
  on Android 10–32 the files may not be readable if the app no longer holds
  `READ_EXTERNAL_STORAGE`, but they were recorded by the same app so typically
  remain accessible. On Android 13+ the legacy path is generally inaccessible
  without `READ_EXTERNAL_STORAGE`; in that case the legacy scan simply returns
  nothing and no error is shown.

## v3.1.11

### Fixed

- **Recording Setup sheet — Start button not reachable** — the `Column` inside
  `RecordingSetupSheet` was not scrollable. With many signals (e.g. 16 for
  200g IMU) the list overflowed the sheet height and the Start button was
  pushed below the visible area with no way to scroll to it. Added
  `.verticalScroll(rememberScrollState())` to the sheet Column.

- **"÷25" displayed as "+25" next to Battery** — the division-ratio badge showed
  U+00F7 (÷) as the divider symbol. This character is absent from several
  Android system font variants, causing the fallback glyph to appear as "+"
  on some devices. Replaced with `1:N` ratio notation (e.g. `1:25`) which
  uses only ASCII digits and a colon, rendering correctly on all fonts.

- **Session folders show zero files; delete and share do nothing** — root
  cause: `getRootDir()` had a silent fallback to `context.filesDir` (internal
  storage) when `getExternalFilesDir()` returned null. This caused a
  split-brain condition:

  - `startRecording()` called `getRootDir()` → `filesDir` → wrote CSV files
    to **internal** storage.
  - `listSessions()` called `getRootDir()` → `getExternalFilesDir()` →
    created a fresh empty directory on **external** storage, found that
    directory, and reported it with zero CSV files.
  - `deleteSession()` searched only `getRootDir()` (external), found the
    empty directory, deleted it (or returned false) — the actual CSV files
    in internal storage were never touched.
  - `FileProvider` shared the wrong path or an empty file list.

  Fix: the `filesDir` fallback is removed from `getRootDir()`. If
  `getExternalFilesDir()` returns null (external storage physically absent
  — rare on devices with internal eMMC), the method now throws
  `IllegalStateException` immediately, making the failure visible in the
  Log screen rather than silently splitting the data across two locations.
  On all modern Android devices `getExternalFilesDir()` reliably returns a
  valid path.

- **Delete session does not find recordings from legacy path** —
  `deleteSession()` only searched `getRootDir()`. Sessions found via
  `getLegacyRootDir()` (old `Downloads/ShimmerENACT/` path) could not be
  deleted. Fixed by searching both roots in order.

## v3.1.10

### Fixed

- **"Live" view — Record button still not visible** — previous attempts (v3.1.8,
  v3.1.9) moved `RecordingBar` out of `Scaffold.bottomBar` into the Scaffold's
  content lambda, but due to a stray extra closing brace it ended up inside the
  Scaffold's implicit `Box` layer rather than inside the `Column`. In Compose,
  a `Scaffold` content lambda is a `Box` — so `Column` and `RecordingBar` were
  stacked on top of each other at (0,0), with `Column(fillMaxSize)` covering
  `RecordingBar` completely.

  Root cause: the restructuring in v3.1.8 introduced an extra `}` that closed
  the `Column` one line early, placing `RecordingBar` at `Scaffold` Box depth
  rather than `Column` depth. Verified by tracing brace depth from `Column(`
  to `// Signal selector sheet` — `RecordingBar` must be at the same depth
  as the `Column` open brace (depth 1 in the segment), not at depth 0 (Scaffold
  Box). Extra brace removed; structure confirmed by full depth trace.

- **"Files" view — recordings made before v3.1.4 not visible** — v3.1.4
  changed the recording save path from
  `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)/ShimmerENACT/`
  to `context.getExternalFilesDir(DIRECTORY_DOCUMENTS)/ShimmerENACT/`.
  `listSessions()` only scanned the new path, so all recordings made with
  earlier versions of the app were invisible in the Files screen.

  Fix: `getLegacyRootDir()` added to `RecordingRepository`. `listSessions()`
  now concatenates entries from both the current path and the legacy
  `Downloads/ShimmerENACT/` path (if it exists). No files are moved or
  deleted; old recordings appear alongside new ones seamlessly. If the
  legacy directory does not exist, `getLegacyRootDir()` returns null and
  the behaviour is unchanged.

## v3.1.9

### Fixed (re-release of v3.1.8 fixes — previous ZIP was not pushed to GitHub)

Both issues were fixed in v3.1.8 but the symptoms persisted because the corrected
ZIP was not committed to the repository before the next CI run. v3.1.9 is identical
to v3.1.8 in source code; it carries a version bump to ensure CI picks up the correct
sources.

For the full description of root causes and fixes see v3.1.8 below.

Summary:
- **"Live" view — Record button hidden**: `RecordingBar` was in a nested
  `Scaffold.bottomBar` rendered beneath the outer `NavigationBar`. Moved into
  content `Column` with `LazyColumn` taking `weight(1f)` (fixed in v3.1.8).
- **"Live" view — `navigationBarsPadding()` removed from `RecordingBar`**:
  after moving `RecordingBar` out of `Scaffold.bottomBar` the
  `.navigationBarsPadding()` modifier on its `Row` added spurious extra bottom
  padding that could clip or partially obscure the Record button on some devices.
- **"Files" view — no recordings visible**: `ON_RESUME` lifecycle observer
  removed in v3.1.4 was not replaced correctly; `LaunchedEffect(Unit)` only fires
  once on first composition. `DisposableEffect`/`LifecycleEventObserver` restored
  so sessions are refreshed on every navigation to the Files screen (fixed in v3.1.8).

## v3.1.8

### Fixed
- **Recording button not visible in Live view** —
  `RecordingBar` was placed in the `bottomBar` of an inner `Scaffold` inside
  `DashboardScreen`. `MainActivity` already owns an outer `Scaffold` with a
  `NavigationBar` in its `bottomBar`. Android renders nested `Scaffold.bottomBar`
  content at the same Z-level as the outer bar, so `RecordingBar` was drawn but
  completely hidden behind the global navigation bar.

  Fix: `RecordingBar` is moved from `Scaffold.bottomBar` into the `Scaffold`'s
  content area. The content is restructured as a `Column(fillMaxSize)` with the
  `LazyColumn` taking `Modifier.weight(1f)` to fill all available space, and
  `RecordingBar` placed below it — always visible at the bottom of the content
  area, above the outer `NavigationBar`.

- **Recordings not visible in Files view after recording** —
  In v3.1.4 the storage permission banner and its `ON_RESUME` lifecycle observer
  were removed. The observer was also calling `viewModel.refreshSessions()` on
  every resume, which ensured the session list was refreshed each time the user
  navigated to the Files screen. It was replaced with `LaunchedEffect(Unit)`,
  which only fires once when the composable first enters composition.

  As a result, if the user opened Files before recording and then recorded new
  sessions, navigating back to Files showed the stale (empty) session list because
  `LaunchedEffect(Unit)` did not re-fire on subsequent navigations.

  Fix: the `ON_RESUME` `DisposableEffect`/`LifecycleEventObserver` pattern is
  restored in `RecordingsScreen`. On every `ON_RESUME` event (which fires each
  time the user navigates to or back to the Files screen) the session list is
  refreshed from the filesystem. No storage permission logic is re-introduced.

## v3.1.7

### Fixed
- **Lint error: `CoarseFineLocation` — `ACCESS_FINE_LOCATION` without `ACCESS_COARSE_LOCATION`
  on API 31+** — `ACCESS_COARSE_LOCATION` had `android:maxSdkVersion="30"`, so on Android
  12+ (API 31+) it was absent from the effective permission set while `ACCESS_FINE_LOCATION`
  was not capped. Android 12 requires both to be declared together so the user can choose
  to grant only coarse location. The `maxSdkVersion` cap is removed; both permissions are
  now declared unconditionally. This was the lone lint *error* that blocked the CI build
  even though the APK and AAB artifacts had already been produced.

### Changed (lint warnings resolved)
- **`Icons.Filled.ArrowBack` → `Icons.AutoMirrored.Filled.ArrowBack`** in all seven
  screens that use a back button (`AboutScreen`, `ConnectScreen`, `LogScreen`,
  `RecordingViewerScreen`, `RecordingsScreen`, `SamplingRateScreen`, `SettingsScreen`).
  The `AutoMirrored` variant mirrors the icon in right-to-left layouts as required by
  Material Design guidelines. The matching `import` line is added to each file.
- **`Icons.Filled.MultilineChart` and `Icons.Filled.ShowChart` →
  `Icons.AutoMirrored.Filled.*`** in `RecordingsScreen`.
- **`Divider` → `HorizontalDivider`** in `RecordingViewerScreen`, `RecordingsScreen`,
  and `SamplingRateScreen`. `Divider` was renamed to `HorizontalDivider` in
  Material3 1.2.0; the old name still compiles but produces a deprecation warning.

## v3.1.6

### Fixed
- **Compile error: `Unresolved reference 'Intent'` (RecordingsScreen.kt lines 105–124)** —
  `import android.content.Intent` was accidentally removed in v3.1.4 when the
  storage permission banner and its associated imports were deleted. `Intent` is
  still used in two places for the share-session and share-file functionality
  (launching `ACTION_SEND_MULTIPLE` and `ACTION_SEND` via the system share sheet).
  The import is restored.
- **Deprecation warning: `android.defaults.buildfeatures.buildconfig=true`** —
  This global `gradle.properties` flag is deprecated since AGP 9 and will be
  removed in AGP 10. The setting is now expressed only in `app/build.gradle`
  via `buildFeatures { buildConfig true }`, which was already present. The global
  property is commented out in `gradle.properties`.

## v3.1.5

### Changed
- **Build toolchain upgraded — Google Play API 35 compliance** —
  The previous toolchain (AGP 8.1.4 / Kotlin 1.9.20) produced the warning
  *"We recommend using a newer Android Gradle plugin to use compileSdk = 35"*
  on every CI run, and did not meet Google Play's requirement for a fully
  supported compileSdk 35 build chain.

  Updated components:

  | Component | Old | New |
  |-----------|-----|-----|
  | Android Gradle Plugin (AGP) | 8.1.4 | **8.9.0** |
  | Kotlin | 1.9.20 | **2.1.10** |
  | Gradle wrapper | 8.2 | **8.11.1** |
  | Compose BOM | 2023.10.01 | **2025.04.01** |
  | `jvmTarget` / Java compatibility | 1.8 | **17** |
  | `androidx.core:core-ktx` | 1.12.0 | 1.15.0 |
  | `androidx.appcompat:appcompat` | 1.6.1 | 1.7.0 |
  | `com.google.android.material` | 1.11.0 | 1.12.0 |
  | `androidx.lifecycle:*` | 2.7.0 | 2.8.7 |
  | `androidx.activity:activity-compose` | 1.8.2 | 1.10.1 |
  | `androidx.navigation:navigation-compose` | 2.7.5 | 2.8.9 |
  | `kotlinx-coroutines-android` | 1.7.3 | 1.9.0 |
  | `androidx.datastore:datastore-preferences` | 1.0.0 | 1.1.3 |
  | `accompanist-permissions` | 0.33.2-alpha | 0.37.0 |
  | `androidx.test.ext:junit` | 1.1.5 | 1.2.1 |
  | `androidx.test.espresso:espresso-core` | 3.5.1 | 3.6.1 |

  **Compose compiler** — with Kotlin 2.0+, the Compose compiler ships as
  part of the Kotlin toolchain via the `org.jetbrains.kotlin.plugin.compose`
  Gradle plugin (applied in both root `build.gradle` and `app/build.gradle`).
  The `composeOptions { kotlinCompilerExtensionVersion }` block is removed;
  it is no longer used or needed.

  The `compileSdk` and `targetSdk` remain at **35** — they were already
  correct; the issue was the AGP version not officially supporting them.

## v3.1.4

### Changed
- **Storage permissions reduced to comply with Google Play policy** —
  `MANAGE_EXTERNAL_STORAGE` (all-files access) and `READ_EXTERNAL_STORAGE` are
  removed from `AndroidManifest.xml`. `WRITE_EXTERNAL_STORAGE` is retained but
  now scoped to `maxSdkVersion="28"` (Android 9 and below only) where it is
  still required by the OS.

  No functionality is lost. The recording save path in `RecordingRepository`
  is changed from `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)`
  (a public directory requiring broad storage access) to
  `context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)` (an
  app-specific external directory requiring **no permission** on API 29+).

  Files are written to:
  `Android/data/com.rfsat.shimmerenact/files/Documents/ShimmerENACT/`

  They remain fully accessible in any Android file manager and can be shared via
  the existing share sheet. The file provider configuration in `AndroidManifest.xml`
  and `file_paths.xml` continues to cover this path.

  | API level | Android version | Permission now required |
  |-----------|----------------|------------------------|
  | ≤ 28 | Android 9 | `WRITE_EXTERNAL_STORAGE` (scoped, Play-approved) |
  | 29–32 | Android 10–12 | **None** |
  | 33+ | Android 13+ | **None** |

- **`RecordingsScreen` simplified** — all storage permission state management,
  the `ON_RESUME` lifecycle observer, and the permission banner UI are removed.
  The screen now unconditionally loads sessions on entry via `LaunchedEffect(Unit)`.
  The accompanist permissions API (`rememberPermissionState`, `ExperimentalPermissionsApi`)
  is no longer used in this screen.

## v3.1.3

### Changed
- **About screen — "Developed by" text formatting** — the RFSAT Limited description
  is now fully justified (`TextAlign.Justify`) and split into two paragraphs: the
  first covers the company overview (type, location, operational focus); the second
  begins with "Focus areas include…" and lists the research domains. A `Spacer`
  of 8 dp separates the two paragraphs.

## v3.1.2

### Changed
- **About screen — Developed by**: added a second link button pointing to the RFSAT
  portal page dedicated to the ENACT project
  (`rfsat.com/…/enact-horizon-2-1-health`), beneath the existing `www.rfsat.com`
  button.
- **About screen — EU Funded Project**: added a second link button for the official
  ENACT project website (`enact-he.eu`), beneath the existing CORDIS button.
- **About screen — Hardware Platform**: replaced the two-unit summary sentence with
  a structured bullet list of all nine supported Shimmer3 units, matching the full
  set available on the Home/Sensors screen:
  GSR+, EXG, IMU, EMG, Ebio, Bridge Amplifier+, 200g IMU, PROTO3 Deluxe, Custom.
  Each entry shows the unit name (bold), SR number, and a brief signal summary.

## v3.1.2

### Changed
- **About screen — "Developed by" description** — the short placeholder text
  ("Specialising in IoT, remote sensing and environmental monitoring solutions.")
  is replaced with the full RFSAT Limited company profile: non-profit
  research-performing SME, established in Ireland with research offices in Athens
  (Greece); focus areas listed include hybrid positioning, GIS, networked A/V,
  VR/AR/XR, immersive U/Is, autonomous systems (UAS/UGV/UUV), Critical
  Infrastructure protection, Cyber-Physical security, Machine Learning and
  Cognitive AI, and Future Internet / 5G/6G Mobile Communications.

## v3.1.1

### Fixed
- **Compile errors: non-exhaustive `when` expressions (4 locations)** —
  Adding 6 new `SensorType` enum members in v3.1.0 left four `when` expressions
  without branches for the new types. Kotlin requires `when` used as a statement
  (and as an expression) on a sealed/enum type to be exhaustive.

  Locations fixed:

  | File | Function | Added branches |
  |------|----------|----------------|
  | `ShimmerBluetoothManager.kt:243` | `defaultBitmapForType()` | IMU, EMG, EBIO, BRIDGE_AMP, IMU_200G, PROTO3_DELUXE |
  | `ShimmerViewModel.kt:356` | `resetAllSignalRates()` | same 6 |
  | `ShimmerViewModel.kt:446` | `toggleRecordingSignal()` | same 6 |
  | `ShimmerViewModel.kt:458` | `setRecordingSignals()` | same 6 |

  `defaultBitmapForType()` now returns the correct sensor bitmap for each new
  unit type, matching the LogAndStream protocol bitmap bits:
  - **IMU**: accel LN + gyro + mag + battery + wide-range accel + BMP280
  - **EMG**: accel + gyro + ExG Chip1 24-bit + battery (Chip2 absent)
  - **Ebio**: accel + gyro + ExG Chip1 24-bit + ExG Chip2 24-bit + battery
  - **Bridge Amp+**: accel + gyro + battery + `SENSOR_b1_BRIDGE_AMP` + wide-range accel
  - **200g IMU**: accel LN + gyro + mag + ext ADC channels (high-g) + battery + wide-range accel
  - **PROTO3 Deluxe**: accel + gyro + ext ADC channels + battery + wide-range accel + bridge amp

## v3.1.0

### Added — Ebio Unit (SR59)

Bioimpedance + ECG unit. Both ADS1292R chips are active simultaneously: Chip 1
measures ECG (lead-I equivalent on Ch1, driven right leg on Ch2); Chip 2 receives
the bioimpedance signal for respiratory monitoring (voltage Ch1, reference Ch2).

| Signal | Key | Unit |
|--------|-----|------|
| ECG Ch1 | `ecg_ch1` | mV |
| ECG Ch2 (RLD) | `ecg_ch2` | mV |
| BioZ Ch1 | `bioz_ch1` | mV |
| BioZ Ch2 (Ref) | `bioz_ch2` | mV |
| Accel / Gyro / Battery | (standard keys) | |

SR number: SR59. Default BT suffix: `A078`.
Protocol: identical packet format to EXG (both ADS chips 24-bit). New signal key
aliases `ecg_ch1/ch2` and `bioz_ch1/ch2` are emitted alongside `exg1_ch1/ch2`
and `exg2_ch1/ch2` from the same raw bytes.

### Added — Bridge Amplifier+ (SR37)

Strain gauge / load cell interface. Two bridge amplifier ADC output channels
(high-gain for unipolar load cells, low-gain for bipolar/strain-gauge inputs)
plus a resistance divider input for skin-surface temperature via thermistor.

| Signal | Key | Unit | Channel |
|--------|-----|------|---------|
| Bridge High Gain | `bridge_high` | raw ADC | `CH_INT_ADC_CH12` |
| Bridge Low Gain | `bridge_low` | raw ADC | `CH_INT_ADC_CH13` |
| Skin Temp Resistance | `skin_temp_kohm` | kΩ | `CH_INT_ADC_CH1` |
| Accel LN / Gyro / Battery | (standard keys) | | |

SR number: SR37. Default BT suffix: `A079`.
Calibration: `bridge_high`/`bridge_low` are raw 12-bit ADC counts; users apply
their own load-cell calibration. Skin temp uses a 10 kΩ reference divider:
R_skin = 10 × raw / (4095 − raw) kΩ.
Protocol: bridge amp signals map to the `SENSOR_b1_BRIDGE_AMP` bitmap bit.
The same ADC channels are used by GSR+ PPG (`CH_INT_ADC_CH13`) — keys are all
emitted and the sensor type selects which ones to display.

### Added — 200g IMU (SR31-200G)

Standard SR31 IMU base with an additional ADXL377 ±200g high-g accelerometer
connected to three external ADC channels. Ideal for concussion detection, blast
events, and high-impact sports science.

| Signal | Key | Unit | Channel |
|--------|-----|------|---------|
| Accel HG X/Y/Z | `accel_hg_x/y/z` | m/s² | `CH_EXT_ADC_CH6/7/15` |
| Accel LN/WR / Gyro / Mag / Pressure / Temp / Battery | (standard IMU keys) | | |

SR number: SR31-200G. Default BT suffix: `A081`.
Calibration: ADXL377 is ratiometric (0g = mid-scale = 2048 ADC counts);
`accel_hg = (raw − 2048) × 200 × 9.81 / 2048` m/s². Range: ±1962 m/s².
The same external ADC channels are also aliased as PROTO3 Deluxe analog channels.

### Added — PROTO3 Deluxe (SR50)

Four-channel analog input expansion board via 3.5mm TRRS jacks plus full IMU.
Intended for custom analog sensor interfacing; users apply sensor-specific
calibration post-collection. ADC values are passed through as raw counts (0–4095).

| Signal | Key | Unit | Channel |
|--------|-----|------|---------|
| Analog Ch1 | `analog_ch1` | raw | `CH_EXT_ADC_CH6` |
| Analog Ch2 | `analog_ch2` | raw | `CH_EXT_ADC_CH7` |
| Analog Ch3 | `analog_ch3` | raw | `CH_EXT_ADC_CH15` |
| Analog Ch4 | `analog_ch4` | raw | `CH_INT_ADC_CH1` |
| Accel LN / Gyro / Battery | (standard keys) | | |

SR number: SR50. Default BT suffix: `A082`.

### Changed

- `SensorType` enum: four new members `EBIO`, `BRIDGE_AMP`, `IMU_200G`,
  `PROTO3_DELUXE` added before `CUSTOM`.
- `CalibrationParams`: new fields `highGSensitivity`; new methods
  `calibrateHighG()`, `calibrateSkinTemp()`, `calibrateAnalog()`.
- ADC channel parser: `CH_EXT_ADC_CH6/7/15` and `CH_INT_ADC_CH1/12/13` now
  emit multiple signal keys simultaneously so each sensor type reads its own
  relevant value from the same raw channel data.
- `activeConfig` combine chain: upgraded from 2-level nesting (6 types) to
  3-level nesting (`_baseConfigs` → `_midConfigs` → `_extConfigs`) to accommodate
  9 total sensor types within the 5-flow `combine()` API limit.
- `HomeScreen`: 4 new sensor type cards added.
- `SettingsScreen`: 4 new BT Radio ID settings groups added.
- `ShimmerViewModel`: 4 new `MutableStateFlow<SensorConfig>` instances; all
  `when(type)` branches updated.
- `PreferencesRepository`: 4 new DataStore keys, flows, and save functions.

### References

- ShimmerResearch/shimmer3-firmware LogAndStream changelog: SR37/SR47/SR59 ExG
  board family confirmed; `SENSOR_b1_BRIDGE_AMP` bitmap bit (Byte1, bit7)
- Shimmer3 Bridge Amplifier+ User Guide Rev1.6 — two-channel output description
- Shimmer3 200g IMU Spec Sheet v0.1 — ADXL377 ±200g ratiometric analog output
- PROTO3 Deluxe product page — 4 analog input channels via 3.5mm TRRS, SR50

## v3.0.0

### Added — IMU Unit support (SR31)

The Shimmer3 IMU Unit (SR31) is the base Shimmer3 device without any expansion
board. It provides all onboard inertial sensors simultaneously:

| Signal | Key | Unit | Sensor |
|--------|-----|------|--------|
| Accel LN X/Y/Z | `accel_ln_x` / `y` / `z` | m/s² | Low-noise accel (ADXL345/KXTC9) |
| Accel WR X/Y/Z | `accel_wr_x` / `y` / `z` | m/s² | Wide-range accel (LSM303AHTR ±8g) |
| Gyro X/Y/Z | `gyro_x` / `y` / `z` | °/s | MPU9250 gyroscope |
| Mag X/Y/Z | `mag_x` / `y` / `z` | µT | LSM303 / MPU9250 compass |
| Pressure | `pressure_pa` | Pa | BMP280 barometric pressure |
| Temperature | `temp_c` | °C | BMP280 temperature |
| Battery | `batt_mv` | mV | ADC |

- Default BT radio ID: `A080` (configurable in Settings)
- BMP280 compensation: full datasheet double-precision formula implemented in
  `CalibrationParams.calibrateBmp280()`. The BMP280 sends pressure and temperature
  as a pair of 32-bit LE compensated integers; both are decoded together in the
  channel parser.
- Wide-range accel uses `calibrateAccelWr()` with LSM303AHTR default sensitivity
  (1671 LSB/g for ±8g range).

### Added — EMG Unit support (SR47-6-0 EMG mode)

The Shimmer3 EMG Unit uses the **same ADS1292R dual-chip ExG hardware as the EXG
Unit (SR47-6-0)** but in EMG configuration: only Chip 1 is active, measuring a
differential EMG signal from two surface electrodes. Chip 2 is disabled. Source:
Shimmer3 EMG User Guide Rev1.12, §2 and ShimmerResearch/shimmer3-firmware.

| Signal | Key | Unit | Notes |
|--------|-----|------|-------|
| EMG Ch1 | `emg_ch1` | µV | ADS1292R Chip 1, Ch1 — differential EMG |
| EMG Reference | `emg_ref` | µV | ADS1292R Chip 1, Ch2 — reference electrode |
| Accel X/Y/Z | `accel_x` / `y` / `z` | m/s² | Onboard LN accel |
| Gyro X/Y/Z | `gyro_x` / `y` / `z` | °/s | MPU9250 |
| Battery | `batt_mv` | mV | ADC |

- Default BT radio ID: `A077` (same hardware as EXG; change in Settings if needed)
- Calibration: ADS1292R 24-bit raw to µV using
  `raw × (2,420,000 / (8,388,608 × gain))` where gain defaults to 4.
  The same formula is used for EXG (result in mV via `calibrateExg()`).
- The packet format is identical to EXG: `exg1_ch1`/`exg1_ch2` keys are also
  emitted alongside `emg_ch1`/`emg_ref` so the file viewer works for both modes.

### Changed

- **`SensorType` enum**: two new members `IMU` and `EMG` added between `EXG` and
  `CUSTOM`. Existing enum ordinals are not used for serialisation (the name string
  is stored), so this is backward-compatible.
- **`ShimmerProtocol`** — new channel codes:
  - `CH_MPU9250_MAG_X/Y/Z` (0x23–0x25) — MPU9250 compass channels
  - `CH_BMP280_PRESS` (0x2A) / `CH_BMP280_TEMP` (0x2B) — BMP280 pressure/temp
  - `CH_ACCEL_AHTR_X/Y/Z` (0x2C–0x2E) — LSM303AHTR wide-range accel
  - Sensor bitmap bits: `SENSOR_b2_ACCEL_MPU`, `SENSOR_b2_MAG_MPU`,
    `SENSOR_b2_BMP280`
- **`CalibrationParams`** — new fields and methods:
  - `accelWrSens` / `accelWrOffset` — wide-range accel calibration
  - `bmp280DigsT[3]` / `bmp280DigsP[9]` — BMP280 compensation coefficients
  - `exgGain` — ADS1292R gain (default 4)
  - `calibrateAccelWr()`, `calibrateEmg()`, `calibrateExg()`, `calibrateBmp280()`
- **`accel_x/y/z` aliases preserved**: the LN accelerometer parser now emits both
  `accel_ln_x/y/z` (IMU-specific) and `accel_x/y/z` (legacy GSR+/EXG keys) so
  that existing recordings and dashboard configurations continue to work unchanged.
- `HomeScreen`: two new sensor type cards added (IMU, EMG).
- `SettingsScreen`: two new BT Radio ID fields added (IMU, EMG).
- `ShimmerViewModel`: `_imuConfig` and `_emgConfig` `MutableStateFlow` instances;
  `activeConfig` combine chain updated (nested combine for 6 types within the
  5-flow API limit); all `when(type)` branches updated.
- `PreferencesRepository`: `KEY_IMU_BT_ID`, `KEY_EMG_BT_ID`,
  `imuBtId`, `emgBtId` flows, `saveImuBtId()`, `saveEmgBtId()`.

### References

- ShimmerResearch/shimmer3-firmware (LogAndStream protocol sensor bitmap, channel
  codes): <https://github.com/ShimmerResearch/shimmer3-firmware>
- Shimmer3 IMU User Guide Rev1.4 — sensor list and calibration for SR31
- Shimmer3 EMG User Guide Rev1.12 — ADS1292R EMG configuration (Chip 1 only)
- Shimmer C# API (ShimmerResearch/Shimmer-C-API) — signal lists and unit
  conventions cross-referenced for consistency

## v2.2.3

### Changed
- **Large-screen / multi-window support added to `AndroidManifest.xml`** —
  Google Play flagged three issues under its large-screen quality guidelines:

  1. *Orientation locked to portrait* — `android:screenOrientation="portrait"` on
     `MainActivity` was removed. The app now follows the device's natural
     orientation. All screens use `fillMaxSize()`, `weight()`, and `LazyColumn`
     throughout, so they reflow correctly in landscape and on tablets without
     any layout code changes.

  2. *Activity not resizeable* — `android:resizeableActivity="true"` added to
     `MainActivity`. This enables split-screen, freeform windowing (ChromeOS /
     Samsung DeX), and picture-in-picture contexts.

  3. *Configuration changes causing Activity recreation* —
     `android:configChanges` set to
     `orientation|screenSize|screenLayout|smallestScreenSize|density|keyboard|keyboardHidden|navigation`.
     Without this, every rotation or window resize triggers a full Activity
     recreation, which disconnects the active Bluetooth sensor session and
     loses the streaming state. With this attribute, the Activity handles
     the configuration change itself and Compose recomposes in place —
     the Bluetooth connection is preserved across rotations and resizes.

  4. *Screen-size support declaration* — `<supports-screens>` element added
     with `smallScreens`, `normalScreens`, `largeScreens`, `xlargeScreens`,
     `resizeable`, and `anyDensity` all set to `true`. This explicitly signals
     to the Play Store that the app is designed for all form factors including
     tablets, foldables, and ChromeOS.

## v2.2.2

### Fixed
- **Graph moves vertically when dragged; drag does not pan the waveform** —
  two root causes, both in the layout of `RecordingViewerScreen`:

  1. *`verticalScroll` on the upper panel Column intercepted touch events* —
     the Column containing the chips strip, stats strip, readout, and chart
     had `verticalScroll(rememberScrollState())`. Compose scroll containers
     claim ownership of vertical gesture events before any child view sees
     them. When the user dragged a finger on the chart, the scroll container
     consumed the event and scrolled the panel instead of passing it to
     MPAndroidChart. This caused the entire chart area to move vertically and
     prevented horizontal pan gestures from reaching the chart.
     Fix: `verticalScroll` removed from the upper panel. The strips above the
     chart are all fixed-height and require no scrolling; the chart itself
     handles panning internally.

  2. *Chart height fixed at 300 dp instead of filling available space* —
     with `verticalScroll` removed, the Column no longer has an unbounded
     height, so `height(300.dp)` would leave unused space below the chart.
     The chart modifier is changed to `weight(1f)` so it fills all space not
     taken by the fixed-height strips (chips ≈ 36 dp when present, stats
     ≈ 44 dp, readout = 36 dp), making full use of the screen regardless of
     device size.

  3. *Touch interception guard added* — `setOnTouchListener` is added to the
     `LineChart` view. On `ACTION_DOWN` it calls
     `parent.requestDisallowInterceptTouchEvent(true)`, telling any ancestor
     view group not to intercept touch events for the duration of the gesture.
     On `ACTION_UP`/`ACTION_CANCEL` the flag is reset. This is belt-and-braces
     protection against future layout changes re-introducing a scroll ancestor,
     and it ensures pinch-to-zoom gestures are never stolen by the outer Column.

## v2.2.1

### Fixed
- **Compile error: `BorderStroke` unresolved reference (line 689)** —
  `BorderStroke` is defined in `androidx.compose.foundation` (the top-level
  package, not a subpackage). The existing wildcard imports covered
  `androidx.compose.foundation.layout.*`, `androidx.compose.foundation.shape.*`
  etc., but not the top-level package itself. Added
  `import androidx.compose.foundation.BorderStroke`.

## v2.2.0

### Added
- **Multi-signal graph viewer** — multiple signals from the same recording session
  can now be displayed together on a single graph:

  - A **"View all signals"** button (stacked-lines icon) appears in the session
    header row when a session contains two or more signal files. Tapping it opens
    the viewer with all files from that session loaded simultaneously.
  - Each signal is drawn in a distinct colour taken from the `ChartColors` palette
    defined in `Color.kt`. The MPAndroidChart legend is shown automatically when
    more than one signal is active.
  - A **signal selection chip bar** appears above the graph when two or more signals
    are loaded. Each chip shows a colour dot and the signal name. Tapping a chip
    toggles that signal's visibility on the graph. At least one signal must remain
    visible at all times (the last active chip cannot be deselected).
  - The **time axis is aligned** across all signals: the X origin is the earliest
    timestamp across all loaded files, so signals recorded simultaneously overlay
    correctly even if their individual timestamp offsets differ slightly.
  - The **stats strip** shows min / mean / max for each visible signal, scrolling
    horizontally if needed.
  - The **cursor readout** shows the value of every visible signal at the timestamp
    nearest to the tap position, colour-coded by signal, in a horizontally
    scrollable row.
  - **GPS / location map** is shown when any of the loaded files contains GPS
    columns; the trace and selection marker work identically to the single-file view.
  - **Single-file entry point is fully backward-compatible**: tapping the chart
    icon on an individual file row opens the same viewer with one signal, with
    no visible change in behaviour.

### Changed
- `RecordingViewerScreen` refactored into two overloads:
  - `RecordingViewerScreen(recordingFile, onBack)` — existing single-file entry
    point; delegates to the multi-file overload with a one-element list.
  - `RecordingViewerScreen(files, title, onBack)` — new multi-file entry point.
- `parseCsv` extracted into a private top-level function (previously inlined in
  `LaunchedEffect`), allowing it to be called once per file in a parallel map.
- Chart fill disabled for multi-signal view (fill areas overlap unreadably when
  signals share the same Y range); fill remains on for single-signal view via the
  same code path.
- `Screen.SessionViewer` route added to `Screen.kt`.
- `RecordingsScreen` receives a new `onViewSession` callback parameter (defaults
  to `{}` for backward compatibility).
- `MainActivity` wires `onViewSession` and adds the `SessionViewer` composable
  destination to the `NavHost`.

## v2.1.6

### Fixed
- **Sensor not reading data in release build (regression introduced in v2.1.0)** —
  Adding `osmdroid` as a dependency introduced two ProGuard/R8 issues that caused
  the release APK to malfunction at startup:

  1. *osmdroid ContentProvider crash at startup* — osmdroid declares
     `OpenStreetMapTilesProvider` (a `ContentProvider`) in its AAR manifest, which
     is merged into the app's `AndroidManifest.xml` at build time. R8 minification
     in the release build stripped and renamed osmdroid's internal classes because
     no keep rule was present. The merged manifest still referenced the original
     class names, causing a `ClassNotFoundException` when Android tried to
     instantiate the `ContentProvider` during `Application` initialisation —
     before any app code ran. This crash prevented the entire app from starting
     correctly, including Bluetooth sensor connectivity.

  2. *Shimmer coroutine lambdas mangled by R8* — `ShimmerBluetoothManager` and
     `ShimmerViewModel` use Kotlin coroutine lambdas and anonymous inner classes
     whose names R8 can mangle when no keep rule is present. This broke the
     Bluetooth streaming coroutine even if the startup crash was somehow bypassed.

  **Fix:** Added the following keep rules to `proguard-rules.pro`:
  ```
  -keep class org.osmdroid.** { *; }
  -dontwarn org.osmdroid.**
  -keep class com.rfsat.shimmerenact.data.bluetooth.ShimmerBluetoothManager { *; }
  -keep class com.rfsat.shimmerenact.data.bluetooth.ShimmerBluetoothManager$* { *; }
  -keep class com.rfsat.shimmerenact.viewmodel.ShimmerViewModel { *; }
  -keep class com.rfsat.shimmerenact.viewmodel.ShimmerViewModel$* { *; }
  ```

  Note: the v2.1.5 fixes for map layout stability and permission refresh are
  also included in this release unchanged.

## v2.1.5

### Fixed
- **Map moves over graph and location marker appears outside map bounds** —
  three separate root causes addressed:

  1. *`animateTo` causing repeated `requestLayout` during pan animation* —
     `controller.animateTo()` produces a smooth pan animation over multiple
     frames; on each frame osmdroid calls `requestLayout()`, which Compose
     intercepts and re-runs the layout pass, shifting the map block relative
     to the graph above it. Replaced with `controller.setCenter()` which
     repositions the viewport instantly with a single layout pass.

  2. *`MapView` painting outside its allocated area* — osmdroid's tile and
     overlay rendering can draw beyond the view's logical bounds during a
     viewport transition. Wrapped the `AndroidView` in a `Box` with
     `requiredHeight(320.dp)` and `clipToBounds()`. `clipToBounds()` sets
     `View.setClipChildren(true)` / `View.setClipToOutline(true)` on the
     Compose layer so no pixel from the `MapView` can appear outside the
     320 dp box regardless of what the osmdroid renderer draws.
     The `AndroidView` inside the Box uses `fillMaxSize()` so it fills the
     Box exactly.

  3. *`onMeasure` override removed* — the override added in v2.1.4 was
     redundant once the Box with `clipToBounds` was in place, and it
     introduced a subtle issue: forcing `MeasureSpec.getSize()` as the exact
     dimension caused the `MapView` to report zero size on first layout
     when Compose passed `MeasureSpec.UNSPECIFIED` during the initial measure.
     Removed; `requiredHeight` + `clipToBounds` is sufficient.

- **Storage permission banner not dismissed after granting (API 30+)** —
  `hasStorageAccess` was computed as a plain `val` using
  `Environment.isExternalStorageManager()`, which is a one-shot snapshot.
  When the user navigated to the system Settings page to grant all-files
  access and then returned to the app, the composable was not recomposed
  (no state change triggered it), so the banner remained visible. Fixed by:
  - Making `hasStorageAccess` a `mutableStateOf` variable
  - Adding a `DisposableEffect` that registers a `LifecycleEventObserver`
    on the screen's lifecycle owner
  - On every `ON_RESUME` event (including return from Settings), the observer
    re-evaluates the permission and updates `hasStorageAccess`, which
    triggers recomposition and dismisses the banner automatically.
  This also covers the `READ_EXTERNAL_STORAGE` path (API ≤ 32) via the same
  observer, making the refresh path uniform across all API levels.

## v2.1.4

### Fixed
- **Map resizes and overlaps graph on pan or selection change** — two root causes:

  1. *`MapView.requestLayout()` escaping into Compose* — osmdroid calls
     `requestLayout()` (not just `invalidate()`) every time it redraws: on tile
     load, pan, zoom, and overlay invalidation. This propagated through the Android
     view hierarchy into Compose's layout pass, causing the `AndroidView` to be
     remeasured. During remeasurement `MapView.onMeasure()` could return a size
     different from what Compose originally allocated, making the map grow or shrink
     and push the graph out of place. Fixed with two measures:
     - `height(320.dp)` replaced by `requiredHeight(320.dp)` — Compose's
       `requiredHeight` sets `MeasureSpec.EXACTLY` on the child and ignores the
       child's reported desired size, so whatever `MapView.onMeasure()` returns is
       discarded.
     - `MapView` wrapped in an anonymous subclass that overrides `onMeasure` to
       always call `setMeasuredDimension` with the spec's exact size — a belt-and-
       braces guard ensuring the size is locked even if Compose's modifier alone
       is insufficient.

  2. *Cursor readout row changing height on selection* — the value readout `Box`
     switched between a one-line placeholder and a one-line data row, but the two
     had different intrinsic heights due to icon/text padding differences. This
     caused the scrollable upper panel to change height, which re-triggered the
     outer Column's layout pass and shifted the map block. Fixed by giving the
     readout `Box` a fixed `height(36.dp)` so it is always the same size regardless
     of whether a point is selected.

## v2.1.3

### Fixed
- **Compile error: wrong `Overlay.draw()` signature** — `DotsOverlay` and
  `SelectionOverlay` overrode `draw(Canvas, Projection, Boolean)`, which does not
  exist as an abstract method in `org.osmdroid.views.overlay.Overlay`. The correct
  abstract method to override is `draw(Canvas, MapView, Boolean)`. Because the
  wrong signature was used, the compiler saw a new non-abstract method rather than
  an override, leaving the actual abstract `draw` unimplemented — making both
  classes effectively abstract and preventing instantiation. Fixed by changing the
  second parameter from `Projection` to `MapView` and obtaining the projection
  inside the body via `mapView.projection`. The now-unused
  `import org.osmdroid.views.Projection` is also removed.

## v2.1.2

### Fixed
- **Compile errors: `IGeoPoint` unresolved and `SimpleFastPointOverlay` generics** —
  The v2.1.1 fix attempted to use `mutableListOf<org.osmdroid.util.IGeoPoint>` and
  `ArrayList<org.osmdroid.util.IGeoPoint>` to satisfy `SimplePointTheme`'s constructor
  signature. This failed because `IGeoPoint` lives in `org.osmdroid.api`, not
  `org.osmdroid.util`, and because the Kotlin generic type argument was still not
  accepted by the Java API. Root fix: **`SimpleFastPointOverlay` and `SimplePointTheme`
  are removed entirely**. Cyan measurement dots and the red selection circle are now
  drawn by two small custom `Overlay` subclasses (`DotsOverlay`, `SelectionOverlay`)
  that paint directly onto the `Canvas` using `Projection.toPixels()` — no generics,
  no external API contracts to satisfy.
- **Compile error: `BoundingBox.fromGeoPoints` type mismatch** — `fromGeoPoints`
  takes `List<GeoPoint>`, not `List<IGeoPoint>`. The cast
  `geoPoints as List<IGeoPoint>` was incorrect and also used the wrong package.
  Fixed by computing the bounding box directly from the min/max of the `GeoPoint`
  latitude and longitude values and constructing
  `BoundingBox(north, east, south, west)` explicitly.

## v2.1.1

### Fixed
- **Compile error: `SimplePointTheme` type mismatch (lines 174, 521)** —
  `SimplePointTheme` constructor expects `MutableList<IGeoPoint>`, not
  `MutableList<GeoPoint>` or `ArrayList<GeoPoint>`. Kotlin does not automatically
  widen a concrete subtype list to the interface list expected by the Java API.
  Fixed by using `mutableListOf<IGeoPoint>(pt)` and `ArrayList<IGeoPoint>(geoPoints)`
  with the explicit interface type argument.
- **Compile error: `HorizontalDivider` unresolved reference (line 473)** —
  `HorizontalDivider` was introduced in Material3 1.2.0; the project uses
  `compose-bom:2023.10.01` which resolves to Material3 1.1.x. Replaced with the
  compatible `Divider` composable, which is available in all Material3 1.x versions.

## v2.1.0

### Changed
- **Map: replaced WebView + Leaflet.js with native osmdroid MapView** —
  The WebView approach was abandoned after four unsuccessful releases. The root cause
  is that `WebView.loadDataWithBaseURL()` does not grant the loaded page network access
  to third-party origins regardless of the base URL argument; Android's security model
  treats the page as coming from a synthetic context and blocks the Leaflet CDN fetch
  and OSM tile requests. There is no setting combination that reliably fixes this across
  Android API 26–35 without running a local HTTP server.

  The map is now rendered with **osmdroid 6.1.20** (`org.osmdroid:osmdroid-android`),
  a pure-Android native library that draws OpenStreetMap tiles directly on a `MapView`
  canvas using standard Android `HttpURLConnection` networking — no WebView, no
  JavaScript engine, no CDN, no CORS:
  - Tile source: OSM Standard (MAPNIK) — same data as Leaflet was using
  - `MapView` integrates with Jetpack Compose via `AndroidView`, identical to the
    existing `LineChart` integration
  - Cyan polyline connecting all measurement GPS positions (`Polyline` overlay)
  - Solid cyan dot at every individual measurement position (`SimpleFastPointOverlay`)
  - Red filled circle (22 px) marks the currently selected sample; updated via
    `LaunchedEffect(selectedIndex)` replacing and re-adding a `SimpleFastPointOverlay`
  - Map pans to the selected point with `controller.animateTo()`
  - On first display, the view is fitted to the bounding box of all GPS points via
    `zoomToBoundingBox()` called in a `post {}` block after layout
  - `Configuration.userAgentValue` set to identify the app to OSM tile servers per
    OSM tile usage policy
  - `INTERNET` permission already declared in `AndroidManifest.xml` from v2.0.0

### Added
- `org.osmdroid:osmdroid-android:6.1.20` dependency in `app/build.gradle`
  (Apache 2.0 licence; available on Maven Central; no additional repository required)

## v2.0.3

### Fixed
- **Graph: independent X/Y zoom** — `setPinchZoom(true)` caused both axes to zoom
  identically together. Changed to `setPinchZoom(false)`: with both `isScaleXEnabled`
  and `isScaleYEnabled` true, MPAndroidChart detects the dominant direction of a
  two-finger gesture and zooms only that axis — a predominantly horizontal spread
  zooms the time axis, a predominantly vertical spread zooms the value axis.
- **Map: WebView now renders correctly** — root cause identified and fixed. The
  entire screen was wrapped in `verticalScroll(rememberScrollState())`. Compose
  scroll containers measure their children with *unbounded (infinite) height*; the
  Android view system then assigns the `WebView` zero actual pixels and it renders
  nothing — the label "LOCATION TRACE" appeared but the map area was blank.
  Fix: the layout is restructured into a non-scrollable outer `Column`. The upper
  section (stats strip, value readout, chart) is wrapped in its own `verticalScroll`
  column sized with `weight(1f)`. The map `AndroidView` sits below it as a separate
  sibling with an explicit `height(320.dp)` — fully constrained, outside any
  scroll container, so the WebView receives its correct measured dimensions and
  Leaflet renders the map. The `osmHtml` string is now built in `remember(gpsPoints)`
  (computed before composition) and loaded directly in `factory`, eliminating the
  previous `update`-lambda timing complexity.

## v2.0.2

### Fixed
- **Map: OSM tiles and Leaflet now render correctly** — two independent bugs caused
  the location trace panel to remain blank:
  1. *SRI integrity check failure* — `<link>` and `<script>` tags for Leaflet (loaded
     from `unpkg.com`) carried `integrity=` SHA-256 hashes and `crossorigin=""`.
     When `loadDataWithBaseURL` is used inside an Android `WebView`, the page is
     served from a synthetic internal origin; the CORS preflight for the CDN resource
     fails, the integrity check cannot be completed, and the browser silently refuses
     to execute the script. Leaflet never initialised, leaving a blank `<div>`. Fix:
     `integrity` and `crossorigin` attributes removed from both tags.
  2. *Stale HTML captured in `factory` lambda* — `AndroidView.factory` is called
     exactly once when the view enters the composition. The GPS data is loaded
     asynchronously, so `gpsPoints` is empty when `factory` first runs; the HTML
     computed at that moment contained no coordinates. By the time data arrived,
     `factory` was never called again. Fix: HTML is now built in
     `LaunchedEffect(gpsPoints)` and stored in a `MutableState<String>`
     (`osmHtmlState`). The `update` lambda — which runs on every recompose — reads
     the current value and calls `loadDataWithBaseURL` whenever the content changes
     (guarded by a `tag`-based hash to prevent redundant reloads).
- **Map: cyan measurement dots now fully filled** — `fillOpacity` raised from `0.7`
  to `1.0` so every dot is solid cyan with no transparency.

## v2.0.1

### Fixed
- **Graph: zoom preserved across interactions** — `fitScreen()` was being called inside
  the Compose `update` lambda, which runs on every recomposition (including on every
  `selectedIndex` state change). This reset the viewport on every tap, making zoom
  impossible to hold. Data is now pushed to the chart exclusively via
  `LaunchedEffect(entries)`, which only fires when the underlying dataset changes.
  `fitScreen()` is called once at that point; subsequent taps and drags no longer
  touch the viewport. Pinch-to-zoom (`setPinchZoom(true)`) re-enabled.
- **Graph: selected-point circle enlarged** — red fill radius increased from 12 px
  to 18 px; white ring radius increased from 14 px to 22 px with a 3 px stroke width,
  making the indicator clearly visible on small-screen devices.
- **Map: OSM tiles and Leaflet now load correctly** — `loadDataWithBaseURL` was using
  `https://tile.openstreetmap.org/` as the base URL, but Leaflet is loaded from
  `unpkg.com`; the mismatched origin caused the WebView security context to block the
  CDN scripts. Base URL changed to `https://www.openstreetmap.org/` (same registrable
  domain as the tile server), which satisfies the browser security model and allows
  Leaflet to bootstrap. `mixedContentMode` set to `MIXED_CONTENT_ALWAYS_ALLOW` and
  `useWideViewPort` / `loadWithOverviewMode` enabled. Background colour set to the
  app dark colour instead of transparent to prevent the white-flash before tiles load.
- **CI: debug APK build removed** — the workflow now builds and publishes only the
  release APK and release AAB. `lintRelease` and `testReleaseUnitTest` replace their
  debug-variant equivalents. Artifact names updated to
  `ShimmerENACT-<version>-release` and `ShimmerENACT-<version>-PlayStore`.



### Added
- **Graph: data-point circles** — every measurement is now marked with a small filled
  circle in the same colour as the signal line, giving a clear visual indication of
  sample density and individual sample positions. Circles are drawn for datasets up to
  5 000 points; above that threshold they are suppressed to prevent a solid-band effect
  at very high sample rates.
- **Graph: red selected-point indicator** — the previously used vertical + horizontal
  crosshair lines are replaced by a prominent red filled circle (white border) drawn
  exactly on the selected measurement. Selection is updated continuously during tap and
  drag, giving real-time tracing without visual clutter.
- **Location trace map** — when a recording CSV contains GPS columns
  (`latitude_deg`, `longitude_deg`) a scrollable OpenStreetMap panel is shown beneath
  the signal graph. The map displays:
  - A cyan polyline connecting all measurement positions in chronological order
  - A small cyan dot at every individual measurement position
  - A red circle (white border) that moves to the GPS position of the currently selected
    measurement as the user taps or drags on the graph; the map pans smoothly to follow
- **OpenStreetMap integration (Leaflet.js)** — the location map is rendered inside an
  in-process `WebView` using [Leaflet.js 1.9.4](https://leafletjs.com/) loaded via CDN.
  OSM standard raster tiles (`tile.openstreetmap.org`) are used. No API key is required.
  The `INTERNET` permission is declared in `AndroidManifest.xml`.
- **Versioning scheme** — version numbering migrated to
  `<major>.<minor>.<revision>` semantics:
  - **major** — incremented for breaking or architectural changes
  - **minor** — incremented for significant new features
  - **revision** — incremented for bug fixes and corrections only
  - This release is v2.0.0 as the first version under the new scheme

### Changed
- `RecordingViewerScreen`: screen is now vertically scrollable so the map panel can
  sit below the chart without clipping on small-screen devices
- CSV parser extended to detect and read `latitude_deg` and `longitude_deg` columns
  (written by the v1.8.0+ `LocationRepository`); values are stored in `CsvPoint` and
  passed to the map renderer
- `versionCode` bumped to 53; `versionName` set to `"2.0.0"`



### Added
- **GPS location tagging in recordings**: every CSV row now includes four additional
  columns — `latitude_deg`, `longitude_deg`, `altitude_m`, `location_accuracy_m` —
  populated from the Android Fused Location Provider at 1 Hz (fastest 4 Hz) with
  `PRIORITY_HIGH_ACCURACY`
  - Columns are present in the header of all new recordings; cells are empty (`,,,,`)
    for any row where no GPS fix is available (permission denied or fix not yet acquired)
  - `LocationRepository` seeds itself from the last known position immediately on
    `startUpdates()` so the very first data row typically has coordinates
  - Location updates start when recording begins and stop when it ends, conserving
    battery between sessions
- **New file**: `data/repository/LocationRepository.kt` — thin wrapper around
  `FusedLocationProviderClient`; exposes `StateFlow<Location?>` for use across the app

### Changed
- `play-services-location:21.3.0` added as a dependency
- `ACCESS_FINE_LOCATION` manifest declaration: `maxSdkVersion="30"` cap removed so
  GPS permission is available on all API levels (was silently missing on API 31+)
- `ConnectScreen`: `ACCESS_FINE_LOCATION` added to the API 31+ permission request
  list alongside `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`; permission banner text
  updated to mention location tagging

## v1.7.6

### Changed
- `compileSdk` and `targetSdk` raised to **35** (Android 15) — required by
  Google Play Store for new app submissions; `buildToolsVersion` updated to
  `35.0.0` accordingly

### Fixed
- Recordings screen: storage permission is now requested at runtime before the
  session list is loaded
  - On API 26–32: `READ_EXTERNAL_STORAGE` runtime permission is requested via the
    standard system dialog; sessions from previous installs become readable once
    the permission is granted
  - On API 33+: `READ_EXTERNAL_STORAGE` is no longer effective for non-media files
    in `Downloads`; the screen detects this and offers a button that opens the
    system **All files access** settings page for the app
  - `READ_EXTERNAL_STORAGE` declaration in `AndroidManifest.xml` extended to all
    API levels (previously capped at `maxSdkVersion="32"`) and
    `MANAGE_EXTERNAL_STORAGE` added (with `tools:ignore="ScopedStorage"`) to
    support the API 30+ all-files-access grant
  - Session list refreshes automatically once the permission is granted (driven by
    `LaunchedEffect(hasStorageAccess)`)

## v1.7.5

### Fixed
- Google Play upload: `applicationId` changed to `com.ShimmerENACT` to match the
  package name registered in Google Play Console; the Kotlin source `namespace`
  (`com.rfsat.shimmerenact`) is unchanged — no source file edits required
- Recordings list: files from sessions recorded with earlier app versions now
  appear correctly with their size and row count; root cause was that
  `f.bufferedReader(Charsets.UTF_8)` throws `MalformedInputException` when the
  file contains bytes that are invalid UTF-8 (written by older `FileWriter` calls
  that defaulted to the device locale charset on some devices); replaced with
  `InputStreamReader` backed by a `CharsetDecoder` configured with
  `CodingErrorAction.REPLACE` so bad bytes are silently substituted rather
  than causing the whole file parse to fail and return `null`

## v1.7.4

### Fixed
- `RecordingRepository.parseRecordingFile`: compile error — `return null` inside
  expression-body function (`= try { … }`); converted to block body (`{ … }`) with
  `return null` guard and `return try { … } catch { … }`

## v1.7.3

### Fixed
- Graph viewer: selected sample now marked with a filled contrast dot (dark fill,
  green border) at the highlight position; dragging a pressed finger across the
  chart continuously updates both the dot and the value readout row — achieved via
  `OnChartGestureListener.onChartTranslate` calling `getHighlightByTouchPoint`
- Graph viewer: CSV now read with `useLines(Charsets.UTF_8)` — explicit charset
  prevents `MalformedInputException` on files written by older app versions on
  devices whose default locale charset was not UTF-8
- Recordings: `parseRecordingFile` rewritten as a streaming line reader — no longer
  loads the entire file into memory with `readLines()`; reads only header metadata
  and counts rows on the fly; reports `sizeBytes` and `rowCount` correctly for all
  session files including those from earlier app versions
- Sensors tab: tapping "Sensors" in the bottom nav now always navigates to the
  Home screen, including when connected and viewing the Live/Dashboard screen;
  the previous `saveState`/`restoreState` combination was restoring the Dashboard
  back-stack entry instead of showing Home
- README.md: version references updated to v1.7.3; `RecordingViewerScreen` added
  to architecture diagram

## v1.7.2

### Fixed
- `RecordingViewerScreen`: type mismatch compile error on line 92 — `if/else`
  returning `Long?` assigned to `Long`; flattened into a single nullable chain
  terminated by `?: continue`

## v1.7.1

### Fixed
- Recordings list: sessions now refresh every time the Files tab is opened, so
  sessions recorded in the current app launch appear without restarting the app
- Recordings list: `listSessions()` no longer silently drops sessions when any
  individual file fails to parse; each failure is logged and the rest of the session
  is still shown; permission errors are logged explicitly
- Graph viewer: chart now scales the time axis to fit all data on first display
  (`fitScreen()` + `notifyDataSetChanged()` called after setting data); previously
  MPAndroidChart rendered at its default zoom, compressing all points into one line
- Graph viewer: CSV column detection rewritten — finds `timestamp_ms` and value
  columns by name rather than assuming fixed positions; handles footer comment lines
  (e.g. `# Session end:`) without treating them as data rows
- Graph viewer: chart entries are built once per data load inside `remember(points)`
  rather than on every recomposition; prevents redundant LineDataSet rebuilds

## v1.7.0

### Fixed
- Graph viewer: tapping the chart icon on a file row now correctly opens the viewer
  (the `onView` callback was wired into `SessionCard` but not forwarded into `FileRow`)
- Recordings list: sessions recorded with earlier versions now appear correctly;
  session start time is extracted from the rightmost 19 characters of the directory
  name (`yyyy-MM-dd_HH-mm-ss`) instead of a broken substring reconstruction that
  failed for device names containing underscores

## v1.6.0

### Added
- About screen now reads version from BuildConfig — version number is always consistent
  with the build, no longer needs manual update
- Graph viewer for recorded CSV files — tap the chart icon on any file in Recordings
  to view its data as an interactive line chart with pinch-to-zoom and drag
- Value tracing in graph viewer — tap any point to display its exact timestamp and value
- Summary statistics (min / mean / max / sample count) shown above the graph
- Robust loading of sessions recorded with earlier app versions (v1.0, v1.1):
  bare CSV files in the ShimmerENACT root are grouped under a "Legacy recordings"
  session; header parsing handles all past formats with graceful fallbacks
- Older session deletion now works correctly for all directory naming schemes

### Changed
- Home screen shows a "Connected" status card when a sensor is already connected,
  replacing the "Connect to Shimmer3" button — prevents accidental re-connection
- Disconnect button on Home screen is disabled while recording is in progress
- Paired Bluetooth device list shows only Shimmer devices by default; a "Show all"
  toggle reveals other paired devices when needed
- Connect screen blocks further connection attempts when a sensor is already connected

## v1.1.1 (current)

### Fixed
- Correct Shimmer3 BT channel type codes (all codes from 0x07 onwards were wrong)
  — GSR is `0x15`, PPG/ExpA0 is `0x17`, Gyro is `0x0C–0x0E`, Mag is `0x09–0x0B`
- Inquiry response reading now waits for complete channel list (was truncating after
  first BT fragment, causing only Accel X/Y to be parsed)
- Added `SENSOR_MAG` to GSR+ default bitmap (Magnetometer was absent from fallback)
- Log screen: entries no longer scroll away — auto-scroll only fires when at bottom;
  manual scroll up is preserved; Jump-to-Bottom FAB added
- Log buffer increased from 500 to 1000 entries
- Removed per-packet verbose parser logs that flooded the log buffer at 250 Hz
- Deselected chart signals now clear immediately (chart.data = null on empty selection)
- LogScreen brace balance fix (compilation error)

## v1.1.0

### Added
- Diagnostic Log screen with colour-coded entries (DEBUG / INFO / OK / WARN / ERROR)
- Error count badge on Log tab icon
- Per-signal configurable sampling rates (hardware rate + software decimation)
- Independent recording signal selection (separate from Live view display selection)
- One CSV file per signal per recording session, with ISO 8601 timestamps
- Session-grouped recordings view with per-file share and delete
- Recording setup sheet showing output rate per signal before starting
- Supported-parameter enforcement — unavailable signals hidden after connection

### Fixed
- Channel-list-based packet parser replaces bitmap inference
- Correct Shimmer3 inquiry response bitmap offset (byte 4)
- ADXL345 accel data width corrected to 16-bit signed
- Battery bitmap check corrected (`b1 & 0x20`)
- Chart X axis uses real timestamps in seconds
- Stream sync drain prevents frame alignment errors
- R8 minification enabled; debug/release app IDs separated

## v1.0.0

- Initial release: BT Classic connection to Shimmer3 GSR+ and ExG units
- Live signal display and MPAndroidChart graph
- Basic CSV recording
- Settings screen with BT radio ID configuration


### Added
- Diagnostic Log screen with colour-coded entries (DEBUG / INFO / OK / WARN / ERROR)
- Error count badge on Log tab icon
- Per-signal configurable sampling rates (hardware rate + software decimation)
- Independent recording signal selection (separate from Live view display selection)
- One CSV file per signal per recording session, with ISO 8601 timestamps
- Session-grouped recordings view with per-file share and delete
- Recording setup sheet showing output rate per signal before starting
- Supported-parameter enforcement — unavailable signals greyed out in selectors

### Fixed
- Channel-list-based packet parser (replaces bitmap inference)  
  — all sensor channels now display correctly, not just PPG
- Inquiry response bitmap offset corrected (byte 4, not byte 3)
- ADXL345 accel data width corrected to 16-bit signed (from 14-bit masked)
- Battery bitmap check corrected (b1 & 0x20, not b1 shr 13)
- Chart X axis now uses real timestamps in seconds (was sample index)
- Stream sync drain after rate command prevents frame alignment errors

### Changed
- Increased text contrast throughout (EnactOnSurfaceDim for secondary labels)
- R8 minification enabled for release builds
- Debug APK uses `.debug` application ID suffix — can coexist with release

## v1.0.0

- Initial release: BT Classic connection to Shimmer3 GSR+ and ExG units
- Live signal display and MPAndroidChart graph
- Basic CSV recording
- Settings screen with BT radio ID configuration
