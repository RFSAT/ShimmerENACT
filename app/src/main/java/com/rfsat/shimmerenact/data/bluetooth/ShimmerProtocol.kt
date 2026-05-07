package com.rfsat.shimmerenact.data.bluetooth

import com.rfsat.shimmerenact.data.repository.AppLog

object ShimmerProtocol {

    // Commands (host → Shimmer)
    const val CMD_START_STREAMING: Byte        = 0x07
    const val CMD_STOP_STREAMING: Byte         = 0x20
    const val CMD_INQUIRY: Byte                = 0x01
    const val CMD_SET_SAMPLING_RATE: Byte      = 0x05

    // Responses
    const val ACK: Byte                        = 0xFF.toByte()
    const val INQUIRY_RESPONSE: Byte           = 0x02
    const val PACKET_TYPE_DATA: Byte           = 0x00

    // ─── Sensor bitmap bits ────────────────────────────────────────────────────
    // Byte 0 of the 3-byte sensor bitmap in the inquiry response:
    const val SENSOR_A_ACCEL:       Int = 0x80
    const val SENSOR_GYRO:          Int = 0x40
    const val SENSOR_MAG:           Int = 0x20
    const val SENSOR_EXG1_24BIT:    Int = 0x10
    const val SENSOR_EXG2_24BIT:    Int = 0x08
    const val SENSOR_GSR:           Int = 0x04
    const val SENSOR_EXP_BOARD_A7:  Int = 0x02
    const val SENSOR_EXP_BOARD_A0:  Int = 0x01
    const val SENSOR_PPG:           Int = SENSOR_EXP_BOARD_A0
    // Byte 1:
    const val SENSOR_b1_VBATT:      Int = 0x20
    const val SENSOR_b1_D_ACCEL:    Int = 0x10
    const val SENSOR_VBATT:         Int = SENSOR_b1_VBATT
    // Byte 2:
    const val SENSOR_b2_EXG1_16BIT: Int = 0x10
    const val SENSOR_b2_EXG2_16BIT: Int = 0x08
    const val SENSOR_EXG1_16BIT:    Int = SENSOR_b2_EXG1_16BIT
    const val SENSOR_EXG2_16BIT:    Int = SENSOR_b2_EXG2_16BIT

    // ─── Inquiry response layout ───────────────────────────────────────────────
    // [0]  0xFF  ACK
    // [1]  0x02  INQUIRY_RESPONSE
    // [2]  rate LSB
    // [3]  rate MSB
    // [4]  sensor bitmap byte 0
    // [5]  sensor bitmap byte 1
    // [6]  sensor bitmap byte 2
    // [7]  number of data channels (n)
    // [8 … 8+n-1]  channel type codes (one byte each)
    const val INQUIRY_BITMAP_OFFSET: Int = 4
    const val INQUIRY_CHANNELS_OFFSET: Int = 7  // byte index of channel count
    const val INQUIRY_MIN_LEN:       Int = 8

