package com.rfsat.shimmerenact.data.repository

import android.content.Context
import android.os.Environment
import com.rfsat.shimmerenact.data.models.LocationPoint
import com.rfsat.shimmerenact.data.models.RateConstraints
import com.rfsat.shimmerenact.data.models.ShimmerSample
import com.rfsat.shimmerenact.data.models.ShimmerSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

// ─── GPS-tagged sample —————————————————————————————————————————————————————────
// We fuse each ShimmerSample with the most recent GPS fix before writing.
// GPS location is written as extra columns in every signal CSV so that data
// can be geo-referenced without a separate join step.

// ─── Per-signal recording channel ─────────────────────────────────────────────

private data class SignalChannel(
    val signal: ShimmerSignal,
    val targetHz: Int,
    val hardwareHz: Int,
    val writer: BufferedWriter,
    val file: File,
    val appendMode: Boolean = false
) {
    val decimationStep: Int = (hardwareHz.toDouble() / targetHz.coerceAtLeast(1))
        .coerceAtLeast(1.0).toInt()
    var counter: Int = 0
    var rowsWritten: Long = 0L
}

// ─── Session metadata ─────────────────────────────────────────────────────────

data class RecordingSession(
    val sessionId: String,
    val deviceName: String,
    val startTimeMs: Long,
    val files: List<RecordingFile>
)

// ─── Repository ───────────────────────────────────────────────────────────────

class RecordingRepository(private val context: Context) {

    private val channels = java.util.concurrent.CopyOnWriteArrayList<SignalChannel>()
    private var sessionId: String = ""
    private var sessionDir: File? = null
    @Volatile private var _isRecording = false
    private var _totalSamplesWritten = 0L

    private val sessionDateFmt = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    val isRecording: Boolean get() = _isRecording
    val totalSamplesWritten: Long get() = _totalSamplesWritten
    val currentSessionId: String get() = sessionId
    val currentSessionDir: String get() = sessionDir?.absolutePath ?: ""

    // ─── Last session — for "continue" feature ────────────────────────────────
    fun lastSession(): RecordingSession? {
        val root = runCatching { getRootDir() }.getOrNull() ?: return null
        val dir = root.listFiles { f -> f.isDirectory }
            ?.maxByOrNull { it.lastModified() } ?: return null
        val csvFiles = dir.listFiles { f -> f.extension == "csv" }
            ?.mapNotNull { f ->
                runCatching {
                    RecordingFile(
                        name = f.nameWithoutExtension,
                        path = f.absolutePath,
                        sizeBytes = f.length(),
                        lastModified = f.lastModified(),
                        sessionId = dir.name,
                        rowCount = 0L
                    )
                }.getOrNull()
            } ?: emptyList()
        return RecordingSession(
            sessionId = dir.name,
            deviceName = dir.name.substringBeforeLast("_"),
            startTimeMs = dir.lastModified(),
            files = csvFiles
        )
    }

