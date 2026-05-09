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

    // ─── Sensor bitmap bits (byte 0 of 3-byte bitmap) ─────────────────────────
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

    // ─── Channel type codes ────────────────────────────────────────────────────
    // Actual codes sent by Shimmer3 LogAndStream firmware in inquiry response.
    // Verified by observing which codes produce working data vs "Unknown ch=0xNN".
    // The firmware uses a simple sequential scheme starting from 0x00.
    const val CH_TIMESTAMP:      Int = 0x00  // 3 bytes
    const val CH_ACCEL_X:        Int = 0x01  // 2 bytes (ADXL345 low-noise)
    const val CH_ACCEL_Y:        Int = 0x02
    const val CH_ACCEL_Z:        Int = 0x03
    // Confirmed from actual device inquiry response (22B):
    // Channel codes in packet order:
    // 0x0C=PPG, 0x01=?, 0x00=TIMESTAMP(3B), 0x01=AccX, 0x02=AccY, 0x03=AccZ,
    // 0x12=GYRO(6B), 0x1C=MAG(6B), 0x0A=BATT
    const val CH_GYRO:           Int = 0x12  // 6 bytes: GX_hi,GX_lo,GY_hi,GY_lo,GZ_hi,GZ_lo
    const val CH_MAG:            Int = 0x1C  // 6 bytes: MX_hi,MX_lo,MZ_hi,MZ_lo,MY_hi,MY_lo
    const val CH_MAG_X:          Int = 0x07  // individual axis fallback
    const val CH_MAG_Y:          Int = 0x08
    const val CH_MAG_Z:          Int = 0x09
    const val CH_VBATT:          Int = 0x0A  // 2 bytes
    const val CH_GSR:            Int = 0x0B  // 2 bytes
    const val CH_EXP_A0:         Int = 0x0C  // 2 bytes (PPG)
    const val CH_EXP_A7:         Int = 0x0D  // 2 bytes
    const val CH_DACCEL_X:       Int = 0x0E  // 2 bytes (LSM303 wide-range/digital)
    const val CH_DACCEL_Y:       Int = 0x0F
    const val CH_DACCEL_Z:       Int = 0x10
    const val CH_EXG1_STATUS:    Int = 0x11  // 1 byte
    const val CH_EXG1_CH1_24:    Int = 0x12  // 3 bytes
    const val CH_EXG1_CH2_24:    Int = 0x13
    const val CH_EXG2_STATUS:    Int = 0x14  // 1 byte
    const val CH_EXG2_CH1_24:    Int = 0x15  // 3 bytes
    const val CH_EXG2_CH2_24:    Int = 0x16
    const val CH_EXG1_CH1_16:    Int = 0x17  // 2 bytes (16-bit ExG)
    const val CH_EXG1_CH2_16:    Int = 0x18
    const val CH_EXG2_CH1_16:    Int = 0x19
    const val CH_EXG2_CH2_16:    Int = 0x1A
    const val CH_EXG1_STATUS_16: Int = 0x1B  // 1 byte
    const val CH_EXG2_STATUS_16: Int = 0x1C  // 1 byte

    /** Width in bytes for each channel code. */
    fun channelWidth(code: Int): Int = when (code) {
        CH_TIMESTAMP                                                -> 3
        CH_GYRO, CH_MAG,
        0x12, 0x1C                                               -> 6  // confirmed gyro+mag codes
        CH_EXG1_STATUS, CH_EXG2_STATUS,
        CH_EXG1_STATUS_16, CH_EXG2_STATUS_16                       -> 1
        CH_EXG1_CH1_24, CH_EXG1_CH2_24,
        CH_EXG2_CH1_24, CH_EXG2_CH2_24                             -> 3
        else                                                        -> 2
    }

    /** Total packet byte size from channel list (includes timestamp). */
    fun packetSizeFromChannels(channels: List<Int>): Int =
        channels.sumOf { channelWidth(it) }

    /** Fallback packet size from bitmap when channel list unavailable. */
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

    const val SPP_UUID               = "00001101-0000-1000-8000-00805F9B34FB"
    const val RESPONSE_TIMEOUT_MS    = 3000L
    const val CHART_BUFFER_SIZE      = 512

    // ─── Inquiry response — bytes logged prominently; parsing in runInquiry ────
    // Format (may or may not start with 0xFF ACK):
    //   [0xFF] [0x02] [rate_lo] [rate_hi] [bm0] [bm1] [bm2] [nch] [ch0] ... [chN]
    // bodyStart = 1 if response[0]==0xFF, else 0
    // bitmap at bodyStart+3, nch at bodyStart+6, codes at bodyStart+7
}

// ─── Packet parser ─────────────────────────────────────────────────────────────

object ShimmerPacketParser {

