package com.leonoretech.tiktokcontroller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat

/**
 * Leonore TikTok Controller - Accessibility Service
 *
 * Performs swipe gestures (next/previous video) and attempts to tap
 * the "like" button when detected, on TikTok / Instagram Reels / YouTube Shorts.
 * Receives commands from the UI (manual buttons) and from VoiceControlManager
 * (offline speech commands) via local broadcast.
 */
class LeonoreAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_COMMAND = "com.leonoretech.tiktokcontroller.ACTION_COMMAND"
        const val EXTRA_COMMAND = "extra_command"

        const val CMD_SWIPE_UP = "swipe_up"
        const val CMD_SWIPE_DOWN = "swipe_down"
        const val CMD_LIKE = "like"
        const val CMD_PAUSE_AUTOSCROLL = "pause_autoscroll"
        const val CMD_RESUME_AUTOSCROLL = "resume_autoscroll"

        const val ACTION_SERVICE_STATE = "com.leonoretech.tiktokcontroller.ACTION_SERVICE_STATE"
        const val EXTRA_CONNECTED = "extra_connected"

        private const val TAG = "LeonoreAccessibility"

        private val SUPPORTED_PACKAGES = setOf(
            "com.zhiliaoapp.musically",   // TikTok (international)
            "com.ss.android.ugc.trill",   // TikTok (alt build / some regions)
            "com.instagram.android",      // Instagram (Reels)
            "com.google.android.youtube"  // YouTube (Shorts)
        )

        @Volatile
        var isServiceConnected: Boolean = false
            private set
    }

    private var autoScrollHandler: Handler? = null
    private var autoScrollRunnable: Runnable? = null
    private var autoScrollEnabled = false
    private var autoScrollIntervalMs = 5000L
    private var currentPackage: String? = null

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra(EXTRA_COMMAND)) {
                CMD_SWIPE_UP -> performSwipeUp()
                CMD_SWIPE_DOWN -> performSwipeDown()
                CMD_LIKE -> performLikeTap()
                CMD_PAUSE_AUTOSCROLL -> setAutoScrollEnabled(false)
                CMD_RESUME_AUTOSCROLL -> setAutoScrollEnabled(true)
                "set_interval" -> {
                    val intervalMs = intent.getLongExtra("extra_interval_ms", autoScrollIntervalMs)
                    setAutoScrollInterval(intervalMs)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        autoScrollHandler = Handler(Looper.getMainLooper())

        val filter = IntentFilter(ACTION_COMMAND)
        ContextCompat.registerReceiver(
            this, commandReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )

        broadcastConnectionState(true)
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg in SUPPORTED_PACKAGES) {
            currentPackage = pkg
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceConnected = false
        broadcastConnectionState(false)
        stopAutoScrollInternal()
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: IllegalArgumentException) {
            // receiver already unregistered, safe to ignore
        }
        return super.onUnbind(intent)
    }

    private fun broadcastConnectionState(connected: Boolean) {
        val intent = Intent(ACTION_SERVICE_STATE).apply {
            putExtra(EXTRA_CONNECTED, connected)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // ---------------------------------------------------------------
    // Gesture dispatch (swipe up = next video, swipe down = previous)
    // ---------------------------------------------------------------

    private fun screenDimensions(): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager().defaultDisplay.getMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun windowManager() =
        getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager

    fun performSwipeUp() {
        val (width, height) = screenDimensions()
        val startY = height * 0.75f
        val endY = height * 0.25f
        dispatchSwipe(width / 2f, startY, width / 2f, endY)
    }

    fun performSwipeDown() {
        val (width, height) = screenDimensions()
        val startY = height * 0.25f
        val endY = height * 0.75f
        dispatchSwipe(width / 2f, startY, width / 2f, endY)
    }

    private fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 250) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val strokeDescription = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gestureBuilder = GestureDescription.Builder().addStroke(strokeDescription)

        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Swipe gesture completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.d(TAG, "Swipe gesture cancelled")
            }
        }, null)
    }

    // ---------------------------------------------------------------
    // Like button detection + tap
    // ---------------------------------------------------------------

    /**
     * Heuristic-based like button search. Each platform uses different
     * view IDs / content descriptions, so we try several known patterns.
     * This performs a best-effort tap via ACTION_CLICK on the node itself
     * (more reliable than coordinate tapping since it works regardless
     * of where the like button is positioned on screen).
     */
    fun performLikeTap() {
        val root = rootInActiveWindow ?: run {
            Log.w(TAG, "No active window root, cannot search for like button")
            return
        }

        val candidates = listOf(
            "like", "Like", "LIKE",
            "Suka", "suka",
            "double tap to like", "Double tap to like"
        )

        var target: AccessibilityNodeInfo? = null
        for (desc in candidates) {
            val found = root.findAccessibilityNodeInfosByText(desc)
            if (found.isNotEmpty()) {
                target = found.first { it.isClickable || it.parent?.isClickable == true }
                    ?: found.firstOrNull()
                if (target != null) break
            }
        }

        if (target == null) {
            target = findNodeByViewIdKeyword(root, "like")
        }

        if (target != null) {
            val clickable = findClickableSelfOrAncestor(target)
            val performed = clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            Log.d(TAG, "Like tap performed=$performed")
        } else {
            Log.w(TAG, "Like button not found in current screen ($currentPackage)")
        }

        root.recycle()
    }

    private fun findClickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return node
    }

    private fun findNodeByViewIdKeyword(
        node: AccessibilityNodeInfo,
        keyword: String,
        depth: Int = 0
    ): AccessibilityNodeInfo? {
        if (depth > 12) return null
        val viewId = node.viewIdResourceName ?: ""
        if (viewId.lowercase().contains(keyword)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByViewIdKeyword(child, keyword, depth + 1)
            if (result != null) return result
        }
        return null
    }

    // ---------------------------------------------------------------
    // Auto scroll mode
    // ---------------------------------------------------------------

    fun setAutoScrollEnabled(enabled: Boolean) {
        autoScrollEnabled = enabled
        if (enabled) {
            startAutoScrollInternal()
        } else {
            stopAutoScrollInternal()
        }
    }

    fun setAutoScrollInterval(intervalMs: Long) {
        autoScrollIntervalMs = intervalMs
        if (autoScrollEnabled) {
            stopAutoScrollInternal()
            startAutoScrollInternal()
        }
    }

    private fun startAutoScrollInternal() {
        stopAutoScrollInternal()
        val handler = autoScrollHandler ?: return
        autoScrollRunnable = object : Runnable {
            override fun run() {
                if (currentPackage in SUPPORTED_PACKAGES) {
                    performSwipeUp()
                }
                handler.postDelayed(this, autoScrollIntervalMs)
            }
        }
        handler.postDelayed(autoScrollRunnable!!, autoScrollIntervalMs)
    }

    private fun stopAutoScrollInternal() {
        autoScrollRunnable?.let { autoScrollHandler?.removeCallbacks(it) }
        autoScrollRunnable = null
    }
}
