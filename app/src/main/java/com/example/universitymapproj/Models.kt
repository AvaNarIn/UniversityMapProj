//package com.example.universitymapproj.models
//
//data class Point(val x: Double, val y: Double)
//
//data class Obshepit(
//    val row: Int,
//    val col: Int,
//    val title: String,
//    val description: String = "",
//    val workingHours: String = "",
//    val type: String = "",
//    val dishes: List<String> = emptyList()
//)
//
//data class Landmark(
//    val id: Int,
//    val row: Int,
//    val col: Int,
//    val title: String,
//    val description: String = ""
//)
//
//data class Node(
//    val r: Int,
//    val c: Int,
//    var g: Int = 0,
//    var h: Int = 0,
//    var parent: Node? = null
//) {
//    val f get() = g + h
//}
//
//enum class FoodEditMode {
//    NONE,
//    ADD,
//    DELETE
//}
//
//data class ClusterPoint(val row: Int, val col: Int)
//data class ClusterCentroid(val x: Double, val y: Double)
//data class ClusteredPoint(val row: Int, val col: Int, val clusterIndex: Int)
//
//data class UserLocation(val row: Int, val col: Int)
//
//data class RouteIndividual(
//    val route: List<Obshepit>,
//    val totalLength: Double
//)
//
//data class PlaceRating(
//    val row: Int,
//    val col: Int,
//    val rating: Int
//)