package com.example.universitymapproj

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val COLS = 54
private const val ROWS = 61
private const val IMG_W = 1900f
private const val IMG_H = 2143f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainScreen()
        }
    }
}

@Composable
fun MainScreen() {
    var gridVisible by remember { mutableStateOf(false) }
    var editEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Header()

        UniversityMap(
            modifier = Modifier.weight(5f),
            showGrid = gridVisible,
            editMode = editEnabled
        )

        Controls(
            modifier = Modifier.weight(1f),
            showGrid = gridVisible,
            editMode = editEnabled,
            onGridClick = { gridVisible = !gridVisible },
            onEditClick = { editEnabled = !editEnabled }
        )
    }
}

@Composable
fun Header() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xFF1976D2)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Карта университета",
            color = Color.White,
            fontSize = 20.sp
        )
    }
}

@Composable
fun Controls(
    modifier: Modifier = Modifier,
    showGrid: Boolean,
    editMode: Boolean,
    onGridClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF2F2F2))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onGridClick) {
            Text(if (showGrid) "Скрыть сетку" else "Показать сетку")
        }

        Button(onClick = onEditClick) {
            Text(if (editMode) "Просмотр" else "Редактировать")
        }
    }
}

@Composable
fun UniversityMap(
    modifier: Modifier = Modifier,
    showGrid: Boolean,
    editMode: Boolean
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val mapGrid = remember {
        Array(ROWS) { BooleanArray(COLS) { true } }
    }

    var gridState by remember { mutableStateOf(0) }

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

        val mapW: Float
        val mapH: Float

        if (imgRatio > screenRatio) {
            mapW = screenW
            mapH = screenW / imgRatio
        } else {
            mapH = screenH
            mapW = screenH * imgRatio
        }

        val minScale = maxOf(
            screenW / mapW,
            screenH / mapH
        )

        if (scale < minScale) {
            scale = minScale
        }

        fun fixOffset(scaleValue: Float, currentOffset: Offset): Offset {
            val maxX = maxOf((mapW * scaleValue - screenW) / 2f, 0f)
            val maxY = maxOf((mapH * scaleValue - screenH) / 2f, 0f)

            return Offset(
                x = currentOffset.x.coerceIn(-maxX, maxX),
                y = currentOffset.y.coerceIn(-maxY, maxY)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, 5f)
                        val newOffset = fixOffset(newScale, offset + pan)
                        scale = newScale
                        offset = newOffset
                    }
                }
                .pointerInput(editMode, screenW, screenH) {
                    if (!editMode) return@pointerInput

                    detectTapGestures { tap ->
                        val x = (tap.x - screenW / 2f - offset.x) / scale + mapW / 2f
                        val y = (tap.y - screenH / 2f - offset.y) / scale + mapH / 2f

                        val col = (x / mapW * COLS).toInt()
                        val row = (y / mapH * ROWS).toInt()

                        if (row in 0 until ROWS && col in 0 until COLS) {
                            mapGrid[row][col] = !mapGrid[row][col]
                            gridState++
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
                    contentDescription = "Карта",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )

                if (showGrid) {
                    val trigger = gridState

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cellW = size.width / COLS
                        val cellH = size.height / ROWS

                        for (row in 0 until ROWS) {
                            for (col in 0 until COLS) {
                                if (!mapGrid[row][col]) {
                                    drawRect(
                                        color = Color.Red.copy(alpha = 0.45f),
                                        topLeft = Offset(col * cellW, row * cellH),
                                        size = Size(cellW, cellH)
                                    )
                                }
                            }
                        }

                        val lineColor = Color.Black.copy(alpha = 0.2f)

                        for (i in 0..COLS) {
                            val x = i * cellW
                            drawLine(
                                color = lineColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1f
                            )
                        }

                        for (i in 0..ROWS) {
                            val y = i * cellH
                            drawLine(
                                color = lineColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewMainScreen() {
    MainScreen()
}