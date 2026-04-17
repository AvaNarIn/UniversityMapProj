package com.example.universitymapproj

import android.content.Context
import android.util.Log
import com.example.universitymapproj.models.Landmark

object MapConfig {
    const val COLS = 162
    const val ROWS = 183
    const val IMG_W = 1170f
    const val IMG_H = 1414f

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