package com.rfsat.shimmerenact.data.models

// ─── Sensor type definitions ────────────────────────────────────────────────

enum class SensorType(
    val displayName: String,
    val defaultBtSuffix: String,
    val modelId: String
) {
    GSR_PLUS("GSR+ Unit",     "A096", "SR48-5-0"),
    EXG("EXG Unit",           "A077", "SR47-6-0"),
    // IMU: base Shimmer3 unit (SR31) — onboard sensors only, no expansion board.
    // 9-DoF inertial (accel LN + WR, gyro, mag) + BMP280 pressure/temperature.
    IMU("IMU Unit",           "A080", "SR31"),
    // EMG: same SR47 ExG hardware as EXG, but configured for EMG mode.
    // Only Chip 1 of the ADS1292R is active; Chip 2 is disabled.
    // Default BT suffix matches EXG hardware — change in Settings if needed.
    EMG("EMG Unit",           "A077", "SR47-6-0-EMG"),
    // Ebio: SR59 expansion board — bioimpedance (respiratory) + ECG using both
    // ADS1292R chips simultaneously. Chip1 drives/measures ECG; Chip2 receives
    // the bioimpedance signal. Also has full IMU.
    EBIO("Ebio Unit",         "A078", "SR59"),
    // Bridge Amplifier+: SR37 expansion board — strain gauge / load cell interface.
    // Two bridge amplifier ADC output channels (high-gain + low-gain) plus a
    // resistance divider channel for skin-surface temperature. Full IMU included.
    BRIDGE_AMP("Bridge Amplifier+", "A079", "SR37"),
    // 200g IMU: SR31 base plus a high-g accelerometer (ADXL377 ±200g) connected
    // to three ADC channels. All standard IMU signals plus the high-g accel.
    IMU_200G("200g IMU",      "A081", "SR31-200G"),
    // PROTO3 Deluxe: SR50 expansion board — 4 analog input channels via 3.5mm
    // TRRS jacks plus full IMU. Used for custom analog sensor interfacing.
    PROTO3_DELUXE("PROTO3 Deluxe", "A082", "SR50"),
    CUSTOM("Custom Sensor",   "", "Custom")
}

// ─── Hardware rate constraints per signal group ───────────────────────────────
//
// Shimmer3 hardware limits:
//   • ADXL345 accel:     1–1600 Hz
//   • MPU9150 gyro:      4–8000 Hz (in practice firmware caps at 6000)
//   • LSM303 mag:        0.75–220 Hz (firmware rounds to nearest step)
//   • ADS1292 ExG:       125, 250, 500, 1000, 2000, 4000 Hz (ADC clock steps)
//   • GSR / PPG (ADC):   1–6000 Hz (firmware ADC)
//   • Battery:           1–10 Hz (slow, no point faster)
//   • Battery ADC

data class RateConstraints(
    val minHz: Int,
    val maxHz: Int,
    // null = any integer; non-null = must be one of these exact values
    val allowedValues: List<Int>? = null
) {
    fun clamp(hz: Int): Int {
        val clamped = hz.coerceIn(minHz, maxHz)
        return if (allowedValues != null) closestAllowed(clamped) else clamped
    }

    private fun closestAllowed(hz: Int): Int =
        allowedValues!!.minByOrNull { kotlin.math.abs(it - hz) } ?: hz
}

val RATE_ACCEL    = RateConstraints(1, 1600)
val RATE_GYRO     = RateConstraints(4, 6000)
val RATE_MAG      = RateConstraints(1, 220)
val RATE_EXG      = RateConstraints(125, 4000, listOf(125, 250, 500, 1000, 2000, 4000))
val RATE_GSR_PPG  = RateConstraints(1, 6000)
val RATE_SLOW     = RateConstraints(1, 10)
val RATE_GENERIC  = RateConstraints(1, 6000)
// BMP280 pressure: max ODR ≈ 157 Hz; practical use ≤ 40 Hz
val RATE_PRESSURE = RateConstraints(1, 40)

