package com.meshconnect.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshconnect.app.ui.theme.DarkBackground
import com.meshconnect.app.ui.theme.DarkCard
import com.meshconnect.app.ui.theme.MeshBlue
import com.meshconnect.app.ui.theme.MeshGreen
import com.meshconnect.app.ui.theme.TextPrimary
import com.meshconnect.app.ui.theme.TextSecondary
import com.meshconnect.app.viewmodel.MainViewModel

@Composable
fun ChatListScreen(
    viewModel: MainViewModel,
    onNavigateToChat: (String) -> Unit,
    onNavigateToDiscovery: () -> Unit
) {
    val messagesMap by viewModel.messages.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsState()

    // جمع‌آوری لیست تمام مخاطبانی که پیام داریم یا متصل هستند
    val chatIds = (messagesMap.keys + connectedPeers.map { it.endpointId }).distinct()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ۱. آیتم چت همگانی مش (Broadcast Channel)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToChat("BROADCAST") }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MeshBlue.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = null,
                                tint = MeshBlue,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "کانال عمومی مش (Broadcast)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "کل شبکه",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeshGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val lastMsg = messagesMap["BROADCAST"]?.lastOrNull()
                            Text(
                                text = lastMsg?.text ?: "پیام‌های ارسالی به تمام دستگاه‌های کلاستر می‌رسد",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "مخاطبان مستقیم و ریموت",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // ۲. لیست چت‌های خصوصی
            val privateChatIds = chatIds.filter { it != "BROADCAST" }
            if (privateChatIds.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "هنوز با هیچ همتایی ارتباط برقرار نکرده‌اید.\nروی دکمه جستجو بزنید تا دوستان اطراف را پیدا کنید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(privateChatIds) { chatId ->
                    val peer = connectedPeers.firstOrNull { it.endpointId == chatId || it.deviceId == chatId }
                    val peerName = peer?.name ?: "کاربر $chatId"
                    val isConnectedNearby = peer != null
                    val lastMsg = messagesMap[chatId]?.lastOrNull()

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToChat(chatId) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isConnectedNearby) MeshGreen.copy(alpha = 0.2f) else MeshBlue.copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isConnectedNearby) MeshGreen else MeshBlue,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = peerName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (isConnectedNearby) "متصل (مش)" else "آفلاین / اینترنت",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isConnectedNearby) MeshGreen else TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = lastMsg?.text ?: "شروع چت متنی، تماس تصویری یا بیسیم...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // دکمه شناور رفتن به صفحه کشف
        FloatingActionButton(
            onClick = onNavigateToDiscovery,
            containerColor = MeshBlue,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.NearMe, contentDescription = "کشف دستگاه‌ها")
        }
    }
}
