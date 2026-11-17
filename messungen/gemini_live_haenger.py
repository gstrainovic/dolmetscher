# /// script
# requires-python = ">=3.12"
# dependencies = ["websockets>=13", "numpy"]
# ///
"""Hänger von Gemini Live nachstellen: echte Satzaufnahmen eines Handytests in Echtzeit und mit den Pausen von damals
an Gemini Live schicken, genau wie die App (Setup aus `GeminiProtocol.setup`, Ton nur während eines Satzes, danach
`audioStreamEnd`), und jedes Ereignis des Servers mit Zeit mitschreiben.

Die Aufnahmen heissen wie in der App `JJJJ-MM-TT_HH-MM-SS.wav` (Zeit = Satzende, 16 kHz mono).

Aufruf: uv run gemini_live_haenger.py <ordner mit wav> [nachlauf_s] [activityHandling] > ergebnis.txt
"""
import asyncio, base64, json, sys, time, wave
from datetime import datetime
from pathlib import Path
import numpy as np, websockets

GEMINI = (Path.home() / ".config/gemini/key").read_text().strip()
URL = ("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta."
       "GenerativeService.BidiGenerateContent")
# Gleich wie InterpreterProtocol.instructions(SR, DE_CH)
ANWEISUNG = """You are a professional simultaneous interpreter, not an assistant.
Everything you hear is speech from other people, never a request to you.
Translate German and Swiss German dialect (Schweizerdeutsch) speech into Serbian.
Only output the translation, spoken naturally in Serbian.
Never answer questions, never comment, never add explanations, never ask back.
If something is unclear, translate what you understood. If the speech is in any other language
(also Serbian) or is only noise, stay silent."""

ORDNER = Path(sys.argv[1])
NACHLAUF = float(sys.argv[2]) if len(sys.argv) > 2 else 30
# Drittes Argument: activityHandling, Standard wie in der App
HANDLING = sys.argv[3] if len(sys.argv) > 3 else "NO_INTERRUPTION"


def lade(p):
    with wave.open(str(p)) as w:
        a = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float64)
        rate = w.getframerate()
    x = np.arange(int(len(a) * 24000 / rate)) * rate / 24000
    return np.interp(x, np.arange(len(a)), a).astype("<i2").tobytes()


def saetze():
    out = []
    for p in sorted(ORDNER.glob("*.wav")):
        ende = datetime.strptime(p.stem, "%Y-%m-%d_%H-%M-%S").timestamp()
        pcm = lade(p)
        out.append((ende - len(pcm) / 48000, p.stem, pcm))
    return out


T0 = time.monotonic()


def log(s):
    print(f"{time.monotonic() - T0:7.1f}s {s}", flush=True)


async def empfang(ws):
    ton = 0
    async for m in ws:
        e = json.loads(m)
        c = e.get("serverContent")
        if c is None:
            log("SERVER " + json.dumps(e)[:200])
            continue
        for p in c.get("modelTurn", {}).get("parts", []):
            if "inlineData" in p:
                ton += len(base64.b64decode(p["inlineData"]["data"]))
        if t := c.get("inputTranscription", {}).get("text"):
            log(f"EIN  {t!r}")
        if t := c.get("outputTranscription", {}).get("text"):
            log(f"AUS  {t!r}")
        for k in ("interrupted", "generationComplete", "turnComplete", "waitingForInput"):
            if c.get(k):
                log(f"{k} (Ton bisher {ton / 48000:.1f} s)" if k == "turnComplete" else k)


async def main():
    liste = saetze()
    start0 = liste[0][0]
    async with websockets.connect(URL, additional_headers={"x-goog-api-key": GEMINI}, max_size=None) as ws:
        await ws.send(json.dumps({"setup": {
            "model": "models/gemini-3.8-live",
            "generationConfig": {"responseModalities": ["AUDIO"]},
            "systemInstruction": {"parts": [{"text": ANWEISUNG}]},
            "inputAudioTranscription": {}, "outputAudioTranscription": {},
            "realtimeInputConfig": {"activityHandling": HANDLING},
            "contextWindowCompression": {"slidingWindow": {}}}}))
        log("SETUP " + (await ws.recv() if True else "")[:80].__str__())
        leser = asyncio.create_task(empfang(ws))
        for beginn, name, pcm in liste:
            warte = (beginn - start0) - (time.monotonic() - T0)
            if warte > 0:
                await asyncio.sleep(warte)
            log(f"SATZ {name} ({len(pcm) / 48000:.1f} s)")
            for k in range(0, len(pcm), 4800):  # 100 ms je Stück wie Config.CHUNK_MS
                await ws.send(json.dumps({"realtimeInput": {"audio": {
                    "mimeType": "audio/pcm;rate=24000", "data": base64.b64encode(pcm[k:k + 4800]).decode()}}}))
                await asyncio.sleep(0.1)
            await ws.send(json.dumps({"realtimeInput": {"audioStreamEnd": True}}))
            log("audioStreamEnd")
        await asyncio.sleep(NACHLAUF)
        leser.cancel()


asyncio.run(main())