const val DEFAULT_RATE_HZ = 250

// ─── Signal / parameter definitions ─────────────────────────────────────────

data class ShimmerSignal(
    val key: String,
    val displayName: String,
    val unit: String,
    val color: Long,                      // ARGB stored as Long for serialisation
    val minRange: Double = -Double.MAX_VALUE,
    val maxRange: Double = Double.MAX_VALUE,
    val rateConstraints: RateConstraints = RATE_GENERIC
)

val GSR_SIGNALS = listOf(
    ShimmerSignal("gsr_kohm",  "GSR",         "kΩ",   0xFF43AF81, 0.0, 2000.0,     RATE_GSR_PPG),
    ShimmerSignal("ppg_mv",    "PPG",         "mV",   0xFF86BA39, -1000.0, 1000.0,  RATE_GSR_PPG),
    ShimmerSignal("accel_x",   "Accel X",     "m/s²", 0xFFE07B39, -20.0, 20.0,     RATE_ACCEL),
    ShimmerSignal("accel_y",   "Accel Y",     "m/s²", 0xFFE0B539, -20.0, 20.0,     RATE_ACCEL),
    ShimmerSignal("accel_z",   "Accel Z",     "m/s²", 0xFF39A8E0, -20.0, 20.0,     RATE_ACCEL),
    ShimmerSignal("gyro_x",    "Gyro X",      "°/s",  0xFFB039E0, -500.0, 500.0,   RATE_GYRO),
    ShimmerSignal("gyro_y",    "Gyro Y",      "°/s",  0xFFE039B0, -500.0, 500.0,   RATE_GYRO),
    ShimmerSignal("gyro_z",    "Gyro Z",      "°/s",  0xFF39E0B0, -500.0, 500.0,   RATE_GYRO),
    ShimmerSignal("mag_x",     "Mag X",       "µT",   0xFFE04040, -1000.0, 1000.0, RATE_MAG),
    ShimmerSignal("mag_y",     "Mag Y",       "µT",   0xFF40E040, -1000.0, 1000.0, RATE_MAG),
    ShimmerSignal("mag_z",     "Mag Z",       "µT",   0xFF4040E0, -1000.0, 1000.0, RATE_MAG),
    ShimmerSignal("batt_mv",   "Battery",     "mV",   0xFF888888, 0.0, 4500.0,     RATE_SLOW)
)

val EXG_SIGNALS = listOf(
    ShimmerSignal("exg1_ch1",  "ExG1 Ch1 (ECG/EMG)", "mV", 0xFF43AF81, -3.0, 3.0, RATE_EXG),
    ShimmerSignal("exg1_ch2",  "ExG1 Ch2",    "mV",  0xFF86BA39, -3.0, 3.0,       RATE_EXG),
    ShimmerSignal("exg2_ch1",  "ExG2 Ch1",    "mV",  0xFFE07B39, -3.0, 3.0,       RATE_EXG),
    ShimmerSignal("exg2_ch2",  "ExG2 Ch2",    "mV",  0xFF39A8E0, -3.0, 3.0,       RATE_EXG),
    ShimmerSignal("accel_x",   "Accel X",     "m/s²",0xFFE0B539, -20.0, 20.0,     RATE_ACCEL),
    ShimmerSignal("accel_y",   "Accel Y",     "m/s²",0xFFB039E0, -20.0, 20.0,     RATE_ACCEL),
    ShimmerSignal("accel_z",   "Accel Z",     "m/s²",0xFFE039B0, -20.0, 20.0,     RATE_ACCEL),
    ShimmerSignal("gyro_x",    "Gyro X",      "°/s", 0xFF39E0B0, -500.0, 500.0,   RATE_GYRO),
    ShimmerSignal("gyro_y",    "Gyro Y",      "°/s", 0xFFE04040, -500.0, 500.0,   RATE_GYRO),
    ShimmerSignal("gyro_z",    "Gyro Z",      "°/s", 0xFF40E040, -500.0, 500.0,   RATE_GYRO),
    ShimmerSignal("batt_mv",   "Battery",     "mV",  0xFF888888, 0.0, 4500.0,     RATE_SLOW)
)

