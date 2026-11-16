# Dolmetscher

Android-App zum Dolmetschen im Ohr: Sie hört Deutsch und Schweizerdeutsch und spricht die serbische Übersetzung in
Bluetooth-Kopfhörer oder eine Audio-Brille. Gedacht für die Arbeit im Altersheim. Hintergrund und Messungen stehen
in `docs/` (`app-grundlage.md` warum Parley, `schweizerdeutsch-erkennung.md`, `serbische-stimmen.md`,
`varianten.md` mit Verständnis, Tempo und Monatskosten je Variante), die Messskripte in `messungen/`.

Das Repo ist öffentlich. Persönliches (Namen, Geräte, Server-Adressen, Betrieb für einzelne Benutzer) gehört nicht
hierher, sondern ins private Repo `vr-brille` (Abschnitt «Dolmetscher-App» in dessen `AGENTS.md` und `todo.md`).

## Herkunft

Kopie von `sync-kyoto-jp/parley` (Apache-2.0; `LICENSE` und `NOTICE` bleiben). Der Remote `upstream` zeigt auf das
Original, `origin` auf `gstrainovic/dolmetscher`. Paketnamen (`jp.kyoto.sync.parley`) und die japanischen
Kommentare des Originals bleiben, damit sich Änderungen von dort übernehmen lassen; eigener Code und Kommentare sind
deutsch. Die App-Kennung ist `ch.strainovic.dolmetscher`.

## Aufbau

- Einrichtungs-Assistent (`core/Assistent.kt`, `ui/AssistentScreen.kt`, `audio/Geraete.kt`, `audio/Testton.kt`):
  Die App läuft nie ohne Kopfhörer oder Brille. `MainActivity` zeigt statt jeder anderen Seite den Assistenten,
  solange `Assistent.schritt` nicht `FERTIG` ist: Bluetooth einschalten (Systemdialog), Gerät koppeln
  (Bluetooth-Einstellungen), Funktionstest (Ton im Gerät gehört und Stimme im Mikrofon erkannt). Der Test gilt für
  das verbundene Gerät bis zum nächsten Öffnen der App; Android vergibt nach einem Neuverbinden eine neue Kennung,
  dann kommt der Test wieder. Als Kopfhörer zählen Bluetooth A2DP und LE, Kabel- und USB-Kopfhörer, nie
  Lautsprecher oder Hörkapsel des Handys. Trennt sich das Gerät im Betrieb, hält `TranslationService` die
  Übersetzung an (`geraeteWache`) und der Assistent erscheint; ohne Gerät startet der Dienst gar nicht. Der Testton
  geht nur an den Kopfhörer und entfällt, wenn keiner verbunden ist. Benutzer können nichts überspringen; nur ein
  Entwickler-Build (`-Pentwickler=true`, `BuildConfig.ENTWICKLER`) kommt ohne Gerät durch, etwa im Emulator. Nicht
  an `BuildConfig.DEBUG` koppeln: Auch die Versionen für Benutzer sind Debug-Builds. Eine Entwickler-APK nie an
  Benutzer geben.
- Einstellungen (`ui/SettingsScreen.kt`, `core/Setup.kt`): Erkennung, Übersetzung und Stimme einzeln wählbar,
  gespeichert je Sprache des Gegenübers. Ohne eigene Wahl gilt `Setup.vorschlag`: «Schweizerdeutsch» Voxtral +
  Claude + «Gemini liest vor», «Deutsch» und alle anderen Gemini Live allein (Hochdeutsch Sinn 12 von 12, erster Ton
  1.5 s, `docs/schweizerdeutsch-erkennung.md`). Erkennung: Suisse Speech, Voxtral, oder ein Modell, das alles in
  einem macht (Gemini Live, OpenAI Realtime `gpt-realtime-2.1`, `gpt-realtime-2.1-mini`). Stimme: Gemini (übersetzt
  und spricht den erkannten Text in einem Schritt), «Gemini liest vor» (Gemini Live liest die Übersetzung des
  Textmodells wortgetreu vor, `audio/GeminiVorleser.kt`), Azure (`sr-Latn-RS-SophieNeural`) oder die Gratis-Stimme
  des Handys. Ausser bei «Gemini-Stimme» übersetzt ein Textmodell: Claude über den eigenen Server oder Gemini Flash.
  Stimmenvergleich mit Hörproben: `docs/serbische-stimmen.md`.
