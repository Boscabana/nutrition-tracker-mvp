package com.nick.nutritiontracker.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.nick.nutritiontracker.data.*
import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.UUID

class NutritionViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("nutrition_tracker", Context.MODE_PRIVATE)
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }

    val healthConnectManager = HealthConnectManager(application)
    val firebaseManager = FirebaseManager()
    private val firestoreRepository = FirestoreRepository()
    private val profileRepository = ProfileRepository(application)
    private var syncJob: Job? = null

    var selectedDate by mutableStateOf(LocalDate.now())
        private set

    fun selectDate(date: LocalDate) {
        selectedDate = date
        syncActivityForSelectedDate()
    }

    private var nextFoodId = 1L
    private var nextPortionId = 1L
    private var nextEntryId = 1L
    private var nextMealId = 1L

    val foods = mutableStateListOf<FoodItemEntity>()
    val meals = mutableStateListOf<MealEntity>()
    val allEntries = mutableStateListOf<FoodEntryEntity>()
    val categories = mutableStateListOf<String>()
    
    val plannedEntries = mutableStateListOf<FoodEntryEntity>()
    val shoppingList = mutableStateListOf<ShoppingItem>()
    var isShoppingListAggregated by mutableStateOf(false)
    var showPantryInShoppingList by mutableStateOf(false)
    var shoppingListSortByCategory by mutableStateOf(prefs.getBoolean("shopping_sort_category", true))
        private set

    fun updateShoppingListSort(sort: Boolean) {
        shoppingListSortByCategory = sort
        prefs.edit().putBoolean("shopping_sort_category", sort).apply()
    }
    
    var foodSearchQuery by mutableStateOf("")
    var selectedFoodCategory by mutableStateOf<String?>(null)
    
    var pendingRecipeImport by mutableStateOf<RecipeData?>(null)
    
    val weightHistory = mutableStateListOf<WeightEntry>()
    val dayVerifications = mutableStateMapOf<String, Boolean>()
    
    val inboxMessages = mutableStateListOf<InboxMessage>()
    val unreadInboxCount by derivedStateOf { inboxMessages.count { !it.isRead } }

    val householdMembers = mutableStateListOf<Map<String, String>>()
    
    private val pendingDeletions = mutableMapOf<String, Long>()

    var forceOnboardingOnStart by mutableStateOf(prefs.getBoolean("force_onboarding", false))
        private set
    
    var geminiApiKey by mutableStateOf(
        prefs.getString("gemini_api_key", null) ?: com.nick.nutritiontracker.BuildConfig.GEMINI_API_KEY
    )
        private set
    
    var selectedAiModel by mutableStateOf(prefs.getString("selected_ai_model", "gemini-3.6-flash") ?: "gemini-3.6-flash")
        private set

    var biometricEnabled by mutableStateOf(prefs.getBoolean("biometric_enabled", false))
        private set

    var isAppUnlocked by mutableStateOf(false)
        private set

    fun unlockApp() {
        isAppUnlocked = true
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        biometricEnabled = enabled
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
    }

    fun updateSelectedAiModel(model: String) {
        selectedAiModel = model
        prefs.edit().putString("selected_ai_model", model).apply()
    }

    fun updateGeminiApiKey(key: String) {
        geminiApiKey = key
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    var isAnalyzingImage by mutableStateOf(false)
    var aiEstimationResult by mutableStateOf<AiEstimationResult?>(null)
    var aiErrorMessage by mutableStateOf<String?>(null)

    val availableAiModels = mutableStateListOf<AiModelStatus>()

    fun probeAiModels() {
        availableAiModels.clear()
        viewModelScope.launch {
            val service = GeminiService()
            val candidates = listOf("gemini-3.6-flash", "gemini-3.1-pro", "gemini-2.0-flash")
            candidates.forEach { model ->
                val status = service.testModelAvailability(model)
                availableAiModels.add(status)
            }
        }
    }

    fun analyzeMealImage(bitmap: Bitmap, isUserPremium: Boolean) {
        if (!isUserPremium) {
            aiErrorMessage = "Premium-Funktion: Upgrade erforderlich für die AI Bilderkennung."
            return
        }

        Log.d("NutritionViewModel", "Starting AI analysis. Selected model: $selectedAiModel")
        
        isAnalyzingImage = true
        aiErrorMessage = null
        viewModelScope.launch {
            try {
                val service = GeminiService()
                val result = service.estimateNutrition(bitmap, selectedAiModel)
                if (result != null) {
                    aiEstimationResult = result
                } else {
                    aiErrorMessage = "Das Bild konnte nicht analysiert werden."
                }
            } catch (e: Exception) {
                Log.e("NutritionViewModel", "AI Analysis failed", e)
                val msg = e.toString().lowercase()
                aiErrorMessage = when {
                    msg.contains("404") || msg.contains("not found") -> 
                        "AI Modell nicht verfügbar. Bitte prüfe die API-Berechtigungen deines Keys im Google AI Studio."
                    msg.contains("403") -> "API Key ungültig oder keine Berechtigung for die AI-Modelle."
                    else -> "Fehler bei der Analyse: ${e.localizedMessage}"
                }
            } finally {
                isAnalyzingImage = false
            }
        }
    }

    fun setForceOnboarding(force: Boolean) {
        forceOnboardingOnStart = force
        prefs.edit().putBoolean("force_onboarding", force).apply()
    }
    
    val dailySteps = mutableStateMapOf<String, Int>()
    val dailyTotalCalories = mutableStateMapOf<String, Double>()
    val dailyExerciseSessions = mutableStateMapOf<String, List<ExerciseSessionInfo>>()

    val todayEntries by derivedStateOf {
        allEntries.filter { it.dateIso == selectedDate.toString() }
            .sortedByDescending { it.id }
    }

    val todayTotalKcal by derivedStateOf { todayEntries.sumOf { it.kcal } }
    val todayTotalProtein by derivedStateOf { todayEntries.sumOf { it.protein } }
    val todayTotalComplexCarbs by derivedStateOf { todayEntries.sumOf { it.complexCarbs } }
    val todayTotalSugar by derivedStateOf { todayEntries.sumOf { it.sugar } }
    val todayTotalUnsaturatedFat by derivedStateOf { todayEntries.sumOf { it.unsaturatedFat } }
    val todayTotalSaturatedFat by derivedStateOf { todayEntries.sumOf { it.saturatedFat } }
    
    val todaySteps by derivedStateOf { dailySteps[selectedDate.toString()] ?: 0 }
    
    val todayStepKcal by derivedStateOf {
        val profile = firebaseManager.userProfile.value ?: return@derivedStateOf 0.0
        calculateStepKcal(selectedDate.toString(), profile)
    }

    val todayExerciseKcal by derivedStateOf { 
        dailyExerciseSessions[selectedDate.toString()]?.sumOf { it.calories ?: 0.0 } ?: 0.0 
    }
    
    val todayActivityKcal by derivedStateOf {
        todayStepKcal + todayExerciseKcal
    }

    private fun calculateStepKcal(dateIso: String, profile: UserProfile): Double {
        val steps = dailySteps[dateIso] ?: 0
        val heightM = profile.heightCm / 100.0
        return 0.55 * profile.weightKg * steps * 0.415 * heightM / 1000.0
    }

    private fun getActivityKcal(dateIso: String, profile: UserProfile): Double {
        val stepKcal = calculateStepKcal(dateIso, profile)
        val sessions = dailyExerciseSessions[dateIso] ?: emptyList()
        val workoutKcal = sessions.sumOf { it.calories ?: 0.0 }
        
        return stepKcal + workoutKcal
    }

    fun calculateWeightBudgetGrams(dateIso: String, userProfile: UserProfile): Double {
        val dateEntries = allEntries.filter { it.dateIso == dateIso }
        if (dateEntries.isEmpty()) return 0.0
        val intake = dateEntries.sumOf { it.kcal }
        val activityKcal = getActivityKcal(dateIso, userProfile)
        
        val actualDeficit = (userProfile.bmr + activityKcal) - intake
        val cappedDeficit = minOf(userProfile.goalIntensity.toDouble(), actualDeficit)
        return (cappedDeficit / 7000.0) * 1000.0
    }

    fun getDayStatusColor(dateIso: String, profile: UserProfile): Int {
        val entries = allEntries.filter { it.dateIso == dateIso }
        if (entries.isEmpty()) return 0xFFFF0000.toInt()
        val intake = entries.sumOf { it.kcal }
        val activityKcal = getActivityKcal(dateIso, profile)

        val bmrLimit = profile.bmr + activityKcal
        val deficitLimit = (profile.bmr - profile.goalIntensity) + activityKcal
        return when {
            intake <= deficitLimit -> 0xFF2196F3.toInt()
            intake <= bmrLimit -> 0xFF4CAF50.toInt()
            else -> 0xFFFFC107.toInt()
        }
    }

    init {
        loadFoods(); loadMeals(); loadEntries(); loadActivity(); loadCategories(); loadWeightHistory(); loadVerifications()
        if (foods.isEmpty()) createDefaultFoods()
        if (categories.isEmpty()) createDefaultCategories()
        syncActivityForSelectedDate()
        setupFirebaseSync()
    }

    fun syncActivityForSelectedDate() {
        viewModelScope.launch {
            if (healthConnectManager.isAvailable() && healthConnectManager.hasAllPermissions()) {
                val data = healthConnectManager.syncActivityForSelectedDate(selectedDate)
                Log.d("Sync", "Activity Sync for $selectedDate: Steps=${data.steps}, Total=${data.totalKcal}, Sessions=${data.sessions.size}")
                updateActivity(data.steps, data.totalKcal, data.sessions)
            }
        }
    }

    private fun setupFirebaseSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            // 1. Profile consistency
            launch {
                firebaseManager.userProfile.collectLatest { cloudProfile ->
                    if (cloudProfile != null) {
                        val localProfile = profileRepository.userProfileFlow.first()
                        if (localProfile.firstName.isBlank() && cloudProfile.firstName.isNotBlank()) {
                            Log.d("Sync", "Initial profile setup from cloud")
                            profileRepository.saveProfile(cloudProfile)
                        }
                    }
                }
            }

            // 2. Personal Data Sync
            launch {
                firebaseManager.currentUser.collectLatest { user ->
                    if (user != null) {
                        Log.d("Sync", "Starting personal sync for user: ${user.uid}")
                        coroutineScope {
                            val migrationKey = "migration_done_${user.uid}"
                            if (!prefs.getBoolean(migrationKey, false) && foods.isNotEmpty()) {
                                migrateLocalDataToCloud(user.uid)
                                prefs.edit().putBoolean(migrationKey, true).apply()
                            }

                            launch {
                                firestoreRepository.getPersonalFoods(user.uid).collect { cloudFoods ->
                                    Log.d("Sync", "Received ${cloudFoods.size} foods from cloud")
                                    val cloudIds = cloudFoods.map { it.id }.toSet()
                                    cloudFoods.forEach { cloudFood ->
                                        val localIndex = foods.indexOfFirst { it.id == cloudFood.id }
                                        if (localIndex != -1) {
                                            if (cloudFood.lastModified > foods[localIndex].lastModified + 2000) {
                                                foods[localIndex] = cloudFood
                                            }
                                        } else {
                                            foods.add(cloudFood)
                                        }
                                    }
                                    val now = System.currentTimeMillis()
                                    val localOnly = foods.filter { it.id != 0L && !cloudIds.contains(it.id) }
                                    localOnly.forEach { local ->
                                        if (now - local.lastModified < 15000) {
                                            Log.d("Sync", "Healing cloud with local food: ${local.name}")
                                            firestoreRepository.savePersonalFood(user.uid, local)
                                        }
                                    }
                                    updateNextFoodIds()
                                    saveFoods()
                                }
                            }

                            launch {
                                firestoreRepository.getPersonalMeals(user.uid).collect { cloudMeals ->
                                    Log.d("Sync", "Received ${cloudMeals.size} meals from cloud")
                                    val cloudIds = cloudMeals.map { it.id }.toSet()
                                    cloudMeals.forEach { cloudMeal ->
                                        val localIndex = meals.indexOfFirst { it.id == cloudMeal.id }
                                        if (localIndex != -1) {
                                            if (cloudMeal.lastModified > meals[localIndex].lastModified + 2000) {
                                                meals[localIndex] = cloudMeal
                                            }
                                        } else {
                                            meals.add(cloudMeal)
                                        }
                                    }
                                    val now = System.currentTimeMillis()
                                    val localOnly = meals.filter { it.id != 0L && !cloudIds.contains(it.id) }
                                    localOnly.forEach { local ->
                                        if (now - local.lastModified < 15000) {
                                            Log.d("Sync", "Healing cloud with local meal: ${local.name}")
                                            firestoreRepository.savePersonalMeal(user.uid, local)
                                        }
                                    }
                                    
                                    // Auto-Repair ingredient IDs in meals
                                    if (foods.isNotEmpty()) {
                                        repairMealIngredients(user.uid)
                                    }
                                    
                                    updateNextMealIds()
                                    saveMeals()
                                }
                            }

                            launch {
                                firestoreRepository.getWeightHistory(user.uid).collect { cloudWeight ->
                                    if (cloudWeight.isNotEmpty()) {
                                        weightHistory.clear()
                                        weightHistory.addAll(cloudWeight.sortedByDescending { it.dateIso })
                                    }
                                }
                            }

                            launch {
                                firestoreRepository.getPersonalEntries(user.uid).collect { cloudEntries ->
                                    Log.d("Sync", "Received ${cloudEntries.size} entries from cloud")
                                    allEntries.clear()
                                    allEntries.addAll(cloudEntries)
                                    updateNextEntryIds()
                                    saveEntries()
                                }
                            }
                        }
                    }
                }
            }

            // 3. Shared Data Sync (Household)
            launch {
                firebaseManager.household.collectLatest { household ->
                    if (household != null) {
                        Log.d("Sync", "Starting shared data sync for household: ${household.id}")
                        coroutineScope {
                            launch {
                                try {
                                    val members = firestoreRepository.getHouseholdMembers(household.members)
                                    householdMembers.clear()
                                    householdMembers.addAll(members)
                                } catch (e: Exception) { Log.e("Firestore", "Error members", e) }
                            }
                            
                            launch {
                                firestoreRepository.getInboxMessages(firebaseManager.currentUser.value?.uid ?: "").collect { messages ->
                                    inboxMessages.clear()
                                    inboxMessages.addAll(messages)
                                }
                            }
                            
                            launch {
                                firestoreRepository.getPlannedEntries(household.id).collect { entries ->
                                    Log.d("Sync", "Received ${entries.size} planned entries for household ${household.id}")
                                    val now = System.currentTimeMillis()
                                    val filtered = entries.filter { 
                                        val delTime = pendingDeletions[it.id.toString()]
                                        delTime == null || now - delTime > 10000 
                                    }
                                    plannedEntries.clear()
                                    plannedEntries.addAll(filtered)
                                    
                                    // Repair planned entry ingredients
                                    if (foods.isNotEmpty()) {
                                        repairPlannedEntryIngredients(household.id)
                                    }
                                }
                            }
                            
                            launch {
                                firestoreRepository.getShoppingList(household.id).collect { items ->
                                    Log.d("Sync", "Received ${items.size} shopping items for household ${household.id}")
                                    val now = System.currentTimeMillis()
                                    val filtered = items.filter { 
                                        val delTime = pendingDeletions[it.id]
                                        delTime == null || now - delTime > 10000 
                                    }
                                    shoppingList.clear()
                                    shoppingList.addAll(filtered)
                                }
                            }
                        }
                    } else {
                        Log.d("Sync", "Clearing shared data (No household)")
                        plannedEntries.clear()
                        shoppingList.clear()
                        householdMembers.clear()
                        inboxMessages.clear()
                    }
                }
            }
        }
    }

    private fun repairMealIngredients(uid: String) {
        var anyFixed = false
        meals.forEachIndexed { index, meal ->
            var mealFixed = false
            val fixedIngredients = meal.ingredients.map { ing ->
                if (ing.foodItemId == -1L) return@map ing
                val food = foods.find { it.id == ing.foodItemId }
                val broken = food == null || !food.name.equals(ing.name, ignoreCase = true)
                
                if (broken) {
                    val correctFood = findFoodByIdOrName(0, ing.name, ing.brand)
                    if (correctFood != null && correctFood.id != ing.foodItemId) {
                        Log.d("DeepRepair", "Auto-Repair Meal '${meal.name}': Fixed '${ing.name}' (${ing.foodItemId} -> ${correctFood.id})")
                        mealFixed = true
                        anyFixed = true
                        ing.copy(foodItemId = correctFood.id)
                    } else { ing }
                } else { ing }
            }
            if (mealFixed) {
                val updatedMeal = meal.copy(ingredients = fixedIngredients, lastModified = System.currentTimeMillis())
                meals[index] = updatedMeal
                viewModelScope.launch { firestoreRepository.savePersonalMeal(uid, updatedMeal) }
            }
        }
        if (anyFixed) saveMeals()
    }

    private fun repairPlannedEntryIngredients(householdId: String) {
        plannedEntries.forEachIndexed { index, entry ->
            if (entry.isMeal && entry.mealIngredients != null) {
                var entryFixed = false
                val fixedIngredients = entry.mealIngredients.map { ing ->
                    if (ing.foodItemId == -1L) return@map ing
                    val food = foods.find { it.id == ing.foodItemId }
                    val broken = food == null || !food.name.equals(ing.name, ignoreCase = true)
                    
                    if (broken) {
                        val correctFood = findFoodByIdOrName(0, ing.name, ing.brand)
                        if (correctFood != null && correctFood.id != ing.foodItemId) {
                            Log.d("DeepRepair", "Auto-Repair PlannedEntry '${entry.name}': Fixed '${ing.name}' (${ing.foodItemId} -> ${correctFood.id})")
                            entryFixed = true
                            ing.copy(foodItemId = correctFood.id)
                        } else { ing }
                    } else { ing }
                }
                if (entryFixed) {
                    val updatedEntry = entry.copy(mealIngredients = fixedIngredients)
                    plannedEntries[index] = updatedEntry
                    viewModelScope.launch { firestoreRepository.addPlannedEntry(householdId, updatedEntry) }
                }
            }
        }
    }

    private fun updateNextFoodIds() {
        nextFoodId = (foods.maxOfOrNull { it.id } ?: 0L) + 1
        nextPortionId = (foods.flatMap { it.portions }.maxOfOrNull { it.id } ?: 0L) + 1
    }

    private fun updateNextMealIds() {
        nextMealId = (meals.maxOfOrNull { it.id } ?: 0L) + 1
    }

    private fun updateNextEntryIds() {
        nextEntryId = (allEntries.maxOfOrNull { it.id } ?: 0L) + 1
    }

    private suspend fun migrateLocalDataToCloud(uid: String) {
        Log.d("Migration", "Starting migration of local data to cloud for user $uid")
        
        // Take a clean snapshot of current local lists
        val foodsToMigrate = foods.toList()
        val mealsToMigrate = meals.toList()

        Log.d("Migration", "Found ${foodsToMigrate.size} local foods and ${mealsToMigrate.size} local meals to migrate")
        
        foodsToMigrate.forEach { food ->
            Log.d("Migration", "Uploading food: ${food.name} (Generic: ${food.isGeneric})")
            firestoreRepository.savePersonalFood(uid, food) 
        }
        mealsToMigrate.forEach { meal -> 
            Log.d("Migration", "Uploading meal: ${meal.name}")
            firestoreRepository.savePersonalMeal(uid, meal) 
        }
        Log.d("Migration", "Migration finished for user $uid")
    }

    suspend fun forceMigrationToCloud() {
        val user = firebaseManager.currentUser.value ?: return
        migrateLocalDataToCloud(user.uid)
    }

    fun repairAndWipeLocalCache() {
        val user = firebaseManager.currentUser.value ?: return
        
        viewModelScope.launch {
            // New Step: Deep Repair before wiping
            forceDeepRepair()

            // 1. Cancel active sync to stop listeners
            syncJob?.cancel()
            
            // 2. Clear local lists (State)
            foods.clear()
            meals.clear()
            allEntries.clear()
            weightHistory.clear()
            
            // 3. Clear SharedPrefs JSON strings (Disk)
            prefs.edit()
                .remove("foods_json")
                .remove("meals_json")
                .remove("entries_json")
                .remove("weight_history_json")
                .putBoolean("migration_done_${user.uid}", true)
                .apply()
            
            Log.d("DeepRepair", "Local cache wiped. Restarting sync for clean pull.")
            
            // 4. Restart sync. This will re-attach all Firestore listeners and force a fresh data pull.
            setupFirebaseSync()
        }
    }

    fun forceDeepRepair() {
        val user = firebaseManager.currentUser.value ?: return
        val householdId = firebaseManager.household.value?.id
        
        Log.d("DeepRepair", "Starting Deep Repair for ${meals.size} meals and ${plannedEntries.size} planned entries")
        
        // 1. Repair Meals
        meals.forEachIndexed { index, meal ->
            var mealFixed = false
            val updatedIngredients = meal.ingredients.map { ing ->
                val correctFood = findFoodByIdOrName(ing.foodItemId, ing.name, ing.brand)
                if (correctFood != null && correctFood.id != ing.foodItemId) {
                    Log.d("DeepRepair", "Deep-Repair Meal '${meal.name}': Fixed '${ing.name}' (${ing.foodItemId} -> ${correctFood.id})")
                    mealFixed = true
                    ing.copy(foodItemId = correctFood.id)
                } else {
                    ing
                }
            }
            if (mealFixed) {
                val updatedMeal = meal.copy(ingredients = updatedIngredients, lastModified = System.currentTimeMillis())
                meals[index] = updatedMeal
                viewModelScope.launch { firestoreRepository.savePersonalMeal(user.uid, updatedMeal) }
            }
        }
        
        // 2. Repair Planned Entries
        if (householdId != null) {
            plannedEntries.forEachIndexed { index, entry ->
                if (entry.isMeal && entry.mealIngredients != null) {
                    var entryFixed = false
                    val updatedIngredients = entry.mealIngredients.map { ing ->
                        val correctFood = findFoodByIdOrName(ing.foodItemId, ing.name, ing.brand)
                        if (correctFood != null && correctFood.id != ing.foodItemId) {
                            Log.d("DeepRepair", "Deep-Repair PlannedEntry '${entry.name}': Fixed Zutat '${ing.name}' (${ing.foodItemId} -> ${correctFood.id})")
                            entryFixed = true
                            ing.copy(foodItemId = correctFood.id)
                        } else {
                            ing
                        }
                    }
                    if (entryFixed) {
                        val updatedEntry = entry.copy(mealIngredients = updatedIngredients)
                        plannedEntries[index] = updatedEntry
                        viewModelScope.launch { firestoreRepository.addPlannedEntry(householdId, updatedEntry) }
                    }
                } else if (!entry.isMeal && entry.foodItemId != -1L) {
                    val correctFood = findFoodByIdOrName(entry.foodItemId, entry.name, entry.brand)
                    if (correctFood != null && correctFood.id != entry.foodItemId) {
                        Log.d("DeepRepair", "Deep-Repair PlannedEntry '${entry.name}': Fixed ID (${entry.foodItemId} -> ${correctFood.id})")
                        val updatedEntry = entry.copy(foodItemId = correctFood.id)
                        plannedEntries[index] = updatedEntry
                        viewModelScope.launch { firestoreRepository.addPlannedEntry(householdId, updatedEntry) }
                    }
                }
            }
        }
        
        saveMeals()
        Log.d("DeepRepair", "Deep Repair finished")
    }

    private fun getGeneralName(food: FoodItemEntity): String {
        val parent = food.parentId?.let { pId -> foods.find { it.id == pId } }
        return parent?.name ?: food.name
    }

    fun findFoodByIdOrName(id: Long, name: String, brand: String? = null): FoodItemEntity? {
        // 1. Precise ID match
        val byId = foods.find { it.id == id && it.id != 0L && it.id != -1L }
        if (byId != null) return byId

        val cleanName = name.trim()
        val cleanBrand = brand?.trim()?.takeIf { it.isNotEmpty() }

        // 2. Name + Brand match (most specific)
        if (cleanBrand != null) {
            val byNameAndBrand = foods.find {
                it.name.equals(cleanName, ignoreCase = true) &&
                it.brand?.equals(cleanBrand, ignoreCase = true) == true
            }
            if (byNameAndBrand != null) return byNameAndBrand
        }

        // 3. Name match where brand is null or empty in BOTH
        val byNameOnlyNoBrand = foods.find {
            it.name.equals(cleanName, ignoreCase = true) &&
            (it.brand == null || it.brand.isBlank()) &&
            (cleanBrand == null)
        }
        if (byNameOnlyNoBrand != null) return byNameOnlyNoBrand

        // 4. Fallback: Just the name
        return foods.find { it.name.equals(cleanName, ignoreCase = true) }
    }

    private fun getGeneralNameForIngredient(foodItemId: Long, fallbackName: String, brand: String? = null): String {
        val food = findFoodByIdOrName(foodItemId, fallbackName, brand)
        val parent = food?.parentId?.let { pId -> foods.find { it.id == pId } }
        return parent?.name ?: food?.name ?: fallbackName
    }

    fun addPlannedEntry(food: FoodItemEntity, amount: Double, portion: FoodPortionEntity?, mealSlot: String, date: LocalDate, autoAddToShoppingList: Boolean = true, pkg: FoodPackageEntity? = null) {
        val householdId = firebaseManager.household.value?.id ?: return
        val grams = when {
            portion != null -> amount * portion.grams
            pkg != null -> amount * pkg.quantity
            else -> amount
        }
        val entry = FoodEntryEntity(id = System.currentTimeMillis(), dateIso = date.toString(), mealSlot = mealSlot, amount = amount, unitLabel = portion?.name ?: pkg?.name ?: food.baseUnit, grams = grams, foodItemId = food.id, name = food.name, brand = food.brand, kcalPer100g = food.kcalPer100g, proteinPer100g = food.proteinPer100g, carbsPer100g = food.carbsPer100g, sugarPer100g = food.sugarPer100g, fatPer100g = food.fatPer100g, saturatedFatPer100g = food.saturatedFatPer100g, alcoholPercent = food.alcoholPercent, baseUnit = food.baseUnit, store = food.store, isPlanned = true)
        viewModelScope.launch {
            try {
                firestoreRepository.addPlannedEntry(householdId, entry)
                if (autoAddToShoppingList) {
                    val item = ShoppingItem(name = getGeneralName(food), amount = amount, unit = pkg?.name ?: portion?.name ?: food.baseUnit, isAutoGenerated = true, category = food.category, householdId = householdId, sourceName = "Einzelne Zutat", weightGrams = grams, baseUnit = food.baseUnit, isPantryItem = food.isPantryItem || (food.parentId?.let { pId -> foods.find { it.id == pId }?.isPantryItem } ?: false))
                    firestoreRepository.addShoppingItem(householdId, item)
                }
            } catch (e: Exception) { Log.e("Firestore", "Add planned error", e) }
        }
    }

    fun addPlannedMeal(meal: MealEntity, mealSlot: String, date: LocalDate, servings: Double = 1.0, autoAddToShoppingList: Boolean = true) {
        val householdId = firebaseManager.household.value?.id ?: return

        val fixedIngredients = meal.ingredients.map { ing ->
            val food = foods.find { it.id == ing.foodItemId }
            if (food == null || !food.name.equals(ing.name, ignoreCase = true)) {
                val correct = findFoodByIdOrName(0, ing.name, ing.brand)
                if (correct != null && correct.id != ing.foodItemId) {
                    Log.d("DeepRepair", "addPlannedMeal '${meal.name}': Fixed '${ing.name}' (${ing.foodItemId} -> ${correct.id})")
                    ing.copy(foodItemId = correct.id)
                } else ing
            } else ing
        }.map { it.copy(id = System.currentTimeMillis() + (Math.random() * 1000).toLong(), grams = it.grams * (servings / meal.servings), amount = it.amount * (servings / meal.servings)) }

        val entry = FoodEntryEntity(id = System.currentTimeMillis(), dateIso = date.toString(), mealSlot = mealSlot, amount = servings, unitLabel = if (servings == 1.0) "Portion" else "Portionen", grams = (meal.ingredients.sumOf { it.grams } * (servings / meal.servings)).coerceAtLeast(0.0), foodItemId = -1, name = meal.name, kcalPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0, sugarPer100g = 0.0, fatPer100g = 0.0, saturatedFatPer100g = 0.0, isMeal = true, mealIngredients = fixedIngredients, isPlanned = true, imageUrl = meal.imageUrl, tags = meal.tags)
        viewModelScope.launch {
            try {
                firestoreRepository.addPlannedEntry(householdId, entry)
                if (autoAddToShoppingList) {
                    meal.ingredients.forEach { ing ->
                        val foodItem = findFoodByIdOrName(ing.foodItemId, ing.name, ing.brand)
                        val isPantry = foodItem?.isPantryItem == true || (foodItem?.parentId?.let { pId -> foods.find { it.id == pId }?.isPantryItem } ?: false)
                        val itemWeight = (ing.grams / meal.servings) * servings
                        val item = ShoppingItem(name = getGeneralNameForIngredient(ing.foodItemId, ing.name, ing.brand), amount = (ing.amount / meal.servings) * servings, unit = ing.unitLabel, isAutoGenerated = true, category = foodItem?.category ?: (foodItem?.parentId?.let { pId -> foods.find { it.id == pId }?.category }), householdId = householdId, sourceName = meal.name, weightGrams = itemWeight, baseUnit = ing.baseUnit, isPantryItem = isPantry)
                        firestoreRepository.addShoppingItem(householdId, item)
                    }
                }
            } catch (e: Exception) { Log.e("Firestore", "Add meal error", e) }
        }
    }

    fun deletePlannedEntry(entryId: Long) {
        val householdId = firebaseManager.household.value?.id ?: return
        
        // Local First
        plannedEntries.removeAll { it.id == entryId }
        pendingDeletions[entryId.toString()] = System.currentTimeMillis()
        
        viewModelScope.launch { firestoreRepository.deletePlannedEntry(householdId, entryId) }
    }

    fun updatePlannedEntry(updated: FoodEntryEntity) {
        val householdId = firebaseManager.household.value?.id ?: return
        val entryToSave = if (updated.foodItemId == -1L && !updated.isMeal) { val saved = saveEntryAsFood(updated); updated.copy(foodItemId = saved.id) }
        else if (updated.isMeal) { val finalIngredients = updated.mealIngredients?.map { ing -> if (foods.none { it.id == ing.foodItemId }) { val saved = saveIngredientAsFood(ing); ing.copy(foodItemId = saved.id) } else ing }; updated.copy(mealIngredients = finalIngredients) }
        else updated
        viewModelScope.launch { try { firestoreRepository.addPlannedEntry(householdId, entryToSave) } catch (e: Exception) { Log.e("Firestore", "Update planner error", e) } }
    }

    fun updateShoppingItem(item: ShoppingItem) {
        val householdId = firebaseManager.household.value?.id ?: return
        viewModelScope.launch {
            try { firestoreRepository.updateShoppingItem(householdId, item) }
            catch (e: Exception) { Log.e("Firestore", "Update shopping error", e) }
        }
    }

    suspend fun suggestCategory(name: String, isPremium: Boolean = false): String {
        // 1. Local catalog check
        val match = foods.find { it.name.equals(name, ignoreCase = true) }
        if (match?.category != null) return match.category!!
        
        // 2. AI check if available
        if (isPremium && geminiApiKey.isNotBlank()) {
            val service = GeminiService()
            return service.categorizeItem(name, categories.toList())
        }
        
        return "Sonstiges"
    }

    fun addShoppingItem(name: String, amount: Double, unit: String, category: String? = null, isPremium: Boolean = false) {
        val householdId = firebaseManager.household.value?.id ?: return
        val lowerUnit = unit.lowercase().trim()
        val isWeight = lowerUnit == "g" || lowerUnit == "ml"
        viewModelScope.launch {
            val finalCategory = category ?: suggestCategory(name, isPremium)
            firestoreRepository.addShoppingItem(
                householdId, 
                ShoppingItem(
                    name = name, 
                    amount = amount, 
                    unit = unit, 
                    householdId = householdId, 
                    weightGrams = if (isWeight) amount else 0.0, 
                    baseUnit = if (lowerUnit == "ml") "ml" else "g",
                    category = finalCategory
                )
            ) 
        }
    }

    fun toggleShoppingItem(item: ShoppingItem) {
        val householdId = firebaseManager.household.value?.id ?: return
        viewModelScope.launch {
            try {
                if (isShoppingListAggregated) {
                    val isWeightItem = item.weightGrams > 0
                    val relatedItems = shoppingList.filter { 
                        val nameMatch = it.name.equals(item.name, ignoreCase = true)
                        val match = if (isWeightItem) nameMatch && it.weightGrams > 0 && it.baseUnit.equals(item.baseUnit, ignoreCase = true)
                        else nameMatch && it.unit.equals(item.unit, ignoreCase = true)
                        match && it.isChecked != !item.isChecked 
                    }
                    relatedItems.forEach { firestoreRepository.updateShoppingItem(householdId, it.copy(isChecked = !item.isChecked)) }
                } else firestoreRepository.updateShoppingItem(householdId, item.copy(isChecked = !item.isChecked))
            } catch (e: Exception) { Log.e("Firestore", "Toggle error", e) }
        }
    }

    fun deleteShoppingItem(itemId: String) {
        val householdId = firebaseManager.household.value?.id ?: return
        
        // Local First
        shoppingList.removeAll { it.id == itemId }
        pendingDeletions[itemId] = System.currentTimeMillis()
        
        viewModelScope.launch { try { firestoreRepository.deleteShoppingItem(householdId, itemId) } catch (e: Exception) { Log.e("Firestore", "Delete error", e) } }
    }

    private fun createDefaultCategories() {
        val defaults = listOf("Obst", "Gemüse", "Backwaren", "Kühlregal", "Fleisch", "Milchprodukte", "Protein", "Teigwaren", "Convenience", "Fertiggerichte", "Tiefkühlprodukte", "Süßigkeiten", "Getränke")
        categories.addAll(defaults); saveCategories()
    }

    private fun createDefaultFoods() {
        addFood(name = "Ei", kcal = 155.0, protein = 13.0, carbs = 1.1, sugar = 1.1, fat = 11.0, saturatedFat = 3.3, alcoholPercent = 0.0, baseUnit = "g", portions = listOf(FoodPortionEntity(0, "S", 43.0), FoodPortionEntity(0, "M", 53.0), FoodPortionEntity(0, "L", 63.0)), packages = emptyList(), category = "Protein", isGeneric = true)
    }

    fun findFoodByBarcode(barcode: String): FoodItemEntity? = foods.find { it.barcode?.equals(barcode, ignoreCase = true) == true }

    fun addFood(name: String, kcal: Double, protein: Double, carbs: Double, sugar: Double, fat: Double, saturatedFat: Double, alcoholPercent: Double, baseUnit: String, portions: List<FoodPortionEntity>, packages: List<FoodPackageEntity>, barcode: String? = null, brand: String? = null, category: String? = null, isGeneric: Boolean = false, parentId: Long? = null, store: String? = null, isPantryItem: Boolean = false): FoodItemEntity {
        Log.d("AddFood", "Adding food: $name (Generic: $isGeneric)")
        val user = firebaseManager.currentUser.value
        val newId = if (user != null) System.currentTimeMillis() else nextFoodId++
        
        val updatedPortions = portions.map { if (it.id == 0L) it.copy(id = nextPortionId++) else it }
        val updatedPackages = packages.mapIndexed { idx, pkg -> if (pkg.id == 0L) pkg.copy(id = newId * 100 + idx) else pkg }
        val newFood = FoodItemEntity(id = newId, name = name.trim(), brand = brand?.trim()?.takeIf { it.isNotBlank() }, kcalPer100g = kcal, proteinPer100g = protein, carbsPer100g = carbs, sugarPer100g = sugar, fatPer100g = fat, saturatedFatPer100g = saturatedFat, alcoholPercent = alcoholPercent, baseUnit = baseUnit, portions = updatedPortions, packages = updatedPackages, barcode = barcode?.trim()?.takeIf { it.isNotBlank() }, category = category?.trim()?.takeIf { it.isNotBlank() }, isGeneric = isGeneric, parentId = parentId, store = store?.trim()?.takeIf { it.isNotBlank() }, isPantryItem = isPantryItem, lastModified = System.currentTimeMillis())
        
        foods.add(newFood)
        saveFoods()
        
        if (user != null) {
            viewModelScope.launch { firestoreRepository.savePersonalFood(user.uid, newFood) }
        }
        return newFood
    }

    fun updateFood(updatedFood: FoodItemEntity) {
        val user = firebaseManager.currentUser.value
        val fixedPortions = updatedFood.portions.map { if (it.id == 0L) it.copy(id = nextPortionId++) else it }
        val fixedPackages = updatedFood.packages.mapIndexed { idx, pkg -> if (pkg.id == 0L) pkg.copy(id = updatedFood.id * 100 + idx) else pkg }
        val finalFood = updatedFood.copy(portions = fixedPortions, packages = fixedPackages, lastModified = System.currentTimeMillis())

        val index = foods.indexOfFirst { it.id == finalFood.id }
        if (index != -1) {
            foods[index] = finalFood
            saveFoods()
            for (i in allEntries.indices) { if (allEntries[i].foodItemId == finalFood.id) allEntries[i] = allEntries[i].copy(name = finalFood.name, brand = finalFood.brand, kcalPer100g = finalFood.kcalPer100g, proteinPer100g = finalFood.proteinPer100g, carbsPer100g = finalFood.carbsPer100g, sugarPer100g = finalFood.sugarPer100g, fatPer100g = finalFood.fatPer100g, saturatedFatPer100g = finalFood.saturatedFatPer100g, alcoholPercent = finalFood.alcoholPercent, baseUnit = finalFood.baseUnit, store = finalFood.store) }
            saveEntries()
            for (mIdx in meals.indices) {
                var changed = false
                val updatedIngredients = meals[mIdx].ingredients.map { ing -> if (ing.foodItemId == finalFood.id) { changed = true; ing.copy(name = finalFood.name, kcalPer100g = finalFood.kcalPer100g, proteinPer100g = finalFood.proteinPer100g, carbsPer100g = finalFood.carbsPer100g, sugarPer100g = finalFood.sugarPer100g, fatPer100g = finalFood.fatPer100g, saturatedFatPer100g = finalFood.saturatedFatPer100g, alcoholPercent = finalFood.alcoholPercent, baseUnit = finalFood.baseUnit, store = finalFood.store, brand = finalFood.brand) } else ing }
                if (changed) meals[mIdx] = meals[mIdx].copy(ingredients = updatedIngredients)
            }
            saveMeals()
        }

        if (user != null) {
            viewModelScope.launch {
                firestoreRepository.savePersonalFood(user.uid, finalFood)
                
                // Still update linked cloud items (Planner & Shopping List)
                val householdId = firebaseManager.household.value?.id
                if (householdId != null) {
                    shoppingList.filter { !it.isChecked && it.name.equals(finalFood.name, ignoreCase = true) }.forEach { 
                        firestoreRepository.updateShoppingItem(householdId, it.copy(category = finalFood.category, isPantryItem = finalFood.isPantryItem)) 
                    }
                    plannedEntries.filter { it.foodItemId == finalFood.id || (it.isMeal && it.mealIngredients?.any { ing -> ing.foodItemId == finalFood.id } == true) }.forEach { entry ->
                        if (entry.isMeal) {
                            val updatedIngredients = entry.mealIngredients?.map { ing -> if (ing.foodItemId == finalFood.id) ing.copy(name = finalFood.name, kcalPer100g = finalFood.kcalPer100g, proteinPer100g = finalFood.proteinPer100g, carbsPer100g = finalFood.carbsPer100g, sugarPer100g = finalFood.sugarPer100g, fatPer100g = finalFood.fatPer100g, saturatedFatPer100g = finalFood.saturatedFatPer100g, alcoholPercent = finalFood.alcoholPercent, baseUnit = finalFood.baseUnit, store = finalFood.store, brand = finalFood.brand) else ing }
                            firestoreRepository.addPlannedEntry(householdId, entry.copy(mealIngredients = updatedIngredients))
                        } else firestoreRepository.addPlannedEntry(householdId, entry.copy(name = finalFood.name, brand = finalFood.brand, kcalPer100g = finalFood.kcalPer100g, proteinPer100g = finalFood.proteinPer100g, carbsPer100g = finalFood.carbsPer100g, sugarPer100g = finalFood.sugarPer100g, fatPer100g = finalFood.fatPer100g, saturatedFatPer100g = finalFood.saturatedFatPer100g, alcoholPercent = finalFood.alcoholPercent, baseUnit = finalFood.baseUnit, store = finalFood.store))
                    }
                }
            }
        }
    }

    fun deleteFood(id: Long) {
        // Local First: Remove immediately from local lists
        foods.removeAll { it.id == id }
        foods.forEachIndexed { i, f -> if (f.parentId == id) foods[i] = f.copy(parentId = null) }
        
        // Update meals containing this food
        for (i in meals.indices) {
            if (meals[i].ingredients.any { it.foodItemId == id }) {
                meals[i] = meals[i].copy(ingredients = meals[i].ingredients.map { 
                    if (it.foodItemId == id) it.copy(foodItemId = -1L) else it 
                })
            }
        }
        
        allEntries.forEachIndexed { i, e -> if (e.foodItemId == id) allEntries[i] = e.copy(foodItemId = -1L) }
        saveFoods()
        saveMeals()
        saveEntries()

        val user = firebaseManager.currentUser.value
        if (user != null) {
            viewModelScope.launch { firestoreRepository.deletePersonalFood(user.uid, id) }
        }
    }
    fun mergeFoods(targetParentId: Long, childIds: List<Long>) {
        val user = firebaseManager.currentUser.value
        childIds.forEach { id -> 
            val idx = foods.indexOfFirst { it.id == id }
            if (idx != -1) {
                val updated = foods[idx].copy(parentId = targetParentId, isGeneric = false, lastModified = System.currentTimeMillis())
                foods[idx] = updated
                if (user != null) {
                    viewModelScope.launch { firestoreRepository.savePersonalFood(user.uid, updated) }
                }
            }
        }
        saveFoods()
    }

    fun promoteToGeneric(foodId: Long) {
        val index = foods.indexOfFirst { it.id == foodId }
        if (index != -1) {
            val updated = foods[index].copy(isGeneric = true, parentId = null, lastModified = System.currentTimeMillis())
            foods[index] = updated
            saveFoods()
            
            val user = firebaseManager.currentUser.value
            if (user != null) {
                viewModelScope.launch { firestoreRepository.savePersonalFood(user.uid, updated) }
            }
        }
    }
    fun addCategory(name: String) { if (name.isNotBlank() && !categories.contains(name.trim())) { categories.add(name.trim()); saveCategories() } }
    fun deleteCategory(name: String) { categories.remove(name); saveCategories() }
    fun updateCategory(oldName: String, newName: String) {
        val idx = categories.indexOf(oldName); if (idx != -1 && newName.isNotBlank() && !categories.contains(newName.trim())) {
            categories[idx] = newName.trim(); saveCategories()
            foods.forEachIndexed { i, f -> if (f.category == oldName) foods[i] = f.copy(category = newName.trim()) }; saveFoods()
        }
    }

    fun saveIngredientAsFood(ingredient: MealIngredientEntity): FoodItemEntity = addFood(ingredient.name, ingredient.kcalPer100g, ingredient.proteinPer100g, ingredient.carbsPer100g, ingredient.sugarPer100g, ingredient.fatPer100g, ingredient.saturatedFatPer100g, ingredient.alcoholPercent, ingredient.baseUnit, emptyList(), emptyList(), brand = ingredient.brand, store = ingredient.store, isGeneric = false)
    fun saveEntryAsFood(entry: FoodEntryEntity): FoodItemEntity = addFood(entry.name, entry.kcalPer100g, entry.proteinPer100g, entry.carbsPer100g, entry.sugarPer100g, entry.fatPer100g, entry.saturatedFatPer100g, entry.alcoholPercent, entry.baseUnit, emptyList(), emptyList(), brand = entry.brand, store = entry.store, isGeneric = false)
    private fun saveCategories() { try { prefs.edit().putString("categories_json", json.encodeToString(categories.toList())).apply() } catch (e: Exception) {} }
    private fun loadCategories() { val data = prefs.getString("categories_json", null); if (data != null) try { val loaded = json.decodeFromString<List<String>>(data); categories.clear(); categories.addAll(loaded) } catch (e: Exception) {} }
    fun addMealTemplate(name: String, ingredients: List<MealIngredientEntity>, servings: Double = 1.0, tags: List<String> = emptyList(), imageUrl: String? = null) {
        val user = firebaseManager.currentUser.value
        val finalIngredients = ingredients.mapIndexed { idx, ing -> 
            if (foods.none { it.id == ing.foodItemId }) { 
                val saved = saveIngredientAsFood(ing)
                ing.copy(id = System.currentTimeMillis() + idx, foodItemId = saved.id) 
            } else ing.copy(id = System.currentTimeMillis() + idx) 
        }
        
        val mealToSave = MealEntity(
            id = nextMealId++, 
            name = name, 
            ingredients = finalIngredients, 
            servings = servings, 
            tags = tags, 
            imageUrl = imageUrl, 
            lastModified = System.currentTimeMillis()
        )
        
        meals.add(mealToSave)
        saveMeals()

        if (user != null) {
            viewModelScope.launch {
                var remoteUrl = imageUrl
                if (imageUrl != null && (imageUrl.startsWith("/") || imageUrl.startsWith("file:"))) {
                    // It's a local image, upload it
                    remoteUrl = firestoreRepository.uploadMealImage(user.uid, imageUrl.replace("file:", ""))
                }
                
                val finalMeal = if (remoteUrl != imageUrl) mealToSave.copy(imageUrl = remoteUrl) else mealToSave
                if (remoteUrl != imageUrl) {
                    // Update local list if we got a remote URL
                    val idx = meals.indexOfFirst { it.id == mealToSave.id }
                    if (idx != -1) meals[idx] = finalMeal
                    saveMeals()
                }
                firestoreRepository.savePersonalMeal(user.uid, finalMeal)
            }
        }
    }

    fun updateMealTemplate(updatedMeal: MealEntity) {
        val user = firebaseManager.currentUser.value
        val finalIngredients = updatedMeal.ingredients.map { ing -> 
            if (foods.none { it.id == ing.foodItemId }) { 
                val saved = saveIngredientAsFood(ing)
                ing.copy(foodItemId = saved.id) 
            } else ing 
        }
        val mealWithIngredients = updatedMeal.copy(ingredients = finalIngredients, lastModified = System.currentTimeMillis())
        
        val idx = meals.indexOfFirst { it.id == mealWithIngredients.id }
        if (idx != -1) {
            meals[idx] = mealWithIngredients
            saveMeals()
        }

        if (user != null) {
            viewModelScope.launch {
                var remoteUrl = mealWithIngredients.imageUrl
                if (remoteUrl != null && (remoteUrl.startsWith("/") || remoteUrl.startsWith("file:"))) {
                    remoteUrl = firestoreRepository.uploadMealImage(user.uid, remoteUrl.replace("file:", ""))
                }
                
                val finalMeal = if (remoteUrl != mealWithIngredients.imageUrl) mealWithIngredients.copy(imageUrl = remoteUrl) else mealWithIngredients
                if (remoteUrl != mealWithIngredients.imageUrl) {
                    val mIdx = meals.indexOfFirst { it.id == finalMeal.id }
                    if (mIdx != -1) meals[mIdx] = finalMeal
                    saveMeals()
                }
                firestoreRepository.savePersonalMeal(user.uid, finalMeal)
            }
        }
    }
    fun deleteMealTemplate(id: Long) {
        // Local First: Remove immediately
        meals.removeAll { it.id == id }
        saveMeals()
        
        val user = firebaseManager.currentUser.value
        if (user != null) {
            viewModelScope.launch { firestoreRepository.deletePersonalMeal(user.uid, id) }
        }
    }
    fun addEntry(food: FoodItemEntity, amount: Double, portion: FoodPortionEntity?, mealSlot: String, pkg: FoodPackageEntity? = null) {
        val grams = when {
            portion != null -> amount * portion.grams
            pkg != null -> amount * pkg.quantity
            else -> amount
        }
        if (grams > 0.0) {
            val user = firebaseManager.currentUser.value
            val entry = FoodEntryEntity(
                id = if (user != null) System.currentTimeMillis() else nextEntryId++,
                dateIso = selectedDate.toString(),
                mealSlot = mealSlot,
                amount = amount,
                unitLabel = portion?.name ?: pkg?.name ?: food.baseUnit,
                grams = grams,
                foodItemId = food.id,
                name = food.name,
                brand = food.brand,
                kcalPer100g = food.kcalPer100g,
                proteinPer100g = food.proteinPer100g,
                carbsPer100g = food.carbsPer100g,
                sugarPer100g = food.sugarPer100g,
                fatPer100g = food.fatPer100g,
                saturatedFatPer100g = food.saturatedFatPer100g,
                alcoholPercent = food.alcoholPercent,
                baseUnit = food.baseUnit,
                store = food.store
            )
            allEntries.add(entry)
            saveEntries()
            if (user != null) {
                viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, entry) }
            }
        }
    }
    fun addMealEntry(meal: MealEntity, mealSlot: String, servings: Double = 1.0) { 
        val user = firebaseManager.currentUser.value
        val entry = FoodEntryEntity(id = if (user != null) System.currentTimeMillis() else nextEntryId++, dateIso = selectedDate.toString(), mealSlot = mealSlot, amount = servings, unitLabel = if (servings == 1.0) "Portion" else "Portionen", grams = (meal.ingredients.sumOf { it.grams } * (servings / meal.servings)).coerceAtLeast(0.0), foodItemId = -1, name = meal.name, kcalPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0, sugarPer100g = 0.0, fatPer100g = 0.0, saturatedFatPer100g = 0.0, isMeal = true, mealIngredients = meal.ingredients.mapIndexed { idx, it -> it.copy(id = System.currentTimeMillis() + idx, grams = it.grams * (servings / meal.servings), amount = it.amount * (servings / meal.servings)) }, imageUrl = meal.imageUrl, tags = meal.tags)
        allEntries.add(entry)
        saveEntries() 
        if (user != null) {
            viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, entry) }
        }
    }
    fun updateEntry(updatedEntry: FoodEntryEntity) { val entryToSave = if (updatedEntry.foodItemId == -1L && !updatedEntry.isMeal) { val saved = saveEntryAsFood(updatedEntry); updatedEntry.copy(foodItemId = saved.id) } else if (updatedEntry.isMeal) { val finalIngredients = updatedEntry.mealIngredients?.map { ing -> if (foods.none { it.id == ing.foodItemId }) { val saved = saveIngredientAsFood(ing); ing.copy(foodItemId = saved.id) } else ing }; updatedEntry.copy(mealIngredients = finalIngredients) } else updatedEntry; val idx = allEntries.indexOfFirst { it.id == entryToSave.id }; if (idx != -1) { allEntries[idx] = entryToSave; saveEntries() }; val user = firebaseManager.currentUser.value; if (user != null) { viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, entryToSave) } } }
    fun deleteEntry(id: Long) { allEntries.removeAll { it.id == id }; saveEntries(); val user = firebaseManager.currentUser.value; if (user != null) { viewModelScope.launch { firestoreRepository.deletePersonalEntry(user.uid, id) } } }
    fun copyEntriesToDate(entryIds: Set<Long>, targetDate: LocalDate) { 
        val user = firebaseManager.currentUser.value
        val newEntries = allEntries.filter { it.id in entryIds }.map { it.copy(id = if (user != null) System.currentTimeMillis() + (Math.random()*1000).toLong() else nextEntryId++, dateIso = targetDate.toString()) }
        allEntries.addAll(newEntries)
        saveEntries() 
        if (user != null) {
            viewModelScope.launch { newEntries.forEach { firestoreRepository.savePersonalEntry(user.uid, it) } }
        }
    }
    fun moveEntriesToDate(entryIds: Set<Long>, targetDate: LocalDate) { copyEntriesToDate(entryIds, targetDate); entryIds.forEach { deleteEntry(it) } }
    
    fun updateActivity(steps: Int?, totalKcal: Double? = null, sessions: List<ExerciseSessionInfo>? = null) {
        val dateKey = selectedDate.toString()
        if (steps != null) dailySteps[dateKey] = steps
        if (totalKcal != null) dailyTotalCalories[dateKey] = totalKcal
        if (sessions != null) dailyExerciseSessions[dateKey] = sessions
        saveActivity()
    }
    fun addWeightEntry(weight: Double, dateIso: String, profile: UserProfile) {
        val entry = WeightEntry(dateIso, weight)
        val existingIdx = weightHistory.indexOfFirst { it.dateIso == dateIso }
        if (existingIdx != -1) weightHistory[existingIdx] = entry else weightHistory.add(entry)
        saveWeightHistory()
        calculateMetabolicFactorForProfile(profile)
        
        firebaseManager.currentUser.value?.let { user ->
            viewModelScope.launch {
                firestoreRepository.saveWeightEntry(user.uid, entry)
            }
        }
    }
    fun deleteWeightEntry(dateIso: String) {
        weightHistory.removeAll { it.dateIso == dateIso }
        saveWeightHistory()
        firebaseManager.currentUser.value?.let { user ->
            viewModelScope.launch {
                firestoreRepository.deleteWeightEntry(user.uid, dateIso)
            }
        }
    }
    fun calculateMetabolicFactorForProfile(profile: UserProfile): Double { if (weightHistory.size < 3) return profile.metabolicFactor; val sorted = weightHistory.sortedBy { it.dateIso }; val actualLossKg = sorted.first().weight - sorted.last().weight; if (actualLossKg <= 0) return profile.metabolicFactor; var predictedLossG = 0.0; var curr = LocalDate.parse(sorted.first().dateIso); val end = LocalDate.parse(sorted.last().dateIso); while (!curr.isAfter(end)) { if (dayVerifications[curr.toString()] == true) predictedLossG += calculateWeightBudgetGrams(curr.toString(), profile); curr = curr.plusDays(1) }; val predictedLossKg = predictedLossG / 1000.0; return if (predictedLossKg <= 0.1) profile.metabolicFactor else actualLossKg / predictedLossKg }
    fun verifyDay(dateIso: String, isComplete: Boolean) { dayVerifications[dateIso] = isComplete; saveVerifications() }
    private fun saveWeightHistory() { try { prefs.edit().putString("weight_history_json", json.encodeToString(weightHistory.toList())).apply() } catch (e: Exception) {} }
    private fun loadWeightHistory() { val data = prefs.getString("weight_history_json", null); if (data != null) try { weightHistory.addAll(json.decodeFromString<List<WeightEntry>>(data)) } catch (e: Exception) {} }
    private fun saveVerifications() { try { prefs.edit().putString("verifications_json", json.encodeToString(dayVerifications.toMap())).apply() } catch (e: Exception) {} }
    private fun loadVerifications() { val data = prefs.getString("verifications_json", null); if (data != null) try { dayVerifications.putAll(json.decodeFromString<Map<String, Boolean>>(data)) } catch (e: Exception) {} }
    fun saveFoods() { try { prefs.edit().putString("foods_json", json.encodeToString(foods.toList())).apply() } catch (e: Exception) {} }
    private fun loadFoods() { val data = prefs.getString("foods_json", null); if (data != null) try { val loaded = json.decodeFromString<List<FoodItemEntity>>(data); foods.clear(); foods.addAll(loaded); nextFoodId = (foods.maxOfOrNull { it.id } ?: 0L) + 1; nextPortionId = (foods.flatMap { it.portions }.maxOfOrNull { it.id } ?: 0L) + 1 } catch (e: Exception) {} }
    fun saveMeals() { try { prefs.edit().putString("meals_json", json.encodeToString(meals.toList())).apply() } catch (e: Exception) {} }
    private fun loadMeals() { val data = prefs.getString("meals_json", null); if (data != null) try { val loaded = json.decodeFromString<List<MealEntity>>(data); meals.clear(); meals.addAll(loaded); nextMealId = (meals.maxOfOrNull { it.id } ?: 0L) + 1 } catch (e: Exception) {} }
    fun saveEntries() { try { prefs.edit().putString("entries_json", json.encodeToString(allEntries.toList())).apply() } catch (e: Exception) {} }

    fun saveImageLocally(uri: android.net.Uri): String? {
        val context = getApplication<Application>().applicationContext
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = "meal_img_${System.currentTimeMillis()}.jpg"
            val file = java.io.File(context.filesDir, fileName)
            
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("NutritionViewModel", "Error saving image locally", e)
            null
        }
    }

    fun loadEntries() { val data = prefs.getString("entries_json", null); if (data != null) try { val loaded = json.decodeFromString<List<FoodEntryEntity>>(data); allEntries.clear(); allEntries.addAll(loaded); nextEntryId = (allEntries.maxOfOrNull { it.id } ?: 0L) + 1 } catch (e: Exception) {} }
    private fun saveActivity() {
        try {
            prefs.edit().putString("steps_json", json.encodeToString(dailySteps.toMap())).apply()
            prefs.edit().putString("total_calories_json", json.encodeToString(dailyTotalCalories.toMap())).apply()
            prefs.edit().putString("exercise_sessions_json", json.encodeToString(dailyExerciseSessions.toMap())).apply()
        } catch (e: Exception) {
            Log.e("SaveActivity", "Error saving activity", e)
        }
    }

    private fun loadActivity() {
        val stepsData = prefs.getString("steps_json", null)
        if (stepsData != null) try {
            val loaded = json.decodeFromString<Map<String, Int>>(stepsData)
            dailySteps.putAll(loaded)
        } catch (e: Exception) {}
        
        val totalCaloriesData = prefs.getString("total_calories_json", null)
        if (totalCaloriesData != null) try {
            val loaded = json.decodeFromString<Map<String, Double>>(totalCaloriesData)
            dailyTotalCalories.putAll(loaded)
        } catch (e: Exception) {}

        val sessionsData = prefs.getString("exercise_sessions_json", null)
        if (sessionsData != null) try {
            val loaded = json.decodeFromString<Map<String, List<ExerciseSessionInfo>>>(sessionsData)
            dailyExerciseSessions.putAll(loaded)
        } catch (e: Exception) {}
    }
    fun getBackupJson(): String = json.encodeToString(BackupData(foods.toList(), meals.toList(), categories.toList(), allEntries.toList()))
    fun getCatalogJson(): String = json.encodeToString(BackupData(foods.toList(), meals.toList(), categories.toList(), emptyList()))
    fun getRecipeJson(meal: MealEntity): String = json.encodeToString(RecipeData(meal, meal.ingredients.mapNotNull { ing -> foods.find { it.id == ing.foodItemId } }))
    fun importBackup(s: String): Boolean = try {
        val b = json.decodeFromString<BackupData>(s)
        val uid = firebaseManager.currentUser.value?.uid
        val idMapping = mutableMapOf<Long, Long>()
        
        b.categories.forEach { if (it.isNotBlank() && !categories.contains(it)) categories.add(it) }
        
        // 1. Process Foods with ID remapping for cloud safety
        val processedFoods = b.foods.map { imp ->
            if (imp.id < 1_000_000L) {
                val newId = System.currentTimeMillis() + (Math.random() * 1000).toLong()
                idMapping[imp.id] = newId
                imp.copy(id = newId, lastModified = System.currentTimeMillis())
            } else imp
        }.map { food ->
            if (food.parentId != null && idMapping.containsKey(food.parentId)) {
                food.copy(parentId = idMapping[food.parentId])
            } else food
        }

        processedFoods.forEach { food ->
            if (foods.none { it.id == food.id }) {
                foods.add(food)
                uid?.let { viewModelScope.launch { firestoreRepository.savePersonalFood(it, food) } }
            }
        }

        // 2. Process Meals with updated food references
        b.meals.forEach { m ->
            var finalMeal = m.copy(ingredients = m.ingredients.map { it.copy(foodItemId = idMapping[it.foodItemId] ?: it.foodItemId) })
            if (finalMeal.id < 1_000_000L) {
                val newId = System.currentTimeMillis() + (Math.random() * 1000).toLong()
                finalMeal = finalMeal.copy(id = newId, lastModified = System.currentTimeMillis())
            }
            if (meals.none { it.id == finalMeal.id }) {
                meals.add(finalMeal)
                uid?.let { viewModelScope.launch { firestoreRepository.savePersonalMeal(it, finalMeal) } }
            }
        }

        // 3. Process Entries with updated food references
        b.entries.forEach { e ->
            val finalEntry = e.copy(
                foodItemId = if (e.foodItemId != -1L) idMapping[e.foodItemId] ?: e.foodItemId else -1L,
                mealIngredients = e.mealIngredients?.map { it.copy(foodItemId = idMapping[it.foodItemId] ?: it.foodItemId) }
            )
            if (allEntries.none { it.dateIso == finalEntry.dateIso && it.name == finalEntry.name && it.mealSlot == finalEntry.mealSlot }) {
                allEntries.add(finalEntry)
                uid?.let { viewModelScope.launch { firestoreRepository.savePersonalEntry(it, finalEntry) } }
            }
        }
        
        if (uid == null) {
            recalculateIds()
        } else {
            updateNextFoodIds()
            updateNextMealIds()
        }
        saveFoods(); saveMeals(); saveEntries()
        true 
    } catch (e: Exception) { 
        Log.e("Import", "Backup import failed", e)
        false 
    }
    fun startRecipeImport(s: String): Boolean = try { pendingRecipeImport = json.decodeFromString<RecipeData>(s); true } catch (e: Exception) { false }
    fun resolveRecipeImport(supplement: Boolean) {
        val r = pendingRecipeImport ?: return
        val uid = firebaseManager.currentUser.value?.uid
        
        r.relatedFoods.forEach { imp -> 
            if (foods.none { it.name == imp.name && it.barcode == imp.barcode }) {
                foods.add(imp)
                uid?.let { viewModelScope.launch { firestoreRepository.savePersonalFood(it, imp) } }
            } else if (supplement && foods.find { it.name == imp.name }?.category == null) {
                val existing = foods.find { it.name == imp.name }!!
                val updated = existing.copy(category = imp.category)
                foods[foods.indexOfFirst { it.name == imp.name }] = updated
                uid?.let { viewModelScope.launch { firestoreRepository.savePersonalFood(it, updated) } }
            }
        }
        
        if (meals.none { it.name == r.meal.name }) {
            meals.add(r.meal)
            uid?.let { viewModelScope.launch { firestoreRepository.savePersonalMeal(it, r.meal) } }
        } else if (supplement) {
            meals[meals.indexOfFirst { it.name == r.meal.name }] = r.meal
            uid?.let { viewModelScope.launch { firestoreRepository.savePersonalMeal(it, r.meal) } }
        }
        
        if (uid == null) recalculateIds()
        else {
            updateNextFoodIds()
            updateNextMealIds()
        }
        pendingRecipeImport = null
    }
    fun deleteInboxMessage(messageId: String) { val uid = firebaseManager.currentUser.value?.uid ?: return; viewModelScope.launch { try { firestoreRepository.deleteInboxMessage(uid, messageId) } catch (e: Exception) { Log.e("Firestore", "Error deleting message", e) } } }
    fun markMessageAsRead(messageId: String) { val uid = firebaseManager.currentUser.value?.uid ?: return; viewModelScope.launch { try { firestoreRepository.markMessageAsRead(uid, messageId) } catch (e: Exception) { Log.e("Firestore", "Error marking as read", e) } } }
    fun sendRecipeToUser(targetUid: String, meal: MealEntity) {
        val currentUser = firebaseManager.currentUser.value ?: return
        viewModelScope.launch {
            try {
                var mealToSend = meal
                // Ensure the image is accessible to the recipient (upload if local)
                if (meal.imageUrl != null && (meal.imageUrl.startsWith("/") || meal.imageUrl.startsWith("file:"))) {
                    Log.d("Sync", "Uploading image before sending recipe...")
                    val remoteUrl = firestoreRepository.uploadMealImage(currentUser.uid, meal.imageUrl.replace("file:", ""))
                    if (remoteUrl != null) {
                        mealToSend = meal.copy(imageUrl = remoteUrl)
                        // Persist the remote URL locally too
                        val idx = meals.indexOfFirst { it.id == meal.id }
                        if (idx != -1) {
                            meals[idx] = mealToSend
                            saveMeals()
                            firestoreRepository.savePersonalMeal(currentUser.uid, mealToSend)
                        }
                    }
                }

                val name = firebaseManager.getUserName(currentUser.uid)
                val jsonPayload = getRecipeJson(mealToSend)
                val message = InboxMessage(
                    fromUid = currentUser.uid, 
                    fromName = name, 
                    type = MessageType.RECIPE, 
                    payloadJson = jsonPayload
                )
                firestoreRepository.sendInboxMessage(targetUid, message)
                Log.d("Sync", "Recipe sent successfully with image: ${mealToSend.imageUrl != null}")
            } catch (e: Exception) { 
                Log.e("Firestore", "Error sending recipe", e) 
            }
        }
    }
    fun sendFoodToUser(targetUid: String, food: FoodItemEntity) { val currentUser = firebaseManager.currentUser.value ?: return; viewModelScope.launch { try { val name = firebaseManager.getUserName(currentUser.uid); val jsonPayload = json.encodeToString(food); val message = InboxMessage(fromUid = currentUser.uid, fromName = name, type = MessageType.FOOD, payloadJson = jsonPayload); firestoreRepository.sendInboxMessage(targetUid, message) } catch (e: Exception) { Log.e("Firestore", "Error sending food", e) } } }
    fun recalculateIds() {
        var fId = 1L; var pId = 1L; var pkgId = 1L; var ingId = 1L
        val foodMap = mutableMapOf<Long, Long>()
        val upFoods = foods.map { f -> val nid = fId++; foodMap[f.id] = nid; f.copy(id = nid, portions = f.portions.map { it.copy(id = pId++) }, packages = f.packages.map { it.copy(id = pkgId++) }) }
        val finalFoods = upFoods.map { f -> if (f.parentId != null) f.copy(parentId = foodMap[f.parentId]) else f }
        val upMeals = meals.map { m -> m.copy(ingredients = m.ingredients.filter { foodMap.containsKey(it.foodItemId) }.map { it.copy(id = ingId++, foodItemId = foodMap[it.foodItemId]!!) }) }
        val upEntries = allEntries.map { e -> e.copy(foodItemId = if (e.foodItemId != -1L) foodMap[e.foodItemId] ?: e.foodItemId else -1L, mealIngredients = e.mealIngredients?.map { it.copy(id = ingId++, foodItemId = foodMap[it.foodItemId] ?: it.foodItemId) }) }
        foods.clear(); foods.addAll(finalFoods); meals.clear(); meals.addAll(upMeals); allEntries.clear(); allEntries.addAll(upEntries)
        nextFoodId = fId; nextPortionId = pId; nextMealId = (meals.maxOfOrNull { it.id } ?: 0L) + 1; nextEntryId = (allEntries.maxOfOrNull { it.id } ?: 0L) + 1; saveFoods(); saveMeals(); saveEntries()
    }
}