// IMU Unit — Shimmer3 base (SR31): onboard 9-DoF IMU + BMP280
// Low-Noise Accel (ADXL345/KXTC9) + Wide-Range Accel (LSM303AHTR)
// + Gyro (MPU9250) + Mag (LSM303/MPU9250) + BMP280 pressure
val IMU_SIGNALS = listOf(
    ShimmerSignal("accel_ln_x", "Accel LN X",   "m/s²", 0xFFE07B39, -20.0,   20.0,     RATE_ACCEL),
    ShimmerSignal("accel_ln_y", "Accel LN Y",   "m/s²", 0xFFE0B539, -20.0,   20.0,     RATE_ACCEL),
    ShimmerSignal("accel_ln_z", "Accel LN Z",   "m/s²", 0xFF39A8E0, -20.0,   20.0,     RATE_ACCEL),
    ShimmerSignal("accel_wr_x", "Accel WR X",   "m/s²", 0xFFB039E0, -156.9,  156.9,    RATE_ACCEL),
    ShimmerSignal("accel_wr_y", "Accel WR Y",   "m/s²", 0xFFE039B0, -156.9,  156.9,    RATE_ACCEL),
    ShimmerSignal("accel_wr_z", "Accel WR Z",   "m/s²", 0xFF39E0B0, -156.9,  156.9,    RATE_ACCEL),
    ShimmerSignal("gyro_x",     "Gyro X",       "°/s",  0xFF43AF81, -500.0,  500.0,    RATE_GYRO),
    ShimmerSignal("gyro_y",     "Gyro Y",       "°/s",  0xFF86BA39, -500.0,  500.0,    RATE_GYRO),
    ShimmerSignal("gyro_z",     "Gyro Z",       "°/s",  0xFFAF4381, -500.0,  500.0,    RATE_GYRO),
    ShimmerSignal("mag_x",      "Mag X",        "µT",   0xFFE04040, -1000.0, 1000.0,   RATE_MAG),
    ShimmerSignal("mag_y",      "Mag Y",        "µT",   0xFF40E040, -1000.0, 1000.0,   RATE_MAG),
    ShimmerSignal("mag_z",      "Mag Z",        "µT",   0xFF4040E0, -1000.0, 1000.0,   RATE_MAG),
    ShimmerSignal("pressure_pa","Pressure",     "Pa",   0xFFAAAAAA, 30000.0, 110000.0, RATE_PRESSURE),
    ShimmerSignal("temp_c",     "Temperature",  "°C",   0xFF888888, -40.0,   85.0,     RATE_PRESSURE),
    ShimmerSignal("batt_mv",    "Battery",      "mV",   0xFF555555, 0.0,     4500.0,   RATE_SLOW)
)

// EMG Unit — SR47-6-0 ExG hardware in EMG configuration
// Only Chip 1 (ADS1292R #1) is active: Ch1 = EMG differential signal,
// Ch2 = reference/ground electrode. Chip 2 is disabled.
// IMU sensors (accel, gyro) are also present on the base board.
val EMG_SIGNALS = listOf(
    ShimmerSignal("emg_ch1",  "EMG Ch1",       "µV",   0xFF43AF81, -5000.0, 5000.0,   RATE_EXG),
    ShimmerSignal("emg_ref",  "EMG Reference", "µV",   0xFF86BA39, -5000.0, 5000.0,   RATE_EXG),
    ShimmerSignal("accel_x",  "Accel X",       "m/s²", 0xFFE07B39, -20.0,   20.0,     RATE_ACCEL),
    ShimmerSignal("accel_y",  "Accel Y",       "m/s²", 0xFFE0B539, -20.0,   20.0,     RATE_ACCEL),
    ShimmerSignal("accel_z",  "Accel Z",       "m/s²", 0xFF39A8E0, -20.0,   20.0,     RATE_ACCEL),
    ShimmerSignal("gyro_x",   "Gyro X",        "°/s",  0xFFB039E0, -500.0,  500.0,    RATE_GYRO),
    ShimmerSignal("gyro_y",   "Gyro Y",        "°/s",  0xFFE039B0, -500.0,  500.0,    RATE_GYRO),
    ShimmerSignal("gyro_z",   "Gyro Z",        "°/s",  0xFF39E0B0, -500.0,  500.0,    RATE_GYRO),
    ShimmerSignal("batt_mv",  "Battery",       "mV",   0xFF888888, 0.0,     4500.0,   RATE_SLOW)
)

