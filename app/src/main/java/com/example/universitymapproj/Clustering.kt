//package com.example.universitymapproj.clustering
//
//import com.example.universitymapproj.models.ClusterCentroid
//import com.example.universitymapproj.models.ClusterPoint
//import com.example.universitymapproj.models.ClusteredPoint
//import kotlin.math.abs
//import kotlin.math.sqrt
//
//fun distance(aRow: Double, aCol: Double, bRow: Double, bCol: Double): Double {
//    return sqrt((aRow - bRow) * (aRow - bRow) + (aCol - bCol) * (aCol - bCol))
//}
//
//fun runKMeans(points: List<ClusterPoint>, k: Int, maxIterations: Int = 100): List<ClusteredPoint> {
//    if (points.isEmpty()) return emptyList()
//
//    val realK = k.coerceAtMost(points.size).coerceAtLeast(1)
//
//    var centroids = points.take(realK).map {
//        ClusterCentroid(it.row.toDouble(), it.col.toDouble())
//    }
//
//    var assignments = List(points.size) { 0 }
//
//    repeat(maxIterations) {
//        val newAssignments = points.map { point ->
//            centroids.indices.minByOrNull { idx ->
//                distance(
//                    point.row.toDouble(),
//                    point.col.toDouble(),
//                    centroids[idx].x,
//                    centroids[idx].y
//                )
//            } ?: 0
//        }
//
//        val newCentroids = centroids.indices.map { clusterIndex ->
//            val clusterPoints = points.filterIndexed { index, _ ->
//                newAssignments[index] == clusterIndex
//            }
//
//            if (clusterPoints.isEmpty()) {
//                centroids[clusterIndex]
//            } else {
//                ClusterCentroid(
//                    x = clusterPoints.map { it.row }.average(),
//                    y = clusterPoints.map { it.col }.average()
//                )
//            }
//        }
//
//        val changed = centroids.indices.any { i ->
//            abs(centroids[i].x - newCentroids[i].x) > 0.001 ||
//                    abs(centroids[i].y - newCentroids[i].y) > 0.001
//        }
//
//        assignments = newAssignments
//        centroids = newCentroids
//
//        if (!changed) return@repeat
//    }
//
//    return points.mapIndexed { index, point ->
//        ClusteredPoint(
//            row = point.row,
//            col = point.col,
//            clusterIndex = assignments[index]
//        )
//    }
//}