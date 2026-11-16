# Nur Deutsch übersetzen: Gemini Flash und Gemini Live allein

Skript `nur_deutsch.py`, Rohdaten des letzten Laufs in `nur-deutsch.json`. Geprüft wird, ob die Anweisung «nur
Deutsch und Schweizerdeutsch übersetzen» hält.

## Gemini Flash (Text)

13 Umschriften aus den Protokollen, darunter Serbisch, gemischtes Deutsch-Serbisch, verstümmeltes Serbisch
(«Gubičk si?»), Dänisch, Englisch und fehlerhafte Mundart. In vier Läufen jedes Mal 13 von 13 richtig: fremde
Sprachen ergeben `[SKIP]`, alle deutschen und Mundart-Sätze werden übersetzt.

## Gemini Live allein (Ton)

Drei deutsche und drei serbische Sätze von der Google-Stimme (`de-DE-Chirp3-HD-Charon`, `sr-RS-Standard-A`) in einer
Sitzung. Deutsch wurde in allen vier Läufen übersetzt. Bei Serbisch kam Ton:

| Lauf | Serbische Sätze mit Ton |
|---|---|
| 1 | 3 von 3 (Länge und Pegel nicht gemessen; Untertitel leer oder «<no speech detected>») |
| 2 | 0 von 3 |
| 3 | 0 von 3 |
| 4 | 1 von 3 (2.4 s, Spitze 25 981, also gut hörbar; Untertitel «<no speech>{pause}») |

Gemini Live schweigt also meist, spricht aber in rund einem Drittel der Fälle trotzdem. Der Untertitel legt nahe,
dass es dann Platzhalter wie «no speech» vorliest statt einer Übersetzung; angehört wurde der Ton nicht.