// Ebio Unit — SR59: ECG (Chip1) + Bioimpedance/Respiration (Chip2) + IMU
// Both ADS1292R chips active. Chip1 Ch1 = ECG lead-I equivalent, Ch2 = driven right leg.
// Chip2 Ch1 = bioimpedance voltage, Ch2 = reference. Signals in mV (same calibration as EXG).
val EBIO_SIGNALS = listOf(
    ShimmerSignal("ecg_ch1",    "ECG Ch1",          "mV",  0xFF43AF81, -3.0,    3.0,     RATE_EXG),
    ShimmerSignal("ecg_ch2",    "ECG Ch2 (RLD)",    "mV",  0xFF86BA39, -3.0,    3.0,     RATE_EXG),
    ShimmerSignal("bioz_ch1",   "BioZ Ch1",         "mV",  0xFFE07B39, -3.0,    3.0,     RATE_EXG),
    ShimmerSignal("bioz_ch2",   "BioZ Ch2 (Ref)",   "mV",  0xFF39A8E0, -3.0,    3.0,     RATE_EXG),
    ShimmerSignal("accel_x",    "Accel X",          "m/s²",0xFFE0B539, -20.0,   20.0,    RATE_ACCEL),
    ShimmerSignal("accel_y",    "Accel Y",          "m/s²",0xFFB039E0, -20.0,   20.0,    RATE_ACCEL),
    ShimmerSignal("accel_z",    "Accel Z",          "m/s²",0xFF39E0B0, -20.0,   20.0,    RATE_ACCEL),
    ShimmerSignal("gyro_x",     "Gyro X",           "°/s", 0xFFE04040, -500.0,  500.0,   RATE_GYRO),
    ShimmerSignal("gyro_y",     "Gyro Y",           "°/s", 0xFF40E040, -500.0,  500.0,   RATE_GYRO),
    ShimmerSignal("gyro_z",     "Gyro Z",           "°/s", 0xFF4040E0, -500.0,  500.0,   RATE_GYRO),
    ShimmerSignal("batt_mv",    "Battery",          "mV",  0xFF888888, 0.0,     4500.0,  RATE_SLOW)
)

