package com.rfsat.shimmerenact.data.bluetooth

import com.rfsat.shimmerenact.data.repository.AppLog

// ─── Shimmer3 BT Classic Protocol Constants ───────────────────────────────────
//
// Based on Shimmer3 BT firmware protocol (FW ver ≥ 0.5)
// Reference: https://shimmersensing.com/support/shimmer-user-manual/
//

object ShimmerProtocol {

    // Commands (host → Shimmer)
    const val CMD_START_STREAMING: Byte        = 0x07
    const val CMD_STOP_STREAMING: Byte         = 0x20
    const val CMD_INQUIRY: Byte                = 0x01
    const val CMD_GET_SAMPLING_RATE: Byte      = 0x03
    const val CMD_SET_SAMPLING_RATE: Byte      = 0x05
    const val CMD_GET_CONFIG_SETUP_BYTES: Byte = 0x22
    const val CMD_SET_SENSORS: Byte            = 0x08
    const val CMD_GET_CALIBRATION_DUMP: Byte   = 0x17
    const val CMD_GET_FW_VERSION: Byte         = 0x2E
    const val CMD_RESET_TO_DEFAULT: Byte       = 0x5A

    // Responses / ACKs (Shimmer → host)
    const val ACK: Byte                        = 0xFF.toByte()
    const val INQUIRY_RESPONSE: Byte           = 0x02

    // Packet start byte for data
    const val PACKET_TYPE_DATA: Byte           = 0x00

    // ─── Sensor bitmap — 3-byte combined field ───────────────────────────────
    // Byte 0 (MSB of the 24-bit bitmap sent in the inquiry response)
    const val SENSOR_A_ACCEL: Int        = 0x80_00_00.ushr(16)   // = 0x80 in byte0
    const val SENSOR_GYRO: Int           = 0x40_00_00.ushr(16)   // = 0x40
    const val SENSOR_MAG: Int            = 0x20_00_00.ushr(16)   // = 0x20
    const val SENSOR_EXG1_24BIT: Int     = 0x10_00_00.ushr(16)   // = 0x10
    const val SENSOR_EXG2_24BIT: Int     = 0x08_00_00.ushr(16)   // = 0x08
    const val SENSOR_GSR: Int            = 0x04_00_00.ushr(16)   // = 0x04
    const val SENSOR_EXP_BOARD_A7: Int   = 0x02_00_00.ushr(16)   // = 0x02
    const val SENSOR_EXP_BOARD_A0: Int   = 0x01_00_00.ushr(16)   // = 0x01
    const val SENSOR_PPG: Int            = SENSOR_EXP_BOARD_A0

    // Byte 1 (middle byte of 3-byte bitmap)
    const val SENSOR_STRAIN_GAUGE: Int   = 0x80   // byte1 bit7
    const val SENSOR_HR: Int             = 0x40   // byte1 bit6
    const val SENSOR_VBATT: Int          = 0x20   // byte1 bit5
    const val SENSOR_D_ACCEL: Int        = 0x10   // byte1 bit4  (LSM303DLHC digital accel)
    const val SENSOR_EXT_A7: Int         = 0x02   // byte1 bit1
    const val SENSOR_EXT_A6: Int         = 0x01   // byte1 bit0

    // Byte 2 (LSB of 3-byte bitmap)
    const val SENSOR_EXG1_16BIT: Int     = 0x10   // byte2 bit4
    const val SENSOR_EXG2_16BIT: Int     = 0x08   // byte2 bit3

    // ─── Inquiry response layout ──────────────────────────────────────────────
    // Byte 0: 0xFF (ACK)
    // Byte 1: 0x02 (INQUIRY_RESPONSE code)
    // Byte 2: sampling rate LSB
    // Byte 3: sampling rate MSB
    // Byte 4: sensor bitmap byte 0
    // Byte 5: sensor bitmap byte 1
    // Byte 6: sensor bitmap byte 2
    // Byte 7: number of data channels
    // Byte 8+: channel types
    const val INQUIRY_BITMAP_OFFSET = 4    // byte index of sensor bitmap[0] in response
    const val INQUIRY_MIN_LEN       = 7    // minimum valid inquiry response length

    // ─── Sampling rate ────────────────────────────────────────────────────────
    // Register value = 32768 / desiredHz  (16-bit, sent little-endian)
    // Valid: 1–6000 Hz

