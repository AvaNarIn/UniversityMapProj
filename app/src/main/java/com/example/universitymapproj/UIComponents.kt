package com.example.universitymapproj

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.universitymapproj.NeuralNetwork.*
import com.example.universitymapproj.LunchDecisionTreeCard
import com.example.universitymapproj.models.*
import com.example.universitymapproj.pathfinding.AStarVisualState
import java.io.File

enum class AppMode {
    USER,
    DEVELOPER
}


private val CLUSTER_COLORS = listOf(
    Color(0xFFE57373),
    Color(0xFF64B5F6),
    Color(0xFF81C784),
    Color(0xFFFFB74D),
    Color(0xFFBA68C8),
    Color(0xFF4DB6AC),
    Color(0xFFAED581),
    Color(0xFFFF8A65)
)

private fun computeClusterCentroids(clustered: List<ClusteredPoint>): List<Pair<Double, Double>> {
    if (clustered.isEmpty()) return emptyList()

    return clustered
        .groupBy { it.clusterIndex }
        .toSortedMap()
        .values
        .map { pts ->
            val avgRow = pts.map { it.row.toDouble() }.average()
            val avgCol = pts.map { it.col.toDouble() }.average()
            avgRow to avgCol
        }
}

private fun buildClusterZoneIndex(centroids: List<Pair<Double, Double>>): Array<IntArray> {
    val rows = MapConfig.ROWS
    val cols = MapConfig.COLS

    return Array(rows) { r ->
        IntArray(cols) { c ->
            var best = 0
            var bestD = Double.MAX_VALUE

            for (i in centroids.indices) {
                val dr = r - centroids[i].first
                val dc = c - centroids[i].second
                val d2 = dr * dr + dc * dc
                if (d2 < bestD) {
                    bestD = d2
                    best = i
                }
            }
            best
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClusterZones(
    zoneIndex: Array<IntArray>,
    cellW: Float,
    cellH: Float,
    alphaFill: Float = 0.22f,
    drawBorders: Boolean = true
) {
    for (r in 0 until MapConfig.ROWS) {
        for (c in 0 until MapConfig.COLS) {
            val ci = zoneIndex[r][c]
            val color = CLUSTER_COLORS[ci % CLUSTER_COLORS.size].copy(alpha = alphaFill)
            drawRect(
                color = color,
                topLeft = Offset(c * cellW, r * cellH),
                size = Size(cellW, cellH)
            )
        }
    }

    if (!drawBorders) return

    val borderColor = Color.Black.copy(alpha = 0.25f)
    val stroke = 1f

    for (r in 0 until MapConfig.ROWS) {
        for (c in 0 until MapConfig.COLS) {
            val here = zoneIndex[r][c]

            if (c + 1 < MapConfig.COLS && zoneIndex[r][c + 1] != here) {
                val x = (c + 1) * cellW
                val y0 = r * cellH
                val y1 = (r + 1) * cellH
                drawLine(borderColor, Offset(x, y0), Offset(x, y1), strokeWidth = stroke)
            }

            if (r + 1 < MapConfig.ROWS && zoneIndex[r + 1][c] != here) {
                val y = (r + 1) * cellH
                val x0 = c * cellW
                val x1 = (c + 1) * cellH
                drawLine(borderColor, Offset(x0, y), Offset(x1, y), strokeWidth = stroke)
            }
        }
    }
}


@Composable
fun ControlCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun DrawingCanvasView(
    modifier: Modifier,
    onReady: (DrawingView) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx: Context ->
            DrawingView(ctx, null).also { onReady(it) }
        }
    )
}

@Composable
fun MainButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLandscape: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1976D2),
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun Header() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color(0xFF1976D2))
            .padding(top = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Навигатор Университета", color = Color.White, fontSize = 20.sp)
    }
}


