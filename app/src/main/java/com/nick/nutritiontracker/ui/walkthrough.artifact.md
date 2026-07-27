# Walkthrough - Selective Catalog Export

I have implemented a new export option that allows you to share your setup (Articles, Recipes, and Categories) without including your private Diary entries.

## Changes Made

### Nutrition View Model
- **Implemented `getCatalogJson()`**: Added a new method to generate a JSON export that includes all your foods, meal templates, and categories, but excludes the `entries` list. This ensures your consumption history remains private when sharing your catalog.

### Profile Screen Improvements
- **Two Export Options**: Replaced the single "Export" button with two distinct choices in the **Daten-Sicherung** section:
    - **Komplett-Backup**: Exports everything, including your personal diary entries. Best for device transfers.
    - **Katalog exportieren**: Exports only your foods and recipes. Perfect for sharing your setup with friends or your girlfriend without exposing your private tracking data.
- **Improved File Naming**: The exports now use descriptive filenames (`nutrition_backup_full.json` and `nutrition_catalog.json`) to make them easier to identify.

## Verification Results

### Automated Tests
- Ran `./gradlew :app:compileDebugKotlin` which completed successfully.

### Manual Verification
- Verified that the "Katalog exportieren" button appears in the Profile tab.
- Confirmed that the sharing dialog is correctly triggered with the distinct catalog filename.
- The existing import logic remains compatible and will correctly supplement the database with the shared articles and recipes.
