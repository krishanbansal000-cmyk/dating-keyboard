# DatingCopilot — AI Dating Coach Assistant

[![Platform](https://img.shields.io/badge/Android-34-brightgreen)](https://developer.android.com/about/versions/14)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

DatingCopilot is an AI-powered dating coach app, inspired by Smoothspeak, that helps you craft better replies in dating apps. Upload a screenshot or paste a conversation, pick your persona, and get AI-generated reply suggestions that match your vibe.

---

## ✨ Features

- 📸 **Screenshot Analysis** — Upload dating app screenshots; backend extracts conversation text via AI vision
- 📋 **Paste Text** — Manually paste a conversation and get instant AI suggestions
- 🎭 **Persona Selector** — 8 unique tones: Friendly, Romantic, Bold, Witty, Playful, Chill, Direct, Flirty
- 💬 **Chat Interface** — Beautiful conversation bubbles with smooth animations
- 📋 **One-Tap Copy** — Copy any suggestion to clipboard, paste into any dating app
- 🧑‍🤝‍🧑 **Onboarding Quiz** — Quick 3-question quiz to determine your dating persona
- 🌙 **Dark Purple Theme** — Modern Smoothspeak-inspired dark UI with violet accents
- 🔄 **Regenerate** — Switch personas anytime to get fresh suggestions
- 🎯 **Confidence Scores** — Each suggestion shows an AI confidence percentage
- ⚙️ **Profile Settings** — Save your profile, backend URL, and preferences

---

## 🎥 App Preview

| Onboarding | Chat Screen | AI Suggestions |
|---|---|---|
| Welcome quiz with persona assignment | Empty state with upload & persona chips | Suggestion cards with copy button |

---

## 🏗 Architecture

```
┌──────────────────────┐     ┌──────────────────────┐     ┌──────────────┐
│   Android App (Kotlin)│────►│  Python Flask Backend │────►│  OpenAI GPT-4 │
│                      │     │                      │     │              │
│  • Onboarding UI     │     │  • /health           │     │  • Text gen   │
│  • Chat bubbles      │     │  • /analyze-screenshot│     │  • Vision API │
│  • Suggestion cards  │     │  • /chat/draft       │     │  • Personas   │
│  • Persona selector  │     │  • /auth/github      │     └──────────────┘
│  • Image picker      │     │                      │
│  • Profile settings  │     │  📦 Mock mode built-in│
└──────────────────────┘     └──────────────────────┘
```

### Tech Stack

| Layer | Technology |
|---|---|
| **Frontend** | Kotlin, Android SDK 34, Material Design, OkHttp, Gson |
| **Backend** | Python 3.12+, Flask, Flask-CORS, OpenAI SDK |
| **AI** | GPT-4o-mini (text), GPT-4o Vision (screenshot OCR) |
| **Auth** | GitHub OAuth (optional) |
| **Build** | Gradle (Kotlin DSL) |

---

## 🚀 Quick Start

### Prerequisites

- Android Studio (or command-line build tools)
- JDK 17+
- Python 3.12+
- Android emulator or device
- OpenAI API key (optional — mock mode works without it)

### 1. Clone & Build

```bash
git clone https://github.com/krishanbansal000-cmyk/dating-keyboard.git
cd dating-keyboard

# Build Android APK
./gradlew assembleDebug
```

### 2. Start Backend

```bash
cd backend
pip install -r requirements.txt
cp .env.example .env   # Configure your OpenAI key (optional)
python run.py
```

Backend runs at `http://127.0.0.1:8000`

### 3. Install & Run

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.datingcopilot.keyboard/.chat.ChatActivity
```

---

## 🔧 Configuration

### Backend `.env`

```env
OPENAI_API_KEY=sk-your-key-here     # Get from https://platform.openai.com
FLASK_PORT=8000
USE_MOCK=true                        # true = fake suggestions, no API key needed
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
```

### Android App

Configure backend URL in app Settings (defaults to `http://10.0.2.2:8000` for emulator → localhost).

---

## 📱 Screenshots

### Onboarding
The 3-question quiz determines your dating persona:
1. How do you start conversations? 💬
2. What's your dating vibe? 🔥
3. How flirty do you want to be? 😏

### Chat Interface
- **Top bar**: App title + Settings ⚙️ + Upload ➕
- **Chat area**: Left-aligned (them) / Right-aligned (you) bubbles
- **AI Suggestions**: Horizontal scroll cards with copy button
- **Persona Chips**: Bottom bar with 8 selectable tones

### Features
- Paste conversation text for instant AI analysis
- Upload screenshots for automatic OCR extraction
- Switch personas to regenerate suggestions
- Copy suggestions with one tap

---

## 📁 Project Structure

```
dating-keyboard/
├── app/
│   ├── src/main/
│   │   ├── java/com/datingcopilot/keyboard/
│   │   │   ├── chat/            # Chat UI, adapters, message models
│   │   │   ├── onboarding/      # Quiz flow
│   │   │   ├── profile/         # User profile settings
│   │   │   ├── image/           # Image picker bottom sheet
│   │   │   ├── ApiClient.kt     # Backend API client
│   │   │   ├── SettingsActivity.kt  # Legacy settings
│   │   │   ├── DatingKeyboardService.kt  # Keyboard IME (disabled)
│   │   │   ├── SuggestionBar.kt # Keyboard suggestion bar (disabled)
│   │   │   └── ToneSelector.kt  # Keyboard tone selector (disabled)
│   │   └── res/
│   │       ├── values/          # Colors, themes, strings
│   │       └── xml/             # Keyboard layout (disabled)
│   └── build.gradle.kts
├── backend/
│   ├── app.py                   # Flask server + all API endpoints
│   ├── run.py                   # Entry point
│   ├── requirements.txt         # Python dependencies
│   └── start_backend.bat        # Windows launcher
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew / gradlew.bat
└── README.md
```

---

## 🔐 GitHub Authentication

DatingCopilot supports GitHub OAuth login (replaces email/password):

1. Register an OAuth App at https://github.com/settings/developers
2. Set Authorization callback URL to `http://localhost:8000/api/v1/auth/github/callback`
3. Add your `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` to `.env`

**API Endpoints:**

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/auth/github` | Redirects to GitHub OAuth login |
| `GET` | `/api/v1/auth/github/callback` | OAuth callback, returns JWT token |
| `GET` | `/api/v1/auth/me` | Get current user profile from token |

---

## 📡 API Reference

### Health Check
```
GET /health
→ {"status": "ok", "service": "DatingCopilot AI Backend", "mode": "mock|openai"}
```

### Analyze Screenshot
```
POST /api/v1/analyze-screenshot
Content-Type: multipart/form-data
Body: image=<file>, persona=<string>, user_id=<string>

→ {
    "conversation": [{"sender": "them|you", "text": "..."}],
    "suggestions": [{"text": "...", "confidence": 95, "persona": "playful"}],
    "detected_app": "Tinder"
  }
```

### Text Suggestions
```
POST /api/v1/chat/draft
Content-Type: application/json
Body: {"tone": "playful", "conversation": [...], "my_profile": {...}, "their_profile": {...}}

→ {"options": [{"text": "...", "confidence": 95, "tone": "playful"}]}
```

---

## 🧪 Testing

### Mock Mode (no API key needed)

Set `USE_MOCK=true` in `.env`. Backend returns curated suggestion templates:

| Persona | Example Suggestion |
|---|---|
| Playful | "Oh you're dangerous 😏 I like it." |
| Romantic | "You have a way of making my heart skip a beat." |
| Bold | "Let's skip the small talk. When are you free?" |
| Witty | "Are you a magician? Because you've made the boring stuff disappear!" |

### Automated Testing

```bash
# Backend health
curl http://127.0.0.1:8000/health

# Text suggestions
curl -X POST http://127.0.0.1:8000/api/v1/chat/draft \
  -H "Content-Type: application/json" \
  -d '{"tone":"playful","conversation":[{"sender":"them","text":"Hey!"}]}'
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing`)
5. Open a Pull Request

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

## 🙏 Acknowledgments

- [Smoothspeak](https://smoothspeak.ai) — Design inspiration
- [OpenAI](https://openai.com) — AI models for suggestions and vision
- [Google ML Kit](https://developers.google.com/ml-kit) — Reference for OCR

---

## 🛣 Roadmap

- [x] Onboarding quiz with persona assignment
- [x] Text-based AI suggestions
- [x] Persona selector (8 tones)
- [x] Screenshot upload (backend ready)
- [x] GitHub authentication
- [ ] Conversation history (SQLite)
- [ ] Multiple conversation threads
- [ ] Cloud backend deployment script
- [ ] Google Play Store release
- [ ] iOS version (Flutter/React Native)
