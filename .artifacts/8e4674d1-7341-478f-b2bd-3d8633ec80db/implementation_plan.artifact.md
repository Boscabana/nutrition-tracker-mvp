# Fix Firestore PERMISSION_DENIED and App Crash

The application is crashing with a `PERMISSION_DENIED` error from Firestore. This is caused by two factors:
1. **Missing/Restrictive Firestore Security Rules:** The database is likely blocking requests because the current rules do not allow the app to read/write to the required collections.
2. **Missing Error Handling in Flows:** In `NutritionViewModel`, the Firestore `Flow` collections are not wrapped in `catch` blocks. When a `PERMISSION_DENIED` error occurs, the flow terminates with an exception that propagates and crashes the app.

## Proposed Changes

### 1. [App Logic] Handle Firestore Flow Errors
Modify `NutritionViewModel.kt` to catch exceptions in all Firestore-backed flows. This will prevent the app from crashing and allow it to log the error instead.

#### [MODIFY] [NutritionViewModel.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/viewmodel/NutritionViewModel.kt)
- Wrap `collect` calls in `try-catch` or use the `.catch {}` operator for each Firestore flow in `setupFirebaseSync`.

### 2. [Firebase Configuration] Firestore Security Rules [NEW]
Provide a recommended `firestore.rules` file that defines the necessary permissions for the app's structure.

#### [NEW] [firestore.rules](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/firestore.rules)
- Define rules for `users` and `households` collections and their sub-collections.

## Verification Plan

### Manual Verification
1. **Apply Code Changes:** Verify that the app no longer crashes even if permissions are denied (check Logcat for "PERMISSION_DENIED" logs instead of a fatal crash).
2. **Update Rules:** Ask the user to copy the content of `firestore.rules` into their Firebase Console (Firestore -> Rules tab).
3. **Test App:** Verify that data syncs correctly once the rules are applied.

> [!IMPORTANT]
> The user MUST manually copy the generated `firestore.rules` to the Firebase Console if they are not using the Firebase CLI for deployment.
