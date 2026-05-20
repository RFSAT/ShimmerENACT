package com.rfsat.shimmerenact.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rfsat.shimmerenact.data.repository.RecordingFile
import com.rfsat.shimmerenact.ui.theme.*
import com.rfsat.shimmerenact.viewmodel.ShimmerViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    viewModel: ShimmerViewModel,
    onBack: () -> Unit
) {
    val recordings by viewModel.recordings.collectAsState()
    val context = LocalContext.current

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US) }

    var deleteTarget by remember { mutableStateOf<RecordingFile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordings", color = EnactOnSurface) },
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

        if (recordings.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null, tint = EnactGreen.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No recordings yet", color = EnactOnSurface.copy(alpha = 0.4f), fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Connect a sensor and press Record", color = EnactOnSurface.copy(alpha = 0.25f),
                        fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("${recordings.size} CSV file${if (recordings.size != 1) "s" else ""}",
                        fontSize = 12.sp, color = EnactGreen.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                items(recordings, key = { it.path }) { rec ->
                    RecordingRow(
                        rec = rec,
                        dateStr = dateFormat.format(Date(rec.lastModified)),
                        onShare = {
                            val file = File(rec.path)
                            val uri = FileProvider.getUriForFile(context,
                                "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share recording"))
                        },
                        onDelete = { deleteTarget = rec }
                    )
                }
            }
        }
    }

    // Confirm delete dialog
    deleteTarget?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = EnactSurface,
            title = { Text("Delete recording?", color = EnactOnSurface) },
            text = { Text("This will permanently delete:\n${rec.name}.csv",
                color = EnactOnSurface.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecording(rec.path)
                    deleteTarget = null
                }) {
                    Text("Delete", color = EnactError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = EnactGreen)
                }
            }
        )
    }
}

@Composable
fun RecordingRow(
    rec: RecordingFile,
    dateStr: String,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(EnactSurface)
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
            Icon(Icons.Default.TableChart, null, tint = EnactGreen, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(rec.name, color = EnactOnSurface, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row {
                Text(dateStr, fontSize = 11.sp, color = EnactOnSurface.copy(alpha = 0.45f))
                Text("  •  ", fontSize = 11.sp, color = EnactOnSurface.copy(alpha = 0.3f))
                Text(rec.sizeDisplay, fontSize = 11.sp, color = EnactGreen.copy(alpha = 0.7f))
            }
        }
        IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Share, "Share", tint = EnactGreen, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, "Delete", tint = EnactError.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp))
        }
    }
}