    // ─── Start recording ──────────────────────────────────────────────────────
    // append=true → reopen the most recent session directory in append mode.
    suspend fun startRecording(
        deviceName: String,
        signals: List<ShimmerSignal>,
        signalRatesHz: Map<String, Int>,
        hardwareHz: Int,
        append: Boolean = false
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (_isRecording) stopRecording()
        try {
            val rootDir = getRootDir()
            val dir: File
            val isAppend: Boolean

            if (append) {
                val existing = rootDir.listFiles { f -> f.isDirectory }
                    ?.maxByOrNull { it.lastModified() }
                if (existing != null) {
                    dir = existing
                    sessionId = dir.name.substringAfterLast("_").let {
                        // Reconstruct session id from dir name
                        dir.name.removePrefix(
                            "${deviceName.replace(Regex("[^A-Za-z0-9_-]"), "_")}_"
                        )
                    }
                    isAppend = true
                    AppLog.i("REC", "Continuing session: ${dir.name}")
                } else {
                    // No prior session — fall through to new session
                    sessionId = sessionDateFmt.format(Date())
                    val safeName = deviceName.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    dir = File(rootDir, "${safeName}_$sessionId").also { it.mkdirs() }
                    isAppend = false
                }
            } else {
                sessionId = sessionDateFmt.format(Date())
                val safeName = deviceName.replace(Regex("[^A-Za-z0-9_-]"), "_")
                dir = File(rootDir, "${safeName}_$sessionId").also { it.mkdirs() }
                isAppend = false
            }

            sessionDir = dir
            val startTs = isoFmt.format(Date())
            val paths = mutableListOf<String>()

            for (sig in signals) {
                val targetHz = (signalRatesHz[sig.key] ?: hardwareHz).coerceIn(1, hardwareHz)
                val safeKey = sig.key.replace(Regex("[^A-Za-z0-9_]"), "_")
                val file = File(dir, "${safeKey}.csv")

                // GPS signals get special handling — they are written to ALL files as
                // extra columns, not as standalone files. Skip creating separate files.
                if (sig.key.startsWith("gps_")) continue

                val needsHeader = !file.exists() || !isAppend
                val bw = BufferedWriter(FileWriter(file, isAppend))

                if (needsHeader) {
                    bw.write("# RFSAT Limited — ENACT Project (Horizon Europe Grant 101157151)\n")
                    bw.write("# Device: $deviceName\n")
                    bw.write("# Signal: ${sig.displayName}  [${sig.unit}]\n")
                    bw.write("# Hardware rate: $hardwareHz Hz  |  Output rate: $targetHz Hz\n")
                    bw.write("# Session start: $startTs\n")
                    bw.write("# Generated by ShimmerENACT v1.5\n")
                    bw.write("timestamp_iso,timestamp_ms,${sig.key}_${sig.unit.replace("/","per")}")
                    bw.write(",gps_lat_deg,gps_lon_deg,gps_alt_m,gps_acc_m\n")
                } else {
                    // Append mode — write a continuation marker
                    bw.write("# --- Session continued: $startTs ---\n")
                }

                channels.add(SignalChannel(sig, targetHz, hardwareHz, bw, file, isAppend))
                paths.add(file.absolutePath)
            }

            _isRecording = true
            _totalSamplesWritten = 0L
            val mode = if (isAppend) "appended" else "started"
            AppLog.ok("REC", "Session $mode — ${signals.size} files in ${dir.name}")
            Result.success(paths)
        } catch (e: Exception) {
            AppLog.e("REC", "startRecording failed: ${e.message}")
            channels.forEach { runCatching { it.writer.close() } }
            channels.clear()
            Result.failure(e)
        }
    }

    // ─── Write a hardware sample — with GPS columns ───────────────────────────
    fun writeSampleSync(sample: ShimmerSample, location: LocationPoint?) {
        if (!_isRecording || channels.isEmpty()) return
        val isoTs = isoFmt.format(Date(sample.timestampMs))
        val gpsStr = if (location != null) {
            ",%.8f,%.8f,%.2f,%.1f".format(
                location.lat, location.lon, location.altM, location.accuracyM
            )
        } else {
            ",,,"
        }
        var wrote = false
        for (ch in channels) {
            val v = sample.values[ch.signal.key] ?: continue
            ch.counter++
            if (ch.counter >= ch.decimationStep) {
                ch.counter = 0
                try {
                    ch.writer.write("$isoTs,${sample.timestampMs},${"%.6f".format(v)}$gpsStr\n")
                    ch.rowsWritten++
                    wrote = true
                } catch (_: Exception) {}
            }
        }
        if (wrote) {
            _totalSamplesWritten++
            if (_totalSamplesWritten % 200L == 0L) {
                channels.forEach { runCatching { it.writer.flush() } }
            }
        }
    }

    // Overload for backward compatibility (no GPS)
    fun writeSampleSync(sample: ShimmerSample) = writeSampleSync(sample, null)

    fun resetRecordingState() {
        channels.forEach { runCatching { it.writer.close() } }
        channels.clear()
        _isRecording = false
        _totalSamplesWritten = 0L
        AppLog.w("REC", "Recording state forcibly reset")
    }

