package com.meshconnect.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshconnect.app.ui.theme.DarkSurface
import com.meshconnect.app.ui.theme.MeshBlue
import com.meshconnect.app.ui.theme.MeshGreen
import com.meshconnect.app.ui.theme.MeshOrange
import com.meshconnect.app.ui.theme.TextPrimary
import com.meshconnect.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshTopBar(
    title: String,
    connectedCount: Int,
    isAdvertising: Boolean,
    isDiscovering: Boolean,
    isInternetAvailable: Boolean,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                // برچسب‌های وضعیت شبکه مش و اینترنت
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    // وضعیت مش و تعداد همتاها
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (connectedCount > 0) MeshGreen.copy(alpha = 0.2f) else DarkSurface,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = if (connectedCount > 0) MeshGreen else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$connectedCount همتا",
                                fontSize = 12.sp,
                                color = if (connectedCount > 0) MeshGreen else TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // وضعیت اینترنت
                    Icon(
                        imageVector = if (isInternetAvailable) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (isInternetAvailable) MeshBlue else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        navigationIcon = { navigationIcon?.invoke() },
        actions = { actions?.invoke() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
    )
}
