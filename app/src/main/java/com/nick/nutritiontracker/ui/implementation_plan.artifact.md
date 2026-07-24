# Implementation Plan - Fix Unresolved Reference: AddEntryCard

The project is failing to build because `AddEntryCard` is called in `NutritionApp.kt` but not defined anywhere in the project. I will implement this missing Composable to restore functionality.

## Proposed Changes

### [Nutrition UI]

#### [MODIFY] [NutritionApp.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/NutritionApp.kt)
- Add the `AddEntryCard` Composable.
- It will feature:
    - A search bar for finding foods and meals.
    - A barcode scan button that triggers `onScanRequest`.
    - A results list showing:
        - Local food items matching the query.
        - Meal templates matching the query.
        - Remote search results (from `onSearchRequest`).
    - Logic to show the `AddAmountDialog` when a food item is selected.
    - Logic to add a meal template directly when selected.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify the unresolved reference is fixed.

### Manual Verification
- Deploy the app and verify the search functionality in the "Tagebuch" (Diary) tab.
- Verify that scanning works (calls the scan callback).
- Verify that adding a food/meal from the search results works as expected.
