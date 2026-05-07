package com.rfsat.shimmerenact.data.models

// ─── Sensor type definitions ────────────────────────────────────────────────

enum class SensorType(
    val displayName: String,
    val defaultBtSuffix: String,
    val modelId: String
) {
    GSR_PLUS("GSR+ Unit", "A096", "SR48-5-0"),
    EXG("EXG Unit", "A077", "SR47-6-0"),
    CUSTOM("Custom Sensor", "", "Custom")
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
//   • Temperature:       1–10 Hz

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
    ShimmerSignal("temp_c",    "Temperature", "°C",   0xFFFF8C00, -40.0, 85.0,     RATE_SLOW),
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

val CUSTOM_SIGNALS = listOf(
    ShimmerSignal("ch1", "Channel 1", "raw", 0xFF43AF81, rateConstraints = RATE_GENERIC),
    ShimmerSignal("ch2", "Channel 2", "raw", 0xFF86BA39, rateConstraints = RATE_GENERIC),
    ShimmerSignal("ch3", "Channel 3", "raw", 0xFFE07B39, rateConstraints = RATE_GENERIC),
    ShimmerSignal("ch4", "Channel 4", "raw", 0xFF39A8E0, rateConstraints = RATE_GENERIC)
)

fun signalsForType(type: SensorType): List<ShimmerSignal> = when (type) {
    SensorType.GSR_PLUS -> GSR_SIGNALS
    SensorType.EXG      -> EXG_SIGNALS
    SensorType.CUSTOM   -> CUSTOM_SIGNALS
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
