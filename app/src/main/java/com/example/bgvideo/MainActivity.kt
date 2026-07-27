package com.example.bgvideo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 主界面：仅一个按钮。
 * 首次点击 -> 申请权限 -> 文字变更为「停止录制」并启动前台服务开始录制。
 * 再次点击 -> 文字恢复「开始录制」并结束录制。
 * 全程不显示任何相机预览画面。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: Button
    private var recording = false

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val camera = result[Manifest.permission.CAMERA] == true
            val audio = result[Manifest.permission.RECORD_AUDIO] == true
            if (camera && audio) {
                startRecording()
            } else {
                Toast.makeText(this, "需要相机和录音权限才能录制视频", Toast.LENGTH_LONG).show()
                if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ||
                    !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                ) {
                    // 用户勾选了「不再询问」，引导到系统设置
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    })
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRecord = findViewById(R.id.btnRecord)
        btnRecord.setOnClickListener {
            if (recording) {
                stopRecording()
            } else {
                tryStart()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 点击通知返回时刷新按钮状态（前台服务可能已自行停止）
        if (intent.getBooleanExtra(RecordingService.EXTRA_REFRESH_UI, false)) {
            updateUi(isRecording = false)
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时与服务的实际状态对齐
        updateUi(isRecording = RecordingService.isRunning)
    }

    private fun tryStart() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startRecording()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startRecording() {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        updateUi(isRecording = true)
    }

    private fun stopRecording() {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        startService(intent)
        updateUi(isRecording = false)
    }

    private fun updateUi(isRecording: Boolean) {
        recording = isRecording
        btnRecord.text = getString(if (isRecording) R.string.btn_stop else R.string.btn_start)
    }
}
