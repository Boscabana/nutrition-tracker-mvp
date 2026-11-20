package com.nick.nutritiontracker.data

enum class RecipeDiffStatus {
    MISSING_LOCALLY,   // Recipe does not exist in local library
    MATCH,             // Recipe exists and is identical
    HAS_DISCREPANCIES  // Recipe exists, but has differences
}

enum class IngredientDiffStatus {
    MATCH,             // Ingredient name & normalized portion amount match
    AMOUNT_DIFF,       // Ingredient exists in both, but portion amount differs
    ADDED_IN_REMOTE,   // Ingredient exists in remote/pool, but not in local recipe
    MISSING_IN_REMOTE  // Ingredient exists in local recipe, but not in remote/pool
}

data class IngredientDiff(
    val name: String,
    val status: IngredientDiffStatus,
    val localAmountDisplay: String? = null,
    val remoteAmountDisplay: String? = null
)

data class RecipeComparisonResult(
    val status: RecipeDiffStatus,
    val hasIngredientNameDiff: Boolean, // "Artikel abweichend"
    val hasAmountDiff: Boolean,         // "Mengen abweichend"
    val hasInstructionDiff: Boolean,    // "Anleitung abweichend"
    val localMeal: MealEntity?,
    val remoteMealName: String,
    val remoteAuthor: String,
    val ingredientDiffs: List<IngredientDiff>,
    val remoteRecipeSteps: List<RecipeStepEntity>,
    val localRecipeSteps: List<RecipeStepEntity>
) {
    val isIdentical: Boolean get() = status == RecipeDiffStatus.MATCH
    val isMissingLocally: Boolean get() = status == RecipeDiffStatus.MISSING_LOCALLY
    val hasDifferences: Boolean get() = status == RecipeDiffStatus.HAS_DISCREPANCIES
}
