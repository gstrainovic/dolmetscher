# /// script
# requires-python = ">=3.12"
# dependencies = ["httpx"]
# ///
"""Gemini-Textmodelle deuten dieselben fehlerhaften Voxtral-Umschriften wie Claude in modellvergleich.py.

Gleiche Anweisung, Sätze nacheinander als ein Gespräch (Verlauf als Zusammenhang). Gemessen: Zeit bis zur Antwort.
Aufruf: uv run gemini_deuten.py modellvergleich.json > gemini-deuten.json
"""
import json, sys, time
from pathlib import Path
import httpx

KEY = (Path.home() / ".config/gemini/key").read_text().strip()
ANWEISUNG = (
    "You are an interpreter in a Swiss care home. Every message is an automatic, often faulty transcript of "
    "spoken Swiss German or German by residents or carers. Work out what was most likely meant, using the "
    "earlier messages of this conversation as context, and translate it into Serbian (Latin script). "
    "Output only the translation, in Serbian: no explanation, no notes, no alternatives, no other language."
)
# Dieselben zwölf Umschriften wie im Claude-Vergleich (dort je Modell einmal).
saetze = []
for z in json.loads(Path(sys.argv[1]).read_text()):
    if z["umschrift"] not in [s["umschrift"] for s in saetze]:
        saetze.append({"umschrift": z["umschrift"], "gemeint": z["gemeint"]})

alle = []
for modell in ["gemini-3.8-flash", "gemini-3.1-pro-preview"]:
    verlauf = []
    for s in saetze:
        verlauf.append({"role": "user", "parts": [{"text": s["umschrift"]}]})
        t0 = time.time()
        r = httpx.post(f"https://generativelanguage.googleapis.com/v1beta/models/{modell}:generateContent",
                       headers={"x-goog-api-key": KEY}, timeout=120,
                       json={"systemInstruction": {"parts": [{"text": ANWEISUNG}]}, "contents": verlauf})
        dauer = round(time.time() - t0, 2)
        try:
            text = r.json()["candidates"][0]["content"]["parts"][0]["text"].strip()
        except Exception:
            text = f"[FEHLER HTTP {r.status_code}]"
        verlauf.append({"role": "model", "parts": [{"text": text}]})
        alle.append({"modell": modell, **s, "uebersetzung": text, "ganz_s": dauer})
        print(modell, dauer, text, file=sys.stderr, flush=True)
print(json.dumps(alle, ensure_ascii=False, indent=1))