- `net/Translator.kt` setzt die Wahl zusammen: Modelle in einem (`GeminiSession`, `InterpreterSession`,
  `TranslationSession` für Ziele von `gpt-realtime-translate`) oder `ChainSession` aus `Recognizer`
  (`SuisseRecognizer` live, `VoxtralRecognizer` je Satz als WAV), `TextTranslator` (`ClaudeServerTranslator`,
  `GeminiFlashTranslator` mit den letzten sechs Sätzen als Zusammenhang) und `SpeechOut` (`AzureVoice`,
  `AndroidVoice` über eine WAV-Datei, damit Lautstärkeregler und Brille gleich bleiben). Eigene Sätze per Taste
  laufen bei den Ketten über Gemini Live, weil Suisse Speech und Voxtral kein Serbisch erkennen. Spricht die
  Benutzerin beim Zuhören selbst in der Zielsprache, kommt ihr Satz trotzdem als Text an; der Text-Übersetzer
  antwortet dann `[SKIP]` (`InterpreterProtocol.SKIP`, `UEBERSPRINGEN` im Dienst), und die Kette liest nichts vor.
  Kyrillischer Text geht gar nicht an den Übersetzer: Voxtral schreibt serbische Sätze oft kyrillisch oder erfindet
  Russisch, und Claude hält das für Russisch und übersetzt es.
- Schlüssel: fest in der App, nicht im Repo. `werkzeuge/schluessel.sh` schreibt aus `~/.config` (`gemini/key`,
  `suisse-speech/key`, `openai/key`, `azure-speech/key` und `region`, `dolmetscher/server-url` und `server-token`,
  `dolmetscher/mistral.env`) die ignorierte `secrets.properties`, Gradle macht daraus `BuildConfig`. Vor jedem Bau
  ausführen. Fehlt ein Schlüssel, nennen Einstellungen und Startmeldung ihn (`Keys.missing`). Wer die APK hat, kann
  die Schlüssel auslesen: **eine gebaute APK nie in dieses Repo legen, auch nicht als GitHub-Release.**
- Claude-Dienst (`server/dienst.py`, FastAPI, `claude-agent-sdk`): hält je Gespräch eine Claude-Sitzung (ohne
  Werkzeuge) offen; Modell aus `DOLMETSCHER_MODELL`, im Betrieb `opus`: im Vergleich mit zwölf fehlerhaften
  Voxtral-Umschriften Sinn richtig bei Opus 10, Sonnet 9, Haiku 6, erstes Stück im Median 0.9 bis 1.3 s bei allen
  (`docs/schweizerdeutsch-erkennung.md`). `/uebersetzen/strom` liefert Teilantworten, `/vorwaermen` legt die Sitzung
  an und lässt sie einmal antworten, `/protokoll` nimmt das Laufprotokoll der App entgegen. Schwer deutbare Sätze
  dauern länger (Opus bis 5 s), und dieselbe Umschrift wird nicht immer gleich gedeutet. Zugang nur mit
  `DOLMETSCHER_TOKEN`. Vorlagen für den Betrieb hinter Caddy als systemd-Dienst: `deploy/`.
- Ton: Aufnahme über das Handymikrofon, Wiedergabe als Medienton (`USAGE_MEDIA`) über Bluetooth A2DP. Deshalb gilt
  nur die Medienlautstärke. Der Regler auf dem Hauptbildschirm (`audio/VolumeLevel.kt`) stellt bis 100 % die
  Medienlautstärke und verstärkt darüber in der App (`audio/Gain.kt`, bis 300 %).
