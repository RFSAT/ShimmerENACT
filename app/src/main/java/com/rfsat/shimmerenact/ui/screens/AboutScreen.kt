package com.rfsat.shimmerenact.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rfsat.shimmerenact.BuildConfig
import com.rfsat.shimmerenact.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About", color = EnactOnSurface) },
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // App logo / header
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.radialGradient(listOf(EnactGreen.copy(alpha = 0.4f), EnactDarkMid))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Sensors, null, tint = EnactGreen, modifier = Modifier.size(48.dp))
            }

            Spacer(Modifier.height(16.dp))
            Text("ShimmerENACT", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = EnactGreen)
            Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = EnactOnSurfaceDim)

            Spacer(Modifier.height(28.dp))

            // RFSAT section
            AboutCard(
                icon = Icons.Default.Business,
                title = "Developed by",
                accentColor = EnactGreen
            ) {
                Text("RFSAT Limited", fontWeight = FontWeight.Bold, color = EnactOnSurface,
                    fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "RFSAT Limited is a non-profit research-performing SME, established in Ireland, " +
                    "with research offices also in Athens (Greece). Its operation focuses on research " +
                    "and development through national and international research funding, and industrial " +
                    "consultancy. Focus areas include hybrid indoor/outdoor positioning, GIS systems/" +
                    "services, networked audio-visual systems, VR/AR/XR for Virtual Worlds, immersive " +
                    "mixed-reality U/Is for gaming and entertainment, autonomous systems (UAS, UGV and " +
                    "UUV), Critical Infrastructure (CI) protection, Cyber-Physical security systems, " +
                    "Machine Learning and Cognitive Artificial Intelligence (AI), Future Internet and " +
                    "5G/6G Mobile Communications etc.",
                    fontSize = 13.sp, color = EnactOnSurface.copy(alpha = 0.65f), lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rfsat.com")))
                    },
                    border = BorderStroke(1.dp, EnactGreen.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, null, tint = EnactGreen,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("www.rfsat.com", color = EnactGreen, fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://rfsat.com/index.php/en/projects-and-activities/ongoing-projects/enact-horizon-2-1-health")))
                    },
                    border = BorderStroke(1.dp, EnactGreen.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, null, tint = EnactGreen,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ENACT on RFSAT portal", color = EnactGreen, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ENACT EU funding section
            AboutCard(
                icon = Icons.Default.Flag,
                title = "EU Funded Project",
                accentColor = Color(0xFF0065AE)  // EU blue
            ) {
                Text("ENACT", fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFF0065AE), fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Funded by the European Union under the Horizon Europe programme.\n" +
                    "Grant Agreement No. 101157151\n\n" +
                    "ENACT focuses on Environmental monitoring, Air quality assessment, and health/wellbeing outcomes across European pilot cities.",
                    fontSize = 13.sp,
                    color = EnactOnSurface.copy(alpha = 0.65f),
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://cordis.europa.eu/project/id/101157151")))
                    },
                    border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF0065AE).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, null,
                        tint = androidx.compose.ui.graphics.Color(0xFF0065AE),
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("CORDIS Project Page",
                        color = androidx.compose.ui.graphics.Color(0xFF0065AE), fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://enact-he.eu")))
                    },
                    border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF0065AE).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, null,
                        tint = androidx.compose.ui.graphics.Color(0xFF0065AE),
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("enact-he.eu  –  Official Project Website",
                        color = androidx.compose.ui.graphics.Color(0xFF0065AE), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Shimmer credit
            AboutCard(
                icon = Icons.Default.Sensors,
                title = "Hardware Platform",
                accentColor = EnactLime
            ) {
                Text("Shimmer3 Research Sensors", fontWeight = FontWeight.Bold,
                    color = EnactOnSurface, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "This application interfaces with Shimmer3 wearable sensing platforms " +
                    "(shimmersensing.com). Supported units:",
                    fontSize = 13.sp,
                    color = EnactOnSurface.copy(alpha = 0.65f),
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(6.dp))
                val units = listOf(
                    "GSR+ Unit" to "SR48-5-0  ·  Galvanic Skin Response, PPG, 9-DoF IMU",
                    "EXG Unit" to "SR47-6-0  ·  ECG, EMG, EEG via dual ADS1292R",
                    "IMU Unit" to "SR31  ·  Accel LN+WR, Gyro, Mag, Pressure, Temperature",
                    "EMG Unit" to "SR47-6-0 (EMG mode)  ·  Surface EMG, 2 channels, 9-DoF IMU",
                    "Ebio Unit" to "SR59  ·  ECG + Bioimpedance/Respiration, 9-DoF IMU",
                    "Bridge Amplifier+" to "SR37  ·  Strain gauge / load cell, skin temp, 9-DoF IMU",
                    "200g IMU" to "SR31-200G  ·  High-g ±200g accel (ADXL377) + full 9-DoF IMU",
                    "PROTO3 Deluxe" to "SR50  ·  4× analogue input (3.5 mm TRRS), 9-DoF IMU",
                    "Custom" to "User-defined  ·  Generic Shimmer3 configuration"
                )
                units.forEach { (name, desc) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("•", fontSize = 12.sp,
                            color = EnactLime, modifier = Modifier.width(14.dp))
                        Column {
                            Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = EnactOnSurface)
                            Text(desc, fontSize = 11.sp,
                                color = EnactOnSurface.copy(alpha = 0.55f), lineHeight = 15.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Open source
            AboutCard(
                icon = Icons.Default.Code,
                title = "Open Source",
                accentColor = EnactOnSurfaceDim
            ) {
                Text("Source Code", fontWeight = FontWeight.Bold, color = EnactOnSurface, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("Available on GitHub under MIT licence.",
                    fontSize = 13.sp, color = EnactOnSurface.copy(alpha = 0.65f))
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/rfsat/ShimmerENACT")))
                    },
                    border = BorderStroke(1.dp, EnactOnSurface.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, null,
                        tint = EnactOnSurfaceDim, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("github.com/rfsat/ShimmerENACT",
                        color = EnactOnSurfaceDim, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "© 2024–2025 RFSAT Limited. All rights reserved.\n" +
                "Funded by the European Union. Views and opinions expressed are those of the author(s) only " +
                "and do not necessarily reflect those of the European Union or the European Research " +
                "Executive Agency (REA). Neither the EU nor REA can be held responsible for them.",
                fontSize = 10.sp,
                color = EnactOnSurface.copy(alpha = 0.3f),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun AboutCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    accentColor: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = EnactSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = accentColor.copy(alpha = 0.8f), letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
