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
    "Output 3 lines. Each starts with >>>."
)

PERSONA_PROMPTS = {
    "friendly": "Friendly smooth vibe. Short, warm, natural. Smile-inducing.",
    "romantic": "Sweet romantic. Warm charm, genuine interest. A hint of poetry, not overdone.",
    "bold": "Bold and direct. Take the lead, show high value. Cool confidence, never aggressive.",
    "witty": "Witty tease. Sharp humor, wordplay, light teasing. Screenshot-worthy.",
    "playful": "Playful flirt. Teasing, cheeky, electric banter. Max rizz energy.",
    "chill": "Laid-back cool. Low pressure, effortless vibe. Simple confidence.",
    "direct": "Direct mover. Cut small talk, set up the date. Confident and clear.",
    "flirty": "Smooth seducer. Subtle tension, confident charm. Bold but classy."
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

INDIA_STYLE_PROMPT = (
    "Audience is mostly Indian. Make replies natural for Indian dating/chat culture. "
    "Hinglish is okay only when requested. Avoid cringe pickup lines, vulgarity, and overly western slang. "
    "Prefer short, respectful, copy-pasteable replies."
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
        "That travel pic is fire — which country stole your heart? 🌍",
        "Your vibe is immaculate. What's your go-to comfort food?",
        "Honestly? You look like someone who'd win at karaoke. Prove me right."
    ],
    "romantic": [
        "You have a way of making my heart skip a beat. Just saying 😘",
        "I can't stop thinking about the last time we talked. You're special.",
        "There's something about you that feels different. In the best way possible."
    ],
    "bold": [
        "Let's skip the small talk. When are you free this week?",
        "I know what I want, and I want to take you out. Tonight?",
        "You're interesting. Let's not waste time — coffee, tomorrow, 7pm."
    ],
    "witty": [
        "Are you a magician? Because every time I look at my phone, you've made the boring stuff disappear! 😄",
        "I'd make a joke about chemistry, but I'm pretty sure we already have it.",
        "You must be made of copper and tellurium because you're Cu-Te! (CuTe, get it?)"
    ],
    "playful": [
        "Oh you're dangerous 😏 I like it.",
        "Tell me more... I'm intrigued now! 🔥",
        "You're definitely not boring. That's a good sign 😉"
    ],
    "chill": [
        "Sounds good. Let me know when you're free and we'll figure something out.",
        "Yeah I'm down. Keep me posted.",
        "Cool, that works. Talk later?"
    ],
    "direct": [
        "I like your vibe. Let's meet up this weekend.",
        "Honestly, I think we should grab a drink. What's your number?",
        "You seem cool. Let's not drag this out — drinks this Friday?"
    ],
    "flirty": [
        "I was just thinking about you... 😈 Great minds think alike.",
        "You're looking good today. Not that I'm complaining 😉",
        "I have a feeling we'd be very good at getting into trouble together."
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


def get_response_text(response):
    if hasattr(response, 'choices'):
        return response.choices[0].message.content.strip()
    return response.content[0].text.strip()


def parse_suggestions(text, persona, key="persona", min_confidence=78, max_confidence=98):
    """Parse model output into app-ready suggestion objects."""
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
        if len(value) <= 10 or any(word in value.lower() for word in SKIP_WORDS):
            continue
        suggestions.append({
            "text": value[:180],
            "confidence": random.randint(min_confidence, max_confidence),
            key: persona
        })
        if len(suggestions) == 3:
            break
    return suggestions


def mock_options(persona, key="persona"):
    return [
        {"text": item["text"], "confidence": item["confidence"], key: persona}
        for item in get_mock_suggestions(persona)
    ]


def build_context_prompt(persona, intent="keep_going", platform="whatsapp", hinglish=False):
    persona_prompt = PERSONA_PROMPTS.get(persona, PERSONA_PROMPTS["playful"])
    intent_prompt = INTENT_PROMPTS.get(intent, INTENT_PROMPTS["keep_going"])
    platform_prompt = PLATFORM_PROMPTS.get(platform, PLATFORM_PROMPTS["whatsapp"])
    lang_hint = "Use natural Hinglish." if hinglish else "Use English with Indian-friendly phrasing."
    return f"{INDIA_STYLE_PROMPT} {platform_prompt} {intent_prompt} {persona_prompt} {lang_hint}"


def truthy(value):
    return str(value).lower() in {"1", "true", "yes", "on"}


def call_ai(messages, model=None, max_tokens=800, temperature=0.85):
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
                
                vision_prompt = f"""Look at this screenshot. Write 3 short replies (under 100 chars each) based on what you see. {context_prompt}

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
                
                response = call_ai(messages, model=OPENAI_MODEL, max_tokens=600, temperature=0.95)
                
                if response:
                    suggestions = parse_suggestions(get_response_text(response), persona)
            
            if os.path.exists(filepath):
                os.remove(filepath)
        
        if not suggestions and not USE_MOCK:
            try:
                context_prompt = build_context_prompt(persona, intent, platform, hinglish)
                response = call_ai(
                    [{"role": "user", "content": f"{context_prompt} Write 3 short openers or replies (under 100 chars).\n\n>>>\n>>>\n>>>"}],
                    max_tokens=600, temperature=0.95
                )
                if response:
                    suggestions = parse_suggestions(
                        get_response_text(response), persona,
                        min_confidence=75, max_confidence=90
                    )
            except Exception as e:
                app.logger.warning("Fallback opener generation failed: %s", e)

        if not suggestions and USE_MOCK:
            suggestions = mock_options(persona)

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
        
        have_client = not USE_MOCK and (openai_client is not None or anthropic_client is not None)
        if have_client:
            context_prompt = build_context_prompt(persona, intent, platform, hinglish)
            convo_text = "\n".join([
                f"{'You' if msg.get('sender') == 'you' else 'Them'}: {msg.get('text', '')}"
                for msg in conversation
            ])
            input_hint = convo_text[:700] if convo_text else "No message provided yet. Generate useful first-message options."
            
            response = call_ai(
                [{"role": "user", "content": f"{input_hint}\n\n{context_prompt} Write 3 short replies (under 100 chars each).\n\n>>>\n>>>\n>>>"}],
                max_tokens=600, temperature=0.95
            )
            
            if response:
                options = parse_suggestions(get_response_text(response), persona, key="tone")
                if options:
                    return jsonify({"options": options})
        elif USE_MOCK:
            return jsonify({"options": mock_options(persona, key="tone")})
        
        return jsonify({"error": "AI failed to generate suggestions"}), 500
        
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    port = int(os.getenv("FLASK_PORT", 8000))
    app.run(host="0.0.0.0", port=port, debug=False)
