package com.example.bgvideo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 伪装主界面：词组背诵 app「词组通」。
 *
 * - RecyclerView 展示词组，中文释义默认隐藏。
 * - 「显示中文释义」按钮：点击 = 显示中文 + 开始录制；再点 = 隐藏中文 + 结束录制。
 *   （原录制按钮的 start/stop 语义保留，仅在文字上伪装为词组释义开关。）
 * - 右下角 FAB：自定义添加词组。
 * - 三击顶部标题「词组通」：进入隐藏的视频列表页。
 */
class MainActivity : AppCompatActivity(), AddPhraseDialogFragment.OnAddListener {

    private lateinit var appTitle: TextView
    private lateinit var rvPhrases: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnRecord: Button

    private lateinit var store: PhraseStore
    private lateinit var adapter: PhraseAdapter

    private var recording = false

    // 三击标题计数
    private var titleClickCount = 0
    private var lastTitleClickTime = 0L

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val camera = result[Manifest.permission.CAMERA] == true
            val audio = result[Manifest.permission.RECORD_AUDIO] == true
            if (camera && audio) {
                toggleRecord()
            } else {
                Toast.makeText(this, "需要相机和录音权限", Toast.LENGTH_LONG).show()
                if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ||
                    !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                ) {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                    )
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appTitle = findViewById(R.id.appTitle)
        rvPhrases = findViewById(R.id.rvPhrases)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnRecord = findViewById(R.id.btnRecord)
        findViewById<android.view.View>(R.id.fabAdd).setOnClickListener {
            AddPhraseDialogFragment().show(supportFragmentManager, "add_phrase")
        }

        store = PhraseStore(this)
        adapter = PhraseAdapter(store.load(), showZh = false)
        rvPhrases.layoutManager = LinearLayoutManager(this)
        rvPhrases.adapter = adapter
        refreshEmptyState()

        // 三击标题进入隐藏视频列表
        appTitle.setOnClickListener {
            val now = SystemClock.uptimeMillis()
            if (now - lastTitleClickTime > 1500) titleClickCount = 0
            titleClickCount++
            lastTitleClickTime = now
            if (titleClickCount >= 3) {
                titleClickCount = 0
                startActivity(Intent(this, VideoListActivity::class.java))
            }
        }

        btnRecord.setOnClickListener {
            // 未录制 -> 检查权限后开始；录制中 -> 结束
            if (recording) {
                toggleRecord()
            } else {
                ensurePermissionsThenToggle()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 与服务的真实录制状态对齐（服务可能已自行停止）
        if (recording && !RecordingService.isRunning) {
            applyUiState(isRecording = false)
        }
    }

    private fun ensurePermissionsThenToggle() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            toggleRecord()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /**
     * 切换录制状态，同时切换中文释义显示：
     *  idle -> 显示中文 + 开始录制
     *  recording -> 隐藏中文 + 结束录制
     */
    private fun toggleRecord() {
        val nowRecording = !recording
        applyUiState(isRecording = nowRecording)
        val intent = Intent(this, RecordingService::class.java).apply {
            action = if (nowRecording) RecordingService.ACTION_START else RecordingService.ACTION_STOP
        }
        if (nowRecording) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun applyUiState(isRecording: Boolean) {
        recording = isRecording
        adapter.setShowZh(isRecording)
        btnRecord.text = getString(
            if (isRecording) R.string.btn_hide_zh else R.string.btn_show_zh
        )
    }

    private fun refreshEmptyState() {
        tvEmpty.visibility = if (adapter.itemCount == 0) android.view.View.VISIBLE else android.view.View.GONE
    }

    // 添加词组回调
    override fun onPhraseAdded(phrase: Phrase) {
        store.add(phrase)
        adapter.submit(store.load())
        refreshEmptyState()
    }
}
