package com.meshconnect.app.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * انواع پایلودهای قابل انتقال در شبکه مش و اتصالات مستقیم
 */
enum class PayloadType {
    @SerializedName("TEXT")
    TEXT,

    @SerializedName("FILE_META")
    FILE_META,

    @SerializedName("AUDIO_STREAM_START")
    AUDIO_STREAM_START,

    @SerializedName("AUDIO_STREAM_END")
    AUDIO_STREAM_END,

    @SerializedName("ACK")
    ACK,

    @SerializedName("PEER_ANNOUNCEMENT")
    PEER_ANNOUNCEMENT,

    @SerializedName("WEBRTC_SIGNAL")
    WEBRTC_SIGNAL
}

/**
 * پاکت کپسوله‌شده شبکه مش (Mesh Packet)
 * شامل مکانیزم‌های جلوگیری از حلقه بی‌نهایت (TTL)، شناسایی تکرار (UUID)،
 * و ردیابی مسیر حرکت در دستگاه‌های واسط (Hop Count & Relay Nodes).
 */
data class MeshPacket(
    @SerializedName("packetId")
    val packetId: String = UUID.randomUUID().toString(),

    @SerializedName("senderId")
    val senderId: String,

    @SerializedName("senderName")
    val senderName: String,

    @SerializedName("recipientId")
    val recipientId: String? = null, // null یا خالی به معنی برودکست برای کل شبکه مش است

    @SerializedName("ttl")
    val ttl: Int = 5, // Time-to-Live: با هر جهش یکی کم می‌شود

    @SerializedName("hopCount")
    val hopCount: Int = 0, // تعداد گره‌های واسط طی شده

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("type")
    val type: PayloadType = PayloadType.TEXT,

    @SerializedName("content")
    val content: String = "",

    @SerializedName("relayNodes")
    val relayNodes: List<String> = emptyList() // شناسه دستگاه‌های رله‌کننده
)

/**
 * وضعیت ارسال و تحویل پیام
 */
enum class MessageStatus {
    SENDING,   // در حال ارسال
    RELAYED,   // توسط گره‌های واسط رله شده
    DELIVERED, // به مقصد نهایی تحویل داده شده
    FAILED     // ناموفق
}

/**
 * مسیر طی شده توسط پیام
 */
enum class RouteType {
    NEARBY_DIRECT,  // اتصال مستقیم بلوتوث/وای‌فای
    MESH_RELAY,     // شبکه مش و عبور از چند گوشی واسط
    CLOUD_INTERNET  // شبکه اینترنت و بک‌اند ابری
}

/**
 * مدل پیام متنی یا فایل در صفحه چت
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val chatId: String, // شناسه مخاطب یا "BROADCAST"
    val senderId: String,
    val senderName: String,
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean,
    val status: MessageStatus = MessageStatus.SENDING,
    val routeType: RouteType = RouteType.NEARBY_DIRECT,
    val hopCount: Int = 0,
    val fileUri: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val fileProgress: Float? = null // 0.0 تا 1.0 برای نوار پیشرفت
)

/**
 * وضعیت اتصال دستگاه همتا
 */
enum class PeerConnectionStatus {
    DISCOVERED,
    CONNECTING,
    CONNECTED,
    REJECTED,
    DISCONNECTED
}

/**
 * مدل دستگاه همتا (پیدا شده با Nearby Connections یا شناسایی شده در مش)
 */
data class PeerDevice(
    val endpointId: String,
    val deviceId: String = endpointId,
    val name: String,
    val status: PeerConnectionStatus = PeerConnectionStatus.DISCOVERED,
    val isDirect: Boolean = true,
    val authToken: String? = null, // کد تایید اتصال برای نمایش به کاربر
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * داده‌های سیگنالینگ WebRTC (Offer, Answer, Ice Candidate)
 */
data class WebRtcSignal(
    @SerializedName("senderId")
    val senderId: String,

    @SerializedName("targetId")
    val targetId: String,

    @SerializedName("type")
    val type: String, // "offer", "answer", "candidate"

    @SerializedName("sdp")
    val sdp: String? = null,

    @SerializedName("sdpMid")
    val sdpMid: String? = null,

    @SerializedName("sdpMLineIndex")
    val sdpMLineIndex: Int? = null,

    @SerializedName("candidate")
    val candidate: String? = null
)

/**
 * وضعیت‌های تماس تصویری / صوتی
 */
enum class CallStatus {
    IDLE,
    OUTGOING_CALL,
    INCOMING_CALL,
    CONNECTING,
    CONNECTED,
    ENDED
}
