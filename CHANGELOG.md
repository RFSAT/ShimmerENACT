# ShimmerENACT Changelog

RFSAT Limited — ENACT Project (Horizon Europe Grant 101157151)

## v1.1.0 (current)

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
