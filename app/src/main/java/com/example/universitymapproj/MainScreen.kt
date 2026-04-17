package com.example.universitymapproj

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.universitymapproj.NeuralNetwork.NeuralNetwork
import com.example.universitymapproj.NeuralNetwork.TrainingSample
import com.example.universitymapproj.clustering.runKMeans
import com.example.universitymapproj.models.ClusterPoint
import com.example.universitymapproj.models.ClusteredPoint
import com.example.universitymapproj.models.FoodEditMode
import com.example.universitymapproj.models.Landmark
import com.example.universitymapproj.models.Obshepit
import com.example.universitymapproj.models.PlaceRating
import com.example.universitymapproj.models.UserLocation
import com.example.universitymapproj.pathfinding.AStarPathfinder
import com.example.universitymapproj.pathfinding.AStarVisualState
import com.example.universitymapproj.pathfinding.geoToGrid
import com.example.universitymapproj.routing.GeneticFoodRouteOptimizer
import com.example.universitymapproj.routing.buildFullPathThroughPoints
import com.example.universitymapproj.routing.buildFullPathThroughRoute
import com.example.universitymapproj.routing.buildLandmarkVisitOrder
import com.example.universitymapproj.routing.loadRatingsFromFile
import com.example.universitymapproj.routing.saveRatingsToFile
import com.example.universitymapproj.serialization.exportFoodPlacesToString
import com.example.universitymapproj.serialization.exportGridToString
import com.example.universitymapproj.serialization.importFoodPlacesFromString
import com.example.universitymapproj.serialization.importGridFromString
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

