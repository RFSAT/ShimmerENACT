package com.rfsat.shimmerenact.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.rfsat.shimmerenact.data.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class ShimmerBluetoothManager(private val context: Context) {

    private val btAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // ─── Flows exposed to ViewModel ─────────────────────────────────────────
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _sampleFlow = MutableSharedFlow<ShimmerSample>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val sampleFlow: SharedFlow<ShimmerSample> = _sampleFlow.asSharedFlow()

    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    private val _discoveredDevices = MutableStateFlow<List<BtDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<BtDeviceInfo>> = _discoveredDevices.asStateFlow()

    // ─── Internal state ──────────────────────────────────────────────────────
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var streamJob: Job? = null
    private var sensorBitmap = intArrayOf(0, 0, 0)
    private var calParams = CalibrationParams()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val sppUUID = UUID.fromString(ShimmerProtocol.SPP_UUID)

    // ─── BT permission helper ─────────────────────────────────────────────────
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(): Boolean = btAdapter?.isEnabled == true

    // ─── Get paired devices ───────────────────────────────────────────────────
    fun getPairedDevices(): List<BtDeviceInfo> {
        if (!hasBluetoothPermissions()) return emptyList()
        return btAdapter?.bondedDevices?.mapNotNull { device ->
            try {
                BtDeviceInfo(
                    name = device.name ?: "Unknown",
                    address = device.address
                )
            } catch (e: SecurityException) { null }
        } ?: emptyList()
    }

    // ─── Connect to a specific BT address ────────────────────────────────────
    suspend fun connect(address: String, config: SensorConfig) {
        if (!hasBluetoothPermissions()) {
            _errorFlow.emit("Bluetooth permission not granted")
            return
        }
        disconnect()
        _connectionState.value = ConnectionState.CONNECTING
        _discoveredDevices.value = emptyList()

        scope.launch {
            try {
                val device: BluetoothDevice = btAdapter?.getRemoteDevice(address)
                    ?: throw IOException("Bluetooth not available")

                // Cancel discovery to avoid interference
                try { btAdapter.cancelDiscovery() } catch (_: SecurityException) {}

                // Create RFCOMM socket
                val s = try {
                    device.createRfcommSocketToServiceRecord(sppUUID)
                } catch (e: SecurityException) {
                    throw IOException("BT permission denied: ${e.message}")
                }

                s.connect()  // blocks until connected or throws
                socket = s
                inputStream = s.inputStream
                outputStream = s.outputStream

                _connectionState.value = ConnectionState.CONNECTED

                // Run inquiry to get sensor config
                runInquiry(config)

                // Start streaming
                sendCommand(byteArrayOf(ShimmerProtocol.CMD_START_STREAMING))

                // Start packet reading loop
                startStreamLoop(config)

            } catch (e: Exception) {
                _connectionState.value = ConnectionState.ERROR
                _errorFlow.emit("Connection failed: ${e.message}")
                cleanupSocket()
            }
        }
    }

    // ─── Disconnect ───────────────────────────────────────────────────────────
    fun disconnect() {
        streamJob?.cancel()
        streamJob = null
        try {
            outputStream?.write(byteArrayOf(ShimmerProtocol.CMD_STOP_STREAMING))
        } catch (_: Exception) {}
        cleanupSocket()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun cleanupSocket() {
        try { outputStream?.close() } catch (_: Exception) {}
        try { inputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        outputStream = null; inputStream = null; socket = null
    }

    // ─── Send raw command bytes ───────────────────────────────────────────────
    private fun sendCommand(bytes: ByteArray) {
        try { outputStream?.write(bytes) } catch (e: Exception) {
            scope.launch { _errorFlow.emit("Write error: ${e.message}") }
        }
    }

    // ─── Inquiry: read back sensor bitmap and sampling rate ───────────────────
    private suspend fun runInquiry(config: SensorConfig) = withContext(Dispatchers.IO) {
        sendCommand(byteArrayOf(ShimmerProtocol.CMD_INQUIRY))
        val response = readResponseWithTimeout(ShimmerProtocol.RESPONSE_TIMEOUT_MS)
        if (response != null && response.size >= 5) {
            // Inquiry response: ACK (0xFF) + 0x02 + sampling_rate(2) + bitmap(3) + ...
            sensorBitmap[0] = response[3].toInt() and 0xFF
            sensorBitmap[1] = response[4].toInt() and 0xFF
            sensorBitmap[2] = if (response.size > 5) response[5].toInt() and 0xFF else 0
        } else {
            // Fall back to default bitmaps for known sensor types
            sensorBitmap = defaultBitmapForType(config.sensorType)
        }
    }

    private fun defaultBitmapForType(type: SensorType): IntArray = when (type) {
        SensorType.GSR_PLUS -> intArrayOf(
            ShimmerProtocol.SENSOR_A_ACCEL or
            ShimmerProtocol.SENSOR_GYRO or
            ShimmerProtocol.SENSOR_GSR or
            ShimmerProtocol.SENSOR_EXP_BOARD_A0, // PPG
            ShimmerProtocol.SENSOR_VBATT shr 8,
            0
        )
        SensorType.EXG -> intArrayOf(
            ShimmerProtocol.SENSOR_A_ACCEL or
            ShimmerProtocol.SENSOR_GYRO or
            ShimmerProtocol.SENSOR_EXG1_24BIT or
            ShimmerProtocol.SENSOR_EXG2_24BIT,
            ShimmerProtocol.SENSOR_VBATT shr 8,
            0
        )
        SensorType.CUSTOM -> intArrayOf(
            ShimmerProtocol.SENSOR_EXP_BOARD_A7 or ShimmerProtocol.SENSOR_EXP_BOARD_A0,
            0, 0
        )
    }

    // ─── Read a response packet with timeout ──────────────────────────────────
    private suspend fun readResponseWithTimeout(timeoutMs: Long): ByteArray? =
        withTimeoutOrNull(timeoutMs) {
            val buf = ByteArray(256)
            val n = inputStream?.read(buf) ?: return@withTimeoutOrNull null
            buf.copyOf(n)
        }

    // ─── Main streaming loop ──────────────────────────────────────────────────
    private fun startStreamLoop(config: SensorConfig) {
        streamJob = scope.launch {
            val packetBuf = ByteArray(256)
            val inStream = inputStream ?: return@launch
            var consecutiveErrors = 0

            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                try {
                    // Read packet start byte
                    val startByte = inStream.read()
                    if (startByte == -1) break
                    if (startByte.toByte() != ShimmerProtocol.PACKET_TYPE_DATA) continue

                    // Estimate packet size from enabled sensors
                    val expectedSize = estimatePacketSize()
                    var bytesRead = 0
                    while (bytesRead < expectedSize) {
                        val n = inStream.read(packetBuf, bytesRead, expectedSize - bytesRead)
                        if (n == -1) break
                        bytesRead += n
                    }

                    if (bytesRead < 3) continue  // minimum: 3-byte timestamp

                    val raw = packetBuf.copyOf(bytesRead)
                    val values = ShimmerPacketParser.parse(raw, sensorBitmap, calParams)

                    if (values.isNotEmpty()) {
                        val sample = ShimmerSample(
                            timestampMs = System.currentTimeMillis(),
                            values = values
                        )
                        _sampleFlow.emit(sample)
                        consecutiveErrors = 0
                    }
                } catch (e: IOException) {
                    consecutiveErrors++
                    if (consecutiveErrors > 10) {
                        _errorFlow.emit("Stream IO error: ${e.message}")
                        _connectionState.value = ConnectionState.ERROR
                        break
                    }
                } catch (e: CancellationException) {
                    break
                }
            }
        }
    }

    // ─── Estimate expected bytes per data packet from sensor bitmap ───────────
    private fun estimatePacketSize(): Int {
        var size = 3   // timestamp
        val b0 = sensorBitmap[0]; val b1 = sensorBitmap[1]; val b2 = sensorBitmap[2]
        if (b0 and ShimmerProtocol.SENSOR_A_ACCEL != 0)      size += 6   // 3 × 2 bytes
        if (b0 and ShimmerProtocol.SENSOR_GYRO != 0)          size += 6
        if (b0 and ShimmerProtocol.SENSOR_MAG != 0)           size += 6
        if (b0 and ShimmerProtocol.SENSOR_EXG1_24BIT != 0)    size += 7   // status+2×3
        if (b0 and ShimmerProtocol.SENSOR_EXG2_24BIT != 0)    size += 7
        if (b2 and ShimmerProtocol.SENSOR_EXG1_16BIT != 0)    size += 5   // status+2×2
        if (b2 and ShimmerProtocol.SENSOR_EXG2_16BIT != 0)    size += 5
        if (b0 and ShimmerProtocol.SENSOR_GSR != 0)           size += 2
        if (b0 and ShimmerProtocol.SENSOR_EXP_BOARD_A0 != 0)  size += 2
        if (b1 shr 13 and 0x01 != 0)                          size += 2   // battery
        return size.coerceAtLeast(4)
    }

    fun cleanup() {
        disconnect()
        scope.cancel()
    }
}
