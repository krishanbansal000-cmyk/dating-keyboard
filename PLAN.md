# RizzSe Keyboard Plan

## Current Goal

Build a native Android IME and companion app that generates dating-message suggestions from pasted text, screenshots, and typed input.

## Flow

```text
Dating app chat
  -> RizzSe keyboard or chat screen
  -> Flask backend /api/v1/chat/draft or /api/v1/analyze-screenshot
  -> 3 short reply suggestions
  -> user taps/copies a suggestion
```

## Core Files

- `DatingKeyboardService.kt`: custom keyboard and suggestion insertion
- `ChatContextService.kt`: saved context/platform preference helper
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

- User pastes a message, uploads a screenshot, or leaves input empty for first-message ideas
- App defaults platform silently and avoids sensitive screen-reading permissions
- Backend generates three short Indian-friendly suggestions: Safe, Smooth, and Bold
- User refines after generation with simple actions like more rizz, less cringe, Hinglish, or ask out

## Next Improvements

- Add HTTPS production backend configuration
- Add better screenshot conversation extraction when using vision-capable models
- Add OCR-first screenshot extraction for faster image replies
- Add release build signing and Play Store hardening
- Add reply refinement actions like shorter, less cheesy, more Hinglish, and more respectful
