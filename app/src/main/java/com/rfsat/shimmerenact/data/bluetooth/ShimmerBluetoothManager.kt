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
                val capturedInput  = s.inputStream
                val capturedOutput = s.outputStream
                inputStream  = capturedInput
                outputStream = capturedOutput
                AppLog.ok("BT", "Socket CONNECTED")
                _connectionState.value = ConnectionState.CONNECTED

                AppLog.i("BT", "Sending INQUIRY…")
                runInquiry(config)

                AppLog.i("BT", "Sending START_STREAMING (0x07)…")
                sendCommand(byteArrayOf(ShimmerProtocol.CMD_START_STREAMING))
                AppLog.ok("BT", "Streaming started — listening for data packets…")

                startStreamLoop(config, capturedInput)

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


    // ── Inquiry: send 0x01, read all available bytes within 2 seconds ─────────
    // The Shimmer3 responds with: [0xFF ACK][rate_lo][rate_hi][bm0][bm1][bm2][nch][ch...]
    // We log every byte received so we can debug any framing issues.
    private suspend fun runInquiry(config: SensorConfig) = withContext(Dispatchers.IO) {
        AppLog.i("BT", "Sending INQUIRY (0x01)…")
        sendCommand(byteArrayOf(ShimmerProtocol.CMD_INQUIRY))

        // Collect all bytes that arrive within 2 seconds
        val buf = ByteArray(64)
        var total = 0
        val deadline = System.currentTimeMillis() + 2000L
        while (System.currentTimeMillis() < deadline && total < buf.size) {
            val stream = inputStream ?: break
            val avail = try { stream.available() } catch (_: Exception) { break }
            if (avail > 0) {
                val n = try { stream.read(buf, total, minOf(avail, buf.size - total)) }
                        catch (_: Exception) { break }
                if (n > 0) total += n
            } else {
                kotlinx.coroutines.delay(20)
            }
        }

        AppLog.ok("BT", "=== Inquiry response (${total}B): " +
            buf.take(total).joinToString(" ") { "0x%02X".format(it.toInt() and 0xFF) } + " ===")

        if (total < 6) {
            AppLog.w("BT", "Inquiry too short — using default bitmap")
            sensorBitmap = defaultBitmapForType(config.sensorType)
            channelList  = emptyList()
            _sensorBitmapFlow.value = sensorBitmap.copyOf()
            return@withContext
        }

        // Find the body start: skip leading 0xFF bytes
        var i = 0
        while (i < total && buf[i].toInt() and 0xFF == 0xFF) i++

        // Body: [rate_lo][rate_hi][bm0][bm1][bm2][nch][ch0..chN]
        if (i + 6 > total) {
            AppLog.w("BT", "Inquiry body too short at offset $i — using default bitmap")
            sensorBitmap = defaultBitmapForType(config.sensorType)
            channelList  = emptyList()
            _sensorBitmapFlow.value = sensorBitmap.copyOf()
            return@withContext
        }

        // Format: [0xFF] [rate_lo] [rate_hi] [bm0] [bm1] [bm2] [nch] [ch0..chN]
        // Skip leading 0xFF ACK byte(s) — there may be one or two.
        // Then read fixed layout: 6 header bytes + nch channel codes.
        // Log full response so we can verify the offset.
        var i = 0
        while (i < total && buf[i].toInt() and 0xFF == 0xFF) i++

        AppLog.i("BT", "Inquiry body at offset $i/${total}: " +
            buf.drop(i).take(10).joinToString(" ") { "0x%02X".format(it.toInt() and 0xFF) })

        // Minimum body: rate(2) + bitmap(3) + nch(1) = 6 bytes
        if (i + 6 > total) {
            AppLog.w("BT", "Inquiry body too short ($total bytes, body at $i) — using default bitmap")
            sensorBitmap = defaultBitmapForType(config.sensorType)
            channelList  = emptyList()
            _sensorBitmapFlow.value = sensorBitmap.copyOf()
            return@withContext
        }

        val regLo    = buf[i + 0].toInt() and 0xFF
        val regHi    = buf[i + 1].toInt() and 0xFF
        val actualHz = ShimmerProtocol.registerToHz(regLo or (regHi shl 8))
        sensorBitmap[0] = buf[i + 2].toInt() and 0xFF
        sensorBitmap[1] = buf[i + 3].toInt() and 0xFF
        sensorBitmap[2] = buf[i + 4].toInt() and 0xFF
        _sensorBitmapFlow.value = sensorBitmap.copyOf()
        val nch = buf[i + 5].toInt() and 0xFF

        channelList = if (nch in 1..20 && i + 6 + nch <= total) {
            (0 until nch).map { buf[i + 6 + it].toInt() and 0xFF }
        } else {
            AppLog.w("BT", "nch=$nch out of range or beyond buffer — using default bitmap")
            emptyList()
        }

        AppLog.ok("BT", "Inquiry OK — bitmap: 0x%02X 0x%02X 0x%02X  rate: %d Hz  channels(%d): [%s]".format(
            sensorBitmap[0], sensorBitmap[1], sensorBitmap[2], actualHz, nch,
            channelList.joinToString { "0x%02X".format(it) }))
    }

    private fun defaultBitmapForType(type: SensorType): IntArray = when (type) {
        SensorType.GSR_PLUS -> intArrayOf(
            ShimmerProtocol.SENSOR_A_ACCEL or ShimmerProtocol.SENSOR_GYRO or
            ShimmerProtocol.SENSOR_MAG or ShimmerProtocol.SENSOR_GSR or
            ShimmerProtocol.SENSOR_EXP_BOARD_A0,
            ShimmerProtocol.SENSOR_VBATT, 0)
        SensorType.EXG -> intArrayOf(
            ShimmerProtocol.SENSOR_A_ACCEL or ShimmerProtocol.SENSOR_GYRO or
            ShimmerProtocol.SENSOR_EXG1_24BIT or ShimmerProtocol.SENSOR_EXG2_24BIT,
            ShimmerProtocol.SENSOR_VBATT, 0)
        SensorType.CUSTOM -> intArrayOf(
            ShimmerProtocol.SENSOR_EXP_BOARD_A7 or ShimmerProtocol.SENSOR_EXP_BOARD_A0, 0, 0)
    }

    private fun startStreamLoop(config: SensorConfig, stream: java.io.InputStream) {
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
            val inStream = stream  // captured before any disconnect can null the field
            var consecutiveErrors = 0
            val pktSize = estimatePacketSize()
            AppLog.i("BT", "Stream started — pkt=${pktSize}B, channels(${channelList.size}): " +
                channelList.joinToString { "0x%02X".format(it) })
            val packetBuf = ByteArray(256)

            // ── Log first 32 raw bytes to determine actual packet framing ────
            // Use a 3-second timeout — if nothing arrives, the device isn't streaming.
            val probeBuf = ByteArray(32)
            var probeGot = 0
            val probeDeadline = System.currentTimeMillis() + 3000L
            while (probeGot < 32 && System.currentTimeMillis() < probeDeadline) {
                val avail = try { inStream.available() } catch (_: Exception) { break }
                if (avail > 0) {
                    val n = try { inStream.read(probeBuf, probeGot, minOf(avail, 32 - probeGot)) }
                            catch (_: Exception) { break }
                    if (n == -1) break
                    probeGot += n
                } else {
                    kotlinx.coroutines.delay(50)
                }
            }
            if (probeGot == 0) {
                AppLog.e("BT", "=== No data received from device after 3s — START_STREAMING may have failed ===")
            } else {
                AppLog.ok("BT", "=== First ${probeGot}B from device: " +
                    probeBuf.take(probeGot).joinToString(" ") { "0x%02X".format(it.toInt() and 0xFF) } + " ===")
            }

            // Push probe bytes into packetBuf so they are not lost.
            // Determine delimiter: if first byte is 0x00, use sync-byte framing.
            // Otherwise read continuously without a delimiter.
            val useDelimiter = probeGot > 0 && probeBuf[0].toInt() and 0xFF == 0x00
            AppLog.i("BT", "Framing: ${if (useDelimiter) "delimiter 0x00" else "continuous (no delimiter)"}")

            // Prime the pipeline with probe bytes
            var probeOffset = 0
            fun readByte(): Int {
                if (probeOffset < probeGot) return probeBuf[probeOffset++].toInt() and 0xFF
                return inStream.read()
            }
            fun readBytes(buf: ByteArray, off: Int, len: Int): Int {
                if (probeOffset >= probeGot) return inStream.read(buf, off, len)
                val fromProbe = minOf(len, probeGot - probeOffset)
                probeBuf.copyInto(buf, off, probeOffset, probeOffset + fromProbe)
                probeOffset += fromProbe
                if (fromProbe < len) {
                    val rest = inStream.read(buf, off + fromProbe, len - fromProbe)
                    return if (rest == -1) fromProbe else fromProbe + rest
                }
                return fromProbe
            }

            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                try {
                    if (useDelimiter) {
                        // Scan for 0x00 packet delimiter
                        val startByte = readByte()
                        if (startByte == -1) { AppLog.w("BT", "Stream EOF"); break }
                        if (startByte != 0x00) { badBytes++; continue }
                    }

                    val expectedSize = pktSize
                    var bytesRead = 0
                    while (bytesRead < expectedSize) {
                        val n = if (probeOffset < probeGot) {
                            readBytes(packetBuf, bytesRead, expectedSize - bytesRead)
                        } else {
                            inStream.read(packetBuf, bytesRead, expectedSize - bytesRead)
                        }
                        if (n == -1) break
                        bytesRead += n
                    }
                    if (bytesRead < 3) { AppLog.w("BT", "Packet too short: ${bytesRead}B"); continue }

                    val rawValues = ShimmerPacketParser.parse(
                        packetBuf.copyOf(bytesRead), sensorBitmap, calParams, channelList)

                    if (rawValues.isEmpty()) {
                        if (totalPackets == 0L) {
                            AppLog.w("BT", "Parse empty — ${bytesRead}B packet, channels=${channelList.size}, bitmap=0x%02X 0x%02X".format(sensorBitmap[0], sensorBitmap[1]))
                            AppLog.i("BT", "Channels: " + channelList.joinToString { "0x%02X".format(it) })
                            AppLog.i("BT", "Raw bytes: " + packetBuf.copyOfRange(0, minOf(bytesRead, 24))
                                .joinToString(" ") { "0x%02X".format(it.toInt() and 0xFF) })
                        }
                        continue
                    }

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
