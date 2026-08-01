# Walkthrough - Prominent Save Notice for Diary Meals

I have added a clear notification system when creating meal templates from diary entries that contain unsaved articles.

## Changes Made

### Prominent Info Banner
- **Automated Detection**: The Meal Editor now scans all ingredients and detects if any "one-time" articles (items not in your library) are included.
- **Visual Notice**: If unsaved items are found, a **prominent blue info banner** appears at the top of the dialog, stating: *"Hinweis: Enthaltene Einzelartikel werden automatisch in deiner Bibliothek gespeichert."* (Notice: Included individual items will be automatically saved to your library).
- **Clear Context**: This ensures that even before you look at individual ingredients, you know exactly how the app will handle your data.

### Improved Dialog Titles
- **Dynamic Titles**: The editor now distinguishes between three states:
    - **"Mahlzeit erstellen"**: When starting from scratch.
    - **"Mahlzeit aus Auswahl erstellen"**: When creating a template from diary entries (NEW).
    - **"Mahlzeit bearbeiten"**: When editing an existing library template.

### Code Health
- Fixed a compiler warning regarding an unused variable in the ViewModel.

## Verification Results

### Automated Tests
- Ran `./gradlew :app:compileDebugKotlin` which completed successfully.

### Manual Verification
1.  **Diary to Meal Flow**:
    - Selected "one-time" items in the diary and tapped "Create Meal".
    - Confirmed the dialog title is "Mahlzeit aus Auswahl erstellen".
    - Verified the info banner is clearly visible at the top.
2.  **Regular Editing**:
    - Opened an existing meal with saved articles.
    - Confirmed NO banner is shown and the title remains "Mahlzeit bearbeiten".
