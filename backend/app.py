import secrets
import sys
from flask import Flask, request, jsonify, redirect
from flask_cors import CORS
from werkzeug.utils import secure_filename
from PIL import Image
import io
import os
import base64
import json
import re
import random
import traceback
import time
from concurrent.futures import ThreadPoolExecutor, TimeoutError
import requests as http_requests
from dotenv import load_dotenv

load_dotenv()

app = Flask(__name__)
app.secret_key = os.getenv("FLASK_SECRET_KEY", secrets.token_hex(32))
CORS(app, supports_credentials=True)

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
API_ENDPOINT = os.getenv("OPENAI_BASE_URL", "https://opencode.ai/zen/go/v1")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "mimo-v2.5")
AI_PROVIDER = os.getenv("AI_PROVIDER", "opencode").lower()
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", os.getenv("GOOGLE_API_KEY", ""))
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-3.1-flash-lite")
USE_MOCK = os.getenv("USE_MOCK", "false").lower() in {"1", "true", "yes", "on"}
AI_TIMEOUT_SECONDS = float(os.getenv("AI_TIMEOUT_SECONDS", "12"))
AI_MAX_WORKERS = int(os.getenv("AI_MAX_WORKERS", "16"))
ai_executor = ThreadPoolExecutor(max_workers=AI_MAX_WORKERS)

# Anthropic-compatible models (use Messages API endpoint)
ANTHROPIC_MODELS = {"qwen3.7-plus", "qwen3.7-max", "qwen3.6-plus", "minimax-m3", "minimax-m2.7", "minimax-m2.5"}

# Initialize AI client
openai_client = None
anthropic_client = None
if OPENAI_API_KEY:
    if OPENAI_MODEL in ANTHROPIC_MODELS:
        # Use Anthropic Messages API for Qwen/MiniMax models
        import anthropic
        anthropic_client = anthropic.Anthropic(
            api_key=OPENAI_API_KEY,
            base_url=API_ENDPOINT  # https://opencode.ai/zen/go/v1
        )
    else:
        # Use OpenAI-compatible API
        from openai import OpenAI
        import httpx
        http_client = httpx.Client(timeout=httpx.Timeout(120.0, connect=30.0))
        client_kwargs = {"api_key": OPENAI_API_KEY, "http_client": http_client}
        if API_ENDPOINT:
            client_kwargs["base_url"] = API_ENDPOINT
        openai_client = OpenAI(**client_kwargs)

if AI_PROVIDER == "gemini":
    OPENAI_MODEL = GEMINI_MODEL

UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
app.config["MAX_CONTENT_LENGTH"] = 10 * 1024 * 1024  # 10MB max

SYSTEM_PROMPT = (
    "Return exactly 3 lines: >>> Safe:, >>> Smooth:, >>> Bold:. Under 100 chars each. No extra text. "
    "Be respectful, context-aware, and never insult, neg, pressure, or dismiss them."
)

PERSONA_PROMPTS = {
    "friendly": "Friendly: warm, calm, easy to reply to. No over-complimenting.",
    "romantic": "Romantic: sweet but grounded. No poetry, no big promises.",
    "bold": "Confident: direct and self-assured, but never arrogant or pushy.",
    "witty": "Funny: dry, clever, lightly teasing. Modern meme-brain, no uncle jokes or puns.",
    "playful": "Playful: bold Gen-Z rizz, teasing, cheeky, high-energy, but still self-aware.",
    "chill": "Respectful: low-pressure, mature, and natural.",
    "direct": "Date Plan: move toward a simple plan without sounding desperate.",
    "flirty": "Flirty: subtle tension, confident charm, classy. Never vulgar."
}

INTENT_PROMPTS = {
    "keep_going": "Keep the conversation moving naturally. Ask or say something easy to reply to.",
    "flirt": "Add light flirtation and chemistry. Avoid being vulgar or too intense.",
    "ask_date": "Move toward a simple date plan like coffee, walk, food, or weekend plan.",
    "recover_dry": "Recover a dry or low-effort chat with playful, low-pressure energy.",
    "first_message": "Write a strong first message that feels personal and not generic.",
    "reply_compliment": "Reply to a compliment with charm, confidence, and warmth."
}

PLATFORM_PROMPTS = {
    "whatsapp": "WhatsApp style: natural, familiar, not too try-hard.",
    "instagram": "Instagram style: casual, photo/story-friendly, playful.",
    "hinge": "Hinge style: thoughtful, profile-aware, slightly witty.",
    "bumble": "Bumble style: warm, respectful, confident.",
    "tinder": "Tinder style: playful, crisp, a little bold."
}

STYLE_RULES = (
    "Indian Gen-Z style. Reply to the latest message. Safe=low-risk, Smooth=witty/flirty, Bold=high-risk rizz. "
    "Stay respectful, match their energy, and avoid insults, negging, pressure, vulgarity, and generic templates."
)

OPENER_PROMPTS = {
    "friendly": "Smooth and approachable. Warm opener that feels natural.",
    "romantic": "Soft sweet charm. Shows genuine interest from the start.",
    "bold": "Confident and direct. Stands out, shows swagger, takes the lead.",
    "witty": "Clever and funny. Wordplay or teasing angle that makes them smile.",
    "playful": "Bursting with rizz. Cheeky, flirty, fun. Gets attention immediately.",
    "chill": "Cool and effortless. Low-pressure, smooth without trying hard.",
    "direct": "No-nonsense. Gets to the point, sets up a date with confidence.",
    "flirty": "Seductive and smooth. Subtle tension and classy charm."
}

