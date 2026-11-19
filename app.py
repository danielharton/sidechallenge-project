"""
Scam Shield web app (GUI + dashboard). Fully local, nothing stored, message text never logged.

    python app.py            -> http://localhost:8000  (also reachable from a phone on the same Wi-Fi)
    python app.py --no-model -> rules only
"""
import argparse
import logging
import os

import uvicorn
from fastapi import FastAPI
from fastapi.responses import FileResponse
from pydantic import BaseModel

import engine

HERE = os.path.dirname(os.path.abspath(__file__))
app = FastAPI(title="Scam Shield")


class TextIn(BaseModel):
    text: str = ""
    known_contact: bool = False



@app.get("/")
def index():
    return FileResponse(os.path.join(HERE, "static", "index.html"))


@app.post("/api/analyze")
def analyze(scenario: dict):
    try:
        return engine.analyze(scenario)
    except Exception as e:  # robustness: never crash the UI
        return {"error": f"Fișier invalid: {e}"}


@app.post("/api/analyze_text")
def analyze_text(body: TextIn):
    try:
        return engine.analyze_text(body.text[:20000], known_contact=body.known_contact)
    except Exception as e:
        return {"error": str(e)}


@app.post("/api/batch")
def batch(scenarios: list[dict]):
    out = []
    for sc in scenarios:
        try:
            out.append(engine.analyze(sc))
        except Exception as e:
            out.append({"id": sc.get("id", "?"), "error": str(e)})
    out.sort(key=lambda r: str(r.get("id", "")))
    return out


@app.get("/api/status")
def status():
    return {"model": engine._MODEL is not None}


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-model", action="store_true")
    ap.add_argument("--port", type=int, default=8000)
    a = ap.parse_args()
    if not a.no_model:
        try:
            import model
            engine.set_model(model.load())
            print("Model loaded (rules + fine-tuned model)")
        except Exception as e:
            print("Rules only:", e)
    logging.getLogger("uvicorn.access").disabled = True   # privacy: no request logs
    uvicorn.run(app, host="0.0.0.0", port=a.port, access_log=False)
