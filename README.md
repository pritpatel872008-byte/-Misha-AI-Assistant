# Misha AI Assistant

Native Android assistant with English-first voice input/output, deterministic offline commands, flashlight control, contact-aware messaging handoff, installed-app launching and online search. No LLM or AI API is used.

## Backend (Vercel)
- Health endpoint: `/api/health`
- Search endpoint: `/api/search?q=your%20query`
- The current key-free provider is Wikipedia Search. It returns encyclopedia article matches, not a complete general web index.
- Vercel Git linking is not yet enabled for this repository. The Vercel integration currently reports that it lacks write/admin access to this GitHub repository.

## Android setup
Open `android/` in Android Studio with JDK 17 and Android SDK 35. GitHub Actions runs unit tests and builds a debug APK.

To point Android at a deployed Vercel project, pass the project origin as a Gradle property:
```bash
gradle :app:assembleDebug -PmishaBackendUrl=https://YOUR-VERCEL-PROJECT.vercel.app
```
Leave this property empty to use the public Wikipedia Search API directly. Do not put API secrets in the app.

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
- Shows a review dialog before opening the target app. The user must tap Send inside that app; Misha does not silently send messages.
- Generic Sharesheet compatibility depends on whether an installed app accepts shared text. Not every app exposes a public API for direct-message automation.

## Phase 3 — Search and app actions
- `Search for <topic>`, `Search the web for <topic>`, and `Look up <topic>` run an online search and show article titles, snippets and source links inside Misha.
- The current no-key search provider is Wikipedia Search; this is an encyclopedia search, not full Google/Bing-style web search.
- `Open <app>`, `Launch <app>`, and `Start <app>` search installed launcher apps and launch a unique match. If multiple app labels match, Misha asks for a numbered choice.
- System shortcuts: `Open Settings`, `Open Camera`, `Open Phone`, and `Open Browser`.
- Flashlight voice controls: `Turn on the flashlight`, `Turn off the flashlight`, and `Torch status`.

## Later phases
Wake-word listening, direct phone calling, broader web search provider, screenshot capture, deeper app-specific integrations, visual UI integration and signed release builds remain for later phases. They will be developed only when requested phase by phase.

## Privacy and safety
Microphone, camera and contacts permissions are requested only when the relevant feature is used. Contacts are read on demand for matching and are not uploaded or saved by Misha. Message text is handed to the selected app only after the user reviews it. Android speech recognition may process audio according to the configured recognition provider.
