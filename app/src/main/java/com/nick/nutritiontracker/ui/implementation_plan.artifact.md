# Implementation Plan - Macro Input Reordering and UX Improvements

This plan addresses the user's request to reorder macro inputs in the "FoodEditDialog" to match standard nutritional tables (Fat, then Carbs, then Protein) and to improve the input experience with numeric keyboards and field navigation.

## User Review Required

> [!IMPORTANT]
> The reordering will be applied to both the Food Creation dialog and the User Profile goal distribution for consistency. I will also apply numeric keyboards to all numeric fields (steps, amounts, portions) throughout the app to improve the overall UX.

## Proposed Changes

### [UI Components]

#### [MODIFY] [NutritionApp.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/NutritionApp.kt)

- **FoodEditDialog**:
    - Reorder macro input fields:
        1. Fat (Fett ges.)
        2. Saturated Fat (davon ges.)
        3. Carbohydrates (KH ges.)
        4. Sugar (davon Zucker)
        5. Protein (Eiweiß)
        6. Alcohol (Alc.-%)
    - Apply `KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)` to all macro fields.
    - Chain other fields (Name, Brand, Store, Portions) with `ImeAction.Next`.
    - Set `ImeAction.Done` for the final field (Barcode).
- **StepInputDialog**:
    - Apply `KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)` to the steps input.
- **AddAmountDialog**:
    - Apply `KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)` to the amount input.
- **EditEntryDialog** & **EditMealEntryDialog**:
    - Apply `KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next/Done)` to amount and ingredient fields.
- **IngredientAdjustRow**:
    - Apply `KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)` to the amount field.

#### [MODIFY] [ProfileScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/ProfileScreen.kt)

- **ProfileScreen**:
    - Reorder macro distribution inputs to match the new standard:
        1. Fats (Ungesättigte / Gesättigte)
        2. Carbs (Komplexe / Zucker)
        3. Protein
    - Apply `KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)` to age, weight, height, budget, and macro percentages.
- **MacroPercentInput**:
    - Ensure it passes `KeyboardOptions` to the underlying `AutoSelectTextField`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to ensure no syntax errors were introduced.

### Manual Verification
- Open the "Food Edit" dialog and verify the new order of macros and the numeric keyboard.
- Verify that pressing "Next" on the keyboard moves to the next logical field.
- Open the Profile and verify the reordered goals and numeric inputs.
- Test step input and amount input for numeric keyboard availability.
