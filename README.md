# Misha AI Assistant

Misha is a native Android assistant built with Kotlin and Jetpack Compose. It uses deterministic local command parsing, Android speech recognition/text-to-speech, and Android intents. No LLM or AI API is used.

## Backend

- Production API: https://misha-search-backend.vercel.app
- Health: /api/health
- Search: /api/search?q=your%20query
- Search provider: Wikipedia Search API (key-free encyclopedia search, not a complete general web index).

## Implemented features

- English-first voice input (en-US) and English text-to-speech (en-US).
- Dark/light Compose interface with animated voice orb, chat, quick actions, history, and settings.
- Local command parsing for flashlight, app launch, calling, messaging, battery, date/time, and web search.
- Flashlight on/off/status with runtime camera permission.
- Supported app launching: WhatsApp, WhatsApp Business, Telegram, YouTube, Chrome, Messages, Signal, Messenger, Maps, Gmail, Play Store, Camera, Phone, and Android Settings.
- Contact matching by exact name or name prefix. Only matching contacts are listed; one match is selected automatically; multiple matches are numbered in the order shown.
- Multiple phone numbers for one contact are presented as numbered choices. A numeric reply selects only from the current, expiring choice list.
- Calling uses Android's call permission. If permission is denied, Misha opens the dialer instead of placing the call.
- Calling and every messaging command use the same contact-selection policy: one match is selected automatically; multiple matching contacts are listed as 1, 2, 3, and the spoken number selects only that listed contact. If that contact has multiple phone numbers, Misha asks which number to use.
- WhatsApp and WhatsApp Business open the selected number's chat with a prefilled message. SMS opens the selected number's compose screen. Telegram uses its documented phone-number deep link and pre-fills the draft when the contact is discoverable by phone number. Signal and Messenger receive a package-targeted share handoff; those apps may still ask you to choose the conversation because they do not provide Misha a documented public phone-number-to-chat handoff.
- Misha never silently sends a message. Review the recipient and text in the target app, then tap Send.
- Online search calls the Misha Vercel API and falls back to Wikipedia's public search API if the backend is unavailable. Results show source links.
- Local chat history and appearance/voice settings are stored on-device using SharedPreferences.

## Voice command examples

- "Turn on the flashlight"
- "Turn off the flashlight"
- "Is the flashlight on?"
- "Open WhatsApp"
- "Open YouTube"
- "Open Settings"
- "Search the web for Android security"
- "Call Rahul"
- "Send a message to Rahul on WhatsApp saying Hello"
- "Message Rahul on Telegram: I am coming"
- "Message Rahul on Signal saying I am on my way"
- "Message Rahul on Messenger saying Please call me"
- "Message Rahul saying Call me"
- "Check battery"
- "What time is it?"

If multiple matching contacts exist, Misha lists only those matches (for example Rahul and Rahul Bhai) and asks for a number. Say "2" or "number two" to select the second displayed contact. If a selected contact has multiple phone numbers, Misha asks you to choose a number too.

## Build

Open the android directory in Android Studio with JDK 17 and Android SDK 35. GitHub Actions runs backend smoke tests, unit tests, and assembles a debug APK.

    cd android
    gradle :app:testDebugUnitTest
    gradle :app:assembleDebug

The APK is written to app/build/outputs/apk/debug/app-debug.apk inside the Android project.

## Platform and privacy limits

- Android microphone recognition starts only after the user taps Speak; this project does not run an always-on wake-word service.
- Android requires a visible permission prompt for microphone, contacts, camera/flashlight, and direct phone calls.
- Messaging apps own their send flow. Misha can prepare or hand off a message, but the user must review and send it inside the destination app.
- Generic third-party messaging apps do not expose a universal public API for selecting a recipient and sending automatically. Telegram, Signal, and Messenger hand off message text to their share interface; the user chooses the conversation and taps Send.
- Call screening, silent recording, background microphone monitoring, and automated screen capture are not enabled. They require additional Android roles, foreground-service flows, or explicit system capture consent and are not silently enabled.
- Contacts are read on demand and are not uploaded or stored by Misha. Search terms are sent to the configured search provider. Chat history is stored locally and can be cleared from Settings.