- Sprecherkennung (`audio/SpeechGate.kt`): Beim Zuhören geht Ton nur an den Anbieter, wenn jemand spricht, zwei
  Rahmen in Folge, mit einer halben Sekunde Vorlauf und 0.6 Sekunden Nachlauf; danach `Translator.endOfSpeech()`.
  Ob gesprochen wird, entscheidet Silero VAD mit 24 dB Verstärkung davor (`audio/SileroSprache.kt`), nicht die
  Lautstärke: Eine eigene Pegelschwelle verlor geflüsterte Sätze. Öffnen ab Wahrscheinlichkeit 0.3, offen bleiben
  bis unter 0.15 (Silero `neg_threshold`); geflüsterte Sätze werden zum Ende hin leiser (Silero 0.16 bis 0.34), mit
  einer Schwelle allein fehlte die zweite Satzhälfte. Eingestellt am Mitschnitt mit
  `messungen/vad_simulation.py`. TEN VAD (`tenAusAssets`) erkannte Flüstern nicht. Die Pegelschwelle bleibt nur
  als Rückfall, wenn das Modell nicht lädt («FEHLER Silero VAD nicht geladen» im Protokoll). Silero läuft über
  sherpa-onnx; `werkzeuge/sherpa.sh` lädt das Paket (49 MB, nicht im Repo) nach `app/libs` und die Modelle nach
  `app/src/main/assets`, vor dem ersten Bau ausführen. Jeder Satz geht auf volle Lautstärke gebracht an Voxtral
  (`audio/Lautheit.kt`). Beim Sprechen per Taste geht alles durch. Kosten in `docs/varianten.md`.
- Testhilfen nur in Debug-Versionen (`BuildConfig.DEBUG`): jeder Satz als WAV in `files/aufnahmen`, der ganze
  Mikrofonton in `files/aufnahmen/mitschnitt.pcm` (16 kHz, höchstens 10 Minuten), die VAD-Wahrscheinlichkeit alle
  5 s im Protokoll. Holen mit `adb exec-out run-as ch.strainovic.dolmetscher cat files/aufnahmen/<datei>`.
- Suisse Speech (`SuisseProtocol`): live, 16 kHz, `finalize_on_pause`, bei Satzende der Sprecherkennung
  `finalize`, Mundart-Wortschatz; liefert hochdeutschen Text. Verrechnet nur geschickte Tonsekunden (CHF 2.20 je
  Stunde live).
- Gemini trennt eine Verbindung nach rund 2 bis 3 Minuten ohne Sprache («1008 client inactivity», auch wenn
  Stille-Rahmen kommen) und spätestens nach rund 10 Minuten (`goAway`). `GeminiSession` verbindet dann still neu
  (`ReconnectPolicy`: 0.5 s, verdoppelt, nach 5 Fehlschlägen in Folge Fehlermeldung). Der Gesprächsverlauf beginnt
  dabei neu, was für reines Übersetzen nicht stört.
- Oberfläche: unabhängig von der Handysprache serbisch, Deutsch in den Einstellungen wählbar («Sprache der App»,
  `core/Oberflaeche.kt`, gespeichert in `AppSettings.oberflaeche`). `MainActivity` und `TranslationService` setzen
  die Sprache in `attachBaseContext`; nach einem Wechsel lädt die Activity neu. Texte: Deutsch (`values`), Serbisch
  in lateinischer Schrift (`values-sr`), dazu Englisch und Japanisch aus dem Original, die nicht wählbar sind.
  `StringsTest` verlangt jeden Text in jeder Sprache.

## Bauen und testen

Android-SDK unter `~/Android/Sdk` (Pfad in der ignorierten `local.properties`), JDK 21:

