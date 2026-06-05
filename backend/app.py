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
import requests as http_requests
from dotenv import load_dotenv

load_dotenv()

app = Flask(__name__)
app.secret_key = os.getenv("FLASK_SECRET_KEY", secrets.token_hex(32))
CORS(app, supports_credentials=True)

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
API_ENDPOINT = os.getenv("OPENAI_BASE_URL", "https://opencode.ai/zen/go/v1")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "kimi-k2.5")

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
    "You are an AI that outputs ONLY 3 concise rizz lines, one per line, each starting with >>>. "
    "NEVER explain, analyze, comment, or think out loud. NEVER include context, options, or notes. "
    "NEVER write anything before or after the 3 lines. Output exactly:\n"
    ">>> first rizz line\n"
    ">>> second rizz line\n"
    ">>> third rizz line\n"
    "No greetings, no setup, no explanation. Just the rizz."
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


def call_ai(messages, model=None, max_tokens=800, temperature=0.85):
    """Call AI model. Raises on failure — no mock fallback."""
    model_name = model or OPENAI_MODEL
    
    # Try Anthropic client first if this is an Anthropic-compatible model
    if model_name in ANTHROPIC_MODELS and anthropic_client is not None:
        return anthropic_client.messages.create(
            model=model_name, max_tokens=max_tokens,
            temperature=temperature, messages=messages
        )
    
    # OpenAI-compatible client
    if openai_client is not None:
        return openai_client.chat.completions.create(
            model=model_name, messages=messages,
            max_tokens=max_tokens, temperature=temperature
        )
    
    raise RuntimeError("No AI client configured. Check OPENAI_API_KEY in .env")


