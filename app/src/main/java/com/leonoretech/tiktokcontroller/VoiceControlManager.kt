package com.leonoretech.tiktokcontroller

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Wraps Android's built-in SpeechRecognizer (android.speech) to provide
 * continuous, restart-on-end voice command listening.
 *
 * Recognition runs fully on-device when the user has downloaded offline
 * speech models (Settings > System > Languages & input > Voice input >
 * Offline speech recognition, on most stock Android / Gboard setups) and
 * EXTRA_PREFER_OFFLINE is requested below. No network calls are made by
 * this app; voice data never leaves the device through our own code.
 *
 * Recognized keywords:
 *   "up"      -> next video (swipe up)
 *   "down"    -> previous video (swipe down)
 *   "like"    -> tap like button
 *   "pause"   -> pause auto scroll
 *   "resume"  -> resume auto scroll
 */
class VoiceControlManager(
    private val context: Context,
    private val onCommandRecognized: (String, String) -> Unit, // (rawText, mappedCommand)
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {

    companion object {
        private const val TAG = "VoiceControlManager"

        private val COMMAND_MAP = mapOf(
            "up" to LeonoreAccessibilityService.CMD_SWIPE_UP,
            "next" to LeonoreAccessibilityService.CMD_SWIPE_UP,
            "down" to LeonoreAccessibilityService.CMD_SWIPE_DOWN,
            "previous" to LeonoreAccessibilityService.CMD_SWIPE_DOWN,
            "back" to LeonoreAccessibilityService.CMD_SWIPE_DOWN,
            "like" to LeonoreAccessibilityService.CMD_LIKE,
            "pause" to LeonoreAccessibilityService.CMD_PAUSE_AUTOSCROLL,
            "stop" to LeonoreAccessibilityService.CMD_PAUSE_AUTOSCROLL,
            "resume" to LeonoreAccessibilityService.CMD_RESUME_AUTOSCROLL,
            "start" to LeonoreAccessibilityService.CMD_RESUME_AUTOSCROLL
        )
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldKeepListening = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isAvailable()) {
            onError("Speech recognizer tidak tersedia di device ini")
            return
        }

        shouldKeepListening = true
        if (isListening) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(buildListener())
        }

        launchListening()
    }

    private fun launchListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                800
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                800
            )
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            onListeningStateChanged(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            onError("Gagal memulai voice recognition: ${e.message}")
            isListening = false
            onListeningStateChanged(false)
        }
    }

    fun stopListening() {
        shouldKeepListening = false
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        onListeningStateChanged(false)
    }

    private fun buildListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onListeningStateChanged(true)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            Log.w(TAG, "Speech recognizer error code: $error")
            // Auto-restart on recoverable errors (no speech, timeout) to
            // keep continuous listening alive, similar to "always-on" voice control.
            if (shouldKeepListening) {
                restartListening()
            } else {
                onListeningStateChanged(false)
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.lowercase(Locale.getDefault())?.trim()
            if (!text.isNullOrEmpty()) {
                handleRecognizedText(text)
            }
            if (shouldKeepListening) {
                restartListening()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.lowercase(Locale.getDefault())?.trim()
            if (!text.isNullOrEmpty()) {
                // Check partials too so commands trigger faster without waiting for silence
                COMMAND_MAP.keys.firstOrNull { keyword -> text.contains(keyword) }?.let {
                    handleRecognizedText(text)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleRecognizedText(text: String) {
        val matchedKeyword = COMMAND_MAP.keys.firstOrNull { keyword -> text.contains(keyword) }
        val command = matchedKeyword?.let { COMMAND_MAP[it] }
        if (command != null) {
            onCommandRecognized(text, command)
        }
    }

    private fun restartListening() {
        speechRecognizer?.cancel()
        launchListening()
    }
}