    fun rateToRegister(hz: Int): Int =
        (32768 / hz.coerceIn(1, 6000)).coerceIn(1, 32767)

    fun registerToHz(reg: Int): Int =
        (32768.0 / reg.coerceIn(1, 32767)).toInt().coerceIn(1, 6000)

    /** Build the 3-byte CMD_SET_SAMPLING_RATE + rate payload. */
    fun buildRateCommand(hz: Int): ByteArray {
        val reg = rateToRegister(hz)
        return byteArrayOf(
            CMD_SET_SAMPLING_RATE,
            (reg and 0xFF).toByte(),
            ((reg shr 8) and 0xFF).toByte()
        )
    }

    // RFCOMM/SPP UUID
    const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

    // Timeouts
    const val RESPONSE_TIMEOUT_MS = 3000L
    const val STREAM_IDLE_TIMEOUT_MS = 5000L

    // Chart ring buffer
    const val CHART_BUFFER_SIZE = 512
}

// ─── Packet parser ────────────────────────────────────────────────────────────

object ShimmerPacketParser {

    /**
     * Parse a raw Shimmer3 data packet into calibrated signal values.
     *
     * [raw] starts AFTER the 0x00 packet-type byte (i.e. begins with the
     * 3-byte Shimmer timestamp).
     *
     * Channel order in the packet exactly follows the sensor bitmap bit order,
     * MSB first: ACCEL → GYRO → MAG → EXG1 → EXG2 → GSR → PPG/ExpA0 → BATT
     *
     * Shimmer3 data widths:
     *   Analog accel (ADXL345):   2 bytes × 3 axes = 6 bytes  (14-bit signed, right-justified)
     *   Gyro (MPU9150):           2 bytes × 3 axes = 6 bytes  (16-bit signed)
     *   Mag (LSM303DLHC):         2 bytes × 3 axes = 6 bytes  (16-bit signed)
     *   ExG 24-bit (ADS1292):     3 bytes status + 3 bytes ch1 + 3 bytes ch2 = 7 bytes per chip
     *   ExG 16-bit:               1 byte status + 2 bytes ch1 + 2 bytes ch2 = 5 bytes per chip
     *   GSR (ADC):                2 bytes (12-bit ADC in lower 12 bits)
     *   PPG / ExpA0 (ADC):        2 bytes (12-bit ADC in lower 12 bits)
     *   Battery (ADC):            2 bytes (12-bit ADC in lower 12 bits)
     */
    fun parse(
        raw: ByteArray,
        sensorBitmap: IntArray,   // [byte0, byte1, byte2]
        calParams: CalibrationParams
    ): Map<String, Double> {

        val result = mutableMapOf<String, Double>()
        if (raw.size < 3) return result

        // 3-byte Shimmer timestamp — consume but not returned as a signal key
        // (timestampMs from System.currentTimeMillis() is used instead)
        var offset = 3

        // ── Read helpers — return 0 and log if bounds exceeded ────────────────
        fun hasBytes(n: Int): Boolean {
            if (offset + n > raw.size) {
                AppLog.d("PKT", "Underrun at offset $offset (need $n, have ${raw.size - offset})")
                return false
            }
            return true
        }

        // 12-bit unsigned ADC value (packed in 2 bytes, lower 12 bits used)
        fun readAdc12(): Int {
            if (!hasBytes(2)) { offset += 2; return 0 }
            val lo = raw[offset].toInt() and 0xFF
            val hi = raw[offset + 1].toInt() and 0xFF
            offset += 2
            return (lo or (hi shl 8)) and 0x0FFF
        }

        // 14-bit signed (ADXL345 low-power mode): 2 bytes, signed, only lower 14 bits valid
        fun readAccel14(): Int {
            if (!hasBytes(2)) { offset += 2; return 0 }
            val lo = raw[offset].toInt() and 0xFF
            val hi = raw[offset + 1].toInt() and 0xFF
            offset += 2
            val raw16 = lo or (hi shl 8)
            // Sign-extend 14-bit: bit 13 is sign
            return if (raw16 and 0x2000 != 0) raw16 or 0xFFFFC000.toInt() else raw16 and 0x3FFF
        }

        // 16-bit signed
        fun readI16(): Int {
            if (!hasBytes(2)) { offset += 2; return 0 }
            val lo = raw[offset].toInt() and 0xFF
            val hi = raw[offset + 1].toInt() and 0xFF
            offset += 2
            val v = lo or (hi shl 8)
            return if (v >= 0x8000) v - 0x10000 else v
        }

        // 24-bit signed (ExG ADS1292)
        fun readI24(): Int {
            if (!hasBytes(3)) { offset += 3; return 0 }
            val b0 = raw[offset].toInt() and 0xFF
            val b1 = raw[offset + 1].toInt() and 0xFF
            val b2 = raw[offset + 2].toInt() and 0xFF
            offset += 3
            val v = (b0 shl 16) or (b1 shl 8) or b2   // big-endian in ADS1292
            return if (v >= 0x800000) v - 0x1000000 else v
        }

        val b0 = sensorBitmap[0]
        val b1 = sensorBitmap[1]
        val b2 = sensorBitmap[2]

        // ── ADXL345 analog accelerometer (14-bit, 3 axes) ────────────────────
        if (b0 and ShimmerProtocol.SENSOR_A_ACCEL != 0) {
            result["accel_x"] = calParams.calibrateAccel(readAccel14(), 0)
            result["accel_y"] = calParams.calibrateAccel(readAccel14(), 1)
            result["accel_z"] = calParams.calibrateAccel(readAccel14(), 2)
        }

        // ── MPU9150 Gyroscope (16-bit, 3 axes) ───────────────────────────────
        if (b0 and ShimmerProtocol.SENSOR_GYRO != 0) {
            result["gyro_x"] = calParams.calibrateGyro(readI16(), 0)
            result["gyro_y"] = calParams.calibrateGyro(readI16(), 1)
            result["gyro_z"] = calParams.calibrateGyro(readI16(), 2)
        }

        // ── LSM303DLHC Magnetometer (16-bit, 3 axes) ─────────────────────────
        if (b0 and ShimmerProtocol.SENSOR_MAG != 0) {
            result["mag_x"] = calParams.calibrateMag(readI16(), 0)
            result["mag_y"] = calParams.calibrateMag(readI16(), 1)
            result["mag_z"] = calParams.calibrateMag(readI16(), 2)
        }

        // ── ADS1292 ExG chip 1 (24-bit: 1 status + 2 channels × 3 bytes) ─────
        if (b0 and ShimmerProtocol.SENSOR_EXG1_24BIT != 0) {
            if (hasBytes(1)) offset += 1    // status byte (discard)
            result["exg1_ch1"] = calParams.calibrateExG(readI24())
            result["exg1_ch2"] = calParams.calibrateExG(readI24())
        } else if (b2 and ShimmerProtocol.SENSOR_EXG1_16BIT != 0) {
            if (hasBytes(1)) offset += 1    // status byte
            result["exg1_ch1"] = calParams.calibrateExG16(readI16())
            result["exg1_ch2"] = calParams.calibrateExG16(readI16())
        }

        // ── ADS1292 ExG chip 2 ────────────────────────────────────────────────
        if (b0 and ShimmerProtocol.SENSOR_EXG2_24BIT != 0) {
            if (hasBytes(1)) offset += 1
            result["exg2_ch1"] = calParams.calibrateExG(readI24())
            result["exg2_ch2"] = calParams.calibrateExG(readI24())
        } else if (b2 and ShimmerProtocol.SENSOR_EXG2_16BIT != 0) {
            if (hasBytes(1)) offset += 1
            result["exg2_ch1"] = calParams.calibrateExG16(readI16())
            result["exg2_ch2"] = calParams.calibrateExG16(readI16())
        }

        // ── GSR (12-bit ADC) ──────────────────────────────────────────────────
        if (b0 and ShimmerProtocol.SENSOR_GSR != 0) {
            result["gsr_kohm"] = calParams.calibrateGsr(readAdc12())
        }

        // ── PPG / Expansion board A0 (12-bit ADC) ────────────────────────────
        if (b0 and ShimmerProtocol.SENSOR_EXP_BOARD_A0 != 0) {
            result["ppg_mv"] = calParams.calibratePpg(readAdc12())
        }

        // ── Battery voltage (12-bit ADC, byte1 bit5 = SENSOR_VBATT) ──────────
        if (b1 and ShimmerProtocol.SENSOR_VBATT != 0) {
            result["batt_mv"] = (readAdc12() * (3000.0 / 4095.0)) * 2.0
        }

        if (result.isEmpty()) {
            AppLog.d("PKT", "Parser produced empty result — bitmap: " +
                "0x%02X 0x%02X 0x%02X  rawLen=${raw.size}".format(b0, b1, b2))
        }

        return result
    }
}