@SuppressLint("MissingPermission")
@Composable
fun MainScreen(
    appMode: AppMode,
    onChangeMode: () -> Unit,
    externalUserCell: Pair<Int, Int>? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val coroutineScope = rememberCoroutineScope()


    var clusteringJob by remember { mutableStateOf<Job?>(null) }
    var foodRouteJob by remember { mutableStateOf<Job?>(null) }
    var landmarkRouteJob by remember { mutableStateOf<Job?>(null) }

    var selectedFoodPlace by remember { mutableStateOf<Obshepit?>(null) }

    var gridVisible by remember { mutableStateOf(false) }
    var editEnabled by remember { mutableStateOf(false) }
    var foodEditMode by remember { mutableStateOf(FoodEditMode.NONE) }
    var isErasing by remember { mutableStateOf(false) }

    val neuralNetwork = remember { NeuralNetwork() }

    var clusteringMode by remember { mutableStateOf(false) }
    var clusterCountText by remember { mutableStateOf("3") }
    val selectedClusterPoints = remember { mutableStateListOf<ClusterPoint>() }
    val clusteredPoints = remember { mutableStateListOf<ClusteredPoint>() }

    var pendingNewFoodPlace by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showNewFoodDialog by remember { mutableStateOf(false) }

    // Просто хардкодные значения по умолчанию, они будут заменены при сохранении
    var tempTitle by remember { mutableStateOf("Новый общепит") }
    var tempDescription by remember { mutableStateOf("Описание") }
    var tempWorkingHours by remember { mutableStateOf("08:00-18:00") }
    var tempType by remember { mutableStateOf("Кафе") }
    var tempDishesText by remember { mutableStateOf("Блюдо1, Блюдо2") }

    val modelFile = remember { File(context.filesDir, "model.dat") }
    var isTraining by remember { mutableStateOf(false) }
    var trainingProgress by remember { mutableStateOf("") }
    var collectingMode by remember { mutableStateOf(false) }
    var currentLabel by remember { mutableStateOf(1) }

    var astarMode by remember { mutableStateOf(false) }
    var obstacleDrawingEnabled by remember { mutableStateOf(false) }
    var selectingStart by remember { mutableStateOf(false) }
    var selectingEnd by remember { mutableStateOf(false) }
    var astarStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var astarEnd by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var astarVisualizationState by remember { mutableStateOf(AStarVisualState()) }
    var isAstarRunning by remember { mutableStateOf(false) }
    var astarAnimationJob by remember { mutableStateOf<Job?>(null) }

    val trainingSamples = remember { mutableStateListOf<TrainingSample>() }

    LaunchedEffect(Unit) {
        neuralNetwork.loadIfExists(modelFile)
    }

    LaunchedEffect(Unit) {
        if (modelFile.exists()) {
            try {
                neuralNetwork.loadModel(modelFile)
                trainingProgress = "Модель загружена"
            } catch (e: Exception) {
                trainingProgress = "Ошибка загрузки модели"
            }
        } else {
            trainingProgress = "Файл модели не найден"
        }
    }

    val mapGrid = remember {
        val fileContent = try {
            context.assets.open("saved_map_grid.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.e("FILE_ERROR", "Файл не найден в assets, используем SAVED_GRID")
            MapConfig.SAVED_GRID
        }

        val initialGrid = if (fileContent.isNotEmpty()) {
            importGridFromString(fileContent)
        } else {
            Array(MapConfig.ROWS) { BooleanArray(MapConfig.COLS) { false } }
        }
        mutableStateOf(initialGrid)
    }

    fun startAStarVisualization() {
        val start = astarStart ?: return
        val end = astarEnd ?: return

        if (!mapGrid.value[start.first][start.second] || !mapGrid.value[end.first][end.second]) return

        isAstarRunning = true

        astarAnimationJob?.cancel()
        astarAnimationJob = coroutineScope.launch(Dispatchers.Default) {
            val pathfinder = AStarPathfinder(mapGrid.value)

            pathfinder.findPathStepByStep(
                start.first, start.second,
                end.first, end.second
            ) { state ->
                coroutineScope.launch {
                    astarVisualizationState = state
                }
            }

            coroutineScope.launch {
                isAstarRunning = false
            }
        }
    }

    val foodPlaces = remember {
        val foodData = MapConfig.readFromAssets(context, "saved_food_places.txt")
        val initialData = if (foodData.isNotBlank()) foodData else MapConfig.DEFAULT_FOOD

        mutableStateListOf<Obshepit>().apply {
            addAll(importFoodPlacesFromString(initialData))
        }
    }

    val landmarks = remember { LandmarkConfig.DEFAULT_LANDMARKS }
    val selectedLandmarkIds = remember { mutableStateListOf<Int>() }

    var updateTick by remember { mutableStateOf(0) }

    var foodRouteMode by remember { mutableStateOf(false) }
    var userRowText by remember { mutableStateOf("80") }
    var userColText by remember { mutableStateOf("60") }
    var dishesText by remember { mutableStateOf("Борщ,Суши,Десерт") }

    var geneticRoutePlaces by remember { mutableStateOf<List<Obshepit>>(emptyList()) }
    var geneticFullPath by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var routeInfoText by remember { mutableStateOf("") }

    var landmarkRouteMode by remember { mutableStateOf(false) }
    var landmarkRoutePath by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var orderedLandmarks by remember { mutableStateOf<List<Landmark>>(emptyList()) }
    var landmarkRouteInfo by remember { mutableStateOf("") }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var gpsUserCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val location = fusedLocationClient.lastLocation.await()
                location?.let { gpsUserCell = geoToGrid(it.latitude, it.longitude) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(2000)
        }
    }

    var manualUserCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val finalUserCell = manualUserCell ?: gpsUserCell
    val effectiveUserCell = externalUserCell ?: manualUserCell
    val ratingsFile = File(context.filesDir, "ratings.txt")
    val ratings = remember {
        mutableStateListOf<PlaceRating>().apply { addAll(loadRatingsFromFile(ratingsFile)) }
    }
    val saveRatings = { saveRatingsToFile(ratingsFile, ratings) }

    if (showNewFoodDialog) {
        Dialog(
            onDismissRequest = {
                showNewFoodDialog = false
                pendingNewFoodPlace = null
            }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dialog_add_food_title),
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = tempTitle,
                        onValueChange = { tempTitle = it },
                        label = { Text(stringResource(R.string.field_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempDescription,
                        onValueChange = { tempDescription = it },
                        label = { Text(stringResource(R.string.field_description)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempWorkingHours,
                        onValueChange = { tempWorkingHours = it },
                        label = { Text(stringResource(R.string.field_working_hours)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempType,
                        onValueChange = { tempType = it },
                        label = { Text(stringResource(R.string.field_place_type)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempDishesText,
                        onValueChange = { tempDishesText = it },
                        label = { Text(stringResource(R.string.field_dishes_csv)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                showNewFoodDialog = false
                                pendingNewFoodPlace = null
                            }
                        ) { Text(stringResource(R.string.btn_cancel)) }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                pendingNewFoodPlace?.let { (row, col) ->
                                    val dishes = tempDishesText
                                        .split(",", ";", "\n")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() }

                                    foodPlaces.add(
                                        Obshepit(
                                            row = row,
                                            col = col,
                                            title = tempTitle.ifBlank { "Новый общепит" },
                                            description = tempDescription.ifBlank { "Описание" },
                                            workingHours = tempWorkingHours.ifBlank { "08:00-18:00" },
                                            type = tempType.ifBlank { "Кафе" },
                                            dishes = dishes.ifEmpty { listOf("Блюдо") }
                                        )
                                    )
                                }

                                tempTitle = "Новый общепит"
                                tempDescription = "Описание"
                                tempWorkingHours = "08:00-18:00"
                                tempType = "Кафе"
                                tempDishesText = "Блюдо1, Блюдо2"

                                showNewFoodDialog = false
                                pendingNewFoodPlace = null
                            }
                        ) { Text(stringResource(R.string.btn_add)) }
                    }
                }
            }
        }
    }

    fun toggleAstarMode() {
        astarMode = !astarMode
        if (astarMode) {
            clusteringMode = false
            foodRouteMode = false
            landmarkRouteMode = false

            clusteringJob?.cancel()
            foodRouteJob?.cancel()
            landmarkRouteJob?.cancel()
            clusteringJob = null
            foodRouteJob = null
            landmarkRouteJob = null

            editEnabled = false
            foodEditMode = FoodEditMode.NONE
            obstacleDrawingEnabled = false
            selectingStart = false
            selectingEnd = false
        } else {
            astarVisualizationState = AStarVisualState()
            astarStart = null
            astarEnd = null
            isAstarRunning = false
            astarAnimationJob?.cancel()
            obstacleDrawingEnabled = false
            selectingStart = false
            selectingEnd = false
        }
    }

    if (isLandscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Header()

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                UniversityMap(
                    modifier = Modifier.weight(1f),
                    ratings = ratings,
                    showGrid = gridVisible,
                    editMode = appMode == AppMode.DEVELOPER &&
                            editEnabled &&
                            !clusteringMode &&
                            !foodRouteMode &&
                            !landmarkRouteMode &&
                            !astarMode,
                    isErasing = isErasing,
                    grid = mapGrid.value,
                    foodPlaces = foodPlaces,
                    landmarks = landmarks,
                    selectedLandmarkIds = selectedLandmarkIds,
                    landmarkRoutePath = landmarkRoutePath,
                    orderedLandmarks = orderedLandmarks,
                    foodEditMode = if (clusteringMode || foodRouteMode || landmarkRouteMode || astarMode) FoodEditMode.NONE else foodEditMode,
                    clusteringMode = clusteringMode,
                    selectedClusterPoints = selectedClusterPoints,
                    clusteredPoints = clusteredPoints,
                    externalPath = geneticFullPath,
                    routeFoodPlaces = geneticRoutePlaces,
                    userCell = finalUserCell,
                    onGridChanged = { updateTick++ },
                    onAddFoodPlace = { row, col ->
                        if (foodPlaces.none { it.row == row && it.col == col }) {
                            pendingNewFoodPlace = row to col
                            showNewFoodDialog = true
                        }
                    },
                    onDeleteFoodPlace = { row, col ->
                        foodPlaces.removeAll { it.row == row && it.col == col }
                    },
                    onClusterPointToggle = { row, col ->
                        val existing = selectedClusterPoints.indexOfFirst { it.row == row && it.col == col }
                        if (existing >= 0) selectedClusterPoints.removeAt(existing)
                        else selectedClusterPoints.add(ClusterPoint(row, col))
                        clusteredPoints.clear()
                    },
                    onFoodPlaceSelected = { foodPlace -> selectedFoodPlace = foodPlace },

                    astarMode = astarMode,
                    obstacleDrawingEnabled = obstacleDrawingEnabled,
                    astarVisualState = astarVisualizationState,
                    astarStart = astarStart,
                    astarEnd = astarEnd,
                    selectingStart = selectingStart,
                    selectingEnd = selectingEnd,
                    onAstarStartSelected = { point ->
                        astarStart = point
                        selectingStart = false
                    },
                    onAstarEndSelected = { point ->
                        astarEnd = point
                        selectingEnd = false
                    },
                    onSelectingStartChanged = { selectingStart = it },
                    onSelectingEndChanged = { selectingEnd = it },
                    onObstacleToggled = { _, _ -> }
                )

                Controls(
                    context = context,
                    appMode = appMode,
                    isTraining = isTraining,
                    trainingProgress = trainingProgress,
                    collectingMode = collectingMode,
                    currentLabel = currentLabel,
                    trainingSamples = trainingSamples,
                    modelFile = modelFile,
                    onTrainingStart = { isTraining = true },
                    onTrainingEnd = { isTraining = false },
                    onProgressUpdate = { progress -> trainingProgress = progress },
                    onCollectingModeToggle = { collectingMode = !collectingMode },
                    onLabelChange = { label -> currentLabel = label },
                    onSampleAdd = { sample -> trainingSamples.add(sample) },
                    neuralNetwork = neuralNetwork,
                    ratings = ratings,
                    onSaveRatings = saveRatings,
                    selectedFoodPlace = selectedFoodPlace,

                    foodPlaces = foodPlaces,
                    userCell = effectiveUserCell,

                    onChangeMode = onChangeMode,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(240.dp),
                    showGrid = gridVisible,
                    editMode = editEnabled,
                    foodEditMode = foodEditMode,
                    clusteringMode = clusteringMode,
                    foodRouteMode = foodRouteMode,
                    landmarkRouteMode = landmarkRouteMode,
                    clusterCountText = clusterCountText,
                    selectedPointsCount = selectedClusterPoints.size,
                    userRowText = userRowText,
                    userColText = userColText,
                    dishesText = dishesText,
                    routeInfoText = routeInfoText,
                    landmarks = landmarks,
                    selectedLandmarkIds = selectedLandmarkIds,
                    landmarkRouteInfo = landmarkRouteInfo,
                    onClusterCountChange = { clusterCountText = it.filter { ch -> ch.isDigit() } },
                    onUserRowChange = {
                        userRowText = it.filter { ch -> ch.isDigit() }
                        val r = userRowText.toIntOrNull()
                        val c = userColText.toIntOrNull()
                        manualUserCell =
                            if (r != null && c != null && r in 0 until MapConfig.ROWS && c in 0 until MapConfig.COLS) r to c else null
                    },
                    onUserColChange = {
                        userColText = it.filter { ch -> ch.isDigit() }
                        val r = userRowText.toIntOrNull()
                        val c = userColText.toIntOrNull()
                        manualUserCell =
                            if (r != null && c != null && r in 0 until MapConfig.ROWS && c in 0 until MapConfig.COLS) r to c else null
                    },
                    onDishesChange = { dishesText = it },
                    onGridClick = { gridVisible = !gridVisible },
                    onEditClick = {
                        if (!clusteringMode && !foodRouteMode && !landmarkRouteMode && !astarMode) {
                            editEnabled = !editEnabled
                        }
                    },
                    isErasing = isErasing,
                    onToggleErasing = { isErasing = !isErasing },
                    onFoodAddClick = {
                        if (!clusteringMode && !foodRouteMode && !landmarkRouteMode && !astarMode) {
                            foodEditMode = if (foodEditMode == FoodEditMode.ADD) FoodEditMode.NONE else FoodEditMode.ADD
                        }
                    },
                    onFoodDeleteClick = {
                        if (!clusteringMode && !foodRouteMode && !landmarkRouteMode && !astarMode) {
                            foodEditMode = if (foodEditMode == FoodEditMode.DELETE) FoodEditMode.NONE else FoodEditMode.DELETE
                        }
                    },
                    onExportClick = {
                        val gridResult = exportGridToString(mapGrid.value)
                        val foodResult = exportFoodPlacesToString(foodPlaces)
                        android.util.Log.d("MAP_DATA_LENGTH", gridResult.length.toString())
                        MapConfig.logLongString("MAP_DATA", gridResult)
                        android.util.Log.d("FOOD_PLACES", foodResult)
                    },

                    onClusteringToggle = {
                        val turningOn = !clusteringMode
                        clusteringMode = turningOn

                        if (turningOn) {
                            foodEditMode = FoodEditMode.NONE
                            editEnabled = false
                            foodRouteMode = false
                            landmarkRouteMode = false

                        } else {
                            selectedClusterPoints.clear()
                            clusteredPoints.clear()
                        }
                    },
                    onRunClustering = {
                        val k = clusterCountText.toIntOrNull() ?: 0
                        if (selectedClusterPoints.isEmpty() || k <= 0) return@Controls

                        val ptsSnapshot = selectedClusterPoints.toList()

                        clusteringJob?.cancel()
                        clusteringJob = coroutineScope.launch {
                            val result = withContext(Dispatchers.Default) {
                                runKMeans(ptsSnapshot, k)
                            }
                            clusteredPoints.clear()
                            clusteredPoints.addAll(result)
                        }
                    },

                    onFoodRouteToggle = {
                        foodRouteMode = !foodRouteMode
                        if (foodRouteMode) {
                            clusteringMode = false
                            editEnabled = false
                            foodEditMode = FoodEditMode.NONE
                            landmarkRouteMode = false
                            astarMode = false
                        } else {
                            foodRouteJob?.cancel()
                            foodRouteJob = null
                            geneticRoutePlaces = emptyList()
                            geneticFullPath = emptyList()
                            routeInfoText = ""
                        }
                    },
                    onRunFoodRoute = {
                        val userRow = userRowText.toIntOrNull()
                        val userCol = userColText.toIntOrNull()

                        if (userRow == null || userCol == null) {
                            routeInfoText = "Некорректные координаты пользователя"
                            return@Controls
                        }

                        if (!mapGrid.value[userRow][userCol]) {
                            routeInfoText = "Пользователь стоит на непроходимой клетке"
                            return@Controls
                        }

                        manualUserCell = userRow to userCol

                        val requiredDishes = dishesText
                            .split(",", ";", "\n")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()

                        if (requiredDishes.isEmpty()) {
                            routeInfoText = "Введите хотя бы одно блюдо"
                            return@Controls
                        }

                        routeInfoText = "Считаю маршрут (генетический алгоритм)…"

                        val gridSnapshot = mapGrid.value
                        val foodSnapshot = foodPlaces.toList()
                        val userLoc = UserLocation(userRow, userCol)

                        foodRouteJob?.cancel()
                        foodRouteJob = coroutineScope.launch {
                            val (bestRoute, fullPath) = withContext(Dispatchers.Default) {
                                val startTime = java.time.LocalTime.now()
                                val optimizer = GeneticFoodRouteOptimizer()

                                val route = optimizer.findOptimalRoute(
                                    userLocation = userLoc,
                                    requiredDishes = requiredDishes,
                                    foodPlaces = foodSnapshot,
                                    grid = gridSnapshot,
                                    populationSize = 20,
                                    generations = 100,
                                    mutationChance = 0.15,
                                    startTime = startTime,
                                    speedMetersPerSecond = 5000.0 / 3600.0,
                                    stayMinutes = 30
                                )

                                if (route.isEmpty()) {
                                    route to emptyList()
                                } else {
                                    val path = buildFullPathThroughRoute(
                                        userLocation = userLoc,
                                        route = route,
                                        grid = gridSnapshot
                                    )
                                    route to path
                                }
                            }

                            if (bestRoute.isEmpty()) {
                                geneticRoutePlaces = emptyList()
                                geneticFullPath = emptyList()
                                routeInfoText = "Маршрут не найден"
                                return@launch
                            }

                            geneticRoutePlaces = bestRoute
                            geneticFullPath = fullPath

                            val routeTitles = bestRoute.joinToString(" -> ") { it.title }
                            routeInfoText = "Маршрут построен: $routeTitles\nДлина: ${fullPath.size} клеток"
                        }
                    },
                    onClearFoodRoute = {
                        foodRouteJob?.cancel()
                        foodRouteJob = null
                        geneticRoutePlaces = emptyList()
                        geneticFullPath = emptyList()
                        routeInfoText = ""
                    },

                    onLandmarkRouteToggle = {
                        landmarkRouteMode = !landmarkRouteMode
                        if (landmarkRouteMode) {
                            clusteringMode = false
                            foodRouteMode = false
                            editEnabled = false
                            foodEditMode = FoodEditMode.NONE
                            astarMode = false
                        } else {
                            landmarkRouteJob?.cancel()
                            landmarkRouteJob = null
                            landmarkRoutePath = emptyList()
                            orderedLandmarks = emptyList()
                            landmarkRouteInfo = ""
                            selectedLandmarkIds.clear()
                        }
                    },
                    onLandmarkSelectionToggle = { id ->
                        if (id in selectedLandmarkIds) selectedLandmarkIds.remove(id)
                        else selectedLandmarkIds.add(id)
                    },
                    onRunLandmarkRoute = {
                        val userCellNow = finalUserCell
                        if (userCellNow == null) {
                            landmarkRouteInfo = "Не удалось определить позицию пользователя"
                            return@Controls
                        }

                        val (uRow, uCol) = userCellNow

                        if (!mapGrid.value[uRow][uCol]) {
                            landmarkRouteInfo = "Пользователь стоит на непроходимой клетке"
                            return@Controls
                        }

                        manualUserCell = uRow to uCol

                        val selected = landmarks.filter { it.id in selectedLandmarkIds }
                        if (selected.isEmpty()) {
                            landmarkRouteInfo = "Выберите хотя бы одну достопримечательность"
                            return@Controls
                        }

                        landmarkRouteInfo = "Считаю маршрут (муравьиный + A*)…"

                        val start = uRow to uCol
                        val gridSnapshot = mapGrid.value

                        landmarkRouteJob?.cancel()
                        landmarkRouteJob = coroutineScope.launch {
                            val (ordered, fullPath) = withContext(Dispatchers.Default) {
                                val ord = buildLandmarkVisitOrder(selected)
                                val fp = buildFullPathThroughPoints(
                                    start = start,
                                    orderedPoints = ord.map { it.row to it.col },
                                    grid = gridSnapshot
                                )
                                ord to fp
                            }

                            if (fullPath.isEmpty()) {
                                landmarkRoutePath = emptyList()
                                orderedLandmarks = emptyList()
                                landmarkRouteInfo = "Не удалось построить маршрут"
                                return@launch
                            }

                            orderedLandmarks = ordered
                            landmarkRoutePath = fullPath
                            landmarkRouteInfo = "Маршрут построен: ${ordered.joinToString(" -> ") { it.title }}\nДлина: ${fullPath.size} клеток"
                        }
                    },
                    onClearLandmarkRoute = {
                        landmarkRouteJob?.cancel()
                        landmarkRouteJob = null
                        landmarkRoutePath = emptyList()
                        orderedLandmarks = emptyList()
                        landmarkRouteInfo = ""
                    },

                    isLandscape = isLandscape,
                    astarMode = astarMode,
                    obstacleDrawingEnabled = obstacleDrawingEnabled,
                    astarStart = astarStart,
                    astarEnd = astarEnd,
                    selectingStart = selectingStart,
                    selectingEnd = selectingEnd,
                    isAstarRunning = isAstarRunning,
                    astarVisualizationState = astarVisualizationState,
                    onAstarModeToggle = { toggleAstarMode() },
                    onObstacleDrawingToggle = {
                        obstacleDrawingEnabled = !obstacleDrawingEnabled
                        selectingStart = false
                        selectingEnd = false
                    },
                    onSelectStartClick = {
                        selectingStart = true
                        selectingEnd = false
                        obstacleDrawingEnabled = false
                    },
                    onSelectEndClick = {
                        selectingEnd = true
                        selectingStart = false
                        obstacleDrawingEnabled = false
                    },
                    onRunAstar = { startAStarVisualization() },
                    onStopAstar = {
                        astarAnimationJob?.cancel()
                        isAstarRunning = false
                    },
                    onClearAstar = {
                        astarVisualizationState = AStarVisualState()
                        astarStart = null
                        astarEnd = null
                        isAstarRunning = false
                        astarAnimationJob?.cancel()
                        selectingStart = false
                        selectingEnd = false
                    }
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Header()

            UniversityMap(
                ratings = ratings,
                astarMode = astarMode,
                editMode = appMode == AppMode.DEVELOPER &&
                        editEnabled &&
                        !clusteringMode &&
                        !foodRouteMode &&
                        !landmarkRouteMode &&
                        !astarMode,
                modifier = Modifier.weight(5f),
                showGrid = gridVisible,
                isErasing = isErasing,
                grid = mapGrid.value,
                foodPlaces = foodPlaces,
                landmarks = landmarks,
                selectedLandmarkIds = selectedLandmarkIds,
                landmarkRoutePath = landmarkRoutePath,
                orderedLandmarks = orderedLandmarks,
                foodEditMode = if (clusteringMode || foodRouteMode || landmarkRouteMode || astarMode) FoodEditMode.NONE else foodEditMode,
                clusteringMode = clusteringMode,
                selectedClusterPoints = selectedClusterPoints,
                clusteredPoints = clusteredPoints,
                externalPath = geneticFullPath,
                routeFoodPlaces = geneticRoutePlaces,
                userCell = finalUserCell,
                onGridChanged = { updateTick++ },
                onAddFoodPlace = { row, col ->
                    if (foodPlaces.none { it.row == row && it.col == col }) {
                        pendingNewFoodPlace = row to col
                        showNewFoodDialog = true
                    }
                },
                onDeleteFoodPlace = { row, col ->
                    foodPlaces.removeAll { it.row == row && it.col == col }
                },
                onClusterPointToggle = { row, col ->
                    val existing = selectedClusterPoints.indexOfFirst { it.row == row && it.col == col }
                    if (existing >= 0) selectedClusterPoints.removeAt(existing)
                    else selectedClusterPoints.add(ClusterPoint(row, col))
                    clusteredPoints.clear()
                },
                onFoodPlaceSelected = { foodPlace -> selectedFoodPlace = foodPlace },

                obstacleDrawingEnabled = obstacleDrawingEnabled,
                astarVisualState = astarVisualizationState,
                astarStart = astarStart,
                astarEnd = astarEnd,
                selectingStart = selectingStart,
                selectingEnd = selectingEnd,
                onAstarStartSelected = { point ->
                    astarStart = point
                    selectingStart = false
                },
                onAstarEndSelected = { point ->
                    astarEnd = point
                    selectingEnd = false
                },
                onSelectingStartChanged = { selectingStart = it },
                onSelectingEndChanged = { selectingEnd = it },
                onObstacleToggled = { _, _ -> }
            )

            Controls(
                context = context,
                appMode = appMode,
                isTraining = isTraining,
                trainingProgress = trainingProgress,
                collectingMode = collectingMode,
                currentLabel = currentLabel,
                trainingSamples = trainingSamples,
                modelFile = modelFile,
                onTrainingStart = { isTraining = true },
                onTrainingEnd = { isTraining = false },
                onProgressUpdate = { progress -> trainingProgress = progress },
                onCollectingModeToggle = { collectingMode = !collectingMode },
                onLabelChange = { label -> currentLabel = label },
                onSampleAdd = { sample -> trainingSamples.add(sample) },
                neuralNetwork = neuralNetwork,
                ratings = ratings,
                onSaveRatings = saveRatings,
                selectedFoodPlace = selectedFoodPlace,
                foodPlaces = foodPlaces,
                userCell = effectiveUserCell,

                onChangeMode = onChangeMode,
                modifier = Modifier.weight(2.8f),
                showGrid = gridVisible,
                editMode = editEnabled,
                foodEditMode = foodEditMode,
                clusteringMode = clusteringMode,
                foodRouteMode = foodRouteMode,
                landmarkRouteMode = landmarkRouteMode,
                clusterCountText = clusterCountText,
                selectedPointsCount = selectedClusterPoints.size,
                userRowText = userRowText,
                userColText = userColText,
                dishesText = dishesText,
                routeInfoText = routeInfoText,
                landmarks = landmarks,
                selectedLandmarkIds = selectedLandmarkIds,
                landmarkRouteInfo = landmarkRouteInfo,
                onClusterCountChange = { clusterCountText = it.filter { ch -> ch.isDigit() } },
                onUserRowChange = {
                    userRowText = it.filter { ch -> ch.isDigit() }
                    val r = userRowText.toIntOrNull()
                    val c = userColText.toIntOrNull()
                    manualUserCell =
                        if (r != null && c != null && r in 0 until MapConfig.ROWS && c in 0 until MapConfig.COLS) r to c else null
                },
                onUserColChange = {
                    userColText = it.filter { ch -> ch.isDigit() }
                    val r = userRowText.toIntOrNull()
                    val c = userColText.toIntOrNull()
                    manualUserCell =
                        if (r != null && c != null && r in 0 until MapConfig.ROWS && c in 0 until MapConfig.COLS) r to c else null
                },
                onDishesChange = { dishesText = it },
                onGridClick = { gridVisible = !gridVisible },
                onEditClick = {
                    if (!clusteringMode && !foodRouteMode && !landmarkRouteMode && !astarMode) {
                        editEnabled = !editEnabled
                    }
                },
                isErasing = isErasing,
                onToggleErasing = { isErasing = !isErasing },
                onFoodAddClick = {
                    if (!clusteringMode && !foodRouteMode && !landmarkRouteMode && !astarMode) {
                        foodEditMode = if (foodEditMode == FoodEditMode.ADD) FoodEditMode.NONE else FoodEditMode.ADD
                    }
                },
                onFoodDeleteClick = {
                    if (!clusteringMode && !foodRouteMode && !landmarkRouteMode && !astarMode) {
                        foodEditMode = if (foodEditMode == FoodEditMode.DELETE) FoodEditMode.NONE else FoodEditMode.DELETE
                    }
                },
                onExportClick = {
                    val gridResult = exportGridToString(mapGrid.value)
                    val foodResult = exportFoodPlacesToString(foodPlaces)
                    android.util.Log.d("MAP_DATA_LENGTH", gridResult.length.toString())
                    MapConfig.logLongString("MAP_DATA", gridResult)
                    android.util.Log.d("FOOD_PLACES", foodResult)
                },

                onClusteringToggle = {
                    val turningOn = !clusteringMode
                    clusteringMode = turningOn

                    if (turningOn) {
                        foodEditMode = FoodEditMode.NONE
                        editEnabled = false
                        foodRouteMode = false
                        landmarkRouteMode = false
                        astarMode = false
                    } else {
                        clusteringJob?.cancel()
                        clusteringJob = null
                        selectedClusterPoints.clear()
                        clusteredPoints.clear()
                    }
                },
                onRunClustering = {
                    val k = clusterCountText.toIntOrNull() ?: 0
                    if (selectedClusterPoints.isEmpty() || k <= 0) return@Controls

                    val ptsSnapshot = selectedClusterPoints.toList()

                    clusteringJob?.cancel()
                    clusteringJob = coroutineScope.launch {
                        val result = withContext(Dispatchers.Default) {
                            runKMeans(ptsSnapshot, k)
                        }
                        clusteredPoints.clear()
                        clusteredPoints.addAll(result)
                    }
                },

                onFoodRouteToggle = {
                    foodRouteMode = !foodRouteMode
                    if (foodRouteMode) {
                        clusteringMode = false
                        editEnabled = false
                        foodEditMode = FoodEditMode.NONE
                        landmarkRouteMode = false
                        astarMode = false
                    } else {
                        foodRouteJob?.cancel()
                        foodRouteJob = null
                        geneticRoutePlaces = emptyList()
                        geneticFullPath = emptyList()
                        routeInfoText = ""
                    }
                },
                onRunFoodRoute = {
                    val userCellNow = finalUserCell

                    if (userCellNow == null) {
                        routeInfoText = "Не удалось определить позицию пользователя"
                        return@Controls
                    }

                    val (userRow, userCol) = userCellNow

                    if (!mapGrid.value[userRow][userCol]) {
                        routeInfoText = "Пользователь стоит на непроходимой клетке"
                        return@Controls
                    }

                    manualUserCell = userRow to userCol

                    val requiredDishes = dishesText
                        .split(",", ";", "\n")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()

                    if (requiredDishes.isEmpty()) {
                        routeInfoText = "Введите хотя бы одно блюдо"
                        return@Controls
                    }

                    routeInfoText = "Считаю маршрут (генетический алгоритм)…"

                    val gridSnapshot = mapGrid.value
                    val foodSnapshot = foodPlaces.toList()
                    val userLoc = UserLocation(userRow, userCol)

                    foodRouteJob?.cancel()
                    foodRouteJob = coroutineScope.launch {
                        val (bestRoute, fullPath) = withContext(Dispatchers.Default) {
                            val startTime = java.time.LocalTime.now()
                            val optimizer = GeneticFoodRouteOptimizer()

                            val route = optimizer.findOptimalRoute(
                                userLocation = userLoc,
                                requiredDishes = requiredDishes,
                                foodPlaces = foodSnapshot,
                                grid = gridSnapshot,
                                populationSize = 20,
                                generations = 100,
                                mutationChance = 0.15,
                                startTime = startTime,
                                speedMetersPerSecond = 5000.0 / 3600.0,
                                stayMinutes = 30
                            )

                            if (route.isEmpty()) {
                                route to emptyList()
                            } else {
                                val path = buildFullPathThroughRoute(
                                    userLocation = userLoc,
                                    route = route,
                                    grid = gridSnapshot
                                )
                                route to path
                            }
                        }

                        if (bestRoute.isEmpty()) {
                            geneticRoutePlaces = emptyList()
                            geneticFullPath = emptyList()
                            routeInfoText = "Маршрут не найден"
                            return@launch
                        }

                        geneticRoutePlaces = bestRoute
                        geneticFullPath = fullPath

                        val routeTitles = bestRoute.joinToString(" -> ") { it.title }
                        routeInfoText = "Маршрут построен: $routeTitles\nДлина: ${fullPath.size} клеток"
                    }
                },
                onClearFoodRoute = {
                    foodRouteJob?.cancel()
                    foodRouteJob = null
                    geneticRoutePlaces = emptyList()
                    geneticFullPath = emptyList()
                    routeInfoText = ""
                },

                onLandmarkRouteToggle = {
                    landmarkRouteMode = !landmarkRouteMode
                    if (landmarkRouteMode) {
                        clusteringMode = false
                        foodRouteMode = false
                        editEnabled = false
                        foodEditMode = FoodEditMode.NONE
                        astarMode = false
                    } else {
                        landmarkRouteJob?.cancel()
                        landmarkRouteJob = null
                        landmarkRoutePath = emptyList()
                        orderedLandmarks = emptyList()
                        landmarkRouteInfo = ""
                        selectedLandmarkIds.clear()
                    }
                },
                onLandmarkSelectionToggle = { id ->
                    if (id in selectedLandmarkIds) selectedLandmarkIds.remove(id)
                    else selectedLandmarkIds.add(id)
                },
                onRunLandmarkRoute = {
                    val userCellNow = finalUserCell
                    if (userCellNow == null) {
                        landmarkRouteInfo = "Не удалось определить позицию пользователя"
                        return@Controls
                    }

                    val (uRow, uCol) = userCellNow

                    if (!mapGrid.value[uRow][uCol]) {
                        landmarkRouteInfo = "Пользователь стоит на непроходимой клетке"
                        return@Controls
                    }

                    manualUserCell = uRow to uCol

                    val selected = landmarks.filter { it.id in selectedLandmarkIds }
                    if (selected.isEmpty()) {
                        landmarkRouteInfo = "Выберите хотя бы одну достопримечательность"
                        return@Controls
                    }

                    landmarkRouteInfo = "Считаю маршрут (муравьиный + A*)…"

                    val start = uRow to uCol
                    val gridSnapshot = mapGrid.value

                    landmarkRouteJob?.cancel()
                    landmarkRouteJob = coroutineScope.launch {
                        val (ordered, fullPath) = withContext(Dispatchers.Default) {
                            val ord = buildLandmarkVisitOrder(selected)
                            val fp = buildFullPathThroughPoints(
                                start = start,
                                orderedPoints = ord.map { it.row to it.col },
                                grid = gridSnapshot
                            )
                            ord to fp
                        }

                        if (fullPath.isEmpty()) {
                            landmarkRoutePath = emptyList()
                            orderedLandmarks = emptyList()
                            landmarkRouteInfo = "Не удалось построить маршрут"
                            return@launch
                        }

                        orderedLandmarks = ordered
                        landmarkRoutePath = fullPath
                        landmarkRouteInfo = "Маршрут построен: ${ordered.joinToString(" -> ") { it.title }}\nДлина: ${fullPath.size} клеток"
                    }
                },
                onClearLandmarkRoute = {
                    landmarkRouteJob?.cancel()
                    landmarkRouteJob = null
                    landmarkRoutePath = emptyList()
                    orderedLandmarks = emptyList()
                    landmarkRouteInfo = ""
                },

                isLandscape = isLandscape,
                astarMode = astarMode,
                obstacleDrawingEnabled = obstacleDrawingEnabled,
                astarStart = astarStart,
                astarEnd = astarEnd,
                selectingStart = selectingStart,
                selectingEnd = selectingEnd,
                isAstarRunning = isAstarRunning,
                astarVisualizationState = astarVisualizationState,
                onAstarModeToggle = { toggleAstarMode() },
                onObstacleDrawingToggle = {
                    obstacleDrawingEnabled = !obstacleDrawingEnabled
                    selectingStart = false
                    selectingEnd = false
                },
                onSelectStartClick = {
                    selectingStart = true
                    selectingEnd = false
                    obstacleDrawingEnabled = false
                },
                onSelectEndClick = {
                    selectingEnd = true
                    selectingStart = false
                    obstacleDrawingEnabled = false
                },
                onRunAstar = { startAStarVisualization() },
                onStopAstar = {
                    astarAnimationJob?.cancel()
                    isAstarRunning = false
                },
                onClearAstar = {
                    astarVisualizationState = AStarVisualState()
                    astarStart = null
                    astarEnd = null
                    isAstarRunning = false
                    astarAnimationJob?.cancel()
                    selectingStart = false
                    selectingEnd = false
                }
            )
        }
    }
}