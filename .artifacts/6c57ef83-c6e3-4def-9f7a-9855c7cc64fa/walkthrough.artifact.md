# Walkthrough - Umfassende Datumsplanung für Kopieren/Verschieben

Ich habe die Funktionalität zum Kopieren und Verschieben von Einträgen so erweitert, dass nun jedes beliebige Datum (Vergangenheit, Heute, Zukunft) ausgewählt werden kann.

## Änderungen

### 1. Kalender für Kopieren/Verschieben
- Die bisherige Liste (die auf 14 Tage beschränkt war) wurde durch einen vollwertigen **Material 3 DatePicker** ersetzt.
- Wenn du mehrere Einträge markierst und auf "Kopieren" oder "Verschieben" klickst, öffnet sich nun der gleiche intuitive Kalender-Dialog wie in der Hauptansicht.
- Dies ermöglicht die Auswahl jedes beliebigen Datums im gesamten Kalender.

### 2. Bereinigung des Codes
- Die `QuickDatePickerDialog`-Komponente wurde entfernt, da sie durch den Standard-DatePicker ersetzt wurde.
- Die `availableDates`-Logik im ViewModel wurde entfernt, da für den Kalender keine vordefinierte Liste von Tagen mehr benötigt wird. Dies vereinfacht das Datenmodell und spart Ressourcen.

## Verifikation

### Testergebnisse:
- [x] Build erfolgreich durchgeführt.
- [x] Kopieren-Funktion öffnet nun den Kalender.
- [x] Verschieben-Funktion öffnet nun den Kalender.
- [x] Erfolgreiches Kopieren eines Eintrags auf ein Datum in der Vergangenheit (z.B. gestern) verifiziert.
- [x] Erfolgreiches Kopieren auf das heutige Datum verifiziert.

> [!TIP]
> Du kannst jetzt auch sehr einfach Mahlzeiten, die du z.B. gestern vergessen hast einzutragen, von einem anderen Tag rüberkopieren, indem du einfach das entsprechende Datum im Kalender wählst.
