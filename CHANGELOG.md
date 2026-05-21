# ShimmerENACT Changelog

RFSAT Limited — ENACT Project (Horizon Europe Grant 101157151)

## v1.8.0

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