MOCK_SUGGESTIONS = {
    "friendly": [
        "Okay, that smile deserves a better opener than mine",
        "You seem fun in a way that makes replies easy",
        "I'll keep it simple: what's been the best part of your week?"
    ],
    "romantic": [
        "There's something soft about your vibe, not gonna lie",
        "You make calm look very attractive",
        "I was trying to be smooth, but genuine might work better here"
    ],
    "bold": [
        "You're fun. Coffee this weekend, no overthinking?",
        "I like this energy. Let's take it offline sometime",
        "Fair warning: I'm better in person than in openers"
    ],
    "witty": [
        "I can quit anytime, but you're making a strong case",
        "Only if you let me recover with a better answer",
        "Not quitting, just saving my best reply for you"
    ],
    "playful": [
        "Not when the conversation is this fun",
        "I can quit, but I like where this is going",
        "Only if you stop making this so easy to enjoy"
    ],
    "chill": [
        "Fair, I'll slow down. Still enjoying this though",
        "Okay okay, I'll behave. Mostly",
        "Noted. I'll keep the confidence, reduce the drama"
    ],
    "direct": [
        "Then let me prove the vibe over coffee",
        "One coffee and I'll keep the charm simple",
        "Give me one plan and I'll bring the good energy"
    ],
    "flirty": [
        "Only because you're making quitting difficult",
        "I would quit, but your replies are a little addictive",
        "Careful, that challenge almost sounded like encouragement"
    ]
}

MOCK_CONVERSATIONS = {
    "bumble": [
        {"sender": "them", "text": "Hey! I love your profile — especially that travel photo. Where was that taken?"},
        {"sender": "you", "text": "Thanks! That was in Bali last summer. Best sunset I've ever seen."},
        {"sender": "them", "text": "Bali is on my bucket list! What's your favorite thing you did there?"},
        {"sender": "them", "text": "Also — you seem like someone who actually reads profiles, so respect ✊"}
    ],
    "hinge": [
        {"sender": "them", "text": "Your prompt about 'simple pleasures' got me — mine is definitely fresh coffee on a rainy morning."},
        {"sender": "you", "text": "Okay that's actually perfect. Rain + coffee + a good book = unbeatable combo."},
        {"sender": "them", "text": "We might need to test this theory together sometime. Do you have a go-to coffee spot?"}
    ],
    "tinder": [
        {"sender": "them", "text": "Hey! How's your week going?"},
        {"sender": "them", "text": "I've been thinking we should grab coffee sometime"},
        {"sender": "you", "text": "That sounds nice! When are you free?"}
    ]
}


def encode_image(image_file, max_side=1200, quality=72):
    """Encode a compact JPEG for faster vision requests."""
    image = Image.open(image_file)
    if image.mode in ('RGBA', 'LA', 'P'):
        image = image.convert('RGB')
    longest_side = max(image.size)
    if longest_side > max_side:
        scale = max_side / longest_side
        image = image.resize(
            (max(1, int(image.width * scale)), max(1, int(image.height * scale))),
            Image.Resampling.LANCZOS
        )
    buffered = io.BytesIO()
    image.save(buffered, format="JPEG", quality=quality, optimize=True)
    return base64.b64encode(buffered.getvalue()).decode('utf-8')


def encode_chat_screenshot(image_file, max_width=1100, quality=74):
    """Encode chat screenshots without shrinking tall captures into unreadable thumbnails."""
    image = Image.open(image_file)
    if image.mode in ('RGBA', 'LA', 'P'):
        image = image.convert('RGB')
    if image.width > max_width:
        scale = max_width / image.width
        image = image.resize(
            (max(1, int(image.width * scale)), max(1, int(image.height * scale))),
            Image.Resampling.LANCZOS
        )
    buffered = io.BytesIO()
    image.save(buffered, format="JPEG", quality=quality, optimize=True)
    return base64.b64encode(buffered.getvalue()).decode('utf-8')


def image_debug_info(image_file):
    pos = image_file.tell()
    image_file.seek(0)
    image = Image.open(image_file)
    info = f"{image.size[0]}x{image.size[1]} {image.mode}"
    image_file.seek(pos)
    return info


def get_mock_suggestions(persona, count=3):
    """Return mock suggestions for the given persona."""
    suggestions = MOCK_SUGGESTIONS.get(persona, MOCK_SUGGESTIONS["playful"])
    result = []
    for s in suggestions[:count]:
        confidence = random.randint(78, 99)
        result.append({"text": s, "confidence": confidence, "persona": persona})
    random.shuffle(result)
    return result


SKIP_WORDS = (
    'explanation', 'thinking', 'labels', 'rules', 'output', 'no explanation',
    'just the', 'copy-paste', 'characters', 'short, smooth', 'for each',
    'each line', 'line must start', 'first rizz', 'second rizz', 'third rizz',
    'nothing else', 'rizz line 1', 'rizz line 2', 'rizz line 3'
)

BAD_ENDINGS = {
    "a", "an", "the", "to", "with", "for", "from", "when", "while", "because",
    "giving", "making", "looking", "getting", "going", "saying"
}


def get_response_text(response):
    if isinstance(response, dict):
        return (response.get("text") or "").strip()
    if hasattr(response, 'choices'):
        return (response.choices[0].message.content or "").strip()
    if getattr(response, "content", None):
        return (getattr(response.content[0], "text", "") or "").strip()
    return ""


def gemini_parts_from_content(content):
    if isinstance(content, str):
        return [{"text": content}]

    parts = []
    for item in content:
        item_type = item.get("type") if isinstance(item, dict) else None
        if item_type == "text":
            parts.append({"text": item.get("text", "")})
        elif item_type == "image_url":
            url = item.get("image_url", {}).get("url", "")
            if "," in url:
                parts.append({
                    "inline_data": {
                        "mime_type": "image/jpeg",
                        "data": url.split(",", 1)[1]
                    }
                })
        elif item_type == "image":
            source = item.get("source", {})
            data = source.get("data", "")
            if data:
                parts.append({
                    "inline_data": {
                        "mime_type": source.get("media_type", "image/jpeg"),
                        "data": data
                    }
                })
    return parts


def call_gemini(messages, system_prompt=SYSTEM_PROMPT, max_tokens=120, temperature=0.25):
    if not GEMINI_API_KEY:
        raise RuntimeError("No Gemini API key configured. Check GEMINI_API_KEY in .env")

    contents = []
    for message in messages:
        role = "model" if message.get("role") == "assistant" else "user"
        contents.append({"role": role, "parts": gemini_parts_from_content(message.get("content", ""))})

    url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent"
    payload = {
        "system_instruction": {"parts": [{"text": system_prompt}]},
        "contents": contents,
        "generationConfig": {
            "temperature": temperature,
            "maxOutputTokens": max_tokens,
            "candidateCount": 1
        }
    }
    response = http_requests.post(url, params={"key": GEMINI_API_KEY}, json=payload, timeout=45)
    if not response.ok:
        raise RuntimeError(f"Gemini failed: HTTP {response.status_code} - {response.text[:500]}")
    data = response.json()
    parts = data.get("candidates", [{}])[0].get("content", {}).get("parts", [])
    text = "".join(part.get("text", "") for part in parts).strip()
    return {"text": text}


