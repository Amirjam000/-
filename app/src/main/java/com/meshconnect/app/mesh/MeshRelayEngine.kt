package com.meshconnect.app.mesh

import android.util.Log
import android.util.LruCache
import com.google.gson.Gson
import com.meshconnect.app.model.MeshPacket
import com.meshconnect.app.model.PayloadType
import java.nio.charset.StandardCharsets

/**
 * نتیجه پردازش بسته توسط موتور مش
 */
sealed class PacketProcessResult {
    data class DeliverLocally(val packet: MeshPacket) : PacketProcessResult()
    data class RelayAndDeliver(val packet: MeshPacket, val relayedPacket: MeshPacket, val targetEndpoints: List<String>) : PacketProcessResult()
    data class RelayOnly(val relayedPacket: MeshPacket, val targetEndpoints: List<String>) : PacketProcessResult()
    object Duplicate : PacketProcessResult()
    object TtlExpired : PacketProcessResult()
    object InvalidPacket : PacketProcessResult()
}

/**
 * موتور رله شبکه مش (Multi-hop Mesh Relay Engine)
 * مشابه پروتکل BitChat و Disaster Radio.
 * وظایف:
 * ۱. جلوگیری از چرخه نامحدود با فیلتر کش LruCache
 * ۲. کاهش TTL و افزایش شمارنده Hop در هر جهش
 * ۳. بازپخش (Forward/Broadcast) به همتاهای دیگر به جز فرستنده مبدا
 * ۴. مدیریت بسته‌های تحویل و تایید دریافت (ACK)
 */
class MeshRelayEngine(
    private val myDeviceId: String,
    private val myDeviceName: String
) {
    companion object {
        private const val TAG = "MeshRelayEngine"
        private const val MAX_SEEN_CACHE_SIZE = 2000
        private const val DEFAULT_TTL = 5
    }

    private val gson = Gson()

    // کش شناسه‌های بسته‌های دیده شده برای جلوگیری از پردازش یا رله مجدد (Broadcast Storm Suppression)
    private val seenPacketCache = object : LruCache<String, Boolean>(MAX_SEEN_CACHE_SIZE) {}

    // جدول مسیریابی تقریبی: نگاشت DeviceId به آخرین EndpointId مستقیمی که بسته‌ای از آن دیده‌ایم
    private val routingTable = mutableMapOf<String, String>()

    /**
     * ایجاد یک بسته پیام مش جدید برای ارسال اولیه
     */
    fun createPacket(
        recipientId: String?,
        type: PayloadType,
        content: String,
        ttl: Int = DEFAULT_TTL
    ): MeshPacket {
        val packet = MeshPacket(
            senderId = myDeviceId,
            senderName = myDeviceName,
            recipientId = recipientId,
            ttl = ttl,
            hopCount = 0,
            timestamp = System.currentTimeMillis(),
            type = type,
            content = content,
            relayNodes = emptyList()
        )
        markAsSeen(packet.packetId)
        return packet
    }

    /**
     * تبدیل بسته به بایت جهت ارسال روی پروتکل Nearby
     */
    fun serializePacket(packet: MeshPacket): ByteArray {
        val json = gson.toJson(packet)
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * تبدیل بایت‌های دریافتی از شبکه به شیء MeshPacket
     */
    fun deserializePacket(data: ByteArray): MeshPacket? {
        return try {
            val json = String(data, StandardCharsets.UTF_8)
            gson.fromJson(json, MeshPacket::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "خطا در دیکود کردن بسته مش: ${e.message}")
            null
        }
    }

    /**
     * بررسی دیده شدن بسته قبلی
     */
    @Synchronized
    fun isSeen(packetId: String): Boolean {
        return seenPacketCache.get(packetId) != null
    }

    /**
     * ثبت شناسه بسته در کش
     */
    @Synchronized
    fun markAsSeen(packetId: String) {
        seenPacketCache.put(packetId, true)
    }

    /**
     * پردازش بسته دریافتی از یک Endpoint خاص
     * @param rawData بایت‌های دریافتی
     * @param fromEndpoint شناسه گره‌ای که بسته را مستقیماً به ما تحویل داده
     * @param allConnectedEndpoints لیست تمام دستگاه‌های متصل به ما در این لحظه
     */
    @Synchronized
    fun processIncomingData(
        rawData: ByteArray,
        fromEndpoint: String,
        allConnectedEndpoints: List<String>
    ): PacketProcessResult {
        val packet = deserializePacket(rawData) ?: return PacketProcessResult.InvalidPacket

        // بررسی آیا این بسته قبلاً توسط دستگاه ما دیده شده است؟
        if (isSeen(packet.packetId)) {
            Log.d(TAG, "بسته تکراری نادیده گرفته شد: ${packet.packetId}")
            return PacketProcessResult.Duplicate
        }

        // ثبت بسته در کش برای جلوگیری از تکرار
        markAsSeen(packet.packetId)

        // به‌روزرسانی جدول روتینگ: گره مبدا این بسته از طریق fromEndpoint قابل دسترسی است
        routingTable[packet.senderId] = fromEndpoint

        val isForMe = packet.recipientId == null || packet.recipientId == myDeviceId
        val canRelay = packet.ttl > 1

        // همتاهایی که باید بسته را برایشان فوروارد کنیم (همه به جز کسی که بسته را از او گرفتیم)
        val targetEndpointsToRelay = allConnectedEndpoints.filter { it != fromEndpoint }

        if (canRelay && targetEndpointsToRelay.isNotEmpty()) {
            val relayedPacket = packet.copy(
                ttl = packet.ttl - 1,
                hopCount = packet.hopCount + 1,
                relayNodes = packet.relayNodes + myDeviceId
            )

            return if (isForMe) {
                // اگر برودکست است، هم خودمان مصرف می‌کنیم و هم به دیگران رله می‌کنیم
                PacketProcessResult.RelayAndDeliver(
                    packet = packet,
                    relayedPacket = relayedPacket,
                    targetEndpoints = targetEndpointsToRelay
                )
            } else {
                // مقصد دستگاه دیگری در مش است؛ فقط رله می‌کنیم
                Log.d(TAG, "رله کردن بسته ${packet.packetId} به مقصد ${packet.recipientId} (هاب: ${relayedPacket.hopCount})")
                PacketProcessResult.RelayOnly(
                    relayedPacket = relayedPacket,
                    targetEndpoints = targetEndpointsToRelay
                )
            }
        }

        if (isForMe) {
            return PacketProcessResult.DeliverLocally(packet)
        }

        // اگر نه برای من است و نه TTL اجازه رله می‌دهد
        Log.w(TAG, "عمر بسته مش (TTL) به پایان رسید: ${packet.packetId}")
        return PacketProcessResult.TtlExpired
    }

    /**
     * ساخت بسته ACK برای اعلام تحویل موفق پیام به فرستنده اولیه
     */
    fun createAckPacket(originalPacket: MeshPacket): MeshPacket {
        return createPacket(
            recipientId = originalPacket.senderId,
            type = PayloadType.ACK,
            content = originalPacket.packetId,
            ttl = DEFAULT_TTL
        )
    }

    /**
     * پاکسازی اتصالات قطع‌شده از جدول مسیریابی
     */
    @Synchronized
    fun removeEndpoint(endpointId: String) {
        val keysToRemove = routingTable.filterValues { it == endpointId }.keys
        keysToRemove.forEach { routingTable.remove(it) }
    }
}
