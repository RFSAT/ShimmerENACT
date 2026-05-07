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

    private val _sensorBitmapFlow = MutableStateFlow(intArrayOf(0, 0, 0))
    /** The 3-byte sensor bitmap received from the device (or default). */
    val sensorBitmapFlow: StateFlow<IntArray> = _sensorBitmapFlow.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _sampleFlow = MutableSharedFlow<ShimmerSample>(
        replay = 0, extraBufferCapacity = 256,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val sampleFlow: SharedFlow<ShimmerSample> = _sampleFlow.asSharedFlow()

    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    private val _discoveredDevices = MutableStateFlow<List<BtDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<BtDeviceInfo>> = _discoveredDevices.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var streamJob: Job? = null
    private var sensorBitmap = intArrayOf(0, 0, 0)
    private var channelList: List<Int> = emptyList()  // from inquiry response, authoritative
    private var calParams = CalibrationParams()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sppUUID = UUID.fromString(ShimmerProtocol.SPP_UUID)

    private var meterWindowStart = System.currentTimeMillis()
    private var meterCount = 0
    private var lastSpsLog = 0L

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

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BtDeviceInfo> {
        if (!hasBluetoothPermissions()) return emptyList()
        val devices = btAdapter?.bondedDevices?.mapNotNull { device ->
            try { BtDeviceInfo(name = device.name ?: "Unknown", address = device.address) }
            catch (e: SecurityException) { AppLog.e("BT", "SecurityException reading device: ${e.message}"); null }
        } ?: emptyList()
        AppLog.i("BT", "Paired devices: ${devices.size}" +
            if (devices.isNotEmpty()) " — ${devices.joinToString { it.name }}" else "")
        return devices
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String, config: SensorConfig) {
        if (!hasBluetoothPermissions()) {
            val msg = "Bluetooth permission not granted"
            AppLog.e("BT", msg); _errorFlow.emit(msg); return
        }
        disconnect()
        _connectionState.value = ConnectionState.CONNECTING
        _discoveredDevices.value = emptyList()
        AppLog.i("BT", "=== Connecting to $address [${config.displayName}] ===")
        AppLog.d("BT", "SPP UUID: $sppUUID")

        scope.launch {
            try {
                val device: BluetoothDevice = btAdapter?.getRemoteDevice(address)
                    ?: throw IOException("BluetoothAdapter not available")
                AppLog.d("BT", "Remote device: ${device.name} / ${device.address}")

                try { btAdapter.cancelDiscovery(); AppLog.d("BT", "Discovery cancelled") }
                catch (e: SecurityException) { AppLog.w("BT", "cancelDiscovery: ${e.message}") }

                AppLog.i("BT", "Creating RFCOMM socket…")
                val s = try {
                    device.createRfcommSocketToServiceRecord(sppUUID)
                } catch (e: SecurityException) {
                    throw IOException("SecurityException creating socket: ${e.message}")
                }
                AppLog.ok("BT", "RFCOMM socket created")

                AppLog.i("BT", "Connecting (blocking)…")
                s.connect()
                socket = s
                inputStream  = s.inputStream
                outputStream = s.outputStream
                AppLog.ok("BT", "Socket CONNECTED")
                _connectionState.value = ConnectionState.CONNECTED

                AppLog.i("BT", "Sending INQUIRY (0x${"%02X".format(ShimmerProtocol.CMD_INQUIRY.toInt() and 0xFF)})…")
                runInquiry(config)

                // ── Set hardware sampling rate ────────────────────────────────
                AppLog.i("BT", "Setting hardware rate: ${config.hardwareRateHz} Hz…")
                val rateCmd = ShimmerProtocol.buildRateCommand(config.hardwareRateHz)
                sendCommand(rateCmd)
                val rateAck = readResponseWithTimeout(ShimmerProtocol.RESPONSE_TIMEOUT_MS)
                if (rateAck != null && rateAck.isNotEmpty() && rateAck[0] == ShimmerProtocol.ACK) {
                    AppLog.ok("BT", "Rate ACK received — hardware running at ${config.hardwareRateHz} Hz")
                } else {
                    AppLog.w("BT", "No ACK for rate command (${rateAck?.size ?: 0}B) — continuing anyway")
                }

                // Drain any residual bytes in the BT buffer before starting stream
                drainInputBuffer()

                AppLog.i("BT", "Sending START_STREAMING (0x${"%02X".format(ShimmerProtocol.CMD_START_STREAMING.toInt() and 0xFF)})…")
                sendCommand(byteArrayOf(ShimmerProtocol.CMD_START_STREAMING))
                AppLog.ok("BT", "Streaming command sent — waiting for data packets…")

                startStreamLoop(config)

            } catch (e: Exception) {
                val msg = "${e.javaClass.simpleName}: ${e.message}"
                AppLog.e("BT", "Connection FAILED — $msg")
                _connectionState.value = ConnectionState.ERROR
                _errorFlow.emit("Connection failed: $msg")
                cleanupSocket()
            }
        }
    }

    fun disconnect() {
        if (_connectionState.value != ConnectionState.DISCONNECTED) AppLog.i("BT", "Disconnecting…")
        streamJob?.cancel(); streamJob = null
        try { outputStream?.write(byteArrayOf(ShimmerProtocol.CMD_STOP_STREAMING)); AppLog.d("BT", "STOP_STREAMING sent") }
        catch (_: Exception) {}
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

    private fun sendCommand(bytes: ByteArray) {
        try { outputStream?.write(bytes) }
        catch (e: Exception) { val m = "Write error: ${e.message}"; AppLog.e("BT", m); scope.launch { _errorFlow.emit(m) } }
    }

    private suspend fun runInquiry(config: SensorConfig) = withContext(Dispatchers.IO) {
        sendCommand(byteArrayOf(ShimmerProtocol.CMD_INQUIRY))
        val response = readResponseWithTimeout(ShimmerProtocol.RESPONSE_TIMEOUT_MS)

        // Inquiry response layout:
        //   [0] = 0xFF (ACK)
        //   [1] = 0x02 (INQUIRY_RESPONSE)
        //   [2] = rate LSB
        //   [3] = rate MSB
        //   [4] = sensor bitmap byte 0
        //   [5] = sensor bitmap byte 1
        //   [6] = sensor bitmap byte 2
        //   [7] = num channels
        //   [8+] = channel types

        if (response != null && response.size >= ShimmerProtocol.INQUIRY_MIN_LEN) {
            val bitmapOffset = ShimmerProtocol.INQUIRY_BITMAP_OFFSET
            sensorBitmap[0] = response[bitmapOffset].toInt() and 0xFF
            sensorBitmap[1] = response[bitmapOffset + 1].toInt() and 0xFF
            sensorBitmap[2] = response[bitmapOffset + 2].toInt() and 0xFF
            _sensorBitmapFlow.value = sensorBitmap.copyOf()

            // Also read back the actual sampling rate the device confirmed
            val regLo  = response[2].toInt() and 0xFF
            val regHi  = response[3].toInt() and 0xFF
            val regVal = regLo or (regHi shl 8)
            val actualHz = ShimmerProtocol.registerToHz(regVal)

            // Parse channel list
            val numChannels = if (response.size > ShimmerProtocol.INQUIRY_CHANNELS_OFFSET)
                response[ShimmerProtocol.INQUIRY_CHANNELS_OFFSET].toInt() and 0xFF else 0
            channelList = if (numChannels > 0 && response.size >= ShimmerProtocol.INQUIRY_CHANNELS_OFFSET + 1 + numChannels) {
                (0 until numChannels).map {
                    response[ShimmerProtocol.INQUIRY_CHANNELS_OFFSET + 1 + it].toInt() and 0xFF
                }
            } else emptyList()

            AppLog.ok("BT", "Inquiry OK — bitmap: 0x%02X 0x%02X 0x%02X  rate: %d Hz  channels(%d): %s".format(
                sensorBitmap[0], sensorBitmap[1], sensorBitmap[2], actualHz, numChannels,
                channelList.joinToString { "0x%02X".format(it) }))
            AppLog.i("BT", "Full response (${response.size}B): " +
                response.take(16).joinToString(" ") { "0x%02X".format(it.toInt() and 0xFF) })
        } else {
            AppLog.w("BT", "Inquiry bad/timeout — size=${response?.size ?: 0}B, using default bitmap for ${config.sensorType.name}")
            AppLog.d("BT", "Raw response: " + (response?.take(8)
                ?.joinToString(" ") { "0x%02X".format(it.toInt() and 0xFF) } ?: "null"))
            sensorBitmap = defaultBitmapForType(config.sensorType)
            channelList = emptyList()  // no authoritative channel list — use bitmap fallback
            _sensorBitmapFlow.value = sensorBitmap.copyOf()
            AppLog.d("BT", "Default bitmap: 0x%02X 0x%02X 0x%02X".format(
                sensorBitmap[0], sensorBitmap[1], sensorBitmap[2]))
        }
    }

    private fun defaultBitmapForType(type: SensorType): IntArray = when (type) {
        SensorType.GSR_PLUS -> intArrayOf(
            ShimmerProtocol.SENSOR_A_ACCEL or ShimmerProtocol.SENSOR_GYRO or
            ShimmerProtocol.SENSOR_MAG or ShimmerProtocol.SENSOR_GSR or
            ShimmerProtocol.SENSOR_EXP_BOARD_A0,
            ShimmerProtocol.SENSOR_VBATT,   // byte1: 0x20
            0)
        SensorType.EXG -> intArrayOf(
            ShimmerProtocol.SENSOR_A_ACCEL or ShimmerProtocol.SENSOR_GYRO or
            ShimmerProtocol.SENSOR_EXG1_24BIT or ShimmerProtocol.SENSOR_EXG2_24BIT,
            ShimmerProtocol.SENSOR_VBATT,   // byte1: 0x20
            0)
        SensorType.CUSTOM -> intArrayOf(
            ShimmerProtocol.SENSOR_EXP_BOARD_A7 or ShimmerProtocol.SENSOR_EXP_BOARD_A0, 0, 0)
    }

    // Reads a Shimmer3 response robustly using blocking reads.
    // available() on BluetoothSocket InputStream is unreliable — it returns 0
    // even when more data is buffered in the BT stack. Instead we:
    //   1. Block-read until we have the first 8 bytes (header + channel count)
    //   2. Read exactly numChannels more bytes for the channel list
    // A separate 50ms SO_TIMEOUT equivalent is simulated via withTimeoutOrNull.
    private suspend fun readResponseWithTimeout(timeoutMs: Long): ByteArray? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                val stream = inputStream ?: return@withTimeoutOrNull null
                val buf = ByteArray(256)
                var total = 0

                // Phase 1: read until we have the minimum header (8 bytes)
                while (total < ShimmerProtocol.INQUIRY_MIN_LEN) {
                    val n = try {
                        stream.read(buf, total, ShimmerProtocol.INQUIRY_MIN_LEN - total)
                    } catch (_: Exception) { return@withTimeoutOrNull null }
                    if (n <= 0) return@withTimeoutOrNull null
                    total += n
                }

                // Phase 2: read the channel codes we now know exist
                val numChannels = buf[ShimmerProtocol.INQUIRY_CHANNELS_OFFSET].toInt() and 0xFF
                val targetTotal = ShimmerProtocol.INQUIRY_CHANNELS_OFFSET + 1 + numChannels

                while (total < targetTotal && total < buf.size) {
                    val n = try {
                        stream.read(buf, total, targetTotal - total)
                    } catch (_: Exception) { break }
                    if (n <= 0) break
                    total += n
                }

                if (total > 0) buf.copyOf(total) else null
            }
        }

    private fun startStreamLoop(config: SensorConfig) {
        meterWindowStart = System.currentTimeMillis(); meterCount = 0; lastSpsLog = System.currentTimeMillis()
        var totalPackets = 0L; var badBytes = 0

        // ── Software decimation state ─────────────────────────────────────────
        // For each signal key, track how many hardware samples we've seen since
        // the last emitted sample.  Emit when counter ≥ (hardwareRate / signalRate).
        val signals = signalsForType(config.sensorType)
        val decimationStep: Map<String, Int> = signals.associate { sig ->
            val sigRate = config.effectiveRateHz(sig.key, sig.rateConstraints)
            val step = (config.hardwareRateHz.toDouble() / sigRate).coerceAtLeast(1.0)
                .toInt().coerceAtLeast(1)
            sig.key to step
        }
        val decimationCounter: MutableMap<String, Int> = signals.associate { it.key to 0 }
            .toMutableMap()

        AppLog.d("BT", "Decimation steps: " + decimationStep.entries
            .filter { it.value > 1 }
            .joinToString { "${it.key}÷${it.value}" }
            .ifEmpty { "none (all at hardware rate)" })

        streamJob = scope.launch {
            val inStream = inputStream ?: run { AppLog.e("BT", "InputStream null — aborting stream loop"); return@launch }
            var consecutiveErrors = 0
            AppLog.d("BT", "Stream loop started — expected packet size: ${estimatePacketSize()} bytes")
            val packetBuf = ByteArray(256)

            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                try {
                    val startByte = inStream.read()
                    if (startByte == -1) { AppLog.w("BT", "Stream EOF"); break }

                    if (startByte.toByte() != ShimmerProtocol.PACKET_TYPE_DATA) {
                        if (badBytes % 50 == 0) AppLog.d("BT", "Non-data byte 0x${"%02X".format(startByte)} (total skip=$badBytes)")
                        badBytes++; continue
                    }

                    val expectedSize = estimatePacketSize()
                    var bytesRead = 0
                    while (bytesRead < expectedSize) {
                        val n = inStream.read(packetBuf, bytesRead, expectedSize - bytesRead)
                        if (n == -1) break
                        bytesRead += n
                    }
                    if (bytesRead < 3) { AppLog.w("BT", "Packet too short: ${bytesRead}B"); continue }

                    val rawValues = ShimmerPacketParser.parse(
                        packetBuf.copyOf(bytesRead), sensorBitmap, calParams, channelList)
                    if (rawValues.isEmpty()) continue

                    totalPackets++; meterCount++; consecutiveErrors = 0

                    // ── Apply per-signal decimation ───────────────────────────
                    val decimatedValues = mutableMapOf<String, Double>()
                    for ((key, value) in rawValues) {
                        val step = decimationStep[key] ?: 1
                        val count = (decimationCounter[key] ?: 0) + 1
                        decimationCounter[key] = count
                        if (count >= step) {
                            decimatedValues[key] = value
                            decimationCounter[key] = 0
                        }
                    }

                    if (totalPackets == 1L) {
                        AppLog.ok("BT", "FIRST DATA PACKET — keys: ${rawValues.keys.joinToString()}")
                        AppLog.i("BT", "Packet raw (${bytesRead}B): " +
                            packetBuf.copyOfRange(0, bytesRead).joinToString(" ") { "0x%02X".format(it.toInt() and 0xFF) })
                        AppLog.i("BT", "Values: " + rawValues.entries.joinToString { "${it.key}=${"%.3f".format(it.value)}" })
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastSpsLog >= 5000L) {
                        val sps = meterCount * 1000.0 / (now - meterWindowStart).coerceAtLeast(1)
                        AppLog.i("BT", "Streaming ${"%.1f".format(sps)} Hz  ($totalPackets pkts, $badBytes skipped bytes)")
                        meterCount = 0; meterWindowStart = now; lastSpsLog = now
                    }

                    // Emit full hardware-rate sample (decimated keys excluded this tick)
                    if (decimatedValues.isNotEmpty() || rawValues.isNotEmpty()) {
                        _sampleFlow.emit(ShimmerSample(
                            timestampMs = System.currentTimeMillis(),
                            values = rawValues   // always emit full for display; decimation affects CSV
                        ))
                    }

                } catch (e: IOException) {
                    consecutiveErrors++
                    AppLog.w("BT", "IO error #$consecutiveErrors: ${e.message}")
                    if (consecutiveErrors > 10) {
                        val msg = "Stream failed after 10 consecutive IO errors"
                        AppLog.e("BT", msg); _errorFlow.emit(msg)
                        _connectionState.value = ConnectionState.ERROR; break
                    }
                } catch (e: CancellationException) { AppLog.d("BT", "Stream loop cancelled"); break }
            }
            AppLog.i("BT", "Stream loop ended — $totalPackets packets, $badBytes skipped bytes")
        }
    }

    private fun estimatePacketSize(): Int =
        if (channelList.isNotEmpty())
            ShimmerProtocol.packetSizeFromChannels(channelList)
        else
            ShimmerProtocol.packetDataSize(sensorBitmap[0], sensorBitmap[1], sensorBitmap[2])
                .coerceAtLeast(4)

    /** Discard any bytes currently waiting in the input buffer (clears command ACKs etc.). */
    private fun drainInputBuffer() {
        try {
            val stream = inputStream ?: return
            var drained = 0
            val deadline = System.currentTimeMillis() + 200L  // drain for max 200ms
            val buf = ByteArray(64)
            while (System.currentTimeMillis() < deadline) {
                val avail = stream.available()
                if (avail <= 0) break
                val n = stream.read(buf, 0, minOf(avail, buf.size))
                if (n > 0) drained += n else break
            }
            if (drained > 0) AppLog.d("BT", "Drained $drained stale bytes before stream start")
        } catch (_: Exception) {}
    }

    fun cleanup() { disconnect(); scope.cancel() }
}