// Bridge Amplifier+ — SR37: strain gauge / load cell + skin temperature + IMU
// bridge_high: high-gain amplifier output (3× additional gain vs low, for unipolar load cells)
// bridge_low:  low-gain amplifier output  (for bipolar inputs / strain gauges)
// skin_temp_kohm: resistance divider input mapped to kΩ (raw ADC reading)
// All three read via internal ADC channels; units are raw ADC counts until user applies
// their own load-cell calibration. Ranges cover the 12-bit ADC full-scale.
val BRIDGE_AMP_SIGNALS = listOf(
    ShimmerSignal("bridge_high",   "Bridge High Gain", "raw", 0xFF43AF81, 0.0, 4095.0, RATE_GENERIC),
    ShimmerSignal("bridge_low",    "Bridge Low Gain",  "raw", 0xFF86BA39, 0.0, 4095.0, RATE_GENERIC),
    ShimmerSignal("skin_temp_kohm","Skin Temp Res",    "kΩ",  0xFFE07B39, 0.0, 2000.0, RATE_SLOW),
    ShimmerSignal("accel_ln_x",   "Accel LN X",       "m/s²",0xFF39A8E0, -20.0, 20.0,  RATE_ACCEL),
    ShimmerSignal("accel_ln_y",   "Accel LN Y",       "m/s²",0xFFE0B539, -20.0, 20.0,  RATE_ACCEL),
    ShimmerSignal("accel_ln_z",   "Accel LN Z",       "m/s²",0xFFB039E0, -20.0, 20.0,  RATE_ACCEL),
    ShimmerSignal("gyro_x",       "Gyro X",           "°/s", 0xFF39E0B0, -500.0, 500.0, RATE_GYRO),
    ShimmerSignal("gyro_y",       "Gyro Y",           "°/s", 0xFFE04040, -500.0, 500.0, RATE_GYRO),
    ShimmerSignal("gyro_z",       "Gyro Z",           "°/s", 0xFF40E040, -500.0, 500.0, RATE_GYRO),
    ShimmerSignal("batt_mv",      "Battery",          "mV",  0xFF888888, 0.0, 4500.0,   RATE_SLOW)
)

// 200g IMU — SR31 base + ADXL377 high-g accel (±200g, analog, 3 ADC channels)
// The ADXL377 is ratiometric: 0g = Vcc/2, ±200g = 0V/Vcc.
// Calibration: accel_hg = (raw - 2048) × (200.0 × 9.81 / 2048) m/s² (approximate)
// Standard 9-DoF IMU sensors also present (same as IMU_SIGNALS).
val IMU_200G_SIGNALS = listOf(
    ShimmerSignal("accel_hg_x", "Accel HG X",   "m/s²", 0xFF43AF81, -1962.0, 1962.0, RATE_ACCEL),
    ShimmerSignal("accel_hg_y", "Accel HG Y",   "m/s²", 0xFF86BA39, -1962.0, 1962.0, RATE_ACCEL),
    ShimmerSignal("accel_hg_z", "Accel HG Z",   "m/s²", 0xFFE07B39, -1962.0, 1962.0, RATE_ACCEL),
    ShimmerSignal("accel_ln_x", "Accel LN X",   "m/s²", 0xFF39A8E0, -20.0,   20.0,   RATE_ACCEL),
    ShimmerSignal("accel_ln_y", "Accel LN Y",   "m/s²", 0xFFE0B539, -20.0,   20.0,   RATE_ACCEL),
    ShimmerSignal("accel_ln_z", "Accel LN Z",   "m/s²", 0xFFB039E0, -20.0,   20.0,   RATE_ACCEL),
    ShimmerSignal("accel_wr_x", "Accel WR X",   "m/s²", 0xFF39E0B0, -156.9,  156.9,  RATE_ACCEL),
    ShimmerSignal("accel_wr_y", "Accel WR Y",   "m/s²", 0xFFE04040, -156.9,  156.9,  RATE_ACCEL),
    ShimmerSignal("accel_wr_z", "Accel WR Z",   "m/s²", 0xFF40E040, -156.9,  156.9,  RATE_ACCEL),
    ShimmerSignal("gyro_x",     "Gyro X",       "°/s",  0xFF4040E0, -500.0,  500.0,  RATE_GYRO),
    ShimmerSignal("gyro_y",     "Gyro Y",       "°/s",  0xFFAF4381, -500.0,  500.0,  RATE_GYRO),
    ShimmerSignal("gyro_z",     "Gyro Z",       "°/s",  0xFF43AF81, -500.0,  500.0,  RATE_GYRO),
    ShimmerSignal("mag_x",      "Mag X",        "µT",   0xFF86BA39, -1000.0, 1000.0, RATE_MAG),
    ShimmerSignal("mag_y",      "Mag Y",        "µT",   0xFFE07B39, -1000.0, 1000.0, RATE_MAG),
    ShimmerSignal("mag_z",      "Mag Z",        "µT",   0xFF39A8E0, -1000.0, 1000.0, RATE_MAG),
    ShimmerSignal("batt_mv",    "Battery",      "mV",   0xFF888888, 0.0,     4500.0, RATE_SLOW)
)

