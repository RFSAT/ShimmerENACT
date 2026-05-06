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

    // ─── Paired / discovered devices ─────────────────────────────────────────
    val pairedDevices: StateFlow<List<BtDeviceInfo>> = flow {
        emit(btManager.getPairedDevices())
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Chart ring buffer ────────────────────────────────────────────────────
    private val sampleBuffer: LinkedList<ShimmerSample> = LinkedList()

    // ─── Recording state ──────────────────────────────────────────────────────
    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    // ─── Recording files list ─────────────────────────────────────────────────
    private val _recordings = MutableStateFlow<List<RecordingFile>>(emptyList())
    val recordings: StateFlow<List<RecordingFile>> = _recordings.asStateFlow()

    // ─── Samples-per-second meter ─────────────────────────────────────────────
    private var lastSpsWindowStart = System.currentTimeMillis()
    private var spsCount = 0

    init {
        observeConnectionState()
        observeSamples()
        observeErrors()
        loadPrefs()
        loadRecordings()
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

                // Ring buffer
                sampleBuffer.addLast(sample)
                while (sampleBuffer.size > ShimmerProtocol.CHART_BUFFER_SIZE) {
                    sampleBuffer.removeFirst()
                }

                _uiState.update { it.copy(
                    latestSample = sample,
                    recentSamples = sampleBuffer.toList(),
                    samplesPerSecond = sps
                )}

                // Write to CSV if recording
                if (recordingRepo.isRecording) {
                    val signals = signalsForType(activeConfig.value.sensorType)
                    recordingRepo.writeSample(sample, signals)
                    _recordingState.update { it.copy(sampleCount = recordingRepo.currentSampleCount) }
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

    private fun loadRecordings() {
        viewModelScope.launch {
            _recordings.value = recordingRepo.listRecordings()
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
            val signals = signalsForType(config.sensorType)
            AppLog.i("REC", "Starting recording — ${signals.size} signals, device: ${config.displayName}")
            val result = recordingRepo.startRecording(config.displayName, signals)
            result.onSuccess { path ->
                AppLog.ok("REC", "Recording started → $path")
                _recordingState.value = RecordingState(
                    isRecording = true,
                    startTimeMs = System.currentTimeMillis(),
                    filePath = path
                )
            }.onFailure { e ->
                AppLog.e("REC", "Recording failed: ${e.message}")
                _uiState.update { it.copy(errorMessage = "Recording failed: ${e.message}") }
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            val result = recordingRepo.stopRecording()
            result.onSuccess { (path, count) ->
                AppLog.ok("REC", "Recording stopped — $count samples saved to ${path.substringAfterLast('/')}")
            }.onFailure { e ->
                AppLog.e("REC", "Stop recording error: ${e.message}")
            }
            _recordingState.value = RecordingState()
            loadRecordings()
        }
    }

    fun deleteRecording(path: String) {
        viewModelScope.launch {
            recordingRepo.deleteRecording(path)
            loadRecordings()
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun refreshPairedDevices(): List<BtDeviceInfo> = btManager.getPairedDevices()

    override fun onCleared() {
        super.onCleared()
        btManager.cleanup()
    }
}
