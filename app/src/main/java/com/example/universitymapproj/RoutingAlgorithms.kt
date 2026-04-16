package com.example.universitymapproj.routing

import com.example.universitymapproj.models.*
import com.example.universitymapproj.pathfinding.AStarPathfinder
import com.example.universitymapproj.pathfinding.RouteDistanceCache
import kotlin.math.pow
import kotlin.random.Random
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

fun antColonyOptimization(
    points: List<Point>,
    alpha: Double = 1.0,
    beta: Double = 2.0,
    rho: Double = 0.1,
    Q: Double = 1.0,
    tau0: Double = 1.0,
    maxIterations: Int = 1000
): List<Point> {
    val n = points.size
    when (n) {
        0 -> return emptyList()
        1 -> return points
        2 -> return points
    }

    val dist = Array(n) { i ->
        DoubleArray(n) { j ->
            if (i == j) 0.0
            else {
                val dx = points[i].x - points[j].x
                val dy = points[i].y - points[j].y
                kotlin.math.hypot(dx, dy)
            }
        }
    }

    val eta = Array(n) { i ->
        DoubleArray(n) { j ->
            if (i == j) 0.0 else 1.0 / dist[i][j]
        }
    }

    val pheromone = Array(n) { DoubleArray(n) { tau0 } }

    var bestTour: List<Int> = emptyList()
    var bestLength = Double.POSITIVE_INFINITY

    var iteration = 0
    var converged = false

    while (!converged && iteration < maxIterations) {
        iteration++

        val tours = Array(n) { mutableListOf<Int>() }
        val lengths = DoubleArray(n)

        for (ant in 0 until n) {
            val visited = BooleanArray(n)
            val tour = mutableListOf<Int>()
            val start = ant
            tour.add(start)
            visited[start] = true
            var current = start

            while (tour.size < n) {
                val allowed = (0 until n).filter { !visited[it] }

                val probabilities = DoubleArray(allowed.size)
                var sumProb = 0.0
                for ((idx, next) in allowed.withIndex()) {
                    val prob = pheromone[current][next].pow(alpha) * eta[current][next].pow(beta)
                    probabilities[idx] = prob
                    sumProb += prob
                }

                var rand = Random.nextDouble() * sumProb
                var chosen = -1
                for ((idx, next) in allowed.withIndex()) {
                    rand -= probabilities[idx]
                    if (rand <= 0.0) {
                        chosen = next
                        break
                    }
                }
                if (chosen == -1) chosen = allowed.last()

                tour.add(chosen)
                visited[chosen] = true
                current = chosen
            }

            val fullTour = tour.toList()
            tours[ant] = fullTour.toMutableList()
            var length = 0.0
            for (i in 0 until n - 1) {
                length += dist[fullTour[i]][fullTour[i + 1]]
            }
            length += dist[fullTour.last()][fullTour.first()]
            lengths[ant] = length

            if (length < bestLength) {
                bestLength = length
                bestTour = fullTour
            }
        }

        for (i in 0 until n) {
            for (j in 0 until n) {
                pheromone[i][j] *= (1 - rho)
            }
        }

        for (ant in 0 until n) {
            val tour = tours[ant]
            val length = lengths[ant]
            val delta = Q / length
            for (k in 0 until n - 1) {
                val i = tour[k]
                val j = tour[k + 1]
                pheromone[i][j] += delta
                pheromone[j][i] += delta
            }
            val last = tour.last()
            val first = tour.first()
            pheromone[last][first] += delta
            pheromone[first][last] += delta
        }

        if (n > 1) {
            fun tourToEdgeSet(tour: List<Int>): Set<Pair<Int, Int>> {
                val edges = mutableSetOf<Pair<Int, Int>>()
                for (i in 0 until tour.size - 1) {
                    val a = tour[i]
                    val b = tour[i + 1]
                    edges.add(if (a < b) a to b else b to a)
                }
                val a = tour.last()
                val b = tour.first()
                edges.add(if (a < b) a to b else b to a)
                return edges
            }

            val firstEdges = tourToEdgeSet(tours[0])
            val allSame = tours.all { tourToEdgeSet(it) == firstEdges }
            if (allSame) converged = true
        }
    }

    return bestTour.map { points[it] }
}


