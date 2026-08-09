# Implementation Plan - Resource Optimization & Premium Strategy

This plan introduces measures to minimize AI costs (Gemini API) and prepares the app for a "Premium" subscription model.

## User Review Required

> [!IMPORTANT]
> **Global Learning (Cloud Cache)**: I propose a shared "Knowledge Base" in Firestore. When a user classifies a new item like "Duschgel", the result is stored globally. Other users will benefit from this result without triggering a new AI call.
> **Premium Flag**: We will add a `isPremium` status to the user profile to control access to high-resource features.

## Proposed Changes

### [Data Layer]

#### [MODIFY] [UserProfile.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/data/UserProfile.kt)
- Add `isPremium: Boolean = false`.
- Add `aiCallsThisMonth: Int = 0`.

#### [NEW] [GlobalCategoryCache] (Firestore)
- A global collection `global_knowledge` where common mappings are stored:
  `{ "term": "klopapier", "category": "Haushalt", "usageCount": 150 }`

### [Business Logic]

#### [MODIFY] [NutritionViewModel.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/viewmodel/NutritionViewModel.kt)
- **Updated `suggestCategory` Logic**:
    1.  **Catalog**: Check personal `foods`.
    2.  **Global Cache**: Check Firestore `global_knowledge` (No AI cost).
    3.  **AI (Gemini)**: Only if 1 & 2 fail **AND** the user is Premium or has not reached their free limit.
- **Limit Tracking**: Increment `aiCallsThisMonth` on every real AI call.

### [User Interface]

#### [MODIFY] [ProfileScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/ProfileScreen.kt)
- Add a "Premium Status" indicator.
- Add a (Developer only for now) toggle to simulate Premium status.

#### [MODIFY] [ShoppingListScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/ShoppingListScreen.kt)
- Show a small hint if AI categorization is limited by the free tier.

## Verification Plan

### Manual Verification
1.  **Global Hit**: User A adds "Zahnbürste" (triggers AI). User B adds "Zahnbürste". Verify that User B's request does **not** trigger an AI call (visible in logs) but gets the correct category.
2.  **Limit Test**: Set a limit of 3 AI calls. Verify that the 4th call results in "Sonstiges" with a hint to upgrade to Premium.
3.  **Premium Toggle**: Activate Premium and verify limits are ignored.
