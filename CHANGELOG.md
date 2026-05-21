# ShimmerENACT Changelog

RFSAT Limited — ENACT Project (Horizon Europe Grant 101157151)

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