@Composable
fun Controls(
    context: Context,
    appMode: AppMode,

    isTraining: Boolean,
    trainingProgress: String,

    collectingMode: Boolean,
    currentLabel: Int,
    trainingSamples: MutableList<TrainingSample>,
    modelFile: File,

    onTrainingStart: () -> Unit,
    onTrainingEnd: () -> Unit,
    onProgressUpdate: (String) -> Unit,

    onCollectingModeToggle: () -> Unit,
    onLabelChange: (Int) -> Unit,
    onSampleAdd: (TrainingSample) -> Unit,

    neuralNetwork: NeuralNetwork,

    ratings: MutableList<PlaceRating>,
    onSaveRatings: () -> Unit,
    selectedFoodPlace: Obshepit?,

    foodPlaces: List<Obshepit>,
    userCell: Pair<Int, Int>?,

    modifier: Modifier,

    showGrid: Boolean,
    editMode: Boolean,

    foodEditMode: FoodEditMode,

    clusteringMode: Boolean,
    foodRouteMode: Boolean,
    landmarkRouteMode: Boolean,

    clusterCountText: String,
    selectedPointsCount: Int,

    userRowText: String,
    userColText: String,

    dishesText: String,
    routeInfoText: String,

    landmarks: List<Landmark>,
    selectedLandmarkIds: List<Int>,
    landmarkRouteInfo: String,

    onClusterCountChange: (String) -> Unit,
    onUserRowChange: (String) -> Unit,
    onUserColChange: (String) -> Unit,
    onDishesChange: (String) -> Unit,

    onGridClick: () -> Unit,
    onEditClick: () -> Unit,

    onFoodAddClick: () -> Unit,
    onFoodDeleteClick: () -> Unit,

    onExportClick: () -> Unit,

    onClusteringToggle: () -> Unit,
    onRunClustering: () -> Unit,

    onFoodRouteToggle: () -> Unit,
    onRunFoodRoute: () -> Unit,
    onClearFoodRoute: () -> Unit,

    onLandmarkRouteToggle: () -> Unit,
    onLandmarkSelectionToggle: (Int) -> Unit,
    onRunLandmarkRoute: () -> Unit,
    onClearLandmarkRoute: () -> Unit,

    isErasing: Boolean,
    onToggleErasing: () -> Unit,

    onChangeMode: () -> Unit,

    astarMode: Boolean,
    obstacleDrawingEnabled: Boolean,
    astarStart: Pair<Int, Int>?,
    astarEnd: Pair<Int, Int>?,
    selectingStart: Boolean,
    selectingEnd: Boolean,
    isAstarRunning: Boolean,
    astarVisualizationState: AStarVisualState,

    onAstarModeToggle: () -> Unit,
    onObstacleDrawingToggle: () -> Unit,
    onSelectStartClick: () -> Unit,
    onSelectEndClick: () -> Unit,
    onRunAstar: () -> Unit,
    onStopAstar: () -> Unit,
    onClearAstar: () -> Unit,

    isLandscape: Boolean = false
) {
    val scrollState = rememberScrollState()
    val isDev = appMode == AppMode.DEVELOPER

    var drawingView by remember { mutableStateOf<DrawingView?>(null) }
    var predictedRating by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .then(if (isLandscape) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
            .background(Color(0xFFF5F7FA))
            .verticalScroll(scrollState)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        if (isDev) {
            ControlCard {
                MainButton(if (showGrid) "Скрыть сетку" else "Показать сетку", onGridClick, isLandscape = isLandscape)

                MainButton(
                    if (editMode) "Режим просмотра" else "Редактировать карту",
                    onEditClick,
                    isLandscape = isLandscape,
                    enabled = !astarMode
                )

                MainButton("Выгрузить лог", onExportClick, isLandscape = isLandscape)

                MainButton(
                    if (isErasing) "Ластик: ВКЛ" else "Ластик",
                    onToggleErasing,
                    isLandscape = isLandscape,
                    enabled = !astarMode
                )
            }

            ControlCard {
                MainButton(
                    if (foodEditMode == FoodEditMode.ADD) "Добавление ВКЛ" else "Добавить общепит",
                    onFoodAddClick,
                    enabled = !clusteringMode && !foodRouteMode && !landmarkRouteMode && !astarMode,
                    isLandscape = isLandscape
                )

                MainButton(
                    if (foodEditMode == FoodEditMode.DELETE) "Удаление ВКЛ" else "Удалить общепит",
                    onFoodDeleteClick,
                    enabled = !clusteringMode && !foodRouteMode && !landmarkRouteMode && !astarMode,
                    isLandscape = isLandscape
                )
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Обучение нейросети", fontWeight = FontWeight.Bold)

                    MainButton(
                        text = if (isTraining) "Идёт обучение..." else "Обучить нейросеть",
                        onClick = {
                            if (isTraining) return@MainButton
                            onTrainingStart()

                            Thread {
                                try {
                                    if (modelFile.exists()) modelFile.delete()

                                    onProgressUpdate("Начинаем обучение...")
                                    Thread.sleep(500)

                                    val trainer = NeuralNetworkTrainer(
                                        context = context,
                                        onProgress = { msg -> onProgressUpdate(msg) },
                                        onComplete = {
                                            try {
                                                neuralNetwork.loadModel(modelFile)
                                                neuralNetwork.markAsTrained()
                                                onProgressUpdate("✅ Модель загружена!")
                                            } catch (e: Exception) {
                                                onProgressUpdate("Ошибка загрузки: ${e.message}")
                                            }
                                            onTrainingEnd()
                                        }
                                    )

                                    trainer.train()
                                } catch (e: Exception) {
                                    onProgressUpdate("Критическая ошибка: ${e.message}")
                                    onTrainingEnd()
                                }
                            }.start()
                        },
                        enabled = !isTraining,
                        isLandscape = isLandscape
                    )

                    if (trainingProgress.isNotBlank()) {
                        Text(
                            trainingProgress,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    MainButton(
                        text = "Сбросить модель",
                        onClick = {
                            if (modelFile.exists()) modelFile.delete()
                            neuralNetwork.initializeRandomWeights()
                            onProgressUpdate("Модель сброшена")
                        },
                        isLandscape = isLandscape
                    )
                }
            }

            ControlCard {
                MainButton(
                    if (astarMode) "A*: ВЫКЛ" else "A*: визуализация",
                    onAstarModeToggle,
                    isLandscape = isLandscape
                )

                if (astarMode) {
                    Text(
                        buildString {
                            append("Start: ${astarStart ?: "-"}\n")
                            append("End: ${astarEnd ?: "-"}\n")
                            append("Open: ${astarVisualizationState.openSet.size}, Closed: ${astarVisualizationState.closedSet.size}\n")
                            append(
                                when {
                                    isAstarRunning -> "Статус: идёт…"
                                    astarVisualizationState.isNoPath -> "Статус: пути нет"
                                    astarVisualizationState.isComplete -> "Статус: готово"
                                    else -> "Статус: ожидание"
                                }
                            )
                        },
                        fontSize = 12.sp
                    )

                    MainButton(
                        if (obstacleDrawingEnabled) "Препятствия: ВКЛ" else "Препятствия",
                        onObstacleDrawingToggle,
                        isLandscape = isLandscape
                    )

                    MainButton(
                        if (selectingStart) "Выбор START: ВКЛ" else "Выбрать START",
                        onSelectStartClick,
                        isLandscape = isLandscape,
                        enabled = !isAstarRunning
                    )

                    MainButton(
                        if (selectingEnd) "Выбор END: ВКЛ" else "Выбрать END",
                        onSelectEndClick,
                        isLandscape = isLandscape,
                        enabled = !isAstarRunning
                    )

                    MainButton(
                        "Запустить",
                        onRunAstar,
                        isLandscape = isLandscape,
                        enabled = !isAstarRunning && astarStart != null && astarEnd != null
                    )

                    MainButton(
                        "Остановить",
                        onStopAstar,
                        isLandscape = isLandscape,
                        enabled = isAstarRunning
                    )

                    MainButton(
                        "Очистить",
                        onClearAstar,
                        isLandscape = isLandscape,
                        enabled = !isAstarRunning
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedFoodPlace != null) {
                    Text("Оценка для: ${selectedFoodPlace.title}", fontWeight = FontWeight.Bold)

                    val avgRating = ratings
                        .filter { it.row == selectedFoodPlace.row && it.col == selectedFoodPlace.col }
                        .map { it.rating }
                        .average()
                        .let { if (it.isNaN()) 0.0 else it }

                    if (avgRating > 0) {
                        Text(
                            "⭐ Текущий рейтинг: ${String.format("%.1f", avgRating)}",
                            color = Color(0xFF1976D2),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text("Нарисуйте цифру (1-9):", fontSize = 12.sp)

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DrawingCanvasView(
                            modifier = Modifier
                                .size(200.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .clipToBounds(),
                            onReady = { drawingView = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                drawingView?.let { view ->
                                    val bitmap = (view as? android.view.View)?.let { v ->
                                        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(bmp)
                                        v.draw(canvas)
                                        bmp
                                    }
                                    if (bitmap != null) {
                                        val input = preprocessBitmap(bitmap)
                                        predictedRating = neuralNetwork.predict(input)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Распознать") }

                        Button(
                            onClick = {
                                drawingView?.clear()
                                predictedRating = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Очистить") }
                    }

                    predictedRating?.let { rating ->
                        Text("Распознано: $rating", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))

                        MainButton(
                            text = "Сохранить оценку",
                            onClick = {
                                ratings.add(PlaceRating(row = selectedFoodPlace.row, col = selectedFoodPlace.col, rating = rating))
                                onSaveRatings()
                                predictedRating = null
                                drawingView?.clear()
                            },
                            isLandscape = isLandscape
                        )
                    }
                } else {
                    Text(
                        "Выберите заведение на карте, чтобы оставить оценку",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        ControlCard {
            MainButton(
                if (clusteringMode) "Вернуться к карте" else "Кластеризация",
                onClusteringToggle,
                isLandscape = isLandscape,
                enabled = !astarMode
            )

            if (clusteringMode) {
                OutlinedTextField(
                    value = clusterCountText,
                    onValueChange = onClusterCountChange,
                    label = { Text("Количество кластеров K") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Выбрано точек: $selectedPointsCount")
                MainButton("Запустить K-средних", onRunClustering, isLandscape = isLandscape)
            }
        }

        ControlCard {
            MainButton(
                if (foodRouteMode) "Скрыть поиск по блюдам" else "Маршрут по блюдам",
                onFoodRouteToggle,
                isLandscape = isLandscape,
                enabled = !astarMode
            )

            if (foodRouteMode || landmarkRouteMode) {
                OutlinedTextField(
                    value = userRowText,
                    onValueChange = onUserRowChange,
                    label = { Text("Строка пользователя") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = userColText,
                    onValueChange = onUserColChange,
                    label = { Text("Столбец пользователя") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (foodRouteMode) {
                OutlinedTextField(
                    value = dishesText,
                    onValueChange = onDishesChange,
                    label = { Text("Блюда через запятую") },
                    modifier = Modifier.fillMaxWidth()
                )

                MainButton("Построить", onRunFoodRoute, isLandscape = isLandscape)
                MainButton("Очистить", onClearFoodRoute, isLandscape = isLandscape)

                if (routeInfoText.isNotBlank()) Text(routeInfoText)
            }
        }

        ControlCard {
            MainButton(
                if (landmarkRouteMode) "Скрыть маршрут по достопримечательностям" else "Маршрут по достопримечательностям",
                onLandmarkRouteToggle,
                isLandscape = isLandscape,
                enabled = !astarMode
            )

            if (landmarkRouteMode) {
                Text("Выберите достопримечательности:", fontWeight = FontWeight.Bold)

                landmarks.forEach { landmark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = landmark.id in selectedLandmarkIds,
                                onValueChange = { onLandmarkSelectionToggle(landmark.id) }
                            )
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = landmark.id in selectedLandmarkIds,
                            onCheckedChange = null
                        )

                        Spacer(Modifier.width(8.dp))

                        Column {
                            Text(landmark.title, fontWeight = FontWeight.SemiBold)
                            if (landmark.description.isNotBlank()) {
                                Text(landmark.description, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                MainButton("Построить", onRunLandmarkRoute, isLandscape = isLandscape)
                MainButton("Очистить", onClearLandmarkRoute, isLandscape = isLandscape)

                if (landmarkRouteInfo.isNotBlank()) Text(landmarkRouteInfo)
            }
        }

        MainButton("Сменить режим", onChangeMode, isLandscape = isLandscape)
        Spacer(modifier = Modifier.height(12.dp))

        LunchDecisionTreeCard(
            isDeveloper = isDev,
            foodPlaces = foodPlaces,
            userCell = userCell,
            modifier = Modifier.fillMaxWidth()
        )

    }
}


@Composable
fun UniversityMap(
    modifier: Modifier = Modifier,
    ratings: List<PlaceRating>,
    showGrid: Boolean,
    editMode: Boolean,
    isErasing: Boolean,
    grid: Array<BooleanArray>,
    foodPlaces: List<Obshepit>,
    landmarks: List<Landmark>,
    selectedLandmarkIds: List<Int>,
    landmarkRoutePath: List<Pair<Int, Int>>,
    orderedLandmarks: List<Landmark>,
    foodEditMode: FoodEditMode,
    clusteringMode: Boolean,
    selectedClusterPoints: List<ClusterPoint>,
    clusteredPoints: List<ClusteredPoint>,
    externalPath: List<Pair<Int, Int>>,
    routeFoodPlaces: List<Obshepit>,
    userCell: Pair<Int, Int>?,

    onGridChanged: () -> Unit,
    onAddFoodPlace: (Int, Int) -> Unit,
    onDeleteFoodPlace: (Int, Int) -> Unit,
    onClusterPointToggle: (Int, Int) -> Unit,
    onFoodPlaceSelected: (Obshepit?) -> Unit,

    astarMode: Boolean = false,
    obstacleDrawingEnabled: Boolean = false,
    astarVisualState: AStarVisualState = AStarVisualState(),
    astarStart: Pair<Int, Int>? = null,
    astarEnd: Pair<Int, Int>? = null,
    selectingStart: Boolean = false,
    selectingEnd: Boolean = false,
    onAstarStartSelected: (Pair<Int, Int>) -> Unit = {},
    onAstarEndSelected: (Pair<Int, Int>) -> Unit = {},
    onSelectingStartChanged: (Boolean) -> Unit = {},
    onSelectingEndChanged: (Boolean) -> Unit = {},
    onObstacleToggled: (Int, Int) -> Unit = { _, _ -> }
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var startPoint by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var endPoint by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var path by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }

    var localGrid by remember { mutableStateOf(grid) }
    var selectedFoodPlaceLocal by remember { mutableStateOf<Obshepit?>(null) }

    val zoneIndex: Array<IntArray>? by remember {
        derivedStateOf {
            val centroids = computeClusterCentroids(clusteredPoints)
            if (centroids.isEmpty()) null else buildClusterZoneIndex(centroids)
        }
    }

    LaunchedEffect(selectedFoodPlaceLocal) {
        onFoodPlaceSelected(selectedFoodPlaceLocal)
    }

    LaunchedEffect(grid) {
        localGrid = grid.map { it.copyOf() }.toTypedArray()
    }

    val clusterColors = listOf(
        Color.Red, Color.Blue, Color.Green, Color.Magenta, Color.Cyan,
        Color.Yellow, Color(0xFFFF9800), Color(0xFF9C27B0),
        Color(0xFF795548), Color(0xFF009688)
    )

    fun getAverageRating(row: Int, col: Int): Float {
        val list = ratings.filter { it.row == row && it.col == col }
        return if (list.isEmpty()) 0f else list.map { it.rating }.average().toFloat()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        val density = LocalDensity.current
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        val imgRatio = MapConfig.IMG_W / MapConfig.IMG_H
        val screenRatio = screenW / screenH
        val mapW = if (imgRatio > screenRatio) screenW else screenH * imgRatio
        val mapH = if (imgRatio > screenRatio) screenW / imgRatio else screenH
        val minScale = maxOf(screenW / mapW, screenH / mapH)

        fun fixOffset(s: Float, o: Offset): Offset {
            val maxX = maxOf((mapW * s - screenW) / 2f, 0f)
            val maxY = maxOf((mapH * s - screenH) / 2f, 0f)
            return Offset(
                o.x.coerceIn(-maxX, maxX),
                o.y.coerceIn(-maxY, maxY)
            )
        }

        fun getGridCoords(tap: Offset): Pair<Int, Int>? {
            val x = (tap.x - screenW / 2f - offset.x) / scale + mapW / 2f
            val y = (tap.y - screenH / 2f - offset.y) / scale + mapH / 2f
            val c = (x / mapW * MapConfig.COLS).toInt()
            val r = (y / mapH * MapConfig.ROWS).toInt()
            return if (r in 0 until MapConfig.ROWS && c in 0 until MapConfig.COLS) r to c else null
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(minScale, 5f)
                        offset = fixOffset(scale, offset + pan)
                    }
                }
                .pointerInput(
                    astarMode,
                    obstacleDrawingEnabled,
                    selectingStart,
                    selectingEnd,
                    editMode,
                    isErasing,
                    screenW,
                    screenH,
                    offset,
                    scale,
                    foodPlaces,
                    foodEditMode,
                    clusteringMode,
                    selectedClusterPoints
                ) {
                    if (editMode && !astarMode) {
                        detectDragGestures { change, _ ->
                            getGridCoords(change.position)?.let { (r, c) ->
                                val newGrid = localGrid.map { it.copyOf() }.toTypedArray()

                                if (isErasing) {
                                    if (localGrid[r][c]) {
                                        newGrid[r][c] = false
                                        localGrid = newGrid
                                        grid[r][c] = false
                                        onGridChanged()
                                    }
                                } else {
                                    if (!localGrid[r][c]) {
                                        newGrid[r][c] = true
                                        localGrid = newGrid
                                        grid[r][c] = true
                                        onGridChanged()
                                    }
                                }

                                if ((startPoint?.first == r && startPoint?.second == c) ||
                                    (endPoint?.first == r && endPoint?.second == c)
                                ) {
                                    startPoint = null
                                    endPoint = null
                                    path = emptyList()
                                }
                            }
                        }
                    } else {
                        detectTapGestures { tap ->
                            getGridCoords(tap)?.let { (r, c) ->

                                if (astarMode) {
                                    if (obstacleDrawingEnabled) {
                                        val newGrid = localGrid.map { it.copyOf() }.toTypedArray()
                                        newGrid[r][c] = !newGrid[r][c]
                                        localGrid = newGrid
                                        grid[r][c] = newGrid[r][c]
                                        onGridChanged()
                                        onObstacleToggled(r, c)
                                        return@detectTapGestures
                                    }

                                    if (selectingStart) {
                                        if (localGrid[r][c]) {
                                            onAstarStartSelected(r to c)
                                            onSelectingStartChanged(false)
                                        }
                                        return@detectTapGestures
                                    }

                                    if (selectingEnd) {
                                        if (localGrid[r][c]) {
                                            onAstarEndSelected(r to c)
                                            onSelectingEndChanged(false)
                                        }
                                        return@detectTapGestures
                                    }

                                    return@detectTapGestures
                                }

                                if (clusteringMode) {
                                    onClusterPointToggle(r, c)
                                    return@detectTapGestures
                                }

                                when (foodEditMode) {
                                    FoodEditMode.ADD -> {
                                        onAddFoodPlace(r, c)
                                        selectedFoodPlaceLocal = foodPlaces.find { it.row == r && it.col == c }
                                    }

                                    FoodEditMode.DELETE -> {
                                        onDeleteFoodPlace(r, c)
                                        if (selectedFoodPlaceLocal?.row == r && selectedFoodPlaceLocal?.col == c) {
                                            selectedFoodPlaceLocal = null
                                        }
                                    }

                                    FoodEditMode.NONE -> {
                                        val clickedFoodPlace = foodPlaces.find { it.row == r && it.col == c }
                                        selectedFoodPlaceLocal = clickedFoodPlace

                                        if (localGrid[r][c]) {
                                            if (startPoint == null || endPoint != null) {
                                                startPoint = r to c
                                                endPoint = null
                                                path = emptyList()
                                            } else {
                                                endPoint = r to c
                                                // оставлено как было (синхронно)
                                                path = com.example.universitymapproj.pathfinding.AStarPathfinder(localGrid).findPath(
                                                    startPoint!!.first,
                                                    startPoint!!.second,
                                                    r,
                                                    c
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(with(density) { mapW.toDp() })
                    .height(with(density) { mapH.toDp() })
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            ) {
                Image(
                    painter = painterResource(R.drawable.map_university),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cw = size.width / MapConfig.COLS
                    val ch = size.height / MapConfig.ROWS

                    // зоны кластеров (за всем)
                    if (clusteredPoints.isNotEmpty()) {
                        zoneIndex?.let { zones ->
                            drawClusterZones(
                                zoneIndex = zones,
                                cellW = cw,
                                cellH = ch,
                                alphaFill = 0.22f,
                                drawBorders = true
                            )
                        }
                    }

                    // сетка
                    if (showGrid) {
                        for (r in 0 until MapConfig.ROWS) {
                            for (c in 0 until MapConfig.COLS) {
                                drawRect(
                                    color = if (!localGrid[r][c]) {
                                        Color.Red.copy(alpha = 0.35f)
                                    } else {
                                        Color.White.copy(alpha = 0.2f)
                                    },
                                    topLeft = Offset(c * cw, r * ch),
                                    size = Size(cw, ch)
                                )
                            }
                        }
                    }

                    if (astarMode) {
                        // closed
                        astarVisualState.closedSet.forEach { (rr, cc) ->
                            drawRect(
                                color = Color(0xFF616161).copy(alpha = 0.35f),
                                topLeft = Offset(cc * cw, rr * ch),
                                size = Size(cw, ch)
                            )
                        }

                        // open
                        astarVisualState.openSet.forEach { (rr, cc) ->
                            drawRect(
                                color = Color(0xFF64B5F6).copy(alpha = 0.35f),
                                topLeft = Offset(cc * cw, rr * ch),
                                size = Size(cw, ch)
                            )
                        }

                        // current
                        astarVisualState.current?.let { (rr, cc) ->
                            drawRect(
                                color = Color.Red.copy(alpha = 0.6f),
                                topLeft = Offset(cc * cw, rr * ch),
                                size = Size(cw, ch)
                            )
                        }

                        // final path
                        astarVisualState.path.forEach { (rr, cc) ->
                            drawRect(
                                color = Color(0xFF4CAF50).copy(alpha = 0.75f),
                                topLeft = Offset(cc * cw, rr * ch),
                                size = Size(cw, ch)
                            )
                        }

                        // start/end markers
                        astarStart?.let { (rr, cc) ->
                            drawCircle(
                                color = Color.Blue,
                                radius = cw / 2,
                                center = Offset(cc * cw + cw / 2, rr * ch + ch / 2)
                            )
                        }
                        astarEnd?.let { (rr, cc) ->
                            drawCircle(
                                color = Color.Magenta,
                                radius = cw / 2,
                                center = Offset(cc * cw + cw / 2, rr * ch + ch / 2)
                            )
                        }
                    }
                    // ----------------------------------------

                    // обычный путь A*
                    path.forEach { (r, c) ->
                        drawRect(
                            color = Color(0xFF4CAF50),
                            topLeft = Offset(c * cw, r * ch),
                            size = Size(cw, ch)
                        )
                    }

                    // внешний путь (генетика)
                    externalPath.forEach { (r, c) ->
                        drawRect(
                            color = Color(0xFF1565C0).copy(alpha = 0.85f),
                            topLeft = Offset(c * cw, r * ch),
                            size = Size(cw, ch)
                        )
                    }

                    // маршрут по достопримечательностям
                    landmarkRoutePath.forEach { (r, c) ->
                        drawRect(
                            color = Color(0xFFFF9800).copy(alpha = 0.85f),
                            topLeft = Offset(c * cw, r * ch),
                            size = Size(cw, ch)
                        )
                    }

                    // общепит + рейтинг
                    foodPlaces.forEach { place ->
                        val avgRating = getAverageRating(place.row, place.col)
                        val cellX = place.col * cw
                        val cellY = place.row * ch

                        val color = when {
                            avgRating >= 7f -> Color(0xFF4CAF50)
                            avgRating >= 4f -> Color(0xFFFFC107)
                            avgRating > 0f -> Color(0xFFF44336)
                            else -> Color.Yellow
                        }

                        drawRect(
                            color = color.copy(alpha = 0.85f),
                            topLeft = Offset(cellX, cellY),
                            size = Size(cw, ch)
                        )

                        if (avgRating > 0f) {
                            drawContext.canvas.nativeCanvas.drawText(
                                String.format("%.1f", avgRating),
                                cellX + cw / 2,
                                cellY + ch / 2,
                                android.graphics.Paint().apply {
                                    this.color = android.graphics.Color.BLACK
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    textSize = cw * 0.6f
                                    isFakeBoldText = true
                                }
                            )
                        }
                    }

                    // достопримечательности
                    landmarks.forEach { landmark ->
                        val color =
                            if (landmark.id in selectedLandmarkIds) Color(0xFF9C27B0) else Color(0xFF00BCD4)
                        drawRect(
                            color = color.copy(alpha = 0.85f),
                            topLeft = Offset(landmark.col * cw, landmark.row * ch),
                            size = Size(cw, ch)
                        )
                    }

                    // маршрут по блюдам - точки
                    routeFoodPlaces.forEachIndexed { index, place ->
                        drawRect(
                            color = if (index == 0) Color(0xFFFF5722) else Color(0xFF8BC34A),
                            topLeft = Offset(place.col * cw, place.row * ch),
                            size = Size(cw, ch)
                        )
                    }

                    // упорядоченные landmarks
                    orderedLandmarks.forEachIndexed { index, landmark ->
                        drawRect(
                            color = if (index == 0) Color(0xFFD32F2F) else Color(0xFF673AB7),
                            topLeft = Offset(landmark.col * cw, landmark.row * ch),
                            size = Size(cw, ch)
                        )
                    }

                    // пользователь
                    userCell?.let { (r, c) ->
                        drawRect(
                            color = Color.Red.copy(alpha = 0.9f),
                            topLeft = Offset(c * cw, r * ch),
                            size = Size(cw, ch)
                        )
                    }

                    // кластеры / выбранные точки
                    if (clusteredPoints.isNotEmpty()) {
                        clusteredPoints.forEach { point ->
                            drawRect(
                                color = clusterColors[point.clusterIndex % clusterColors.size].copy(alpha = 0.85f),
                                topLeft = Offset(point.col * cw, point.row * ch),
                                size = Size(cw, ch)
                            )
                        }
                    } else {
                        selectedClusterPoints.forEach { point ->
                            drawRect(
                                color = Color.Black.copy(alpha = 0.85f),
                                topLeft = Offset(point.col * cw, point.row * ch),
                                size = Size(cw, ch)
                            )
                        }
                    }

                    // start/end (старый режим)
                    startPoint?.let { (r, c) ->
                        drawCircle(
                            color = Color.Blue,
                            radius = cw / 2,
                            center = Offset(c * cw + cw / 2, r * ch + ch / 2)
                        )
                    }

                    endPoint?.let { (r, c) ->
                        drawCircle(
                            color = Color.Magenta,
                            radius = cw / 2,
                            center = Offset(c * cw + cw / 2, r * ch + ch / 2)
                        )
                    }
                }

                // всплывашка по заведению
                selectedFoodPlaceLocal?.let { place ->
                    val cellW = mapW / MapConfig.COLS
                    val cellH = mapH / MapConfig.ROWS

                    val placeRatings = ratings.filter { it.row == place.row && it.col == place.col }
                    val avgRating = if (placeRatings.isNotEmpty()) placeRatings.map { it.rating }.average() else 0.0

                    val dishesText =
                        if (place.dishes.isNotEmpty()) place.dishes.joinToString(", ") else ""

                    Text(
                        text = buildString {
                            append(place.title)
                            if (avgRating > 0) append("\n⭐ Рейтинг: ${String.format("%.1f", avgRating)}")
                            if (dishesText.isNotBlank()) {
                                append("\nМеню: ")
                                append(dishesText)
                            }
                        },
                        color = Color.Black,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .offset(
                                x = with(density) { (place.col * cellW + 6f).toDp() },
                                y = with(density) { (place.row * cellH - 40f).toDp() }
                            )
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}