    // ─── Channel type codes — Shimmer3 BT protocol (verified against SDK source) ──
    // Ref: ShimmerAndroidInstrumentDriver / DataObject.java / shimmer3 firmware
    const val CH_TIMESTAMP:      Int = 0x01  // 3 bytes, always first
    // ADXL345 low-noise accelerometer (LN)
    const val CH_ACCEL_X:        Int = 0x02  // 2 bytes each
    const val CH_ACCEL_Y:        Int = 0x03
    const val CH_ACCEL_Z:        Int = 0x04
    const val CH_VBATT:          Int = 0x05  // 2 bytes
    // LSM303DLHC wide-range accelerometer (WR / digital accel)
    const val CH_DACCEL_X:       Int = 0x06  // 2 bytes each
    const val CH_DACCEL_Y:       Int = 0x07
    const val CH_DACCEL_Z:       Int = 0x08
    // LSM303DLHC magnetometer
    const val CH_MAG_X:          Int = 0x09  // 2 bytes each
    const val CH_MAG_Y:          Int = 0x0A
    const val CH_MAG_Z:          Int = 0x0B
    // MPU9150 gyroscope
    const val CH_GYRO_X:         Int = 0x0C  // 2 bytes each
    const val CH_GYRO_Y:         Int = 0x0D
    const val CH_GYRO_Z:         Int = 0x0E
    // ADS1292 ExG chip 1 — 24-bit mode
    const val CH_EXG1_STATUS:    Int = 0x0F  // 1 byte
    const val CH_EXG1_CH1_24:   Int = 0x10  // 3 bytes
    const val CH_EXG1_CH2_24:   Int = 0x11  // 3 bytes
    // ADS1292 ExG chip 2 — 24-bit mode
    const val CH_EXG2_STATUS:    Int = 0x12  // 1 byte
    const val CH_EXG2_CH1_24:   Int = 0x13  // 3 bytes
    const val CH_EXG2_CH2_24:   Int = 0x14  // 3 bytes
    // GSR+ board channels
    const val CH_GSR:            Int = 0x15  // 2 bytes — galvanic skin response
    const val CH_EXP_A7:         Int = 0x16  // 2 bytes — expansion ADC A7
    const val CH_EXP_A0:         Int = 0x17  // 2 bytes — expansion ADC A0 (PPG)
    const val CH_EXP_A6:         Int = 0x18  // 2 bytes — expansion ADC A6
    // Bridge amplifier
    const val CH_BRIDGE_HIGH:    Int = 0x1A  // 2 bytes
    const val CH_BRIDGE_LOW:     Int = 0x1B  // 2 bytes
    // Heart rate (3 bytes: 1 status + 2 data)
    const val CH_HEART_RATE:     Int = 0x1C  // 3 bytes
    // ADS1292 ExG — 16-bit mode
    const val CH_EXG1_CH1_16:   Int = 0x1D  // 2 bytes
    const val CH_EXG1_CH2_16:   Int = 0x1E  // 2 bytes
    const val CH_EXG2_CH1_16:   Int = 0x1F  // 2 bytes
    const val CH_EXG2_CH2_16:   Int = 0x20  // 2 bytes
    const val CH_EXG1_STATUS_16: Int = 0x21  // 1 byte
    const val CH_EXG2_STATUS_16: Int = 0x22  // 1 byte

    /** Byte width of each channel type code. Unknown codes default to 2 (safe). */
    fun channelWidth(code: Int): Int = when (code) {
        CH_TIMESTAMP                                                        -> 3
        CH_EXG1_STATUS, CH_EXG2_STATUS,
        CH_EXG1_STATUS_16, CH_EXG2_STATUS_16                               -> 1
        CH_EXG1_CH1_24, CH_EXG1_CH2_24,
        CH_EXG2_CH1_24, CH_EXG2_CH2_24, CH_HEART_RATE                      -> 3
        else                                                                -> 2
    }

    /** Compute exact packet data size from a list of channel codes (includes timestamp). */
    fun packetSizeFromChannels(channels: List<Int>): Int =
        channels.sumOf { channelWidth(it) }

    /**
     * Fallback packet size from bitmap when channel list is unavailable.
     * NOTE: Uses corrected b1 VBATT check (bit 5 of byte1).
     */
    fun packetDataSize(b0: Int, b1: Int, b2: Int): Int {
        var size = 3  // timestamp
        if (b0 and SENSOR_A_ACCEL      != 0) size += 6
        if (b0 and SENSOR_GYRO         != 0) size += 6
        if (b0 and SENSOR_MAG          != 0) size += 6
        if (b0 and SENSOR_EXG1_24BIT   != 0) size += 7
        if (b0 and SENSOR_EXG2_24BIT   != 0) size += 7
        if (b2 and SENSOR_b2_EXG1_16BIT != 0) size += 5
        if (b2 and SENSOR_b2_EXG2_16BIT != 0) size += 5
        if (b0 and SENSOR_GSR          != 0) size += 2
        if (b0 and SENSOR_EXP_BOARD_A0 != 0) size += 2
        if (b0 and SENSOR_EXP_BOARD_A7 != 0) size += 2
        if (b1 and SENSOR_b1_VBATT     != 0) size += 2
        if (b1 and SENSOR_b1_D_ACCEL   != 0) size += 6
        return size
    }

    // ─── Sampling rate ─────────────────────────────────────────────────────────
    fun rateToRegister(hz: Int): Int =
        (32768 / hz.coerceIn(1, 6000)).coerceIn(1, 32767)

    fun registerToHz(reg: Int): Int =
        (32768.0 / reg.coerceIn(1, 32767)).toInt().coerceIn(1, 6000)

    fun buildRateCommand(hz: Int): ByteArray {
        val reg = rateToRegister(hz)
        return byteArrayOf(
            CMD_SET_SAMPLING_RATE,
            (reg and 0xFF).toByte(),
            ((reg shr 8) and 0xFF).toByte()
        )
    }