// PROTO3 Deluxe — SR50: 4 analog input channels via 3.5mm TRRS jacks + full IMU
// Channels connect to internal ADC inputs; units are raw ADC counts (0–4095).
// User applies their own sensor-specific calibration after data collection.
val PROTO3_DELUXE_SIGNALS = listOf(
    ShimmerSignal("analog_ch1", "Analog Ch1",  "raw", 0xFF43AF81, 0.0, 4095.0, RATE_GENERIC),
    ShimmerSignal("analog_ch2", "Analog Ch2",  "raw", 0xFF86BA39, 0.0, 4095.0, RATE_GENERIC),
    ShimmerSignal("analog_ch3", "Analog Ch3",  "raw", 0xFFE07B39, 0.0, 4095.0, RATE_GENERIC),
    ShimmerSignal("analog_ch4", "Analog Ch4",  "raw", 0xFF39A8E0, 0.0, 4095.0, RATE_GENERIC),
    ShimmerSignal("accel_ln_x", "Accel LN X",  "m/s²",0xFFE0B539, -20.0,  20.0,  RATE_ACCEL),
    ShimmerSignal("accel_ln_y", "Accel LN Y",  "m/s²",0xFFB039E0, -20.0,  20.0,  RATE_ACCEL),
    ShimmerSignal("accel_ln_z", "Accel LN Z",  "m/s²",0xFF39E0B0, -20.0,  20.0,  RATE_ACCEL),
    ShimmerSignal("gyro_x",     "Gyro X",      "°/s", 0xFFE04040, -500.0, 500.0, RATE_GYRO),
    ShimmerSignal("gyro_y",     "Gyro Y",      "°/s", 0xFF40E040, -500.0, 500.0, RATE_GYRO),
    ShimmerSignal("gyro_z",     "Gyro Z",      "°/s", 0xFF4040E0, -500.0, 500.0, RATE_GYRO),
    ShimmerSignal("batt_mv",    "Battery",     "mV",  0xFF888888, 0.0, 4500.0,   RATE_SLOW)
)

val CUSTOM_SIGNALS = listOf(
    ShimmerSignal("ch1", "Channel 1", "raw", 0xFF43AF81, rateConstraints = RATE_GENERIC),
    ShimmerSignal("ch2", "Channel 2", "raw", 0xFF86BA39, rateConstraints = RATE_GENERIC),
    ShimmerSignal("ch3", "Channel 3", "raw", 0xFFE07B39, rateConstraints = RATE_GENERIC),
    ShimmerSignal("ch4", "Channel 4", "raw", 0xFF39A8E0, rateConstraints = RATE_GENERIC)
)

fun signalsForType(type: SensorType): List<ShimmerSignal> = when (type) {
    SensorType.GSR_PLUS      -> GSR_SIGNALS
    SensorType.EXG           -> EXG_SIGNALS
    SensorType.IMU           -> IMU_SIGNALS
    SensorType.EMG           -> EMG_SIGNALS
    SensorType.EBIO          -> EBIO_SIGNALS
    SensorType.BRIDGE_AMP    -> BRIDGE_AMP_SIGNALS
    SensorType.IMU_200G      -> IMU_200G_SIGNALS
    SensorType.PROTO3_DELUXE -> PROTO3_DELUXE_SIGNALS
    SensorType.CUSTOM        -> CUSTOM_SIGNALS
}

// ─── A single timestamped sample ─────────────────────────────────────────────

data class ShimmerSample(
    val timestampMs: Long,
    val values: Map<String, Double>     // key → calibrated value
)