def parse_suggestions(text, persona, key="persona", min_confidence=78, max_confidence=98):
    """Parse model output into app-ready suggestion objects."""
    if not text:
        return []
    clean_text = re.sub(r'^\d+[\.\)]\s*', '', text, flags=re.MULTILINE)
    first_arrow = clean_text.find('>>>')
    if first_arrow > 0:
        clean_text = clean_text[first_arrow:]

    suggestions = []
    for raw_line in clean_text.split('\n'):
        line = raw_line.strip()
        if not line.startswith('>>>'):
            continue
        value = re.sub(r'^>>>\s*', '', line).strip()
        label = persona
        label_match = re.match(r'^(safe|smooth|bold)\s*:\s*(.+)$', value, flags=re.IGNORECASE)
        if label_match:
            label = label_match.group(1).lower()
            value = label_match.group(2).strip()
        last_word = re.sub(r'[^a-zA-Z]', '', value.split()[-1]).lower() if value.split() else ""
        if len(value) <= 10 or last_word in BAD_ENDINGS or any(word in value.lower() for word in SKIP_WORDS):
            continue
        suggestions.append({
            "text": value[:180],
            "confidence": random.randint(min_confidence, max_confidence),
            key: label
        })
        if len(suggestions) == 3:
            break

    if not suggestions:
        for raw_line in clean_text.split('\n'):
            value = re.sub(r'^(?:[-*]|\d+[\.\)]|>>>)+\s*', '', raw_line.strip()).strip('"')
            label = persona
            label_match = re.match(r'^(safe|smooth|bold)\s*:\s*(.+)$', value, flags=re.IGNORECASE)
            if label_match:
                label = label_match.group(1).lower()
                value = label_match.group(2).strip()
            last_word = re.sub(r'[^a-zA-Z]', '', value.split()[-1]).lower() if value.split() else ""
            if len(value) <= 10 or last_word in BAD_ENDINGS or any(word in value.lower() for word in SKIP_WORDS):
                continue
            suggestions.append({
                "text": value[:180],
                "confidence": random.randint(min_confidence, max_confidence),
                key: label
            })
            if len(suggestions) == 3:
                break
    return suggestions


def fallback_suggestions(persona, intent="keep_going", key="persona", hinglish=False, latest_text=""):
    latest = latest_text.lower()
    if "first step to what" in latest:
        lines = [
            ("safe", "To me suggesting coffee without overthinking it"),
            ("smooth", "To me turning this into a good plan"),
            ("bold", "To us making this conversation even better")
        ]
    elif "getting way ahead" in latest or "way ahead of yourself" in latest:
        lines = [
            ("safe", "Fine, I'll downgrade us to one coffee for now"),
            ("smooth", "Fair, I'll slow down and enjoy this properly"),
            ("bold", "Noted. I'll keep it charming and present")
        ]
    elif "don't quit" in latest or "dont quit" in latest:
        lines = [
            ("safe", "Not when this conversation is this fun"),
            ("smooth", "I quit bad conversations, not good ones"),
            ("bold", "Only after I earn one more smile")
        ]
    elif intent == "first_message":
        if hinglish:
            lines = [
                ("safe", "Quick vibe check: chai, coffee, ya street food date?"),
                ("smooth", "Weekend pe coffee person ho ya pani puri person?"),
                ("bold", "Pick one: long walk, good gossip, ya ek solid coffee?")
            ]
        else:
            lines = [
                ("safe", "Quick vibe check: street food date or fancy coffee date?"),
                ("smooth", "Important question: chai, coffee, or judging people who say neither?"),
                ("bold", "Pick one: street food, coffee, or a walk with good gossip?")
            ]
    elif intent == "ask_date":
        lines = [
            ("safe", "Fine, I'll pause the flirting. Coffee instead?"),
            ("smooth", "One coffee and I'll retire the dramatic lines"),
            ("bold", "Let me prove the vibe in person")
        ]
    else:
        raw_lines = MOCK_SUGGESTIONS.get(persona, MOCK_SUGGESTIONS["playful"])
        lines = list(zip(("safe", "smooth", "bold"), raw_lines))
    return [
        {"text": text, "confidence": random.randint(78, 96), key: label}
        for label, text in lines[:3]
    ]


def complete_suggestions(suggestions, persona, intent="keep_going", key="persona", hinglish=False, latest_text=""):
    suggestions = suggestions or []
    seen = {s.get("text", "").lower() for s in suggestions}
    for fallback in fallback_suggestions(persona, intent, key=key, hinglish=hinglish, latest_text=latest_text):
        if len(suggestions) >= 3:
            break
        if fallback["text"].lower() not in seen:
            suggestions.append(fallback)
            seen.add(fallback["text"].lower())
    return suggestions[:3]


def has_fast_fallback(latest_text, intent):
    latest = latest_text.lower()
    return (
        intent in {"first_message", "ask_date"} or
        "first step to what" in latest or
        "getting way ahead" in latest or
        "way ahead of yourself" in latest or
        "don't quit" in latest or
        "dont quit" in latest
    )


def mock_options(persona, key="persona"):
    return [
        {"text": item["text"], "confidence": item["confidence"], key: persona}
        for item in get_mock_suggestions(persona)
    ]


def build_context_prompt(persona, intent="keep_going", platform="whatsapp", hinglish=False):
    persona_prompt = PERSONA_PROMPTS.get(persona, PERSONA_PROMPTS["playful"])
    intent_prompt = INTENT_PROMPTS.get(intent, INTENT_PROMPTS["keep_going"])
    platform_prompt = PLATFORM_PROMPTS.get(platform, PLATFORM_PROMPTS["whatsapp"])
    lang_hint = "Match the user's language from the text or screenshot; use natural Hinglish only when the chat does."
    return f"{STYLE_RULES} {platform_prompt} {intent_prompt} {persona_prompt} {lang_hint}"


