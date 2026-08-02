# Implementation Plan - Switching to No-Cost AI Tier

This plan addresses the "Your prepayment credits are depleted" error by switching the AI backend from the enterprise Vertex AI (which requires prepaid credits on Blaze) to the **Gemini Developer API** (Google AI) backend, which offers a generous no-cost tier.

## User Review Required

> [!IMPORTANT]
> **Switch to Free Tier**: I am updating the code to use the `googleAI()` backend. This backend is designed for developers and generally stays within a free quota without requiring a prepaid balance.
> **Firebase Console Action**: After I apply the code changes, you **must** enable the Gemini Developer API in the Firebase Console:
> 1. Go to the **Firebase AI Logic** page.
> 2. Go to **Settings** > **Gemini Developer API**.
> 3. Click **Enable**.

## Proposed Changes

### [Business Logic]

#### [MODIFY] [GeminiService.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/data/GeminiService.kt)
- Import `com.google.firebase.ai.GenerativeBackend`.
- Initialize the AI model using the `googleAI()` backend:
  ```kotlin
  Firebase.ai(backend = GenerativeBackend.googleAI(location = region)).generativeModel(modelName)
  ```
- This forces the use of the Developer API instead of the Enterprise Vertex AI.

## Verification Plan

### Manual Verification
1. **Model Check**: Once the Gemini Developer API is enabled in the console, tap "AI Modelle prüfen" in the app.
2. **Analysis Test**: Test image recognition. The "credits depleted" error should no longer occur as it's now routing through the no-cost developer tier.
