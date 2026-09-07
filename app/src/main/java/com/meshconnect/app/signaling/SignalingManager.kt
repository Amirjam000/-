package com.meshconnect.app.signaling

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.meshconnect.app.model.ChatMessage
import com.meshconnect.app.model.MessageStatus
import com.meshconnect.app.model.RouteType
import com.meshconnect.app.model.WebRtcSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * مدیر سیگنالینگ آنلاین (Online Signaling & Cloud Messaging)
 * مبتنی بر Firebase Firestore برای تبادل اطلاعات ارتباطی WebRTC (Offer, Answer, ICE)
 * و ارسال پیام‌های متنی در فواصل دور وقتی کاربر از برد رادیویی خارج است.
 */
class SignalingManager(
    private val context: Context,
    val myUserId: String,
    val myUserName: String
) {
    companion object {
        private const val TAG = "SignalingManager"
        private const val COLLECTION_SIGNALS = "webrtc_signals"
        private const val COLLECTION_MESSAGES = "cloud_messages"
    }

    private var firestore: FirebaseFirestore? = null
    private var signalsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // کالبک‌ها
    var onSignalReceived: ((WebRtcSignal) -> Unit)? = null
    var onRemoteMessageReceived: ((ChatMessage) -> Unit)? = null

    init {
        initFirebaseSafely()
    }

    /**
     * راه‌اندازی ایمن فایربیس (در صورت نبود فایل google-services.json برنامه کرش نمی‌کند)
     */
    private fun initFirebaseSafely() {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                firestore = FirebaseFirestore.getInstance()
                Log.d(TAG, "فایربیس با موفقیت متصل شد")
                listenForSignals()
            } else {
                Log.w(TAG, "FirebaseApp یافت نشد؛ حالت آنلاین با اضافه کردن google-services.json فعال خواهد شد")
            }
        } catch (e: Exception) {
            Log.w(TAG, "عدم اتصال به فایربیس (حالت آفلاین فعال است): ${e.message}")
        }
    }

    /**
     * بررسی دسترسی به اینترنت
     */
    fun isInternetAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    // =================================================================
    // ۱. سیگنالینگ WebRTC (Offer / Answer / ICE Candidates)
    // =================================================================

    fun sendSignal(signal: WebRtcSignal) {
        val db = firestore ?: return
        scope.launch {
            try {
                val data = mapOf(
                    "senderId" to signal.senderId,
                    "targetId" to signal.targetId,
                    "type" to signal.type,
                    "sdp" to (signal.sdp ?: ""),
                    "sdpMid" to (signal.sdpMid ?: ""),
                    "sdpMLineIndex" to (signal.sdpMLineIndex ?: 0),
                    "candidate" to (signal.candidate ?: ""),
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection(COLLECTION_SIGNALS)
                    .add(data)
                    .addOnSuccessListener {
                        Log.d(TAG, "سیگنال ${signal.type} با موفقیت در فایربیس ثبت شد")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "خطا در ارسال سیگنال: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "خطا در سیگنالینگ: ${e.message}")
            }
        }
    }

    fun listenForSignals() {
        val db = firestore ?: return
        signalsListener?.remove()

        signalsListener = db.collection(COLLECTION_SIGNALS)
            .whereEqualTo("targetId", myUserId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "خطا در شنود سیگنال‌ها: ${error.message}")
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    val doc = change.document
                    val senderId = doc.getString("senderId") ?: return@forEach
                    val type = doc.getString("type") ?: return@forEach
                    val sdp = doc.getString("sdp")
                    val sdpMid = doc.getString("sdpMid")
                    val sdpMLineIndex = doc.getLong("sdpMLineIndex")?.toInt()
                    val candidate = doc.getString("candidate")

                    val signal = WebRtcSignal(
                        senderId = senderId,
                        targetId = myUserId,
                        type = type,
                        sdp = sdp,
                        sdpMid = sdpMid,
                        sdpMLineIndex = sdpMLineIndex,
                        candidate = candidate
                    )

                    onSignalReceived?.invoke(signal)

                    // حذف سند پس از مصرف
                    doc.reference.delete()
                }
            }
    }

    // =================================================================
    // ۲. ارسال و دریافت پیام‌های آنلاین در فواصل دور (Cloud Messaging)
    // =================================================================

    fun sendRemoteMessage(chatId: String, text: String, onSent: (ChatMessage) -> Unit) {
        val db = firestore ?: return
        scope.launch {
            val messageId = java.util.UUID.randomUUID().toString()
            val chatMsg = ChatMessage(
                id = messageId,
                chatId = chatId,
                senderId = myUserId,
                senderName = myUserName,
                text = text,
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                status = MessageStatus.DELIVERED,
                routeType = RouteType.CLOUD_INTERNET
            )

            val data = mapOf(
                "id" to chatMsg.id,
                "chatId" to chatMsg.chatId,
                "senderId" to chatMsg.senderId,
                "senderName" to chatMsg.senderName,
                "text" to chatMsg.text,
                "timestamp" to chatMsg.timestamp
            )

            db.collection(COLLECTION_MESSAGES)
                .document(chatId)
                .collection("history")
                .document(messageId)
                .set(data)
                .addOnSuccessListener {
                    onSent(chatMsg)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "خطا در ارسال پیام ابری: ${e.message}")
                }
        }
    }

    fun listenForRemoteMessages(chatId: String) {
        val db = firestore ?: return
        messagesListener?.remove()

        messagesListener = db.collection(COLLECTION_MESSAGES)
            .document(chatId)
            .collection("history")
            .whereNotEqualTo("senderId", myUserId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                snapshots?.documentChanges?.forEach { change ->
                    val doc = change.document
                    val id = doc.getString("id") ?: doc.id
                    val senderId = doc.getString("senderId") ?: ""
                    val senderName = doc.getString("senderName") ?: "کاربر آنلاین"
                    val text = doc.getString("text") ?: ""
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                    val msg = ChatMessage(
                        id = id,
                        chatId = chatId,
                        senderId = senderId,
                        senderName = senderName,
                        text = text,
                        timestamp = timestamp,
                        isFromMe = false,
                        status = MessageStatus.DELIVERED,
                        routeType = RouteType.CLOUD_INTERNET
                    )

                    onRemoteMessageReceived?.invoke(msg)
                }
            }
    }

    fun release() {
        signalsListener?.remove()
        messagesListener?.remove()
    }
}
