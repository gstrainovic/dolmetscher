# Spracherkennung für Schweizerdeutsch: Anbieter, Preise, Qualität

Gesucht ist eine Erkennung, die gesprochenes Schweizerdeutsch in hochdeutschen Text umschreibt, damit eine eigene
Übersetzungskette (Text nach Serbisch, serbische Stimme) im Altersheim hilft. Für eine Übersetzung im Ohr
reicht auch eine Datei-API, wenn jeder Redebeitrag einzeln geschickt wird: So arbeitet die Batch-Umschrift im
Sprachbot eines anderen Projekts (Antwort 0.2 bis 0.7 s).

## Anbieter

| Anbieter | Angebot | Preis | Gratis | Standort | Quelle |
|---|---|---|---|---|---|
| Suisse Speech (Suisse IT GmbH, Brugg) | eigens für Schweizerdeutsch; Live per WebSocket und Dateien; auch Schweizer Stimmen und Sprachagenten | live CHF 2.20/h, Datei CHF 1.35/h, Sprachagent CHF 19.80/h, exkl. MWST | 60 Minuten, verfallen nicht | Schweiz, laut Anbieter ohne Speicherung | suisse-speech.ch/de/pricing |
| Swisscribe | eigens für Schweizerdeutsch; nur Dateien (OpenAI-kompatible API), kein Live-Strom | Standard CHF 2.40/h, Pro CHF 7.20/h | CHF 1.00 Startguthaben (rund 25 Minuten) | Schweiz, kein Training mit Kundendaten | swisscribe.ch/preise |
| Ateleris Transcription | Modell `whisper-ch` für Schweizerdeutsch; nur asynchron (hochladen, abholen) | ab CHF 2.50/h | nicht genannt | Schweiz | transcription.ateleris.com/de |
| Infomaniak AI Services | nur allgemeines Whisper V3, kein Schweizerdeutsch-Modell, nur Dateien; Live nicht belegt | CHF 0.006/min = CHF 0.36/h | Startguthaben (Umrechnung in Minuten nicht genannt) | Schweiz | infomaniak.com/de/hosting/ai-services/tarife |
| Helvetra | nur Text: übersetzt geschriebenes Schweizerdeutsch, keine Spracherkennung | API CHF 39/Monat | 14 Tage | Schweiz | helvetra.ch/faq |
| Swisscom, Spitch, Recapp/Töggl | kein öffentliches Live-Angebot mit Preis gefunden | auf Anfrage | – | Schweiz | Codex-Recherche, nicht weiter geprüft |
| Azure `de-CH`, Deepgram `de-CH`, Speechmatics | Schweizer Hochdeutsch, kein Dialektmodell belegt | Azure USD 1/h, Deepgram USD 0.46/h | – | USA/EU | Herstellerseiten |

Offene Modelle zum Selberbetreiben (GPU nötig, Lizenz nur nichtkommerziell): `apexAI-Switzerland/whisper-large-v3-swissgerman`
(Modellkarte: 13.3 % Wortfehler auf dem Berner Parlamentskorpus SPC-R), `Flurin17/whisper-large-v3-turbo-swiss-german`
(21.9 % auf ASGDTS, laut Modellkarte zu optimistisch, weil der Testsatz im Training war).

## Qualität

Es gibt keinen unabhängigen Vergleich der Schweizer Spezialanbieter. Was belegt ist:

- Raven-Benchmark «Mundart» (raven.ceo/benchmark/mundart, 02.10.2026, Berner Kantonsparlament): bestes allgemeines
  Modell Whisper large-v3 mit 28.4 % Wortfehlern, Google 46 bis 55 %. Die Spezialanbieter fehlen dort.
- Swisscribes eigener Vergleich (swisscribe.ch, Oktober und November 2026, je 5 Minuten): Gespräch in Mundart
  81.6 % Wortgenauigkeit gegen ElevenLabs 72.8 %; Berner Parlamentsrede 94.7 % gegen Swiss Transcript 90.7 % und
  Whisper large-v3 76.9 %. Herstellerangabe, nicht nachgeprüft.
- Suisse Speech veröffentlicht bewusst keinen Genauigkeitswert und verweist auf die 60 Gratisminuten.
- Kanton Basel-Stadt (dcc-bs.github.io/documentation/evaluation/results.html, eigene Diktate auf Schweizerdeutsch):
  Whisper large-v2 45 % Wortfehler bei strenger Zählung mit Satzzeichen; zeigt, wie stark die Werte vom Testmaterial
  abhängen.

