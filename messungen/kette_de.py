# /// script
# requires-python = ">=3.12"
# dependencies = ["httpx", "websockets>=13"]
# ///
"""Kette für «Deutsch / Schweizerdeutsch» so, wie die App sie fährt: Voxtral (language=de) → Claude-Dienst
(/uebersetzen/strom) → Gemini liest vor. Je Satz Mundart (Suisse-TTS) und Hochdeutsch (Google-Stimme), dazu
serbische Sätze, die ausgelassen werden müssen. Gemessen ab Satzende: Umschrift, erster Satz von Claude, erster Ton.

Aufruf: uv run kette_de.py <ordner für ton> > kette-de.json
"""
import asyncio, base64, io, json, re, subprocess, sys, time, uuid, wave
from pathlib import Path
import httpx, websockets

OUT = Path(sys.argv[1]); OUT.mkdir(parents=True, exist_ok=True)
CFG = Path.home() / ".config"
GEMINI = (CFG / "gemini/key").read_text().strip()
SUISSE = (CFG / "suisse-speech/key").read_text().strip()
MISTRAL = re.search(r"MISTRAL_API_KEY=(\S+)", (Path.home() / "projects/ki-tel/.secrets/mistral.env").read_text()).group(1).strip("'\"")
SEC = dict(l.split("=", 1) for l in (Path(__file__).parent.parent / "secrets.properties").read_text().splitlines() if "=" in l)
CLAUDE_URL, CLAUDE_TOKEN = SEC["CLAUDE_SERVER_URL"].strip().rstrip("/"), SEC["CLAUDE_SERVER_TOKEN"].strip()
PROJEKT = "gen-lang-client-0650867108"
VORLESEN = ("You are a text-to-speech voice. Read the user's Serbian text aloud exactly as written, word for word, "
            "in natural Serbian. Do not translate, answer, comment, add or leave out anything.")

SAETZE = [  # (Name, Dialekt, Mundart, Hochdeutsch)
    ("s0", "zurich", "Wänd Sie zum Zvieri es Gipfeli mit Anke und Konfi?", "Wollen Sie zum Zvieri ein Gipfeli mit Butter und Konfitüre?"),
    ("s1", "zurich", "Ich ha Buuchweh und es bitzeli Pfnüsel.", "Ich habe Bauchweh und ein bisschen Schnupfen."),
    ("s2", "luzern", "Chönd Sie mer bitte d'Finke ufem Stägeli hole?", "Können Sie mir bitte die Hausschuhe auf der Treppe holen?"),
    ("s3", "bern", "I ha mi Brülle verleit, hesch se gseh?", "Ich habe meine Brille verlegt, hast du sie gesehen?"),
    ("s4", "basel", "Mir isch es chalt, chönnt ich no e Tschope ha?", "Mir ist kalt, könnte ich noch eine Jacke haben?"),
    ("s5", "luzern", "Hend Sie mini Tablette scho brocht?", "Haben Sie meine Tabletten schon gebracht?"),
]
SERBISCH = ["Dobar dan, kako ste danas?", "Molim vas, donesite mi čašu vode."]

def suisse_tts(name, dialekt, text):
    f = OUT / f"{name}-mundart.pcm"
    if not f.exists():
        r = httpx.post("https://api.suisse-speech.ch/v1/tts", headers={"X-API-Key": SUISSE}, timeout=60,
                       json={"text": text, "language": "de-CH", "dialect": dialekt, "format": "pcm16",
                             "sample_rate_hz": 16000, "model": "studio"})
        r.raise_for_status(); f.write_bytes(r.content)
    return f.read_bytes()

def google_tts(name, text, sprache, stimme):
    f = OUT / f"{name}.pcm"
    if not f.exists():
        tok = subprocess.run(["gcloud", "auth", "print-access-token"], capture_output=True, text=True).stdout.strip()
        r = httpx.post("https://texttospeech.googleapis.com/v1/text:synthesize", timeout=60,
                       headers={"Authorization": f"Bearer {tok}", "x-goog-user-project": PROJEKT},
                       json={"input": {"text": text}, "voice": {"languageCode": sprache, "name": stimme},
                             "audioConfig": {"audioEncoding": "LINEAR16", "sampleRateHertz": 16000}})
        r.raise_for_status(); f.write_bytes(base64.b64decode(r.json()["audioContent"])[44:])
    return f.read_bytes()

