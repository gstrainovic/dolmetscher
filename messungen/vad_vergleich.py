# /// script
# requires-python = ">=3.12"
# dependencies = ["sherpa-onnx", "numpy"]
# ///
"""Silero und TEN VAD auf Flüster-Stücken: höchste Sprachwahrscheinlichkeit je Datei, roh und verstärkt."""
import sys, wave
from pathlib import Path
import numpy as np, sherpa_onnx

ASSETS = Path(sys.argv[1])

def vad(art):
    c = sherpa_onnx.VadModelConfig(sample_rate=16000)
    if art == "silero":
        c.silero_vad.model = str(ASSETS / "silero_vad.onnx")
        return sherpa_onnx.VoiceActivityDetector(c, buffer_size_in_seconds=30), 512
    c.ten_vad.model = str(ASSETS / "ten-vad.onnx")
    return sherpa_onnx.VoiceActivityDetector(c, buffer_size_in_seconds=30), 256

for f in sorted(Path(sys.argv[2]).glob("*.wav")):
    with wave.open(str(f)) as w:
        a = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float32) / 32768
    zeile = [f.name]
    for art in ("silero", "ten"):
        for faktor in (1, 4):
            v, n = vad(art)
            x = np.clip(a * faktor, -1, 1)
            treffer = 0
            for i in range(0, len(x) - n, n):
                v.accept_waveform(x[i:i + n])
                treffer += v.is_speech_detected()
            zeile.append(f"{art}x{faktor}={treffer * n / 16000:.1f}s")
    print(" ".join(zeile), flush=True)
