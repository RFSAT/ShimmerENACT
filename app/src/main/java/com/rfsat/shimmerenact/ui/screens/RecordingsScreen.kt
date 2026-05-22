package com.rfsat.shimmerenact.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.rfsat.shimmerenact.data.repository.RecordingFile
import com.rfsat.shimmerenact.data.repository.RecordingSession
import com.rfsat.shimmerenact.ui.theme.*
import com.rfsat.shimmerenact.viewmodel.ShimmerViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RecordingsScreen(
    viewModel: ShimmerViewModel,
    onBack: () -> Unit,
    onViewFile: (RecordingFile) -> Unit = {},
    onViewSession: (RecordingSession) -> Unit = {}
) {
    val sessions by viewModel.sessions.collectAsState()
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy  HH:mm:ss", Locale.US) }

    // ── Storage permission handling ────────────────────────────────────────────
    // On API 29–32: READ_EXTERNAL_STORAGE grants full read of external storage.
    // On API 33+:   READ_EXTERNAL_STORAGE is no longer effective for non-media
    //               files in Downloads; MANAGE_EXTERNAL_STORAGE is required but
    //               must be granted via the system Settings page.
    val needsLegacyRead = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2  // ≤ API 32
    val needsManage     = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R      // ≥ API 30

    val readPermState = if (needsLegacyRead) {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    } else null

    // hasStorageAccess must be a mutableState so it is recomputed on every
    // ON_RESUME event. On API 30+, isExternalStorageManager() is the only
    // reliable way to check the permission, but it returns a snapshot value —
    // it does not trigger recomposition by itself when the user grants the
    // permission in the system Settings page and then returns to the app.
    // Without observing ON_RESUME, the banner stays on screen forever even
    // after the permission is granted.
    var hasStorageAccess by remember {
        mutableStateOf(
            when {
                needsLegacyRead -> readPermState?.status?.isGranted == true
                needsManage     -> Environment.isExternalStorageManager()
                else            -> true
            }
        )
    }

    // Re-evaluate permission and refresh sessions on every resume.
    // This catches the return from the system Settings page (API 30+) and
    // the accompanist permission grant callback (API ≤ 32).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val newAccess = when {
                    needsLegacyRead -> readPermState?.status?.isGranted == true
                    needsManage     -> Environment.isExternalStorageManager()
                    else            -> true
                }
                hasStorageAccess = newAccess
                if (newAccess) viewModel.refreshSessions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var deleteTarget by remember { mutableStateOf<RecordingSession?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Recordings", color = EnactOnSurface)
                        Text(
                            "${sessions.size} session${if (sessions.size != 1) "s" else ""}  •  " +
                            "${sessions.sumOf { it.files.size }} files",
                            fontSize = 11.sp, color = EnactOnSurfaceDim
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = EnactGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EnactDarkMid)
            )
        },
        containerColor = EnactDark
    ) { padding ->

        Column(Modifier.padding(padding).fillMaxSize()) {

        // ── Storage permission banner ──────────────────────────────────────────
        if (!hasStorageAccess) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = EnactWarning.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, EnactWarning.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOff, null,
                            tint = EnactWarning, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Storage access required",
                            fontWeight = FontWeight.SemiBold,
                            color = EnactWarning, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (needsLegacyRead)
                            "Grant storage permission to view recordings from previous sessions."
                        else
                            "Grant 'All files access' in Settings to view recordings from previous sessions. " +
                            "New recordings will still appear automatically.",
                        fontSize = 12.sp,
                        color = EnactOnSurface.copy(alpha = 0.75f),
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (needsLegacyRead) {
                                readPermState?.launchPermissionRequest()
                            } else {
                                // API 30+: open system all-files-access settings page
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EnactWarning),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (needsLegacyRead) "Grant Permission" else "Open Settings",
                            color = EnactDark, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null,
                        tint = EnactGreen.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No recordings yet", color = EnactOnSurface.copy(alpha = 0.4f), fontSize = 16.sp)
                    Text("Connect a sensor and press Record",
                        color = EnactOnSurface.copy(alpha = 0.25f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sessions, key = { it.sessionId }) { session ->
                    SessionCard(
                        session = session,
                        dateFmt = dateFmt,
                        onViewFile = onViewFile,
                        onViewSession = onViewSession,
                        onShareSession = {
                            // Share all files in session as a zip or sequential intents
                            val uris = session.files.mapNotNull { rf ->
                                try {
                                    FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", File(rf.path))
                                } catch (_: Exception) { null }
                            }
                            if (uris.isNotEmpty()) {
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "text/csv"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    putExtra(Intent.EXTRA_SUBJECT,
                                        "ShimmerENACT Recording — ${session.sessionId}")
                                }
                                context.startActivity(Intent.createChooser(intent, "Share recording"))
                            }
                        },
                        onShareFile = { rf ->
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", File(rf.path))
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share ${rf.name}"))
                            } catch (_: Exception) {}
                        },
                        onDeleteSession = { deleteTarget = session }
                    )
                }
            }
        }
        } // end outer Column
    }

    deleteTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = EnactSurface,
            title = { Text("Delete session?", color = EnactOnSurface) },
            text = {
                Text(
                    "This will permanently delete ${session.files.size} file${if (session.files.size != 1) "s" else ""} " +
                    "from session ${session.sessionId}.",
                    color = EnactOnSurface.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session.sessionId)
                    deleteTarget = null
                }) { Text("Delete", color = EnactError, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = EnactGreen) }
            }
        )
    }
}

