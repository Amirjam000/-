package com.meshconnect.app.webrtc

import android.content.Context
import android.util.Log
import com.meshconnect.app.model.CallStatus
import com.meshconnect.app.model.WebRtcSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * مدیریت تماس صوتی و تصویری با کتابخانه WebRTC
 * شامل تنظیمات سخت‌افزاری انکودینگ، دوربین، میکروفون و تعامل با STUN/TURN سرور
 */
class WebRtcManager(private val context: Context) {

    companion object {
        private const val TAG = "WebRtcManager"
        // سرور STUN عمومی گوگل برای عبور از NAT در اکثر شبکه‌های اینترنتی
        private const val GOOGLE_STUN = "stun:stun.l.google.com:19302"
        private const val GOOGLE_STUN_2 = "stun:stun1.l.google.com:19302"
    }

    val rootEglBase: EglBase = EglBase.create()
    private val scope = CoroutineScope(Dispatchers.Main)

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var remoteVideoTrack: VideoTrack? = null

    // وضعیت‌های تماس
    private val _callStatus = MutableStateFlow(CallStatus.IDLE)
    val callStatus: StateFlow<CallStatus> = _callStatus.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isVideoDisabled = MutableStateFlow(false)
    val isVideoDisabled: StateFlow<Boolean> = _isVideoDisabled.asStateFlow()

    private var currentTargetUserId: String? = null

    // کالبک ارسال سیگنال به لایه بیرونی (SignalingManager یا Mesh Relay)
    var onSendSignal: ((WebRtcSignal) -> Unit)? = null

    init {
        initPeerConnectionFactory()
    }

    /**
     * آماده‌سازی و مقداردهی اولیه کارخانه PeerConnectionFactory با پشتیبانی شتاب‌دهنده سخت‌افزاری
     */
    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(
            rootEglBase.eglBaseContext,
            /* enableIntelVp8 = */ true,
            /* enableH264HighProfile = */ true
        )
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    /**
     * راه‌اندازی ضبط تصویر دوربین با استفاده از Camera2 API
     */
    fun startLocalVideoCapture(localRenderer: SurfaceViewRenderer) {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // جستجوی دوربین جلو (Front-facing)
        val frontCamera = deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: deviceNames.firstOrNull()
        if (frontCamera == null) {
            Log.e(TAG, "هیچ دوربینی روی دستگاه یافت نشد")
            return
        }

        videoCapturer = enumerator.createCapturer(frontCamera, null)
        surfaceTextureHelper = SurfaceTextureHelper.create("CameraCaptureThread", rootEglBase.eglBaseContext)

        val factory = peerConnectionFactory ?: return
        videoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer!!.initialize(surfaceTextureHelper, context, videoSource!!.capturerObserver)
        // وضوح 720p با 30 فریم بر ثانیه برای کیفیت بهینه
        videoCapturer!!.startCapture(1280, 720, 30)

        localVideoTrack = factory.createVideoTrack("MESH_LOCAL_VIDEO", videoSource)
        localVideoTrack?.setEnabled(true)
        localVideoTrack?.addSink(localRenderer)

        // راه‌اندازی همزمان میکروفون
        val mediaConstraints = MediaConstraints()
        audioSource = factory.createAudioSource(mediaConstraints)
        localAudioTrack = factory.createAudioTrack("MESH_LOCAL_AUDIO", audioSource)
        localAudioTrack?.setEnabled(true)
    }

