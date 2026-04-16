//package com.example.universitymapproj.pathfinding
//
//import com.example.universitymapproj.MapConfig.COLS
//import com.example.universitymapproj.MapConfig.ROWS
//import com.example.universitymapproj.models.Node
//import kotlin.math.abs
//
//class AStarPathfinder(private val grid: Array<BooleanArray>) {
//    fun findPath(sr: Int, sc: Int, er: Int, ec: Int): List<Pair<Int, Int>> {
//        val open = mutableListOf(Node(sr, sc))
//        val closed = mutableSetOf<Pair<Int, Int>>()
//
//        val gCost = Array(ROWS) { IntArray(COLS) { Int.MAX_VALUE } }
//        gCost[sr][sc] = 0
//
//        while (open.isNotEmpty()) {
//            val curr = open.minByOrNull { it.f }!!
//            if (curr.r == er && curr.c == ec) {
//                val path = mutableListOf<Pair<Int, Int>>()
//                var temp: Node? = curr
//                while (temp != null) {
//                    path.add(temp.r to temp.c)
//                    temp = temp.parent
//                }
//                return path.reversed()
//            }
//
//            open.remove(curr)
//            closed.add(curr.r to curr.c)
//
//            val directions = listOf(
//                0 to 1, 0 to -1, 1 to 0, -1 to 0,
//                1 to 1, 1 to -1, -1 to 1, -1 to -1
//            )
//
//            for ((dr, dc) in directions) {
//                val nr = curr.r + dr
//                val nc = curr.c + dc
//
//                if (nr !in 0 until ROWS || nc !in 0 until COLS) continue
//                if (!grid[nr][nc]) continue
//                if (nr to nc in closed) continue
//
//                val moveCost = if (dr != 0 && dc != 0) 14 else 10
//                val newG = curr.g + moveCost
//
//                if (newG < gCost[nr][nc]) {
//                    gCost[nr][nc] = newG
//                    val h = (abs(nr - er) + abs(nc - ec)) * 10
//
//                    val existing = open.find { it.r == nr && it.c == nc }
//                    if (existing != null) {
//                        existing.g = newG
//                        existing.h = h
//                        existing.parent = curr
//                    } else {
//                        open.add(Node(nr, nc, newG, h, curr))
//                    }
//                }
//            }
//        }
//        return emptyList()
//    }
//}
//
//class RouteDistanceCache(private val grid: Array<BooleanArray>) {
//    private val pathfinder = AStarPathfinder(grid)
//    private val distanceCache = mutableMapOf<String, Double>()
//    private val pathCache = mutableMapOf<String, List<Pair<Int, Int>>>()
//
//    fun getDistance(sr: Int, sc: Int, er: Int, ec: Int): Double {
//        val key = "$sr,$sc->$er,$ec"
//        val reverseKey = "$er,$ec->$sr,$sc"
//
//        distanceCache[key]?.let { return it }
//        distanceCache[reverseKey]?.let { return it }
//
//        val path = pathfinder.findPath(sr, sc, er, ec)
//        val result = if (path.isEmpty()) Double.POSITIVE_INFINITY else path.size.toDouble()
//
//        distanceCache[key] = result
//        distanceCache[reverseKey] = result
//        pathCache[key] = path
//        pathCache[reverseKey] = path.reversed()
//
//        return result
//    }
//
//    fun getPath(sr: Int, sc: Int, er: Int, ec: Int): List<Pair<Int, Int>> {
//        val key = "$sr,$sc->$er,$ec"
//        val reverseKey = "$er,$ec->$sr,$sc"
//
//        pathCache[key]?.let { return it }
//        pathCache[reverseKey]?.let { return it.reversed() }
//
//        val path = pathfinder.findPath(sr, sc, er, ec)
//        pathCache[key] = path
//        pathCache[reverseKey] = path.reversed()
//        distanceCache[key] = if (path.isEmpty()) Double.POSITIVE_INFINITY else path.size.toDouble()
//        distanceCache[reverseKey] = distanceCache[key]!!
//        return path
//    }
//}