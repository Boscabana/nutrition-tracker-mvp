# Warnsystem für Rezepte bei Artikel-Löschung

Diese Änderung führt ein Sicherheits- und Warnsystem ein, das dich informiert, wenn ein zu löschender Artikel in Mahlzeiten verwendet wird, und betroffene Mahlzeiten visuell kennzeichnet.

## User Review Required

> [!WARNING]
> - **Lösch-Verhalten**: Wenn du einen Artikel löschst, der in Rezepten verwendet wird, wird er **nicht mehr automatisch entfernt**. Stattdessen bleibt er im Rezept erhalten (damit die Kalorien stimmen), wird aber als "gelöscht/verwaist" markiert.
> - **Warn-Dialog**: Vor dem Löschen wird dir nun angezeigt, in welchen Mahlzeiten der Artikel vorkommt.

## Proposed Changes

### UI & UX (NutritionApp.kt)

#### [MODIFY] [FoodsScreen Delete Dialog](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/NutritionApp.kt)
- Vor dem Löschen wird geprüft: `vm.meals.filter { it.ingredients.any { ing -> ing.foodItemId == id } }`.
- Wenn Treffer gefunden werden: Anzeige einer Liste der betroffenen Mahlzeiten im Dialog mit dem Text: *"Achtung: Dieser Artikel wird in folgenden Mahlzeiten verwendet. Er wird dort als 'verwaist' markiert, falls du ihn löschst."*

### UI & UX (MealsScreen.kt)

#### [MODIFY] [MealsScreen List](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/MealsScreen.kt)
- Prüfung pro Mahlzeit: Hat eine Zutat eine ID, die nicht mehr in `vm.foods` existiert?
- Wenn ja: Anzeige eines roten Ausrufezeichens (`Icons.Default.Warning`) neben dem Namen der Mahlzeit in der Hauptliste.

#### [MODIFY] [IngredientRow](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/MealsScreen.kt)
- Wenn der zugehörige Artikel (`food`) null ist (da gelöscht):
    - Anzeige eines Warntexts: *"Original-Artikel wurde gelöscht"*.
    - Das Swap-Icon wird immer angezeigt, damit der Nutzer die verwaiste Zutat durch eine neue (z. B. eine neue Basis-Zutat) ersetzen kann.

### Daten-Logik (NutritionViewModel.kt)

#### [MODIFY] [NutritionViewModel.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/viewmodel/NutritionViewModel.kt)
- **`deleteFood(id)`**: Die Logik zum Entfernen der Zutat aus `meals` wird entfernt. Die Zutat bleibt im `MealEntity` stehen, verliert aber faktisch ihren Bezug zum aktuellen Lebensmittel-Stamm.

## Verification Plan

### Manual Verification
1. Eine Mahlzeit "Pesto Pasta" mit "ja!-Nudeln" erstellen.
2. Im Lebensmittel-Screen versuchen, "ja!-Nudeln" zu löschen.
3. Prüfen, ob der Warn-Dialog "Pesto Pasta" als betroffene Mahlzeit auflistet.
4. Löschen bestätigen.
5. Zum Mahlzeiten-Screen wechseln -> Prüfen, ob "Pesto Pasta" ein rotes Ausrufezeichen hat.
6. Mahlzeit öffnen -> Prüfen, ob bei den Nudeln die Warnung "Original-Artikel wurde gelöscht" steht.
7. Die Zutat über das Swap-Icon durch eine andere (z.B. Basis-Pasta) ersetzen -> Warnung muss verschwinden.
