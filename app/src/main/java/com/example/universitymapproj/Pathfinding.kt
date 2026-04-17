package com.example.universitymapproj.pathfinding

import com.example.universitymapproj.MapConfig.COLS
import com.example.universitymapproj.MapConfig.ROWS
import com.example.universitymapproj.models.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

class AStarPathfinder(private val grid: Array<BooleanArray>) {

    fun findPath(sr: Int, sc: Int, er: Int, ec: Int): List<Pair<Int, Int>> {
        val open = mutableListOf(Node(sr, sc))
        val closed = mutableSetOf<Pair<Int, Int>>()

        val gCost = Array(ROWS) { IntArray(COLS) { Int.MAX_VALUE } }
        gCost[sr][sc] = 0

        while (open.isNotEmpty()) {
            val curr = open.minByOrNull { it.f }!!

            if (curr.r == er && curr.c == ec) {
                return reconstructPath(curr)
            }

            open.remove(curr)
            closed.add(curr.r to curr.c)

            val directions = listOf(
                0 to 1, 0 to -1, 1 to 0, -1 to 0,
                1 to 1, 1 to -1, -1 to 1, -1 to -1
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

                    val existing = open.find { it.r == nr && it.c == nc }
                    if (existing != null) {
                        existing.g = newG
                        existing.h = h
                        existing.parent = curr
                    } else {
                        open.add(Node(nr, nc, newG, h, curr))
                    }
                }
            }
        }

        return emptyList()
    }

    suspend fun findPathStepByStep(
        sr: Int, sc: Int, er: Int, ec: Int,
        onStateUpdate: (AStarVisualState) -> Unit
    ): List<Pair<Int, Int>> {
        val open = mutableListOf(Node(sr, sc))
        val closed = mutableSetOf<Pair<Int, Int>>()
        val gCost = Array(ROWS) { IntArray(COLS) { Int.MAX_VALUE } }
        gCost[sr][sc] = 0

        while (open.isNotEmpty()) {
            val curr = open.minByOrNull { it.f }!!

            onStateUpdate(
                AStarVisualState(
                    openSet = open.map { it.r to it.c }.toSet(),
                    closedSet = closed.toSet(),
                    current = curr.r to curr.c,
                    path = emptyList(),
                    isComplete = false
                )
            )
            delay(50)

            if (curr.r == er && curr.c == ec) {
                val path = reconstructPath(curr)
                onStateUpdate(
                    AStarVisualState(
                        openSet = open.map { it.r to it.c }.toSet(),
                        closedSet = closed.toSet(),
                        current = curr.r to curr.c,
                        path = path,
                        isComplete = true
                    )
                )
                return path
            }

            open.remove(curr)
            closed.add(curr.r to curr.c)

            val directions = listOf(
                0 to 1, 0 to -1, 1 to 0, -1 to 0,
                1 to 1, 1 to -1, -1 to 1, -1 to -1
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

                    val existing = open.find { it.r == nr && it.c == nc }
                    if (existing != null) {
                        existing.g = newG
                        existing.h = h
                        existing.parent = curr
                    } else {
                        open.add(Node(nr, nc, newG, h, curr))
                    }
                }
            }
        }

        onStateUpdate(
            AStarVisualState(
                openSet = emptySet(),
                closedSet = closed.toSet(),
                current = null,
                path = emptyList(),
                isComplete = true,
                isNoPath = true
            )
        )
        return emptyList()
    }

    private fun reconstructPath(endNode: Node): List<Pair<Int, Int>> {
        val path = mutableListOf<Pair<Int, Int>>()
        var temp: Node? = endNode
        while (temp != null) {
            path.add(temp.r to temp.c)
            temp = temp.parent
        }
        return path.reversed()
    }
}

