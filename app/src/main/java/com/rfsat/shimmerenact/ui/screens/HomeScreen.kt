package com.rfsat.shimmerenact.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rfsat.shimmerenact.data.models.ConnectionState
import com.rfsat.shimmerenact.data.models.SensorType
import com.rfsat.shimmerenact.ui.theme.*
import com.rfsat.shimmerenact.viewmodel.ShimmerViewModel

@Composable
fun HomeScreen(
    viewModel: ShimmerViewModel,
    onNavigateToConnect: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onDisconnect: () -> Unit = {}
) {
    val activeSensorType by viewModel.activeSensorType.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isConnected = uiState.connectionState == ConnectionState.CONNECTED
    val recordingState by viewModel.recordingState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EnactDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Header with RFSAT branding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(listOf(EnactSurface, EnactDarkMid))
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "ShimmerENACT",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = EnactGreen
                )
                Text(
                    "by RFSAT Limited",
                    fontSize = 13.sp,
                    color = EnactOnSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "ENACT • Horizon Europe 101157151",
                    fontSize = 11.sp,
                    color = EnactLime.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "SELECT SENSOR TYPE",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = EnactGreen.copy(alpha = 0.7f),
            letterSpacing = 2.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Sensor type cards
        SensorTypeCard(
            title = "GSR+ Unit",
            subtitle = "SR48-5-0",
            description = "Galvanic Skin Response, PPG, IMU",
            icon = Icons.Default.Sensors,
            isSelected = activeSensorType == SensorType.GSR_PLUS,
            accentColor = EnactGreen,
            onClick = { viewModel.selectSensorType(SensorType.GSR_PLUS) }
        )
        Spacer(Modifier.height(10.dp))
        SensorTypeCard(
            title = "EXG Unit",
            subtitle = "SR47-6-0",
            description = "ECG / EMG / EEG, IMU",
            icon = Icons.Default.MonitorHeart,
            isSelected = activeSensorType == SensorType.EXG,
            accentColor = EnactLime,
            onClick = { viewModel.selectSensorType(SensorType.EXG) }
        )
        Spacer(Modifier.height(10.dp))
        SensorTypeCard(
            title = "Custom Sensor",
            subtitle = "User-defined",
            description = "Generic Shimmer3 with custom configuration",
            icon = Icons.Default.Tune,
            isSelected = activeSensorType == SensorType.CUSTOM,
            accentColor = Color(0xFF39A8E0),
            onClick = { viewModel.selectSensorType(SensorType.CUSTOM) }
        )

        Spacer(Modifier.height(28.dp))

        if (isConnected) {
            // Show connected status + disconnect button
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = EnactGreen.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EnactGreen.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BluetoothConnected, null,
                        tint = EnactGreen, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Connected", fontWeight = FontWeight.SemiBold,
                            color = EnactGreen, fontSize = 14.sp)
                        if (recordingState.isRecording) {
                            Text("Recording in progress", fontSize = 12.sp,
                                color = EnactError.copy(alpha = 0.8f))
                        }
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        enabled = !recordingState.isRecording,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (recordingState.isRecording) EnactOnSurfaceDim.copy(alpha = 0.3f)
                            else EnactError.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Disconnect",
                            color = if (recordingState.isRecording)
                                EnactOnSurfaceDim.copy(alpha = 0.4f) else EnactError,
                            fontSize = 13.sp)
                    }
                }
            }
        } else {
            Button(
                onClick = onNavigateToConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EnactGreen)
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = EnactDark)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Connect to Shimmer3",
                    fontWeight = FontWeight.Bold,
                    color = EnactDark,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onNavigateToAbout) {
            Icon(Icons.Default.Info, contentDescription = null,
                tint = EnactGreen.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("About & Credits", color = EnactGreen.copy(alpha = 0.6f), fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SensorTypeCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) accentColor else Color.Transparent
    val bgColor = if (isSelected) accentColor.copy(alpha = 0.12f) else EnactSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) borderColor else EnactSurfaceVar,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = if (isSelected) 0.25f else 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, color = EnactOnSurface, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    subtitle,
                    fontSize = 10.sp,
                    color = accentColor.copy(alpha = 0.8f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(description, fontSize = 12.sp, color = EnactOnSurfaceDim)
        }
        if (isSelected) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Selected",
                tint = accentColor, modifier = Modifier.size(22.dp))
        }
    }
}
