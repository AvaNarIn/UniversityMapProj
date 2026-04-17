package com.example.universitymapproj.routing

import com.example.universitymapproj.models.*
import com.example.universitymapproj.pathfinding.AStarPathfinder
import com.example.universitymapproj.pathfinding.RouteDistanceCache
import java.time.LocalTime
import kotlin.random.Random

class GeneticFoodRouteOptimizer {
    fun findOptimalRoute(
        userLocation: UserLocation,
        requiredDishes: List<String>,
        foodPlaces: List<Obshepit>,
        grid: Array<BooleanArray>,
        populationSize: Int = 20,
        generations: Int = 100,
        mutationChance: Double = 0.15,
        startTime: LocalTime = LocalTime.now(),
        speedMetersPerSecond: Double = 5000.0 / 3600.0,
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


    private fun coversAllDishes(route: List<Obshepit>, requiredDishes: List<String>): Boolean {
        val collected = route.flatMap { it.dishes }.toSet()
        return requiredDishes.all { it in collected }
    }

    private fun calculateRouteLengthAStar(
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

    private fun removeRedundantPlaces(
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

    private fun optimizeRouteOrder(
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

    private fun parseWorkingHours(hoursStr: String): Pair<LocalTime, LocalTime>? {
        if (hoursStr.isBlank()) return null
        val parts = hoursStr.split("-")
        if (parts.size != 2) return null
        val formatter = java.time.format.DateTimeFormatter.ofPattern("H:mm")
        return try {
            val open = LocalTime.parse(parts[0].trim(), formatter)
            val close = LocalTime.parse(parts[1].trim(), formatter)
            open to close
        } catch (e: java.time.format.DateTimeParseException) {
            null
        }
    }

    private fun isPlaceOpenAt(place: Obshepit, time: LocalTime): Boolean {
        val hours = parseWorkingHours(place.workingHours) ?: return true
        val (open, close) = hours
        return !time.isBefore(open) && !time.isAfter(close)
    }

    private fun isRouteTimeFeasible(
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

    private fun generateRandomIndividual(
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

    private fun crossoverIndividuals(
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

    private fun mutateIndividual(
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
            when (Random.nextInt(2)) {
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
}