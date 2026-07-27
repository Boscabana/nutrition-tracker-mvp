# Implementation Plan - Selective Export (Catalog Only)

This plan allows users to export only their articles and meals (the "Catalog") without including their personal diary entries. This is useful for sharing a setup with friends without sharing private consumption data.

## Proposed Changes

### [Business Logic]

#### [MODIFY] [NutritionViewModel.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/viewmodel/NutritionViewModel.kt)
- Add a new method `getCatalogJson(): String`.
    - This will generate a `BackupData` object containing `foods`, `meals`, and `categories`, but leaving `entries` empty.
- Since the existing `importBackup` already checks for item existence and doesn't delete existing data (it supplements), no changes to the import logic are required.

### [User Interface]

#### [MODIFY] [ProfileScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/ProfileScreen.kt)
- **Export Options**: Add a second export button or a selection dialog to the "Daten-Sicherung" section.
- **Button 1**: "Komplett-Backup" (Backup including diary).
- **Button 2**: "Katalog exportieren" (Foods & Recipes only).
- Update the sharing intent for the Catalog export to use a distinct filename like `nutrition_catalog.json`.

## Verification Plan

### Manual Verification
1.  **Catalog Export**:
    - Tap "Katalog exportieren".
    - Share the file with another device.
    - Import the file.
    - Verify that all foods, categories, and meals are imported, but the diary remains untouched.
2.  **Full Backup**:
    - Verify that the original "Komplett-Backup" still includes diary entries as expected.
