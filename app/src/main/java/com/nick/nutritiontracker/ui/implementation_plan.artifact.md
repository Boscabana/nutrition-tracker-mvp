# Implementation Plan - Prominent Saving Notice

This plan adds a clear, dialog-wide hint when creating a meal from the diary that contains articles not yet in the library. This ensures the user is aware that these items will be automatically persisted.

## Proposed Changes

### [User Interface]

#### [MODIFY] [MealsScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/MealsScreen.kt)
- **`MealEditDialog` Title & Banner**:
    - Update the title to say "Mahlzeit aus Auswahl erstellen" if `meal?.id == 0L`.
    - Detect if any ingredient is "orphaned" (id not in library).
    - If orphans exist, show a **prominent banner** at the top of the dialog (e.g., a `Card` with a primary color background or an info icon) stating: "Hinweis: Enthaltene Einzelartikel werden automatisch in deiner Bibliothek gespeichert."
- **`IngredientRow`**: Refine the per-row hint to be slightly more subtle now that a global notice exists.

#### [MODIFY] [NutritionApp.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/NutritionApp.kt)
- **`DiarySelectionActions`**: (No change needed here, as the dialog itself will handle the notice).

### [Code Quality]

#### [MODIFY] [NutritionViewModel.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/viewmodel/NutritionViewModel.kt)
- Fix the `idx` warning in `updateMealTemplate`.

## Verification Plan

### Manual Verification
1.  **Selection Creation Flow**:
    - Select unsaved items in the diary.
    - Tap the "Create Meal" icon.
    - Verify the dialog title is "Mahlzeit aus Auswahl erstellen".
    - Verify the prominent info banner is visible at the top.
2.  **Regular Edit Flow**:
    - Edit an existing meal that only has saved articles.
    - Verify the dialog title is "Mahlzeit bearbeiten" and NO banner is shown.
3.  **Library Sync**:
    - Save the meal and verify articles are added to the library as before.