@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "ok",
        "service": "RizzSe AI Backend",
        "mode": "ai",
        "model": OPENAI_MODEL,
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
        print(f"[ANALYZE] Received request. Files: {list(request.files.keys())}, Form: {dict(request.form)}")
        persona = request.form.get("persona", "playful")
        user_gender = request.form.get("user_gender", "male")
        hinglish = request.form.get("hinglish", "false").lower() == "true"
        conversation = []
        suggestions = None
        
        if "image" in request.files:
            image_file = request.files["image"]
            print(f"[ANALYZE] Image received: {image_file.filename}, content_type: {image_file.content_type}")
            filename = secure_filename(image_file.filename)
            filepath = os.path.join(UPLOAD_FOLDER, filename)
            image_file.save(filepath)
            print(f"[ANALYZE] Image saved: {filepath}, size: {os.path.getsize(filepath)} bytes")
            
            # Try AI Vision if not in mock mode (handles both OpenAI and Anthropic formats)
            have_client = openai_client is not None or anthropic_client is not None
            if have_client:
                image_file.seek(0)
                base64_image = encode_image(image_file)
                
                # Build messages - Anthropic uses different image format
                is_anthropic = OPENAI_MODEL in ANTHROPIC_MODELS
                if is_anthropic:
                    messages = [
                        {"role": "user", "content": [
                            {"type": "text", "text": "Extract ONLY the meaningful conversation or profile text from this dating app screenshot. Ignore UI elements like timestamps, nav bars, buttons (Discover, Liked You, Chats, etc.), and app names. Just give me the actual bio/prompts/messages. No explanations."},
                            {"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": base64_image}}
                        ]}
                    ]
                else:
                    messages = [
                        {"role": "user", "content": [
                            {"type": "text", "text": "Extract ONLY the meaningful conversation or profile text from this dating app screenshot. Ignore UI elements like timestamps, nav bars, buttons (Discover, Liked You, Chats, etc.), and app names. Just give me the actual bio/prompts/messages. No explanations."},
                            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{base64_image}", "detail": "low"}}
                        ]}
                    ]
                
                text_instruction = messages[0]["content"][0]["text"]
                print(f"[PROMPT] Vision instruction: {text_instruction}")
                vision_response = call_ai(messages, model=OPENAI_MODEL, max_tokens=1500, temperature=0.1)
                
                if vision_response:
                    # Extract text from either Anthropic or OpenAI response
                    if is_anthropic:
                        vision_text = vision_response.content[0].text.strip()
                    else:
                        vision_text = vision_response.choices[0].message.content.strip()
                    
                    print(f"[DEBUG] AI raw response: {vision_text[:400]}", file=sys.stderr)
                    
                    # Remove quoted text (AI often puts extracted text in quotes)
                    clean_text = re.sub(r'^["\']+|["\']+$', '', vision_text.strip())
                    
                    # Build conversation from the extracted text
                    # Split into lines and take unique text as messages
                    lines = [l.strip().rstrip(',.') for l in clean_text.split('\n') if l.strip()]
                    all_text = ' '.join(lines)
                    
                    # Use the extracted text as a single "them" message
                    conversation = [{"sender": "them", "text": all_text[:500]}]
                    
                    # Generate suggestions from extracted text
                    try:
                        # Determine if this is a profile (needs opener) or chat (needs reply)
                        is_profile = any(w in clean_text.lower() for w in ['profile', 'bio', 'photos', 'prompts', 'about me', 'interests', 'located in', 'lives in', 'works at'])
                        is_chat = any(w in clean_text.lower() for w in ['them:', 'you:', 'hey', 'message', 'chat', 'sent', 'typing', 'read'])
                        
                        # Reference the other person based on user's gender
                        them = "she" if user_gender in ("male", "boy") else "he" if user_gender in ("female", "girl") else "them"
                        them_possessive = "her" if user_gender in ("male", "boy") else "his" if user_gender in ("female", "girl") else "their"
                        
                        lang_hint = "Use Hinglish (Hindi+English mix). Examples: 'Bhai tera taste top hai', 'That's lit yaar', 'Proper cute hai', 'Kya baat hai', 'Mast hai yeh'. Keep it natural and smooth. Use some Hindi words naturally." if hinglish else "Use English only. Keep it smooth and natural."
                        
                        # Generate openers or replies
                        their_text = clean_text[:250]
                        hinglish_prompt = " (Hinglish me batao)" if hinglish else ""
                        prompt = f"Her: \"{their_text}\"{hinglish_prompt}\nMe: "
                        print(f"[PROMPT] {prompt[:200]}", file=sys.stderr)
                        sug_response = call_ai(
                            [{"role": "user", "content": prompt}],
                            max_tokens=200, temperature=0.9
                        )
                        print(f"[PROMPT] {prompt[:300]}", file=sys.stderr)
                        sug_response = call_ai(
                            [{"role": "user", "content": prompt}],
                            max_tokens=300, temperature=0.85
                        )
                        
                        if sug_response:
                            if hasattr(sug_response, 'choices'):
                                msg = sug_response.choices[0].message
                                sug_text = (msg.content or getattr(msg, 'reasoning_content', '') or '').strip()
                            else:
                                sug_text = sug_response.content[0].text.strip() if sug_response.content else ''
                            print(f"[DEBUG] AI raw suggestion text: {sug_text[:500]}", file=sys.stderr)
                            # Parse >>> lines or fall back to numbered/cleanest lines
                            lines = [l.strip() for l in sug_text.split('\n') if l.strip()]
                            arrow_lines = [l for l in lines if l.startswith('>>>')]
                            if arrow_lines:
                                parsed_sugs = [{"text": re.sub(r'^>>>\s*', '', l).strip(), "confidence": random.randint(78, 98), "persona": persona} for l in arrow_lines[:3]]
                            else:
                                skip_prefixes = ('Context', 'Option', 'Note', 'Wait', 'Possible', 'Let me', 'I need', 'I think', 'Maybe', 'Here', 'Alright', 'So', 'First', 'Second', 'Third', 'Finally', 'The', 'This', 'That', 'Here are', 'She', 'He', 'They', 'Her profile', 'His profile', 'Looking at', 'Based on', 'Given', 'For', 'Romantic', 'Playful', 'Flirty', 'Direct', 'Chill', 'Witty', 'Bold', 'Friendly', 'Smooth', 'Cheeky', 'Confident', 'Teasing', 'Charming', 'Deconstruct', 'Analyze', 'Goal')
                                candidates = [l for l in lines if len(l) > 20 and not l.startswith(skip_prefixes) and not any(w in l.lower() for w in ['option', 'note', 'context', 'possib', 'interpret', 'angle', 'approach', 'maybe:', 'deconstruct', 'analyze', 'goal', 'target', 'character'])]
                                if not candidates:
                                    candidates = [l for l in lines if len(l) > 15][-5:]
                                parsed_sugs = []
                                for l in (candidates[-3:] if candidates else lines[-3:]):
                                    l = re.sub(r'^[\d]+[\.\)\-\:\s]+', '', l).strip()
                                    if l and len(l) > 10:
                                        parsed_sugs.append({"text": l, "confidence": random.randint(78, 98), "persona": persona})
                            if parsed_sugs:
                                suggestions = parsed_sugs[:3]
                                print(f"[DEBUG] AI generated {len(suggestions)} {'openers' if is_profile else 'replies'}", file=sys.stderr)
                            else:
                                print(f"[DEBUG] No suggestions parsed", file=sys.stderr)
                    except Exception as e:
                        print(f"[DEBUG] Suggestion call failed: {e}", file=sys.stderr)
            
            try:
                os.remove(filepath)
            except:
                pass
        
        if not suggestions:
            return jsonify({"error": "AI failed to generate suggestions"}), 500
        
        return jsonify({
            "conversation": conversation,
            "suggestions": suggestions,
            "detected_app": "Dating App",
            "persona": persona
        })
        
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route("/api/v1/chat/draft", methods=["POST"])
def chat_draft():
    """Text-based suggestions endpoint."""
    try:
        data = request.get_json() or {}
        persona = data.get("tone", data.get("persona", "playful"))
        user_gender = data.get("user_gender", "male")
        hinglish = data.get("hinglish", "false").lower() == "true"
        conversation = data.get("conversation", [])
        
        # Try AI if not mock
        have_client = openai_client is not None or anthropic_client is not None
        if have_client:
            persona_prompt = PERSONA_PROMPTS.get(persona, PERSONA_PROMPTS["playful"])
            convo_text = "\n".join([
                f"{'You' if msg.get('sender') == 'you' else 'Them'}: {msg.get('text', '')}"
                for msg in conversation
            ])
            them = "she" if user_gender in ("male", "boy") else "he" if user_gender in ("female", "girl") else "they"
            lang_hint = "Use Hinglish (Hindi+English mix). Examples: 'Bhai tera taste top hai', 'That's lit yaar', 'Proper cute hai', 'Kya baat hai', 'Mast hai yeh'. Keep it natural and smooth. Use some Hindi words naturally." if hinglish else "Use English only. Keep it smooth and natural."
            
            prompt_text = f"{them.capitalize()} said: \"{convo_text[:500]}\". {persona_prompt} Write 3 replies. {lang_hint} Start each with >>>"
            print(f"[PROMPT] Chat draft:\n{prompt_text}")
            response = call_ai(
                [{"role": "user", "content": prompt_text}],
                max_tokens=400, temperature=0.85
            )
            
            if response:
                if hasattr(response, 'choices'):
                    text = response.choices[0].message.content.strip()
                else:
                    text = response.content[0].text.strip()
                lines = [l.strip() for l in text.split('\n') if l.strip()]
                arrow_lines = [l for l in lines if l.startswith('>>>')]
                if arrow_lines:
                    options = [{"text": re.sub(r'^>>>\s*', '', l).strip(), "confidence": random.randint(78, 98), "tone": persona} for l in arrow_lines[:3]]
                else:
                    skip_prefixes = ('Context', 'Option', 'Note', 'Wait', 'Possible', 'Let me', 'I need', 'I think', 'Maybe', 'Here', 'Alright', 'So', 'First', 'Second', 'Third', 'Finally', 'The', 'This', 'That', 'Here are', 'She', 'He', 'They', 'Her profile', 'His profile', 'Looking at', 'Based on', 'Given', 'For')
                    candidates = [l for l in lines if len(l) > 15 and not l.startswith(skip_prefixes) and not any(w in l.lower() for w in ['option', 'note', 'context', 'possib', 'interpret'])]
                    if not candidates:
                        candidates = [l for l in lines if len(l) > 15][-5:]
                    options = []
                    for l in (candidates[-3:] if candidates else lines[-3:]):
                        l = re.sub(r'^[\d]+[\.\)\-\:\s]+', '', l).strip()
                        if l and len(l) > 10:
                            options.append({"text": l, "confidence": random.randint(78, 98), "tone": persona})
                if options:
                    return jsonify({"options": options})
        
        return jsonify({"error": "AI failed to generate suggestions"}), 500
        
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


# ── Simple token auth (no GitHub OAuth) ──
@app.route("/api/v1/auth/me", methods=["GET"])
def auth_me():
    return jsonify({"user": {"name": "RizzSe User"}})


if __name__ == "__main__":
    port = int(os.getenv("FLASK_PORT", 8000))
    app.run(host="0.0.0.0", port=port, debug=False)