## Test in der eigenen App

Test im Alltag mit dieser App: «Suisse Speech + Gemini» (Suisse Speech erkennt, Gemini Live übersetzt
den Text und spricht Serbisch) versteht Schweizerdeutsch viel besser als Gemini Live allein und besser als frühere
Tests mit ChatGPT. Herdöpfel, Rüebli und Ranze kamen richtig, Chüngel nicht zuverlässig.

Wortschatz-Test mit Sätzen, die Suisse Speech selbst in Zürcher Mundart sprach (Studio-Stimme), erkannt live mit
`finalize_on_pause`:

| Gesprochen | Ohne Wortschatz | Mit Wortschatz der App |
|---|---|---|
| Hüt git's Chüngel mit Herdöpfelstock. | Heute gibt es Küngel mit Kartoffelstock. | Heute gibt es Chüngel mit Kartoffelstock. |
| Dä Chüngel im Garte frisst Rüebli. | Der Hüngel im Garten frisst Rüebli. | Der Chüngel im Garten frisst Rüebli. |
| Wänd Sie zum Zvieri es Gipfeli mit Anke und Konfi? | Wenn Sie zum Zwiri ein Gipfel mit Anker und Komfi | Wenn Sie zum Zviri ein Gipfeli mit Anke und Konfi |
| Ich ha Buuchweh und es bitzeli Pfnüsel. | Ich habe Bauchweh, und ein bisschen Pfeifen. | Ich habe Bauchweh, und ein bisschen Pfnüsel. |

Ein Wort im Wortschatz wird in Mundart geschrieben: Mit «Buuchweh» darin kam «Buchweh» statt «Bauchweh». Deshalb
stehen nur Wörter darin, die ohne Hilfe falsch kommen. Gemini übersetzt «Chüngel» richtig mit «zec». Die Berner
Testsätze der synthetischen Stimme wurden mit und ohne Wortschatz kaum verstanden; ob das an der Stimme oder an der
Erkennung liegt, ist offen.

## Suisse Speech gegen Voxtral

Zwölf Alltagssätze aus dem Altersheim, von Suisse Speech selbst in Mundart gesprochen (Studio-Stimme; Zürich, Bern,
Basel, Luzern), erkannt von Suisse Speech live (mit dem Wortschatz der App) und Voxtral Mini Transcribe
(`voxtral-mini-2602`, `language=de`, ohne und mit demselben Wortschatz als `context_bias`). Vorlage zum Zählen der
Wortfehler ist der Satz auf Hochdeutsch. Die Wortfehlerrate bestraft auch richtig erkannte Mundartwörter («Chüngel»
statt «Kaninchen»), deshalb zusätzlich gezählt, ob der Sinn ankommt:

| | Wortfehler (Mittel) | Sinn richtig (von 12) |
|---|---|---|
| Suisse Speech | 34 % | 10 |
| Voxtral | 42 % | 7 |
| Voxtral mit Wortschatz | 43 % | 8 |

Auffällige Fehler: Voxtral machte aus «I ha mi Brülle verleit, hesch se gseh?» ein «Ich habe mich brüllen verleiht.
Herzlichen Glückwunsch.» (Suisse: richtig), aus «Tschope» einen «Job» und aus «Chüngelfleisch» nur «Fleisch». Beide
scheiterten am ersten Berner Satz. Suisse Speech hörte «Tschope» als «Choppen».

Vorbehalt: Die Stimmen stammen von Suisse Speech selbst, was dessen Erkennung begünstigen kann; echte ältere
Sprecherinnen und Sprecher klingen anders. Der Alltagstest zeigt dieselbe Richtung.

## Voxtral-Umschrift, gedeutet von Claude über das Abo

Gedanke: Ein gutes Sprachmodell erschliesst aus einer fehlerhaften Umschrift und dem Gesprächsverlauf, was
gemeint war, ein reiner Übersetzer nicht. Geprüft mit einer dauernd offenen Claude-Sitzung über das Claude-Code-Abo
(Agent SDK `claude-agent-sdk` 0.2.182, Modell Haiku, ohne Werkzeuge, kein API-Schlüssel): die zwölf
Voxtral-Umschriften aus der Messung oben nacheinander als ein Gespräch, Auftrag «fehlerhafte Umschrift aus einem
Altersheim, Sinn erschliessen, nur serbisch ausgeben».

- Zeit: Sitzung offen nach 0.7 s, danach 1.3 bis 2.0 s je Satz (erster Satz 2.7 s). Zum Vergleich `claude -p` je
  Aufruf 8.5 bis 9.1 s.
