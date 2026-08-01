# Implementation Plan - Gamification & Weight Tracking Update

This plan introduces a comprehensive weight tracking system, a gamified "Weight Loss Budget," and an intelligent "Metabolic Factor" to provide users with more accurate progress tracking and motivation.

## Proposed Changes

### [Data Layer]

#### [NEW] [WeightEntry.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/data/WeightEntry.kt)
- `data class WeightEntry(val date: String, val weight: Double)`
- `data class DayVerification(val date: String, val isComplete: Boolean)`

#### [MODIFY] [UserProfile.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/data/UserProfile.kt)
- Add `initialWeight: Double?` to help calculate the cumulative metabolic factor.
- Add `metabolicFactor: Double = 1.0` (Secretly updated).

### [Business Logic]

#### [MODIFY] [NutritionViewModel.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/viewmodel/NutritionViewModel.kt)
- **State**:
    - `weightHistory`: `SnapshotStateList<WeightEntry>` (Local + Household Sync?).
    - `verifications`: `SnapshotStateMap<String, Boolean>`.
- **Calculations**:
    - `currentDayWeightBudget`: Calculate daily weight loss in grams based on `(BMR + Activity) - Intake`.
    - `metabolicFactor`: Periodically compare `Predicted Weight Loss` (from calorie deficits) vs `Actual Weight Loss` (from scale).
- **Gamification Logic**:
    - "Cheat Days": Handle unverified days as ±0 deficit.
    - End-of-day verification trigger.
    - Weekly Summary (Sunday popup).

### [User Interface]

#### [NEW] [WeightScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/WeightScreen.kt)
- **Header**: Current weight & total loss.
- **Entry**: Field to log today's weight (updates profile weight).
- **Chart**: Line chart showing weight progress over time.
- **List**: History of weight entries.

#### [MODIFY] [NutritionApp.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/NutritionApp.kt)
- Add "Gewicht" tab to `NavigationBar`.
- Implement global Popup logic for:
    1. **End-of-day verification**: "Everything logged today?"
    2. **Sunday Weekly Update**: Summary of weight loss (only for verified negative-balance days).

#### [MODIFY] [MacroProgressSection.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/MacroProgressSection.kt)
- Add "Abnehm-Budget" display in grams.
- Visually show how activity (steps) increases the remaining gram budget.

#### [MODIFY] [TodayScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/NutritionApp.kt) (Inside TodayScreen)
- Update Calendar view to show color-coded status:
    - **Blue + Check**: Goal met (Deficit maintained).
    - **Green**: Under BMR but over deficit goal.
    - **Yellow**: Over BMR (Gain).
    - **Red**: No entries.

## Verification Plan

### Manual Verification
1. **Weight Entry**: Log a new weight. Verify `UserProfile` weight updates and the chart reflects the new point.
2. **Gram Budget**: Eat 500 kcal under BMR. Verify the "Abnehm-Budget" shows ~71g. Add 10,000 steps and verify the budget increases.
3. **Verification Flow**: Close the day (simulated). Verify the "Cheat Day" logic if unverified.
4. **Developer Options**: Inspect the "Metabolic Factor" after having 3+ weight entries and deficits logged.