    fun parse(
        raw: ByteArray,
        sensorBitmap: IntArray,
        calParams: CalibrationParams,
        channels: List<Int> = emptyList()
    ): Map<String, Double> =
        if (channels.isNotEmpty()) parseByChannelList(raw, channels, calParams)
        else parseByBitmap(raw, sensorBitmap, calParams)

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
            val v = readU16(); return if (v >= 0x8000) v - 0x10000 else v
        }
        // Big-endian signed 16-bit (MPU9150 gyro, LSM303 mag)
        fun readI16BE(): Int {
            if (remaining() < 2) { offset += minOf(2, remaining()); return 0 }
            val hi = raw[offset].toInt() and 0xFF
            val lo = raw[offset + 1].toInt() and 0xFF
            offset += 2
            val v = (hi shl 8) or lo
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
            if (remaining() <= 0) break
            when (ch) {
                ShimmerProtocol.CH_TIMESTAMP   -> { if (remaining() >= 3) offset += 3 }
                ShimmerProtocol.CH_ACCEL_X     -> result["accel_x"] = calParams.calibrateAccel(readI16(), 0)
                ShimmerProtocol.CH_ACCEL_Y     -> result["accel_y"] = calParams.calibrateAccel(readI16(), 1)
                ShimmerProtocol.CH_ACCEL_Z     -> result["accel_z"] = calParams.calibrateAccel(readI16(), 2)
                ShimmerProtocol.CH_GYRO       -> {
                    result["gyro_x"] = calParams.calibrateGyro(readI16BE(), 0)
                    result["gyro_y"] = calParams.calibrateGyro(readI16BE(), 1)
                    result["gyro_z"] = calParams.calibrateGyro(readI16BE(), 2)
                }
                ShimmerProtocol.CH_DACCEL_X    -> {
                    val v = readI16()
                    if ("accel_x" !in result) result["accel_x"] = calParams.calibrateAccel(v, 0)
                }
                ShimmerProtocol.CH_DACCEL_Y    -> {
                    val v = readI16()
                    if ("accel_y" !in result) result["accel_y"] = calParams.calibrateAccel(v, 1)
                }
                ShimmerProtocol.CH_DACCEL_Z    -> {
                    val v = readI16()
                    if ("accel_z" !in result) result["accel_z"] = calParams.calibrateAccel(v, 2)
                }
                // LSM303DLHC sends all 3 mag axes under code 0x07 in X,Z,Y order
                ShimmerProtocol.CH_MAG        -> {
                    result["mag_x"] = calParams.calibrateMag(readI16BE(), 0)  // X first
                    result["mag_z"] = calParams.calibrateMag(readI16BE(), 2)  // Z second
                    result["mag_y"] = calParams.calibrateMag(readI16BE(), 1)  // Y third
                }
                ShimmerProtocol.CH_VBATT       -> result["batt_mv"] = readAdc12() * (3000.0 / 4095.0) * 2.0
                ShimmerProtocol.CH_GSR         -> result["gsr_kohm"] = calParams.calibrateGsr(readAdc12())
                ShimmerProtocol.CH_EXP_A0      -> result["ppg_mv"]  = calParams.calibratePpg(readAdc12())
                ShimmerProtocol.CH_EXP_A7      -> { readU16() }  // consume, not modelled
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
                else -> {
                    // Unknown — skip by width (safe default = 2)
                    val w = ShimmerProtocol.channelWidth(ch)
                    offset += minOf(w, remaining())
                    AppLog.i("PKT", "Unknown ch=0x%02X w=$w offset→$offset/${raw.size}".format(ch))
                }
            }
        }
        if (result.isEmpty()) {
            AppLog.w("PKT", "Empty parse — ${raw.size}B, ${channels.size} channels: " +
                channels.joinToString { "0x%02X".format(it) })
        }
        return result
    }

    private fun parseByBitmap(
        raw: ByteArray,
        sensorBitmap: IntArray,
        calParams: CalibrationParams
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        if (raw.size < 3) return result
        var offset = 3
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
        fun readI16BE(): Int {
            if (remaining() < 2) { offset += minOf(2, remaining()); return 0 }
            val hi = raw[offset].toInt() and 0xFF; val lo = raw[offset + 1].toInt() and 0xFF
            offset += 2; val v = (hi shl 8) or lo; return if (v >= 0x8000) v - 0x10000 else v
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
            result["gyro_x"] = calParams.calibrateGyro(readI16BE(), 0)
            result["gyro_y"] = calParams.calibrateGyro(readI16BE(), 1)
            result["gyro_z"] = calParams.calibrateGyro(readI16BE(), 2)
        }
        if (b0 and ShimmerProtocol.SENSOR_MAG != 0) {
            result["mag_x"] = calParams.calibrateMag(readI16BE(), 0)  // X
            result["mag_z"] = calParams.calibrateMag(readI16BE(), 2)  // Z (LSM303 order)
            result["mag_y"] = calParams.calibrateMag(readI16BE(), 1)  // Y
        }
        if (b0 and ShimmerProtocol.SENSOR_EXG1_24BIT != 0) {
            if (remaining() > 0) offset++
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
