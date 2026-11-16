#!/usr/bin/env bash
# Erzeugt secrets.properties (ignoriert, nie im Repo) aus den Schlüsseldateien unter ~/.config.
# Fehlt eine Datei, bleibt der Wert leer; die App meldet dann beim Start der Variante den fehlenden Schlüssel.
set -euo pipefail
cd "$(dirname "$0")/.."
lies() { [ -f "$1" ] && tr -d '\n\r' < "$1" || true; }
mistral=$(grep -h '^MISTRAL_API_KEY=' ~/.config/dolmetscher/mistral.env ~/projects/ki-tel/.secrets/mistral.env 2>/dev/null | head -1 | cut -d= -f2- | tr -d "'\"" || true)
umask 077
cat > secrets.properties <<EOF
GEMINI_KEY=$(lies ~/.config/gemini/key)
SUISSE_KEY=$(lies ~/.config/suisse-speech/key)
MISTRAL_KEY=$mistral
OPENAI_KEY=$(lies ~/.config/openai/key)
AZURE_KEY=$(lies ~/.config/azure-speech/key)
AZURE_REGION=$(lies ~/.config/azure-speech/region)
CLAUDE_SERVER_URL=$(lies ~/.config/dolmetscher/server-url)
CLAUDE_SERVER_TOKEN=$(lies ~/.config/dolmetscher/server-token)
EOF
# Übersicht ohne Werte
while IFS='=' read -r k v; do printf '%-20s %s\n' "$k" "$([ -n "$v" ] && echo gesetzt || echo FEHLT)"; done < secrets.properties
