package com.misha.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private val brain = LocalBrain()
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var cameraManager: CameraManager? = null
    private var torchOn = false
    private var pendingTorchCommand: String? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else addReply("Microphone permission was denied. You can type commands instead.")
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val command = pendingTorchCommand
        pendingTorchCommand = null
        if (command != null) {
            val reply = if (granted) setTorch(true) else "Camera permission was denied. I could not turn on the flashlight."
            addExchange(command, reply)
        }
    }

    private val messages = mutableStateListOf<Pair<Boolean, String>>()
    private var draft by mutableStateOf("")
    private var listening by mutableStateOf(false)
    private var errorText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        messages.add(false to "Hello! I'm Misha. How can I help you?")
        if (SpeechRecognizer.isRecognitionAvailable(this)) speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF43DDF5), background = Color(0xFF050B16), surface = Color(0xFF101D30), onSurface = Color.White)) {
                Column(Modifier.fillMaxSize().background(Color(0xFF050B16)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("MISHA", color = Color.White, fontSize = 28.sp)
                    Text("ENGLISH • LOCAL MODE", color = Color(0xFF43DDF5), fontSize = 11.sp)
                    Spacer(Modifier.height(20.dp))
                    Text(if (listening) "LISTENING..." else "AT YOUR SERVICE", color = Color(0xFF43DDF5))
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { processCommand("Turn on the flashlight") }) { Text("Torch ON") }
                        Button(onClick = { processCommand("Turn off the flashlight") }) { Text("Torch OFF") }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(messages) { item ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.first) Arrangement.End else Arrangement.Start) {
                                Surface(color = if (item.first) Color(0xFF12435A) else Color(0xFF101D30), shape = RoundedCornerShape(14.dp)) { Text(item.second, color = Color.White, modifier = Modifier.padding(12.dp)) }
                            }
                        }
                    }
                    if (errorText.isNotBlank()) Text(errorText, color = Color(0xFFFF9B9B), fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Type an English command") }, singleLine = true)
                        Spacer(Modifier.width(6.dp))
                        Button(onClick = { val value = draft; draft = ""; processCommand(value) }) { Text("Send") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO) }) { Text(if (listening) "Listening..." else "Speak") }
                }
            }
        }
    }

    private fun processCommand(raw: String) {
        val command = raw.trim()
        if (command.isEmpty()) return
        when (brain.torchAction(command)) {
            LocalBrain.TorchAction.ON -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) addExchange(command, setTorch(true))
                else { pendingTorchCommand = command; cameraPermission.launch(Manifest.permission.CAMERA) }
            }
            LocalBrain.TorchAction.OFF -> addExchange(command, setTorch(false))
            LocalBrain.TorchAction.STATUS -> addExchange(command, if (torchOn) "The flashlight is on." else "The flashlight is off.")
            null -> addExchange(command, brain.reply(command))
        }
    }

    private fun setTorch(enabled: Boolean): String {
        val manager = cameraManager ?: return "The flashlight service is unavailable."
        return try {
            val id = manager.cameraIdList.firstOrNull { cameraId -> manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
                ?: return "This phone does not report an available rear flashlight."
            manager.setTorchMode(id, enabled)
            torchOn = enabled
            if (enabled) "Flashlight turned on." else "Flashlight turned off."
        } catch (_: SecurityException) { "I could not access the flashlight. Check camera permission and try again." }
          catch (_: Exception) { "I could not change the flashlight. Another app may be using the camera." }
    }

    private fun startListening() {
        val recognizer = speechRecognizer ?: run { addReply("Speech recognition is unavailable on this device."); return }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true; errorText = "" }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) { listening = false; errorText = "Voice input failed. Please try again or type your command." }
            override fun onResults(results: Bundle?) { listening = false; results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(::processCommand) }
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Misha in English")
        }
        listening = true
        recognizer.startListening(intent)
    }

    private fun addExchange(user: String, reply: String) {
        messages.add(true to user); messages.add(false to reply)
        if (ttsReady) tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "misha_reply")
    }
    private fun addReply(reply: String) { messages.add(false to reply) }
    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            val language = tts?.setLanguage(Locale.US)
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) { ttsReady = false; runOnUiThread { errorText = "Install an English text-to-speech voice in Android settings." } }
        }
    }
    override fun onDestroy() { speechRecognizer?.destroy(); tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
