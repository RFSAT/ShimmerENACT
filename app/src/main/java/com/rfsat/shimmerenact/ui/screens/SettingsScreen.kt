package com.rfsat.shimmerenact.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rfsat.shimmerenact.data.models.SensorType
import com.rfsat.shimmerenact.ui.theme.*
import com.rfsat.shimmerenact.viewmodel.ShimmerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ShimmerViewModel,
    onBack: () -> Unit,
    onSamplingRate: () -> Unit = {}
) {
    val activeConfig by viewModel.activeConfig.collectAsState()

    var gsrId    by remember { mutableStateOf("") }
    var exgId    by remember { mutableStateOf("") }
    var imuId    by remember { mutableStateOf("") }
    var emgId    by remember { mutableStateOf("") }
    var customId by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        gsrId = SensorType.GSR_PLUS.defaultBtSuffix
        exgId = SensorType.EXG.defaultBtSuffix
        imuId = SensorType.IMU.defaultBtSuffix
        emgId = SensorType.EMG.defaultBtSuffix
    }

    LaunchedEffect(activeConfig) {
        when (activeConfig.sensorType) {
            SensorType.GSR_PLUS -> gsrId = activeConfig.btRadioId
            SensorType.EXG      -> exgId = activeConfig.btRadioId
            SensorType.IMU      -> imuId = activeConfig.btRadioId
            SensorType.EMG      -> emgId = activeConfig.btRadioId
            SensorType.CUSTOM   -> { customId = activeConfig.btRadioId; customName = activeConfig.customName }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = EnactOnSurface) },
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

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // GSR+ settings
            SettingsGroup(title = "GSR+ Unit (SR48-5-0)", accentColor = EnactGreen) {
                SettingsTextField(
                    label = "BT Radio ID",
                    value = gsrId,
                    onChange = { gsrId = it.uppercase().take(8) },
                    hint = "Default: A096",
                    onSave = { viewModel.updateBtRadioId(SensorType.GSR_PLUS, gsrId) }
                )
            }

            // EXG settings
            SettingsGroup(title = "EXG Unit (SR47-6-0)", accentColor = EnactLime) {
                SettingsTextField(
                    label = "BT Radio ID",
                    value = exgId,
                    onChange = { exgId = it.uppercase().take(8) },
                    hint = "Default: A077",
                    onSave = { viewModel.updateBtRadioId(SensorType.EXG, exgId) }
                )
            }

            // IMU settings
            SettingsGroup(title = "IMU Unit (SR31)", accentColor = androidx.compose.ui.graphics.Color(0xFF39A8E0)) {
                SettingsTextField(
                    label = "BT Radio ID",
                    value = imuId,
                    onChange = { imuId = it.uppercase().take(8) },
                    hint = "Default: A080",
                    onSave = { viewModel.updateBtRadioId(SensorType.IMU, imuId) }
                )
            }

            // EMG settings
            SettingsGroup(title = "EMG Unit (SR47-6-0 EMG mode)", accentColor = androidx.compose.ui.graphics.Color(0xFFE07B39)) {
                SettingsTextField(
                    label = "BT Radio ID",
                    value = emgId,
                    onChange = { emgId = it.uppercase().take(8) },
                    hint = "Default: A077 (same hardware as EXG)",
                    onSave = { viewModel.updateBtRadioId(SensorType.EMG, emgId) }
                )
            }

            // Custom sensor settings
            SettingsGroup(title = "Custom Sensor", accentColor = androidx.compose.ui.graphics.Color(0xFF39A8E0)) {
                SettingsTextField(
                    label = "BT Radio ID",
                    value = customId,
                    onChange = { customId = it.uppercase().take(8) },
                    hint = "Enter radio suffix",
                    onSave = { viewModel.updateBtRadioId(SensorType.CUSTOM, customId) }
                )
                Spacer(Modifier.height(8.dp))
                SettingsTextField(
                    label = "Sensor display name",
                    value = customName,
                    onChange = { customName = it },
                    hint = "e.g. Environmental Node 1",
                    onSave = { viewModel.updateCustomName(customName) }
                )
            }

            // Sampling rate card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSamplingRate),
                colors = CardDefaults.cardColors(
                    containerColor = EnactGreen.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, EnactGreen.copy(alpha = 0.3f))
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Speed, null, tint = EnactGreen,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sampling Rates", fontWeight = FontWeight.SemiBold,
                            color = EnactOnSurface, fontSize = 14.sp)
                        Text(
                            "Hardware: ${activeConfig.hardwareRateHz} Hz  •  Per-signal decimation",
                            fontSize = 12.sp, color = EnactOnSurfaceDim
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null,
                        tint = EnactGreen.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }

            // Info about storage
            Card(
                colors = CardDefaults.cardColors(containerColor = EnactSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.FolderOpen, null, tint = EnactGreen.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Recording storage", fontWeight = FontWeight.SemiBold,
                            color = EnactOnSurface, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "CSV files are saved to:\nAndroid/data/com.rfsat.shimmerenact/files/Documents/ShimmerENACT/\n\n" +
                            "Access via Files app, Android/data, or share directly from the Recordings screen.",
                            fontSize = 12.sp,
                            color = EnactOnSurfaceDim,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    accentColor: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = EnactSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = accentColor, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingsTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    onSave: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label, fontSize = 12.sp) },
            placeholder = { Text(hint, fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EnactGreen,
                unfocusedBorderColor = EnactSurfaceVar,
                focusedLabelColor = EnactGreen,
                cursorColor = EnactGreen,
                focusedTextColor = EnactOnSurface,
                unfocusedTextColor = EnactOnSurface
            )
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSave,
            modifier = Modifier.size(42.dp)
        ) {
            Icon(Icons.Default.Save, "Save", tint = EnactGreen)
        }
    }
}
