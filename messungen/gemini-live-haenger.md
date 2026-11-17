# Gemini Live hängt nach einer stillen Antwort

Beobachtet im Handytest mit Gemini Live allein (Samsung SM-A556B, Version 0.5.7): Nach einigen serbischen Sätzen
und dem Echo der eigenen Übersetzung zeigte die App mehrmals «<no speech>» im Untertitel, die Wartezeit wuchs auf
15 und 25 s, danach kam für den Rest der Sitzung nichts mehr, auch nicht für deutsche Sätze («Kannst du die Milch
holen?»). Die Verbindung blieb offen, das Protokoll zeigt keinen Fehler und keine Trennung.

## Nachstellung

`gemini_live_haenger.py` schickt die 18 Satzaufnahmen dieser Sitzung in Echtzeit, mit den Pausen von damals und
mit dem Setup der App an Gemini Live und schreibt jedes Ereignis des Servers mit. Aufnahmen und Ausgaben
enthalten echte Gespräche und liegen deshalb nur im privaten Repo `vr-brille` (`messungen/gemini-live-haenger-*.txt`).

- Mit `activityHandling: NO_INTERRUPTION` (Setup der App bis 0.5.7), zwei Durchläufe: Gemini antwortet auf ein
  kurzes «No.» mit `<no speech>{pause}` und `generationComplete`, schickt aber nie `turnComplete`. Danach meldet es
  für alle 15 folgenden Sätze nichts mehr, weder erkannten Text noch Sprachbeginn.
- Mit der Standardeinstellung (neue Sprache unterbricht): Jede stille Antwort endet spätestens beim nächsten
  Sprachbeginn mit `interrupted` und `turnComplete`; alle deutschen Sätze bis zum Schluss werden übersetzt,
  serbische und fremdsprachige bleiben still. Die Unterbrechung kam
  in diesem Durchlauf immer nach `generationComplete`, der Ton der Übersetzung war also schon ganz beim Handy.

Ursache ist damit nicht das Serbisch an sich, sondern jede Runde, in der Gemini schweigen soll (Serbisch, Echo,
Geräusch): Mit `NO_INTERRUPTION` bleibt diese Runde offen und sperrt alle folgenden.

Gemini verteilt die Marke manchmal auf zwei Nachrichten («<no » und «speech>{pause}»), deshalb stand sie trotz
Filter im Untertitel; `GeminiProtocol.Untertitelfilter` hält einen angefangenen Teil zurück.
