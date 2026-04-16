//package com.example.universitymapproj.serialization
//
//import com.example.universitymapproj.MapConfig
//import com.example.universitymapproj.models.Obshepit
//
//fun exportGridToString(grid: Array<BooleanArray>): String {
//    val sb = StringBuilder()
//    for (row in grid) {
//        for (cell in row) sb.append(if (cell) "1" else "0")
//    }
//    return sb.toString()
//}
//
//fun importGridFromString(data: String): Array<BooleanArray> {
//    if (data.length != MapConfig.ROWS * MapConfig.COLS) {
//        return Array(MapConfig.ROWS) { BooleanArray(MapConfig.COLS) { false } }
//    }
//
//    val grid = Array(MapConfig.ROWS) { BooleanArray(MapConfig.COLS) }
//    var index = 0
//    for (r in 0 until MapConfig.ROWS) {
//        for (c in 0 until MapConfig.COLS) {
//            grid[r][c] = data[index] == '1'
//            index++
//        }
//    }
//    return grid
//}
//
//fun exportFoodPlacesToString(foodPlaces: List<Obshepit>): String {
//    return foodPlaces.joinToString(";") { place ->
//        val dishesString = place.dishes.joinToString("|")
//            .replace(",", " ")
//            .replace(";", " ")
//
//        listOf(
//            place.row.toString(),
//            place.col.toString(),
//            place.title.replace(",", " ").replace(";", " "),
//            place.description.replace(",", " ").replace(";", " "),
//            place.workingHours.replace(",", " ").replace(";", " "),
//            place.type.replace(",", " ").replace(";", " "),
//            dishesString
//        ).joinToString(",")
//    }
//}
//
//fun importFoodPlacesFromString(data: String): MutableList<Obshepit> {
//    if (data.isBlank()) return mutableListOf()
//
//    return data.split(";").mapNotNull { item ->
//        val parts = item.split(",")
//        if (parts.size < 6) return@mapNotNull null
//
//        val row = parts[0].toIntOrNull() ?: return@mapNotNull null
//        val col = parts[1].toIntOrNull() ?: return@mapNotNull null
//
//        val dishes = if (parts.size >= 7 && parts[6].isNotBlank()) {
//            parts[6].split("|").map { it.trim() }.filter { it.isNotBlank() }
//        } else {
//            emptyList()
//        }
//
//        Obshepit(
//            row = row,
//            col = col,
//            title = parts[2],
//            description = parts[3],
//            workingHours = parts[4],
//            type = parts[5],
//            dishes = dishes
//        )
//    }.toMutableList()
//}