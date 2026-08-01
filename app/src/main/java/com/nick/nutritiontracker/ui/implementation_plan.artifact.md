# Implementation Plan - Fix Setup Wizard Flicker

This plan resolves the UI flicker where the Setup Wizard (Tour) is briefly visible during app launch even when onboarding is disabled.

## Proposed Changes

### [ViewModel]

#### [MODIFY] [ProfileViewModel.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/viewmodel/ProfileViewModel.kt)
- Change the `userProfile` state flow to be nullable: `StateFlow<UserProfile?>`.
- Set `initialValue = null`. This explicitly marks the state as "loading" until the first value is read from the disk.

### [UI Components]

#### [MODIFY] [NutritionApp.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/NutritionApp.kt)
- **Loading State**: Check if `userProfile` is `null`. If so, display an empty screen or a simple loading indicator.
- **Setup Logic**: Once `userProfile` is non-null, use its actual value from the disk to decide if `SetupWizard` should be shown.
- **Routing**: Clean up the `LaunchedEffect` logic to be more robust.

## Verification Plan

### Manual Verification
1. **Cold Start**: Close the app and start it. Verify that the Tour screen does **not** flash if onboarding is completed and disabled.
2. **First Start**: Clear app data and start. Verify the Setup Wizard appears correctly (with no flicker of the main app).
3. **Manual Reset**: Trigger a manual reset from Developer Options. Verify it still correctly jumps into the wizard.