- Sinn richtig: 8 von 12 (Voxtral allein 7, Suisse Speech 10). Gerettet: «Mir ist es kalt. Könnte ich noch einen Job
  haben?» wurde «… još jednu jaknu» (Jacke); «Küngel» wurde Kaninchen. Nicht zu retten: «Ich habe mich brüllen
  verleiht. Herzlichen Glückwunsch.» wurde «Verlobila sam se» (ich habe mich verlobt), dazu der Berner Satz und
  «Chüngelfleisch», von dem in der Umschrift nur «Fleisch» blieb. «Krumfi» wurde Kartoffel statt Konfitüre.
- Beim ersten Satz antwortete das Modell erst tschechisch und korrigierte sich mit einer Erklärung; die Anweisung
  braucht eine strengere Ausgaberegel.

Ob der Abo-Login für einen Dienst, den eine andere Person benutzt, erlaubt ist, ist nicht geprüft (für Produkte, die Dritte
bedienen, schliesst ihn die Agent-SDK-Doku aus).

## Haiku, Sonnet und Opus im Vergleich

Dieselben zwölf Voxtral-Umschriften, je Modell eine offene Sitzung über das Claude-Code-Abo, angewärmt wie mit
`/vorwaermen`, Anweisung wie im Dolmetscher-Dienst, Teilantworten eingeschaltet (`messungen/modellvergleich.py`,
Rohdaten `messungen/modellvergleich.json`).

| Modell | Sinn richtig (von 12) | erstes Stück, Median (max) | ganze Übersetzung, Median | Auffälliges |
|---|---|---|---|---|
| Haiku | 6 | 1.27 s (2.76) | 1.44 s | erster Satz gar nicht übersetzt, sondern deutsche Notiz; «Brille verlegt» wurde «ich habe mich verlobt» |
| Sonnet | 9 | 0.93 s (3.09) | 1.34 s | erster Satz falsch und mit Notiz «Hmm — treba samo prevod»; trotz Anweisung Varianten in Klammern |
| Opus | 10 | 0.88 s (5.29) | 1.57 s | sauber ohne Zusätze; einziger mit «zečetina» (Kaninchen) im ersten Satz |

Nachgemessen mit Fable 5.2 (`claude-fable-5-2`, über das Abo verfügbar; Rohdaten `messungen/modellvergleich-fable.json`):
Sinn richtig 10 von 12, die genauesten Deutungen («Zaturio sam negde naočare. Jeste li ih možda videli?» mit der
Frage, die die Umschrift durch «Herzlichen Glückwunsch» ersetzt hatte; «Treppe» und «Jacke» wörtlich), aber erstes
Stück im Median 2.9 s (bis 7.2 s), ganze Übersetzung 3.5 s, also rund dreimal langsamer als Opus.

Gemini-Textmodelle mit denselben zwölf Umschriften und derselben Anweisung, ein Gespräch je Modell, ohne
Teilantworten über die Gemini-API (`messungen/gemini_deuten.py`, Rohdaten `messungen/gemini-deuten.json`):
`gemini-3.8-flash` Sinn richtig 10 von 12, ganze Übersetzung im Median 4.6 s (2.3 bis 26.5 s); `gemini-3.1-pro-preview`
ebenfalls 10 von 12, Median 4.8 s (bis 114 s). Beide scheiterten an denselben zwei Sätzen wie Opus, beide hängten bei
«Brille verlegt» ein «Čestitam» (Glückwunsch) aus der Umschrift an, Pro traf «Jacke» wörtlich. Gemini deutet als
Textmodell also so gut wie Opus, braucht aber rund dreimal so lange; schwächer ist Gemini Live, das in der App den
erkannten Text selbst übersetzt (Voxtral + Gemini-Stimme: Sinn 7 bis 8 von 12, `docs/varianten.md`).

Alle vier scheiterten am Berner Satz, von dem in der Umschrift nichts Brauchbares blieb, und übersetzten
«Chüngelfleisch» nur als «Fleisch». Opus verbraucht mehr vom Abo-Kontingent als Sonnet.

## OpenAI Realtime

Dieselben zwölf Mundart-Sätze (Suisse-TTS) als Ton an `gpt-realtime-2.1` und `gpt-realtime-2.1-mini`, Einstellung
wie in der App (Dolmetscher-Anweisung, Server-VAD ohne Unterbrechung), eine Sitzung je Modell
(`messungen/openai_realtime.py`, Rohdaten `messungen/openai-realtime.json`).

