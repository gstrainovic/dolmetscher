#!/usr/bin/env bash
# Lädt sherpa-onnx (Android-AAR, 49 MB, deshalb nicht im Repo) und das Silero-VAD-Modell für die Sprecherkennung.
# Vor dem ersten Bau ausführen; Version hier und in app/build.gradle.kts gleich halten.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=2.0.1
mkdir -p app/libs app/src/main/assets
aar="app/libs/sherpa-onnx-$VERSION.aar"
[ -f "$aar" ] || curl -fL -o "$aar" "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$VERSION/sherpa-onnx-$VERSION.aar"
for name in silero_vad.onnx ten-vad.onnx; do
  modell="app/src/main/assets/$name"
  [ -f "$modell" ] || curl -fL -o "$modell" "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$name"
done
ls -la "$aar" app/src/main/assets