    // ─── Stop recording ───────────────────────────────────────────────────────
    suspend fun stopRecording(): Result<RecordingSession> = withContext(Dispatchers.IO) {
        _isRecording = false
        return@withContext try {
            val endTs = isoFmt.format(Date())
            val files = mutableListOf<RecordingFile>()
            val channelsCopy = channels.toList()
            channels.clear()

            for (ch in channelsCopy) {
                try {
                    ch.writer.write("# Session end: $endTs\n")
                    ch.writer.write("# Rows written: ${ch.rowsWritten}\n")
                    ch.writer.flush()
                    ch.writer.close()
                    if (ch.file.exists() && ch.file.length() > 0) {
                        files.add(RecordingFile(
                            name = ch.file.nameWithoutExtension,
                            path = ch.file.absolutePath,
                            sizeBytes = ch.file.length(),
                            lastModified = ch.file.lastModified(),
                            signalDisplayName = ch.signal.displayName,
                            signalUnit = ch.signal.unit,
                            rateHz = ch.targetHz,
                            sessionId = sessionId,
                            rowCount = ch.rowsWritten
                        ))
                    }
                } catch (e: Exception) {
                    AppLog.e("REC", "Error closing ${ch.signal.key}: ${e.message}")
                }
            }

            val session = RecordingSession(
                sessionId = sessionId,
                deviceName = sessionDir?.name?.substringBeforeLast("_") ?: "Unknown",
                startTimeMs = runCatching {
                    sessionDateFmt.parse(sessionId)?.time ?: 0L
                }.getOrDefault(0L),
                files = files
            )
            AppLog.ok("REC", "Session stopped — ${files.size} files, $_totalSamplesWritten rows")
            Result.success(session)
        } catch (e: Exception) {
            AppLog.e("REC", "stopRecording exception: ${e.javaClass.simpleName}: ${e.message}")
            channels.clear()
            Result.failure(e)
        }
    }

    // ─── List sessions ────────────────────────────────────────────────────────
    suspend fun listSessions(): List<RecordingSession> = withContext(Dispatchers.IO) {
        val root = try { getRootDir() } catch (_: Exception) { return@withContext emptyList() }
        root.listFiles { f -> f.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { dir ->
                try {
                    val csvFiles = dir.listFiles { f -> f.extension == "csv" }
                        ?.sortedBy { it.name }
                        ?.mapNotNull { f ->
                            try {
                                val lines = f.readLines()
                                val rate = lines
                                    .find { it.startsWith("# Hardware rate:") }
                                    ?.substringAfter("Output rate:")
                                    ?.trim()?.substringBefore(" Hz")?.toIntOrNull() ?: 0
                                val rows = lines
                                    .find { it.startsWith("# Rows written:") }
                                    ?.substringAfter(":")?.trim()?.toLongOrNull() ?: 0L
                                RecordingFile(
                                    name = f.nameWithoutExtension,
                                    path = f.absolutePath,
                                    sizeBytes = f.length(),
                                    lastModified = f.lastModified(),
                                    sessionId = dir.name,
                                    rateHz = rate,
                                    rowCount = rows
                                )
                            } catch (_: Exception) { null }
                        } ?: emptyList()
                    RecordingSession(
                        sessionId = dir.name,
                        deviceName = dir.name.substringBeforeLast("_"),
                        startTimeMs = dir.lastModified(),
                        files = csvFiles
                    )
                } catch (_: Exception) { null }
            } ?: emptyList()
    }

    suspend fun deleteSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        getRootDir().listFiles { f -> f.isDirectory && f.name.contains(sessionId) }
            ?.firstOrNull()?.deleteRecursively() ?: false
    }

    suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).delete()
    }

    private fun getRootDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS)
        val root = File(downloads, "ShimmerENACT")
        if (!root.exists()) root.mkdirs()
        return root
    }
}

// ─── Data classes ─────────────────────────────────────────────────────────────

data class RecordingFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val signalDisplayName: String = name,
    val signalUnit: String = "",
    val rateHz: Int = 0,
    val sessionId: String = "",
    val rowCount: Long = 0L
) {
    val sizeDisplay: String get() = when {
        sizeBytes > 1_048_576 -> "%.1f MB".format(sizeBytes / 1_048_576.0)
        sizeBytes > 1024 -> "%.1f KB".format(sizeBytes / 1024.0)
        else -> "$sizeBytes B"
    }
}
