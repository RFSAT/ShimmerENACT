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
import com.rfsat.shimmerenact.data.repository.LocationRepository
import com.rfsat.shimmerenact.data.repository.RecordingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.LinkedList

class ShimmerViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    val btManager = ShimmerBluetoothManager(context)
    val prefsRepo = PreferencesRepository(context)
    val recordingRepo = RecordingRepository(context)
    val locationRepo = LocationRepository(context)

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
    private val _imuConfig = MutableStateFlow(
        SensorConfig(
            sensorType = SensorType.IMU,
            btRadioId = SensorType.IMU.defaultBtSuffix,
            hardwareRateHz = DEFAULT_RATE_HZ,
            enabledSignals = IMU_SIGNALS.map { it.key }.toSet()
        )
    )
    private val _emgConfig = MutableStateFlow(
        SensorConfig(
            sensorType = SensorType.EMG,
            btRadioId = SensorType.EMG.defaultBtSuffix,
            hardwareRateHz = DEFAULT_RATE_HZ,
            enabledSignals = EMG_SIGNALS.map { it.key }.toSet()
        )
    )
    private val _ebioConfig = MutableStateFlow(
        SensorConfig(SensorType.EBIO, SensorType.EBIO.defaultBtSuffix,
            DEFAULT_RATE_HZ, EBIO_SIGNALS.map { it.key }.toSet())
    )
    private val _bridgeConfig = MutableStateFlow(
        SensorConfig(SensorType.BRIDGE_AMP, SensorType.BRIDGE_AMP.defaultBtSuffix,
            DEFAULT_RATE_HZ, BRIDGE_AMP_SIGNALS.map { it.key }.toSet())
    )
    private val _imu200gConfig = MutableStateFlow(
        SensorConfig(SensorType.IMU_200G, SensorType.IMU_200G.defaultBtSuffix,
            DEFAULT_RATE_HZ, IMU_200G_SIGNALS.map { it.key }.toSet())
    )
    private val _proto3dConfig = MutableStateFlow(
        SensorConfig(SensorType.PROTO3_DELUXE, SensorType.PROTO3_DELUXE.defaultBtSuffix,
            DEFAULT_RATE_HZ, PROTO3_DELUXE_SIGNALS.map { it.key }.toSet())
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

    // Combine all 9 configs: combine() supports ≤5 flows; use three nesting levels.
    private val _baseConfigs = combine(
        _activeSensorType, _gsrConfig, _exgConfig
    ) { type, gsr, exg -> Triple(type, gsr, exg) }

    private val _midConfigs = combine(
        _imuConfig, _emgConfig, _ebioConfig, _bridgeConfig
    ) { imu, emg, ebio, bridge -> listOf(imu, emg, ebio, bridge) }

    private val _extConfigs = combine(
        _imu200gConfig, _proto3dConfig, _customConfig
    ) { imu200g, proto3d, custom -> Triple(imu200g, proto3d, custom) }

    val activeConfig: StateFlow<SensorConfig> = combine(
        _baseConfigs, _midConfigs, _extConfigs
    ) { (type, gsr, exg), mid, (imu200g, proto3d, custom) ->
        val (imu, emg, ebio, bridge) = mid
        when (type) {
            SensorType.GSR_PLUS      -> gsr
            SensorType.EXG           -> exg
            SensorType.IMU           -> imu
            SensorType.EMG           -> emg
            SensorType.EBIO          -> ebio
            SensorType.BRIDGE_AMP    -> bridge
            SensorType.IMU_200G      -> imu200g
            SensorType.PROTO3_DELUXE -> proto3d
            SensorType.CUSTOM        -> custom
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
        // Derive from channel list (authoritative) rather than bitmap.
        // The bitmap byte order varies by firmware; the channel list is always correct.
        btManager.channelListFlow.map { channels ->
            if (channels.isEmpty()) return@map emptySet()
            val keys = mutableSetOf<String>()
            for (ch in channels) {
                when (ch) {
                    // Low-noise accelerometer (any one axis means all three present)
                    ShimmerProtocol.CH_ACCEL_LN_X,
                    ShimmerProtocol.CH_ACCEL_LN_Y,
                    ShimmerProtocol.CH_ACCEL_LN_Z -> keys += listOf("accel_x", "accel_y", "accel_z")
                    // Wide-range accelerometer
                    ShimmerProtocol.CH_ACCEL_WR_X,
                    ShimmerProtocol.CH_ACCEL_WR_Y,
                    ShimmerProtocol.CH_ACCEL_WR_Z -> keys += listOf("accel_wr_x", "accel_wr_y", "accel_wr_z")
                    // Gyroscope
                    ShimmerProtocol.CH_GYRO_X,
                    ShimmerProtocol.CH_GYRO_Y,
                    ShimmerProtocol.CH_GYRO_Z,
                    0x12 /* empirical gyro SR48-5-0 */ -> keys += listOf("gyro_x", "gyro_y", "gyro_z")
                    // Magnetometer
                    ShimmerProtocol.CH_MAG_X,
                    ShimmerProtocol.CH_MAG_Y,
                    ShimmerProtocol.CH_MAG_Z,
                    0x1C /* empirical mag SR48-5-0 */  -> keys += listOf("mag_x", "mag_y", "mag_z")
                    // GSR
                    ShimmerProtocol.CH_GSR         -> keys += "gsr_kohm"
                    // PPG (IntADC Ch13 or Ch14)
                    ShimmerProtocol.CH_INT_ADC_CH13,
                    ShimmerProtocol.CH_INT_ADC_CH14 -> keys += "ppg_mv"
                    // Battery
                    ShimmerProtocol.CH_VBATT,
                    0x0A /* empirical batt SR48-5-0 */ -> keys += "batt_mv"
                    // ExG
                    ShimmerProtocol.CH_EXG1_CH1_24,
                    ShimmerProtocol.CH_EXG1_CH1_16 -> keys += listOf("exg1_ch1", "exg1_ch2",
                                                                       "emg_ch1",  "emg_ref")  // dual alias for EMG mode
                    ShimmerProtocol.CH_EXG2_CH1_24,
                    ShimmerProtocol.CH_EXG2_CH1_16 -> keys += listOf("exg2_ch1", "exg2_ch2")
                }
            }
            AppLog.i("VM", "Channels: ${channels.map { "0x%02X".format(it) }}  Keys: $keys")
            val defined = signalsForType(_activeSensorType.value).map { it.key }.toSet()
            keys.intersect(defined)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ─── Paired / discovered devices ─────────────────────────────────────────
    val pairedDevices: StateFlow<List<BtDeviceInfo>> = flow {
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
        try {
            // Always reset recording state on startup — if the app crashed mid-recording,
            // this prevents a corrupted state from crashing every subsequent launch.
            recordingRepo.resetRecordingState()
            _recordingState.value = RecordingState()

            observeConnectionState()
            observeSamples()
            observeErrors()
            loadPrefs()
            loadSessions()
        } catch (e: Exception) {
            AppLog.e("VM", "Init error: ${e.javaClass.simpleName}: ${e.message}")
        }
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
                    recordingRepo.writeSampleSync(sample, locationRepo.location.value)
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
            prefsRepo.imuBtId.collect { id -> _imuConfig.update { it.copy(btRadioId = id) } }
            prefsRepo.emgBtId.collect     { id -> _emgConfig.update    { it.copy(btRadioId = id) } }
            prefsRepo.ebioBtId.collect    { id -> _ebioConfig.update   { it.copy(btRadioId = id) } }
            prefsRepo.bridgeBtId.collect  { id -> _bridgeConfig.update { it.copy(btRadioId = id) } }
            prefsRepo.imu200gBtId.collect { id -> _imu200gConfig.update{ it.copy(btRadioId = id) } }
            prefsRepo.proto3dBtId.collect { id -> _proto3dConfig.update{ it.copy(btRadioId = id) } }
        }
    }

    private fun loadSessions() {
        viewModelScope.launch {
            _sessions.value = recordingRepo.listSessions()
        }
    }

    /** Reload the sessions list — call when the Recordings screen becomes visible. */
    fun refreshSessions() = loadSessions()

    // ─── Public actions ───────────────────────────────────────────────────────

    fun selectSensorType(type: SensorType) {
        _activeSensorType.value = type
        _uiState.update { it.copy(config = activeConfig.value, errorMessage = null) }
    }

    fun updateBtRadioId(type: SensorType, id: String) {
        viewModelScope.launch {
            when (type) {
                SensorType.GSR_PLUS -> { _gsrConfig.update { it.copy(btRadioId = id) };    prefsRepo.saveGsrBtId(id) }
                SensorType.EXG      -> { _exgConfig.update { it.copy(btRadioId = id) };    prefsRepo.saveExgBtId(id) }
                SensorType.IMU      -> { _imuConfig.update { it.copy(btRadioId = id) };    prefsRepo.saveImuBtId(id) }
                SensorType.EMG      -> { _emgConfig.update { it.copy(btRadioId = id) };    prefsRepo.saveEmgBtId(id) }
                SensorType.EBIO          -> { _ebioConfig.update   { it.copy(btRadioId = id) }; prefsRepo.saveEbioBtId(id) }
                SensorType.BRIDGE_AMP    -> { _bridgeConfig.update { it.copy(btRadioId = id) }; prefsRepo.saveBridgeBtId(id) }
                SensorType.IMU_200G      -> { _imu200gConfig.update{ it.copy(btRadioId = id) }; prefsRepo.saveImu200gBtId(id) }
                SensorType.PROTO3_DELUXE -> { _proto3dConfig.update{ it.copy(btRadioId = id) }; prefsRepo.saveProto3dBtId(id) }
                SensorType.CUSTOM        -> { _customConfig.update  { it.copy(btRadioId = id) }; prefsRepo.saveCustomBtId(id) }
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
            SensorType.IMU      -> _imuConfig.update { it.withHardwareRate(clamped) }
            SensorType.EMG      -> _emgConfig.update { it.withHardwareRate(clamped) }
            SensorType.EBIO          -> _ebioConfig.update   { it.withHardwareRate(clamped) }
            SensorType.BRIDGE_AMP    -> _bridgeConfig.update { it.withHardwareRate(clamped) }
            SensorType.IMU_200G      -> _imu200gConfig.update{ it.withHardwareRate(clamped) }
            SensorType.PROTO3_DELUXE -> _proto3dConfig.update{ it.withHardwareRate(clamped) }
            SensorType.CUSTOM        -> _customConfig.update  { it.withHardwareRate(clamped) }
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
            SensorType.IMU      -> _imuConfig.update { it.withSignalRate(signalKey, hz, constraints) }
            SensorType.EMG      -> _emgConfig.update { it.withSignalRate(signalKey, hz, constraints) }
            SensorType.EBIO          -> _ebioConfig.update   { it.withSignalRate(signalKey, hz, constraints) }
            SensorType.BRIDGE_AMP    -> _bridgeConfig.update { it.withSignalRate(signalKey, hz, constraints) }
            SensorType.IMU_200G      -> _imu200gConfig.update{ it.withSignalRate(signalKey, hz, constraints) }
            SensorType.PROTO3_DELUXE -> _proto3dConfig.update{ it.withSignalRate(signalKey, hz, constraints) }
            SensorType.CUSTOM        -> _customConfig.update  { it.withSignalRate(signalKey, hz, constraints) }
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
                locationRepo.startUpdates()
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
            try {
                val result = recordingRepo.stopRecording()
                result.onSuccess { session ->
                    AppLog.ok("REC", "Stopped — ${session.files.size} files, ${recordingRepo.totalSamplesWritten} rows")
                }.onFailure { e ->
                    AppLog.e("REC", "Stop error: ${e.message}")
                }
            } catch (e: Exception) {
                AppLog.e("REC", "stopRecording crashed: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                locationRepo.stopUpdates()
                _recordingState.value = RecordingState()
                try { loadSessions() } catch (e: Exception) {
                    AppLog.e("REC", "loadSessions failed: ${e.message}")
                }
            }
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
        locationRepo.cleanup()
    }
}