// ─── Calibration parameters ───────────────────────────────────────────────────

data class CalibrationParams(
    // ADXL345: sensitivity in LSB/g (14-bit mode, ±2g range → 256 LSB/g)
    val accelOffset: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0),
    val accelSens:   DoubleArray = doubleArrayOf(256.0, 256.0, 256.0),

    // MPU9150 Gyro: sensitivity in LSB/(°/s), ±500 dps range → 65.5 LSB/°/s
    val gyroOffset:  DoubleArray = doubleArrayOf(0.0, 0.0, 0.0),
    val gyroSens:    DoubleArray = doubleArrayOf(65.5, 65.5, 65.5),

    // LSM303DLHC Mag sensitivity (µT per LSB), ±1.3 gauss range
    val magSens:     DoubleArray = doubleArrayOf(0.909, 0.909, 1.020),  // X/Y differ from Z

    // ADS1292 ExG: Vref=2.42V, PGA gain=6 (default ECG mode)
    val exgVref: Double = 2.42,
    val exgGain: Double = 6.0,

    // GSR range index (0=10kΩ–56kΩ range)
    val gsrRange: Int = 0,

    // PPG: 3.0V reference, 12-bit ADC
    val ppgVoltageRef: Double = 3000.0   // mV
) {
    /** ADXL345 14-bit → m/s²  (offset + sensitivity then × g) */
    fun calibrateAccel(raw: Int, axis: Int): Double =
        ((raw - accelOffset[axis]) / accelSens[axis]) * 9.80665

    /** MPU9150 → °/s */
    fun calibrateGyro(raw: Int, axis: Int): Double =
        (raw - gyroOffset[axis]) / gyroSens[axis]

    /** LSM303DLHC → µT */
    fun calibrateMag(raw: Int, axis: Int): Double =
        raw * magSens[axis]

    /** ADS1292 24-bit → mV */
    fun calibrateExG(raw24: Int): Double {
        // Sensitivity = Vref / (Gain × 2^23)  in V/LSB, then ×1000 for mV
        val sensitivityMv = (exgVref / (exgGain * 8388607.0)) * 1000.0
        return raw24 * sensitivityMv
    }

    /** ADS1292 16-bit → mV */
    fun calibrateExG16(raw16: Int): Double {
        val sensitivityMv = (exgVref / (exgGain * 32767.0)) * 1000.0
        return raw16 * sensitivityMv
    }

    /**
     * GSR ADC → kΩ.
     * The GSR board uses a trans-impedance amplifier; actual formula depends on
     * which resistor range the firmware has selected (ranges 0–4).
     * Using linearised approximation from Shimmer2r/3 application notes.
     */
    fun calibrateGsr(adcRaw: Int): Double {
        // ADC raw (12-bit) → voltage at GSR pin
        val vGsr = adcRaw * (ppgVoltageRef / 4095.0)   // mV

        // Each range has a different feedback resistor Rf; conductance = (Vout - Vref/2) / (Rf × Vin)
        // Simplified to the lookup formula published in the Shimmer SDK source:
        val rfKOhm = when (gsrRange) {
            0 -> 40.2
            1 -> 287.0
            2 -> 1000.0
            3 -> 3300.0
            4 -> 3300.0
            else -> 40.2
        }
        // Vref/2 offset for the diff amp
        val vOffset = ppgVoltageRef / 2.0
        val vDiff = vGsr - vOffset
        // Avoid divide by zero; return max resistance for negative diff (disconnected)
        if (vDiff <= 0.0) return 9999.0
        // Conductance in µS: G = vDiff / (rfKOhm × Vin)
        // Resistance in kΩ: R = 1/G × Vin / vDiff
        val rKOhm = rfKOhm * (ppgVoltageRef / vDiff - 1.0)
        return rKOhm.coerceIn(0.0, 9999.0)
    }

    /** PPG / expansion board ADC → mV */
    fun calibratePpg(adcRaw: Int): Double =
        adcRaw * (ppgVoltageRef / 4095.0)
}
