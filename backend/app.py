import secrets
from flask import Flask, request, jsonify, redirect, session
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
from urllib.parse import urlencode

load_dotenv()

app = Flask(__name__)
app.secret_key = os.getenv("FLASK_SECRET_KEY", secrets.token_hex(32))
CORS(app, supports_credentials=True)

USE_MOCK = os.getenv("USE_MOCK", "true").lower() == "true"
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "qwen3.6-plus")

# Anthropic-compatible models (use Messages API endpoint)
ANTHROPIC_MODELS = {"qwen3.7-plus", "qwen3.7-max", "qwen3.6-plus", "minimax-m3", "minimax-m2.7", "minimax-m2.5"}
API_ENDPOINT = os.getenv("OPENAI_BASE_URL", "")

# GitHub OAuth config
GITHUB_CLIENT_ID = os.getenv("GITHUB_CLIENT_ID", "")
GITHUB_CLIENT_SECRET = os.getenv("GITHUB_CLIENT_SECRET", "")
GITHUB_REDIRECT_URI = os.getenv("GITHUB_REDIRECT_URI", "http://localhost:8000/api/v1/auth/github/callback")

# Only initialize clients if we have a key and mock is disabled
openai_client = None
anthropic_client = None
if not USE_MOCK and OPENAI_API_KEY:
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
        client_kwargs = {"api_key": OPENAI_API_KEY}
        if API_ENDPOINT:
            client_kwargs["base_url"] = API_ENDPOINT
        openai_client = OpenAI(**client_kwargs)

UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
app.config["MAX_CONTENT_LENGTH"] = 10 * 1024 * 1024  # 10MB max

