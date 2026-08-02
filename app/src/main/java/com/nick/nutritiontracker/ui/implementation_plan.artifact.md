# Implementation Plan - In-App Inbox & Community Integration

This plan introduces a direct sharing system ("Postfach") within the app, a community screen for managing connections, and enhanced dietary preferences in the onboarding flow.

## User Review Required

> [!IMPORTANT]
> **Authentication**: To enable direct person-to-person sharing, users will eventually need a more permanent account (Email/Google). I will start by building the system on the existing anonymous UID foundation, allowing a seamless transition to full accounts later.
> **Privacy**: User preferences (vegan, etc.) will be stored in Firestore to enable future community feed features.

## Proposed Changes

### [Data Layer]

#### [NEW] [InboxMessage.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/data/InboxMessage.kt)
- `data class InboxMessage(id, fromUid, fromName, timestamp, type (RECIPE|FOOD), payloadJson, isRead)`

#### [MODIFY] [UserProfile.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/data/UserProfile.kt)
- Add `dietaryPreference: DietaryPreference = DietaryPreference.NONE`.
- `enum class DietaryPreference { NONE, VEGETARIAN, VEGAN, PALEO, KETO }`

#### [MODIFY] [FirestoreRepository.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/data/FirestoreRepository.kt)
- Add `getInbox(uid)`: Flow for real-time message updates.
- Add `sendMessage(toUid, message)`: Logic to deliver a recipe/article to a specific user.
- Add `getHouseholdMembers(householdId)`: Fetch names of people in your house.

### [UI Components]

#### [MODIFY] [NutritionApp.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/NutritionApp.kt)
- **Top Bar**: Add a bell icon with a numeric badge for unread messages.
- **Navigation**: Add a "Community" tab.

#### [NEW] [InboxScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/InboxScreen.kt)
- Displays a list of received items.
- "Vorschau" button: See the recipe details.
- "Importieren" button: Adds the item directly to the user's library.

#### [NEW] [CommunityScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/CommunityScreen.kt)
- View current household members.
- "Freunde" section (Add by email/code).

#### [MODIFY] [SetupWizard.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/SetupWizard.kt)
- Add a new step: "Deine Ernährungsgewohnheiten" (Vegetarisch, Vegan, etc.).

#### [MODIFY] [ProfileScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/ProfileScreen.kt)
- Add a section to manage the "Permanent Account" link.

### [Business Logic]

#### [MODIFY] [NutritionViewModel.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/viewmodel/NutritionViewModel.kt)
- Implement `sendRecipeToMember(targetUid, recipe)`.
- Stream unread message count for the badge.

## Verification Plan

### Manual Verification
1. **Sharing Flow**: Use Device A to send a recipe to Device B (same household). Verify Device B shows a badge on the bell icon.
2. **Import Flow**: Open Inbox on Device B, tap "Importieren", and verify the recipe appears in the "Mahlzeiten" tab.
3. **Onboarding**: Re-run the tour and verify the new dietary preferences step works and saves correctly.
4. **Community View**: Verify names of household members appear correctly in the new screen.
