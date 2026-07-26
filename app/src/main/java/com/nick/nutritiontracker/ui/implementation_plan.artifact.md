# Implementation Plan - Collapsible Shopping List Archive

This plan implements an archive for checked shopping items. Items marked as completed will move to a collapsed "Zuletzt verwendet" (Recently Used) section at the bottom of the shopping list.

## Proposed Changes

### [User Interface]

#### [MODIFY] [ShoppingListScreen.kt](file:///C:/Entwicklung/AndroidStudio/nutrition-tracker-mvp/app/src/main/java/com/nick/nutritiontracker/ui/ShoppingListScreen.kt)

- **Archive State**: Add a `var isArchiveExpanded by remember { mutableStateOf(false) }` to track the collapse state of the "Zuletzt verwendet" section.
- **Item Filtering**:
    - **Active List**: Filter items where `isChecked == false`. Group or aggregate these according to the current user settings.
    - **Archive List**: Filter items where `isChecked == true`.
- **Dynamic Layout**:
    - Show the **Active List** (grouped by meal or aggregated) at the top.
    - Add a header/divider for the **Archive**.
    - Implement a button/header for "Zuletzt verwendet" that toggles `isArchiveExpanded`.
    - Show the **Archive List** only when expanded.
- **Sorting**: Ensure that when an item is unchecked, it naturally flows back into the active list's logic (since it retains its `sourceName`).

## Verification Plan

### Manual Verification
1.  **Check-off Action**: Tap a checkbox on "Brokkoli". Verify it disappears from the main list and appears in the "Zuletzt verwendet" section.
2.  **Expansion**: Verify that the "Zuletzt verwendet" section can be toggled open and closed.
3.  **Restore Action**: Uncheck "Brokkoli" in the archive. Verify it moves back to the top list, correctly placed under its original meal header (if grouping is active).
4.  **Aggregation**: Test the "Zusammenfassen" toggle while items are in both lists. Sums should only reflect active items or keep the sections distinct as appropriate for clarity.
