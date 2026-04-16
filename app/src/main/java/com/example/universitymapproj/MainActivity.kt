package com.example.universitymapproj

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.universitymapproj.models.*
import com.example.universitymapproj.routing.*
import com.example.universitymapproj.clustering.runKMeans
import com.example.universitymapproj.serialization.*
import com.example.universitymapproj.ui.*
import com.example.universitymapproj.NeuralNetwork.*
import java.io.File
import java.time.LocalTime


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var appMode by remember { mutableStateOf<AppMode?>(null) }

            if (appMode == null) {
                ModeSelectionScreen { selected ->
                    appMode = selected
                }
            } else {
                MainScreen(
                    appMode = appMode!!,
                    onChangeMode = { appMode = null }
                )
            }
        }
    }
}

@Composable
fun ModeSelectionScreen(onModeSelected: (AppMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Выберите режим", fontSize = 22.sp)

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = { onModeSelected(AppMode.USER) }) {
            Text("Пользователь")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = { onModeSelected(AppMode.DEVELOPER) }) {
            Text("Разработчик")
        }
    }
}

@Composable
fun MainScreen(
    appMode: AppMode,
    onChangeMode: () -> Unit
) {
    val context = LocalContext.current
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

    val nn = remember { NeuralNetwork() }
    val modelFile = remember { File(context.filesDir, "model.dat") }

    LaunchedEffect(Unit) {
        if (modelFile.exists()) {
            nn.loadModel(modelFile)
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

    val foodPlaces = remember {
        val foodData = MapConfig.readFromAssets(context, "saved_food_places.txt")

        val initialData = if (foodData.isNotBlank()) {
            foodData
        } else {
            MapConfig.DEFAULT_FOOD
        }

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

    var manualUserCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val ratingsFile = File(context.filesDir, "ratings.txt")
    val ratings = remember {
        mutableStateListOf<PlaceRating>().apply {
            addAll(loadRatingsFromFile(ratingsFile))
        }
    }
    val saveRatings = {
        saveRatingsToFile(ratingsFile, ratings)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Header()

        UniversityMap(
            ratings = ratings,
            editMode = appMode == AppMode.DEVELOPER &&
                    editEnabled &&
                    !clusteringMode &&
                    !foodRouteMode &&
                    !landmarkRouteMode,
            modifier = Modifier.weight(5f),
            showGrid = gridVisible,
            isErasing = isErasing,
            grid = mapGrid.value,
            foodPlaces = foodPlaces,
            landmarks = landmarks,
            selectedLandmarkIds = selectedLandmarkIds,
            landmarkRoutePath = landmarkRoutePath,
            orderedLandmarks = orderedLandmarks,
            foodEditMode = if (clusteringMode || foodRouteMode || landmarkRouteMode) FoodEditMode.NONE else foodEditMode,
            clusteringMode = clusteringMode,
            selectedClusterPoints = selectedClusterPoints,
            clusteredPoints = clusteredPoints,
            externalPath = geneticFullPath,
            routeFoodPlaces = geneticRoutePlaces,
            userCell = manualUserCell,
            onGridChanged = { updateTick++ },
            onAddFoodPlace = { row, col ->
                if (foodPlaces.none { it.row == row && it.col == col }) {
                    foodPlaces.add(
                        Obshepit(
                            row = row,
                            col = col,
                            title = "Новый общепит",
                            description = "Описание",
                            workingHours = "08:00-18:00",
                            type = "Кафе",
                            dishes = listOf("Блюдо")
                        )
                    )
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
            onFoodPlaceSelected = { foodPlace ->
                selectedFoodPlace = foodPlace
            }
        )

        Controls(
            appMode = appMode,
            ratings = ratings,
            neuralNetwork = nn,
            onSaveRatings = saveRatings,
            selectedFoodPlace = selectedFoodPlace,
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
                if (!clusteringMode && !foodRouteMode && !landmarkRouteMode) editEnabled =
                    !editEnabled
            },
            isErasing = isErasing,
            onToggleErasing = { isErasing = !isErasing },
            onFoodAddClick = {
                if (!clusteringMode && !foodRouteMode && !landmarkRouteMode) {
                    foodEditMode =
                        if (foodEditMode == FoodEditMode.ADD) FoodEditMode.NONE else FoodEditMode.ADD
                }
            },
            onFoodDeleteClick = {
                if (!clusteringMode && !foodRouteMode && !landmarkRouteMode) {
                    foodEditMode =
                        if (foodEditMode == FoodEditMode.DELETE) FoodEditMode.NONE else FoodEditMode.DELETE
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
                clusteringMode = !clusteringMode
                if (!clusteringMode) {
                    selectedClusterPoints.clear()
                    clusteredPoints.clear()
                } else {
                    foodEditMode = FoodEditMode.NONE
                    editEnabled = false
                    foodRouteMode = false
                    landmarkRouteMode = false
                }
            },
            onRunClustering = {
                val k = clusterCountText.toIntOrNull() ?: 0
                if (selectedClusterPoints.isNotEmpty() && k > 0) {
                    clusteredPoints.clear()
                    clusteredPoints.addAll(runKMeans(selectedClusterPoints.toList(), k))
                }
            },
            onFoodRouteToggle = {
                foodRouteMode = !foodRouteMode
                if (foodRouteMode) {
                    clusteringMode = false
                    editEnabled = false
                    foodEditMode = FoodEditMode.NONE
                    landmarkRouteMode = false
                } else {
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

                if (userRow !in 0 until MapConfig.ROWS || userCol !in 0 until MapConfig.COLS) {
                    routeInfoText = "Координаты пользователя вне карты"
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

                val startTime = java.time.LocalTime.now()

                val bestRoute = buildOptimalFoodRouteGenetic(
                    userLocation = UserLocation(userRow, userCol),
                    requiredDishes = requiredDishes,
                    foodPlaces = foodPlaces.toList(),
                    grid = mapGrid.value,
                    populationSize = 20,
                    generations = 100,
                    mutationChance = 0.15,
                    startTime = startTime,
                    speedMetersPerSecond = 5000.0 / 3600.0, // 5 км/ч
                    stayMinutes = 30
                )

                if (bestRoute.isEmpty()) {
                    geneticRoutePlaces = emptyList()
                    geneticFullPath = emptyList()
                    routeInfoText = "Маршрут не найден"
                    return@Controls
                }

                val fullPath = buildFullPathThroughRoute(
                    userLocation = UserLocation(userRow, userCol),
                    route = bestRoute,
                    grid = mapGrid.value
                )

                geneticRoutePlaces = bestRoute
                geneticFullPath = fullPath

                val routeTitles = bestRoute.joinToString(" -> ") { it.title }
                routeInfoText =
                    "Найдено: ${bestRoute.size} точек, длина пути: ${fullPath.size} клеток\n$routeTitles"
            },
            onClearFoodRoute = {
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
                } else {
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
                val userRow = userRowText.toIntOrNull()
                val userCol = userColText.toIntOrNull()

                if (userRow == null || userCol == null) {
                    landmarkRouteInfo = "Некорректные координаты пользователя"
                    return@Controls
                }

                if (userRow !in 0 until MapConfig.ROWS || userCol !in 0 until MapConfig.COLS) {
                    landmarkRouteInfo = "Координаты пользователя вне карты"
                    return@Controls
                }

                if (!mapGrid.value[userRow][userCol]) {
                    landmarkRouteInfo = "Пользователь стоит на непроходимой клетке"
                    return@Controls
                }

                manualUserCell = userRow to userCol

                val selected = landmarks.filter { it.id in selectedLandmarkIds }
                if (selected.isEmpty()) {
                    landmarkRouteInfo = "Выберите хотя бы одну достопримечательность"
                    return@Controls
                }

                val ordered = buildLandmarkVisitOrder(selected)
                val fullPath = buildFullPathThroughPoints(
                    start = userRow to userCol,
                    orderedPoints = ordered.map { it.row to it.col },
                    grid = mapGrid.value
                )

                if (fullPath.isEmpty()) {
                    landmarkRoutePath = emptyList()
                    orderedLandmarks = emptyList()
                    landmarkRouteInfo = "Не удалось построить маршрут"
                    return@Controls
                }

                orderedLandmarks = ordered
                landmarkRoutePath = fullPath
                landmarkRouteInfo =
                    "Маршрут построен: ${ordered.joinToString(" -> ") { it.title }}\nДлина: ${fullPath.size} клеток"
            },
            onClearLandmarkRoute = {
                landmarkRoutePath = emptyList()
                orderedLandmarks = emptyList()
                landmarkRouteInfo = ""
            }
        )
    }
}