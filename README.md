# DatingCopilot Keyboard (Android IME)

A native Android keyboard that provides AI-powered reply suggestions for dating apps. When you type in Tinder, Bumble, or Hinge, the keyboard analyzes the conversation and suggests smart replies above the keys.

## Architecture

```
┌─────────────────────────────────────────────┐
│  SuggestionBar (AI reply cards)             │
│  [😄 Playful] [😊 Warm] [🎯 Direct] ...    │ ← ToneSelector
├─────────────────────────────────────────────┤
│                                              │
│   q  w  e  r  t  y  u  i  o  p             │
│    a  s  d  f  g  h  j  k  l               │
│     z  x  c  v  b  n  m  ⌫                │
│   ?123  ,  ████████████  .  ↵              │
└─────────────────────────────────────────────┘
```

### Components

| File | Purpose |
|------|---------|
| `DatingKeyboardService.kt` | Main IME — handles input lifecycle, key presses, triggers AI |
| `SuggestionBar.kt` | Horizontal scrollable strip of AI reply cards above keyboard |
| `ToneSelector.kt` | Tone chips (Playful, Warm, Direct, Flirty, Witty) |
| `ApiClient.kt` | HTTP client to DatingCopilot backend |
| `ConversationCache.kt` | SQLite cache for conversation history per match |
| `SettingsActivity.kt` | Settings screen — backend URL, login |

## How It Works

1. User enables DatingCopilot in Android Settings → System → Languages & input → On-screen keyboard
2. User opens Tinder/Bumble/Hinge and taps a text field
3. Keyboard appears with AI suggestion strip above the QWERTY layout
4. User types a message → keyboard automatically fetches 3 AI reply options
5. User taps a suggestion → it's typed directly into the dating app's text field
6. User can switch tone with chips above the keyboard

## Build

Open in Android Studio:

```bash
cd dating-keyboard
```

Then File → Open → select the `dating-keyboard` folder. Sync Gradle and run on device/emulator.

Or build APK from command line:
```bash
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## Backend

The keyboard connects to your existing DatingCopilot FastAPI backend:

- `POST /api/v1/chat/draft` — returns AI reply options
- `POST /api/v1/auth/login` — for authentication

Configure the backend URL in Settings (default: `http://10.0.2.2:8000` for emulator → host).

## Setup on Device

1. Build and install the APK
2. Go to Android Settings → System → Languages & input → On-screen keyboard → Manage keyboards
3. Enable "DatingCopilot"
4. Open Tinder, tap a text field → switch keyboard to DatingCopilot
5. Log in with your DatingCopilot account in the Settings activity
