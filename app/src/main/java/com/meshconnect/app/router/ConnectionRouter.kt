package com.meshconnect.app.router

import android.util.Log
import com.meshconnect.app.mesh.MeshRelayEngine
import com.meshconnect.app.model.ChatMessage
import com.meshconnect.app.model.MessageStatus
import com.meshconnect.app.model.PayloadType
import com.meshconnect.app.model.RouteType
import com.meshconnect.app.nearby.NearbyManager
import com.meshconnect.app.signaling.SignalingManager

/**
 * روتر هوشمند ارتباطات (Connection Router)
 * وظیفه تصمیم‌گیری برای انتخاب بهینه‌ترین مسیر ارتباطی:
 * ۱. اولویت اول: اتصال محلی و رایگان مش/Nearby (بلوتوث و وای‌فای بدون نیاز به اینترنت)
 * ۲. اولویت دوم: در صورت عدم وجود همتا در برد مستقیم یا مش، سوئیچ خودکار به بستر آنلاین اینترنت (Firebase)
 */
class ConnectionRouter(
    private val nearbyManager: NearbyManager,
    private val meshRelayEngine: MeshRelayEngine,
    private val signalingManager: SignalingManager
) {
    companion object {
        private const val TAG = "ConnectionRouter"
    }

    /**
     * ارسال هوشمند پیام با سوئیچینگ خودکار بین آفلاین و آنلاین
     */
    fun routeMessage(
        targetPeerId: String,
        text: String,
        onMessageCreated: (ChatMessage) -> Unit
    ) {
        val connectedNearbyPeers = nearbyManager.connectedPeers.value
        val isDirectlyConnected = connectedNearbyPeers.any { it.endpointId == targetPeerId || it.deviceId == targetPeerId }
        val isBroadcast = targetPeerId.equals("BROADCAST", ignoreCase = true)

        // ۱. بررسی امکان ارسال از طریق بستر محلی و مش آفلاین
        if (isBroadcast || isDirectlyConnected || connectedNearbyPeers.isNotEmpty()) {
            Log.d(TAG, "ارسال پیام از طریق شبکه محلی و مش Nearby")

            val target = if (isBroadcast) null else targetPeerId
            val packet = meshRelayEngine.createPacket(
                recipientId = target,
                type = PayloadType.TEXT,
                content = text
            )

            val localMessage = ChatMessage(
                id = packet.packetId,
                chatId = targetPeerId,
                senderId = nearbyManager.myDeviceId,
                senderName = nearbyManager.myDeviceName,
                text = text,
                timestamp = packet.timestamp,
                isFromMe = true,
                status = MessageStatus.SENDING,
                routeType = if (isDirectlyConnected) RouteType.NEARBY_DIRECT else RouteType.MESH_RELAY,
                hopCount = 0
            )

            // انتشار روی کلاستر Nearby
            nearbyManager.sendMeshPacket(packet)
            onMessageCreated(localMessage)
            return
        }

        // ۲. در صورتی که همتا در برد آفلاین نبود، بررسی دسترسی به اینترنت
        if (signalingManager.isInternetAvailable()) {
            Log.d(TAG, "سوئیچ خودکار به اینترنت: ارسال پیام از طریق بستر ابری")
            signalingManager.sendRemoteMessage(
                chatId = targetPeerId,
                text = text
            ) { cloudMessage ->
                onMessageCreated(cloudMessage)
            }
            return
        }

        // ۳. در صورت عدم اتصال آفلاین و عدم اینترنت
        Log.w(TAG, "هیچ مسیر ارتباطی (نه مش محلی و نه اینترنت) در دسترس نیست")
        val failedMessage = ChatMessage(
            chatId = targetPeerId,
            senderId = nearbyManager.myDeviceId,
            senderName = nearbyManager.myDeviceName,
            text = text,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = MessageStatus.FAILED,
            routeType = RouteType.NEARBY_DIRECT
        )
        onMessageCreated(failedMessage)
    }
}
