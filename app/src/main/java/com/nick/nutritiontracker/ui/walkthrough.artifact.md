# Walkthrough - Macro Input Reordering and UX Improvements

I have reorganized the macro nutrient input fields and improved the data entry experience by adding numeric keyboards and better focus navigation.

## Changes Made

### Nutrition App & Food Editing
- **Reordered Macros**: In the `FoodEditDialog`, fields now follow the standard nutritional table format:
    1. Fett (inkl. gesättigte)
    2. Kohlenhydrate (inkl. Zucker)
    3. Eiweiß
    4. Alkohol
- **Numeric Keyboards**: Applied `KeyboardType.Decimal` or `KeyboardType.Number` to all numeric input fields (Steps, Amount, Macro values, Barcode).
- **Navigation (IME Actions)**: Added `ImeAction.Next` to most fields and `ImeAction.Done` or `ImeAction.Search` to final fields. This allows users to jump to the next field using the keyboard button.

### Profile & Goals
- **Standardized Order**: Updated the macro distribution section in `ProfileScreen` to match the new Fat → Carbs → Protein order.
- **Enhanced Inputs**: Added numeric keyboards and "Next" actions to personal data (Age, Weight, Height) and goal settings (Calorie budget, Macro percentages).

## Verification Results

### Automated Tests
- Ran `./gradlew :app:compileDebugKotlin` which completed successfully.

### Manual Verification
- Verified the new order in both the Food Creation dialog and the Profile screen.
- Verified that the numeric keyboard pops up for decimal/number fields.
- Verified that the "Next" button on the keyboard correctly moves focus through the form.
