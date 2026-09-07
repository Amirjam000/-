package com.meshconnect.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.abs

/**
 * مدیریت استریم زنده صدا برای قابلیت واکی‌تاکی / بیسیم (Push-to-Talk)
 * استفاده از PCM 16-bit با نرخ نمونه‌برداری 16000Hz برای بهینه‌ترین کیفیت و پهنای باند روی بلوتوث و وای‌فای
 */
class AudioStreamManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioStreamManager"
        const val SAMPLE_RATE = 16000 // 16 kHz بهینه برای صدا
        const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null

    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null

    // وضعیت‌های واکنشی (StateFlow) برای UI
    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0)
    val currentAmplitude: StateFlow<Int> = _currentAmplitude.asStateFlow()

    /**
     * شروع ضبط و ارسال استریم صدا هنگام نگه داشتن کلید PTT
     */
    @SuppressLint("MissingPermission")
    fun startStreaming(outputStream: OutputStream) {
        if (_isTransmitting.value) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT
        )
        val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "مقداردهی اولیه AudioRecord ناموفق بود")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            _isTransmitting.value = true

            recordJob = scope.launch {
                val buffer = ByteArray(bufferSize)
                try {
                    while (isActive && _isTransmitting.value) {
                        val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readBytes > 0) {
                            // ارسال چانک داده روی استریم Nearby
                            outputStream.write(buffer, 0, readBytes)
                            outputStream.flush()

                            // محاسبه دامنه سیگنال برای انیمیشن امواج صوتی
                            var maxAmp = 0
                            for (i in 0 until readBytes step 2) {
                                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                                val amp = abs(sample.toShort().toInt())
                                if (amp > maxAmp) maxAmp = amp
                            }
                            _currentAmplitude.value = maxAmp
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "خطا در جریان ضبط و ارسال صدا: ${e.message}")
                } finally {
                    try {
                        outputStream.close()
                    } catch (_: Exception) {}
                    _currentAmplitude.value = 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطا در استارت PTT: ${e.message}")
            stopStreaming()
        }
    }

    /**
     * توقف ضبط هنگام رها کردن کلید PTT
     */
    fun stopStreaming() {
        _isTransmitting.value = false
        recordJob?.cancel()
        recordJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "خطا در توقف ضبط: ${e.message}")
        } finally {
            audioRecord = null
            _currentAmplitude.value = 0
        }
    }

    /**
     * پخش استریم صوتی دریافتی از همتا روی بلندگو با کمترین تاخیر (AudioTrack)
     */
    fun playIncomingStream(inputStream: InputStream) {
        if (_isReceiving.value) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG_OUT,
            AUDIO_FORMAT
        )
        val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG_OUT)
            .setEncoding(AUDIO_FORMAT)
            .build()

        try {
            audioTrack = AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM,
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack?.play()
            _isReceiving.value = true

            playJob = scope.launch {
                val buffer = ByteArray(bufferSize)
                try {
                    while (isActive && _isReceiving.value) {
                        val readBytes = inputStream.read(buffer)
                        if (readBytes == -1) break // پایان استریم
                        if (readBytes > 0) {
                            audioTrack?.write(buffer, 0, readBytes)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "خطا در پخش استریم صوتی: ${e.message}")
                } finally {
                    stopPlayback()
                    try {
                        inputStream.close()
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطا در شروع پخش صدا: ${e.message}")
            stopPlayback()
        }
    }

    /**
     * توقف پخش صدا و آزادسازی حافظه
     */
    fun stopPlayback() {
        _isReceiving.value = false
        playJob?.cancel()
        playJob = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "خطا در توقف پخش: ${e.message}")
        } finally {
            audioTrack = null
        }
    }

    /**
     * آزادسازی کلی تمام منابع صوتی در چرخه حیات برنامه
     */
    fun release() {
        stopStreaming()
        stopPlayback()
    }
}