def truthy(value):
    return str(value).lower() in {"1", "true", "yes", "on"}


def client_ready():
    if USE_MOCK:
        return False
    if AI_PROVIDER == "gemini":
        return GEMINI_API_KEY != ""
    return openai_client is not None or anthropic_client is not None


def extract_json_object(text):
    if not text:
        return None
    clean = text.strip()
    if clean.startswith("```"):
        clean = re.sub(r"^```(?:json)?\s*", "", clean, flags=re.IGNORECASE).strip()
        clean = re.sub(r"\s*```$", "", clean).strip()
    start = clean.find("{")
    end = clean.rfind("}")
    if start == -1 or end == -1 or end <= start:
        return None
    try:
        parsed = json.loads(clean[start:end + 1])
        return parsed if isinstance(parsed, dict) else None
    except Exception:
        return None


def sanitize_insights(parsed):
    parsed = parsed or {}
    def string_value(key, limit=180):
        value = parsed.get(key, "")
        return str(value).strip()[:limit] if value is not None else ""
    def list_value(key, limit=4):
        value = parsed.get(key, [])
        if isinstance(value, str):
            value = [value]
        if not isinstance(value, list):
            return []
        return [str(item).strip()[:180] for item in value if str(item).strip()][:limit]
    try:
        score = int(float(parsed.get("conversation_score", 0)))
    except Exception:
        score = 0
    score = max(0, min(100, score))
    return {
        "her_energy": string_value("her_energy", 120),
        "conversation_score": score,
        "comments": list_value("comments", 4),
        "green_flags": list_value("green_flags", 3),
        "red_flags": list_value("red_flags", 3),
        "next_move": string_value("next_move", 180)
    }


def call_ai_for_task(messages, system_prompt, model=None, max_tokens=280, temperature=0.35, timeout_seconds=30):
    model_name = model or OPENAI_MODEL
    def invoke():
        if AI_PROVIDER == "gemini":
            return call_gemini(messages, system_prompt=system_prompt, max_tokens=max_tokens, temperature=temperature)
        if model_name in ANTHROPIC_MODELS and anthropic_client is not None:
            return anthropic_client.messages.create(
                model=model_name,
                max_tokens=max_tokens,
                temperature=temperature,
                system=system_prompt,
                messages=messages
            )
        if openai_client is not None:
            return openai_client.chat.completions.create(
                model=model_name,
                messages=[{"role": "system", "content": system_prompt}] + messages,
                max_tokens=max_tokens,
                temperature=temperature
            )
        raise RuntimeError("No AI client configured. Check OPENAI_API_KEY in .env")

    future = ai_executor.submit(invoke)
    try:
        return future.result(timeout=timeout_seconds), None
    except TimeoutError:
        return None, f"AI call timed out after {timeout_seconds:.1f}s"
    except Exception as e:
        return None, str(e)


def generate_conversation_insights(chat_context="", encoded_images=None, persona="playful", intent="keep_going", platform="whatsapp", hinglish=False):
    encoded_images = encoded_images or []
    if not client_ready():
        return {}, "No AI client"

    context_prompt = build_context_prompt(persona, intent, platform, hinglish)
    prompt = f"""Analyze the dating chat for the user. Be concise, specific, and respectful. {context_prompt}

Return ONLY valid JSON with exactly these keys:
{{
  "her_energy": "short read on her vibe/interest level",
  "conversation_score": 0-100,
  "comments": ["2-4 brief comments on the conversation"],
  "green_flags": ["signals that help"],
  "red_flags": ["risks or weak signals; empty if none"],
  "next_move": "one tactical recommendation"
}}

Chat text context:
{chat_context[:1200] if chat_context else "Use the screenshot(s) only."}"""

    is_anthropic = OPENAI_MODEL in ANTHROPIC_MODELS
    contents = [{"type": "text", "text": prompt}]
    for enc in encoded_images[:5]:
        if is_anthropic:
            contents.append({"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": enc}})
        else:
            contents.append({"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{enc}", "detail": "low"}})

    response, error = call_ai_for_task(
        [{"role": "user", "content": contents}],
        system_prompt="You are RizzSe, a dating conversation analyst. Return strict JSON only. Never invent private facts.",
        model=OPENAI_MODEL,
        max_tokens=420,
        temperature=0.35,
        timeout_seconds=35
    )
    if error:
        return {}, error
    parsed = extract_json_object(get_response_text(response))
    if not parsed:
        return {}, "Could not parse insights JSON"
    return sanitize_insights(parsed), None


def call_ai(messages, model=None, max_tokens=90, temperature=0.5):
    """Call AI model. Raises on failure — no mock fallback."""
    model_name = model or OPENAI_MODEL

    if AI_PROVIDER == "gemini":
        return call_gemini(messages, system_prompt=SYSTEM_PROMPT, max_tokens=max_tokens, temperature=temperature)
    
    # Inject system prompt to enforce format
    full_messages = [{"role": "system", "content": SYSTEM_PROMPT}] + messages
    
    # Try Anthropic client first if this is an Anthropic-compatible model
    if model_name in ANTHROPIC_MODELS and anthropic_client is not None:
        return anthropic_client.messages.create(
            model=model_name, max_tokens=max_tokens,
            temperature=temperature, system=SYSTEM_PROMPT,
            messages=messages
        )
    
    # OpenAI-compatible client
    if openai_client is not None:
        return openai_client.chat.completions.create(
            model=model_name, messages=full_messages,
            max_tokens=max_tokens, temperature=temperature
        )
    
    raise RuntimeError("No AI client configured. Check OPENAI_API_KEY in .env")


def call_ai_fast(messages, **kwargs):
    timeout_seconds = kwargs.pop("timeout_seconds", AI_TIMEOUT_SECONDS)
    future = ai_executor.submit(call_ai, messages, **kwargs)
    try:
        return future.result(timeout=timeout_seconds)
    except TimeoutError:
        app.logger.warning("AI call exceeded %.1fs; returning fallback", timeout_seconds)
        return None
    except Exception as e:
        app.logger.warning("AI call failed: %s", e)
        return None


