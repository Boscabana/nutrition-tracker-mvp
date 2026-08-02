# Implementation Plan - Refined Budget, Retroactive Weights & Article Swapping

This plan implements a strict cap on the daily weight loss budget, adds a date picker for weight tracking, and introduces the ability to "swap" diary entries between generic and brand variants.

## Proposed Changes

### [Business Logic]

#### [MODIFY] [NutritionViewModel.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/viewmodel/NutritionViewModel.kt)
- **`calculateWeightBudgetGrams`**:
    - Let `MaxDeficit = UserProfile.goalIntensity`.
    - Let `CurrentDeficit = (BMR + Activity) - Intake`.
    - `ResultDeficit = minOf(MaxDeficit, CurrentDeficit)`.
    - `Grams = (ResultDeficit / 7000.0) * 1000.0`.
- **`addWeightEntry`**:
    - Update signature to accept `dateIso: String`.
    - Handle overwriting existing entries for the same date.

### [User Interface]

#### [MODIFY] [WeightScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/WeightScreen.kt)
- **Date Selection**:
    - Add `selectedDate` state and a `DatePickerDialog`.
    - Add a calendar icon next to the weight input to choose the date.
- **Logic**:
    - Only update the global profile weight if the entry date is today or more recent than existing entries.

#### [MODIFY] [NutritionApp.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/NutritionApp.kt)
- **`EditEntryDialog`**:
    - Add "Swap" logic (Two arrows icon).
    - Find relatives (parent/children/generic counterparts) for the entry's food item.
    - Show a `DropdownMenu` to switch the entry to a different variant (e.g., from "Apple" to "Bio Apple").
    - Ensure nutrient data is updated to the new variant while preserving the amount/grams if possible.

## Verification Plan

### Manual Verification
1.  **Capping Check**: Set deficit to 500. Log 0 intake. Budget must show 71g (max).
2.  **Retroactive Weight**: Log weight for a past date. Verify history list and chart update.
3.  **Diary Swap**: Edit a "Generic Pasta" entry. Use the swap icon to change it to "Barilla Pasta". Verify the entry name and nutrients update instantly.
