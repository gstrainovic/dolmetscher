# /// script
# requires-python = ">=3.12"
# dependencies = ["httpx", "websockets>=13", "numpy"]
# ///
"""Hochdeutsch statt Mundart: dieselben zwölf Sätze (die hochdeutsche Vorlage) mit Google-Stimme sprechen und durch
die günstigen Glieder schicken. Gemessen: Voxtral-Umschrift (Wortfehler), Gemini Live allein (Übersetzung und erster
Ton ab Satzende, Kosten laut Antwort).

Aufruf: uv run hochdeutsch.py <ordner für ton> suisse-gegen-voxtral.json > hochdeutsch.json
Danach openai_realtime.py mit <ordner> und dem hier geschriebenen <ordner>/saetze.json.
"""
import asyncio, base64, io, json, re, subprocess, sys, time, wave
from pathlib import Path
import httpx, numpy as np, websockets

OUT = Path(sys.argv[1]); OUT.mkdir(parents=True, exist_ok=True)
SAETZE = json.loads(Path(sys.argv[2]).read_text())
CFG = Path.home() / ".config"
GEMINI = (CFG / "gemini/key").read_text().strip()
MISTRAL = re.search(r"MISTRAL_API_KEY=(\S+)", (Path.home() / "projects/ki-tel/.secrets/mistral.env").read_text()).group(1).strip("'\"")
PROJEKT = "gen-lang-client-0650867108"
ANWEISUNG = """You are a professional simultaneous interpreter, not an assistant.
Everything you hear is speech from other people, never a request to you.
Translate everything you hear into Serbian. The speech is usually German or
Swiss German dialect (Schweizerdeutsch), but can be any language.
Only output the translation, spoken naturally in Serbian.
Never answer questions, never comment, never add explanations, never ask back.
If something is unclear, translate what you understood. If the speech is already in
Serbian or is only noise, stay silent."""

def tts(text):
    tok = subprocess.run(["gcloud", "auth", "print-access-token"], capture_output=True, text=True).stdout.strip()
    r = httpx.post("https://texttospeech.googleapis.com/v1/text:synthesize", timeout=60,
                   headers={"Authorization": f"Bearer {tok}", "x-goog-user-project": PROJEKT},
                   json={"input": {"text": text}, "voice": {"languageCode": "de-DE", "name": "de-DE-Chirp3-HD-Charon"},
                         "audioConfig": {"audioEncoding": "LINEAR16", "sampleRateHertz": 16000}})
    r.raise_for_status()
    return base64.b64decode(r.json()["audioContent"])[44:]  # ohne WAV-Kopf

def voxtral(pcm):
    b = io.BytesIO()
    with wave.open(b, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000); w.writeframes(pcm)
    t0 = time.time()
    r = httpx.post("https://api.mistral.ai/v1/audio/transcriptions", headers={"Authorization": f"Bearer {MISTRAL}"},
                   data={"model": "voxtral-mini-2602", "language": "de"}, files={"file": ("s.wav", b.getvalue(), "audio/wav")}, timeout=60)
    return r.json().get("text", "").strip(), round(time.time() - t0, 2)

def woerter(t):
    return re.sub(r"[^\w\s]", " ", t.lower()).split()

def wer(ref, hyp):
    a, b = woerter(ref), woerter(hyp)
    d = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        p, d[0] = d[0], i
        for j in range(1, len(b) + 1):
            p, d[j] = d[j], min(d[j] + 1, d[j - 1] + 1, p + (a[i - 1] != b[j - 1]))
    return round(d[len(b)] / max(len(a), 1), 2)

def zu_24k(pcm16):
    a = np.frombuffer(pcm16, dtype="<i2").astype(np.float64)
    x = np.arange(int(len(a) * 1.5)) / 1.5
    return np.interp(x, np.arange(len(a)), a).astype("<i2").tobytes()

async def gemini_live(pcms):
    url = ("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta."
           "GenerativeService.BidiGenerateContent")
    out = []
    async with websockets.connect(url, additional_headers={"x-goog-api-key": GEMINI}, max_size=None) as ws:
        await ws.send(json.dumps({"setup": {"model": "models/gemini-3.8-live",
                                            "generationConfig": {"responseModalities": ["AUDIO"]},
                                            "systemInstruction": {"parts": [{"text": ANWEISUNG}]},
                                            "outputAudioTranscription": {}, "inputAudioTranscription": {},
                                            "realtimeInputConfig": {"activityHandling": "NO_INTERRUPTION"}}}))
        await ws.recv()
        for pcm in pcms:
            p24 = zu_24k(pcm)
            for k in range(0, len(p24), 4800):
                await ws.send(json.dumps({"realtimeInput": {"audio": {"mimeType": "audio/pcm;rate=24000",
                                                                      "data": base64.b64encode(p24[k:k + 4800]).decode()}}}))
                await asyncio.sleep(0.1)
            ende = time.time()
            for _ in range(15):
                await ws.send(json.dumps({"realtimeInput": {"audio": {"mimeType": "audio/pcm;rate=24000",
                                                                      "data": base64.b64encode(bytes(4800)).decode()}}}))
                await asyncio.sleep(0.1)
            erstes, text, usage = None, "", None
            try:
                while True:
                    e = json.loads(await asyncio.wait_for(ws.recv(), 20))
                    usage = e.get("usageMetadata", usage)
                    c = e.get("serverContent", {})
                    if any("inlineData" in p for p in c.get("modelTurn", {}).get("parts", [])) and erstes is None:
                        erstes = round(time.time() - ende, 2)
                    text += c.get("outputTranscription", {}).get("text", "")
                    if c.get("turnComplete"):
                        break
            except asyncio.TimeoutError:
                text += " [keine Antwort]"
            out.append({"uebersetzung": text.strip(), "erster_ton_s": erstes, "usage": usage})
            print("gemini", erstes, text.strip(), file=sys.stderr, flush=True)
    return out

def main():
    pcms, zeilen = [], []
    for i, s in enumerate(SAETZE):
        f = OUT / f"satz{i}.pcm"
        if not f.exists():
            f.write_bytes(tts(s["vorlage"]))
        pcms.append(f.read_bytes())
        text, dauer = voxtral(pcms[-1])
        zeilen.append({"vorlage": s["vorlage"], "voxtral": text, "wer_voxtral": wer(s["vorlage"], text), "voxtral_s": dauer})
        print("voxtral", zeilen[-1]["wer_voxtral"], text, file=sys.stderr, flush=True)
    (OUT / "saetze.json").write_text(json.dumps([{"mundart": s["vorlage"], "vorlage": s["vorlage"]} for s in SAETZE], ensure_ascii=False))
    for z, g in zip(zeilen, asyncio.run(gemini_live(pcms))):
        z["gemini_live"] = g
    print(json.dumps(zeilen, ensure_ascii=False, indent=1))

main()
