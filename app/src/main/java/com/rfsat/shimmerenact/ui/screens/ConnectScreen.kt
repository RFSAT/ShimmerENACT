package com.rfsat.shimmerenact.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.rfsat.shimmerenact.data.models.*
import com.rfsat.shimmerenact.ui.theme.*
import com.rfsat.shimmerenact.viewmodel.ShimmerViewModel

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    viewModel: ShimmerViewModel,
    onConnected: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val activeSensorType by viewModel.activeSensorType.collectAsState()
    val activeConfig by viewModel.activeConfig.collectAsState()

    var pairedDevices by remember { mutableStateOf<List<BtDeviceInfo>>(emptyList()) }
    var manualAddress by remember { mutableStateOf("") }
    var showManualEntry by remember { mutableStateOf(false) }
    var btRadioIdInput by remember { mutableStateOf(activeConfig.btRadioId) }

    // BT permissions
    val btPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        listOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionsState = rememberMultiplePermissionsState(btPermissions)

    // Enable BT launcher
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pairedDevices = viewModel.refreshPairedDevices()
    }

    // Navigate when connected
    LaunchedEffect(uiState.connectionState) {
        if (uiState.connectionState == ConnectionState.CONNECTED) onConnected()
    }

    // Load paired devices when permissions granted
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            pairedDevices = viewModel.refreshPairedDevices()
        }
    }

    LaunchedEffect(activeConfig.btRadioId) {
        btRadioIdInput = activeConfig.btRadioId
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect Shimmer3", color = EnactOnSurface) },
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
                .padding(16.dp)
        ) {

            // Active sensor indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(EnactSurface)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sensors, null, tint = EnactGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(activeConfig.displayName, fontWeight = FontWeight.SemiBold,
                            color = EnactOnSurface, fontSize = 14.sp)
                        Text("Bluetooth name: Shimmer3-${activeConfig.btRadioId}",
                            fontSize = 12.sp, color = EnactGreen.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // BT Radio ID configurator
            Text("BT RADIO ID", fontSize = 11.sp, color = EnactGreen.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = btRadioIdInput,
                    onValueChange = { btRadioIdInput = it.uppercase().take(8) },
                    label = { Text("Radio ID (e.g. A096)") },
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
                Button(
                    onClick = {
                        viewModel.updateBtRadioId(activeSensorType, btRadioIdInput)
                        pairedDevices = viewModel.refreshPairedDevices()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EnactSurface),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, tint = EnactGreen)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Permission / BT state guard
            if (!permissionsState.allPermissionsGranted) {
                PermissionBanner { permissionsState.launchMultiplePermissionRequest() }
            } else if (!viewModel.btManager.isBluetoothEnabled()) {
                BtOffBanner {
                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            } else {

                // Connection status
                AnimatedVisibility(uiState.connectionState == ConnectionState.CONNECTING) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = EnactGreen
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Connecting…", color = EnactGreen)
                    }
                }

                // Error
                uiState.errorMessage?.let { err ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = EnactError.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, EnactError.copy(alpha = 0.5f))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = EnactError, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(err, color = EnactError, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = viewModel::clearError, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, tint = EnactError)
                            }
                        }
                    }
                }

                // Paired devices list
                Text("PAIRED DEVICES", fontSize = 11.sp, color = EnactGreen.copy(alpha = 0.6f),
                    letterSpacing = 1.5.sp)
                Spacer(Modifier.height(8.dp))

                if (pairedDevices.isEmpty()) {
                    Text("No paired devices found. Pair your Shimmer3 in Android Settings → Bluetooth first.",
                        fontSize = 13.sp, color = EnactOnSurface.copy(alpha = 0.5f))
                } else {
                    pairedDevices.forEach { device ->
                        val isShimmer = device.name.contains("Shimmer", ignoreCase = true)
                        DeviceRow(
                            device = device,
                            isRecommended = isShimmer,
                            isConnecting = uiState.connectionState == ConnectionState.CONNECTING,
                            onClick = { viewModel.connectToDevice(device.address) }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Manual address entry
                TextButton(onClick = { showManualEntry = !showManualEntry }) {
                    Icon(if (showManualEntry) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = EnactGreen.copy(alpha = 0.7f))
                    Spacer(Modifier.width(4.dp))
                    Text("Enter MAC address manually", color = EnactGreen.copy(alpha = 0.7f), fontSize = 13.sp)
                }

                AnimatedVisibility(showManualEntry) {
                    Column {
                        OutlinedTextField(
                            value = manualAddress,
                            onValueChange = { manualAddress = it.uppercase().take(17) },
                            label = { Text("MAC address (XX:XX:XX:XX:XX:XX)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EnactGreen,
                                focusedLabelColor = EnactGreen,
                                cursorColor = EnactGreen,
                                focusedTextColor = EnactOnSurface,
                                unfocusedTextColor = EnactOnSurface,
                                unfocusedBorderColor = EnactSurfaceVar
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { if (manualAddress.length == 17) viewModel.connectToDevice(manualAddress) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = manualAddress.length == 17 && uiState.connectionState != ConnectionState.CONNECTING,
                            colors = ButtonDefaults.buttonColors(containerColor = EnactGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.BluetoothConnected, null, tint = EnactDark)
                            Spacer(Modifier.width(6.dp))
                            Text("Connect", color = EnactDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Shimmer pairing hint
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = EnactSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, tint = EnactLime, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pairing a Shimmer3", fontWeight = FontWeight.SemiBold,
                            color = EnactLime, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "1. Hold the Shimmer3 button until the LED flashes yellow.\n" +
                        "2. Go to Android Settings → Bluetooth → Pair new device.\n" +
                        "3. Select \"Shimmer3-XXXX\" and enter PIN 1234 if prompted.\n" +
                        "4. Return here and tap the device to connect.",
                        fontSize = 12.sp,
                        color = EnactOnSurface.copy(alpha = 0.65f),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceRow(
    device: BtDeviceInfo,
    isRecommended: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isRecommended) EnactGreen.copy(alpha = 0.1f) else EnactSurface)
            .border(
                1.dp,
                if (isRecommended) EnactGreen.copy(alpha = 0.4f) else EnactSurfaceVar,
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = !isConnecting, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (isRecommended) EnactGreen.copy(alpha = 0.2f) else EnactSurfaceVar),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Bluetooth, null,
                tint = if (isRecommended) EnactGreen else EnactOnSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(device.name, fontWeight = FontWeight.Medium, color = EnactOnSurface, fontSize = 14.sp)
            Text(device.address, fontSize = 11.sp, color = EnactOnSurface.copy(alpha = 0.5f))
        }
        if (isRecommended) {
            Text("SHIMMER",
                fontSize = 9.sp,
                color = EnactGreen,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(EnactGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, null,
            tint = EnactOnSurface.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PermissionBanner(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = EnactWarning.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, EnactWarning.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Bluetooth permissions required", fontWeight = FontWeight.SemiBold,
                color = EnactWarning, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text("This app needs Bluetooth permissions to discover and connect to Shimmer3 sensors.",
                fontSize = 12.sp, color = EnactOnSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = EnactWarning),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Grant Permissions", color = EnactDark, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
fun BtOffBanner(onEnable: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = EnactError.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, EnactError.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Bluetooth is off", fontWeight = FontWeight.SemiBold,
                color = EnactError, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onEnable,
                colors = ButtonDefaults.buttonColors(containerColor = EnactError),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Enable Bluetooth", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}
