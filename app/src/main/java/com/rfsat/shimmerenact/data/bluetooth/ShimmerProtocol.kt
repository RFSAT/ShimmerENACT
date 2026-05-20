package com.rfsat.shimmerenact.data.bluetooth

// ─── Shimmer3 BT Classic Protocol Constants ───────────────────────────────────
//
// Based on Shimmer3 BT firmware protocol (FW ver ≥ 0.5)
// Reference: https://shimmersensing.com/support/shimmer-user-manual/
//

object ShimmerProtocol {

    // Commands (host → Shimmer)
    const val CMD_START_STREAMING: Byte       = 0x07
    const val CMD_STOP_STREAMING: Byte        = 0x20
    const val CMD_INQUIRY: Byte               = 0x01
    const val CMD_GET_SAMPLING_RATE: Byte     = 0x03
    const val CMD_SET_SAMPLING_RATE: Byte     = 0x05
    const val CMD_GET_CONFIG_SETUP_BYTES: Byte= 0x22
    const val CMD_SET_SENSORS: Byte           = 0x08
    const val CMD_GET_CALIBRATION_DUMP: Byte  = 0x17
    const val CMD_GET_FW_VERSION: Byte        = 0x2E
    const val CMD_RESET_TO_DEFAULT: Byte      = 0x5A

    // Responses / ACKs (Shimmer → host)
    const val ACK: Byte                       = 0xFF.toByte()
    const val DATA_PACKET_START: Byte         = 0x00

    // Packet start byte for data
    const val PACKET_TYPE_DATA: Byte          = 0x00

    // Sensor bitmap bytes (3-byte sensor bitmap for Shimmer3)
    const val SENSOR_A_ACCEL: Int             = 0x80       // byte 0
    const val SENSOR_GYRO: Int                = 0x40
    const val SENSOR_MAG: Int                 = 0x20
    const val SENSOR_EXG1_24BIT: Int          = 0x10
    const val SENSOR_EXG2_24BIT: Int          = 0x08
    const val SENSOR_GSR: Int                 = 0x04
    const val SENSOR_EXP_BOARD_A7: Int        = 0x02
    const val SENSOR_EXP_BOARD_A0: Int        = 0x01
    const val SENSOR_PPG: Int                 = SENSOR_EXP_BOARD_A0 // PPG on A0
    const val SENSOR_STRAIN_GAUGE: Int        = 0x8000    // byte 1 bit 15
    const val SENSOR_HR: Int                  = 0x4000    // byte 1 bit 14
    const val SENSOR_VBATT: Int               = 0x2000    // byte 1 bit 13
    const val SENSOR_D_ACCEL: Int             = 0x1000    // byte 1 bit 12 (LSM303)
    const val SENSOR_EXT_A7: Int              = 0x0200    // byte 1 bit 9
    const val SENSOR_EXT_A6: Int              = 0x0100    // byte 1 bit 8
    const val SENSOR_EXG1_16BIT: Int          = 0x0010    // byte 2 bit 4
    const val SENSOR_EXG2_16BIT: Int          = 0x0008    // byte 2 bit 3

    // Sampling rate codes (256/N)
    val SAMPLING_RATES = mapOf(
        1    to 0xFF00,
        10   to 0x1980,
        51   to 0x0500,
        102  to 0x0280,
        204  to 0x0140,
        256  to 0x0100,
        512  to 0x0080,
        1024 to 0x0040
    )

    // RFCOMM/SPP UUID for Bluetooth Serial Port Profile
    const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

    // Packet timeouts
    fun registerToHz(reg: Int): Int =
        (32768.0 / reg.coerceIn(1, 32767)).toInt().coerceIn(1, 6000)

    const val RESPONSE_TIMEOUT_MS = 3000L
    const val STREAM_IDLE_TIMEOUT_MS = 5000L

    // Ring buffer size for charting (number of samples kept in memory)
    const val CHART_BUFFER_SIZE = 512
}

// ─── Raw packet → calibrated values ──────────────────────────────────────────

object ShimmerPacketParser {

