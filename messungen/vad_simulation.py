# /// script
# requires-python = ">=3.12"
# dependencies = ["silero-vad", "onnxruntime", "numpy"]
# ///
"""Sprecherkennung der App auf einem Mitschnitt nachrechnen (16 kHz PCM16): Silero mit Verstärkung, Tor mit
Vorlauf, zwei Rahmen zum Öffnen und Nachlauf wie in SpeechGate; Variante mit zweiter, tieferer Schwelle zum
Offenhalten (Silero `neg_threshold`, Standard Schwelle − 0.15). Gibt die durchgelassenen Abschnitte aus.

Aufruf: uv run vad_simulation.py mitschnitt.pcm
"""
import sys
import numpy as np
from silero_vad import load_silero_vad
import torch

pcm = np.fromfile(sys.argv[1], dtype="<i2").astype(np.float32) / 32768
modell = load_silero_vad(onnx=True)
RAHMEN = 1600  # 100 ms

def wahrscheinlichkeiten(verstaerkung):
    modell.reset_states()
    x = np.clip(pcm * verstaerkung, -1, 1)
    p = [float(modell(torch.from_numpy(x[i:i + 512]), 16000)) for i in range(0, len(x) - 512, 512)]
    # je 100-ms-Rahmen das Maximum der Fenster darin
    return [max(p[int(r * RAHMEN / 512):int((r + 1) * RAHMEN / 512) + 1] or [0]) for r in range(len(x) // RAHMEN)]

def tor(p, start, halten, nachlauf=6):
    offen, ruhig, laut, von, teile = False, 0, 0, 0, []
    for i, w in enumerate(p):
        if offen:
            ruhig = 0 if w >= halten else ruhig + 1
            if ruhig >= nachlauf:
                teile.append((von, i)); offen = False; laut = 0
        else:
            laut = laut + 1 if w >= start else 0
            if laut >= 2:
                offen, ruhig, von = True, 0, max(0, i - 6)
    if offen:
        teile.append((von, len(p)))
    return [f"{a / 10:.1f}-{b / 10:.1f}s" for a, b in teile]

for verstaerkung in (4, 8, 16):
    p = wahrscheinlichkeiten(verstaerkung)
    print(f"x{verstaerkung} je Sekunde:", " ".join(f"{max(p[s * 10:(s + 1) * 10]):.2f}" for s in range(len(p) // 10)))
    for start in (0.5, 0.3):
        for halten in (start, start - 0.15):
            print(f"  x{verstaerkung} start {start} halten {halten:.2f}:", tor(p, start, halten))
