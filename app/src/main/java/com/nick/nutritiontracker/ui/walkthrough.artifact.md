# Walkthrough - Shopping List Archive & Dynamic Grouping

I have implemented a smarter shopping list that separates active items from completed ones, with a collapsible archive section.

## Changes Made

### Shopping List Archive
- **Collapsible Section**: Added a "Zuletzt verwendet" (Recently Used) section at the bottom of the shopping list. This section stays collapsed by default to keep the interface clean.
- **Smart Reordering**: When you check an item, it instantly moves to the archive. If you uncheck it, it jumps back to its original meal-based group or the aggregated list.
- **Improved Filtering**: The meal grouping and aggregation logic now only applies to **active** items, making the current shopping task much clearer.

### Interaction Improvements
- **Group Persistence**: Items "remember" their source (the meal they belong to) even when moved to the archive.
- **Visual Feedback**: Added dividers and expansion icons to clearly distinguish between the active list and the archive.

## Verification Results

### Automated Tests
- Ran `./gradlew :app:compileDebugKotlin` which completed successfully.

### Manual Verification
- Items move correctly between the active list and the archive when toggled.
- Aggregation switch correctly sums up amounts for active items only.
- The "Zuletzt verwendet" section correctly displays the count of checked items.
