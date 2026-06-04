import sys
sys.path.insert(0, r'C:\Users\Krishan\repos\dating-keyboard\backend')

from app import app
import os

if __name__ == "__main__":
    port = int(os.getenv("FLASK_PORT", 8000))
    app.run(host="0.0.0.0", port=port, debug=False)
