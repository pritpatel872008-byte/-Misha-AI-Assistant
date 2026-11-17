package com.misha.assistant

import android.Manifest
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.Settings
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
    private val searchClient by lazy { SearchClient(BuildConfig.MISHA_BACKEND_URL) }
    private data class LaunchOption(val label: String, val packageName: String)
    private var pendingApps: List<LaunchOption> = emptyList()
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var cameraManager: CameraManager? = null
    private var torchOn = false
    private var pendingTorchCommand: String? = null
    private var pendingMessageRequest: MessageRequest? = null
    private val messagingController by lazy { MessagingController(this, ::addReply) }

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

    private val contactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = pendingMessageRequest
        pendingMessageRequest = null
        if (granted && request != null) messagingController.resolve(request)
        else addReply("Contacts permission was denied. Enable it in Android settings if you want Misha to find recipients.")
    }

    private val messages = mutableStateListOf<Pair<Boolean, String>>()
    private val searchResults = mutableStateListOf<SearchResult>()
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
                        if (searchResults.isNotEmpty()) {
                            item { Text("WEB SEARCH RESULTS", color = Color(0xFF43DDF5), fontSize = 12.sp) }
                            items(searchResults) { result ->
                                Surface(color = Color(0xFF101D30), shape = RoundedCornerShape(12.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(result.title, color = Color.White, fontSize = 16.sp)
                                        Text(result.snippet, color = Color(0xFFB9C8D9), fontSize = 12.sp)
                                        Text("Source: ${result.source}", color = Color(0xFF43DDF5), fontSize = 11.sp)
                                        TextButton(onClick = { openWebResult(result.url) }) { Text("Open source") }
                                    }
                                }
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
        if (handlePendingAppSelection(command)) return
        if (messagingController.handleSelection(command)) return
        val searchQuery = SearchQueryParser.parse(command)
        if (searchQuery != null) {
            messages.add(true to command)
            runWebSearch(searchQuery)
            return
        }
        val appName = AppCommandParser.parse(command)
        if (appName != null) {
            messages.add(true to command)
            openInstalledApp(appName)
            return
        }
        val messageRequest = MessageCommandParser.parse(command)
        if (messageRequest != null) {
            messages.add(true to command)
            pendingMessageRequest = messageRequest
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                pendingMessageRequest = null
                messagingController.resolve(messageRequest)
            } else {
                addReply("I need Contacts permission to find ${messageRequest.recipient}.")
                contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            }
            return
        }
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

    private fun runWebSearch(query: String) {
        addReply("Searching the web for: $query")
        Thread {
            try {
                val results = searchClient.search(query)
                runOnUiThread {
                    searchResults.clear()
                    searchResults.addAll(results)
                    addReply(if (results.isEmpty()) "I couldn't find matching results. Try different keywords." else "I found ${results.size} results. Open a source to read more.")
                }
            } catch (_: Exception) {
                runOnUiThread { addReply("Online search is unavailable right now. Check your internet connection and try again.") }
            }
        }.start()
    }

    private fun openWebResult(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) { addReply("No browser is available to open this result.") }
    }

    private fun openInstalledApp(requestedName: String) {
        val name = requestedName.lowercase(Locale.ROOT).trim()
        try {
            val directIntent = when (name) {
                "settings", "system settings" -> Intent(Settings.ACTION_SETTINGS)
                "camera" -> Intent("android.media.action.IMAGE_CAPTURE")
                "phone", "dialer" -> Intent(Intent.ACTION_DIAL)
                "browser", "internet" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                else -> null
            }
            if (directIntent != null) {
                if (directIntent.resolveActivity(packageManager) != null) startActivity(directIntent)
                else addReply("That app or system screen is not available on this phone.")
                return
            }
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val options = packageManager.queryIntentActivities(launcher, 0)
                .mapNotNull { info ->
                    val label = info.loadLabel(packageManager)?.toString().orEmpty()
                    val packageName = info.activityInfo?.packageName.orEmpty()
                    if (label.isNotBlank() && packageName.isNotBlank() && label.contains(requestedName, ignoreCase = true))
                        LaunchOption(label, packageName) else null
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase(Locale.ROOT) }
            when (options.size) {
                0 -> addReply("I couldn't find an installed app matching $requestedName.")
                1 -> launchPackage(options.first())
                else -> {
                    pendingApps = options
                    addReply("I found multiple matching apps. Say or type a number:\n" +
                        options.mapIndexed { index, option -> "${index + 1}. ${option.label}" }.joinToString("\n"))
                }
            }
        } catch (_: Exception) {
            addReply("I couldn't open $requestedName. Please check that it is installed.")
        }
    }

    private fun handlePendingAppSelection(input: String): Boolean {
        if (pendingApps.isEmpty()) return false
        val value = input.trim().lowercase(Locale.ROOT).removePrefix("number ").removePrefix("option ").removeSuffix(".")
        if (value == "cancel") { pendingApps = emptyList(); addReply("App launch cancelled."); return true }
        val choice = value.toIntOrNull() ?: when (value) { "one" -> 1; "two" -> 2; "three" -> 3; "four" -> 4; "five" -> 5; else -> null }
        if (choice == null || choice !in 1..pendingApps.size) {
            addReply("Please choose a number from 1 to ${pendingApps.size}, or say cancel.")
            return true
        }
        val selected = pendingApps[choice - 1]
        pendingApps = emptyList()
        launchPackage(selected)
        return true
    }

    private fun launchPackage(option: LaunchOption) {
        val launchIntent = packageManager.getLaunchIntentForPackage(option.packageName)
        if (launchIntent == null) addReply("${option.label} is installed, but Android did not provide a launch action.")
        else try { startActivity(launchIntent); addReply("Opening ${option.label}.") }
        catch (_: Exception) { addReply("I couldn't launch ${option.label}.") }
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
    private fun addReply(reply: String) {
        messages.add(false to reply)
        if (ttsReady) tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "misha_reply")
    }
    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            val language = tts?.setLanguage(Locale.US)
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) { ttsReady = false; runOnUiThread { errorText = "Install an English text-to-speech voice in Android settings." } }
        }
    }
    override fun onDestroy() { speechRecognizer?.destroy(); tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
