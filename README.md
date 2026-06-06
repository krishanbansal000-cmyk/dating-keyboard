# RizzSe — AI Dating Coach Keyboard

RizzSe is an Android dating-assistant app and keyboard. It can analyze pasted text or screenshots, generate persona-based reply suggestions, and optionally use an accessibility service to bring visible chat context into the keyboard flow.

## Features

- Screenshot analysis via `/api/v1/analyze-screenshot`
- Pasted conversation analysis via `/api/v1/chat/draft`
- Eight tones: friendly, romantic, bold, witty, playful, chill, direct, flirty
- Android chat screen with upload, paste, copy, persona switching, and Hinglish mode
- India-focused platform, intent, and vibe chips for WhatsApp, Instagram, Hinge, Bumble, and Tinder
- Optional empty-input generation for first-message ideas
- Keyboard IME with an AI suggestion strip
- Optional accessibility service for visible chat context
- Mock backend mode for running without an API key

## Architecture

```text
Android app / IME -> Flask backend -> OpenAI-compatible or Anthropic-compatible model
```

The Android app defaults to `http://10.0.2.2:8000`, which maps an emulator to the local backend. Change the backend URL from the app settings sheet for a physical device or deployed backend.

## Requirements

- JDK 17+
- Android SDK / Android Studio
- Python 3.12+
- OpenAI-compatible API key only when `USE_MOCK=false`

## Quick Start

```bash
./gradlew assembleDebug
```

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
python run.py
```

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.datingcopilot.keyboard/.chat.ChatActivity
```

## Backend Config

```env
OPENAI_API_KEY=
OPENAI_BASE_URL=https://opencode.ai/zen/go/v1
OPENAI_MODEL=mimo-v2.5
FLASK_PORT=8000
USE_MOCK=true
FLASK_SECRET_KEY=change-this-to-a-random-secret-in-production
```

Set `USE_MOCK=true` to return built-in suggestions without a model call. Set it to `false` and provide an API key to use the configured model.

## API

`GET /health`

Returns service status, model, mock mode, and client readiness.

`POST /api/v1/chat/draft`

```json
{
  "tone": "playful",
  "intent": "ask_date",
  "platform": "whatsapp",
  "hinglish": "false",
  "conversation": [
    {"sender": "them", "text": "Hey, how was your day?"}
  ]
}
```

Supported intents: `keep_going`, `flirt`, `ask_date`, `recover_dry`, `first_message`, `reply_compliment`.

Supported platforms: `whatsapp`, `instagram`, `hinge`, `bumble`, `tinder`.

`POST /api/v1/analyze-screenshot`

Multipart form with `image`, `persona`, optional `intent`, optional `platform`, optional `hinglish`, and optional `user_gender`.

## Project Structure

```text
app/src/main/java/com/datingcopilot/keyboard/
  ApiClient.kt
  ChatContextService.kt
  DatingKeyboardService.kt
  SettingsSheet.kt
  chat/
  image/
  onboarding/
backend/
  app.py
  run.py
  requirements.txt
```

## Security Notes

- Do not commit real API keys.
- Production backend URLs should use HTTPS.
- The accessibility service must be enabled by the user and should only be used for contextual suggestions.
