package com.meshconnect.app.ui.components

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.meshconnect.app.ui.theme.DarkCard
import com.meshconnect.app.ui.theme.DarkSurface
import com.meshconnect.app.ui.theme.MeshBlue
import com.meshconnect.app.ui.theme.MeshGreen
import com.meshconnect.app.ui.theme.TextPrimary
import com.meshconnect.app.ui.theme.TextSecondary

/**
 * دریافت لیست دقیق دسترسی‌های مورد نیاز بر اساس نسخه اندروید دستگاه کاربر
 * (سازگار با گوشی‌های قدیمی و اندروید ۱۲ به بالا)
 */
fun getRequiredPermissions(): List<String> {
    val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // اندروید ۱۲ به بالا
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // اندروید ۱۳ به بالا برای ارتباط وای‌فای محلی
        permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    return permissions
}

/**
 * کامپوننت هوشمند اعطای مجوزها با توضیحات فارسی و طراحی مدرن
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionHandler(
    onPermissionsGranted: @Composable () -> Unit
) {
    val requiredPermissions = getRequiredPermissions()
    val permissionsState = rememberMultiplePermissionsState(permissions = requiredPermissions)

    if (permissionsState.allPermissionsGranted) {
        onPermissionsGranted()
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkSurface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MeshBlue,
                        modifier = Modifier.size(56.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "مجوزهای دسترسی مورد نیاز",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "برای برقراری ارتباط آفلاین بلوتوث/وای‌فای، بی‌سیم صوتی و تماس تصویری WebRTC، به دسترسی‌های زیر نیاز است:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    PermissionItem(
                        icon = Icons.Default.Bluetooth,
                        title = "بلوتوث و دستگاه‌های اطراف",
                        desc = "کشف و اتصال به دوستان در فواصل نزدیک"
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionItem(
                        icon = Icons.Default.Wifi,
                        title = "شبکه محلی و موقعیت مکانی",
                        desc = "ایجاد شبکه مش P2P با حداکثر سرعت انتقال"
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionItem(
                        icon = Icons.Default.Mic,
                        title = "میکروفون",
                        desc = "ارسال پیام صوتی واکی‌تاکی و مکالمه زنده"
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionItem(
                        icon = Icons.Default.Videocam,
                        title = "دوربین",
                        desc = "برقراری تماس‌های تصویری WebRTC"
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { permissionsState.launchMultiplePermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = MeshBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = "تایید و فعال‌سازی دسترسی‌ها",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MeshGreen,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
