package com.example.universitymapproj

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private const val COLS = 108
private const val ROWS = 122
private const val IMG_W = 1944f
private const val IMG_H = 2196f

// ХРАНИЛИЩЕ
object MapConfig {
    //СТРОКА ИЗ ЛОГ КЕТА сюда
    var SAVED_GRID = ""
}

fun exportGridToString(grid: Array<BooleanArray>): String {
    val sb = StringBuilder()
    for (row in grid) {
        for (cell in row) sb.append(if (cell) "1" else "0")
    }
    return sb.toString()
}

fun importGridFromString(data: String): Array<BooleanArray> {
    if (data.length != ROWS * COLS) return Array(ROWS) { BooleanArray(COLS) { false } }

    val grid = Array(ROWS) { BooleanArray(COLS) }
    var index = 0
    for (r in 0 until ROWS) {
        for (c in 0 until COLS) {
            grid[r][c] = data[index] == '1'
            index++
        }
    }
    return grid
}
//  A*
data class Node(val r: Int, val c: Int, var g: Int = 0, var h: Int = 0, var parent: Node? = null) {
    val f get() = g + h
}

class AStarPathfinder(private val grid: Array<BooleanArray>) {
    fun findPath(sr: Int, sc: Int, er: Int, ec: Int): List<Pair<Int, Int>> {
        val open = mutableListOf(Node(sr, sc))
        val closed = mutableSetOf<Pair<Int, Int>>()

        val gCost = Array(ROWS) { IntArray(COLS) { Int.MAX_VALUE } }
        gCost[sr][sc] = 0

        while (open.isNotEmpty()) {
            val curr = open.minByOrNull { it.f }!!
            if (curr.r == er && curr.c == ec) {
                val path = mutableListOf<Pair<Int, Int>>()
                var temp: Node? = curr
                while (temp != null) {
                    path.add(temp.r to temp.c)
                    temp = temp.parent
                }
                return path.reversed()
            }

            open.remove(curr)
            closed.add(curr.r to curr.c)

            val directions = listOf(
                0 to 1,
                0 to -1,
                1 to 0,
                -1 to 0,
                1 to 1,
                1 to -1,
                -1 to 1,
                -1 to -1
            )

            for ((dr, dc) in directions) {
                val nr = curr.r + dr
                val nc = curr.c + dc

                if (nr !in 0 until ROWS || nc !in 0 until COLS) continue

                if (!grid[nr][nc]) continue

                if (nr to nc in closed) continue

                val moveCost = if (dr != 0 && dc != 0) 14 else 10
                val newG = curr.g + moveCost

                if (newG < gCost[nr][nc]) {
                    gCost[nr][nc] = newG

                    val h = (abs(nr - er) + abs(nc - ec)) * 10


                    val node = Node(nr, nc)
                    node.parent = curr
                    node.g = newG
                    node.h = h


                    val existing = open.find { it.r == nr && it.c == nc }
                    if (existing != null) {
                        existing.g = newG
                        existing.h = h
                        existing.parent = curr
                    } else {
                        open.add(node)
                    }
                }
            }
        }
        return emptyList()
    }
}

//  UI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MainScreen() }
    }
}

@Composable
fun MainScreen() {
    var gridVisible by remember { mutableStateOf(false) }
    var editEnabled by remember { mutableStateOf(false) }

    val mapGrid = remember { mutableStateOf(importGridFromString(MapConfig.SAVED_GRID)) }
    var updateTick by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Header()

        UniversityMap(
            modifier = Modifier.weight(5f),
            showGrid = gridVisible,
            editMode = editEnabled,
            grid = mapGrid.value,
            onGridChanged = { updateTick++ }
        )

        Controls(
            modifier = Modifier.weight(1f),
            showGrid = gridVisible,
            editMode = editEnabled,
            onGridClick = { gridVisible = !gridVisible },
            onEditClick = { editEnabled = !editEnabled },
            onExportClick = {
                val result = exportGridToString(mapGrid.value)
                Log.d("MAP_DATA", result)
            }
        )
    }
}

