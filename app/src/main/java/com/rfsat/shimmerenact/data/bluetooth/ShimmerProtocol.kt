package com.rfsat.shimmerenact.data.bluetooth

import com.rfsat.shimmerenact.data.repository.AppLog

object ShimmerProtocol {

    // ─── Commands ─────────────────────────────────────────────────────────────
    const val CMD_INQUIRY: Byte             = 0x01
    const val CMD_SET_SAMPLING_RATE: Byte   = 0x05
    const val CMD_START_STREAMING: Byte     = 0x07
    const val CMD_STOP_STREAMING: Byte      = 0x20

    // ─── Response codes ───────────────────────────────────────────────────────
    const val ACK: Byte                     = 0xFF.toByte()
    const val INQUIRY_RESPONSE: Byte        = 0x02
    const val PACKET_TYPE_DATA: Byte        = 0x00

    // ─── Sensor bitmap bits ───────────────────────────────────────────────────
    const val SENSOR_A_ACCEL:       Int = 0x80
    const val SENSOR_GYRO:          Int = 0x40
    const val SENSOR_MAG:           Int = 0x20
    const val SENSOR_EXG1_24BIT:    Int = 0x10
    const val SENSOR_EXG2_24BIT:    Int = 0x08
    const val SENSOR_GSR:           Int = 0x04
    const val SENSOR_EXP_BOARD_A7:  Int = 0x02
    const val SENSOR_EXP_BOARD_A0:  Int = 0x01
    const val SENSOR_b1_VBATT:      Int = 0x20
    const val SENSOR_b1_D_ACCEL:    Int = 0x10
    const val SENSOR_VBATT:         Int = SENSOR_b1_VBATT
    const val SENSOR_b2_EXG1_16BIT: Int = 0x10
    const val SENSOR_b2_EXG2_16BIT: Int = 0x08
    const val SENSOR_EXG1_16BIT:    Int = SENSOR_b2_EXG1_16BIT
    const val SENSOR_EXG2_16BIT:    Int = SENSOR_b2_EXG2_16BIT

    // ─── Channel codes ────────────────────────────────────────────────────────
    // Source: Official Shimmer3 protocol (matmont/shimmer3 util.py)
    // Verified against actual device inquiry response bytes.
    const val CH_TIMESTAMP:     Int = 0x00  // u24  3B LE
    const val CH_ACCEL_LN_X:   Int = 0x01  // i16  2B LE  ADXL345 low-noise
    const val CH_ACCEL_LN_Y:   Int = 0x02
    const val CH_ACCEL_LN_Z:   Int = 0x03
    const val CH_ACCEL_X:      Int = 0x01  // alias
    const val CH_ACCEL_Y:      Int = 0x02
    const val CH_ACCEL_Z:      Int = 0x03
    const val CH_VBATT:        Int = 0x04  // u12  2B LE
    const val CH_GSR:          Int = 0x05  // u16  2B LE
    const val CH_EXT_ADC_CH7:  Int = 0x06  // u12  2B LE
    const val CH_EXT_ADC_CH6:  Int = 0x07  // u12  2B LE
    const val CH_EXT_ADC_CH15: Int = 0x08  // u12  2B LE
    const val CH_INT_ADC_CH1:  Int = 0x09  // u12  2B LE
    const val CH_INT_ADC_CH12: Int = 0x0A  // u12  2B LE
    const val CH_INT_ADC_CH13: Int = 0x0B  // u12  2B LE  PPG on GSR+
    const val CH_EXP_A0:       Int = 0x0B  // alias PPG
    const val CH_INT_ADC_CH14: Int = 0x0C  // u12  2B LE
    const val CH_ACCEL_WR_X:   Int = 0x0D  // i16  2B LE  LSM303 wide-range
    const val CH_ACCEL_WR_Y:   Int = 0x0E
    const val CH_ACCEL_WR_Z:   Int = 0x0F
    const val CH_EXG1_STATUS:  Int = 0x10  // u8   1B
    const val CH_EXG1_CH1_24:  Int = 0x11  // i24* 3B BE
    const val CH_EXG1_CH2_24:  Int = 0x12  // i24* 3B BE
    const val CH_EXG2_STATUS:  Int = 0x13  // u8   1B
    const val CH_EXG2_CH1_24:  Int = 0x14  // i24* 3B BE
    const val CH_EXG2_CH2_24:  Int = 0x15  // i24* 3B BE
    const val CH_GYRO_X:       Int = 0x16  // i16* 2B BE  MPU9150
    const val CH_GYRO_Y:       Int = 0x17
    const val CH_GYRO_Z:       Int = 0x18
    const val CH_MAG_X:        Int = 0x19  // i16* 2B BE  LSM303 sends X,Z,Y order
    const val CH_MAG_Y:        Int = 0x1A
    const val CH_MAG_Z:        Int = 0x1B
    const val CH_ACCEL_WR2_X:  Int = 0x1C  // i16  2B LE  alt WR accel
    const val CH_ACCEL_WR2_Y:  Int = 0x1D
    const val CH_ACCEL_WR2_Z:  Int = 0x1E
    const val CH_EXG1_CH1_16:  Int = 0x1F  // i16* 2B BE
    const val CH_EXG1_CH2_16:  Int = 0x20
    const val CH_EXG2_CH1_16:  Int = 0x21
    const val CH_EXG2_CH2_16:  Int = 0x22

