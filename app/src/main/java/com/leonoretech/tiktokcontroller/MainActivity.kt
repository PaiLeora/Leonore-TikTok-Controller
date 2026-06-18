package com.leonoretech.tiktokcontroller

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvMicStatus: TextView
    private lateinit var tvVoiceLog: TextView
    private lateinit var tvIntervalValue: TextView

    private lateinit var btnEnableAccessibility: Button
    private lateinit var btnToggleVoice: Button
    private lateinit var btnToggleAutoScroll: Button
    private lateinit var btnSwipeUp: Button
    private lateinit var btnSwipeDown: Button
    private lateinit var btnLike: Button
    private lateinit var btnGesturePlaceholder: Button
    private lateinit var seekInterval: SeekBar

    private var voiceControlManager: VoiceControlManager? = null
    private var isVoiceActive = false
    private var isAutoScrollActive = false
    private var intervalSeconds = 5

    private val micPermissionRequestCode = 100

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LeonoreAccessibilityService.ACTION_SERVICE_STATE) {
                val connected = intent.getBooleanExtra(
                    LeonoreAccessibilityService.EXTRA_CONNECTED, false
                )
                updateAccessibilityStatusUi(connected)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupListeners()

        val filter = IntentFilter(LeonoreAccessibilityService.ACTION_SERVICE_STATE)
        ContextCompat.registerReceiver(
            this, serviceStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatusUi(LeonoreAccessibilityService.isServiceConnected)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceControlManager?.stopListening()
        try {
            unregisterReceiver(serviceStateReceiver)
        } catch (e: IllegalArgumentException) {
            // already unregistered
        }
    }

    private fun bindViews() {
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvMicStatus = findViewById(R.id.tvMicStatus)
        tvVoiceLog = findViewById(R.id.tvVoiceLog)
        tvIntervalValue = findViewById(R.id.tvIntervalValue)

        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility)
        btnToggleVoice = findViewById(R.id.btnToggleVoice)
        btnToggleAutoScroll = findViewById(R.id.btnToggleAutoScroll)
        btnSwipeUp = findViewById(R.id.btnSwipeUp)
        btnSwipeDown = findViewById(R.id.btnSwipeDown)
        btnLike = findViewById(R.id.btnLike)
        btnGesturePlaceholder = findViewById(R.id.btnGesturePlaceholder)
        seekInterval = findViewById(R.id.seekInterval)
    }

    private fun setupListeners() {
        btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnToggleVoice.setOnClickListener {
            if (isVoiceActive) {
                stopVoiceControl()
            } else {
                requestMicPermissionThenStart()
            }
        }

        btnToggleAutoScroll.setOnClickListener {
            isAutoScrollActive = !isAutoScrollActive
            sendCommandToService(
                if (isAutoScrollActive) LeonoreAccessibilityService.CMD_RESUME_AUTOSCROLL
                else LeonoreAccessibilityService.CMD_PAUSE_AUTOSCROLL
            )
            if (isAutoScrollActive) {
                startForegroundServiceIfNeeded()
            }
            updateAutoScrollUi()
        }

        btnSwipeUp.setOnClickListener {
            sendCommandToService(LeonoreAccessibilityService.CMD_SWIPE_UP)
        }

        btnSwipeDown.setOnClickListener {
            sendCommandToService(LeonoreAccessibilityService.CMD_SWIPE_DOWN)
        }

        btnLike.setOnClickListener {
            sendCommandToService(LeonoreAccessibilityService.CMD_LIKE)
        }

        btnGesturePlaceholder.setOnClickListener {
            // Basic placeholder: cycles through swipe up as a stand-in gesture trigger.
            // Full version will map camera-tracked gestures (via MediaPipe) to these
            // same broadcast commands, so the accessibility service side needs no changes.
            sendCommandToService(LeonoreAccessibilityService.CMD_SWIPE_UP)
            appendVoiceLog("[gesture] simulated swipe-up trigger")
        }

        seekInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                intervalSeconds = progress + 3 // range: 3s - 30s
                tvIntervalValue.text = "${intervalSeconds}s"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                broadcastIntervalChange(intervalSeconds * 1000L)
            }
        })
    }

    // ---------------------------------------------------------------
    // Accessibility service status + commands
    // ---------------------------------------------------------------

    private fun updateAccessibilityStatusUi(active: Boolean) {
        if (active) {
            tvAccessibilityStatus.text = getString(R.string.status_active)
            tvAccessibilityStatus.setTextColor(getColorCompat(R.color.status_on))
            tvAccessibilityStatus.setBackgroundResource(R.drawable.bg_status_active)
        } else {
            tvAccessibilityStatus.text = getString(R.string.status_inactive)
            tvAccessibilityStatus.setTextColor(getColorCompat(R.color.status_off))
            tvAccessibilityStatus.setBackgroundResource(R.drawable.bg_status_inactive)
        }
    }

    private fun sendCommandToService(command: String) {
        if (!LeonoreAccessibilityService.isServiceConnected) {
            appendVoiceLog("[warn] Accessibility service belum aktif")
            return
        }
        val intent = Intent(LeonoreAccessibilityService.ACTION_COMMAND).apply {
            putExtra(LeonoreAccessibilityService.EXTRA_COMMAND, command)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastIntervalChange(intervalMs: Long) {
        // Reuses the same command channel; service reads custom extra for interval updates.
        val intent = Intent(LeonoreAccessibilityService.ACTION_COMMAND).apply {
            putExtra(LeonoreAccessibilityService.EXTRA_COMMAND, "set_interval")
            putExtra("extra_interval_ms", intervalMs)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun updateAutoScrollUi() {
        btnToggleAutoScroll.text = if (isAutoScrollActive) {
            getString(R.string.btn_stop_service)
        } else {
            getString(R.string.btn_start_service)
        }
    }

    private fun startForegroundServiceIfNeeded() {
        val intent = Intent(this, AutoScrollForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // ---------------------------------------------------------------
    // Voice control
    // ---------------------------------------------------------------

    private fun requestMicPermissionThenStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceControl()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), micPermissionRequestCode
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == micPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceControl()
            } else {
                appendVoiceLog("[warn] Izin microphone ditolak")
            }
        }
    }

    private fun startVoiceControl() {
        startForegroundServiceIfNeeded()

        voiceControlManager = VoiceControlManager(
            context = this,
            onCommandRecognized = { rawText, command ->
                runOnUiThread {
                    appendVoiceLog("\"$rawText\" -> $command")
                    sendCommandToService(command)
                }
            },
            onListeningStateChanged = { listening ->
                runOnUiThread { updateMicStatusUi(listening) }
            },
            onError = { message ->
                runOnUiThread { appendVoiceLog("[error] $message") }
            }
        )

        if (voiceControlManager?.isAvailable() == true) {
            voiceControlManager?.startListening()
            isVoiceActive = true
            btnToggleVoice.text = getString(R.string.btn_stop_voice)
        } else {
            appendVoiceLog("[error] Speech recognizer tidak tersedia di device ini")
        }
    }

    private fun stopVoiceControl() {
        voiceControlManager?.stopListening()
        voiceControlManager = null
        isVoiceActive = false
        btnToggleVoice.text = getString(R.string.btn_start_voice)
        updateMicStatusUi(false)
    }

    private fun updateMicStatusUi(active: Boolean) {
        if (active) {
            tvMicStatus.text = getString(R.string.status_active)
            tvMicStatus.setTextColor(getColorCompat(R.color.status_on))
            tvMicStatus.setBackgroundResource(R.drawable.bg_status_active)
        } else {
            tvMicStatus.text = getString(R.string.status_inactive)
            tvMicStatus.setTextColor(getColorCompat(R.color.status_off))
            tvMicStatus.setBackgroundResource(R.drawable.bg_status_inactive)
        }
    }

    private fun appendVoiceLog(line: String) {
        val current = tvVoiceLog.text.toString()
        val updated = if (TextUtils.isEmpty(current) || current == getString(R.string.label_voice_log)) {
            line
        } else {
            "$line\n$current"
        }
        val lines = updated.split("\n").take(5)
        tvVoiceLog.text = lines.joinToString("\n")
    }

    private fun getColorCompat(resId: Int): Int = ContextCompat.getColor(this, resId)
}