    const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    const val RESPONSE_TIMEOUT_MS    = 3000L
    const val CHART_BUFFER_SIZE      = 512
}

// ─── Packet parser ─────────────────────────────────────────────────────────────
// Uses the channel list from the inquiry response for exact byte layout.
// Falls back to bitmap-based parsing if channel list is empty.

object ShimmerPacketParser {

    /**
     * Parse one Shimmer3 data packet.
     *
     * [raw] is the bytes AFTER the 0x00 packet-type byte.
     * If [channels] is non-empty, uses channel-list parsing (authoritative).
     * Otherwise falls back to bitmap-based parsing.
     */
    fun parse(
        raw: ByteArray,
        sensorBitmap: IntArray,
        calParams: CalibrationParams,
        channels: List<Int> = emptyList()
    ): Map<String, Double> {
        return if (channels.isNotEmpty()) {
            parseByChannelList(raw, channels, calParams)
        } else {
            parseByBitmap(raw, sensorBitmap, calParams)
        }
    }

    // ── Channel-list parser ────────────────────────────────────────────────────
    // This is authoritative — uses exactly the channels the device reported.

    private fun parseByChannelList(
        raw: ByteArray,
        channels: List<Int>,
        calParams: CalibrationParams
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        var offset = 0

        fun remaining() = raw.size - offset

        fun readU16(): Int {
            if (remaining() < 2) { offset += minOf(2, remaining()); return 0 }
            val lo = raw[offset].toInt() and 0xFF
            val hi = raw[offset + 1].toInt() and 0xFF
            offset += 2
            return lo or (hi shl 8)
        }

        fun readI16(): Int {
            val v = readU16()
            return if (v >= 0x8000) v - 0x10000 else v
        }

        fun readI24BE(): Int {
            if (remaining() < 3) { offset += minOf(3, remaining()); return 0 }
            val b0 = raw[offset].toInt() and 0xFF
            val b1 = raw[offset + 1].toInt() and 0xFF
            val b2 = raw[offset + 2].toInt() and 0xFF
            offset += 3
            val v = (b0 shl 16) or (b1 shl 8) or b2
            return if (v >= 0x800000) v - 0x1000000 else v
        }

        fun readAdc12() = readU16() and 0x0FFF

        for (ch in channels) {
            when (ch) {
                ShimmerProtocol.CH_TIMESTAMP -> {
                    // 3-byte timestamp — consume, not emitted
                    if (remaining() >= 3) offset += 3 else break
                }
                ShimmerProtocol.CH_ACCEL_X -> result["accel_x"] = calParams.calibrateAccel(readI16(), 0)
                ShimmerProtocol.CH_ACCEL_Y -> result["accel_y"] = calParams.calibrateAccel(readI16(), 1)
                ShimmerProtocol.CH_ACCEL_Z -> result["accel_z"] = calParams.calibrateAccel(readI16(), 2)
                ShimmerProtocol.CH_GYRO_X  -> result["gyro_x"] = calParams.calibrateGyro(readI16(), 0)
                ShimmerProtocol.CH_GYRO_Y  -> result["gyro_y"] = calParams.calibrateGyro(readI16(), 1)
                ShimmerProtocol.CH_GYRO_Z  -> result["gyro_z"] = calParams.calibrateGyro(readI16(), 2)
                ShimmerProtocol.CH_MAG_X   -> result["mag_x"] = calParams.calibrateMag(readI16(), 0)
                ShimmerProtocol.CH_MAG_Y   -> result["mag_y"] = calParams.calibrateMag(readI16(), 1)
                ShimmerProtocol.CH_MAG_Z   -> result["mag_z"] = calParams.calibrateMag(readI16(), 2)
                ShimmerProtocol.CH_VBATT   -> result["batt_mv"] = readAdc12() * (3000.0 / 4095.0) * 2.0
                ShimmerProtocol.CH_GSR     -> result["gsr_kohm"] = calParams.calibrateGsr(readAdc12())
                ShimmerProtocol.CH_EXP_A0  -> result["ppg_mv"] = calParams.calibratePpg(readAdc12())
                ShimmerProtocol.CH_EXP_A7  -> result["ch_a7"] = readAdc12().toDouble()
                ShimmerProtocol.CH_EXG1_STATUS,
                ShimmerProtocol.CH_EXG1_STATUS_16 -> { if (remaining() > 0) offset++ }
                ShimmerProtocol.CH_EXG1_CH1_24 -> result["exg1_ch1"] = calParams.calibrateExG(readI24BE())
                ShimmerProtocol.CH_EXG1_CH2_24 -> result["exg1_ch2"] = calParams.calibrateExG(readI24BE())
                ShimmerProtocol.CH_EXG2_STATUS,
                ShimmerProtocol.CH_EXG2_STATUS_16 -> { if (remaining() > 0) offset++ }
                ShimmerProtocol.CH_EXG2_CH1_24 -> result["exg2_ch1"] = calParams.calibrateExG(readI24BE())
                ShimmerProtocol.CH_EXG2_CH2_24 -> result["exg2_ch2"] = calParams.calibrateExG(readI24BE())
                ShimmerProtocol.CH_EXG1_CH1_16 -> result["exg1_ch1"] = calParams.calibrateExG16(readI16())
                ShimmerProtocol.CH_EXG1_CH2_16 -> result["exg1_ch2"] = calParams.calibrateExG16(readI16())
                ShimmerProtocol.CH_EXG2_CH1_16 -> result["exg2_ch1"] = calParams.calibrateExG16(readI16())
                ShimmerProtocol.CH_EXG2_CH2_16 -> result["exg2_ch2"] = calParams.calibrateExG16(readI16())
                ShimmerProtocol.CH_EXP_A6       -> { readU16() }  // consume, not modelled
                ShimmerProtocol.CH_BRIDGE_HIGH  -> { readU16() }
                ShimmerProtocol.CH_BRIDGE_LOW   -> { readU16() }
                ShimmerProtocol.CH_HEART_RATE   -> { readI24BE() }  // 3 bytes, consume
                ShimmerProtocol.CH_DACCEL_X -> {
                    val v = readI16()
                    if ("accel_x" !in result) result["accel_x"] = calParams.calibrateAccel(v, 0)
                }
                ShimmerProtocol.CH_DACCEL_Y -> {
                    val v = readI16()
                    if ("accel_y" !in result) result["accel_y"] = calParams.calibrateAccel(v, 1)
                }
                ShimmerProtocol.CH_DACCEL_Z -> {
                    val v = readI16()
                    if ("accel_z" !in result) result["accel_z"] = calParams.calibrateAccel(v, 2)
                }
                else -> {
                    val w = ShimmerProtocol.channelWidth(ch)
                    offset += minOf(w, remaining())
                    AppLog.i("PKT", "UNHANDLED channel 0x%02X (width=$w, offset now=$offset/${raw.size})".format(ch))
                }
            }
        }
        if (result.isEmpty()) {
            AppLog.w("PKT", "Channel-list parse produced EMPTY result from ${raw.size}B, ${channels.size} channels")
        }
        return result
    }