fun coversAllDishes(route: List<Obshepit>, requiredDishes: List<String>): Boolean {
    val collected = route.flatMap { it.dishes }.toSet()
    return requiredDishes.all { it in collected }
}

fun calculateRouteLengthAStar(
    userLocation: UserLocation,
    route: List<Obshepit>,
    distanceCache: RouteDistanceCache
): Double {
    if (route.isEmpty()) return Double.POSITIVE_INFINITY

    var total = 0.0
    var currentRow = userLocation.row
    var currentCol = userLocation.col

    for (place in route) {
        val dist = distanceCache.getDistance(currentRow, currentCol, place.row, place.col)
        if (dist == Double.POSITIVE_INFINITY) return Double.POSITIVE_INFINITY
        total += dist
        currentRow = place.row
        currentCol = place.col
    }

    return total
}

fun removeRedundantPlaces(
    route: List<Obshepit>,
    requiredDishes: List<String>,
    userLocation: UserLocation,
    distanceCache: RouteDistanceCache
): List<Obshepit> {
    val result = route.toMutableList()
    var i = 0

    while (i < result.size) {
        val testRoute = result.toMutableList()
        testRoute.removeAt(i)

        if (coversAllDishes(testRoute, requiredDishes)) {
            val oldLength = calculateRouteLengthAStar(userLocation, result, distanceCache)
            val newLength = calculateRouteLengthAStar(userLocation, testRoute, distanceCache)
            if (newLength <= oldLength) {
                result.removeAt(i)
                continue
            }
        }

        i++
    }

    return result
}

