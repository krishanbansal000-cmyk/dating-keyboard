from flask import Flask, request, jsonify
from flask_cors import CORS
from werkzeug.utils import secure_filename
from PIL import Image
import io
import os
import base64
import json
import re
import random
from dotenv import load_dotenv

load_dotenv()

app = Flask(__name__)
CORS(app)

USE_MOCK = os.getenv("USE_MOCK", "true").lower() == "true"
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")

# Only initialize OpenAI client if we have a key and mock is disabled
openai_client = None
if not USE_MOCK and OPENAI_API_KEY:
    from openai import OpenAI
    openai_client = OpenAI(api_key=OPENAI_API_KEY)

UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
app.config["MAX_CONTENT_LENGTH"] = 10 * 1024 * 1024  # 10MB max

PERSONA_PROMPTS = {
    "friendly": "You are a friendly, warm, and approachable dating coach. Generate replies that are kind, easy-going, and make the other person feel comfortable.",
    "romantic": "You are a romantic dating coach. Generate replies that are sweet, charming, and show genuine interest. Use warmth and a touch of poetic flair.",
    "bold": "You are a bold, confident dating coach. Generate replies that are direct, assertive, and show high value. Take the lead in the conversation.",
    "witty": "You are a witty, clever dating coach. Generate replies that are funny, sharp, and playful. Use humor, wordplay, and teasing in a lighthearted way.",
    "playful": "You are a playful, fun dating coach. Generate replies that are light, teasing, and energetic. Use emojis, banter, and keep the vibe flirty but not too serious.",
    "chill": "You are a chill, laid-back dating coach. Generate replies that are relaxed, effortless, and cool. Don't try too hard. Keep it simple and confident.",
    "direct": "You are a direct, honest dating coach. Generate replies that are straightforward, no games, and get to the point. Good for setting up dates.",
    "flirty": "You are a flirty, sensual dating coach. Generate replies that are suggestive, sexy, and build sexual tension. Use innuendo and confidence. Be bold but not creepy."
}

MOCK_SUGGESTIONS = {
    "friendly": [
        "Hey! That sounds really fun — I'd love to join you! 😊",
        "That's so sweet of you to say! I'm really enjoying our conversation too.",
        "Thanks for sharing that with me. I appreciate how open you are!"
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

MOCK_CONVERSATIONS = [
    {"sender": "them", "text": "Hey! How's your week going?"},
    {"sender": "them", "text": "I've been thinking we should grab coffee sometime"},
    {"sender": "you", "text": "That sounds nice! When are you free?"}
]


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


def call_openai_safely(messages, model="gpt-4o-mini", max_tokens=800, temperature=0.85):
    """Call OpenAI with fallback to mock."""
    if USE_MOCK or openai_client is None:
        return None
    try:
        return openai_client.chat.completions.create(
            model=model,
            messages=messages,
            max_tokens=max_tokens,
            temperature=temperature
        )
    except Exception as e:
        print(f"[WARN] OpenAI call failed, falling back to mock: {e}")
        return None


@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "ok",
        "service": "DatingCopilot AI Backend",
        "mode": "mock" if USE_MOCK else "openai"
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
        conversation = MOCK_CONVERSATIONS.copy()
        
        if "image" in request.files:
            image_file = request.files["image"]
            filename = secure_filename(image_file.filename)
            filepath = os.path.join(UPLOAD_FOLDER, filename)
            image_file.save(filepath)
            
            # Try OpenAI Vision if not in mock mode
            if not USE_MOCK and openai_client:
                image_file.seek(0)
                base64_image = encode_image(image_file)
                
                vision_response = call_openai_safely([
                    {"role": "system", "content": "Extract conversation from this screenshot. Return JSON array: [{\"sender\": \"them\"/\"you\", \"text\": \"...\"}]"},
                    {"role": "user", "content": [
                        {"type": "text", "text": "Extract the conversation."},
                        {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{base64_image}", "detail": "low"}}
                    ]}
                ], model="gpt-4o-mini", max_tokens=1500, temperature=0.1)
                
                if vision_response:
                    vision_text = vision_response.choices[0].message.content.strip()
                    vision_text = re.sub(r'^```json\s*', '', vision_text)
                    vision_text = re.sub(r'```\s*$', '', vision_text)
                    try:
                        parsed = json.loads(vision_text)
                        if isinstance(parsed, list) and len(parsed) > 0:
                            conversation = parsed
                    except:
                        pass
            
            try:
                os.remove(filepath)
            except:
                pass
        
        suggestions = get_mock_suggestions(persona)
        
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
        conversation = data.get("conversation", [])
        
        # Try OpenAI if not mock
        if not USE_MOCK and openai_client:
            persona_prompt = PERSONA_PROMPTS.get(persona, PERSONA_PROMPTS["playful"])
            convo_text = "\n".join([
                f"{'You' if msg.get('sender') == 'you' else 'Them'}: {msg.get('text', '')}"
                for msg in conversation
            ])
            my_profile = data.get("my_profile", {})
            their_profile = data.get("their_profile", {})
            
            response = call_openai_safely([
                {"role": "system", "content": f"{persona_prompt}\n\nReturn exactly 3 reply options as JSON: [{{\"text\": \"...\", \"confidence\": 95, \"tone\": \"{persona}\"}}, ...]"},
                {"role": "user", "content": f"My profile: {my_profile}\nTheir: {their_profile}\n\nConversation:\n{convo_text}\n\nSuggest 3 replies."}
            ], max_tokens=800, temperature=0.85)
            
            if response:
                text = response.choices[0].message.content.strip()
                text = re.sub(r'^```json\s*', '', text)
                text = re.sub(r'```\s*$', '', text)
                try:
                    options = json.loads(text)
                    if isinstance(options, list):
                        for opt in options:
                            opt["tone"] = opt.get("tone", persona)
                        return jsonify({"options": options})
                except:
                    pass
        
        # Fallback to mock
        suggestions = get_mock_suggestions(persona)
        options = []
        for s in suggestions:
            options.append({
                "text": s["text"],
                "confidence": s["confidence"],
                "tone": persona
            })
        return jsonify({"options": options})
        
    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    port = int(os.getenv("FLASK_PORT", 8000))
    app.run(host="0.0.0.0", port=port, debug=False)
