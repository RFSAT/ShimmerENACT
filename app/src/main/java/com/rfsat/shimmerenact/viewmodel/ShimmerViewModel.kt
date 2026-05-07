package com.rfsat.shimmerenact.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rfsat.shimmerenact.data.bluetooth.ShimmerBluetoothManager
import com.rfsat.shimmerenact.data.bluetooth.ShimmerProtocol
import com.rfsat.shimmerenact.data.models.*
import com.rfsat.shimmerenact.data.repository.AppLog
import com.rfsat.shimmerenact.data.repository.PreferencesRepository
import com.rfsat.shimmerenact.data.repository.RecordingFile
import com.rfsat.shimmerenact.data.repository.RecordingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.LinkedList

class ShimmerViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    val btManager = ShimmerBluetoothManager(context)
    val prefsRepo = PreferencesRepository(context)
    val recordingRepo = RecordingRepository(context)

    // ─── Sensor configurations ────────────────────────────────────────────────
    private val _gsrConfig = MutableStateFlow(
        SensorConfig(
            sensorType = SensorType.GSR_PLUS,
            btRadioId = SensorType.GSR_PLUS.defaultBtSuffix,
            hardwareRateHz = DEFAULT_RATE_HZ,
            enabledSignals = GSR_SIGNALS.map { it.key }.toSet()
        )
    )
    private val _exgConfig = MutableStateFlow(
        SensorConfig(
            sensorType = SensorType.EXG,
            btRadioId = SensorType.EXG.defaultBtSuffix,
            hardwareRateHz = DEFAULT_RATE_HZ,
            enabledSignals = EXG_SIGNALS.map { it.key }.toSet()
        )
    )
    private val _customConfig = MutableStateFlow(
        SensorConfig(
            sensorType = SensorType.CUSTOM,
            btRadioId = "",
            hardwareRateHz = DEFAULT_RATE_HZ,
            enabledSignals = CUSTOM_SIGNALS.map { it.key }.toSet(),
            customName = "Custom Sensor"
        )
    )

    private val _activeSensorType = MutableStateFlow(SensorType.GSR_PLUS)
    val activeSensorType: StateFlow<SensorType> = _activeSensorType.asStateFlow()

    val activeConfig: StateFlow<SensorConfig> = combine(
        _activeSensorType, _gsrConfig, _exgConfig, _customConfig
    ) { type, gsr, exg, custom ->
        when (type) {
            SensorType.GSR_PLUS -> gsr
            SensorType.EXG      -> exg
            SensorType.CUSTOM   -> custom
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly,
        SensorConfig(SensorType.GSR_PLUS, SensorType.GSR_PLUS.defaultBtSuffix,
            enabledSignals = GSR_SIGNALS.map { it.key }.toSet()))

    // ─── UI State ─────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(
        SensorUiState(config = activeConfig.value)
    )
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()

    // ─── Signals supported by the connected device (from inquiry) ────────────
    // Derived from the sensorBitmapFlow; the BT manager updates this after inquiry.
    // Empty set = not yet connected (treat as "all supported" in the UI).
    val supportedSignalKeys: StateFlow<Set<String>> =
        btManager.sensorBitmapFlow.map { bitmap ->
            if (bitmap.all { it == 0 }) return@map emptySet()
            val b0 = bitmap[0]; val b1 = bitmap[1]; val b2 = bitmap[2]
            val keys = mutableSetOf<String>()
            if (b0 and ShimmerProtocol.SENSOR_A_ACCEL != 0 ||
                b1 and ShimmerProtocol.SENSOR_b1_D_ACCEL != 0)
                keys += listOf("accel_x", "accel_y", "accel_z")
            if (b0 and ShimmerProtocol.SENSOR_GYRO != 0)
                keys += listOf("gyro_x", "gyro_y", "gyro_z")
            if (b0 and ShimmerProtocol.SENSOR_MAG != 0)
                keys += listOf("mag_x", "mag_y", "mag_z")
            if (b0 and ShimmerProtocol.SENSOR_EXG1_24BIT != 0 ||
                b2 and ShimmerProtocol.SENSOR_EXG1_16BIT != 0)
                keys += listOf("exg1_ch1", "exg1_ch2")
            if (b0 and ShimmerProtocol.SENSOR_EXG2_24BIT != 0 ||
                b2 and ShimmerProtocol.SENSOR_EXG2_16BIT != 0)
                keys += listOf("exg2_ch1", "exg2_ch2")
            if (b0 and ShimmerProtocol.SENSOR_GSR           != 0) keys += "gsr_kohm"
            if (b0 and ShimmerProtocol.SENSOR_EXP_BOARD_A0  != 0) keys += "ppg_mv"
            if (b0 and ShimmerProtocol.SENSOR_EXP_BOARD_A7  != 0) keys += "ch_a7"
            if (b1 and ShimmerProtocol.SENSOR_b1_VBATT      != 0) keys += "batt_mv"
            // Intersect with signals defined for this sensor type so we never
            // show an "available" badge for a channel the app doesn't model
            val defined = signalsForType(_activeSensorType.value).map { it.key }.toSet()
            keys.intersect(defined)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
        emit(btManager.getPairedDevices())
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Chart ring buffer ────────────────────────────────────────────────────
    private val sampleBuffer: LinkedList<ShimmerSample> = LinkedList()

    // ─── Recording state ──────────────────────────────────────────────────────
    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    // ─── Recording sessions list ──────────────────────────────────────────────
    private val _sessions = MutableStateFlow<List<com.rfsat.shimmerenact.data.repository.RecordingSession>>(emptyList())
    val sessions: StateFlow<List<com.rfsat.shimmerenact.data.repository.RecordingSession>> = _sessions.asStateFlow()

    // ─── Samples-per-second meter ─────────────────────────────────────────────
    private var lastSpsWindowStart = System.currentTimeMillis()
    private var spsCount = 0

    init {
        observeConnectionState()
        observeSamples()
        observeErrors()
        loadPrefs()
        loadSessions()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            btManager.connectionState.collect { state ->
                AppLog.i("VM", "Connection state → $state")
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    private fun observeSamples() {
        viewModelScope.launch {
            btManager.sampleFlow.collect { sample ->
                // Update SPS
                spsCount++
                val now = System.currentTimeMillis()
                val elapsed = now - lastSpsWindowStart
                val sps = if (elapsed >= 1000L) {
                    val rate = spsCount * 1000.0 / elapsed
                    spsCount = 0; lastSpsWindowStart = now; rate
                } else _uiState.value.samplesPerSecond

                // Ring buffer for chart
                sampleBuffer.addLast(sample)
                while (sampleBuffer.size > ShimmerProtocol.CHART_BUFFER_SIZE) {
                    sampleBuffer.removeFirst()
                }

                _uiState.update { it.copy(
                    latestSample = sample,
                    recentSamples = sampleBuffer.toList(),
                    samplesPerSecond = sps
                )}

                // Write to per-signal CSV files if recording
                if (recordingRepo.isRecording) {
                    recordingRepo.writeSampleSync(sample)
                    _recordingState.update { rs ->
                        rs.copy(
                            sampleCount = rs.sampleCount + 1,
                            rowsWritten = recordingRepo.totalSamplesWritten
                        )
                    }
                }
            }
        }
    }

    private fun observeErrors() {
        viewModelScope.launch {
            btManager.errorFlow.collect { msg ->
                AppLog.e("VM", msg)
                _uiState.update { it.copy(errorMessage = msg) }
            }
        }
    }

    private fun loadPrefs() {
        viewModelScope.launch {
            prefsRepo.gsrBtId.collect { id -> _gsrConfig.update { it.copy(btRadioId = id) } }
        }
        viewModelScope.launch {
            prefsRepo.exgBtId.collect { id -> _exgConfig.update { it.copy(btRadioId = id) } }
        }
    }

    private fun loadSessions() {
        viewModelScope.launch {
            _sessions.value = recordingRepo.listSessions()
        }
    }

    // ─── Public actions ───────────────────────────────────────────────────────

    fun selectSensorType(type: SensorType) {
        _activeSensorType.value = type
        _uiState.update { it.copy(config = activeConfig.value, errorMessage = null) }
    }

    fun updateBtRadioId(type: SensorType, id: String) {
        viewModelScope.launch {
            when (type) {
                SensorType.GSR_PLUS -> { _gsrConfig.update { it.copy(btRadioId = id) }; prefsRepo.saveGsrBtId(id) }
                SensorType.EXG      -> { _exgConfig.update { it.copy(btRadioId = id) }; prefsRepo.saveExgBtId(id) }
                SensorType.CUSTOM   -> { _customConfig.update { it.copy(btRadioId = id) }; prefsRepo.saveCustomBtId(id) }
            }
        }
    }

    /** Update the global hardware sampling rate for a sensor type (1–6000 Hz). */
    fun updateHardwareRate(type: SensorType, hz: Int) {
        val clamped = hz.coerceIn(1, 6000)
        AppLog.i("VM", "Hardware rate → $clamped Hz [${type.name}]")
        when (type) {
            SensorType.GSR_PLUS -> _gsrConfig.update { it.withHardwareRate(clamped) }
            SensorType.EXG      -> _exgConfig.update { it.withHardwareRate(clamped) }
            SensorType.CUSTOM   -> _customConfig.update { it.withHardwareRate(clamped) }
        }
    }

    /** Update the per-signal effective (decimated) rate for the active sensor. */
    fun updateSignalRate(signalKey: String, hz: Int) {
        val type = _activeSensorType.value
        val signals = signalsForType(type)
        val constraints = signals.find { it.key == signalKey }?.rateConstraints ?: RATE_GENERIC
        AppLog.i("VM", "Signal rate $signalKey → $hz Hz [${type.name}]")
        when (type) {
            SensorType.GSR_PLUS -> _gsrConfig.update { it.withSignalRate(signalKey, hz, constraints) }
            SensorType.EXG      -> _exgConfig.update { it.withSignalRate(signalKey, hz, constraints) }
            SensorType.CUSTOM   -> _customConfig.update { it.withSignalRate(signalKey, hz, constraints) }
        }
    }

    /** Reset all per-signal rates to the hardware rate (remove decimation). */
    fun resetAllSignalRates(type: SensorType) {
        AppLog.i("VM", "Resetting all signal rates to hardware rate [${type.name}]")
        when (type) {
            SensorType.GSR_PLUS -> _gsrConfig.update { it.copy(signalRatesHz = emptyMap()) }
            SensorType.EXG      -> _exgConfig.update { it.copy(signalRatesHz = emptyMap()) }
            SensorType.CUSTOM   -> _customConfig.update { it.copy(signalRatesHz = emptyMap()) }
        }
    }

    fun updateCustomName(name: String) {
        _customConfig.update { it.copy(customName = name) }
        viewModelScope.launch { prefsRepo.saveCustomName(name) }
    }

    fun connectToDevice(address: String) {
        AppLog.i("VM", "connectToDevice($address) — sensor: ${activeConfig.value.displayName}")
        viewModelScope.launch {
            prefsRepo.saveLastAddress(address)
            btManager.connect(address, activeConfig.value)
        }
    }

    fun disconnect() = btManager.disconnect()

    fun startRecording() {
        viewModelScope.launch {
            val config = activeConfig.value
            val allSignals = signalsForType(config.sensorType)
            val recKeys = config.resolvedRecordingSignals(allSignals)
            val recSignals = allSignals.filter { it.key in recKeys }

            AppLog.i("REC", "Starting — ${recSignals.size} signals at hw=${config.hardwareRateHz}Hz")
            recSignals.forEach { sig ->
                val rate = config.effectiveRateHz(sig.key, sig.rateConstraints)
                AppLog.d("REC", "  ${sig.key}: $rate Hz")
            }

            val result = recordingRepo.startRecording(
                deviceName = config.displayName,
                signals = recSignals,
                signalRatesHz = config.signalRatesHz,
                hardwareHz = config.hardwareRateHz
            )
            result.onSuccess { paths ->
                AppLog.ok("REC", "Recording started — ${paths.size} files")
                _recordingState.value = RecordingState(
                    isRecording = true,
                    startTimeMs = System.currentTimeMillis(),
                    fileCount = paths.size,
                    sessionId = recordingRepo.currentSessionId
                )
            }.onFailure { e ->
                AppLog.e("REC", "Failed to start: ${e.message}")
                _uiState.update { it.copy(errorMessage = "Recording failed: ${e.message}") }
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            val result = recordingRepo.stopRecording()
            result.onSuccess { session ->
                AppLog.ok("REC", "Stopped — ${session.files.size} files, ${recordingRepo.totalSamplesWritten} rows")
            }.onFailure { e ->
                AppLog.e("REC", "Stop error: ${e.message}")
            }
            _recordingState.value = RecordingState()
            loadSessions()
        }
    }

    /** Toggle a signal key in the recording selection for the active sensor type. */
    fun toggleRecordingSignal(key: String) {
        val type = _activeSensorType.value
        val allSignals = signalsForType(type)
        fun toggle(config: SensorConfig): SensorConfig {
            // If empty → all are selected; expand to explicit full set before toggling
            val current = config.resolvedRecordingSignals(allSignals)
            val updated = if (key in current) current - key else current + key
            // If result equals full set, store as empty (meaning "all")
            val stored = if (updated == allSignals.map { it.key }.toSet()) emptySet() else updated
            return config.copy(recordingSignals = stored)
        }
        when (type) {
            SensorType.GSR_PLUS -> _gsrConfig.update { toggle(it) }
            SensorType.EXG      -> _exgConfig.update { toggle(it) }
            SensorType.CUSTOM   -> _customConfig.update { toggle(it) }
        }
    }

    /** Set all recording signals at once (empty = all). */
    fun setRecordingSignals(keys: Set<String>) {
        val type = _activeSensorType.value
        val allKeys = signalsForType(type).map { it.key }.toSet()
        val stored = if (keys == allKeys) emptySet() else keys
        when (type) {
            SensorType.GSR_PLUS -> _gsrConfig.update { it.copy(recordingSignals = stored) }
            SensorType.EXG      -> _exgConfig.update { it.copy(recordingSignals = stored) }
            SensorType.CUSTOM   -> _customConfig.update { it.copy(recordingSignals = stored) }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            recordingRepo.deleteSession(sessionId)
            loadSessions()
        }
    }

    fun deleteRecordingFile(path: String) {
        viewModelScope.launch {
            recordingRepo.deleteFile(path)
            loadSessions()
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun refreshPairedDevices(): List<BtDeviceInfo> = btManager.getPairedDevices()

    override fun onCleared() {
        super.onCleared()
        btManager.cleanup()
    }
}