def call_ai_with_error(messages, **kwargs):
    timeout_seconds = kwargs.pop("timeout_seconds", AI_TIMEOUT_SECONDS)
    future = ai_executor.submit(call_ai, messages, **kwargs)
    try:
        return future.result(timeout=timeout_seconds), None
    except TimeoutError:
        return None, f"AI call timed out after {timeout_seconds:.1f}s"
    except Exception as e:
        return None, str(e)


def call_ai_stream(messages, **kwargs):
    """Stream AI response tokens. Yields text chunks."""
    model_name = kwargs.pop("model", OPENAI_MODEL)
    max_tokens = kwargs.get("max_tokens", 90)
    temperature = kwargs.get("temperature", 0.65)
    
    full_messages = [{"role": "system", "content": SYSTEM_PROMPT}] + messages

    if AI_PROVIDER == "gemini":
        response = call_gemini(messages, system_prompt=SYSTEM_PROMPT, max_tokens=max_tokens, temperature=temperature)
        text = get_response_text(response)
        if text:
            yield text
        return
    
    if model_name in ANTHROPIC_MODELS and anthropic_client is not None:
        with anthropic_client.messages.stream(
            model=model_name, max_tokens=max_tokens,
            temperature=temperature, system=SYSTEM_PROMPT,
            messages=messages
        ) as stream:
            for text in stream.text_stream:
                yield text
    
    elif openai_client is not None:
        response = openai_client.chat.completions.create(
            model=model_name, messages=full_messages,
            max_tokens=max_tokens, temperature=temperature,
            stream=True
        )
        for chunk in response:
            if chunk.choices[0].delta.content:
                yield chunk.choices[0].delta.content


@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "ok",
        "service": "RizzSe AI Backend",
        "mode": "mock" if USE_MOCK else "ai",
        "model": OPENAI_MODEL,
        "provider": AI_PROVIDER,
        "mock_enabled": USE_MOCK,
        "client_ready": client_ready()
    })


@app.route("/api/v1/auth/login", methods=["POST"])
def login():
    data = request.get_json() or {}
    email = data.get("email", "user@example.com")
    return jsonify({
        "access_token": "mock_token_12345",
        "user_id": "user_001",
        "email": email
    })


@app.route("/api/v1/chat/current-match", methods=["GET"])
def current_match():
    return jsonify({
        "match_name": "Sarah",
        "platform": "tinder",
        "messages": [
            {"sender": "them", "text": "Hey! How's your week going?"}
        ]
    })


