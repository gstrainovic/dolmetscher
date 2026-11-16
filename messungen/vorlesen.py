# /// script
# requires-python = ">=3.12"
# dependencies = ["websockets>=13"]
# ///
"""Lesen Gemini Live und OpenAI Realtime mini Claudes serbische Übersetzung wortgetreu vor?

Je Modell eine Sitzung, die Sätze nacheinander als Text. Gemessen: Zeit vom Senden bis zum ersten Ton,
gesprochener Text (Untertitel des Modells) gegen die Vorlage, Tokens laut Antwort.
Aufruf: uv run vorlesen.py > vorlesen.json
"""
import asyncio, json, sys, time
from pathlib import Path
import websockets

SAETZE = [
    "Molim te, donesi iz kuhinje dva krompira, tri šargarepe i zečetinu.",
    "Uzimamo i zečetinu za ovu nedelju.",
    "Dva krompira i jednu šargarepu.",
    "Iz kuhinje.",
    "I zeca, molim te.",
    "Imam bolove u stomaku i malo kijavicu.",
    "Da li biste mi molim vas doneli papuče sa stepenica?",
    "Moja ćerka dolazi u nedelju u posetu.",
]
ANWEISUNG = ("You are a text-to-speech voice. Read the user's Serbian text aloud exactly as written, word for "
             "word, in natural Serbian. Do not translate, answer, comment, add or leave out anything.")

async def openai(modell="gpt-realtime-2.1-mini"):
    key = (Path.home() / ".config/openai/key").read_text().strip()
    url = f"wss://api.openai.com/v1/realtime?model={modell}"
    out = []
    async with websockets.connect(url, additional_headers={"Authorization": f"Bearer {key}"}, max_size=None) as ws:
        await ws.send(json.dumps({"type": "session.update", "session": {
            "type": "realtime", "instructions": ANWEISUNG, "output_modalities": ["audio"],
            "audio": {"input": {"turn_detection": None},
                      "output": {"format": {"type": "audio/pcm", "rate": 24000}, "voice": "marin"}}}}))
        for s in SAETZE:
            await ws.send(json.dumps({"type": "conversation.item.create", "item": {
                "type": "message", "role": "user", "content": [{"type": "input_text", "text": s}]}}))
            t0 = time.time()
            await ws.send(json.dumps({"type": "response.create"}))
            erstes, text, usage, ton = None, "", None, 0
            while True:
                e = json.loads(await asyncio.wait_for(ws.recv(), 30))
                t = e.get("type")
                if t == "response.output_audio.delta":
                    erstes = erstes or round(time.time() - t0, 2)
                    ton += len(e["delta"]) * 3 // 4
                elif t == "response.output_audio_transcript.delta":
                    text += e.get("delta", "")
                elif t == "error":
                    text += f" [FEHLER {e.get('error', {}).get('message')}]"
                elif t == "response.done":
                    usage = e["response"].get("usage")
                    break
            out.append({"modell": modell, "vorlage": s, "gesprochen": text.strip(), "erster_ton_s": erstes,
                        "ton_s": round(ton / 48000, 1), "usage": usage})
            print(modell, erstes, text.strip(), file=sys.stderr, flush=True)
    return out

async def gemini(modell="gemini-3.8-live"):
    key = (Path.home() / ".config/gemini/key").read_text().strip()
    url = ("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta."
           f"GenerativeService.BidiGenerateContent?key={key}")
    out = []
    async with websockets.connect(url, max_size=None) as ws:
        await ws.send(json.dumps({"setup": {"model": f"models/{modell}",
                                            "generationConfig": {"responseModalities": ["AUDIO"]},
                                            "systemInstruction": {"parts": [{"text": ANWEISUNG}]},
                                            "outputAudioTranscription": {}}}))
        await ws.recv()
        for s in SAETZE:
            t0 = time.time()
            await ws.send(json.dumps({"realtimeInput": {"text": s}}))
            erstes, text, usage, ton = None, "", None, 0
            while True:
                e = json.loads(await asyncio.wait_for(ws.recv(), 30))
                usage = e.get("usageMetadata", usage)
                c = e.get("serverContent", {})
                for p in c.get("modelTurn", {}).get("parts", []):
                    if "inlineData" in p:
                        erstes = erstes or round(time.time() - t0, 2)
                        ton += len(p["inlineData"]["data"]) * 3 // 4
                text += c.get("outputTranscription", {}).get("text", "")
                if c.get("turnComplete"):
                    break
            out.append({"modell": modell, "vorlage": s, "gesprochen": text.strip(), "erster_ton_s": erstes,
                        "ton_s": round(ton / 48000, 1), "usage": usage})
            print(modell, erstes, text.strip(), file=sys.stderr, flush=True)
    return out

async def main():
    print(json.dumps(await openai() + await gemini(), ensure_ascii=False, indent=1))

asyncio.run(main())
