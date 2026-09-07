package com.meshconnect.app.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.nearby.connection.Payload
import com.google.gson.Gson
import com.meshconnect.app.audio.AudioStreamManager
import com.meshconnect.app.mesh.MeshRelayEngine
import com.meshconnect.app.model.CallStatus
import com.meshconnect.app.model.ChatMessage
import com.meshconnect.app.model.MessageStatus
import com.meshconnect.app.model.PayloadType
import com.meshconnect.app.model.PeerDevice
import com.meshconnect.app.model.RouteType
import com.meshconnect.app.model.WebRtcSignal
import com.meshconnect.app.nearby.NearbyManager
import com.meshconnect.app.router.ConnectionRouter
import com.meshconnect.app.signaling.SignalingManager
import com.meshconnect.app.webrtc.WebRtcManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * ویومودل مرکزی برنامه (MainViewModel)
 * تجمیع و مدیریت تمام لایه‌های اتصال، مش، صدا، تماس و پیام‌رسانی
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val gson = Gson()

    // اطلاعات کاربر و شناسه پایدار دستگاه
    val myDeviceId: String = Build.MODEL + "_" + UUID.randomUUID().toString().take(6)
    private val _myDeviceName = MutableStateFlow(Build.MODEL ?: "گوشی اندروید")
    val myDeviceName: StateFlow<String> = _myDeviceName.asStateFlow()

    // ماژول‌های هسته‌ای
    val meshRelayEngine = MeshRelayEngine(myDeviceId, _myDeviceName.value)
    val nearbyManager = NearbyManager(application, myDeviceId, _myDeviceName.value, meshRelayEngine)
    val audioStreamManager = AudioStreamManager(application)
    val signalingManager = SignalingManager(application, myDeviceId, _myDeviceName.value)
    val webrtcManager = WebRtcManager(application)
    val connectionRouter = ConnectionRouter(nearbyManager, meshRelayEngine, signalingManager)

    // جریان‌های وضعیت UI
    val isAdvertising = nearbyManager.isAdvertising
    val isDiscovering = nearbyManager.isDiscovering
    val discoveredPeers = nearbyManager.discoveredPeers
    val connectedPeers = nearbyManager.connectedPeers
    val pendingConnection = nearbyManager.pendingConnection
    val fileTransferProgress = nearbyManager.fileProgress

    val isPttTransmitting = audioStreamManager.isTransmitting
    val isPttReceiving = audioStreamManager.isReceiving
    val pttAmplitude = audioStreamManager.currentAmplitude

    val callStatus = webrtcManager.callStatus
    val isMicMuted = webrtcManager.isMicMuted
    val isVideoDisabled = webrtcManager.isVideoDisabled

    // تاریخچه پیام‌ها بر اساس ChatId (شناسه مخاطب یا BROADCAST)
    private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(
        mapOf("BROADCAST" to emptyList())
    )
    val messages: StateFlow<Map<String, List<ChatMessage>>> = _messages.asStateFlow()

    // تماس ورودی در انتظار پاسخ
    private val _incomingCallSignal = MutableStateFlow<WebRtcSignal?>(null)
    val incomingCallSignal: StateFlow<WebRtcSignal?> = _incomingCallSignal.asStateFlow()

    init {
        setupCallbacks()
    }

    private fun setupCallbacks() {
        // ۱. دریافت بسته‌های مش محلی از Nearby
        nearbyManager.onPacketDeliveredLocally = { packet ->
            handleIncomingMeshPacket(packet)
        }

        // ۲. دریافت استریم زنده صدا برای واکی‌تاکی PTT
        nearbyManager.onStreamPayloadReceived = { payload ->
            if (payload.type == Payload.Type.STREAM) {
                payload.asStream()?.asInputStream()?.let { inputStream ->
                    audioStreamManager.playIncomingStream(inputStream)
                }
            }
        }

        // ۳. دریافت فایل محلی
        nearbyManager.onFileReceived = { file, fromEndpoint ->
            handleIncomingFile(file, fromEndpoint)
        }

        // ۴. دریافت سیگنال‌های WebRTC از طریق اینترنت (Firebase)
        signalingManager.onSignalReceived = { signal ->
            handleIncomingWebRtcSignal(signal)
        }

        // ۵. دریافت پیام‌های اینترنتی در فواصل دور
        signalingManager.onRemoteMessageReceived = { msg ->
            addMessage(msg.chatId, msg)
        }

        // ۶. ارسال سیگنال‌های WebRTC (تولید شده توسط WebRtcManager)
        webrtcManager.onSendSignal = { signal ->
            val completeSignal = signal.copy(senderId = myDeviceId)
            // بررسی همتای متصل در مش محلی
            val isTargetNearby = connectedPeers.value.any { it.endpointId == completeSignal.targetId || it.deviceId == completeSignal.targetId }
            if (isTargetNearby) {
                // ارسال آفلاین سیگنال از طریق بلوتوث/وای‌فای
                val packet = meshRelayEngine.createPacket(
                    recipientId = completeSignal.targetId,
                    type = PayloadType.WEBRTC_SIGNAL,
                    content = gson.toJson(completeSignal)
                )
                nearbyManager.sendMeshPacket(packet)
            } else {
                // ارسال از طریق بستر آنلاین فایربیس
                signalingManager.sendSignal(completeSignal)
            }
        }
    }

    // =================================================================
    // پردازش داده‌های دریافتی
    // =================================================================

    private fun handleIncomingMeshPacket(packet: MeshPacket) {
        when (packet.type) {
            PayloadType.TEXT -> {
                val chatId = if (packet.recipientId == null) "BROADCAST" else packet.senderId
                val chatMessage = ChatMessage(
                    id = packet.packetId,
                    chatId = chatId,
                    senderId = packet.senderId,
                    senderName = packet.senderName,
                    text = packet.content,
                    timestamp = packet.timestamp,
                    isFromMe = false,
                    status = MessageStatus.DELIVERED,
                    routeType = if (packet.hopCount > 0) RouteType.MESH_RELAY else RouteType.NEARBY_DIRECT,
                    hopCount = packet.hopCount
                )
                addMessage(chatId, chatMessage)

                // ارسال بسته تاییدیه دریافت (ACK) در صورت پیام اختصاصی
                if (packet.recipientId == myDeviceId) {
                    val ack = meshRelayEngine.createAckPacket(packet)
                    nearbyManager.sendMeshPacket(ack)
                }
            }
            PayloadType.ACK -> {
                // به‌روزرسانی وضعیت پیام ارسالی به DELIVERED
                val originalPacketId = packet.content
                updateMessageStatus(originalPacketId, MessageStatus.DELIVERED)
            }
            PayloadType.WEBRTC_SIGNAL -> {
                try {
                    val signal = gson.fromJson(packet.content, WebRtcSignal::class.java)
                    handleIncomingWebRtcSignal(signal)
                } catch (e: Exception) {
                    Log.e(TAG, "خطا در پارس سیگنال WebRTC مش: ${e.message}")
                }
            }
            else -> {}
        }
    }

    private fun handleIncomingWebRtcSignal(signal: WebRtcSignal) {
        when (signal.type) {
            "offer" -> {
                _incomingCallSignal.value = signal
            }
            "answer" -> {
                signal.sdp?.let { webrtcManager.onAnswerReceived(it) }
            }
            "candidate" -> {
                webrtcManager.onIceCandidateReceived(signal)
            }
        }
    }

    private fun handleIncomingFile(file: File, fromEndpoint: String) {
        val chatMsg = ChatMessage(
            chatId = fromEndpoint,
            senderId = fromEndpoint,
            senderName = "همتای $fromEndpoint",
            text = "فایل جدید دریافت شد: ${file.name}",
            fileUri = file.absolutePath,
            fileName = file.name,
            fileSize = file.length(),
            isFromMe = false,
            status = MessageStatus.DELIVERED,
            routeType = RouteType.NEARBY_DIRECT
        )
        addMessage(fromEndpoint, chatMsg)
    }

    // =================================================================
    // متدهای مربوط به ارسال و چت
    // =================================================================

    fun sendMessage(chatId: String, text: String) {
        if (text.isBlank()) return
        connectionRouter.routeMessage(chatId, text) { message ->
            addMessage(chatId, message)
        }
    }

    private fun addMessage(chatId: String, message: ChatMessage) {
        _messages.update { current ->
            val chatHistory = current[chatId] ?: emptyList()
            current + (chatId to (chatHistory + message))
        }
    }

    private fun updateMessageStatus(messageId: String, status: MessageStatus) {
        _messages.update { current ->
            current.mapValues { (_, list) ->
                list.map { msg ->
                    if (msg.id == messageId) msg.copy(status = status) else msg
                }
            }
        }
    }

    // =================================================================
    // بیسیم صوتی (Push-to-Talk)
    // =================================================================

    fun startPtt() {
        nearbyManager.sendAudioStream { outputStream ->
            audioStreamManager.startStreaming(outputStream)
        }
    }

    fun stopPtt() {
        audioStreamManager.stopStreaming()
    }

    // =================================================================
    // ارسال فایل
    // =================================================================

    fun sendFile(chatId: String, uri: Uri, fileName: String, fileSize: Long) {
        val connectedEndpoint = connectedPeers.value.firstOrNull { it.endpointId == chatId || it.deviceId == chatId }
        if (connectedEndpoint != null) {
            nearbyManager.sendFile(connectedEndpoint.endpointId, uri) { payloadId ->
                val msg = ChatMessage(
                    chatId = chatId,
                    senderId = myDeviceId,
                    senderName = _myDeviceName.value,
                    text = "ارسال فایل: $fileName",
                    fileName = fileName,
                    fileSize = fileSize,
                    fileUri = uri.toString(),
                    isFromMe = true,
                    status = MessageStatus.SENDING,
                    routeType = RouteType.NEARBY_DIRECT
                )
                addMessage(chatId, msg)
            }
        } else {
            // در حالت راه دور می‌توان از Firebase Storage بهره گرفت
            sendMessage(chatId, "ارسال فایل آنلاین: $fileName (${fileSize / 1024} KB)")
        }
    }

    // =================================================================
    // تماس تصویری (WebRTC)
    // =================================================================

    fun startCall(targetPeerId: String) {
        webrtcManager.startCall(targetPeerId)
    }

    fun answerCall() {
        val signal = _incomingCallSignal.value ?: return
        signal.sdp?.let { sdp ->
            webrtcManager.answerCall(signal.senderId, sdp)
        }
        _incomingCallSignal.value = null
    }

    fun rejectCall() {
        _incomingCallSignal.value = null
        webrtcManager.endCall()
    }

    fun endCall() {
        webrtcManager.endCall()
    }

    // =================================================================
    // کنترل Nearby و تغییر نام
    // =================================================================

    fun updateDeviceName(name: String) {
        _myDeviceName.value = name
        nearbyManager.myDeviceName = name
    }

    fun toggleAdvertising() {
        if (isAdvertising.value) nearbyManager.stopAdvertising() else nearbyManager.startAdvertising()
    }

    fun toggleDiscovery() {
        if (isDiscovering.value) nearbyManager.stopDiscovery() else nearbyManager.startDiscovery()
    }

    fun requestConnection(endpointId: String) = nearbyManager.requestConnection(endpointId)
    fun acceptConnection(endpointId: String) = nearbyManager.acceptConnection(endpointId)
    fun rejectConnection(endpointId: String) = nearbyManager.rejectConnection(endpointId)
    fun disconnect(endpointId: String) = nearbyManager.disconnect(endpointId)

    override fun onCleared() {
        super.onCleared()
        nearbyManager.disconnectAll()
        audioStreamManager.release()
        webrtcManager.release()
        signalingManager.release()
    }
}