    // ─── Timing ───────────────────────────────────────────────────────────────
    const val RESPONSE_TIMEOUT_MS: Long   = 3000L
    const val CLOCK_FREQ_HZ:       Double = 32768.0

    fun registerToHz(reg: Int): Int =
        if (reg == 0) 0 else (CLOCK_FREQ_HZ / reg + 0.5).toInt()

    fun buildRateCommand(hz: Int): ByteArray {
        val reg = (CLOCK_FREQ_HZ / hz).toInt().coerceIn(1, 65535)
        return byteArrayOf(CMD_SET_SAMPLING_RATE, (reg and 0xFF).toByte(), (reg shr 8).toByte())
    }

    // ─── Channel width in bytes ───────────────────────────────────────────────
    fun channelWidth(ch: Int): Int = when (ch) {
        CH_TIMESTAMP                                -> 3
        CH_EXG1_STATUS, CH_EXG2_STATUS             -> 1
        CH_EXG1_CH1_24, CH_EXG1_CH2_24,
        CH_EXG2_CH1_24, CH_EXG2_CH2_24             -> 3
        // Empirically verified on SR48-5-0: 0x12=Gyro(6B), 0x1C=Mag(6B)
        0x12, 0x1C                                  -> 6
        else                                        -> 2
    }

    // ─── Packet size from channel list ────────────────────────────────────────
    fun packetSizeFromChannels(channels: List<Int>): Int =
        channels.sumOf { channelWidth(it) }

    // ─── Packet size from bitmap fallback ────────────────────────────────────
    fun packetDataSize(b0: Int, b1: Int, b2: Int): Int {
        var size = 3
        if (b0 and SENSOR_A_ACCEL       != 0) size += 6
        if (b0 and SENSOR_GYRO          != 0) size += 6
        if (b0 and SENSOR_MAG           != 0) size += 6
        if (b0 and SENSOR_GSR           != 0) size += 2
        if (b0 and SENSOR_EXP_BOARD_A0  != 0) size += 2
        if (b0 and SENSOR_EXP_BOARD_A7  != 0) size += 2
        if (b1 and SENSOR_b1_VBATT      != 0) size += 2
        if (b1 and SENSOR_b1_D_ACCEL    != 0) size += 6
        if (b0 and SENSOR_EXG1_24BIT    != 0) size += 7
        if (b0 and SENSOR_EXG2_24BIT    != 0) size += 7
        if (b2 and SENSOR_b2_EXG1_16BIT != 0) size += 5
        if (b2 and SENSOR_b2_EXG2_16BIT != 0) size += 5
        return size
    }