@Composable
fun SessionCard(
    session: RecordingSession,
    dateFmt: SimpleDateFormat,
    onViewFile: (RecordingFile) -> Unit = {},
    onViewSession: (RecordingSession) -> Unit = {},
    onShareSession: () -> Unit,
    onShareFile: (RecordingFile) -> Unit,
    onDeleteSession: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val totalSize = session.files.sumOf { it.sizeBytes }
    val totalRows = session.files.sumOf { it.rowCount }

    Card(
        colors = CardDefaults.cardColors(containerColor = EnactSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            // Session header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(EnactGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FolderOpen, null,
                        tint = EnactGreen, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dateFmt.format(Date(session.startTimeMs)),
                        fontWeight = FontWeight.SemiBold,
                        color = EnactOnSurface, fontSize = 13.sp
                    )
                    Row {
                        Text("${session.files.size} file${if (session.files.size != 1) "s" else ""}",
                            fontSize = 11.sp, color = EnactGreen.copy(alpha = 0.7f))
                        Text("  •  ", fontSize = 11.sp, color = EnactOnSurface.copy(alpha = 0.3f))
                        Text(formatSize(totalSize),
                            fontSize = 11.sp, color = EnactOnSurfaceDim)
                        if (totalRows > 0) {
                            Text("  •  ", fontSize = 11.sp, color = EnactOnSurface.copy(alpha = 0.3f))
                            Text("$totalRows rows",
                                fontSize = 11.sp, color = EnactOnSurfaceDim)
                        }
                    }
                }
                // View all signals button (only when session has 2+ signal files)
                if (session.files.size >= 2) {
                    IconButton(onClick = { onViewSession(session) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MultilineChart, "View all signals", tint = EnactGreen,
                            modifier = Modifier.size(18.dp))
                    }
                }
                // Share all button
                IconButton(onClick = onShareSession, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Share, "Share session", tint = EnactGreen,
                        modifier = Modifier.size(18.dp))
                }
                // Delete session button
                IconButton(onClick = onDeleteSession, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = EnactError.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp))
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = EnactOnSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Expanded file list
            AnimatedVisibility(visible = expanded) {
                Column {
                    Divider(color = EnactSurfaceVar)
                    session.files.forEach { rf ->
                        FileRow(
                            file = rf,
                            onView = { onViewFile(rf) },
                            onShare = { onShareFile(rf) }
                        )
                        if (rf != session.files.last()) {
                            Divider(
                                color = EnactSurfaceVar.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileRow(file: RecordingFile, onView: () -> Unit = {}, onShare: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.TableChart, null,
            tint = EnactGreen.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.signalDisplayName.ifBlank { file.name },
                color = EnactOnSurface, fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row {
                if (file.rateHz > 0) {
                    Text("${file.rateHz} Hz", fontSize = 10.sp, color = EnactGreen.copy(alpha = 0.7f))
                    Text("  •  ", fontSize = 10.sp, color = EnactOnSurface.copy(alpha = 0.25f))
                }
                if (file.signalUnit.isNotBlank()) {
                    Text(file.signalUnit, fontSize = 10.sp, color = EnactOnSurface.copy(alpha = 0.4f))
                    Text("  •  ", fontSize = 10.sp, color = EnactOnSurface.copy(alpha = 0.25f))
                }
                Text(file.sizeDisplay, fontSize = 10.sp, color = EnactOnSurface.copy(alpha = 0.4f))
                if (file.rowCount > 0) {
                    Text("  •  ", fontSize = 10.sp, color = EnactOnSurface.copy(alpha = 0.25f))
                    Text("${file.rowCount} rows", fontSize = 10.sp,
                        color = EnactOnSurface.copy(alpha = 0.4f))
                }
            }
        }
        IconButton(onClick = onView, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ShowChart, "View graph", tint = EnactGreen.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Share, "Share", tint = EnactGreen.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp))
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes > 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes > 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