@Composable
fun Header() {
    Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color(0xFF1976D2)).padding(top = 24.dp), contentAlignment = Alignment.Center) {
        Text("Навигатор Университета", color = Color.White, fontSize = 20.sp)
    }
}

@Composable
fun Controls(
    modifier: Modifier, showGrid: Boolean, editMode: Boolean,
    onGridClick: () -> Unit, onEditClick: () -> Unit, onExportClick: () -> Unit
) {
    Row(modifier = modifier.fillMaxWidth().background(Color(0xFFF2F2F2)).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onGridClick) { Text(if (showGrid) "Скрыть" else "Сетка") }
        Button(onClick = onEditClick) { Text(if (editMode) "Просмотр" else "Править") }
        Button(onClick = onExportClick) { Text("Log") }
    }
}



@Composable
fun UniversityMap(
    modifier: Modifier = Modifier,
    showGrid: Boolean,
    editMode: Boolean,
    grid: Array<BooleanArray>,
    onGridChanged: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var startPoint by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var endPoint by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var path by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }

    var localGrid by remember { mutableStateOf(grid) }

    LaunchedEffect(grid) {
        localGrid = grid.map { it.copyOf() }.toTypedArray()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        val density = LocalDensity.current
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        val imgRatio = IMG_W / IMG_H
        val screenRatio = screenW / screenH
        val mapW = if (imgRatio > screenRatio) screenW else screenH * imgRatio
        val mapH = if (imgRatio > screenRatio) screenW / imgRatio else screenH
        val minScale = maxOf(screenW / mapW, screenH / mapH)

        fun fixOffset(s: Float, o: Offset): Offset {
            val maxX = maxOf((mapW * s - screenW) / 2f, 0f)
            val maxY = maxOf((mapH * s - screenH) / 2f, 0f)
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }

        fun getGridCoords(tap: Offset): Pair<Int, Int>? {
            val x = (tap.x - screenW / 2f - offset.x) / scale + mapW / 2f
            val y = (tap.y - screenH / 2f - offset.y) / scale + mapH / 2f
            val c = (x / mapW * COLS).toInt()
            val r = (y / mapH * ROWS).toInt()
            return if (r in 0 until ROWS && c in 0 until COLS) r to c else null
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
                .pointerInput(editMode, screenW, screenH, offset, scale) {
                    if (editMode) {
                        detectDragGestures { change, _ ->
                            getGridCoords(change.position)?.let { (r, c) ->
                                if (!localGrid[r][c]) {
                                    val newGrid = localGrid.map { it.copyOf() }.toTypedArray()
                                    newGrid[r][c] = true
                                    localGrid = newGrid
                                    grid[r][c] = true
                                    onGridChanged()
                                }
                            }
                        }
                    } else {
                        detectTapGestures { tap ->
                            getGridCoords(tap)?.let { (r, c) ->
                                if (localGrid[r][c]) {
                                    if (startPoint == null || (startPoint != null && endPoint != null)) {
                                        startPoint = r to c
                                        endPoint = null
                                        path = emptyList()
                                    } else {
                                        endPoint = r to c
                                        path = AStarPathfinder(localGrid).findPath(
                                            startPoint!!.first, startPoint!!.second, r, c
                                        )
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
                    val cw = size.width / COLS
                    val ch = size.height / ROWS

                    if (showGrid) {
                        for (r in 0 until ROWS) {
                            for (c in 0 until COLS) {
                                if (!localGrid[r][c]) {
                                    drawRect(
                                        color = Color.Red.copy(alpha = 0.35f),
                                        topLeft = Offset(c * cw, r * ch),
                                        size = Size(cw, ch)
                                    )
                                } else {
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.2f),
                                        topLeft = Offset(c * cw, r * ch),
                                        size = Size(cw, ch)
                                    )
                                }
                            }
                        }
                    }

                    path.forEach { (r, c) ->
                        drawRect(
                            color = Color(0xFF4CAF50),
                            topLeft = Offset(c * cw, r * ch),
                            size = Size(cw, ch)
                        )
                    }

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
            }
        }
    }
}