    // ─── Calibration parameters ───────────────────────────────────────────────
    data class CalibrationParams(
        val accelSens:   Double      = 83.0,
        val accelOffset: DoubleArray = doubleArrayOf(2081.0, 2081.0, 2087.0),
        val gyroSens:    Double      = 65.5,
        val gyroOffset:  DoubleArray = doubleArrayOf(0.0, 0.0, 0.0),
        val magSens:     Double      = 1100.0,
        val magOffset:   DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)
    ) {
        fun calibrateAccel(raw: Int, axis: Int) = (raw - accelOffset[axis]) / accelSens
        fun calibrateGyro(raw: Int, axis: Int)  = (raw - gyroOffset[axis])  / gyroSens
        fun calibrateMag(raw: Int, axis: Int)   = (raw - magOffset[axis])   / magSens
        // Battery: ADC reads through a ÷2 voltage divider, 3.0V reference
        // Vbatt (mV) = (raw / 4095) × 3000 × 2
        fun calibrateBatt(raw: Int): Double = (raw / 4095.0) * 3000.0 * 2.0
        fun calibrateGsr(raw: Int): Double {
            val r = if (raw == 0) 1.0 else raw.toDouble()
            return (1.0 / ((r / 4096.0 * 3.0) / 1000.0 + 1.0e-9)) / 1000.0
        }
        fun calibratePpg(raw: Int): Double = raw * (3.0 / 4095.0) * 1000.0
    }

    // ─── Packet parser ────────────────────────────────────────────────────────
    fun parsePacket(
        raw: ByteArray,
        channels: List<Int>,
        calParams: CalibrationParams
    ): Map<String, Double> =
        if (channels.isNotEmpty()) parseByChannelList(raw, channels, calParams)
        else emptyMap()

    private fun parseByChannelList(
        raw: ByteArray,
        channels: List<Int>,
        calParams: CalibrationParams
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        var offset = 0

        fun remaining() = raw.size - offset

        // Per official shimmer.py: timestamp is ALWAYS the first 3 bytes,
        // regardless of what appears first in the channel list.
        // Read it now, then parse the remaining channels in order,
        // skipping any 0x00 (CH_TIMESTAMP) entries in the list.
        fun readU8(): Int {
            if (remaining() < 1) { offset++; return 0 }
            return raw[offset++].toInt() and 0xFF
        }
        fun readU16(): Int {
            if (remaining() < 2) { offset += minOf(2, remaining()); return 0 }
            val lo = raw[offset].toInt() and 0xFF; val hi = raw[offset + 1].toInt() and 0xFF
            offset += 2; return lo or (hi shl 8)
        }
        fun readI16(): Int { val v = readU16(); return if (v >= 0x8000) v - 0x10000 else v }
        fun readI16BE(): Int {
            if (remaining() < 2) { offset += minOf(2, remaining()); return 0 }
            val hi = raw[offset].toInt() and 0xFF; val lo = raw[offset + 1].toInt() and 0xFF
            offset += 2; val v = (hi shl 8) or lo; return if (v >= 0x8000) v - 0x10000 else v
        }
        fun readU24(): Long {
            if (remaining() < 3) { offset += minOf(3, remaining()); return 0L }
            val b0 = raw[offset].toInt() and 0xFF; val b1 = raw[offset+1].toInt() and 0xFF
            val b2 = raw[offset+2].toInt() and 0xFF; offset += 3
            return (b0 or (b1 shl 8) or (b2 shl 16)).toLong()
        }
        fun readI24BE(): Int {
            if (remaining() < 3) { offset += minOf(3, remaining()); return 0 }
            val b0 = raw[offset].toInt() and 0xFF; val b1 = raw[offset+1].toInt() and 0xFF
            val b2 = raw[offset+2].toInt() and 0xFF; offset += 3
            val v = (b0 shl 16) or (b1 shl 8) or b2
            return if (v >= 0x800000) v - 0x1000000 else v
        }
        fun readAdc12() = readU16() and 0x0FFF

        // Always read timestamp first (implicit, always at bytes 0-2)
        if (raw.size >= 3) {
            val ts = readU24()
            result["timestamp_ticks"] = ts.toDouble()
        }

        var unknownLogged = false
        for (ch in channels) {
            if (ch == CH_TIMESTAMP) continue  // already read above
            when (ch) {
                // ── Empirical codes for SR48-5-0 firmware — MUST be first ──────
                // These codes (0x12, 0x1C, 0x0A) coincide with official constants
                // for OTHER sensors, so empirical entries must precede them to win
                // the first-match rule of Kotlin when().
                0x12 -> {  // Gyro XYZ block (6B BE) — verified empirically
                    result["gyro_x"] = calParams.calibrateGyro(readI16BE(), 0)
                    result["gyro_y"] = calParams.calibrateGyro(readI16BE(), 1)
                    result["gyro_z"] = calParams.calibrateGyro(readI16BE(), 2)
                }
                0x1C -> {  // Mag XZY block (6B BE, LSM303 sends X,Z,Y order) — empirical
                    result["mag_x"]  = calParams.calibrateMag(readI16BE(), 0)
                    result["mag_z"]  = calParams.calibrateMag(readI16BE(), 2)
                    result["mag_y"]  = calParams.calibrateMag(readI16BE(), 1)
                }
                0x0A -> result["batt_mv"]  = calParams.calibrateBatt(readAdc12())  // empirical
                // ── Standard channel codes ────────────────────────────────────
                CH_TIMESTAMP     -> {}  // unreachable — handled above
                CH_ACCEL_LN_X   -> result["accel_x"]   = calParams.calibrateAccel(readI16(), 0)
                CH_ACCEL_LN_Y   -> result["accel_y"]   = calParams.calibrateAccel(readI16(), 1)
                CH_ACCEL_LN_Z   -> result["accel_z"]   = calParams.calibrateAccel(readI16(), 2)
                CH_VBATT        -> result["batt_mv"]    = calParams.calibrateBatt(readAdc12())
                CH_GSR          -> result["gsr_kohm"]   = calParams.calibrateGsr(readU16())
                CH_EXT_ADC_CH7  -> { readU16() }
                CH_EXT_ADC_CH6  -> { readU16() }
                CH_EXT_ADC_CH15 -> { readU16() }
                CH_INT_ADC_CH1  -> { readU16() }
                CH_INT_ADC_CH12 -> { readU16() }
                CH_INT_ADC_CH13 -> result["ppg_mv"]    = calParams.calibratePpg(readAdc12())
                CH_INT_ADC_CH14 -> result["ppg_mv"]    = calParams.calibratePpg(readAdc12())
                CH_ACCEL_WR_X   -> result["accel_wr_x"] = calParams.calibrateAccel(readI16(), 0)
                CH_ACCEL_WR_Y   -> result["accel_wr_y"] = calParams.calibrateAccel(readI16(), 1)
                CH_ACCEL_WR_Z   -> result["accel_wr_z"] = calParams.calibrateAccel(readI16(), 2)
                CH_EXG1_STATUS  -> { readU8() }
                CH_EXG1_CH1_24  -> result["exg1_ch1"]  = readI24BE().toDouble()
                CH_EXG1_CH2_24  -> result["exg1_ch2"]  = readI24BE().toDouble()
                CH_EXG2_STATUS  -> { readU8() }
                CH_EXG2_CH1_24  -> result["exg2_ch1"]  = readI24BE().toDouble()
                CH_EXG2_CH2_24  -> result["exg2_ch2"]  = readI24BE().toDouble()
                CH_GYRO_X       -> result["gyro_x"]    = calParams.calibrateGyro(readI16BE(), 0)
                CH_GYRO_Y       -> result["gyro_y"]    = calParams.calibrateGyro(readI16BE(), 1)
                CH_GYRO_Z       -> result["gyro_z"]    = calParams.calibrateGyro(readI16BE(), 2)
                // LSM303 sends mag in order X, Z, Y
                CH_MAG_X        -> result["mag_x"]     = calParams.calibrateMag(readI16BE(), 0)
                CH_MAG_Y        -> result["mag_z"]     = calParams.calibrateMag(readI16BE(), 2)
                CH_MAG_Z        -> result["mag_y"]     = calParams.calibrateMag(readI16BE(), 1)
                CH_ACCEL_WR2_X  -> { readI16() }
                CH_ACCEL_WR2_Y  -> { readI16() }
                CH_ACCEL_WR2_Z  -> { readI16() }
                CH_EXG1_CH1_16  -> result["exg1_ch1"]  = readI16BE().toDouble()
                CH_EXG1_CH2_16  -> result["exg1_ch2"]  = readI16BE().toDouble()
                CH_EXG2_CH1_16  -> result["exg2_ch1"]  = readI16BE().toDouble()
                CH_EXG2_CH2_16  -> result["exg2_ch2"]  = readI16BE().toDouble()
                else -> {
                    if (!unknownLogged) {
                        AppLog.i("PKT", "Unknown ch=0x%02X w=2 offset=$offset/${raw.size}".format(ch))
                        unknownLogged = true
                    }
                    offset += minOf(2, remaining())
                }
            }
        }
        return result
    }
    // ─── Bluetooth ────────────────────────────────────────────────────────────
    const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

    // ─── UI constants ─────────────────────────────────────────────────────────
    const val CHART_BUFFER_SIZE = 500

    // ─── ShimmerPacketParser (thin wrapper kept for API compatibility) ────────
    object ShimmerPacketParser {
        fun parse(
            raw: ByteArray,
            channels: List<Int>,
            calParams: CalibrationParams
        ): Map<String, Double> = parsePacket(raw, channels, calParams)
    }

}
