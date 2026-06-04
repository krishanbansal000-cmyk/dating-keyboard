# DatingCopilot Keyboard — Plan

## What We're Building
A native Android keyboard (IME) that shows up as a system keyboard option. When the user types in Tinder/Bumble/Hinge, it reads the conversation context, sends it to the DatingCopilot AI backend, and shows smart reply suggestions above the keyboard keys.

## Architecture
```
User types in Tinder
     ↓
Android system shows DatingCopilot Keyboard (IME)
     ↓
Keyboard detects conversation context via:
  1. Text in the active input field
  2. Clipboard (manual share)
     ↓
Keyboard calls our FastAPI backend at POST /api/v1/chat/draft
     ↓
Backend returns 3 AI-generated reply options
     ↓
Keyboard shows suggestions in a strip above the QWERTY layout
     ↓
User taps a suggestion → it's typed into Tinder automatically
     ↓
No app switching, no copy-paste
```

## Project Structure
```
dating-keyboard/
├── app/src/main/java/com/datingcopilot/keyboard/
│   ├── DatingKeyboardService.kt   — Main IME (InputMethodService)
│   ├── KeyboardUI.kt              — Keyboard layout + keys
│   ├── SuggestionBar.kt           — AI suggestion strip
│   ├── ToneSelector.kt            — Tone chips (Playful/Warm/etc)
│   ├── ApiClient.kt               — HTTP client to backend
│   ├── ConversationCache.kt       — SQLite cache per match
│   └── SettingsActivity.kt        — API key / backend URL config
├── app/src/main/
│   ├── AndroidManifest.xml        — IME declaration
│   ├── res/layout/
│   │   ├── keyboard_view.xml      — Main keyboard layout
│   │   └── suggestion_item.xml    — Single suggestion card
│   ├── res/xml/
│   │   └── method.xml             — IME metadata
│   └── res/values/strings.xml
├── build.gradle.kts               — App-level build
├── build.gradle.kts (root)        — Project-level build
├── settings.gradle.kts
└── gradle.properties
```

## Build Order
1. Project scaffolding (gradle, manifest, resources)
2. DatingKeyboardService.kt — core IME lifecycle
3. KeyboardUI.kt — basic QWERTY keyboard
4. SuggestionBar.kt — AI reply strip above keys
5. ToneSelector.kt — tone chips inside suggestion bar
6. ApiClient.kt — HTTP calls to backend
7. ConversationCache.kt — local storage
8. SettingsActivity.kt — config screen

## Backend API (already built)
- POST /api/v1/chat/draft — takes conversation + tone → returns 3 options
- POST /api/v1/auth/login — for auth token

## Android Permissions
- No special permissions needed for IME
- INTERNET for API calls
