# Serbische Stimmen

Welche Stimme spricht Claudes serbische Übersetzung am besten und am günstigsten? Gemessen mit sechs Sätzen aus dem
Altersheim-Alltag (`messungen/serbische_stimmen.py`, Ergebnis `messungen/serbische-stimmen.json`, je Stimme eine
Hörprobe in `messungen/serbische-hoerproben/`, Satz «Gospođo Müller, vreme je za vaše tablete. Hoćete li čašu
vode?»). Verständlichkeit: Gemini 3.8 Flash schreibt den Ton zurück in Text, gezählt sind die falschen Wörter
gegen die Vorlage. Sie unterscheidet die Stimmen kaum; wie natürlich eine Stimme klingt, entscheidet das Gehör.

Kosten je Monat für 120 Minuten Sprache an 22 Arbeitstagen, rund 2.4 Millionen Zeichen (Mengen aus
`docs/varianten.md`).

Hörurteil: Piper, Google Chirp3-HD und Google Standard klingen schlecht; ElevenLabs v3, Gemini 3.8 Flash
TTS und Flash-Lite TTS passen. RHVoice, AlfaNum, OpenAI und Gemini Live sind noch nicht gehört.

## Auf dem Handy (ohne Netz, gratis)

| Stimme | Art | Zeit je Satz | Wortfehler | Kosten | Quelle |
| --- | --- | --- | --- | --- | --- |
| Piper `sr_RS-serbski_institut-medium` | neuronal (VITS), läuft über sherpa-onnx in der App | 0.16 bis 0.4 s auf dem Laptop, auf dem Handy ungemessen | 0 bis 14 % | 0 | k2-fsa.github.io/sherpa/onnx/tts/all/Serbian; Modell huggingface.co/rhasspy/piper-voices |
| RHVoice «Dragana» | Systemstimme (App im Play Store und auf F-Droid) | ungemessen, nur am Handy | ungemessen | 0 | rhvoice.org/sr-voices |
| AlfaNum TTS SER | Systemstimme eines serbischen Herstellers (Novi Sad), Frau und Mann, aktualisiert 28.08.2025 | ungemessen | ungemessen | Preis nicht veröffentlicht, 10 Tage Vollversion über info@alfanum.co.rs | Play Store `alfanum.co.rs.alfanumtts` |
| Google-Sprachausgabe | Systemstimme, auf den meisten Android-Handys | | | 0 | klingt holzig |

Bei sherpa-onnx ist Piper `serbski_institut` das einzige serbische Modell. Ein weiteres Piper-Modell liegt unter
huggingface.co/phantom9623/piper-serbian-tts (nicht gemessen).

## In der Cloud

| Stimme | Zeit je Satz | Wortfehler | je Monat | Preisquelle |
| --- | --- | --- | --- | --- |
| Google Cloud Standard `sr-RS-Standard-B` | 0.23 bis 0.51 s | 0 bis 9 % | 0 (4 Millionen Zeichen im Monat gratis, danach USD 4 je Million) | cloud.google.com/text-to-speech/pricing |
| Google Cloud Chirp3-HD (`Kore`, `Charon` und 28 weitere) | 0.47 bis 0.77 s | 0 bis 14 % | ≈ USD 42 (1 Million gratis, danach USD 30 je Million) | dito |
| Gemini 3.8 Flash TTS | 3.1 bis 3.6 s | 0 bis 9 % | ≈ USD 71 ab 2027 (USD 18 je Million Ton-Tokens, rund 25 je Sekunde) | dito; auf Google Cloud als «Preview» geführt |
| Gemini 3.8 Flash-Lite TTS | 1.6 bis 2.6 s | 0 bis 29 % | ≈ USD 47 ab 2027 (USD 12 je Million) | dito; «Preview» |
| ElevenLabs v3 | 1.2 bis 2.6 s | 0 bis 9 % | ≈ USD 190 (USD 0.08 je 1000 Zeichen) | elevenlabs.io/pricing/api |
| OpenAI `gpt-realtime-2.1-mini` (liest vor) | erster Ton 0.6 bis 1.0 s | 8 von 8 wortgetreu | ≈ USD 65 | `docs/varianten.md` |
| Gemini Live `gemini-3.8-live` (liest vor) | erster Ton 0.35 bis 0.7 s | 8 von 8 wortgetreu | ≈ USD 53 bis 73 | dito |
| Azure `sr-RS-SophieNeural` | ungemessen, kein Schlüssel | | ≈ USD 29 | dito |

ElevenLabs Multilingual v2 und Flash v2.5 lehnten Serbisch ab (HTTP 400 mit `language_code: sr`). Die Zeit je
Satz ist bis zur fertigen Datei gemessen; Google Cloud und Piper sind auch ohne Strom schnell genug.