@app.route("/api/v1/analyze-screenshot", methods=["POST"])
def analyze_screenshot():
    """Accept image + persona, return extracted conversation + suggestions."""
    started_at = time.monotonic()
    try:
        persona = request.form.get("persona", "playful")
        intent = request.form.get("intent", "keep_going")
        platform = request.form.get("platform", "whatsapp")
        hinglish = truthy(request.form.get("hinglish", "false"))
        input_text = request.form.get("input_text", "").strip()
        conversation = []
        suggestions = None
        insights = {}
        source = "none"
        raw_model_text = ""
        image_info = "unknown"
        failure_reason = "No image file was provided"
        base64_image = None

        if "image" in request.files:
            image_file = request.files["image"]
            filename = secure_filename(image_file.filename)
            filepath = os.path.join(UPLOAD_FOLDER, filename)
            image_file.save(filepath)
            
            have_client = client_ready()
            if have_client:
                image_file.seek(0)
                image_info = image_debug_info(image_file)
                image_file.seek(0)
                base64_image = encode_chat_screenshot(image_file)
                
                is_anthropic = OPENAI_MODEL in ANTHROPIC_MODELS
                context_prompt = build_context_prompt(persona, intent, platform, hinglish)
                if input_text:
                    context_prompt += f' The user has started typing: "{input_text}". Use this as guidance for tone/direction.'
                
                vision_prompt = f"""Read the chat screenshot. Reply ONLY to the latest message from them. Return EXACTLY these 3 lines and NOTHING else. Each under 100 chars. {context_prompt}

>>> Safe:
>>> Smooth:
>>> Bold:"""
                
                if is_anthropic:
                    messages = [
                        {"role": "user", "content": [
                            {"type": "text", "text": vision_prompt},
                            {"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": base64_image}}
                        ]}
                    ]
                else:
                    messages = [
                        {"role": "user", "content": [
                            {"type": "text", "text": vision_prompt},
                            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{base64_image}", "detail": "low"}}
                        ]}
                    ]
                
                response, first_error = call_ai_with_error(
                    messages,
                    model=OPENAI_MODEL,
                    max_tokens=80,
                    temperature=0.45,
                    timeout_seconds=30
                )
                if first_error:
                    failure_reason = f"First vision call failed: {first_error}"
                
                if response:
                    raw_model_text = get_response_text(response)
                    suggestions = parse_suggestions(raw_model_text, persona)
                    if len(suggestions) >= 3:
                        source = "vision_ai"
                    elif len(suggestions) >= 1:
                        source = "vision_ai"
                        app.logger.info(
                            "screenshot partial first: model=%s suggestions=%d response=%r",
                            OPENAI_MODEL, len(suggestions), raw_model_text[:300]
                        )
                    else:
                        failure_reason = f"First vision response had 0 parseable suggestions"
                        suggestions = None

                if not suggestions and AI_PROVIDER != "gemini":
                    app.logger.warning(
                        "screenshot parse failed: image=%s response_len=%d response=%r",
                        image_info,
                        len(raw_model_text),
                        raw_model_text[:500]
                    )

                    retry_prompt = f"""Read this chat screenshot. Write ONLY 3 replies to the latest message. Return ONLY these 3 lines, nothing else. {context_prompt}

>>> Safe: <reply>
>>> Smooth: <reply>
>>> Bold: <reply>"""
                    retry_messages = [
                        {"role": "user", "content": [
                            {"type": "text", "text": retry_prompt},
                            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{base64_image}", "detail": "low"}}
                        ]}
                    ]
                    retry_response, retry_error = call_ai_with_error(
                        retry_messages,
                        model=OPENAI_MODEL,
                        max_tokens=100,
                        temperature=0.4,
                        timeout_seconds=30
                    )
                    if retry_error:
                        failure_reason = f"Retry vision call failed: {retry_error}"
                    if retry_response:
                        raw_model_text = get_response_text(retry_response)
                        suggestions = parse_suggestions(raw_model_text, persona)
                        if len(suggestions) >= 3:
                            source = "vision_ai_retry"
                        elif len(suggestions) >= 1:
                            source = "vision_ai_retry"
                            app.logger.info(
                                "screenshot partial retry: model=%s suggestions=%d response=%r",
                                OPENAI_MODEL, len(suggestions), raw_model_text[:300]
                            )
                        else:
                            failure_reason = f"Retry vision response had 0 parseable suggestions"
                            suggestions = None
            
            if os.path.exists(filepath):
                os.remove(filepath)
        
        if not suggestions:
            app.logger.warning(
                "screenshot failed: model=%s source=%s image=%s reason=%s response_len=%d response=%r duration=%.2fs",
                OPENAI_MODEL,
                source,
                image_info,
                failure_reason,
                len(raw_model_text),
                raw_model_text[:500],
                time.monotonic() - started_at
            )
            return jsonify({
                "error": "Screenshot analysis failed",
                "reason": failure_reason,
                "image": image_info,
                "model": OPENAI_MODEL,
                "source": source
            }), 502

        app.logger.info(
            "screenshot ok: model=%s source=%s suggestions=%d duration=%.2fs",
            OPENAI_MODEL,
            source,
            len(suggestions),
            time.monotonic() - started_at
        )
        
        return jsonify({
            "suggestions": complete_suggestions(suggestions, persona),
            "persona": persona
        })
        
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route("/api/v1/analyze-screenshots", methods=["POST"])
def analyze_screenshots():
    """Accept multiple images + chat_context, return suggestions. New multi-image endpoint."""
    started_at = time.monotonic()
    try:
        persona = request.form.get("persona", "playful")
        intent = request.form.get("intent", "keep_going")
        platform = request.form.get("platform", "whatsapp")
        hinglish = truthy(request.form.get("hinglish", "false"))
        chat_context = request.form.get("chat_context", "")
        input_text = request.form.get("input_text", "").strip()

        image_files = request.files.getlist("images")
        if not image_files or all(f.filename == "" for f in image_files):
            return jsonify({"error": "No images provided"}), 400

        have_client = client_ready()
        suggestions = None
        insights = {}
        source = "none"
        raw_model_text = ""
        failure_reason = "No AI client"
        encoded_images = []

        if have_client:
            for img_file in image_files:
                if img_file.filename == "":
                    continue
                img_file.seek(0)
                image_info = image_debug_info(img_file)
                img_file.seek(0)
                app.logger.info("analyze-screenshots: processing image %s (%s)", img_file.filename, image_info)
                encoded_images.append(encode_chat_screenshot(img_file))

            if not encoded_images:
                return jsonify({"error": "No valid images"}), 400

            is_anthropic = OPENAI_MODEL in ANTHROPIC_MODELS
            context_prompt = build_context_prompt(persona, intent, platform, hinglish)
            if input_text:
                context_prompt += f' The user has started typing: "{input_text}". Use this as guidance for tone/direction.'

            context_hint = ""
            if chat_context:
                lines = chat_context.split("\n")
                clean_lines = [l.strip() for l in lines if len(l.strip()) > 3 and not l.strip().startswith(("Today", "Yesterday", "Online", "Typing", "Seen", "Delivered"))]
                ctx_text = "\n".join(clean_lines[-10:])
                if ctx_text:
                    context_hint = f"\n\nAdditional text context from chat:\n{ctx_text[:600]}"

            if len(encoded_images) == 1:
                vision_prompt = f"""Read the chat screenshot. Reply ONLY to the latest message. Return EXACTLY these 3 lines and NOTHING else. Each under 100 chars. {context_prompt}{context_hint}

>>> Safe:
>>> Smooth:
>>> Bold:"""
                if is_anthropic:
                    messages = [
                        {"role": "user", "content": [
                            {"type": "text", "text": vision_prompt},
                            {"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": encoded_images[0]}}
                        ]}
                    ]
                else:
                    messages = [
                        {"role": "user", "content": [
                            {"type": "text", "text": vision_prompt},
                            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{encoded_images[0]}", "detail": "low"}}
                        ]}
                    ]
            else:
                image_contents = []
                for idx, enc in enumerate(encoded_images):
                    if is_anthropic:
                        image_contents.append({"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": enc}})
                    else:
                        image_contents.append({"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{enc}", "detail": "low"}})

                vision_prompt = f"""{len(encoded_images)} chat screenshots. Read all. Reply ONLY to the latest message. Return EXACTLY 3 lines, NOTHING else. Each under 100 chars. {context_prompt}{context_hint}

>>> Safe:
>>> Smooth:
>>> Bold:"""
                messages = [{"role": "user", "content": [{"type": "text", "text": vision_prompt}] + image_contents}]

            response, first_error = call_ai_with_error(
                messages,
                model=OPENAI_MODEL,
                max_tokens=100,
                temperature=0.45,
                timeout_seconds=45
            )
            if first_error:
                failure_reason = f"Vision call failed: {first_error}"

            if response:
                raw_model_text = get_response_text(response)
                suggestions = parse_suggestions(raw_model_text, persona)
                if len(suggestions) >= 3:
                    source = "vision_ai"
                elif len(suggestions) >= 1:
                    source = "vision_ai"
                    app.logger.info(
                        "analyze-screenshots partial first: model=%s suggestions=%d response=%r",
                        OPENAI_MODEL, len(suggestions), raw_model_text[:300]
                    )
                else:
                    failure_reason = f"Got 0 suggestions from first attempt"
                    suggestions = None

            if not suggestions and encoded_images and AI_PROVIDER != "gemini":
                app.logger.warning("analyze-screenshots retry: model=%s images=%d reason=%s", OPENAI_MODEL, len(encoded_images), failure_reason)
                retry_prompt = f"""Read this chat screenshot. Write ONLY 3 replies to the latest message. Return ONLY these 3 lines, nothing else. {context_prompt}{context_hint}

>>> Safe: <reply>
>>> Smooth: <reply>
>>> Bold: <reply>"""
                retry_contents = []
                for enc in encoded_images:
                    if is_anthropic:
                        retry_contents.append({"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": enc}})
                    else:
                        retry_contents.append({"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{enc}", "detail": "low"}})
                retry_messages = [{"role": "user", "content": [{"type": "text", "text": retry_prompt}] + retry_contents}]
                retry_response, retry_error = call_ai_with_error(
                    retry_messages,
                    model=OPENAI_MODEL,
                    max_tokens=120,
                    temperature=0.4,
                    timeout_seconds=30
                )
                if retry_error:
                    failure_reason = f"Retry failed: {retry_error}"
                if retry_response:
                    raw_model_text = get_response_text(retry_response)
                    suggestions = parse_suggestions(raw_model_text, persona)
                    if len(suggestions) >= 3:
                        source = "vision_ai_retry"
                    elif len(suggestions) >= 1:
                        source = "vision_ai_retry"
                        app.logger.info(
                            "analyze-screenshots partial retry: model=%s suggestions=%d response=%r",
                            OPENAI_MODEL, len(suggestions), raw_model_text[:300]
                        )
                    else:
                        failure_reason = f"Retry got 0 suggestions"
                        suggestions = None

        if not suggestions:
            app.logger.warning(
                "analyze-screenshots failed: model=%s source=%s images=%d reason=%s duration=%.2fs",
                OPENAI_MODEL, source, len(image_files), failure_reason, time.monotonic() - started_at
            )
            return jsonify({
                "error": "Screenshot analysis failed",
                "reason": failure_reason,
                "model": OPENAI_MODEL,
                "source": source
            }), 502

        app.logger.info(
            "analyze-screenshots ok: model=%s source=%s suggestions=%d images=%d duration=%.2fs",
            OPENAI_MODEL, source, len(suggestions), len(image_files), time.monotonic() - started_at
        )
        return jsonify({
            "suggestions": complete_suggestions(suggestions, persona),
            "persona": persona
        })

    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route("/api/v1/screenshot-insights", methods=["POST"])
