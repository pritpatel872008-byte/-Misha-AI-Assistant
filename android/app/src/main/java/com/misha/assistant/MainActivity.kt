package com.misha.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.content.IntentFilter
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ChatMessage(val isUser: Boolean, val text: String)
private enum class ContactActionType { CALL, MESSAGE }
private data class ContactAction(
    val type: ContactActionType,
    val contactName: String,
    val appName: String = "Choose app",
    val message: String = ""
)
private data class ContactChoiceState(
    val contacts: List<ContactMatch>,
    val action: ContactAction,
    val expiresAt: Long
)
private data class NumberChoiceState(
    val contact: ContactMatch,
    val action: ContactAction,
    val expiresAt: Long
)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private val messages = mutableStateListOf<ChatMessage>()
    private val searchResults = mutableStateListOf<SearchResult>()
    private var searchNotice by mutableStateOf("")
    private var searchLoading by mutableStateOf(false)
    private var listening by mutableStateOf(false)
    private var statusText by mutableStateOf("READY • LOCAL MODE")
    private var darkTheme by mutableStateOf(true)
    private var voiceReplies by mutableStateOf(true)
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var cameraManager: CameraManager? = null
    private var torchOn = false
    private var pendingTorch: Boolean? = null
    private var pendingContactAction: ContactAction? = null
    private var pendingCallNumber: String? = null
    private var contactChoice: ContactChoiceState? = null
    private var numberChoice: NumberChoiceState? = null
    private var screen by mutableStateOf("Assistant")

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else addAssistantReply("Microphone permission was denied. You can still type commands.")
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val command = pendingTorch
        pendingTorch = null
        if (command != null) {
            if (granted) addAssistantReply(setTorch(command))
            else addAssistantReply("Camera permission was denied. I could not change the flashlight.")
        }
    }
    private val contactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingContactAction
        pendingContactAction = null
        if (action != null) {
            if (granted) resolveContactAction(action)
            else addAssistantReply("Contacts permission was denied. I cannot look up that contact.")
        }
    }
    private val callPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val number = pendingCallNumber
        pendingCallNumber = null
        if (number != null) {
            if (granted) placeCall(number) else openDialer(number)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        textToSpeech = TextToSpeech(this, this)
        loadPreferences()
        loadHistory()
        if (messages.isEmpty()) {
            messages.add(ChatMessage(false, "Hello! I'm Misha. I can open apps, search online, call contacts, prepare messages, and control your phone."))
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }

        setContent {
            val colors = if (darkTheme) {
                darkColorScheme(
                    primary = Color(0xFF55E6F7),
                    secondary = Color(0xFF8A9BFF),
                    background = Color(0xFF050B16),
                    surface = Color(0xFF101C2D),
                    onBackground = Color(0xFFEAF7FF),
                    onSurface = Color(0xFFEAF7FF)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF087E9A),
                    secondary = Color(0xFF4E61B8),
                    background = Color(0xFFF3F8FC),
                    surface = Color.White,
                    onBackground = Color(0xFF142235),
                    onSurface = Color(0xFF142235)
                )
            }
            MaterialTheme(colorScheme = colors) {
                MishaHome(
                    messages = messages,
                    results = searchResults,
                    searchNotice = searchNotice,
                    searchLoading = searchLoading,
                    listening = listening,
                    statusText = statusText,
                    screen = screen,
                    darkTheme = darkTheme,
                    voiceReplies = voiceReplies,
                    onScreenChange = { screen = it },
                    onSend = ::processCommand,
                    onSpeak = ::requestListening,
                    onQuickCommand = ::processCommand,
                    onOpenUrl = ::openUrl,
                    onClearHistory = ::clearHistory,
                    onDarkThemeChange = { darkTheme = it; savePreferences() },
                    onVoiceRepliesChange = { voiceReplies = it; savePreferences() }
                )
            }
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("misha_settings", MODE_PRIVATE)
        darkTheme = prefs.getBoolean("dark_theme", true)
        voiceReplies = prefs.getBoolean("voice_replies", true)
    }

    private fun savePreferences() {
        getSharedPreferences("misha_settings", MODE_PRIVATE).edit()
            .putBoolean("dark_theme", darkTheme)
            .putBoolean("voice_replies", voiceReplies)
            .apply()
    }

    private fun loadHistory() {
        val raw = getSharedPreferences("misha_history", MODE_PRIVATE).getString("messages", null) ?: return
        runCatching {
            val array = org.json.JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                messages.add(ChatMessage(item.optBoolean("user"), item.optString("text")))
            }
        }
    }

    private fun saveHistory() {
        val array = org.json.JSONArray()
        messages.takeLast(80).forEach { item ->
            array.put(org.json.JSONObject().put("user", item.isUser).put("text", item.text))
        }
        getSharedPreferences("misha_history", MODE_PRIVATE).edit()
            .putString("messages", array.toString())
            .apply()
    }

    private fun addUserMessage(text: String) {
        messages.add(ChatMessage(true, text))
        saveHistory()
    }

    private fun addAssistantReply(text: String) {
        messages.add(ChatMessage(false, text))
        saveHistory()
        if (voiceReplies && ttsReady) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "misha_reply")
        }
    }

    private fun processCommand(raw: String) {
        val text = raw.trim()
        if (text.isBlank()) return
        addUserMessage(text)
        if (handlePendingChoice(text)) return

        when (val command = LocalBrain.parse(text)) {
            is MishaCommand.Torch -> {
                if (command.enabled == null) {
                    addAssistantReply(if (torchOn) "The flashlight is on." else "The flashlight is off.")
                } else {
                    requestTorch(command.enabled)
                }
            }
            is MishaCommand.OpenApp -> openApp(command.appName)
            is MishaCommand.Search -> performSearch(command.query)
            is MishaCommand.Call -> resolveContactAction(ContactAction(ContactActionType.CALL, command.contactName))
            is MishaCommand.Message -> resolveContactAction(
                ContactAction(ContactActionType.MESSAGE, command.contactName, command.appName, command.body)
            )
            MishaCommand.Battery -> reportBattery()
            MishaCommand.DateTime -> {
                val now = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.US).format(Date())
                addAssistantReply("It is " + now + ".")
            }
            is MishaCommand.Conversation -> addAssistantReply(LocalBrain.reply(command.text))
        }
    }

    private fun requestTorch(enabled: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            addAssistantReply(setTorch(enabled))
        } else {
            pendingTorch = enabled
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setTorch(enabled: Boolean): String {
        val manager = cameraManager ?: return "The flashlight service is unavailable."
        return try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return "This phone does not report an available rear flashlight."
            manager.setTorchMode(cameraId, enabled)
            torchOn = enabled
            statusText = if (enabled) "FLASHLIGHT ON" else "READY • LOCAL MODE"
            if (enabled) "Flashlight turned on." else "Flashlight turned off."
        } catch (_: SecurityException) {
            "I could not access the flashlight. Check camera permission and try again."
        } catch (_: Exception) {
            "I could not change the flashlight. Another app may be using the camera."
        }
    }

    private fun requestListening() {
        if (speechRecognizer == null) {
            addAssistantReply("Speech recognition is unavailable on this device. Please type your command.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        val recognizer = speechRecognizer ?: return
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                statusText = "LISTENING • ENGLISH"
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                listening = false
                statusText = "PROCESSING COMMAND"
            }
            override fun onError(error: Int) {
                listening = false
                statusText = "READY • LOCAL MODE"
            }
            override fun onResults(results: Bundle?) {
                listening = false
                statusText = "READY • LOCAL MODE"
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(::processCommand)
            }
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
        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            listening = false
            statusText = "READY • LOCAL MODE"
            addAssistantReply("Voice input could not start. Please try again or type your command.")
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            addAssistantReply("Please say what you want me to search for.")
            return
        }
        searchResults.clear()
        searchNotice = ""
        searchLoading = true
        statusText = "SEARCHING ONLINE"
        addAssistantReply("Searching for: " + query)
        SearchClient.search(query) { result ->
            runOnUiThread {
                searchLoading = false
                statusText = "READY • LOCAL MODE"
                result.onSuccess { response ->
                    searchResults.clear()
                    searchResults.addAll(response.results)
                    searchNotice = response.provider + ". " + response.notice
                    if (response.results.isEmpty()) {
                        addAssistantReply("I could not find matching results. Try a different search phrase.")
                    } else {
                        addAssistantReply("I found " + response.results.size + " results. Tap a result to open its source.")
                    }
                }.onFailure {
                    addAssistantReply("Online search is unavailable right now. Please check your internet connection and try again.")
                }
            }
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { addAssistantReply("I could not open that source link.") }
    }

    private fun openApp(rawName: String) {
        val name = rawName.lowercase(Locale.ROOT).trim()
        val specialIntent = when (name) {
            "settings", "system settings" -> Intent(Settings.ACTION_SETTINGS)
            "camera" -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            "phone", "dialer", "call app" -> Intent(Intent.ACTION_DIAL)
            "bluetooth settings" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "wifi settings", "wi-fi settings" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "display settings" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            else -> null
        }
        if (specialIntent != null) {
            runCatching {
                startActivity(specialIntent)
                addAssistantReply("Opening " + rawName + ".")
            }.onFailure { addAssistantReply("I could not open " + rawName + " on this phone.") }
            return
        }

        val packageName = appPackages[name]
        if (packageName == null) {
            addAssistantReply("I do not have a launcher mapping for " + rawName + " yet. Try WhatsApp, Telegram, YouTube, Chrome, Messages, Maps, Gmail, Camera, Phone, or Settings.")
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            addAssistantReply(rawName + " does not appear to be installed.")
            return
        }
        runCatching {
            startActivity(launchIntent)
            addAssistantReply("Opening " + rawName + ".")
        }.onFailure { addAssistantReply("Android could not open " + rawName + ".") }
    }

    private val appPackages = mapOf(
        "whatsapp" to "com.whatsapp",
        "whatsapp business" to "com.whatsapp.w4b",
        "telegram" to "org.telegram.messenger",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "messages" to "com.google.android.apps.messaging",
        "google messages" to "com.google.android.apps.messaging",
        "signal" to "org.thoughtcrime.securesms",
        "messenger" to "com.facebook.orca",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "play store" to "com.android.vending"
    )

    private fun resolveContactAction(action: ContactAction) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingContactAction = action
            addAssistantReply("I need Contacts permission to find that person. Please allow it when Android asks.")
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            return
        }

        val matches = runCatching { ContactRepository.findMatches(this, action.contactName) }.getOrElse {
            addAssistantReply("I could not read contacts on this device.")
            return
        }
        when {
            matches.isEmpty() -> addAssistantReply("I could not find a contact matching \"" + action.contactName + "\". Check the name and try again.")
            matches.size == 1 -> selectContact(matches.first(), action)
            else -> {
                contactChoice = ContactChoiceState(matches, action, System.currentTimeMillis() + 60_000L)
                val duplicateNames = matches.groupingBy { it.name.lowercase(Locale.ROOT) }.eachCount()
                val options = matches.mapIndexed { index, contact ->
                    val suffix = if ((duplicateNames[contact.name.lowercase(Locale.ROOT)] ?: 0) > 1) {
                        " ••••" + contact.numbers.firstOrNull()?.filter(Char::isDigit)?.takeLast(4).orEmpty()
                    } else ""
                    (index + 1).toString() + ". " + contact.name + suffix
                }
                addAssistantReply("I found these matching contacts. Say the number to select one:\n" + options.joinToString("\n"))
            }
        }
    }

    private fun selectContact(contact: ContactMatch, action: ContactAction) {
        if (contact.numbers.size == 1) {
            executeContactAction(contact, contact.numbers.first(), action)
        } else {
            numberChoice = NumberChoiceState(contact, action, System.currentTimeMillis() + 60_000L)
            val options = contact.numbers.mapIndexed { index, number ->
                (index + 1).toString() + ". Number ending " + number.filter(Char::isDigit).takeLast(4)
            }
            addAssistantReply("There are multiple numbers saved for " + contact.name + ". Which number should I use?\n" + options.joinToString("\n"))
        }
    }

    private fun handlePendingChoice(input: String): Boolean {
        val contacts = contactChoice
        if (contacts != null) {
            if (System.currentTimeMillis() > contacts.expiresAt) {
                contactChoice = null
                addAssistantReply("That selection expired. Please repeat the contact command.")
                return true
            }
            val index = LocalBrain.ordinal(input)
            if (index == null || index !in 1..contacts.contacts.size) {
                addAssistantReply("Please say a valid number from 1 to " + contacts.contacts.size + ".")
                return true
            }
            contactChoice = null
            selectContact(contacts.contacts[index - 1], contacts.action)
            return true
        }

        val numbers = numberChoice
        if (numbers != null) {
            if (System.currentTimeMillis() > numbers.expiresAt) {
                numberChoice = null
                addAssistantReply("That selection expired. Please repeat the command.")
                return true
            }
            val index = LocalBrain.ordinal(input)
            if (index == null || index !in 1..numbers.contact.numbers.size) {
                addAssistantReply("Please say a valid number from 1 to " + numbers.contact.numbers.size + ".")
                return true
            }
            numberChoice = null
            executeContactAction(numbers.contact, numbers.contact.numbers[index - 1], numbers.action)
            return true
        }
        return false
    }

    private fun executeContactAction(contact: ContactMatch, number: String, action: ContactAction) {
        when (action.type) {
            ContactActionType.CALL -> requestCall(number, contact.name)
            ContactActionType.MESSAGE -> launchMessageApp(action.appName, contact.name, number, action.message)
        }
    }

    private fun requestCall(number: String, name: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            addAssistantReply("Calling " + name + ".")
            placeCall(number)
        } else {
            pendingCallNumber = number
            addAssistantReply("Android needs Phone permission to place this call.")
            callPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun placeCall(number: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))))
        }.onFailure { openDialer(number) }
    }

    private fun openDialer(number: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
            addAssistantReply("Phone permission was not granted, so I opened the dialer instead.")
        }.onFailure { addAssistantReply("I could not open the phone dialer.") }
    }

    private fun launchMessageApp(appName: String, contactName: String, number: String, body: String) {
        val digits = number.filter(Char::isDigit)
        val target = appName.lowercase(Locale.ROOT).trim()
        val intent = when (target) {
            "sms", "messages", "google messages", "text message" ->
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", body)
            "whatsapp", "whatsapp business" -> {
                val pkg = if (target == "whatsapp business") "com.whatsapp.w4b" else "com.whatsapp"
                if (packageManager.getLaunchIntentForPackage(pkg) == null) {
                    addAssistantReply(appName + " is not installed. Install it or choose another messaging app.")
                    return
                }
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits + "?text=" + Uri.encode(body))).setPackage(pkg)
            }
            "telegram" -> shareTextToPackage("org.telegram.messenger", body)
            "signal" -> shareTextToPackage("org.thoughtcrime.securesms", body)
            "messenger" -> shareTextToPackage("com.facebook.orca", body)
            else -> Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
        }

        runCatching {
            if (intent.action == Intent.ACTION_SEND && intent.getPackage() == null) {
                startActivity(Intent.createChooser(intent, "Choose an app to message " + contactName))
            } else {
                startActivity(intent)
            }
            addAssistantReply("Message prepared for " + contactName + " in " + appName + ". Review the recipient and text in that app, then tap Send. Misha never sends messages silently.")
        }.onFailure {
            addAssistantReply("I could not open " + appName + ". Check that it is installed, or try Messages.")
        }
    }

    private fun shareTextToPackage(packageName: String, body: String): Intent {
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
        return if (packageManager.getLaunchIntentForPackage(packageName) != null) share.setPackage(packageName) else share
    }

    private fun reportBattery() {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (battery == null) {
            addAssistantReply("I could not read the battery status.")
            return
        }
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = (level * 100 / scale).coerceIn(0, 100)
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val chargeText = if (charging) "The phone is charging." else "The phone is not charging."
        addAssistantReply("Battery level is " + percent + " percent. " + chargeText)
    }

    private fun clearHistory() {
        messages.clear()
        searchResults.clear()
        searchNotice = ""
        messages.add(ChatMessage(false, "Chat history cleared. I'm ready for your next command."))
        saveHistory()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            val result = textToSpeech?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsReady = false
                statusText = "ENGLISH TTS NOT AVAILABLE"
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun MishaHome(
    messages: List<ChatMessage>,
    results: List<SearchResult>,
    searchNotice: String,
    searchLoading: Boolean,
    listening: Boolean,
    statusText: String,
    screen: String,
    darkTheme: Boolean,
    voiceReplies: Boolean,
    onScreenChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onSpeak: () -> Unit,
    onQuickCommand: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onVoiceRepliesChange: (Boolean) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val accent = MaterialTheme.colorScheme.primary
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(selected = screen == "Assistant", onClick = { onScreenChange("Assistant") }, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Home") })
                NavigationBarItem(selected = screen == "Apps", onClick = { onScreenChange("Apps") }, icon = { Icon(Icons.Filled.Apps, null) }, label = { Text("Apps") })
                NavigationBarItem(selected = screen == "History", onClick = { onScreenChange("History") }, icon = { Icon(Icons.Filled.History, null) }, label = { Text("History") })
                NavigationBarItem(selected = screen == "Settings", onClick = { onScreenChange("Settings") }, icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") })
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("MISHA", color = MaterialTheme.colorScheme.onBackground, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                    Text("PERSONAL ASSISTANT  •  NO LLM API", color = accent, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.12f), border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))) {
                    Text(statusText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))

            when (screen) {
                "Settings" -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text("Settings", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(12.dp))
                        SettingRow("Dark interface", "Switch between dark and light appearance", darkTheme, onDarkThemeChange)
                        SettingRow("Voice replies", "Speak Misha's replies in English", voiceReplies, onVoiceRepliesChange)
                        Spacer(Modifier.height(12.dp))
                        Text("Privacy", color = accent, fontWeight = FontWeight.Bold)
                        Text("Contacts are read only when you ask Misha to call or prepare a message. Search queries go to the search provider. Microphone audio is handled by Android's speech-recognition service.", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Platform limits", color = accent, fontWeight = FontWeight.Bold)
                        Text("Always-on wake-word listening and automatic message sending are not enabled. Android requires explicit user action for these sensitive operations.", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        TextButton(onClick = onClearHistory) {
                            Icon(Icons.Filled.Delete, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Clear chat history")
                        }
                    }
                }
                "Apps" -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text("Quick actions", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("Tap an action or say the same command.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        QuickAction("Search the web", "Search the web for Android security", Icons.Filled.Search) { onQuickCommand("Search the web for Android security") }
                        QuickAction("Call a contact", "Call Rahul", Icons.Filled.Call) { onQuickCommand("Call Rahul") }
                        QuickAction("Prepare a message", "Message Rahul on WhatsApp saying Hello", Icons.Filled.Message) { onQuickCommand("Message Rahul on WhatsApp saying Hello") }
                        QuickAction("Flashlight", "Turn on the flashlight", Icons.Filled.FlashOn) { onQuickCommand("Turn on the flashlight") }
                        QuickAction("Battery", "Check battery", Icons.Filled.BatteryFull) { onQuickCommand("Check battery") }
                        QuickAction("Open YouTube", "Open YouTube", Icons.Filled.Apps) { onQuickCommand("Open YouTube") }
                    }
                }
                "History" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent activity", modifier = Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onClearHistory) {
                            Icon(Icons.Filled.Delete, null)
                            Text("Clear")
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(messages) { message -> MessageBubble(message) }
                    }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        val transition = rememberInfiniteTransition(label = "misha-orb")
                        val pulse by transition.animateFloat(
                            initialValue = 0.94f,
                            targetValue = 1.04f,
                            animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
                            label = "orb-pulse"
                        )
                        Box(
                            modifier = Modifier.padding(top = 4.dp).size(142.dp).scale(if (listening) pulse * 1.04f else pulse)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(Color(0xFF49E7FF), Color(0xFF126A91), Color(0xFF09182C))))
                                .border(2.dp, accent.copy(alpha = 0.8f), CircleShape)
                                .clickable(onClick = onSpeak),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (listening) "LISTENING" else "M", color = Color.White, fontSize = if (listening) 15.sp else 50.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(if (listening) "SPEAK NOW" else "TAP THE ORB TO SPEAK", color = accent, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                        SmallAction("Torch ON") { onQuickCommand("Turn on the flashlight") }
                        SmallAction("Torch OFF") { onQuickCommand("Turn off the flashlight") }
                        SmallAction("Battery") { onQuickCommand("Check battery") }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { message -> MessageBubble(message) }
                        if (searchLoading) item { Text("Searching online…", color = accent, fontSize = 12.sp) }
                    }
                    if (results.isNotEmpty()) {
                        Text("Search results", color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(searchNotice, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(170.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(results) { result ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { onOpenUrl(result.url) },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(result.title, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(result.snippet, color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(result.source + " • Tap to open", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type an English command…", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp)
                        )
                        IconButton(onClick = onSpeak, modifier = Modifier.size(48.dp).background(accent.copy(alpha = 0.14f), CircleShape)) {
                            Icon(Icons.Filled.Mic, contentDescription = "Speak", tint = accent)
                        }
                        IconButton(
                            onClick = {
                                val value = draft.trim()
                                draft = ""
                                if (value.isNotBlank()) onSend(value)
                            },
                            modifier = Modifier.size(48.dp).background(accent, CircleShape)
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Send command", tint = Color(0xFF03101A))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isUser) Color(0xFF0C536B) else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(17.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (message.isUser) 0.4f else 0.14f)),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun RowScope.SmallAction(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.weight(1f).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Text(label, modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun QuickAction(title: String, command: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(command, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
