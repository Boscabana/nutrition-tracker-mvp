# Platzsparendere UI für Artikel und Mahlzeiten

Diese Änderung macht die Listen für Artikel (Lebensmittel) und Mahlzeiten kompakter, indem sie standardmäßig eingeklappt angezeigt werden. Erst beim Antippen werden detaillierte Informationen wie Makros, Portionen oder Zutaten eingeblendet.

## User Review Required

> [!IMPORTANT]
> - **Mahlzeiten-Portionen**: Um "Kalorien pro Portion" anzeigen zu können, füge ich dem `MealEntity` ein Feld `servings` (Portionen) hinzu. Standardmäßig ist dies 1.0. Im Bearbeitungsdialog für Mahlzeiten wird ein neues Feld hinzugefügt, um die Anzahl der Portionen anzugeben.
> - **Artikel-Anzeige**: Im eingeklappten Zustand wird wirklich *nur* der Name angezeigt, wie gewünscht. Alle anderen Infos (Marke, Kalorien, Makros) erscheinen erst beim Ausklappen.

## Proposed Changes

### Datenmodell

#### [MODIFY] [MealEntity.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/data/MealEntity.kt)
- Feld `servings: Double = 1.0` zur `MealEntity` hinzufügen.

### Mahlzeiten-Verwaltung

#### [MODIFY] [NutritionViewModel.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/viewmodel/NutritionViewModel.kt)
- `addMealTemplate` und `updateMealTemplate` anpassen, um das `servings`-Feld zu unterstützen.

#### [MODIFY] [MealsScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/MealsScreen.kt)
- **`MealEditDialog`**: Eingabefeld für "Portionen" hinzufügen.
- **Mahlzeiten-Liste**:
    - `expanded`-Status pro Element einführen.
    - Karte anklickbar machen zum Auf-/Zuklappen.
    - Eingeklappt: `Name (Kcal pro Portion)`.
    - Ausgeklappt: Zutatenliste anzeigen, wobei die Mengen pro Portion berechnet werden (`Gesamtmenge / Portionen`).

### Artikel-Verwaltung (Lebensmittel)

#### [MODIFY] [NutritionApp.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/NutritionApp.kt)
- **`FoodsScreen`**:
    - `expanded`-Status pro Lebensmittel-Element einführen.
    - Karte anklickbar machen zum Auf-/Zuklappen.
    - Eingeklappt: Nur der Name in einer Zeile.
    - Ausgeklappt: Marke, Kalorien/100g, Makros, Portionen und Packungen anzeigen.

## Verification Plan

### Manual Verification
- **Artikel-Screen**: Prüfen, ob Lebensmittel nur als Name erscheinen und sich beim Klicken korrekt erweitern.
- **Mahlzeiten-Screen**:
    - Neue Mahlzeit erstellen und Portionen angeben (z.B. 2).
    - Prüfen, ob in der Liste die Kalorien korrekt (Gesamt / 2) angezeigt werden.
    - Prüfen, ob beim Ausklappen die Zutatenmengen ebenfalls halbiert angezeigt werden (pro Portion).
    - Mahlzeit bearbeiten und Portionen ändern -> Anzeige muss sich aktualisieren.
