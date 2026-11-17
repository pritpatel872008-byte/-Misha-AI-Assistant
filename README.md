# Misha AI Assistant

Native Android assistant with an English-first interface, deterministic offline commands, voice input/output, and permission-gated flashlight control. No LLM or AI API is used.

## Backend (Vercel)
- Health: `/api/health`
- Search: `/api/search?q=your%20query`
- Current key-free search provider: Wikipedia Search API. It returns encyclopedia matches, not a complete general web index.

## Android
Open `android/` in Android Studio with JDK 17 and Android SDK 35. GitHub Actions runs unit tests and builds a debug APK on pushes and pull requests to `main`.

## Implemented in Phase 1
- English speech recognition (`en-US`) and English text-to-speech (`en-US`).
- Deterministic offline greetings, help, date/time, and basic responses.
- Flashlight on/off/status commands with runtime camera permission.
- Vercel serverless health and key-free encyclopedia search endpoints.

## Later phases
Contact matching and calling, WhatsApp message handoff, in-app search UI, wake-word listening, screenshot capture, and signed release builds are not implemented yet. They will be developed only when requested phase by phase.

## Privacy
Microphone and camera permissions are requested only when the relevant feature is used. Android speech recognition may process audio according to the configured recognition provider. The backend receives search queries only; it does not receive microphone audio or contacts.