    // ── Bitmap-based parser (fallback) ─────────────────────────────────────────
    private fun parseByBitmap(
        raw: ByteArray,
        sensorBitmap: IntArray,
        calParams: CalibrationParams
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        if (raw.size < 3) return result
        var offset = 3  // skip 3-byte timestamp

        fun remaining() = raw.size - offset
        fun readAdc12(): Int {
            if (remaining() < 2) { offset += minOf(2, remaining()); return 0 }
            val lo = raw[offset].toInt() and 0xFF; val hi = raw[offset + 1].toInt() and 0xFF
            offset += 2; return (lo or (hi shl 8)) and 0x0FFF
        }
        fun readI16(): Int {
            if (remaining() < 2) { offset += minOf(2, remaining()); return 0 }
            val lo = raw[offset].toInt() and 0xFF; val hi = raw[offset + 1].toInt() and 0xFF
            offset += 2; val v = lo or (hi shl 8); return if (v >= 0x8000) v - 0x10000 else v
        }
        fun readI24BE(): Int {
            if (remaining() < 3) { offset += minOf(3, remaining()); return 0 }
            val b0 = raw[offset].toInt() and 0xFF; val b1 = raw[offset+1].toInt() and 0xFF
            val b2 = raw[offset+2].toInt() and 0xFF; offset += 3
            val v = (b0 shl 16) or (b1 shl 8) or b2; return if (v >= 0x800000) v - 0x1000000 else v
        }

        val b0 = sensorBitmap[0]; val b1 = sensorBitmap[1]; val b2 = sensorBitmap[2]

        if (b0 and ShimmerProtocol.SENSOR_A_ACCEL != 0) {
            result["accel_x"] = calParams.calibrateAccel(readI16(), 0)
            result["accel_y"] = calParams.calibrateAccel(readI16(), 1)
            result["accel_z"] = calParams.calibrateAccel(readI16(), 2)
        }
        if (b0 and ShimmerProtocol.SENSOR_GYRO != 0) {
            result["gyro_x"] = calParams.calibrateGyro(readI16(), 0)
            result["gyro_y"] = calParams.calibrateGyro(readI16(), 1)
            result["gyro_z"] = calParams.calibrateGyro(readI16(), 2)
        }
        if (b0 and ShimmerProtocol.SENSOR_MAG != 0) {
            result["mag_x"] = calParams.calibrateMag(readI16(), 0)
            result["mag_y"] = calParams.calibrateMag(readI16(), 1)
            result["mag_z"] = calParams.calibrateMag(readI16(), 2)
        }
        if (b0 and ShimmerProtocol.SENSOR_EXG1_24BIT != 0) {
            if (remaining() > 0) offset++  // status byte
            result["exg1_ch1"] = calParams.calibrateExG(readI24BE())
            result["exg1_ch2"] = calParams.calibrateExG(readI24BE())
        } else if (b2 and ShimmerProtocol.SENSOR_b2_EXG1_16BIT != 0) {
            if (remaining() > 0) offset++
            result["exg1_ch1"] = calParams.calibrateExG16(readI16())
            result["exg1_ch2"] = calParams.calibrateExG16(readI16())
        }
        if (b0 and ShimmerProtocol.SENSOR_EXG2_24BIT != 0) {
            if (remaining() > 0) offset++
            result["exg2_ch1"] = calParams.calibrateExG(readI24BE())
            result["exg2_ch2"] = calParams.calibrateExG(readI24BE())
        } else if (b2 and ShimmerProtocol.SENSOR_b2_EXG2_16BIT != 0) {
            if (remaining() > 0) offset++
            result["exg2_ch1"] = calParams.calibrateExG16(readI16())
            result["exg2_ch2"] = calParams.calibrateExG16(readI16())
        }
        if (b0 and ShimmerProtocol.SENSOR_GSR          != 0) result["gsr_kohm"] = calParams.calibrateGsr(readAdc12())
        if (b0 and ShimmerProtocol.SENSOR_EXP_BOARD_A0 != 0) result["ppg_mv"]   = calParams.calibratePpg(readAdc12())
        if (b1 and ShimmerProtocol.SENSOR_b1_VBATT     != 0) result["batt_mv"]  = readAdc12() * (3000.0 / 4095.0) * 2.0

        return result
    }
}

