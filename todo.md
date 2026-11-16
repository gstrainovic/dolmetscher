# Offene Punkte

- Einrichtungs-Assistent: Die App läuft nie ohne Kopfhörer oder Brille. Der Assistent erscheint beim Öffnen und
  jedes Mal, wenn das Audiogerät fehlt, auch mitten im Betrieb (Bluetooth aus, Gerät getrennt): Übersetzung
  anhalten, Assistent zeigen, danach dort weitermachen. Die Hauptseite ist erst erreichbar, wenn alle Schritte
  bestanden sind.
  1. Bluetooth einschalten, falls aus (Systemdialog anfragen).
  2. Kopfhörer oder Brille koppeln und verbinden (gekoppelte Geräte zeigen, sonst in die Bluetooth-Einstellungen
     führen).
  3. Funktionstest: ein Probesatz kommt über das Gerät (Medienton, A2DP) und das Mikrofon hört etwas; die Person
     bestätigt, dass sie den Ton hört.

  Benutzer können keinen Schritt überspringen. Nur Entwickler dürfen ohne Gerät arbeiten (Emulator, Test über den
  Handylautsprecher): über ein eigenes Build-Flag (etwa `./gradlew installDebug -Pentwickler=true` →
  `BuildConfig.ENTWICKLER`), nicht über `BuildConfig.DEBUG`, weil auch die Versionen für Benutzer heute
  Debug-Builds sind. Ohne das Flag gibt es keinen Weg vorbei, auch keinen versteckten in der Oberfläche.