| Modell | Sinn richtig (von 12) | erster Ton ab letztem Wort, Median (max) | Kosten der 12 Sätze |
|---|---|---|---|
| `gpt-realtime-2.1` | 10 (davon einer nur ungefähr) | 1.56 s (2.06) | USD 0.122 |
| `gpt-realtime-2.1-mini` | 4 bis 5 | 1.51 s (1.97) | USD 0.032 |

`gpt-realtime-2.1` deutete «Brille verlegt, hast du sie gesehen?» und «Jacke» richtig und als einziges Modell
«Chüngelfleisch» als Kaninchenfleisch, machte aber aus dem «Chüngel» im ersten Satz Hühnchen. Mini übersetzte
Finken als Vögel, Jacke als Glas und Kaninchen als Popcorn oder Hühner. Hochgerechnet aus den gemessenen Kosten
(USD 0.010 je Satz von rund 5 s bei 2.1, 0.0027 bei mini) und rund 1800 solchen Sätzen je Arbeitstag:
`gpt-realtime-2.1` rund USD 18 je Tag, mini rund 5; mit wachsendem Gesprächsverlauf eher mehr.

## Hochdeutsch statt Mundart

Dieselben zwölf Sätze in der hochdeutschen Vorlage, gesprochen von Google `de-DE-Chirp3-HD-Charon`
(`messungen/hochdeutsch.py`, Rohdaten `messungen/hochdeutsch.json` und `messungen/openai-realtime-hochdeutsch.json`):

| Glied oder Kette | Ergebnis | erster Ton ab Satzende | je Monat (`docs/varianten.md`) |
|---|---|---|---|
| Voxtral (Umschrift) | 12 von 12 fehlerfrei | | ≈ USD 20 für die Erkennung |
| Gemini Live allein | Sinn 12 von 12 | 1.5 s (bis 1.7) | ≈ USD 85 |
| `gpt-realtime-2.1` | Sinn 12 von 12 | 1.5 s (bis 1.7) | ≈ USD 300 |
| `gpt-realtime-2.1-mini` | Sinn 11 bis 12 von 12, teils kroatische Formen («vrtu», «mrkvu», «osjećam») und ein Zusatz in Klammern | 1.5 s (bis 1.8) | ≈ USD 100 |

Bei Hochdeutsch braucht es weder Suisse Speech noch Claudes Deutung: Gemini Live allein übersetzt so gut wie die
Mundart-Kette und ist mit 1.5 s schneller als Voxtral + Claude + Stimme (2 bis 4 s).

## Folgerung

Statt eines Aufnahmetests mit allen Anbietern Suisse Speech direkt in der App im Alltag
testen; Swisscribe entfällt. Suisse Speech erkennt Mundart am besten: im Alltagstest besser als Gemini Live und ChatGPT, in der Messung besser
als Voxtral. Monatskosten aller Varianten in `docs/varianten.md`.

Im Dauerbetrieb sind die Spezialanbieter allein für eine Erkennung im Ohr teuer.

- Infomaniak hat kein Schweizerdeutsch-Modell, nur Whisper V3, das in Raven 28 % Fehler macht; ChatGPT nutzt
  dieselbe Modellfamilie. Gegenüber ChatGPT wäre damit wenig gewonnen.
- Für eine Übersetzung im Altersheim kommen Suisse Speech (einziger mit Live-Strom und Gratisminuten) und Swisscribe (bester
  veröffentlichter Wert, je Redebeitrag als Datei) in Frage. Welcher Dialekt im Altersheim besser erkannt wird, zeigt
  nur ein Test mit eigenen Aufnahmen; die Gratisguthaben reichen dafür.
- Vergleich mit Gemini Live in der eigenen App (`docs/varianten.md`): Gemini verrechnet Ton-Eingabe mit
  USD 0.30 je Stunde und liefert Erkennung, Übersetzung und Stimme zusammen. Die Spezialanbieter kosten allein für
  die Erkennung CHF 2.20 bis 2.50 je Stunde, also rund das 7- bis 8-Fache (bei etwa gleichem Kurs CHF zu USD,
  ungeprüft); Übersetzung und serbische Stimme kämen dazu. Die ganze Kette käme so auf rund das 4- bis 6-Fache von
  Gemini.
- Kosten bei 4 Stunden Zuhören am Tag und 20 Arbeitstagen: Suisse Speech live rund CHF 176 im Monat, Swisscribe
  rund CHF 192; je Redebeitrag geschickt nur die Sprechzeit, also deutlich weniger (Schätzung, ungeprüft).
