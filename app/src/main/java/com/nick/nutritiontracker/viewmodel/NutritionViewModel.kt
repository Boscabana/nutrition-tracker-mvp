package com.nick.nutritiontracker.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nick.nutritiontracker.data.*
import com.nick.nutritiontracker.NotificationHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class PlanMatchStatus {
    EXACT,            // Alles vorhanden
    DIVERGENT,        // Artikel existiert, aber Werte weichen ab
    TEMPLATE_MISSING, // Alle Artikel da, aber Mahlzeit-Vorlage fehlt
    MISSING,          // Artikel fehlen komplett
    DELETED_INGREDIENT // Zutat wurde aus der Bibliothek gelöscht
}

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

    // --- State Properties ---
    var selectedDate: LocalDate by mutableStateOf(LocalDate.now())
        private set

    val foods = mutableStateListOf<FoodItemEntity>()
    val meals = mutableStateListOf<MealEntity>()
    val allEntries = mutableStateListOf<FoodEntryEntity>()
    val plannedMealPool = mutableStateListOf<PlannedMealPoolEntity>()
    val categories = mutableStateListOf<String>()
    val plannedEntries = mutableStateListOf<FoodEntryEntity>()
    val shoppingList = mutableStateListOf<ShoppingItem>()
    val weightHistory = mutableStateListOf<WeightEntry>()
    val dayVerifications = mutableStateMapOf<String, Boolean>()
    val inboxMessages = mutableStateListOf<InboxMessage>()
    val unreadInboxCount by derivedStateOf { inboxMessages.count { !it.isRead } }
    val householdMembers = mutableStateListOf<Map<String, String>>()

    val dailySteps = mutableStateMapOf<String, Int>()
    val dailyTotalCalories = mutableStateMapOf<String, Double>()
    val dailyExerciseSessions = mutableStateMapOf<String, List<ExerciseSessionInfo>>()

    private var userProfile by mutableStateOf(UserProfile())
    private var isRepairing = false
    private val shoppingMutex = Mutex()

    // --- Mandatory Properties for NutritionApp.kt ---
    val todayEntries: List<FoodEntryEntity>
        get() = allEntries.filter { it.dateIso == selectedDate.toString() }

    val todayTotalKcal: Double
        get() = todayEntries.sumOf { it.kcal }

    val todayTotalProtein: Double
        get() = todayEntries.sumOf { it.protein }

    val todayTotalComplexCarbs: Double
        get() = todayEntries.sumOf { it.complexCarbs }

    val todayTotalSugar: Double
        get() = todayEntries.sumOf { it.sugar }

    val todayTotalUnsaturatedFat: Double
        get() = todayEntries.sumOf { it.unsaturatedFat }

    val todayTotalSaturatedFat: Double
        get() = todayEntries.sumOf { it.saturatedFat }

    val todaySteps: Int
        get() = dailySteps[selectedDate.toString()] ?: 0

    val todayStepKcal: Double
        get() = calculateStepKcal(selectedDate.toString(), userProfile)

    val todayActivityKcal: Double
        get() = getActivityKcal(selectedDate.toString(), userProfile)

    // UI States
    var foodSearchQuery by mutableStateOf("")
    var selectedFoodCategory by mutableStateOf<String?>(null)
    var pendingRecipeImport by mutableStateOf<RecipeData?>(null)
    var isShoppingListAggregated by mutableStateOf(value = false)
    var showPantryInShoppingList by mutableStateOf(value = false)
    var shoppingListSortByCategory by mutableStateOf(prefs.getBoolean("shopping_sort_category", true))

    var forceOnboardingOnStart by mutableStateOf(prefs.getBoolean("force_onboarding", false))
    var geminiApiKey by mutableStateOf(prefs.getString("gemini_api_key", null) ?: "")
    var biometricEnabled by mutableStateOf(prefs.getBoolean("biometric_enabled", false))
    var isAppUnlocked by mutableStateOf(false)
    var shouldTriggerQuickScan by mutableStateOf(false)
    var isQuickScanRunning by mutableStateOf(false)
    var shouldCloseApp by mutableStateOf(false)
    
    // Neuer Trigger für den Scan-Vorgang (Replay = 1 damit der UI-Collector den Start nicht verpasst)
    private val _scanTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 1)
    val scanTrigger = _scanTrigger.asSharedFlow()

    fun triggerScan(isQuickScan: Boolean = false) {
        if (isQuickScan) {
            isQuickScanRunning = true
        }
        viewModelScope.launch { _scanTrigger.emit(Unit) }
    }
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consumeScanTrigger() {
        // Löscht den letzten Wert aus dem Replay-Cache, damit er nicht bei Rotation neu triggert
        _scanTrigger.resetReplayCache()
    }
    
    // States für Scan-Resultate (Global damit sie Recompositions überstehen)
    var pendingScanResult by mutableStateOf<FoodItemEntity?>(null)
    var pendingAskToCapture by mutableStateOf<FoodItemEntity?>(null)
    var pendingFoodToCapture by mutableStateOf<FoodItemEntity?>(null)
    var pendingDuplicateFood by mutableStateOf<FoodItemEntity?>(null)

    val availableAiModels = mutableStateListOf<AiModelStatus>()

    private var nextFoodId = 1L
    private var nextEntryId = 1L
    private var nextMealId = 1L

    var isAnalyzingImage by mutableStateOf(false)
    var aiEstimationResult by mutableStateOf<AiEstimationResult?>(null)
    var aiErrorMessage by mutableStateOf<String?>(null)
    var selectedAiModel by mutableStateOf(prefs.getString("selected_ai_model", "gemini-3.6-flash") ?: "gemini-3.6-flash")

    init {
        loadLocalState()
        if (foods.isEmpty()) createDefaultFoods()
        if (categories.isEmpty()) createDefaultCategories()
        syncActivityForSelectedDate()
        setupFirebaseSync()

        viewModelScope.launch {
            profileRepository.userProfileFlow.collect { profile ->
                userProfile = profile
            }
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        syncActivityForSelectedDate()
    }

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

    fun setForceOnboarding(force: Boolean) {
        forceOnboardingOnStart = force
        prefs.edit().putBoolean("force_onboarding", force).apply()
    }

    fun updateShoppingListSort(sort: Boolean) {
        shoppingListSortByCategory = sort
        prefs.edit().putBoolean("shopping_sort_category", sort).apply()
    }

    fun syncActivityForSelectedDate() {
        viewModelScope.launch {
            if (healthConnectManager.isAvailable() && healthConnectManager.hasAllPermissions()) {
                val data = healthConnectManager.syncActivityForSelectedDate(selectedDate)
                updateActivity(data.steps, data.totalKcal, data.sessions)
            }
        }
    }

    private fun registerFcmToken(uid: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("NutritionViewModel", "FCM Token: $token")
                viewModelScope.launch {
                    val current = userProfile
                    // Only update if we have a valid profile (avoid overwriting with defaults during load)
                    if (current.setupCompleted && current.fcmToken != token) {
                        profileRepository.saveProfile(current.copy(fcmToken = token))
                        firebaseManager.updateFcmToken(uid, token)
                    }
                }
            }
        }
    }

    // --- Sync Logic ---
    private fun setupFirebaseSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            firebaseManager.currentUser.collectLatest { user ->
                if (user != null) {
                    registerFcmToken(user.uid)
                    coroutineScope {
                        launch {
                            firestoreRepository.getPersonalFoods(user.uid).collect { cloudFoods ->
                                syncList(foods, cloudFoods) { it.id }
                                updateNextIds()
                                saveFoods()
                            }
                        }
                        launch {
                            firestoreRepository.getPersonalMeals(user.uid).collect { cloudMeals ->
                                syncList(meals, cloudMeals) { it.id }
                                updateNextIds()
                                saveMeals()
                            }
                        }
                        launch {
                            firestoreRepository.getPersonalEntries(user.uid).collect { cloudEntries ->
                                syncList(allEntries, cloudEntries) { it.id }
                                updateNextIds()
                                saveEntries()
                            }
                        }
                        launch {
                            firestoreRepository.getWeightHistory(user.uid).collect { cloudWeight ->
                                weightHistory.clear()
                                weightHistory.addAll(cloudWeight.sortedByDescending { it.dateIso })
                                saveWeightHistory()
                            }
                        }
                        launch {
                            var lastMessageCount = -1
                            firestoreRepository.getInboxMessages(user.uid).collect { cloudMessages ->
                                // Show notification only if count increased and it's not the first load
                                if (lastMessageCount != -1 && cloudMessages.size > lastMessageCount) {
                                    val newestMessage = cloudMessages.firstOrNull()
                                    if (newestMessage != null && !newestMessage.isRead) {
                                        NotificationHelper.showInboxNotification(
                                            getApplication(),
                                            newestMessage.fromName,
                                            newestMessage.type.name
                                        )
                                    }
                                }
                                lastMessageCount = cloudMessages.size
                                inboxMessages.clear()
                                inboxMessages.addAll(cloudMessages)
                            }
                        }
                        launch {
                            firestoreRepository.getPersonalPlannedEntries(user.uid).collect { cloudPlanned ->
                                syncList(plannedEntries, cloudPlanned) { it.id }
                                updateNextIds()
                                savePlannedEntries()
                            }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            firebaseManager.household.collectLatest { household ->
                if (household != null) {
                    coroutineScope {
                        launch {
                            firestoreRepository.getPlannedMealPool(household.id).collect { cloudPool ->
                                syncList(plannedMealPool, cloudPool) { it.id }
                                savePlannedMealPool()
                            }
                        }
                        launch {
                            firestoreRepository.getShoppingList(household.id).collect { cloudShopping ->
                                syncList(shoppingList, cloudShopping) { it.id }
                                saveShoppingList()
                            }
                        }
                        launch {
                            val members = firestoreRepository.getHouseholdMembers(household.members)
                            householdMembers.clear()
                            householdMembers.addAll(members)
                        }
                    }
                }
            }
        }
    }

    private fun <T, K> syncList(localList: MutableList<T>, cloudList: List<T>, idSelector: (T) -> K) {
        if (cloudList.isEmpty()) {
            if (localList.isNotEmpty()) localList.clear()
            return
        }

        // Use a set of IDs for efficient removal check
        val cloudIds = cloudList.mapTo(HashSet()) { idSelector(it) }
        localList.removeAll { idSelector(it) !in cloudIds }
        cloudIds.clear() // Clear to help GC

        // Build a temporary index map for O(1) lookups during sync
        val localIndexMap = mutableMapOf<K, Int>()
        localList.forEachIndexed { index, item ->
            localIndexMap[idSelector(item)] = index
        }
        
        cloudList.forEach { cloudItem ->
            val id = idSelector(cloudItem)
            val index = localIndexMap[id]
            if (index != -1 && index != null) {
                if (localList[index] != cloudItem) {
                    localList[index] = cloudItem
                }
            } else {
                localList.add(cloudItem)
            }
        }
        localIndexMap.clear() // Clear to help GC
    }

    private suspend fun repairMealIngredients(uid: String) = withContext(Dispatchers.IO) {
        if (foods.isEmpty()) return@withContext
        val foodById = foods.associateBy { it.id }
        val foodByName = foods.associateBy { "${it.name}|${it.brand}" }

        val currentMeals = meals.toList()
        for (meal in currentMeals) {
            var changed = false
            val repairedIngredients = meal.ingredients.map { ing ->
                if (ing.kcalPer100g == 0.0) {
                    val food = foodById[ing.foodItemId] ?: foodByName["${ing.name}|${ing.brand}"]
                    if (food != null) {
                        val repaired = ing.copy(
                            foodItemId = food.id,
                            kcalPer100g = food.kcalPer100g,
                            proteinPer100g = food.proteinPer100g,
                            carbsPer100g = food.carbsPer100g,
                            sugarPer100g = food.sugarPer100g,
                            fatPer100g = food.fatPer100g,
                            saturatedFatPer100g = food.saturatedFatPer100g,
                            baseUnit = food.baseUnit,
                        )
                        if (repaired != ing) {
                            changed = true
                            repaired
                        } else ing
                    } else ing
                } else ing
            }
            if (changed) {
                val updatedMeal = meal.copy(ingredients = repairedIngredients, lastModified = System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    val idx = meals.indexOfFirst { it.id == meal.id }
                    if (idx != -1) meals[idx] = updatedMeal
                }
                firestoreRepository.savePersonalMeal(uid, updatedMeal)
                delay(100) // Sequentialize and throttle writes
            }
            yield()
        }
    }

    private suspend fun repairPlannedEntryIngredients(householdId: String) = withContext(Dispatchers.IO) {
        if (foods.isEmpty()) return@withContext
        val foodById = foods.associateBy { it.id }
        val foodByName = foods.associateBy { "${it.name}|${it.brand}" }

        val currentPlanned = plannedEntries.toList()
        for (entry in currentPlanned) {
            val ingredients = entry.mealIngredients
            if (entry.isMeal && (ingredients != null)) {
                var changed = false
                val repairedIngredients = ingredients.map { ing ->
                    if (ing.kcalPer100g == 0.0) {
                        val food = foodById[ing.foodItemId] ?: foodByName["${ing.name}|${ing.brand}"]
                        if (food != null) {
                            val repaired = ing.copy(
                                foodItemId = food.id,
                                kcalPer100g = food.kcalPer100g,
                                proteinPer100g = food.proteinPer100g,
                                carbsPer100g = food.carbsPer100g,
                                sugarPer100g = food.sugarPer100g,
                                fatPer100g = food.fatPer100g,
                                saturatedFatPer100g = food.saturatedFatPer100g,
                                baseUnit = food.baseUnit
                            )
                            if (repaired != ing) {
                                changed = true
                                repaired
                            } else ing
                        } else ing
                    } else ing
                }
                if (changed) {
                    val updatedEntry = entry.copy(mealIngredients = repairedIngredients)
                    withContext(Dispatchers.Main) {
                        val idx = plannedEntries.indexOfFirst { it.id == entry.id }
                        if (idx != -1) plannedEntries[idx] = updatedEntry
                    }
                    val user = firebaseManager.currentUser.value
                    if (user != null) {
                        firestoreRepository.savePersonalPlannedEntry(user.uid, updatedEntry)
                    }
                    delay(100) // Sequentialize and throttle writes
                }
            }
            yield()
        }
    }

    fun triggerAutoRepair() {
        if (isRepairing) return
        val user = firebaseManager.currentUser.value ?: return
        val householdId = firebaseManager.household.value?.id
        viewModelScope.launch {
            isRepairing = true
            try {
                repairMealIngredients(user.uid)
                if (householdId != null) repairPlannedEntryIngredients(householdId)
                saveMeals()
                savePlannedEntries()
            } catch (e: Exception) {
                Log.e("NutritionViewModel", "Auto repair failed", e)
            } finally {
                isRepairing = false
            }
        }
    }

    fun findFoodByBarcode(barcode: String): FoodItemEntity? = foods.find { it.barcode == barcode }

    fun getMatchStatus(entry: FoodEntryEntity): PlanMatchStatus {
        val currentUid = firebaseManager.currentUser.value?.uid
        val isOwnEntry = entry.plannedByUid == null || entry.plannedByUid == currentUid

        if (!isOwnEntry) return PlanMatchStatus.EXACT

        if (entry.isMeal) {
            val ings = entry.mealIngredients ?: return PlanMatchStatus.EXACT
            
            val missingAny = ings.any { ing -> 
                foods.none { 
                    it.id == ing.foodItemId || 
                    (!ing.barcode.isNullOrBlank() && it.barcode == ing.barcode) ||
                    (it.name.trim().equals(ing.name.trim(), ignoreCase = true) && (it.brand?.trim() ?: "") == (ing.brand?.trim() ?: "")) 
                }
            }
            
            if (missingAny) return PlanMatchStatus.DELETED_INGREDIENT
            
            return PlanMatchStatus.EXACT
        } else {
            val exists = foods.any { 
                it.id == entry.foodItemId || 
                (!entry.barcode.isNullOrBlank() && it.barcode == entry.barcode) ||
                (it.name.trim().equals(entry.name.trim(), ignoreCase = true) && (it.brand?.trim() ?: "") == (entry.brand?.trim() ?: "")) 
            }
            
            if (!exists) return PlanMatchStatus.MISSING
            
            return PlanMatchStatus.EXACT
        }
    }

    fun isMealModified(entry: FoodEntryEntity): Boolean {
        if (!entry.isMeal) return false
        val currentUid = firebaseManager.currentUser.value?.uid
        val isOwnEntry = entry.plannedByUid == null || entry.plannedByUid == currentUid
        if (!isOwnEntry) return false

        val template = meals.find { it.name.trim().equals(entry.name.trim(), ignoreCase = true) } ?: return false
        
        val ings = entry.mealIngredients ?: return false
        if (ings.size != template.ingredients.size) return true
        
        val ratio = entry.amount / template.servings
        
        return !ingredientsMatch(ings, template.ingredients, ratio)
    }

    fun isPoolItemModified(item: PlannedMealPoolEntity): Boolean {
        val currentUid = firebaseManager.currentUser.value?.uid
        if (item.createdByUid != currentUid) return false

        val template = meals.find { it.name.trim().equals(item.mealName.trim(), ignoreCase = true) } ?: return false
        val ings = item.mealIngredients
        if (ings.size != template.ingredients.size) return true
        
        val ratio = item.plannedPortions / template.servings
        return !ingredientsMatch(ings, template.ingredients, ratio)
    }

    private fun ingredientsMatch(current: List<MealIngredientEntity>, template: List<MealIngredientEntity>, ratio: Double): Boolean {
        if (current.size != template.size) return false
        
        return current.all { cIng ->
            template.any { tIng ->
                (tIng.name.trim().equals(cIng.name.trim(), ignoreCase = true) &&
                 (tIng.brand?.trim() ?: "").equals(cIng.brand?.trim() ?: "", ignoreCase = true)) &&
                kotlin.math.abs(cIng.amount - (tIng.amount * ratio)) < 0.01 &&
                kotlin.math.abs(cIng.kcalPer100g - tIng.kcalPer100g) < 0.1 &&
                kotlin.math.abs(cIng.proteinPer100g - tIng.proteinPer100g) < 0.1
            }
        }
    }

    private fun isDataCompatible(existing: FoodItemEntity, incoming: FoodItemEntity): Boolean {
        val kcalDiff = kotlin.math.abs(existing.kcalPer100g - incoming.kcalPer100g)
        val proteinDiff = kotlin.math.abs(existing.proteinPer100g - incoming.proteinPer100g)
        // Max 5% Abweichung oder 5 kcal / 1g Protein
        val kcalCompatible = kcalDiff < 5.0 || kcalDiff / incoming.kcalPer100g.coerceAtLeast(1.0) < 0.05
        val proteinCompatible = proteinDiff < 1.0 || proteinDiff / incoming.proteinPer100g.coerceAtLeast(1.0) < 0.05
        return kcalCompatible && proteinCompatible
    }

    private fun isDataCompatible(existing: FoodItemEntity, incoming: MealIngredientEntity): Boolean {
        val kcalDiff = kotlin.math.abs(existing.kcalPer100g - incoming.kcalPer100g)
        val proteinDiff = kotlin.math.abs(existing.proteinPer100g - incoming.proteinPer100g)
        val kcalCompatible = kcalDiff < 5.0 || kcalDiff / incoming.kcalPer100g.coerceAtLeast(1.0) < 0.05
        val proteinCompatible = proteinDiff < 1.0 || proteinDiff / incoming.proteinPer100g.coerceAtLeast(1.0) < 0.05
        return kcalCompatible && proteinCompatible
    }

    fun importPlannedMealToLibrary(entry: FoodEntryEntity) {
        if (!entry.isMeal) return
        val ingredients = entry.mealIngredients ?: return
        
        val householdId = firebaseManager.household.value?.id
        val user = firebaseManager.currentUser.value
        
        // 1. Alle Zutaten importieren/zuordnen
        val localIngredients = resolveIngredients(ingredients)

        // 2. Mahlzeit als Vorlage in die eigene Bibliothek speichern
        val newMealTemplate = MealEntity(
            id = nextMealId++,
            name = entry.name,
            ingredients = localIngredients,
            servings = entry.amount, // Wir nehmen die Menge aus dem Planer als Basis-Portion
            tags = entry.tags,
            imageUrl = entry.imageUrl,
            lastModified = System.currentTimeMillis()
        )
        meals.add(newMealTemplate)
        saveMeals()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.savePersonalMeal(user.uid, newMealTemplate) }
        }

        // 3. Den Planer-Eintrag aktualisieren (Zutaten-Links korrigieren)
        val entryIdx = plannedEntries.indexOfFirst { it.id == entry.id }
        if (entryIdx != -1) {
            val updated = plannedEntries[entryIdx].copy(
                mealIngredients = localIngredients,
                lastModifiedByUid = user?.uid,
                lastModifiedByName = userProfile.firstName
            )
            plannedEntries[entryIdx] = updated
            savePlannedEntries()
            if (user != null) {
                viewModelScope.launch { firestoreRepository.savePersonalPlannedEntry(user.uid, updated) }
            }
        }
    }

    private fun resolveIngredients(ingredients: List<MealIngredientEntity>): List<MealIngredientEntity> {
        return ingredients.map { ing ->
            val pseudoEntry = FoodEntryEntity(
                foodItemId = ing.foodItemId,
                name = ing.name,
                brand = ing.brand,
                kcalPer100g = ing.kcalPer100g,
                proteinPer100g = ing.proteinPer100g,
                carbsPer100g = ing.carbsPer100g,
                sugarPer100g = ing.sugarPer100g,
                fatPer100g = ing.fatPer100g,
                saturatedFatPer100g = ing.saturatedFatPer100g,
                alcoholPercent = ing.alcoholPercent,
                baseUnit = ing.baseUnit,
                store = ing.store,
                category = ing.category,
                barcode = ing.barcode,
                isGeneric = ing.isGeneric
            )
            
            val similar = foods.find { it.isSimilarTo(pseudoEntry) }
            val finalFoodId = if (similar != null) {
                similar.id
            } else {
                val newFood = addFood(
                    name = pseudoEntry.name,
                    kcal = pseudoEntry.kcalPer100g,
                    protein = pseudoEntry.proteinPer100g,
                    carbs = pseudoEntry.carbsPer100g,
                    sugar = pseudoEntry.sugarPer100g,
                    fat = pseudoEntry.fatPer100g,
                    saturatedFat = pseudoEntry.saturatedFatPer100g,
                    alcoholPercent = pseudoEntry.alcoholPercent,
                    baseUnit = pseudoEntry.baseUnit,
                    portions = emptyList(),
                    packages = emptyList(),
                    brand = pseudoEntry.brand,
                    store = pseudoEntry.store,
                    category = pseudoEntry.category,
                    barcode = pseudoEntry.barcode,
                    isGeneric = pseudoEntry.isGeneric
                )
                newFood.id
            }
            ing.copy(foodItemId = finalFoodId)
        }
    }

    fun fixMealTemplateArticles(meal: MealEntity) {
        val user = firebaseManager.currentUser.value ?: return
        val resolved = resolveIngredients(meal.ingredients)
        val updatedMeal = meal.copy(ingredients = resolved, lastModified = System.currentTimeMillis())
        
        val idx = meals.indexOfFirst { it.id == meal.id }
        if (idx != -1) {
            meals[idx] = updatedMeal
            saveMeals()
            viewModelScope.launch { firestoreRepository.savePersonalMeal(user.uid, updatedMeal) }
        }
    }

    fun importPlannedEntryToLibrary(entry: FoodEntryEntity, replaceExisting: Boolean = false) {
        if (entry.isMeal) return 
        
        val similar = foods.find { it.isSimilarTo(entry) }
        val householdId = firebaseManager.household.value?.id
        val finalFoodId: Long

        if (similar != null && replaceExisting) {
            // Existierenden Artikel aktualisieren
            val updated = similar.copy(
                kcalPer100g = entry.kcalPer100g,
                proteinPer100g = entry.proteinPer100g,
                carbsPer100g = entry.carbsPer100g,
                sugarPer100g = entry.sugarPer100g,
                fatPer100g = entry.fatPer100g,
                saturatedFatPer100g = entry.saturatedFatPer100g,
                alcoholPercent = entry.alcoholPercent,
                baseUnit = entry.baseUnit,
                category = entry.category,
                barcode = entry.barcode,
                store = entry.store,
                isGeneric = entry.isGeneric,
                lastModified = System.currentTimeMillis()
            )
            updateFood(updated)
            finalFoodId = updated.id
        } else {
            // Neuen Artikel anlegen (entweder weil MISSING oder weil User "Nebeneinander" wollte)
            val newFood = addFood(
                name = entry.name,
                kcal = entry.kcalPer100g,
                protein = entry.proteinPer100g,
                carbs = entry.carbsPer100g,
                sugar = entry.sugarPer100g,
                fat = entry.fatPer100g,
                saturatedFat = entry.saturatedFatPer100g,
                alcoholPercent = entry.alcoholPercent,
                baseUnit = entry.baseUnit,
                portions = emptyList(),
                packages = emptyList(),
                brand = entry.brand,
                store = entry.store,
                category = entry.category,
                barcode = entry.barcode,
                isGeneric = entry.isGeneric
            )
            finalFoodId = newFood.id
        }

        // Alle Planer-Einträge aktualisieren
        var changedAny = false
        val currentUser = firebaseManager.currentUser.value
        plannedEntries.forEachIndexed { idx, planned ->
            if (planned.isMeal) {
                val ingredients = planned.mealIngredients
                if (ingredients != null) {
                    var mealChanged = false
                    val updatedIngs = ingredients.map { ing ->
                        if (ing.name.trim().equals(entry.name.trim(), ignoreCase = true) && 
                            (ing.brand?.trim() ?: "").equals(entry.brand?.trim() ?: "", ignoreCase = true)) {
                            mealChanged = true
                            ing.copy(foodItemId = finalFoodId)
                        } else ing
                    }
                    if (mealChanged) {
                        val updatedMeal = planned.copy(
                            mealIngredients = updatedIngs,
                            lastModifiedByUid = currentUser?.uid,
                            lastModifiedByName = userProfile.firstName
                        )
                        plannedEntries[idx] = updatedMeal
                        if (currentUser != null) {
                            viewModelScope.launch { firestoreRepository.savePersonalPlannedEntry(currentUser.uid, updatedMeal) }
                        }
                        changedAny = true
                    }
                }
            } else {
                if (planned.name.trim().equals(entry.name.trim(), ignoreCase = true) && 
                    (planned.brand?.trim() ?: "").equals(entry.brand?.trim() ?: "", ignoreCase = true)) {
                    val updated = planned.copy(
                        foodItemId = finalFoodId,
                        lastModifiedByUid = currentUser?.uid,
                        lastModifiedByName = userProfile.firstName
                    )
                    plannedEntries[idx] = updated
                    if (currentUser != null) {
                        viewModelScope.launch { firestoreRepository.savePersonalPlannedEntry(currentUser.uid, updated) }
                    }
                    changedAny = true
                }
            }
        }

        if (changedAny) {
            savePlannedEntries()
        }
    }

    // --- Calorie Calculation ---
    fun calculateStepKcal(dateIso: String, profile: UserProfile): Double {
        val steps = dailySteps[dateIso] ?: 0
        val heightM = profile.heightCm / 100.0
        return 0.55 * profile.weightKg * steps * 0.415 * heightM / 1000.0
    }

    fun getActivityKcal(dateIso: String, profile: UserProfile): Double {
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
        val targetDeficit = userProfile.goalIntensity.toDouble()
        val deficitToUse = if (userProfile.goal == UserGoal.LOSE_WEIGHT) minOf(targetDeficit, actualDeficit) else actualDeficit
        return (deficitToUse / 7000.0) * 1000.0
    }

    fun getDayStatusColor(dateIso: String, profile: UserProfile): Int {
        val entries = allEntries.filter { it.dateIso == dateIso }
        if (entries.isEmpty()) return 0xFF9E9E9E.toInt()
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

    // --- AI Integration ---
    fun analyzeMealImage(bitmap: Bitmap, isUserPremium: Boolean) {
        if (!isUserPremium) {
            aiErrorMessage = "Premium erforderlich."
            return
        }
        isAnalyzingImage = true
        aiErrorMessage = null
        aiEstimationResult = null
        viewModelScope.launch {
            try {
                val service = GeminiService()
                val result = service.estimateNutrition(bitmap, selectedAiModel)
                aiEstimationResult = result
            } catch (e: Exception) {
                Log.e("NutritionViewModel", "AI analysis failed", e)
                aiErrorMessage = "Fehler: ${e.localizedMessage}"
            } finally {
                isAnalyzingImage = false
            }
        }
    }

    var isAnalyzingGenericFood by mutableStateOf(false)
    var aiGenericFoodError by mutableStateOf<String?>(null)

    suspend fun estimateGenericMacros(name: String, isBrandSearch: Boolean = false): AiGenericFoodResult? {
        isAnalyzingGenericFood = true
        aiGenericFoodError = null
        return try {
            val service = GeminiService()
            val result = service.estimateGenericFood(name, categories.toList(), isBrandSearch, selectedAiModel)
            if (result == null) {
                aiGenericFoodError = "Keine Daten gefunden."
            }
            result
        } catch (e: Exception) {
            Log.e("NutritionViewModel", "AI generic macros failed", e)
            aiGenericFoodError = "Fehler: ${e.localizedMessage}"
            null
        } finally {
            isAnalyzingGenericFood = false
        }
    }

    fun probeAiModels() {
        viewModelScope.launch {
            availableAiModels.clear()
            val service = GeminiService()
            val models = listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-3.6-flash")
            models.forEach { model ->
            availableAiModels.add(service.testModelAvailability(model))
        }
    }
    }

    // --- Data Management ---
    fun addFood(
        name: String, kcal: Double, protein: Double, carbs: Double, sugar: Double,
        fat: Double, saturatedFat: Double, alcoholPercent: Double, baseUnit: String,
        portions: List<FoodPortionEntity>, packages: List<FoodPackageEntity>,
        barcode: String? = null, brand: String? = null, category: String? = null,
        isGeneric: Boolean = false, parentId: Long? = null, store: String? = null,
        isPantryItem: Boolean = false
    ): FoodItemEntity {
        val user = firebaseManager.currentUser.value
        val newFood = FoodItemEntity(
            id = nextFoodId++,
            name = name,
            kcalPer100g = kcal,
            proteinPer100g = protein,
            carbsPer100g = carbs,
            sugarPer100g = sugar,
            fatPer100g = fat,
            saturatedFatPer100g = saturatedFat,
            alcoholPercent = alcoholPercent,
            baseUnit = baseUnit,
            portions = portions,
            packages = packages,
            barcode = barcode,
            brand = brand,
            category = category,
            isGeneric = isGeneric,
            parentId = parentId,
            store = store,
            isPantryItem = isPantryItem,
            lastModified = System.currentTimeMillis()
        )
        foods.add(newFood)
        saveFoods()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.savePersonalFood(user.uid, newFood) }
        }
        return newFood
    }

    fun updateFood(updatedFood: FoodItemEntity) {
        val user = firebaseManager.currentUser.value
        val householdId = firebaseManager.household.value?.id
        val index = foods.indexOfFirst { it.id == updatedFood.id }
        
        if (index != -1) {
            val oldName = foods[index].name
            val finalFood = updatedFood.copy(lastModified = System.currentTimeMillis())
            foods[index] = finalFood
            saveFoods()
            
            if (user != null) {
                viewModelScope.launch { firestoreRepository.savePersonalFood(user.uid, finalFood) }
            }

            // 1. Tagebuch-Einträge aktualisieren
            allEntries.forEachIndexed { i, entry ->
                if (entry.foodItemId == finalFood.id) {
                    val updatedEntry = entry.copy(
                        name = finalFood.name,
                        brand = finalFood.brand,
                        kcalPer100g = finalFood.kcalPer100g,
                        proteinPer100g = finalFood.proteinPer100g,
                        carbsPer100g = finalFood.carbsPer100g,
                        sugarPer100g = finalFood.sugarPer100g,
                        fatPer100g = finalFood.fatPer100g,
                        saturatedFatPer100g = finalFood.saturatedFatPer100g,
                        alcoholPercent = finalFood.alcoholPercent,
                        baseUnit = finalFood.baseUnit,
                        store = finalFood.store,
                        category = finalFood.category,
                        barcode = finalFood.barcode,
                        isGeneric = finalFood.isGeneric
                    )
                    allEntries[i] = updatedEntry
                    if (user != null) {
                        viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, updatedEntry) }
                    }
                } else if (entry.isMeal) {
                    val ingredients = entry.mealIngredients
                    if (ingredients != null) {
                        var changed = false
                        val updatedIngs = ingredients.map { ing ->
                            if (ing.foodItemId == finalFood.id) {
                                changed = true
                                ing.copy(
                                    name = finalFood.name,
                                    kcalPer100g = finalFood.kcalPer100g,
                                    proteinPer100g = finalFood.proteinPer100g,
                                    carbsPer100g = finalFood.carbsPer100g,
                                    sugarPer100g = finalFood.sugarPer100g,
                                    fatPer100g = finalFood.fatPer100g,
                                    saturatedFatPer100g = finalFood.saturatedFatPer100g,
                                    baseUnit = finalFood.baseUnit,
                                    store = finalFood.store,
                                    brand = finalFood.brand,
                                    category = finalFood.category,
                                    barcode = finalFood.barcode,
                                    isGeneric = finalFood.isGeneric
                                )
                            } else ing
                        }
                        if (changed) {
                            val updatedMealEntry = entry.copy(mealIngredients = updatedIngs)
                            allEntries[i] = updatedMealEntry
                            if (user != null) {
                                viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, updatedMealEntry) }
                            }
                        }
                    }
                }
            }
            saveEntries()

            // 2. Mahlzeiten-Vorlagen aktualisieren
            meals.forEachIndexed { i, meal ->
                var changed = false
                val updatedIngs = meal.ingredients.map { ing ->
                    if (ing.foodItemId == finalFood.id) {
                        changed = true
                        ing.copy(
                            name = finalFood.name,
                            kcalPer100g = finalFood.kcalPer100g,
                            proteinPer100g = finalFood.proteinPer100g,
                            carbsPer100g = finalFood.carbsPer100g,
                            sugarPer100g = finalFood.sugarPer100g,
                            fatPer100g = finalFood.fatPer100g,
                            saturatedFatPer100g = finalFood.saturatedFatPer100g,
                            baseUnit = finalFood.baseUnit,
                            store = finalFood.store,
                            brand = finalFood.brand,
                            category = finalFood.category,
                            barcode = finalFood.barcode,
                            isGeneric = finalFood.isGeneric
                        )
                    } else ing
                }
                if (changed) {
                    val updatedMeal = meal.copy(ingredients = updatedIngs, lastModified = System.currentTimeMillis())
                    meals[i] = updatedMeal
                    if (user != null) {
                        viewModelScope.launch { firestoreRepository.savePersonalMeal(user.uid, updatedMeal) }
                    }
                }
            }
            saveMeals()

            // 3. Planer-Einträge aktualisieren
            plannedEntries.forEachIndexed { i, entry ->
                if (entry.foodItemId == finalFood.id) {
                    val updatedEntry = entry.copy(
                        name = finalFood.name,
                        brand = finalFood.brand,
                        kcalPer100g = finalFood.kcalPer100g,
                        proteinPer100g = finalFood.proteinPer100g,
                        carbsPer100g = finalFood.carbsPer100g,
                        sugarPer100g = finalFood.sugarPer100g,
                        fatPer100g = finalFood.fatPer100g,
                        saturatedFatPer100g = finalFood.saturatedFatPer100g,
                        alcoholPercent = finalFood.alcoholPercent,
                        baseUnit = finalFood.baseUnit,
                        store = finalFood.store,
                        category = finalFood.category,
                        barcode = finalFood.barcode,
                        isGeneric = finalFood.isGeneric
                    )
                    plannedEntries[i] = updatedEntry
                    val user = firebaseManager.currentUser.value
                    if (user != null) {
                        viewModelScope.launch { firestoreRepository.savePersonalPlannedEntry(user.uid, updatedEntry) }
                    }
                } else if (entry.isMeal) {
                    val ingredients = entry.mealIngredients
                    if (ingredients != null) {
                        var changed = false
                        val updatedIngs = ingredients.map { ing ->
                            if (ing.foodItemId == finalFood.id) {
                                changed = true
                                ing.copy(
                                    name = finalFood.name,
                                    kcalPer100g = finalFood.kcalPer100g,
                                    proteinPer100g = finalFood.proteinPer100g,
                                    carbsPer100g = finalFood.carbsPer100g,
                                    sugarPer100g = finalFood.sugarPer100g,
                                    fatPer100g = finalFood.fatPer100g,
                                    saturatedFatPer100g = finalFood.saturatedFatPer100g,
                                    baseUnit = finalFood.baseUnit,
                                    store = finalFood.store,
                                    brand = finalFood.brand,
                                    category = finalFood.category,
                                    barcode = finalFood.barcode,
                                    isGeneric = finalFood.isGeneric
                                )
                            } else ing
                        }
                        if (changed) {
                            val updatedMealEntry = entry.copy(mealIngredients = updatedIngs)
                            plannedEntries[i] = updatedMealEntry
                            val user = firebaseManager.currentUser.value
                            if (user != null) {
                                viewModelScope.launch { firestoreRepository.savePersonalPlannedEntry(user.uid, updatedMealEntry) }
                            }
                        }
                    }
                }
            }
            savePlannedEntries()

            // 4. Einkaufsliste aktualisieren
            if (oldName != finalFood.name) {
                shoppingList.forEachIndexed { i, item ->
                    // Wir aktualisieren nur auto-generierte Items, die exakt den alten Namen hatten
                    if (item.isAutoGenerated && item.name.equals(oldName, ignoreCase = true)) {
                        val updatedItem = item.copy(name = finalFood.name, category = finalFood.category)
                        shoppingList[i] = updatedItem
                        if (householdId != null) {
                            viewModelScope.launch { firestoreRepository.updateShoppingItem(householdId, updatedItem) }
                        }
                    }
                }
                saveShoppingList()
            }
        }
    }

    fun deleteFood(id: Long) {
        val user = firebaseManager.currentUser.value
        foods.removeAll { it.id == id }
        saveFoods()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.deletePersonalFood(user.uid, id) }
        }
    }

    fun mergeFoods(targetParentId: Long, childIds: List<Long>) {
        val user = firebaseManager.currentUser.value
        childIds.forEach { childId ->
            allEntries.forEachIndexed { index, entry ->
                if (entry.foodItemId == childId) {
                    val updated = entry.copy(foodItemId = targetParentId)
                    allEntries[index] = updated
                    if (user != null) {
                        viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, updated) }
                    }
                }
            }
            deleteFood(childId)
        }
        saveEntries()
    }

    fun promoteToGeneric(foodId: Long) {
        val food = foods.find { it.id == foodId } ?: return
        updateFood(food.copy(isGeneric = true))
    }

    fun addMealTemplate(name: String, ingredients: List<MealIngredientEntity>, servings: Double, tags: List<String>, imageUrl: String?) {
        val user = firebaseManager.currentUser.value
        val newMeal = MealEntity(
            id = nextMealId++,
            name = name,
            ingredients = ingredients,
            servings = servings,
            tags = tags,
            imageUrl = imageUrl,
            lastModified = System.currentTimeMillis()
        )
        meals.add(newMeal)
        saveMeals()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.savePersonalMeal(user.uid, newMeal) }
        }
    }

    fun updateMealTemplate(updatedMeal: MealEntity) {
        val user = firebaseManager.currentUser.value
        val index = meals.indexOfFirst { it.id == updatedMeal.id }
        if (index != -1) {
            val updated = updatedMeal.copy(lastModified = System.currentTimeMillis())
            meals[index] = updated
            saveMeals()
            if (user != null) {
                viewModelScope.launch { firestoreRepository.savePersonalMeal(user.uid, updated) }
            }
        }
    }

    fun deleteMealTemplate(id: Long) {
        val user = firebaseManager.currentUser.value
        meals.removeAll { it.id == id }
        saveMeals()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.deletePersonalMeal(user.uid, id) }
        }
    }

    fun addEntry(food: FoodItemEntity, amount: Double, portion: FoodPortionEntity?, mealSlot: String, pkg: FoodPackageEntity? = null) {
        val grams = when {
            pkg != null -> amount * pkg.quantity
            portion != null -> amount * portion.grams
            else -> amount
        }
        val unitLabel = pkg?.name ?: portion?.name ?: food.baseUnit

        val entry = FoodEntryEntity(
            dateIso = selectedDate.toString(),
            mealSlot = mealSlot,
            amount = amount,
            unitLabel = unitLabel,
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
            store = food.store,
            isGeneric = food.isGeneric
        )
        addEntry(entry)
    }

    fun addEntry(entry: FoodEntryEntity) {
        val user = firebaseManager.currentUser.value
        // Highly unique ID using current time nanos + large random to prevent collisions in fast loops
        val newEntry = entry.copy(id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000))
        allEntries.add(newEntry)
        saveEntries()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, newEntry) }
        }
    }

    fun addMealEntry(meal: MealEntity, mealSlot: String, servings: Double = 1.0) {
        val ratio = servings / meal.servings
        val adjustedIngredients = meal.ingredients.map { ing ->
            val food = foods.find { it.id == ing.foodItemId }
            ing.copy(
                id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000),
                amount = ing.amount * ratio,
                grams = ing.grams * ratio,
                name = food?.name ?: ing.name,
                brand = food?.brand ?: ing.brand,
                kcalPer100g = food?.kcalPer100g ?: ing.kcalPer100g,
                proteinPer100g = food?.proteinPer100g ?: ing.proteinPer100g,
                carbsPer100g = food?.carbsPer100g ?: ing.carbsPer100g,
                sugarPer100g = food?.sugarPer100g ?: ing.sugarPer100g,
                fatPer100g = food?.fatPer100g ?: ing.fatPer100g,
                saturatedFatPer100g = food?.saturatedFatPer100g ?: ing.saturatedFatPer100g,
                alcoholPercent = food?.alcoholPercent ?: ing.alcoholPercent,
                baseUnit = food?.baseUnit ?: ing.baseUnit,
                category = food?.category ?: ing.category,
                barcode = food?.barcode ?: ing.barcode,
                isGeneric = food?.isGeneric ?: ing.isGeneric
            )
        }
        val entry = FoodEntryEntity(
            id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000),
            dateIso = selectedDate.toString(),
            mealSlot = mealSlot,
            name = meal.name,
            isMeal = true,
            mealIngredients = adjustedIngredients,
            grams = adjustedIngredients.sumOf { it.grams },
            amount = servings,
            unitLabel = "Portion(en)",
            imageUrl = meal.imageUrl,
            tags = meal.tags
        )
        addEntry(entry)
    }

    fun updateEntry(updatedEntry: FoodEntryEntity) {
        val user = firebaseManager.currentUser.value
        val index = allEntries.indexOfFirst { it.id == updatedEntry.id }
        if (index != -1) {
            allEntries[index] = updatedEntry
            saveEntries()
            if (user != null) {
                viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, updatedEntry) }
            }
        }
    }

    fun deleteEntry(id: Long) {
        val user = firebaseManager.currentUser.value
        allEntries.removeAll { it.id == id }
        saveEntries()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.deletePersonalEntry(user.uid, id) }
        }
    }

    fun copyEntriesToDate(entryIds: Set<Long>, targetDate: LocalDate) {
        val entriesToCopy = allEntries.filter { it.id in entryIds }
        entriesToCopy.forEach { entry ->
            addEntry(entry.copy(id = 0, dateIso = targetDate.toString()))
        }
    }

    fun moveEntriesToDate(entryIds: Set<Long>, targetDate: LocalDate) {
        val user = firebaseManager.currentUser.value
        allEntries.forEachIndexed { index, entry ->
            if (entry.id in entryIds) {
                val updated = entry.copy(dateIso = targetDate.toString())
                allEntries[index] = updated
                if (user != null) {
                    viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, updated) }
                }
            }
        }
        saveEntries()
    }

    fun verifyDay(dateIso: String, isComplete: Boolean) {
        dayVerifications[dateIso] = isComplete
        saveVerifications()
    }

    // --- Planner Pool ---
    fun addMealToPool(meal: MealEntity, portions: Double, isFrozen: Boolean = false, providedId: String? = null) {
        val householdId = firebaseManager.household.value?.id ?: return
        val user = firebaseManager.currentUser.value
        
        // Scale ingredients to the desired total pool portions
        val scale = portions / meal.servings
        val poolIngredients = meal.ingredients.map { ing ->
            val food = foods.find { it.id == ing.foodItemId }
            ing.copy(
                id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000),
                amount = ing.amount * scale,
                grams = ing.grams * scale,
                name = food?.name ?: ing.name,
                brand = food?.brand ?: ing.brand,
                kcalPer100g = food?.kcalPer100g ?: ing.kcalPer100g,
                proteinPer100g = food?.proteinPer100g ?: ing.proteinPer100g,
                carbsPer100g = food?.carbsPer100g ?: ing.carbsPer100g,
                sugarPer100g = food?.sugarPer100g ?: ing.sugarPer100g,
                fatPer100g = food?.fatPer100g ?: ing.fatPer100g,
                saturatedFatPer100g = food?.saturatedFatPer100g ?: ing.saturatedFatPer100g,
                alcoholPercent = food?.alcoholPercent ?: ing.alcoholPercent,
                baseUnit = food?.baseUnit ?: ing.baseUnit,
                category = food?.category ?: ing.category,
                barcode = food?.barcode ?: ing.barcode,
                isGeneric = food?.isGeneric ?: ing.isGeneric
            )
        }

        val poolId = providedId ?: UUID.randomUUID().toString()
        val poolEntry = PlannedMealPoolEntity(
            id = poolId,
            mealName = meal.name,
            mealIngredients = poolIngredients,
            plannedPortions = portions,
            remainingPortions = portions,
            imageUrl = meal.imageUrl,
            tags = meal.tags,
            createdByUid = user?.uid ?: "",
            createdByName = userProfile.firstName,
            isFrozen = isFrozen
        )
        viewModelScope.launch {
            firestoreRepository.addPlannedMealToPool(householdId, poolEntry)
            // Centralized shopping list logic in updatePoolItem
            updatePoolItem(poolEntry)
        }
    }

    fun takeFromPool(poolItem: PlannedMealPoolEntity, date: LocalDate, servings: Double, addToDiary: Boolean) {
        val householdId = firebaseManager.household.value?.id ?: return
        val user = firebaseManager.currentUser.value
        
        val updatedPool = if (poolItem.isFrozen) {
            // Gefrierschrank-Logik: Bestand verringern (Inventar-Modus)
            poolItem.copy(
                plannedPortions = (poolItem.plannedPortions - servings).coerceAtLeast(0.0)
            )
        } else {
            // Pool-Logik: Gesamtbedarf tracken (Einkaufs-Modus)
            val alreadyPlanned = plannedEntries.filter { it.poolItemId == poolItem.id }.sumOf { it.amount }
            val newTotalPlanned = alreadyPlanned + servings
            if (newTotalPlanned > poolItem.plannedPortions) {
                val scaleFactor = newTotalPlanned / poolItem.plannedPortions
                val newIngredients = poolItem.mealIngredients.map { it.copy(
                    amount = it.amount * scaleFactor,
                    grams = it.grams * scaleFactor
                ) }
                poolItem.copy(
                    plannedPortions = newTotalPlanned,
                    mealIngredients = newIngredients
                )
            } else {
                poolItem
            }
        }
        
        val entryId = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000)
        val factor = servings / poolItem.plannedPortions
        
        // Nährwerte und Zutaten auflösen
        val localIngredients = resolveIngredients(poolItem.mealIngredients.map { 
            it.copy(
                id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000),
                amount = it.amount * factor,
                grams = it.grams * factor
            )
        })

        val entry = FoodEntryEntity(
            id = entryId,
            dateIso = date.toString(),
            mealSlot = "Mittag",
            name = poolItem.mealName,
            isMeal = true,
            mealIngredients = localIngredients,
            amount = servings,
            unitLabel = "Portion(en)",
            imageUrl = poolItem.imageUrl,
            tags = poolItem.tags,
            isPlanned = true,
            poolItemId = poolItem.id,
            isFromFreezer = poolItem.isFrozen,
            plannedByUid = user?.uid,
            plannedByName = userProfile.firstName,
            lastModifiedByUid = user?.uid,
            lastModifiedByName = userProfile.firstName
        )

        viewModelScope.launch {
            if (updatedPool != poolItem) {
                updatePoolItem(updatedPool)
            }
            addPlannedEntry(entry)
            if (addToDiary) {
                addEntry(entry.copy(id = 0, isPlanned = false, originPlannedEntryId = entryId))
            }
        }
    }

    fun deletePoolItem(poolId: String, deleteFromShoppingList: Boolean) {
        val householdId = firebaseManager.household.value?.id ?: return
        val item = plannedMealPool.find { it.id == poolId } ?: return
        
        viewModelScope.launch {
            firestoreRepository.deletePlannedMealFromPool(householdId, poolId)
            if (deleteFromShoppingList) {
                val itemsToDelete = shoppingList.filter { it.poolItemId == poolId }.map { it.id }
                if (itemsToDelete.isNotEmpty()) {
                    itemsToDelete.forEach { deleteShoppingItem(it) }
                } else {
                    // Fallback für ältere Einträge
                    val sourceName = item.mealName
                    val itemsBySource = shoppingList.filter { it.sourceName == sourceName }.map { it.id }
                    itemsBySource.forEach { deleteShoppingItem(it) }
                }
            }
        }
    }

    fun updatePoolItem(updatedItem: PlannedMealPoolEntity) {
        val householdId = firebaseManager.household.value?.id ?: return
        viewModelScope.launch {
            shoppingMutex.withLock {
                firestoreRepository.updatePlannedMealInPool(householdId, updatedItem)

                // Shopping list sync (Authoritative Clean Slate Strategy)
                // 1. Identify all items that currently belong to this meal
                val itemsToRemove = shoppingList.filter { 
                    it.poolItemId == updatedItem.id || 
                    (it.sourceName?.trim()?.equals(updatedItem.mealName.trim(), ignoreCase = true) == true && !it.isPantryItem)
                }
                
                // 2. Remember checked status
                val checkedNames = itemsToRemove.filter { it.isChecked }.map { it.name.trim().lowercase() }.toSet()
                
                // 3. Remove all old items from Local and Cloud and WAIT
                itemsToRemove.forEach { item ->
                    shoppingList.removeAll { it.id == item.id }
                    firestoreRepository.deleteShoppingItem(householdId, item.id)
                }
                
                if (!updatedItem.isFrozen) {
                    // 4. Group and add fresh ingredients
                    val newIngredientsGrouped = updatedItem.mealIngredients
                        .filter { ing -> 
                            val food = foods.find { it.id == ing.foodItemId }
                            food?.isPantryItem != true 
                        }
                        .groupBy { it.name.trim().lowercase() to it.unitLabel }

                    newIngredientsGrouped.forEach { (key, ings) ->
                        val (name, unit) = key
                        val totalAmount = ings.sumOf { it.amount }
                        val totalWeight = ings.sumOf { it.grams }
                        val firstIng = ings.first()
                        
                        val newItemId = UUID.randomUUID().toString()
                        val newItem = ShoppingItem(
                            id = newItemId,
                            name = firstIng.name,
                            amount = totalAmount,
                            unit = unit,
                            isChecked = checkedNames.contains(name),
                            category = firstIng.category,
                            householdId = householdId,
                            isAutoGenerated = true,
                            isPantryItem = false,
                            baseUnit = firstIng.baseUnit,
                            weightGrams = totalWeight,
                            sourceName = updatedItem.mealName,
                            poolItemId = updatedItem.id
                        )
                        shoppingList.add(newItem)
                        firestoreRepository.addShoppingItem(householdId, newItem)
                    }
                }
                saveShoppingList()
            }
        }
    }

    // --- Planner ---
    fun addPlannedEntry(food: FoodItemEntity, amount: Double, portion: FoodPortionEntity?, mealSlot: String, date: LocalDate, pkg: FoodPackageEntity? = null) {
        val grams = when {
            pkg != null -> amount * pkg.quantity
            portion != null -> amount * portion.grams
            else -> amount
        }
        val unitLabel = pkg?.name ?: portion?.name ?: food.baseUnit

        val entryId = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000)
        val user = firebaseManager.currentUser.value
        val entry = FoodEntryEntity(
            id = entryId,
            dateIso = date.toString(),
            mealSlot = mealSlot,
            amount = amount,
            unitLabel = unitLabel,
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
            store = food.store,
            category = food.category,
            barcode = food.barcode,
            isGeneric = food.isGeneric,
            isPlanned = true,
            plannedByUid = user?.uid,
            plannedByName = userProfile.firstName,
            lastModifiedByUid = user?.uid,
            lastModifiedByName = userProfile.firstName
        )
        addPlannedEntry(entry)
        internalAddToShoppingList(food, amount, unitLabel, pkg, sourceName = "Einzelartikel @ ${date}", plannedEntryId = entryId)
    }

    fun addPlannedEntry(entry: FoodEntryEntity) {
        val user = firebaseManager.currentUser.value
        // Sicherstellen, dass die ID absolut eindeutig ist
        val newEntry = if (entry.id == 0L) {
            entry.copy(id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000))
        } else entry
        
        plannedEntries.add(newEntry)
        savePlannedEntries()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.savePersonalPlannedEntry(user.uid, newEntry) }
        }
    }

    fun addPlannedMeal(meal: MealEntity, mealSlot: String, date: LocalDate, servings: Double = 1.0) {
        val ratio = servings / meal.servings

        val entryId = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000)
        val user = firebaseManager.currentUser.value
        val adjustedIngredients = meal.ingredients.map { ing ->
            val food = foods.find { it.id == ing.foodItemId }
            ing.copy(
                id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000),
                amount = ing.amount * ratio,
                grams = ing.grams * ratio,
                name = food?.name ?: ing.name,
                brand = food?.brand ?: ing.brand,
                kcalPer100g = food?.kcalPer100g ?: ing.kcalPer100g,
                proteinPer100g = food?.proteinPer100g ?: ing.proteinPer100g,
                carbsPer100g = food?.carbsPer100g ?: ing.carbsPer100g,
                sugarPer100g = food?.sugarPer100g ?: ing.sugarPer100g,
                fatPer100g = food?.fatPer100g ?: ing.fatPer100g,
                saturatedFatPer100g = food?.saturatedFatPer100g ?: ing.saturatedFatPer100g,
                alcoholPercent = food?.alcoholPercent ?: ing.alcoholPercent,
                baseUnit = food?.baseUnit ?: ing.baseUnit,
                category = food?.category ?: ing.category,
                barcode = food?.barcode ?: ing.barcode,
                isGeneric = food?.isGeneric ?: ing.isGeneric
            )
        }

        // Sync with pool: Every planned meal should be in the pool
        val existingPoolItem = plannedMealPool.find { it.mealName == meal.name && !it.isFrozen }
        val poolId = if (existingPoolItem != null) {
            // Check if we need to increase portions
            val currentlyPlanned = plannedEntries.filter { it.poolItemId == existingPoolItem.id }.sumOf { it.amount }
            val newTotal = currentlyPlanned + servings
            if (newTotal > existingPoolItem.plannedPortions) {
                updatePoolItem(existingPoolItem.copy(plannedPortions = newTotal))
            }
            existingPoolItem.id
        } else {
            // Create new pool item
            val newId = UUID.randomUUID().toString()
            addMealToPool(meal, servings, isFrozen = false, providedId = newId) 
            newId 
        }

        val entry = FoodEntryEntity(
            id = entryId,
            dateIso = date.toString(),
            mealSlot = mealSlot,
            name = meal.name,
            isMeal = true,
            mealIngredients = adjustedIngredients,
            grams = adjustedIngredients.sumOf { it.grams },
            amount = servings,
            unitLabel = "Portion(en)",
            imageUrl = meal.imageUrl,
            tags = meal.tags,
            isPlanned = true,
            poolItemId = poolId,
            plannedByUid = user?.uid,
            plannedByName = userProfile.firstName,
            lastModifiedByUid = user?.uid,
            lastModifiedByName = userProfile.firstName
        )
        addPlannedEntry(entry)
    }

    fun updatePlannedEntry(updatedEntry: FoodEntryEntity) {
        val user = firebaseManager.currentUser.value
        val householdId = firebaseManager.household.value?.id
        val index = plannedEntries.indexOfFirst { it.id == updatedEntry.id }
        if (index != -1) {
            val oldEntry = plannedEntries[index]
            val finalEntry = updatedEntry.copy(
                lastModifiedByUid = user?.uid,
                lastModifiedByName = userProfile.firstName
            )
            plannedEntries[index] = finalEntry
            savePlannedEntries()

            // Pool-Synchronisierung: Wenn die Menge im Planer geändert wird, muss der Pool angepasst werden
            if (finalEntry.poolItemId != null && householdId != null) {
                val poolItem = plannedMealPool.find { it.id == finalEntry.poolItemId }
                if (poolItem != null) {
                    val diff = finalEntry.amount - oldEntry.amount
                    if (poolItem.isFrozen) {
                        // Gefrierschrank: Bestand anpassen (Inventar)
                        updatePoolItem(poolItem.copy(plannedPortions = (poolItem.plannedPortions - diff).coerceAtLeast(0.0)))
                    } else {
                        // Pool: Gesamtbedarf anpassen (Einkauf)
                        val currentlyPlanned = plannedEntries.filter { it.poolItemId == poolItem.id }.sumOf { it.amount }
                        if (currentlyPlanned > poolItem.plannedPortions) {
                            updatePoolItem(poolItem.copy(plannedPortions = currentlyPlanned))
                        }
                    }
                }
            }

            // Tagebuch-Synchronisierung: Wenn ein zugehöriger Eintrag im Tagebuch existiert, diesen aktualisieren
            val diaryEntryIndex = allEntries.indexOfFirst { it.originPlannedEntryId == finalEntry.id }
            if (diaryEntryIndex != -1) {
                val oldDiary = allEntries[diaryEntryIndex]
                val updatedDiary = oldDiary.copy(
                    dateIso = finalEntry.dateIso,
                    mealSlot = finalEntry.mealSlot,
                    amount = finalEntry.amount,
                    grams = finalEntry.grams,
                    mealIngredients = finalEntry.mealIngredients,
                    name = finalEntry.name,
                    brand = finalEntry.brand,
                    barcode = finalEntry.barcode,
                    isGeneric = finalEntry.isGeneric,
                    isMeal = finalEntry.isMeal,
                    imageUrl = finalEntry.imageUrl,
                    tags = finalEntry.tags,
                    kcalPer100g = finalEntry.kcalPer100g,
                    proteinPer100g = finalEntry.proteinPer100g,
                    carbsPer100g = finalEntry.carbsPer100g,
                    sugarPer100g = finalEntry.sugarPer100g,
                    fatPer100g = finalEntry.fatPer100g,
                    saturatedFatPer100g = finalEntry.saturatedFatPer100g,
                    alcoholPercent = finalEntry.alcoholPercent,
                    baseUnit = finalEntry.baseUnit,
                    category = finalEntry.category,
                    store = finalEntry.store
                )
                allEntries[diaryEntryIndex] = updatedDiary
                saveEntries()
                if (user != null) {
                    viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, updatedDiary) }
                }
            }

            if (user != null) {
                viewModelScope.launch { 
                    firestoreRepository.savePersonalPlannedEntry(user.uid, finalEntry)
                    
                    // Einkaufsliste aktualisieren
                    // NUR für Einzelartikel (poolItemId == null) und NICHT aus dem Gefrierschrank
                    if (finalEntry.poolItemId == null && !finalEntry.isFromFreezer) {
                        // 1. Bestehende Items für diesen Eintrag entfernen
                        val itemsToDelete = shoppingList.filter { it.plannedEntryId == finalEntry.id }.map { it.id }
                        itemsToDelete.forEach { deleteShoppingItem(it) }
                        
                        // 2. Neue Items hinzufügen (basierend auf geänderter Menge/Portionen)
                        // Da poolItemId null ist, kann es eigentlich nur ein Einzelartikel sein
                        val food = foods.find { it.id == finalEntry.foodItemId }
                        if (food != null && !food.isPantryItem) {
                            internalAddToShoppingList(food, finalEntry.amount, finalEntry.unitLabel, sourceName = "Einzelartikel @ ${finalEntry.dateIso}", plannedEntryId = finalEntry.id)
                        } else if (food == null) {
                            // Fallback für unbekannte Einzelartikel (z.B. vom Partner geplant)
                            internalAddToShoppingList(
                                FoodItemEntity(name = finalEntry.name, category = finalEntry.category, brand = finalEntry.brand),
                                finalEntry.amount,
                                finalEntry.unitLabel,
                                sourceName = "Einzelartikel @ ${finalEntry.dateIso}",
                                plannedEntryId = finalEntry.id
                            )
                        }
                    } else if (finalEntry.isFromFreezer) {
                        // Falls es eine Freezer-Mahlzeit ist, stellen wir sicher, dass nichts auf der Liste steht
                        val itemsToDelete = shoppingList.filter { it.plannedEntryId == finalEntry.id }.map { it.id }
                        itemsToDelete.forEach { deleteShoppingItem(it) }
                    }
                }
            }
        }
    }

    fun movePlannedEntry(entryId: Long, targetDate: LocalDate, portionsToMove: Double) {
        val entry = plannedEntries.find { it.id == entryId } ?: return
        val user = firebaseManager.currentUser.value
        val householdId = firebaseManager.household.value?.id

        if (portionsToMove >= entry.amount) {
            // Move entire entry
            val updated = entry.copy(
                dateIso = targetDate.toString(),
                lastModifiedByUid = user?.uid,
                lastModifiedByName = userProfile.firstName
            )
            val idx = plannedEntries.indexOfFirst { it.id == entryId }
            if (idx != -1) {
                plannedEntries[idx] = updated
                savePlannedEntries()
                if (user != null) {
                    viewModelScope.launch { firestoreRepository.savePersonalPlannedEntry(user.uid, updated) }
                }
            }
        } else {
            // Split entry: reduce source, create new for target
            val ratioSource = (entry.amount - portionsToMove) / entry.amount
            val ratioTarget = portionsToMove / entry.amount

            val updatedSource = entry.copy(
                amount = entry.amount - portionsToMove,
                grams = entry.grams * ratioSource,
                mealIngredients = entry.mealIngredients?.map { it.copy(
                    amount = it.amount * ratioSource,
                    grams = it.grams * ratioSource
                )},
                lastModifiedByUid = user?.uid,
                lastModifiedByName = userProfile.firstName
            )

            val newId = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000)
            val newEntry = entry.copy(
                id = newId,
                dateIso = targetDate.toString(),
                amount = portionsToMove,
                grams = entry.grams * ratioTarget,
                mealIngredients = entry.mealIngredients?.map { it.copy(
                    id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000),
                    amount = it.amount * ratioTarget,
                    grams = it.grams * ratioTarget
                )},
                lastModifiedByUid = user?.uid,
                lastModifiedByName = userProfile.firstName
            )

            val idx = plannedEntries.indexOfFirst { it.id == entryId }
            if (idx != -1) {
                plannedEntries[idx] = updatedSource
                plannedEntries.add(newEntry)
                savePlannedEntries()
                if (user != null) {
                    viewModelScope.launch {
                        firestoreRepository.savePersonalPlannedEntry(user.uid, updatedSource)
                        firestoreRepository.savePersonalPlannedEntry(user.uid, newEntry)
                    }
                }
            }
        }
    }

    fun deletePlannedEntry(entryId: Long, deleteFromShoppingList: Boolean = false) {
        val householdId = firebaseManager.household.value?.id
        val entry = plannedEntries.find { it.id == entryId } ?: return
        
        // "Zurücklegen"-Logik für Gefrierschrank
        if (entry.poolItemId != null && householdId != null) {
            val poolItem = plannedMealPool.find { it.id == entry.poolItemId }
            if (poolItem != null && poolItem.isFrozen) {
                updatePoolItem(poolItem.copy(plannedPortions = poolItem.plannedPortions + entry.amount))
            }
        }
        
        if (deleteFromShoppingList && householdId != null) {
            // Erst versuchen über ID zu löschen (präziser)
            val itemsToDeleteById = shoppingList.filter { it.plannedEntryId == entryId }.map { it.id }
            if (itemsToDeleteById.isNotEmpty()) {
                itemsToDeleteById.forEach { deleteShoppingItem(it) }
            } else {
                // Fallback auf sourceName für ältere Einträge
                val sourceName = if (entry.isMeal) {
                    "${entry.name} @ ${entry.dateIso}"
                } else {
                    "Einzelartikel @ ${entry.dateIso}"
                }
                val itemsToDeleteBySource = shoppingList.filter { it.sourceName == sourceName }.map { it.id }
                itemsToDeleteBySource.forEach { deleteShoppingItem(it) }
            }
        }

        plannedEntries.removeAll { it.id == entryId }
        savePlannedEntries()

        val user = firebaseManager.currentUser.value
        if (user != null) {
            viewModelScope.launch { firestoreRepository.deletePersonalPlannedEntry(user.uid, entryId) }
        }
    }

    // --- Shopping List ---
    fun toggleShoppingItem(item: ShoppingItem) {
        val householdId = firebaseManager.household.value?.id
        val index = shoppingList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            val updated = item.copy(isChecked = !item.isChecked)
            shoppingList[index] = updated
            saveShoppingList()
            if (householdId != null) {
                viewModelScope.launch { firestoreRepository.updateShoppingItem(householdId, updated) }
            }
        }
    }

    fun deleteShoppingItem(id: String) {
        val householdId = firebaseManager.household.value?.id
        shoppingList.removeAll { it.id == id }
        saveShoppingList()
        if (householdId != null) {
            viewModelScope.launch { firestoreRepository.deleteShoppingItem(householdId, id) }
        }
    }

    private fun getGeneralName(food: FoodItemEntity): String {
        return food.name.split(",").first().trim()
    }

    private fun internalAddToShoppingList(
        food: FoodItemEntity, 
        amount: Double, 
        unit: String, 
        pkg: FoodPackageEntity? = null, 
        sourceName: String? = null, 
        plannedEntryId: Long? = null
    ) {
        val householdId = firebaseManager.household.value?.id ?: return
        val name = getGeneralName(food)
        
        val addedWeight = when {
            pkg != null -> amount * pkg.quantity
            unit == food.baseUnit -> amount
            else -> 0.0 
        }

        // NUR für Einzelartikel (keine Mahlzeiten-Bestandteile)
        val existingIndex = shoppingList.indexOfFirst { 
            it.name.equals(name, ignoreCase = true) && 
            it.unit == unit && 
            it.isPantryItem == food.isPantryItem &&
            (if (plannedEntryId != null) it.plannedEntryId == plannedEntryId else it.sourceName == (sourceName ?: "Manuell hinzugefügt"))
        }
        
        if (existingIndex != -1) {
            val existing = shoppingList[existingIndex]
            val updated = existing.copy(
                amount = existing.amount + amount,
                weightGrams = existing.weightGrams + addedWeight
            )
            shoppingList[existingIndex] = updated
            viewModelScope.launch { firestoreRepository.updateShoppingItem(householdId, updated) }
        } else {
            val newItemId = UUID.randomUUID().toString()
            val newItem = ShoppingItem(
                id = newItemId,
                name = name,
                amount = amount,
                unit = unit,
                category = food.category,
                householdId = householdId,
                isAutoGenerated = true,
                isPantryItem = food.isPantryItem,
                baseUnit = food.baseUnit,
                weightGrams = addedWeight,
                sourceName = sourceName ?: "Manuell hinzugefügt",
                plannedEntryId = plannedEntryId
            )
            shoppingList.add(newItem)
            viewModelScope.launch { firestoreRepository.addShoppingItem(householdId, newItem) }
        }
        saveShoppingList()
    }

    fun addShoppingItem(name: String, amount: Double, unit: String, category: String?, isPremium: Boolean) {
        val householdId = firebaseManager.household.value?.id ?: ""
        val newItem = ShoppingItem(
            id = UUID.randomUUID().toString(),
            name = name,
            amount = amount,
            unit = unit,
            category = category,
            householdId = householdId,
            sourceName = "Manuell hinzugefügt"
        )
        shoppingList.add(newItem)
        saveShoppingList()
        if (householdId.isNotEmpty()) {
            viewModelScope.launch { firestoreRepository.addShoppingItem(householdId, newItem) }
        }
    }

    fun updateShoppingItem(updatedItem: ShoppingItem) {
        val householdId = firebaseManager.household.value?.id
        val index = shoppingList.indexOfFirst { it.id == updatedItem.id }
        if (index != -1) {
            shoppingList[index] = updatedItem
            saveShoppingList()
            if (householdId != null) {
                viewModelScope.launch { firestoreRepository.updateShoppingItem(householdId, updatedItem) }
            }
        }
    }

    suspend fun suggestCategory(name: String, isPremium: Boolean): String? {
        if (!isPremium) return foods.find { it.name.equals(name, ignoreCase = true) }?.category
        return try {
            GeminiService().categorizeItem(name, categories)
        } catch (e: Exception) {
            null
        }
    }

    // --- Weight Management ---
    fun addWeightEntry(weight: Double, dateIso: String, profile: UserProfile) {
        val user = firebaseManager.currentUser.value
        val newEntry = WeightEntry(dateIso, weight)
        weightHistory.removeAll { it.dateIso == dateIso }
        weightHistory.add(newEntry)
        weightHistory.sortByDescending { it.dateIso }
        saveWeightHistory()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.saveWeightEntry(user.uid, newEntry) }
        }
    }

    fun deleteWeightEntry(dateIso: String) {
        val user = firebaseManager.currentUser.value
        weightHistory.removeAll { it.dateIso == dateIso }
        saveWeightHistory()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.deleteWeightEntry(user.uid, dateIso) }
        }
    }

    fun calculateMetabolicFactorForProfile(profile: UserProfile): Double {
        if (weightHistory.size < 2) return profile.metabolicFactor
        
        val sorted = weightHistory.sortedBy { it.dateIso }
        val latest = sorted.last()
        val first = sorted.first()
        
        val days = ChronoUnit.DAYS.between(LocalDate.parse(first.dateIso), LocalDate.parse(latest.dateIso))
        if (days < 7) return profile.metabolicFactor
        
        val actualLossKg = first.weight - latest.weight
        val actualDeficit = actualLossKg * 7000.0
        
        var totalTheoreticalDeficit = 0.0
        var daysWithEntries = 0
        
        val startDate = LocalDate.parse(first.dateIso)
        val endDate = LocalDate.parse(latest.dateIso)
        
        var curr = startDate
        while (!curr.isAfter(endDate)) {
            val dateIso = curr.toString()
            val entries = allEntries.filter { it.dateIso == dateIso }
            if (entries.isNotEmpty()) {
                val intake = entries.sumOf { it.kcal }
                val activity = getActivityKcal(dateIso, profile)
                totalTheoreticalDeficit += (profile.bmr + activity) - intake
                daysWithEntries++
            }
            curr = curr.plusDays(1)
        }
        
        if (daysWithEntries < 5 || totalTheoreticalDeficit == 0.0) return profile.metabolicFactor
        
        val factor = actualDeficit / totalTheoreticalDeficit
        return factor.coerceIn(0.5, 2.0)
    }

    // --- Category Management ---
    fun addCategory(category: String) {
        if (!categories.contains(category)) {
            categories.add(category)
            saveCategories()
        }
    }

    fun updateCategory(old: String, new: String) {
        val index = categories.indexOf(old)
        if (index != -1) {
            categories[index] = new
            foods.forEachIndexed { fIndex, food ->
                if (food.category == old) {
                    val updated = food.copy(category = new)
                    foods[fIndex] = updated
                    updateFood(updated)
                }
            }
            saveCategories()
        }
    }

    fun deleteCategory(category: String) {
        categories.remove(category)
        saveCategories()
    }

    // --- Social / Inbox ---
    fun updateActivity(steps: Int?, totalKcal: Double?, sessions: List<ExerciseSessionInfo>? = null) {
        val dateStr = selectedDate.toString()
        steps?.let { dailySteps[dateStr] = it }
        totalKcal?.let { dailyTotalCalories[dateStr] = it }
        sessions?.let { dailyExerciseSessions[dateStr] = it }
        saveActivity()
    }

    fun markMessageAsRead(messageId: String) {
        val user = firebaseManager.currentUser.value ?: return
        viewModelScope.launch { firestoreRepository.markMessageAsRead(user.uid, messageId) }
    }

    fun deleteInboxMessage(messageId: String) {
        val user = firebaseManager.currentUser.value ?: return
        viewModelScope.launch { firestoreRepository.deleteInboxMessage(user.uid, messageId) }
    }

    fun sendFoodToUser(targetUid: String, food: FoodItemEntity) {
        val user = firebaseManager.currentUser.value ?: return
        val message = InboxMessage(
            fromUid = user.uid,
            fromName = userProfile.firstName,
            type = MessageType.FOOD,
            payloadJson = json.encodeToString(food),
            timestamp = System.currentTimeMillis()
        )
        viewModelScope.launch { firestoreRepository.sendInboxMessage(targetUid, message) }
    }

    fun sendRecipeToUser(targetUid: String, meal: MealEntity) {
        val user = firebaseManager.currentUser.value ?: return
        viewModelScope.launch {
            var finalMeal = meal
            val imageUrl = meal.imageUrl
            if (imageUrl != null && imageUrl.startsWith("/")) { // Local path
                val cloudUrl = firestoreRepository.uploadMealImage(user.uid, imageUrl)
                if (cloudUrl != null) {
                    finalMeal = meal.copy(imageUrl = cloudUrl)
                }
            }
            
            // Get all related foods for this meal
            val relatedFoods = finalMeal.ingredients.mapNotNull { ing ->
                foods.find { it.id == ing.foodItemId }
            }.distinctBy { it.id }

            val recipeData = RecipeData(meal = finalMeal, relatedFoods = relatedFoods)

            val message = InboxMessage(
                fromUid = user.uid,
                fromName = userProfile.firstName,
                type = MessageType.RECIPE,
                payloadJson = json.encodeToString(recipeData),
                timestamp = System.currentTimeMillis()
            )
            firestoreRepository.sendInboxMessage(targetUid, message)
        }
    }

    fun getRecipeJson(meal: MealEntity): String {
        val relatedFoods = meal.ingredients.mapNotNull { ing ->
            foods.find { it.id == ing.foodItemId }
        }.distinctBy { it.id }
        return json.encodeToString(RecipeData(meal = meal, relatedFoods = relatedFoods))
    }

    fun startRecipeImport(jsonStr: String): Boolean {
        return try {
            pendingRecipeImport = json.decodeFromString<RecipeData>(jsonStr)
            true
        } catch (e: Exception) {
            Log.e("NutritionViewModel", "Recipe import failed", e)
            false
        }
    }

    fun resolveRecipeImport(supplement: Boolean) {
        val importData = pendingRecipeImport ?: return
        viewModelScope.launch {
            // Map old ID to new local ID
            val idMapping = mutableMapOf<Long, Long>()
            
            importData.relatedFoods.forEach { food ->
                val existing = foods.find { 
                    (!food.barcode.isNullOrBlank() && it.barcode == food.barcode) ||
                    (it.name.trim().equals(food.name.trim(), ignoreCase = true) && 
                     (it.brand?.trim() ?: "").equals(food.brand?.trim() ?: "", ignoreCase = true))
                }
                
                if (existing == null) {
                    val added = addFood(
                        name = food.name, kcal = food.kcalPer100g, protein = food.proteinPer100g,
                        carbs = food.carbsPer100g, sugar = food.sugarPer100g, fat = food.fatPer100g,
                        saturatedFat = food.saturatedFatPer100g, alcoholPercent = food.alcoholPercent,
                        baseUnit = food.baseUnit, portions = food.portions, packages = food.packages,
                        barcode = food.barcode, brand = food.brand, category = food.category,
                        isGeneric = food.isGeneric, store = food.store
                    )
                    idMapping[food.id] = added.id
                } else {
                    if (isDataCompatible(existing, food)) {
                        if (supplement) {
                            updateFood(existing.copy(
                                kcalPer100g = food.kcalPer100g,
                                proteinPer100g = food.proteinPer100g,
                                carbsPer100g = food.carbsPer100g,
                                sugarPer100g = food.sugarPer100g,
                                fatPer100g = food.fatPer100g,
                                saturatedFatPer100g = food.saturatedFatPer100g,
                                alcoholPercent = food.alcoholPercent,
                                portions = food.portions,
                                packages = food.packages,
                                category = food.category,
                                barcode = food.barcode,
                                brand = food.brand,
                                isGeneric = food.isGeneric
                            ))
                        }
                        idMapping[food.id] = existing.id
                    } else {
                        // Smart Conflict: Create variant
                        val added = addFood(
                            name = "${food.name} (Import)", kcal = food.kcalPer100g, protein = food.proteinPer100g,
                            carbs = food.carbsPer100g, sugar = food.sugarPer100g, fat = food.fatPer100g,
                            saturatedFat = food.saturatedFatPer100g, alcoholPercent = food.alcoholPercent,
                            baseUnit = food.baseUnit, portions = food.portions, packages = food.packages,
                            barcode = food.barcode, brand = food.brand, category = food.category,
                            isGeneric = food.isGeneric, store = food.store
                        )
                        idMapping[food.id] = added.id
                    }
                }
            }
            
            // Update ingredient links
            val updatedIngredients = importData.meal.ingredients.map { ing ->
                ing.copy(foodItemId = idMapping[ing.foodItemId] ?: ing.foodItemId)
            }
            
            val localMeal = importData.meal.copy(ingredients = updatedIngredients)
            
            val existingMeal = meals.find { it.name.trim().equals(localMeal.name.trim(), ignoreCase = true) }
            if (existingMeal == null) {
                addMealTemplate(
                    localMeal.name, localMeal.ingredients,
                    localMeal.servings, localMeal.tags, localMeal.imageUrl
                )
            } else if (supplement) {
                updateMealTemplate(existingMeal.copy(
                    ingredients = localMeal.ingredients,
                    servings = localMeal.servings,
                    tags = localMeal.tags,
                    imageUrl = localMeal.imageUrl
                ))
            }
            pendingRecipeImport = null
        }
    }

    fun forceMigrationToCloud() {
        val user = firebaseManager.currentUser.value ?: return
        val householdId = firebaseManager.household.value?.id
        viewModelScope.launch {
            foods.forEach { firestoreRepository.savePersonalFood(user.uid, it) }
            meals.forEach { firestoreRepository.savePersonalMeal(user.uid, it) }
            allEntries.forEach { firestoreRepository.savePersonalEntry(user.uid, it) }
            weightHistory.forEach { firestoreRepository.saveWeightEntry(user.uid, it) }
            plannedEntries.forEach { firestoreRepository.savePersonalPlannedEntry(user.uid, it) }
            if (householdId != null) {
                shoppingList.forEach { firestoreRepository.addShoppingItem(householdId, it) }
            }
        }
    }

    fun saveImageLocally(uri: android.net.Uri): String? {
        return try {
            val contentResolver = getApplication<Application>().contentResolver
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(getApplication<Application>().filesDir, "meal_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("NutritionViewModel", "Failed to save image locally", e)
            null
        }
    }

    // --- Persistence & Backup ---
    fun importBackup(jsonStr: String): Boolean {
        return try {
            val data = json.decodeFromString<BackupData>(jsonStr)
            foods.clear(); foods.addAll(data.foods)
            meals.clear(); meals.addAll(data.meals)
            allEntries.clear(); allEntries.addAll(data.entries)
            categories.clear(); categories.addAll(data.categories)
            saveFoods(); saveMeals(); saveEntries(); saveCategories()
            updateNextIds()
            true
        } catch (e: Exception) {
            Log.e("NutritionViewModel", "Import failed", e)
            false
        }
    }

    fun getBackupJson(): String {
        val data = BackupData(
            foods = foods.toList(),
            meals = meals.toList(),
            categories = categories.toList(),
            entries = allEntries.toList()
        )
        return json.encodeToString(data)
    }

    fun getCatalogJson(): String {
        val data = BackupData(
            foods = foods.toList(),
            meals = meals.toList(),
            categories = categories.toList()
        )
        return json.encodeToString(data)
    }

    private fun saveFoods() = viewModelScope.launch(Dispatchers.IO) {
        val data = foods.toList()
        prefs.edit().putString("foods", json.encodeToString(data)).apply()
    }

    private fun saveMeals() = viewModelScope.launch(Dispatchers.IO) {
        val data = meals.toList()
        prefs.edit().putString("meals", json.encodeToString(data)).apply()
    }

    private fun saveEntries() = viewModelScope.launch(Dispatchers.IO) {
        val data = allEntries.toList()
        prefs.edit().putString("entries", json.encodeToString(data)).apply()
    }

    private fun saveCategories() = viewModelScope.launch(Dispatchers.IO) {
        val data = categories.toList()
        prefs.edit().putString("categories", json.encodeToString(data)).apply()
    }

    private fun savePlannedMealPool() = viewModelScope.launch(Dispatchers.IO) {
        val data = plannedMealPool.toList()
        prefs.edit().putString("planned_meal_pool", json.encodeToString(data)).apply()
    }

    private fun savePlannedEntries() = viewModelScope.launch(Dispatchers.IO) {
        val data = plannedEntries.toList()
        prefs.edit().putString("planned_entries", json.encodeToString(data)).apply()
    }

    private fun saveShoppingList() = viewModelScope.launch(Dispatchers.IO) {
        val data = shoppingList.toList()
        prefs.edit().putString("shopping_list", json.encodeToString(data)).apply()
    }

    private fun saveWeightHistory() = viewModelScope.launch(Dispatchers.IO) {
        val data = weightHistory.toList()
        prefs.edit().putString("weight_history", json.encodeToString(data)).apply()
    }

    private fun saveVerifications() = viewModelScope.launch(Dispatchers.IO) {
        val data = dayVerifications.toMap()
        prefs.edit().putString("verifications", json.encodeToString(data)).apply()
    }

    private fun saveActivity() = viewModelScope.launch(Dispatchers.IO) {
        val steps = dailySteps.toMap()
        val cals = dailyTotalCalories.toMap()
        val sessions = dailyExerciseSessions.toMap()
        prefs.edit().let {
            it.putString("daily_steps", json.encodeToString(steps))
            it.putString("daily_total_kcal", json.encodeToString(cals))
            it.putString("daily_sessions", json.encodeToString(sessions))
            it.apply()
        }
    }

    private fun loadLocalState() {
        try {
            prefs.getString("foods", null)?.let { foods.addAll(json.decodeFromString<List<FoodItemEntity>>(it)) }
            prefs.getString("meals", null)?.let { meals.addAll(json.decodeFromString<List<MealEntity>>(it)) }
            prefs.getString("entries", null)?.let { allEntries.addAll(json.decodeFromString<List<FoodEntryEntity>>(it)) }
            prefs.getString("categories", null)?.let { categories.addAll(json.decodeFromString<List<String>>(it)) }
            prefs.getString("planned_entries", null)?.let { plannedEntries.addAll(json.decodeFromString<List<FoodEntryEntity>>(it)) }
            prefs.getString("planned_meal_pool", null)?.let { plannedMealPool.addAll(json.decodeFromString<List<PlannedMealPoolEntity>>(it)) }
            prefs.getString("shopping_list", null)?.let { shoppingList.addAll(json.decodeFromString<List<ShoppingItem>>(it)) }
            prefs.getString("weight_history", null)?.let { weightHistory.addAll(json.decodeFromString<List<WeightEntry>>(it)) }
            prefs.getString("verifications", null)?.let { dayVerifications.putAll(json.decodeFromString<Map<String, Boolean>>(it)) }
            prefs.getString("daily_steps", null)?.let { dailySteps.putAll(json.decodeFromString<Map<String, Int>>(it)) }
            prefs.getString("daily_total_kcal", null)?.let { dailyTotalCalories.putAll(json.decodeFromString<Map<String, Double>>(it)) }
            prefs.getString("daily_sessions", null)?.let { dailyExerciseSessions.putAll(json.decodeFromString<Map<String, List<ExerciseSessionInfo>>>(it)) }
            updateNextIds()
        } catch (e: Exception) {
            Log.e("NutritionViewModel", "Error loading local state", e)
        }
    }

    private fun updateNextIds() {
        if (foods.isNotEmpty()) nextFoodId = (foods.maxOf { it.id }) + 1
        if (meals.isNotEmpty()) nextMealId = (meals.maxOf { it.id }) + 1
        if (allEntries.isNotEmpty()) nextEntryId = (allEntries.maxOf { it.id }) + 1
    }

    fun repairAndWipeLocalCache() {
        if (isRepairing) return
        foods.clear(); meals.clear(); allEntries.clear(); plannedEntries.clear(); shoppingList.clear()
        saveFoods(); saveMeals(); saveEntries(); savePlannedEntries(); saveShoppingList()
        setupFirebaseSync()
        triggerAutoRepair()
    }

    fun forceDeepRepair() {
        triggerAutoRepair()
    }

    private fun createDefaultFoods() {
        addFood("Apfel", 52.0, 0.3, 14.0, 10.0, 0.2, 0.0, 0.0, "g", listOf(FoodPortionEntity(0, "Stück", 150.0)), emptyList(), category = "Obst")
    }

    private fun createDefaultCategories() {
        categories.addAll(listOf("Obst", "Gemüse", "Fleisch", "Milchprodukte", "Getreide", "Sonstiges"))
        saveCategories()
    }
}