data class AStarVisualState(
    val openSet: Set<Pair<Int, Int>> = emptySet(),
    val closedSet: Set<Pair<Int, Int>> = emptySet(),
    val current: Pair<Int, Int>? = null,
    val path: List<Pair<Int, Int>> = emptyList(),
    val isComplete: Boolean = false,
    val isNoPath: Boolean = false
)

fun geoToGrid(
    userLat: Double,
    userLon: Double,
    rows: Int = ROWS,
    cols: Int = COLS
): Pair<Int, Int>? {
    val latTop = 56.46594
    val latBottom = 56.471736
    val lonLeft = 84.939305
    val lonRight = 84.954786

    val latClamped = userLat.coerceIn(latTop, latBottom)
    val lonClamped = userLon.coerceIn(lonLeft, lonRight)

    val latNorm = (latClamped - latBottom) / (latTop - latBottom)
    val lonNorm = (lonClamped - lonLeft) / (lonRight - lonLeft)

    val row = ((1 - latNorm) * rows).toInt().coerceIn(0, rows - 1)
    val col = (lonNorm * cols).toInt().coerceIn(0, cols - 1)

    return row to col
}

class RouteDistanceCache(private val grid: Array<BooleanArray>) {
    private val pathfinder = AStarPathfinder(grid)
    private val distanceCache = mutableMapOf<String, Double>()
    private val pathCache = mutableMapOf<String, List<Pair<Int, Int>>>()

    fun getDistance(sr: Int, sc: Int, er: Int, ec: Int): Double {
        val key = "$sr,$sc->$er,$ec"
        val reverseKey = "$er,$ec->$sr,$sc"

        distanceCache[key]?.let { return it }
        distanceCache[reverseKey]?.let { return it }

        val path = pathfinder.findPath(sr, sc, er, ec)
        val result = if (path.isEmpty()) Double.POSITIVE_INFINITY else path.size.toDouble()

        distanceCache[key] = result
        distanceCache[reverseKey] = result
        pathCache[key] = path
        pathCache[reverseKey] = path.reversed()

        return result
    }

    fun getPath(sr: Int, sc: Int, er: Int, ec: Int): List<Pair<Int, Int>> {
        val key = "$sr,$sc->$er,$ec"
        val reverseKey = "$er,$ec->$sr,$sc"

        pathCache[key]?.let { return it }
        pathCache[reverseKey]?.let { return it.reversed() }

        val path = pathfinder.findPath(sr, sc, er, ec)
        pathCache[key] = path
        pathCache[reverseKey] = path.reversed()
        distanceCache[key] = if (path.isEmpty()) Double.POSITIVE_INFINITY else path.size.toDouble()
        distanceCache[reverseKey] = distanceCache[key]!!
        return path
    }
}

suspend fun AStarPathfinder.findPathAsync(
    sr: Int, sc: Int, er: Int, ec: Int
): List<Pair<Int, Int>> = withContext(Dispatchers.Default) {
    findPath(sr, sc, er, ec)
}


fun findNearestPassable(r: Int, c: Int, grid: Array<BooleanArray>): Pair<Int, Int> {
    val rows = grid.size
    val cols = grid[0].size
    if (r in 0 until rows && c in 0 until cols && grid[r][c]) return r to c

    val queue: java.util.Queue<Pair<Int, Int>> = java.util.LinkedList()
    val visited = mutableSetOf<Pair<Int, Int>>()
    queue.add(r to c)
    visited.add(r to c)

    val directions = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0, 1 to 1, 1 to -1, -1 to 1, -1 to -1)

    while (queue.isNotEmpty()) {
        val (currR, currC) = queue.poll() ?: continue
        for ((dr, dc) in directions) {
            val nr = currR + dr
            val nc = currC + dc
            if (nr in 0 until rows && nc in 0 until cols && nr to nc !in visited) {
                if (grid[nr][nc]) return nr to nc
                visited.add(nr to nc)
                queue.add(nr to nc)
            }
        }
        if (visited.size > 2000) break
    }
    return r to c
}

