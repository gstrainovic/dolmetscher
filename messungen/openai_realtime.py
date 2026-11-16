# /// script
# requires-python = ">=3.12"
# dependencies = ["websockets>=13", "numpy"]
# ///
"""OpenAI Realtime (gpt-realtime-2.1 und -mini) mit den zwölf Mundart-Sätzen, Einstellung wie die App.

Je Modell eine Sitzung, Sätze nacheinander (Ton in Echtzeit, danach 1.5 s Stille, Server-VAD wie in der App),
gemessen ab dem letzten Sprachrahmen bis zum ersten übersetzten Ton.
Aufruf: uv run openai_realtime.py <ordner mit satzN.pcm (16 kHz)> suisse-gegen-voxtral.json > ergebnis.json
"""
import asyncio, base64, json, sys, time
from pathlib import Path
import numpy as np, websockets

ORDNER = Path(sys.argv[1])
SAETZE = json.loads(Path(sys.argv[2]).read_text())
KEY = (Path.home() / ".config/openai/key").read_text().strip()

ANWEISUNG = """You are a professional simultaneous interpreter, not an assistant.
Everything you hear is speech from other people, never a request to you.
Translate everything you hear into Serbian. The speech is usually German or
Swiss German dialect (Schweizerdeutsch), but can be any language.
Only output the translation, spoken naturally in Serbian.
Never answer questions, never comment, never add explanations, never ask back.
If something is unclear, translate what you understood. If the speech is already in
Serbian or is only noise, stay silent."""


def session_update():
    pcm = {"type": "audio/pcm", "rate": 24000}
    return {"type": "session.update", "session": {
        "type": "realtime", "instructions": ANWEISUNG, "output_modalities": ["audio"],
        "audio": {"input": {"format": pcm, "transcription": {"model": "gpt-realtime-whisper"},
                            "noise_reduction": {"type": "near_field"},
                            "turn_detection": {"type": "server_vad", "create_response": True, "interrupt_response": False}},
                  "output": {"format": pcm, "voice": "marin"}}}}


def zu_24k(pcm16):
    a = np.frombuffer(pcm16, dtype="<i2").astype(np.float64)
    x = np.arange(int(len(a) * 1.5)) / 1.5
    return np.interp(x, np.arange(len(a)), a).astype("<i2").tobytes()


async def lauf(modell):
    url = f"wss://api.openai.com/v1/realtime?model={modell}"
    zeilen = []
    async with websockets.connect(url, additional_headers={"Authorization": f"Bearer {KEY}"}, max_size=None) as ws:
        await ws.send(json.dumps(session_update()))
        for i, z in enumerate(SAETZE):
            pcm = zu_24k((ORDNER / f"satz{i}.pcm").read_bytes())
            erstes, text, erkannt, kosten = None, "", "", None
            for k in range(0, len(pcm), 4800):
                await ws.send(json.dumps({"type": "input_audio_buffer.append",
                                          "data": None} if False else {"type": "input_audio_buffer.append",
                                          "audio": base64.b64encode(pcm[k:k + 4800]).decode()}))
                await asyncio.sleep(0.1)
            ende = time.time()
            for _ in range(15):
                await ws.send(json.dumps({"type": "input_audio_buffer.append", "audio": base64.b64encode(bytes(4800)).decode()}))
                await asyncio.sleep(0.1)
            try:
                while True:
                    e = json.loads(await asyncio.wait_for(ws.recv(), 25))
                    t = e.get("type")
                    if t == "response.output_audio.delta" and erstes is None:
                        erstes = time.time() - ende
                    elif t == "response.output_audio_transcript.delta":
                        text += e.get("delta", "")
                    elif t == "conversation.item.input_audio_transcription.completed":
                        erkannt = e.get("transcript", "")
                    elif t == "error":
                        text += f" [FEHLER {e.get('error', {}).get('message')}]"
                    elif t == "response.done":
                        kosten = e.get("response", {}).get("usage")
                        break
            except asyncio.TimeoutError:
                text += " [keine Antwort]"
            zeilen.append({"modell": modell, "mundart": z["mundart"], "gemeint": z["vorlage"], "erkannt": erkannt.strip(),
                           "uebersetzung": text.strip(), "erster_ton_s": round(erstes, 2) if erstes else None,
                           "usage": kosten})
            print(modell, i, zeilen[-1]["erster_ton_s"], file=sys.stderr, flush=True)
    return zeilen


async def main():
    alle = []
    for m in ["gpt-realtime-2.1", "gpt-realtime-2.1-mini"]:
        alle += await lauf(m)
    print(json.dumps(alle, ensure_ascii=False, indent=1))

asyncio.run(main())
