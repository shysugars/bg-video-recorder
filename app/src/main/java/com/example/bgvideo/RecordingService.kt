package com.example.bgvideo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Size
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 前台录制服务：使用 Camera2 + MediaRecorder 在后台录制视频，无任何预览画面。
 * 视频保存到应用私有目录（getExternalFilesDir/Movies，无需存储权限）。
 */
class RecordingService : Service() {

    companion object {
        const val ACTION_START = "com.example.bgvideo.action.START"
        const val ACTION_STOP = "com.example.bgvideo.action.STOP"
        const val EXTRA_REFRESH_UI = "refresh_ui"

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "recording_channel"

        // 供 UI 在 onResume 时对齐真实状态使用
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val cameraThread = HandlerThread("camera-thread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var cameraDevice: CameraDevice? = null
    private var mediaRecorder: MediaRecorder? = null
    private var captureSession: android.hardware.camera2.CameraCaptureSession? = null
    private var currentOutputFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
    }

    override fun onDestroy() {
        releaseResources()
        cameraThread.quitSafely()
        isRunning = false
        super.onDestroy()
    }

    // ---------------- 前台通知 ----------------

    private fun startForegroundWithNotification() {
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 可声明前台服务类型（相机 + 麦克风）
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.noti_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REFRESH_UI, true)
        }
        val pi = android.app.PendingIntent.getActivity(
            this, 0, contentIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.noti_title))
            .setContentText(getString(R.string.noti_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    // ---------------- 开始录制 ----------------

    private fun startRecording() {
        if (isRunning) return
        isRunning = true
        openBackCamera()
    }

    private fun openBackCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = pickBackCamera(manager) ?: run {
            stopWithError("未找到可用后置摄像头")
            return
        }

        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    stopWithError("摄像头被断开")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    stopWithError("摄像头错误 code=$error")
                }
            }, cameraHandler)
        } catch (e: Exception) {
            // Android 11+ 可能需要确认 camera 权限已授予
            stopWithError("打开摄像头失败: ${e.message}")
        }
    }

    private fun pickBackCamera(manager: CameraManager): String? {
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            val hwLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            if (facing == CameraCharacteristics.LENS_FACING_BACK &&
                hwLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
            ) {
                return id
            }
        }
        // 退而求其次，任意一个非前置摄像头
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_FRONT) {
                return id
            }
        }
        return manager.cameraIdList.firstOrNull()
    }

    private fun startCaptureSession() {
        val camera = cameraDevice ?: return
        val recorder = createConfiguredRecorder() ?: run {
            stopWithError("MediaRecorder 配置失败")
            return
        }
        mediaRecorder = recorder

        val surface = recorder.surface
        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        request.addTarget(surface)

        // Android 12 (API 31) 可用 SessionConfiguration；低于 31 用旧 API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val outputConfig = OutputConfiguration(surface)
            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(outputConfig),
                { cameraHandler.post(it) },
                object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                        captureSession = session
                        session.setRepeatingRequest(request.build(), null, cameraHandler)
                        recorder.start()
                    }

                    override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                        stopWithError("创建录制会话失败")
                    }
                }
            )
            camera.createCaptureSession(sessionConfig)
        } else {
            camera.createCaptureSession(
                listOf(surface),
                object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                        captureSession = session
                        session.setRepeatingRequest(request.build(), null, cameraHandler)
                        recorder.start()
                    }

                    override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                        stopWithError("创建录制会话失败")
                    }
                },
                cameraHandler
            )
        }
    }

    private fun createConfiguredRecorder(): MediaRecorder? {
        val outputDir = File(getExternalFilesDir(null), "Movies").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).let {
            // SimpleDateFormat 不是线程安全的，这里在 camera 线程构造，可接受
            it.format(java.util.Date())
        }
        val outFile = File(outputDir, "VID_$timeStamp.mp4")
        currentOutputFile = outFile

        // MediaRecorder() 无参构造兼容 minSdk 26；带 Context 的重载在 API 31 才引入
        return MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(4_000_000)
            setVideoFrameRate(30)
            setVideoSize(1280, 720)
            setOrientationHint(90) // 后置摄像头竖屏录制
            setOutputFile(outFile.absolutePath)
            try {
                prepare()
            } catch (e: Exception) {
                release()
                return null
            }
        }
    }

    // ---------------- 停止录制 ----------------

    private fun stopRecording() {
        // 切到 camera 线程执行释放，避免与正在进行的回调竞争
        cameraHandler.post {
            releaseResources()
            isRunning = false
            stopForeground(true)
            stopSelf()
            currentOutputFile?.let {
                android.util.Log.i("RecordingService", "已保存: ${it.absolutePath}")
            }
        }
    }

    private fun stopWithError(msg: String) {
        android.util.Log.e("RecordingService", msg)
        releaseResources()
        isRunning = false
        stopForeground(true)
        stopSelf()
    }

    private fun releaseResources() {
        try {
            mediaRecorder?.let {
                runCatching { it.stop() }
                runCatching { it.reset() }
                runCatching { it.release() }
            }
        } catch (_: Exception) {
        }
        mediaRecorder = null
        try {
            captureSession?.let {
                runCatching { it.stopRepeating() }
                runCatching { it.abortCaptures() }
                runCatching { it.close() }
            }
        } catch (_: Exception) {
        }
        captureSession = null
        try {
            cameraDevice?.let { runCatching { it.close() } }
        } catch (_: Exception) {
        }
        cameraDevice = null
    }
}