// ─── Sensor configuration stored per device ──────────────────────────────────

data class SensorConfig(
    val sensorType: SensorType,
    val btRadioId: String,
    // Global hardware rate: Shimmer3 ADC runs at a single rate for all channels.
    // This is sent to the device via CMD_SET_SAMPLING_RATE.
    // Range 1–6000 Hz; default 250 Hz.
    val hardwareRateHz: Int = DEFAULT_RATE_HZ,
    // Signals shown in the Live view and chart
    val enabledSignals: Set<String>,
    // Signals written to CSV files during recording (independent of display selection)
    // Default = all signals for this sensor type
    val recordingSignals: Set<String> = emptySet(),   // emptySet() means "use all signals"
    // Per-signal effective rate in Hz.  Must be ≤ hardwareRateHz.
    // Software decimation is applied in the stream processor.
    // Signals absent from this map inherit hardwareRateHz.
    val signalRatesHz: Map<String, Int> = emptyMap(),
    val customName: String = ""
) {
    val displayName: String get() = when (sensorType) {
        SensorType.CUSTOM -> customName.ifBlank { "Custom Sensor" }
        else -> "${sensorType.displayName} (${sensorType.modelId})"
    }

    val btNameFilter: String get() = "Shimmer3-$btRadioId"

    /** Signals to record — defaults to all signals if recordingSignals is empty. */
    fun resolvedRecordingSignals(allSignals: List<ShimmerSignal>): Set<String> =
        if (recordingSignals.isEmpty()) allSignals.map { it.key }.toSet()
        else recordingSignals

    /** Effective rate for a given signal key, clamped to hardware rate and constraints. */
    fun effectiveRateHz(key: String, constraints: RateConstraints): Int {
        val requested = signalRatesHz[key] ?: hardwareRateHz
        val capped = requested.coerceAtMost(hardwareRateHz)
        return constraints.clamp(capped)
    }

    /** Return a new config with per-signal rate updated and clamped. */
    fun withSignalRate(key: String, hz: Int, constraints: RateConstraints): SensorConfig {
        val clamped = constraints.clamp(hz.coerceIn(1, hardwareRateHz))
        return copy(signalRatesHz = signalRatesHz + (key to clamped))
    }

    /** Return config with hardware rate updated; re-clamp all per-signal rates. */
    fun withHardwareRate(hz: Int): SensorConfig {
        val newHw = hz.coerceIn(1, 6000)
        val signals = signalsForType(sensorType)
        val reclamped = signalRatesHz.mapValues { (key, rate) ->
            val constraints = signals.find { it.key == key }?.rateConstraints ?: RATE_GENERIC
            constraints.clamp(rate.coerceAtMost(newHw))
        }
        return copy(hardwareRateHz = newHw, signalRatesHz = reclamped)
    }
}

// ─── Connection state ─────────────────────────────────────────────────────────

enum class ConnectionState {
    DISCONNECTED, SCANNING, CONNECTING, CONNECTED, ERROR
}

// ─── Recording state ──────────────────────────────────────────────────────────

data class RecordingState(
    val isRecording: Boolean = false,
    val startTimeMs: Long = 0L,
    val sampleCount: Long = 0L,      // total hardware samples seen since record start
    val rowsWritten: Long = 0L,      // rows actually written across all files (after decimation)
    val fileCount: Int = 0,
    val sessionId: String = ""
)

// ─── Bluetooth device info ────────────────────────────────────────────────────

data class BtDeviceInfo(
    val name: String,
    val address: String,
    val rssi: Int = 0
)

// ─── App-level UI state assembled by ViewModel ───────────────────────────────

data class SensorUiState(
    val config: SensorConfig,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val latestSample: ShimmerSample? = null,
    val recentSamples: List<ShimmerSample> = emptyList(),
    val recordingState: RecordingState = RecordingState(),
    val errorMessage: String? = null,
    val samplesPerSecond: Double = 0.0
)
