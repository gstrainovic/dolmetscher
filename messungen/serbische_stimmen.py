# /// script
# requires-python = ">=3.12"
# dependencies = ["httpx", "piper-tts"]
# ///
"""Serbische Stimmen im Vergleich: dieselben Sätze bei jedem Anbieter sprechen lassen, Zeit messen, Hörproben
ablegen und die Verständlichkeit prüfen (Gemini 3.8 Flash schreibt den Ton zurück in Text, Vergleich mit der Vorlage).

Aufruf: uv run serbische_stimmen.py <ordner für hörproben> > serbische-stimmen.json
"""
import base64, io, json, os, subprocess, sys, time, unicodedata, wave
from pathlib import Path
import httpx

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
CFG = Path.home() / ".config"
GEMINI = (CFG / "gemini/key").read_text().strip()
OPENAI = (CFG / "openai/key").read_text().strip()
ELEVEN = (CFG / "elevenlabs/key").read_text().strip()
PROJEKT = "gen-lang-client-0650867108"

SAETZE = [
    "Molim te, donesi iz kuhinje dva krompira, tri šargarepe i zečetinu.",
    "Imam bolove u stomaku i malo kijavicu.",
    "Da li biste mi molim vas doneli papuče sa stepenica?",
    "Moja ćerka dolazi u nedelju u posetu.",
    "Gospođo Müller, vreme je za vaše tablete. Hoćete li čašu vode?",
    "Ne brinite, sve je u redu. Sestra dolazi za pet minuta.",
]

def wav(pcm, rate):
    b = io.BytesIO()
    with wave.open(b, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate); w.writeframes(pcm)
    return b.getvalue()

def google(stimme):
    tok = subprocess.run(["gcloud", "auth", "print-access-token"], capture_output=True, text=True).stdout.strip()
    def f(text):
        r = httpx.post("https://texttospeech.googleapis.com/v1/text:synthesize", timeout=60,
                       headers={"Authorization": f"Bearer {tok}", "x-goog-user-project": PROJEKT},
                       json={"input": {"text": text}, "voice": {"languageCode": "sr-RS", "name": stimme},
                             "audioConfig": {"audioEncoding": "MP3"}})
        r.raise_for_status()
        return base64.b64decode(r.json()["audioContent"]), "mp3"
    return f

def gemini_tts(modell, stimme="Kore"):
    def f(text):
        r = httpx.post(f"https://generativelanguage.googleapis.com/v1beta/models/{modell}:generateContent",
                       headers={"x-goog-api-key": GEMINI}, timeout=60, json={"contents": [{"parts": [{"text": f"Read aloud in Serbian: {text}"}]}],
                                         "generationConfig": {"responseModalities": ["AUDIO"], "speechConfig": {
                                             "voiceConfig": {"prebuiltVoiceConfig": {"voiceName": stimme}}}}})
        r.raise_for_status()
        pcm = base64.b64decode(r.json()["candidates"][0]["content"]["parts"][0]["inlineData"]["data"])
        return wav(pcm, 24000), "wav"
    return f

def eleven(modell, stimme="21m00Tcm4TlvDq8ikWAM"):
    def f(text):
        r = httpx.post(f"https://api.elevenlabs.io/v1/text-to-speech/{stimme}?output_format=mp3_44100_128", timeout=60,
                       headers={"xi-api-key": ELEVEN}, json={"text": text, "model_id": modell, "language_code": "sr"})
        r.raise_for_status()
        return r.content, "mp3"
    return f

def piper():
    from piper import PiperVoice
    modell = OUT.parent / "piper" / "sr_RS-serbski_institut-medium.onnx"
    if not modell.exists():
        modell.parent.mkdir(parents=True, exist_ok=True)
        basis = "https://huggingface.co/rhasspy/piper-voices/resolve/main/sr/sr_RS/serbski_institut/medium/"
        for name in (modell.name, modell.name + ".json"):
            (modell.parent / name).write_bytes(httpx.get(basis + name, follow_redirects=True, timeout=120).content)
    stimme = PiperVoice.load(str(modell))
    def f(text):
        b = io.BytesIO()
        with wave.open(b, "wb") as w:
            stimme.synthesize_wav(text, w)
        return b.getvalue(), "wav"
    return f

KANDIDATEN = {
    "google-chirp3-hd-kore": google("sr-RS-Chirp3-HD-Kore"),
    "google-chirp3-hd-charon": google("sr-RS-Chirp3-HD-Charon"),
    "google-standard-b": google("sr-RS-Standard-B"),
    "gemini-3.8-flash-tts": gemini_tts("gemini-3.8-flash-tts"),
    "gemini-3.8-flash-lite-tts": gemini_tts("gemini-3.8-flash-lite-tts"),
    "elevenlabs-v3": eleven("eleven_v3"),
    "elevenlabs-multilingual-v2": eleven("eleven_multilingual_v2"),
    "elevenlabs-flash-v2.5": eleven("eleven_flash_v2_5"),
}

def zurueck(ton, art):
    mime = "audio/mpeg" if art == "mp3" else "audio/wav"
    r = httpx.post("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent",
                   headers={"x-goog-api-key": GEMINI}, timeout=60, json={"contents": [{"parts": [
                       {"inlineData": {"mimeType": mime, "data": base64.b64encode(ton).decode()}},
                       {"text": "Transcribe this Serbian speech exactly, in Latin script. Output only the transcript."}]}]})
    r.raise_for_status()
    return r.json()["candidates"][0]["content"]["parts"][0]["text"].strip()

def woerter(t):
    t = unicodedata.normalize("NFC", t.lower())
    return "".join(c if c.isalnum() or c.isspace() else " " for c in t).split()

def wortfehler(ref, hyp):
    a, b = woerter(ref), woerter(hyp)
    d = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        p, d[0] = d[0], i
        for j in range(1, len(b) + 1):
            p, d[j] = d[j], min(d[j] + 1, d[j - 1] + 1, p + (a[i - 1] != b[j - 1]))
    return d[len(b)] / max(len(a), 1)

def main():
    try:
        KANDIDATEN["piper-serbski-institut"] = piper()
    except Exception as e:
        print("piper", e, file=sys.stderr)
    alle = []
    for name, f in KANDIDATEN.items():
        for i, s in enumerate(SAETZE):
            zeile = {"stimme": name, "satz": i, "vorlage": s}
            try:
                t0 = time.time()
                ton, art = f(s)
                zeile["sekunden"] = round(time.time() - t0, 2)
                (OUT / f"{name}-{i}.{art}").write_bytes(ton)
                zeile["zurueck"] = zurueck(ton, art)
                zeile["wortfehler"] = round(wortfehler(s, zeile["zurueck"]), 2)
            except Exception as e:
                msg = str(e)
                for k in (GEMINI, OPENAI, ELEVEN):
                    msg = msg.replace(k, "***")
                zeile["fehler"] = msg[:300]
            print(name, i, zeile.get("sekunden"), zeile.get("wortfehler"), zeile.get("fehler", ""), file=sys.stderr, flush=True)
            alle.append(zeile)
    print(json.dumps(alle, ensure_ascii=False, indent=1))

main()