PERSONA_PROMPTS = {
    "friendly": "You are a friendly, warm, and approachable dating coach. Generate replies that are kind, easy-going, and make the other person feel comfortable. Match the energy of the conversation.",
    "romantic": "You are a romantic dating coach. Generate replies that are sweet, charming, and show genuine interest. Use warmth and a touch of poetic flair. Good for deeper connections.",
    "bold": "You are a bold, confident dating coach. Generate replies that are direct, assertive, and show high value. Take the lead in the conversation. Move things forward decisively.",
    "witty": "You are a witty, clever dating coach. Generate replies that are funny, sharp, and playful. Use humor, wordplay, and teasing in a lighthearted way. Break the ice with charm.",
    "playful": "You are a playful, fun dating coach. Generate replies that are light, teasing, and energetic. Use emojis, banter, and keep the vibe flirty but not too serious.",
    "chill": "You are a chill, laid-back dating coach. Generate replies that are relaxed, effortless, and cool. Don't try too hard. Keep it simple and confident. Low pressure.",
    "direct": "You are a direct, honest dating coach. Generate replies that are straightforward, no games, and get to the point. Good for setting up dates quickly.",
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


def call_openai_safely(messages, model=None, max_tokens=800, temperature=0.85):
    """Call AI model with fallback to mock. Handles both OpenAI and Anthropic clients."""
    model_name = model or OPENAI_MODEL
    if USE_MOCK:
        return None
    
    # Try Anthropic client first if this is an Anthropic-compatible model
    if model_name in ANTHROPIC_MODELS and anthropic_client is not None:
        try:
            return anthropic_client.messages.create(
                model=model_name,
                max_tokens=max_tokens,
                temperature=temperature,
                messages=messages
            )
        except Exception as e:
            print(f"[WARN] Anthropic call failed, falling back to mock: {e}")
            return None
    
    # Fallback to OpenAI-compatible client
    if openai_client is not None:
        try:
            return openai_client.chat.completions.create(
                model=model_name,
                messages=messages,
                max_tokens=max_tokens,
                temperature=temperature
            )
        except Exception as e:
            print(f"[WARN] OpenAI call failed, falling back to mock: {e}")
            return None
    
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
        print(f"[ANALYZE] Received request. Files: {list(request.files.keys())}, Form: {dict(request.form)}")
        persona = request.form.get("persona", "playful")
        conversation = MOCK_CONVERSATIONS.copy()
        
        if "image" in request.files:
            image_file = request.files["image"]
            print(f"[ANALYZE] Image received: {image_file.filename}, content_type: {image_file.content_type}")
            filename = secure_filename(image_file.filename)
            filepath = os.path.join(UPLOAD_FOLDER, filename)
            image_file.save(filepath)
            print(f"[ANALYZE] Image saved: {filepath}, size: {os.path.getsize(filepath)} bytes")
            
            # Try AI Vision if not in mock mode (handles both OpenAI and Anthropic formats)
            have_client = openai_client is not None or anthropic_client is not None
            if not USE_MOCK and have_client:
                image_file.seek(0)
                base64_image = encode_image(image_file)
                
                # Build messages - Anthropic uses different image format
                is_anthropic = OPENAI_MODEL in ANTHROPIC_MODELS
                if is_anthropic:
                    messages = [
                        {
                            "role": "user",
                            "content": [
                                {"type": "text", "text": "Extract conversation from this screenshot. Return JSON array: [{\"sender\": \"them\"/\"you\", \"text\": \"...\"}]"},
                                {"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": base64_image}}
                            ]
                        }
                    ]
                else:
                    messages = [
                        {"role": "system", "content": "Extract conversation from this screenshot. Return JSON array: [{\"sender\": \"them\"/\"you\", \"text\": \"...\"}]"},
                        {"role": "user", "content": [
                            {"type": "text", "text": "Extract the conversation."},
                            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{base64_image}", "detail": "low"}}
                        ]}
                    ]
                
                vision_response = call_openai_safely(messages, model=OPENAI_MODEL, max_tokens=1500, temperature=0.1)
                
                if vision_response:
                    # Extract text from either Anthropic or OpenAI response
                    if is_anthropic:
                        vision_text = vision_response.content[0].text.strip()
                    else:
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
        
        # Try AI if not mock (handles both OpenAI and Anthropic formats)
        have_client = openai_client is not None or anthropic_client is not None
        if not USE_MOCK and have_client:
            persona_prompt = PERSONA_PROMPTS.get(persona, PERSONA_PROMPTS["playful"])
            convo_text = "\n".join([
                f"{'You' if msg.get('sender') == 'you' else 'Them'}: {msg.get('text', '')}"
                for msg in conversation
            ])
            my_profile = data.get("my_profile", {})
            their_profile = data.get("their_profile", {})
            
            is_anthropic = OPENAI_MODEL in ANTHROPIC_MODELS
            if is_anthropic:
                messages = [
                    {
                        "role": "user",
                        "content": f"System: {persona_prompt}\n\nMy profile: {my_profile}\nTheir: {their_profile}\n\nConversation:\n{convo_text}\n\nReturn exactly 3 reply options as JSON: [{{\"text\": \"...\", \"confidence\": 95, \"tone\": \"{persona}\"}}, ...]"
                    }
                ]
            else:
                messages = [
                    {"role": "system", "content": f"{persona_prompt}\n\nReturn exactly 3 reply options as JSON: [{{\"text\": \"...\", \"confidence\": 95, \"tone\": \"{persona}\"}}, ...]"},
                    {"role": "user", "content": f"My profile: {my_profile}\nTheir: {their_profile}\n\nConversation:\n{convo_text}\n\nSuggest 3 replies."}
                ]
            
            response = call_openai_safely(messages, max_tokens=800, temperature=0.85)
            
            if response:
                if is_anthropic:
                    text = response.content[0].text.strip()
                else:
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


# ── GitHub OAuth ──
@app.route("/api/v1/auth/github/login", methods=["GET"])
def github_login():
    """Redirect user to GitHub for OAuth authorization."""
    if not GITHUB_CLIENT_ID:
        return jsonify({"error": "GitHub OAuth not configured. Set GITHUB_CLIENT_ID in .env"}), 501
    
    params = {
        "client_id": GITHUB_CLIENT_ID,
        "redirect_uri": GITHUB_REDIRECT_URI,
        "scope": "read:user user:email",
        "state": secrets.token_hex(16)
    }
    session["oauth_state"] = params["state"]
    github_url = f"https://github.com/login/oauth/authorize?{urlencode(params)}"
    return jsonify({"redirect_url": github_url})


