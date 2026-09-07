package com.meshconnect.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshconnect.app.model.ChatMessage
import com.meshconnect.app.model.MessageStatus
import com.meshconnect.app.model.RouteType
import com.meshconnect.app.ui.theme.BubbleOther
import com.meshconnect.app.ui.theme.BubbleSelf
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    chatId: String,
    viewModel: MainViewModel,
    onStartVideoCall: (String) -> Unit
) {
    val context = LocalContext.current
    val messagesMap by viewModel.messages.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsState()
    val isPttTransmitting by viewModel.isPttTransmitting.collectAsState()
    val isPttReceiving by viewModel.isPttReceiving.collectAsState()
    val pttAmplitude by viewModel.pttAmplitude.collectAsState()
    val fileProgressMap by viewModel.fileTransferProgress.collectAsState()

    val chatMessages = messagesMap[chatId] ?: emptyList()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }

    val peer = connectedPeers.firstOrNull { it.endpointId == chatId || it.deviceId == chatId }
    val isBroadcast = chatId.equals("BROADCAST", ignoreCase = true)
    val chatTitle = if (isBroadcast) "کانال عمومی مش (همه همتاها)" else (peer?.name ?: "کاربر $chatId")

    // لانچر انتخاب فایل
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            var fileName = "فایل_انتخابی"
            var fileSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
            viewModel.sendFile(chatId, uri, fileName, fileSize)
        }
    }

    // اسکرول به آخرین پیام با رسیدن پیام جدید
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    // انیمیشن پالس برای دکمه PTT در حال ضبط
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // ۱. نوار اطلاعات هدر چت
        Card(
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = chatTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isBroadcast) "ارسال آزاد به تمامی گره‌ها" else (if (peer != null) "اتصال مستقیم P2P محلی" else "مسیر رله مش یا اینترنت"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (peer != null || isBroadcast) MeshGreen else TextSecondary
                    )
                }

                // دکمه تماس تصویری (برای چت‌های خصوصی)
                if (!isBroadcast) {
                    IconButton(
                        onClick = { onStartVideoCall(chatId) },
                        modifier = Modifier
                            .background(MeshBlue.copy(alpha = 0.2f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "تماس تصویری",
                            tint = MeshBlue
                        )
                    }
                }
            }
        }

        // ۲. نشانگر وضعیت استریم صوتی زنده (Push-to-Talk)
        AnimatedVisibility(visible = isPttTransmitting || isPttReceiving) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isPttTransmitting) MeshGreen.copy(alpha = 0.25f) else MeshOrange.copy(alpha = 0.25f))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (isPttTransmitting) MeshGreen else MeshOrange,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(pulseScale)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPttTransmitting) "در حال ارسال زنده صدا (PTT)... دامنه: $pttAmplitude" else "در حال پخش صدای دریافتی واکی‌تاکی...",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ۳. لیست پیام‌ها
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatMessages) { msg ->
                ChatBubble(message = msg, fileProgress = fileProgressMap.values.firstOrNull())
            }
        }

        // ۴. بخش ورودی پیام، دکمه ارسال فایل و دکمه نگهدار و صحبت کن (PTT)
        Card(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // دکمه انتخاب و ارسال فایل
                IconButton(
                    onClick = { filePickerLauncher.launch("*/*") }
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "ارسال فایل",
                        tint = MeshBlue
                    )
                }

                // فیلد ورود متن
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("پیام خود را بنویسید...", fontSize = 14.sp, color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = DarkCard,
                        unfocusedContainerColor = DarkCard
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // اگر متنی نوشته شده باشد، دکمه Send؛ در غیر این صورت دکمه Push-to-Talk صوتی
                if (inputText.isNotBlank()) {
                    IconButton(
                        onClick = {
                            viewModel.sendMessage(chatId, inputText)
                            inputText = ""
                        },
                        modifier = Modifier
                            .background(MeshBlue, CircleShape)
                            .size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "ارسال",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    // دکمه بیسیم (Push-to-Talk) با تشخیص نگه داشتن و رها کردن لمس
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .scale(if (isPttTransmitting) pulseScale else 1.0f)
                            .background(if (isPttTransmitting) MeshGreen else DarkCard, CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        viewModel.startPtt()
                                        tryAwaitRelease()
                                        viewModel.stopPtt()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "بیسیم PTT",
                            tint = if (isPttTransmitting) Color.Black else MeshBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    fileProgress: Float?
) {
    val isSelf = message.isFromMe
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isSelf) 16.dp else 4.dp,
                bottomEnd = if (isSelf) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelf) BubbleSelf else BubbleOther
            ),
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // نام فرستنده در چت‌های گروهی یا دریافتی
                if (!isSelf) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MeshGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // متن پیام
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }

                // نمایش فایل و نوار پیشرفت انتقال
                if (message.fileName != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBackground.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = MeshBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message.fileName,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                            message.fileSize?.let { size ->
                                Text(
                                    text = "${size / 1024} KB",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (message.status == MessageStatus.SENDING && fileProgress != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { fileProgress },
                            color = MeshGreen,
                            trackColor = DarkCard,
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // برچسب مسیر طی شده، زمان و وضعیت تحویل پیام
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // برچسب مسیر (آفلاین محلی / رله مش / اینترنت)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (message.routeType) {
                            RouteType.NEARBY_DIRECT -> {
                                Icon(Icons.Default.NearMe, contentDescription = null, tint = MeshGreen, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Nearby مستقیم", fontSize = 10.sp, color = MeshGreen)
                            }
                            RouteType.MESH_RELAY -> {
                                Icon(Icons.Default.Hub, contentDescription = null, tint = MeshOrange, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("رله مش (${message.hopCount} جهش)", fontSize = 10.sp, color = MeshOrange)
                            }
                            RouteType.CLOUD_INTERNET -> {
                                Icon(Icons.Default.Public, contentDescription = null, tint = MeshBlue, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("اینترنت", fontSize = 10.sp, color = MeshBlue)
                            }
                        }
                    }

                    // زمان و تیک وضعیت تحویل
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timeFormat.format(Date(message.timestamp)),
                            fontSize = 10.sp,
                            color = TextSecondary
                        )

                        if (isSelf) {
                            Spacer(modifier = Modifier.width(4.dp))
                            when (message.status) {
                                MessageStatus.SENDING -> Icon(Icons.Default.Schedule, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                                MessageStatus.RELAYED -> Icon(Icons.Default.Check, contentDescription = null, tint = MeshOrange, modifier = Modifier.size(14.dp))
                                MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, contentDescription = null, tint = MeshGreen, modifier = Modifier.size(14.dp))
                                MessageStatus.FAILED -> Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MeshRed, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
