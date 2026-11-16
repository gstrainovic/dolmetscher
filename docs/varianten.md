# Varianten: Verständnis, Tempo, Preis

Zusammenzug aller Tests (Mundart-Messung mit zwölf Sätzen und Modellvergleich in
`docs/schweizerdeutsch-erkennung.md`, Handytests, Emulator mit Testsatz). «Sinn» heisst: Bei wie vielen der zwölf
Mundart-Sätze kam der Sinn auf Serbisch an. «Erster Ton»: ab dem letzten gesprochenen Wort. Alle Mundart-Messungen
stammen von einer künstlichen Stimme (Suisse-TTS) oder einem einzelnen Sprecher; ältere Sprecherinnen und Sprecher
klingen anders.

Monatspreis bei 22 Arbeitstagen à 9 Stunden mit laufender App. Annahmen: Die Sprecherkennung schickt nur, wenn
jemand spricht; bei einem Viertel Sprechzeit sind das mit Vor- und Nachlauf rund 150 Minuten Ton je Tag zum
Anbieter, dazu rund 120 Minuten gesprochene Übersetzung (rund 300 Sätze, 108'000 Zeichen serbischer Text). Beträge
in USD fallen mit Wechselkurs und allfälliger Kartengebühr an, CHF und USD hier etwa gleich gesetzt. Alle Preise
sind Hochrechnungen, nicht gemessen.

| Variante | Sinn (von 12) | Erster Ton | Monat | Bemerkung |
|---|---|---|---|---|
| Voxtral + Claude Opus (Abo) + Gratis-Stimme | 10 | 3.5 s | ≈ 20 | Opus repariert Voxtral-Fehler; Voxtral erfand auf dem Handy einmal russischen und dänischen Text |
| Suisse Speech + Claude Opus (Abo) + Gratis-Stimme | 10 oder mehr (Suisse allein 10, Claude deutet dazu) | 2 bis 4 s (Handy, vor den Beschleunigungen), neu ungemessen | ≈ 130 | im Handytest alle Sätze richtig (Rüebli, Häfeli, Seife, Lavabo) |
| Suisse Speech + Azure-Stimme (Claude übersetzt) | wie oben | ähnlich, ungemessen | ≈ 160 | Azure-Stimme vermutlich natürlicher, ungeprüft |
| Suisse Speech + Gemini-Stimme | 10 (Erkennung), Gemini deutet Fehler nicht aus dem Verlauf | 1.3 s | ≈ 200 | schnellste Kette |
| Voxtral + Gemini-Stimme | 7 bis 8 | 3.5 bis 4.4 s (alte Messung) | ≈ 85 | Gemini übersetzt Voxtral-Fehler wörtlich («Osterkuchen») |
| Gemini Live allein | ungemessen mit den zwölf Sätzen; im Handytest «Rühreier» statt Herdöpfel | 1.5 s | ≈ 85 | am schnellsten, versteht Mundart am schlechtesten |
| OpenAI Realtime `gpt-realtime-2.1` | 10 | 1.56 s | ≈ 400 (gemessen USD 0.010 je Satz) | versteht Mundart so gut wie Suisse + Opus, aber am teuersten |
| OpenAI Realtime mini | 4 bis 5 | 1.51 s | ≈ 110 | übersetzt Mundart oft falsch (Finken als Vögel, Jacke als Glas) |
| Claude-Modell im Vergleich (auf Voxtral-Fehlern) | Haiku 6, Sonnet 9, Opus 10, Fable 10 (genauer) | erstes Stück 1.3 / 0.9 / 0.9 / 2.9 s | Abo | Fable rund dreimal langsamer |

Preis-Leistung: Voxtral + Claude Opus + Gratis-Stimme bietet fast dasselbe Verständnis wie Suisse Speech für rund
ein Sechstel; Suisse Speech + Claude Opus + Gratis-Stimme ist die sicherste Variante.

## Stimme für Claudes Übersetzung

Gemessen mit acht serbischen Übersetzungen aus einem Handytest (`messungen/vorlesen.py`, Ergebnis in
`messungen/vorlesen.json`), Anweisung «wortgetreu vorlesen»:

| Stimme | wortgetreu | erster Ton | je Tag (120 Min. Sprache) | je Monat |
| --- | --- | --- | --- | --- |
| OpenAI `gpt-realtime-2.1-mini` | 8 von 8 | 0.6 bis 1.0 s | rund USD 2.95 | ≈ USD 65 |
| Gemini Live `gemini-3.8-live` | 8 von 8 gesprochen (beim ersten Satz fehlte der Untertitel) | 0.35 bis 0.7 s | rund USD 2.40 bis 3.30 | ≈ USD 53 bis 73 |
| Azure Neural `sr-RS-SophieNeural` | nicht anwendbar (reine Sprachausgabe) | ungemessen | rund USD 1.30 | ≈ USD 29 |

Rechnung: OpenAI rund 20 Ton-Tokens je Sekunde zu USD 20 je Million, dazu Text rund 80 Tokens je Satz zu USD 2.40;
Gemini rund 27 Ton-Tokens je Sekunde zu USD 12 je Million, Nachdenken rund 55 Tokens je Satz zu USD 4.50, dazu der
mitwachsende Verlauf als Eingabe (je Satz 600 bis 1500 Tokens, Text USD 0.75 und Ton 3 je Million); die obere
Grenze gilt ohne Neustart der Sitzung. Preise: OpenAI developers.openai.com/api/docs/pricing, Gemini
ai.google.dev/gemini-api/docs/pricing. OpenAI hat `gpt-4o-mini-tts`, `tts-1` und `tts-1-hd` abgekündigt,
Abschaltung am 6. Januar 2027 (developers.openai.com/api/docs/guides/text-to-speech); Nachfolger ist
`gpt-realtime-2.1-mini`.
