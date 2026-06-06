# RizzSe Keyboard Plan

## Current Goal

Build a native Android IME and companion app that generates dating-message suggestions from pasted text, screenshots, typed input, and optional visible chat context.

## Flow

```text
Dating app chat
  -> optional accessibility context capture
  -> RizzSe keyboard or chat screen
  -> Flask backend /api/v1/chat/draft or /api/v1/analyze-screenshot
  -> 3 short reply suggestions
  -> user taps/copies a suggestion
```

## Core Files

- `DatingKeyboardService.kt`: custom keyboard and suggestion insertion
- `ChatContextService.kt`: optional visible chat context capture
- `SuggestionBar.kt`: keyboard suggestion UI
- `ChatActivity.kt`: standalone chat/upload/paste UI
- `SettingsSheet.kt`: backend URL and user preference settings
- `ApiClient.kt`: Android API boundary
- `backend/app.py`: Flask API and model/mock suggestion generation

## Backend API

- `GET /health`: status and model readiness
- `POST /api/v1/chat/draft`: text conversation to reply options
- `POST /api/v1/analyze-screenshot`: image upload to screenshot-based suggestions

## Product Flow

- User selects platform: WhatsApp, Instagram, Hinge, Bumble, or Tinder
- User selects intent: keep going, flirt, ask date, recover dry chat, first message, or reply to compliment
- User selects vibe: Sweet, Romantic, Confident, Funny, Playful, Respectful, Date Plan, or Flirty
- User can paste a message, upload a screenshot, or leave input empty for first-message ideas
- Backend generates three short Indian-friendly suggestions

## Next Improvements

- Add HTTPS production backend configuration
- Add better screenshot conversation extraction when using vision-capable models
- Add user-visible accessibility setup guidance
- Add release build signing and Play Store hardening
- Add reply refinement actions like shorter, less cheesy, more Hinglish, and more respectful
