import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app import app

if __name__ == "__main__":
    import sys, os
    log_path = os.path.join(os.path.dirname(__file__), "backend_errors.log")
    f = open(log_path, "w", buffering=1)
    sys.stderr = f
    port = int(os.getenv("FLASK_PORT", 8000))
    app.run(host="0.0.0.0", port=port, debug=False)
