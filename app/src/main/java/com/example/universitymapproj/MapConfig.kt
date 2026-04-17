package com.example.universitymapproj

import android.content.Context
import android.util.Log
import com.example.universitymapproj.models.Landmark

object MapConfig {
    const val COLS = 162
    const val ROWS = 183
    const val IMG_W = 1170f
    const val IMG_H = 1414f

    const val REF_LAT = 56.46950
    const val REF_LON = 84.94750
    const val REF_ROW = 104
    const val REF_COL = 81
    const val LAT_SCALE = 34400.0
    const val LON_SCALE = 7600.0

    const val ROW_CORRECTION = 0

    fun gpsToGrid(lat: Double, lon: Double): Pair<Int, Int> {
        val row = (REF_ROW + ROW_CORRECTION) + (lat - REF_LAT) * LAT_SCALE
        val col = REF_COL + (lon - REF_LON) * LON_SCALE
        val finalRow = (REF_ROW + ROW_CORRECTION) + (lat - REF_LAT) * LAT_SCALE
        Log.d("MAP_CALC", "Lat: $lat -> Row: $finalRow (Correction: $ROW_CORRECTION)")
        return row.toInt().coerceIn(0, ROWS - 1) to col.toInt().coerceIn(0, COLS - 1)
    }
    fun readFromAssets(context: Context, fileName: String): String {
        return try {
            val inputStream = context.assets.open(fileName)
            java.io.InputStreamReader(inputStream).buffered().use { it.readText() }
        } catch (e: Exception) {
            Log.e("MAP_ERROR", "Ошибка при чтении файла")
            ""
        }
    }

    fun logLongString(tag: String, text: String) {
        val chunkSize = 4000
        var i = 0
        while (i < text.length) {
            val end = (i + chunkSize).coerceAtMost(text.length)
            Log.d(tag, text.substring(i, end))
            i += chunkSize
        }
    }

    var SAVED_GRID = ""
    var SAVED_FOOD_PLACES = ""
    const val DEFAULT_FOOD = ""
}

object LandmarkConfig {
    val DEFAULT_LANDMARKS = listOf(
        Landmark(1, 104, 81, "Главный корпус", "Центральное здание университета"),
        Landmark(2, 120, 33, "Библиотека", "Университетская библиотека"),
        Landmark(3, 72, 63, "Второй корпус", "Корпус ИПМКН и HITs"),
        Landmark(4, 82, 56, "Спортивный корпус", "Корпус для занятий физической культурой"),
        Landmark(5, 112, 81, "Парк", "Лавочки и клумбы"),
        Landmark(6, 83, 82, "Центр Культуры", "Место проведения мероприятий"),
        Landmark(7, 76, 16, "Центр Культуры", "Место проведения мероприятий"),
        Landmark(8, 59, 42, "Озеро", "Есть уточки")
    )
}