@app.route("/api/v1/auth/github/callback", methods=["GET"])
def github_callback():
    """Handle GitHub OAuth callback and return user info + token."""
    code = request.args.get("code")
    state = request.args.get("state")
    
    if not code:
        return jsonify({"error": "No authorization code provided"}), 400
    
    # Verify state to prevent CSRF
    stored_state = session.get("oauth_state")
    if stored_state and state != stored_state:
        return jsonify({"error": "State mismatch"}), 400
    
    # Exchange code for access token
    token_response = http_requests.post(
        "https://github.com/login/oauth/access_token",
        data={
            "client_id": GITHUB_CLIENT_ID,
            "client_secret": GITHUB_CLIENT_SECRET,
            "code": code,
            "redirect_uri": GITHUB_REDIRECT_URI
        },
        headers={"Accept": "application/json"}
    )
    
    if token_response.status_code != 200:
        return jsonify({"error": "Failed to get access token"}), 400
    
    access_token = token_response.json().get("access_token")
    if not access_token:
        return jsonify({"error": "No access token in response"}), 400
    
    # Get user info from GitHub
    user_response = http_requests.get(
        "https://api.github.com/user",
        headers={
            "Authorization": f"Bearer {access_token}",
            "Accept": "application/json"
        }
    )
    
    if user_response.status_code != 200:
        return jsonify({"error": "Failed to get user info"}), 400
    
    user_data = user_response.json()
    
    # Get user email
    email_response = http_requests.get(
        "https://api.github.com/user/emails",
        headers={
            "Authorization": f"Bearer {access_token}",
            "Accept": "application/json"
        }
    )
    
    email = ""
    if email_response.status_code == 200:
        emails = email_response.json()
        primary = [e for e in emails if e.get("primary")]
        if primary:
            email = primary[0].get("email", "")
        elif emails:
            email = emails[0].get("email", "")
    
    # Create user profile
    github_user = {
        "id": user_data.get("id"),
        "login": user_data.get("login"),
        "name": user_data.get("name", user_data.get("login")),
        "email": email,
        "avatar_url": user_data.get("avatar_url"),
        "bio": user_data.get("bio", ""),
        "github_url": user_data.get("html_url")
    }
    
    # Generate a simple token for the Android app
    import hashlib
    import time
    app_token = hashlib.sha256(f"{user_data['id']}:{access_token}:{time.time()}".encode()).hexdigest()
    
    # Store user in memory (in production use a database)
    import threading
    if not hasattr(app, "github_users"):
        app.github_users = {}
        app.github_users_lock = threading.Lock()
    with app.github_users_lock:
        app.github_users[app_token] = github_user
    
    # Return token and user info (as JSON for app, or HTML page for browser)
    accept = request.headers.get("Accept", "")
    if "text/html" in accept:
        return f"""
        <html><body><script>
        localStorage.setItem('github_token', '{app_token}');
        localStorage.setItem('github_user', '{json.dumps(github_user)}');
        window.location.href = 'datingcopilot://auth?token={app_token}';
        setTimeout(() => {{ window.close(); }}, 1000);
        </script><p>Logged in! You can close this window.</p></body></html>
        """, 200, {"Content-Type": "text/html"}
    
    return jsonify({
        "access_token": app_token,
        "user": github_user
    })


@app.route("/api/v1/auth/me", methods=["GET"])
def auth_me():
    """Get current user from token."""
    auth = request.headers.get("Authorization", "")
    token = auth.replace("Bearer ", "").strip()
    
    if not token or not hasattr(app, "github_users"):
        return jsonify({"error": "Not authenticated"}), 401
    
    user = app.github_users.get(token)
    if not user:
        return jsonify({"error": "Invalid token"}), 401
    
    return jsonify({"user": user})


@app.route("/api/v1/auth/logout", methods=["POST"])
def auth_logout():
    """Logout and invalidate token."""
    auth = request.headers.get("Authorization", "")
    token = auth.replace("Bearer ", "").strip()
    
    if token and hasattr(app, "github_users"):
        app.github_users.pop(token, None)
    
    return jsonify({"status": "logged_out"})


if __name__ == "__main__":
    port = int(os.getenv("FLASK_PORT", 8000))
    app.run(host="0.0.0.0", port=port, debug=False)
