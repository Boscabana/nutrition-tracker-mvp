# Implementation Plan - Pantry System and UX Enhancements

This plan introduces a "Pantry" (Vorratsschrank) system to track items that are always in stock, improves the shopping list workflow, and adds gesture-based editing to the meal planner.

## Proposed Changes

### [Data Models]

#### [MODIFY] [FoodItemEntity.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/data/FoodItemEntity.kt)
- Add `val isPantryItem: Boolean = false` to track if an item should be considered a staple.

#### [MODIFY] [ShoppingItem.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/data/ShoppingItem.kt)
- Add `val isPantryItem: Boolean = false`.
- This will allow filtering these items on the shopping list even if they were added via the planner.

### [Business Logic]

#### [MODIFY] [NutritionViewModel.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/viewmodel/NutritionViewModel.kt)
- **State**: Add `var showPantryInShoppingList by mutableStateOf(false)`.
- **Planned Entries**: Update `addPlannedEntry` and `addPlannedMeal` to set `isPantryItem` on the resulting `ShoppingItem` if the source food item is marked as a pantry item.
- **Toggle Action**: Update `toggleShoppingItem` to handle the "Check & Archive" logic as requested. Clicking an item will mark it as checked, moving it to the archive section.

### [User Interface]

#### [MODIFY] [NutritionApp.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/NutritionApp.kt)
- **`FoodEditDialog`**: Add a Switch for "Vorratsartikel" (Pantry Item).
- **`FoodsScreen`**: Add a "Vorratsschrank" button in the header.
- **[NEW] `PantryScreen`**: A view that displays only food items marked as `isPantryItem`.

#### [MODIFY] [ShoppingListScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/ShoppingListScreen.kt)
- **Filtering**: By default, hide active items where `isPantryItem == true`.
- **Toggle**: Add a switch "Vorratsartikel anzeigen" in the header.
- **Interaction**: Make the entire shopping item card clickable to trigger the check/archive action.

#### [MODIFY] [PlannerScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/PlannerScreen.kt)
- **Gestures**: Wrap `PlannedEntryRow` with `SwipeActionContainer`.
- **Actions**: Add an edit action to the swipe (similar to the Today screen) to allow changing portions or meal types for planned entries.

## Verification Plan

### Manual Verification
1.  **Pantry Toggle**: Mark "Salz" as a pantry item. Plan a meal with salt. Verify it doesn't appear on the shopping list until "Vorratsartikel anzeigen" is enabled.
2.  **Pantry View**: Go to "Artikel" -> "Vorratsschrank". Verify "Salz" is listed there.
3.  **Shopping List Archive**: Click a shopping item (not just the checkbox). Verify it moves instantly to "Zuletzt verwendet".
4.  **Planner Swipe**: Swipe a planned meal to the right. Verify the edit dialog opens and allows changing servings/slot.
