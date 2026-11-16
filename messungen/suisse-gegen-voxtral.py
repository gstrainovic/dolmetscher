# /// script
# requires-python = ">=3.12"
# dependencies = ["websockets>=13", "httpx", "jiwer"]
# ///
"""Mundart-Sätze (Suisse-TTS) mit Suisse Speech live und Voxtral Batch erkennen; Wortfehler gegen Hochdeutsch.

Aufruf: vergleich.py <ordner> <mistral.env>
"""
import asyncio, base64, io, json, re, sys, wave
from pathlib import Path
import httpx, jiwer, websockets

OUT = Path(sys.argv[1])
MISTRAL = re.search(r"MISTRAL_API_KEY=(\S+)", Path(sys.argv[2]).read_text()).group(1).strip("'\"")
SUISSE = (Path.home() / ".config/suisse-speech/key").read_text().strip()
VOCAB = ["Chüngel", "Znüni", "Zvieri", "Zmittag", "Znacht", "Zmorge", "Gipfeli", "Anke", "Konfi",
         "Schoggi", "Nastuech", "Finke", "Grosi", "Grossätti", "Spitex", "Rollator", "Tschope", "Pfnüsel",
         "Häfeli", "Stägeli", "Lavabo", "Poschte"]

# (Datei, Dialekt, Mundart für die Stimme, Hochdeutsch als Vorlage)
SAETZE = [
    ("satz0", "zurich", "Hüt git's Chüngel mit Herdöpfelstock.", "Heute gibt es Kaninchen mit Kartoffelstock."),
    ("satz1", "bern", "Mir hei früecher dihei Chüngel gha.", "Wir hatten früher zu Hause Kaninchen."),
    ("satz2", "zurich", "Dä Chüngel im Garte frisst Rüebli.", "Das Kaninchen im Garten frisst Karotten."),
    ("satz3", "bern", "Wosch no chli Chüngelfleisch?", "Willst du noch etwas Kaninchenfleisch?"),
    ("satz4", "zurich", "Wänd Sie zum Zvieri es Gipfeli mit Anke und Konfi?", "Wollen Sie zum Zvieri ein Gipfeli mit Butter und Konfitüre?"),
    ("satz5", "zurich", "Ich ha Buuchweh und es bitzeli Pfnüsel.", "Ich habe Bauchweh und ein bisschen Schnupfen."),
    ("satz6", "basel", "I due mi nid so guet fühle hüt, ich mag nüt ässe.", "Ich fühle mich heute nicht so gut, ich mag nichts essen."),
    ("satz7", "luzern", "Chönd Sie mer bitte d'Finke ufem Stägeli hole?", "Können Sie mir bitte die Hausschuhe auf der Treppe holen?"),
    ("satz8", "zurich", "Mini Tochter chunnt am Sunntig cho luege.", "Meine Tochter kommt am Sonntag zu Besuch."),
    ("satz9", "bern", "I ha mi Brülle verleit, hesch se gseh?", "Ich habe meine Brille verlegt, hast du sie gesehen?"),
    ("satz10", "basel", "Mir isch es chalt, chönnt ich no e Tschope ha?", "Mir ist kalt, könnte ich noch eine Jacke haben?"),
    ("satz11", "luzern", "Hend Sie mini Tablette scho brocht?", "Haben Sie meine Tabletten schon gebracht?"),
]

def tts(name, dialect, text):
    f = OUT / f"{name}.pcm"
    if not f.exists():
        r = httpx.post("https://api.suisse-speech.ch/v1/tts", headers={"X-API-Key": SUISSE}, timeout=60,
                       json={"text": text, "language": "de-CH", "dialect": dialect, "format": "pcm16",
                             "sample_rate_hz": 16000, "model": "studio"})
        r.raise_for_status()
        f.write_bytes(r.content)
    return f.read_bytes()

async def suisse(pcm):
    texte = []
    async with websockets.connect("wss://api.suisse-speech.ch/v1/stt/stream", additional_headers={"X-API-Key": SUISSE}) as ws:
        await ws.send(json.dumps({"type": "config", "lang": "de-CH", "finalize_on_pause": True, "vocabulary": VOCAB}))
        async def recv():
            async for m in ws:
                e = json.loads(m)
                if e["type"] == "transcript.final":
                    texte.append(e["text"])
                if e["type"] == "session.complete":
                    return
        r = asyncio.create_task(recv())
        for i in range(0, len(pcm), 3200):
            await ws.send(json.dumps({"type": "audio.append", "data": base64.b64encode(pcm[i:i + 3200]).decode()}))
            await asyncio.sleep(0.1)
        await ws.send(json.dumps({"type": "audio.end"}))
        await asyncio.wait_for(r, 20)
    return " ".join(texte)

def voxtral(pcm, bias):
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000); w.writeframes(pcm)
    data = {"model": "voxtral-mini-2602", "language": "de"}
    if bias:
        data["context_bias"] = ",".join(VOCAB)
    r = httpx.post("https://api.mistral.ai/v1/audio/transcriptions", headers={"Authorization": f"Bearer {MISTRAL}"},
                   data=data, files={"file": ("satz.wav", buf.getvalue(), "audio/wav")}, timeout=60)
    r.raise_for_status()
    return r.json()["text"].strip()

def wer(ref, hyp):
    norm = jiwer.Compose([jiwer.ToLowerCase(), jiwer.RemovePunctuation(), jiwer.RemoveMultipleSpaces(), jiwer.Strip(),
                          jiwer.ReduceToListOfListOfWords()])
    return jiwer.wer(ref, hyp, reference_transform=norm, hypothesis_transform=norm)

async def main():
    summe = {"Suisse": [], "Voxtral": [], "Voxtral+Wortschatz": []}
    zeilen = []
    for name, d, mundart, ref in SAETZE:
        pcm = tts(name, d, mundart)
        res = {"Suisse": await suisse(pcm), "Voxtral": voxtral(pcm, False), "Voxtral+Wortschatz": voxtral(pcm, True)}
        for k, v in res.items():
            summe[k].append(wer(ref, v))
        zeilen.append({"dialekt": d, "mundart": mundart, "vorlage": ref, **res,
                       **{f"wer_{k}": round(wer(ref, v), 2) for k, v in res.items()}})
    (OUT / "ergebnis.json").write_text(json.dumps(zeilen, ensure_ascii=False, indent=1))
    for k, v in summe.items():
        print(f"{k}: mittlere Wortfehlerrate {sum(v) / len(v):.0%}")

asyncio.run(main())
