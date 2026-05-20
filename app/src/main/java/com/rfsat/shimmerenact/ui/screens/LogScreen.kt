package com.rfsat.shimmerenact.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rfsat.shimmerenact.data.repository.AppLog
import com.rfsat.shimmerenact.data.repository.LogEntry
import com.rfsat.shimmerenact.data.repository.LogLevel
import com.rfsat.shimmerenact.ui.theme.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by AppLog.entries.collectAsState()

    // Filter state
    var showDebug by remember { mutableStateOf(false) }
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }

    val filtered = remember(entries, showDebug, filterLevel) {
        entries.filter { e ->
            (showDebug || e.level != LogLevel.DEBUG) &&
            (filterLevel == null || e.level == filterLevel)
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new entries
    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) {
            listState.animateScrollToItem(filtered.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Diagnostic Log", color = EnactOnSurface, fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text("${filtered.size} entries", fontSize = 11.sp,
                            color = EnactOnSurface.copy(alpha = 0.5f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = EnactGreen)
                    }
                },
                actions = {
                    // Share / export
                    IconButton(onClick = {
                        val text = AppLog.exportText()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            putExtra(Intent.EXTRA_SUBJECT, "ShimmerENACT Diagnostic Log")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share log"))
                    }) {
                        Icon(Icons.Default.Share, "Share log", tint = EnactGreen)
                    }
                    // Copy all
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("ShimmerENACT Log", AppLog.exportText()))
                        Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = EnactGreen)
                    }
                    // Clear
                    IconButton(onClick = { AppLog.clear() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear log", tint = EnactError)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EnactDarkMid)
            )
        },
        containerColor = EnactDark
    ) { padding ->

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ── Filter bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EnactDarkMid)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Filter:", fontSize = 11.sp, color = EnactOnSurface.copy(alpha = 0.5f))

                // "All" chip
                FilterChip(
                    selected = filterLevel == null,
                    onClick = { filterLevel = null },
                    label = { Text("All", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EnactGreen.copy(alpha = 0.2f),
                        selectedLabelColor = EnactGreen,
                        labelColor = EnactOnSurface.copy(alpha = 0.5f)
                    )
                )
                listOf(LogLevel.OK, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR).forEach { lvl ->
                    FilterChip(
                        selected = filterLevel == lvl,
                        onClick = { filterLevel = if (filterLevel == lvl) null else lvl },
                        label = { Text(lvl.name, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = levelColor(lvl).copy(alpha = 0.2f),
                            selectedLabelColor = levelColor(lvl),
                            labelColor = EnactOnSurface.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(Modifier.weight(1f))

                // Debug toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showDebug = !showDebug }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("DBG", fontSize = 10.sp,
                        color = if (showDebug) Color(0xFF888888) else EnactOnSurface.copy(alpha = 0.3f))
                    Spacer(Modifier.width(4.dp))
                    Switch(
                        checked = showDebug,
                        onCheckedChange = { showDebug = it },
                        modifier = Modifier.height(20.dp).width(36.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF888888),
                            checkedTrackColor = Color(0xFF888888).copy(alpha = 0.4f),
                            uncheckedThumbColor = EnactOnSurface.copy(alpha = 0.2f),
                            uncheckedTrackColor = EnactSurfaceVar
                        )
                    )
                }
            }

            // ── Log list ──────────────────────────────────────────────────────
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Terminal, null,
                            tint = EnactGreen.copy(alpha = 0.2f),
                            modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No log entries",
                            color = EnactOnSurface.copy(alpha = 0.3f), fontSize = 15.sp)
                        Text("Connect to a sensor to start logging",
                            color = EnactOnSurface.copy(alpha = 0.2f), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        LogEntryRow(entry = entry, context = context)
                    }
                }
            }
        }
    }
}

// ─── Single log row ───────────────────────────────────────────────────────────

@Composable
fun LogEntryRow(entry: LogEntry, context: Context) {
    val color = levelColor(entry.level)
    val bgColor = when (entry.level) {
        LogLevel.ERROR -> EnactError.copy(alpha = 0.08f)
        LogLevel.WARN  -> EnactWarning.copy(alpha = 0.06f)
        LogLevel.OK    -> EnactGreen.copy(alpha = 0.06f)
        else           -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("log", "${entry.timeStr} [${entry.tag}] ${entry.message}"))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timestamp
        Text(
            entry.timeStr,
            fontSize = 9.sp,
            color = EnactOnSurface.copy(alpha = 0.35f),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(76.dp).padding(top = 1.dp)
        )

        // Level badge
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.18f))
                .padding(top = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when (entry.level) {
                    LogLevel.DEBUG -> "DBG"
                    LogLevel.INFO  -> "INFO"
                    LogLevel.OK    -> " OK "
                    LogLevel.WARN  -> "WARN"
                    LogLevel.ERROR -> " ERR"
                },
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.width(6.dp))

        // Tag + message
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    "[${entry.tag}]",
                    fontSize = 10.sp,
                    color = color.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    entry.message,
                    fontSize = 10.sp,
                    color = if (entry.level == LogLevel.DEBUG)
                        EnactOnSurface.copy(alpha = 0.45f) else EnactOnSurface.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

// ─── Level → colour mapping ───────────────────────────────────────────────────

fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.DEBUG -> Color(0xFF888888)
    LogLevel.INFO  -> Color(0xFF39A8E0)
    LogLevel.OK    -> Color(0xFF43AF81)
    LogLevel.WARN  -> Color(0xFFFFB74D)
    LogLevel.ERROR -> Color(0xFFCF6679)
}
