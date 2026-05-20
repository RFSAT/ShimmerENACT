package com.rfsat.shimmerenact.data.bluetooth

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.rfsat.shimmerenact.data.models.*
import com.rfsat.shimmerenact.data.repository.AppLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class ShimmerBluetoothManager(private val context: Context) {

    private val btAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    // ─── Flows exposed to ViewModel ──────────────────────────────────────────
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
    private var channelList: List<Int> = emptyList()
    private var calParams = CalibrationParams()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sppUUID = UUID.fromString(ShimmerProtocol.SPP_UUID)

    // sample-rate meter
    private var meterWindowStart = System.currentTimeMillis()
    private var meterCount = 0
    private var lastSpsLog = 0L

    // ─── Permission helper ────────────────────────────────────────────────────
    fun hasBluetoothPermissions(): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED
        }
        if (!granted) AppLog.w("BT", "Bluetooth permission NOT granted")
        return granted
    }

    fun isBluetoothEnabled(): Boolean {
        val on = btAdapter?.isEnabled == true
        if (!on) AppLog.w("BT", "Bluetooth adapter is disabled")
        return on
    }

    // ─── Get paired devices ───────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BtDeviceInfo> {
        if (!hasBluetoothPermissions()) return emptyList()
        val devices = btAdapter?.bondedDevices?.mapNotNull { device ->
            try {
                BtDeviceInfo(name = device.name ?: "Unknown", address = device.address)
            } catch (e: SecurityException) {
                AppLog.e("BT", "SecurityException reading bonded device: ${e.message}")
                null
            }
        } ?: emptyList()
        AppLog.i("BT", "Paired devices found: ${devices.size}" +
            if (devices.isNotEmpty()) " — ${devices.joinToString { it.name }}" else "")
        return devices
    }

    // ─── Connect ─────────────────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    suspend fun connect(address: String, config: SensorConfig) {
        if (!hasBluetoothPermissions()) {
            val msg = "Bluetooth permission not granted"
            AppLog.e("BT", msg); _errorFlow.emit(msg); return
        }
        disconnect()
        _connectionState.value = ConnectionState.CONNECTING
        _discoveredDevices.value = emptyList()

        AppLog.i("BT", "Connecting to $address  [${config.displayName}]")
        AppLog.d("BT", "SPP UUID: $sppUUID")

        scope.launch {
            try {
                val device: BluetoothDevice = btAdapter?.getRemoteDevice(address)
                    ?: throw IOException("BluetoothAdapter not available")

                AppLog.d("BT", "Remote device obtained: ${device.name} / ${device.address}")

                // Cancel discovery to reduce RF interference
                try {
                    btAdapter.cancelDiscovery()
                    AppLog.d("BT", "Discovery cancelled")
                } catch (e: SecurityException) {
                    AppLog.w("BT", "Could not cancel discovery: ${e.message}")
                }

                // Create RFCOMM socket
                AppLog.i("BT", "Creating RFCOMM socket…")
                val s = try {
                    device.createRfcommSocketToServiceRecord(sppUUID)
                } catch (e: SecurityException) {
                    throw IOException("SecurityException creating socket: ${e.message}")
                }
                AppLog.ok("BT", "RFCOMM socket created")

                // Blocking connect
                AppLog.i("BT", "Connecting socket (blocking)…")
                s.connect()
                socket = s
                inputStream  = s.inputStream
                outputStream = s.outputStream
                AppLog.ok("BT", "Socket connected ✓")

                _connectionState.value = ConnectionState.CONNECTED

                // ── Inquiry ──────────────────────────────────────────────────
                AppLog.i("BT", "Sending INQUIRY command (0x${"%02X".format(ShimmerProtocol.CMD_INQUIRY.toInt() and 0xFF)})…")
                runInquiry(config)

                // ── Start streaming ───────────────────────────────────────────
                AppLog.i("BT", "Sending START_STREAMING command (0x${"%02X".format(ShimmerProtocol.CMD_START_STREAMING.toInt() and 0xFF)})…")
                sendCommand(byteArrayOf(ShimmerProtocol.CMD_START_STREAMING))
                AppLog.ok("BT", "Streaming started — waiting for data packets")

                startStreamLoop(config)

            } catch (e: Exception) {
                val msg = "Connection failed: ${e.javaClass.simpleName}: ${e.message}"
                AppLog.e("BT", msg)
                _connectionState.value = ConnectionState.ERROR
                _errorFlow.emit(msg)
                cleanupSocket()
            }
        }
    }

    // ─── Disconnect ───────────────────────────────────────────────────────────
    fun disconnect() {
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            AppLog.i("BT", "Disconnecting…")
        }
        streamJob?.cancel()
        streamJob = null
        try {
            outputStream?.write(byteArrayOf(ShimmerProtocol.CMD_STOP_STREAMING))
            AppLog.d("BT", "STOP_STREAMING sent")
        } catch (_: Exception) {}
        cleanupSocket()
        _connectionState.value = ConnectionState.DISCONNECTED
        AppLog.i("BT", "Disconnected")
    }

    private fun cleanupSocket() {
        try { outputStream?.close() } catch (_: Exception) {}
        try { inputStream?.close()  } catch (_: Exception) {}
        try { socket?.close()       } catch (_: Exception) {}
        outputStream = null; inputStream = null; socket = null
    }

    // ─── Send raw command bytes ───────────────────────────────────────────────
    private fun sendCommand(bytes: ByteArray) {
        try {
            outputStream?.write(bytes)
        } catch (e: Exception) {
            val msg = "Write error: ${e.message}"
            AppLog.e("BT", msg)
            scope.launch { _errorFlow.emit(msg) }
        }
    }

    // ─── Inquiry ─────────────────────────────────────────────────────────────
    private suspend fun runInquiry(config: SensorConfig) = withContext(Dispatchers.IO) {
        sendCommand(byteArrayOf(ShimmerProtocol.CMD_INQUIRY))
        val response = readResponseWithTimeout(ShimmerProtocol.RESPONSE_TIMEOUT_MS)
        AppLog.ok("BT", "=== Inquiry raw (${response?.size ?: 0}B): " +
            (response?.take(20)?.joinToString(" ") { "0x%02X".format(it.toInt() and 0xFF) } ?: "null") + " ===")

        // The Shimmer3 may send: [0xFF ACK][0x02 code][rate×2][bitmap×3][nch][codes...]
        // or just:               [0x02 code][rate×2][bitmap×3][nch][codes...]
        // Detect which case by checking the first byte and adjust offset accordingly.
        val bodyStart = if (response != null && response.isNotEmpty() &&
            response[0].toInt() and 0xFF == 0xFF) 1 else 0

        // Body layout (from bodyStart): [0x02][rate_lo][rate_hi][bm0][bm1][bm2][nch][codes...]
        val minBodyLen = 7   // response-code(1) + rate(2) + bitmap(3) + nch(1)

        if (response != null && response.size >= bodyStart + minBodyLen) {
            val b = bodyStart  // short alias
            sensorBitmap[0] = response[b + 3].toInt() and 0xFF
            sensorBitmap[1] = response[b + 4].toInt() and 0xFF
            sensorBitmap[2] = response[b + 5].toInt() and 0xFF

            val regLo    = response[b + 1].toInt() and 0xFF
            val regHi    = response[b + 2].toInt() and 0xFF
            val actualHz = ShimmerProtocol.registerToHz(regLo or (regHi shl 8))

            val numChannels = response[b + 6].toInt() and 0xFF
            val codesStart  = b + 7
            channelList = if (numChannels > 0 && response.size >= codesStart + numChannels) {
                (0 until numChannels).map { response[codesStart + it].toInt() and 0xFF }
            } else emptyList()

            AppLog.ok("BT", "Inquiry OK (bodyStart=$bodyStart) — " +
                "bitmap: 0x%02X 0x%02X 0x%02X  rate: %d Hz  channels(%d): %s".format(
                sensorBitmap[0], sensorBitmap[1], sensorBitmap[2], actualHz, numChannels,
                channelList.joinToString { "0x%02X".format(it) }))
            AppLog.i("BT", "Full response (${response.size}B): " +
                response.take(16).joinToString(" ") { "0x%02X".format(it.toInt() and 0xFF) })
        } else {
            AppLog.w("BT", "Inquiry bad/timeout — size=${response?.size ?: 0}B, using default bitmap for ${config.sensorType.name}")
            AppLog.d("BT", "Raw response: " + (response?.take(8)
                ?.joinToString(" ") { "0x%02X".format(it.toInt() and 0xFF) } ?: "null"))
            sensorBitmap = defaultBitmapForType(config.sensorType)
            channelList = emptyList()
            AppLog.d("BT", "Default bitmap: 0x%02X 0x%02X 0x%02X".format(
                sensorBitmap[0], sensorBitmap[1], sensorBitmap[2]))
        }
    }

    private fun defaultBitmapForType(type: SensorType): IntArray = when (type) {
        SensorType.GSR_PLUS -> intArrayOf(
            ShimmerProtocol.SENSOR_A_ACCEL or ShimmerProtocol.SENSOR_GYRO or
            ShimmerProtocol.SENSOR_GSR or ShimmerProtocol.SENSOR_EXP_BOARD_A0,
            ShimmerProtocol.SENSOR_VBATT shr 8, 0
        )
        SensorType.EXG -> intArrayOf(
            ShimmerProtocol.SENSOR_A_ACCEL or ShimmerProtocol.SENSOR_GYRO or
            ShimmerProtocol.SENSOR_EXG1_24BIT or ShimmerProtocol.SENSOR_EXG2_24BIT,
            ShimmerProtocol.SENSOR_VBATT shr 8, 0
        )
        SensorType.CUSTOM -> intArrayOf(
            ShimmerProtocol.SENSOR_EXP_BOARD_A7 or ShimmerProtocol.SENSOR_EXP_BOARD_A0, 0, 0
        )
    }

    // ─── Read response with timeout ───────────────────────────────────────────
    private suspend fun readResponseWithTimeout(timeoutMs: Long): ByteArray? =
        withTimeoutOrNull(timeoutMs) {
            val buf = ByteArray(256)
            val n = inputStream?.read(buf) ?: return@withTimeoutOrNull null
            buf.copyOf(n)
        }

    // ─── Main streaming loop ──────────────────────────────────────────────────
    private fun startStreamLoop(config: SensorConfig) {
        meterWindowStart = System.currentTimeMillis()
        meterCount = 0
        var totalPackets = 0L
        var badPackets   = 0

        streamJob = scope.launch {
            val packetBuf = ByteArray(256)
            val inStream  = inputStream ?: run {
                AppLog.e("BT", "InputStream is null — cannot start stream loop"); return@launch
            }
            var consecutiveErrors = 0

            AppLog.d("BT", "Stream loop started — expected packet size: ${estimatePacketSize()} bytes")

            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                try {
                    val startByte = inStream.read()
                    if (startByte == -1) {
                        AppLog.w("BT", "Stream EOF received")
                        break
                    }

                    if (startByte.toByte() != ShimmerProtocol.PACKET_TYPE_DATA) {
                        // Log unexpected byte occasionally to avoid spam
                        if (totalPackets == 0L || badPackets % 20 == 0) {
                            AppLog.d("BT", "Non-data byte: 0x${"%02X".format(startByte)} (bad=$badPackets)")
                        }
                        badPackets++
                        continue
                    }

                    val expectedSize = estimatePacketSize()
                    var bytesRead = 0
                    while (bytesRead < expectedSize) {
                        val n = inStream.read(packetBuf, bytesRead, expectedSize - bytesRead)
                        if (n == -1) break
                        bytesRead += n
                    }

                    if (bytesRead < 3) {
                        AppLog.w("BT", "Packet too short: $bytesRead bytes")
                        continue
                    }

                    val raw    = packetBuf.copyOf(bytesRead)
                    val values = ShimmerPacketParser.parse(raw, sensorBitmap, calParams, channelList)

                    if (values.isEmpty()) {
                        AppLog.d("BT", "Parser returned empty map for packet of $bytesRead bytes")
                        continue
                    }

                    totalPackets++
                    meterCount++
                    consecutiveErrors = 0

                    // Log first packet with full detail
                    if (totalPackets == 1L) {
                        AppLog.ok("BT", "First data packet received! Keys: ${values.keys.joinToString()}")
                        AppLog.d("BT", "Sample values: " + values.entries.take(5)
                            .joinToString { "${it.key}=${"%.3f".format(it.value)}" })
                    }

                    // Log SPS every 5 seconds
                    val now = System.currentTimeMillis()
                    if (now - lastSpsLog >= 5000L) {
                        val elapsed = (now - meterWindowStart) / 1000.0
                        val sps = if (elapsed > 0) meterCount / elapsed else 0.0
                        AppLog.i("BT", "Stream OK — ${"%.1f".format(sps)} Hz  ($totalPackets packets total)")
                        meterCount = 0
                        meterWindowStart = now
                        lastSpsLog = now
                    }

                    _sampleFlow.emit(ShimmerSample(
                        timestampMs = System.currentTimeMillis(),
                        values = values
                    ))

                } catch (e: IOException) {
                    consecutiveErrors++
                    AppLog.w("BT", "IO error #$consecutiveErrors: ${e.message}")
                    if (consecutiveErrors > 10) {
                        val msg = "Stream failed after 10 consecutive IO errors"
                        AppLog.e("BT", msg)
                        _errorFlow.emit(msg)
                        _connectionState.value = ConnectionState.ERROR
                        break
                    }
                } catch (e: CancellationException) {
                    AppLog.d("BT", "Stream loop cancelled")
                    break
                }
            }
            AppLog.i("BT", "Stream loop ended — total packets: $totalPackets, bad bytes: $badPackets")
        }
    }

    // ─── Estimate packet size ─────────────────────────────────────────────────
    private fun estimatePacketSize(): Int {
        var size = 3
        val b0 = sensorBitmap[0]; val b1 = sensorBitmap[1]; val b2 = sensorBitmap[2]
        if (b0 and ShimmerProtocol.SENSOR_A_ACCEL     != 0) size += 6
        if (b0 and ShimmerProtocol.SENSOR_GYRO        != 0) size += 6
        if (b0 and ShimmerProtocol.SENSOR_MAG         != 0) size += 6
        if (b0 and ShimmerProtocol.SENSOR_EXG1_24BIT  != 0) size += 7
        if (b0 and ShimmerProtocol.SENSOR_EXG2_24BIT  != 0) size += 7
        if (b2 and ShimmerProtocol.SENSOR_EXG1_16BIT  != 0) size += 5
        if (b2 and ShimmerProtocol.SENSOR_EXG2_16BIT  != 0) size += 5
        if (b0 and ShimmerProtocol.SENSOR_GSR         != 0) size += 2
        if (b0 and ShimmerProtocol.SENSOR_EXP_BOARD_A0 != 0) size += 2
        if (b1 shr 13 and 0x01                        != 0) size += 2
        return size.coerceAtLeast(4)
    }

    fun cleanup() {
        disconnect()
        scope.cancel()
    }
}
