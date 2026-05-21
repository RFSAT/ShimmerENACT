package com.rfsat.shimmerenact.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Log entry levels ─────────────────────────────────────────────────────────

enum class LogLevel { DEBUG, INFO, OK, WARN, ERROR }

data class LogEntry(
    val id: Long,
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    val timeStr: String get() =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestampMs))
}

// ─── Singleton log bus ────────────────────────────────────────────────────────
//
// Usage anywhere in the app:
//   AppLog.i("BT", "Socket connected to $address")
//   AppLog.e("BT", "Connection failed: ${e.message}")

object AppLog {

    private const val MAX_ENTRIES = 1000

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private var nextId = 0L

    fun d(tag: String, msg: String) = add(LogLevel.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = add(LogLevel.INFO,  tag, msg)
    fun ok(tag: String, msg: String) = add(LogLevel.OK,   tag, msg)
    fun w(tag: String, msg: String) = add(LogLevel.WARN,  tag, msg)
    fun e(tag: String, msg: String) = add(LogLevel.ERROR, tag, msg)

    fun add(level: LogLevel, tag: String, msg: String) {
        val entry = LogEntry(
            id = nextId++,
            timestampMs = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = msg
        )
        _entries.update { current ->
            val updated = current + entry
            if (updated.size > MAX_ENTRIES) updated.takeLast(MAX_ENTRIES) else updated
        }
        // Also mirror to Android logcat for ADB debugging
        when (level) {
            LogLevel.DEBUG -> android.util.Log.d(tag, msg)
            LogLevel.INFO  -> android.util.Log.i(tag, msg)
            LogLevel.OK    -> android.util.Log.i(tag, "✓ $msg")
            LogLevel.WARN  -> android.util.Log.w(tag, msg)
            LogLevel.ERROR -> android.util.Log.e(tag, msg)
        }
    }

    fun clear() = _entries.update { emptyList() }

    /** Export all log entries as plain text */
    fun exportText(): String = buildString {
        _entries.value.forEach { e ->
            appendLine("[${e.timeStr}] ${e.level.name.padEnd(5)} [${e.tag}] ${e.message}")
        }
    }
}
