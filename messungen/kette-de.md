# Kette «Deutsch / Schweizerdeutsch»: Voxtral → Claude → Gemini liest vor

Skript `kette_de.py`, Rohdaten `kette-de.json`. Gemessen wird dieselbe Kette mit denselben Aufrufen wie in der App:
Voxtral `voxtral-mini-2602` mit `language=de`, der Claude-Dienst über `/uebersetzen/strom` mit vorgewärmter Sitzung
und Gemini Live als Vorleser. Gesprochen wurden sechs Sätze aus dem Pflegealltag, jeder einmal in Mundart
(Suisse-TTS, Zürich, Bern, Basel, Luzern) und einmal auf Hochdeutsch (Google `de-DE-Chirp3-HD-Charon`), dazu zwei
serbische Sätze (`sr-RS-Standard-A`). Die Zeiten zählen ab Satzende bis zum ersten Ton auf Serbisch.

| | Ergebnis | Erster Ton (Median, Bereich) |
|---|---|---|
| Hochdeutsch | 6 von 6 richtig übersetzt | 2.1 s (1.8 bis 3.5 s) |
| Mundart | 5 von 6 richtig, einer falsch | 2.1 s (1.8 bis 6.4 s) |
| Serbisch | 2 von 2 ausgelassen (`[SKIP]`), nichts vorgelesen | – |

Gemini Live allein brauchte bei denselben hochdeutschen Sätzen 1.5 s (`hochdeutsch.json`). Die Kette ist damit rund
0.6 s langsamer. Den grössten Teil der Zeit braucht Claude mit rund 1 bis 1.4 s bis zum ersten Satz. Voxtral braucht
rund 0.3 s, der Vorleser rund 0.4 s.

Der Fehler: «Ich ha Buuchweh und es bitzeli Pfnüsel» erkannte Voxtral als «Spitzenlipfknüsel», Claude übersetzte das
als «probadanje» (Stechen) statt Schnupfen und brauchte dafür 5.6 s. Auch «Tschope» wurde zu «Job»; Claude
verstand trotzdem «Jacke» aus dem Zusammenhang.

Grenze der Messung: Die Mundart kommt aus einer Kunststimme. Echte, ältere Stimmen über das Mikrofon des Handys
sind damit nicht abgedeckt.
