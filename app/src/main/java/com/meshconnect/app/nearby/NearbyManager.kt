package com.meshconnect.app.nearby

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.meshconnect.app.mesh.MeshRelayEngine
import com.meshconnect.app.mesh.PacketProcessResult
import com.meshconnect.app.model.MeshPacket
import com.meshconnect.app.model.PeerConnectionStatus
import com.meshconnect.app.model.PeerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * مدیریت اتصالات آفلاین Nearby Connections گوگل
 * پیاده‌سازی شده با استراتژی P2P_CLUSTER برای ایجاد شبکه کلاستر و مش چند نفره
 */
class NearbyManager(
    private val context: Context,
    val myDeviceId: String,
    var myDeviceName: String,
    val meshRelayEngine: MeshRelayEngine
) {
    companion object {
        private const val TAG = "NearbyManager"
        const val SERVICE_ID = "com.meshconnect.mesh.cluster"
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER // استراتژی چند به چند برای پشتیبانی از مش
    }

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    // وضعیت‌های واکنشی
    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<Map<String, PeerDevice>>(emptyMap())
    val discoveredPeers: StateFlow<List<PeerDevice>> = MutableStateFlow(emptyList())

    private val _connectedPeers = MutableStateFlow<Map<String, PeerDevice>>(emptyMap())
    val connectedPeers: StateFlow<List<PeerDevice>> = MutableStateFlow(emptyList())

    // درخواست اتصال در انتظار تایید کاربر (Accept / Reject)
    private val _pendingConnection = MutableStateFlow<PeerDevice?>(null)
    val pendingConnection: StateFlow<PeerDevice?> = _pendingConnection.asStateFlow()

    // پیشرفت انتقال فایل‌ها (PayloadId -> درصد بین 0 تا 1)
    private val _fileProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val fileProgress: StateFlow<Map<Long, Float>> = _fileProgress.asStateFlow()

    // نگاشت فایل‌های ورودی در حال دریافت
    private val incomingFilePayloads = mutableMapOf<Long, Payload>()

    // شنونده‌های رویدادها
    var onPacketDeliveredLocally: ((MeshPacket) -> Unit)? = null
    var onStreamPayloadReceived: ((Payload) -> Unit)? = null
    var onFileReceived: ((File, String) -> Unit)? = null

    init {
        // همگام‌سازی مپ به لیست برای Compose
        scope.launch {
            _discoveredPeers.collect { map ->
                (discoveredPeers as MutableStateFlow).value = map.values.toList()
            }
        }
        scope.launch {
            _connectedPeers.collect { map ->
                (connectedPeers as MutableStateFlow).value = map.values.toList()
            }
        }
    }

    // =================================================================
    // ۱. چرخه حیات جستجو (Discovery) و انتشار (Advertising)
    // =================================================================

    fun startAdvertising() {
        if (_isAdvertising.value) return
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient.startAdvertising(
            myDeviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising با موفقیت شروع شد با نام: $myDeviceName")
            _isAdvertising.value = true
        }.addOnFailureListener { e ->
            Log.e(TAG, "خطا در شروع Advertising: ${e.message}")
            _isAdvertising.value = false
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        _isAdvertising.value = false
    }

    fun startDiscovery() {
        if (_isDiscovering.value) return
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery با موفقیت شروع شد")
            _isDiscovering.value = true
        }.addOnFailureListener { e ->
            Log.e(TAG, "خطا در شروع Discovery: ${e.message}")
            _isDiscovering.value = false
        }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        _isDiscovering.value = false
        _discoveredPeers.value = emptyMap()
    }

    // =================================================================
    // ۲. درخواست اتصال، تایید و قطع اتصال
    // =================================================================

    fun requestConnection(endpointId: String) {
        val peer = _discoveredPeers.value[endpointId] ?: return
        _discoveredPeers.update { current ->
            current + (endpointId to peer.copy(status = PeerConnectionStatus.CONNECTING))
        }

        connectionsClient.requestConnection(
            myDeviceName,
            endpointId,
            connectionLifecycleCallback
        ).addOnFailureListener { e ->
            Log.e(TAG, "خطا در ارسال درخواست اتصال به $endpointId: ${e.message}")
            _discoveredPeers.update { current ->
                current + (endpointId to peer.copy(status = PeerConnectionStatus.DISCONNECTED))
            }
        }
    }

    fun acceptConnection(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnSuccessListener {
                Log.d(TAG, "اتصال با $endpointId تایید شد")
                if (_pendingConnection.value?.endpointId == endpointId) {
                    _pendingConnection.value = null
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "خطا در پذیرش اتصال: ${e.message}")
            }
    }

    fun rejectConnection(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
        if (_pendingConnection.value?.endpointId == endpointId) {
            _pendingConnection.value = null
        }
    }

    fun disconnect(endpointId: String) {
        connectionsClient.disconnectFromEndpoint(endpointId)
        handleEndpointDisconnected(endpointId)
    }

    fun disconnectAll() {
        connectionsClient.stopAllEndpoints()
        _connectedPeers.value = emptyMap()
        _discoveredPeers.value = emptyMap()
        _isAdvertising.value = false
        _isDiscovering.value = false
    }

    // =================================================================
    // ۳. ارسال داده‌ها (بسته‌های مش، استریم صوتی PTT و فایل‌ها)
    // =================================================================

    /**
     * ارسال یک بسته مش به همتای مستقیم یا رله در کل کلاستر
     */
    fun sendMeshPacket(packet: MeshPacket) {
        val bytes = meshRelayEngine.serializePacket(packet)
        val payload = Payload.fromBytes(bytes)
        val connectedEndpoints = _connectedPeers.value.keys.toList()

        if (connectedEndpoints.isEmpty()) {
            Log.w(TAG, "هیچ دستگاه متصلی برای ارسال بسته مش وجود ندارد")
            return
        }

        connectionsClient.sendPayload(connectedEndpoints, payload)
            .addOnSuccessListener {
                Log.d(TAG, "بسته ${packet.packetId} با موفقیت به گره‌ها ارسال شد")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "خطا در ارسال بسته مش: ${e.message}")
            }
    }

    /**
     * فوروارد کردن بسته رله‌شده به لیست خاصی از همتاها
     */
    private fun forwardRelayedPacket(packet: MeshPacket, targetEndpoints: List<String>) {
        if (targetEndpoints.isEmpty()) return
        val bytes = meshRelayEngine.serializePacket(packet)
        val payload = Payload.fromBytes(bytes)
        connectionsClient.sendPayload(targetEndpoints, payload)
    }

    /**
     * ارسال فایل از طریق Nearby Connections با Payload.fromFile
     */
    fun sendFile(targetEndpointId: String, fileUri: Uri, onPayloadIdCreated: (Long) -> Unit) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(fileUri, "r") ?: return
            val filePayload = Payload.fromFile(pfd)
            onPayloadIdCreated(filePayload.id)

            connectionsClient.sendPayload(targetEndpointId, filePayload)
                .addOnSuccessListener {
                    Log.d(TAG, "ارسال فایل با PayloadId: ${filePayload.id} آغاز شد")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "خطا در ارسال فایل: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "خطا در باز کردن فایل: ${e.message}")
        }
    }

    /**
     * ارسال استریم صوتی زنده (Push-to-Talk) با استفاده از ParcelFileDescriptor Pipe
     */
    fun sendAudioStream(onStreamReady: (java.io.OutputStream) -> Unit) {
        val connectedEndpoints = _connectedPeers.value.keys.toList()
        if (connectedEndpoints.isEmpty()) return

        try {
            val pipe = ParcelFileDescriptor.createPipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]

            val streamPayload = Payload.fromStream(readSide)

            connectionsClient.sendPayload(connectedEndpoints, streamPayload)
                .addOnSuccessListener {
                    val outputStream = ParcelFileDescriptor.AutoCloseOutputStream(writeSide)
                    onStreamReady(outputStream)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "خطا در راه‌اندازی استریم صوتی: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "خطا در ایجاد پایپ صدا: ${e.message}")
        }
    }

    // =================================================================
    // ۴. کالبک‌های کشف (Discovery Callbacks)
    // =================================================================

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "دستگاه همتا پیدا شد: $endpointId (${info.endpointName})")
            val peer = PeerDevice(
                endpointId = endpointId,
                name = info.endpointName,
                status = PeerConnectionStatus.DISCOVERED,
                isDirect = true
            )
            _discoveredPeers.update { current -> current + (endpointId to peer) }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "دستگاه از دسترس خارج شد: $endpointId")
            _discoveredPeers.update { current -> current - endpointId }
        }
    }

    // =================================================================
    // ۵. کالبک‌های چرخه حیات اتصال (Connection Lifecycle Callbacks)
    // =================================================================

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "درخواست اتصال دریافت شد از: $endpointId (${info.endpointName}) با کد تایید: ${info.authenticationDigits}")
            val peer = PeerDevice(
                endpointId = endpointId,
                name = info.endpointName,
                status = PeerConnectionStatus.CONNECTING,
                authToken = info.authenticationDigits
            )
            // ذخیره برای نمایش تاییدیه به کاربر
            _pendingConnection.value = peer
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            when (resolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "اتصال با موفقیت برقرار شد با: $endpointId")
                    val discovered = _discoveredPeers.value[endpointId]
                    val peer = discovered?.copy(status = PeerConnectionStatus.CONNECTED)
                        ?: PeerDevice(endpointId = endpointId, name = "دستگاه $endpointId", status = PeerConnectionStatus.CONNECTED)

                    _connectedPeers.update { it + (endpointId to peer) }
                    _discoveredPeers.update { it - endpointId }
                    if (_pendingConnection.value?.endpointId == endpointId) {
                        _pendingConnection.value = null
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "اتصال توسط طرف مقابل رد شد: $endpointId")
                    handleEndpointDisconnected(endpointId)
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "خطا در فرآیند اتصال به: $endpointId")
                    handleEndpointDisconnected(endpointId)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "ارتباط با $endpointId قطع شد")
            handleEndpointDisconnected(endpointId)
        }
    }

    private fun handleEndpointDisconnected(endpointId: String) {
        _connectedPeers.update { it - endpointId }
        _discoveredPeers.update { it - endpointId }
        meshRelayEngine.removeEndpoint(endpointId)
        if (_pendingConnection.value?.endpointId == endpointId) {
            _pendingConnection.value = null
        }
    }

    // =================================================================
    // ۶. کالبک دریافت پایلود و پیشرفت انتقال (Payload Callbacks)
    // =================================================================

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val rawBytes = payload.asBytes() ?: return
                    val allConnected = _connectedPeers.value.keys.toList()

                    when (val result = meshRelayEngine.processIncomingData(rawBytes, endpointId, allConnected)) {
                        is PacketProcessResult.DeliverLocally -> {
                            onPacketDeliveredLocally?.invoke(result.packet)
                        }
                        is PacketProcessResult.RelayOnly -> {
                            forwardRelayedPacket(result.relayedPacket, result.targetEndpoints)
                        }
                        is PacketProcessResult.RelayAndDeliver -> {
                            onPacketDeliveredLocally?.invoke(result.packet)
                            forwardRelayedPacket(result.relayedPacket, result.targetEndpoints)
                        }
                        else -> { /* تکراری، انقضای TTL یا بسته نامعتبر */ }
                    }
                }
                Payload.Type.STREAM -> {
                    Log.d(TAG, "پایلود استریم صوتی PTT دریافت شد از: $endpointId")
                    onStreamPayloadReceived?.invoke(payload)
                }
                Payload.Type.FILE -> {
                    Log.d(TAG, "پایلود فایل با شناسه ${payload.id} دریافت شد")
                    incomingFilePayloads[payload.id] = payload
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            val total = update.totalBytes
            val transferred = update.bytesTransferred

            if (total > 0) {
                val progress = transferred.toFloat() / total.toFloat()
                _fileProgress.update { it + (payloadId to progress) }
            }

            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    Log.d(TAG, "انتقال پایلود $payloadId با موفقیت به اتمام رسید")
                    _fileProgress.update { it + (payloadId to 1.0f) }

                    val filePayload = incomingFilePayloads.remove(payloadId)
                    if (filePayload != null && filePayload.type == Payload.Type.FILE) {
                        filePayload.asFile()?.asJavaFile()?.let { receivedFile ->
                            onFileReceived?.invoke(receivedFile, endpointId)
                        }
                    }
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                    Log.e(TAG, "انتقال پایلود $payloadId ناموفق بود یا لغو شد")
                    incomingFilePayloads.remove(payloadId)
                    _fileProgress.update { it - payloadId }
                }
            }
        }
    }
}
