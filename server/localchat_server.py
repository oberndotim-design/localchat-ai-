"""LocalChat's small LAN gateway for a local Ollama installation.

The Android app communicates with this server on port 8787.  Ollama itself is
kept on 127.0.0.1:11434 and is never exposed to the phone or the network.
"""

import os
from typing import Any

import requests
from flask import Flask, jsonify, request

app = Flask(__name__)

OLLAMA = "http://127.0.0.1:11434"
PORT = int(os.environ.get("LOCALCHAT_PORT", "8787"))
# If set, the app must provide this value under the X-LocalChat-Key header.
ACCESS_KEY = os.environ.get("LOCALCHAT_TOKEN", "").strip()
MAX_MESSAGE_LENGTH = 12_000
MAX_HISTORY_ITEMS = 16

MODE_SETTINGS = {
    "fast": {
        "system": "Antworte auf Deutsch, kurz, direkt und hilfreich. Gib nur die fertige Antwort aus.",
        "options": {"temperature": 0.35, "num_predict": 320},
    },
    "standard": {
        "system": "Antworte standardmäßig auf Deutsch. Sei klar, hilfreich und angemessen ausführlich. Gib nur die fertige Antwort aus.",
        "options": {"temperature": 0.55, "num_predict": 700},
    },
    "think": {
        "system": "Antworte standardmäßig auf Deutsch. Prüfe die Aufgabe sorgfältig und liefere eine gut begründete, strukturierte Endantwort. Gib keine interne Gedankenkette aus.",
        "options": {"temperature": 0.35, "num_predict": 1_200},
    },
}


def allowed() -> bool:
    """A token is optional for localhost-only use, but recommended for a LAN."""
    return not ACCESS_KEY or request.headers.get("X-LocalChat-Key", "") == ACCESS_KEY


@app.before_request
def require_key():
    if request.path in {"/", "/health", "/models", "/chat"} and not allowed():
        return jsonify({"error": "Zugriffscode fehlt oder ist ungültig."}), 401
    return None


def ollama_models() -> list[str]:
    response = requests.get(f"{OLLAMA}/api/tags", timeout=4)
    response.raise_for_status()
    return [item.get("name", "") for item in response.json().get("models", []) if item.get("name")]


def default_model() -> str:
    """Prefer the smaller Qwen3 when it has been installed; otherwise keep qwen3:4b."""
    models = ollama_models()
    for candidate in ("qwen3:1.7b", "qwen3:4b"):
        if candidate in models:
            return candidate
    return models[0] if models else "qwen3:4b"


def valid_history(items: Any) -> list[dict[str, str]]:
    if not isinstance(items, list):
        return []
    safe: list[dict[str, str]] = []
    for item in items[-MAX_HISTORY_ITEMS:]:
        if not isinstance(item, dict):
            continue
        role = item.get("role")
        content = item.get("content")
        if role in {"user", "assistant"} and isinstance(content, str) and content.strip():
            safe.append({"role": role, "content": content.strip()[:MAX_MESSAGE_LENGTH]})
    return safe


@app.get("/")
def home():
    return jsonify({"name": "LocalChat server", "ok": True, "ollama": "localhost only"})


@app.get("/health")
def health():
    try:
        models = ollama_models()
        return jsonify({"ok": True, "models": len(models), "default_model": default_model()})
    except requests.RequestException as error:
        return jsonify({"ok": False, "error": f"Ollama ist nicht erreichbar: {error}"}), 503


@app.get("/models")
def models():
    try:
        return jsonify({
            "models": ollama_models(),
            "default_model": default_model(),
            "recommended_fast": "qwen3:1.7b",
            "recommended_command": "ollama pull qwen3:1.7b",
        })
    except requests.RequestException as error:
        return jsonify({"error": f"Ollama ist nicht erreichbar: {error}"}), 503


@app.post("/chat")
def chat():
    try:
        data = request.get_json(silent=True) or {}
        message = data.get("message", "")
        if not isinstance(message, str) or not message.strip():
            return jsonify({"error": "Keine Nachricht eingegeben."}), 400
        message = message.strip()[:MAX_MESSAGE_LENGTH]
        mode_name = data.get("mode", "standard")
        mode = MODE_SETTINGS.get(mode_name, MODE_SETTINGS["standard"])
        requested_model = data.get("model", "")
        model = requested_model.strip()[:120] if isinstance(requested_model, str) else ""
        model = model or default_model()

        installed = ollama_models()
        if model not in installed:
            return jsonify({
                "error": f"Das Modell '{model}' ist auf dem PC noch nicht installiert.",
                "hint": "Installiere es am PC mit: ollama pull " + model,
            }), 400

        messages = [{"role": "system", "content": mode["system"]}]
        messages.extend(valid_history(data.get("history", [])))
        messages.append({"role": "user", "content": message})
        payload = {"model": model, "messages": messages, "stream": False, "options": mode["options"]}
        response = requests.post(f"{OLLAMA}/api/chat", json=payload, timeout=300)
        response.raise_for_status()
        result = response.json()
        answer = result.get("message", {}).get("content", "").strip()
        if not answer:
            return jsonify({"error": "Ollama hat keine Textantwort geliefert."}), 502
        return jsonify({"response": answer, "model": model, "mode": mode_name})
    except requests.Timeout:
        return jsonify({"error": "Ollama hat zu lange für die Antwort gebraucht."}), 504
    except requests.RequestException as error:
        return jsonify({"error": f"Ollama-Fehler: {error}"}), 502
    except Exception as error:  # keeps malformed client data from killing the server
        return jsonify({"error": f"Serverfehler: {error}"}), 500


if __name__ == "__main__":
    print("LocalChat Server startet …")
    print(f"Ollama bleibt lokal unter {OLLAMA}.")
    print(f"Die Android-App verbindet sich mit http://DEINE-PC-IP:{PORT}")
    if not ACCESS_KEY:
        print("Hinweis: Kein LOCALCHAT_TOKEN gesetzt. Für Zugriff im WLAN wird ein Zugriffscode empfohlen.")
    app.run(host="0.0.0.0", port=PORT, debug=False)
