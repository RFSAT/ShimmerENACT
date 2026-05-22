# ShimmerENACT Changelog

RFSAT Limited — ENACT Project (Horizon Europe Grant 101157151)

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
