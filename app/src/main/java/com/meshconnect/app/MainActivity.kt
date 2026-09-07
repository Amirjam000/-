package com.meshconnect.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meshconnect.app.ui.components.MeshTopBar
import com.meshconnect.app.ui.components.PermissionHandler
import com.meshconnect.app.ui.screens.ChatListScreen
import com.meshconnect.app.ui.screens.ChatScreen
import com.meshconnect.app.ui.screens.DiscoveryScreen
import com.meshconnect.app.ui.screens.VideoCallScreen
import com.meshconnect.app.ui.theme.DarkBackground
import com.meshconnect.app.ui.theme.DarkCard
import com.meshconnect.app.ui.theme.DarkSurface
import com.meshconnect.app.ui.theme.MeshBlue
import com.meshconnect.app.ui.theme.MeshConnectTheme
import com.meshconnect.app.ui.theme.MeshGreen
import com.meshconnect.app.ui.theme.MeshRed
import com.meshconnect.app.ui.theme.TextPrimary
import com.meshconnect.app.ui.theme.TextSecondary
import com.meshconnect.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MeshConnectTheme {
                // مدیریت دسترسی‌ها در بدو ورود برنامه
                PermissionHandler {
                    MeshAppMain(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MeshAppMain(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val connectedPeers by viewModel.connectedPeers.collectAsState()
    val isAdvertising by viewModel.isAdvertising.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val incomingCall by viewModel.incomingCallSignal.collectAsState()

    // پنجره اعلان تماس تصویری ورودی (Incoming Call Dialog)
    incomingCall?.let { signal ->
        AlertDialog(
            onDismissRequest = { viewModel.rejectCall() },
            containerColor = DarkCard,
            title = {
                Text("تماس تصویری ورودی", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "کاربر ${signal.senderId} در حال برقراری تماس تصویری WebRTC با شماست.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.answerCall()
                        navController.navigate("video_call/${signal.senderId}")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeshGreen)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("پاسخ دادن", color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.rejectCall() }) {
                    Icon(Icons.Default.CallEnd, contentDescription = null, tint = MeshRed, modifier = Modifier.size(18.dp))
                    Text("رد تماس", color = MeshRed, modifier = Modifier.padding(start = 4.dp))
                }
            }
        )
    }

    val showBottomBar = currentRoute in listOf("chat_list", "discovery")

    Scaffold(
        topBar = {
            if (currentRoute?.startsWith("video_call") != true) {
                val title = when {
                    currentRoute == "chat_list" -> "پیام‌های مش"
                    currentRoute == "discovery" -> "اتصال و کشف همتاها"
                    currentRoute?.startsWith("chat/") == true -> "گفتگو"
                    else -> "MeshConnect"
                }

                MeshTopBar(
                    title = title,
                    connectedCount = connectedPeers.size,
                    isAdvertising = isAdvertising,
                    isDiscovering = isDiscovering,
                    isInternetAvailable = viewModel.signalingManager.isInternetAvailable(),
                    navigationIcon = if (!showBottomBar) {
                        {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت", tint = TextPrimary)
                            }
                        }
                    } else null
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = DarkSurface) {
                    NavigationBarItem(
                        selected = currentRoute == "chat_list",
                        onClick = {
                            if (currentRoute != "chat_list") {
                                navController.navigate("chat_list") {
                                    popUpTo("chat_list") { inclusive = true }
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "چت‌ها") },
                        label = { Text("چت‌ها", fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MeshBlue,
                            selectedTextColor = MeshBlue,
                            indicatorColor = MeshBlue.copy(alpha = 0.2f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )

                    NavigationBarItem(
                        selected = currentRoute == "discovery",
                        onClick = {
                            if (currentRoute != "discovery") {
                                navController.navigate("discovery") {
                                    popUpTo("discovery") { inclusive = true }
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.NearMe, contentDescription = "کشف") },
                        label = { Text("کشف و مش", fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MeshBlue,
                            selectedTextColor = MeshBlue,
                            indicatorColor = MeshBlue.copy(alpha = 0.2f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = "chat_list"
            ) {
                composable("chat_list") {
                    ChatListScreen(
                        viewModel = viewModel,
                        onNavigateToChat = { chatId ->
                            navController.navigate("chat/$chatId")
                        },
                        onNavigateToDiscovery = {
                            navController.navigate("discovery")
                        }
                    )
                }

                composable("discovery") {
                    DiscoveryScreen(
                        viewModel = viewModel,
                        onNavigateToChat = { chatId ->
                            navController.navigate("chat/$chatId")
                        }
                    )
                }

                composable(
                    route = "chat/{chatId}",
                    arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId") ?: "BROADCAST"
                    ChatScreen(
                        chatId = chatId,
                        viewModel = viewModel,
                        onStartVideoCall = { targetId ->
                            navController.navigate("video_call/$targetId")
                        }
                    )
                }

                composable(
                    route = "video_call/{targetUserId}",
                    arguments = listOf(navArgument("targetUserId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val targetUserId = backStackEntry.arguments?.getString("targetUserId") ?: ""
                    VideoCallScreen(
                        targetUserId = targetUserId,
                        viewModel = viewModel,
                        onCallEnded = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
