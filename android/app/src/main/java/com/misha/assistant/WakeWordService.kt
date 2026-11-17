package com.misha.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Visible foreground microphone service. It uses the device speech-recognition
 * provider in short sessions and only reacts to the explicit "Hey Misha" phrase.
 */
class WakeWordService : Service() {
    companion object {
        const val ACTION_STOP = "com.misha.assistant.action.STOP_WAKE_WORD"
        const val EXTRA_WAKE_WORD_TRIGGERED = "wake_word_triggered"
        private const val CHANNEL_ID = "misha_wake_word"
        private const val NOTIFICATION_ID = 7401
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var recognitionActive = false
    private var popupShown = false
    private var overlayView: LinearLayout? = null
    private var windowManager: WindowManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition is unavailable on this device.")
            stopSelf()
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
        }
        startRecognition()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            "com.misha.assistant.action.RESUME_WAKE_WORD" -> {
                popupShown = false
                startRecognition()
            }
            "com.misha.assistant.action.PAUSE_WAKE_WORD" -> stopRecognition()
        }
        return START_NOT_STICKY
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            recognitionActive = false
            if (!popupShown) scheduleRecognitionRestart()
        }

        override fun onResults(results: Bundle?) {
            recognitionActive = false
            if (popupShown) return
            val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            if (phrases.any(WakeWordPhraseMatcher::matches)) {
                showWakePopup()
            } else {
                scheduleRecognitionRestart()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (popupShown) return
            val phrases = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            if (phrases.any(WakeWordPhraseMatcher::matches)) showWakePopup()
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun startRecognition() {
        if (popupShown || recognitionActive || recognizer == null) return
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            updateNotification("Microphone permission is required. Open Misha to enable Hey Misha.")
            stopSelf()
            return
        }
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra("android.speech.extra.PREFER_OFFLINE", true)
        }
        try {
            recognitionActive = true
            recognizer?.startListening(request)
        } catch (_: Exception) {
            recognitionActive = false
            scheduleRecognitionRestart()
        }
    }

    private fun stopRecognition() {
        if (!recognitionActive) return
        recognitionActive = false
        runCatching { recognizer?.cancel() }
    }

    private fun scheduleRecognitionRestart() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ startRecognition() }, 700L)
    }

    private fun showWakePopup() {
        if (popupShown) return
        popupShown = true
        stopRecognition()
        updateNotification("Hey Misha heard you. Tap the notification or popup to speak.")
        if (!Settings.canDrawOverlays(this)) return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(16), dp(22), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.rgb(9, 24, 43))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(2), Color.rgb(73, 220, 245))
            }
            elevation = dp(12).toFloat()
        }
        val heading = TextView(this).apply {
            text = "HEY, I'M MISHA"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Tap here to give your command"
            textSize = 13f
            setTextColor(Color.rgb(145, 225, 245))
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        }
        panel.addView(heading)
        panel.addView(subtitle)
        panel.setOnClickListener { openAssistant() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = dp(16)
            y = dp(72)
            width = resources.displayMetrics.widthPixels - dp(32)
        }
        try {
            windowManager?.addView(panel, params)
            overlayView = panel
        } catch (_: Exception) {
            updateNotification("Hey Misha heard you. Tap the notification to open Misha.")
        }
    }

    private fun openAssistant() {
        removePopup()
        val open = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_WAKE_WORD_TRIGGERED, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        stopSelf()
        startActivity(open)
    }

    private fun removePopup() {
        val view = overlayView ?: return
        runCatching { windowManager?.removeView(view) }
        overlayView = null
    }

    private fun startAsForeground() {
        val notification = buildNotification("Say “Hey Misha” to open the assistant.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(message: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_WAKE_WORD_TRIGGERED, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(
            this, 7402, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Misha wake word is active")
            .setContentText(message)
            .setContentIntent(pending)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Misha wake-word listener", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Visible notification while Hey Misha detection is enabled."
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopRecognition()
        recognizer?.destroy()
        recognizer = null
        removePopup()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
