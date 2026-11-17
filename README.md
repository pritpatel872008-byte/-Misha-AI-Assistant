# Misha AI Assistant

Native Android assistant with an English-first interface, deterministic offline commands, voice input/output, flashlight control, and contact-aware messaging handoff. No LLM or AI API is used.

## Backend (Vercel)
- Health: `/api/health`
- Search: `/api/search?q=your%20query`
- Current key-free search provider: Wikipedia Search API. It returns encyclopedia matches, not a complete general web index.

## Android
Open `android/` in Android Studio with JDK 17 and Android SDK 35. GitHub Actions runs unit tests and builds a debug APK.

## Phase 1
- English speech recognition (`en-US`) and English text-to-speech (`en-US`).
- Deterministic offline greetings, help, date/time, and basic responses.
- Flashlight on/off/status commands with runtime camera permission.
- Vercel serverless health and key-free encyclopedia search endpoints.

## Phase 2 — Messaging foundation
- English message-command parser (for example: `Send a message to Rahul on WhatsApp saying Hello`).
- Requests Contacts permission only when message recipient resolution is requested.
- Matches contact names and shows all matching contacts in a numbered list; a single match is selected automatically.
- If one contact has multiple phone numbers, asks the user to choose a numbered number.
- Opens WhatsApp via a prefilled `wa.me` link, Telegram via its app URI, SMS via the system messaging intent, and other compatible apps through Android's Sharesheet.
- Shows a review dialog before opening the target app. The user must tap Send inside the selected app; Misha does not silently send messages.
- Generic Sharesheet compatibility depends on whether an installed app accepts shared text. Not every app exposes a public API for direct-message automation.

## Later phases
Wake-word listening, full search results UI, more call/contact actions, screenshot capture, deeper app-specific integrations, and signed release builds are not implemented yet. They will be developed only when requested phase by phase.

## Privacy and safety
Microphone, camera, and contacts permissions are requested only when the relevant feature is used. Contacts are read on demand for matching and are not uploaded to the backend or saved by Misha. Message text is handed to the selected app only after the user reviews the preview. Android speech recognition may process audio according to the configured recognition provider.