    /**
     * ایجاد اتصال همتا (PeerConnection) با پیکربندی سرورهای STUN و TURN
     */
    private fun createPeerConnection(): PeerConnection? {
        val iceServers = listOf(
            PeerConnection.IceServer.builder(GOOGLE_STUN).createIceServer(),
            PeerConnection.IceServer.builder(GOOGLE_STUN_2).createIceServer()
            /*
             * نکته: در صورت نیاز به عبور از NAT متقارن در سازمان‌ها یا شبکه‌های خاص:
             * PeerConnection.IceServer.builder("turn:your-turn-server.com:3478")
             *     .setUsername("username")
             *     .setPassword("password")
             *     .createIceServer()
             */
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        return peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val target = currentTargetUserId ?: return@let
                    val signal = WebRtcSignal(
                        senderId = "", // توسط فرستنده پر می‌شود
                        targetId = target,
                        type = "candidate",
                        sdpMid = it.sdpMid,
                        sdpMLineIndex = it.sdpMLineIndex,
                        candidate = it.sdp
                    )
                    onSendSignal?.invoke(signal)
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    Log.d(TAG, "ترک ویدیوی ریموت دریافت شد")
                    remoteVideoTrack = track
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "وضعیت اتصال ICE: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> _callStatus.value = CallStatus.CONNECTED
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> _callStatus.value = CallStatus.ENDED
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddStream(stream: MediaStream?) {}
        })
    }

    /**
     * اتصال ترک ریموت به SurfaceViewRenderer در UI
     */
    fun attachRemoteVideo(remoteRenderer: SurfaceViewRenderer) {
        remoteVideoTrack?.addSink(remoteRenderer)
    }

    /**
     * برقراری تماس خروجی (ایجاد Offer)
     */
    fun startCall(targetUserId: String) {
        currentTargetUserId = targetUserId
        _callStatus.value = CallStatus.OUTGOING_CALL

        peerConnection = createPeerConnection()
        localVideoTrack?.let { peerConnection?.addTrack(it, listOf("MESH_STREAM")) }
        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("MESH_STREAM")) }

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            val signal = WebRtcSignal(
                                senderId = "",
                                targetId = targetUserId,
                                type = "offer",
                                sdp = it.description
                            )
                            onSendSignal?.invoke(signal)
                        }
                        override fun onCreateFailure(err: String?) {}
                        override fun onSetFailure(err: String?) {}
                    }, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "خطا در ساخت Offer: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, constraints)
    }

    /**
     * پاسخ به تماس ورودی (پذیرش Offer و ایجاد Answer)
     */
    fun answerCall(senderUserId: String, offerSdp: String) {
        currentTargetUserId = senderUserId
        _callStatus.value = CallStatus.CONNECTING

        peerConnection = createPeerConnection()
        localVideoTrack?.let { peerConnection?.addTrack(it, listOf("MESH_STREAM")) }
        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("MESH_STREAM")) }

        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }

                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        desc?.let {
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    val signal = WebRtcSignal(
                                        senderId = "",
                                        targetId = senderUserId,
                                        type = "answer",
                                        sdp = it.description
                                    )
                                    onSendSignal?.invoke(signal)
                                }
                                override fun onCreateFailure(p0: String?) {}
                                override fun onSetFailure(p0: String?) {}
                            }, it)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(err: String?) {
                        Log.e(TAG, "خطا در ساخت Answer: $err")
                    }
                    override fun onSetFailure(err: String?) {}
                }, constraints)
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "خطا در تنظیم Remote Description: $err")
            }
        }, remoteDesc)
    }

    /**
     * دریافت Answer از طرف مقابل
     */
    fun onAnswerReceived(answerSdp: String) {
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "Answer با موفقیت ثبت شد")
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "خطا در ثبت Answer: $err")
            }
        }, remoteDesc)
    }

    /**
     * افزودن کاندیدای ICE دریافتی
     */
    fun onIceCandidateReceived(candidate: WebRtcSignal) {
        val iceCandidate = IceCandidate(
            candidate.sdpMid,
            candidate.sdpMLineIndex ?: 0,
            candidate.candidate
        )
        peerConnection?.addIceCandidate(iceCandidate)
    }

    /**
     * قطع و وصل میکروفون
     */
    fun toggleMic() {
        val newState = !_isMicMuted.value
        localAudioTrack?.setEnabled(!newState)
        _isMicMuted.value = newState
    }

    /**
     * قطع و وصل تصویر دوربین
     */
    fun toggleVideo() {
        val newState = !_isVideoDisabled.value
        localVideoTrack?.setEnabled(!newState)
        _isVideoDisabled.value = newState
    }

    /**
     * تعویض دوربین جلو و عقب
     */
    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    /**
     * پایان تماس و آزادسازی حافظه
     */
    fun endCall() {
        _callStatus.value = CallStatus.ENDED
        try {
            peerConnection?.close()
            peerConnection = null
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e(TAG, "خطا در بستن تماس: ${e.message}")
        }
        _callStatus.value = CallStatus.IDLE
    }

    /**
     * آزادسازی کلی منابع WebRTC
     */
    fun release() {
        endCall()
        try {
            videoCapturer?.dispose()
            videoSource?.dispose()
            audioSource?.dispose()
            surfaceTextureHelper?.dispose()
            peerConnectionFactory?.dispose()
            rootEglBase.release()
        } catch (e: Exception) {
            Log.e(TAG, "خطا در Dispose وب‌آرتی‌سی: ${e.message}")
        }
    }
}
