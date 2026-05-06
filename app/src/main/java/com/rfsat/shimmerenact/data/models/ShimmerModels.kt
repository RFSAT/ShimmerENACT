package com.rfsat.shimmerenact.data.models

import androidx.compose.ui.graphics.Color

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

// ─── Signal / parameter definitions ─────────────────────────────────────────

data class ShimmerSignal(
    val key: String,
    val displayName: String,
    val unit: String,
    val color: Long,           // ARGB stored as Long for serialisation
    val minRange: Double = -Double.MAX_VALUE,
    val maxRange: Double = Double.MAX_VALUE
)

val GSR_SIGNALS = listOf(
    ShimmerSignal("gsr_kohm",    "GSR",          "kΩ",   0xFF43AF81, 0.0, 2000.0),
    ShimmerSignal("ppg_mv",      "PPG",          "mV",   0xFF86BA39, -1000.0, 1000.0),
    ShimmerSignal("accel_x",     "Accel X",      "m/s²", 0xFFE07B39, -20.0, 20.0),
    ShimmerSignal("accel_y",     "Accel Y",      "m/s²", 0xFFE0B539, -20.0, 20.0),
    ShimmerSignal("accel_z",     "Accel Z",      "m/s²", 0xFF39A8E0, -20.0, 20.0),
    ShimmerSignal("gyro_x",      "Gyro X",       "°/s",  0xFFB039E0, -500.0, 500.0),
    ShimmerSignal("gyro_y",      "Gyro Y",       "°/s",  0xFFE039B0, -500.0, 500.0),
    ShimmerSignal("gyro_z",      "Gyro Z",       "°/s",  0xFF39E0B0, -500.0, 500.0),
    ShimmerSignal("mag_x",       "Mag X",        "µT",   0xFFE04040, -1000.0, 1000.0),
    ShimmerSignal("mag_y",       "Mag Y",        "µT",   0xFF40E040, -1000.0, 1000.0),
    ShimmerSignal("mag_z",       "Mag Z",        "µT",   0xFF4040E0, -1000.0, 1000.0),
    ShimmerSignal("temp_c",      "Temperature",  "°C",   0xFFFF8C00, -40.0, 85.0),
    ShimmerSignal("batt_mv",     "Battery",      "mV",   0xFF888888, 0.0, 4500.0)
)

val EXG_SIGNALS = listOf(
    ShimmerSignal("exg1_ch1",    "ExG1 Ch1 (ECG/EMG)", "mV",  0xFF43AF81, -3.0, 3.0),
    ShimmerSignal("exg1_ch2",    "ExG1 Ch2",     "mV",   0xFF86BA39, -3.0, 3.0),
    ShimmerSignal("exg2_ch1",    "ExG2 Ch1",     "mV",   0xFFE07B39, -3.0, 3.0),
    ShimmerSignal("exg2_ch2",    "ExG2 Ch2",     "mV",   0xFF39A8E0, -3.0, 3.0),
    ShimmerSignal("accel_x",     "Accel X",      "m/s²", 0xFFE0B539, -20.0, 20.0),
    ShimmerSignal("accel_y",     "Accel Y",      "m/s²", 0xFFB039E0, -20.0, 20.0),
    ShimmerSignal("accel_z",     "Accel Z",      "m/s²", 0xFFE039B0, -20.0, 20.0),
    ShimmerSignal("gyro_x",      "Gyro X",       "°/s",  0xFF39E0B0, -500.0, 500.0),
    ShimmerSignal("gyro_y",      "Gyro Y",       "°/s",  0xFFE04040, -500.0, 500.0),
    ShimmerSignal("gyro_z",      "Gyro Z",       "°/s",  0xFF40E040, -500.0, 500.0),
    ShimmerSignal("batt_mv",     "Battery",      "mV",   0xFF888888, 0.0, 4500.0)
)

val CUSTOM_SIGNALS = listOf(
    ShimmerSignal("ch1", "Channel 1", "raw", 0xFF43AF81),
    ShimmerSignal("ch2", "Channel 2", "raw", 0xFF86BA39),
    ShimmerSignal("ch3", "Channel 3", "raw", 0xFFE07B39),
    ShimmerSignal("ch4", "Channel 4", "raw", 0xFF39A8E0)
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
    val btRadioId: String,              // e.g. "A096"
    val samplingRateHz: Int = 51,
    val enabledSignals: Set<String>,    // keys from the signal list
    val customName: String = ""
) {
    val displayName: String get() = when (sensorType) {
        SensorType.CUSTOM -> customName.ifBlank { "Custom Sensor" }
        else -> "${sensorType.displayName} (${sensorType.modelId})"
    }

    val btNameFilter: String get() = "Shimmer3-$btRadioId"
}

// ─── Connection state ─────────────────────────────────────────────────────────

enum class ConnectionState {
    DISCONNECTED, SCANNING, CONNECTING, CONNECTED, ERROR
}

// ─── Recording state ──────────────────────────────────────────────────────────

data class RecordingState(
    val isRecording: Boolean = false,
    val startTimeMs: Long = 0L,
    val sampleCount: Long = 0L,
    val filePath: String = ""
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
    val recentSamples: List<ShimmerSample> = emptyList(),   // ring buffer for chart
    val recordingState: RecordingState = RecordingState(),
    val errorMessage: String? = null,
    val samplesPerSecond: Double = 0.0
)
