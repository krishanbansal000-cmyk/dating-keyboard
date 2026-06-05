import sys
sys.path.insert(0, r'C:\Users\Krishan\repos\dating-keyboard\backend')

from app import app
import os

if __name__ == "__main__":
    import sys, os
    log_path = os.path.join(os.path.dirname(__file__), "backend_errors.log")
    f = open(log_path, "w", buffering=1)
    sys.stderr = f
    port = int(os.getenv("FLASK_PORT", 8000))
    app.run(host="0.0.0.0", port=port, debug=False)
