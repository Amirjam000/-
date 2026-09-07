package com.meshconnect.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.meshconnect.app.model.CallStatus
import com.meshconnect.app.ui.theme.DarkBackground
import com.meshconnect.app.ui.theme.DarkCard
import com.meshconnect.app.ui.theme.MeshBlue
import com.meshconnect.app.ui.theme.MeshGreen
import com.meshconnect.app.ui.theme.MeshRed
import com.meshconnect.app.ui.theme.TextPrimary
import com.meshconnect.app.ui.theme.TextSecondary
import com.meshconnect.app.viewmodel.MainViewModel
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun VideoCallScreen(
    targetUserId: String,
    viewModel: MainViewModel,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
    val callStatus by viewModel.callStatus.collectAsState()
    val isMicMuted by viewModel.isMicMuted.collectAsState()
    val isVideoDisabled by viewModel.isVideoDisabled.collectAsState()

    val webrtcManager = viewModel.webrtcManager

    // ایجاد رندرهای WebRTC
    val localRenderer = remember {
        SurfaceViewRenderer(context).apply {
            init(webrtcManager.rootEglBase.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setMirror(true)
            setZOrderMediaOverlay(true)
        }
    }

    val remoteRenderer = remember {
        SurfaceViewRenderer(context).apply {
            init(webrtcManager.rootEglBase.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
        }
    }

    // راه‌اندازی دوربین و جریان تماس
    LaunchedEffect(Unit) {
        webrtcManager.startLocalVideoCapture(localRenderer)
        webrtcManager.attachRemoteVideo(remoteRenderer)
        if (callStatus == CallStatus.IDLE) {
            viewModel.startCall(targetUserId)
        }
    }

    // پایان تماس و بازگشت در صورت قطع
    LaunchedEffect(callStatus) {
        if (callStatus == CallStatus.ENDED) {
            onCallEnded()
        }
    }

    // آزادسازی رندرهای بومی در خروج از صفحه
    DisposableEffect(Unit) {
        onDispose {
            localRenderer.release()
            remoteRenderer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // ۱. نمایش تمام صفحه ویدیوی ریموت طرف مقابل
        AndroidView(
            factory = { remoteRenderer },
            modifier = Modifier.fillMaxSize()
        )

        // وضعیت اتصال یا انیمیشن انتظار
        if (callStatus != CallStatus.CONNECTED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MeshBlue, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (callStatus) {
                            CallStatus.OUTGOING_CALL -> "در حال برقراری تماس تصویری..."
                            CallStatus.CONNECTING -> "در حال تبادل اطلاعات و اتصال..."
                            else -> "در انتظار پاسخ طرف مقابل..."
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "هدف: $targetUserId",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // ۲. تصویر در تصویر (Picture-in-Picture) دوربین خود در گوشه بالا
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 16.dp)
                .size(width = 110.dp, height = 150.dp)
                .border(2.dp, MeshBlue.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
        ) {
            AndroidView(
                factory = { localRenderer },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ۳. نوار کنترل‌های تماس (میکروفون، دوربین، تعویض دوربین و قطع تماس)
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.92f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp, start = 20.dp, end = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // دکمه قطع/وصل میکروفون
                IconButton(
                    onClick = { webrtcManager.toggleMic() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (isMicMuted) MeshRed else DarkBackground, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "میکروفون",
                        tint = Color.White
                    )
                }

                // دکمه تعویض دوربین جلو/عقب
                IconButton(
                    onClick = { webrtcManager.switchCamera() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(DarkBackground, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "تعویض دوربین",
                        tint = Color.White
                    )
                }

                // دکمه قطع/وصل تصویر دوربین
                IconButton(
                    onClick = { webrtcManager.toggleVideo() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (isVideoDisabled) MeshRed else DarkBackground, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isVideoDisabled) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = "تصویر دوربین",
                        tint = Color.White
                    )
                }

                // دکمه قرمز پایان تماس
                FloatingActionButton(
                    onClick = {
                        viewModel.endCall()
                        onCallEnded()
                    },
                    containerColor = MeshRed,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "قطع تماس",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
