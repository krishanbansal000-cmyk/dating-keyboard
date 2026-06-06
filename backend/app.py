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
USE_MOCK = os.getenv("USE_MOCK", "false").lower() in {"1", "true", "yes", "on"}
AI_TIMEOUT_SECONDS = float(os.getenv("AI_TIMEOUT_SECONDS", "12"))
ai_executor = ThreadPoolExecutor(max_workers=4)

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

UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
app.config["MAX_CONTENT_LENGTH"] = 10 * 1024 * 1024  # 10MB max

SYSTEM_PROMPT = (
    "Output exactly 3 dating replies under 100 chars: >>> Safe:, >>> Smooth:, >>> Bold:. No extras."
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
    "Indian Gen-Z style. Reply to latest message. Safe=low-risk, Smooth=witty/flirty, "
    "Bold=high-risk rizz. Avoid vulgarity, uncle jokes, fake deep lines, chemistry jokes, generic templates."
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
        "Only if you admit I recovered from the cringe",
        "Not quitting, just waiting for my redemption arc"
    ],
    "playful": [
        "Not when the conversation is this fun",
        "I can quit, but this is clearly my best bad decision today",
        "Only if you stop making it this entertaining"
    ],
    "chill": [
        "Fair, I'll slow down. Still enjoying this though",
        "Okay okay, I'll behave. Mostly",
        "Noted. I'll keep the confidence, reduce the drama"
    ],
    "direct": [
        "Then let me prove I'm less cringe over coffee",
        "One coffee and I'll retire the dramatic lines",
        "Give me one plan and I'll stop freelancing the future"
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


def encode_image(image_file):
    """Encode image to base64 for Vision API."""
    image = Image.open(image_file)
    if image.mode in ('RGBA', 'LA', 'P'):
        image = image.convert('RGB')
    buffered = io.BytesIO()
    image.save(buffered, format="JPEG", quality=85)
    return base64.b64encode(buffered.getvalue()).decode('utf-8')


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
    if hasattr(response, 'choices'):
        return (response.choices[0].message.content or "").strip()
    if getattr(response, "content", None):
        return (getattr(response.content[0], "text", "") or "").strip()
    return ""


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
            ("safe", "To me pretending I had a plan all along"),
            ("smooth", "To me becoming your best bad decision"),
            ("bold", "Deleting this app together, obviously")
        ]
    elif "getting way ahead" in latest or "way ahead of yourself" in latest:
        lines = [
            ("safe", "Fine, I'll downgrade us to one coffee for now"),
            ("smooth", "True, but the view from ahead is pretty good"),
            ("bold", "I call it confidence, HR would call it forecasting")
        ]
    elif "don't quit" in latest or "dont quit" in latest:
        lines = [
            ("safe", "Not when you're this fun to annoy"),
            ("smooth", "I quit bad conversations, not good ones"),
            ("bold", "Only after you admit the cringe worked")
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
            ("bold", "Let me prove I'm less cringe in person")
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
    lang_hint = "Natural Hinglish." if hinglish else "English, Indian-friendly."
    return f"{STYLE_RULES} {platform_prompt} {intent_prompt} {persona_prompt} {lang_hint}"


def truthy(value):
    return str(value).lower() in {"1", "true", "yes", "on"}


def call_ai(messages, model=None, max_tokens=90, temperature=0.65):
    """Call AI model. Raises on failure — no mock fallback."""
    model_name = model or OPENAI_MODEL
    
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
    future = ai_executor.submit(call_ai, messages, **kwargs)
    try:
        return future.result(timeout=AI_TIMEOUT_SECONDS)
    except TimeoutError:
        app.logger.warning("AI call exceeded %.1fs; returning fallback", AI_TIMEOUT_SECONDS)
        return None


def call_ai_stream(messages, **kwargs):
    """Stream AI response tokens. Yields text chunks."""
    model_name = kwargs.pop("model", OPENAI_MODEL)
    max_tokens = kwargs.get("max_tokens", 90)
    temperature = kwargs.get("temperature", 0.65)
    
    full_messages = [{"role": "system", "content": SYSTEM_PROMPT}] + messages
    
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
        "mock_enabled": USE_MOCK,
        "client_ready": openai_client is not None or anthropic_client is not None
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
    try:
        persona = request.form.get("persona", "playful")
        intent = request.form.get("intent", "keep_going")
        platform = request.form.get("platform", "whatsapp")
        hinglish = truthy(request.form.get("hinglish", "false"))
        conversation = []
        suggestions = None

        if "image" in request.files:
            image_file = request.files["image"]
            filename = secure_filename(image_file.filename)
            filepath = os.path.join(UPLOAD_FOLDER, filename)
            image_file.save(filepath)
            
            have_client = not USE_MOCK and (openai_client is not None or anthropic_client is not None)
            if have_client:
                image_file.seek(0)
                base64_image = encode_image(image_file)
                
                is_anthropic = OPENAI_MODEL in ANTHROPIC_MODELS
                context_prompt = build_context_prompt(persona, intent, platform, hinglish)
                
                vision_prompt = f"""Look at this screenshot. If it shows a chat, read the latest incoming message and write 3 replies to that message. Do not write profile/opening lines unless no chat is visible. Keep each under 100 chars. Return Safe, Smooth, Bold. {context_prompt}

>>>
>>>
>>>"""
                
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
                
                response = call_ai_fast(messages, model=OPENAI_MODEL, max_tokens=90, temperature=0.65)
                
                if response:
                    suggestions = parse_suggestions(get_response_text(response), persona)
            
            if os.path.exists(filepath):
                os.remove(filepath)
        
        if not suggestions and not USE_MOCK:
            try:
                context_prompt = build_context_prompt(persona, intent, platform, hinglish)
                response = call_ai_fast(
                    [{"role": "user", "content": f"{context_prompt} Write 3 short openers or replies as Safe, Smooth, Bold (under 100 chars).\n\n>>> Safe:\n>>> Smooth:\n>>> Bold:"}],
                    max_tokens=90, temperature=0.65
                )
                if response:
                    suggestions = parse_suggestions(
                        get_response_text(response), persona,
                        min_confidence=75, max_confidence=90
                    )
            except Exception as e:
                app.logger.warning("Fallback opener generation failed: %s", e)

            suggestions = complete_suggestions(suggestions, persona, intent, hinglish=hinglish)

        if not suggestions:
            return jsonify({"error": "AI failed to generate suggestions"}), 500
        
        return jsonify({
            "conversation": conversation,
            "suggestions": suggestions,
            "detected_app": "Dating App",
            "persona": persona
        })
        
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

        have_client = not USE_MOCK and (openai_client is not None or anthropic_client is not None)
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
                max_tokens=90, temperature=0.65
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

        have_client = not USE_MOCK and (openai_client is not None or anthropic_client is not None)
        
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
                        max_tokens=90, temperature=0.65
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
    app.run(host="0.0.0.0", port=port, debug=False)
