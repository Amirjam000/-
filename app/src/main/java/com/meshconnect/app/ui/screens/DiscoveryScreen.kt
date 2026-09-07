package com.meshconnect.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshconnect.app.model.PeerConnectionStatus
import com.meshconnect.app.model.PeerDevice
import com.meshconnect.app.ui.theme.DarkBackground
import com.meshconnect.app.ui.theme.DarkCard
import com.meshconnect.app.ui.theme.DarkSurface
import com.meshconnect.app.ui.theme.MeshBlue
import com.meshconnect.app.ui.theme.MeshGreen
import com.meshconnect.app.ui.theme.MeshOrange
import com.meshconnect.app.ui.theme.MeshRed
import com.meshconnect.app.ui.theme.TextPrimary
import com.meshconnect.app.ui.theme.TextSecondary
import com.meshconnect.app.viewmodel.MainViewModel

@Composable
fun DiscoveryScreen(
    viewModel: MainViewModel,
    onNavigateToChat: (String) -> Unit
) {
    val myDeviceName by viewModel.myDeviceName.collectAsState()
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsState()
    val pendingConnection by viewModel.pendingConnection.collectAsState()

    var showNameDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(myDeviceName) }

    // دیالوگ تایید اتصال ورودی (Accept / Reject Dialog)
    pendingConnection?.let { peer ->
        AlertDialog(
            onDismissRequest = { viewModel.rejectConnection(peer.endpointId) },
            containerColor = DarkCard,
            title = {
                Text(
                    text = "درخواست اتصال جدید",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "دستگاه «${peer.name}» مایل به برقراری ارتباط در شبکه مش است.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    peer.authToken?.let { token ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "کد احراز هویت: $token",
                                color = MeshGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.acceptConnection(peer.endpointId) },
                    colors = ButtonDefaults.buttonColors(containerColor = MeshGreen)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("پذیرش", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.rejectConnection(peer.endpointId) }
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = MeshRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("رد کردن", color = MeshRed)
                }
            }
        )
    }

    // دیالوگ تغییر نام دستگاه
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            containerColor = DarkCard,
            title = { Text("تغییر نام دستگاه", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("نام نمایشی شما در شبکه مش") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = MeshBlue,
                        unfocusedBorderColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDeviceName(tempName)
                        showNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeshBlue)
                ) {
                    Text("ذخیره")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("انصراف", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // ۱. کارت اطلاعات من و کنترل نام
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "نام این دستگاه در شبکه مش:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = myDeviceName,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = {
                    tempName = myDeviceName
                    showNameDialog = true
                }) {
                    Icon(Icons.Default.Edit, contentDescription = "ویرایش نام", tint = MeshBlue)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ۲. دکمه‌های کنترل Advertising و Discovery
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.toggleAdvertising() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAdvertising) MeshGreen else DarkSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = if (isAdvertising) Color.Black else TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAdvertising) "در حال انتشار" else "شروع انتشار",
                    color = if (isAdvertising) Color.Black else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = { viewModel.toggleDiscovery() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDiscovering) MeshBlue else DarkSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                if (isDiscovering) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = null,
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isDiscovering) "در حال جستجو..." else "جستجوی همتا",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ۳. دکمه ورود به چت برودکست مش گروهی
        Button(
            onClick = { onNavigateToChat("BROADCAST") },
            colors = ButtonDefaults.buttonColors(containerColor = MeshBlue.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Cast, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("چت گروهی مش (Broadcast به همه همتاها)", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ۴. لیست دستگاه‌های متصل و کشف شده
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // بخش دستگاه‌های متصل
            if (connectedPeers.isNotEmpty()) {
                item {
                    Text(
                        text = "دستگاه‌های متصل (${connectedPeers.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MeshGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(connectedPeers) { peer ->
                    ConnectedPeerItem(
                        peer = peer,
                        onChatClick = { onNavigateToChat(peer.endpointId) },
                        onDisconnectClick = { viewModel.disconnect(peer.endpointId) }
                    )
                }
            }

            // بخش دستگاه‌های کشف شده اطراف
            item {
                Text(
                    text = "دستگاه‌های پیدا شده (${discoveredPeers.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MeshBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (discoveredPeers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isDiscovering) "در حال اسکن فرکانس‌های رادیویی اطراف..." else "جستجو را شروع کنید تا دستگاه‌های نزدیک را بیابید.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(discoveredPeers) { peer ->
                    DiscoveredPeerItem(
                        peer = peer,
                        onConnectClick = { viewModel.requestConnection(peer.endpointId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedPeerItem(
    peer: PeerDevice,
    onChatClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(MeshGreen, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = peer.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "متصل در کلاستر مش • شناسه: ${peer.endpointId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Row {
                Button(
                    onClick = onChatClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MeshBlue),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("چت / تماس", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onDisconnectClick) {
                    Icon(Icons.Default.LinkOff, contentDescription = "قطع", tint = MeshRed)
                }
            }
        }
    }
}

@Composable
private fun DiscoveredPeerItem(
    peer: PeerDevice,
    onConnectClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = peer.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = "شناسه: ${peer.endpointId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            if (peer.status == PeerConnectionStatus.CONNECTING) {
                CircularProgressIndicator(
                    color = MeshBlue,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Button(
                    onClick = onConnectClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MeshBlue),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("درخواست اتصال", fontSize = 12.sp)
                }
            }
        }
    }
}
