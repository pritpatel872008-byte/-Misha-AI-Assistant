package com.misha.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.graphics.Color

/**
 * First-run gate. Misha does not open the assistant until the user has reviewed
 * and granted the permissions required by the enabled phone features.
 */
class SetupActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var grantButton: Button
    private var waitingForOverlay = false
    private var launched = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (missingPermissions().isEmpty()) {
                requestOverlayPermission()
            } else {
                updateStatus()
            }
        }

    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            waitingForOverlay = false
            if (Settings.canDrawOverlays(this)) {
                finishSetupAndOpenAssistant()
            } else {
                updateStatus()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderSetup()
        if (missingPermissions().isEmpty() && Settings.canDrawOverlays(this)) {
            finishSetupAndOpenAssistant()
        } else {
            updateStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!launched && waitingForOverlay && Settings.canDrawOverlays(this)) {
            waitingForOverlay = false
            finishSetupAndOpenAssistant()
        }
    }

    private fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun missingPermissions(): List<String> = requiredPermissions().filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    private fun renderSetup() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(Color.rgb(5, 11, 22))
        }
        val heading = TextView(this).apply {
            text = "Welcome to Misha"
            textSize = 28f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(12))
        }
        val explanation = TextView(this).apply {
            text = "Before using the assistant, review these permissions. Microphone enables voice commands and Hey Misha; Contacts enables exact contact lookup; Phone enables calling; Camera enables the flashlight; Notifications keep the wake-word service visible. Display over other apps is needed for the wake popup."
            textSize = 15f
            setTextColor(Color.rgb(190, 210, 230))
            setPadding(0, 0, 0, dp(22))
        }
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(85, 230, 247))
            setPadding(0, 0, 0, dp(18))
        }
        grantButton = Button(this).apply {
            text = "Grant required permissions"
            setOnClickListener { requestMissingPermissionsOrOverlay() }
        }
        val settingsButton = Button(this).apply {
            text = "Open app settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        root.addView(heading, matchWidth())
        root.addView(explanation, matchWidth())
        root.addView(status, matchWidth())
        root.addView(grantButton, matchWidth())
        root.addView(settingsButton, matchWidth())
        setContentView(root)
    }

    private fun requestMissingPermissionsOrOverlay() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            requestOverlayPermission()
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            finishSetupAndOpenAssistant()
            return
        }
        waitingForOverlay = true
        status.text = "Allow Misha to display over other apps so the Hey Misha popup can appear."
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        overlayLauncher.launch(intent)
    }

    private fun updateStatus() {
        val missing = missingPermissions()
        val labels = missing.map { permission ->
            when (permission) {
                Manifest.permission.RECORD_AUDIO -> "Microphone"
                Manifest.permission.READ_CONTACTS -> "Contacts"
                Manifest.permission.CAMERA -> "Camera / flashlight"
                Manifest.permission.CALL_PHONE -> "Phone calls"
                Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
                else -> permission
            }
        }
        status.text = when {
            labels.isNotEmpty() -> "Still needed: " + labels.joinToString(", ")
            !Settings.canDrawOverlays(this) -> "Runtime permissions granted. Next, enable Display over other apps for the wake popup."
            else -> "Setup complete."
        }
        grantButton.text = if (labels.isEmpty()) "Enable Hey Misha popup" else "Grant required permissions"
    }

    private fun finishSetupAndOpenAssistant() {
        if (launched || missingPermissions().isNotEmpty() || !Settings.canDrawOverlays(this)) return
        launched = true
        getSharedPreferences("misha_settings", MODE_PRIVATE).edit()
            .putBoolean("setup_complete", true)
            .putBoolean("wake_word_enabled", true)
            .apply()
        try {
            ContextCompat.startForegroundService(this, Intent(this, WakeWordService::class.java))
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (_: SecurityException) {
            launched = false
            status.text = "Android blocked microphone background service. Recheck microphone and notification permissions, then try again."
        } catch (_: Exception) {
            launched = false
            status.text = "Misha could not start the wake-word service. Please try again."
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
