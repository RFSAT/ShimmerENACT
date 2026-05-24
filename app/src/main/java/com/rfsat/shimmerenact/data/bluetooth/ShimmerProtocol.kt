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
    // Byte 1 additional
    const val SENSOR_b1_BRIDGE_AMP: Int = 0x80  // Bridge Amplifier
    // Byte 2 additional (MPU9250 / BMP280 — IMU unit)
    const val SENSOR_b2_ACCEL_MPU:  Int = 0x40  // MPU9250 accel
    const val SENSOR_b2_MAG_MPU:    Int = 0x20  // MPU9250 mag
    const val SENSOR_b2_BMP280:     Int = 0x04  // BMP280 pressure

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
    // MPU9250 magnetometer (separate from LSM303 mag above)
    const val CH_MPU9250_MAG_X: Int = 0x23  // i16  2B LE
    const val CH_MPU9250_MAG_Y: Int = 0x24
    const val CH_MPU9250_MAG_Z: Int = 0x25
    // BMP280 pressure and temperature (IMU unit)
    const val CH_BMP280_PRESS:  Int = 0x2A  // u32 compensated, 4B LE
    const val CH_BMP280_TEMP:   Int = 0x2B  // i32 compensated, 4B LE
    // LSM303AHTR wide-range accel (updated chip on newer Shimmer3)
    const val CH_ACCEL_AHTR_X:  Int = 0x2C  // i16  2B LE
    const val CH_ACCEL_AHTR_Y:  Int = 0x2D
    const val CH_ACCEL_AHTR_Z:  Int = 0x2E

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
        // Empirical overrides for SR48-5-0 firmware — MUST come first
        0x12                                        -> 6  // Gyro XYZ block
        0x1C                                        -> 6  // Mag XZY block
        CH_EXG1_STATUS, CH_EXG2_STATUS             -> 1
        CH_EXG1_CH1_24, CH_EXG1_CH2_24,
        CH_EXG2_CH1_24, CH_EXG2_CH2_24             -> 3
        CH_BMP280_PRESS, CH_BMP280_TEMP            -> 4   // 32-bit compensated
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
        if (b2 and SENSOR_b2_ACCEL_MPU   != 0) size += 6
        if (b2 and SENSOR_b2_MAG_MPU     != 0) size += 6
        if (b2 and SENSOR_b2_BMP280      != 0) size += 8  // 4B press + 4B temp
        return size
    }

    // ─── Calibration parameters ───────────────────────────────────────────────
    data class CalibrationParams(
        val accelSens:      Double      = 83.0,
        val accelOffset:    DoubleArray = doubleArrayOf(2081.0, 2081.0, 2087.0),
        val accelWrSens:    Double      = 1671.0,      // LSM303AHTR ±8g default
        val accelWrOffset:  DoubleArray = doubleArrayOf(0.0, 0.0, 0.0),
        val gyroSens:       Double      = 65.5,
        val gyroOffset:     DoubleArray = doubleArrayOf(0.0, 0.0, 0.0),
        val magSens:        Double      = 1100.0,
        val magOffset:      DoubleArray = doubleArrayOf(0.0, 0.0, 0.0),
        // BMP280 compensation values — read from device EEPROM in production;
        // defaults give approximate sea-level readings without calibration.
        val bmp280DigsT:    IntArray    = intArrayOf(27504, 26435, -1000), // T1, T2, T3
        val bmp280DigsP:    IntArray    = intArrayOf(36477, -10685, 3024,  // P1..P9
                                                      2855, 31, 15500,
                                                      -14600, 6000, 0),
        // ADS1292R ExG / EMG gain (default ±4 range, gain=4, Vref=2.42V)
        // LSB = Vref / (2^23 * gain) = 2.42 / (2^23 * 4) → ~72 nV/LSB
        // Output in µV: raw * (2420000.0 / (8388608.0 * 4))
        val exgGain:        Int         = 4,            // 1,2,3,4,6,8,12
        // ADXL377 high-g accel sensitivity: ±200g over 0–3.3V ratiometric.
        // Mid-scale = 2048 (12-bit ADC), sensitivity ≈ 9.81 m/s²/LSB × 200/2048
        val highGSensitivity: Double    = 200.0 * 9.81 / 2048.0
    ) {
        fun calibrateAccel(raw: Int, axis: Int)   = (raw - accelOffset[axis]) / accelSens
        fun calibrateAccelWr(raw: Int, axis: Int) = (raw - accelWrOffset[axis]) / accelWrSens
        fun calibrateGyro(raw: Int, axis: Int)    = (raw - gyroOffset[axis])  / gyroSens
        fun calibrateMag(raw: Int, axis: Int)     = (raw - magOffset[axis])   / magSens
        // Battery: ADC reads through a ÷2 voltage divider, 3.0V reference
        // Vbatt (mV) = (raw / 4095) × 3000 × 2
        fun calibrateBatt(raw: Int): Double = (raw / 4095.0) * 3000.0 * 2.0
        fun calibrateGsr(raw: Int): Double {
            val r = if (raw == 0) 1.0 else raw.toDouble()
            return (1.0 / ((r / 4096.0 * 3.0) / 1000.0 + 1.0e-9)) / 1000.0
        }
        fun calibratePpg(raw: Int): Double = raw * (3.0 / 4095.0) * 1000.0
        // EMG/ExG: convert 24-bit ADC raw to µV
        // LSB value = Vref_µV / (2^23 * gain) where Vref = 2,420,000 µV
        fun calibrateEmg(raw: Int): Double = raw * (2420000.0 / (8388608.0 * exgGain))
        fun calibrateExg(raw: Int): Double = calibrateEmg(raw) / 1000.0  // → mV
        // BMP280 compensated temperature — returns °C × 100 (integer)
        // Uses standard BMP280 compensation formula from datasheet.
        private fun bmp280CompTemp(rawT: Int): Pair<Double, Long> {
            val t1 = bmp280DigsT[0].toLong()
            val t2 = bmp280DigsT[1].toLong()
            val t3 = bmp280DigsT[2].toLong()
            val adcT = rawT.toLong()
            val var1 = (((adcT shr 3) - (t1 shl 1)) * t2) shr 11
            val var2 = (((adcT shr 4) - t1) * ((adcT shr 4) - t1) shr 12) * t3 shr 14
            val tFine = var1 + var2
            val tempC = ((tFine * 5 + 128) shr 8) / 100.0
            return Pair(tempC, tFine)
        }
        // BMP280 compensated pressure in Pa
        // High-g accel (ADXL377 ±200g) — ratiometric, 12-bit ADC
        // Output in m/s²: (raw - 2048) × sensitivity
        fun calibrateHighG(raw: Int): Double = (raw - 2048) * highGSensitivity

        // Bridge Amplifier raw ADC → pass-through (user applies load-cell cal)
        fun calibrateBridgeRaw(raw: Int): Double = raw.toDouble()

        // Skin temperature from resistance divider: convert ADC to kΩ
        // V_out = V_ref × R_skin / (R_ref + R_skin); R_ref typically 10kΩ
        // R_skin (kΩ) = R_ref × V_out / (V_ref - V_out) = 10 × raw / (4095 - raw)
        fun calibrateSkinTemp(raw: Int): Double {
            val denom = (4095 - raw).coerceAtLeast(1)
            return 10.0 * raw / denom
        }

        // PROTO3 Deluxe / generic analog: pass ADC raw through (user calibrates)
        fun calibrateAnalog(raw: Int): Double = raw.toDouble()

        fun calibrateBmp280(rawT: Int, rawP: Int): Pair<Double, Double> {
            val (tempC, tFine) = bmp280CompTemp(rawT)
            var v1 = tFine - 128000L
            var v2 = v1 * v1 * bmp280DigsP[5].toLong()
            v2 += (v1 * bmp280DigsP[4].toLong()) shl 17
            v2 += bmp280DigsP[3].toLong() shl 35
            v1 = ((v1 * v1 * bmp280DigsP[2]) shr 8) + ((v1 * bmp280DigsP[1]) shl 12)
            v1 = (((1L shl 47) + v1) * bmp280DigsP[0]) shr 33
            if (v1 == 0L) return Pair(tempC, 0.0)
            var p = 1048576L - rawP
            p = ((p shl 31) - v2) * 3125 / v1
            v1 = (bmp280DigsP[8].toLong() * (p shr 13) * (p shr 13)) shr 25
            v2 = (bmp280DigsP[7].toLong() * p) shr 19
            val pressurePa = ((p + v1 + v2) shr 8) / 256.0
            return Pair(tempC, pressurePa)
        }
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
                CH_ACCEL_LN_X   -> { val v = calParams.calibrateAccel(readI16(), 0);
                                     result["accel_ln_x"] = v; result["accel_x"] = v }
                CH_ACCEL_LN_Y   -> { val v = calParams.calibrateAccel(readI16(), 1);
                                     result["accel_ln_y"] = v; result["accel_y"] = v }
                CH_ACCEL_LN_Z   -> { val v = calParams.calibrateAccel(readI16(), 2);
                                     result["accel_ln_z"] = v; result["accel_z"] = v }
                CH_VBATT        -> result["batt_mv"]    = calParams.calibrateBatt(readAdc12())
                CH_GSR          -> result["gsr_kohm"]   = calParams.calibrateGsr(readU16())
                // Multi-role ADC channels — emit multiple keys so each sensor type
                // picks up its relevant signal from the same raw data.
                CH_EXT_ADC_CH7  -> {
                    val raw = readU16()
                    result["analog_ch2"]  = calParams.calibrateAnalog(raw)   // PROTO3 Deluxe Ch2
                    result["accel_hg_y"]  = calParams.calibrateHighG(raw and 0x0FFF)  // 200g IMU Y
                }
                CH_EXT_ADC_CH6  -> {
                    val raw = readU16()
                    result["analog_ch1"]  = calParams.calibrateAnalog(raw)   // PROTO3 Deluxe Ch1
                    result["accel_hg_x"]  = calParams.calibrateHighG(raw and 0x0FFF)  // 200g IMU X
                }
                CH_EXT_ADC_CH15 -> {
                    val raw = readU16()
                    result["analog_ch3"]  = calParams.calibrateAnalog(raw)   // PROTO3 Deluxe Ch3
                    result["accel_hg_z"]  = calParams.calibrateHighG(raw and 0x0FFF)  // 200g IMU Z
                }
                CH_INT_ADC_CH1  -> {
                    val raw = readAdc12()
                    result["skin_temp_kohm"] = calParams.calibrateSkinTemp(raw)  // Bridge Amp+ skin temp
                    result["analog_ch4"]     = calParams.calibrateAnalog(raw)   // PROTO3 Deluxe Ch4
                }
                CH_INT_ADC_CH12 -> {
                    val raw = readAdc12()
                    result["bridge_high"] = calParams.calibrateBridgeRaw(raw)  // Bridge Amp+ high gain
                }
                CH_INT_ADC_CH13 -> {
                    val raw = readAdc12()
                    result["ppg_mv"]      = calParams.calibratePpg(raw)         // GSR+ PPG
                    result["bridge_low"]  = calParams.calibrateBridgeRaw(raw)   // Bridge Amp+ low gain
                }
                CH_INT_ADC_CH14 -> result["ppg_mv"]    = calParams.calibratePpg(readAdc12())
                CH_ACCEL_WR_X   -> result["accel_wr_x"]  = calParams.calibrateAccelWr(readI16(), 0)
                CH_ACCEL_WR_Y   -> result["accel_wr_y"]  = calParams.calibrateAccelWr(readI16(), 1)
                CH_ACCEL_WR_Z   -> result["accel_wr_z"]  = calParams.calibrateAccelWr(readI16(), 2)
                CH_EXG1_STATUS  -> { readU8() }
                CH_EXG1_CH1_24  -> {
                    val raw = readI24BE()
                    result["exg1_ch1"] = calParams.calibrateExg(raw)
                    result["emg_ch1"]  = calParams.calibrateEmg(raw)  // EMG mode µV alias
                    result["ecg_ch1"]  = calParams.calibrateExg(raw)  // Ebio ECG alias
                }
                CH_EXG1_CH2_24  -> {
                    val raw = readI24BE()
                    result["exg1_ch2"] = calParams.calibrateExg(raw)
                    result["emg_ref"]  = calParams.calibrateEmg(raw)  // EMG mode µV alias
                    result["ecg_ch2"]  = calParams.calibrateExg(raw)  // Ebio ECG RLD alias
                }
                CH_EXG2_STATUS  -> { readU8() }
                CH_EXG2_CH1_24  -> {
                    val raw = readI24BE()
                    result["exg2_ch1"]  = calParams.calibrateExg(raw)
                    result["bioz_ch1"]  = calParams.calibrateExg(raw)  // Ebio bioimpedance alias
                }
                CH_EXG2_CH2_24  -> {
                    val raw = readI24BE()
                    result["exg2_ch2"]  = calParams.calibrateExg(raw)
                    result["bioz_ch2"]  = calParams.calibrateExg(raw)  // Ebio bioimpedance ref alias
                }
                CH_GYRO_X       -> result["gyro_x"]    = calParams.calibrateGyro(readI16BE(), 0)
                CH_GYRO_Y       -> result["gyro_y"]    = calParams.calibrateGyro(readI16BE(), 1)
                CH_GYRO_Z       -> result["gyro_z"]    = calParams.calibrateGyro(readI16BE(), 2)
                // LSM303 sends mag in order X, Z, Y
                CH_MAG_X        -> result["mag_x"]     = calParams.calibrateMag(readI16BE(), 0)
                CH_MAG_Y        -> result["mag_z"]     = calParams.calibrateMag(readI16BE(), 2)
                CH_MAG_Z        -> result["mag_y"]     = calParams.calibrateMag(readI16BE(), 1)
                CH_ACCEL_WR2_X  -> result["accel_wr_x"] = calParams.calibrateAccelWr(readI16(), 0)
                CH_ACCEL_WR2_Y  -> result["accel_wr_y"] = calParams.calibrateAccelWr(readI16(), 1)
                CH_ACCEL_WR2_Z  -> result["accel_wr_z"] = calParams.calibrateAccelWr(readI16(), 2)
                CH_EXG1_CH1_16  -> result["exg1_ch1"]   = calParams.calibrateExg(readI16BE())
                CH_EXG1_CH2_16  -> result["exg1_ch2"]   = calParams.calibrateExg(readI16BE())
                CH_EXG2_CH1_16  -> result["exg2_ch1"]   = calParams.calibrateExg(readI16BE())
                CH_EXG2_CH2_16  -> result["exg2_ch2"]   = calParams.calibrateExg(readI16BE())
                // MPU9250 magnetometer (IMU unit, LE 16-bit)
                CH_MPU9250_MAG_X -> result["mag_x"]      = calParams.calibrateMag(readI16(), 0)
                CH_MPU9250_MAG_Y -> result["mag_y"]      = calParams.calibrateMag(readI16(), 1)
                CH_MPU9250_MAG_Z -> result["mag_z"]      = calParams.calibrateMag(readI16(), 2)
                // LSM303AHTR wide-range accel (newer Shimmer3 hardware)
                CH_ACCEL_AHTR_X  -> result["accel_wr_x"] = calParams.calibrateAccelWr(readI16(), 0)
                CH_ACCEL_AHTR_Y  -> result["accel_wr_y"] = calParams.calibrateAccelWr(readI16(), 1)
                CH_ACCEL_AHTR_Z  -> result["accel_wr_z"] = calParams.calibrateAccelWr(readI16(), 2)
                // BMP280 pressure + temperature (IMU unit)
                // The firmware sends pressure first, then temperature as two 4-byte LE values.
                // Both values are stored in a temporary slot; the pair is calibrated together.
                CH_BMP280_PRESS -> {
                    // Read 4B pressure; peek at next 4B temperature in same block
                    if (remaining() >= 8) {
                        val rawP = ((raw[offset].toInt() and 0xFF) or
                                   ((raw[offset+1].toInt() and 0xFF) shl 8) or
                                   ((raw[offset+2].toInt() and 0xFF) shl 16) or
                                   ((raw[offset+3].toInt() and 0xFF) shl 24))
                        val rawT = ((raw[offset+4].toInt() and 0xFF) or
                                   ((raw[offset+5].toInt() and 0xFF) shl 8) or
                                   ((raw[offset+6].toInt() and 0xFF) shl 16) or
                                   ((raw[offset+7].toInt() and 0xFF) shl 24))
                        offset += 4  // consume only pressure here; temp channel will consume next 4
                        val (t, p) = calParams.calibrateBmp280(rawT, rawP)
                        result["pressure_pa"] = p
                        result["temp_c"]      = t
                    } else offset += minOf(4, remaining())
                }
                CH_BMP280_TEMP -> { offset += minOf(4, remaining()) }  // already consumed above
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