fun optimizeRouteOrder(
    route: List<Obshepit>,
    userLocation: UserLocation,
    distanceCache: RouteDistanceCache
): List<Obshepit> {
    if (route.size <= 1) return route

    var bestRoute = route.toList()
    var improved = true

    while (improved) {
        improved = false
        val currentBestLength = calculateRouteLengthAStar(userLocation, bestRoute, distanceCache)

        for (i in 0 until bestRoute.size) {
            for (j in i + 1 until bestRoute.size) {
                val candidate = bestRoute.toMutableList()
                val temp = candidate[i]
                candidate[i] = candidate[j]
                candidate[j] = temp

                val candidateLength = calculateRouteLengthAStar(userLocation, candidate, distanceCache)
                if (candidateLength < currentBestLength) {
                    bestRoute = candidate
                    improved = true
                    break
                }
            }
            if (improved) break
        }
    }

    return bestRoute
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

fun buildLandmarkVisitOrder(
    selectedLandmarks: List<Landmark>
): List<Landmark> {
    if (selectedLandmarks.isEmpty()) return emptyList()

    val points = selectedLandmarks.map {
        Point(it.row.toDouble(), it.col.toDouble())
    }

    val optimizedPoints = antColonyOptimization(points)

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

fun saveRatingsToFile(file: File, ratings: List<PlaceRating>) {
    file.printWriter().use { out ->
        ratings.forEach {
            out.println("${it.row},${it.col},${it.rating}")
        }
    }
}

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

fun generateRandomIndividual(
    requiredDishes: List<String>,
    foodPlaces: List<Obshepit>,
    userLocation: UserLocation,
    distanceCache: RouteDistanceCache
): RouteIndividual? {
    val shuffled = foodPlaces.shuffled()
    val selected = mutableListOf<Obshepit>()
    val collected = mutableSetOf<String>()

    for (place in shuffled) {
        if (requiredDishes.all { it in collected }) break
        val givesNewDish = place.dishes.any { it in requiredDishes && it !in collected }
        if (givesNewDish) {
            selected.add(place)
            collected.addAll(place.dishes.filter { it in requiredDishes })
        }
    }

    if (!requiredDishes.all { it in collected }) return null

    val cleaned = removeRedundantPlaces(selected, requiredDishes, userLocation, distanceCache)
    val optimized = optimizeRouteOrder(cleaned, userLocation, distanceCache)
    val length = calculateRouteLengthAStar(userLocation, optimized, distanceCache)

    if (length == Double.POSITIVE_INFINITY) return null
    return RouteIndividual(optimized, length)
}

fun crossoverIndividuals(
    parent1: RouteIndividual,
    parent2: RouteIndividual,
    requiredDishes: List<String>,
    allPlaces: List<Obshepit>,
    userLocation: UserLocation,
    distanceCache: RouteDistanceCache
): RouteIndividual? {
    if (parent1.route.isEmpty() || parent2.route.isEmpty()) return null

    val cut1 = Random.nextInt(parent1.route.size)
    val cut2 = Random.nextInt(parent2.route.size)

    val childRoute = mutableListOf<Obshepit>()
    childRoute.addAll(parent1.route.take(cut1))

    for (place in parent2.route.drop(cut2)) {
        if (place !in childRoute) childRoute.add(place)
    }

    val collected = childRoute.flatMap { it.dishes }.toMutableSet()

    if (!requiredDishes.all { it in collected }) {
        for (place in allPlaces.shuffled()) {
            if (place !in childRoute) {
                val givesNewDish = place.dishes.any { it in requiredDishes && it !in collected }
                if (givesNewDish) {
                    childRoute.add(place)
                    collected.addAll(place.dishes.filter { it in requiredDishes })
                }
                if (requiredDishes.all { it in collected }) break
            }
        }
    }

    if (!requiredDishes.all { it in collected }) return null

    val cleaned = removeRedundantPlaces(childRoute, requiredDishes, userLocation, distanceCache)
    val optimized = optimizeRouteOrder(cleaned, userLocation, distanceCache)
    val length = calculateRouteLengthAStar(userLocation, optimized, distanceCache)

    if (length == Double.POSITIVE_INFINITY) return null
    return RouteIndividual(optimized, length)
}

fun mutateIndividual(
    individual: RouteIndividual,
    requiredDishes: List<String>,
    userLocation: UserLocation,
    distanceCache: RouteDistanceCache
): RouteIndividual {
    val route = individual.route.toMutableList()

    if (route.size >= 2) {
        when (Random.nextInt(3)) {
            0 -> {
                val i = Random.nextInt(route.size)
                val j = Random.nextInt(route.size)
                val temp = route[i]
                route[i] = route[j]
                route[j] = temp
            }
            1 -> {
                val i = Random.nextInt(route.size)
                val j = Random.nextInt(route.size)
                val from = minOf(i, j)
                val to = maxOf(i, j)
                route.subList(from, to + 1).reverse()
            }
            2 -> {
                val removable = route.indices.shuffled().firstOrNull()
                if (removable != null) {
                    val test = route.toMutableList()
                    test.removeAt(removable)
                    if (coversAllDishes(test, requiredDishes)) {
                        route.clear()
                        route.addAll(test)
                    }
                }
            }
        }
    }

    val cleaned = removeRedundantPlaces(route, requiredDishes, userLocation, distanceCache)
    val optimized = optimizeRouteOrder(cleaned, userLocation, distanceCache)
    val length = calculateRouteLengthAStar(userLocation, optimized, distanceCache)

    return RouteIndividual(optimized, length)
}

fun buildOptimalFoodRouteGenetic(
    userLocation: UserLocation,
    requiredDishes: List<String>,
    foodPlaces: List<Obshepit>,
    grid: Array<BooleanArray>,
    populationSize: Int = 20,
    generations: Int = 100,
    mutationChance: Double = 0.15
): List<Obshepit> {
    if (requiredDishes.isEmpty()) return emptyList()

    val normalizedRequired = requiredDishes.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (normalizedRequired.isEmpty()) return emptyList()

    val availableDishes = foodPlaces.flatMap { it.dishes }.toSet()
    if (!normalizedRequired.all { it in availableDishes }) return emptyList()

    val distanceCache = RouteDistanceCache(grid)

    var population = mutableListOf<RouteIndividual>()
    var attempts = 0
    val maxAttempts = populationSize * 30

    while (population.size < populationSize && attempts < maxAttempts) {
        attempts++
        val individual = generateRandomIndividual(
            requiredDishes = normalizedRequired,
            foodPlaces = foodPlaces,
            userLocation = userLocation,
            distanceCache = distanceCache
        )
        if (individual != null) population.add(individual)
    }

    if (population.isEmpty()) return emptyList()

    repeat(generations) {
        val offspring = mutableListOf<RouteIndividual>()

        for (i in population.indices) {
            for (j in population.indices) {
                if (i == j) continue

                val child = crossoverIndividuals(
                    parent1 = population[i],
                    parent2 = population[j],
                    requiredDishes = normalizedRequired,
                    allPlaces = foodPlaces,
                    userLocation = userLocation,
                    distanceCache = distanceCache
                ) ?: continue

                val finalChild =
                    if (Random.nextDouble() < mutationChance) {
                        mutateIndividual(
                            individual = child,
                            requiredDishes = normalizedRequired,
                            userLocation = userLocation,
                            distanceCache = distanceCache
                        )
                    } else child

                if (finalChild.totalLength != Double.POSITIVE_INFINITY) {
                    offspring.add(finalChild)
                }
            }
        }

        population = (population + offspring)
            .distinctBy { individual ->
                individual.route.joinToString("->") { "${it.row}_${it.col}_${it.title}" }
            }
            .sortedBy { it.totalLength }
            .take(populationSize)
            .toMutableList()

        if (population.isEmpty()) return emptyList()
    }

    return population.minByOrNull { it.totalLength }?.route ?: emptyList()
}

fun parseWorkingHours(hoursStr: String): Pair<LocalTime, LocalTime>? {
    if (hoursStr.isBlank()) return null
    val parts = hoursStr.split("-")
    if (parts.size != 2) return null
    val formatter = DateTimeFormatter.ofPattern("H:mm")
    return try {
        val open = LocalTime.parse(parts[0].trim(), formatter)
        val close = LocalTime.parse(parts[1].trim(), formatter)
        open to close
    } catch (e: DateTimeParseException) {
        null
    }
}

fun isPlaceOpenAt(place: Obshepit, time: LocalTime): Boolean {
    val hours = parseWorkingHours(place.workingHours) ?: return true
    val (open, close) = hours
    return !time.isBefore(open) && !time.isAfter(close)
}


fun isRouteTimeFeasible(
    userLocation: UserLocation,
    route: List<Obshepit>,
    distanceCache: RouteDistanceCache,
    startTime: LocalTime,
    speedMetersPerSecond: Double,
    stayMinutes: Int
): Boolean {
    var currentTime = startTime
    var currentRow = userLocation.row
    var currentCol = userLocation.col

    for (place in route) {
        val distUnits = distanceCache.getDistance(currentRow, currentCol, place.row, place.col)
        if (distUnits == Double.POSITIVE_INFINITY) return false

        val distanceMeters = distUnits * 0.2
        val travelSeconds = distanceMeters / speedMetersPerSecond
        currentTime = currentTime.plusSeconds(travelSeconds.toLong())

        if (!isPlaceOpenAt(place, currentTime)) {
            return false
        }

        if (stayMinutes > 0) {
            currentTime = currentTime.plusMinutes(stayMinutes.toLong())
        }

        currentRow = place.row
        currentCol = place.col
    }
    return true
}

fun generateRandomIndividual(
    requiredDishes: List<String>,
    foodPlaces: List<Obshepit>,
    userLocation: UserLocation,
    distanceCache: RouteDistanceCache,
    startTime: LocalTime,
    speedMetersPerSecond: Double,
    stayMinutes: Int
): RouteIndividual? {
    val shuffled = foodPlaces.shuffled()
    val selected = mutableListOf<Obshepit>()
    val collected = mutableSetOf<String>()

    for (place in shuffled) {
        if (requiredDishes.all { it in collected }) break
        val givesNewDish = place.dishes.any { it in requiredDishes && it !in collected }
        if (givesNewDish) {
            selected.add(place)
            collected.addAll(place.dishes.filter { it in requiredDishes })
        }
    }

    if (!requiredDishes.all { it in collected }) return null

    val cleaned = removeRedundantPlaces(selected, requiredDishes, userLocation, distanceCache)
    val optimized = optimizeRouteOrder(cleaned, userLocation, distanceCache)

    if (!isRouteTimeFeasible(userLocation, optimized, distanceCache, startTime, speedMetersPerSecond, stayMinutes)) {
        return null
    }

    val length = calculateRouteLengthAStar(userLocation, optimized, distanceCache)
    if (length == Double.POSITIVE_INFINITY) return null
    return RouteIndividual(optimized, length)
}

fun crossoverIndividuals(
    parent1: RouteIndividual,
    parent2: RouteIndividual,
    requiredDishes: List<String>,
    allPlaces: List<Obshepit>,
    userLocation: UserLocation,
    distanceCache: RouteDistanceCache,
    startTime: LocalTime,
    speedMetersPerSecond: Double,
    stayMinutes: Int
): RouteIndividual? {
    if (parent1.route.isEmpty() || parent2.route.isEmpty()) return null

    val cut1 = Random.nextInt(parent1.route.size)
    val cut2 = Random.nextInt(parent2.route.size)

    val childRoute = mutableListOf<Obshepit>()
    childRoute.addAll(parent1.route.take(cut1))

    for (place in parent2.route.drop(cut2)) {
        if (place !in childRoute) childRoute.add(place)
    }

    val collected = childRoute.flatMap { it.dishes }.toMutableSet()

    if (!requiredDishes.all { it in collected }) {
        for (place in allPlaces.shuffled()) {
            if (place !in childRoute) {
                val givesNewDish = place.dishes.any { it in requiredDishes && it !in collected }
                if (givesNewDish) {
                    childRoute.add(place)
                    collected.addAll(place.dishes.filter { it in requiredDishes })
                }
                if (requiredDishes.all { it in collected }) break
            }
        }
    }

    if (!requiredDishes.all { it in collected }) return null

    val cleaned = removeRedundantPlaces(childRoute, requiredDishes, userLocation, distanceCache)
    val optimized = optimizeRouteOrder(cleaned, userLocation, distanceCache)

    if (!isRouteTimeFeasible(userLocation, optimized, distanceCache, startTime, speedMetersPerSecond, stayMinutes)) {
        return null
    }

    val length = calculateRouteLengthAStar(userLocation, optimized, distanceCache)
    if (length == Double.POSITIVE_INFINITY) return null
    return RouteIndividual(optimized, length)
}

fun mutateIndividual(
    individual: RouteIndividual,
    requiredDishes: List<String>,
    userLocation: UserLocation,
    distanceCache: RouteDistanceCache,
    startTime: LocalTime,
    speedMetersPerSecond: Double,
    stayMinutes: Int
): RouteIndividual? {
    val route = individual.route.toMutableList()

    if (route.size >= 2) {
        when (Random.nextInt(3)) {
            0 -> {
                val i = Random.nextInt(route.size)
                val j = Random.nextInt(route.size)
                val temp = route[i]
                route[i] = route[j]
                route[j] = temp
            }
            1 -> {
                val i = Random.nextInt(route.size)
                val j = Random.nextInt(route.size)
                val from = minOf(i, j)
                val to = maxOf(i, j)
                route.subList(from, to + 1).reverse()
            }
            2 -> {
                val removable = route.indices.shuffled().firstOrNull()
                if (removable != null) {
                    val test = route.toMutableList()
                    test.removeAt(removable)
                    if (coversAllDishes(test, requiredDishes)) {
                        route.clear()
                        route.addAll(test)
                    }
                }
            }
        }
    }

    val cleaned = removeRedundantPlaces(route, requiredDishes, userLocation, distanceCache)
    val optimized = optimizeRouteOrder(cleaned, userLocation, distanceCache)

    if (!isRouteTimeFeasible(userLocation, optimized, distanceCache, startTime, speedMetersPerSecond, stayMinutes)) {
        return null
    }

    val length = calculateRouteLengthAStar(userLocation, optimized, distanceCache)
    return RouteIndividual(optimized, length)
}

fun buildOptimalFoodRouteGenetic(
    userLocation: UserLocation,
    requiredDishes: List<String>,
    foodPlaces: List<Obshepit>,
    grid: Array<BooleanArray>,
    populationSize: Int = 20,
    generations: Int = 100,
    mutationChance: Double = 0.15,
    startTime: LocalTime = LocalTime.now(),
    speedMetersPerSecond: Double = 5000.0 / 3600.0, // 5 км/ч
    stayMinutes: Int = 30
): List<Obshepit> {
    if (requiredDishes.isEmpty()) return emptyList()

    val normalizedRequired = requiredDishes.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (normalizedRequired.isEmpty()) return emptyList()

    val availableDishes = foodPlaces.flatMap { it.dishes }.toSet()
    if (!normalizedRequired.all { it in availableDishes }) return emptyList()

    val distanceCache = RouteDistanceCache(grid)

    var population = mutableListOf<RouteIndividual>()
    var attempts = 0
    val maxAttempts = populationSize * 30

    while (population.size < populationSize && attempts < maxAttempts) {
        attempts++
        val individual = generateRandomIndividual(
            requiredDishes = normalizedRequired,
            foodPlaces = foodPlaces,
            userLocation = userLocation,
            distanceCache = distanceCache,
            startTime = startTime,
            speedMetersPerSecond = speedMetersPerSecond,
            stayMinutes = stayMinutes
        )
        if (individual != null) population.add(individual)
    }

    if (population.isEmpty()) return emptyList()

    repeat(generations) {
        val offspring = mutableListOf<RouteIndividual>()

        for (i in population.indices) {
            for (j in population.indices) {
                if (i == j) continue

                val child = crossoverIndividuals(
                    parent1 = population[i],
                    parent2 = population[j],
                    requiredDishes = normalizedRequired,
                    allPlaces = foodPlaces,
                    userLocation = userLocation,
                    distanceCache = distanceCache,
                    startTime = startTime,
                    speedMetersPerSecond = speedMetersPerSecond,
                    stayMinutes = stayMinutes
                ) ?: continue

                val finalChild =
                    if (Random.nextDouble() < mutationChance) {
                        mutateIndividual(
                            individual = child,
                            requiredDishes = normalizedRequired,
                            userLocation = userLocation,
                            distanceCache = distanceCache,
                            startTime = startTime,
                            speedMetersPerSecond = speedMetersPerSecond,
                            stayMinutes = stayMinutes
                        )
                    } else child

                if (finalChild != null && finalChild.totalLength != Double.POSITIVE_INFINITY) {
                    offspring.add(finalChild)
                }
            }
        }

        population = (population + offspring)
            .distinctBy { individual ->
                individual.route.joinToString("->") { "${it.row}_${it.col}_${it.title}" }
            }
            .sortedBy { it.totalLength }
            .take(populationSize)
            .toMutableList()

        if (population.isEmpty()) return emptyList()
    }

    val feasiblePopulation = population.filter {
        isRouteTimeFeasible(
            userLocation, it.route, distanceCache,
            startTime, speedMetersPerSecond, stayMinutes
        )
    }

    return feasiblePopulation.minByOrNull { it.totalLength }?.route ?: emptyList()
}