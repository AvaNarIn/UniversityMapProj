package com.example.universitymapproj.routing

import com.example.universitymapproj.models.Point
import kotlin.math.pow
import kotlin.random.Random

class AntColonyOptimizer {
    fun optimize(
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
}