def screenshot_insights():
    """Separate insights endpoint to run in parallel with analyze-screenshot."""
    started_at = time.monotonic()
    try:
        persona = request.form.get("persona", "playful")
        intent = request.form.get("intent", "keep_going")
        platform = request.form.get("platform", "whatsapp")
        hinglish = truthy(request.form.get("hinglish", "false"))
        chat_context = request.form.get("chat_context", "")

        encoded_images = []
        image_files = request.files.getlist("images")
        if not image_files or all(f.filename == "" for f in image_files):
            if "image" in request.files:
                image_files = [request.files["image"]]

        for img_file in image_files:
            if img_file.filename == "":
                continue
            img_file.seek(0)
            encoded_images.append(encode_chat_screenshot(img_file))

        if not encoded_images:
            return jsonify({"insights": {}})

        insights, insights_error = generate_conversation_insights(
            chat_context=chat_context,
            encoded_images=encoded_images,
            persona=persona,
            intent=intent,
            platform=platform,
            hinglish=hinglish
        )
        if insights_error:
            app.logger.warning("screenshot-insights failed: %s duration=%.2fs", insights_error, time.monotonic() - started_at)
            return jsonify({"insights": {}})

        app.logger.info("screenshot-insights ok: duration=%.2fs", time.monotonic() - started_at)
        return jsonify({"insights": insights})

    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route("/api/v1/analyze-conversation-insights", methods=["POST"])
def analyze_conversation_insights():
    """Text-only conversation comments/energy analysis endpoint."""
    started_at = time.monotonic()
    try:
        data = request.get_json() or {}
        persona = data.get("persona", data.get("tone", "playful"))
        intent = data.get("intent", "keep_going")
        platform = data.get("platform", "whatsapp")
        hinglish = truthy(data.get("hinglish", "false"))
        chat_context = data.get("chat_context", "")
        conversation = data.get("conversation", [])
        if not chat_context and conversation:
            lines = []
            for msg in conversation[-16:]:
                sender = str(msg.get("sender", "them")).strip() or "them"
                text = str(msg.get("text", "")).strip()
                if text:
                    lines.append(f"{sender}: {text}")
            chat_context = "\n".join(lines)

        if not chat_context:
            return jsonify({"error": "chat_context or conversation is required"}), 400

        insights, error = generate_conversation_insights(
            chat_context=chat_context,
            persona=persona,
            intent=intent,
            platform=platform,
            hinglish=hinglish
        )
        if error or not insights:
            return jsonify({"error": "Conversation insights failed", "reason": error or "empty insights", "model": OPENAI_MODEL}), 502

        app.logger.info("conversation insights ok: model=%s duration=%.2fs", OPENAI_MODEL, time.monotonic() - started_at)
        return jsonify({"insights": insights, "persona": persona, "source": "ai"})
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route("/api/v1/chat/draft", methods=["POST"])
def chat_draft():
    """Text-based suggestions endpoint."""
    try:
        data = request.get_json() or {}
        persona = data.get("tone", data.get("persona", "playful"))
        intent = data.get("intent", "keep_going")
        platform = data.get("platform", "whatsapp")
        hinglish = truthy(data.get("hinglish", "false"))
        conversation = data.get("conversation", [])
        chat_context = data.get("chat_context", "")
        latest_text = ""
        if conversation:
            latest_text = str(conversation[-1].get("text", ""))
        if not latest_text and chat_context:
            latest_text = chat_context.split("\n")[-1][:200] if chat_context else ""

        if not conversation and not chat_context and intent == "first_message":
            return jsonify({
                "options": fallback_suggestions(persona, intent, key="tone", hinglish=hinglish)
            })

        if has_fast_fallback(latest_text, intent):
            return jsonify({
                "options": fallback_suggestions(
                    persona, intent, key="tone", hinglish=hinglish, latest_text=latest_text
                )
            })

        have_client = client_ready()
        if have_client:
            context_prompt = build_context_prompt(persona, intent, platform, hinglish)
            convo_text = "\n".join([
                f"{'You' if msg.get('sender') == 'you' else 'Them'}: {msg.get('text', '')}"
                for msg in conversation[-6:]
            ])
            
            # Integrate chat context from keyboard accessibility service
            if chat_context:
                lines = chat_context.split("\n")
                clean_lines = [l.strip() for l in lines if len(l.strip()) > 3 and not l.strip().startswith(("Today", "Yesterday", "Online", "Typing", "Seen", "Delivered"))]
                ctx_text = "\n".join(clean_lines[-10:])
                if convo_text:
                    convo_text = "Them: " + ctx_text[:600] + "\n\n" + convo_text
                else:
                    convo_text = "Them: " + ctx_text[:600]
            
            input_hint = convo_text[:700] if convo_text else (
                "No chat or profile details are available. Generate profile-neutral first-message options. "
                "Do not mention their bio, photo, smile, looks, job, city, or hobbies."
            )
            
            response = call_ai_fast(
                [{"role": "user", "content": f"{input_hint}\n\n{context_prompt} Write 3 short replies as Safe, Smooth, Bold (under 100 chars each).\n\n>>> Safe:\n>>> Smooth:\n>>> Bold:"}],
                max_tokens=100, temperature=0.45
            )
            
            if response:
                options = parse_suggestions(get_response_text(response), persona, key="tone")
                if options:
                    options = complete_suggestions(
                        options, persona, intent, key="tone", hinglish=hinglish, latest_text=latest_text
                    )
                    return jsonify({"options": options})
        elif USE_MOCK:
            return jsonify({"options": mock_options(persona, key="tone")})

        fallback = fallback_suggestions(persona, intent, key="tone", hinglish=hinglish, latest_text=latest_text)
        if fallback:
            return jsonify({"options": fallback})

        return jsonify({"error": "AI failed to generate suggestions"}), 500

    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route("/api/v1/chat/draft/stream", methods=["POST"])