def voxtral(pcm):
    b = io.BytesIO()
    with wave.open(b, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000); w.writeframes(pcm)
    r = httpx.post("https://api.mistral.ai/v1/audio/transcriptions", headers={"Authorization": f"Bearer {MISTRAL}"},
                   data={"model": "voxtral-mini-2602", "language": "de"}, files={"file": ("s.wav", b.getvalue(), "audio/wav")}, timeout=60)
    r.raise_for_status()
    return r.json().get("text", "").strip()

def claude_erster_satz(sitzung, text):
    """Wie ClaudeServerTranslator.translateSentences: Strom lesen, erster Satz = erste Zeichen bis Satzende."""
    t0, erster, ganz = time.time(), None, ""
    with httpx.stream("POST", CLAUDE_URL + "/uebersetzen/strom", timeout=60,
                      headers={"Authorization": f"Bearer {CLAUDE_TOKEN}"},
                      json={"sitzung": sitzung, "ziel": "sr", "text": text}) as r:
        r.raise_for_status()
        for chunk in r.iter_text():
            ganz += chunk
            if erster is None and re.search(r"[.!?\]]", ganz):
                erster = time.time() - t0
    return ganz.strip(), round(erster or time.time() - t0, 2), round(time.time() - t0, 2)

async def vorlesen_erster_ton(text):
    url = ("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta."
           "GenerativeService.BidiGenerateContent")
    async with websockets.connect(url, additional_headers={"x-goog-api-key": GEMINI}, max_size=None) as ws:
        await ws.send(json.dumps({"setup": {"model": "models/gemini-3.8-live",
                                            "generationConfig": {"responseModalities": ["AUDIO"]},
                                            "systemInstruction": {"parts": [{"text": VORLESEN}]}}}))
        await ws.recv()
        t0 = time.time()
        await ws.send(json.dumps({"realtimeInput": {"text": text}}))
        async for m in ws:
            e = json.loads(m)
            for p in e.get("serverContent", {}).get("modelTurn", {}).get("parts", []):
                if "inlineData" in p:
                    return round(time.time() - t0, 2)
            if e.get("serverContent", {}).get("turnComplete"):
                return None

def lauf(art, vorlage, pcm, sitzung):
    t0 = time.time(); umschrift = voxtral(pcm); t_vox = round(time.time() - t0, 2)
    uebers, t_claude1, t_claude = claude_erster_satz(sitzung, umschrift)
    skip = "[SKIP]" in uebers
    t_ton = None if skip else asyncio.run(vorlesen_erster_ton(re.split(r"(?<=[.!?])\s", uebers)[0]))
    erster_ton = None if t_ton is None else round(t_vox + t_claude1 + t_ton, 2)
    return {"art": art, "vorlage": vorlage, "umschrift": umschrift, "uebersetzung": uebers, "ausgelassen": skip,
            "voxtral_s": t_vox, "claude_erster_satz_s": t_claude1, "claude_ganz_s": t_claude,
            "vorlesen_erster_ton_s": t_ton, "erster_ton_ab_satzende_s": erster_ton}

sitzung = str(uuid.uuid4())
httpx.post(CLAUDE_URL + "/vorwaermen", headers={"Authorization": f"Bearer {CLAUDE_TOKEN}"}, timeout=60,
           json={"sitzung": sitzung, "ziel": "sr", "text": "-"})
ergebnis = []
for name, dialekt, mundart, hoch in SAETZE:
    ergebnis.append(lauf("mundart", mundart, suisse_tts(name, dialekt, mundart), sitzung))
    ergebnis.append(lauf("hochdeutsch", hoch, google_tts(f"{name}-hoch", hoch, "de-DE", "de-DE-Chirp3-HD-Charon"), sitzung))
    print(json.dumps(ergebnis[-2:], ensure_ascii=False), file=sys.stderr)
for i, s in enumerate(SERBISCH):
    ergebnis.append(lauf("serbisch", s, google_tts(f"sr{i}", s, "sr-RS", "sr-RS-Standard-A"), sitzung))
print(json.dumps(ergebnis, ensure_ascii=False, indent=1))