```bash
export ANDROID_HOME=~/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions und Dependabot sind
abgeschaltet und die Workflows gelöscht; gebaut wird nur lokal. Server: `cd server && uv run pytest`.

Unter Windows: JDK und SDK aus scoop (`java/temurin21-jdk`, `main/android-clt`, dazu per `sdkmanager`
`platforms;android-35`, `build-tools;35.0.0`, `platform-tools`), in `local.properties` den SDK-Pfad mit
Schrägstrichen (`sdk.dir=C:/Users/<name>/scoop/apps/android-clt/current`), dann in PowerShell `JAVA_HOME` und
`ANDROID_HOME` setzen und `.\gradlew.bat testDebugUnitTest assembleDebug lintDebug`. Das sherpa-Paket lädt
`werkzeuge/sherpa.sh`; wo Git Bash `curl` sperrt, dieselbe Adresse mit `Invoke-WebRequest` nach `app/libs` laden.

## Ausprobieren

- **Laptop (Emulator):** virtuelles Gerät `dolmetscher` (Android 35, x86_64). Start mit
  `~/Android/Sdk/emulator/emulator -avd dolmetscher`, dann `./gradlew installDebug -Pentwickler=true` (der
  Emulator hat keinen Kopfhörer, ohne das Flag bleibt der Assistent stehen). Das Laptop-Mikrofon dient als
  Handymikrofon, wenn `adb emu avd hostmicon` gesetzt ist; der Ton kommt aus dem Standard-Ausgang des Laptops, also
  auch aus der Brille, wenn sie mit dem Laptop gekoppelt ist. Taugt für Oberfläche, Anbieter und Übersetzung, nicht
  für Bluetooth-Verhalten, gesperrten Bildschirm und Akku.
- **Handy per WLAN:** Entwickleroptionen, «Debugging über WLAN», «Gerät mit Kopplungscode koppeln», dann
  `adb pair <ip>:<port>` (einmal) und `adb connect <ip>:<port>`; danach installiert `./gradlew installDebug` die
  neue Version direkt aufs Handy. Handy und Laptop müssen im selben WLAN sein. Ist das Handy einmal gekoppelt,
  meldet es sich mit eingeschaltetem «Debugging über WLAN» in `adb mdns services` als `_adb-tls-connect` mit
  Adresse und Port (beide wechseln); den Kopplungsdienst zeigte die Liste nicht, dafür Code und Port vom Handy
  abschreiben. Fernsteuerung mit offener Tastatur: Tipps auf den unteren Bildschirmteil landen auf der Tastatur,
  deshalb vor dem Antippen von Knöpfen `adb shell input keyevent 4`.
- **Aufnahme statt Stimme einspeisen:** Ist eine Bluetooth-Brille mit dem Laptop gekoppelt, sind deren Mikrofon und
  Lautsprecher die Standardgeräte, und eine abgespielte Datei kommt nicht im Emulator an. Dann eine leere Senke
  anlegen (`pactl load-module module-null-sink sink_name=dolmetscher_probe rate=24000 channels=1`), den Emulator
  mit `PULSE_SOURCE=dolmetscher_probe.monitor` starten und die Aufnahme mit
  `pacat -d dolmetscher_probe --format=s16le --rate=24000 --channels=1 <datei>` abspielen; danach das Modul mit
  `pactl unload-module <nummer>` entfernen. Die Standardgeräte bleiben dabei unverändert.
- **Anbieterprobe ohne App:** Die Dolmetscher-Anweisung lässt sich mit einer Aufnahme direkt gegen die API prüfen
  (Gemini: Setup-Nachricht aus `GeminiProtocol.setup`, Ton als `realtimeInput` mit `audio/pcm;rate=24000`).

Testläufe auswerten: Die App schreibt `files/protokoll.txt` (Variante, Satzende, erkannter Text, Übersetzung mit
Sekunden seit Satzende, Fehler; `core/Protokoll.kt`), lesen mit
`adb exec-out run-as ch.strainovic.dolmetscher cat files/protokoll.txt`. Logcat hält auf dem Handy nur wenige
Minuten. Gespeicherte Gespräche stehen in `files/sessions.json` (nur Gespräche, die mit Stopp oder Neustart
beendet wurden).

Für die Fernsteuerung durch Claude: `adb exec-out uiautomator dump /dev/tty` liefert die Elemente mit Text und
Koordinaten, `adb shell input tap x y` tippt, `adb shell input text` schreibt.

Jede Version muss mit demselben Schlüssel signiert sein, sonst lehnt Android das Update ab und die App muss neu
installiert werden (Verlauf und Schlüssel gehen dabei verloren).
