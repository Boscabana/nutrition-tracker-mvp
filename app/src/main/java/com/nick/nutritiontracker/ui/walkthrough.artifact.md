# Walkthrough - Minimalist App Check Debug Setup

I have configured Firebase App Check with the **Debug Provider**, which allows you to perform a private functional test on your own device and get past the "AI logic config" error.

## Changes Made

### 🛡️ App Check Debug Integration
- **Dependency Added**: Included `firebase-appcheck-debug` in your `build.gradle.kts`.
- **Application Class**: Created a minimal `NutritionApplication.kt` that initializes App Check specifically with the **Debug Provider Factory**. This ensures your requests are recognized as coming from an authorized development environment.
- **Manifest Registration**: Registered the new Application class in your `AndroidManifest.xml`.

## 🚀 How to get your Debug Token

To finalize the setup and start using the AI features, follow these steps:

1.  **Launch the App**: Run the app on your device.
2.  **Check Logcat**: In Android Studio, open the **Logcat** tab at the bottom.
3.  **Find the Token**: Enter `AppCheckDebugCritical` in the search filter. You will see a log message like:
    `D/AppCheckDebugCritical: Enter this debug secret into the allow list in the Firebase Console: 123e4567-e89b-12d3-a456-426614174000`
4.  **Firebase Console**:
    - Go to your [Firebase Console](https://console.firebase.google.com/project/nutrition-mvp-aa293/appcheck/apps).
    - Under **App Check** -> **Apps**, find your Android app.
    - Click **Manage debug tokens**.
    - Tap **Add debug token**, give it a name (e.g., "My Phone"), and paste the token from Logcat.

**Once this is done, wait a few seconds and restart the app. The "AI logic config" error will disappear, and you can test the AI image recognition!** 🥘📸✅