// ─── Calibration parameters ────────────────────────────────────────────────────

data class CalibrationParams(
    val accelOffset: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0),
    val accelSens:   DoubleArray = doubleArrayOf(256.0, 256.0, 256.0),
    val gyroOffset:  DoubleArray = doubleArrayOf(0.0, 0.0, 0.0),
    val gyroSens:    DoubleArray = doubleArrayOf(65.5, 65.5, 65.5),
    val magSens:     DoubleArray = doubleArrayOf(0.909, 0.909, 1.020),
    val exgVref:     Double = 2.42,
    val exgGain:     Double = 6.0,
    val gsrRange:    Int    = 0,
    val adcVref:     Double = 3000.0
) {
    fun calibrateAccel(raw: Int, axis: Int): Double =
        ((raw - accelOffset[axis]) / accelSens[axis]) * 9.80665
    fun calibrateGyro(raw: Int, axis: Int): Double =
        (raw - gyroOffset[axis]) / gyroSens[axis]
    fun calibrateMag(raw: Int, axis: Int): Double =
        raw * magSens[axis]
    fun calibrateExG(raw24: Int): Double =
        raw24 * (exgVref / (exgGain * 8388607.0)) * 1000.0
    fun calibrateExG16(raw16: Int): Double =
        raw16 * (exgVref / (exgGain * 32767.0)) * 1000.0
    fun calibrateGsr(adcRaw: Int): Double {
        val vGsr = adcRaw * (adcVref / 4095.0)
        val rfKOhm = when (gsrRange) { 0 -> 40.2; 1 -> 287.0; 2 -> 1000.0; else -> 3300.0 }
        val vDiff = vGsr - adcVref / 2.0
        if (vDiff <= 0.0) return 9999.0
        return (rfKOhm * (adcVref / vDiff - 1.0)).coerceIn(0.0, 9999.0)
    }
    fun calibratePpg(adcRaw: Int): Double = adcRaw * (adcVref / 4095.0)
}
