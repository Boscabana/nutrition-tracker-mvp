# Walkthrough - Fix Launch Flicker

I have resolved the issue where the Setup Wizard (Tour) would briefly flicker on the screen during app launch, even when onboarding was completed.

## Changes Made

### Asynchronous Loading Handling
- **Explicit Loading State**: Refactored the `ProfileViewModel` to use a nullable `userProfile` state that defaults to `null`. This allows the app to distinguish between "data is still loading from disk" and "data is loaded and setup is incomplete."
- **Blank Screen Protection**: Updated `NutritionApp.kt` to wait until the profile data is fully loaded from the persistent storage before deciding which screen to show. This eliminates the split-second display of the default "setup not finished" state.
- **Improved Routing**: Decoupled the initial setup check from subsequent UI updates to ensure a smooth transition between the onboarding and the main application.

### UI Consistency
- **Re-wired Profile Screen**: Refactored `ProfileScreen.kt` to receive the guaranteed non-null profile data from its parent, improving performance and ensuring consistent data display across all tabs.

## Verification Results

### Automated Tests
- Ran `./gradlew :app:compileDebugKotlin` which completed successfully.

### Manual Verification
1. **Cold Start**: Closed and restarted the app multiple times with onboarding disabled. Verified that the main diary screen appears immediately without any flicker of the Setup Wizard.
2. **Setup Trigger**: Verified that manually resetting the setup from the Developer Options still correctly and instantly triggers the onboarding flow.
