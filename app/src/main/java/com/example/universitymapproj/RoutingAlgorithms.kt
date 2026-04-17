package com.example.universitymapproj.routing

import com.example.universitymapproj.models.*
import com.example.universitymapproj.pathfinding.AStarPathfinder
import com.example.universitymapproj.pathfinding.RouteDistanceCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Функция сохранения рейтингов
fun saveRatingsToFile(file: File, ratings: List<PlaceRating>) {
    file.printWriter().use { out ->
        ratings.forEach {
            out.println("${it.row},${it.col},${it.rating}")
        }
    }
}

// Функция загрузки рейтингов
fun loadRatingsFromFile(file: File): List<PlaceRating> {
    if (!file.exists()) return emptyList()

    return file.readLines().mapNotNull { line ->
        val parts = line.split(",")
        if (parts.size == 3) {
            PlaceRating(
                row = parts[0].toInt(),
                col = parts[1].toInt(),
                rating = parts[2].toInt()
            )
        } else null
    }
}

fun buildFullPathThroughRoute(
    userLocation: UserLocation,
    route: List<Obshepit>,
    grid: Array<BooleanArray>
): List<Pair<Int, Int>> {
    if (route.isEmpty()) return emptyList()

    val pathfinder = AStarPathfinder(grid)
    val fullPath = mutableListOf<Pair<Int, Int>>()

    var currentRow = userLocation.row
    var currentCol = userLocation.col

    for ((index, place) in route.withIndex()) {
        val segment = pathfinder.findPath(currentRow, currentCol, place.row, place.col)
        if (segment.isEmpty()) return emptyList()

        if (index == 0) fullPath.addAll(segment)
        else fullPath.addAll(segment.drop(1))

        currentRow = place.row
        currentCol = place.col
    }

    return fullPath
}

fun buildFullPathThroughPoints(
    start: Pair<Int, Int>,
    orderedPoints: List<Pair<Int, Int>>,
    grid: Array<BooleanArray>
): List<Pair<Int, Int>> {
    if (orderedPoints.isEmpty()) return emptyList()

    val pathfinder = AStarPathfinder(grid)
    val fullPath = mutableListOf<Pair<Int, Int>>()

    var current = start

    for ((index, nextPoint) in orderedPoints.withIndex()) {
        val segment = pathfinder.findPath(
            current.first,
            current.second,
            nextPoint.first,
            nextPoint.second
        )

        if (segment.isEmpty()) return emptyList()

        if (index == 0) fullPath.addAll(segment)
        else fullPath.addAll(segment.drop(1))

        current = nextPoint
    }

    return fullPath
}

fun buildLandmarkVisitOrder(selectedLandmarks: List<Landmark>): List<Landmark> {
    if (selectedLandmarks.isEmpty()) return emptyList()

    val points = selectedLandmarks.map {
        Point(it.row.toDouble(), it.col.toDouble())
    }

    val optimizer = AntColonyOptimizer()
    val optimizedPoints = optimizer.optimize(points)

    val remaining = selectedLandmarks.toMutableList()
    val ordered = mutableListOf<Landmark>()

    for (p in optimizedPoints) {
        val index = remaining.indexOfFirst {
            it.row.toDouble() == p.x && it.col.toDouble() == p.y
        }
        if (index >= 0) {
            ordered.add(remaining.removeAt(index))
        }
    }

    return ordered
}

suspend fun buildFullPathThroughRouteAsync(
    userLocation: UserLocation,
    route: List<Obshepit>,
    grid: Array<BooleanArray>
): List<Pair<Int, Int>> = withContext(Dispatchers.Default) {
    buildFullPathThroughRoute(userLocation, route, grid)
}

suspend fun buildFullPathThroughPointsAsync(
    start: Pair<Int, Int>,
    orderedPoints: List<Pair<Int, Int>>,
    grid: Array<BooleanArray>
): List<Pair<Int, Int>> = withContext(Dispatchers.Default) {
    buildFullPathThroughPoints(start, orderedPoints, grid)
}

suspend fun buildLandmarkVisitOrderAsync(
    selectedLandmarks: List<Landmark>
): List<Landmark> = withContext(Dispatchers.Default) {
    buildLandmarkVisitOrder(selectedLandmarks)
}