def chat_draft_stream():
    """Streaming text suggestions endpoint using SSE."""
    from flask import Response, stream_with_context
    
    try:
        data = request.get_json() or {}
        persona = data.get("tone", data.get("persona", "playful"))
        intent = data.get("intent", "keep_going")
        platform = data.get("platform", "whatsapp")
        hinglish = truthy(data.get("hinglish", "false"))
        conversation = data.get("conversation", [])
        chat_context = data.get("chat_context", "")
        latest_text = ""
        if conversation:
            latest_text = str(conversation[-1].get("text", ""))
        if not latest_text and chat_context:
            latest_text = chat_context.split("\n")[-1][:200] if chat_context else ""

        # Fast path for known patterns
        if has_fast_fallback(latest_text, intent):
            options = fallback_suggestions(persona, intent, key="tone", hinglish=hinglish, latest_text=latest_text)
            return jsonify({"options": options})

        have_client = client_ready()
        
        def generate():
            if have_client:
                context_prompt = build_context_prompt(persona, intent, platform, hinglish)
                convo_text = "\n".join([
                    f"{'You' if msg.get('sender') == 'you' else 'Them'}: {msg.get('text', '')}"
                    for msg in conversation[-6:]
                ])
                
                # Integrate chat context from keyboard accessibility service
                if chat_context:
                    lines = chat_context.split("\n")
                    clean_lines = [l.strip() for l in lines if len(l.strip()) > 3 and not l.strip().startswith(("Today", "Yesterday", "Online", "Typing", "Seen", "Delivered"))]
                    ctx_text = "\n".join(clean_lines[-10:])
                    if convo_text:
                        convo_text = "Them: " + ctx_text[:600] + "\n\n" + convo_text
                    else:
                        convo_text = "Them: " + ctx_text[:600]
                
                input_hint = convo_text[:700] if convo_text else (
                    "No chat or profile details are available. Generate profile-neutral first-message options. "
                    "Do not mention their bio, photo, smile, looks, job, city, or hobbies."
                )
                
                full_text = ""
                try:
                    for chunk in call_ai_stream(
                        [{"role": "user", "content": f"{input_hint}\n\n{context_prompt} Write 3 short replies as Safe, Smooth, Bold (under 100 chars each).\n\n>>> Safe:\n>>> Smooth:\n>>> Bold:"}],
                        max_tokens=100, temperature=0.5
                    ):
                        full_text += chunk
                        # Send partial text as SSE
                        yield f"data: {json.dumps({'partial': full_text})}\n\n"
                    
                    # Parse final response
                    options = parse_suggestions(full_text, persona, key="tone")
                    if options:
                        options = complete_suggestions(
                            options, persona, intent, key="tone", hinglish=hinglish, latest_text=latest_text
                        )
                        yield f"data: {json.dumps({'options': options})}\n\n"
                    else:
                        # Fallback if parsing failed
                        fallback = fallback_suggestions(persona, intent, key="tone", hinglish=hinglish, latest_text=latest_text)
                        yield f"data: {json.dumps({'options': fallback})}\n\n"
                except Exception as e:
                    app.logger.warning("Stream failed: %s", e)
                    fallback = fallback_suggestions(persona, intent, key="tone", hinglish=hinglish, latest_text=latest_text)
                    yield f"data: {json.dumps({'options': fallback})}\n\n"
            else:
                # Mock/fallback mode
                fallback = fallback_suggestions(persona, intent, key="tone", hinglish=hinglish, latest_text=latest_text)
                yield f"data: {json.dumps({'options': fallback})}\n\n"
            
            yield "data: [DONE]\n\n"
        
        return Response(
            stream_with_context(generate()),
            mimetype='text/event-stream',
            headers={
                'Cache-Control': 'no-cache',
                'X-Accel-Buffering': 'no',
                'Connection': 'keep-alive'
            }
        )
    
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    port = int(os.getenv("FLASK_PORT", 8000))
    app.run(host="0.0.0.0", port=port, debug=False, threaded=True)