    /**
     * Parses a raw Shimmer3 data packet byte array into a map of signal keys → doubles.
     *
     * The byte layout depends on which sensors are enabled (sensorBitmap).
     * Shimmer3 uses little-endian, 2-byte (or 3-byte for 24-bit ExG) signed integers.
     *
     * This implementation covers the sensors used by GSR+ (SR48-5-0) and EXG (SR47-6-0).
     *
     * @param raw        Full raw packet bytes starting after the packet-type byte
     * @param sensorBitmap 3-byte bitmap from inquiry response
     * @param calParams  Optional calibration parameters from device
     * @return Map of signal key → calibrated double value
     */
    fun parse(
        raw: ByteArray,
        sensorBitmap: IntArray,          // [byte0, byte1, byte2]
        calParams: CalibrationParams,
        channels: List<Int> = emptyList()  // from inquiry channel list; empty = use bitmap
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        var offset = 0

        // Shimmer3 always prepends a 3-byte timestamp (little-endian, 32768 Hz clock)
        if (raw.size < 3) return result
        val shimmerTs = (raw[0].toInt() and 0xFF) or
                        ((raw[1].toInt() and 0xFF) shl 8) or
                        ((raw[2].toInt() and 0xFF) shl 16)
        offset = 3

        fun readU12(): Int {
            if (offset + 1 >= raw.size) return 0
            val v = ((raw[offset].toInt() and 0xFF) or ((raw[offset + 1].toInt() and 0xFF) shl 8)) and 0x0FFF
            offset += 2; return v
        }
        fun readI16(): Int {
            if (offset + 1 >= raw.size) return 0
            val v = (raw[offset].toInt() and 0xFF) or ((raw[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
            return if (v >= 0x8000) v - 0x10000 else v
        }
        fun readI24(): Int {
            if (offset + 2 >= raw.size) return 0
            val v = (raw[offset].toInt() and 0xFF) or
                    ((raw[offset + 1].toInt() and 0xFF) shl 8) or
                    ((raw[offset + 2].toInt() and 0xFF) shl 16)
            offset += 3
            return if (v >= 0x800000) v - 0x1000000 else v
        }

        val b0 = sensorBitmap[0]; val b1 = sensorBitmap[1]; val b2 = sensorBitmap[2]

        // Analog accel (ADXL345) — 12-bit, 3 channels
        if (b0 and ShimmerProtocol.SENSOR_A_ACCEL != 0) {
            val ax = readU12(); val ay = readU12(); val az = readU12()
            result["accel_x"] = calParams.calibrateAccel(ax, 0)
            result["accel_y"] = calParams.calibrateAccel(ay, 1)
            result["accel_z"] = calParams.calibrateAccel(az, 2)
        }
        // Gyro (MPU9150) — 16-bit
        if (b0 and ShimmerProtocol.SENSOR_GYRO != 0) {
            result["gyro_x"] = calParams.calibrateGyro(readI16(), 0)
            result["gyro_y"] = calParams.calibrateGyro(readI16(), 1)
            result["gyro_z"] = calParams.calibrateGyro(readI16(), 2)
        }
        // Mag (LSM303DLHC) — 16-bit
        if (b0 and ShimmerProtocol.SENSOR_MAG != 0) {
            result["mag_x"] = calParams.calibrateMag(readI16(), 0)
            result["mag_y"] = calParams.calibrateMag(readI16(), 1)
            result["mag_z"] = calParams.calibrateMag(readI16(), 2)
        }
        // ExG chip 1 — 24-bit, 2 channels (status + ch1 + ch2)
        if (b0 and ShimmerProtocol.SENSOR_EXG1_24BIT != 0) {
            offset += 1  // status byte
            result["exg1_ch1"] = calParams.calibrateExG(readI24())
            result["exg1_ch2"] = calParams.calibrateExG(readI24())
        } else if (b2 and ShimmerProtocol.SENSOR_EXG1_16BIT != 0) {
            offset += 1
            result["exg1_ch1"] = calParams.calibrateExG16(readI16())
            result["exg1_ch2"] = calParams.calibrateExG16(readI16())
        }
        // ExG chip 2
        if (b0 and ShimmerProtocol.SENSOR_EXG2_24BIT != 0) {
            offset += 1
            result["exg2_ch1"] = calParams.calibrateExG(readI24())
            result["exg2_ch2"] = calParams.calibrateExG(readI24())
        } else if (b2 and ShimmerProtocol.SENSOR_EXG2_16BIT != 0) {
            offset += 1
            result["exg2_ch1"] = calParams.calibrateExG16(readI16())
            result["exg2_ch2"] = calParams.calibrateExG16(readI16())
        }
        // GSR — 16-bit ADC
        if (b0 and ShimmerProtocol.SENSOR_GSR != 0) {
            result["gsr_kohm"] = calParams.calibrateGsr(readI16())
        }
        // PPG on expansion board A0 — 12-bit
        if (b0 and ShimmerProtocol.SENSOR_EXP_BOARD_A0 != 0) {
            result["ppg_mv"] = calParams.calibratePpg(readU12())
        }
        // Battery voltage — 12-bit
        if (b1 shr 13 and 0x01 != 0) {
            result["batt_mv"] = (readU12() * (3000.0 / 4095.0)) * 2.0  // voltage divider
        }

        return result
    }
}

// ─── Calibration parameters ───────────────────────────────────────────────────
//
// Shimmer3 stores calibration params in on-device memory.
// Defaults here are typical factory values; real values come from
// CMD_GET_CALIBRATION_DUMP once connected.

data class CalibrationParams(
    // Accel ADXL345 ±2g: offset (raw ADC) + sensitivity (raw ADC / g)
    val accelOffset: DoubleArray = doubleArrayOf(2048.0, 2048.0, 2048.0),
    val accelSens: DoubleArray   = doubleArrayOf(83.0, 83.0, 83.0),

    // Gyro MPU9150: offset + sensitivity (raw ADC / °/s)
    val gyroOffset: DoubleArray  = doubleArrayOf(0.0, 0.0, 0.0),
    val gyroSens: DoubleArray    = doubleArrayOf(65.5, 65.5, 65.5),   // ±500 dps

    // Mag sensitivity (µT per raw LSB)
    val magSens: DoubleArray     = doubleArrayOf(0.58, 0.58, 0.58),

    // ExG: Vref = 2.42V, gain = 4 → sensitivity = Vref/(gain*2^23)
    val exgVref: Double          = 2.42,
    val exgGain: Double          = 4.0,

    // GSR: look-up factors
    val gsrRange: Int            = 0,     // 0=10kΩ–56kΩ, 1=56kΩ–220kΩ ...

    // PPG: mV per ADC count on 3V rail, 12-bit ADC
    val ppgScale: Double         = 3000.0 / 4095.0
) {
    fun calibrateAccel(raw: Int, axis: Int): Double =
        (raw - accelOffset[axis]) / accelSens[axis] * 9.81   // → m/s²

    fun calibrateGyro(raw: Int, axis: Int): Double =
        (raw - gyroOffset[axis]) / gyroSens[axis]             // → °/s

    fun calibrateMag(raw: Int, axis: Int): Double =
        raw * magSens[axis]                                   // → µT

    fun calibrateExG(raw24: Int): Double {
        val sens = exgVref / (exgGain * 8388607.0) * 1000.0  // → mV
        return raw24 * sens
    }

    fun calibrateExG16(raw16: Int): Double {
        val sens = exgVref / (exgGain * 32767.0) * 1000.0
        return raw16 * sens
    }

    fun calibrateGsr(raw: Int): Double {
        // GSR equation from Shimmer manual (Table 2)
        // Range-dependent RF conversion
        val p = when (gsrRange) {
            0 -> Pair(0.0000000065995, -0.000068950)
            1 -> Pair(0.000000015600, -0.00015245)
            2 -> Pair(0.000000012500, -0.00010695)
            3 -> Pair(0.00000017300, -0.0017735)
            4 -> Pair(0.000000017210, -0.00015245)
            else -> Pair(0.0000000065995, -0.000068950)
        }
        val vCurrent = raw * (3000.0 / 4095.0)        // ADC → mV
        val conductance = p.first * vCurrent + p.second // S
        return if (conductance > 0) 1.0 / conductance / 1000.0 else 9999.0  // → kΩ
    }

    fun calibratePpg(raw: Int): Double = raw * ppgScale   // → mV
}
