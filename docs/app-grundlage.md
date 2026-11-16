# Grundlage für eine eigene Übersetzungs-App

Gesucht: ein offenes Android-Projekt auf GitHub, auf dem eine benutzerfreundliche Dolmetscher-App aufbaut. Die App
soll Deutsch und Schweizerdeutsch hören, Serbisch in die Brille sprechen und die Lautstärke selbst regeln. Gesucht
wurde mit `gh search repos` (Übersetzung, Echtzeit, Dolmetscher, OpenAI Realtime, Whisper, Themen
`speech-translation`, `live-translation`, `voice-translation`), Stand November 2026.

## Kandidaten

| Projekt | Was es ist | Stand | Passt |
|---|---|---|---|
| **sync-kyoto-jp/parley** | Android-App (Kotlin, Compose) für Live-Übersetzung über OpenAI, eigener API-Schlüssel, kein eigener Server | Apache-2.0, 25 Commits, letzte Änderung Juni 2026, 1 Stern | am besten: kleine, saubere Codebasis mit genau dem Aufbau, der gebraucht wird; Serbisch muss nachgerüstet werden |
| **niedev/RTranslator** | Android-App (Java) für Offline-Übersetzung mit Whisper-Small | Apache-2.0, 10 600 Sterne, aktiv | zweite Wahl: ausgereift, aber gross; lokale Modelle müssten durch Cloud-Erkennung ersetzt werden, Serbisch fehlt |
| **kizuna-ai-lab/sokuji** | Live-Übersetzung mit OpenAI, Gemini, Soniox und lokalen Modellen | AGPL-3.0, 1 500 Sterne, aktiv | nein: nur Desktop (Electron) und Chrome-Erweiterung, auf Android nicht lauffähig |
| **pony-huang/Gimi** | Android-KI-Assistent mit Sprachanrufen über mehrere Anbieter | Apache-2.0, aktiv | nein: Assistent, kein Dolmetscher; zu viel fremder Ballast |
| aravindr001/translation-app, chillinh/2WT, olich97/vokala | kleine Android-Versuche | 0 bis 12 Sterne, 1 bis 2 Commits | nein: zu unreif |

## Warum Parley

Im Code geprüft (`app/src/main/java/jp/kyoto/sync/parley/`):

- `audio/AudioCapturer.kt` nimmt über das Handymikrofon auf (`AudioSource.VOICE_RECOGNITION`), nicht über die Brille.
- `audio/AudioPlayer.kt` spielt als Medienton (`USAGE_MEDIA`) und erkennt Bluetooth-A2DP. Damit gilt immer die
  Medienlautstärke, das Handy wechselt nicht ins Anrufprofil, und es gibt nur einen Regler. Ein eigener
  Lautstärkeregler in der App lässt sich am `AudioTrack` direkt anbringen.
- `service/TranslationService.kt` läuft als Vordergrunddienst mit Mikrofonrecht, also auch bei gesperrtem Bildschirm.
- Der API-Schlüssel liegt verschlüsselt im Android-Keystore; Verlauf, Untertitel und Einrichtungsseite sind schon da.

Was fehlt: Parley nutzt `gpt-realtime-translate`, und dieses Modell spricht nur 13 Zielsprachen (en, es, pt, fr, de,
it, ru, hi, id, vi, ja, ko, zh), weder Serbisch noch Kroatisch (developers.openai.com, Cookbook
«realtime_translation_guide»; jitsi/opus-transcriber-proxy, `TRANSLATION_BACKENDS.md`). Für Serbisch muss
`net/TranslationSession.kt` auf ein allgemeines Sprachmodell umgestellt werden, entweder `gpt-realtime-2` mit der
einer Dolmetscher-Anweisung oder eine Kette aus Spracherkennung (`gpt-realtime-whisper`),
Übersetzung und serbischer Stimme. Ob `gpt-realtime-2` Schweizerdeutsch so gut versteht wie die ChatGPT-App und wie
natürlich es Serbisch spricht, ist ungeprüft. Kosten: `gpt-realtime-translate` kostet 0.034 USD je Minute (rund
2 USD je Stunde, platform.openai.com); die Kosten von `gpt-realtime-2` richten sich nach Tokens und sind nicht
berechnet.

## Anbieter wählbar

Entscheid: Die App baut auf Parley auf, darf aber nicht an OpenAI hängen; der Anbieter ist in den
Einstellungen wählbar. Dafür bekommt die App zwei Betriebsarten hinter einer gemeinsamen Schnittstelle:

- **Direkt (Sprache zu Sprache):** ein Modell hört zu und spricht die Übersetzung. Anbieter: OpenAI Realtime
  (`gpt-realtime-2`) und Google Gemini Live API. Am schnellsten, aber Erkennung und Stimme kommen vom selben Anbieter.
- **Kette:** drei austauschbare Glieder. Spracherkennung (OpenAI Whisper, Mistral Voxtral, ElevenLabs, Soniox oder
  Suisse Speech für Schweizerdeutsch), Übersetzung mit einem beliebigen Sprachmodell (Claude, Gemini, Mistral, GPT)
  und serbische Stimme (Android-Sprachausgabe, Google, ElevenLabs, OpenAI). Jedes Glied ist einzeln wählbar, so
  lässt sich die beste Schweizerdeutsch-Erkennung mit dem besten Übersetzer kombinieren. Langsamer als «Direkt»; um
  wie viel, ist ungeprüft.

Claude kann nur das Übersetzungsglied der Kette übernehmen: Die Claude API nimmt Text, Bilder und Dokumente an, aber
weder Ton noch gibt sie Sprache aus (Inhaltstypen laut Anthropic-API-Doku, Stand November 2026).
