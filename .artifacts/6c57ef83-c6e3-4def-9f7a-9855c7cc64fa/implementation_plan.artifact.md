# Erweiterte Datumsplanung (Vorausplanung bis zu 2 Wochen)

Diese Änderung ermöglicht es, Mahlzeiten bis zu zwei Wochen im Voraus zu planen. Die bisherige einfache Liste zur Datumsauswahl wird durch einen Kalender-Dialog ersetzt.

## User Review Required

> [!IMPORTANT]
> - **Kalender-Ansicht**: Statt einer einfachen Liste im Dropdown wird nun ein vollwertiger Material 3 `DatePicker` verwendet. Dies bietet die gewünschte "Minikalender"-Funktionalität.
> - **Zeitraum**: Der Kalender erlaubt die Auswahl jedes Datums, wobei wir den Fokus auf den Bereich von "heute" bis "+14 Tage" legen können. Technisch ist jedoch jedes Datum möglich, was maximale Flexibilität bietet.

## Proposed Changes

### UI & UX (NutritionApp.kt)

#### [MODIFY] [NutritionApp.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/NutritionApp.kt)
- **TopAppBar**: Den `DropdownMenu` für die Datumsauswahl entfernen.
- **Datumsauswahl**: Ein Klick auf den Datums-Button öffnet nun einen Material 3 `DatePickerDialog`.
- **DatePicker**: Den `DatePicker` so konfigurieren, dass er standardmäßig das aktuell ausgewählte Datum markiert.
- **Schnellauswahl**: (Optional) "Heute" und "Morgen" als prominente Buttons im Dialog beibehalten, falls der User schnell wechseln möchte.

### Datenverwaltung (NutritionViewModel.kt)

#### [MODIFY] [NutritionViewModel.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/viewmodel/NutritionViewModel.kt)
- **availableDates**: Diese Liste wird weiterhin für die "Copy/Move"-Funktionalität genutzt. Ich werde sie so anpassen, dass sie zumindest alle Tage der nächsten 14 Tage enthält, damit man beim Verschieben/Kopieren ebenfalls leicht in die Zukunft planen kann.

## Verification Plan

### Manual Verification
- **Tagebuch**: Auf das Datum in der Top-Bar klicken. Prüfen, ob sich der Kalender öffnet.
- **Vorausplanung**: Ein Datum in 10 Tagen auswählen und einen Eintrag hinzufügen. Prüfen, ob der Eintrag gespeichert wird und beim Zurückkehren auf diesen Tag wieder erscheint.
- **Kopieren/Verschieben**: Mehrere Einträge markieren, "Kopieren" wählen und prüfen, ob im Ziel-Dialog nun auch zukünftige Daten leicht auswählbar sind.
